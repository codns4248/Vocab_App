package com.example.vocaapp.VocabularyBookList;

import static com.example.vocaapp.VocabularyBookList.VocabularyBookFirestore.deleteVocabularyBook;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.example.vocaapp.VocabularyList.VocabularyActivity;
import com.example.vocaapp.VocabularyList.VocabularyFirestore;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VocabularyBookListFragment extends Fragment {

    private RecyclerView recyclerView;
    private VocabularyBookListAdapter adapter;
    private final ArrayList<Map<String, Object>> dataList = new ArrayList<>();
    private String uid;

    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private BottomSheetDialog bottomSheetDialog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_vocabulary_book_list, container, false);

        recyclerView = view.findViewById(R.id.recyclerViewVocabulary);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            uid = user.getUid();
        }

        ImageView vocabularyBookRegisterImageView = view.findViewById(R.id.vocabularyBookRegisterImageView);
        vocabularyBookRegisterImageView.setOnClickListener(v -> {
            bottomSheetDialog = new BottomSheetDialog(requireContext());
            View view2 = getLayoutInflater().inflate(R.layout.vocabulary_book_bottom_sheet, null);
            bottomSheetDialog.setContentView(view2);
            bottomSheetDialog.show();

            Button registerButton = view2.findViewById(R.id.registerButton);
            EditText bookNameEditText = view2.findViewById(R.id.bookNameEditText);

            // 단어장을 등록하는 처리
            registerButton.setOnClickListener( v2 -> {
                // 입력 없을 시 처리
                String bookName = bookNameEditText.getText().toString();
                if (bookName.isEmpty()){
                    Toast.makeText(getContext(), "단어장 이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 이미 존재하는 단어장 이름 검사
                alreadyVocabularyBookFilter(bookName);

            });
        });


        VocabularyBookFirestore.listenVocabularies(uid, new VocabularyBookFirestore.VocabularyListCallback() {
            @Override
            public void onUpdate(List<Map<String, Object>> newDataList) {
                Log.d("CHECK_DATA", "데이터 업데이트됨! 개수: " + newDataList.size());

                for (Map<String, Object> data : newDataList) {
                    Log.d("CHECK_DATA", "단어장 제목: " + data.get("title") + " | 학습중: " + data.get("isStudying"));
                }

                dataList.clear();
                dataList.addAll(newDataList);

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                scheduleNextStudyTime();

                for (Map<String, Object> vocab : dataList) {
                    Boolean showPopup = (Boolean) vocab.get("showRollbackPopup");
                    if (Boolean.TRUE.equals(showPopup)) {
                        String vocabId = String.valueOf(vocab.get("id"));
                        String title = String.valueOf(vocab.get("title"));

                        int currentStamp = vocab.get("stampCount") != null ? ((Number) vocab.get("stampCount")).intValue() : 0;
                        int rolledBackFrom = vocab.get("rolledBackFrom") != null ? ((Number) vocab.get("rolledBackFrom")).intValue() : (currentStamp + 1);

                        showRollbackDialog(vocabId, title, currentStamp, rolledBackFrom);
                        break;
                    }
                }
            }



            @Override
            public void onFailure(Exception e) {
                Log.e("Firestore", "Failed to listen vocabularies", e);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new VocabularyBookListAdapter(dataList, VocabularyBookListFragment.this);
        recyclerView.setAdapter(adapter);

        return view;
    }



    private void scheduleNextStudyTime() {
        if (dataList == null || dataList.isEmpty()) return;

        long now = System.currentTimeMillis();
        long closestFutureTime = Long.MAX_VALUE;

        for (Map<String, Object> vocab : dataList) {
            Boolean isStudying = (Boolean) vocab.get("isStudying");
            Timestamp nextReviewTimestamp = (Timestamp) vocab.get("nextReviewDate");

            if (Boolean.TRUE.equals(isStudying) && nextReviewTimestamp != null) {
                long reviewTime = nextReviewTimestamp.toDate().getTime();

                if (reviewTime > now && reviewTime < closestFutureTime) {
                    closestFutureTime = reviewTime;
                }
            }
        }
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        if (closestFutureTime != Long.MAX_VALUE) {
            long delay = closestFutureTime - now;

            timerRunnable = () -> {
                if (isAdded() && adapter != null){
                    adapter.notifyDataSetChanged();

                    scheduleNextStudyTime();
                }
            };
            timerHandler.postDelayed(timerRunnable, delay);
            Log.d("StudyTimer", (delay / 1000) + "초 뒤에 화면이 새로고침 됩니다.");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    public void onItemClick(int position) {
        Map<String, Object> selectedVocabulary = dataList.get(position);
        //  ID 꺼낼 때 String으로 변환
        String selectedVocabularyId = String.valueOf(selectedVocabulary.get("id"));

        //학습 모드 상태 꺼내기
        boolean isStudying = false;
        if (selectedVocabulary.get("isStudying") != null) {
            isStudying = (boolean) selectedVocabulary.get("isStudying");
        }

        Intent intent = new Intent(requireContext(), VocabularyActivity.class);
        intent.putExtra("vocabularyId", selectedVocabularyId);

        intent.putExtra("isStudying", isStudying);

        startActivity(intent);
    }

    // 학습 모드를 끌 때 띄울 경고창
    public void showResetWarningDialog(int position) {
        Map<String, Object> selectedVocabulary = dataList.get(position);
        String vocabId = String.valueOf(selectedVocabulary.get("id"));

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("학습 초기화 경고")
                .setMessage("학습 모드를 끄면 단어를 추가할 수 있지만, 지금까지의 학습 횟수와 마지막 학습 시간이 모두 초기화됩니다. 정말 끄시겠습니까?")
                .setPositiveButton("확인", (dialog, which) -> {
                    // [확인] 클릭 시: 장부 초기화 (isStudying -> false, 횟수 -> 0 등)
                    resetStudyStatus(vocabId);
                })
                .setNegativeButton("취소", (dialog, which) -> {
                    // [취소] 클릭 시: 아무것도 안 함 (스위치는 다시 켜진 상태로 유지됨)
                    adapter.notifyItemChanged(position); // 화면 새로고침해서 스위치 상태 복구
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    // Firestore 장부 초기화 로직
    private void resetStudyStatus(String vocabId) {
        // DB 작업은 Firestore 파일에서 하고, 여기선 결과만 받습니다.
        VocabularyBookFirestore.resetStudyStatus(uid, vocabId, new VocabularyBookFirestore.VocabularyBookCallback() {
            @Override
            public void onSuccess() {
                if (isAdded()) {
                    Toast.makeText(getContext(), "학습 진행 상황이 초기화되었습니다.", Toast.LENGTH_SHORT).show();
                }
                // ListenVocabularies가 작동 중이므로 화면은 자동 갱신됩니다.
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(getContext(), "초기화 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                // 실패했으니 스위치 상태를 다시 'ON'으로 돌려놓음
                adapter.notifyDataSetChanged();
            }
        });
    }

    // 학습 모드를 켤 때 호출할 메서드 (단순 업데이트)
    public void startStudyMode(int position) {
        Map<String, Object> selectedVocabulary = dataList.get(position);
        String vocabId = String.valueOf(selectedVocabulary.get("id"));

        // 1. 처음 켜는 건지(스탬프가 0개인지) 확인
        boolean isFirstTime = false;
        long stampCount = 0;
        if (selectedVocabulary.containsKey("stampCount") && selectedVocabulary.get("stampCount") != null) {
            try {
                stampCount = Long.parseLong(String.valueOf(selectedVocabulary.get("stampCount")));
            } catch (Exception e) {
                stampCount = 0;
            }
        }
        if (stampCount == 0) {
            isFirstTime = true;
        }

        // 2. DB에 업데이트할 데이터 세팅
        Map<String, Object> updates = new HashMap<>();
        updates.put("isStudying", true);
        updates.put("isReviewReady", false);

        // 다음 복습 시간을 '지금 당장'으로 설정!
        if (isFirstTime) {
            updates.put("nextReviewDate", new com.google.firebase.Timestamp(new java.util.Date()));
        }

        // 3. Firestore 업데이트 실행
        VocabularyBookFirestore.updateVocabularyBook(uid, vocabId, updates, new VocabularyBookFirestore.VocabularyBookCallback() {
            @Override
            public void onSuccess() {
                if (isAdded()) {
                    Toast.makeText(getContext(), "학습 모드가 시작되었습니다.", Toast.LENGTH_SHORT).show();
                    Log.d("StudyMode", "학습 모드 ON! (첫 시작이면 바로 학습창이 뜹니다)");
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (isAdded()) {
                    Toast.makeText(getContext(), "학습 모드 켜기 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // 단어장 삭제를 위한 팝업
    public void showDeleteConfirmDialog(int position) {
        // 1. 다이얼로그를 띄우기 직전에 미리 docId를 추출합니다.
        if (dataList == null || position < 0 || position >= dataList.size()) {
            return; // 데이터가 없으면 다이얼로그 자체를 띄우지 않음
        }

        // 미리 꺼내두기 (final 상수로 선언하여 리스너 내부에서 사용 가능하게 함)
        final String targetDocId = (String) dataList.get(position).get("docId");

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("삭제 확인");
        builder.setMessage("삭제를 진행하면 단어장을 복구할 수 없습니다.\n정말 삭제하시겠습니까?");

        builder.setPositiveButton("삭제", (dialog, id) -> {
            // 2. 리스너 내부에서는 리스트를 다시 참조하지 않고, 미리 꺼내둔 targetDocId를 사용합니다.
            deleteVocabularyBook(targetDocId, uid);
        });

        builder.setNegativeButton("취소", null);
        builder.show();
    }

    //롤백 팝업
    private void showRollbackDialog(String vocabId, String vocabTitle, int currentStamp, int rolledBackFrom) {
        if (!isAdded()) return;

        //1. 다이얼로그 뷰 생성
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_rollback, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);

        //2. 텍스트
        TextView tvMessage = dialogView.findViewById(R.id.tv_rollback_message);
        tvMessage.setText("[" + vocabTitle + "]\n단어장이 " + currentStamp + "단계로 롤백 되었습니다.");

        //3.스탬프
        ImageView[] dialogStamps = new ImageView[]{
                dialogView.findViewById(R.id.dialog_stamp1),
                dialogView.findViewById(R.id.dialog_stamp2),
                dialogView.findViewById(R.id.dialog_stamp3),
                dialogView.findViewById(R.id.dialog_stamp4),
                dialogView.findViewById(R.id.dialog_stamp5),
                dialogView.findViewById(R.id.dialog_stamp6),
                dialogView.findViewById(R.id.dialog_stamp7),
        };

        //4.스챔프 색칠
        for (int i = 0; i < 7; i++) {
            if (i < currentStamp) {  //현개 스탬프
                dialogStamps[i].setImageResource(R.drawable.checked_stamp_icon);
                dialogStamps[i].setAlpha(1.0f);
            } else if (i == currentStamp && i < rolledBackFrom) { //깎임 스탬프
                dialogStamps[i].setImageResource(R.drawable.unchecked_stamp_icon);
                dialogStamps[i].setAlpha(0.3f);
            } else {  //원래 빈 스탬프
                dialogStamps[i].setImageResource(R.drawable.unchecked_stamp_icon);
                dialogStamps[i].setAlpha(1.0f);
            }
        }

        //5. 확인 버튼 눌렀을 때 db에서 플러그 삭제
        Button btnConfirm = dialogView.findViewById(R.id.btn_rollback_confirm);
        btnConfirm.setOnClickListener(v -> {
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            db.collection("users").document(uid)
                    .collection("vocabularies").document(vocabId)
                    .update(
                            "showRollbackPopup", FieldValue.delete(),
                            "rolledBackFrom", FieldValue.delete()
                    ).addOnSuccessListener(aVoid -> {
                        dialog.dismiss();
                    });
        });
        dialog.show();
    }

    // 이미 존재하는 단어 검사하는 메서드
    private void alreadyVocabularyBookFilter(String bookName){
        VocabularyBookFirestore alreadyVocabularyBookFirestore = new VocabularyBookFirestore();
        alreadyVocabularyBookFirestore.alreadyVocabularyBook(uid, bookName, isAlready ->  {
            if (isAlready){
                Toast.makeText(getContext(), "이미 등록된 단어장 이름입니다", Toast.LENGTH_SHORT).show();
            }
            else{
                saveWordToFirestore(bookName);
            }
        });
    }

    // 실제 Firestore 저장 로직을 분리
    private void saveWordToFirestore(String bookName) {
        Map<String, Object> inputVocabularyBookName = new HashMap<>();
        inputVocabularyBookName.put("title", bookName);
        inputVocabularyBookName.put("stampCount", 0);
        inputVocabularyBookName.put("isStudying",false);
        inputVocabularyBookName.put("isReviewReady", false);

        VocabularyBookFirestore.addVocabularyBook(inputVocabularyBookName, uid, new VocabularyBookFirestore.VocabularyBookCallback() {
            @Override
            public void onSuccess() {
                bottomSheetDialog.dismiss();
            }
            @Override
            public void onFailure(Exception e) {
                Toast.makeText(getContext(), "등록 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

}