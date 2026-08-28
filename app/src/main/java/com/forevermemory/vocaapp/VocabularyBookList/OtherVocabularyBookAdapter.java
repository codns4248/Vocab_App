package com.forevermemory.vocaapp.VocabularyBookList;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.forevermemory.vocaapp.R;

import java.util.List;
import java.util.Map;

public class OtherVocabularyBookAdapter extends RecyclerView.Adapter<OtherVocabularyBookAdapter.VH> {

    public interface OnBookActionListener {
        void onBookClick(Map<String, Object> book);
        void onBookLongClick(Map<String, Object> book);
    }

    private final List<Map<String, Object>> items;
    private final OnBookActionListener listener;

    public OtherVocabularyBookAdapter(List<Map<String, Object>> items, OnBookActionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvBookLabel;
        TextView tvBookTitle;
        TextView tvBookWordCount;

        public VH(@NonNull View itemView) {
            super(itemView);
            tvBookLabel = itemView.findViewById(R.id.tvBookLabel);
            tvBookTitle = itemView.findViewById(R.id.tvBookTitle);
            tvBookWordCount = itemView.findViewById(R.id.tvBookWordCount);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.test_item_vocabulary_book, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Map<String, Object> book = items.get(position);

        String title = String.valueOf(book.get("title"));
        h.tvBookTitle.setText(title);

        int wordCount = toInt(book.get("wordCount"));
        int unknownCount = toInt(book.get("unknownCount"));
        int confusedCount = toInt(book.get("confusedCount"));
        int memorizedCount = toInt(book.get("memorizedCount"));
        h.tvBookWordCount.setText("단어 " + wordCount + "개 · 미학습 " + unknownCount
                + " · 헷갈림 " + confusedCount + " · 학습 " + memorizedCount);


        String label = title != null && !title.isEmpty()
                ? title.substring(0, Math.min(2, title.length())).toUpperCase()
                : "";
        h.tvBookLabel.setText(label);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBookClick(book);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onBookLongClick(book);
            return true;
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
