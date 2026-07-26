package com.aegismesh.models;

import java.io.Serializable;

public class Responder implements Serializable {
    private static final long serialVersionUID = 1L;

    public String displayName;
    public double trustScore;
    public int completedAssists;
    public int etaMinutes;
    public boolean isVerified;

    public Responder() {
    }

    public Responder(String displayName, double trustScore, int completedAssists, int etaMinutes, boolean isVerified) {
        this.displayName = displayName;
        this.trustScore = trustScore;
        this.completedAssists = completedAssists;
        this.etaMinutes = etaMinutes;
        this.isVerified = isVerified;
    }
}
