package com.example.vocaapp.QuizAndGame;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import com.example.vocaapp.R;

public class QuizAndGameFragment extends Fragment {
    ConstraintLayout dictationConstraintLayout;
    ConstraintLayout flashcardConstraintLayout;
    ConstraintLayout multiplechoiceConstraintLayout;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_quiz_and_game, container, false);

        dictationConstraintLayout = view.findViewById(R.id.dictationConstraintLayout);
        flashcardConstraintLayout = view.findViewById(R.id.flashcardConstraintLayout);
        multiplechoiceConstraintLayout = view.findViewById(R.id.multiplechoiceConstraintLayout);


        //  받아쓰기 버튼
        dictationConstraintLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                QuizSettingBottomSheet bottomSheet = new QuizSettingBottomSheet();
                Bundle args = new Bundle();
                args.putString("quizType", "DICTATION");

                args.putBoolean("isOfficial", false);

                bottomSheet.setArguments(args);
                bottomSheet.show(getChildFragmentManager(), "QuizSettingTag");
            }
        });

        //  플래시카드(O/X) 버튼
        flashcardConstraintLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                QuizSettingBottomSheet bottomSheet = new QuizSettingBottomSheet();
                Bundle args = new Bundle();
                args.putString("quizType", "FLASHCARD");

                args.putBoolean("isOfficial", false);

                bottomSheet.setArguments(args);
                bottomSheet.show(getChildFragmentManager(), "QuizSettingTag");
            }
        });

        // 객관식
        multiplechoiceConstraintLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                QuizSettingBottomSheet bottomSheet = new QuizSettingBottomSheet();
                Bundle args = new Bundle();
                args.putString("quizType", "MULTIPLE_CHOICE");

                args.putBoolean("isOfficial", false);

                bottomSheet.setArguments(args);
                bottomSheet.show(getChildFragmentManager(), "QuizSettingTag");
            }
        });

        return view;

    }
}
