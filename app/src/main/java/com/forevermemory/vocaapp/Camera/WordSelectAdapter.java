package com.forevermemory.vocaapp.Camera;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.forevermemory.vocaapp.R;
import com.forevermemory.vocaapp.VocabularyList.WordItem;

import java.util.ArrayList;
import java.util.List;

public class WordSelectAdapter extends RecyclerView.Adapter<WordSelectAdapter.ViewHolder> {

    private List<WordItem> wordList;
    private OnSelectionChangedListener selectionChangedListener;

    // 전체 선택 체크박스 동기화를 위한 인터페이스
    public interface OnSelectionChangedListener {
        void onSelectionChanged();
    }

    public WordSelectAdapter(List<WordItem> wordList) {
        this.wordList = wordList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_word_select, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WordItem item = wordList.get(position);
        holder.wordText.setText(item.word);
        holder.meaningText.setText(item.meaning);
        holder.pronunciationText.setText(item.pronunciation);

        // 리스너 먼저 제거 (RecyclerView 재활용 이슈 방지)
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(item.selected);
        holder.itemView.setOnClickListener(null);

        if (item.isDuplicate) {
            // 이미 등록된 단어: 선택 불가 처리 + 구분 표시
            holder.checkBox.setEnabled(false);
            holder.duplicateLabel.setVisibility(View.VISIBLE);
            holder.wordText.setTextColor(0xFFAAAAAA);
            holder.meaningText.setTextColor(0xFFAAAAAA);
            holder.pronunciationText.setTextColor(0xFFAAAAAA);
        } else {
            holder.checkBox.setEnabled(true);
            holder.duplicateLabel.setVisibility(View.GONE);
            holder.wordText.setTextColor(0xFF000000);
            holder.meaningText.setTextColor(0xFF555555);
            holder.pronunciationText.setTextColor(0xFF888888);

            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.selected = isChecked;
                // 전체 선택 체크박스 동기화 알림
                if (selectionChangedListener != null) {
                    selectionChangedListener.onSelectionChanged();
                }
            });

            // 행 전체 클릭으로도 토글되게
            holder.itemView.setOnClickListener(v ->
                    holder.checkBox.setChecked(!holder.checkBox.isChecked())
            );
        }
    }

    @Override
    public int getItemCount() {
        return wordList == null ? 0 : wordList.size();
    }

    // 선택된 단어들만 반환 (이미 등록된 단어는 추가 대상에서 제외)
    public List<WordItem> getSelectedWords() {
        List<WordItem> selected = new ArrayList<>();
        for (WordItem item : wordList) {
            if (item.selected && !item.isDuplicate) selected.add(item);
        }
        return selected;
    }

    // 모든 단어 반환 (전체 선택 상태 확인용)
    public List<WordItem> getAllWords() {
        return wordList;
    }

    // 전체 선택/해제 (전체 선택 체크박스용, 이미 등록된 단어는 제외)
    public void setAllSelected(boolean selected) {
        for (WordItem item : wordList) {
            if (!item.isDuplicate) item.selected = selected;
        }
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged();
        }
    }

    // 선택 변경 리스너 설정
    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView wordText;
        TextView meaningText;
        TextView pronunciationText;
        TextView duplicateLabel;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkBox);
            wordText = itemView.findViewById(R.id.wordText);
            meaningText = itemView.findViewById(R.id.meaningText);
            pronunciationText = itemView.findViewById(R.id.pronunciationText);
            duplicateLabel = itemView.findViewById(R.id.duplicateLabel);
        }
    }
}