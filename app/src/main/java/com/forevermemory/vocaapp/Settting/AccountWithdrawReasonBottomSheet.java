package com.forevermemory.vocaapp.Settting;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.forevermemory.vocaapp.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 회원탈퇴 플로우의 마지막 단계. 기존 표준 AlertDialog(showUnregisterDialog) 대신
 * 탈퇴 이유를 체크하는 설문형 바텀시트로, {@link AccountRetentionBottomSheet} 다음에 이어진다.
 *
 * 이유를 하나도 선택하지 않으면 "탈퇴하기" 버튼은 비활성 상태(클릭 불가)로 남고,
 * 하나 이상 선택하면 활성화되어 setFragmentResult 로 호스트(SettingFragment)에 결과를 넘긴다.
 * "기타" 를 고르면 같은 박스 안에서 자유 입력창이 펼쳐지고, 적은 내용은
 * 결과에 "기타: {내용}" 형태로 담긴다(비워도 됨).
 * 구조·스타일·통신 패턴은 AccountRetentionBottomSheet 를 그대로 따른다.
 */
public class AccountWithdrawReasonBottomSheet extends BottomSheetDialogFragment {

    public static final String REQUEST_KEY = "accountWithdrawReasonRequestKey";
    public static final String RESULT_PROCEED = "proceed";
    public static final String RESULT_REASONS = "reasons";

    private static final String OTHER_REASON = "기타";

    // 순서 고정. 화면에 보이는 문구가 그대로 결과(reasons)로 넘어간다. 마지막 항목은 "기타".
    private static final String[] REASONS = {
            "사용하는 빈도가 낮아요",
            "원하는 기능이 없어요",
            "오류(버그)가 자주 발생해요",
            "다른 학습 앱으로 옮겨가요",
            "목표한 학습을 다 마쳤어요",
            OTHER_REASON,
    };

    private static final int LABEL_COLOR_SELECTED = 0xFF1F2024;
    private static final int LABEL_COLOR_UNSELECTED = 0xFF3A3B3F;

    // 선택 순서를 유지하기 위해 LinkedHashSet.
    private final Set<String> selectedReasons = new LinkedHashSet<>();
    private MaterialButton btnWithdraw;
    private EditText otherInput;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_withdraw_reason, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        // "기타" 입력창이 키보드 위로 올라오도록 (기존 다른 바텀시트와 동일한 설정).
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView btnClose = view.findViewById(R.id.btnClose);
        TextView btnCancel = view.findViewById(R.id.btnCancel);
        btnWithdraw = view.findViewById(R.id.btnWithdraw);
        LinearLayout reasonContainer = view.findViewById(R.id.reasonContainer);

        // 닫기 / 취소 는 탈퇴 진행 없이 시트만 닫는다 (proceed 결과 없음).
        btnClose.setOnClickListener(v -> dismiss());
        btnCancel.setOnClickListener(v -> dismiss());

        buildReasonRows(reasonContainer);
        bindReauthNotice(view);
        updateWithdrawButton();   // 처음엔 선택이 없으므로 비활성

        btnWithdraw.setOnClickListener(v -> {
            // isEnabled=false 면 클릭 자체가 안 들어오지만, 방어적으로 한 번 더 확인
            if (selectedReasons.isEmpty()) return;

            hideKeyboard();
            Bundle result = new Bundle();
            result.putBoolean(RESULT_PROCEED, true);
            result.putStringArray(RESULT_REASONS, buildResultReasons());
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
            dismiss();
        });
    }

    private void buildReasonRows(@NonNull LinearLayout container) {
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        int gap = Math.round(getResources().getDisplayMetrics().density * 10);

        for (int i = 0; i < REASONS.length; i++) {
            final String reason = REASONS[i];
            boolean isOther = OTHER_REASON.equals(reason);

            View row = inflater.inflate(
                    isOther ? R.layout.item_withdraw_reason_other : R.layout.item_withdraw_reason,
                    container, false);

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
            lp.topMargin = (i == 0) ? 0 : gap;   // row 간 10dp gap
            row.setLayoutParams(lp);

            TextView label = row.findViewById(R.id.reasonLabel);
            ImageView check = row.findViewById(R.id.reasonCheck);

            if (isOther) {
                otherInput = row.findViewById(R.id.otherInput);
                View header = row.findViewById(R.id.otherHeader);
                header.setOnClickListener(v -> toggleReason(reason, row, label, check));
            } else {
                label.setText(reason);
                row.setOnClickListener(v -> toggleReason(reason, row, label, check));
            }

            container.addView(row);
        }
    }

    private void toggleReason(String reason, View row, TextView label, ImageView check) {
        boolean nowSelected = !selectedReasons.contains(reason);
        if (nowSelected) {
            selectedReasons.add(reason);
        } else {
            selectedReasons.remove(reason);
        }

        // 배경(withdraw_reason_row_bg) 과 체크박스 배경(withdraw_reason_check_bg) 이
        // state_selected 로 반응한다. setSelected 는 자식까지 전파된다.
        row.setSelected(nowSelected);
        check.setVisibility(nowSelected ? View.VISIBLE : View.GONE);
        label.setTextColor(nowSelected ? LABEL_COLOR_SELECTED : LABEL_COLOR_UNSELECTED);
        label.setTypeface(null, nowSelected ? Typeface.BOLD : Typeface.NORMAL);

        if (OTHER_REASON.equals(reason) && otherInput != null) {
            otherInput.setVisibility(nowSelected ? View.VISIBLE : View.GONE);
            if (nowSelected) {
                otherInput.post(this::focusOtherInput);
            } else {
                otherInput.clearFocus();
                hideKeyboard();
            }
        }

        updateWithdrawButton();
    }

    private void focusOtherInput() {
        if (otherInput == null || !isAdded()) return;
        otherInput.requestFocus();
        otherInput.setSelection(otherInput.getText().length());
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(otherInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        if (otherInput == null) return;
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(otherInput.getWindowToken(), 0);
        }
    }

    private void updateWithdrawButton() {
        // 색(다크/회색)은 @color 셀렉터(state_enabled)가 처리하므로 enabled 만 토글하면 된다.
        // isEnabled=false 이면 실제 터치도 막힌다.
        btnWithdraw.setEnabled(!selectedReasons.isEmpty());
    }

    // "기타" 는 입력한 사유가 있으면 "기타: {내용}" 으로, 없으면 그대로 "기타" 로 넘긴다.
    private String[] buildResultReasons() {
        List<String> out = new ArrayList<>();
        for (String reason : selectedReasons) {
            if (OTHER_REASON.equals(reason)) {
                String extra = otherInput == null ? "" : otherInput.getText().toString().trim();
                out.add(extra.isEmpty() ? OTHER_REASON : OTHER_REASON + ": " + extra);
            } else {
                out.add(reason);
            }
        }
        return out.toArray(new String[0]);
    }

    /**
     * 재인증 방식이 제공자마다 달라 안내 배지 문구를 나눈다.
     * (기존 SettingFragment.showUnregisterDialog() 의 분기 문구를 이식)
     */
    private void bindReauthNotice(@NonNull View view) {
        TextView notice = view.findViewById(R.id.tvReauthNotice);
        boolean isKakao = SettingFirebase.isKakaoAccount(FirebaseAuth.getInstance().getCurrentUser());
        notice.setText(isKakao
                ? "탈퇴를 위해 카카오 로그인을 다시 진행해야 해요. 탈퇴 시 카카오 계정과의 연결도 해제됩니다."
                : "탈퇴를 위해 구글 로그인을 다시 진행해야 해요.");
    }
}
