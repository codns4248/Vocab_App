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
    const { docId, title, scheduledTime } = data;
    const uid = auth.uid;

    // ✅ 1. 기존 Task ID를 Firestore에서 가져와서 삭제
    try {
        const vocabDoc = await admin.firestore()
            .collection("users").doc(uid)
            .collection("vocabularies").doc(docId).get();
        
        const existingTaskId = vocabDoc.data()?.currentTaskId;
        if (existingTaskId) {
            await client.deleteTask({ name: existingTaskId });
            console.log("기존 태스크 삭제 성공:", existingTaskId);
        }
    } catch (e) {
        console.log("삭제할 기존 태스크 없음 (무시)");
    }

    // ✅ 2. 새 Task는 매번 고유 이름으로 생성
    const uniqueSuffix = Date.now();
    const taskName = client.taskPath(PROJECT_ID, LOCATION, QUEUE_NAME, 
        `task_${uid}_${docId}_${uniqueSuffix}`);
    
    const targetUrl = `https://${LOCATION}-${PROJECT_ID}.cloudfunctions.net/sendFcmNotification`;

    const task = {
        name: taskName,
        httpRequest: {
            httpMethod: "POST",
            url: targetUrl,
            headers: { "Content-Type": "application/json" },
            body: Buffer.from(JSON.stringify({ uid, docId, title })).toString("base64"),
        },
        scheduleTime: {
            seconds: scheduledTime, // ✅ Android에서 계산한 시간 그대로 사용
        },
    };

    const parent = client.queuePath(PROJECT_ID, LOCATION, QUEUE_NAME);
    const [response] = await client.createTask({ parent, task });

    // ✅ 3. 새 Task ID를 Firestore에 저장 (다음번 삭제에 사용)
    await admin.firestore()
        .collection("users").doc(uid)
        .collection("vocabularies").doc(docId)
        .update({ currentTaskId: response.name });

    console.log(`알림 예약 성공: ${scheduledTime}초, Task: ${response.name}`);
    return { success: true };
});

// [2] 알림 취소 함수 (onCall)
exports.cancelReviewNotification = onCall({ region: "asia-northeast3" }, async (request) => {
    const { docId } = request.data;
    const uid = request.auth.uid;

    try {
        // ★ 추가: Firestore에서 저장된 Task ID를 가져옴
        const vocabDoc = await admin.firestore().collection("users").doc(uid)
            .collection("vocabularies").doc(docId).get();
        const taskId = vocabDoc.data()?.currentTaskId;

        if (taskId) {
            await client.deleteTask({ name: taskId });
            console.log(`알림 취소 완료: ${taskId}`);
        }
        return { success: true };
    } catch (error) {
        return { success: false, message: "취소할 작업이 없거나 이미 실행됨" };
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