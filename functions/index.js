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

function getTaskPath(uid, vocabId) {
    return client.taskPath(PROJECT_ID, LOCATION, QUEUE_NAME, `task_${uid}_${vocabId}`);
}

// [1] 알림 예약 함수 (onCall)
exports.scheduleReviewNotification = onCall({ region: "asia-northeast3" }, async (request) => {
    const { data, auth } = request; // v2는 request 객체에서 data와 auth를 추출합니다.
    
    if (!auth) {
        throw new Error('로그인이 필요합니다.');
    }

    const { vocabId, title, scheduledTime } = data; 
    const uid = auth.uid;
    const taskName = getTaskPath(uid, vocabId);
    const targetUrl = `https://${LOCATION}-${PROJECT_ID}.cloudfunctions.net/sendFcmNotification`;

    try {
        await client.deleteTask({ name: taskName });
    } catch (e) { /* 기존 태스크 없음 무시 */ }

    const task = {
        name: taskName,
        httpRequest: {
            httpMethod: "POST",
            url: targetUrl,
            headers: { "Content-Type": "application/json" },
            body: Buffer.from(JSON.stringify({
                uid: uid,
                vocabId: vocabId,
                title: title
            })).toString("base64"),
        },
        scheduleTime: {
            seconds: scheduledTime,
        },
    };

    try {
        const parent = client.queuePath(PROJECT_ID, LOCATION, QUEUE_NAME);
        const [response] = await client.createTask({ parent, task });
        console.log(`알림 예약 성공: ${response.name}`);
        return { success: true };
    } catch (error) {
        console.error("Task 생성 실패:", error);
        throw new Error(error.message);
    }
});

// [2] 알림 취소 함수 (onCall)
exports.cancelReviewNotification = onCall({ region: "asia-northeast3" }, async (request) => {
    const { data, auth } = request;
    if (!auth) throw new Error('로그인이 필요합니다.');

    const { vocabId } = data;
    const taskName = getTaskPath(auth.uid, vocabId);

    try {
        await client.deleteTask({ name: taskName });
        return { success: true };
    } catch (error) {
        return { success: false, message: "취소할 작업이 없습니다." };
    }
});

// [3] 실제 알림 발송 함수 (onRequest)
exports.sendFcmNotification = onRequest({ region: "asia-northeast3" }, async (req, res) => {
    // Cloud Tasks에서 오는 요청 바디 파싱
    const data = (typeof req.body === 'string') ? JSON.parse(req.body) : req.body;
    const { uid, vocabId, title } = data;

    try {
        const userDoc = await admin.firestore().collection("users").doc(uid).get();
        const fcmToken = userDoc.data() ? userDoc.data().fcmToken : null;

        if (!fcmToken) {
            console.log("FCM 토큰을 찾을 수 없습니다.");
            return res.status(200).send("No token");
        }

        const message = {
            notification: {
                title: "복습 시간이 되었습니다! ✍️",
                body: `'${title}' 단어장을 복습하고 스탬프를 찍으세요!`
            },
            data: {
                vocabId: vocabId,
                type: "REVIEW_NOTIFICATION"
            },
            token: fcmToken
        };

        await admin.messaging().send(message);
        console.log(`FCM 발송 성공: ${uid}`);
        res.status(200).send("OK");
    } catch (error) {
        console.error("FCM 발송 실패:", error);
        res.status(500).send(error.message);
    }
});