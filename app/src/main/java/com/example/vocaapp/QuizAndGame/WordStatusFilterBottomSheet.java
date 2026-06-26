package com.example.vocaapp.QuizAndGame;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.vocaapp.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WordStatusFilterBottomSheet extends BottomSheetDialogFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.word_status_filter_bottom_sheet, container, false);

        CheckBox checkNotStudied = view.findViewById(R.id.checkNotStudied);
        CheckBox checkConfused = view.findViewById(R.id.checkConfused);
        CheckBox checkMemorized = view.findViewById(R.id.checkMemorized);

        LinearLayout rowNotStudied = view.findViewById(R.id.rowNotStudied);
        LinearLayout rowConfused = view.findViewById(R.id.rowConfused);
        LinearLayout rowMemorized = view.findViewById(R.id.rowMemorized);

        rowNotStudied.setOnClickListener(v -> checkNotStudied.setChecked(!checkNotStudied.isChecked()));
        rowConfused.setOnClickListener(v -> checkConfused.setChecked(!checkConfused.isChecked()));
        rowMemorized.setOnClickListener(v -> checkMemorized.setChecked(!checkMemorized.isChecked()));

        MaterialButton startButton = view.findViewById(R.id.startButton);
        startButton.setOnClickListener(v -> {
            List<Integer> selectedStatuses = new ArrayList<>();
            if (checkNotStudied.isChecked()) selectedStatuses.add(0);
            if (checkConfused.isChecked()) selectedStatuses.add(1);
            if (checkMemorized.isChecked()) selectedStatuses.add(2);

            if (selectedStatuses.isEmpty()) {
                Toast.makeText(getContext(), "최소 1개 이상 선택해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            String vocabularyId = getArguments() != null ? getArguments().getString("vocabularyId") : null;
            String quizType = getArguments() != null ? getArguments().getString("quizType", "DICTATION") : "DICTATION";
            boolean isOfficial = getArguments() != null && getArguments().getBoolean("isOfficial", false);

            if (vocabularyId == null) {
                Toast.makeText(getContext(), "단어장 정보가 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(getContext(), "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            String uid = user.getUid();

            startButton.setEnabled(false);

            QuizAndGameFirestore firestoreHelper = new QuizAndGameFirestore();
            firestoreHelper.getWordsByStatus(uid, vocabularyId, selectedStatuses, words -> {
                if (!isAdded()) return;
                startButton.setEnabled(true);

                if (words.isEmpty()) {
                    Toast.makeText(getContext(), "선택한 조건에 해당하는 단어가 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // HashMap으로 복사하여 Serializable 보장
                ArrayList<HashMap<String, Object>> serializableWords = new ArrayList<>();
                for (Map<String, Object> w : words) {
                    serializableWords.add(new HashMap<>(w));
                }

                Intent intent;
                if ("FLASHCARD".equals(quizType)) {
                    intent = new Intent(getContext(), OXTestActivity.class);
                } else if ("MULTIPLE_CHOICE".equals(quizType)) {
                    intent = new Intent(getContext(), MultipleChoiceActivity.class);
                } else {
                    intent = new Intent(getContext(), DictationActivity.class);
                }

                intent.putExtra("vocabularyId", vocabularyId);
                intent.putExtra("isOfficial", isOfficial);
                intent.putExtra("preloadedWords", serializableWords);
                startActivity(intent);
                dismiss();
            });
        });

        return view;
    }
}
