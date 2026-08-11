package com.aegismesh.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.aegismesh.activities.ProfileActivity;
import com.aegismesh.database.EmergencyDbHelper;
import com.aegismesh.models.Emergency;
import com.aegismesh.models.User;
import com.aegismesh.network.ApiClient;

import java.util.List;

/**
 * WorkManager Worker responsible for eventual background retransmission of any
 * pending emergency alerts. Runs periodically when network connectivity is
 * available.
 */
public class EmergencyResendWorker extends Worker {

    private static final String TAG = "EmergencyResendWorker";

    public EmergencyResendWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "WorkManager backup recovery started: Checking for unsent emergency alerts...");
        Context context = getApplicationContext();
        EmergencyDbHelper dbHelper = EmergencyDbHelper.getInstance(context);

        List<Emergency> unsentList = dbHelper.getUnsentEmergencies();
        if (unsentList.isEmpty()) {
            Log.d(TAG, "No unsent emergencies found in local storage.");
            return Result.success();
        }

        Log.i(TAG, "Found " + unsentList.size() + " unsent emergency alert(s) in database. Attempting retransmission...");
        boolean allSentSuccessfully = true;

        for (Emergency emergency : unsentList) {
            try {
                Log.d(TAG, "Retransmitting emergency ID: " + emergency.getEmergencyId());

                // Fetch the saved profile from the device and send it with the emergency for AI Triage
                User victim = ProfileActivity.getSavedUser(context);
                if (victim == null) {
                    Log.e(TAG, "Cannot retransmit emergency " + emergency.getEmergencyId() + " because no saved User profile was found.");
                    allSentSuccessfully = false;
                    continue;
                }

                ApiClient.sendEmergency(emergency, victim);

                // Successfully reached the backend -- it's now OFFERED to
                // responder-matching, not yet ACCEPTED by anyone.
                dbHelper.updateStatus(emergency.getEmergencyId(), Emergency.STATUS_OFFERED);
                Log.i(TAG, "Emergency alert " + emergency.getEmergencyId() + " successfully delivered via background worker.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to retransmit emergency alert " + emergency.getEmergencyId() + ": " + e.getMessage());
                // No separate FAILED status in the current state machine --
                // leave it explicitly at PENDING so the next scheduled run
                // picks it back up via getUnsentEmergencies().
                dbHelper.updateStatus(emergency.getEmergencyId(), Emergency.STATUS_PENDING);
                allSentSuccessfully = false;
            }
        }

        if (allSentSuccessfully) {
            Log.i(TAG, "All local emergency alerts have been successfully synchronized.");
            return Result.success();
        } else {
            Log.w(TAG, "Some emergency alerts failed to send. Will retry in the next scheduled execution.");
            return Result.success();
        }
    }
}
