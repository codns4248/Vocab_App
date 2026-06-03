package com.example.vocaapp.VocabularyBookList;

import static com.example.vocaapp.VocabularyBookList.VocabularyBookFirestore.deleteVocabularyBook;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;
import com.example.vocaapp.Test.StudyManager;
import com.example.vocaapp.VocabularyList.VocabularyFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VocabularyBookListFragment extends Fragment {

    private RecyclerView recyclerViewCurrentStudy;
    private RecyclerView recyclerViewOthers;
    private TextView tvHeaderSubtitle;
    private View currentStudySection;
    private View otherBooksSection;
    private View emptyStateLayout;
    private View scrollContainer;

    private CurrentVocabularyBookAdapter currentAdapter;
    private OtherVocabularyBookAdapter otherAdapter;

    private final ArrayList<Map<String, Object>> currentList = new ArrayList<>();
    private final ArrayList<Map<String, Object>> otherList = new ArrayList<>();

    private String uid;
    private AlertDialog addBookDialog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.test_vocabulary_book, container, false);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) uid = user.getUid();

        recyclerViewCurrentStudy = view.findViewById(R.id.recyclerViewCurrentStudy);
        recyclerViewOthers = view.findViewById(R.id.recyclerViewVocabulary);
        tvHeaderSubtitle = view.findViewById(R.id.tvHeaderSubtitle);
        currentStudySection = view.findViewById(R.id.currentStudySection);
        otherBooksSection = view.findViewById(R.id.otherBooksSection);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        scrollContainer = view.findViewById(R.id.scrollContainer);

        recyclerViewCurrentStudy.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewOthers.setLayoutManager(new LinearLayoutManager(getContext()));

        currentAdapter = new CurrentVocabularyBookAdapter(currentList, this::onBookSelected);
        otherAdapter = new OtherVocabularyBookAdapter(otherList, new OtherVocabularyBookAdapter.OnBookActionListener() {
            @Override
            public void onBookClick(Map<String, Object> book) {
                onBookSelected(book);
            }

            @Override
            public void onBookLongClick(Map<String, Object> book) {
                showDeleteConfirmDialog(book);
            }
        });
        recyclerViewCurrentStudy.setAdapter(currentAdapter);
        recyclerViewOthers.setAdapter(otherAdapter);

        FloatingActionButton fab = view.findViewById(R.id.vocabularyBookRegisterImageView);
        fab.setOnClickListener(v -> showAddBookBottomSheet());

        VocabularyBookFirestore.listenVocabularies(uid, new VocabularyBookFirestore.VocabularyListCallback() {
            @Override
            public void onUpdate(List<Map<String, Object>> newDataList) {
                currentList.clear();
                otherList.clear();
                for (Map<String, Object> book : newDataList) {
                    if (Boolean.TRUE.equals(book.get("isStudying"))) {
                        currentList.add(book);
                    } else {
                        otherList.add(book);
                    }
                }
                if (currentAdapter != null) currentAdapter.notifyDataSetChanged();
                if (otherAdapter != null) otherAdapter.notifyDataSetChanged();
                updateHeaderSubtitle(newDataList.size(), currentList.size());
                updateSectionVisibility();
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("Firestore", "Failed to listen vocabularies", e);
            }
        });

        return view;
    }

    private void updateHeaderSubtitle(int total, int studying) {
        if (tvHeaderSubtitle != null) {
            tvHeaderSubtitle.setText("총 " + total + "개 · 학습 중 " + studying + "개");
        }
    }

    private void updateSectionVisibility() {
        boolean isEmpty = currentList.isEmpty() && otherList.isEmpty();
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (scrollContainer != null) {
            scrollContainer.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        if (currentStudySection != null) {
            currentStudySection.setVisibility(currentList.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (otherBooksSection != null) {
            otherBooksSection.setVisibility(otherList.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void onBookSelected(Map<String, Object> book) {
        String selectedId = String.valueOf(book.get("id"));
        VocabularyFragment.saveCurrentVocabularyId(requireContext(), selectedId);

        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new VocabularyFragment())
                    .commit();
        }
    }

    private void showDeleteConfirmDialog(Map<String, Object> book) {
        String targetDocId = (String) book.get("docId");
        String vocabId = String.valueOf(book.get("id"));

        new AlertDialog.Builder(requireContext())
                .setTitle("단어장 삭제")
                .setMessage("삭제하면 모든 단어와 학습 데이터가 사라지며 복구할 수 없습니다. 정말 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, id) -> {
                    StudyManager.getInstance().stopStudying(uid, vocabId);
                    deleteVocabularyBook(targetDocId, uid);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showAddBookBottomSheet() {
        View dialogView = getLayoutInflater().inflate(R.layout.vocabulary_book_register_bottom_sheet, null);

        addBookDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

        if (addBookDialog.getWindow() != null) {
            addBookDialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        addBookDialog.show();

        EditText bookNameEditText = dialogView.findViewById(R.id.bookNameEditText);
        dialogView.findViewById(R.id.closeButton).setOnClickListener(v -> addBookDialog.dismiss());
        dialogView.findViewById(R.id.registerButton).setOnClickListener(v -> {
            String bookName = bookNameEditText.getText().toString().trim();
            if (bookName.isEmpty()) {
                Toast.makeText(getContext(), "단어장 이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            alreadyVocabularyBookFilter(bookName);
        });
    }

    private void alreadyVocabularyBookFilter(String bookName) {
        VocabularyBookFirestore checker = new VocabularyBookFirestore();
        checker.alreadyVocabularyBook(uid, bookName, isAlready -> {
            if (isAlready) {
                Toast.makeText(getContext(), "이미 등록된 단어장 이름입니다", Toast.LENGTH_SHORT).show();
            } else {
                saveBookToFirestore(bookName);
            }
        });
    }

    private void saveBookToFirestore(String bookName) {
        Map<String, Object> input = new HashMap<>();
        input.put("title", bookName);
        input.put("stampCount", 0);
        input.put("isStudying", false);
        input.put("wordCount", 0);
        input.put("timeStamp", FieldValue.serverTimestamp());

        VocabularyBookFirestore.addVocabularyBook(input, uid, new VocabularyBookFirestore.VocabularyBookCallback() {
            @Override
            public void onSuccess() {
                if (addBookDialog != null) addBookDialog.dismiss();
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(getContext(), "등록 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
