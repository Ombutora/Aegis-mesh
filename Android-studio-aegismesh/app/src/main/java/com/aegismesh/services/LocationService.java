package com.aegismesh.services;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * Service that handles requesting and retrieving the current GPS/Network location coordinates of the user.
 */
public class LocationService extends Service {
    private static final String TAG = "LocationService";
    private final IBinder binder = new LocalBinder();
    private LocationManager locationManager;

    /**
     * Interface to receive location updates asynchronously.
     */
    public interface LocationCallback {
        void onLocationRetrieved(double latitude, double longitude, float accuracy, long timestamp);
        void onLocationError(String error);
    }

    public class LocalBinder extends Binder {
        public LocationService getService() {
            return LocationService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    //IBinder works by returning a LocalBinder instance which is a Binder that holds a reference to the LocationService instance.
    // this allows the activity to communicate with the service
    // a binder is a proxy object that allows an app to hold a reference to a service and  to call methods on it
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Attempts to retrieve the user's current location.
     * Runs asynchronously and calls the provided callback.
     */
    @SuppressLint("MissingPermission")
    public void getCurrentLocation(final LocationCallback callback) {
        if (callback == null) return;

        if (locationManager == null) {
            callback.onLocationError("LocationManager is not available.");
            return;
        }

        try {
            boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!isGpsEnabled && !isNetworkEnabled) {
                callback.onLocationError("GPS and Network location providers are disabled.");
                return;
            }

            // Attempt to get last known location first for immediate response
            Location lastKnownGps = null;
            Location lastKnownNetwork = null;

            if (isGpsEnabled) {
                lastKnownGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (isNetworkEnabled) {
                lastKnownNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            Location bestLocation = getBestLocation(lastKnownGps, lastKnownNetwork);
            if (bestLocation != null && (System.currentTimeMillis() - bestLocation.getTime()) < 30000) {
                // If last known location is fresh (less than 30 seconds old), return it immediately,keeps the live 
                //tracking accurate incase the user is in a low signal area
                Log.d(TAG, "Using fresh last known location.");
                callback.onLocationRetrieved(
                        bestLocation.getLatitude(),
                        bestLocation.getLongitude(),
                        bestLocation.getAccuracy(),
                        bestLocation.getTime()
                );
                return;
            }

            // Otherwise, request a single location update
            // this is neccesary because the last known location could be old and inaccurate
            final String provider = isGpsEnabled ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            Log.d(TAG, "Requesting single location update from provider: " + provider);

            //it creates a new location update from the provider and uses last known location if available
            // otherwise it will create a new location update if available
            
            locationManager.requestSingleUpdate(provider, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (location != null) {
                        callback.onLocationRetrieved(
                                location.getLatitude(),
                                location.getLongitude(),
                                location.getAccuracy(),
                                location.getTime()
                        );
                    } else {
                        callback.onLocationError("Received null location from update.");
                    }
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}

                @Override
                public void onProviderEnabled(String provider) {}

                @Override
                public void onProviderDisabled(String provider) {}
            }, Looper.getMainLooper());

        } catch (SecurityException e) {
            Log.e(TAG, "Location permissions missing: " + e.getMessage(), e);
            callback.onLocationError("Location permission denied: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Exception getting location: " + e.getMessage(), e);
            callback.onLocationError("Unexpected location error: " + e.getMessage());
        }
    }

    private Location getBestLocation(Location loc1, Location loc2) {
        if (loc1 == null) return loc2;
        if (loc2 == null) return loc1;
        // Return the one with the higher accuracy or more recent timestamp
        return loc1.getTime() > loc2.getTime() ? loc1 : loc2;
    }
}
