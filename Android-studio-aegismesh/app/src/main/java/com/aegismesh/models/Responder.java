package com.aegismesh.models;

import java.io.Serializable;

/**
 * Represents a nearby person or professional who has accepted the emergency
 * call and is en route to the victim.
 */
public class Responder implements Serializable {

    private static final long serialVersionUID = 1L;

    private String displayName;
    private double trustScore;
    private int completedAssists;
    private int etaMinutes;
    private boolean isVerified;
    private double latitude;
    private double longitude;

    public Responder() {
    }

    public Responder(String displayName, double trustScore, int completedAssists, int etaMinutes, boolean isVerified) {
        this.displayName = displayName;
        this.trustScore = trustScore;
        this.completedAssists = completedAssists;
        this.etaMinutes = etaMinutes;
        this.isVerified = isVerified;
    }

    /**
     * Lightweight placeholder representing "a nearby mesh peer volunteered to
     * help," used when a BLE relay ack confirms a live device received the
     * emergency but we have no backend-verified identity for them yet.
     */
    public static Responder meshVolunteer() {
        return new Responder("Nearby Helper", 0.0, 0, 0, false);
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public double getTrustScore() {
        return trustScore;
    }

    public void setTrustScore(double trustScore) {
        this.trustScore = trustScore;
    }

    public int getCompletedAssists() {
        return completedAssists;
    }

    public void setCompletedAssists(int completedAssists) {
        this.completedAssists = completedAssists;
    }

    public int getEtaMinutes() {
        return etaMinutes;
    }

    public void setEtaMinutes(int etaMinutes) {
        this.etaMinutes = etaMinutes;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
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
}
