package com.example.vocaapp.VocabularyList;

import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;

import java.util.List;

import android.view.View;

public class VocabularyListAdapter extends RecyclerView.Adapter<VocabularyListAdapter.WordViewHolder> {

    private List<WordItem> wordList;

    private TextToSpeech tts;

    public VocabularyListAdapter(List<WordItem> wordList, TextToSpeech tts) {
        this.wordList = wordList;
        this.tts = tts;
    }

    // ViewHolder 정의
    public static class WordViewHolder extends RecyclerView.ViewHolder {
        TextView wordTextView, meanTextView, pronunciationTextView;

        ImageView speakerImageView;

        public WordViewHolder(@NonNull View itemView) {
            super(itemView);
            wordTextView = itemView.findViewById(R.id.wordTextView);
            meanTextView = itemView.findViewById(R.id.meanTextView);
            pronunciationTextView = itemView.findViewById(R.id.pronunciationTextView);
            speakerImageView = itemView.findViewById(R.id.speakerImageView);
        }
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vocabulary, parent, false); // XML 이름에 맞게
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
            holder.speakerImageView.setVisibility(View.VISIBLE);
            holder.pronunciationTextView.setText("(" + item.pronunciation + ")");
            holder.speakerImageView.setOnClickListener(v -> {
                if (tts == null) return;
                if (item.word == null || item.word.isEmpty()) return;
                tts.speak(item.word, TextToSpeech.QUEUE_FLUSH, null,
                        "word_" + holder.getAdapterPosition());
            });
        } else {
            holder.pronunciationTextView.setVisibility(View.GONE);
            holder.speakerImageView.setVisibility(View.GONE);
            holder.speakerImageView.setOnClickListener(null);
        }

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

    // 이 메서드를 추가해야 VocabularyActivity에서 호출할 수 있습니다.
    public void updateItems(List<WordItem> newList) {
        // 1. 어댑터가 들고 있는 기존 리스트를 새로운 리스트로 교체
        this.wordList = newList;

        // 2. 어댑터에게 데이터가 변경되었으니 화면을 다시 그리라고 명령 (핵심!)
        notifyDataSetChanged();
    }

    // 특정 위치의 단어를 가져오기 (Undo용 백업)
    public WordItem getItemAt(int position) {
        return wordList.get(position);
    }

    // 특정 위치에 단어를 다시 삽입 (Undo 시 복구)
    public void addItem(int position, WordItem item) {
        wordList.add(position, item);
        notifyItemInserted(position);
    }

}
