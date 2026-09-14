package com.forevermemory.vocaapp.util;

import android.content.Context;
import android.content.res.Resources;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.forevermemory.vocaapp.R;

/**
 * 다이얼로그/바텀시트가 떠 있는 동안에도 가려지지 않도록 화면 상단에 띄우는 안내 배지.
 * Material Snackbar는 자신이 그려지는 창(다이얼로그) 안에 갇혀서 팝업의 입력창·버튼과
 * 겹칠 수 있어, 창에 구애받지 않고 항상 위에 뜨는 Toast를 화면 상단에 고정해 대신 쓴다.
 */
public final class SnackbarUtil {

    private SnackbarUtil() {
    }

    @SuppressWarnings("deprecation")
    public static void show(@Nullable Context context, CharSequence message) {
        if (context == null) return;

        Resources res = context.getResources();

        TextView text = new TextView(context);
        text.setText(message);
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(13);
        text.setGravity(Gravity.CENTER);
        int hPad = dp(res, 20);
        int vPad = dp(res, 8);
        text.setPadding(hPad, vPad, hPad, vPad);
        text.setBackgroundResource(R.drawable.snackbar_rounded_bg);

        Toast toast = new Toast(context.getApplicationContext());
        toast.setView(text);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(res, 64));
        toast.show();
    }

    private static int dp(Resources res, int value) {
        return (int) (value * res.getDisplayMetrics().density);
    }
}
