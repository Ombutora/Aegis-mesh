package com.aegismesh.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aegismesh.R;
import com.aegismesh.databinding.ActivityProfileBinding;
import com.aegismesh.models.MedicalProfile;
import com.aegismesh.models.User;
import com.aegismesh.models.VerificationLevel;
import com.aegismesh.network.ApiCallback;
import com.aegismesh.network.ApiClient;

/**
 * Lets the user view and edit their medical profile, emergency contacts,
 * and identity verification status.
 *
 * Medical data here (blood group, allergies, chronic illnesses, current
 * medications) is what gets shared with an accepted responder during an
 * emergency - see MedicalProfileSharing in the system proposal. It is
 * encrypted at rest and only decrypted for an authorized responder.
 *
 * Verification badges mirror the tiers described in the security proposal:
 * phone (required at signup), national ID, and selfie/face match. Higher
 * tiers unlock the "Verified Responder" badge required for volunteer
 * response eligibility.
 */
public class ProfileActivity extends AppCompatActivity {

    public static final String EXTRA_ONBOARDING = "extra_onboarding";

    private ActivityProfileBinding binding;
    private boolean isOnboarding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        isOnboarding = getIntent().getBooleanExtra(EXTRA_ONBOARDING, false);
        binding.buttonSave.setText(isOnboarding ? R.string.action_finish_setup : R.string.action_save);

        binding.buttonSave.setOnClickListener(v -> saveProfile());
        binding.buttonVerifyId.setOnClickListener(v -> startIdVerification());
        binding.buttonVerifySelfie.setOnClickListener(v -> startSelfieVerification());
        binding.buttonAddEmergencyContact.setOnClickListener(v -> addEmergencyContactRow());

        loadCurrentUser();
    }

    private void loadCurrentUser() {
        setLoading(true);
        ApiClient.getUserService().getCurrentUser(new ApiCallback<User>() {
            @Override
            public void onSuccess(User user) {
                runOnUiThread(() -> {
                    setLoading(false);
                    bindUser(user);
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showToast(getString(R.string.error_load_profile_failed));
                });
            }
        });
    }

    private void bindUser(User user) {
        MedicalProfile profile = user.medicalProfile;
        binding.inputFullName.setText(user.fullName);
        binding.inputBloodGroup.setText(profile.bloodGroup);
        binding.inputAllergies.setText(profile.allergiesCsv());
        binding.inputChronicIllnesses.setText(profile.chronicIllnessesCsv());
        binding.inputMedications.setText(profile.currentMedicationsCsv());

        renderVerificationBadges(user.verificationLevel);
    }

    private void renderVerificationBadges(VerificationLevel level) {
        binding.badgePhoneVerified.setVisibility(View.VISIBLE); // required at signup
        binding.badgeIdVerified.setVisibility(
                level.hasNationalId() ? View.VISIBLE : View.GONE);
        binding.badgeFaceVerified.setVisibility(
                level.hasFaceMatch() ? View.VISIBLE : View.GONE);

        boolean isFullyVerified = level.hasNationalId() && level.hasFaceMatch();
        binding.textVerifiedResponderStatus.setText(
                isFullyVerified
                        ? getString(R.string.verified_responder_badge_earned)
                        : getString(R.string.verified_responder_badge_locked));

        binding.buttonVerifyId.setEnabled(!level.hasNationalId());
        binding.buttonVerifySelfie.setEnabled(level.hasNationalId() && !level.hasFaceMatch());
    }

    private void saveProfile() {
        String fullName = textOf(binding.inputFullName);
        if (fullName.isEmpty()) {
            binding.inputFullName.setError(getString(R.string.error_field_required));
            return;
        }

        MedicalProfile profile = new MedicalProfile(
                textOf(binding.inputBloodGroup),
                splitCsv(textOf(binding.inputAllergies)),
                splitCsv(textOf(binding.inputChronicIllnesses)),
                splitCsv(textOf(binding.inputMedications))
        );

        setLoading(true);
        ApiClient.getUserService().updateProfile(fullName, profile, new ApiCallback<User>() {
            @Override
            public void onSuccess(User user) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showToast(getString(R.string.profile_saved));
                    if (isOnboarding) {
                        finish();
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showToast(getString(R.string.error_save_profile_failed));
                });
            }
        });
    }

    private void startIdVerification() {
        // Launches the national ID capture + verification flow. Implementation
        // lives in a dedicated capture activity/module, invoked here.
        IdVerificationActivity.start(this);
    }

    private void startSelfieVerification() {
        // Launches selfie capture for face-match verification, unlocked only
        // after national ID verification succeeds.
        SelfieVerificationActivity.start(this);
    }

    private void addEmergencyContactRow() {
        binding.emergencyContactsContainer.addView(
                EmergencyContactRowView.newInstance(this));
    }

    private String textOf(android.widget.EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private String[] splitCsv(String csv) {
        if (csv.isEmpty()) return new String[0];
        String[] parts = csv.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.buttonSave.setEnabled(!loading);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
