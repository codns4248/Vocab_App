package com.forevermemory.vocaapp.Settting;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.forevermemory.vocaapp.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 단어장을 엑셀로 만들어 메일로 보내는 창.
 *
 * 파일 생성과 발송은 exportVocabularyToEmail Cloud Function이 담당한다.
 * 안드로이드에서 xlsx를 만들려면 무거운 라이브러리가 필요하고, 메일 발송도
 * 결국 서버 몫이라 둘 다 서버에서 처리한다.
 */
public class ExportVocabularyDialog {

    private ExportVocabularyDialog() {
    }

    public static void show(Activity activity) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(activity, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("vocabularies")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (activity.isFinishing()) return;
                    if (snapshot.isEmpty()) {
                        Toast.makeText(activity, "내보낼 단어장이 없습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<String> ids = new ArrayList<>();
                    List<String> titles = new ArrayList<>();
                    List<Long> counts = new ArrayList<>();
                    snapshot.getDocuments().forEach(d -> {
                        ids.add(d.getId());
                        titles.add(d.getString("title") != null ? d.getString("title") : "제목 없음");
                        Long c = d.getLong("wordCount");
                        counts.add(c != null ? c : 0L);
                    });
                    buildDialog(activity, user, ids, titles, counts);
                })
                .addOnFailureListener(e -> {
                    if (!activity.isFinishing()) {
                        Toast.makeText(activity, "단어장을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static void buildDialog(Activity activity, FirebaseUser user,
                                    List<String> ids, List<String> titles, List<Long> counts) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_export_vocabulary, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setCancelable(true)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        LinearLayout container = view.findViewById(R.id.bookCheckContainer);
        EditText emailEdit = view.findViewById(R.id.exportEmailEditText);
        Button cancelBtn = view.findViewById(R.id.exportCancelButton);
        Button sendBtn = view.findViewById(R.id.exportSendButton);

        // 단어장 체크박스. 기본은 전체 선택.
        List<CheckBox> boxes = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            CheckBox cb = new CheckBox(activity);
            cb.setText(titles.get(i) + "  (" + counts.get(i) + "개)");
            cb.setChecked(true);
            cb.setTextSize(14f);
            container.addView(cb);
            boxes.add(cb);
        }

        // 카카오 계정은 이메일이 없을 수 있어 비워둔다. 구글이면 채워주되 수정 가능하다.
        if (!TextUtils.isEmpty(user.getEmail())) {
            emailEdit.setText(user.getEmail());
        }

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        sendBtn.setOnClickListener(v -> {
            String email = emailEdit.getText().toString().trim();
            if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(activity, "이메일 주소를 확인해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            ArrayList<String> selected = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).isChecked()) selected.add(ids.get(i));
            }
            if (selected.isEmpty()) {
                Toast.makeText(activity, "단어장을 하나 이상 선택해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 단어가 많으면 몇 초 걸린다. 두 번 눌러 중복 발송되지 않도록 막는다.
            sendBtn.setEnabled(false);
            sendBtn.setText("보내는 중...");

            Map<String, Object> data = new HashMap<>();
            data.put("email", email);
            data.put("vocabularyIds", selected);

            FirebaseFunctions.getInstance("asia-northeast3")
                    .getHttpsCallable("exportVocabularyToEmail")
                    .call(data)
                    .addOnSuccessListener(result -> {
                        if (activity.isFinishing()) return;
                        Toast.makeText(activity,
                                email + " 으로 보냈습니다.\n메일이 안 보이면 스팸함도 확인해주세요.",
                                Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        if (activity.isFinishing()) return;
                        sendBtn.setEnabled(true);
                        sendBtn.setText("보내기");
                        Toast.makeText(activity, "발송 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });

        dialog.show();
    }
}
