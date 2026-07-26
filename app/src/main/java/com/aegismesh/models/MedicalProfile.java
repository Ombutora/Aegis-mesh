package com.aegismesh.models;

import java.io.Serializable;

public class MedicalProfile implements Serializable {
    private static final long serialVersionUID = 1L;
    private String bloodGroup;
    private String[] allergies;
    private String[] chronicIllnesses;
    private String[] currentMedications;

    public MedicalProfile() {
        allergies = new String[0];
        chronicIllnesses = new String[0];
        currentMedications = new String[0];
    }
    public MedicalProfile(String bloodGroup, String[] allergies, String[] chronicIllnesses,
                          String[] currentMedications) {
        this.bloodGroup = bloodGroup;
        this.allergies = allergies != null ? allergies : new String[0];
        this.chronicIllnesses = chronicIllnesses != null ? chronicIllnesses : new String[0];
        this.currentMedications = currentMedications != null ? currentMedications : new String[0];
    }
    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }
    public String[] getAllergies() { return allergies; }
    public String[] getChronicIllnesses() { return chronicIllnesses; }
    public String[] getCurrentMedications() { return currentMedications; }
    public String allergiesCsv() { return String.join(", ", allergies); }
    public String chronicIllnessesCsv() { return String.join(", ", chronicIllnesses); }
    public String currentMedicationsCsv() { return String.join(", ", currentMedications); }
}
