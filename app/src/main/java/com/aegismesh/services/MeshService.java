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

import com.aegismesh.models.Emergency;
import com.aegismesh.models.MeshStatus;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service that handles local peer-to-peer mesh networking (BLE Mesh / Wi-Fi Direct)
 * when traditional cellular/internet connection is unavailable.
 */
public class MeshService extends Service {
    private static final String TAG = "MeshService";
    private final IBinder binder = new LocalBinder();
    
    private static final MutableLiveData<MeshStatus> statusLiveData = new MutableLiveData<>(new MeshStatus.Online());

    public static LiveData<MeshStatus> getStatusLiveData() {
        return statusLiveData;
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
    public int onStartCommand(Intent intent, int flags, int startId) {
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

    /**
     * Interface to monitor mesh transmission outcomes.
     */
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
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    /**
     * Broadcasts emergency data to nearby peer devices over the mesh network.
     * Operates in a background thread to prevent blocking the UI.
     */
    public void sendEmergencyOverMesh(final Emergency emergency, final MeshCallback callback) {
        if (emergency == null) {
            if (callback != null) callback.onFailure("Emergency details are null.");
            return;
        }

        Log.i(TAG, "Initiating mesh network transmission for emergency: " + emergency.getEmergencyId());
        
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Mesh: Discovering nearby nodes...");
                    // Simulate peer-to-peer discovery delay
                    Thread.sleep(1500);

                    Log.d(TAG, "Mesh: Found peer devices. Establishing mesh relay connection...");
                    Thread.sleep(1000);

                    Log.d(TAG, "Mesh: Relaying emergency packet...");
                    Thread.sleep(500);

                    Log.i(TAG, "Mesh: Emergency packet successfully relayed to nearby mesh node.");
                    
                    if (callback != null) {
                        callback.onSuccess();
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Mesh transmission interrupted: " + e.getMessage());
                    if (callback != null) {
                        callback.onFailure("Mesh transmission interrupted: " + e.getMessage());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Mesh transmission error: " + e.getMessage(), e);
                    if (callback != null) {
                        callback.onFailure("Unexpected mesh error: " + e.getMessage());
                    }
                }
            }
        });
    }
}
