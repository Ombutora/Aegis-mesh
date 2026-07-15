package com.aegismesh.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.aegismesh.database.EmergencyDbHelper;
import com.aegismesh.models.Emergency;
import com.aegismesh.network.ApiClient;

import androidx.work.ListenableWorker.Result;

import java.util.List;

/**
 * WorkManager Worker responsible for eventual background retransmission of any failed or pending
 * emergency alerts. Runs periodically (e.g. 15-minute intervals) when network connectivity is available.
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
                ApiClient.sendEmergency(emergency);
                
                // If transmission succeeds, mark as delivered
                dbHelper.updateStatus(emergency.getEmergencyId(), Emergency.STATUS_DELIVERED);
                Log.i(TAG, "Emergency alert " + emergency.getEmergencyId() + " successfully delivered via background worker.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to retransmit emergency alert " + emergency.getEmergencyId() + ": " + e.getMessage());
                // Leave it in local storage as PENDING or FAILED for the next execution
                dbHelper.updateStatus(emergency.getEmergencyId(), Emergency.STATUS_FAILED);
                allSentSuccessfully = false;
            }
        }

        if (allSentSuccessfully) {
            Log.i(TAG, "All local emergency alerts have been successfully synchronized.");
            return Result.success();
        } else {
            Log.w(TAG, "Some emergency alerts failed to send. Will retry in the next scheduled execution.");
            // Returning success since the periodic scheduler runs it every 15 minutes.
            return Result.success();
        }
    }
}
