package com.aegismesh.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import com.aegismesh.network.ApiClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ResponderLocationTracker {

    private static final String TAG = "ResponderLocationTracker";
    private static ScheduledExecutorService scheduler;
    private static boolean isTracking = false;

    @SuppressLint("MissingPermission")
    public static synchronized void startTracking(Context context, String emergencyId, int responderId) {
        if (isTracking) return;
        isTracking = true;

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                double latitude = -1.1023;
                double longitude = 37.0199;

                if (locationManager != null) {
                    Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (lastKnown == null) {
                        lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    }
                    if (lastKnown != null) {
                        latitude = lastKnown.getLatitude();
                        longitude = lastKnown.getLongitude();
                    }
                }

                ApiClient.sendLocation(emergencyId, responderId, latitude, longitude);
                Log.d(TAG, "Pushed location update for emergency " + emergencyId);
            } catch (Exception e) {
                Log.w(TAG, "Failed to push location update: " + e.getMessage());
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    public static synchronized void stopTracking() {
        if (!isTracking) return;
        isTracking = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}
