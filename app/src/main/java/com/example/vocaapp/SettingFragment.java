package com.example.vocaapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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

        //로그인 한 유저 이메일 보여주기
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            tvUserEmail.setText(user.getEmail());
        }

        //로그아웃 버튼 눌렀을 때 할 일
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 파이어베이스 로그아웃
                mAuth.signOut();

                //Toast.makeText(getActivity(), "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();

                // 로그인 화면으로 이동
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                // 뒤로가기 눌러도 설정 화면으로 못 돌아오게 기록 삭제
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });
        return view;
        // return inflater.inflate(R.layout.fragment_setting, container, false);
    }
}
