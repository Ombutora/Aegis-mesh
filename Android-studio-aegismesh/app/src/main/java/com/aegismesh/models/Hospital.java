package com.aegismesh.models;

import java.io.Serializable;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a medical facility capable of receiving emergency patients.
 */
public class Hospital implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String inventory;
    public String routingReason;
    public double distanceKm;
    public double latitude;
    public double longitude;

    public Hospital() {
    }

    public Hospital(String name, String inventory, double distanceKm, double latitude, double longitude) {
        this.name = name;
        this.inventory = inventory;
        this.distanceKm = distanceKm;
        this.latitude = latitude;
        this.longitude = longitude;
        this.routingReason = "Nearest facility with available inventory";
    }

    public String getName() {
        return name;
    }

    public String getDistance() {
        return String.format("%.1f km", distanceKm);
    }

    /**
     * Helper to parse the JSON response returned by our FastAPI web scraper.
     */
    public static Hospital fromBackendJson(JSONObject json) throws JSONException {
        JSONObject coords = json.getJSONObject("coordinates");
        Hospital h = new Hospital(
                json.getString("name"),
                json.getString("inventory"),
                json.optDouble("distance_km", 0.0),
                coords.getDouble("lat"),
                coords.getDouble("lon")
        );
        h.routingReason = json.optString("routing_reason", "Nearest facility with available inventory");
        return h;
    }
}
