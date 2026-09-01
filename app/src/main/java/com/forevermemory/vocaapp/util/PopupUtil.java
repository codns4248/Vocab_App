package com.forevermemory.vocaapp.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 기존 Toast 를 대체하는 공용 안내 팝업.
 * Toast.makeText(ctx, msg, len).show() 를 PopupUtil.show(ctx, msg) 로 바꿔 쓴다.
 *
 * 다이얼로그는 Toast 와 달리 살아있는 Activity 컨텍스트가 필요하고,
 * 화면이 닫히는 도중 호출되면 예외가 날 수 있어 방어적으로 처리한다.
 */
public final class PopupUtil {

    private PopupUtil() {
    }

    public static void show(@Nullable Context context, @Nullable CharSequence message) {
        show(context, "알림", message, null);
    }

    public static void show(@Nullable Context context, @Nullable CharSequence title,
                            @Nullable CharSequence message) {
        show(context, title, message, null);
    }

    /**
     * 확인을 누르거나 팝업이 닫히면 onDismiss 를 실행한다.
     * (예전에 Toast 를 띄운 뒤 곧바로 finish() 하던 자리를 대체할 때 사용)
     * 팝업을 띄울 수 없는 상황이면 onDismiss 를 즉시 실행한다.
     */
    public static void show(@Nullable Context context, @Nullable CharSequence message,
                            @Nullable Runnable onDismiss) {
        show(context, "알림", message, onDismiss);
    }

    public static void show(@Nullable Context context, @Nullable CharSequence title,
                            @Nullable CharSequence message, @Nullable Runnable onDismiss) {
        Activity activity = asActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            if (onDismiss != null) onDismiss.run();
            return;
        }

        try {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("확인", null);
            if (onDismiss != null) {
                builder.setOnDismissListener(d -> onDismiss.run());
            }
            builder.show();
        } catch (Exception e) {
            // 창 토큰이 이미 사라진 경우 등 - 안내 팝업이므로 조용히 무시한다.
            if (onDismiss != null) onDismiss.run();
        }
    }

    @Nullable
    private static Activity asActivity(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
