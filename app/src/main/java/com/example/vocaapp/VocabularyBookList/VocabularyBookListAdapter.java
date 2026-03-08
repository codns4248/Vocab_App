package com.example.vocaapp.VocabularyBookList;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.graphics.Color;
import android.widget.ImageView;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;

import java.util.List;
import java.util.Map;

public class VocabularyBookListAdapter extends RecyclerView.Adapter<VocabularyBookListAdapter.VocabularyListViewHolder>{

    // [수정] 데이터를 Object 타입으로 변경
    private List<Map<String, Object>> dataList;
    private VocabularyBookListFragment fragment;

    public VocabularyBookListAdapter(List<Map<String, Object>> dataList, VocabularyBookListFragment fragment) {
        this.dataList = dataList;
        this.fragment = fragment;
    }

    public static class VocabularyListViewHolder extends RecyclerView.ViewHolder {
        TextView textViewItem;
        ImageView[] stamps = new ImageView[6];

        androidx.appcompat.widget.SwitchCompat studyModeSwitch; //스위치 변수 추가

        public VocabularyListViewHolder(View itemView) {
            super(itemView);
            textViewItem = itemView.findViewById(R.id.textView);

            studyModeSwitch = itemView.findViewById(R.id.studyModeSwitch);
            stamps[0] = itemView.findViewById(R.id.stamp1);
            stamps[1] = itemView.findViewById(R.id.stamp2);
            stamps[2] = itemView.findViewById(R.id.stamp3);
            stamps[3] = itemView.findViewById(R.id.stamp4);
            stamps[4] = itemView.findViewById(R.id.stamp5);
            stamps[5] = itemView.findViewById(R.id.stamp6);
        }
    }

    @NonNull
    @Override
    public VocabularyListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.vocabulary_book_list_item, parent, false);
        return new VocabularyListViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VocabularyListViewHolder holder, int position) {
        Map<String, Object> vocab = dataList.get(position);
        holder.textViewItem.setText(String.valueOf(vocab.get("title")));



        // 스탬프 개수를 안전하게 가져오는 로직
        int stampCount = 0;
        Object countObj = vocab.get("stampCount");

        if (countObj != null) {
            try {
                // 어떤 숫자 타입이 오든 안전하게 int로 변환
                stampCount = Integer.parseInt(String.valueOf(countObj));
            } catch (Exception e) {
                stampCount = 0;
            }
        }

        // 스탬프 개수만큼 빨간색 필터 적용
        for (int i = 0; i < 6; i++) {
            if (i < stampCount) {
                // 획득 시: 빨간색 (원하는 색상 코드로 변경 가능)
                holder.stamps[i].setColorFilter(Color.RED);
            } else {
                // 미획득 시: 회색
                holder.stamps[i].setColorFilter(Color.LTGRAY);
            }
        }
        // 1. 스위치 상태 초기화 (현재 데이터에 저장된 대로)
        boolean isStudying = false;
        if (vocab.get("isStudying") != null) {
            isStudying = (boolean) vocab.get("isStudying");
        }

        // 리스너가 중복 호출되지 않도록 일단 null로 초기화 후 상태 설정
        holder.studyModeSwitch.setOnCheckedChangeListener(null);
        holder.studyModeSwitch.setChecked(isStudying);

        // 2. 스위치 클릭 이벤트 (작성하신 setOnClickListener 그대로 사용)
        holder.studyModeSwitch.setOnClickListener(v -> {
            boolean isChecked = holder.studyModeSwitch.isChecked();

            if (isChecked) {
                // 스위치를 켰을 때: 학습 모드 시작
                fragment.startStudyMode(holder.getBindingAdapterPosition());
            } else {
                // 스위치를 껐을 때:
                // 팝업에서 '취소'를 누를 수도 있으므로
                // 일단 화면상으로는 스위치를 다시 켜두고 팝업을 띄움
                holder.studyModeSwitch.setChecked(true);
                fragment.showResetWarningDialog(holder.getBindingAdapterPosition());
            }
        });

        // 단어장 안 단어들 확인을 위한 클릭
        holder.itemView.setOnClickListener(v -> fragment.onItemClick(holder.getBindingAdapterPosition()));

        // [추가] 아이템 롱 클릭 이벤트 (삭제 팝업)
        holder.itemView.setOnLongClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();

            // Fragment에 삭제 확인 다이얼로그를 띄우는 메서드를 호출합니다.
            fragment.showDeleteConfirmDialog(currentPos);

            return true; // true를 반환해야 일반 클릭 이벤트가 동시에 발생하지 않습니다.
        });

    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }
}
