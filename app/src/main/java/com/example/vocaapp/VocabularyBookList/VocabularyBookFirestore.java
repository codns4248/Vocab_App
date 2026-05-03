package com.example.vocaapp.VocabularyBookList;

import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VocabularyBookFirestore {

    // 단어장 db에 추가하는 로직
    public static void addVocabularyBook(Map<String, Object> inputVocabularyBookName, String uid, VocabularyBookCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. 문서를 생성하기 전에 참조(Reference)를 먼저 만듭니다.
        // .document()에 인자를 넣지 않으면 Firestore가 고유 ID를 미리 생성해줍니다.
        DocumentReference newDocRef = db.collection("users")
                .document(uid)
                .collection("vocabularies")
                .document();

        // 2. 생성된 고유 ID를 데이터 Map에 삽입합니다.
        String generatedId = newDocRef.getId();
        inputVocabularyBookName.put("docId", generatedId);

        // 3. .add() 대신 .set()을 사용하여 데이터를 저장합니다.
        newDocRef.set(inputVocabularyBookName)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    // 성공, 실패 인터페이스
    public interface VocabularyBookCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    // 단어장 불러오는 db 로직
    public static void listenVocabularies(String uid, VocabularyListCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .document(uid)
                .collection("vocabularies")
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null) {
                        if (callback != null) callback.onFailure(e);
                        return;
                    }
                    if (querySnapshot != null) {
                        //  String 대신 Object를 사용하여 모든 타입의 데이터를 담습니다.
                        List<Map<String, Object>> dataList = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            //  doc.getData()를 쓰면 title, stampCount 등 모든 필드를 한 번에 가져옵니다.
                            Map<String, Object> vocabData = doc.getData();
                            // ID 값도 나중에 필요하므로 함께 넣어줍니다.
                            vocabData.put("id", doc.getId());
                            dataList.add(vocabData);
                        }
                        if (callback != null) callback.onUpdate(dataList);
                    }
                });
    }

    //  인터페이스 타입 변경
    public interface VocabularyListCallback {
        // List<Map<String, Object>>로 변경하여 숫자 데이터를 허용합니다.
        void onUpdate(List<Map<String, Object>> dataList);
        void onFailure(Exception e);
    }

    // 단어장 정보를 수정(업데이트)하는 로직
    public static void updateVocabularyBook(String uid, String vocabId, Map<String, Object> updates, VocabularyBookCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .document(uid)
                .collection("vocabularies")
                .document(vocabId) // 특정 단어장 ID로 접근
                .update(updates)   // 보낸 데이터만 수정
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }


    // 진행상황 초기화 메서드
    public static void resetStudyStatus(String uid, String vocabId, VocabularyBookCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> updates = new HashMap<>();

        // 기본 상태 초기화
        updates.put("isStudying", false);
        updates.put("stampCount", 0);
        updates.put("nextReviewDate", FieldValue.delete());
        updates.put("lastStudiedAt", FieldValue.delete());

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

    public static void deleteVocabularyBook(String docId, String uid) {
        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vocabularies").document(docId)
                .delete()
                .addOnSuccessListener(aVoid -> {

                })
                .addOnFailureListener(e -> {

                });
    }

    public void alreadyVocabularyBook(String uid, String bookName, alreadyVocabularyBookInterface alreadyVocabularyBookInterface) {
        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vocabularies") // 단어장 목록이 있는 컬렉션
                .whereEqualTo("title", bookName) // 단어장 이름 필드와 비교
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        // 문서가 존재하면(isEmpty가 false면) 이미 있는 단어장 이름
                        boolean exists = !task.getResult().isEmpty();
                        alreadyVocabularyBookInterface.alreadyVocabularyBook(exists);
                    } else {
                        // 에러 발생 시 안전하게 false 처리
                        alreadyVocabularyBookInterface.alreadyVocabularyBook(false);
                    }
                });
    }

    // 인터페이스 선언 (클래스 내부에 맞춰서 위치시키세요)
    public interface alreadyVocabularyBookInterface {
        void alreadyVocabularyBook(boolean isAlready);
    }

    public static void bringTime(int stampCount, BringTimeInterface bringTimeInterface) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference docRef = db.collection("reviewAndRollbackTimeSetting").document("reviewAndRollbackTimeSetting");

        docRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    // 1. stampCount가 6 이상일 경우 마지막 단계(step6)를 사용하도록 제한 (선택 사항)
                    int stepLevel = Math.min(stampCount + 1, 6);
                    String stepKey = "step" + stepLevel;

                    Map<String, Object> data = (Map<String, Object>) document.get(stepKey);

                    if (data != null) {
                        bringTimeInterface.bringTime(data);
                    } else {
                        // stepKey에 해당하는 데이터가 없을 경우 기본값(step1)으로 폴백
                        Log.e("Firestore", stepKey + " 데이터가 없습니다. 기본값 step1을 사용합니다.");
                        bringTimeInterface.bringTime((Map<String, Object>) document.get("step1"));
                    }
                }
            } else {
                Log.e("Firestore", "데이터 가져오기 실패", task.getException());
            }
        });
    }

    public interface BringTimeInterface{
        void bringTime(Map<String, Object> data);
    }

    public static void getWordCount(String userId, String vocabId, GetWordCountInterface getWordCountInterface) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference docRef = db.collection("users")
                .document(userId)
                .collection("vocabularies")
                .document(vocabId);

        docRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    Long count = document.getLong("wordCount");

                    if (count != null) {
                        // 1. 필드가 정상적으로 있을 때
                        getWordCountInterface.wordCount(count.intValue());
                        Log.d("Firestore", "wordCount 값: " + count);
                    } else {
                        // 2. 문서는 있지만 wordCount 필드가 없을 때 -> 0 전달
                        Log.d("Firestore", "wordCount 필드가 없음 -> 0 반환");
                        getWordCountInterface.wordCount(0);
                    }
                } else {
                    // 3. 문서 자체가 없을 때 -> 0 혹은 null 전달
                    Log.d("Firestore", "문서 없음 -> 0 반환");
                    getWordCountInterface.wordCount(0);
                }
            } else {
                // 4. 에러 발생 시 -> null 혹은 -1 전달 (에러 처리용)
                Log.e("Firestore", "에러 발생: ", task.getException());
                getWordCountInterface.wordCount(null);
            }
        });
    }

    public interface GetWordCountInterface{
        void wordCount(Integer count);
    }

}
