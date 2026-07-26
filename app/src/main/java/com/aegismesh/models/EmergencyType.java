package com.aegismesh.models;

public enum EmergencyType {
    GENERAL("General Emergency"),
    MEDICAL("Medical Emergency"),
    FIRE("Fire Emergency"),
    POLICE("Police Emergency");

    private final String displayName;

    EmergencyType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
