package com.aegismesh.mesh;

import java.util.UUID;

/**
 * Shared BLE mesh protocol constants. Every Aegis Mesh node advertises/scans
 * for MESH_SERVICE_UUID so peers can recognize each other without pairing.
 */
public final class MeshConstants {

    private MeshConstants() {
    }

    public static final UUID MESH_SERVICE_UUID
            = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");

    // Peers WRITE an Emergency packet to this characteristic to relay it to us.
    public static final UUID MESH_RELAY_CHARACTERISTIC_UUID
            = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");

    // Peers READ this characteristic right after a successful relay write to
    // confirm a live device actually received and parsed the emergency --
    // this is the "helper found" signal, distinct from "bytes were written."
    public static final UUID MESH_ACK_CHARACTERISTIC_UUID
            = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    public static final UUID CLIENT_CONFIG_DESCRIPTOR_UUID
            = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final int DEFAULT_TTL = 6;

    public static final long SEEN_MESSAGE_EXPIRY_MS = 5 * 60 * 1000;

    public static final long SCAN_WINDOW_MS = 10_000;
    public static final long SCAN_PAUSE_MS = 5_000;

    public static final String FIELD_MESSAGE_ID = "msgId";
    public static final String FIELD_TTL = "ttl";
    public static final String FIELD_EMERGENCY_ID = "emergencyId";
    public static final String FIELD_LAT = "lat";
    public static final String FIELD_LNG = "lng";
    public static final String FIELD_TIMESTAMP = "ts";
    public static final String FIELD_USER_ID = "userId";

    // Ack packet field names (small, separate JSON payload)
    public static final String ACK_FIELD_EMERGENCY_ID = "emergencyId";
    public static final String ACK_FIELD_ACKNOWLEDGED = "acknowledged";
}
