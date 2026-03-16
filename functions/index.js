const {onSchedule} = require("firebase-functions/v2/scheduler");
const {setGlobalOptions} = require("firebase-functions/v2");
const admin = require("firebase-admin");

admin.initializeApp();

// 배포 지역을 서울(asia-northeast3)로 설정 (선택 사항이나 권장)
setGlobalOptions({region: "asia-northeast3"});

//유예 시간 계산
function getGracePeriodMillis(stampCount) {
  const MINUTE = 60 * 1000;

  switch (stampCount) {
    case 0:
    case 1: return 50 * MINUTE;
    case 2: return 120 * MINUTE;
    case 3: return 360 * MINUTE;
    case 4: return 720 * MINUTE;
    case 5: return 1440 * MINUTE;
    case 6: return 2880 * MINUTE;
    case 7: return 4320 * MINUTE;
    default: return 50 * MINUTE;
  }


}
//학습하세요~알림
exports.sendReviewNotification = onSchedule("every 5 minutes", async (event) => {
  const now = admin.firestore.Timestamp.now();

  const bufferTime = 60 * 1000;
  const nowPlusBuffer = admin.firestore.Timestamp.fromMillis(Date.now() + bufferTime);

  // 1. 복습 대상자 조회
  const snapshot = await admin.firestore().collectionGroup("vocabularies")
    .where("nextReviewDate", "<=", nowPlusBuffer)
    .where("isStudying", "==", true)
    .where("isReviewReady", "==", false)
    .get();

  if (snapshot.empty) {
    console.log("복습 대상이 없습니다.");
    return;
  }

  for (const doc of snapshot.docs) {
    const vocabData = doc.data();
    // 부모 문서(users/{userId})로 올라가서 fcmToken 확인
    const userDoc = await doc.ref.parent.parent.get();
    const userData = userDoc.data();
    const fcmToken = userData ? userData.fcmToken : null;

    if (fcmToken) {
      const message = {
        notification: {
          title: "복습할 시간이에요! 📖",
          body: `[${vocabData.title || "단어장"}]의 다음 단계 학습이 가능합니다.`,
        },
        token: fcmToken,
      };


      try {
        await admin.messaging().send(message);
        //nextReviewDate: admin.firestore.FieldValue.delete()에서 수정 시간 삭제 안함
        const currentStamp = vocabData.stampCount || 0;
        const gracePeriod = getGracePeriodMillis(currentStamp);
        const penaltyTime = admin.firestore.Timestamp.fromMillis(Date.now() + gracePeriod);

        await doc.ref.update({
          isReviewReady: true,
          penaltyDate: penaltyTime,
          isWarningSent: false
        });
        console.log(`알림 발송 성공: ${doc.id}`);
      } catch (error) {
        console.error("FCM 발송 에러:", error);
      }
    } else {
      console.log(`토큰이 없어서 알림 못보냄: ${doc.id}`);
    }
  }
});

exports.sendwarningNotification = onSchedule("every 5 minutes", async (event) => {

  const WARNING_MARGIN = 30 * 60 *  1000; // 30분
  const warningTimeThreshold = admin.firestore.Timestamp.fromMillis(Date.now() + WARNING_MARGIN);

  const snapshot = await admin.firestore().collectionGroup("vocabularies")
    .where("isStudying", "==", true)
    .where("isReviewReady", "==", true)
    .where("isWarningSent", "==", false)
    .where("penaltyDate", "<=", warningTimeThreshold)
    .get();


  if (snapshot.empty) return;

  for (const doc of snapshot.docs) {
    const vocabData = doc.data();
    const userDoc = await doc.ref.parent.parent.get();
    const userData = userDoc.data();
    const fcmToken = userData ? userData.fcmToken : null;

    if (fcmToken) {
      const message = {
      notification: {
        title: "단어장 스탬프 깎임 주의",
        body: `[${vocabData.title || "단어장"}] 복습 유예 시간이 얼마 안남았어요! 얼른 복습하세요!`,
      },
      token: fcmToken,
    };

    try {
      await admin.messaging().send(message);

      await doc.ref.update({
        isWarningSent: true
      });
      console.log(`경고 알림 발송 성공: ${doc.id}`);
    } catch (error) {
        console.error("경고 알림 에러:", error);
      }
    }
  }
});

//롤백 로직
exports.applySpyPenalty = onSchedule("every 5 minutes", async (event) => {
  const now = admin.firestore.Timestamp.now();

  const snapshot = await admin.firestore().collectionGroup("vocabularies")
    .where("isStudying", "==", true)
    .where("isReviewReady", "==", true)
    .where("penaltyDate", "<=",now)
    .get();

  if (snapshot.empty) {
    return;
  }

  for (const doc of snapshot.docs) {
    const vocabData = doc.data();
    const currentStamp = vocabData.stampCount || 0;

    const newStamp = Math.max(0, currentStamp -1);

    const userDoc = await doc.ref.parent.parent.get();
    const userData = userDoc.data();
    const fcmToken = userData ? userData.fcmToken : null;

    try {
      await doc.ref.update({
        stampCount: newStamp,
        isReviewReady: false,
        isWarningSent: false,
        penaltyDate: admin.firestore.FieldValue.delete(),
        showRollbackPopup: true,
        rolledBackFrom: currentStamp
      });

      if (fcmToken){
        const message = {
          notification: {
            title: "단어장 스탬프 롤백",
            body: `[${vocabData.title || "단어장"}] 유예 시간이 지나 스탬프가 깎였습니다`
          },
            token: fcmToken,
        };
        await admin.messaging().send(message);
      }
        console.log(`롤백완료: ${doc.id} (스탬프 ${currentStamp} -> ${newStamp})`);
    } catch (error) {
        console.error("롤백 에러:", error);
    }
  }
});