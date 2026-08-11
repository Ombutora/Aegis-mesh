package com.aegismesh.mesh;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.aegismesh.models.Emergency;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real BLE mesh transport with delivery acknowledgement.
 *
 * Flow for a single relay hop: 1. Sender writes an Emergency packet to the
 * peer's MESH_RELAY_CHARACTERISTIC_UUID 2. Peer's GATT server parses it, stores
 * it as "last acknowledged", and (if new) surfaces it via
 * Listener.onEmergencyReceived() 3. Sender, immediately after the write
 * confirms, READS the peer's MESH_ACK_CHARACTERISTIC_UUID 4. If the ack's
 * emergencyId matches what was just sent, the sender knows a live device
 * genuinely received it -- not just that bytes landed. This fires
 * Listener.onHelperFound() / RelayCallback.onHelperFound().
 */
public class BleMeshManager {

    private static final String TAG = "BleMeshManager";

    public interface Listener {

        void onPeerCountChanged(int peerCount);

        void onEmergencyReceived(Emergency emergency);

        void onMeshUnavailable(String reason);

        /**
         * Fired when THIS device (acting as the sender/originator side of a
         * relay) receives a confirmed acknowledgement from a peer it just wrote
         * an emergency to. This is the "a helper has been found" signal --
         * distinct from onEmergencyReceived(), which fires on the RECEIVING
         * device instead.
         */
        void onHelperFound(String peerAddress, String emergencyId);

        /**
         * Fired when a peer explicitly indicates in its ack that it has accepted
         * the emergency.
         */
        void onHelperAccepted(String peerAddress, String emergencyId);
    }

    public interface RelayCallback {

        /**
         * Fired as soon as the write succeeds -- confirms bytes were delivered,
         * nothing more.
         */
        void onRelayedToAtLeastOnePeer(int peerCount);

        /**
         * Fired once a peer's ack characteristic confirms it actually received
         * THIS emergency.
         */
        void onHelperFound(String peerAddress);

        void onNoPeersFound();

        void onError(String message);
    }

    private final Context context;
    private final Listener listener;

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothLeScanner scanner;
    private BluetoothGattServer gattServer;

    private final Map<String, BluetoothDevice> discoveredPeers = new ConcurrentHashMap<>();
    private final Map<String, Long> seenEmergencyIds = new ConcurrentHashMap<>();

    // The most recently received emergency's ID, served back to anyone who
    // reads our ack characteristic. Simple by design -- phase 1 only needs
    // to confirm "did my last write reach a live node," not a full
    // per-connection ack ledger.
    private volatile String lastAcknowledgedEmergencyId;
    
    private static volatile String globallyAcceptedEmergencyId = null;

    public static void setAcceptedEmergency(String emergencyId) {
        globallyAcceptedEmergencyId = emergencyId;
    }

    private volatile boolean running = false;

    public BleMeshManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------
    public synchronized void start() {
        if (running) {
            return;
        }

        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            listener.onMeshUnavailable("No Bluetooth hardware on this device.");
            return;
        }
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            listener.onMeshUnavailable("Bluetooth is not enabled.");
            return;
        }
        if (!hasBlePermissions()) {
            listener.onMeshUnavailable("Missing BLUETOOTH_SCAN/CONNECT/ADVERTISE runtime permission.");
            return;
        }

        running = true;
        startGattServer();
        startAdvertising();
        startScanning();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        if (hasBlePermissions()) {
            if (advertiser != null) {
                try {
                    advertiser.stopAdvertising(advertiseCallback);
                } catch (Exception ignored) {
                }
            }
            if (scanner != null) {
                try {
                    scanner.stopScan(scanCallback);
                } catch (Exception ignored) {
                }
            }
            if (gattServer != null) {
                try {
                    gattServer.close();
                } catch (Exception ignored) {
                }
            }
        }
        discoveredPeers.clear();
    }

    private boolean hasBlePermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ---------------------------------------------------------------------
    // Advertising
    // ---------------------------------------------------------------------
    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "BLE advertising started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG, "BLE advertising failed to start, error code: " + errorCode);
            listener.onMeshUnavailable("Advertising failed (code " + errorCode + ")");
        }
    };

    private void startAdvertising() {
        if (!hasBlePermissions()) {
            return;
        }
        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            listener.onMeshUnavailable("Device does not support BLE advertising (peripheral mode).");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(MeshConstants.MESH_SERVICE_UUID))
                .setIncludeDeviceName(false)
                .build();

        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    // ---------------------------------------------------------------------
    // GATT server -- receives relay writes AND serves ack reads
    // ---------------------------------------------------------------------
    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                discoveredPeers.put(device.getAddress(), device);
                listener.onPeerCountChanged(discoveredPeers.size());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                discoveredPeers.remove(device.getAddress());
                listener.onPeerCountChanged(discoveredPeers.size());
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                boolean responseNeeded, int offset, byte[] value) {

            if (responseNeeded && hasBlePermissions()) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            }

            if (!MeshConstants.MESH_RELAY_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                return;
            }

            try {
                Emergency incoming = Emergency.fromJsonString(new String(value, "UTF-8"));
                handleIncomingEmergency(incoming);
            } catch (JSONException | java.io.UnsupportedEncodingException e) {
                Log.e(TAG, "Failed to parse incoming mesh packet: " + e.getMessage());
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId,
                int offset, BluetoothGattCharacteristic characteristic) {

            if (!MeshConstants.MESH_ACK_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                if (hasBlePermissions()) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                }
                return;
            }

            byte[] ackPayload = buildAckPayload();
            if (hasBlePermissions()) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ackPayload);
            }
        }
    };

    private byte[] buildAckPayload() {
        try {
            JSONObject json = new JSONObject();
            json.put(MeshConstants.ACK_FIELD_EMERGENCY_ID, lastAcknowledgedEmergencyId);
            json.put(MeshConstants.ACK_FIELD_ACKNOWLEDGED, lastAcknowledgedEmergencyId != null);
            
            boolean isAccepted = (lastAcknowledgedEmergencyId != null && 
                                  lastAcknowledgedEmergencyId.equals(globallyAcceptedEmergencyId));
            json.put("accepted", isAccepted);
            
            return json.toString().getBytes("UTF-8");
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private void startGattServer() {
        if (!hasBlePermissions()) {
            return;
        }
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        if (gattServer == null) {
            listener.onMeshUnavailable("Could not open GATT server.");
            return;
        }

        BluetoothGattCharacteristic relayCharacteristic = new BluetoothGattCharacteristic(
                MeshConstants.MESH_RELAY_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        BluetoothGattCharacteristic ackCharacteristic = new BluetoothGattCharacteristic(
                MeshConstants.MESH_ACK_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        BluetoothGattService meshService = new BluetoothGattService(
                MeshConstants.MESH_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );
        meshService.addCharacteristic(relayCharacteristic);
        meshService.addCharacteristic(ackCharacteristic);
        gattServer.addService(meshService);
    }

    private void handleIncomingEmergency(Emergency emergency) {
        String id = emergency.getEmergencyId();
        if (id == null) {
            return;
        }

        // Always update the ack payload, even for a duplicate, so a sender
        // reading right after their write reliably sees confirmation.
        lastAcknowledgedEmergencyId = id;

        if (seenEmergencyIds.containsKey(id)) {
            return; // already processed for flood-forwarding purposes
        }
        seenEmergencyIds.put(id, System.currentTimeMillis());
        listener.onEmergencyReceived(emergency);

        if (emergency.relayHopCount < MeshConstants.DEFAULT_TTL) {
            emergency.relayHopCount = emergency.relayHopCount + 1;
            relayToDiscoveredPeers(emergency, null);
        }
    }

    // ---------------------------------------------------------------------
    // Scanning
    // ---------------------------------------------------------------------
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (!discoveredPeers.containsKey(device.getAddress())) {
                discoveredPeers.put(device.getAddress(), device);
                listener.onPeerCountChanged(discoveredPeers.size());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE scan failed, error code: " + errorCode);
        }
    };

    private void startScanning() {
        if (!hasBlePermissions()) {
            return;
        }
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            return;
        }

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(MeshConstants.MESH_SERVICE_UUID))
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
    }

    // ---------------------------------------------------------------------
    // Relaying OUT + reading back the ack
    // ---------------------------------------------------------------------
    public void relayEmergency(final Emergency emergency, final RelayCallback callback) {
        if (emergency.getEmergencyId() != null) {
            seenEmergencyIds.put(emergency.getEmergencyId(), System.currentTimeMillis());
        }
        relayToDiscoveredPeers(emergency, callback);
    }

    private void relayToDiscoveredPeers(final Emergency emergency, final RelayCallback callback) {
        if (!hasBlePermissions()) {
            if (callback != null) {
                callback.onError("Missing BLE runtime permissions.");
            }
            return;
        }

        Set<BluetoothDevice> targets = new HashSet<>(discoveredPeers.values());
        if (targets.isEmpty()) {
            if (callback != null) {
                callback.onNoPeersFound();
            }
            return;
        }

        final int[] successCount = {0};
        final Object lock = new Object();
        final boolean[] reportedRelay = {false};

        for (BluetoothDevice device : targets) {
            device.connectGatt(context, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gatt.close();
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        gatt.close();
                        return;
                    }
                    BluetoothGattService service = gatt.getService(MeshConstants.MESH_SERVICE_UUID);
                    if (service == null) {
                        gatt.close();
                        return;
                    }
                    BluetoothGattCharacteristic relayCharacteristic
                            = service.getCharacteristic(MeshConstants.MESH_RELAY_CHARACTERISTIC_UUID);
                    if (relayCharacteristic == null) {
                        gatt.close();
                        return;
                    }
                    try {
                        byte[] payload = emergency.toJsonString().getBytes("UTF-8");
                        relayCharacteristic.setValue(payload);
                        gatt.writeCharacteristic(relayCharacteristic);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to serialize emergency for relay: " + e.getMessage());
                        gatt.close();
                    }
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    boolean shouldReportRelayNow = false;
                    synchronized (lock) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            successCount[0]++;
                            if (!reportedRelay[0]) {
                                reportedRelay[0] = true;
                                shouldReportRelayNow = true;
                            }
                        }
                    }
                    if (shouldReportRelayNow && callback != null) {
                        callback.onRelayedToAtLeastOnePeer(successCount[0]);
                    }

                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        gatt.disconnect();
                        gatt.close();
                        return;
                    }

                    // Write succeeded -- now read the peer's ack characteristic
                    // to confirm a live device actually received THIS emergency,
                    // instead of disconnecting immediately.
                    BluetoothGattService service = gatt.getService(MeshConstants.MESH_SERVICE_UUID);
                    BluetoothGattCharacteristic ackCharacteristic
                            = service != null ? service.getCharacteristic(MeshConstants.MESH_ACK_CHARACTERISTIC_UUID) : null;
                    if (ackCharacteristic != null) {
                        gatt.readCharacteristic(ackCharacteristic);
                    } else {
                        gatt.disconnect();
                        gatt.close();
                    }
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS
                            && MeshConstants.MESH_ACK_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                        try {
                            JSONObject ack = new JSONObject(new String(characteristic.getValue(), "UTF-8"));
                            String ackedId = ack.optString(MeshConstants.ACK_FIELD_EMERGENCY_ID, null);
                            boolean acknowledged = ack.optBoolean(MeshConstants.ACK_FIELD_ACKNOWLEDGED, false);

                            if (acknowledged && emergency.getEmergencyId() != null
                                    && emergency.getEmergencyId().equals(ackedId)) {
                                String peerAddress = gatt.getDevice().getAddress();
                                boolean accepted = ack.optBoolean("accepted", false);
                                
                                if (accepted) {
                                    listener.onHelperAccepted(peerAddress, ackedId);
                                } else {
                                    listener.onHelperFound(peerAddress, ackedId);
                                }
                                
                                if (callback != null) {
                                    callback.onHelperFound(peerAddress);
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to parse ack payload: " + e.getMessage());
                        }
                    }
                    gatt.disconnect();
                    gatt.close();
                }
            });
        }
    }

    public int getPeerCount() {
        return discoveredPeers.size();
    }
}
