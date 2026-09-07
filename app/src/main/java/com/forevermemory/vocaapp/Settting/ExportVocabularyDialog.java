package com.forevermemory.vocaapp.Settting;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
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
 * 단어장을 엑셀 파일로 만들어 내려받는 창.
 *
 * 파일 생성과 보관은 exportVocabularyFile Cloud Function이 담당한다.
 * 서버가 Firebase Storage에 올린 뒤 시간제한이 걸린 링크를 돌려주고,
 * 앱은 그 링크를 브라우저로 열어 내려받는다.
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
                    buildDialog(activity, ids, titles, counts);
                })
                .addOnFailureListener(e -> {
                    if (!activity.isFinishing()) {
                        Toast.makeText(activity, "단어장을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static void buildDialog(Activity activity, List<String> ids,
                                    List<String> titles, List<Long> counts) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_export_vocabulary, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setCancelable(true)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        LinearLayout container = view.findViewById(R.id.bookCheckContainer);
        Button cancelBtn = view.findViewById(R.id.exportCancelButton);
        Button sendBtn = view.findViewById(R.id.exportSendButton);

        List<CheckBox> boxes = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            CheckBox cb = new CheckBox(activity);
            cb.setText(titles.get(i) + "  (" + counts.get(i) + "개)");
            cb.setChecked(true);
            cb.setTextSize(14f);
            container.addView(cb);
            boxes.add(cb);
        }

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        sendBtn.setOnClickListener(v -> {
            ArrayList<String> selected = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).isChecked()) selected.add(ids.get(i));
            }
            if (selected.isEmpty()) {
                Toast.makeText(activity, "단어장을 하나 이상 선택해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 단어가 많으면 몇 초 걸린다. 두 번 눌러 중복 생성되지 않도록 막는다.
            sendBtn.setEnabled(false);
            sendBtn.setText("만드는 중...");

            Map<String, Object> data = new HashMap<>();
            data.put("vocabularyIds", selected);

            FirebaseFunctions.getInstance("asia-northeast3")
                    .getHttpsCallable("exportVocabularyFile")
                    .call(data)
                    .addOnSuccessListener(result -> {
                        if (activity.isFinishing()) return;
                        dialog.dismiss();
                        Map<?, ?> body = (Map<?, ?>) result.getData();
                        String url = body == null ? null : (String) body.get("url");
                        if (url == null) {
                            Toast.makeText(activity, "파일을 만들지 못했습니다.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showReadyDialog(activity, url,
                                toInt(body.get("wordCount")), toInt(body.get("validHours")));
                    })
                    .addOnFailureListener(e -> {
                        if (activity.isFinishing()) return;
                        sendBtn.setEnabled(true);
                        sendBtn.setText("만들기");
                        Toast.makeText(activity, "실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });

        dialog.show();
    }

    private static void showReadyDialog(Activity activity, String url, int wordCount, int validHours) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("엑셀 파일이 준비됐어요")
                .setMessage("단어 " + wordCount + "개가 담겼습니다.\n"
                        + "다운로드 링크는 " + validHours + "시간 동안 유효합니다.")
                .setPositiveButton("다운로드", (d, w) -> openUrl(activity, url))
                .setNeutralButton("링크 공유", (d, w) -> shareUrl(activity, url))
                .setNegativeButton("닫기", (d, w) -> d.dismiss())
                .show();
    }

    private static void openUrl(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "링크를 열 앱이 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private static void shareUrl(Activity activity, String url) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "단어장 엑셀 파일");
        share.putExtra(Intent.EXTRA_TEXT, url);
        activity.startActivity(Intent.createChooser(share, "링크 공유"));
    }

    private static int toInt(Object o) {
        return (o instanceof Number) ? ((Number) o).intValue() : 0;
    }
}
