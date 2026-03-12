package com.example.vocaapp.QuizAndGame;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocaapp.R;
import com.example.vocaapp.manager.StudyManager;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class TestResultActivity extends AppCompatActivity {

    private String userId;
    private String vocabularyId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_result);

        // 1. 인텐트 데이터 수신
        Intent intent = getIntent();
        int pass = intent.getIntExtra("pass", 0);
        int fail = intent.getIntExtra("fail", 0);
        userId = intent.getStringExtra("userId");
        vocabularyId = intent.getStringExtra("vocabularyId");

        boolean isOfficial = intent.getBooleanExtra("isOfficial", false);

        // 2. XML 뷰 연결
        TextView passTextView = findViewById(R.id.passTextView);
        TextView failTextView = findViewById(R.id.failTextView);
        ProgressBar circularProgressBar = findViewById(R.id.circularProgressBar);
        TextView tvProgress = findViewById(R.id.tvProgress);
        TextView finishTextView = findViewById(R.id.finishTextView);
        TextView resultTextView = findViewById(R.id.resultTextView);

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

    // [메서드 분리] 단어별 망각곡선 적용 및 에러 처리
    private void applySpacedRepetition(FirebaseFirestore db, String userId, String vocabularyId) {
        db.collection("users").document(userId)
                .collection("vocabularies").document(vocabularyId)
                .collection("words")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        // StudyManager를 통해 각 단어별 stage/날짜 업데이트
                        StudyManager.getInstance()
                                .studyVocabulary(TestResultActivity.this, userId, vocabularyId);
                    }
                    Log.d("StudyLogic", "모든 단어 망각곡선 적용 완료");
                })
                .addOnFailureListener(e -> {
                    Log.e("StudyLogic", "단어 목록 로드 실패: ", e);
                    Toast.makeText(TestResultActivity.this, "❌ 복습 일정 업데이트 실패", Toast.LENGTH_SHORT).show();
                });
    }
}