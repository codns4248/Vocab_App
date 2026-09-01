package com.forevermemory.vocaapp.Settting;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.forevermemory.vocaapp.R;
import com.forevermemory.vocaapp.VocabularyBookList.VocabularyBookFirestore;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.TimeUnit;

/**
 * "회원탈퇴" 를 눌렀을 때 기존 탈퇴 확인 다이얼로그 대신 먼저 뜨는 방지용 바텀시트.
 * 사용자의 학습 로그(만든 단어장 수, 외운 단어 수, 함께한 일수)를 강조해 보여주고,
 * "그래도 탈퇴할게요" 를 누르면 setFragmentResult 로 호스트에 알려 기존 플로우를 잇는다.
 */
public class AccountRetentionBottomSheet extends BottomSheetDialogFragment {

    public static final String REQUEST_KEY = "accountRetentionRequestKey";
    public static final String RESULT_PROCEED = "proceedWithdrawal";

    private static final int HIGHLIGHT_COLOR = 0xFFDC2626;

    private TextView tvBookCount;
    private TextView tvMemorizedCount;
    private TextView tvEmphasis;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_account_retention, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView btnClose = view.findViewById(R.id.btnClose);
        MaterialButton btnStay = view.findViewById(R.id.btnStay);
        MaterialButton btnWithdraw = view.findViewById(R.id.btnWithdraw);
        tvBookCount = view.findViewById(R.id.tvBookCount);
        tvMemorizedCount = view.findViewById(R.id.tvMemorizedCount);
        tvEmphasis = view.findViewById(R.id.tvEmphasis);

        btnClose.setOnClickListener(v -> dismiss());
        btnStay.setOnClickListener(v -> dismiss());
        btnWithdraw.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putBoolean(RESULT_PROCEED, true);
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
            dismiss();
        });

        bindDaysTogether(view);
        loadStudySummary();
    }

    // 가입 후 함께한 일수. FirebaseUser 메타데이터라 별도 조회가 필요 없다.
    private void bindDaysTogether(@NonNull View view) {
        View daysContainer = view.findViewById(R.id.daysContainer);
        View daysDivider = view.findViewById(R.id.daysDivider);
        TextView tvDays = view.findViewById(R.id.tvDays);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        long createdAt = (user != null && user.getMetadata() != null)
                ? user.getMetadata().getCreationTimestamp() : 0L;

        if (createdAt <= 0L) {
            daysContainer.setVisibility(View.GONE);
            daysDivider.setVisibility(View.GONE);
            return;
        }

        long days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - createdAt) + 1;
        if (days < 1) days = 1;
        tvDays.setText(String.valueOf(days));
    }

    private void loadStudySummary() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : null;

        VocabularyBookFirestore.getWithdrawalSummary(uid,
                new VocabularyBookFirestore.WithdrawalSummaryCallback() {
                    @Override
                    public void onResult(int bookCount, int memorizedWordCount) {
                        if (!isAdded()) return;
                        tvBookCount.setText(String.valueOf(bookCount));
                        tvMemorizedCount.setText(String.valueOf(memorizedWordCount));
                        tvEmphasis.setText(buildEmphasisCopy(bookCount, memorizedWordCount));
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (!isAdded()) return;
                        // 수치를 못 불러와도 흐름은 막지 않는다. 일반 문구로 대체.
                        tvEmphasis.setText("지금까지의 학습 기록이 모두 사라집니다");
                    }
                });
    }

    // 값이 0인 항목은 문구가 어색하지 않도록 예외 처리한다.
    private CharSequence buildEmphasisCopy(int books, int memorized) {
        if (books <= 0 && memorized <= 0) {
            return "지금까지의 학습 기록이 모두 사라집니다";
        }
        if (memorized <= 0) {
            SpannableStringBuilder sb = new SpannableStringBuilder("단어장 ");
            appendHighlighted(sb, books + "개");
            sb.append("와 지금까지의 학습 기록이 전부 사라집니다");
            return sb;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder("단어장 ");
        appendHighlighted(sb, books + "개");
        sb.append(", 외운 단어 ");
        appendHighlighted(sb, memorized + "개");
        sb.append(" — 전부 사라집니다");
        return sb;
    }

    private void appendHighlighted(SpannableStringBuilder sb, String text) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new ForegroundColorSpan(HIGHLIGHT_COLOR), start, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
