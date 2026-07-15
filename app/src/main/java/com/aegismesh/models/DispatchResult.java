package com.aegismesh.models;

import java.io.Serializable;

/**
 * Represents the backend's response to a dispatched emergency: AI-generated
 * first-aid guidance for the victim, and the hospital the emergency has been
 * routed to.
 */
public class DispatchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String aiFirstAidInstructions;
    private Hospital recommendedHospital;

    public DispatchResult() {
    }

    public DispatchResult(String aiFirstAidInstructions, Hospital recommendedHospital) {
        this.aiFirstAidInstructions = aiFirstAidInstructions;
        this.recommendedHospital = recommendedHospital;
    }

    public String getAiFirstAidInstructions() {
        return aiFirstAidInstructions;
    }

    public void setAiFirstAidInstructions(String aiFirstAidInstructions) {
        this.aiFirstAidInstructions = aiFirstAidInstructions;
    }

    public Hospital getRecommendedHospital() {
        return recommendedHospital;
    }

    public void setRecommendedHospital(Hospital recommendedHospital) {
        this.recommendedHospital = recommendedHospital;
    }
}
