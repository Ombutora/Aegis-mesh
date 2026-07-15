package com.aegismesh.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class GestureDetector implements SensorEventListener {
    private static final String TAG = "AegisGestureJava";

    // Load our C++ library
    static {
        System.loadLibrary("aegismesh-native");
    }

    // Native JNI Method Declarations
    private native void nativeSetScreenHoldState(boolean isHeld);
    private native boolean nativeProcessAccelerometer(float x, float y, float z);

    // Android Sensor API variables
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private GestureListener listener;

    // Interface for the Activity to listen for SOS
    public interface GestureListener {
        void onSosTriggered();
    }

    public GestureDetector(Context context, GestureListener listener) {
        this.listener = listener;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (this.sensorManager != null) {
            this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    public void startListening() {
        if (accelerometer != null) {
            // SENSOR_DELAY_GAME provides high-frequency polling perfect for gesture detection
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            Log.i(TAG, "Started listening to accelerometer");
        }
    }

    public void stopListening() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            Log.i(TAG, "Stopped listening to accelerometer");
        }
    }

    // Called by the UI (e.g., EmergencyActivity) when the user touches/releases the SOS button
    public void setScreenHeld(boolean isHeld) {
        nativeSetScreenHoldState(isHeld);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            // Stream raw data to C++. If C++ returns true, the gesture requirement is met!
            boolean sosTriggered = nativeProcessAccelerometer(x, y, z);
            if (sosTriggered) {
                Log.w(TAG, "SOS TRIGGERED BY C++ ENGINE!");
                if (listener != null) {
                    listener.onSosTriggered();
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for basic accelerometer
    }
}