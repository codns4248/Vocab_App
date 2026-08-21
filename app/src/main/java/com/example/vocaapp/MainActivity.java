package com.example.vocaapp;

import com.example.vocaapp.QuizAndGame.QuizAndGameFragment;
import com.example.vocaapp.Settting.MarketingPushPrefs;
import com.example.vocaapp.Settting.SettingFragment;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;

import com.example.vocaapp.VocabularyBookList.VocabularyBookListFragment;
import com.example.vocaapp.VocabularyList.VocabularyFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;


public class MainActivity extends AppCompatActivity {

    private static final int COLOR_SELECTED_BG   = 0xFF3B5BDB;
    private static final int COLOR_UNSELECTED_BG  = 0x003B5BDB;
    private static final int COLOR_ICON_SELECTED  = 0xFFFFFFFF;
    private static final int COLOR_ICON_UNSELECTED = 0xFF9E9E9E;
    private static final int ANIM_DURATION_MS = 175;

    private int selectedTabIndex = 0;
    private LinearLayout[] tabs;
    private ImageView[] tabIcons;
    private TextView[] tabLabels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            checkRollbackOnEntry(user.getUid());
        }

        initCustomNav();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new VocabularyFragment())
                .commit();

        // 첫 진입에는 마케팅 수신 동의만 묻는다.
        // 복습 알림 권한은 알림을 실제로 예약하는 '학습시작' 시점에 요청한다.
        // 신규 가입자는 환영 팝업이 먼저 뜨므로, 그게 닫힌 다음에 물어본다.
        if (getIntent().getBooleanExtra("isNewUser", false)) {
            showWelcomePointDialog(this::askMarketingConsentIfNeeded);
        } else if (user != null) {
            askMarketingConsentIfNeeded();
        }
    }

    private void initCustomNav() {
        tabs = new LinearLayout[]{
            findViewById(R.id.tab_vocabulary),
            findViewById(R.id.tab_quiz),
            findViewById(R.id.tab_setting)
        };
        tabIcons = new ImageView[]{
            findViewById(R.id.tab_vocabulary_icon),
            findViewById(R.id.tab_quiz_icon),
            findViewById(R.id.tab_setting_icon)
        };
        tabLabels = new TextView[]{
            findViewById(R.id.tab_vocabulary_label),
            findViewById(R.id.tab_quiz_label),
            findViewById(R.id.tab_setting_label)
        };

        float cornerRadius = getResources().getDisplayMetrics().density * 24;
        for (LinearLayout tab : tabs) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(cornerRadius);
            bg.setColor(COLOR_UNSELECTED_BG);
            tab.setBackground(bg);
        }

        // 첫 탭 즉시 선택 상태로 초기화 (애니메이션 없이)
        applyTabColorImmediate(0, true);
        applyTabColorImmediate(1, false);
        applyTabColorImmediate(2, false);

        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i].setOnClickListener(v -> onTabSelected(index));
        }
    }

    private void onTabSelected(int index) {
        if (index == selectedTabIndex) return;

        animateTabTransition(selectedTabIndex, false);
        animateTabTransition(index, true);
        selectedTabIndex = index;

        Fragment fragment;
        if (index == 0)      fragment = new VocabularyFragment();
        else if (index == 1) fragment = new QuizAndGameFragment();
        else                 fragment = new SettingFragment();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void animateTabTransition(int index, boolean selecting) {
        GradientDrawable bg = (GradientDrawable) tabs[index].getBackground();

        int fromBg    = selecting ? COLOR_UNSELECTED_BG  : COLOR_SELECTED_BG;
        int toBg      = selecting ? COLOR_SELECTED_BG    : COLOR_UNSELECTED_BG;
        int fromColor = selecting ? COLOR_ICON_UNSELECTED : COLOR_ICON_SELECTED;
        int toColor   = selecting ? COLOR_ICON_SELECTED  : COLOR_ICON_UNSELECTED;

        ValueAnimator bgAnim = ValueAnimator.ofArgb(fromBg, toBg);
        bgAnim.setDuration(ANIM_DURATION_MS);
        bgAnim.addUpdateListener(a -> bg.setColor((int) a.getAnimatedValue()));
        bgAnim.start();

        ValueAnimator colorAnim = ValueAnimator.ofArgb(fromColor, toColor);
        colorAnim.setDuration(ANIM_DURATION_MS);
        colorAnim.addUpdateListener(a -> {
            int color = (int) a.getAnimatedValue();
            ImageViewCompat.setImageTintList(tabIcons[index], ColorStateList.valueOf(color));
            tabLabels[index].setTextColor(color);
        });
        colorAnim.start();
    }

    private void applyTabColorImmediate(int index, boolean selected) {
        GradientDrawable bg = (GradientDrawable) tabs[index].getBackground();
        bg.setColor(selected ? COLOR_SELECTED_BG : COLOR_UNSELECTED_BG);
        int color = selected ? COLOR_ICON_SELECTED : COLOR_ICON_UNSELECTED;
        ImageViewCompat.setImageTintList(tabIcons[index], ColorStateList.valueOf(color));
        tabLabels[index].setTextColor(color);
    }

    private void showWelcomePointDialog(Runnable onDismissed) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_welcome_point, null);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.welcomeConfirmBtn).setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            if (onDismissed != null) onDismissed.run();
        });
        dialog.show();
    }

    // 마케팅 수신 동의를 한 번만 묻는다. 광고성 정보라 기본값은 '받지 않음'이다.
    private void askMarketingConsentIfNeeded() {
        if (MarketingPushPrefs.hasAsked(this)) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_marketing_consent, null);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btn_marketing_allow).setOnClickListener(v -> {
            MarketingPushPrefs.setEnabled(this, true);
            dialog.dismiss();
            Toast.makeText(this, "마케팅 정보 수신에 동의했습니다.", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btn_marketing_deny).setOnClickListener(v -> {
            MarketingPushPrefs.setEnabled(this, false);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void checkRollbackOnEntry(String uid) {
        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vocabularies")
                .whereEqualTo("rollbackState", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            // 롤백된 단어장이 발견됨!
                            String title = doc.getString("title");
                            Long stampCountLong = doc.getLong("stampCount");
                            int stampCount = stampCountLong != null ? stampCountLong.intValue() : 0;
                            showRollbackDialog(title, stampCount, doc.getReference());
                        }
                    }
                });
    }

    private void showRollbackDialog(String title, int stampCount, DocumentReference docRef) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_rollback, null);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView bookTitleView = dialogView.findViewById(R.id.tv_rollback_book_title);
        bookTitleView.setText(title);

        TextView stageView = dialogView.findViewById(R.id.tv_rollback_stage);
        stageView.setText(stampCount + "단계로 롤백되었습니다");

        Button confirmBtn = dialogView.findViewById(R.id.btn_rollback_confirm);
        confirmBtn.setOnClickListener(v -> {
            docRef.update("rollbackState", false);
            dialog.dismiss();
        });

        dialog.show();
    }
}
