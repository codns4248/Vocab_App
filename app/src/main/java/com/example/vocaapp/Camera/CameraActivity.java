package com.example.vocaapp.Camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.gson.Gson;

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

    private GenerativeModelFutures model;

    private String vocabularyId;
    private String uid;
    private FirebaseUser user;

    private View loadingOverlay;

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

    // 사진에서 단어를 추출하는 method
    private void extractData() {

        loadingOverlay.setVisibility(View.VISIBLE);

        if (photoList.isEmpty()) {
            Toast.makeText(this, "먼저 사진을 촬영해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

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
                                loadingOverlay.setVisibility(View.GONE);

                                if (wordList != null && !wordList.isEmpty()) {
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
                        runOnUiThread(() ->
                                Toast.makeText(CameraActivity.this, "분석 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show()
                        );
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
