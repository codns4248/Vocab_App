package com.example.vocaapp.Test;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.vocaapp.QuizAndGame.BarChartView;
import com.google.android.material.button.MaterialButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.QuizAndGame.FailVocaListAdapter;
import com.example.vocaapp.QuizAndGame.QuizAndGameFirestore;
import com.example.vocaapp.R;

import java.util.ArrayList;
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


        // 뷰 연결
        TextView passTextView = findViewById(R.id.passTextView);
        TextView failTextView = findViewById(R.id.failTextView);
        BarChartView barChartView = findViewById(R.id.barChartView);
        MaterialButton finishButton = findViewById(R.id.finishButton);
        TextView resultTextView = findViewById(R.id.resultTextView);
        RecyclerView recycler = findViewById(R.id.failVocaRecyclerView);

        if (failedWordList != null && !failedWordList.isEmpty()) {
            FailVocaListAdapter adapter = new FailVocaListAdapter(this, failedWordList);
            recycler.setAdapter(adapter);
            recycler.setLayoutManager(new LinearLayoutManager(this));
        } else {
            recycler.setVisibility(View.GONE);
        }

        passTextView.setText(String.valueOf(pass));
        failTextView.setText(String.valueOf(fail));

        barChartView.setValues(pass, fail);

        int total = pass + fail;
        int progress = (total > 0) ? (int) ((double) pass / total * 100) : 0;

        // 합격 조건에 부합 처리
        if (progress >= 80) {
            if (userId != null && vocabularyId != null) {
                resultTextView.setText("오~~ 잘했어요! 합격이에요!");

                TestFirestore.getStampCount(userId, vocabularyId, new TestFirestore.StampCountCallback() {
                    @Override
                    public void onResult(int stampCount) {
                        int nextStamp = stampCount + 1;

                        TestFirestore.handleTestPass(userId, vocabularyId, new TestFirestore.TestResultCallback() {
                            @Override
                            public void onSuccess() {
                                Log.d("Firestore", "스탬프 획득 성공!");

                                StudyManager.getInstance().studyVocabulary(
                                        TestResultActivity.this,
                                        userId,
                                        vocabularyId,
                                        nextStamp
                                );

                                Toast.makeText(TestResultActivity.this, "✅ 스탬프가 찍혔습니다!", Toast.LENGTH_SHORT).show();
                            }
                            @Override
                            public void onFailure(Exception e) {
                                Log.e("DB_ERROR", "업데이트 실패", e);
                            }
                        });
                    }
                    @Override
                    public void onError(Exception e) {

                    }
                });


            } else {
                // 자율 복습일 때
                resultTextView.setText("오~~ 잘했어요!\n(자율 복습이라 기록은 안 돼요!)");
            }
        } else {
            resultTextView.setText("흑흑.. 아쉽게도 불합격이에요..");
        }

        finishButton.setOnClickListener(v -> finish());
    }
}
