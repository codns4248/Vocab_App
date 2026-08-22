package com.example.vocaapp.Settting;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.vocaapp.R;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.functions.FirebaseFunctions;
import com.kakao.sdk.auth.model.OAuthToken;
import com.kakao.sdk.user.UserApiClient;

import java.util.HashMap;
import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;

public class SettingFirebase {

    private final Context context;
    private final FirebaseAuth auth;
    private final OnUnregisterListener listener; // 콜백 리스너 추가

    // 1. 성공/실패 처리를 위한 인터페이스 정의
    public interface OnUnregisterListener {
        void onSuccess();
        void onFailure(String errorMsg);
    }

    // 2. 생성자에서 리스너를 받도록 수정
    public SettingFirebase(Context context, OnUnregisterListener listener) {
        this.context = context;
        this.auth = FirebaseAuth.getInstance();
        this.listener = listener;
    }

    /** 카카오로 만든 계정인지. 커스텀 토큰 uid를 kakao:{회원번호} 형태로 발급한다. */
    public static boolean isKakaoAccount(FirebaseUser user) {
        return user != null && user.getUid() != null && user.getUid().startsWith("kakao:");
    }

    public void performUnregister() {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            if (listener != null) listener.onFailure("로그인된 사용자가 없습니다.");
            return;
        }

        if (isKakaoAccount(user)) {
            // 카카오는 Firebase 재인증(reauthenticate)이 불가능하다. 커스텀 토큰이라
            // 재인증에 쓸 AuthCredential이 없다. 대신 카카오 로그인을 다시 시켜
            // 그 액세스 토큰을 서버가 검증하게 한다.
            reauthenticateWithKakaoThenDelete();
            return;
        }

        performGoogleUnregister(user);
    }

    private void performGoogleUnregister(FirebaseUser user) {
        CredentialManager credentialManager = CredentialManager.create(context);

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(context.getString(R.string.default_web_client_id))
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(context, request, null, Runnable::run,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        if (result.getCredential() instanceof CustomCredential &&
                                result.getCredential().getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
                            try {
                                GoogleIdTokenCredential credentialData = GoogleIdTokenCredential.createFrom(result.getCredential().getData());
                                String idToken = credentialData.getIdToken();
                                AuthCredential authCredential = GoogleAuthProvider.getCredential(idToken, null);

                                user.reauthenticate(authCredential).addOnCompleteListener(reauthTask -> {
                                    if (reauthTask.isSuccessful()) {
                                        callDeleteAccount(null, credentialManager);
                                    } else {
                                        if (listener != null) listener.onFailure("재인증에 실패했습니다.");
                                    }
                                });
                            } catch (Exception e) {
                                if (listener != null) listener.onFailure("토큰 처리 중 오류가 발생했습니다.");
                            }
                        } else {
                            // 구글 자격 증명이 아니면 여기서 끝나버려 아무 반응이 없었다.
                            if (listener != null) listener.onFailure("재인증에 실패했습니다.");
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (listener != null) listener.onFailure("인증 오류: " + e.getMessage());
                    }
                });
    }

    // 카카오 재인증: 로그인 창을 다시 띄워 받은 액세스 토큰을 서버로 넘긴다.
    private void reauthenticateWithKakaoThenDelete() {
        Function2<OAuthToken, Throwable, Unit> callback = (token, error) -> {
            if (error != null || token == null) {
                if (listener != null) listener.onFailure("카카오 재인증에 실패했습니다.");
                return Unit.INSTANCE;
            }
            callDeleteAccount(token.getAccessToken(), null);
            return Unit.INSTANCE;
        };

        UserApiClient client = UserApiClient.getInstance();
        if (client.isKakaoTalkLoginAvailable(context)) {
            client.loginWithKakaoTalk(context, callback);
        } else {
            client.loginWithKakaoAccount(context, callback);
        }
    }

    /**
     * 서버에서 탈퇴를 처리한다.
     * 하위 컬렉션까지 지우려면 서버가 필요하고(Firestore는 연쇄 삭제를 안 한다),
     * 카카오 연결 해제도 REST API 키가 있는 서버에서만 가능하다.
     *
     * @param kakaoAccessToken 카카오 계정이면 재인증용 토큰, 구글이면 null
     * @param credentialManager 구글 자격 증명 정리용, 카카오면 null
     */
    private void callDeleteAccount(String kakaoAccessToken, CredentialManager credentialManager) {
        Map<String, Object> data = new HashMap<>();
        if (kakaoAccessToken != null) {
            data.put("kakaoAccessToken", kakaoAccessToken);
        }

        FirebaseFunctions.getInstance("asia-northeast3")
                .getHttpsCallable("deleteAccount")
                .call(data)
                .addOnSuccessListener(result -> {
                    // 서버가 계정을 지웠으므로 로컬 세션도 정리한다.
                    auth.signOut();

                    if (credentialManager == null) {
                        if (listener != null) listener.onSuccess();
                        return;
                    }
                    credentialManager.clearCredentialStateAsync(new ClearCredentialStateRequest(), null, Runnable::run,
                            new CredentialManagerCallback<Void, ClearCredentialException>() {
                                @Override
                                public void onResult(Void result) {
                                    if (listener != null) listener.onSuccess();
                                }

                                @Override
                                public void onError(@NonNull ClearCredentialException e) {
                                    // 계정은 이미 지워졌으니 탈퇴 자체는 성공으로 본다.
                                    Log.w("SettingFirebase", "자격 증명 정리 실패: " + e.getMessage());
                                    if (listener != null) listener.onSuccess();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("SettingFirebase", "탈퇴 실패", e);
                    if (listener != null) listener.onFailure(e.getMessage());
                });
    }
}