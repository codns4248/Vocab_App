package com.example.vocaapp.VocabularyBookList;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;

import java.util.List;
import java.util.Map;

public class CurrentVocabularyBookAdapter extends RecyclerView.Adapter<CurrentVocabularyBookAdapter.VH> {

    public interface OnBookClickListener {
        void onBookClick(Map<String, Object> book);
    }

    private final List<Map<String, Object>> items;
    private final OnBookClickListener listener;

    public CurrentVocabularyBookAdapter(List<Map<String, Object>> items, OnBookClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvCurrentBookTitle;
        TextView tvProgressFraction;
        TextView tvProgressPercent;
        TextView tvContinueStudy;
        TextView tvWordStatusCounts;
        ProgressBar progressBarStudy;

        public VH(@NonNull View itemView) {
            super(itemView);
            tvCurrentBookTitle = itemView.findViewById(R.id.tvCurrentBookTitle);
            tvProgressFraction = itemView.findViewById(R.id.tvProgressFraction);
            tvProgressPercent = itemView.findViewById(R.id.tvProgressPercent);
            tvContinueStudy = itemView.findViewById(R.id.tvContinueStudy);
            tvWordStatusCounts = itemView.findViewById(R.id.tvWordStatusCounts);
            progressBarStudy = itemView.findViewById(R.id.progressBarStudy);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.test_item_current_vocabulary_book, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Map<String, Object> book = items.get(position);

        String title = String.valueOf(book.get("title"));
        h.tvCurrentBookTitle.setText(title);

        int stampCount = toInt(book.get("stampCount"));
        int max = 6;
        int percent = (int) Math.round((stampCount * 100.0) / max);

        h.tvProgressFraction.setText(stampCount + " / " + max);
        h.tvProgressPercent.setText(percent + "% 완료");
        h.progressBarStudy.setMax(100);
        h.progressBarStudy.setProgress(percent);

        if (h.tvWordStatusCounts != null) {
            int wordCount = toInt(book.get("wordCount"));
            h.tvWordStatusCounts.setText("단어 " + wordCount + "개 · 미학습 " + toInt(book.get("unknownCount"))
                    + " · 헷갈림 " + toInt(book.get("confusedCount"))
                    + " · 학습 " + toInt(book.get("memorizedCount")));
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBookClick(book);
        });
        h.tvContinueStudy.setOnClickListener(v -> {
            if (listener != null) listener.onBookClick(book);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o == null) return 0;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}
