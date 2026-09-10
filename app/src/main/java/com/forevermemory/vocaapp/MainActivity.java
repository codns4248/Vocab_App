package com.forevermemory.vocaapp;

import com.forevermemory.vocaapp.Onboarding.TutorialActivity;
import com.forevermemory.vocaapp.QuizAndGame.QuizAndGameFragment;
import com.forevermemory.vocaapp.Settting.MarketingPushPrefs;
import com.forevermemory.vocaapp.Settting.SettingFragment;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;

import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;

import com.forevermemory.vocaapp.VocabularyBookList.VocabularyBookListFragment;
import com.forevermemory.vocaapp.VocabularyBookList.VocabularyCounterBackfill;
import com.forevermemory.vocaapp.VocabularyList.VocabularyFragment;
import com.forevermemory.vocaapp.Test.StudyManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.forevermemory.vocaapp.util.PopupUtil;

import java.util.HashSet;
import java.util.Set;


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
    private ListenerRegistration rollbackListener;
    private final Set<String> visibleRollbackDialogs = new HashSet<>();

    // 신규 가입 시 웰컴(튜토리얼) 화면을 먼저 띄우고, 그 화면을 실제로 닫았을 때만
    // 포인트 지급 팝업을 이어서 띄운다.
    // RESULT_OK 는 TutorialActivity 가 떠서 닫힐 때만 준다. 화면이 뜨기 전 시스템이
    // 곧바로 돌려주는 RESULT_CANCELED 에는 반응하지 않는다(팝업이 먼저 뜨던 원인).
    private final ActivityResultLauncher<Intent> welcomeTutorialLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    showWelcomePointDialog(this::askMarketingConsentIfNeeded);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            StudyManager.getInstance().updateFCMToken(user.getUid());
            VocabularyCounterBackfill.runIfNeeded(this, user.getUid());
        }

        initCustomNav();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new VocabularyFragment())
                .commit();

        // 신규 가입: 웰컴(튜토리얼) 화면을 먼저 보여주고, 그 화면을 닫으면
        //           포인트 지급 팝업 → 마케팅 수신 동의 순으로 이어진다.
        // 기존 유저: 마케팅 수신 동의만 (아직 안 물어봤다면) 묻는다.
        // 복습 알림 권한은 알림을 실제로 예약하는 '학습시작' 시점에 요청한다.
        if (savedInstanceState == null && getIntent().getBooleanExtra("isNewUser", false)) {
            welcomeTutorialLauncher.launch(new Intent(this, TutorialActivity.class));
        } else if (user != null) {
            askMarketingConsentIfNeeded();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            startRollbackListener(user.getUid());
        }
    }

    @Override
    protected void onStop() {
        if (rollbackListener != null) {
            rollbackListener.remove();
            rollbackListener = null;
        }
        super.onStop();
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
            PopupUtil.show(this, "마케팅 정보 수신에 동의했습니다.");
        });

        dialogView.findViewById(R.id.btn_marketing_deny).setOnClickListener(v -> {
            MarketingPushPrefs.setEnabled(this, false);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void startRollbackListener(String uid) {
        if (rollbackListener != null) return;

        rollbackListener = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vocabularies")
                .whereEqualTo("rollbackState", true)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e("Rollback", "롤백 상태 실시간 감지 실패", error);
                        return;
                    }
                    if (snapshots == null) return;

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        if (!visibleRollbackDialogs.add(doc.getId())) {
                            continue;
                        }

                        String title = doc.getString("title");
                        Long stampCountLong = doc.getLong("stampCount");
                        int stampCount = stampCountLong != null ? stampCountLong.intValue() : 0;
                        showRollbackDialog(title, stampCount, doc.getReference(), doc.getId());
                    }
                });
    }

    private void showRollbackDialog(String title, int stampCount,
                                    DocumentReference docRef, String docId) {
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
            confirmBtn.setEnabled(false);
            docRef.update("rollbackState", false)
                    .addOnSuccessListener(unused -> {
                        visibleRollbackDialogs.remove(docId);
                        dialog.dismiss();
                    })
                    .addOnFailureListener(error -> {
                        confirmBtn.setEnabled(true);
                        Log.e("Rollback", "롤백 확인 상태 저장 실패", error);
                        PopupUtil.show(this, "확인 처리에 실패했습니다. 다시 시도해주세요.");
                    });
        });

        dialog.show();
    }
}
