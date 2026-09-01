package com.forevermemory.vocaapp.QuizAndGame;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.forevermemory.vocaapp.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.forevermemory.vocaapp.util.PopupUtil;

public class SelectVocabularyBookBottomSheet extends BottomSheetDialogFragment {

    public static final String REQUEST_KEY = "vocabularySelectionRequestKey";
    public static final String RESULT_SELECTED_ID = "selectedId";

    private RecyclerView recyclerView;
    private WordbookAdapter adapter;
    private List<Map<String, Object>> dataList = new ArrayList<>();
    private String uid;
    private ImageView cancelImageView;
    private TextView registerTextView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.select_vocabulary_book_bottom_sheet, container, false);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            uid = user.getUid();
        }

        // 초기화 및 레이아웃 연결
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        cancelImageView = view.findViewById(R.id.cancelImageView);
        registerTextView = view.findViewById(R.id.registerTextView);

        cancelImageView.setOnClickListener(v->{
            dismiss();
        });

        registerTextView.setOnClickListener(v -> {
            Map<String, Object> selectedData = adapter.getSelectedWordbook();

            if (selectedData == null) {
                PopupUtil.show(getContext(), "단어장을 선택해주세요!");
                return;
            }

            String id = String.valueOf(selectedData.get("id"));

            boolean selectOnly = getArguments() != null && getArguments().getBoolean("selectOnly", false);
            if (selectOnly) {
                Bundle result = new Bundle();
                result.putString(RESULT_SELECTED_ID, id);
                getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
                dismiss();
                return;
            }

            boolean isStudying;
            if (selectedData.get("isStudying") != null) {
                isStudying = (boolean) selectedData.get("isStudying");
            } else {
                isStudying = false;
            }

            String quizType = "DICTATION";
            boolean isOfficial = false;
            if (getArguments() != null) {
                quizType = getArguments().getString("quizType", "DICTATION");
                isOfficial = getArguments().getBoolean("isOfficial", false);
            }

            if (isOfficial && !isStudying) {
                PopupUtil.show(getContext(), "이 단어장은 학습 모드가 꺼져있습니다.\n단어장 탭에서 학습 모드를 켜주세요!");
                return;
            }

            final String finalQuizType = quizType;
            final boolean finalIsOfficial = isOfficial;

            QuizAndGameFirestore quizAndGameFirestore = new QuizAndGameFirestore();
            quizAndGameFirestore.getWordCount(uid, id, wordCount -> {
                if (wordCount == 0) {
                    PopupUtil.show(requireContext(), "단어장에 단어가 없습니다.");
                    return;
                }

                WordStatusFilterBottomSheet filterSheet = new WordStatusFilterBottomSheet();
                Bundle filterArgs = new Bundle();
                filterArgs.putString("vocabularyId", id);
                filterArgs.putString("quizType", finalQuizType);
                filterArgs.putBoolean("isOfficial", finalIsOfficial);
                filterSheet.setArguments(filterArgs);
                filterSheet.show(getParentFragmentManager(), "WordStatusFilterTag");

                dismiss();
            });
        });

        adapter = new WordbookAdapter(dataList);
        recyclerView.setAdapter(adapter);

        // 2. DB 데이터 불러오기
        loadVocabularies();

        return view;
    }

    private void loadVocabularies() {
        QuizAndGameFirestore.listenVocabularies(uid, new QuizAndGameFirestore.VocabularyListCallback() {
            @Override
            public void onUpdate(List<Map<String, Object>> newDataList) {
                dataList.clear();
                dataList.addAll(newDataList);
                adapter.notifyDataSetChanged(); // 리스트 갱신
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("Firestore", "데이터 로드 실패: ", e);
            }
        });
    }

}
