package com.example.vocaapp;

import com.example.vocaapp.QuizAndGame.QuizAndGameFragment;
import com.example.vocaapp.Settting.SettingFragment;

import android.app.AlertDialog;
import android.widget.Toast; //토스트 메세지 출력용
import android.util.Log; // 로그 출력용

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;

import com.example.vocaapp.VocabularyBookList.VocabularyBookListFragment;
import com.example.vocaapp.VocabularyList.VocabularyFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null){
            String uid = user.getUid();
            checkRollbackOnEntry(uid);
        }

        askNotificationPermission();

        bottomNavigationView = findViewById(R.id.bottom_navigation);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new VocabularyFragment())
                .commit();

        // 하단 네비게시연 선택에 따른 화면 이동
        bottomNavigationView.setOnItemSelectedListener(item -> {

            // 기본 화면을 단어 화면으로
            Fragment selectedFragment = new VocabularyFragment();

            int id = item.getItemId();

            if (id == R.id.vocabularylist) {
                selectedFragment = new VocabularyFragment();
            } else if (id == R.id.quizandgame) {
                selectedFragment = new QuizAndGameFragment();
            } else if (id == R.id.setting) {
                selectedFragment = new SettingFragment();
            }

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();

            return true;
        });
    }

    private void askNotificationPermission() {
        // 안드로이드 13 (TIRAMISU, API 33) 이상일 때만 작동
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                // 이미 권한이 허용되어 있는 경우
                Log.d("FCM_PERMISSION", "이미 알림 권한이 허용되어 있습니다.");
            } else {
                // 권한이 없다면 시스템에 팝업 띄워달라고 요청
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d("FCM_PERMISSION", "알림 권한이 허용되었습니다");
                } else {
                    Log.e("FCM_PERMISSION", "알림 권한이 거부되었습니다. 알림을 받을 수 없어요 ");
                    Toast.makeText(this, "설정에서 알림 권한을 허용해야 복습 알림을 받을 수 있습니다.", Toast.LENGTH_LONG).show();
                }
            });

    private void checkRollbackOnEntry(String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 사용자의 전체 단어장을 뒤져서 rollbackState가 true인 게 있는지 확인
        db.collection("users").document(uid).collection("vocabularies")
                .whereEqualTo("rollbackState", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            // 롤백된 단어장이 발견됨!
                            String title = doc.getString("title");
                            showRollbackDialog(title, doc.getReference());
                        }
                    }
                });
    }

    private void showRollbackDialog(String title, DocumentReference docRef) {
        new AlertDialog.Builder(this)
                .setTitle("복습 시간 초과 ⚠️")
                .setMessage("'" + title + "' 단어장의 복습 시간이 지나 진도가 초기화되었습니다.")
                .setPositiveButton("확인", (dialog, which) -> {
                    // [중요] 확인을 눌렀으면 다시 false로 바꿔줘야 다음에 또 안 뜹니다.
                    docRef.update("rollbackState", false);
                })
                .setCancelable(false)
                .show();
    }
}