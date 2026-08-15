const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https"); // v2 필수
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const { CloudTasksClient } = require("@google-cloud/tasks");
const Anthropic = require("@anthropic-ai/sdk");

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

const EXTRACT_PROMPT =
    "이미지에서 영어 단어들을 추출하고, 한국어 뜻과 한국어 발음을 적어줘. 만약 전문 용어라면 가장 대중적인 뜻을 선택해줘.";

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

        // 앱은 JPEG로 압축한 base64 문자열을 보냅니다.
        const content = images.map((data) => ({
            type: "image",
            source: { type: "base64", media_type: "image/jpeg", data },
        }));
        content.push({ type: "text", text: EXTRACT_PROMPT });

        const anthropic = new Anthropic({ apiKey: ANTHROPIC_API_KEY.value() });

        let response;
        try {
            response = await anthropic.messages.create({
                model: AI_MODEL,
                max_tokens: 16000,
                output_config: {
                    format: { type: "json_schema", schema: WORD_LIST_SCHEMA },
                },
                messages: [{ role: "user", content }],
            });
        } catch (error) {
            console.error("Claude 호출 실패:", error);
            throw new HttpsError("internal", "단어 분석에 실패했습니다.");
        }

        // 안전 거부 / 토큰 초과는 스키마를 만족하지 않을 수 있으므로 먼저 확인
        if (response.stop_reason === "refusal") {
            throw new HttpsError("invalid-argument", "이 이미지는 분석할 수 없습니다.");
        }
        if (response.stop_reason === "max_tokens") {
            throw new HttpsError("resource-exhausted", "단어가 너무 많습니다. 사진 수를 줄여주세요.");
        }

        const textBlock = response.content.find((block) => block.type === "text");
        if (!textBlock) {
            console.error("텍스트 블록 없음:", JSON.stringify(response.content));
            throw new HttpsError("internal", "분석 결과를 읽지 못했습니다.");
        }

        let parsed;
        try {
            parsed = JSON.parse(textBlock.text);
        } catch (error) {
            console.error("파싱 실패:", textBlock.text);
            throw new HttpsError("internal", "분석 결과를 읽지 못했습니다.");
        }

        return { words: parsed.words ?? [] };
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