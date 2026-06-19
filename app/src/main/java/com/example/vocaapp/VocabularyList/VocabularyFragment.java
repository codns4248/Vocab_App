package com.example.vocaapp.VocabularyList;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.Camera.CameraActivity;
import com.example.vocaapp.R;
import com.example.vocaapp.Test.StudyManager;
import com.example.vocaapp.Test.TestActivity;
import com.example.vocaapp.VocabularyBookList.VocabularyBookFirestore;
import com.example.vocaapp.VocabularyBookList.VocabularyBookListFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VocabularyFragment extends Fragment implements TextToSpeech.OnInitListener {

    public static final String PREFS_NAME = "voca_prefs";
    public static final String KEY_CURRENT_VOCAB_ID = "currentVocabularyId";

    private TextToSpeech tts;

    private String uid;
    private String vocabularyId;
    private boolean isStudying = false;
    private boolean buttonOn = false;

    private TextView tvCurrentBookTitle;
    private TextView tvWordCountStat;
    private TextView tvStudyRateStat;
    private TextView tvLastStudyStat;
    private MaterialButton btnStudyToggle;
    private MaterialButton btnStudyNow;
    private TextView btnSortToggle;
    private boolean isSortedAlphabetically = false;
    private List<WordItem> originalWordList = new ArrayList<>();
    private RecyclerView recyclerView;
    private FloatingActionButton fab;
    private FloatingActionButton fabOption1;
    private FloatingActionButton fabOption2;
    private TextView fabOption1Label;
    private TextView fabOption2Label;
    private boolean isFabOpen = false;
    private int currentWordCount = 0;
    private int currentStampCount = 0;
    private Date lastStudiedAt = null;
    private View normalContent;
    private View emptyStateLayout;

    private VocabularyListAdapter adapter;

    private ListenerRegistration bookListener;
    private ListenerRegistration wordsListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_vocabulary, container, false);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) uid = user.getUid();

        tvCurrentBookTitle = view.findViewById(R.id.tvCurrentBookTitle);
        tvWordCountStat = view.findViewById(R.id.tvWordCountStat);
        tvStudyRateStat = view.findViewById(R.id.tvStudyRateStat);
        tvLastStudyStat = view.findViewById(R.id.tvLastStudyStat);
        btnStudyToggle = view.findViewById(R.id.btnStudyToggle);
        btnStudyNow = view.findViewById(R.id.btnStudyNow);
        btnSortToggle = view.findViewById(R.id.btnSortToggle);
        recyclerView = view.findViewById(R.id.recyclerViewVocabulary);
        fab = view.findViewById(R.id.vocabularyBookRegisterImageView);
        fabOption1 = view.findViewById(R.id.fab_option1);
        fabOption2 = view.findViewById(R.id.fab_option2);
        fabOption1Label = view.findViewById(R.id.fab_option1_label);
        fabOption2Label = view.findViewById(R.id.fab_option2_label);
        normalContent = view.findViewById(R.id.normalContent);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);

        view.findViewById(R.id.btnGoToBookList).setOnClickListener(v -> openBookList());

        View headerContainer = view.findViewById(R.id.headerContainer);
        headerContainer.setOnClickListener(v -> openBookList());

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        androidx.recyclerview.widget.SimpleItemAnimator animator =
                (androidx.recyclerview.widget.SimpleItemAnimator) recyclerView.getItemAnimator();
        if (animator != null) animator.setSupportsChangeAnimations(false);

        tts = new TextToSpeech(requireContext(), this);

        fab.setOnClickListener(v -> {
            if (vocabularyId == null) {
                Toast.makeText(getContext(), "단어장을 먼저 선택해주세요.", Toast.LENGTH_SHORT).show();
                openBookList();
                return;
            }
            if (isStudying) {
                Toast.makeText(getContext(), "학습 모드 중에는 단어를 추가할 수 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isFabOpen) closeFabMenu(); else openFabMenu();
        });

        fabOption1.setOnClickListener(v -> {
            closeFabMenu();
            Intent intent = new Intent(requireContext(), CameraActivity.class);
            intent.putExtra("vocabularyId", vocabularyId);
            startActivity(intent);
        });

        fabOption2.setOnClickListener(v -> {
            closeFabMenu();
            showWordRegisterBottomSheet();
        });

        btnStudyToggle.setOnClickListener(v -> {
            if (vocabularyId == null) {
                Toast.makeText(getContext(), "단어장을 먼저 선택해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isStudying) {
                showStopStudyDialog();
            } else {
                startStudyMode();
            }
        });

        btnStudyNow.setOnClickListener(v -> {
            if (vocabularyId == null) return;
            if (!buttonOn) {
                Toast.makeText(getContext(), "아직 공부 시간이 아닙니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(requireContext(), TestActivity.class);
            intent.putExtra("vocabularyId", vocabularyId);
            startActivity(intent);
        });

        btnSortToggle.setOnClickListener(v -> {
            isSortedAlphabetically = !isSortedAlphabetically;
            btnSortToggle.setText(isSortedAlphabetically ? "알파벳 순" : "추가 순");
            applySortToAdapter();
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCurrentBook();
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
        normalContent = null;
        emptyStateLayout = null;
        adapter = null;
        vocabularyId = null;
        isStudying = false;
        buttonOn = false;
        isSortedAlphabetically = false;
        originalWordList = new ArrayList<>();
        currentWordCount = 0;
        currentStampCount = 0;
        lastStudiedAt = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    private void loadCurrentBook() {
        if (uid == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedId = prefs.getString(KEY_CURRENT_VOCAB_ID, null);

        if (savedId != null) {
            attachBookListener(savedId);
        } else {
            // 저장된 단어장이 없을 때, 첫 번째 단어장으로 자동 지정
            FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("vocabularies")
                    .limit(1)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot != null && !snapshot.isEmpty()) {
                            String firstId = snapshot.getDocuments().get(0).getId();
                            saveCurrentVocabularyId(requireContext(), firstId);
                            attachBookListener(firstId);
                        } else {
                            if (!isAdded()) return;
                            getParentFragmentManager().beginTransaction()
                                    .replace(R.id.fragment_container, new VocabularyBookListFragment())
                                    .commit();
                        }
                    });
        }
    }

    private List<WordItem> getSortedList() {
        List<WordItem> copy = new ArrayList<>(originalWordList);
        if (isSortedAlphabetically) {
            Collections.sort(copy, (a, b) -> a.word.compareToIgnoreCase(b.word));
        }
        return copy;
    }

    private void applySortToAdapter() {
        if (adapter == null) return;
        adapter.updateItems(getSortedList());
    }

    private void openFabMenu() {
        isFabOpen = true;
        fabOption1.setVisibility(View.VISIBLE);
        fabOption2.setVisibility(View.VISIBLE);
        fabOption1Label.setVisibility(View.VISIBLE);
        fabOption2Label.setVisibility(View.VISIBLE);
        float density = getResources().getDisplayMetrics().density;
        float step1 = -90 * density;
        float step2 = -160 * density;
        fabOption1.animate().translationY(step1);
        fabOption2.animate().translationY(step2);
        fabOption1Label.animate().translationY(step1);
        fabOption2Label.animate().translationY(step2);
        fab.animate().rotation(45f);
    }

    private void closeFabMenu() {
        isFabOpen = false;
        fabOption1.animate().translationY(0).withEndAction(() -> fabOption1.setVisibility(View.GONE));
        fabOption2.animate().translationY(0).withEndAction(() -> fabOption2.setVisibility(View.GONE));
        fabOption1Label.animate().translationY(0).withEndAction(() -> fabOption1Label.setVisibility(View.GONE));
        fabOption2Label.animate().translationY(0).withEndAction(() -> fabOption2Label.setVisibility(View.GONE));
        fab.animate().rotation(0f);
    }

    private void showEmptyState(boolean isEmpty) {
        if (normalContent == null || emptyStateLayout == null) return;
        normalContent.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (tvCurrentBookTitle != null) {
            tvCurrentBookTitle.setText(isEmpty ? "단어장 없음" : "");
        }
        if (isEmpty) {
            currentWordCount = 0;
            currentStampCount = 0;
            lastStudiedAt = null;
            updateHeaderStats();
        }
    }

    private void attachBookListener(String newVocabId) {
        showEmptyState(false);
        if (bookListener != null) bookListener.remove();
        if (wordsListener != null) wordsListener.remove();

        vocabularyId = newVocabId;

        bookListener = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) {
                        if (!isAdded() || getContext() == null) return;
                        // 단어장이 삭제된 경우: 저장된 ID 초기화 후 재탐색
                        saveCurrentVocabularyId(requireContext(), null);
                        if (bookListener != null) { bookListener.remove(); bookListener = null; }
                        if (wordsListener != null) { wordsListener.remove(); wordsListener = null; }
                        vocabularyId = null;
                        loadCurrentBook();
                        return;
                    }
                    String title = doc.getString("title");
                    Boolean studying = doc.getBoolean("isStudying");
                    Boolean btnOn = doc.getBoolean("buttonOn");
                    Object stampObj = doc.get("stampCount");
                    isStudying = Boolean.TRUE.equals(studying);
                    buttonOn = Boolean.TRUE.equals(btnOn);
                    currentStampCount = (stampObj instanceof Number) ? ((Number) stampObj).intValue() : 0;
                    lastStudiedAt = doc.getDate("lastStudiedAt");
                    tvCurrentBookTitle.setText(title != null ? title : "");
                    updateHeaderStats();
                    updateButtonsByState();
                });

        attachWordsListener();
    }

    private void attachWordsListener() {
        wordsListener = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId)
                .collection("words")
                .orderBy("timeStamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    List<WordItem> newList = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snapshots) {
                        String word = d.getString("word");
                        String meaning = d.getString("meaning");
                        String pronunciation = d.getString("pronunciation");
                        if (word != null) {
                            WordItem item = new WordItem(word, meaning, pronunciation);
                            item.docId = d.getId();
                            newList.add(item);
                        }
                    }
                    originalWordList = newList;
                    currentWordCount = newList.size();
                    updateHeaderStats();
                    if (adapter == null) {
                        adapter = new VocabularyListAdapter(getSortedList(), tts);
                        recyclerView.setAdapter(adapter);
                        setupSwipeController();
                    } else {
                        applySortToAdapter();
                    }
                });
    }

    private void updateButtonsByState() {
        if (btnStudyToggle == null || btnStudyNow == null) return;

        // ===== 학습 토글 버튼 =====
        // 학습 시작 전: #3b5bdb
        // 학습 진행 중: 빨강 솔리드 (#DC2626) — '학습 종료' 액션
        if (isStudying) {
            btnStudyToggle.setText("학습 종료");
            btnStudyToggle.setTextColor(0xFFFFFFFF);
            btnStudyToggle.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFDC2626));
            btnStudyToggle.setStrokeColor(
                    android.content.res.ColorStateList.valueOf(0xFFDC2626));
            btnStudyToggle.setIcon(null);
        } else {
            btnStudyToggle.setText("학습시작");
            btnStudyToggle.setTextColor(0xFFFFFFFF);
            btnStudyToggle.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF3B5BDB));
            btnStudyToggle.setStrokeColor(
                    android.content.res.ColorStateList.valueOf(0xFF3B5BDB));
            btnStudyToggle.setIcon(null);
        }

        // ===== 공부하기 버튼 =====
        // 활성화 (isStudying && buttonOn): 초록 솔리드 (#16A34A)
        // 비활성화: 회색 아웃라인
        boolean canStudyNow = isStudying && buttonOn;
        btnStudyNow.setEnabled(canStudyNow);
        if (canStudyNow) {
            btnStudyNow.setTextColor(0xFFFFFFFF);
            btnStudyNow.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF16A34A));
            btnStudyNow.setStrokeColor(
                    android.content.res.ColorStateList.valueOf(0xFF16A34A));
        } else {
            btnStudyNow.setTextColor(0xFFA3A3A3);
            btnStudyNow.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFF0F0F4));
            btnStudyNow.setStrokeColor(
                    android.content.res.ColorStateList.valueOf(0x00E5E5E5));
        }
    }

    private void updateHeaderStats() {
        if (tvWordCountStat != null) {
            tvWordCountStat.setText(currentWordCount + "개");
        }
        if (tvStudyRateStat != null) {
            int percent = Math.min(100, Math.max(0, Math.round(currentStampCount * 100f / 6f)));
            tvStudyRateStat.setText(percent + "%");
        }
        if (tvLastStudyStat != null) {
            tvLastStudyStat.setText(formatLastStudiedAt(lastStudiedAt));
        }
    }

    private String formatLastStudiedAt(Date date) {
        if (date == null) return "-";

        Calendar target = Calendar.getInstance();
        target.setTime(date);

        Calendar today = Calendar.getInstance();
        if (isSameDay(target, today)) return "오늘";

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(target, yesterday)) return "어제";

        return String.format(Locale.KOREA,
                "%d/%d",
                target.get(Calendar.MONTH) + 1,
                target.get(Calendar.DAY_OF_MONTH));
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private void openBookList() {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new VocabularyBookListFragment())
                .addToBackStack(null)
                .commit();
    }

    public static void saveCurrentVocabularyId(Context ctx, String vocabId) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CURRENT_VOCAB_ID, vocabId).apply();
    }

    private void setupSwipeController() {
        SwipeController swipeController = new SwipeController(position -> {
            if (adapter == null || position < 0 || position >= adapter.getItemCount()) {
                if (adapter != null) adapter.notifyDataSetChanged();
                return;
            }

            WordItem deletedWord = adapter.getItemAt(position);
            String wordIdToDelete = deletedWord.docId;
            int deletedPosition = position;

            adapter.removeItem(position);

            VocabularyFirestore.deleteWord(uid, vocabularyId, wordIdToDelete, () -> {}, null);

            Snackbar.make(recyclerView,
                            "'" + deletedWord.word + "' 삭제됨",
                            Snackbar.LENGTH_LONG)
                    .setAction("실행 취소", v -> {
                        adapter.addItem(deletedPosition, deletedWord);

                        Map<String, Object> wordData = new HashMap<>();
                        wordData.put("word", deletedWord.word);
                        wordData.put("meaning", deletedWord.meaning);
                        wordData.put("pronunciation", deletedWord.pronunciation);
                        wordData.put("timeStamp", FieldValue.serverTimestamp());

                        VocabularyFirestore.addWord(uid, vocabularyId, wordData, () -> {}, () -> {});
                    })
                    .show();
        });

        new ItemTouchHelper(swipeController).attachToRecyclerView(recyclerView);
    }

    private void showWordRegisterBottomSheet() {
        View dialogView = getLayoutInflater().inflate(R.layout.vocabulary_register_bottom_sheet, null);

        AlertDialog wordDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

        if (wordDialog.getWindow() != null) {
            wordDialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        wordDialog.show();

        EditText wordEditText = dialogView.findViewById(R.id.wordEditText);
        EditText meanEditText = dialogView.findViewById(R.id.meanEditText);
        EditText pronunciationEditText = dialogView.findViewById(R.id.pronunciationEditText);

        dialogView.findViewById(R.id.closeButton).setOnClickListener(v -> wordDialog.dismiss());
        dialogView.findViewById(R.id.wordRegisterButton).setOnClickListener(v -> {
            String word = wordEditText.getText().toString().trim();
            String mean = meanEditText.getText().toString().trim();
            String pronunciation = pronunciationEditText.getText().toString().trim();

            if (word.isEmpty() || mean.isEmpty()) {
                Toast.makeText(getContext(), "단어와 의미는 필수입니다", Toast.LENGTH_SHORT).show();
                return;
            }

            new VocabularyFirestore().alreadyVocabulary(uid, vocabularyId, word, isAlready -> {
                if (isAlready) {
                    Toast.makeText(getContext(), "이미 등록된 단어입니다", Toast.LENGTH_SHORT).show();
                } else {
                    Map<String, Object> wordData = new HashMap<>();
                    wordData.put("word", word);
                    wordData.put("meaning", mean);
                    wordData.put("pronunciation", pronunciation);
                    wordData.put("timeStamp", FieldValue.serverTimestamp());

                    VocabularyFirestore.addWord(uid, vocabularyId, wordData,
                            () -> {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(), "단어가 등록되었습니다.", Toast.LENGTH_SHORT).show();
                                wordEditText.setText("");
                                meanEditText.setText("");
                                pronunciationEditText.setText("");
                                wordEditText.requestFocus();
                            },
                            () -> {
                                if (isAdded()) Toast.makeText(getContext(), "등록 실패", Toast.LENGTH_SHORT).show();
                            });
                }
            });
        });
    }

    private void startStudyMode() {
        VocabularyBookFirestore.getWordCount(uid, vocabularyId, count -> {
            if (!isAdded() || getContext() == null) return;
            if (count == null || count <= 0) {
                String msg = (count == null) ? "단어장 데이터가 유효하지 않습니다." : "단어장에 단어를 추가해주세요.";
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                return;
            }
            proceedStartStudy();
        });
    }

    private void proceedStartStudy() {
        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vocabularies").document(vocabularyId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists() || !isAdded()) return;
                    String title = doc.getString("title");
                    Object stampObj = doc.get("stampCount");
                    int currentStampCount = (stampObj instanceof Number) ? ((Number) stampObj).intValue() : 0;

                    VocabularyBookFirestore.bringTime(currentStampCount, data -> {
                        if (data == null || !isAdded()) return;
                        int intervalMinutes = ((Number) data.get("interval")).intValue();
                        int graceMinutes = ((Number) data.get("grace")).intValue();

                        Calendar now = Calendar.getInstance();
                        Calendar reviewCal = (Calendar) now.clone();
                        reviewCal.add(Calendar.MINUTE, intervalMinutes);
                        Date reviewTime = reviewCal.getTime();

                        Calendar rollbackCal = (Calendar) now.clone();
                        rollbackCal.add(Calendar.MINUTE, intervalMinutes + graceMinutes);
                        Date rollbackTime = rollbackCal.getTime();

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("isStudying", true);
                        updates.put("buttonOn", false);
                        updates.put("nextReviewDate", reviewTime);
                        updates.put("stampCount", currentStampCount);
                        updates.put("rollbackTime", rollbackTime);
                        updates.put("rollbackState", false);

                        VocabularyBookFirestore.updateVocabularyBook(uid, vocabularyId, updates,
                                new VocabularyBookFirestore.VocabularyBookCallback() {
                                    @Override
                                    public void onSuccess() {
                                        if (!isAdded()) return;
                                        String msg = String.format(Locale.KOREA,
                                                "단계 %d 학습 시작! %d분 뒤 알림이 옵니다.",
                                                (currentStampCount + 1), intervalMinutes);
                                        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                                        StudyManager.getInstance().scheduleNotification(
                                                vocabularyId, title,
                                                reviewTime.getTime() / 1000,
                                                rollbackTime.getTime() / 1000);
                                    }

                                    @Override
                                    public void onFailure(Exception e) {
                                        if (isAdded()) {
                                            Toast.makeText(getContext(), "업데이트 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                    });
                });
    }

    private void showStopStudyDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("학습 초기화 경고")
                .setMessage("학습 모드를 끄면 단어를 추가할 수 있지만, 지금까지의 학습 횟수와 마지막 학습 시간이 모두 초기화됩니다. 정말 끄시겠습니까?")
                .setPositiveButton("확인", (dialog, which) -> {
                    StudyManager.getInstance().stopStudying(uid, vocabularyId);
                    FirebaseFirestore.getInstance()
                            .collection("users").document(uid)
                            .collection("vocabularies").document(vocabularyId)
                            .update("buttonOn", false);
                    if (isAdded()) {
                        Toast.makeText(getContext(), "학습 모드가 해제되고 예약된 알림이 취소되었습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    @Override
    public void onInit(int status) {
        // onInit은 비동기로 호출되므로, 그 사이 프래그먼트가 정리되어 tts가 null이 됐을 수 있음
        if (status == TextToSpeech.SUCCESS && tts != null) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (isAdded()) Toast.makeText(getContext(), "영어 음성 데이터가 없습니다", Toast.LENGTH_SHORT).show();
            } else {
                tts.setSpeechRate(0.9f);
                tts.setPitch(1.0f);
            }
        }
    }
}
