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

    private final ColorDrawable deleteBackground;
    private final ColorDrawable editBackground;
    private final Paint textPaint;
    private final SwipeControllerActions actions;

    public SwipeController(SwipeControllerActions actions) {
        // 왼쪽으로 밀면 삭제, 오른쪽으로 밀면 수정
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.actions = actions;
        this.deleteBackground = new ColorDrawable(Color.parseColor("#F44336"));
        this.editBackground = new ColorDrawable(Color.parseColor("#3b5bdb"));

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
        if (position == RecyclerView.NO_POSITION || actions == null) return;

        if (direction == ItemTouchHelper.RIGHT) {
            actions.onEditRequested(position);
        } else {
            actions.onRightClicked(position);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        View itemView = viewHolder.itemView;
        float textCenterY = itemView.getTop() + (itemView.getHeight() / 2f)
                + (textPaint.getTextSize() / 3);  // 세로 중앙 보정

        if (dX < 0) {
            // 왼쪽 스와이프: 빨간 배경 + "삭제" (스와이프된 만큼만 보이도록)
            deleteBackground.setBounds(
                    itemView.getRight() + (int) dX, itemView.getTop(),
                    itemView.getRight(), itemView.getBottom()
            );
            deleteBackground.draw(c);

            // 충분히 스와이프했을 때만 텍스트 표시 (너무 좁을 때 깨짐 방지)
            if (Math.abs(dX) > 100) {
                c.drawText("삭제", itemView.getRight() + (dX / 2), textCenterY, textPaint);
            }
        } else if (dX > 0) {
            // 오른쪽 스와이프: 파란 배경 + "수정"
            editBackground.setBounds(
                    itemView.getLeft(), itemView.getTop(),
                    itemView.getLeft() + (int) dX, itemView.getBottom()
            );
            editBackground.draw(c);

            if (dX > 100) {
                c.drawText("수정", itemView.getLeft() + (dX / 2), textCenterY, textPaint);
            }
        } else {
            deleteBackground.setBounds(0, 0, 0, 0);
            editBackground.setBounds(0, 0, 0, 0);
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    public interface SwipeControllerActions {
        /** 왼쪽으로 밀었을 때 (삭제) */
        void onRightClicked(int position);

        /** 오른쪽으로 밀었을 때 (수정) */
        void onEditRequested(int position);
    }
}