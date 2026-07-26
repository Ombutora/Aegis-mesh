package com.aegismesh.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aegismesh.R;
import com.aegismesh.adapters.TriageMessageAdapter;
import com.aegismesh.databinding.ActivityEmergencyBinding;
import com.aegismesh.models.Emergency;
import com.aegismesh.models.Hospital;
import com.aegismesh.models.Responder;
import com.aegismesh.models.TriageMessage;
import com.aegismesh.services.SOSService;

import java.util.ArrayList;

/**
 * Active-emergency screen.
 *
 * Shown immediately after an SOS is triggered and stays active for the
 * duration of the incident. It surfaces three concurrent data streams from
 * {@link SOSService}, each backed by a LiveData the service updates as
 * mesh relays, responders, and the AI triage engine report in:
 *
 *   1. Mesh relay status  - how the alert is propagating peer-to-peer.
 *   2. AI first-aid guidance - step-by-step instructions while help is en route.
 *   3. Responder + hospital routing - who is coming and where the victim
 *      should ultimately be taken.
 *
 * Per the security proposal, the victim's exact location is only released
 * to a responder after acceptance/confirmation; this screen reflects that
 * by showing "approximate" location language until [Emergency.locationConfirmed]
 * flips to true.
 */
public class EmergencyActivity extends AppCompatActivity {

    public static final String EXTRA_EMERGENCY_ID = "extra_emergency_id";

    private ActivityEmergencyBinding binding;
    private TriageMessageAdapter triageAdapter;
    @Nullable private String emergencyId;

    private final Observer<Emergency> emergencyObserver = this::renderEmergency;
    private final Observer<TriageMessage> triageObserver = this::appendTriageMessage;
    private final Observer<Responder> responderObserver = this::renderResponder;
    private final Observer<Hospital> hospitalObserver = this::renderHospital;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEmergencyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        emergencyId = getIntent().getStringExtra(EXTRA_EMERGENCY_ID);
        triageAdapter = new TriageMessageAdapter(new ArrayList<>());
        binding.recyclerTriage.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTriage.setAdapter(triageAdapter);

        binding.buttonCancelSos.setOnClickListener(v -> confirmCancel());
        binding.buttonEscalate.setOnClickListener(v -> escalate());

                // TEMPORARY TEST BUTTON FOR EMULATOR
        Button btnForceSos = new Button(this);
        btnForceSos.setText("FORCE TRIGGER SOS (TEST)");
        btnForceSos.setOnClickListener(v -> {
            // Manually trigger the callback as if C++ detected 3 shakes
            onSosTriggered();
        });
        // Add it to your layout (Assuming your root layout is a LinearLayout or ConstraintLayout)
        ((android.view.ViewGroup) findViewById(android.R.id.content)).addView(btnForceSos);

        observeActiveEmergency();
    }

    private void observeActiveEmergency() {
        SOSService.getActiveEmergency().observe(this, emergencyObserver);
        SOSService.getTriageMessages().observe(this, triageObserver);
        SOSService.getAssignedResponder().observe(this, responderObserver);
        SOSService.getRecommendedHospital().observe(this, hospitalObserver);
    }

    private void renderEmergency(@Nullable Emergency emergency) {
        if (emergency == null) {
            // Emergency was resolved or cancelled elsewhere (e.g. another device).
            finish();
            return;
        }

        binding.textEmergencyType.setText(emergency.type.getDisplayName());
        binding.textMeshRelayStatus.setText(
                getResources().getQuantityString(
                        R.plurals.mesh_relay_peer_count,
                        emergency.relayHopCount,
                        emergency.relayHopCount));

        binding.textLocationStatus.setText(
                emergency.locationConfirmed
                        ? getString(R.string.location_shared_exact)
                        : getString(R.string.location_shared_approximate, emergency.approximateRadiusMeters));
    }

    private void appendTriageMessage(@Nullable TriageMessage message) {
        if (message == null) return;
        triageAdapter.append(message);
        binding.recyclerTriage.scrollToPosition(triageAdapter.getItemCount() - 1);
    }

    private void renderResponder(@Nullable Responder responder) {
        if (responder == null) {
            binding.groupResponder.setVisibility(View.GONE);
            return;
        }

        binding.groupResponder.setVisibility(View.VISIBLE);
        binding.textResponderName.setText(responder.displayName);
        binding.textResponderTrustScore.setText(
                getString(R.string.responder_trust_score, responder.trustScore, responder.completedAssists));
        binding.textResponderEta.setText(getString(R.string.responder_eta, responder.etaMinutes));
        binding.iconVerifiedBadge.setVisibility(responder.isVerified ? View.VISIBLE : View.GONE);
    }

    private void renderHospital(@Nullable Hospital hospital) {
        if (hospital == null) {
            binding.groupHospital.setVisibility(View.GONE);
            return;
        }

        binding.groupHospital.setVisibility(View.VISIBLE);
        binding.textHospitalName.setText(hospital.name);
        binding.textHospitalReason.setText(hospital.routingReason);
        binding.textHospitalDistance.setText(getString(R.string.hospital_distance_km, hospital.distanceKm));
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
        // Notifies trusted contacts, nearby responders, and emergency
        // services, and begins continuous GPS tracking - see
        // "Automatic Escalation" in the security proposal.
        if (emergencyId != null) {
            SOSService.escalate(this, emergencyId);
        }
    }

    @Override
    public void onBackPressed() {
        // Prevent accidental dismissal of an active emergency; require
        // an explicit cancel action instead.
        confirmCancel();
    }
}
