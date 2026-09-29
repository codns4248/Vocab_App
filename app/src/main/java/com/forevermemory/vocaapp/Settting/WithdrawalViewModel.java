package com.forevermemory.vocaapp.Settting;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class WithdrawalViewModel extends ViewModel {
    enum Status { IDLE, LOADING, SUCCESS, ERROR }

    private final MutableLiveData<Status> status = new MutableLiveData<>(Status.IDLE);
    private String errorMessage;

    LiveData<Status> getStatus() {
        return status;
    }

    String getErrorMessage() {
        return errorMessage;
    }

    void start(Context context, String[] reasons) {
        if (status.getValue() == Status.LOADING || status.getValue() == Status.SUCCESS) return;
        status.setValue(Status.LOADING);
        SettingFirebase firebase = new SettingFirebase(context, new SettingFirebase.OnUnregisterListener() {
            @Override
            public void onSuccess() {
                status.setValue(Status.SUCCESS);
            }

            @Override
            public void onFailure(String message) {
                errorMessage = message;
                status.setValue(Status.ERROR);
            }
        });
        try {
            firebase.performUnregister(reasons);
        } catch (Exception error) {
            errorMessage = "탈퇴 요청을 시작하지 못했습니다. 다시 시도해주세요.";
            status.setValue(Status.ERROR);
        }
    }

    void clearError() {
        if (status.getValue() == Status.ERROR) status.setValue(Status.IDLE);
    }
}
