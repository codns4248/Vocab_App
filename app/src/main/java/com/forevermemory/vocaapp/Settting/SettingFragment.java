package com.forevermemory.vocaapp.Settting;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.forevermemory.vocaapp.util.PopupUtil;

public class SettingFragment extends Fragment {

    private FirebaseAuth mAuth; // 파이어베이스 관리자

    // 탈퇴 성공 콜백은 이 화면이 사라진 뒤에 올 수도 있어, 그때도 로그인 화면으로
    // 넘어갈 수 있도록 앱 컨텍스트를 들고 있는다.
    private Context appContext;

    // 엑셀 파일 선택기. Fragment 생성 시점에 등록해야 하므로 필드로 둔다.
    private final ActivityResultLauncher<String[]> excelPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && isAdded()) {
                    ImportVocabularyHelper.handlePickedFile(requireActivity(), uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 화면 가져옴
        View view = inflater.inflate(R.layout.fragment_profile, container,false);

        appContext = inflater.getContext().getApplicationContext();

        // 파이어베이스 준비
        mAuth = FirebaseAuth.getInstance();

        //xml에 있는 이메일 글씨, 버튼 가져오기
        TextView tvUserEmail = view.findViewById(R.id.tvUserEmail);
        TextView tvUserPoint = view.findViewById(R.id.tvUserPoint);
        LinearLayout checkNoticeLinear = view.findViewById(R.id.checkNoticeLinear);
        LinearLayout sendCommentLinear = view.findViewById(R.id.sendCommentLinear);
        LinearLayout checkPolicyLinear = view.findViewById(R.id.checkPolicyLinear);
        LinearLayout forgettingCurveStoryLinear = view.findViewById(R.id.forgettingCurveStoryLinear);
        MaterialSwitch switchMarketingPush = view.findViewById(R.id.switchMarketingPush);
        LinearLayout exportVocabularyLinear = view.findViewById(R.id.exportVocabularyLinear);
        LinearLayout importVocabularyLinear = view.findViewById(R.id.importVocabularyLinear);
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
            PopupUtil.show(getContext(), isChecked ? "마케팅 정보 수신에 동의했습니다." : "마케팅 정보 수신을 해제했습니다.");
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

        forgettingCurveStoryLinear.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), ForgettingCurveStoryActivity.class)));

        exportVocabularyLinear.setOnClickListener(v -> ExportVocabularyDialog.show(requireActivity()));
        importVocabularyLinear.setOnClickListener(v ->
                excelPickerLauncher.launch(ImportVocabularyHelper.mimeTypes()));
        logoutLinear.setOnClickListener(v -> showLogoutDialog());

        // "회원탈퇴" 는 3단계다.
        //  1) unregisterLinear 클릭 → 이탈 방지 바텀시트(AccountRetentionBottomSheet)
        //  2) "그래도 탈퇴할게요" → 탈퇴 이유 설문 바텀시트(AccountWithdrawReasonBottomSheet)
        //  3) 이유 1개 이상 선택 후 "탈퇴하기" → proceedWithdrawal() 로 실제 탈퇴 진행
        getChildFragmentManager().setFragmentResultListener(
                AccountRetentionBottomSheet.REQUEST_KEY, this, (requestKey, bundle) -> {
                    if (bundle.getBoolean(AccountRetentionBottomSheet.RESULT_PROCEED, false)) {
                        new AccountWithdrawReasonBottomSheet()
                                .show(getChildFragmentManager(), "WithdrawReasonTag");
                    }
                });
        getChildFragmentManager().setFragmentResultListener(
                AccountWithdrawReasonBottomSheet.REQUEST_KEY, this, (requestKey, bundle) -> {
                    if (bundle.getBoolean(AccountWithdrawReasonBottomSheet.RESULT_PROCEED, false)) {
                        String[] reasons = bundle.getStringArray(AccountWithdrawReasonBottomSheet.RESULT_REASONS);
                        proceedWithdrawal(reasons != null ? reasons : new String[0]);
                    }
                });
        unregisterLinear.setOnClickListener(v ->
                new AccountRetentionBottomSheet().show(getChildFragmentManager(), "AccountRetentionTag"));

        return view;
    }

    private void showLogoutDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_logout, null);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btn_logout_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_logout_confirm).setOnClickListener(v -> {
            dialog.dismiss();
            mAuth.signOut();
            goToLogin();
        });

        dialog.show();

        // 기본 AlertDialog 창이 다소 넓어서 하얀 박스를 살짝 좁힌다.
        if (dialog.getWindow() != null) {
            int width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.82f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // 탈퇴 이유 설문(AccountWithdrawReasonBottomSheet)에서 "탈퇴하기"를 누른 뒤 실제 탈퇴를 진행한다.
    // 재인증 안내는 설문 바텀시트의 배지가 이미 보여줬으므로 여기서 다시 확인창을 띄우지 않는다.
    private void proceedWithdrawal(String[] reasons) {
        // 애플리케이션 컨텍스트로 넘긴다. 탈퇴는 성공하면 이 화면이 사라지므로,
        // 콜백 시점에 Fragment 가 이미 detach 됐더라도 로그인 화면으로 넘어가야 한다.
        SettingFirebase settingFirebase = new SettingFirebase(requireContext(),
                new SettingFirebase.OnUnregisterListener() {
                    @Override
                    public void onSuccess() {
                        // SettingFirebase 가 메인 스레드로 올려서 호출해준다.
                        goToLogin();
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        if (isAdded()) {
                            PopupUtil.show(getContext(), "탈퇴 실패: " + errorMsg);
                        }
                    }
                });
        settingFirebase.performUnregister(reasons);
    }

    private void goToLogin() {
        Context context = appContext != null ? appContext : getContext();
        if (context == null) return;

        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);

        if (getActivity() != null) getActivity().finish();
    }
}
