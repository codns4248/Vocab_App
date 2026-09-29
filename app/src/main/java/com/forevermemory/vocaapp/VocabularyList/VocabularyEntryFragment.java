package com.forevermemory.vocaapp.VocabularyList;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.forevermemory.vocaapp.R;
import com.forevermemory.vocaapp.VocabularyBookList.VocabularyBookListFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class VocabularyEntryFragment extends Fragment {
    private int requestVersion;

    public VocabularyEntryFragment() {
        super(R.layout.fragment_vocabulary_entry);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.retryVocabularyEntry).setOnClickListener(v -> loadDestination());
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDestination();
    }

    @Override
    public void onPause() {
        cancelPendingNavigation();
        super.onPause();
    }

    public void cancelPendingNavigation() {
        requestVersion++;
    }

    private boolean isCurrentRequest(int version) {
        return version == requestVersion && isResumed() && getView() != null
                && !getParentFragmentManager().isStateSaved()
                && getParentFragmentManager().findFragmentById(R.id.fragment_container) == this;
    }

    private void loadDestination() {
        if (!isResumed() || getView() == null) return;
        int version = ++requestVersion;
        getView().findViewById(R.id.vocabularyEntryProgress).setVisibility(View.VISIBLE);
        getView().findViewById(R.id.vocabularyEntryError).setVisibility(View.GONE);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showFailure(version, null);
            return;
        }
        CollectionReference books = FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid()).collection("vocabularies");
        String savedId = requireContext()
                .getSharedPreferences(VocabularyFragment.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(VocabularyFragment.KEY_CURRENT_VOCAB_ID, null);

        if (savedId == null) {
            findFirstBook(books, version);
            return;
        }
        books.document(savedId).get()
                .addOnSuccessListener(book -> {
                    if (!isCurrentRequest(version)) return;
                    if (book.exists()) {
                        openDestination(savedId, version);
                    } else {
                        VocabularyFragment.saveCurrentVocabularyId(requireContext(), null);
                        findFirstBook(books, version);
                    }
                })
                .addOnFailureListener(error -> showFailure(version, error));
    }

    private void findFirstBook(CollectionReference books, int version) {
        books.limit(1).get()
                .addOnSuccessListener(snapshot -> {
                    if (!isCurrentRequest(version)) return;
                    String id = snapshot.isEmpty() ? null : snapshot.getDocuments().get(0).getId();
                    openDestination(id, version);
                })
                .addOnFailureListener(error -> showFailure(version, error));
    }

    private void openDestination(@Nullable String bookId, int version) {
        if (!isCurrentRequest(version)) return;
        VocabularyFragment.saveCurrentVocabularyId(requireContext(), bookId);
        Fragment destination = bookId == null
                ? new VocabularyBookListFragment() : new VocabularyFragment();
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, destination)
                .commit();
    }

    private void showFailure(int version, @Nullable Exception error) {
        if (!isCurrentRequest(version)) return;
        if (error != null) Log.w("VocabularyEntry", "Failed to resolve vocabulary", error);
        getView().findViewById(R.id.vocabularyEntryProgress).setVisibility(View.GONE);
        getView().findViewById(R.id.vocabularyEntryError).setVisibility(View.VISIBLE);
    }
}
