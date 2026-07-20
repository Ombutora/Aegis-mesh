package com.aegismesh.network;

import com.aegismesh.models.AuthResult;

import java.util.UUID;

/**
 * MOCK IMPLEMENTATION. The backend has no OTP endpoints yet (auth.py only
 * implements username/password login). This simulates a network round-trip
 * on a background thread and always succeeds, so LoginActivity's OTP flow is
 * usable end-to-end during development.
 *
 * Replace requestOtp()/verifyOtp() with real HTTP calls once backend OTP
 * endpoints exist - the method signatures/ApiCallback contract shouldn't
 * need to change at the call sites in LoginActivity.
 */
public class AuthService {

    public void requestOtp(String phoneNumber, ApiCallback<AuthResult.OtpRequest> callback) {
        new Thread(() -> {
            try {
                Thread.sleep(1200);
                callback.onSuccess(new AuthResult.OtpRequest(UUID.randomUUID().toString()));
            } catch (InterruptedException e) {
                callback.onError(e);
            }
        }).start();
    }

    public void verifyOtp(String requestId, String code, ApiCallback<AuthResult> callback) {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                // MOCK: accepts any correctly-formatted code and always reports a new user
                // (routes to ProfileActivity onboarding). Adjust once real verification exists.
                callback.onSuccess(new AuthResult("mock-token-" + UUID.randomUUID(), true));
            } catch (InterruptedException e) {
                callback.onError(e);
            }
        }).start();
    }
}