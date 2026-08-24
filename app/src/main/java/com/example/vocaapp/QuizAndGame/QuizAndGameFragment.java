package com.example.vocaapp.QuizAndGame;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;

import com.example.vocaapp.R;
import com.example.vocaapp.VocabularyList.VocabularyFragment;
import com.example.vocaapp.VocabularyList.WordItem;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuizAndGameFragment extends Fragment {

    private static final int STATUS_UNKNOWN = 0;
    private static final int STATUS_CONFUSED = 1;
    private static final int STATUS_MEMORIZED = 2;

    private TextView tvSelectedBookInfo;
    private TextView btnChangeBook;

    private TextView chipUnknown;
    private TextView chipConfused;
    private TextView chipMemorized;

    private TextView tvTargetCount;

    private MaterialCardView cardFlashcard;
    private MaterialCardView cardMultipleChoice;
    private MaterialCardView cardDictation;
    private ImageView ivFlashcardIcon;
    private ImageView ivMultipleChoiceIcon;
    private ImageView ivDictationIcon;
    private TextView tvFlashcardSubtitle;
    private TextView tvMultipleChoiceSubtitle;
    private TextView tvDictationSubtitle;

    private String uid;
    private String vocabularyId;
    private String vocabularyTitle;
    private final List<WordItem> originalWordList = new ArrayList<>();
    private final Set<Integer> selectedStatuses = new HashSet<>();

    private ListenerRegistration bookListener;
    private ListenerRegistration wordsListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_quiz_and_game, container, false);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) uid = user.getUid();

        selectedStatuses.add(STATUS_UNKNOWN);
        selectedStatuses.add(STATUS_CONFUSED);

        tvSelectedBookInfo = view.findViewById(R.id.tvSelectedBookInfo);
        btnChangeBook = view.findViewById(R.id.btnChangeBook);

        chipUnknown = view.findViewById(R.id.chipUnknown);
        chipConfused = view.findViewById(R.id.chipConfused);
        chipMemorized = view.findViewById(R.id.chipMemorized);

        tvTargetCount = view.findViewById(R.id.tvTargetCount);

        cardFlashcard = view.findViewById(R.id.cardFlashcard);
        cardMultipleChoice = view.findViewById(R.id.cardMultipleChoice);
        cardDictation = view.findViewById(R.id.cardDictation);
        ivFlashcardIcon = view.findViewById(R.id.ivFlashcardIcon);
        ivMultipleChoiceIcon = view.findViewById(R.id.ivMultipleChoiceIcon);
        ivDictationIcon = view.findViewById(R.id.ivDictationIcon);
        tvFlashcardSubtitle = view.findViewById(R.id.tvFlashcardSubtitle);
        tvMultipleChoiceSubtitle = view.findViewById(R.id.tvMultipleChoiceSubtitle);
        tvDictationSubtitle = view.findViewById(R.id.tvDictationSubtitle);

        ImageView ivBookThumbnail = view.findViewById(R.id.ivBookThumbnail);
        styleModeIcon(ivBookThumbnail, ContextCompat.getColor(requireContext(), R.color.md_theme_primaryContainer),
                ContextCompat.getColor(requireContext(), R.color.md_theme_onPrimaryContainer));

        styleModeIcon(ivFlashcardIcon, ContextCompat.getColor(requireContext(), R.color.md_theme_primaryContainer),
                ContextCompat.getColor(requireContext(), R.color.md_theme_primary));
        styleModeIcon(ivMultipleChoiceIcon, Color.parseColor("#E1F5E6"), Color.parseColor("#16A34A"));
        styleModeIcon(ivDictationIcon, Color.parseColor("#FFE9D6"), Color.parseColor("#F57C00"));

        btnChangeBook.setOnClickListener(v -> openWordbookPicker());

        chipUnknown.setOnClickListener(v -> toggleStatus(STATUS_UNKNOWN));
        chipConfused.setOnClickListener(v -> toggleStatus(STATUS_CONFUSED));
        chipMemorized.setOnClickListener(v -> toggleStatus(STATUS_MEMORIZED));

        cardFlashcard.setOnClickListener(v -> launchMode("FLASHCARD"));
        cardMultipleChoice.setOnClickListener(v -> launchMode("MULTIPLE_CHOICE"));
        cardDictation.setOnClickListener(v -> launchMode("DICTATION"));

        getChildFragmentManager().setFragmentResultListener(
                SelectVocabularyBookBottomSheet.REQUEST_KEY, this, (requestKey, bundle) -> {
                    String selectedId = bundle.getString(SelectVocabularyBookBottomSheet.RESULT_SELECTED_ID);
                    if (selectedId == null) return;
                    VocabularyFragment.saveCurrentVocabularyId(requireContext(), selectedId);
                    attachBookListener(selectedId);
                });

        loadCurrentBook();
        refreshUi();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (bookListener != null) {
            bookListener.remove();
            bookListener = null;
        }
        if (wordsListener != null) {
            wordsListener.remove();
            wordsListener = null;
        }
    }

    private void loadCurrentBook() {
        if (uid == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences(
                VocabularyFragment.PREFS_NAME, Context.MODE_PRIVATE);
        String savedId = prefs.getString(VocabularyFragment.KEY_CURRENT_VOCAB_ID, null);

        if (savedId != null) {
            attachBookListener(savedId);
        } else {
            FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("vocabularies")
                    .limit(1)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!isAdded()) return;
                        if (snapshot != null && !snapshot.isEmpty()) {
                            String firstId = snapshot.getDocuments().get(0).getId();
                            VocabularyFragment.saveCurrentVocabularyId(requireContext(), firstId);
                            attachBookListener(firstId);
                        }
                    });
        }
    }

    private void attachBookListener(String newVocabId) {
        if (bookListener != null) bookListener.remove();
        if (wordsListener != null) wordsListener.remove();

        vocabularyId = newVocabId;

        bookListener = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId)
                .addSnapshotListener((doc, e) -> {
                    if (!isAdded()) return;
                    if (e != null || doc == null || !doc.exists()) {
                        vocabularyTitle = null;
                        vocabularyId = null;
                        originalWordList.clear();
                        refreshUi();
                        return;
                    }
                    vocabularyTitle = doc.getString("title");
                    refreshUi();
                });

        attachWordsListener();
    }

    private void attachWordsListener() {
        wordsListener = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId)
                .collection("words")
                .addSnapshotListener((snapshots, e) -> {
                    if (!isAdded() || e != null || snapshots == null) return;

                    originalWordList.clear();
                    for (QueryDocumentSnapshot d : snapshots) {
                        String word = d.getString("word");
                        String meaning = d.getString("meaning");
                        String pronunciation = d.getString("pronunciation");
                        Long statusLong = d.getLong("studyStatus");
                        int studyStatus = statusLong != null ? statusLong.intValue() : STATUS_UNKNOWN;
                        if (word != null) {
                            WordItem item = new WordItem(word, meaning, pronunciation);
                            item.docId = d.getId();
                            item.studyStatus = studyStatus;
                            originalWordList.add(item);
                        }
                    }
                    refreshUi();
                });
    }

    private void toggleStatus(int status) {
        if (selectedStatuses.contains(status)) {
            selectedStatuses.remove(status);
        } else {
            selectedStatuses.add(status);
        }
        refreshUi();
    }

    private int countByStatus(int status) {
        int count = 0;
        for (WordItem item : originalWordList) {
            if (item.studyStatus == status) count++;
        }
        return count;
    }

    private List<WordItem> getTargetWordItems() {
        List<WordItem> result = new ArrayList<>();
        for (WordItem item : originalWordList) {
            if (selectedStatuses.contains(item.studyStatus)) result.add(item);
        }
        return result;
    }

    private void refreshUi() {
        if (!isAdded() || getView() == null) return;

        boolean hasBook = vocabularyId != null && vocabularyTitle != null;
        tvSelectedBookInfo.setText(hasBook
                ? vocabularyTitle + " · " + originalWordList.size() + " 단어"
                : "단어장을 선택해주세요");

        int unknownCount = countByStatus(STATUS_UNKNOWN);
        int confusedCount = countByStatus(STATUS_CONFUSED);
        int memorizedCount = countByStatus(STATUS_MEMORIZED);

        chipUnknown.setText("미학습 " + unknownCount);
        chipConfused.setText("헷갈림 " + confusedCount);
        chipMemorized.setText("학습 " + memorizedCount);

        applyChipStyle(chipUnknown, selectedStatuses.contains(STATUS_UNKNOWN));
        applyChipStyle(chipConfused, selectedStatuses.contains(STATUS_CONFUSED));
        applyChipStyle(chipMemorized, selectedStatuses.contains(STATUS_MEMORIZED));

        int targetCount = getTargetWordItems().size();
        tvTargetCount.setText("대상 " + targetCount + "개");

        tvFlashcardSubtitle.setText("약 " + estimateMinutes(targetCount, 4f) + "분 소요");
        tvMultipleChoiceSubtitle.setText("약 " + estimateMinutes(targetCount, 6f) + "분 소요");
        tvDictationSubtitle.setText("약 " + estimateMinutes(targetCount, 8f) + "분 소요");
    }

    private int estimateMinutes(int count, float secondsPerWord) {
        if (count <= 0) return 0;
        return Math.max(1, Math.round(count * secondsPerWord / 60f));
    }

    private void applyChipStyle(TextView chip, boolean selected) {
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(19 * density);

        if (selected) {
            bg.setColor(ContextCompat.getColor(requireContext(), R.color.md_theme_onPrimaryContainer));
            chip.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.WHITE);
            bg.setStroke(Math.round(1 * density), Color.parseColor("#E4E4E4"));
            chip.setTextColor(Color.parseColor("#9E9E9E"));
        }
        chip.setBackground(bg);
    }

    private void styleModeIcon(ImageView iconView, int bgColor, int tintColor) {
        GradientDrawable bg = (GradientDrawable) iconView.getBackground().mutate();
        bg.setColor(bgColor);
        iconView.setBackground(bg);
        ImageViewCompat.setImageTintList(iconView, android.content.res.ColorStateList.valueOf(tintColor));
    }

    private void openWordbookPicker() {
        SelectVocabularyBookBottomSheet bottomSheet = new SelectVocabularyBookBottomSheet();
        Bundle args = new Bundle();
        args.putBoolean("selectOnly", true);
        bottomSheet.setArguments(args);
        bottomSheet.show(getChildFragmentManager(), "SelectVocabularyBookTag");
    }

    private void launchMode(String quizType) {
        if (vocabularyId == null) {
            Toast.makeText(getContext(), "단어장을 먼저 선택해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<WordItem> targetWords = getTargetWordItems();
        if (targetWords.isEmpty()) {
            Toast.makeText(getContext(), "선택한 조건에 해당하는 단어가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<HashMap<String, Object>> serializableWords = new ArrayList<>();
        for (WordItem item : targetWords) {
            HashMap<String, Object> wordData = new HashMap<>();
            wordData.put("word", item.word);
            wordData.put("meaning", item.meaning);
            wordData.put("pronunciation", item.pronunciation);
            wordData.put("studyStatus", (long) item.studyStatus);
            serializableWords.add(wordData);
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
        intent.putExtra("isOfficial", false);
        intent.putExtra("preloadedWords", serializableWords);
        startActivity(intent);
    }
}
