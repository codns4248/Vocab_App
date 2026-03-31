package com.example.vocaapp.Test;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.example.vocaapp.VocabularyBookList.VocabularyBookFirestore;
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

    public void studyVocabulary(Context context, String userId, String vocabId, int nextStamp) {
        DocumentReference vocabRef = db.collection("users").document(userId)
                .collection("vocabularies").document(vocabId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastStudiedAt", new Timestamp(new Date()));
        updates.put("stampCount", nextStamp);

        TestFirestore.getStampCount(userId, vocabId, new TestFirestore.StampCountCallback() {
            @Override
            public void onResult(int stampCount) {
                // 6개의 스탬프를 달성하면 이후의 처리
                if (nextStamp >= 6) {
                    updates.put("isStudying", false);
                    updates.put("nextReviewDate", null);

                    vocabRef.update(updates).addOnSuccessListener(aVoid -> {
                        if (context != null) Toast.makeText(context, "🎉 모든 복습 완료! 수고하셨습니다.", Toast.LENGTH_SHORT).show();
                    });
                }

                // 학습 진행 중 처리
                else {
                    // 1. DB에서 설정값(interval, grace) 가져오기
                    VocabularyBookFirestore.bringTime(stampCount, configData -> {
                        if (configData == null) {
                            Log.e("StudyManager", "설정 데이터를 가져오지 못했습니다.");
                            return;
                        }

                        // DB에서 가져온 분(minute) 단위 값 (없을 경우를 대비해 기본값 설정)
                        int intervalMinutes = configData.get("interval") != null ? ((Long) configData.get("interval")).intValue() : 10;
                        int graceMinutes = configData.get("grace") != null ? ((Long) configData.get("grace")).intValue() : 5;

                        // 2. 알림 시간 계산 (현재 시간 + interval)
                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.MINUTE, intervalMinutes);
                        Date nextReviewDate = cal.getTime();

                        // 3. 롤백 시간 계산 (알림 시간 + grace)
                        Calendar rollCal = Calendar.getInstance();
                        rollCal.setTime(nextReviewDate);
                        rollCal.add(Calendar.MINUTE, graceMinutes);
                        Date rollbackDate = rollCal.getTime();

                        // 4. DB 업데이트 맵 구성
                        updates.put("isStudying", true);
                        updates.put("nextReviewDate", new Timestamp(nextReviewDate));
                        updates.put("rollbackTime", new Timestamp(rollbackDate));
                        updates.put("rollbackState", false); // 새로 시작하거나 다음 단계로 갈 때 초기화

                        vocabRef.update(updates).addOnSuccessListener(aVoid -> {
                            long scheduledTimeSeconds = nextReviewDate.getTime() / 1000;
                            long rollbackTimeSeconds = rollbackDate.getTime() / 1000;

                            // 5. 서버에 알림 및 롤백 예약 (인자 4개 전달)
                            scheduleNotification(vocabId, "단어장 복습 시간입니다!", scheduledTimeSeconds, rollbackTimeSeconds);

                            String timeInfo = intervalMinutes + "분";
                            if (context != null) {
                                Toast.makeText(context, nextStamp + "단계 완료! (" + timeInfo + " 후 알림)", Toast.LENGTH_SHORT).show();
                            }
                        }).addOnFailureListener(e -> Log.e("StudyManager", "DB 업데이트 실패", e));
                    });
                }
            }

            @Override
            public void onError(Exception e) {

            }
        });


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
    public void scheduleNotification(String vocabId, String title, long scheduledTimeSeconds, long rollbackTimeSeconds) {
        Map<String, Object> funcData = new HashMap<>();
        funcData.put("docId", vocabId);
        funcData.put("scheduledTime", String.valueOf(scheduledTimeSeconds));
        funcData.put("title", title);
        funcData.put("rollbackTime", rollbackTimeSeconds);

        mFunctions.getHttpsCallable("scheduleReviewNotification")
                .call(funcData)
                .addOnSuccessListener(result -> Log.d("CloudTask", "알림 예약 성공"))
                .addOnFailureListener(e -> Log.e("CloudTask", "알림 예약 실패", e));
    }
}
