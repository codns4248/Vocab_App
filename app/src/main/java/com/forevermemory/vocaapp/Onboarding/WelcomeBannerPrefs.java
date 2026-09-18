package com.forevermemory.vocaapp.Onboarding;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 메인화면 웰컴 배너를 이미 봤는지 여부.
 *
 * 최초 로그인 직후에만 배너를 보여주고, 탭해서 튜토리얼로 넘어가든 닫기 버튼으로
 * 직접 닫든 한 번 처리되면 다시는 뜨지 않도록 기기에 저장해둔다.
 */
public class WelcomeBannerPrefs {

    private static final String PREFS_NAME = "onboarding_prefs";
    private static final String KEY_BANNER_SEEN = "welcome_banner_seen";

    private WelcomeBannerPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean hasSeenBanner(Context context) {
        return prefs(context).getBoolean(KEY_BANNER_SEEN, false);
    }

    public static void markBannerSeen(Context context) {
        prefs(context).edit().putBoolean(KEY_BANNER_SEEN, true).apply();
    }
}
