package com.aegismesh.models;

import java.io.Serializable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String fullName;
    private String age;
    private MedicalProfile medicalProfile;
    private VerificationLevel verificationLevel;

    public User() {
        this.medicalProfile = new MedicalProfile();
        this.verificationLevel = new VerificationLevel();
    }

    public User(String fullName, String age) {
        this.fullName = fullName;
        this.age = age;
        this.medicalProfile = new MedicalProfile();
        this.verificationLevel = new VerificationLevel();
    }

    public User(String fullName, String age, String allergies, String chronicConditions) {
        this.fullName = fullName;
        this.age = age;
        this.medicalProfile = new MedicalProfile();
        this.medicalProfile.setAllergies(parseCsv(allergies));
        this.medicalProfile.setChronicIllnesses(parseCsv(chronicConditions));
        this.verificationLevel = new VerificationLevel();
    }

    private String[] parseCsv(String csv) {
        if (csv == null || csv.trim().isEmpty() || csv.equalsIgnoreCase("None")) {
            return new String[0];
        }
        String[] parts = csv.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        this.age = age;
    }

    public MedicalProfile getMedicalProfile() {
        return medicalProfile;
    }

    public void setMedicalProfile(MedicalProfile medicalProfile) {
        this.medicalProfile = medicalProfile;
    }

    public VerificationLevel getVerificationLevel() {
        return verificationLevel;
    }

    public void setVerificationLevel(VerificationLevel verificationLevel) {
        this.verificationLevel = verificationLevel;
    }

    /**
     * Factory method to create a User from backend JSON.
     */
    public static User fromJson(JSONObject json) throws JSONException {
        User user = new User(
                json.optString("full_name", ""),
                json.optString("age", "")
        );

        if (json.has("medical_profile")) {
            JSONObject medJson = json.getJSONObject("medical_profile");
            MedicalProfile mp = new MedicalProfile();
            mp.setBloodGroup(medJson.optString("blood_group", ""));
            mp.setAllergies(jsonArrayToStringArray(medJson.optJSONArray("allergies")));
            mp.setChronicIllnesses(jsonArrayToStringArray(medJson.optJSONArray("chronic_illnesses")));
            mp.setCurrentMedications(jsonArrayToStringArray(medJson.optJSONArray("current_medications")));
            user.setMedicalProfile(mp);
        }

        if (json.has("verification_level")) {
            JSONObject verJson = json.getJSONObject("verification_level");
            VerificationLevel vl = new VerificationLevel(
                    verJson.optBoolean("phone_verified", true),
                    verJson.optBoolean("national_id_verified", false),
                    verJson.optBoolean("face_match_verified", false)
            );
            user.setVerificationLevel(vl);
        }

        return user;
    }

    private static String[] jsonArrayToStringArray(JSONArray array) {
        if (array == null) return new String[0];
        String[] result = new String[array.length()];
        for (int i = 0; i < array.length(); i++) {
            result[i] = array.optString(i, "");
        }
        return result;
    }

    private JSONArray stringArrayToJsonArray(String[] array) {
        JSONArray jsonArray = new JSONArray();
        if (array != null) {
            for (String s : array) {
                jsonArray.put(s);
            }
        }
        return jsonArray;
    }

    public JSONObject toBackendJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("full_name", fullName);
        json.put("age", age);

        JSONObject medJson = new JSONObject();
        medJson.put("blood_group", medicalProfile.getBloodGroup());
        medJson.put("allergies", stringArrayToJsonArray(medicalProfile.getAllergies()));
        medJson.put("chronic_illnesses", stringArrayToJsonArray(medicalProfile.getChronicIllnesses()));
        medJson.put("current_medications", stringArrayToJsonArray(medicalProfile.getCurrentMedications()));
        json.put("medical_profile", medJson);

        return json;
    }
}
