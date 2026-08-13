package com.example.vocaapp;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.onesignal.Continue;
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

        // Prompt user for push notification permission
        // In production, consider using an in-app message instead for better opt-in rates
        OneSignal.getNotifications().requestPermission(false, Continue.none());
    }
}