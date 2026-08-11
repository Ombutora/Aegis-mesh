package com.aegismesh.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.aegismesh.databinding.ActivityHomeBinding;
import com.aegismesh.models.User;
import com.aegismesh.services.SOSService;

/**
 * Main dashboard shown after login.
 */
public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private static final int PERMISSION_REQ = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        checkPermissions();
        setupListeners();
    }

    private void setupListeners() {
        // Primary SOS Button: Immediately triggers the full response chain
        binding.buttonSos.setOnClickListener(v -> triggerSos());

        // Navigation to Emergency screen without triggering a new alert
        binding.btnGoToEmergency.setOnClickListener(v ->
                startActivity(new Intent(this, EmergencyActivity.class)));

        // Profile Management
        binding.buttonProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ViewProfileActivity.class)));

        binding.btnEditProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        // Logout
        binding.btnLogout.setOnClickListener(v -> {
            getSharedPreferences("AegisAuthPrefs", MODE_PRIVATE).edit().clear().apply();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void triggerSos() {
        // 1. Command the SOS service to start background transmission & GPS
        SOSService.trigger(this);
        
        // 2. Open the active monitoring UI
        Intent intent = new Intent(this, EmergencyActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        User currentUser = com.aegismesh.session.UserSession.getInstance().getCurrentUser();
        if (currentUser == null) {
            currentUser = ProfileActivity.getSavedUser(this);
        }
        binding.tvGreeting.setText("Hello, " + currentUser.getFullName());

        if (currentUser.getFullName().equals("Unknown Victim")) {
            binding.tvProfileStatus.setText("⚠️ Setup Medical Profile for AI Triage.");
            binding.tvProfileStatus.setTextColor(0xFFFF0000);
        } else {
            binding.tvProfileStatus.setText("✅ Medical Profile is Ready.");
            binding.tvProfileStatus.setTextColor(0xFF00AA00);
        }
    }


    private void checkPermissions() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            perms = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, perms, PERMISSION_REQ);
                return;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                // We should ideally show a rationale, but for now we just log it
                // and the user will likely see limited functionality
            }
        }
    }
}
