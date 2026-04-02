package com.example.vocaapp.VocabularyBookList;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;
import com.example.vocaapp.Test.TestActivity;

import java.util.List;
import java.util.Map;

public class VocabularyBookListAdapter extends RecyclerView.Adapter<VocabularyBookListAdapter.UnifiedViewHolder> {

    private List<Map<String, Object>> dataList;
    private VocabularyBookListFragment fragment;

    public VocabularyBookListAdapter(List<Map<String, Object>> dataList, VocabularyBookListFragment fragment) {
        this.dataList = dataList;
        this.fragment = fragment;
    }

    public static class UnifiedViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        SwitchCompat studyModeSwitch;
        View stampContainer;
        View btnStartStudy;
        ImageView[] stamps = new ImageView[6];

        public UnifiedViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.vocabularyNameTextView);
            studyModeSwitch = itemView.findViewById(R.id.studyModeSwitch);
            stampContainer = itemView.findViewById(R.id.stampContainer);
            btnStartStudy = itemView.findViewById(R.id.btn_start_study);

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
    public UnifiedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 단일 레이아웃 인플레이트
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_integrated_vacabulary_book, parent, false);
        return new UnifiedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UnifiedViewHolder holder, int position) {
        Map<String, Object> vocab = dataList.get(position);

        // 1. 기준 데이터 추출
        boolean buttonOn = Boolean.TRUE.equals(vocab.get("buttonOn")); // 레이아웃 결정자
        boolean isStudying = Boolean.TRUE.equals(vocab.get("isStudying")); // 스위치 상태

        holder.titleTextView.setText(String.valueOf(vocab.get("title")));

        // 2. buttonOn 값에 따라 하단 레이아웃 교체
        if (buttonOn) {
            holder.stampContainer.setVisibility(View.GONE);
            holder.btnStartStudy.setVisibility(View.VISIBLE);
        } else {
            holder.stampContainer.setVisibility(View.VISIBLE);
            holder.btnStartStudy.setVisibility(View.GONE);

            // 스탬프 상태 업데이트
            int stampCount = 0;
            Object countObj = vocab.get("stampCount");
            if (countObj != null) {
                try { stampCount = Integer.parseInt(String.valueOf(countObj)); } catch (Exception e) { stampCount = 0; }
            }
            for (int i = 0; i < 6; i++) {
                if (holder.stamps[i] != null) {
                    holder.stamps[i].setImageResource(i < stampCount ?
                            R.drawable.checked_stamp_icon : R.drawable.unchecked_stamp_icon);
                }
            }
        }

        // 3. 스위치 설정 (데이터의 isStudying 참조)
        holder.studyModeSwitch.setOnCheckedChangeListener(null);
        holder.studyModeSwitch.setChecked(isStudying);

        holder.studyModeSwitch.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;

            if (holder.studyModeSwitch.isChecked()) {
                // 스위치를 켰을 때
                fragment.startStudyMode(currentPos);
            } else {
                // 스위치를 끌 때 (다이얼로그 노출을 위해 체크유지)
                holder.studyModeSwitch.setChecked(true);
                fragment.showResetWarningDialog(currentPos);
            }
        });

        // 4. 학습하기 버튼 클릭
        holder.btnStartStudy.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), TestActivity.class);
            intent.putExtra("vocabularyId", String.valueOf(vocab.get("id")));
            v.getContext().startActivity(intent);
        });

        // 5. 기타 아이템 클릭
        holder.itemView.setOnClickListener(v -> fragment.onItemClick(holder.getBindingAdapterPosition()));
        holder.itemView.setOnLongClickListener(v -> {
            fragment.showDeleteConfirmDialog(holder.getBindingAdapterPosition());
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }
}