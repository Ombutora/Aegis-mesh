package com.aegismesh.models;

import java.io.Serializable;

public class VerificationLevel implements Serializable {
    private static final long serialVersionUID = 1L;
    private boolean nationalIdVerified;
    private boolean faceMatchVerified;
    public VerificationLevel() { }
    public VerificationLevel(boolean nationalIdVerified, boolean faceMatchVerified) {
        this.nationalIdVerified = nationalIdVerified;
        this.faceMatchVerified = faceMatchVerified;
    }
    public boolean hasNationalId() { return nationalIdVerified; }
    public void setNationalIdVerified(boolean value) { nationalIdVerified = value; }
    public boolean hasFaceMatch() { return faceMatchVerified; }
    public void setFaceMatchVerified(boolean value) { faceMatchVerified = value; }
}
