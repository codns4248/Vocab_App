package com.example.vocaapp;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.example.vocaapp.Settting.MarketingPushPrefs;
import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;

public class ApplicationClass extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Firebase 초기화 (App Check보다 먼저)
        FirebaseApp.initializeApp(this);

        FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance();
        if (com.example.vocaapp.BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance());
        } else {
            appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance());
        }

        // Enable verbose logging to debug issues (remove in production)
        OneSignal.getDebug().setLogLevel(LogLevel.VERBOSE);

        // Replace with your 36-character App ID from Dashboard > Settings > Keys & IDs
        OneSignal.initWithContext(this, "10e974dc-c7c8-4499-b744-0c1fdae7ed60");

        // 알림 권한은 여기서 요청하지 않는다. 로그인 화면에서 바로 권한 창이 떠서
        // 앱이 뭘 하는지 보기도 전에 거절당한다. MainActivity 진입 후에 요청한다.
        // OneSignal은 앱이 포커스를 받을 때 권한 상태를 다시 확인하므로
        // 표준 권한 요청으로 허용되면 그대로 반영된다.

        // 설정 화면에서 끈 마케팅 수신을 앱 시작 시에도 유지한다.
        MarketingPushPrefs.applySaved(this);
    }
}