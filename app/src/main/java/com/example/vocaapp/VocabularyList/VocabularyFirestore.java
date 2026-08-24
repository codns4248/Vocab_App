package com.example.vocaapp.VocabularyList;

import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VocabularyFirestore {
    public static final int STATUS_UNKNOWN = 0;
    public static final int STATUS_CONFUSED = 1;
    public static final int STATUS_MEMORIZED = 2;

    private static String statusCountField(int status) {
        switch (status) {
            case STATUS_CONFUSED: return "confusedCount";
            case STATUS_MEMORIZED: return "memorizedCount";
            case STATUS_UNKNOWN:
            default: return "unknownCount";
        }
    }

    // 단어 db에 추가하는 로직
    public static void addWord(String uid, String vocabularyId, Map<String, Object> wordData, Runnable onSuccess, Runnable onFailure) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();

        // 1. 단어장 문서 참조 (개수를 업데이트할 부모 문서)
        DocumentReference vocabRef = db.collection("users")
                .document(uid)
                .collection("vocabularies")
                .document(vocabularyId);

        // 2. 단어 추가를 위한 새 문서 참조 생성
        DocumentReference wordRef = vocabRef.collection("words").document();

        Object statusValue = wordData.get("studyStatus");
        int initialStatus = statusValue instanceof Number ? ((Number) statusValue).intValue() : STATUS_UNKNOWN;

        // 3. 작업 예약: 단어 추가 & 개수 필드, 학습 상태별 개수 필드 1 증가
        // 주의: 같은 문서(vocabRef)에 대한 update()는 배치 내에서 마지막 호출만 반영되므로
        // 반드시 하나의 Map으로 합쳐서 한 번만 호출해야 한다.
        Map<String, Object> vocabUpdates = new HashMap<>();
        vocabUpdates.put("wordCount", FieldValue.increment(1));
        vocabUpdates.put(statusCountField(initialStatus), FieldValue.increment(1));

        batch.set(wordRef, wordData);
        batch.update(vocabRef, vocabUpdates);

        // 4. 한 번에 실행
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    if (onSuccess != null) onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    if (onFailure != null) onFailure.run();
                });
    }
    // 단어 db에서 삭제하는 로직
    public static void deleteWord(String uid, String vocabularyId, String wordId, int studyStatus, Runnable onSuccess, Runnable onFailure) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();

        // 1. 단어장 문서 참조 (개수를 줄일 부모 문서)
        DocumentReference vocabRef = db.collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId);

        // 2. 삭제할 단어 문서 참조
        DocumentReference wordRef = vocabRef.collection("words").document(wordId);

        // 3. 작업 예약: 단어 삭제 & 개수 필드, 학습 상태별 개수 필드 1 감소
        Map<String, Object> vocabUpdates = new HashMap<>();
        vocabUpdates.put("wordCount", FieldValue.increment(-1));
        vocabUpdates.put(statusCountField(studyStatus), FieldValue.increment(-1));

        batch.delete(wordRef);
        batch.update(vocabRef, vocabUpdates);

        // 4. 실행
        batch.commit()
                .addOnSuccessListener(aVoid -> { if (onSuccess != null) onSuccess.run(); })
                .addOnFailureListener(e -> { if (onFailure != null) onFailure.run(); });
    }

    // 단어 불러오는 db 로직
    public static void listenWords(String userId, String vocabularyId, OnWordsChanged listener) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("vocabularies")
                .document(vocabularyId)
                .collection("words")
                .orderBy("timeStamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) {
                        listener.onError(e);
                        return;
                    }

                    listener.onChanged(snapshots);
                });
    }
    public interface OnWordsChanged {
        void onChanged(QuerySnapshot snapshots);
        void onError(Exception e);
    }

    public void alreadyVocabulary(String uid, String vocabularyId, String word, alreadyVocabularyInterface alreadyVocabularyInterface){
        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId)
                .collection("words")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        // 대소문자를 구분하지 않고 동일한 단어가 있는지 확인
                        boolean exists = false;
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            String existingWord = doc.getString("word");
                            if (existingWord != null && existingWord.equalsIgnoreCase(word)) {
                                exists = true;
                                break;
                            }
                        }
                        alreadyVocabularyInterface.alreadyVocabulary(exists);
                    } else {
                        // 쿼리 실패 시에는 안전하게 false를 반환하거나 에러 처리를 합니다.
                        alreadyVocabularyInterface.alreadyVocabulary(false);
                    }
                });
    }

    public interface alreadyVocabularyInterface{
        void alreadyVocabulary(boolean isAlready);
    }

    // 단어장에 이미 등록된 단어들을 한 번에 조회 (소문자로 변환하여 대소문자 구분 없이 비교)
    public static void getExistingWords(String uid, String vocabularyId, OnExistingWordsListener listener) {
        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId)
                .collection("words")
                .get()
                .addOnCompleteListener(task -> {
                    Set<String> existingWords = new HashSet<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            String word = doc.getString("word");
                            if (word != null) existingWords.add(word.toLowerCase());
                        }
                    }
                    listener.onResult(existingWords);
                });
    }

    public interface OnExistingWordsListener {
        void onResult(Set<String> existingWordsLowerCase);
    }

    public static void updateStudyStatus(String uid, String vocabularyId, String wordId, int newStatus, int oldStatus) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference vocabRef = db.collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId);
        DocumentReference wordRef = vocabRef.collection("words").document(wordId);

        WriteBatch batch = db.batch();
        batch.update(wordRef, "studyStatus", newStatus);
        if (newStatus != oldStatus) {
            Map<String, Object> vocabUpdates = new HashMap<>();
            vocabUpdates.put(statusCountField(newStatus), FieldValue.increment(1));
            vocabUpdates.put(statusCountField(oldStatus), FieldValue.increment(-1));
            batch.update(vocabRef, vocabUpdates);
        }
        batch.commit()
                .addOnFailureListener(e -> Log.e("VocabularyFirestore", "studyStatus 업데이트 실패: " + e.getMessage()));
    }
}
