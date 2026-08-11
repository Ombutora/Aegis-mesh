package com.aegismesh.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.aegismesh.activities.EmergencyActivity;
import com.aegismesh.activities.ProfileActivity;
import com.aegismesh.ai.OfflineFirstAidEngine;
import com.aegismesh.database.EmergencyDbHelper;
import com.aegismesh.models.DispatchResult;
import com.aegismesh.models.Emergency;
import com.aegismesh.models.Hospital;
import com.aegismesh.models.Responder;
import com.aegismesh.models.TriageMessage;
import com.aegismesh.models.User;
import com.aegismesh.network.ApiClient;
import com.aegismesh.session.UserSession;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The central coordinator responsible for managing emergency events.
 *
 * NOTE: a previous version of this file included connectWebSocketForUpdates(),
 * a hand-rolled WebSocket client built on a blocking `while (true) { in.read()
 * }` loop submitted to the SAME single-thread executor used for all other SOS
 * work. Because the executor is single-threaded, that infinite blocking read
 * would permanently occupy the only worker thread the first time a responder
 * was assigned online -- silently freezing all future SOS processing (new
 * triggers, mesh fallback, resend) for the rest of the service's lifetime, with
 * no error logged anywhere. It has been removed here rather than patched in
 * place. Real-time responder location/status updates are a legitimate future
 * feature, but need their own dedicated thread and lifecycle (started/stopped
 * with the active emergency, not queued onto shared work), built as an explicit
 * separate step.
 */
import java.util.concurrent.ScheduledExecutorService;

public class SOSService extends Service {

    private static final String TAG = "SOSService";

    // Intent Actions and Extras
    public static final String ACTION_TRIGGER_SOS = "com.aegismesh.action.TRIGGER_SOS";
    public static final String EXTRA_USER_ID = "com.aegismesh.extra.USER_ID";
    public static final String EXTRA_TRIGGER_TYPE = "com.aegismesh.extra.TRIGGER_TYPE";
    public static final String EXTRA_EMERGENCY_TYPE = "com.aegismesh.extra.EMERGENCY_TYPE";
    public static final String EXTRA_ADDITIONAL_NOTES = "com.aegismesh.extra.ADDITIONAL_NOTES";

    // Trigger constants
    public static final String MANUAL_TRIGGER = "MANUAL_TRIGGER";
    public static final String GESTURE_TRIGGER = "GESTURE_TRIGGER";
    public static final String FALL_TRIGGER = "FALL_TRIGGER";
    public static final String VOICE_TRIGGER = "VOICE_TRIGGER";

    private static final String CHANNEL_ID = "SOS_SERVICE_CHANNEL";
    private static final int NOTIFICATION_ID = 911;
    private static final int RETRY_MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 10000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object locationLock = new Object();
    private final Object meshLock = new Object();
    
    private ScheduledExecutorService locationPollExecutor;

    // LiveData for UI observation
    private static final MutableLiveData<Emergency> activeEmergency = new MutableLiveData<>();
    private static final MutableLiveData<TriageMessage> triageMessages = new MutableLiveData<>();
    private static final MutableLiveData<Responder> assignedResponder = new MutableLiveData<>();
    private static final MutableLiveData<Hospital> recommendedHospital = new MutableLiveData<>();

    public static LiveData<Emergency> getActiveEmergency() {
        return activeEmergency;
    }

    public static LiveData<TriageMessage> getTriageMessages() {
        return triageMessages;
    }

    public static LiveData<Responder> getAssignedResponder() {
        return assignedResponder;
    }

    public static LiveData<Hospital> getRecommendedHospital() {
        return recommendedHospital;
    }

    public static void trigger(Context context) {
        Intent intent = new Intent(context, SOSService.class);
        intent.setAction(ACTION_TRIGGER_SOS);
        User currentUser = UserSession.getInstance().getCurrentUser();
        if (currentUser != null) {
            intent.putExtra(EXTRA_USER_ID, currentUser.getFullName());
            String condition = "GENERAL";
            if (currentUser.getMedicalProfile() != null) {
                condition = currentUser.getMedicalProfile().chronicIllnessesCsv();
                if (condition.isEmpty()) {
                    condition = "GENERAL";
                }
            }
            intent.putExtra(EXTRA_EMERGENCY_TYPE, condition);
        } else {
            intent.putExtra(EXTRA_EMERGENCY_TYPE, "GENERAL");
        }
        intent.putExtra(EXTRA_TRIGGER_TYPE, MANUAL_TRIGGER);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void cancel(Context context, String emergencyId) {
        Log.i(TAG, "Cancelling SOS: " + emergencyId);
        activeEmergency.postValue(null);
        triageMessages.postValue(null);
        assignedResponder.postValue(null);
        recommendedHospital.postValue(null);
        // Location polling is stopped in onDestroy() when the service is torn down.
        
        Intent intent = new Intent(context, SOSService.class);
        context.stopService(intent);
    }

    public static void escalate(Context context, String emergencyId) {
        Log.i(TAG, "Escalating SOS: " + emergencyId);
        Emergency current = activeEmergency.getValue();
        if (current != null) {
            current.locationConfirmed = true;
            current.approximateRadiusMeters = 0;
            activeEmergency.postValue(current);
        }
    }

    private LocationService locationService;
    private boolean isLocationServiceBound = false;
    private MeshService meshService;
    private boolean isMeshServiceBound = false;
    private ConnectivityManager.NetworkCallback networkCallback;
    private EmergencyDbHelper dbHelper;

    /**
     * Observes MeshService's static helper-found LiveData for the lifetime of
     * this service. Fires whenever ANY BLE peer acknowledges receipt of ANY
     * emergency this device sent -- the emergencyId check below filters that
     * down to only the currently active emergency. Uses observeForever()
     * because Service is not a LifecycleOwner; explicitly removed in
     * onDestroy() to avoid leaking the observer.
     *
     * NOTE: this represents "a nearby device confirmed receipt," not "a person
     * explicitly accepted to help" -- it populates assignedResponder with a
     * lightweight Responder.meshVolunteer() placeholder so the existing UI has
     * something to show even fully offline. Real ACCEPT/DENY with a named,
     * rated responder is a separate future phase.
     */
    private final Observer<MeshService.HelperFoundEvent> meshHelperFoundObserver = event -> {
        Emergency current = activeEmergency.getValue();
        if (current != null && current.getEmergencyId() != null && current.getEmergencyId().equals(event.emergencyId)) {
            Log.i(TAG, "Mesh volunteer found for active emergency: " + event.emergencyId);
            assignedResponder.postValue(Responder.meshVolunteer());
        }
    };

    /**
     * Observes MeshService's helperAcceptedLiveData. Fires when a BLE peer
     * explicitly signals it has ACCEPTED (not just acknowledged) the emergency.
     * This is the mesh-offline equivalent of the backend's atomic accept.
     *
     * Conflict resolution: backend state always wins. If the device is online
     * and the backend already assigned a different responder, the mesh accept
     * is silently superseded -- the UI will show the backend-assigned responder
     * from the location-polling loop instead.
     */
    private final Observer<MeshService.HelperFoundEvent> meshHelperAcceptedObserver = event -> {
        Emergency current = activeEmergency.getValue();
        if (current != null && current.getEmergencyId() != null && current.getEmergencyId().equals(event.emergencyId)) {
            Responder currentResponder = assignedResponder.getValue();
            // Only upgrade if we don't already have a backend-verified responder
            if (currentResponder == null || currentResponder.getTrustScore() == 0.0) {
                Log.i(TAG, "Mesh peer explicitly accepted emergency: " + event.emergencyId);
                Responder accepted = Responder.meshVolunteer();
                accepted.setDisplayName("Nearby Helper (Accepted)");
                assignedResponder.postValue(accepted);
            } else {
                Log.i(TAG, "Mesh accept received but backend-assigned responder already present — backend wins.");
            }
        }
    };

    private final ServiceConnection locationServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationService.LocalBinder binder = (LocationService.LocalBinder) service;
            locationService = binder.getService();
            isLocationServiceBound = true;
            synchronized (locationLock) {
                locationLock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            locationService = null;
            isLocationServiceBound = false;
        }
    };

    private final ServiceConnection meshServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MeshService.LocalBinder binder = (MeshService.LocalBinder) service;
            meshService = binder.getService();
            isMeshServiceBound = true;
            synchronized (meshLock) {
                meshLock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            meshService = null;
            isMeshServiceBound = false;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = EmergencyDbHelper.getInstance(this);
        createNotificationChannel();
        registerNetworkCallback();
        scheduleBackupRecovery();
        Intent locationIntent = new Intent(this, LocationService.class);
        bindService(locationIntent, locationServiceConnection, Context.BIND_AUTO_CREATE);
        MeshService.getHelperFoundLiveData().observeForever(meshHelperFoundObserver);
        MeshService.getHelperAcceptedLiveData().observeForever(meshHelperAcceptedObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("Emergency Mode Active", "Sending SOS Alert..."));
        if (intent != null && ACTION_TRIGGER_SOS.equals(intent.getAction())) {
            String userId = sanitizeInput(intent.getStringExtra(EXTRA_USER_ID), "UNKNOWN_USER");
            String triggerType = validateTriggerType(intent.getStringExtra(EXTRA_TRIGGER_TYPE));
            String emergencyType = sanitizeInput(intent.getStringExtra(EXTRA_EMERGENCY_TYPE), "GENERAL");
            String notes = sanitizeNotes(intent.getStringExtra(EXTRA_ADDITIONAL_NOTES));
            executor.execute(() -> processSos(userId, triggerType, emergencyType, notes));
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        MeshService.getHelperFoundLiveData().removeObserver(meshHelperFoundObserver);
        MeshService.getHelperAcceptedLiveData().removeObserver(meshHelperAcceptedObserver);
        unregisterNetworkCallback();
        if (isLocationServiceBound) {
            unbindService(locationServiceConnection);
        }
        safeUnbindMesh();
        executor.shutdown();
        stopLocationPolling();
    }

    private void startLocationPolling(String emergencyId) {
        if (locationPollExecutor != null) return;
        locationPollExecutor = Executors.newSingleThreadScheduledExecutor();
        locationPollExecutor.scheduleAtFixedRate(() -> {
            try {
                double[] coords = ApiClient.getLocation(emergencyId);
                Responder currentResponder = assignedResponder.getValue();
                if (currentResponder != null) {
                    currentResponder.setLatitude(coords[0]);
                    currentResponder.setLongitude(coords[1]);
                    assignedResponder.postValue(currentResponder);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to poll responder location: " + e.getMessage());
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void stopLocationPolling() {
        if (locationPollExecutor != null) {
            locationPollExecutor.shutdownNow();
            locationPollExecutor = null;
        }
    }

    private void processSos(String userId, String triggerType, String emergencyType, String notes) {
        double latitude = 0.0, longitude = 0.0;
        long timestamp = System.currentTimeMillis();

        synchronized (locationLock) {
            if (locationService == null) {
                try {
                    locationLock.wait(3000);
                } catch (InterruptedException ignored) {
                }
            }
        }

        if (locationService != null) {
            CountDownLatch latch = new CountDownLatch(1);
            double[] coords = new double[2];
            locationService.getCurrentLocation(new LocationService.LocationCallback() {
                @Override
                public void onLocationRetrieved(double lat, double lon, float acc, long ts) {
                    coords[0] = lat;
                    coords[1] = lon;
                    latch.countDown();
                }

                @Override
                public void onLocationError(String error) {
                    latch.countDown();
                }
            });
            try {
                latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            latitude = coords[0];
            longitude = coords[1];
        }

        String emergencyId = UUID.randomUUID().toString();
        Emergency emergency = new Emergency(emergencyId, userId, triggerType, emergencyType, latitude, longitude, timestamp, Emergency.STATUS_PENDING);
        User victim = ProfileActivity.getSavedUser(this);
        activeEmergency.postValue(emergency);

        if (isNetworkAvailable()) {
            sendViaInternet(emergency, victim);
        } else {
            dbHelper.insertOrUpdate(emergency);
            sendViaMeshNetwork(emergency);
        }
    }

    private void sendViaInternet(Emergency emergency, User victim) {
        if (victim == null) {
            sendViaMeshNetwork(emergency);
            return;
        }
        executor.execute(() -> {
            try {
                DispatchResult result = ApiClient.sendEmergency(emergency, victim);
                emergency.setStatus(Emergency.STATUS_OFFERED);
                dbHelper.insertOrUpdate(emergency);
                activeEmergency.postValue(emergency);
                if (result.getRecommendedHospital() != null) {
                    recommendedHospital.postValue(result.getRecommendedHospital());
                }
                if (result.getAiFirstAidInstructions() != null) {
                    triageMessages.postValue(new TriageMessage(result.getAiFirstAidInstructions(), System.currentTimeMillis()));
                }
                if (result.getAssignedResponder() != null) {
                    assignedResponder.postValue(result.getAssignedResponder());
                    startLocationPolling(emergency.getEmergencyId());
                }
                updateNotification("Emergency Mode Active", "Alert delivered.");
            } catch (Exception e) {
                Log.w(TAG, "sendViaInternet failed, falling back to offline/mesh path: " + e.getMessage());
                sendViaMeshNetwork(emergency);
            }
        });
    }

    /**
     * Handles the offline/backend-unreachable path. Two things happen here,
     * independently of each other: 1. Local AI first-aid guidance is generated
     * immediately via OfflineFirstAidEngine and published to triageMessages, so
     * the victim gets instructions right away regardless of whether a mesh peer
     * is ever found. 2. The emergency is still handed to MeshService for peer
     * relay, in case a nearby device can forward it toward connectivity/a
     * responder. These are independent because a lack of mesh peers should
     * never block the victim from getting first-aid instructions.
     */
    private void sendViaMeshNetwork(Emergency emergency) {
        Log.i(TAG, "Generating offline AI first-aid guidance for emergency: " + emergency.getEmergencyId());
        try {
            DispatchResult offlineResult = OfflineFirstAidEngine.generate(emergency);
            if (offlineResult != null && offlineResult.getAiFirstAidInstructions() != null) {
                triageMessages.postValue(new TriageMessage(offlineResult.getAiFirstAidInstructions(), System.currentTimeMillis()));
                Log.i(TAG, "Offline first-aid guidance published for: " + emergency.getEmergencyId());
            }
            // OfflineFirstAidEngine currently returns a null recommended hospital
            // (no offline routing data available) -- recommendedHospital LiveData
            // is intentionally left untouched here rather than posting null,
            // so any previously-shown online recommendation isn't wiped out.
        } catch (Exception e) {
            Log.e(TAG, "Offline first-aid generation failed: " + e.getMessage(), e);
        }

        synchronized (meshLock) {
            if (meshService == null) {
                Intent meshIntent = new Intent(this, MeshService.class);
                bindService(meshIntent, meshServiceConnection, Context.BIND_AUTO_CREATE);
                try {
                    meshLock.wait(3000);
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (meshService != null) {
            meshService.sendEmergencyOverMesh(emergency, new MeshService.MeshCallback() {
                @Override
                public void onSuccess() {
                    emergency.setStatus(Emergency.STATUS_OFFERED);
                    dbHelper.insertOrUpdate(emergency);
                    safeUnbindMesh();
                }

                @Override
                public void onFailure(String error) {
                    Log.w(TAG, "Mesh relay failed for " + emergency.getEmergencyId() + ": " + error);
                    safeUnbindMesh();
                }
            });
        }
    }

    private void safeUnbindMesh() {
        if (isMeshServiceBound) {
            try {
                unbindService(meshServiceConnection);
            } catch (Exception ignored) {
            }
            isMeshServiceBound = false;
            meshService = null;
        }
    }

    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            cm.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                    new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    triggerImmediateResend();
                }
            });
        }
    }

    private void unregisterNetworkCallback() {
        /* logic to unregister if stored */ }

    private void triggerImmediateResend() {
        executor.execute(() -> {
            List<Emergency> unsent = dbHelper.getUnsentEmergencies();
            User victim = ProfileActivity.getSavedUser(this);
            if (victim == null) {
                return;
            }
            for (Emergency e : unsent) {
                try {
                    ApiClient.sendEmergency(e, victim);
                    dbHelper.updateStatus(e.getEmergencyId(), Emergency.STATUS_OFFERED);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void scheduleBackupRecovery() {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(EmergencyResendWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("EmergencyResendWorker", ExistingPeriodicWorkPolicy.KEEP, request);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities cap = cm.getNetworkCapabilities(network);
        return cap != null && (cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "SOS Emergency Channel", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String title, String content) {
        Intent intent = new Intent(this, EmergencyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title).setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(true).setContentIntent(pendingIntent).build();
    }

    private void updateNotification(String title, String content) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(title, content));
        }
    }

    private String sanitizeInput(String input, String def) {
        return (input == null || input.trim().isEmpty()) ? def : input.replaceAll("[^a-zA-Z0-9_\\-\\s]", "").trim();
    }

    private String validateTriggerType(String trigger) {
        if (trigger == null) {
            return MANUAL_TRIGGER;
        }
        switch (trigger) {
            case GESTURE_TRIGGER:
            case FALL_TRIGGER:
            case VOICE_TRIGGER:
                return trigger;
            default:
                return MANUAL_TRIGGER;
        }
    }

    private String sanitizeNotes(String notes) {
        if (notes == null) {
            return "";
        }
        String f = notes.replaceAll("[<>\"'&/\\\\]", "").trim();
        return f.length() > 250 ? f.substring(0, 250) : f;
    }
}
