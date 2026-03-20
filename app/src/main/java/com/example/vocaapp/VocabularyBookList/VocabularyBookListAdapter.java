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
import com.google.firebase.Timestamp;

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

    // 1. 일반 화면용 뷰홀더 (기존 기능 그대로)
    public static class NormalViewHolder extends RecyclerView.ViewHolder {
        TextView textViewItem;
        ImageView[] stamps = new ImageView[7];
        SwitchCompat studyModeSwitch;

        public NormalViewHolder(View itemView) {
            super(itemView);
            textViewItem = itemView.findViewById(R.id.textView);
            studyModeSwitch = itemView.findViewById(R.id.studyModeSwitch);
            stamps[0] = itemView.findViewById(R.id.stamp1);
            stamps[1] = itemView.findViewById(R.id.stamp2);
            stamps[2] = itemView.findViewById(R.id.stamp3);
            stamps[3] = itemView.findViewById(R.id.stamp4);
            stamps[4] = itemView.findViewById(R.id.stamp5);
            stamps[5] = itemView.findViewById(R.id.stamp6);
            stamps[6] = itemView.findViewById(R.id.stamp7); //스탬프 하나 추가
        }
    }

    // 2. 학습 버튼용 뷰홀더
    public static class StudyViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        SwitchCompat studyModeSwitch;
        ConstraintLayout btnStartStudy;

        public StudyViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.textView6);
            studyModeSwitch = itemView.findViewById(R.id.studyModeSwitch);
            btnStartStudy = itemView.findViewById(R.id.btn_start_study);
        }
    }

    @Override
    public int getItemViewType(int position) {
        Map<String, Object> vocab = dataList.get(position);

        boolean isStudying = Boolean.TRUE.equals(vocab.get("isStudying"));
        Timestamp nextReview = (Timestamp) vocab.get("nextReviewDate");
        long now = System.currentTimeMillis();

        if (isStudying && nextReview != null) {
            long reviewTime = nextReview.toDate().getTime();

            // 로그
            Log.d("VIEW_CHECK", "단어장: " + vocab.get("title") +
                    " | 현재: " + now +
                    " | 복습: " + reviewTime +
                    " | 남은시간: " + (reviewTime - now) / 1000 + "초");

            if (now >= reviewTime) {
                Log.d("VIEW_CHECK", "학습 버튼 화면으로 보냅니다.");
                return VIEW_TYPE_STUDY;
            }
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

        // --- 일반 모드일 때 세팅 ---
        if (holder instanceof NormalViewHolder) {
            NormalViewHolder normalHolder = (NormalViewHolder) holder;
            normalHolder.textViewItem.setText(title);

            // 기존 스탬프 로직 그대로
            int stampCount = 0;
            Object countObj = vocab.get("stampCount");
            if (countObj != null) {
                try { stampCount = Integer.parseInt(String.valueOf(countObj)); } catch (Exception e) { stampCount = 0; }
            }
            for (int i = 0; i < 7; i++) {  //7개 늘림
                if (i < stampCount) normalHolder.stamps[i].setImageResource(R.drawable.checked_stamp_icon);
                else normalHolder.stamps[i].setImageResource(R.drawable.unchecked_stamp_icon);
            }

            // 기존 스위치 로직 그대로
            normalHolder.studyModeSwitch.setOnCheckedChangeListener(null);
            normalHolder.studyModeSwitch.setChecked(isStudying);
            normalHolder.studyModeSwitch.setOnClickListener(v -> {
                if (normalHolder.studyModeSwitch.isChecked()) fragment.startStudyMode(holder.getBindingAdapterPosition());
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

        // --- 학습 버튼 모드일 때 세팅 ---
        else if (holder instanceof StudyViewHolder) {
            StudyViewHolder studyHolder = (StudyViewHolder) holder;
            studyHolder.titleTextView.setText(title);

            studyHolder.studyModeSwitch.setOnCheckedChangeListener(null);
            studyHolder.studyModeSwitch.setChecked(true); // 이 화면이 떴다는 건 무조건 true임
            studyHolder.studyModeSwitch.setOnClickListener(v -> {
                studyHolder.studyModeSwitch.setChecked(true);
                fragment.showResetWarningDialog(holder.getBindingAdapterPosition());
            });

            // '학습하기' 버튼 누르면 ox
            studyHolder.btnStartStudy.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(v.getContext(), com.example.vocaapp.QuizAndGame.OXTestActivity.class);

                // OX 퀴즈에서 필요한 단어장 ID 넘겨주기
                intent.putExtra("vocabularyId", String.valueOf(vocab.get("id")));

                // 망각 곡선 학습 모드라는 걸 알려주기 위해 isOfficial을 true로 넘김 (선택 사항)
                intent.putExtra("isOfficial", true);

                // 화면 이동!
                v.getContext().startActivity(intent);
            });
        }
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }
}