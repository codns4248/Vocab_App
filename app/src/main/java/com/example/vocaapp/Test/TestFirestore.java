package com.example.vocaapp.Test;

import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class TestFirestore {
    public static void handleTestPass(String uid, String vocabId, TestFirestore.TestResultCallback callback) {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();

        Map<String, Object> updates = new HashMap<>();
        updates.put("stampCount", com.google.firebase.firestore.FieldValue.increment(1));
        updates.put("lastStudiedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        updates.put("buttonOn", false);

        db.collection("users").document(uid)
                .collection("vocabularies").document(vocabId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }
    public interface TestResultCallback {
        void onSuccess();
        void onFailure(Exception e);
    }
    public static void getStampCount(String userId, String vocabId, StampCountCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. 문서 경로 참조
        DocumentReference vocabRef = db.collection("users").document(userId)
                .collection("vocabularies").document(vocabId);

        // 2. 데이터 단건 조회
        vocabRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    // 3. 필드 가져오기 및 null 체크
                    Long stampCountLong = document.getLong("stampCount");
                    int currentStamp = (stampCountLong != null) ? stampCountLong.intValue() : 0;

                    Log.d("Firestore", "성공적으로 가져온 스탬프: " + currentStamp);

                    // 콜백을 통해 결과 전달
                    callback.onResult(currentStamp);
                } else {
                    Log.d("Firestore", "문서가 존재하지 않음");
                    callback.onResult(0); // 문서가 없으면 기본값 0 전달
                }
            } else {
                Log.e("Firestore", "조회 실패", task.getException());
                callback.onError(task.getException());
            }
        });
    }
    public interface StampCountCallback {
        void onResult(int stampCount);
        void onError(Exception e);
    }
}
