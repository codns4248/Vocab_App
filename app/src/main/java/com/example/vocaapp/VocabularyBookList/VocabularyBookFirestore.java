package com.example.vocaapp.VocabularyBookList;

import com.example.vocaapp.VocabularyList.VocabularyFirestore;
import com.google.firebase.auth.FirebaseAuth;
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

    //  단어장 불러오는 db 로직
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


    //진행상황 초기화 메서드
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
    public static void updateAfterQuiz(String uid, String vocabId, VocabularyBookCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference docRef = db.collection("users").document(uid)
                .collection("vocabularies").document(vocabId);

        // 트랜잭션을 사용하여 안전하게 업데이트
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(docRef);

            // 1. 현재 스탬프 개수 가져오기 (기존 스탬프가 올랐다면 그 값을 기준으로 함)
            long currentStamp = 0;
            if (snapshot.contains("stampCount")) {
                currentStamp = snapshot.getLong("stampCount");
            }

            // 2. 다음 복습 시간 계산
            int nextStage = (int) currentStamp;
            if (nextStage < 1) nextStage = 1; // 최소 1단계부터 시작

            int waitMinutes = com.example.vocaapp.manager.StudyManager.getInstance().getWaitMinutes(nextStage);

            long waitMillis = waitMinutes * 60 * 1000L;
            long nextReviewMillis = System.currentTimeMillis() + waitMillis;

            // com.google.firebase.Timestamp 를 사용하여 에러 방지
            com.google.firebase.Timestamp nextReviewDate = new com.google.firebase.Timestamp(new java.util.Date(nextReviewMillis));

            // 3. DB 업데이트 (시간 갱신)
            transaction.update(docRef, "nextReviewDate", nextReviewDate);

            return null;
        }).addOnSuccessListener(aVoid -> {
            if (callback != null) callback.onSuccess();
        }).addOnFailureListener(e -> {
            if (callback != null) callback.onFailure(e);
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

}