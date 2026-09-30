package com.forevermemory.vocaapp.Settting;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.forevermemory.vocaapp.R;

public class AccountLinkActivity extends AppCompatActivity {
    private AccountLinkViewModel model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_link);
        model = new ViewModelProvider(this).get(AccountLinkViewModel.class);
        Button google = findViewById(R.id.linkGoogleButton);
        Button kakao = findViewById(R.id.linkKakaoButton);
        findViewById(R.id.linkBackButton).setOnClickListener(v -> {
            if (!model.busy) finish();
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!model.busy) finish();
            }
        });
        google.setOnClickListener(v -> model.linkGoogle(this));
        kakao.setOnClickListener(v -> model.linkKakao(this));
        findViewById(R.id.linkRefreshIcon).setOnClickListener(v -> model.refresh());
        model.state.observe(this, message -> {
            renderProvider(google, R.id.linkGoogleState, R.id.linkGoogleCheck, model.googleLinked);
            renderProvider(kakao, R.id.linkKakaoState, R.id.linkKakaoCheck, model.kakaoLinked);
            google.setEnabled(model.loaded && !model.busy && !model.googleLinked);
            kakao.setEnabled(model.loaded && !model.busy && !model.kakaoLinked);
            findViewById(R.id.linkProgress).setVisibility(model.busy ? View.VISIBLE : View.GONE);
            findViewById(R.id.linkBackButton).setEnabled(!model.busy);
            findViewById(R.id.linkRefreshIcon).setEnabled(!model.busy);
            ((TextView) findViewById(R.id.linkStatus)).setText(message);
        });
        if (!model.loaded && !model.busy) model.refresh();
    }

    private void renderProvider(Button button, int stateId, int checkId, boolean linked) {
        boolean confirmed = model.loaded && linked;
        TextView state = findViewById(stateId);
        state.setText(!model.loaded ? (model.busy ? "확인 중" : "확인 필요")
                : linked ? "연결됨" : "연결 안 됨");
        state.setTextColor(confirmed ? 0xFF16805D : 0xFF737373);
        button.setVisibility(confirmed ? View.INVISIBLE : View.VISIBLE);
        findViewById(checkId).setVisibility(confirmed ? View.VISIBLE : View.INVISIBLE);
    }
}
