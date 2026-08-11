package com.aegismesh.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.aegismesh.session.UserSession;

public class ProfileActivity extends AppCompatActivity {

    public static final String EXTRA_ONBOARDING = "extra_onboarding";

    // Preferences for offline SOS Mesh access
    private static final String PREF_NAME = "AegisProfilePrefs";
    private static final String KEY_FULL_NAME = "fullName";
    private static final String KEY_BLOOD_GROUP = "bloodGroup";
    private static final String KEY_ALLERGIES = "allergies";
    private static final String KEY_CONDITIONS = "conditions";
    private static final String KEY_MEDICATIONS = "medications";

    private ActivityProfileBinding binding;
    private boolean isOnboarding;

    /**
     * Used by SOSService to grab the user's data instantly for the Python AI Engine
     * without needing an internet connection.
     */
    public static User getSavedUser(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String name = prefs.getString(KEY_FULL_NAME, "Unknown Victim");
        String blood = prefs.getString(KEY_BLOOD_GROUP, "");
        String allergies = prefs.getString(KEY_ALLERGIES, "None");
        String conditions = prefs.getString(KEY_CONDITIONS, "None");
        String medications = prefs.getString(KEY_MEDICATIONS, "None");

        User user = new User(name, "0");
        user.setMedicalProfile(new MedicalProfile(
                blood,
                allergies.split(","),
                conditions.split(","),
                medications.split(",")));
        return user;
    }

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

        // THE NEW CLOUD SYNC BUTTON
        if (binding.btnSyncCloud != null) {
            binding.btnSyncCloud.setOnClickListener(v -> {
                showToast("Syncing with cloud...");
                loadCurrentUser();
            });
        }

        // Pre-fill UI if data exists locally, then try fetching cloud updates
        populateFromLocalOfflineStorage();
        loadCurrentUser();
    }

    private void loadCurrentUser() {
        setLoading(true);
        // Assuming ApiClient.getUserService() is implemented by your team
        try {
            ApiClient.getUserService().getCurrentUser(new ApiCallback<User>() {
                @Override
                public void onSuccess(User user) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        bindUser(user);
                        saveToLocalOfflineStorage(user.getFullName(), user.getMedicalProfile());
                        showToast("Profile synced from cloud.");
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
        } catch (Exception e) {
            setLoading(false);
            // Ignore error if team hasn't built getUserService yet
        }
    }

    private void bindUser(User user) {
        MedicalProfile profile = user.getMedicalProfile();
        binding.inputFullName.setText(user.getFullName());
        if (profile != null) {
            binding.inputBloodGroup.setText(profile.getBloodGroup());
            binding.inputAllergies.setText(profile.allergiesCsv());
            binding.inputChronicIllnesses.setText(profile.chronicIllnessesCsv());
            binding.inputMedications.setText(profile.currentMedicationsCsv());
        }

        if (user.getVerificationLevel() != null) {
            renderVerificationBadges(user.getVerificationLevel());
        }
    }

    private void renderVerificationBadges(VerificationLevel level) {
        binding.badgePhoneVerified.setVisibility(View.VISIBLE); // required at signup
        binding.badgeIdVerified.setVisibility(level.hasNationalId() ? View.VISIBLE : View.GONE);
        binding.badgeFaceVerified.setVisibility(level.hasFaceMatch() ? View.VISIBLE : View.GONE);

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

        // 1. Save locally IMMEDIATELY so offline SOS works even if network fails
        saveToLocalOfflineStorage(fullName, profile);

        // 2. Try to save to the Cloud
        setLoading(true);
        try {
            ApiClient.getUserService().updateProfile(fullName, profile, new ApiCallback<User>() {
                @Override
                public void onSuccess(User user) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        showToast(getString(R.string.profile_saved));
                        if (UserSession.getInstance() != null) {
                            UserSession.getInstance().setCurrentUser(user);
                        }
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
        } catch (Exception e) {
            setLoading(false);
            showToast("Saved locally for offline use.");
            if (isOnboarding) finish();
        }
    }

    // --- NEW HELPERS FOR OFFLINE AI & MESH NETWORKING ---

    private void saveToLocalOfflineStorage(String name, MedicalProfile profile) {
        SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_FULL_NAME, name);
        if (profile != null) {
            editor.putString(KEY_BLOOD_GROUP, profile.getBloodGroup());
            editor.putString(KEY_ALLERGIES, profile.allergiesCsv().isEmpty() ? "None" : profile.allergiesCsv());
            editor.putString(KEY_CONDITIONS, profile.chronicIllnessesCsv().isEmpty() ? "None" : profile.chronicIllnessesCsv());
            editor.putString(KEY_MEDICATIONS, profile.currentMedicationsCsv().isEmpty() ? "None" : profile.currentMedicationsCsv());
        }
        editor.apply();
        android.util.Log.d("ProfileActivity", "Saved to SharedPreferences: " + name);
    }

    private void populateFromLocalOfflineStorage() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String name = prefs.getString(KEY_FULL_NAME, "");
        if (!TextUtils.isEmpty(name)) {
            binding.inputFullName.setText(name);
            binding.inputBloodGroup.setText(prefs.getString(KEY_BLOOD_GROUP, ""));
            binding.inputAllergies.setText(prefs.getString(KEY_ALLERGIES, ""));
            binding.inputChronicIllnesses.setText(prefs.getString(KEY_CONDITIONS, ""));
            binding.inputMedications.setText(prefs.getString(KEY_MEDICATIONS, ""));
            android.util.Log.d("ProfileActivity", "Populated from SharedPreferences: " + name);
        }
    }

    // --- EXISTING HELPERS ---

    private void startIdVerification() {
        showToast(getString(R.string.feature_coming_soon));
    }

    private void startSelfieVerification() {
        showToast(getString(R.string.feature_coming_soon));
    }

    private void addEmergencyContactRow() {
        android.widget.EditText contactInput = new android.widget.EditText(this);
        contactInput.setHint("Name & Phone (e.g. John +123...)");
        contactInput.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        
        binding.emergencyContactsContainer.addView(contactInput);
        contactInput.requestFocus();
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
        if (binding.progressBar != null) {
            binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        binding.buttonSave.setEnabled(!loading);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}