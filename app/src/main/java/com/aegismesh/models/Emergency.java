package com.aegismesh.models;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.Serializable;

/**
 * Represents an emergency event in the Aegis Mesh application.
 */
public class Emergency implements Serializable {

    private static final long serialVersionUID = 1L;

    // Status Constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";

    private String emergencyId;
    private String userId;
    private String triggerType;
    private String emergencyType;
    private double latitude;
    private double longitude;
    private long timestamp;
    private String status;

    public Emergency() {
    }

    public Emergency(String emergencyId, String userId, String triggerType, String emergencyType,
            double latitude, double longitude, long timestamp, String status) {
        this.emergencyId = emergencyId;
        this.userId = userId;
        this.triggerType = triggerType;
        this.emergencyType = emergencyType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.status = status;
    }

    // Getters and Setters
    public String getEmergencyId() {
        return emergencyId;
    }

    public void setEmergencyId(String emergencyId) {
        this.emergencyId = emergencyId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getEmergencyType() {
        return emergencyType;
    }

    public void setEmergencyType(String emergencyType) {
        this.emergencyType = emergencyType;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Converts this emergency object, together with the victim's medical
     * profile, into a JSON String matching the FastAPI backend schema: {
     * "victim_name": "...", "condition": "...", "latitude": ..., "longitude":
     * ..., "profile": { ... } }
     *
     * @param victim the User whose full name and medical profile should be
     * embedded alongside this emergency's data in the outgoing payload
     * @return a JSON string ready to be sent to the backend
     * @throws JSONException if the JSON object cannot be constructed
     */
    public String toBackendJsonString(User victim) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("victim_name", victim.getFullName());
        json.put("condition", emergencyType);
        json.put("latitude", latitude);
        json.put("longitude", longitude);
        json.put("profile", victim.toProfileJsonObject());
        return json.toString();
    }

    /**
     * Converts this emergency object into a complete local JSON representation
     * for storage.
     */
    public String toJsonString() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("emergencyId", emergencyId);
        json.put("userId", userId);
        json.put("triggerType", triggerType);
        json.put("emergencyType", emergencyType);
        json.put("latitude", latitude);
        json.put("longitude", longitude);
        json.put("timestamp", timestamp);
        json.put("status", status);
        return json.toString();
    }

    /**
     * Parses an emergency object from a JSON string.
     */
    public static Emergency fromJsonString(String jsonStr) throws JSONException {
        JSONObject json = new JSONObject(jsonStr);
        return new Emergency(
                json.getString("emergencyId"),
                json.getString("userId"),
                json.getString("triggerType"),
                json.getString("emergencyType"),
                json.getDouble("latitude"),
                json.getDouble("longitude"),
                json.getLong("timestamp"),
                json.getString("status")
        );
    }
}
