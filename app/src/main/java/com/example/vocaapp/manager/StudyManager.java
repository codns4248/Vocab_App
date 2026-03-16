package com.example.vocaapp.manager;

import com.google.firebase.messaging.FirebaseMessaging;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class StudyManager {

    private static StudyManager instance;
    private final FirebaseFirestore db;

    // 생성자를 private으로 막아서 외부에서 new StudyManager()를 못 하게 합니다.
    private StudyManager() {
        db = FirebaseFirestore.getInstance();
    }

    // 어디서든 StudyManager.getInstance()로 접근 가능하게 합니다.
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

                    // 새로운 토큰 가져오기
                    String token = task.getResult();

                    Map<String, Object> tokenData = new HashMap<>();
                    tokenData.put("fcmToken", token);

                    // Firestore의 사용자 문서에 토큰 저장
                    db.collection("users").document(userId)
                            .set(tokenData, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(aVoid -> Log.d("FCM", "토큰 저장 성공!"))
                            .addOnFailureListener(e -> Log.e("FCM", "토큰 저장 실패: " + e.getMessage()));
                });
    }

    // StudyManager.java 내의 메서드 수정
    public void studyVocabulary(Context context, String userId, String vocabId) {
        // 단어(words)가 아니라 단어장(vocabularies) 문서 자체를 가리킵니다.
        DocumentReference vocabRef = db.collection("users").document(userId)
                .collection("vocabularies").document(vocabId);

        vocabRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                // A. 현재 단계 확인
                Long currentStampLong = documentSnapshot.getLong("stampCount");
                int currentStamp = (currentStampLong != null) ? currentStampLong.intValue() : 0;

                int waitMinutes = getWaitMinutes(currentStamp);

                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.MINUTE, waitMinutes);
                Date nextReviewDate = cal.getTime();

                // B. 업데이트 데이터 준비 (단어장에 직접 저장)
                Map<String, Object> updates = new HashMap<>();
                updates.put("nextReviewDate", new Timestamp(nextReviewDate));
                updates.put("isStudying", true);
                updates.put("lastStudiedAt", new Timestamp(new Date()));

                // C. DB 업데이트 실행
                vocabRef.update(updates)
                        .addOnSuccessListener(aVoid -> {
                            String timeInfo = formatWaitTime(waitMinutes);
                            String msg = currentStamp + "단계 복습 완료! (" + timeInfo + " 후 알림)";
                            Log.d("StudyManager", msg);
                            if (context != null) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                        });
            }
        });
    }

    // 단계별 대기 시간 로직 (분 단위)
    public int getWaitMinutes(int stage) {
        switch (stage) {
            case 0:
            case 1: return 10;       // 10분 뒤
            case 2: return 60;      // 1시간 뒤
            case 3: return 1440;    // 1일 뒤 (60 * 24)
            case 4: return 4320;    // 3일 뒤 (1440 * 3)
            case 5: return 10080;   // 7일 뒤 (1440 * 7)
            case 6: return 20160;   // 14일 뒤 (1440 * 14)
            default: return 43200;  // 30일 뒤 (1440 * 30)
        }
    }

    // 시간을 보기 좋게 변환 (로그/토스트용)
    private String formatWaitTime(int minutes) {
        if (minutes < 60) return minutes + "분";
        if (minutes < 1440) return (minutes / 60) + "시간";
        return (minutes / 1440) + "일";
    }

    public void stopStudying(String userId, String vocabId) {
        DocumentReference vocabRef = db.collection("users").document(userId)
                .collection("vocabularies").document(vocabId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("isStudying", false);
        updates.put("stampCount", 0);
        updates.put("nextReviewDate", FieldValue.delete()); // 날짜 필드 삭제

        vocabRef.update(updates)
                .addOnSuccessListener(aVoid -> Log.d("StudyManager", "학습 초기화 성공"))
                .addOnFailureListener(e -> Log.e("StudyManager", "학습 초기화 실패", e));
    }
}
