package com.forevermemory.vocaapp;

import com.forevermemory.vocaapp.Onboarding.TutorialActivity;
import com.forevermemory.vocaapp.Onboarding.WelcomeTutorialPrefs;
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
import com.forevermemory.vocaapp.VocabularyList.VocabularyEntryFragment;
import com.forevermemory.vocaapp.VocabularyList.VocabularyFragment;
import com.forevermemory.vocaapp.Test.StudyManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.forevermemory.vocaapp.util.PopupUtil;
import com.google.android.material.snackbar.Snackbar;

import java.util.HashSet;
import java.util.Set;


public class MainActivity extends AppCompatActivity {

    private static final int COLOR_SELECTED_BG   = 0xFF3B5BDB;
    private static final int COLOR_UNSELECTED_BG  = 0x003B5BDB;
    private static final int COLOR_ICON_SELECTED  = 0xFFFFFFFF;
    private static final int COLOR_ICON_UNSELECTED = 0xFF9E9E9E;
    private static final int ANIM_DURATION_MS = 175;

    private int selectedTabIndex = 0;
    private static final String STATE_SELECTED_TAB = "selectedTab";
    private static final String STATE_VOCABULARY_LIST = "vocabularyListVisible";
    private boolean returnToVocabularyList;
    private LinearLayout[] tabs;
    private ImageView[] tabIcons;
    private TextView[] tabLabels;
    private ListenerRegistration rollbackListener;
    private final Set<String> visibleRollbackDialogs = new HashSet<>();

    // 튜토리얼을 띄운 뒤 그 결과를 아직 받지 못한 상태.
    // 튜토리얼 화면에서 회전하는 등으로 이 화면이 다시 만들어져도 유지해서, 그때 마케팅 동의를
    // 먼저 묻지 않게 한다(결과가 오면 포인트 팝업 → 마케팅 동의 순으로 이어진다).
    private static final String STATE_AWAITING_TUTORIAL = "awaitingWelcomeTutorial";
    private boolean awaitingWelcomeTutorial = false;

    // 포인트 지급 팝업이 떠 있는 상태. 팝업이 떠 있는 채로 회전 등으로 이 화면이 다시 만들어지면
    // 팝업이 사라지므로, 이 값을 이어받아 새 화면에서 팝업을 다시 띄운다.
    private static final String STATE_SHOWING_WELCOME_POINT = "showingWelcomePoint";
    private boolean showingWelcomePoint = false;
    private androidx.appcompat.app.AlertDialog welcomePointDialog;

    // 마케팅 수신 동의 팝업이 이미 떠 있으면 중복으로 띄우지 않는다.
    private androidx.appcompat.app.AlertDialog marketingConsentDialog;

    // 신규 가입 직후 튜토리얼 화면을 열고, 그 화면을 실제로 닫았을 때만
    // 포인트 지급 팝업을 이어서 띄운다.
    // RESULT_OK 는 TutorialActivity 가 떠서 닫힐 때만 준다. 화면이 뜨기 전 시스템이
    // 곧바로 돌려주는 RESULT_CANCELED 에는 반응하지 않는다(팝업이 먼저 뜨던 원인).
    // 이 경우 마케팅 동의는 아직 묻지 않은 상태로 남아 다음 실행 때 묻게 된다.
    private final ActivityResultLauncher<Intent> welcomeTutorialLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                awaitingWelcomeTutorial = false;
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

        if (savedInstanceState != null) {
            selectedTabIndex = savedInstanceState.getInt(STATE_SELECTED_TAB, 0);
            returnToVocabularyList = savedInstanceState.getBoolean(STATE_VOCABULARY_LIST, false);
        }
        initCustomNav();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new VocabularyEntryFragment())
                    .commit();
        }

        // 신규 가입: 튜토리얼을 바로 띄운다. 튜토리얼을 닫으면
        //           포인트 지급 팝업 → 마케팅 수신 동의 순으로 이어진다.
        //           띄우는 즉시 표시를 해제해서, 화면이 다시 만들어져도 중복으로 뜨지 않는다.
        // 기존 유저: 마케팅 수신 동의만 (아직 안 물어봤다면) 묻는다.
        // 복습 알림 권한은 알림을 실제로 예약하는 '학습시작' 시점에 요청한다.
        if (savedInstanceState != null) {
            awaitingWelcomeTutorial = savedInstanceState.getBoolean(STATE_AWAITING_TUTORIAL, false);
            showingWelcomePoint = savedInstanceState.getBoolean(STATE_SHOWING_WELCOME_POINT, false);
        }

        if (WelcomeTutorialPrefs.isPending(this)) {
            WelcomeTutorialPrefs.setPending(this, false);
            awaitingWelcomeTutorial = true;
            welcomeTutorialLauncher.launch(new Intent(this, TutorialActivity.class));
        } else if (showingWelcomePoint) {
            showWelcomePointDialog(this::askMarketingConsentIfNeeded);
        } else if (user != null && !awaitingWelcomeTutorial) {
            askMarketingConsentIfNeeded();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_AWAITING_TUTORIAL, awaitingWelcomeTutorial);
        outState.putBoolean(STATE_SHOWING_WELCOME_POINT, showingWelcomePoint);
        outState.putInt(STATE_SELECTED_TAB, selectedTabIndex);
        rememberVocabularyDestination();
        outState.putBoolean(STATE_VOCABULARY_LIST, returnToVocabularyList);
    }

    @Override
    protected void onDestroy() {
        // 화면이 다시 만들어질 때 떠 있던 포인트 팝업을 정리한다. 닫힘 콜백(마케팅 동의)이
        // 사라지는 화면에서 불리지 않도록 리스너를 먼저 떼고, 팝업은 새 화면에서 다시 띄운다.
        if (welcomePointDialog != null) {
            welcomePointDialog.setOnDismissListener(null);
            welcomePointDialog.dismiss();
            welcomePointDialog = null;
        }
        super.onDestroy();
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

        // 복원된 탭을 애니메이션 없이 선택한다.
        applyTabColorImmediate(0, selectedTabIndex == 0);
        applyTabColorImmediate(1, selectedTabIndex == 1);
        applyTabColorImmediate(2, selectedTabIndex == 2);

        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i].setOnClickListener(v -> onTabSelected(index));
        }
    }

    private void onTabSelected(int index) {
        if (index == selectedTabIndex) return;

        rememberVocabularyDestination();
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (current instanceof VocabularyEntryFragment) {
            ((VocabularyEntryFragment) current).cancelPendingNavigation();
        }

        animateTabTransition(selectedTabIndex, false);
        animateTabTransition(index, true);
        selectedTabIndex = index;

        Fragment fragment;
        if (index == 0)      fragment = returnToVocabularyList
                ? new VocabularyBookListFragment() : new VocabularyEntryFragment();
        else if (index == 1) fragment = new QuizAndGameFragment();
        else                 fragment = new SettingFragment();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void rememberVocabularyDestination() {
        if (selectedTabIndex != 0) return;
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (current instanceof VocabularyBookListFragment) {
            returnToVocabularyList = true;
        } else if (current instanceof VocabularyFragment) {
            returnToVocabularyList = false;
        }
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
        welcomePointDialog = dialog;
        showingWelcomePoint = true;

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.welcomeConfirmBtn).setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            welcomePointDialog = null;
            showingWelcomePoint = false;
            if (onDismissed != null) onDismissed.run();
        });
        dialog.show();
    }

    // 마케팅 수신 동의를 한 번만 묻는다. 광고성 정보라 기본값은 '받지 않음'이다.
    private void askMarketingConsentIfNeeded() {
        if (MarketingPushPrefs.hasAsked(this)) return;
        if (marketingConsentDialog != null && marketingConsentDialog.isShowing()) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_marketing_consent, null);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        marketingConsentDialog = dialog;

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btn_marketing_allow).setOnClickListener(v -> {
            MarketingPushPrefs.setEnabled(this, true);
            dialog.dismiss();
            Snackbar.make(findViewById(R.id.main), "마케팅 정보 수신에 동의했습니다.", Snackbar.LENGTH_SHORT)
                    .setAnchorView(R.id.bottom_nav_container).show();
        });

        dialogView.findViewById(R.id.btn_marketing_deny).setOnClickListener(v -> {
            MarketingPushPrefs.setEnabled(this, false);
            dialog.dismiss();
            Snackbar.make(findViewById(R.id.main), "마케팅 정보 수신을 거부했습니다.", Snackbar.LENGTH_SHORT)
                    .setAnchorView(R.id.bottom_nav_container).show();
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
