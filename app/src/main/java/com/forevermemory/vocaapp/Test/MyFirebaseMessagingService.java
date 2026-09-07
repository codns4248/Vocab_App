package com.forevermemory.vocaapp.Test;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;
import com.forevermemory.vocaapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String title = "복습 알림";
        String message = "단어를 복습할 시간입니다.";

        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            if (notification.getTitle() != null) title = notification.getTitle();
            if (notification.getBody() != null) message = notification.getBody();
        }

        if (remoteMessage.getData().size() > 0) {
            String dataTitle = remoteMessage.getData().get("title");
            String dataMessage = remoteMessage.getData().get("message");
            if (dataTitle != null && !dataTitle.trim().isEmpty()) title = dataTitle;
            if (dataMessage != null && !dataMessage.trim().isEmpty()) message = dataMessage;
        }

        showNotification(title, message);
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            StudyManager.getInstance().saveFCMToken(user.getUid(), token);
        }
    }

    private void showNotification(String title, String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String channelId = "review_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "복습 알림", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // 아이콘 확인 필요
                .setAutoCancel(true);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
