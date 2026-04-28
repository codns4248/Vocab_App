package com.example.vocaapp.Camera;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;
import com.example.vocaapp.VocabularyList.VocabularyFirestore;
import com.example.vocaapp.VocabularyList.WordItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WordSelectActivity extends AppCompatActivity {
    private WordSelectAdapter adapter;
    private String vocabularyId;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_select);

        ArrayList<WordItem> wordList = getIntent().getParcelableArrayListExtra("wordList");
        vocabularyId = getIntent().getStringExtra("vocabularyId");
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        RecyclerView recyclerView = findViewById(R.id.wordRecyclerView);
        adapter = new WordSelectAdapter(wordList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.saveButton).setOnClickListener(v -> saveSelectedWords());
    }

    private void saveSelectedWords() {
        List<WordItem> selected = adapter.getSelectedWords();

        if (selected.isEmpty()) {
            Toast.makeText(this, "선택된 단어가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        int total = selected.size();
        final int[] successCount = {0};

        for (WordItem item : selected) {
            Map<String, Object> wordData = new HashMap<>();
            wordData.put("word", item.word);
            wordData.put("meaning", item.meaning);
            wordData.put("pronunciation", item.pronunciation);
            wordData.put("timeStamp", FieldValue.serverTimestamp());

            VocabularyFirestore.addWord(uid, vocabularyId, wordData,
                    () -> {
                        successCount[0]++;
                        if (successCount[0] == total) {
                            Toast.makeText(this, total + "개 단어 저장 완료", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    },
                    () -> Log.e("Firestore", "저장 실패: " + item.word)
            );
        }
    }
}
