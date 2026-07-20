package com.aegismesh.models;

import java.io.Serializable;

/**
 * Result of the phone + OTP login flow (see LoginActivity). Currently
 * produced by a MOCKED AuthService - the backend has no real OTP endpoints
 * yet (auth.py only implements username/password login).
 */
public class AuthResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String accessToken;
    private boolean isNewUser;

    public AuthResult() {
    }

    public AuthResult(String accessToken, boolean isNewUser) {
        this.accessToken = accessToken;
        this.isNewUser = isNewUser;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public boolean isNewUser() { return isNewUser; }
    public void setNewUser(boolean newUser) { isNewUser = newUser; }

    /** Correlates a requestOtp() call with the subsequent verifyOtp() call. */
    public static class OtpRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestId;

        public OtpRequest() {
        }

        public OtpRequest(String requestId) {
            this.requestId = requestId;
        }

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
    }
}