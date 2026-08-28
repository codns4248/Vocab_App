package com.example.vocaapp.VocabularyList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.vocaapp.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

/**
 * 단어 수정 창.
 * 단어 목록이 두 화면(VocabularyFragment, VocabularyActivity)에 있어서 공용으로 뺐다.
 * 레이아웃은 등록 창(vocabulary_register_bottom_sheet)을 그대로 쓰고 제목과 버튼 문구만 바꾼다.
 */
public class WordEditDialog {

    public interface OnWordUpdatedListener {
        void onWordUpdated(String word, String meaning, String pronunciation);
    }

    public static void show(Context context, LayoutInflater inflater,
                            String uid, String vocabularyId,
                            WordItem target, OnWordUpdatedListener listener) {
        if (context == null || target == null || target.docId == null) return;

        View dialogView = inflater.inflate(R.layout.vocabulary_register_bottom_sheet, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();

        TextView sheetTitle = dialogView.findViewById(R.id.sheetTitleTextView);
        EditText wordEditText = dialogView.findViewById(R.id.wordEditText);
        EditText meanEditText = dialogView.findViewById(R.id.meanEditText);
        EditText pronunciationEditText = dialogView.findViewById(R.id.pronunciationEditText);
        MaterialButton submitButton = dialogView.findViewById(R.id.wordRegisterButton);

        if (sheetTitle != null) sheetTitle.setText("단어 수정");
        if (submitButton != null) submitButton.setText("수정");

        wordEditText.setText(target.word);
        meanEditText.setText(target.meaning);
        pronunciationEditText.setText(target.pronunciation);
        wordEditText.setSelection(wordEditText.getText().length());

        dialogView.findViewById(R.id.closeButton).setOnClickListener(v -> dialog.dismiss());

        submitButton.setOnClickListener(v -> {
            String word = wordEditText.getText().toString().trim().toLowerCase(Locale.ENGLISH);
            String mean = meanEditText.getText().toString().trim();
            String pronunciation = pronunciationEditText.getText().toString().trim();

            if (word.isEmpty() || mean.isEmpty()) {
                Toast.makeText(context, "단어와 의미는 필수입니다", Toast.LENGTH_SHORT).show();
                return;
            }

            Runnable save = () -> VocabularyFirestore.updateWord(uid, vocabularyId, target.docId,
                    word, mean, pronunciation,
                    () -> {
                        target.word = word;
                        target.meaning = mean;
                        target.pronunciation = pronunciation;
                        Toast.makeText(context, "수정되었습니다.", Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onWordUpdated(word, mean, pronunciation);
                        dialog.dismiss();
                    },
                    () -> Toast.makeText(context, "수정 실패", Toast.LENGTH_SHORT).show());

            // 철자를 바꾸지 않았다면 중복 검사를 건너뛴다. 그대로 두면 자기 자신과 부딪힌다.
            if (word.equalsIgnoreCase(target.word)) {
                save.run();
                return;
            }

            new VocabularyFirestore().alreadyVocabulary(uid, vocabularyId, word, isAlready -> {
                if (isAlready) {
                    Toast.makeText(context, "이미 등록된 단어입니다", Toast.LENGTH_SHORT).show();
                } else {
                    save.run();
                }
            });
        });
    }
}
