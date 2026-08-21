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

    // studyStatus 값과 단어장 문서의 카운터 필드 대응
    // 0 = 미학습, 1 = 헷갈림, 2 = 암기
    public static final int STATUS_UNLEARNED = 0;
    public static final int STATUS_CONFUSED = 1;
    public static final int STATUS_LEARNED = 2;
    public static final String[] STATUS_FIELDS = {"unlearnedCount", "confusedCount", "learnedCount"};

    private static String statusField(int status) {
        if (status < 0 || status >= STATUS_FIELDS.length) return STATUS_FIELDS[STATUS_UNLEARNED];
        return STATUS_FIELDS[status];
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

        // 3. 작업 예약: 단어 추가 & 개수 필드 1 증가
        batch.set(wordRef, wordData);
        // 보통은 미학습으로 시작하지만, 삭제 취소로 되살릴 때는 원래 상태가 실려온다.
        Object rawStatus = wordData.get("studyStatus");
        int status = (rawStatus instanceof Number) ? ((Number) rawStatus).intValue() : STATUS_UNLEARNED;
        batch.update(vocabRef,
                "wordCount", FieldValue.increment(1),
                statusField(status), FieldValue.increment(1));

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
    // studyStatus를 함께 받아 해당 상태의 카운터도 줄인다.
    public static void deleteWord(String uid, String vocabularyId, String wordId, int studyStatus,
                                  Runnable onSuccess, Runnable onFailure) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();

        // 1. 단어장 문서 참조 (개수를 줄일 부모 문서)
        DocumentReference vocabRef = db.collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId);

        // 2. 삭제할 단어 문서 참조
        DocumentReference wordRef = vocabRef.collection("words").document(wordId);

        // 3. 작업 예약: 단어 삭제 & 개수 필드 1 감소
        batch.delete(wordRef);
        batch.update(vocabRef,
                "wordCount", FieldValue.increment(-1),
                statusField(studyStatus), FieldValue.increment(-1));

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

    // 학습 상태를 바꾸면서 단어장의 상태별 카운터도 함께 옮긴다.
    // 이전 상태를 알아야 어느 카운터를 줄일지 정할 수 있다.
    public static void updateStudyStatus(String uid, String vocabularyId, String wordId,
                                         int oldStatus, int newStatus) {
        if (oldStatus == newStatus) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference vocabRef = db.collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId);

        WriteBatch batch = db.batch();
        batch.update(vocabRef.collection("words").document(wordId), "studyStatus", newStatus);
        batch.update(vocabRef,
                statusField(oldStatus), FieldValue.increment(-1),
                statusField(newStatus), FieldValue.increment(1));

        batch.commit().addOnFailureListener(e ->
                Log.e("VocabularyFirestore", "studyStatus 업데이트 실패: " + e.getMessage()));
    }

    // 단어 내용 수정 (학습 상태와 카운터는 건드리지 않는다)
    public static void updateWord(String uid, String vocabularyId, String wordId,
                                  String word, String meaning, String pronunciation,
                                  Runnable onSuccess, Runnable onFailure) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("word", word);
        updates.put("meaning", meaning);
        updates.put("pronunciation", pronunciation);

        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId)
                .collection("words").document(wordId)
                .update(updates)
                .addOnSuccessListener(aVoid -> { if (onSuccess != null) onSuccess.run(); })
                .addOnFailureListener(e -> {
                    Log.e("VocabularyFirestore", "단어 수정 실패: " + e.getMessage());
                    if (onFailure != null) onFailure.run();
                });
    }

    // 실제 단어들로부터 센 값을 단어장 문서에 되돌려 쓴다.
    // 카운터는 증감으로 유지하지만 어긋날 수 있고, 기존 단어장에는 필드 자체가 없다.
    // 단어 목록을 여는 시점에 한 번 맞춰주면 그 두 경우가 모두 해결된다.
    public static void syncStatusCounts(String uid, String vocabularyId,
                                        int total, int unlearned, int confused, int learned) {
        Map<String, Object> counts = new HashMap<>();
        counts.put("wordCount", total);
        counts.put(STATUS_FIELDS[STATUS_UNLEARNED], unlearned);
        counts.put(STATUS_FIELDS[STATUS_CONFUSED], confused);
        counts.put(STATUS_FIELDS[STATUS_LEARNED], learned);

        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId)
                .update(counts)
                .addOnFailureListener(e ->
                        Log.e("VocabularyFirestore", "상태 카운트 동기화 실패: " + e.getMessage()));
    }
}
