package com.example.vocaapp.Settting;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocaapp.LoginActivity;
import com.example.vocaapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;

public class AccountSettingActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_setting);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        TextView tvAccountEmail = findViewById(R.id.tvAccountEmail);
        TextView tvAccountProvider = findViewById(R.id.tvAccountProvider);
        TextView btnLogout = findViewById(R.id.logoutTextView);
        TextView unregisterTextView = findViewById(R.id.unregisterTextView);

        unregisterTextView.setPaintFlags(unregisterTextView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        if (user != null) {
            tvAccountEmail.setText(user.getEmail());

            String providerText = "로그인됨";
            for (UserInfo info : user.getProviderData()) {
                if ("google.com".equals(info.getProviderId())) {
                    providerText = "Google 계정으로 로그인됨";
                    break;
                } else if ("password".equals(info.getProviderId())) {
                    providerText = "이메일로 로그인됨";
                    break;
                }
            }
            tvAccountProvider.setText(providerText);
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        unregisterTextView.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("회원 탈퇴");
            builder.setMessage("정말로 탈퇴하시겠습니까?\n탈퇴 시 작성하신 단어장과 학습 기록이 모두 삭제되며 복구할 수 없습니다.\n탈퇴를 위해 구글 로그인을 다시 진행해야합니다.");

            builder.setPositiveButton("탈퇴", (dialog, which) -> {
                SettingFirebase settingFirebase = new SettingFirebase(this, new SettingFirebase.OnUnregisterListener() {
                    @Override
                    public void onSuccess() {
                        Intent intent = new Intent(AccountSettingActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        Toast.makeText(AccountSettingActivity.this, "탈퇴 실패: " + errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });
                settingFirebase.performUnregister();
            });

            builder.setNegativeButton("취소", (dialog, which) -> dialog.dismiss());
            builder.create().show();
        });
    }
}
