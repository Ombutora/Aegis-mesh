package com.aegismesh.models;

import java.io.Serializable;

/**
 * Tiered identity verification status per the security proposal: phone
 * (required at signup - implied true once logged in, not tracked here),
 * national ID, and selfie/face match. Earning both national ID and face
 * match unlocks the "Verified Responder" badge.
 */
public class VerificationLevel implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean nationalIdVerified;
    private boolean faceMatchVerified;

    public VerificationLevel() {
    }

    public VerificationLevel(boolean nationalIdVerified, boolean faceMatchVerified) {
        this.nationalIdVerified = nationalIdVerified;
        this.faceMatchVerified = faceMatchVerified;
    }

    public boolean hasNationalId() { return nationalIdVerified; }
    public void setNationalIdVerified(boolean nationalIdVerified) { this.nationalIdVerified = nationalIdVerified; }

    public boolean hasFaceMatch() { return faceMatchVerified; }
    public void setFaceMatchVerified(boolean faceMatchVerified) { this.faceMatchVerified = faceMatchVerified; }
}