package com.example.vocaapp.VocabularyList;

import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.Map;

public class VocabularyFirestore {
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

        // 3. 작업 예약: 단어 추가 & 개수 필드 1 증가
        batch.set(wordRef, wordData);
        batch.update(vocabRef, "wordCount", FieldValue.increment(1));

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
    public static void deleteWord(String uid, String vocabularyId, String wordId, Runnable onSuccess, Runnable onFailure) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();

        // 1. 단어장 문서 참조 (개수를 줄일 부모 문서)
        DocumentReference vocabRef = db.collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId);

        // 2. 삭제할 단어 문서 참조
        DocumentReference wordRef = vocabRef.collection("words").document(wordId);

        // 3. 작업 예약: 단어 삭제 & 개수 필드 1 감소
        batch.delete(wordRef);
        batch.update(vocabRef, "wordCount", FieldValue.increment(-1));

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
                .whereEqualTo("word", word)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        // 결과 리스트가 비어있지 않으면(isEmpty()가 false면) 단어가 존재하는 것임
                        boolean exists = !task.getResult().isEmpty();
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
}
