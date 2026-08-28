package com.forevermemory.vocaapp.Settting;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import androidx.appcompat.app.AlertDialog;

import com.forevermemory.vocaapp.LoginActivity;
import com.forevermemory.vocaapp.R;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

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
        TextView tvUserPoint = view.findViewById(R.id.tvUserPoint);
        LinearLayout checkNoticeLinear = view.findViewById(R.id.checkNoticeLinear);
        LinearLayout sendCommentLinear = view.findViewById(R.id.sendCommentLinear);
        LinearLayout checkPolicyLinear = view.findViewById(R.id.checkPolicyLinear);
        MaterialSwitch switchMarketingPush = view.findViewById(R.id.switchMarketingPush);
        LinearLayout logoutLinear = view.findViewById(R.id.logoutLinear);
        LinearLayout unregisterLinear = view.findViewById(R.id.unregisterLinear);

        //로그인 한 유저 이메일 보여주기
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // 카카오 계정은 이메일이 선택 동의라 없을 수 있다.
            // 빈칸으로 두면 화면이 비어 보이므로 식별자(uid)를 대신 보여준다.
            if (SettingFirebase.isKakaoAccount(user)) {
                tvUserEmail.setText(user.getUid());
            } else {
                tvUserEmail.setText(user.getEmail());
            }

            // 잔여 포인트 불러와서 표시하기
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        long point = 0;
                        if (snapshot.exists() && snapshot.get("point") != null) {
                            point = snapshot.getLong("point");
                        }
                        tvUserPoint.setText(point + " P");
                    })
                    .addOnFailureListener(e -> tvUserPoint.setText("0 P"));
        }

        // 마케팅 알림(OneSignal) 수신 토글.
        // 복습 알림은 Cloud Functions가 FCM으로 직접 보내므로 이 스위치와 무관하다.
        switchMarketingPush.setChecked(MarketingPushPrefs.isEnabled(requireContext()));
        switchMarketingPush.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;   // setChecked로 인한 호출은 무시
            MarketingPushPrefs.setEnabled(requireContext(), isChecked);
            Toast.makeText(getContext(),
                    isChecked ? "마케팅 정보 수신에 동의했습니다." : "마케팅 정보 수신을 해제했습니다.",
                    Toast.LENGTH_SHORT).show();
        });

        checkNoticeLinear.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), NoticeActivity.class);
            startActivity(intent);
        });

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

        logoutLinear.setOnClickListener(v -> showLogoutDialog());
        unregisterLinear.setOnClickListener(v -> showUnregisterDialog());

        return view;
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("로그아웃")
                .setMessage("로그아웃 하시겠습니까?")
                .setPositiveButton("로그아웃", (dialog, which) -> {
                    mAuth.signOut();
                    goToLogin();
                })
                .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showUnregisterDialog() {
        // 재인증 방식이 제공자마다 달라서 안내 문구도 나눈다.
        boolean isKakao = SettingFirebase.isKakaoAccount(mAuth.getCurrentUser());
        String reauthNotice = isKakao
                ? "\n탈퇴를 위해 카카오 로그인을 다시 진행해야 합니다.\n탈퇴 시 카카오 계정과의 연결도 해제됩니다."
                : "\n탈퇴를 위해 구글 로그인을 다시 진행해야 합니다.";

        new AlertDialog.Builder(requireContext())
                .setTitle("회원 탈퇴")
                .setMessage("정말로 탈퇴하시겠습니까?\n탈퇴 시 작성하신 단어장과 학습 기록이 모두 삭제되며 복구할 수 없습니다." + reauthNotice)
                .setPositiveButton("탈퇴", (dialog, which) -> {
                    SettingFirebase settingFirebase = new SettingFirebase(requireContext(),
                            new SettingFirebase.OnUnregisterListener() {
                                @Override
                                public void onSuccess() {
                                    if (isAdded()) goToLogin();
                                }

                                @Override
                                public void onFailure(String errorMsg) {
                                    if (isAdded()) {
                                        Toast.makeText(getContext(), "탈퇴 실패: " + errorMsg,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                    settingFirebase.performUnregister();
                })
                .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void goToLogin() {
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
