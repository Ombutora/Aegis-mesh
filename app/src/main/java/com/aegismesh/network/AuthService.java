package com.aegismesh.network;

import com.aegismesh.models.AuthResult;

import java.util.UUID;

public class AuthService {
    public void requestOtp(String phoneNumber, ApiCallback<AuthResult.OtpRequest> callback) {
        new Thread(() -> callback.onSuccess(new AuthResult.OtpRequest(UUID.randomUUID().toString()))).start();
    }
    public void verifyOtp(String requestId, String code, ApiCallback<AuthResult> callback) {
        new Thread(() -> callback.onSuccess(new AuthResult("mock-token-" + UUID.randomUUID(), true))).start();
    }
}
