package com.aegismesh.services;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.aegismesh.models.Emergency;
import com.aegismesh.network.ApiClient;

/**
 * Responder-side coordinator: holds the currently-offered emergency (if any)
 * and exposes accept/deny actions on it.
 *
 * NOT YET WIRED TO A BACKEND. The FastAPI backend has no confirmed
 * offer/accept/deny/arrived/complete endpoints as of this build -- see the
 * TODOs in acceptOffer()/denyOffer() below. Until those exist, this class only
 * manages local state so the Accept/Deny UI (ResponderMapActivity) can be built
 * and tested independently of the backend.
 *
 * How an offer is expected to arrive (not yet implemented): - Online: a backend
 * push/poll delivers an Emergency this responder is being offered -- call
 * offerEmergency() when that happens. - Offline/mesh: a peer relays an
 * emergency via BleMeshManager; once mesh-propagated ACCEPT exists (separate
 * follow-up piece), the same offerEmergency() entry point should be used so the
 * UI layer doesn't need to know which transport the offer came through.
 */
public class ResponderService {

    private static final String TAG = "ResponderService";

    private static final MutableLiveData<Emergency> offeredEmergency = new MutableLiveData<>();

    public static LiveData<Emergency> getOfferedEmergency() {
        return offeredEmergency;
    }

    public interface ResponderCallback {

        void onSuccess();

        void onFailure(String error);
    }

    /**
     * Call this when this responder has been offered an emergency, from
     * whichever transport the offer arrived on. Posting a new value here is
     * what should trigger ResponderMapActivity to show the Accept/Deny UI.
     */
    public static void offerEmergency(Emergency emergency) {
        Log.i(TAG, "Emergency offered to this responder: " + emergency.getEmergencyId());
        offeredEmergency.postValue(emergency);
    }

    /**
     * Clears the current offer without accepting or denying it -- e.g. if the
     * offer expires, or the emergency is cancelled/reassigned elsewhere.
     */
    public static void clearOffer() {
        offeredEmergency.postValue(null);
    }

    public static void acceptOffer(Context context, String emergencyId, ResponderCallback callback) {
        Emergency current = offeredEmergency.getValue();
        if (current == null || current.getEmergencyId() == null || !current.getEmergencyId().equals(emergencyId)) {
            if (callback != null) {
                callback.onFailure("No matching offer is currently active.");
            }
            return;
        }

        // Use mock responder ID 1 for prototype
        int mockResponderId = 1;

        // Offline / Mesh scenario: if we don't have internet, we accept locally
        // and notify meshManager. A robust offline sync would retry the API
        // call later when online and handle the 409 conflict gracefully.
        new Thread(() -> {
            try {
                ApiClient.acceptEmergency(emergencyId, mockResponderId);
                
                // Start location tracking
                ResponderLocationTracker.startTracking(context, emergencyId, mockResponderId);
                
                offeredEmergency.postValue(null);
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                Log.w(TAG, "Backend accept failed, attempting local/mesh accept: " + errorMsg);
                
                // Flag acceptance via mesh if applicable
                com.aegismesh.mesh.BleMeshManager.setAcceptedEmergency(emergencyId);

                // If it was already accepted by someone else on backend (409 conflict):
                if (errorMsg.contains("409")) {
                    if (callback != null) {
                        callback.onFailure("Emergency was already accepted by another responder.");
                    }
                    offeredEmergency.postValue(null);
                } else {
                    // Start location tracking anyway for offline case
                    ResponderLocationTracker.startTracking(context, emergencyId, mockResponderId);
                    
                    offeredEmergency.postValue(null);
                    if (callback != null) {
                        callback.onSuccess();
                    }
                }
            }
        }).start();
    }

    public static void denyOffer(Context context, String emergencyId, String reason, ResponderCallback callback) {
        Emergency current = offeredEmergency.getValue();
        if (current == null || current.getEmergencyId() == null || !current.getEmergencyId().equals(emergencyId)) {
            if (callback != null) {
                callback.onFailure("No matching offer is currently active.");
            }
            return;
        }

        int mockResponderId = 1;

        new Thread(() -> {
            try {
                ApiClient.denyEmergency(emergencyId, mockResponderId, reason);
                offeredEmergency.postValue(null);
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.w(TAG, "Backend deny failed: " + e.getMessage());
                // For prototype, we treat offline deny as success locally
                offeredEmergency.postValue(null);
                if (callback != null) {
                    callback.onSuccess();
                }
            }
        }).start();
    }
}
