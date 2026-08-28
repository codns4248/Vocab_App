package com.forevermemory.vocaapp.QuizAndGame;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.forevermemory.vocaapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OXTestActivity extends AppCompatActivity {

    private TextView vocabularyTextView;
    private ConstraintLayout failImageView, passImageView;
    private List<Map<String, Object>> wordList = new ArrayList<>();
    private List<Map<String, Object>> failedWordList = new ArrayList<>();

    private int currentIndex = 0;
    private int correctCount = 0;
    private String vocabularyId;
    private String userId;
    int currentPage = 1;

    private boolean isOfficial = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ox_test);

        // 인텐트로 전달받은 vocabularyId 가져오기
        vocabularyId = getIntent().getStringExtra("vocabularyId");
        isOfficial = getIntent().getBooleanExtra("isOfficial", false);

        // 뷰 연결
        vocabularyTextView = findViewById(R.id.vocabularyTextView);
        failImageView = findViewById(R.id.failImageView);
        passImageView = findViewById(R.id.passImageView);
        ImageView cancelImageView = findViewById(R.id.cancelImageView);
        TextView totalPageTextView = findViewById(R.id.totalPageTextView);

        cancelImageView.setOnClickListener(v -> finish());

        QuizAndGameFirestore quizAndGameFirestore = new QuizAndGameFirestore();

        // 현재 로그인한 사용자의 id 가져오기
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userId = user.getUid();
        }

        @SuppressWarnings("unchecked")
        ArrayList<HashMap<String, Object>> preloaded =
                (ArrayList<HashMap<String, Object>>) getIntent().getSerializableExtra("preloadedWords");

        if (preloaded != null && !preloaded.isEmpty()) {
            wordList.addAll(preloaded);
            totalPageTextView.setText(String.valueOf(wordList.size()));
            displayWord();
        } else {
            quizAndGameFirestore.getWordCount(userId, vocabularyId, wordCount ->
                    totalPageTextView.setText(String.valueOf(wordCount)));

            quizAndGameFirestore.loadWordsFromFirestore(userId, vocabularyId, wordList, loadedList -> {
                if (!loadedList.isEmpty()) {
                    displayWord();
                } else {
                    Toast.makeText(OXTestActivity.this, "단어장에 단어가 없습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        }

        // X 버튼 클릭 리스너
        failImageView.setOnClickListener(v -> {

            if (currentIndex < wordList.size()) {
                failedWordList.add(wordList.get(currentIndex));
            }

            currentPage ++;
            moveToNextWord();
        });

        // O 버튼 클릭 리스너
        passImageView.setOnClickListener(v -> {
            correctCount++;
            currentPage ++;
            moveToNextWord();
        });
    }

    private void displayWord() {
        if (currentIndex < wordList.size()) {
            String word = (String) wordList.get(currentIndex).get("word");
            vocabularyTextView.setText(word);

            TextView currentPageTextView = findViewById(R.id.currentPageTextView);
            if (currentPageTextView != null) {
                currentPageTextView.setText(String.valueOf(currentPage));
            }
        } else {
            // 모든 단어를 다 본 경우 결과 화면으로 이동
            showFinalResult();
        }
    }

    private void moveToNextWord() {
        currentIndex++;
        displayWord();
    }

    // testResultActivity로 데이터 넘기기
    private void showFinalResult() {
        int pass = correctCount;
        int fail = wordList.size() - correctCount;

        Intent intent = new Intent(OXTestActivity.this, QuizAndGameResultActivity.class);

        intent.putExtra("pass", pass);
        intent.putExtra("fail", fail);
        intent.putExtra("userId", userId);
        intent.putExtra("vocabularyId", vocabularyId);
        intent.putExtra("isOfficial", isOfficial);

        // 틀린 단어와 뜻을 넘겨줌
        intent.putExtra("failedWords", (Serializable) failedWordList);

        startActivity(intent);
        finish();
    }
}
