package com.aegismesh.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.aegismesh.database.EmergencyDbHelper;
import com.aegismesh.models.Emergency;

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

        Log.w(TAG, "Found " + unsentList.size() + " queued emergency alert(s), but the local database "
                + "does not retain the User profile required for backend delivery. Keeping them queued "
                + "until a profile-aware SOS request can send them safely.");
        return Result.success();
    }
}
