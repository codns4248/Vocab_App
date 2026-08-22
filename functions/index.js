const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https"); // v2 필수
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const { CloudTasksClient } = require("@google-cloud/tasks");
const Anthropic = require("@anthropic-ai/sdk");
const { base64ByteLength, classifyError, logApiUsage } = require("./usageLogger");

if (!admin.apps.length) {
    admin.initializeApp();
}

const client = new CloudTasksClient();

const PROJECT_ID = "vocaapp-bf580";
const LOCATION = "asia-northeast3";
const QUEUE_NAME = "voca-review-queue";

// Claude API 키는 앱이 아닌 서버(Secret Manager)에만 존재합니다.
// 설정: firebase functions:secrets:set ANTHROPIC_API_KEY
const ANTHROPIC_API_KEY = defineSecret("ANTHROPIC_API_KEY");

// 카카오 REST API 키. 회원 탈퇴 시 카카오 연결 해제(관리자 API)에 쓴다.
// 설정: firebase functions:secrets:set KAKAO_REST_API_KEY
const KAKAO_REST_API_KEY = defineSecret("KAKAO_REST_API_KEY");

// 카카오 콘솔의 앱 ID(숫자). 비밀값이 아니라 상수로 둔다.
// 액세스 토큰이 "우리 앱" 것인지 대조하는 데 쓴다. 이 대조가 없으면
// 공격자가 자기 카카오 앱에서 받은 토큰으로 우리 서비스에 로그인할 수 있다.
const KAKAO_APP_ID = 1553470;

const AI_MODEL = "claude-haiku-4-5";
const MAX_IMAGES = 5; // 갤러리 선택 상한(PickMultipleVisualMedia)과 동일

// 구조화된 출력 스키마 — 기존 Gemini responseSchema와 동일한 형태
const WORD_LIST_SCHEMA = {
    type: "object",
    properties: {
        words: {
            type: "array",
            description: "추출된 단어 리스트",
            items: {
                type: "object",
                properties: {
                    word: { type: "string", description: "추출된 단어" },
                    meaning: { type: "string", description: "단어의 뜻" },
                    pronunciation: { type: "string", description: "단어의 발음을 한국어로" },
                },
                required: ["word", "meaning", "pronunciation"],
                additionalProperties: false,
            },
        },
    },
    required: ["words"],
    additionalProperties: false,
};

// 이미지는 장별로 따로 호출합니다. 여러 장을 한 호출에 넣으면
// (1) 모델이 전체를 한 장면처럼 훑고 중간에 끝내 단어를 빠뜨리고
// (2) 출력 토큰과 소요 시간이 장수만큼 누적돼 max_tokens / 함수 타임아웃에 걸립니다.
const EXTRACT_PROMPT = `이 이미지에 있는 영어 단어를 하나도 빠짐없이 추출하고, 단어마다 한국어 뜻과 한국어 발음을 적어줘.
만약 전문 용어라면 가장 대중적인 뜻을 선택해줘.

반드시 지켜야 할 규칙:
- 이미지를 위에서 아래로 훑으면서 모든 단어를 포함해. 중간을 생략하거나 "이하 생략" 같은 식으로 요약하지 마.
- 같은 단어가 여러 번 나오면 한 번만 포함해.`;

// 실패 종류에 따라 사용자에게 보여줄 메시지를 정합니다.
const ERROR_RESPONSES = {
    refusal: ["invalid-argument", "이 이미지는 분석할 수 없습니다."],
    max_tokens: ["resource-exhausted", "단어가 너무 많습니다. 사진을 나눠서 시도해주세요."],
};

/**
 * 실패 종류를 클라이언트에 던질 HttpsError로 바꿉니다.
 * @param {string|null} errorType 실패 종류
 * @return {HttpsError} 클라이언트에 던질 에러
 */
function toHttpsError(errorType) {
    const mapped = ERROR_RESPONSES[errorType];
    if (mapped) {
        return new HttpsError(mapped[0], mapped[1]);
    }
    return new HttpsError("internal", "단어 분석에 실패했습니다.");
}

/**
 * 여러 호출에서 모인 단어를 합치고 중복을 제거합니다.
 * 호출이 나뉘면 모델이 장 간 중복을 잡지 못하므로 서버에서 걸러야 합니다.
 *
 * @param {object[]} words 단어 객체 배열
 * @return {object[]} 중복이 제거된 단어 배열(처음 등장한 것을 유지)
 */
function dedupeWords(words) {
    const seen = new Set();
    const merged = [];
    for (const item of words) {
        if (!item || typeof item.word !== "string") {
            continue;
        }
        const key = item.word.trim().toLowerCase();
        if (key === "" || seen.has(key)) {
            continue;
        }
        seen.add(key);
        merged.push(item);
    }
    return merged;
}

/**
 * 이미지 한 장에서 단어를 추출합니다. 이 함수는 예외를 던지지 않고
 * 성공/실패를 결과 객체로 돌려주며, 어느 쪽이든 사용량을 기록합니다.
 *
 * @param {object} anthropic Anthropic 클라이언트
 * @param {string} imageData base64 JPEG 문자열
 * @param {string|null} uid Firebase Auth UID
 * @return {Promise<{ok: boolean, words?: object[], errorType?: string}>} 추출 결과
 */
async function extractFromSingleImage(anthropic, imageData, uid) {
    const imageBytes = base64ByteLength(imageData);

    const startedAt = Date.now();
    let response = null;
    let apiError = null;
    try {
        response = await anthropic.messages.create({
            model: AI_MODEL,
            max_tokens: 16000,
            output_config: {
                format: { type: "json_schema", schema: WORD_LIST_SCHEMA },
            },
            messages: [
                {
                    role: "user",
                    content: [
                        { type: "image", source: { type: "base64", media_type: "image/jpeg", data: imageData } },
                        { type: "text", text: EXTRACT_PROMPT },
                    ],
                },
            ],
        });
    } catch (error) {
        apiError = error;
    }
    const latencyMs = Date.now() - startedAt;

    // 성공/실패 양쪽 모두, 결과를 돌려주기 전에 기록합니다.
    const logUsage = (success, errorType, extractedWordCount) =>
        logApiUsage({
            uid,
            model: AI_MODEL,
            response,
            latencyMs,
            imageCount: 1,
            imageBytes,
            extractedWordCount,
            success,
            errorType,
        });

    const fail = (errorType) => {
        logUsage(false, errorType, 0);
        return { ok: false, errorType };
    };

    if (apiError) {
        console.error("Claude 호출 실패:", apiError);
        return fail(classifyError(apiError));
    }

    // 안전 거부 / 토큰 초과는 스키마를 만족하지 않을 수 있으므로 먼저 확인
    if (response.stop_reason === "refusal") {
        return fail("refusal");
    }
    if (response.stop_reason === "max_tokens") {
        return fail("max_tokens");
    }

    const textBlock = (response.content || []).find((block) => block.type === "text");
    if (!textBlock) {
        console.error("텍스트 블록 없음:", JSON.stringify(response.content));
        return fail("no_text_block");
    }

    let parsed;
    try {
        parsed = JSON.parse(textBlock.text);
    } catch (error) {
        console.error("파싱 실패:", textBlock.text);
        return fail("json_parse_error");
    }

    const words = Array.isArray(parsed.words) ? parsed.words : [];
    logUsage(true, null, words.length);
    return { ok: true, words };
}

function getTaskPath(uid, docId) {
    const uniqueSuffix = Date.now();
    return client.taskPath(PROJECT_ID, LOCATION, QUEUE_NAME, `task_${uid}_${docId}_${uniqueSuffix}`);
}

// 사진에서 단어를 추출하는 함수 (Claude API 호출은 전부 서버에서만 이뤄집니다)
exports.extractWordsFromImages = onCall(
    {
        region: "asia-northeast3",
        secrets: [ANTHROPIC_API_KEY],
        memory: "512MiB",
        timeoutSeconds: 180,
    },
    async (request) => {
        if (!request.auth) {
            throw new HttpsError("unauthenticated", "로그인이 필요합니다.");
        }

        const images = request.data?.images;
        if (!Array.isArray(images) || images.length === 0) {
            throw new HttpsError("invalid-argument", "이미지가 없습니다.");
        }
        if (images.length > MAX_IMAGES) {
            throw new HttpsError("invalid-argument", `이미지는 최대 ${MAX_IMAGES}장까지 가능합니다.`);
        }

        const uid = request.auth?.uid ?? null;
        const anthropic = new Anthropic({ apiKey: ANTHROPIC_API_KEY.value() });

        // 장별로 병렬 호출합니다. 한 호출에 몰아넣으면 출력 토큰과 소요 시간이
        // 장수만큼 누적되지만, 나누면 각 호출이 자기 몫만 쓰고 전체 시간도
        // 합이 아니라 가장 느린 한 장이 됩니다.
        const settled = await Promise.allSettled(
            images.map((imageData) => extractFromSingleImage(anthropic, imageData, uid))
        );

        const succeeded = [];
        const failedTypes = [];
        settled.forEach((outcome, index) => {
            if (outcome.status === "rejected") {
                // extractFromSingleImage는 예외를 던지지 않도록 만들었지만, 만약을 대비합니다.
                console.error(`${index + 1}번째 이미지 처리 중 예외:`, outcome.reason);
                failedTypes.push(classifyError(outcome.reason));
                return;
            }
            if (outcome.value.ok) {
                succeeded.push(outcome.value);
            } else {
                failedTypes.push(outcome.value.errorType);
            }
        });

        // 전부 실패했을 때만 사용자에게 에러를 돌려줍니다.
        if (succeeded.length === 0) {
            throw toHttpsError(failedTypes[0]);
        }

        if (failedTypes.length > 0) {
            console.warn(`이미지 ${images.length}장 중 ${failedTypes.length}장 실패:`, failedTypes.join(", "));
        }

        const words = dedupeWords(succeeded.flatMap((result) => result.words));

        // failedImageCount는 기존 앱이 무시하는 추가 필드입니다.
        // 일부만 성공했을 때 사용자에게 알려주려면 앱에서 이 값을 읽으면 됩니다.
        return { words, failedImageCount: failedTypes.length };
    }
);

exports.scheduleReviewNotification = onCall({ region: "asia-northeast3" }, async (request) => {
    const { data, auth } = request;
    // Android에서 넘겨준 scheduledTime과 rollbackTime을 받습니다.
    const { docId, title, scheduledTime, rollbackTime } = data;
    const uid = auth.uid;

    const vocabRef = admin.firestore()
        .collection("users").doc(uid)
        .collection("vocabularies").doc(docId);

    try {
        // [1] 기존 Task 삭제 (알림용, 롤백용 둘 다)
        const doc = await vocabRef.get();
        const oldReviewId = doc.data()?.currentTaskId;
        const oldRollbackId = doc.data()?.currentRollbackTaskId;
        
        if (oldReviewId) await client.deleteTask({ name: oldReviewId }).catch(() => {});
        if (oldRollbackId) await client.deleteTask({ name: oldRollbackId }).catch(() => {});

        // [2] 알림(Review) Task 생성
        const reviewTaskName = client.taskPath(PROJECT_ID, LOCATION, QUEUE_NAME, `rev_${uid}_${docId}_${Date.now()}`);
        const reviewTask = {
            name: reviewTaskName,
            httpRequest: {
                httpMethod: "POST",
                url: `https://${LOCATION}-${PROJECT_ID}.cloudfunctions.net/sendFcmNotification`,
                headers: { "Content-Type": "application/json" },
                body: Buffer.from(JSON.stringify({ uid, docId, title })).toString("base64"),
            },
            scheduleTime: { seconds: Number(scheduledTime) },
        };

        // [3] 롤백(Rollback) Task 생성
        const rollbackTaskName = client.taskPath(PROJECT_ID, LOCATION, QUEUE_NAME, `roll_${uid}_${docId}_${Date.now()}`);
        const rollbackTask = {
            name: rollbackTaskName,
            httpRequest: {
                httpMethod: "POST",
                url: `https://${LOCATION}-${PROJECT_ID}.cloudfunctions.net/handleRollback`, // 롤백 처리 함수 URL
                headers: { "Content-Type": "application/json" },
                body: Buffer.from(JSON.stringify({ uid, docId })).toString("base64"),
            },
            scheduleTime: { seconds: Number(rollbackTime) },
        };

        const parent = client.queuePath(PROJECT_ID, LOCATION, QUEUE_NAME);
        const [revRes] = await client.createTask({ parent, task: reviewTask });
        const [rollRes] = await client.createTask({ parent, task: rollbackTask });

        // [4] DB 업데이트 (두 Task ID 모두 저장)
        await vocabRef.update({
            currentTaskId: revRes.name,
            currentRollbackTaskId: rollRes.name,
            rollbackState: false
        });

        return { success: true };
    } catch (error) {
        console.error("Task 예약 에러:", error);
        throw new Error(error.message);
    }
});

// 알림 취소 함수
exports.cancelReviewNotification = onCall({ region: "asia-northeast3" }, async (request) => {
    const { docId } = request.data;
    const uid = request.auth.uid;

    try {
        const vocabDoc = await admin.firestore().collection("users").doc(uid)
            .collection("vocabularies").doc(docId).get();
        
        const data = vocabDoc.data();
        const taskId = data?.currentTaskId;
        const rollbackId = data?.currentRollbackTaskId; // 롤백 ID 추가

        // 알림 삭제
        if (taskId) {
            await client.deleteTask({ name: taskId }).catch(() => console.log("알림 이미 실행됨"));
        }
        // 롤백 삭제 (이게 빠지면 복습을 해도 나중에 롤백이 되어버립니다)
        if (rollbackId) {
            await client.deleteTask({ name: rollbackId }).catch(() => console.log("롤백 이미 실행됨"));
        }

        return { success: true };
    } catch (error) {
        return { success: false, message: error.message };
    }
});

exports.sendFcmNotification = onRequest({ region: "asia-northeast3" }, async (req, res) => {
    try {
        // [1] 안전하게 데이터 파싱 (기존 로직)
        let payload = req.body;
        if (typeof payload === 'string') payload = JSON.parse(payload);
        if (Buffer.isBuffer(payload)) payload = JSON.parse(payload.toString());

        const { uid, docId, title } = payload;

        // --- 여기서부터 추가 ---
        const vocabRef = admin.firestore()
            .collection("users").doc(uid)
            .collection("vocabularies").doc(docId);

        const vocabSnap = await vocabRef.get();
        const vocabData = vocabSnap.data();

        // 학습 모드가 꺼져 있다면 중단
        if (!vocabData || vocabData.isStudying === false) {
            console.log(`[알림 중단] UID: ${uid}, DocID: ${docId} - 학습 모드 비활성화 상태입니다.`);
            return res.status(200).send("Studying disabled");
        }
        // --- 여기까지 추가 ---

        // [2] Firestore 업데이트 및 FCM 발송 로직 계속...
        await vocabRef.update({
            buttonOn: true,
            lastNotificationSent: admin.firestore.FieldValue.serverTimestamp()
        });

        
        // [3] FCM 메시지 구성
        console.log("FCM message data 타입 확인:", JSON.stringify({
        docId: typeof docId,
        uid: typeof uid,
        title: typeof title
        }));  // ← 여기 추가

                // [3] FCM 메시지 구성
        const message = {
            notification: {
                title: "복습 시간이 되었습니다! ✍️",
                body: `'${String(title || "단어장")}'을 복습할 시간입니다.`
            },
            data: {
            // null이나 undefined가 들어가지 않도록 확실하게 처리
            docId: (docId || "").toString(), 
            type: "REVIEW_NOTIFICATION",
            uid: (uid || "").toString()
            },
            // 토큰도 확실하게 문자열임을 보장
            token: String((await admin.firestore().collection("users").doc(uid).get()).data()?.fcmToken || "")
        };

        // 토큰이 비어있으면 전송 시도조차 하지 않음
        if (!message.token || message.token === "undefined") {
            console.error("FCM 토큰이 유효하지 않습니다.");
            return res.status(400).send("Invalid Token");
        }

        await admin.messaging().send(message);

        console.log(`FCM 발송 최종 성공: ${uid}`);
        return res.status(200).send("OK");

    } catch (error) {
        console.error("최종 에러 발생:", error);
        return res.status(500).send(error.message);
    }
});

// 롤백을 위한 함수
// 롤백을 위한 함수 (전체 버전)
exports.handleRollback = onRequest({ region: "asia-northeast3" }, async (req, res) => {
    try {
        // [1] 데이터 파싱 및 기본 검증
        let payload = req.body;
        if (typeof payload === 'string') payload = JSON.parse(payload);
        if (Buffer.isBuffer(payload)) payload = JSON.parse(payload.toString());
        
        const { uid, docId } = payload;
        if (!uid || !docId) {
            console.error("롤백 에러: UID 또는 DocID 누락");
            return res.status(400).send("Missing Data");
        }

        // [2] Firestore에서 현재 단어장 상태 조회
        const vocabRef = admin.firestore()
            .collection("users").doc(uid)
            .collection("vocabularies").doc(docId);
        
        const vocabSnap = await vocabRef.get();
        if (!vocabSnap.exists) {
            console.log("롤백 중단: 해당 문서가 존재하지 않음");
            return res.status(404).send("Doc Not Found");
        }
        
        const vocabData = vocabSnap.data();

        // ★ 핵심 방어 로직: 사용자가 스위치를 껐다면 롤백 절차를 진행하지 않음
        if (vocabData.isStudying === false) {
            console.log(`[롤백 중단] UID: ${uid}, DocID: ${docId} - 학습 모드가 꺼져 있습니다.`);
            return res.status(200).send("Studying disabled. Rollback skipped.");
        }

        const currentStamp = vocabData.stampCount || 0;
        const title = vocabData.title || "단어장";

        // [3] 복습 시간 및 그레이스 타임 설정 로드 (기본값 설정)
        let intervalMin = 10;
        let graceMin = 5;

        try {
            const settingsDoc = await admin.firestore()
                .collection("reviewAndRollbackTimeSetting")
                .doc("reviewAndRollbackTimeSetting")
                .get();
            
            if (settingsDoc.exists) {
                const settingsData = settingsDoc.data();
                // 롤백 되었으므로 현재 스탬프(깎인 상태) 기준으로 다음 시간을 계산하거나, 
                // 로직에 따라 step을 결정합니다. 여기서는 현재 스탬프 + 1 기준 설정을 가져옵니다.
                const stepKey = `step${currentStamp + 1}`;
                
                if (settingsData && settingsData[stepKey]) {
                    intervalMin = Number(settingsData[stepKey].interval) || intervalMin;
                    graceMin = Number(settingsData[stepKey].grace) || graceMin;
                    console.log(`설정 적용: ${stepKey} (Interval: ${intervalMin}, Grace: ${graceMin})`);
                }
            }
        } catch (dbError) {
            console.error("설정 로드 중 오류(기본값 사용):", dbError.message);
        }

        // [4] 다음 복습 및 롤백 시간 계산
        const nowInSeconds = Math.floor(Date.now() / 1000);
        const nextScheduledTime = nowInSeconds + (intervalMin * 60);
        const nextRollbackTime = nextScheduledTime + (graceMin * 60);

        // [5] 새로운 Cloud Tasks 생성 (다음 알림 및 다음 롤백 예약)
        const parent = client.queuePath(PROJECT_ID, LOCATION, QUEUE_NAME);
        const revTaskName = client.taskPath(PROJECT_ID, LOCATION, QUEUE_NAME, `rev_${uid}_${docId}_${Date.now()}`);
        const rollTaskName = client.taskPath(PROJECT_ID, LOCATION, QUEUE_NAME, `roll_${uid}_${docId}_${Date.now()}`);

        const [revRes] = await client.createTask({
            parent,
            task: {
                name: revTaskName,
                httpRequest: {
                    httpMethod: "POST",
                    url: `https://${LOCATION}-${PROJECT_ID}.cloudfunctions.net/sendFcmNotification`,
                    headers: { "Content-Type": "application/json" },
                    body: Buffer.from(JSON.stringify({ uid, docId, title })).toString("base64"),
                },
                scheduleTime: { seconds: nextScheduledTime },
            }
        });

        const [rollRes] = await client.createTask({
            parent,
            task: {
                name: rollTaskName,
                httpRequest: {
                    httpMethod: "POST",
                    url: `https://${LOCATION}-${PROJECT_ID}.cloudfunctions.net/handleRollback`,
                    headers: { "Content-Type": "application/json" },
                    body: Buffer.from(JSON.stringify({ uid, docId })).toString("base64"),
                },
                scheduleTime: { seconds: nextRollbackTime },
            }
        });

        // [6] DB 업데이트 (롤백 상태 반영 및 새 Task ID 저장)
        await vocabRef.update({
            rollbackState: true,     // 롤백됨을 표시
            buttonOn: false,         // 학습하기 버튼은 숨김 (알림이 올 때까지)
            currentTaskId: revRes.name,
            currentRollbackTaskId: rollRes.name,
            nextReviewDate: admin.firestore.Timestamp.fromMillis(nextScheduledTime * 1000),
            rollbackTime: admin.firestore.Timestamp.fromMillis(nextRollbackTime * 1000)
        });

        console.log(`[롤백 완료] 문서: ${docId}, 다음 스케줄 예약됨.`);
        return res.status(200).send("OK");

    } catch (error) {
        console.error("Critical handleRollback Error:", error);
        return res.status(500).send(error.message);
    }
});

// ---------------------------------------------------------------------------
// 카카오 로그인
//
// Firebase Auth에는 카카오 제공자가 없다. 앱 전체가 Firebase UID 위에 서 있으므로
// (Firestore 경로, Functions의 request.auth, 보안 규칙) 카카오 로그인도 Firebase
// 유저를 만들어내야 한다. 그래서 커스텀 토큰을 발급한다.
//
//   앱: 카카오 SDK 로그인 -> 액세스 토큰
//   -> 이 함수: 토큰을 카카오 서버에 검증하고 Firebase 커스텀 토큰 발급
//   -> 앱: signInWithCustomToken()
//
// 클라이언트가 보낸 카카오 ID를 그대로 믿으면 안 된다. 반드시 액세스 토큰을
// 카카오 서버에 물어봐서 신원을 확인한다.
// ---------------------------------------------------------------------------

const KAKAO_TOKEN_INFO_URL = "https://kapi.kakao.com/v1/user/access_token_info";
const KAKAO_USER_ME_URL = "https://kapi.kakao.com/v2/user/me";

exports.kakaoCustomToken = onCall(
    {
        region: "asia-northeast3",
        timeoutSeconds: 30,
    },
    async (request) => {
        // 로그인 전이라 request.auth는 비어 있다. 인증은 카카오 토큰으로 한다.
        const accessToken = request.data?.accessToken;
        if (typeof accessToken !== "string" || accessToken.trim() === "") {
            throw new HttpsError("invalid-argument", "카카오 액세스 토큰이 필요합니다.");
        }

        if (!KAKAO_APP_ID) {
            console.error("KAKAO_APP_ID가 설정되지 않았습니다. index.js 상단 상수를 채워야 합니다.");
            throw new HttpsError("failed-precondition", "서버 설정이 완료되지 않았습니다.");
        }

        // [1] 토큰 검증: 누구의 토큰인지 + 어느 앱의 토큰인지
        let tokenInfo;
        try {
            const res = await fetch(KAKAO_TOKEN_INFO_URL, {
                headers: { Authorization: `Bearer ${accessToken}` },
            });
            if (!res.ok) {
                console.warn("카카오 토큰 검증 실패:", res.status, await res.text());
                throw new HttpsError("unauthenticated", "카카오 로그인 정보가 유효하지 않습니다.");
            }
            tokenInfo = await res.json();
        } catch (error) {
            if (error instanceof HttpsError) throw error;
            console.error("카카오 토큰 검증 중 오류:", error);
            throw new HttpsError("internal", "카카오 인증에 실패했습니다.");
        }

        // 다른 앱에서 발급된 토큰을 막는다. 이 대조가 이 함수의 핵심 방어선이다.
        if (Number(tokenInfo.app_id) !== Number(KAKAO_APP_ID)) {
            console.warn(`다른 앱의 토큰 거부: app_id=${tokenInfo.app_id}`);
            throw new HttpsError("permission-denied", "허용되지 않은 로그인 요청입니다.");
        }

        const kakaoId = tokenInfo.id;
        if (kakaoId === undefined || kakaoId === null) {
            throw new HttpsError("internal", "카카오 사용자 정보를 읽지 못했습니다.");
        }

        // [2] 프로필 조회. 실패해도 로그인은 진행한다(동의 안 한 항목일 수 있다).
        let nickname = null;
        let email = null;
        try {
            const res = await fetch(KAKAO_USER_ME_URL, {
                headers: { Authorization: `Bearer ${accessToken}` },
            });
            if (res.ok) {
                const profile = await res.json();
                nickname = profile.kakao_account?.profile?.nickname ?? null;
                email = profile.kakao_account?.email ?? null;
            }
        } catch (error) {
            console.warn("카카오 프로필 조회 실패(로그인은 계속):", error.message);
        }

        // [3] Firebase 유저 생성 또는 갱신
        const uid = `kakao:${kakaoId}`;
        let isNewUser = false;
        try {
            await admin.auth().getUser(uid);
        } catch (error) {
            if (error.code === "auth/user-not-found") {
                isNewUser = true;
            } else {
                console.error("Firebase 유저 조회 실패:", error);
                throw new HttpsError("internal", "로그인 처리에 실패했습니다.");
            }
        }

        const profileFields = {};
        if (nickname) profileFields.displayName = nickname;
        // 이메일은 선택 동의라 없을 수 있고, 다른 계정이 이미 쓰고 있으면 충돌한다.
        if (email) profileFields.email = email;

        try {
            if (isNewUser) {
                await admin.auth().createUser({ uid, ...profileFields });
            } else if (Object.keys(profileFields).length > 0) {
                await admin.auth().updateUser(uid, profileFields);
            }
        } catch (error) {
            // 이메일 중복(auth/email-already-exists)은 흔하다. 이메일 없이 다시 시도한다.
            if (error.code === "auth/email-already-exists" && profileFields.email) {
                console.warn(`이메일 중복으로 이메일 없이 처리: ${uid}`);
                delete profileFields.email;
                if (isNewUser) {
                    await admin.auth().createUser({ uid, ...profileFields });
                } else if (Object.keys(profileFields).length > 0) {
                    await admin.auth().updateUser(uid, profileFields);
                }
            } else {
                console.error("Firebase 유저 생성/갱신 실패:", error);
                throw new HttpsError("internal", "로그인 처리에 실패했습니다.");
            }
        }

        // [4] 커스텀 토큰 발급
        let customToken;
        try {
            customToken = await admin.auth().createCustomToken(uid, { provider: "kakao" });
        } catch (error) {
            console.error("커스텀 토큰 발급 실패:", error);
            throw new HttpsError("internal", "로그인 처리에 실패했습니다.");
        }

        // isNewUser는 앱에서 신규 포인트 지급 여부를 정하는 데 쓴다.
        // 커스텀 토큰 로그인은 getAdditionalUserInfo()가 신뢰할 수 없어 서버가 알려준다.
        return { customToken, isNewUser };
    }
);


// ---------------------------------------------------------------------------
// 회원 탈퇴
//
// 클라이언트에서 지우면 users/{uid} 문서만 지워지고 하위 컬렉션
// (vocabularies, words)은 그대로 남는다. Firestore는 하위 컬렉션을 따라
// 지워주지 않기 때문이다. 서버에서 recursiveDelete로 통째로 지운다.
//
// 카카오 계정이면 카카오 쪽 연결도 끊는다. 이걸 안 하면 사용자의 카카오
// 계정에 우리 앱이 계속 연결된 채로 남는다.
// ---------------------------------------------------------------------------

const KAKAO_UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";

/**
 * 카카오 사용자 연결을 해제한다. 실패해도 탈퇴 자체는 진행한다.
 * (이미 연결이 끊겼거나 카카오 장애일 수 있는데, 그것 때문에 탈퇴를 막으면 안 된다)
 *
 * @param {string} kakaoId 카카오 회원번호
 * @param {string} restApiKey 카카오 REST API 키
 * @return {Promise<void>} 완료 프라미스
 */
async function unlinkKakaoUser(kakaoId, restApiKey) {
    try {
        const res = await fetch(KAKAO_UNLINK_URL, {
            method: "POST",
            headers: {
                Authorization: `KakaoAK ${restApiKey}`,
                "Content-Type": "application/x-www-form-urlencoded",
            },
            body: `target_id_type=user_id&target_id=${encodeURIComponent(kakaoId)}`,
        });
        if (!res.ok) {
            console.warn("카카오 연결 해제 실패(탈퇴는 계속):", res.status, await res.text());
        }
    } catch (error) {
        console.warn("카카오 연결 해제 중 오류(탈퇴는 계속):", error.message);
    }
}

exports.deleteAccount = onCall(
    {
        region: "asia-northeast3",
        secrets: [KAKAO_REST_API_KEY],
        timeoutSeconds: 300,
    },
    async (request) => {
        if (!request.auth) {
            throw new HttpsError("unauthenticated", "로그인이 필요합니다.");
        }

        const uid = request.auth.uid;
        const isKakao = uid.startsWith("kakao:");

        // 카카오 계정은 Firebase 재인증을 할 수 없다(커스텀 토큰이라 reauthenticate 불가).
        // 대신 방금 받은 카카오 액세스 토큰을 요구해서, 계정 주인이 맞는지 확인한다.
        if (isKakao) {
            const accessToken = request.data?.kakaoAccessToken;
            if (typeof accessToken !== "string" || accessToken.trim() === "") {
                throw new HttpsError("failed-precondition", "카카오 재인증이 필요합니다.");
            }

            let tokenInfo;
            try {
                const res = await fetch(KAKAO_TOKEN_INFO_URL, {
                    headers: { Authorization: `Bearer ${accessToken}` },
                });
                if (!res.ok) {
                    throw new HttpsError("unauthenticated", "카카오 재인증에 실패했습니다.");
                }
                tokenInfo = await res.json();
            } catch (error) {
                if (error instanceof HttpsError) throw error;
                throw new HttpsError("internal", "카카오 재인증에 실패했습니다.");
            }

            // 남의 토큰으로 다른 계정을 지우지 못하도록 uid와 대조한다.
            if (`kakao:${tokenInfo.id}` !== uid) {
                console.warn(`탈퇴 요청 uid 불일치: ${uid} vs kakao:${tokenInfo.id}`);
                throw new HttpsError("permission-denied", "본인 계정만 탈퇴할 수 있습니다.");
            }

            await unlinkKakaoUser(tokenInfo.id, KAKAO_REST_API_KEY.value());
        }

        // [1] Firestore 데이터 삭제 (하위 컬렉션까지)
        try {
            const db = admin.firestore();
            await db.recursiveDelete(db.collection("users").doc(uid));
        } catch (error) {
            console.error("사용자 데이터 삭제 실패:", error);
            throw new HttpsError("internal", "데이터 삭제에 실패했습니다.");
        }

        // [2] Firebase 계정 삭제
        try {
            await admin.auth().deleteUser(uid);
        } catch (error) {
            if (error.code !== "auth/user-not-found") {
                console.error("계정 삭제 실패:", error);
                throw new HttpsError("internal", "계정 삭제에 실패했습니다.");
            }
        }

        console.log(`회원 탈퇴 완료: ${uid} (카카오=${isKakao})`);
        return { success: true };
    }
);
