package com.aegismesh.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.aegismesh.mesh.BleMeshManager;
import com.aegismesh.models.Emergency;
import com.aegismesh.models.MeshStatus;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service that handles local peer-to-peer mesh networking (BLE Mesh) when
 * traditional cellular/internet connection is unavailable.
 *
 * IMPORTANT: this service can be reached two ways -- MeshService.start()
 * (startService/startForegroundService) OR bindService() from SOSService.
 * onStartCommand() only fires on the FIRST path. Since SOSService currently
 * only binds, BLE initialization must live in onCreate(), which fires on BOTH
 * paths -- otherwise meshManager stays null on the bind-only path and
 * sendEmergencyOverMesh() fails silently.
 */
public class MeshService extends Service {

    private static final String TAG = "MeshService";
    private final IBinder binder = new LocalBinder();

    private static final MutableLiveData<MeshStatus> statusLiveData = new MutableLiveData<>(new MeshStatus.Online());

    private static final MutableLiveData<HelperFoundEvent> helperFoundLiveData = new MutableLiveData<>();
    private static final MutableLiveData<HelperFoundEvent> helperAcceptedLiveData = new MutableLiveData<>();

    private BleMeshManager meshManager;

    public static LiveData<MeshStatus> getStatusLiveData() {
        return statusLiveData;
    }

    public static LiveData<HelperFoundEvent> getHelperFoundLiveData() {
        return helperFoundLiveData;
    }

    public static LiveData<HelperFoundEvent> getHelperAcceptedLiveData() {
        return helperAcceptedLiveData;
    }

    /**
     * Simple event payload for a confirmed mesh helper-found signal.
     */
    public static class HelperFoundEvent {

        public final String peerAddress;
        public final String emergencyId;

        public HelperFoundEvent(String peerAddress, String emergencyId) {
            this.peerAddress = peerAddress;
            this.emergencyId = emergencyId;
        }
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, MeshService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: initializing BLE mesh transport.");
        initMeshManagerIfNeeded();
    }

    private void initMeshManagerIfNeeded() {
        if (meshManager != null) {
            return;
        }

        meshManager = new BleMeshManager(this, new BleMeshManager.Listener() {
            @Override
            public void onPeerCountChanged(int peerCount) {
                Log.d(TAG, "Mesh peer count changed: " + peerCount);
                statusLiveData.postValue(new MeshStatus.OfflineRelay(peerCount));
            }

            @Override
            public void onEmergencyReceived(Emergency emergency) {
                Log.i(TAG, "Received relayed emergency from peer: " + emergency.getEmergencyId()
                        + " (hop " + emergency.relayHopCount + ")");
                // TODO: hand off to your notification/responder-alert flow here.
            }

            @Override
            public void onMeshUnavailable(String reason) {
                Log.w(TAG, "Mesh unavailable: " + reason);
                statusLiveData.postValue(new MeshStatus.Disconnected());
            }

            @Override
            public void onHelperFound(String peerAddress, String emergencyId) {
                Log.i(TAG, "Helper found via mesh: peer=" + peerAddress + " emergency=" + emergencyId);
                helperFoundLiveData.postValue(new HelperFoundEvent(peerAddress, emergencyId));
            }

            @Override
            public void onHelperAccepted(String peerAddress, String emergencyId) {
                Log.i(TAG, "Helper explicitly accepted via mesh: peer=" + peerAddress + " emergency=" + emergencyId);
                helperAcceptedLiveData.postValue(new HelperFoundEvent(peerAddress, emergencyId));
            }
        });
        meshManager.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // This only runs when MeshService.start() (startService /
        // startForegroundService) was used, NOT on a bind-only path.
        // meshManager is already guaranteed to exist from onCreate() by now.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    "MESH_SERVICE_CHANNEL",
                    "Mesh Network Service Channel",
                    android.app.NotificationManager.IMPORTANCE_LOW
            );
            android.app.NotificationManager manager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
            android.app.Notification notification = new androidx.core.app.NotificationCompat.Builder(this, "MESH_SERVICE_CHANNEL")
                    .setContentTitle("Mesh Network Active")
                    .setContentText("Broadcasting and listening for peer alerts...")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .build();
            startForeground(912, notification);
        }
        return START_STICKY;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface MeshCallback {

        void onSuccess();

        void onFailure(String error);
    }

    public class LocalBinder extends Binder {

        public MeshService getService() {
            return MeshService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // meshManager is already initialized via onCreate() by the time
        // any bind-only caller (e.g. SOSService) gets this binder.
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (meshManager != null) {
            meshManager.stop();
        }
        executor.shutdown();
    }

    public void sendEmergencyOverMesh(final Emergency emergency, final MeshCallback callback) {
        if (emergency == null) {
            Log.w(TAG, "sendEmergencyOverMesh called with null emergency.");
            if (callback != null) {
                callback.onFailure("Emergency details are null.");
            }
            return;
        }
        if (meshManager == null) {
            // Should not happen now that init lives in onCreate(), but log
            // loudly if it ever does -- this was the exact silent-failure
            // bug that made earlier test runs show zero MeshService logs.
            Log.e(TAG, "sendEmergencyOverMesh called but meshManager is still null!");
            if (callback != null) {
                callback.onFailure("Mesh transport not initialized yet.");
            }
            return;
        }

        Log.i(TAG, "Initiating mesh network transmission for emergency: " + emergency.getEmergencyId());

        executor.execute(new Runnable() {
            @Override
            public void run() {
                meshManager.relayEmergency(emergency, new BleMeshManager.RelayCallback() {
                    @Override
                    public void onRelayedToAtLeastOnePeer(int peerCount) {
                        Log.i(TAG, "Mesh: Emergency packet relayed to " + peerCount + " nearby node(s).");
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    }

                    @Override
                    public void onHelperFound(String peerAddress) {
                        Log.i(TAG, "Relay callback confirms helper found: " + peerAddress);
                    }

                    @Override
                    public void onNoPeersFound() {
                        Log.w(TAG, "Mesh: No nearby peers discovered.");
                        if (callback != null) {
                            callback.onFailure("No nearby mesh peers found.");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "Mesh transmission error: " + message);
                        if (callback != null) {
                            callback.onFailure(message);
                        }
                    }
                });
            }
        });
    }
}
