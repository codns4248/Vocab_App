package com.example.vocaapp.Camera;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;
import com.example.vocaapp.VocabularyList.WordItem;

import java.util.ArrayList;
import java.util.List;

public class WordSelectAdapter extends RecyclerView.Adapter<WordSelectAdapter.ViewHolder> {

    private List<WordItem> wordList;

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
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.selected = isChecked;
        });

        // 행 전체 클릭으로도 토글되게
        holder.itemView.setOnClickListener(v ->
                holder.checkBox.setChecked(!holder.checkBox.isChecked())
        );
    }

    @Override
    public int getItemCount() {
        return wordList == null ? 0 : wordList.size();
    }

    // 선택된 단어들만 반환
    public List<WordItem> getSelectedWords() {
        List<WordItem> selected = new ArrayList<>();
        for (WordItem item : wordList) {
            if (item.selected) selected.add(item);
        }
        return selected;
    }

    // 전체 선택/해제 (전체 선택 체크박스용)
    public void setAllSelected(boolean selected) {
        for (WordItem item : wordList) {
            item.selected = selected;
        }
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView wordText;
        TextView meaningText;
        TextView pronunciationText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkBox);
            wordText = itemView.findViewById(R.id.wordText);
            meaningText = itemView.findViewById(R.id.meaningText);
            pronunciationText = itemView.findViewById(R.id.pronunciationText);
        }
    }
}