package com.example.vocaapp.VocabularyBookList;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.auth.User;

import java.util.List;
import java.util.Map;

public class VocabularyBookListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<Map<String, Object>> dataList;
    private VocabularyBookListFragment fragment;

    private static final int VIEW_TYPE_NORMAL = 0;
    private static final int VIEW_TYPE_STUDY = 1;

    public VocabularyBookListAdapter(List<Map<String, Object>> dataList, VocabularyBookListFragment fragment) {
        this.dataList = dataList;
        this.fragment = fragment;
    }

    public static class NormalViewHolder extends RecyclerView.ViewHolder {
        TextView textViewItem;
        ImageView[] stamps = new ImageView[7];
        SwitchCompat studyModeSwitch;


        public NormalViewHolder(View itemView) {
            super(itemView);
            textViewItem = itemView.findViewById(R.id.vocabularyNameTextView);
            studyModeSwitch = itemView.findViewById(R.id.studyModeSwitch);
            stamps[0] = itemView.findViewById(R.id.stamp1);
            stamps[1] = itemView.findViewById(R.id.stamp2);
            stamps[2] = itemView.findViewById(R.id.stamp3);
            stamps[3] = itemView.findViewById(R.id.stamp4);
            stamps[4] = itemView.findViewById(R.id.stamp5);
            stamps[5] = itemView.findViewById(R.id.stamp6);
        }
    }

    // 학습 버튼의 뷰 필드들 연결
    public static class StudyViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        SwitchCompat studyModeSwitch;
        ConstraintLayout btnStartStudy;

        public StudyViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.vocabularyNameTextView);
            studyModeSwitch = itemView.findViewById(R.id.studyModeSwitch);
            btnStartStudy = itemView.findViewById(R.id.btn_start_study);
        }
    }

    // 단어장 필드 buttonOn이 true면 학습하기 아이템으로 체인지
    @Override
    public int getItemViewType(int position) {
        Map<String, Object> vocab = dataList.get(position);

        boolean buttonOn = Boolean.TRUE.equals(vocab.get("buttonOn"));

        if (buttonOn) {
            Log.d("VIEW_CHECK", "학습 버튼 화면으로 보냅니다.");
            return VIEW_TYPE_STUDY;
        }
        return VIEW_TYPE_NORMAL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_STUDY) {
            // 학습 버튼이 있는 레이아웃
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.vocabulary_book_study_list_item, parent, false);
            return new StudyViewHolder(view);
        } else {
            // 기존 일반 레이아웃
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_vocabulary_book_list, parent, false);
            return new NormalViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Map<String, Object> vocab = dataList.get(position);
        String title = String.valueOf(vocab.get("title"));

        boolean isStudying = false;
        if (vocab.get("isStudying") != null) {
            isStudying = (boolean) vocab.get("isStudying");
        }

        // 일반 모드
        if (holder instanceof NormalViewHolder) {
            NormalViewHolder normalHolder = (NormalViewHolder) holder;
            normalHolder.textViewItem.setText(title);

            int stampCount = 0;
            Object countObj = vocab.get("stampCount");
            if (countObj != null) {
                try { stampCount = Integer.parseInt(String.valueOf(countObj)); } catch (Exception e) { stampCount = 0; }
            }
            for (int i = 0; i < 6; i++) {
                if (i < stampCount) normalHolder.stamps[i].setImageResource(R.drawable.checked_stamp_icon);
                else normalHolder.stamps[i].setImageResource(R.drawable.unchecked_stamp_icon);
            }

            normalHolder.studyModeSwitch.setOnCheckedChangeListener(null);
            normalHolder.studyModeSwitch.setChecked(isStudying);

            normalHolder.studyModeSwitch.setOnClickListener(v -> {
                // 스위치를 활성화를 처리
                if (normalHolder.studyModeSwitch.isChecked()) {
                    fragment.startStudyMode(holder.getBindingAdapterPosition());
                }
                // 스위치를 비활성화 처리
                else {
                    normalHolder.studyModeSwitch.setChecked(true);
                    fragment.showResetWarningDialog(holder.getBindingAdapterPosition());
                }
            });

            normalHolder.itemView.setOnClickListener(v -> fragment.onItemClick(holder.getBindingAdapterPosition()));
            normalHolder.itemView.setOnLongClickListener(v -> {
                fragment.showDeleteConfirmDialog(holder.getBindingAdapterPosition());
                return true;
            });
        }

        else if (holder instanceof StudyViewHolder) {
            StudyViewHolder studyHolder = (StudyViewHolder) holder;
            studyHolder.titleTextView.setText(title);

            studyHolder.studyModeSwitch.setOnCheckedChangeListener(null);
            studyHolder.studyModeSwitch.setChecked(true);
            studyHolder.studyModeSwitch.setOnClickListener(v -> {
                studyHolder.studyModeSwitch.setChecked(true);
                fragment.showResetWarningDialog(holder.getBindingAdapterPosition());
            });

            // 학습하기 버튼을 누르면 intent에 데이터를 넣어서 testActivity로 넘겨줌
            studyHolder.btnStartStudy.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(v.getContext(), com.example.vocaapp.Test.TestActivity.class);
                intent.putExtra("vocabularyId", String.valueOf(vocab.get("id")));
                v.getContext().startActivity(intent);
            });
        }
    }
    @Override
    public int getItemCount() {
        return dataList.size();
    }
}
