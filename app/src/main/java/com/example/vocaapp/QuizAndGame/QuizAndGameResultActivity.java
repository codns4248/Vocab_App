package com.example.vocaapp.QuizAndGame;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QuizAndGameResultActivity extends AppCompatActivity {

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
        ProgressBar circularProgressBar = findViewById(R.id.circularProgressBar);
        TextView tvProgress = findViewById(R.id.tvProgress);
        TextView finishTextView = findViewById(R.id.finishTextView);
        TextView resultTextView = findViewById(R.id.resultTextView);
        RecyclerView recycler = findViewById(R.id.failVocaRecyclerView);

        // RecyclerView 설정
        if (failedWordList != null && !failedWordList.isEmpty()) {
            // 어댑터 연결
            FailVocaListAdapter adapter = new FailVocaListAdapter(this, failedWordList);
            recycler.setAdapter(adapter);
            // 레이아웃 매니저 설정 (리스트 형태로 보여줌)
            recycler.setLayoutManager(new LinearLayoutManager(this));
        } else {
            // 틀린 단어가 없으면 리스트를 숨김
            recycler.setVisibility(View.GONE);
        }

        // 점수 계산 및 텍스트 설정
        passTextView.setText(String.valueOf(pass));
        failTextView.setText(String.valueOf(fail));

        int total = pass + fail;
        int progress = (total > 0) ? (int) ((double) pass / total * 100) : 0;

        circularProgressBar.setProgress(progress);
        tvProgress.setText(String.valueOf(progress));

        if (progress > 80){
            resultTextView.setText("축하해요! 합격이에요. 더욱 노력해봐요!");
        }
        else{
            resultTextView.setText("아쉬워요! 불합격이에요. 더욱 분발해봐요!");
        }

        // 종료 버튼 설정
        finishTextView.setOnClickListener(v -> {
            finish();
        });
    }
}
