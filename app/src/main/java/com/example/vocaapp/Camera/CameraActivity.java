package com.example.vocaapp.Camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocaapp.R;
import com.example.vocaapp.VocabularyList.VocabularyFirestore;
import com.example.vocaapp.VocabularyList.WordItem;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class CameraActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ImageView captureImageView, backImageView;

    private RecyclerView photoRecyclerView;
    private CameraAdapter photoAdapter;
    private List<Bitmap> photoList;
    private TextView finishTextView;
    private ImageView galleryImageView;

    // 갤러리에서 여러 장의 사진을 선택하는 런처 (Photo Picker)
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(5), uris -> {
                if (uris == null || uris.isEmpty()) {
                    return;
                }
                for (Uri uri : uris) {
                    Bitmap bitmap = loadBitmapFromUri(uri);
                    if (bitmap != null) {
                        photoAdapter.addPhoto(bitmap, CameraActivity.this);
                    }
                }
            });

    private final FirebaseFunctions mFunctions = FirebaseFunctions.getInstance("asia-northeast3");

    private String vocabularyId;
    private String uid;
    private FirebaseUser user;

    private View loadingOverlay;
    private TextView loadingText;
    private TextView loadingSubText;

    // 로딩 문구 타자 효과용
    private final android.os.Handler typingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final String LOADING_FULL_TEXT = "단어를 추출하고 있어요...";
    private int typingIndex = 0;
    private final Runnable typingRunnable = new Runnable() {
        @Override
        public void run() {
            typingIndex++;
            if (typingIndex > LOADING_FULL_TEXT.length()) {
                // 다 입력되면 잠깐 멈췄다가 처음부터 다시 (반복)
                typingIndex = 0;
                loadingText.setText("");
                typingHandler.postDelayed(this, 700);
                return;
            }
            loadingText.setText(LOADING_FULL_TEXT.substring(0, typingIndex));
            typingHandler.postDelayed(this, 120);
        }
    };

    // 로딩 중 보조 안내. 한 줄만 두고 번갈아 보여준다.
    // 여러 줄을 동시에 띄우면 화면이 지저분해진다.
    private static final String[] LOADING_TIPS = {
            "단어가 많을 경우 시간이 더 걸릴 수 있어요.",
            "AI가 모든 단어를 찾지 못할 수 있어요.",
            "빠진 단어는 다음 화면에서 직접 추가할 수 있어요.",
    };
    private static final long TIP_INTERVAL_MS = 3000;
    private int tipIndex = 0;
    private final Runnable tipRunnable = new Runnable() {
        @Override
        public void run() {
            tipIndex = (tipIndex + 1) % LOADING_TIPS.length;
            if (loadingSubText != null) {
                // 툭 바뀌지 않도록 페이드 아웃 후 교체하고 페이드 인
                loadingSubText.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                    loadingSubText.setText(LOADING_TIPS[tipIndex]);
                    loadingSubText.animate().alpha(1f).setDuration(250).start();
                }).start();
            }
            typingHandler.postDelayed(this, TIP_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // 사진 리스트 초기화
        photoList = new ArrayList<>();

        // RecyclerView 설정
        photoRecyclerView = findViewById(R.id.photoRecyclerView);
        photoAdapter = new CameraAdapter(photoList);

        previewView = findViewById(R.id.previewView);
        captureImageView = findViewById(R.id.captureImageView);
        backImageView = findViewById(R.id.backImageView);
        finishTextView = findViewById(R.id.finishTextView);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingText = findViewById(R.id.loadingText);
        loadingSubText = findViewById(R.id.loadingSubText);

        vocabularyId = getIntent().getStringExtra("vocabularyId");

        user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null){
            return;
        }

        uid = user.getUid();

        // 권한 체크
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }

        captureImageView.setOnClickListener(v -> takePhoto());

        backImageView.setOnClickListener(v -> finish());

        // 갤러리에서 사진 가져오기 (이미지만 선택)
        galleryImageView = findViewById(R.id.galleryImageView);
        galleryImageView.setOnClickListener(v ->
                pickMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        // LayoutManager 설정 (가로 스크롤)
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                this,
                LinearLayoutManager.HORIZONTAL,
                false
        );

        photoRecyclerView.setLayoutManager(layoutManager);
        photoRecyclerView.setAdapter(photoAdapter);

        // 단어 추출(Claude 호출)은 extractWordsFromImages Cloud Function이 담당합니다.
        // API 키를 앱에 두지 않기 위한 구조이며, 출력 스키마도 함수 쪽에 정의돼 있습니다.

        finishTextView.setOnClickListener(v -> extractData());

    }

    // AI 단어 등록 시 사진 1장당 차감되는 포인트.
    // 서버는 사진을 장별로 나눠 Claude API를 호출하므로 원가가 장수에 비례한다.
    // 요청당 고정으로 두면 5장 올리는 사용자가 1장 사용자의 5배를 쓰고도 같은 값을 낸다.
    private static final int AI_EXTRACT_COST_PER_PHOTO = 20;

    // 사진에서 단어를 추출하는 method
    private void extractData() {

        if (photoList.isEmpty()) {
            Toast.makeText(this, "먼저 사진을 촬영해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 사진 장수만큼의 포인트가 있는지 먼저 확인 후 추출 진행
        final int requiredPoint = AI_EXTRACT_COST_PER_PHOTO * photoList.size();

        showLoading();
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    long point = (snapshot.exists() && snapshot.get("point") != null)
                            ? snapshot.getLong("point") : 0;
                    if (point < requiredPoint) {
                        hideLoading();
                        showInsufficientPointDialog(requiredPoint, point);
                        return;
                    }
                    runExtraction();
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    Toast.makeText(this, "포인트 확인 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // 로딩 오버레이 표시 + 타자 효과 시작
    private void showLoading() {
        loadingOverlay.setVisibility(View.VISIBLE);
        typingHandler.removeCallbacks(typingRunnable);
        typingIndex = 0;
        loadingText.setText("");
        typingHandler.post(typingRunnable);

        // 보조 안내는 첫 문구를 보여준 뒤 일정 간격으로 넘긴다.
        typingHandler.removeCallbacks(tipRunnable);
        tipIndex = 0;
        if (loadingSubText != null) {
            loadingSubText.setAlpha(1f);
            loadingSubText.setText(LOADING_TIPS[0]);
        }
        typingHandler.postDelayed(tipRunnable, TIP_INTERVAL_MS);
    }

    // 로딩 오버레이 숨김 + 타자 효과/안내 순환 중단
    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
        typingHandler.removeCallbacks(typingRunnable);
        typingHandler.removeCallbacks(tipRunnable);
        if (loadingSubText != null) {
            // 페이드 도중에 멈출 수 있으므로 다음 표시를 위해 알파를 되돌린다.
            loadingSubText.animate().cancel();
            loadingSubText.setAlpha(1f);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        typingHandler.removeCallbacks(typingRunnable);
        typingHandler.removeCallbacks(tipRunnable);
    }

    // 포인트 부족 안내 팝업 (확인 / 결제하기)
    private void showInsufficientPointDialog(int requiredPoint, long currentPoint) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_insufficient_point, null);

        // 사진 장수에 따라 필요 포인트가 달라지므로 얼마가 왜 필요한지 알려준다.
        TextView messageText = dialogView.findViewById(R.id.messageText);
        if (messageText != null) {
            messageText.setText("사진 " + photoList.size() + "장 분석에 " + requiredPoint + "P가 필요해요.\n"
                    + "보유 포인트는 " + currentPoint + "P입니다.");
        }

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // 팝업 배경을 투명하게 해서 둥근 모서리가 보이도록 처리
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 확인: 팝업 닫기
        dialogView.findViewById(R.id.cancelBtn).setOnClickListener(v -> dialog.dismiss());

        // 결제하기: 결제 로직은 추후 구현 예정 (현재는 닫기만)
        dialogView.findViewById(R.id.chargeBtn).setOnClickListener(v -> {
            // TODO: 포인트 결제/충전 로직 연결
            dialog.dismiss();
        });

        dialog.show();
    }

    // 실제 추출을 수행하는 method (이미지 인코딩 → Cloud Function 호출)
    private void runExtraction() {
        // 리사이즈 + JPEG 압축 + Base64 인코딩은 무거우므로 백그라운드에서 처리
        new Thread(() -> {
            ArrayList<String> encodedImages = new ArrayList<>();
            try {
                for (Bitmap photo : photoList) {
                    Bitmap resized = getResizedBitmap(photo, 1024);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    resized.compress(Bitmap.CompressFormat.JPEG, 80, out);
                    encodedImages.add(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
                }
            } catch (Exception e) {
                Log.e("EncodeError", "이미지 인코딩 실패: " + e.getMessage());
                runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(CameraActivity.this, "사진 처리에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            runOnUiThread(() -> requestWordExtraction(encodedImages));
        }).start();
    }

    // extractWordsFromImages 함수 호출 (Claude 호출은 서버에서 이뤄짐)
    private void requestWordExtraction(ArrayList<String> encodedImages) {
        Map<String, Object> funcData = new HashMap<>();
        funcData.put("images", encodedImages);

        mFunctions.getHttpsCallable("extractWordsFromImages")
                .call(funcData)
                .addOnSuccessListener(result -> {
                    List<WordItem> wordList;
                    int failedImages;
                    try {
                        // 응답은 { "words": [ {word, meaning, pronunciation}, ... ],
                        //          "failedImageCount": n } 형태
                        Map<?, ?> data = (Map<?, ?>) result.getData();
                        Gson gson = new Gson();
                        String wordsJson = gson.toJson(data.get("words"));
                        java.lang.reflect.Type listType =
                                new com.google.gson.reflect.TypeToken<ArrayList<WordItem>>(){}.getType();
                        wordList = gson.fromJson(wordsJson, listType);

                        // 서버가 장별로 호출하므로 일부만 실패할 수 있다.
                        // 실패한 장은 결과가 없으니 포인트도 받지 않는다.
                        Object failedRaw = data.get("failedImageCount");
                        failedImages = (failedRaw instanceof Number) ? ((Number) failedRaw).intValue() : 0;
                    } catch (Exception e) {
                        Log.e("ParsingError", "파싱 실패: " + e.getMessage());
                        hideLoading();
                        Toast.makeText(CameraActivity.this, "분석 결과를 읽지 못했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    hideLoading();

                    if (wordList != null && !wordList.isEmpty()) {
                        // 성공한 사진 장수만큼만 차감한다.
                        int chargedPhotos = Math.max(photoList.size() - failedImages, 0);
                        if (chargedPhotos > 0) {
                            FirebaseFirestore.getInstance().collection("users").document(uid)
                                    .update("point", FieldValue.increment(
                                            (long) -AI_EXTRACT_COST_PER_PHOTO * chargedPhotos));
                        }

                        if (failedImages > 0) {
                            Toast.makeText(CameraActivity.this,
                                    "사진 " + failedImages + "장은 분석하지 못했습니다. 해당 사진의 포인트는 차감되지 않았습니다.",
                                    Toast.LENGTH_LONG).show();
                        }

                        Intent intent = new Intent(CameraActivity.this, WordSelectActivity.class);
                        intent.putParcelableArrayListExtra("wordList", new ArrayList<>(wordList));
                        intent.putExtra("vocabularyId", vocabularyId);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(CameraActivity.this, "추출된 단어가 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ExtractError", "에러 발생: " + e.getMessage());
                    hideLoading();
                    Toast.makeText(CameraActivity.this, "분석 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private Bitmap getResizedBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }

        // 원본 비트맵의 비율을 유지하며 리사이징
        return Bitmap.createScaledBitmap(image, width, height, true);
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "카메라 시작 실패: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        // Preview 설정
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // ImageCapture 설정
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        // 후면 카메라 선택
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        // 기존 바인딩 해제
        cameraProvider.unbindAll();

        // 카메라 바인딩
        try {
            cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
            );
        } catch (Exception e) {
            Toast.makeText(this, "카메라 바인딩 실패: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        if (imageCapture == null) {
            return;
        }

        // 메모리에서 직접 이미지 캡처 (파일 저장 대신)
        imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        // ImageProxy를 Bitmap으로 변환
                        Bitmap bitmap = imageProxyToBitmap(image);

                        // RecyclerView에 사진 추가
                        photoAdapter.addPhoto(bitmap, CameraActivity.this);

                        // 이미지 닫기
                        image.close();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(CameraActivity.this,
                                "사진 촬영 실패: " + exception.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    // 갤러리 Uri를 Bitmap으로 변환하는 메서드 (EXIF 회전 정보 반영)
    private Bitmap loadBitmapFromUri(Uri uri) {
        try {
            // 1. 비트맵 디코딩
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (is != null) is.close();
            if (bitmap == null) return null;

            // 2. EXIF 회전 정보 읽어서 보정
            int degrees = 0;
            InputStream exifStream = getContentResolver().openInputStream(uri);
            if (exifStream != null) {
                ExifInterface exif = new ExifInterface(exifStream);
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
                exifStream.close();
            }

            return rotateBitmap(bitmap, degrees);
        } catch (IOException e) {
            Log.e("GalleryError", "이미지 불러오기 실패: " + e.getMessage());
            Toast.makeText(this, "이미지를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    // ImageProxy를 Bitmap으로 변환하는 메서드
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        // 이미지 회전 정보 가져오기
        int rotationDegrees = image.getImageInfo().getRotationDegrees();

        // 회전된 Bitmap 반환
        return rotateBitmap(bitmap, rotationDegrees);
    }

    // Bitmap을 회전시키는 메서드
    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        if (degrees == 0) {
            return bitmap;
        }

        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(degrees);

        Bitmap rotatedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                matrix,
                true
        );

        // 원본 bitmap 메모리 해제
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }

        return rotatedBitmap;
    }
}
