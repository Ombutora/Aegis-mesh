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
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.aegismesh.database.EmergencyDbHelper;
import com.aegismesh.models.DispatchResult;
import com.aegismesh.models.Emergency;
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
 * The central coordinator responsible for managing emergency events. It
 * coordinates SOS activation, foreground services, GPS location retrieval,
 * backend transmissions, peer-to-peer mesh failovers, retries, and local
 * persistence.
 */
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
    private static final long RETRY_DELAY_MS = 10000; // 10 seconds

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object locationLock = new Object();
    private final Object meshLock = new Object();

    // Services connection state
    private LocationService locationService;
    private boolean isLocationServiceBound = false;

    private MeshService meshService;
    private boolean isMeshServiceBound = false;

    private ConnectivityManager.NetworkCallback networkCallback;
    private EmergencyDbHelper dbHelper;

    private final ServiceConnection locationServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationService.LocalBinder binder = (LocationService.LocalBinder) service;
            locationService = binder.getService();
            isLocationServiceBound = true;
            Log.d(TAG, "Bound to LocationService successfully.");
            synchronized (locationLock) {
                locationLock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            locationService = null;
            isLocationServiceBound = false;
            Log.d(TAG, "Unbound/Disconnected from LocationService.");
        }
    };

    private final ServiceConnection meshServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MeshService.LocalBinder binder = (MeshService.LocalBinder) service;
            meshService = binder.getService();
            isMeshServiceBound = true;
            Log.d(TAG, "Bound to MeshService successfully.");
            synchronized (meshLock) {
                meshLock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            meshService = null;
            isMeshServiceBound = false;
            Log.d(TAG, "Unbound/Disconnected from MeshService.");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "SOSService onCreate: Initializing components...");
        dbHelper = EmergencyDbHelper.getInstance(this);

        createNotificationChannel();
        registerNetworkCallback();
        scheduleBackupRecovery();

        // Bind to LocationService immediately
        Intent locationIntent = new Intent(this, LocationService.class);
        bindService(locationIntent, locationServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start foreground immediately to comply with Android 8.0+ rules
        startForeground(NOTIFICATION_ID, buildNotification("Emergency Mode Active", "Sending SOS Alert..."));

        if (intent != null && ACTION_TRIGGER_SOS.equals(intent.getAction())) {
            final String rawUserId = intent.getStringExtra(EXTRA_USER_ID);
            final String rawTrigger = intent.getStringExtra(EXTRA_TRIGGER_TYPE);
            final String rawEmergency = intent.getStringExtra(EXTRA_EMERGENCY_TYPE);
            final String rawNotes = intent.getStringExtra(EXTRA_ADDITIONAL_NOTES);

            // Input Validation and Sanitization
            final String userId = sanitizeInput(rawUserId, "UNKNOWN_USER");
            final String triggerType = validateTriggerType(rawTrigger);
            final String emergencyType = sanitizeInput(rawEmergency, "GENERAL");
            final String additionalNotes = sanitizeNotes(rawNotes);

            Log.i(TAG, "SOS triggered with Trigger=" + triggerType + ", Emergency=" + emergencyType);

            // Execute coordinates fetching and transmission workflow in background thread
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        processSos(userId, triggerType, emergencyType, additionalNotes);
                    } catch (Exception e) {
                        Log.e(TAG, "Unhandled exception in SOS thread execution: " + e.getMessage(), e);
                    }
                }
            });
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
        Log.i(TAG, "SOSService onDestroy: Cleaning up resources...");

        unregisterNetworkCallback();

        if (isLocationServiceBound) {
            unbindService(locationServiceConnection);
            isLocationServiceBound = false;
        }

        safeUnbindMesh();
        executor.shutdown();
    }

    /**
     * Executes the main coordinate aggregation and communication failover
     * sequence.
     */
    private void processSos(String userId, String triggerType, String emergencyType, String additionalNotes) {
        updateNotification("Emergency Mode Active", "Sending SOS Alert...");

        // Coordinate collection
        double latitude = 0.0;
        double longitude = 0.0;
        float accuracy = 0.0f;
        long locationTimestamp = System.currentTimeMillis();

        synchronized (locationLock) {
            if (locationService == null) {
                try {
                    // Wait up to 3 seconds for bound connection
                    locationLock.wait(3000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted waiting for LocationService binder: " + e.getMessage());
                }
            }
        }

        if (locationService != null) {
            final CountDownLatch latch = new CountDownLatch(1);
            final double[] coordinates = new double[2];
            final float[] accResult = new float[1];
            final long[] timeResult = new long[1];
            final String[] errorHolder = new String[1];

            locationService.getCurrentLocation(new LocationService.LocationCallback() {
                @Override
                public void onLocationRetrieved(double lat, double lon, float acc, long timestamp) {
                    coordinates[0] = lat;
                    coordinates[1] = lon;
                    accResult[0] = acc;
                    timeResult[0] = timestamp;
                    latch.countDown();
                }

                @Override
                public void onLocationError(String error) {
                    errorHolder[0] = error;
                    latch.countDown();
                }
            });

            try {
                // Wait up to 10 seconds for location readings
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    errorHolder[0] = "Location acquisition timed out.";
                }
            } catch (InterruptedException e) {
                errorHolder[0] = "Location acquisition process was interrupted.";
            }

            if (errorHolder[0] == null) {
                latitude = coordinates[0];
                longitude = coordinates[1];
                accuracy = accResult[0];
                locationTimestamp = timeResult[0];
                Log.i(TAG, "Location acquired: Lat=" + latitude + ", Lon=" + longitude + ", Acc=" + accuracy);
            } else {
                Log.w(TAG, "Could not acquire location: " + errorHolder[0] + " Proceeding with fallback coords.");
            }
        } else {
            Log.w(TAG, "LocationService binder is unavailable. Proceeding with fallback coords.");
        }

        // Build Emergency Model
        String emergencyId = UUID.randomUUID().toString();
        Emergency emergency = new Emergency(
                emergencyId,
                userId,
                triggerType,
                emergencyType,
                latitude,
                longitude,
                locationTimestamp,
                Emergency.STATUS_PENDING
        );

        // Resolve the logged-in victim's profile from the cached session.
        // The backend payload (toBackendJsonString) requires this to embed medical/profile data.
        User victim = UserSession.getInstance().getCurrentUser();
        if (victim == null) {
            Log.w(TAG, "No cached User found in UserSession; backend payload will be incomplete.");
        }

        // Network check
        if (isNetworkAvailable()) {
            sendViaInternet(emergency, victim);
        } else {
            Log.i(TAG, "Switching to mesh mode");
            dbHelper.insertOrUpdate(emergency);
            sendViaMeshNetwork(emergency);
        }
    }

    /**
     * Attempts internet-based API delivery with a retry logic.
     *
     * @param emergency the emergency event to transmit
     * @param victim the logged-in user's profile, embedded in the backend
     * payload; may be {@code null} if the session cache was unavailable, in
     * which case the backend call is skipped and the alert falls back to mesh
     * delivery
     */
    private void sendViaInternet(Emergency emergency, User victim) {
        updateNotification("Emergency Mode Active", "Sending emergency alert...");
        Log.i(TAG, "Sending to backend");

        if (victim == null) {
            Log.e(TAG, "Cannot send to backend without a User profile. Falling back to mesh.");
            dbHelper.insertOrUpdate(emergency);
            sendViaMeshNetwork(emergency);
            return;
        }

        boolean success = false;
        DispatchResult dispatchResult = null;
        for (int attempt = 1; attempt <= RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                dispatchResult = ApiClient.sendEmergency(emergency, victim);
                success = true;
                break;
            } catch (Exception e) {
                Log.e(TAG, "Retry attempt " + attempt + " failed: " + e.getMessage());
                if (attempt < RETRY_MAX_ATTEMPTS) {
                    updateNotification("Emergency Mode Active", "Unable to deliver emergency alert. Retrying...");
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Log.w(TAG, "Retry sleep interrupted: " + ie.getMessage());
                        break;
                    }
                }
            }
        }

        if (success) {
            Log.i(TAG, "Backend success");
            emergency.setStatus(Emergency.STATUS_DELIVERED);
            dbHelper.insertOrUpdate(emergency);

            // Surface the AI first-aid guidance and hospital routing decision.
            // TODO: broadcast/pass dispatchResult to EmergencyActivity for on-screen display
            // once that UI is built, rather than relying on the notification text alone.
            String hospitalName = "nearest facility";
            if (dispatchResult != null && dispatchResult.getRecommendedHospital() != null) {
                hospitalName = dispatchResult.getRecommendedHospital().getName();
                Log.i(TAG, "AI first-aid instructions: " + dispatchResult.getAiFirstAidInstructions());
            }
            updateNotification("Emergency Mode Active", "Alert delivered. Routed to " + hospitalName + ".");
            Log.i(TAG, "Emergency completed");
        } else {
            Log.w(TAG, "All internet retries failed. Storing locally as FAILED and switching to mesh.");
            emergency.setStatus(Emergency.STATUS_FAILED);
            dbHelper.insertOrUpdate(emergency);
            sendViaMeshNetwork(emergency);
        }
    }

    /**
     * Relays the emergency packet through Bluetooth Mesh or Wi-Fi Direct.
     */
    private void sendViaMeshNetwork(final Emergency emergency) {
        updateNotification("Emergency Mode Active", "Internet unavailable. Using mesh network.");

        synchronized (meshLock) {
            if (meshService == null) {
                Intent meshIntent = new Intent(this, MeshService.class);
                bindService(meshIntent, meshServiceConnection, Context.BIND_AUTO_CREATE);
                try {
                    meshLock.wait(3000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted binding to MeshService: " + e.getMessage());
                }
            }
        }

        if (meshService != null) {
            meshService.sendEmergencyOverMesh(emergency, new MeshService.MeshCallback() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Mesh delivery success");
                    emergency.setStatus(Emergency.STATUS_SENT); // SENT indicates successfully relayed in mesh
                    dbHelper.insertOrUpdate(emergency);
                    updateNotification("Emergency Mode Active", "Emergency alert delivered successfully.");
                    safeUnbindMesh();
                }

                @Override
                public void onFailure(String error) {
                    Log.e(TAG, "Mesh relay failed: " + error);
                    // Remains in database with previous state (FAILED)
                    safeUnbindMesh();
                }
            });
        } else {
            Log.e(TAG, "MeshService binding failed. Retransmission will rely on background recovery.");
        }
    }

    /**
     * Unbinds the MeshService connection dynamically when no longer required.
     */
    private void safeUnbindMesh() {
        if (isMeshServiceBound) {
            try {
                unbindService(meshServiceConnection);
            } catch (Exception e) {
                Log.e(TAG, "Unbind mesh service exception: " + e.getMessage());
            }
            isMeshServiceBound = false;
            meshService = null;
        }
    }

    /**
     * Installs ConnectivityManager.NetworkCallback to recover failed emergency
     * alerts in real-time.
     */
    private void registerNetworkCallback() {
        final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    Log.i(TAG, "Internet connectivity restored. Triggering dynamic recovery...");
                    triggerImmediateResend();
                }
            };
            cm.registerNetworkCallback(request, networkCallback);
            Log.d(TAG, "Dynamic NetworkCallback registered.");
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                try {
                    cm.unregisterNetworkCallback(networkCallback);
                } catch (Exception e) {
                    Log.e(TAG, "Error unregistering NetworkCallback: " + e.getMessage());
                }
            }
            networkCallback = null;
            Log.d(TAG, "Dynamic NetworkCallback unregistered.");
        }
    }

    /**
     * Queries the local database for pending/failed reports and transmits them
     * upon connection recovery.
     */
    private void triggerImmediateResend() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                List<Emergency> unsentList = dbHelper.getUnsentEmergencies();
                if (unsentList.isEmpty()) {
                    return;
                }

                User victim = UserSession.getInstance().getCurrentUser();
                if (victim == null) {
                    Log.w(TAG, "No cached User found in UserSession; skipping resend until session is restored.");
                    return;
                }

                Log.i(TAG, "Internet restored: Resending " + unsentList.size() + " unsent emergency alert(s)...");
                for (Emergency emergency : unsentList) {
                    try {
                        DispatchResult dispatchResult = ApiClient.sendEmergency(emergency, victim);
                        if (dispatchResult != null && dispatchResult.getRecommendedHospital() != null) {
                            Log.i(TAG, "Resent alert routed to " + dispatchResult.getRecommendedHospital().getName());
                        }
                        dbHelper.updateStatus(emergency.getEmergencyId(), Emergency.STATUS_DELIVERED);
                        Log.i(TAG, "Emergency alert " + emergency.getEmergencyId() + " successfully resent and delivered.");
                        updateNotification("Emergency Mode Active", "Emergency alert delivered successfully.");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to resend emergency alert " + emergency.getEmergencyId() + " on restoration: " + e.getMessage());
                        dbHelper.updateStatus(emergency.getEmergencyId(), Emergency.STATUS_FAILED);
                    }
                }
            }
        });
    }

    /**
     * Schedules the background Eventual Recovery WorkManager task.
     */
    private void scheduleBackupRecovery() {
        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                    EmergencyResendWorker.class,
                    15, TimeUnit.MINUTES
            )
                    .setConstraints(constraints)
                    .build();

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "EmergencyResendWorker",
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
            );
            Log.i(TAG, "Periodic resend worker successfully registered in WorkManager.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule WorkManager periodic request: " + e.getMessage(), e);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            @SuppressWarnings("deprecation")
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SOS Emergency Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Shows active emergency broadcast states for Aegis Mesh.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String title, String content) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        // Point click action back to Main Activity / Emergency Status page
        Intent notificationIntent = new Intent();
        notificationIntent.setClassName(this, "com.aegismesh.activities.EmergencyActivity");
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.setContentIntent(pendingIntent);

        return builder.build();
    }

    private void updateNotification(String title, String content) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(title, content));
        }
    }

    // --- Input Sanitation & Validation Helpers ---
    private String sanitizeInput(String input, String defaultValue) {
        if (input == null || input.trim().isEmpty()) {
            return defaultValue;
        }
        // Basic alphanumeric and formatting filter to sanitize inputs
        return input.replaceAll("[^a-zA-Z0-9_\\-\\s]", "").trim();
    }

    private String validateTriggerType(String trigger) {
        if (trigger == null) {
            return MANUAL_TRIGGER;
        }
        switch (trigger) {
            case MANUAL_TRIGGER:
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
        // Sanitize note strings against potential script injections, capping length at 250 characters.
        String filtered = notes.replaceAll("[<>\"'&/\\\\]", "").trim();
        if (filtered.length() > 250) {
            return filtered.substring(0, 250);
        }
        return filtered;
    }
}
