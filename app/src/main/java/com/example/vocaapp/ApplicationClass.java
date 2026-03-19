package com.example.vocaapp;

import android.app.Application;

import com.onesignal.Continue;
import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;

public class ApplicationClass extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Enable verbose logging to debug issues (remove in production)
        OneSignal.getDebug().setLogLevel(LogLevel.VERBOSE);

        // Replace with your 36-character App ID from Dashboard > Settings > Keys & IDs
        OneSignal.initWithContext(this, "10e974dc-c7c8-4499-b744-0c1fdae7ed60");

        // Prompt user for push notification permission
        // In production, consider using an in-app message instead for better opt-in rates
        OneSignal.getNotifications().requestPermission(false, Continue.none());
    }
}
