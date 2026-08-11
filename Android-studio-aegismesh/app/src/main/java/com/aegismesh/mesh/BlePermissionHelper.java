package com.aegismesh.mesh;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles the runtime permissions BleMeshManager needs before start() can
 * actually do anything (BleMeshManager.hasBlePermissions() silently no-ops if
 * these aren't granted -- this is the piece that gets them granted).
 *
 * On API 31+ (Android 12+), BLE needs BLUETOOTH_SCAN / BLUETOOTH_CONNECT /
 * BLUETOOTH_ADVERTISE. Below API 31, BLE scanning instead requires
 * ACCESS_FINE_LOCATION (a long-standing Android quirk -- BLE scan results can
 * reveal location, so it was gated behind the location permission before
 * Android split out dedicated BLE permissions).
 *
 * MUST be constructed in an Activity's onCreate() (or as a field initializer),
 * BEFORE the activity reaches STARTED -- ActivityResultLauncher registration
 * requires that, same as any other ActivityResultContract.
 */
public class BlePermissionHelper {

    public interface Callback {

        void onAllGranted();

        void onDenied(List<String> deniedPermissions);
    }

    private final ActivityResultLauncher<String[]> launcher;
    private Callback pendingCallback;

    public BlePermissionHelper(ComponentActivity activity) {
        launcher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handleResult
        );
    }

    private void handleResult(Map<String, Boolean> result) {
        if (pendingCallback == null) {
            return;
        }

        List<String> denied = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : result.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                denied.add(entry.getKey());
            }
        }

        if (denied.isEmpty()) {
            pendingCallback.onAllGranted();
        } else {
            pendingCallback.onDenied(denied);
        }
        pendingCallback = null;
    }

    public static String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            return new String[]{
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            };
        } else {
            return new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
    }

    public static boolean hasAllRequiredPermissions(Context context) {
        for (String permission : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Requests whatever's missing. Calls back immediately with onAllGranted()
     * if everything's already granted -- safe to call unconditionally before
     * starting the mesh service.
     */
    public void requestIfNeeded(Context context, Callback callback) {
        if (hasAllRequiredPermissions(context)) {
            callback.onAllGranted();
            return;
        }
        pendingCallback = callback;
        launcher.launch(requiredPermissions());
    }
}
