package com.example.vocaapp.Camera;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
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
    private CheckBox selectAllCheckBox;
    private Button saveButton;
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
        selectAllCheckBox = findViewById(R.id.selectAllCheckBox);

        adapter = new WordSelectAdapter(wordList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // 개별 항목 체크 변경 시 → 전체 선택 체크박스 동기화
        adapter.setOnSelectionChangedListener(() -> updateSelectAllCheckBox());

        // 전체 선택 체크박스 토글
        selectAllCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 사용자가 직접 누른 경우에만 동작 (코드로 setChecked 할 때는 무시)
            if (buttonView.isPressed()) {
                adapter.setAllSelected(isChecked);
            }
        });

        saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> saveSelectedWords());

        // 중복 확인이 끝나기 전에는 저장하지 못하도록 비활성화
        saveButton.setEnabled(false);
        markDuplicateWords(wordList);
    }

    // 이미 단어장에 등록된 단어인지 확인하여 표시하고, 기본 선택에서 제외
    private void markDuplicateWords(List<WordItem> wordList) {
        VocabularyFirestore.getExistingWords(uid, vocabularyId, existingWords -> {
            for (WordItem item : wordList) {
                if (item.word != null && existingWords.contains(item.word.toLowerCase())) {
                    item.isDuplicate = true;
                    item.selected = false;
                }
            }
            adapter.notifyDataSetChanged();
            updateSelectAllCheckBox();
            saveButton.setEnabled(true);
        });
    }

    // 이미 등록된 단어를 제외한 나머지가 모두 선택되어 있으면 전체 선택 체크박스도 체크
    private void updateSelectAllCheckBox() {
        boolean allSelected = true;
        for (WordItem item : adapter.getAllWords()) {
            if (!item.isDuplicate && !item.selected) {
                allSelected = false;
                break;
            }
        }
        // 리스너 일시 제거 후 변경 (무한 루프 방지)
        selectAllCheckBox.setChecked(allSelected);
    }

    private void saveSelectedWords() {
        List<WordItem> selected = adapter.getSelectedWords();

        if (selected.isEmpty()) {
            Toast.makeText(this, "선택된 단어가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
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