const { onCall, onRequest } = require("firebase-functions/v2/https"); // v2 필수
const admin = require("firebase-admin");
const { CloudTasksClient } = require("@google-cloud/tasks");

if (!admin.apps.length) {
    admin.initializeApp();
}

const client = new CloudTasksClient();

const PROJECT_ID = "vocaapp-bf580";
const LOCATION = "asia-northeast3"; 
const QUEUE_NAME = "voca-review-queue";

function getTaskPath(uid, docId) {
    const uniqueSuffix = Date.now();
    return client.taskPath(PROJECT_ID, LOCATION, QUEUE_NAME, `task_${uid}_${docId}_${uniqueSuffix}`);
}

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


    console.log("함수 시작"); // ← 추가
    console.log("req.body 타입:", typeof req.body); // ← 추가
    console.log("req.body 내용:", JSON.stringify(req.body)); // ← 추가

    try {
        // [1] 안전하게 데이터 파싱
        let payload = req.body;
        if (typeof payload === 'string') payload = JSON.parse(payload);
        if (Buffer.isBuffer(payload)) payload = JSON.parse(payload.toString());

        const { uid, docId, title } = payload;
        
        // 디버깅 로그: 여기서 값이 잘 찍히는지 꼭 확인하세요!
        console.log(`수신 데이터 확인 -> UID: ${uid}, DocID: ${docId}, Title: ${title}`);

        if (!uid || !docId) {
            console.error("데이터 누락: UID나 DocID가 없습니다.");
            return res.status(400).send("Missing Data");
        }

        // [2] Firestore 업데이트 (기본 로직)
        const vocabRef = admin.firestore()
            .collection("users").doc(uid)
            .collection("vocabularies").doc(docId);

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
exports.handleRollback = onRequest({ region: "asia-northeast3" }, async (req, res) => {
    try {
        let payload = req.body;
        if (typeof payload === 'string') payload = JSON.parse(payload);
        if (Buffer.isBuffer(payload)) payload = JSON.parse(payload.toString());
        
        const { uid, docId } = payload;
        if (!uid || !docId) return res.status(400).send("Missing Data");

        const vocabRef = admin.firestore().collection("users").doc(uid).collection("vocabularies").doc(docId);
        const vocabSnap = await vocabRef.get();
        if (!vocabSnap.exists) return res.status(404).send("Doc Not Found");
        
        const vocabData = vocabSnap.data();
        const currentStamp = vocabData.stampCount || 0;
        const title = vocabData.title || "단어장";

        // 기본값 설정 (DB 로드 실패 대비)
        let intervalMin = 10;
        let graceMin = 5;

        // --- 설정 로드 로직 (강화됨) ---
        try {
            const settingsDoc = await admin.firestore().collection("reviewAndRollbackTimeSetting").doc("reviewAndRollbackTimeSetting").get();
            
            if (settingsDoc.exists) {
                const settingsData = settingsDoc.data();
                const stepKey = `step${currentStamp + 1}`;
                
                console.log(`설정 로드 성공: ${stepKey} 찾는 중...`);

                // settingsData가 null이 아니고 해당 스텝이 있는지 체크
                if (settingsData && settingsData[stepKey]) {
                    intervalMin = Number(settingsData[stepKey].interval) || intervalMin;
                    graceMin = Number(settingsData[stepKey].grace) || graceMin;
                    console.log(`시간 설정 적용됨: interval=${intervalMin}, grace=${graceMin}`);
                } else {
                    console.warn(`${stepKey} 필드가 없어서 기본값을 사용합니다.`);
                }
            } else {
                console.warn("reviewAndRollbackTimeSetting 문서가 Firestore에 존재하지 않습니다.");
            }
        } catch (dbError) {
            console.error("설정 DB 접근 중 오류 발생 (기본값으로 진행):", dbError.message);
        }

        // 시간 계산
        const nowInSeconds = Math.floor(Date.now() / 1000);
        const nextScheduledTime = nowInSeconds + (intervalMin * 60);
        const nextRollbackTime = nextScheduledTime + (graceMin * 60);

        // Task 생성 (이전과 동일)
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

        // 마지막 DB 업데이트
        await vocabRef.update({
            rollbackState: true,
            buttonOn: false,
            currentTaskId: revRes.name,
            currentRollbackTaskId: rollRes.name,
            nextReviewDate: admin.firestore.Timestamp.fromMillis(nextScheduledTime * 1000),
            rollbackTime: admin.firestore.Timestamp.fromMillis(nextRollbackTime * 1000)
        });

        console.log(`롤백 완료: ${docId}, 다음 스케줄 예약됨.`);
        return res.status(200).send("OK");

    } catch (error) {
        console.error("Critical handleRollback Error:", error);
        return res.status(500).send(error.message);
    }
});