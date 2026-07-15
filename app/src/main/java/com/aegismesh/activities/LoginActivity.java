package com.aegismesh.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aegismesh.R;
import com.aegismesh.databinding.ActivityLoginBinding;
import com.aegismesh.models.AuthResult;
import com.aegismesh.network.ApiCallback;
import com.aegismesh.network.ApiClient;

import java.util.regex.Pattern;

/**
 * Entry point for authentication.
 *
 * Aegis Mesh uses a two-step, tiered verification flow (see security_proposal.md):
 *   1. Phone number + OTP - required for every account.
 *   2. National ID + selfie match - optional, but required to earn the
 *      "Verified Responder" badge and unlock volunteer response features.
 *
 * This activity only handles step 1. Step 2 is offered afterwards from
 * {@link ProfileActivity} so new users aren't blocked from using the app
 * while they complete deeper verification.
 */
public class LoginActivity extends AppCompatActivity {

    private static final int OTP_LENGTH = 6;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{9,15}$");

    private ActivityLoginBinding binding;
    @Nullable private String otpRequestId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.buttonSendOtp.setOnClickListener(v -> requestOtp());
        binding.buttonVerifyOtp.setOnClickListener(v -> verifyOtp());
    }

    private void requestOtp() {
        String phoneNumber = textOf(binding.inputPhoneNumber);
        if (!isValidPhoneNumber(phoneNumber)) {
            binding.inputPhoneNumber.setError(getString(R.string.error_invalid_phone));
            return;
        }

        setLoading(true);
        ApiClient.getAuthService().requestOtp(phoneNumber, new ApiCallback<AuthResult.OtpRequest>() {
            @Override
            public void onSuccess(AuthResult.OtpRequest response) {
                runOnUiThread(() -> {
                    setLoading(false);
                    otpRequestId = response.requestId;
                    binding.otpGroup.setVisibility(View.VISIBLE);
                    binding.textStatus.setText(getString(R.string.otp_sent, phoneNumber));
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showError(messageOrDefault(error, R.string.error_otp_request_failed));
                });
            }
        });
    }

    private void verifyOtp() {
        if (otpRequestId == null) {
            showError(getString(R.string.error_request_otp_first));
            return;
        }

        String code = textOf(binding.inputOtpCode);
        if (code.length() != OTP_LENGTH) {
            binding.inputOtpCode.setError(getString(R.string.error_invalid_otp));
            return;
        }

        setLoading(true);
        ApiClient.getAuthService().verifyOtp(otpRequestId, code, new ApiCallback<AuthResult>() {
            @Override
            public void onSuccess(AuthResult auth) {
                runOnUiThread(() -> {
                    setLoading(false);
                    ApiClient.setSessionToken(auth.accessToken);
                    onAuthenticated(auth.isNewUser);
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showError(messageOrDefault(error, R.string.error_otp_invalid));
                });
            }
        });
    }

    private void onAuthenticated(boolean isNewUser) {
        Class<?> destination = isNewUser ? ProfileActivity.class : HomeActivity.class;
        Intent intent = new Intent(this, destination);
        if (isNewUser) {
            intent.putExtra(ProfileActivity.EXTRA_ONBOARDING, true);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        return PHONE_PATTERN.matcher(phoneNumber).matches();
    }

    private String textOf(android.widget.EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private String messageOrDefault(Throwable error, int defaultResId) {
        return !TextUtils.isEmpty(error.getMessage()) ? error.getMessage() : getString(defaultResId);
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.buttonSendOtp.setEnabled(!loading);
        binding.buttonVerifyOtp.setEnabled(!loading);
    }

    private void showError(String message) {
        binding.textStatus.setText(message);
    }
}
