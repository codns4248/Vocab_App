package com.forevermemory.vocaapp.Settting;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.forevermemory.vocaapp.R;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.kakao.sdk.auth.model.OAuthToken;
import com.kakao.sdk.user.UserApiClient;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;

public class AccountLinkViewModel extends ViewModel {
    final MutableLiveData<String> state = new MutableLiveData<>("");
    boolean busy;
    boolean loaded;
    boolean googleLinked;
    boolean kakaoLinked;
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFunctions functions = FirebaseFunctions.getInstance("asia-northeast3");
    private String uid;

    void refresh() {
        if (busy) return;
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { fail(new IllegalStateException("로그인이 필요합니다.")); return; }
        uid = user.getUid();
        busy = true;
        state.setValue("연결 상태 확인 중");
        loadStatus("");
    }

    private void loadStatus(String message) {
        loaded = false;
        functions.getHttpsCallable("getAccountLinks").call()
                .addOnSuccessListener(result -> {
                    if (!sameUser()) return;
                    Map<?, ?> data = (Map<?, ?>) result.getData();
                    googleLinked = Boolean.TRUE.equals(data.get("google"));
                    kakaoLinked = Boolean.TRUE.equals(data.get("kakao"));
                    loaded = true;
                    busy = false;
                    state.setValue(message);
                }).addOnFailureListener(this::fail);
    }

    void linkGoogle(Activity activity) {
        if (busy || !loaded || googleLinked || !sameUser()) return;
        busy = true;
        state.setValue("기존 카카오 계정 인증 중");
        requestKakao(activity, token -> functions.getHttpsCallable("reauthenticateKakaoAccount")
                .call(Collections.singletonMap("accessToken", token))
                .addOnSuccessListener(result -> {
                    if (!sameUser()) return;
                    String customToken = (String) ((Map<?, ?>) result.getData()).get("customToken");
                    auth.signInWithCustomToken(customToken).addOnSuccessListener(authResult -> {
                        if (!sameUser()) return;
                        state.setValue("연결할 구글 계정 선택 중");
                        requestGoogle(activity, credential -> {
                            if (!sameUser()) return;
                            auth.getCurrentUser().linkWithCredential(credential)
                                    .addOnSuccessListener(linked -> loadStatus("구글 계정이 연결되었습니다."))
                                    .addOnFailureListener(this::fail);
                        });
                    }).addOnFailureListener(this::fail);
                }).addOnFailureListener(this::fail));
    }

    void linkKakao(Activity activity) {
        if (busy || !loaded || kakaoLinked || !sameUser()) return;
        busy = true;
        state.setValue("기존 구글 계정 인증 중");
        requestGoogle(activity, credential -> {
            if (!sameUser()) return;
            auth.getCurrentUser().reauthenticate(credential).addOnSuccessListener(result -> {
                if (!sameUser()) return;
                auth.getCurrentUser().getIdToken(true).addOnSuccessListener(token -> {
                    if (!sameUser()) return;
                    state.setValue("연결할 카카오 계정 선택 중");
                    requestKakao(activity, accessToken -> {
                        if (!sameUser()) return;
                        functions.getHttpsCallable("linkKakaoAccount")
                                .call(Collections.singletonMap("accessToken", accessToken))
                                .addOnSuccessListener(linked -> loadStatus("카카오 계정이 연결되었습니다."))
                                .addOnFailureListener(this::fail);
                    });
                }).addOnFailureListener(this::fail);
            }).addOnFailureListener(this::fail);
        });
    }

    private boolean sameUser() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && user.getUid().equals(uid)) return true;
        loaded = false;
        fail(new IllegalStateException("로그인 계정이 변경되었습니다. 다시 로그인해주세요."));
        return false;
    }

    private void requestGoogle(Activity activity, Consumer<AuthCredential> callback) {
        if (activity.isDestroyed() || activity.isFinishing()) {
            fail(new IllegalStateException("화면이 변경되었습니다. 다시 시도해주세요."));
            return;
        }
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .setServerClientId(activity.getString(R.string.default_web_client_id)).build();
        CredentialManager.create(activity).getCredentialAsync(activity,
                new GetCredentialRequest.Builder().addCredentialOption(option).build(), null,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        try {
                            if (!(result.getCredential() instanceof CustomCredential)
                                    || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                    .equals(result.getCredential().getType())) {
                                throw new IllegalStateException("구글 계정을 확인하지 못했습니다.");
                            }
                            String token = GoogleIdTokenCredential.createFrom(result.getCredential().getData()).getIdToken();
                            callback.accept(GoogleAuthProvider.getCredential(token, null));
                        } catch (Exception error) { fail(error); }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException error) {
                        fail(new IllegalStateException("구글 인증이 취소되었거나 실패했습니다."));
                    }
                });
    }

    private void requestKakao(Activity activity, Consumer<String> callback) {
        if (activity.isDestroyed() || activity.isFinishing()) {
            fail(new IllegalStateException("화면이 변경되었습니다. 다시 시도해주세요."));
            return;
        }
        Function2<OAuthToken, Throwable, Unit> result = (token, error) -> {
            activity.runOnUiThread(() -> {
                if (error != null || token == null) {
                    fail(new IllegalStateException("카카오 인증이 취소되었거나 실패했습니다."));
                } else {
                    callback.accept(token.getAccessToken());
                }
            });
            return Unit.INSTANCE;
        };
        UserApiClient client = UserApiClient.getInstance();
        if (client.isKakaoTalkLoginAvailable(activity)) client.loginWithKakaoTalk(activity, result);
        else client.loginWithKakaoAccount(activity, result);
    }

    private void fail(Exception error) {
        busy = false;
        boolean conflict = error instanceof FirebaseAuthUserCollisionException
                || (error instanceof FirebaseFunctionsException
                && ((FirebaseFunctionsException) error).getCode() == FirebaseFunctionsException.Code.ALREADY_EXISTS);
        state.setValue(conflict ? "이미 다른 앱 계정에 등록된 로그인입니다. 해당 계정을 탈퇴한 뒤 연결해주세요."
                : "연결 처리 실패: " + (error.getMessage() == null ? "다시 시도해주세요." : error.getMessage()));
    }
}
