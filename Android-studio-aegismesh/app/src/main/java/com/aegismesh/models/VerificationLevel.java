package com.aegismesh.models;

import java.io.Serializable;

public class VerificationLevel implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean phoneVerified;
    private boolean nationalIdVerified;
    private boolean faceMatchVerified;

    public VerificationLevel() {
        this.phoneVerified = true; // Phone verification is mandatory at signup
    }

    public VerificationLevel(boolean phoneVerified, boolean nationalIdVerified, boolean faceMatchVerified) {
        this.phoneVerified = phoneVerified;
        this.nationalIdVerified = nationalIdVerified;
        this.faceMatchVerified = faceMatchVerified;
    }

    public boolean hasPhone() {
        return phoneVerified;
    }

    public void setPhoneVerified(boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    public boolean hasNationalId() {
        return nationalIdVerified;
    }

    public void setNationalIdVerified(boolean nationalIdVerified) {
        this.nationalIdVerified = nationalIdVerified;
    }

    public boolean hasFaceMatch() {
        return faceMatchVerified;
    }

    public void setFaceMatchVerified(boolean faceMatchVerified) {
        this.faceMatchVerified = faceMatchVerified;
    }
}
