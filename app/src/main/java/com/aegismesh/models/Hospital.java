package com.aegismesh.models;

import java.io.Serializable;

import org.json.JSONException;
import org.json.JSONObject;

public class Hospital implements Serializable {
    private String name;
    private String inventory;
    private String distance;
    private double latitude;
    private double longitude;

    public Hospital(String name, String inventory, String distance, double latitude, double longitude) {
        this.name = name;
        this.inventory = inventory;
        this.distance = distance;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getName() { return name; }
    public String getDistance() { return distance; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    /**
     * Helper to parse the JSON response returned by our FastAPI web scraper.
     */
    public static Hospital fromBackendJson(JSONObject json) throws JSONException {
        JSONObject coords = json.getJSONObject("coordinates");
        return new Hospital(
                json.getString("name"),
                json.getString("inventory"),
                json.getString("distance"),
                coords.getDouble("lat"),
                coords.getDouble("lon")
        );
    }
}