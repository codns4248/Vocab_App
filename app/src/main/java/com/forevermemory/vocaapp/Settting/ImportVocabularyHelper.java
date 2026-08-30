package com.forevermemory.vocaapp.Settting;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 엑셀 파일에서 단어를 가져온다.
 *
 * 파싱은 importVocabularyFromExcel Cloud Function이 한다. 안드로이드에서 xlsx를
 * 읽으려면 Apache POI가 필요해 앱 용량이 크게 늘기 때문이다.
 * 앱은 파일을 읽어 base64로 넘기기만 한다.
 */
public class ImportVocabularyHelper {

    /** Callable 요청 본문 상한(10MB)을 넘지 않도록 원본 기준으로 제한한다. */
    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    private ImportVocabularyHelper() {
    }

    /** 파일 선택기에서 쓸 MIME 타입. exceljs는 xlsx만 읽을 수 있다. */
    public static String[] mimeTypes() {
        return new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"};
    }

    /** 파일을 고른 뒤 호출한다. 단어장을 고르게 하고 서버로 넘긴다. */
    public static void handlePickedFile(Activity activity, Uri uri) {
        if (uri == null) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(activity, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String base64;
        try {
            base64 = readAsBase64(activity, uri);
        } catch (Exception e) {
            Toast.makeText(activity, "파일을 읽지 못했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (base64 == null) {
            Toast.makeText(activity, "파일이 너무 큽니다. (5MB 이하)", Toast.LENGTH_LONG).show();
            return;
        }

        final String fileData = base64;

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("vocabularies")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (activity.isFinishing()) return;
                    List<String> ids = new ArrayList<>();
                    List<String> labels = new ArrayList<>();
                    snapshot.getDocuments().forEach(d -> {
                        ids.add(d.getId());
                        Long c = d.getLong("wordCount");
                        String title = d.getString("title") != null ? d.getString("title") : "제목 없음";
                        labels.add(title + "  (" + (c != null ? c : 0) + "개)");
                    });
                    showBookPicker(activity, ids, labels, fileData);
                })
                .addOnFailureListener(e -> {
                    if (!activity.isFinishing()) {
                        Toast.makeText(activity, "단어장을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static void showBookPicker(Activity activity, List<String> ids,
                                       List<String> labels, String fileData) {
        // 첫 항목은 새 단어장 만들기. 기존 단어장이 없어도 가져올 수 있어야 한다.
        List<String> items = new ArrayList<>();
        items.add("+ 새 단어장 만들기");
        items.addAll(labels);

        new MaterialAlertDialogBuilder(activity)
                .setTitle("어디에 넣을까요?")
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        askNewBookTitle(activity, fileData);
                    } else {
                        upload(activity, ids.get(which - 1), null, fileData);
                    }
                })
                .setNegativeButton("취소", (d, w) -> d.dismiss())
                .show();
    }

    private static void askNewBookTitle(Activity activity, String fileData) {
        EditText input = new EditText(activity);
        input.setHint("단어장 이름");
        input.setSingleLine(true);

        int pad = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        FrameLayout wrap = new FrameLayout(activity);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input);

        new MaterialAlertDialogBuilder(activity)
                .setTitle("새 단어장 만들기")
                .setView(wrap)
                .setPositiveButton("만들기", (d, w) -> {
                    String title = input.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(activity, "단어장 이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    upload(activity, null, title, fileData);
                })
                .setNegativeButton("취소", (d, w) -> d.dismiss())
                .show();
    }

    private static void upload(Activity activity, String vocabularyId,
                               String newBookTitle, String fileData) {
        AlertDialog progress = new MaterialAlertDialogBuilder(activity)
                .setMessage("단어를 가져오는 중입니다...")
                .setCancelable(false)
                .show();

        Map<String, Object> data = new HashMap<>();
        data.put("file", fileData);
        if (newBookTitle != null) {
            data.put("newBookTitle", newBookTitle);
        } else {
            data.put("vocabularyId", vocabularyId);
        }

        FirebaseFunctions.getInstance("asia-northeast3")
                .getHttpsCallable("importVocabularyFromExcel")
                .call(data)
                .addOnSuccessListener(result -> {
                    if (activity.isFinishing()) return;
                    progress.dismiss();
                    Map<?, ?> body = (Map<?, ?>) result.getData();
                    int added = toInt(body == null ? null : body.get("added"));
                    int skipped = toInt(body == null ? null : body.get("skipped"));

                    boolean createdBook = body != null && Boolean.TRUE.equals(body.get("createdBook"));
                    String msg = added > 0
                            ? (createdBook ? "새 단어장에 " : "") + added + "개의 단어를 추가했습니다."
                            : "추가된 단어가 없습니다.";
                    if (skipped > 0) {
                        msg += "\n이미 있는 단어 " + skipped + "개는 건너뛰었습니다.";
                    }
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle("가져오기 완료")
                            .setMessage(msg)
                            .setPositiveButton("확인", (d, w) -> d.dismiss())
                            .show();
                })
                .addOnFailureListener(e -> {
                    if (activity.isFinishing()) return;
                    progress.dismiss();
                    Toast.makeText(activity, "가져오기 실패: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    /** 파일을 base64로 읽는다. 상한을 넘으면 null. */
    private static String readAsBase64(Context context, Uri uri) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("스트림 없음");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_FILE_BYTES) return null;
                out.write(buf, 0, n);
            }
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        }
    }

    private static int toInt(Object o) {
        return (o instanceof Number) ? ((Number) o).intValue() : 0;
    }
}
