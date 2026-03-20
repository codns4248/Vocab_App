package com.example.vocaapp.QuizAndGame;

import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;
import com.example.vocaapp.manager.StudyManager;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestResultActivity extends AppCompatActivity {

    private String userId;
    private String vocabularyId;

    private List<Map<String, Object>> failedWordList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_result);


        if (getIntent().hasExtra("failedWords")) {
            failedWordList = (List<Map<String, Object>>) getIntent().getSerializableExtra("failedWords");
        } else {
            failedWordList = new ArrayList<>();
        }

        // 인텐트 데이터 수신
        Intent intent = getIntent();
        int pass = intent.getIntExtra("pass", 0);
        int fail = intent.getIntExtra("fail", 0);
        userId = intent.getStringExtra("userId");
        vocabularyId = intent.getStringExtra("vocabularyId");

        boolean isOfficial = intent.getBooleanExtra("isOfficial", false);

        // 뷰 연결
        TextView passTextView = findViewById(R.id.passTextView);
        TextView failTextView = findViewById(R.id.failTextView);
        ProgressBar circularProgressBar = findViewById(R.id.circularProgressBar);
        TextView tvProgress = findViewById(R.id.tvProgress);
        TextView finishTextView = findViewById(R.id.finishTextView);
        TextView resultTextView = findViewById(R.id.resultTextView);
        RecyclerView recycler = findViewById(R.id.failVocaRecyclerView);

        // [추가 및 수정] 2-1. RecyclerView 설정 (매우 중요!)
        if (failedWordList != null && !failedWordList.isEmpty()) {
            // 어댑터 연결 (생성자에 'this'와 '리스트' 전달)
            FailVocaListAdapter adapter = new FailVocaListAdapter(this, failedWordList);
            recycler.setAdapter(adapter);
            // 레이아웃 매니저 설정 (리스트 형태로 보여줌)
            recycler.setLayoutManager(new LinearLayoutManager(this));
        } else {
            // 틀린 단어가 없으면 리스트를 숨김
            recycler.setVisibility(View.GONE);
        }

        // 3. 점수 계산 및 텍스트 설정
        passTextView.setText(String.valueOf(pass));
        failTextView.setText(String.valueOf(fail));

        int total = pass + fail;
        int progress = (total > 0) ? (int) ((double) pass / total * 100) : 0;

        circularProgressBar.setProgress(progress);
        tvProgress.setText(String.valueOf(progress));

        // 4. 합격 조건(80점) 체크
        if (progress >= 80) {
            StudyManager.getInstance().studyVocabulary(TestResultActivity.this, userId, vocabularyId);
            com.example.vocaapp.VocabularyBookList.VocabularyBookFirestore.updateAfterQuiz(userId, vocabularyId, new com.example.vocaapp.VocabularyBookList.VocabularyBookFirestore.VocabularyBookCallback() {
                @Override
                public void onSuccess() {
                    android.util.Log.d("TestResult", "다음 복습 시간 갱신 완료!");
                }

                @Override
                public void onFailure(Exception e) {
                    android.util.Log.e("TestResult", "시간 갱신 실패", e);
                }
            });

            if (isOfficial) {

                resultTextView.setText("오~~ 잘했어요! 합격이에요!");

                if (userId != null && vocabularyId != null) {
                    QuizAndGameFirestore.handleTestPass(userId, vocabularyId, new QuizAndGameFirestore.QuizResultCallback() {
                        @Override
                        public void onSuccess() {
                            Log.d("Firestore", "스탬프 획득 성공!");
                            Toast.makeText(TestResultActivity.this, "✅ 스탬프가 찍혔습니다!", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(Exception e) {
                            Log.e("DB_ERROR", "업데이트 실패", e);
                            Toast.makeText(TestResultActivity.this, "❌ 스탬프 기록 실패", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } else {

                resultTextView.setText("오~~ 잘했어요!\n(자율 복습이라 기록은 안 돼요!)");
                Toast.makeText(TestResultActivity.this, "✍️ 자율학습 완료! 스탬프는 적립되지 않습니다.", Toast.LENGTH_SHORT).show();
            }
        } else {
            resultTextView.setText("흑흑.. 아쉽게도 불합격이에요..");
        }

        // 6. 종료 버튼 설정
        finishTextView.setOnClickListener(v -> {
            finish();
        });
    }
}
