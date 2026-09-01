package com.forevermemory.vocaapp.QuizAndGame;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.forevermemory.vocaapp.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.forevermemory.vocaapp.util.PopupUtil;

public class QuizSettingBottomSheet extends BottomSheetDialogFragment {
    private String selectedDocId;
    private String selectedDocTitle;
    private TextView selectdWordBookTextView;
    private boolean selectedBookIsStudying = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getChildFragmentManager().setFragmentResultListener("requestKey", this, (requestKey, bundle) -> {
            this.selectedDocId = bundle.getString("selectedId");
            this.selectedDocTitle = bundle.getString("selectedTitle");

            this.selectedBookIsStudying = bundle.getBoolean("isStudying", false);

            selectdWordBookTextView = getView().findViewById(R.id.selectedWordBookTextView);
            selectdWordBookTextView.setText(selectedDocTitle);
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dictation_bottom_sheet, container, false);

        Button startButton = view.findViewById(R.id.button);
        LinearLayout selectLinearLayout = view.findViewById(R.id.selectLinearLayout);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedDocId == null){
                    PopupUtil.show(getContext(), "단어장을 선택해주세요!");
                    return;
                }

                String quizType = "DICTATION";
                boolean isOfficial = false; // 추가: 기본값은 '자율 모드(false)'로 설정

                if (getArguments() != null) {
                    quizType = getArguments().getString("quizType", "DICTATION");
                    isOfficial = getArguments().getBoolean("isOfficial", false); // 추가 2: 이전 화면에서 넘겨준 모드 값이 있는지 확인해서 받기
                }

                if (isOfficial && !selectedBookIsStudying) {
                    PopupUtil.show(getContext(), "이 단어장은 학습 모드가 꺼져있습니다.\n단어장 탭에서 학습 모드를 켜주세요!");
                    return;
                }


                dismiss();

                // 게임 선택에 따라 다른 화면 이동
                Intent intent;
                if ("FLASHCARD".equals(quizType)) {
                    intent = new Intent(getContext(), OXTestActivity.class);
                } else if ("MULTIPLE_CHOICE".equals(quizType)) {
                    intent = new Intent(getContext(), MultipleChoiceActivity.class);
                } else{
                    intent = new Intent(getContext(), DictationActivity.class);
                }


                intent.putExtra("vocabularyId", selectedDocId);
                intent.putExtra("isOfficial", isOfficial);  // 추가 3: 퀴즈 화면으로 '공식 모드인지 아닌지' 이름표 넘겨주기
                startActivity(intent);
            }
        });

        selectLinearLayout.setOnClickListener(v -> {
            SelectVocabularyBookBottomSheet bottomSheet = new SelectVocabularyBookBottomSheet();
            bottomSheet.show(getChildFragmentManager(), "SelectVocabularyBookTag");
        });

        return view;
    }
}
