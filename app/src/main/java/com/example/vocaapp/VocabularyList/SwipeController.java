package com.example.vocaapp.VocabularyList;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class SwipeController extends ItemTouchHelper.SimpleCallback {

    private final ColorDrawable background;
    private final Paint textPaint;
    private final SwipeControllerActions actions;

    public SwipeController(SwipeControllerActions actions) {
        super(0, ItemTouchHelper.LEFT);
        this.actions = actions;
        this.background = new ColorDrawable(Color.parseColor("#F44336"));

        // "삭제" 텍스트용 Paint 미리 설정
        this.textPaint = new Paint();
        this.textPaint.setColor(Color.WHITE);
        this.textPaint.setAntiAlias(true);
        this.textPaint.setTextSize(50);
        this.textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION && actions != null) {
            actions.onRightClicked(position);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        View itemView = viewHolder.itemView;

        if (dX < 0) {
            // 빨간 배경 (스와이프된 만큼만 보이도록)
            background.setBounds(
                    itemView.getRight() + (int) dX, itemView.getTop(),
                    itemView.getRight(), itemView.getBottom()
            );
            background.draw(c);

            // "삭제" 텍스트 - 빨간 영역의 중앙에 표시
            String text = "삭제";
            float textCenterX = itemView.getRight() + (dX / 2);  // 빨간 영역의 가로 중앙
            float textCenterY = itemView.getTop() + (itemView.getHeight() / 2f)
                    + (textPaint.getTextSize() / 3);  // 세로 중앙 보정

            // 충분히 스와이프했을 때만 텍스트 표시 (너무 좁을 때 깨짐 방지)
            if (Math.abs(dX) > 100) {
                c.drawText(text, textCenterX, textCenterY, textPaint);
            }
        } else {
            background.setBounds(0, 0, 0, 0);
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    public interface SwipeControllerActions {
        void onRightClicked(int position);
    }
}