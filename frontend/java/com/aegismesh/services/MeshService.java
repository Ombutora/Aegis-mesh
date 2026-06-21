package com.aegismesh.services;

import java.security.Provider.Service;

import javax.naming.Context;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

public class MeshService extends Service {
    private static final String TAG = "AegisMeshService";

    // A unique UUID for Aegis Mesh so our app only picks up our own BLE packets
    private static final ParcelUuid AEGIS_SERVICE_UUID = ParcelUuid.fromString("0000180F-0000-1000-8000-00805f9b34fb");

    // Load our C++ library
    static {
        System.loadLibrary("aegismesh-native");
    }

    // Native JNI Method Declarations (BLE)
    private native byte[] nativeBuildSosPacket(int msgId, String name, String condition);
    private native byte[] nativeProcessIncomingPacket(byte[] inputPayload);
    
    // Native JNI Method Declarations (Wi-Fi Direct)
    private native String nativeStartWifiServer(int port);
    private native boolean nativeSendProfile(String targetIp, int port, String profileJson);

    private BluetoothLeAdvertiser bleAdvertiser;
    private BluetoothLeScanner bleScanner;

    @Override
    public void onCreate() {
        super.onCreate();
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null && bluetoothManager.getAdapter() != null) {
            BluetoothAdapter adapter = bluetoothManager.getAdapter();
            bleAdvertiser = adapter.getBluetoothLeAdvertiser();
            bleScanner = adapter.getBluetoothLeScanner();
        }
    }

    // =========================================
    // 1. BROADCASTING AN SOS (The "Flare")
    // =========================================
    public void broadcastSOS(String victimName, String condition) {
        if (bleAdvertiser == null) return;

        // 1. Get the tightly packed 31-byte array from C++
        int randomMsgId = (int) (System.currentTimeMillis() % 100000); // Simple unique ID
        byte[] payload = nativeBuildSosPacket(randomMsgId, victimName, condition);

        // 2. Put it into Android's BLE Advertising packet
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true) // Needs to be true for Wi-Fi Direct handshake later
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(AEGIS_SERVICE_UUID)
                .addServiceData(AEGIS_SERVICE_UUID, payload) // Attach C++ payload here
                .build();

        // 3. Start broadcasting!
        bleAdvertiser.startAdvertising(settings, data, advertiseCallback);
        Log.i(TAG, "Started Broadcasting SOS via BLE");
    }

    // =========================================
    // 2. SCANNING & MULTI-HOP FORWARDING
    // =========================================
    public void startListeningForSOS() {
        if (bleScanner != null) {
            bleScanner.startScan(scanCallback);
            Log.i(TAG, "Scanning for Aegis SOS packets...");
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            // 1. Extract raw bytes from the BLE packet
            byte[] incomingPayload = result.getScanRecord().getServiceData(AEGIS_SERVICE_UUID);

            if (incomingPayload != null) {
                // 2. Pass to C++ to check routing table and hop count
                byte[] forwardPayload = nativeProcessIncomingPacket(incomingPayload);

                // 3. If C++ returns a payload, it means we need to multi-hop forward it!
                if (forwardPayload != null) {
                    Log.i(TAG, "Multi-hop condition met! Rebroadcasting packet from C++");
                    forwardSOS(forwardPayload);
                }
            }
        }
    };

    private void forwardSOS(byte[] payloadToForward) {
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(AEGIS_SERVICE_UUID)
                .addServiceData(AEGIS_SERVICE_UUID, payloadToForward)
                .build();

        bleAdvertiser.startAdvertising(settings, data, advertiseCallback);
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "BLE Advertisement successfully started.");
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Used for unbound services
    }
}