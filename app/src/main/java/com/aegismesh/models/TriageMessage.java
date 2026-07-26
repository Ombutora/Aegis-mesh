package com.aegismesh.models;

import java.io.Serializable;

public class TriageMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public String message;
    public long timestamp;

    public TriageMessage() {
    }

    public TriageMessage(String message, long timestamp) {
        this.message = message;
        this.timestamp = timestamp;
    }
}
