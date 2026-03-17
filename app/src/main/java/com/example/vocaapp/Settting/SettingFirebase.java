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
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingFirebase {

    private final Context context;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
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
        this.db = FirebaseFirestore.getInstance();
        this.listener = listener;
    }

    public void performUnregister() {
        CredentialManager credentialManager = CredentialManager.create(context);
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            if (listener != null) listener.onFailure("로그인된 사용자가 없습니다.");
            return;
        }

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
                                        deleteUserAndData(user, credentialManager);
                                    } else {
                                        if (listener != null) listener.onFailure("재인증에 실패했습니다.");
                                    }
                                });
                            } catch (Exception e) {
                                if (listener != null) listener.onFailure("토큰 처리 중 오류가 발생했습니다.");
                            }
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (listener != null) listener.onFailure("인증 오류: " + e.getMessage());
                    }
                });
    }

    private void deleteUserAndData(FirebaseUser user, CredentialManager credentialManager) {
        String uid = user.getUid();

        db.collection("users").document(uid).delete()
                .addOnSuccessListener(aVoid -> {
                    user.delete().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            credentialManager.clearCredentialStateAsync(new ClearCredentialStateRequest(), null, Runnable::run,
                                    new CredentialManagerCallback<Void, ClearCredentialException>() {
                                        @Override
                                        public void onResult(Void result) {
                                            // 3. 최종 성공 시 콜백 호출
                                            if (listener != null) listener.onSuccess();
                                        }

                                        @Override
                                        public void onError(@NonNull ClearCredentialException e) {
                                            if (listener != null) listener.onFailure("로그아웃 상태 초기화 실패");
                                        }
                                    });
                        } else {
                            if (listener != null) listener.onFailure("계정 삭제에 실패했습니다.");
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onFailure("사용자 데이터 삭제 실패");
                });
    }
}