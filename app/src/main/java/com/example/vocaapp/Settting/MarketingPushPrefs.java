package com.example.vocaapp.Settting;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.onesignal.OneSignal;

import java.util.HashMap;
import java.util.Map;

/**
 * 마케팅 알림(OneSignal) 수신 여부.
 *
 * 복습 알림은 Cloud Functions가 Firestore에 저장된 fcmToken으로 직접 보내고,
 * 마케팅 알림은 OneSignal이 자체 구독으로 보낸다. 경로가 달라서 따로 끌 수 있다.
 * OneSignal 구독을 optOut 해도 앱의 FCM 토큰은 그대로라 복습 알림은 계속 온다.
 *
 * 광고성 정보는 사전 동의를 받아야 하므로 기본값은 '받지 않음'이고,
 * 첫 진입 때 물어본 뒤에만 켜진다.
 */
public class MarketingPushPrefs {

    private static final String TAG = "MarketingPushPrefs";
    private static final String PREFS_NAME = "notification_prefs";
    private static final String KEY_ENABLED = "marketing_push_enabled";
    private static final String KEY_ASKED = "marketing_push_asked";

    private MarketingPushPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 수신 여부. 동의 전에는 받지 않는다. */
    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    /** 동의 여부를 물어본 적이 있는지. 첫 진입 팝업을 한 번만 띄우기 위한 값. */
    public static boolean hasAsked(Context context) {
        return prefs(context).getBoolean(KEY_ASKED, false);
    }

    /** 설정을 저장하고 OneSignal 구독에 바로 반영한다. */
    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_ASKED, true)
                .apply();
        apply(enabled);
        recordConsent(enabled);
    }

    /**
     * 앱이 시작될 때 저장된 설정을 OneSignal에 다시 적용한다.
     * OneSignal도 상태를 들고 있지만, 재설치나 로그아웃으로 초기화될 수 있어
     * 우리 쪽 설정을 기준으로 맞춰준다.
     */
    public static void applySaved(Context context) {
        apply(isEnabled(context));
    }

    private static void apply(boolean enabled) {
        try {
            if (enabled) {
                OneSignal.getUser().getPushSubscription().optIn();
            } else {
                OneSignal.getUser().getPushSubscription().optOut();
            }
        } catch (Exception e) {
            // 초기화 전이거나 SDK 내부 오류. 설정값은 저장돼 있으므로 다음 실행에 다시 적용된다.
            Log.e(TAG, "OneSignal 구독 상태 변경 실패: " + e.getMessage());
        }
    }

    /**
     * 동의/철회 사실과 시각을 서버에 남긴다.
     * 광고성 정보 수신은 동의 이력을 보관해야 하므로 기기 설정만으로는 부족하다.
     */
    private static void recordConsent(boolean enabled) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("marketingConsent", enabled);
        data.put("marketingConsentAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(e ->
                        Log.e(TAG, "마케팅 동의 이력 저장 실패: " + e.getMessage()));
    }
}
