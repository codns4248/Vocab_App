package com.example.vocaapp.VocabularyList;

import android.content.Intent;
import android.graphics.Canvas;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.example.vocaapp.Camera.CameraActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VocabularyActivity extends AppCompatActivity {

    private String vocabularyId;
    private boolean isStudying = false; //학습 상 태 저장용 변수 추가
    VocabularyListAdapter adapter;

    List<String> words = new ArrayList<>();
    List<String> meanings = new ArrayList<>();
    List<String> pronunciations = new ArrayList<>();
    List<String> comments = new ArrayList<>();

    List<String> wordIds = new ArrayList<>(); // 삭제할 때 필요한 단어 ID 리스트
    String uid; // uid를 전역에서 쓰기 위해 선언

    private FloatingActionButton fab, fabOption1, fabOption2;
    private boolean isFabOpen = false;
    private TextView fabOption1Label, fabOption2Label;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vocabulary_detail);

        isStudying = getIntent().getBooleanExtra("isStudying", false);
        vocabularyId = getIntent().getStringExtra("vocabularyId");

        // FAB 초기화
        fab = findViewById(R.id.fab);
        fabOption1 = findViewById(R.id.fab_option1);
        fabOption2 = findViewById(R.id.fab_option2);
        fabOption1Label = findViewById(R.id.fab_option1_label);
        fabOption2Label = findViewById(R.id.fab_option2_label);

        // 뒤로가기 처리
        ImageView backImageView = findViewById(R.id.backImageView);
        backImageView.setOnClickListener(v -> {
            finish();
        });


        if (isStudying) {
            // 1.  배경색 변경 (setSupportBackgroundTintList 또는 setBackgroundTintList 사용)
            // 회색(LTGRAY)으로 배경색을 직접 바꿉니다.
            fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.LTGRAY));

            // 2. 아이콘 색상 변경 (선택사항: 아이콘도 어둡게 만들고 싶을 때)
            fab.setColorFilter(android.graphics.Color.DKGRAY);

            // 3. 투명도 조절
            fab.setAlpha(0.5f);
            fabOption1.setAlpha(0.5f);
            fabOption2.setAlpha(0.5f);
        }

        // 메인 FAB 클릭 리스너
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isStudying) {
                    // 학습 모드일 땐 메뉴를 열지 않고 바로 안내 메시지
                    Toast.makeText(VocabularyActivity.this, "학습 모드 중에는 단어를 추가할 수 없습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    // 학습 모드가 아닐 때만 기존처럼 메뉴를 열거나 닫음
                    if (isFabOpen) {
                        closeFabMenu();
                    } else {
                        openFabMenu();
                    }
                }
            }
        });

        // 옵션1 클릭 리스너
        fabOption1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isStudying) {
                    Toast.makeText(VocabularyActivity.this,"학습 모드 중에는 단어를 추가할 수 없습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    Intent intent = new Intent(VocabularyActivity.this, CameraActivity.class);
                    intent.putExtra("vocabularyId", vocabularyId);
                    startActivity(intent);
                }
                closeFabMenu();
            }
        });

        // 옵션2 클릭 리스너
        fabOption2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isStudying) {
                    Toast.makeText(VocabularyActivity.this, "학습 모드 중에는 단어를 추가할 수 없습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    showWordRegisterBottomSheet();
                }
                closeFabMenu();
            }
        });

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            return;
        }

        uid = user.getUid();

        VocabularyFirestore.listenWords(uid, vocabularyId, new VocabularyFirestore.OnWordsChanged() {
            @Override
            public void onChanged(QuerySnapshot snapshots) {
                List<WordItem> newWordList = new ArrayList<>();
                wordIds.clear();

                for (DocumentSnapshot document : snapshots) {
                    wordIds.add(document.getId());

                    // 데이터 매핑
                    String word = document.getString("word");
                    String meaning = document.getString("meaning");
                    String pronunciation = document.getString("pronunciation");

                    if (word != null) {
                        newWordList.add(new WordItem(word, meaning, pronunciation));
                    }
                }

                // 어댑터 갱신 로직
                if (adapter == null) {
                    adapter = new VocabularyListAdapter(newWordList);
                    recyclerView.setAdapter(adapter);
                    setupSwipeController(recyclerView);
                } else {
                    adapter.updateItems(newWordList);
                }
            }

            // 🔴 이 부분이 빠져서 에러가 났던 것입니다!
            @Override
            public void onError(Exception e) {
                Log.e("FirestoreError", "단어 목록을 불러오는 중 에러 발생: " + e.getMessage());
                // 사용자에게 알림을 주고 싶다면 토스트 추가
                // Toast.makeText(VocabularyActivity.this, "데이터 로딩 실패", Toast.LENGTH_SHORT).show();
            }
        });


        // LayoutManager 설정
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        androidx.recyclerview.widget.SimpleItemAnimator animator =           //삭제시 깜빡임(잔상) 수정
                (androidx.recyclerview.widget.SimpleItemAnimator) recyclerView.getItemAnimator();
        if (animator != null) {
            animator.setSupportsChangeAnimations(false);
        }
    }

    // FAB 메뉴 열기
    private void openFabMenu() {
        isFabOpen = true;
        fabOption1.setVisibility(View.VISIBLE);
        fabOption2.setVisibility(View.VISIBLE);
        fabOption1Label.setVisibility(View.VISIBLE);
        fabOption2Label.setVisibility(View.VISIBLE);

        // 위로 펼치는 애니메이션
        fabOption1.animate().translationY(-200f);
        fabOption2.animate().translationY(-400f);
        fabOption1Label.animate().translationY(-200f);
        fabOption2Label.animate().translationY(-400f);
        fab.animate().rotation(45f);
    }

    // FAB 메뉴 닫기
    private void closeFabMenu() {
        isFabOpen = false;

        fabOption1.animate().translationY(0);
        fabOption2.animate().translationY(0);
        fabOption1Label.animate().translationY(0);
        fabOption2Label.animate().translationY(0);
        fab.animate().rotation(0f);

        fabOption1.setVisibility(View.GONE);
        fabOption2.setVisibility(View.GONE);
        fabOption1Label.setVisibility(View.GONE);
        fabOption2Label.setVisibility(View.GONE);
    }


    private void showWordRegisterBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(VocabularyActivity.this);

        View view = getLayoutInflater()
                .inflate(R.layout.vocabulary_register_bottom_sheet, null);

        bottomSheetDialog.setContentView(view);

        // 키보드가 올라올 때 BottomSheet가 키보드 위에 붙도록 설정
        if (bottomSheetDialog.getWindow() != null) {
            bottomSheetDialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            );
        }

        // BottomSheet를 처음부터 EXPANDED 상태로 설정
        bottomSheetDialog.setOnShowListener(dialog -> {
            View bottomSheet = bottomSheetDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet
            );
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        bottomSheetDialog.show();

        EditText wordEditText = view.findViewById(R.id.wordEditText);
        EditText meanEditText = view.findViewById(R.id.meanEditText);
        EditText pronunciationEditText = view.findViewById(R.id.pronunciationEditText);
        Button wordRegisterButton = view.findViewById(R.id.wordRegisterButton);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            // 로그인 안 된 상태
            return;
        }

        // 단어 등록 처리
        wordRegisterButton.setOnClickListener(v -> {
            // 입력 단어 가져오기
            String word = wordEditText.getText().toString().trim();
            String mean = meanEditText.getText().toString().trim();
            String pronunciation = pronunciationEditText.getText().toString().trim();

            // 유효성 검사
            if (word.isEmpty() || mean.isEmpty()) {
                Toast.makeText(this, "단어와 의미는 필수입니다", Toast.LENGTH_SHORT).show();
                return;
            }

            alreadyVocabularyFilter(word, mean, pronunciation);

            // 유효성 검사와 db 단어 등록 완료 시 글자 초기화
            wordEditText.setText("");
            meanEditText.setText("");
            pronunciationEditText.setText("");
        });

    }
    private void setupSwipeController(RecyclerView recyclerView) {
        SwipeController swipeController = new SwipeController(new SwipeController.SwipeControllerActions() {
            @Override
            public void onRightClicked(int position) {
                // [안전 장치] 위치가 유효한지 확인
                if (position < 0 || position >= wordIds.size()) {
                    if (adapter != null) adapter.notifyDataSetChanged();
                    return;
                }

                // 1. 지울 단어의 ID를 미리 백업해둡니다 (화면에서 지워지면 못 찾으니까요)
                String wordIdToDelete = wordIds.get(position);

                // 2. 화면(UI)과 메모리 리스트에서 '먼저' 삭제
                wordIds.remove(position); // 액티비티의 ID 리스트에서 삭제
                if (adapter != null){
                    adapter.removeItem(position); // 어댑터의 데이터 리스트 삭제 + 애니메이션 수행
                }

                // 단어 삭제 요청
                VocabularyFirestore.deleteWord(uid, vocabularyId, wordIdToDelete,
                        () -> {
                            // 성공 시 처리
                            Toast.makeText(VocabularyActivity.this, "삭제되었습니다.", Toast.LENGTH_SHORT).show();
                            // 필요하다면 여기서 리스트를 새로고침하는 로직을 추가하세요.
                        },
                        null // 실패 처리가 필요 없다면 null 전달
                );
            }
        });

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeController);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                swipeController.onDraw(c);
            }
        });
    }

    // 이미 존재하는 단어 검사하는 메서드
    private void alreadyVocabularyFilter(String word, String mean, String pronunciation){
        VocabularyFirestore alreadyVocabularyFirestore = new VocabularyFirestore();
        alreadyVocabularyFirestore.alreadyVocabulary(uid, vocabularyId, word, isAlready -> {
            if (isAlready){
                Toast.makeText(this, "이미 등록된 단어입니다", Toast.LENGTH_SHORT).show();
            }
            else{
                saveWordToFirestore(word, mean, pronunciation);
            }
        });
    }

    // 실제 Firestore 저장 로직을 분리
    private void saveWordToFirestore(String word, String mean, String pronunciation) {
        Map<String, Object> wordData = new HashMap<>();
        wordData.put("word", word);
        wordData.put("meaning", mean);
        wordData.put("pronunciation", pronunciation);
        wordData.put("timeStamp", FieldValue.serverTimestamp());

        VocabularyFirestore.addWord(uid, vocabularyId, wordData, () -> {
            Toast.makeText(this, "단어가 등록되었습니다.", Toast.LENGTH_SHORT).show();
        }, () -> {
            Toast.makeText(this, "등록 실패", Toast.LENGTH_SHORT).show();
        });
    }
}
