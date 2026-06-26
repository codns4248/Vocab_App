package com.example.vocaapp.QuizAndGame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

public class BarChartView extends View {

    private int pass = 0;
    private int fail = 0;

    private final Paint passPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint failPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint passCountPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint failCountPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BarChartView(Context context) {
        super(context);
        init();
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BarChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        passPaint.setColor(0xFF16A34A);
        failPaint.setColor(0xFFDC2626);

        passCountPaint.setColor(0xFF16A34A);
        passCountPaint.setTextAlign(Paint.Align.CENTER);
        passCountPaint.setTypeface(Typeface.DEFAULT_BOLD);
        passCountPaint.setTextSize(sp(20));

        failCountPaint.setColor(0xFFDC2626);
        failCountPaint.setTextAlign(Paint.Align.CENTER);
        failCountPaint.setTypeface(Typeface.DEFAULT_BOLD);
        failCountPaint.setTextSize(sp(20));

        labelPaint.setColor(0xFF777777);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(sp(13));

        baselinePaint.setColor(0xFFE0E0E0);
        baselinePaint.setStrokeWidth(dp(1));
    }

    private float sp(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                getResources().getDisplayMetrics());
    }

    private float dp(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    public void setValues(int pass, int fail) {
        this.pass = pass;
        this.fail = fail;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int total = pass + fail;

        float labelH = dp(24);
        float countH = dp(28);
        float barBottom = h - labelH - dp(6);
        float maxBarH = h - labelH - countH - dp(12);
        float minBarH = dp(12);
        float barWidth = dp(72);
        float cornerRadius = dp(8);

        float passCx = w * 0.3f;
        float failCx = w * 0.7f;

        float passBarH, failBarH;
        if (total == 0) {
            passBarH = maxBarH * 0.15f;
            failBarH = maxBarH * 0.15f;
        } else {
            passBarH = (pass > 0) ? Math.max(minBarH, maxBarH * (float) pass / total) : 0;
            failBarH = (fail > 0) ? Math.max(minBarH, maxBarH * (float) fail / total) : 0;
        }

        // 베이스라인
        canvas.drawLine(dp(16), barBottom, w - dp(16), barBottom, baselinePaint);

        // 정답 막대
        if (passBarH > 0) {
            RectF passRect = new RectF(
                    passCx - barWidth / 2, barBottom - passBarH,
                    passCx + barWidth / 2, barBottom
            );
            canvas.drawRoundRect(passRect, cornerRadius, cornerRadius, passPaint);
        }

        // 오답 막대
        if (failBarH > 0) {
            RectF failRect = new RectF(
                    failCx - barWidth / 2, barBottom - failBarH,
                    failCx + barWidth / 2, barBottom
            );
            canvas.drawRoundRect(failRect, cornerRadius, cornerRadius, failPaint);
        }

        // 수치 (막대 위)
        float passCountY = barBottom - passBarH - dp(4);
        if (passCountY < countH) passCountY = countH;
        canvas.drawText(String.valueOf(pass), passCx, passCountY, passCountPaint);

        float failCountY = barBottom - failBarH - dp(4);
        if (failCountY < countH) failCountY = countH;
        canvas.drawText(String.valueOf(fail), failCx, failCountY, failCountPaint);

        // 라벨 (막대 아래)
        canvas.drawText("정답", passCx, h - dp(2), labelPaint);
        canvas.drawText("오답", failCx, h - dp(2), labelPaint);
    }
}
