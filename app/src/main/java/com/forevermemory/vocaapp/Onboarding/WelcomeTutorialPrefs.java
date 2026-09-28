package com.forevermemory.vocaapp.Onboarding;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 신규 가입 직후 튜토리얼을 띄워야 하는지 여부.
 *
 * 로그인할 때마다 신규 가입 여부로 덮어쓰므로, 탈퇴 후 재가입하거나 같은 기기에서
 * 다른 계정으로 새로 가입해도 튜토리얼이 다시 뜬다. 메인화면이 튜토리얼을 띄우는
 * 순간 해제해서, 화면 회전 등으로 메인화면이 다시 만들어져도 중복으로 뜨지 않는다.
 */
public class WelcomeTutorialPrefs {

    private static final String PREFS_NAME = "onboarding_prefs";
    private static final String KEY_TUTORIAL_PENDING = "welcome_tutorial_pending";

    private WelcomeTutorialPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isPending(Context context) {
        return prefs(context).getBoolean(KEY_TUTORIAL_PENDING, false);
    }

    public static void setPending(Context context, boolean pending) {
        prefs(context).edit().putBoolean(KEY_TUTORIAL_PENDING, pending).apply();
    }
}
