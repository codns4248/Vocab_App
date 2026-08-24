package com.example.vocaapp.VocabularyBookList;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.vocaapp.VocabularyList.VocabularyFirestore;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// wordCount/unknownCount/confusedCount/memorizedCount 필드가 배치 내 중복 update() 버그로
// 어긋난 기존 단어장을 words 서브컬렉션을 직접 세어 한 번 바로잡는 마이그레이션.
public class VocabularyCounterBackfill {

    private static final String PREFS_NAME = "voca_prefs";
    private static final String KEY_PREFIX = "counterBackfillV1Done_";
    private static final String TAG = "CounterBackfill";

    public static void runIfNeeded(Context context, String uid) {
        if (uid == null) return;

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String prefsKey = KEY_PREFIX + uid;
        if (prefs.getBoolean(prefsKey, false)) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid).collection("vocabularies")
                .get()
                .addOnSuccessListener(vocabSnapshot -> {
                    List<DocumentSnapshot> vocabDocs = vocabSnapshot.getDocuments();
                    if (vocabDocs.isEmpty()) {
                        prefs.edit().putBoolean(prefsKey, true).apply();
                        return;
                    }

                    AtomicInteger remaining = new AtomicInteger(vocabDocs.size());
                    AtomicInteger failures = new AtomicInteger(0);

                    for (DocumentSnapshot vocabDoc : vocabDocs) {
                        vocabDoc.getReference().collection("words").get()
                                .addOnSuccessListener(wordsSnapshot ->
                                        recountAndSave(vocabDoc, wordsSnapshot.getDocuments())
                                                .addOnCompleteListener(t -> {
                                                    if (!t.isSuccessful()) failures.incrementAndGet();
                                                    if (remaining.decrementAndGet() == 0) {
                                                        finish(prefs, prefsKey, failures.get());
                                                    }
                                                }))
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "단어 목록 조회 실패: " + vocabDoc.getId(), e);
                                    failures.incrementAndGet();
                                    if (remaining.decrementAndGet() == 0) {
                                        finish(prefs, prefsKey, failures.get());
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "단어장 목록 조회 실패", e));
    }

    private static Task<Void> recountAndSave(
            DocumentSnapshot vocabDoc, List<? extends DocumentSnapshot> wordDocs) {
        int unknown = 0, confused = 0, memorized = 0;
        for (DocumentSnapshot wordDoc : wordDocs) {
            Long statusLong = wordDoc.getLong("studyStatus");
            int status = statusLong != null ? statusLong.intValue() : VocabularyFirestore.STATUS_UNKNOWN;
            if (status == VocabularyFirestore.STATUS_CONFUSED) confused++;
            else if (status == VocabularyFirestore.STATUS_MEMORIZED) memorized++;
            else unknown++;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("wordCount", wordDocs.size());
        updates.put("unknownCount", unknown);
        updates.put("confusedCount", confused);
        updates.put("memorizedCount", memorized);

        return vocabDoc.getReference().update(updates)
                .addOnFailureListener(e -> Log.e(TAG, "카운터 저장 실패: " + vocabDoc.getId(), e));
    }

    private static void finish(SharedPreferences prefs, String prefsKey, int failureCount) {
        if (failureCount == 0) {
            prefs.edit().putBoolean(prefsKey, true).apply();
        } else {
            Log.e(TAG, "카운터 백필 중 " + failureCount + "건 실패 - 다음 실행 시 재시도됨");
        }
    }
}
