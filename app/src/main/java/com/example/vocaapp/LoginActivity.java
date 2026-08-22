package com.example.vocaapp; //  본인 패키지 이름이 맞는지 맨 윗줄 확인

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.CredentialManager;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.functions.FirebaseFunctions;
import com.kakao.sdk.auth.model.OAuthToken;
import com.kakao.sdk.common.model.ClientError;
import com.kakao.sdk.common.model.ClientErrorCause;
import com.kakao.sdk.user.UserApiClient;

import java.util.HashMap;
import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;

// 안드로이드 구버전 호환성을 위해 추가
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private CredentialManager credentialManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 파이어베이스 및 신식 로그인 매니저 준비
        mAuth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // 이미 로그인 된 유저의 경우 바로 메인화면 이동
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // 뒤로가기 못하게 종료
            return;   // 아래 코드 실행 안 함
        }
        setContentView(R.layout.activity_login); //로그인이 안된 경우 이 화면

        credentialManager = CredentialManager.create(this);  // 나머지 연결 버튼 등

        // 2. 구글 로그인 버튼 연결
        Button googleBtn = findViewById(R.id.googleBtn);
        googleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signInWithGoogle(); // 신식 로그인 함수 실행
            }
        });

        Button kakaoBtn = findViewById(R.id.kakaoBtn);
        kakaoBtn.setOnClickListener(v -> signInWithKakao());
    }

    // 구글 로그인 요청 함수
    private void signInWithGoogle() {
        // ID (google-services.json -> client_type: 3)
        String myWebClientId = "211185583428-b8jvrel36olutnuiakphpibrofcge2j6.apps.googleusercontent.com";

        // 1. 옵션 설정
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(myWebClientId)
                .setAutoSelectEnabled(true)
                .build();

        // 2. 요청 객체 생성
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        // 3. 비동기 처리를 위한 실행기 (스레드 관리)
        Executor executor = Executors.newSingleThreadExecutor();

        // 4. 진짜 로그인 화면 띄우기
        credentialManager.getCredentialAsync(
                this,
                request,
                null,
                executor,
                new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        // 로그인 성공 시 처리
                        handleSignIn(result);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        // 에러 났을 때
                        Log.e("Login", "로그인 창 오류", e);
                        runOnUiThread(() -> Toast.makeText(LoginActivity.this, "로그인 창 안뜸: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }
        );
    }

    // 결과물(토큰) 꺼내는 함수
    private void handleSignIn(GetCredentialResponse result) {
        CustomCredential credential = (CustomCredential) result.getCredential();

        // 구글 토큰인지 확인
        if (credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
            try {
                // 토큰 껍질 까서 알맹이(ID Token) 꺼내기
                GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.getData());
                String idToken = googleIdTokenCredential.getIdToken();

                // 파이어베이스로 넘겨서 최종 인증!
                firebaseAuthWithGoogle(idToken);

            } catch (Exception e) {
                Log.e("Login", "토큰 해석 실패", e);
            }
        } else {
            Log.e("Login", "알 수 없는 자격 증명 타입");
        }
    }

    // 파이어베이스에 신고하는 함수 (여기는 구식과 원리가 같음)
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {

                        FirebaseUser user = mAuth.getCurrentUser();

                        // 신규 가입 유저인지 판별
                        boolean isNewUser = user != null
                                && task.getResult().getAdditionalUserInfo() != null
                                && task.getResult().getAdditionalUserInfo().isNewUser();

                        if (user != null) {
                            // 로그인/회원가입 시점에 FCM 토큰을 Firestore에 저장합니다.
                            com.example.vocaapp.Test.StudyManager.getInstance().updateFCMToken(user.getUid());

                            // 신규 가입 유저라면 기본 포인트 100P를 지급합니다.
                            if (isNewUser) {
                                com.example.vocaapp.Test.StudyManager.getInstance().initNewUserPoint(user.getUid());
                            }
                        }

                        runOnUiThread(() -> goToMain(isNewUser));
                    } else {
                        // 실패
                        runOnUiThread(() -> Toast.makeText(LoginActivity.this, "파이어베이스 인증 실패", Toast.LENGTH_SHORT).show());
                    }
                });
    }

    // ------------------------------------------------------------------
    // 카카오 로그인
    //
    // Firebase Auth에는 카카오 제공자가 없어서 커스텀 토큰을 거친다.
    //   카카오 SDK 로그인 -> 액세스 토큰
    //   -> kakaoCustomToken 함수가 토큰을 카카오 서버에 검증하고 Firebase 토큰 발급
    //   -> signInWithCustomToken
    // 이후로는 구글 로그인과 완전히 같은 Firebase 유저다.
    // ------------------------------------------------------------------
    private void signInWithKakao() {
        Function2<OAuthToken, Throwable, Unit> callback = (token, error) -> {
            if (error != null) {
                // 사용자가 로그인 창을 닫은 경우는 오류로 알리지 않는다.
                if (error instanceof ClientError
                        && ((ClientError) error).getReason() == ClientErrorCause.Cancelled) {
                    return Unit.INSTANCE;
                }
                Log.e("KakaoLogin", "카카오 로그인 실패", error);
                runOnUiThread(() -> Toast.makeText(this,
                        "카카오 로그인에 실패했습니다.", Toast.LENGTH_SHORT).show());
                return Unit.INSTANCE;
            }
            if (token == null) {
                runOnUiThread(() -> Toast.makeText(this,
                        "카카오 로그인에 실패했습니다.", Toast.LENGTH_SHORT).show());
                return Unit.INSTANCE;
            }
            exchangeKakaoTokenForFirebase(token.getAccessToken());
            return Unit.INSTANCE;
        };

        // 카카오톡이 깔려 있으면 앱으로, 아니면 웹(카카오계정)으로 로그인한다.
        UserApiClient client = UserApiClient.getInstance();
        if (client.isKakaoTalkLoginAvailable(this)) {
            client.loginWithKakaoTalk(this, callback);
        } else {
            client.loginWithKakaoAccount(this, callback);
        }
    }

    // 카카오 액세스 토큰을 서버로 보내 Firebase 커스텀 토큰으로 바꾼다.
    // 클라이언트가 카카오 ID를 직접 주장하지 않고, 서버가 카카오에 물어본다.
    private void exchangeKakaoTokenForFirebase(String kakaoAccessToken) {
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", kakaoAccessToken);

        FirebaseFunctions.getInstance("asia-northeast3")
                .getHttpsCallable("kakaoCustomToken")
                .call(data)
                .addOnSuccessListener(result -> {
                    Map<?, ?> body = (Map<?, ?>) result.getData();
                    String customToken = body == null ? null : (String) body.get("customToken");
                    boolean isNewUser = body != null && Boolean.TRUE.equals(body.get("isNewUser"));

                    if (customToken == null) {
                        Toast.makeText(this, "로그인에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    firebaseAuthWithCustomToken(customToken, isNewUser);
                })
                .addOnFailureListener(e -> {
                    Log.e("KakaoLogin", "커스텀 토큰 발급 실패", e);
                    Toast.makeText(this, "로그인에 실패했습니다: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void firebaseAuthWithCustomToken(String customToken, boolean isNewUser) {
        mAuth.signInWithCustomToken(customToken)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        Log.e("KakaoLogin", "Firebase 인증 실패", task.getException());
                        Toast.makeText(this, "파이어베이스 인증 실패", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        com.example.vocaapp.Test.StudyManager.getInstance().updateFCMToken(user.getUid());
                        // 커스텀 토큰 로그인은 getAdditionalUserInfo()를 믿을 수 없어
                        // 서버가 알려준 isNewUser를 쓴다.
                        if (isNewUser) {
                            com.example.vocaapp.Test.StudyManager.getInstance().initNewUserPoint(user.getUid());
                        }
                    }
                    runOnUiThread(() -> goToMain(isNewUser));
                });
    }

    // 메인 화면으로 이동 (신규 유저면 환영 팝업을 띄우도록 플래그 전달)
    private void goToMain(boolean isNewUser) {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("isNewUser", isNewUser);
        startActivity(intent);
        finish();
    }
}
