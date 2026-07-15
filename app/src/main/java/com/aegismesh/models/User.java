package com.aegismesh.models;

import java.io.Serializable;

import org.json.JSONException;
import org.json.JSONObject;

public class User implements Serializable {

    private String fullName;
    private String age;
    private String allergies;
    private String chronicConditions;

    public User(String fullName, String age, String allergies, String chronicConditions) {
        this.fullName = fullName;
        this.age = age;
        this.allergies = allergies;
        this.chronicConditions = chronicConditions;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAge() {
        return age;
    }

    public String getAllergies() {
        return allergies;
    }

    public String getChronicConditions() {
        return chronicConditions;
    }

    /**
     * Converts the medical profile into the nested JSON object required by the
     * FastAPI backend. Null fields are coalesced to empty strings so the key is
     * always present in the payload — org.json.JSONObject.put() silently omits
     * a key when given a null value, which would otherwise cause incomplete
     * profiles (e.g. a user who triggers SOS before finishing onboarding) to
     * send a payload missing required backend fields.
     */
    public JSONObject toProfileJsonObject() throws JSONException {
        JSONObject profileJson = new JSONObject();
        profileJson.put("age", age != null ? age : "");
        profileJson.put("allergies", allergies != null ? allergies : "");
        profileJson.put("chronic_conditions", chronicConditions != null ? chronicConditions : "");
        return profileJson;
    }
}
