package com.aegismesh.activities;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import com.aegismesh.databinding.ActivityResponderMapBinding;
import com.aegismesh.models.Emergency;
import com.aegismesh.services.ResponderService;

/**
 * Responder-facing screen: shows the currently-offered emergency and lets the
 * responder Accept or Deny it.
 *
 * Launch this Activity whenever ResponderService.getOfferedEmergency() posts a
 * non-null value (e.g. from a notification tap once push/poll delivery exists,
 * or directly if the app is already foregrounded). This class does not itself
 * listen for incoming offers globally -- ResponderService owns that; this
 * Activity only reacts to and acts on the current one.
 */
public class ResponderMapActivity extends AppCompatActivity {

    private ActivityResponderMapBinding binding;

    @Nullable
    private Emergency currentOffer;

    private final Observer<Emergency> offerObserver = this::renderOffer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResponderMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.buttonAcceptOffer.setOnClickListener(v -> handleAccept());
        binding.buttonDenyOffer.setOnClickListener(v -> handleDeny());

        ResponderService.getOfferedEmergency().observe(this, offerObserver);
    }

    private void renderOffer(@Nullable Emergency emergency) {
        currentOffer = emergency;

        if (emergency == null) {
            // Offer cleared (accepted, denied, or expired elsewhere) --
            // nothing left for this screen to do.
            finish();
            return;
        }

        binding.textResponderEmergencyType.setText(emergency.getEmergencyType());
        binding.textResponderLocation.setText(
                emergency.locationConfirmed
                        ? "Exact location shared"
                        : "Approximate location (~"
                        + (emergency.approximateRadiusMeters > 0 ? emergency.approximateRadiusMeters : 100)
                        + "m radius)");
    }

    private void handleAccept() {
        if (currentOffer == null || currentOffer.getEmergencyId() == null) {
            return;
        }
        setActionButtonsEnabled(false);
        ResponderService.acceptOffer(this, currentOffer.getEmergencyId(), new ResponderService.ResponderCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ResponderMapActivity.this, "Accepted", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    setActionButtonsEnabled(true);
                    Toast.makeText(ResponderMapActivity.this, "Could not accept: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void handleDeny() {
        if (currentOffer == null || currentOffer.getEmergencyId() == null) {
            return;
        }

        final String[] reasons = {"TOO_FAR", "UNSAFE_SCENE", "UNAVAILABLE", "WRONG_SPECIALTY", "OTHER"};
        final String[] displayReasons = {"Too far away", "Scene looks unsafe", "Currently unavailable", "Wrong medical specialty", "Other"};

        new android.app.AlertDialog.Builder(this)
                .setTitle("Select Reason")
                .setItems(displayReasons, (dialog, which) -> {
                    String selectedReason = reasons[which];
                    setActionButtonsEnabled(false);
                    ResponderService.denyOffer(ResponderMapActivity.this, currentOffer.getEmergencyId(), selectedReason, new ResponderService.ResponderCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                Toast.makeText(ResponderMapActivity.this, "Denied", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }

                        @Override
                        public void onFailure(String error) {
                            runOnUiThread(() -> {
                                setActionButtonsEnabled(true);
                                Toast.makeText(ResponderMapActivity.this, "Could not deny: " + error, Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setActionButtonsEnabled(boolean enabled) {
        binding.buttonAcceptOffer.setEnabled(enabled);
        binding.buttonDenyOffer.setEnabled(enabled);
    }
}
