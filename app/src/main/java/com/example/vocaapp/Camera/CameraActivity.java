package com.example.vocaapp.Camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.type.Schema;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;

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

    private GenerativeModelFutures model;

    private String vocabularyId;
    private String uid;
    private FirebaseUser user;

    private View loadingOverlay;
    private TextView loadingText;

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

        // 출력 schema 구조
        Schema wordSchema = Schema.obj(
                Map.of(
                        "word", Schema.str("추출된 단어"),
                        "meaning", Schema.str("단어의 뜻"),
                        "pronunciation", Schema.str("단어의 발음을 한국어로")
                ),
                List.of("word", "meaning", "pronunciation") // 필수 필드 지정
        );

        Schema responseSchema = Schema.array(wordSchema, "추출된 단어 리스트");

        // 2. GenerationConfig 설정
        GenerationConfig.Builder configBuilder = new GenerationConfig.Builder();
        configBuilder.responseMimeType = "application/json";
        configBuilder.responseSchema = responseSchema;
        GenerationConfig generationConfig = configBuilder.build();

        // 3. 모델 초기화 (Vertex AI Backend 사용)
        GenerativeModel ai = FirebaseAI.getInstance(GenerativeBackend.vertexAI("global"))
                .generativeModel("gemini-2.5-flash", generationConfig);

        model = GenerativeModelFutures.from(ai);

        finishTextView.setOnClickListener(v -> extractData());

    }

    // AI 단어 등록 1회당 차감되는 포인트
    private static final int AI_EXTRACT_COST = 20;

    // 사진에서 단어를 추출하는 method
    private void extractData() {

        if (photoList.isEmpty()) {
            Toast.makeText(this, "먼저 사진을 촬영해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 포인트가 충분한지 먼저 확인 후 추출 진행
        showLoading();
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    long point = (snapshot.exists() && snapshot.get("point") != null)
                            ? snapshot.getLong("point") : 0;
                    if (point < AI_EXTRACT_COST) {
                        hideLoading();
                        showInsufficientPointDialog();
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
    }

    // 로딩 오버레이 숨김 + 타자 효과 중단
    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
        typingHandler.removeCallbacks(typingRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        typingHandler.removeCallbacks(typingRunnable);
    }

    // 포인트 부족 안내 팝업 (확인 / 결제하기)
    private void showInsufficientPointDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_insufficient_point, null);

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

    // 실제 Gemini 추출을 수행하는 method
    private void runExtraction() {

        Content.Builder contentBuilder = new Content.Builder();

        for (Bitmap photo : photoList) {
            Bitmap resized = getResizedBitmap(photo, 1024);
            contentBuilder.addImage(resized);
        }

        contentBuilder.addText("이미지에서 영어 단어들을 추출하고, 한국어 뜻과 한국어 발음을 적어줘. 만약 전문 용어라면 가장 대중적인 뜻을 선택해줘.");

        Content content = contentBuilder.build();

        // Gemini 호출
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        // 콜백 설정 (executor 에러 해결을 위해 ContextCompat 사용)
        Futures.addCallback(
                response,
                new FutureCallback<GenerateContentResponse>() {
                    @Override
                    public void onSuccess(GenerateContentResponse result) {
                        String resultText = result.getText();
                        Log.d("GeminiResult", "응답 JSON: " + resultText);

                        try {
                            // GSON을 사용하여 JSON 문자열을 List<WordItem>으로 변환
                            Gson gson = new Gson();
                            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<WordItem>>(){}.getType();
                            List<WordItem> wordList = gson.fromJson(resultText, listType);

                            // 결과 처리 (메인 스레드에서 UI 업데이트)
                            runOnUiThread(() -> {
                                hideLoading();

                                if (wordList != null && !wordList.isEmpty()) {
                                    // AI 단어 등록 성공 시 포인트 차감
                                    FirebaseFirestore.getInstance().collection("users").document(uid)
                                            .update("point", FieldValue.increment(-AI_EXTRACT_COST));

                                    Intent intent = new Intent(CameraActivity.this, WordSelectActivity.class);
                                    intent.putParcelableArrayListExtra("wordList", new ArrayList<>(wordList));
                                    intent.putExtra("vocabularyId", vocabularyId);
                                    startActivity(intent);
                                    finish();
                                } else {
                                    Toast.makeText(CameraActivity.this, "추출된 단어가 없습니다.", Toast.LENGTH_SHORT).show();
                                }
                            });

                        } catch (Exception e) {
                            Log.e("ParsingError", "파싱 실패: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e("GeminiError", "에러 발생: " + t.getMessage());
                        runOnUiThread(() -> {
                            hideLoading();
                            Toast.makeText(CameraActivity.this, "분석 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                },
                ContextCompat.getMainExecutor(this) // 별도 변수 없이 안드로이드 메인 실행기 사용
        );
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
