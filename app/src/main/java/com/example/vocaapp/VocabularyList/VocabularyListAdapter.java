package com.example.vocaapp.VocabularyList;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;

import java.util.List;

public class VocabularyListAdapter extends RecyclerView.Adapter<VocabularyListAdapter.WordViewHolder> {

    private List<WordItem> wordList;
    private TextToSpeech tts;
    private String uid;
    private String vocabularyId;

    public VocabularyListAdapter(List<WordItem> wordList, TextToSpeech tts,
                                 String uid, String vocabularyId) {
        this.wordList = wordList;
        this.tts = tts;
        this.uid = uid;
        this.vocabularyId = vocabularyId;
    }

    public static class WordViewHolder extends RecyclerView.ViewHolder {
        TextView wordTextView, meanTextView, pronunciationTextView;
        ImageView speakerImageView;
        LinearLayout statusButton;
        ImageView statusIcon;
        TextView statusLabel;

        public WordViewHolder(@NonNull View itemView) {
            super(itemView);
            wordTextView = itemView.findViewById(R.id.wordTextView);
            meanTextView = itemView.findViewById(R.id.meanTextView);
            pronunciationTextView = itemView.findViewById(R.id.pronunciationTextView);
            speakerImageView = itemView.findViewById(R.id.speakerImageView);
            statusButton = itemView.findViewById(R.id.statusButton);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            statusLabel = itemView.findViewById(R.id.statusLabel);
        }
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vocabulary, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        holder.itemView.setTranslationX(0f);

        WordItem item = wordList.get(position);

        holder.wordTextView.setText(item.word);
        holder.meanTextView.setText(item.meaning);

        boolean hasPronunciation = item.pronunciation != null && !item.pronunciation.isEmpty();
        if (hasPronunciation) {
            holder.pronunciationTextView.setVisibility(View.VISIBLE);
            holder.pronunciationTextView.setText("(" + item.pronunciation + ")");
        } else {
            holder.pronunciationTextView.setVisibility(View.GONE);
        }

        boolean hasWord = item.word != null && !item.word.isEmpty();
        holder.speakerImageView.setVisibility(hasWord ? View.VISIBLE : View.GONE);
        holder.speakerImageView.setOnClickListener(hasWord ? v -> {
            if (tts == null) return;
            tts.speak(item.word, TextToSpeech.QUEUE_FLUSH, null,
                    "word_" + holder.getAdapterPosition());
        } : null);

        applyStatus(holder, item);

        holder.statusButton.setOnClickListener(v -> {
            int oldStatus = item.studyStatus;
            item.studyStatus = (item.studyStatus + 1) % 3;
            applyStatus(holder, item);
            if (uid != null && vocabularyId != null && item.docId != null) {
                VocabularyFirestore.updateStudyStatus(uid, vocabularyId, item.docId, item.studyStatus, oldStatus);
            }
        });
    }

    private static void applyStatus(WordViewHolder holder, WordItem item) {
        float cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 6,
                holder.itemView.getContext().getResources().getDisplayMetrics());

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(cornerRadius);

        switch (item.studyStatus) {
            case 1: // 헷갈림
                bg.setColor(Color.parseColor("#FFEBEE"));
                holder.statusIcon.setImageResource(R.drawable.ic_status_confused);
                holder.statusIcon.setColorFilter(Color.parseColor("#E53935"));
                holder.statusLabel.setText("헷갈림");
                holder.statusLabel.setTextColor(Color.parseColor("#E53935"));
                break;
            case 2: // 암기함
                bg.setColor(Color.parseColor("#E8EAF6"));
                holder.statusIcon.setImageResource(R.drawable.ic_status_memorized);
                holder.statusIcon.setColorFilter(Color.parseColor("#3b5bdb"));
                holder.statusLabel.setText("암기");
                holder.statusLabel.setTextColor(Color.parseColor("#3b5bdb"));
                break;
            default: // 미확인 (0)
                bg.setColor(Color.parseColor("#F5F5F5"));
                holder.statusIcon.setImageResource(R.drawable.ic_status_unknown);
                holder.statusIcon.setColorFilter(Color.parseColor("#9E9E9E"));
                holder.statusLabel.setText("미학습");
                holder.statusLabel.setTextColor(Color.parseColor("#9E9E9E"));
                break;
        }
        holder.statusButton.setBackground(bg);
    }

    @Override
    public int getItemCount() {
        return wordList.size();
    }

    public void removeItem(int position) {
        wordList.remove(position);
        notifyItemRemoved(position);
        if (position < wordList.size()) {
            notifyItemRangeChanged(position, wordList.size() - position);
        }
    }

    public void updateItems(List<WordItem> newList) {
        this.wordList = newList;
        notifyDataSetChanged();
    }

    public WordItem getItemAt(int position) {
        return wordList.get(position);
    }

    public void addItem(int position, WordItem item) {
        wordList.add(position, item);
        notifyItemInserted(position);
    }
}
