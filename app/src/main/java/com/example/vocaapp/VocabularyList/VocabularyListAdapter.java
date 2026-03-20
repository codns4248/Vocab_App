package com.example.vocaapp.VocabularyList;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;

import java.util.List;

public class VocabularyListAdapter extends RecyclerView.Adapter<VocabularyListAdapter.WordViewHolder> {

    // 4개의 문자열을 각각 담은 리스트들
    private List<String> words;
    private List<String> meanings;
    private List<String> pronunciations;


    // 생성자
    public VocabularyListAdapter(List<String> words, List<String> meanings,
                                 List<String> pronunciations) {
        this.words = words;
        this.meanings = meanings;
        this.pronunciations = pronunciations;

    }

    // ViewHolder 정의
    public static class WordViewHolder extends RecyclerView.ViewHolder {
        TextView wordTextView, meanTextView, pronunciationTextView, commentTextView;

        public WordViewHolder(@NonNull View itemView) {
            super(itemView);
            wordTextView = itemView.findViewById(R.id.wordTextView);
            meanTextView = itemView.findViewById(R.id.meanTextView);
            pronunciationTextView = itemView.findViewById(R.id.pronunciationTextView);

        }
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_word_list, parent, false); // XML 이름에 맞게
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {

        holder.itemView.setTranslationX(0f);  // [수정] 뷰가 재사용될 때, 기존에 밀려있던 위치를 0으로 강제 초기화

        // 리스트에서 각각 꺼내서 TextView에 세팅 (기존 코드)
        holder.wordTextView.setText(words.get(position));
        holder.meanTextView.setText(meanings.get(position));
        holder.pronunciationTextView.setText("(" + pronunciations.get(position) + ")");
    }

    @Override
    public int getItemCount() {
        return words.size(); // 모든 리스트는 같은 길이여야 함
    }

    public void removeItem(int position) {   //디버깅 추가(단어 삭제 헬퍼)
        //1. 데이터 리스트에서 삭제
        words.remove(position);
        meanings.remove(position);
        pronunciations.remove(position);
        //comments.remove(position);

        //2. 삭제 애니메이션 실행
        notifyItemRemoved(position);

        //3. 번호표 다시 붙이기 (삭제된 위치부터 남은 개수만큼만 업데이트
        if (position < words.size()) {
            notifyItemRangeChanged(position, words.size() - position);
        }
    }
}

