package com.aegismesh.models;

import java.io.Serializable;

/**
 * Represents a single message in the AI-driven first-aid triage stream.
 */
public class TriageMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String text;
    private long timestamp;

    public TriageMessage() {
    }

    public TriageMessage(String text, long timestamp) {
        this.text = text;
        this.timestamp = timestamp;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
