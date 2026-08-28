package com.forevermemory.vocaapp.QuizAndGame;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.forevermemory.vocaapp.R;

import java.util.List;
import java.util.Map;

public class WordbookAdapter extends RecyclerView.Adapter<WordbookAdapter.WordbookViewHolder> {

    private List<Map<String, Object>> wordbookList;
    private int selectedPosition = -1;

    // 생성자: 데이터를 전달받습니다.
    public WordbookAdapter(List<Map<String, Object>> list) {
        this.wordbookList = list;
    }

    // 현재 선택된 아이템의 title과 id를 가져오는 메서드
    public Map<String, Object> getSelectedWordbook() {
        if (selectedPosition != -1 && selectedPosition < wordbookList.size()) {
            return wordbookList.get(selectedPosition);
        }
        return null;
    }

    // 1. 뷰홀더 생성: 아이템 레이아웃(XML)을 가져와서 뷰객체로 만듭니다.
    @NonNull
    @Override
    public WordbookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.select_vocabulary_book_item, parent, false);
        return new WordbookViewHolder(view);
    }

    // 2. 데이터 바인딩: 생성된 뷰홀더에 실제 데이터를 넣습니다.
    @Override
    public void onBindViewHolder(@NonNull WordbookViewHolder holder, int position) {
        Map<String, Object> vocab = wordbookList.get(position);

        // 단어장 이름 설정
        holder.tvTitle.setText(String.valueOf(vocab.get("title")));

        // 단어 개수 설정 (숫자 + " 단어")
        Object count = vocab.get("wordCount");
        String countText = (count != null ? String.valueOf(count) : "0") + " 단어";
        holder.wordCountsTextView.setText(countText);

        // 1. 배경색 결정 로직 (둥근 모서리 유지)
        if (selectedPosition == position) {
            int color = ContextCompat.getColor(holder.itemView.getContext(), R.color.md_theme_primary);
            holder.colorConstraintLayout.getBackground().mutate().setTint(color);
        } else {
            // 선택되지 않은 항목은 기본 배경색(예: 흰색)으로 초기화
            holder.colorConstraintLayout.getBackground().setTint(Color.WHITE);
        }

        // 2. 클릭 리스너: 단일 선택 구현
        holder.itemView.setOnClickListener(v -> {
            // 이미 선택된 항목을 또 누른 경우 (선택 해제하고 싶다면)
            if (selectedPosition == holder.getAdapterPosition()) {
                selectedPosition = -1;
                notifyItemChanged(holder.getAdapterPosition());
            }
            // 새로운 항목을 누른 경우
            else {
                int previousPosition = selectedPosition;
                selectedPosition = holder.getAdapterPosition();

                // 이전 선택된 아이템은 원래대로(흰색)
                if (previousPosition != -1) {
                    notifyItemChanged(previousPosition);
                }
                // 새로 선택된 아이템은 강조색으로
                notifyItemChanged(selectedPosition);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            // 1. 클릭한 아이템의 위치를 가져옵니다.
            int currentPosition = holder.getAdapterPosition();

            // 2. 이미 선택된 아이템을 또 눌렀을 때의 처리 (선택 해제 기능)
            if (selectedPosition == currentPosition) {
                selectedPosition = -1; // 선택 해제
                notifyItemChanged(currentPosition);
                return; // 아래 로직 실행 안 함
            }

            // 3. 새로운 아이템을 선택하는 경우
            int previousPosition = selectedPosition;
            selectedPosition = currentPosition;

            // 이전 선택된 아이템 갱신 (왕관 박탈)
            if (previousPosition != -1) {
                notifyItemChanged(previousPosition);
            }

            // 현재 선택된 아이템 갱신 (왕관 부여)
            notifyItemChanged(selectedPosition);
        });
    }

    // 3. 아이템 개수 반환
    @Override
    public int getItemCount() {
        return wordbookList.size();
    }

    // 뷰홀더 클래스: 아이템 레이아웃 안에 있는 위젯들을 연결합니다.
    public static class WordbookViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView wordCountsTextView;
        ConstraintLayout colorConstraintLayout;


        public WordbookViewHolder(@NonNull View itemView) {
            super(itemView);
            this.tvTitle = itemView.findViewById(R.id.tv_wordbook_name);
            this.wordCountsTextView = itemView.findViewById(R.id.wrodCountsTextView);
            this.colorConstraintLayout = itemView.findViewById(R.id.colorConstraintLayout);
        }
    }
}
