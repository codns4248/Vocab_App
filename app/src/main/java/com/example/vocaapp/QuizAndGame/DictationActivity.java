package com.example.vocaapp.QuizAndGame;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.vocaapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DictationActivity extends AppCompatActivity {

    ImageView cancelImageView;
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    TextView currentPageTextView, displayedMeanTextView, totalPageTextView;
    Map<String, String> wordsAndMeanings = new HashMap<>();
    EditText wordEditText;
    ConstraintLayout nextConstraintLayout;
    int currentIndex = 0;
    List<String> keyList = new ArrayList<>();
    int pass = 0;
    int fail = 0;
    int currentPage = 1;

    // 결과에서 틀린 단어들을 표시하기 위한 리스트
    ArrayList<Map<String, Object>> failedWordsList = new ArrayList<>();

    List<Map<String, Object>> originWordsList = new ArrayList<>();


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictation);

        // 데이터 연결
        cancelImageView = findViewById(R.id.cancelImageView);
        currentPageTextView = findViewById(R.id.currentPageTextView);
        displayedMeanTextView = findViewById(R.id.wordTextView);
        nextConstraintLayout = findViewById(R.id.nextConstraintLayout);
        wordEditText = findViewById(R.id.wordEditText);
        totalPageTextView = findViewById(R.id.totalPageTextView);

        // 유저 정보 가져오기
        String userId = user.getUid();

        // intent로 받은 데이터 꺼내기
        Intent intent = getIntent();
        String vocabularyId = intent.getStringExtra("vocabularyId");

        boolean isOfficial = intent.getBooleanExtra("isOfficial", false);

        // Firestore 메서드 실행
        QuizAndGameFirestore quizAndGameFirestore = new QuizAndGameFirestore();
        // 메서드 호출 시 세 번째 인자로 콜백 구현
        quizAndGameFirestore.getWordCount(userId, vocabularyId, new QuizAndGameFirestore.OnWordCountCallback() {
            @Override
            public void onCallback(long wordCount) {

                currentPage = 1;

                String currentPageToString = String.valueOf(currentPage);
                currentPageTextView.setText(currentPageToString);

                // 전체 단어 수 표시
                String wordCountToString = String.valueOf(wordCount);
                totalPageTextView.setText(wordCountToString);
            }
        });

        // 단어장 안에 있는 단어들 가져오기
        quizAndGameFirestore.getAllWords(userId, vocabularyId, new QuizAndGameFirestore.OnWordsLoadedCallback(){
            @Override
            public void onCallback(List<Map<String, Object>> words) {

                originWordsList = new ArrayList<>(words);

                // 데이터 저장
                for (Map<String, Object> data : words) {
                    wordsAndMeanings.put((String) data.get("meaning"), (String) data.get("word"));
                }

                if (wordsAndMeanings.isEmpty()) return;

                // 초기화
                keyList = new ArrayList<>(wordsAndMeanings.keySet());
                currentIndex = 0; // 시작 인덱스
                pass = 0;
                fail = 0;
                currentPage = 1;

                // 첫 번째 문제 표시 함수 호출
                updateUI();
            }
        });

        nextConstraintLayout.setOnClickListener(v -> {
            if (keyList == null || currentIndex >= keyList.size()) return;

            String currentMean = keyList.get(currentIndex);
            String correctWord = wordsAndMeanings.get(currentMean);
            String userInput = wordEditText.getText().toString().trim();

            // 정답 유무 체크
            if (userInput.equalsIgnoreCase(correctWord)) { // 대소문자 구분 없이 체크하려면 equalsIgnoreCase 권장
                pass++;
            } else {
                failedWordsList.add(originWordsList.get(currentIndex));
                fail++;
            }

            // 5. 다음 문제로 인덱스 증가 및 UI 갱신
            currentIndex++;
            currentPage++;

            if (currentIndex < keyList.size()) {
                updateUI(); // 다음 단어 보여주기
            } else {
                Intent testResultIntent = new Intent(this, TestResultActivity.class);

                if (user != null) {
                    testResultIntent.putExtra("pass", pass);
                    testResultIntent.putExtra("fail", fail);

                    testResultIntent.putExtra("vocabularyId", vocabularyId);
                    testResultIntent.putExtra("userId", user.getUid());

                    testResultIntent.putExtra("failedWords", failedWordsList);

                    testResultIntent.putExtra("isOfficial", isOfficial);

                    startActivity(testResultIntent);
                    finish();
                } else {
                    Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        cancelImageView.setOnClickListener(v -> {
            finish();
        });
    }
    private void updateUI() {
        String currentMean = keyList.get(currentIndex);
        displayedMeanTextView.setText(currentMean);
        wordEditText.setText(""); // 입력창 초기화
        String currentPageToString = String.valueOf(currentPage);
        currentPageTextView.setText(currentPageToString);
    }
}
