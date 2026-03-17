package com.example.vocaapp;

import com.example.vocaapp.QuizAndGame.QuizAndGameFragment;
import com.example.vocaapp.Settting.SettingFragment;

import android.widget.Toast; //토스트 메세지 출력용
import android.util.Log; // 로그 출력용

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.vocaapp.VocabularyBookList.VocabularyBookListFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNavigationView;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d("FCM_PERMISSION", "알림 권한이 허용되었습니다");
                } else {
                    Log.e("FCM_PERMISSION", "알림 권한이 거부되었습니다. 알림을 받을 수 없어요 ");
                    Toast.makeText(this, "설정에서 알림 권한을 허용해야 복습 알림을 받을 수 있습니다.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        askNotificationPermission();

        bottomNavigationView = findViewById(R.id.bottom_navigation);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new VocabularyBookListFragment())
                .commit();

        // 하단 네비게시연 선택에 따른 화면 이동
        bottomNavigationView.setOnItemSelectedListener(item -> {

            // 기본 화면을 단어장 화면으로
            Fragment selectedFragment = new VocabularyBookListFragment();

            int id = item.getItemId();

            if (id == R.id.vocabularylist) {
                selectedFragment = new VocabularyBookListFragment();
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

}