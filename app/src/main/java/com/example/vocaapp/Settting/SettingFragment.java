package com.example.vocaapp.Settting;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.vocaapp.LoginActivity;
import com.example.vocaapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingFragment extends Fragment {

    private FirebaseAuth mAuth; // 파이어베이스 관리자

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 화면 가져옴
        View view = inflater.inflate(R.layout.fragment_profile, container,false);

        // 파이어베이스 준비
        mAuth = FirebaseAuth.getInstance();

        //xml에 있는 이메일 글씨, 버튼 가져오기
        TextView tvUserEmail = view.findViewById(R.id.tvUserEmail);
        TextView btnLogout = view.findViewById(R.id.logoutTextView);
        TextView unregisterTextView = view.findViewById(R.id.unregisterTextView);
        LinearLayout sendCommentLinear = view.findViewById(R.id.sendCommentLinear);
        LinearLayout checkPolicyLinear = view.findViewById(R.id.checkPolicyLinear);

        //로그인 한 유저 이메일 보여주기
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            tvUserEmail.setText(user.getEmail());
        }

        sendCommentLinear.setOnClickListener(v -> {
            String commentUrl = "https://docs.google.com/forms/d/e/1FAIpQLSefi_zWPR3Lry12_PVikiCkDr7e6s19GGj_kTORSLWUGE1Egg/viewform?usp=dialog";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(commentUrl));
            startActivity(intent);
        });

        checkPolicyLinear.setOnClickListener(v -> {
            String notionUrl = "https://ajar-saturnalia-176.notion.site/Voca-App-Privacy-Policy-Terms-Conditions-KOR-335c76a94e95808ab816d27f73c51e8c";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(notionUrl));
            startActivity(intent);
        });

        //로그아웃 버튼 눌렀을 때 할 일
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 파이어베이스 로그아웃
                mAuth.signOut();

                // 로그인 화면으로 이동
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                // 뒤로가기 눌러도 설정 화면으로 못 돌아오게 기록 삭제
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });

        unregisterTextView.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());

            builder.setTitle("회원 탈퇴");
            builder.setMessage("정말로 탈퇴하시겠습니까?\n탈퇴 시 작성하신 단어장과 학습 기록이 모두 삭제되며 복구할 수 없습니다.\n탈퇴를 위해 구글 로그인을 다시 진행해야합니다.");

            // '탈퇴' 버튼 클릭 시
            builder.setPositiveButton("탈퇴", (dialog, which) -> {

                // 1. SettingFirebase 인스턴스 생성 시 콜백 정의
                SettingFirebase settingFirebase = new SettingFirebase(v.getContext(), new SettingFirebase.OnUnregisterListener() {
                    @Override
                    public void onSuccess() {
                        Intent intent = new Intent(getActivity(), LoginActivity.class);
                        // 뒤로가기 눌러도 설정 화면으로 못 돌아오게 스택 비우기
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        Toast.makeText(v.getContext(), "탈퇴 실패: " + errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });

                // 4. 실제 탈퇴 로직 시작
                settingFirebase.performUnregister();
            });

            builder.setNegativeButton("취소", (dialog, which) -> {
                dialog.dismiss();
            });

            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        });

        return view;
    }
}
