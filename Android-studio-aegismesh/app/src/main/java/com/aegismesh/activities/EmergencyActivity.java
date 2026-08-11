package com.aegismesh.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aegismesh.R;
import com.aegismesh.adapters.TriageMessageAdapter;
import com.aegismesh.databinding.ActivityEmergencyBinding;
import com.aegismesh.mesh.BlePermissionHelper;
import com.aegismesh.models.Emergency;
import com.aegismesh.models.Hospital;
import com.aegismesh.models.Responder;
import com.aegismesh.models.TriageMessage;
import com.aegismesh.models.User;
import com.aegismesh.sensors.GestureDetector;
import com.aegismesh.services.SOSService;

import java.util.ArrayList;
import java.util.List;

/**
 * Active-emergency screen.
 *
 * Handles both the hardware trigger (hold + shake) and provides real-time
 * updates on the emergency status, mesh propagation, and responder tracking.
 */
public class EmergencyActivity extends AppCompatActivity implements GestureDetector.GestureListener {

    private static final String TAG = "EmergencyActivity";

    public static final String EXTRA_EMERGENCY_ID = "extra_emergency_id";

    private ActivityEmergencyBinding binding;
    private GestureDetector gestureDetector;
    private TriageMessageAdapter triageAdapter;
    private BlePermissionHelper blePermissionHelper;
    @Nullable
    private String emergencyId;

    private final Observer<Emergency> emergencyObserver = this::renderEmergency;
    private final Observer<TriageMessage> triageObserver = this::appendTriageMessage;
    private final Observer<Responder> responderObserver = this::renderResponder;
    private final Observer<Hospital> hospitalObserver = this::renderHospital;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEmergencyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        emergencyId = getIntent().getStringExtra(EXTRA_EMERGENCY_ID);

        // Initialize Triage UI
        triageAdapter = new TriageMessageAdapter(new ArrayList<>());
        if (binding.recyclerTriage != null) {
            binding.recyclerTriage.setLayoutManager(new LinearLayoutManager(this));
            binding.recyclerTriage.setAdapter(triageAdapter);
        }

        // Initialize Hardware Bridge
        gestureDetector = new GestureDetector(this, this);

        // Request BLE mesh runtime permissions up front, before the gesture
        // detector can start listening in onResume() -- a shake/hold trigger
        // that fires before these are granted would fall through to
        // SOSService's offline/mesh path with BleMeshManager silently
        // no-op'ing (hasBlePermissions() returns false). This does not block
        // the online path; it only affects mesh fallback.
        blePermissionHelper = new BlePermissionHelper(this);
        blePermissionHelper.requestIfNeeded(this, new BlePermissionHelper.Callback() {
            @Override
            public void onAllGranted() {
                android.util.Log.i(TAG, "BLE mesh permissions granted.");
            }

            @Override
            public void onDenied(List<String> deniedPermissions) {
                android.util.Log.w(TAG, "BLE mesh permissions denied: " + deniedPermissions);
                Toast.makeText(EmergencyActivity.this,
                        "Offline mesh relay unavailable without Bluetooth permissions.",
                        Toast.LENGTH_LONG).show();
            }
        });

        setupListeners();
        observeActiveEmergency();
        setupBackNavigation();
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmCancel();
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        if (binding.btnHoldToArm != null) {
            binding.btnHoldToArm.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        gestureDetector.setScreenHeld(true);
                        binding.btnHoldToArm.setText("ARMED!\nSHAKE DEVICE NOW!");
                        binding.btnHoldToArm.setBackgroundColor(0xFFFF0000);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        gestureDetector.setScreenHeld(false);
                        binding.btnHoldToArm.setText("HOLD TO ARM");
                        binding.btnHoldToArm.setBackgroundColor(0xFF888888);
                        return true;
                }
                return false;
            });
        }

        if (binding.buttonCancelSos != null) {
            binding.buttonCancelSos.setOnClickListener(v -> confirmCancel());
        }

        if (binding.buttonEscalate != null) {
            binding.buttonEscalate.setOnClickListener(v -> escalate());
        }

        if (binding.tvStatus != null) {
            binding.tvStatus.setOnClickListener(v -> {
                Toast.makeText(this, "Software Test Triggered!", Toast.LENGTH_SHORT).show();
                onSosTriggered();
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        gestureDetector.startListening();
    }

    @Override
    protected void onPause() {
        super.onPause();
        gestureDetector.stopListening();
    }

    @Override
    public void onSosTriggered() {
        runOnUiThread(() -> {
            if (binding.tvStatus != null) {
                binding.tvStatus.setText("SOS DEPLOYED!");
                binding.tvStatus.setTextColor(0xFFFF0000);
            }

            User victim = ProfileActivity.getSavedUser(this);
            String victimName = victim != null ? victim.getFullName() : "Unknown User";
            String condition = "General Emergency";
            if (victim != null && victim.getMedicalProfile() != null) {
                condition = victim.getMedicalProfile().chronicIllnessesCsv();
                if (condition.isEmpty()) {
                    condition = "General Emergency";
                }
            }

            Intent sosIntent = new Intent(this, SOSService.class);
            sosIntent.setAction(SOSService.ACTION_TRIGGER_SOS);
            sosIntent.putExtra(SOSService.EXTRA_USER_ID, victimName);
            sosIntent.putExtra(SOSService.EXTRA_TRIGGER_TYPE, SOSService.GESTURE_TRIGGER);
            sosIntent.putExtra(SOSService.EXTRA_EMERGENCY_TYPE, condition);

            startService(sosIntent);
        });
    }

    private void observeActiveEmergency() {
        SOSService.getActiveEmergency().observe(this, emergencyObserver);
        SOSService.getTriageMessages().observe(this, triageObserver);
        SOSService.getAssignedResponder().observe(this, responderObserver);
        SOSService.getRecommendedHospital().observe(this, hospitalObserver);
    }

    private void renderEmergency(@Nullable Emergency emergency) {
        if (emergency == null) {
            finish();
            return;
        }

        // Keep track of the active emergency ID for cancel/escalate actions
        this.emergencyId = emergency.getEmergencyId();

        if (binding.textEmergencyType != null) {
            binding.textEmergencyType.setText(emergency.getEmergencyType());
        }

        if (binding.textLocationStatus != null) {
            binding.textLocationStatus.setText(
                    emergency.locationConfirmed
                            ? getString(R.string.location_shared_exact)
                            : getString(R.string.location_shared_approximate, emergency.approximateRadiusMeters > 0 ? emergency.approximateRadiusMeters : 100));
        }

        // Update SOS Header based on escalation status
        View root = binding.getRoot();
        View headerText = root.findViewWithTag("sos_header_text");
        if (headerText instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) headerText;
            if (emergency.locationConfirmed) {
                tv.setText("SOS ESCALATED");
                tv.setTextColor(0xFF00FF00); // Green
            } else {
                tv.setText("SOS ACTIVE");
                tv.setTextColor(0xFFFF4444); // Red
            }
        }
    }

    private void appendTriageMessage(@Nullable TriageMessage message) {
        if (message == null || triageAdapter == null) {
            return;
        }
        triageAdapter.append(message);
        if (binding.recyclerTriage != null) {
            binding.recyclerTriage.scrollToPosition(triageAdapter.getItemCount() - 1);
        }
    }

    private void renderResponder(@Nullable Responder responder) {
        if (binding.groupResponder == null) {
            return;
        }

        if (responder == null) {
            binding.groupResponder.setVisibility(View.GONE);
            return;
        }

        binding.groupResponder.setVisibility(View.VISIBLE);
        if (binding.textResponderName != null) {
            binding.textResponderName.setText(responder.getDisplayName());
        }
        if (binding.textResponderTrustScore != null) {
            binding.textResponderTrustScore.setText(
                    getString(R.string.responder_trust_score, responder.getTrustScore(), responder.getCompletedAssists()));
        }
        if (binding.textResponderEta != null) {
            binding.textResponderEta.setText(getString(R.string.responder_eta, responder.getEtaMinutes()));
        }
        if (binding.iconVerifiedBadge != null) {
            binding.iconVerifiedBadge.setVisibility(responder.isVerified() ? View.VISIBLE : View.GONE);
        }
    }

    private void renderHospital(@Nullable Hospital hospital) {
        if (binding.groupHospital == null) {
            return;
        }

        if (hospital == null) {
            binding.groupHospital.setVisibility(View.GONE);
            return;
        }

        binding.groupHospital.setVisibility(View.VISIBLE);
        if (binding.textHospitalName != null) {
            binding.textHospitalName.setText(hospital.getName());
        }
        if (binding.textHospitalReason != null) {
            binding.textHospitalReason.setText(hospital.routingReason != null ? hospital.routingReason : "Recommended Facility");
        }
        if (binding.textHospitalDistance != null) {
            binding.textHospitalDistance.setText(getString(R.string.hospital_distance_km, hospital.distanceKm));
        }
    }

    private void confirmCancel() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_cancel_sos_title)
                .setMessage(R.string.dialog_cancel_sos_message)
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> cancelSos())
                .setNegativeButton(R.string.action_dismiss, null)
                .show();
    }

    private void cancelSos() {
        if (emergencyId != null) {
            SOSService.cancel(this, emergencyId);
        }
        finish();
    }

    private void escalate() {
        if (emergencyId != null) {
            SOSService.escalate(this, emergencyId);
        }
    }

}
