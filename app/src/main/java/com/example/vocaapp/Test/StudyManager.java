package com.example.vocaapp.Test;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class StudyManager {

    private static StudyManager instance;
    private final FirebaseFirestore db;
    private final FirebaseFunctions mFunctions; // final 필드는 생성자에서 반드시 초기화되어야 함

    // 1. 생성자를 private으로 변경하고 모든 final 필드 초기화
    private StudyManager() {
        db = FirebaseFirestore.getInstance();
        // 리전 설정을 포함하여 초기화 (오류 해결 핵심)
        mFunctions = FirebaseFunctions.getInstance("asia-northeast3");
    }

    // 싱글톤 인스턴스 가져오기
    public static synchronized StudyManager getInstance() {
        if (instance == null) {
            instance = new StudyManager();
        }
        return instance;
    }

    public void updateFCMToken(String userId) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("FCM", "토큰 가져오기 실패", task.getException());
                        return;
                    }
                    String token = task.getResult();
                    Map<String, Object> tokenData = new HashMap<>();
                    tokenData.put("fcmToken", token);

                    db.collection("users").document(userId)
                            .set(tokenData, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(aVoid -> Log.d("FCM", "토큰 저장 성공!"))
                            .addOnFailureListener(e -> Log.e("FCM", "토큰 저장 실패: " + e.getMessage()));
                });
    }

    // StudyManager.java 내 수정 부분

    public void studyVocabulary(Context context, String userId, String vocabId, int nextStamp) {
        DocumentReference vocabRef = db.collection("users").document(userId)
                .collection("vocabularies").document(vocabId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastStudiedAt", new Timestamp(new Date()));
        updates.put("stampCount", nextStamp);

        // [핵심] 스탬프 7단계 도달 여부에 따른 분기 처리
        if (nextStamp >= 7) {
            // 모든 단계 완료 -> 학습 모드 종료 (버튼 사라짐)
            updates.put("isStudying", false);
            updates.put("nextReviewDate", null);

            vocabRef.update(updates).addOnSuccessListener(aVoid -> {
                if (context != null) Toast.makeText(context, "🎉 모든 복습 완료! 수고하셨습니다.", Toast.LENGTH_SHORT).show();
            });
        } else {
            // 다음 단계가 있음 -> 다음 복습 예약
            int waitMinutes = getWaitMinutes(nextStamp);
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, waitMinutes);
            Date nextReviewDate = cal.getTime();

            updates.put("buttonReady", true);
            updates.put("isStudying", true);
            updates.put("nextReviewDate", new Timestamp(nextReviewDate));

            vocabRef.update(updates).addOnSuccessListener(aVoid -> {
                // 알림 예약 로직 실행 (아래에 새로 만든 메소드 호출)
                long scheduledTimeSeconds = nextReviewDate.getTime() / 1000;
                callScheduleNotificationFunction(vocabId, scheduledTimeSeconds);

                String timeInfo = formatWaitTime(waitMinutes);
                if (context != null) Toast.makeText(context, nextStamp + "단계 완료! (" + timeInfo + " 후 알림)", Toast.LENGTH_SHORT).show();
            }).addOnFailureListener(e -> Log.e("StudyManager", "DB 업데이트 실패", e));
        }
    }

    // [추가] 에러가 났던 알림 예약 로직을 메소드로 분리
    private void callScheduleNotificationFunction(String vocabId, long scheduledTimeSeconds) {
        Map<String, Object> funcData = new HashMap<>();
        funcData.put("vocabId", vocabId);
        funcData.put("scheduledTime", scheduledTimeSeconds);
        funcData.put("title", "단어장 복습 시간입니다!");

        mFunctions.getHttpsCallable("scheduleReviewNotification")
                .call(funcData)
                .addOnSuccessListener(result -> Log.d("CloudTask", "알림 예약 성공"))
                .addOnFailureListener(e -> Log.e("CloudTask", "알림 예약 실패", e));
    }

    public void stopStudying(String userId, String vocabId) {
        DocumentReference vocabRef = db.collection("users").document(userId)
                .collection("vocabularies").document(vocabId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("isStudying", false);
        updates.put("stampCount", 0);
        updates.put("nextReviewDate", FieldValue.delete());

        vocabRef.update(updates).addOnSuccessListener(aVoid -> {
            Map<String, Object> funcData = new HashMap<>();
            funcData.put("vocabId", vocabId);

            // 직접 getInstance를 호출하지 않고 클래스 멤버 mFunctions 사용 가능
            mFunctions.getHttpsCallable("cancelReviewNotification")
                    .call(funcData)
                    .addOnSuccessListener(result -> Log.d("StudyManager", "알림 취소 성공"))
                    .addOnFailureListener(e -> Log.e("StudyManager", "알림 취소 실패", e));

            Log.d("StudyManager", "학습 초기화 성공");
        });
    }

    public int getWaitMinutes(int stage) {
        // 어댑터에서 즉시 다시 버튼이 뜨는 것을 방지하기 위해 2분 이상 권장
        return 2;
    }

    private String formatWaitTime(int minutes) {
        if (minutes < 60) return minutes + "분";
        if (minutes < 1440) return (minutes / 60) + "시간";
        return (minutes / 1440) + "일";
    }

    // StudyManager.java 내의 이 부분을 수정
    public void scheduleNotification(String userId, String vocabId, String title, long scheduledTimeSeconds) {
        Map<String, Object> funcData = new HashMap<>();
        funcData.put("vocabId", vocabId);
        funcData.put("scheduledTime", scheduledTimeSeconds);
        funcData.put("title", title); // 인자로 받은 title 사용

        mFunctions.getHttpsCallable("scheduleReviewNotification")
                .call(funcData)
                .addOnSuccessListener(result -> Log.d("CloudTask", "알림 예약 성공"))
                .addOnFailureListener(e -> Log.e("CloudTask", "알림 예약 실패", e));
    }
}
