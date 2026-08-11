package com.aegismesh.ai;

import com.aegismesh.models.DispatchResult;
import com.aegismesh.models.Emergency;

public class OfflineFirstAidEngine {

    public static DispatchResult generate(Emergency emergency) {

        String type = emergency.getEmergencyType();

        if (type == null)
            type = "GENERAL";

        type = type.toUpperCase();

        String instructions;

        switch (type) {

            case "BLEEDING":
                instructions =
                        "1. Apply firm pressure to the wound.\n\n" +
                        "2. Raise the injured limb above the heart if possible.\n\n" +
                        "3. Do not remove soaked dressings.\n\n" +
                        "4. Continue pressure until help arrives.";
                break;

            case "BURN":
                instructions =
                        "1. Cool the burn under running water for 20 minutes.\n\n" +
                        "2. Remove rings or tight clothing.\n\n" +
                        "3. Do NOT apply toothpaste, butter or oil.\n\n" +
                        "4. Cover with a clean cloth.";
                break;

            case "FRACTURE":
                instructions =
                        "1. Keep the injured limb still.\n\n" +
                        "2. Immobilize using a splint if available.\n\n" +
                        "3. Apply a cold pack.\n\n" +
                        "4. Wait for responders.";
                break;

            case "HEART ATTACK":
                instructions =
                        "1. Keep the patient seated.\n\n" +
                        "2. Loosen tight clothing.\n\n" +
                        "3. Encourage slow breathing.\n\n" +
                        "4. Prepare for CPR if unconscious.";
                break;

            case "STROKE":
                instructions =
                        "1. Keep the patient still.\n\n" +
                        "2. Do NOT give food or water.\n\n" +
                        "3. Monitor breathing.\n\n" +
                        "4. Prepare responders with symptom onset time.";
                break;

            case "SEIZURE":
                instructions =
                        "1. Protect the head.\n\n" +
                        "2. Remove nearby dangerous objects.\n\n" +
                        "3. Do NOT restrain the patient.\n\n" +
                        "4. Turn onto the side once movements stop.";
                break;

            case "CHOKING":
                instructions =
                        "1. Encourage coughing.\n\n" +
                        "2. Give 5 back blows.\n\n" +
                        "3. Give 5 abdominal thrusts.\n\n" +
                        "4. Repeat until airway clears.";
                break;

            default:
                instructions =
                        "1. Stay calm.\n\n" +
                        "2. Stay where you are if safe.\n\n" +
                        "3. Help is being contacted.\n\n" +
                        "4. Keep your phone nearby.";
        }

        return new DispatchResult(instructions, null);
    }
}