package com.example.vocaapp.QuizAndGame;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;

import java.util.List;
import java.util.Map; // 추가

public class FailVocaListAdapter extends RecyclerView.Adapter<FailVocaListAdapter.ViewHolder> {

    // [수정] String 리스트에서 Map 리스트로 변경
    private List<Map<String, Object>> dataList;
    private Context context;

    // [수정] 생성자 파라미터 타입 변경
    public FailVocaListAdapter(Context context, List<Map<String, Object>> dataList) {
        this.context = context;
        this.dataList = dataList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // R.layout.item_word_list 안에 단어와 뜻을 위한 TextView가 각각 있어야 합니다.
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vocabulary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // [수정] 현재 위치의 Map 데이터를 가져옴
        Map<String, Object> item = dataList.get(position);

        // [추가] Firestore 필드명("word", "meaning")에 맞춰 데이터 추출
        String word = String.valueOf(item.get("word"));
        String meaning = String.valueOf(item.get("meaning"));
        String pronunciation = String.valueOf(item.get("pronunciation"));

        // [수정] 뷰 홀더에 데이터 세팅
        holder.wordTextView.setText(word);
        holder.meaningTextView.setText(meaning);
        holder.pronunciationTextView.setText(pronunciation);

        holder.itemView.setOnClickListener(v -> {
            // 클릭 이벤트 처리 (필요시)
        });
    }

    @Override
    public int getItemCount() {
        return dataList != null ? dataList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        // [수정] 뜻을 표시할 TextView 추가
        TextView wordTextView;
        TextView meaningTextView;
        TextView pronunciationTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // [수정] XML 레이아웃(item_word_list)의 ID와 일치시켜주세요.
            wordTextView = itemView.findViewById(R.id.wordTextView); // 기존 단어 뷰
            meaningTextView = itemView.findViewById(R.id.meanTextView); // 뜻을 위한 뷰 ID 추가 필요
            pronunciationTextView = itemView.findViewById(R.id.pronunciationTextView);
        }
    }
}