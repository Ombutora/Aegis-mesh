#include <android/sensor.h>
#include <android/log.h>
#include <math.h>
#include <time.h>

#define LOG_TAG "AegisGestureCPP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Configuration Constants
const float SHAKE_THRESHOLD_GRAVITY = 2.7f; // 2.7 Gs of force
const int SHAKE_COUNT_REQUIRED = 3;
const int SHAKE_WINDOW_MS = 3000; // 3 seconds to complete 3 shakes

// State Variables
bool is_screen_held = false;
int shake_count = 0;
long last_shake_time = 0;

// Helper to get current time in milliseconds
long getCurrentTimeMs() {
    struct timespec res;
    clock_gettime(CLOCK_MONOTONIC, &res);
    return 1000.0 * res.tv_sec + (double) res.tv_nsec / 1e6;
}

// Called by JNI when user touches or releases the screen circle
void setScreenHoldState(bool isHeld) {
    is_screen_held = isHeld;
    if (!isHeld) {
        shake_count = 0; // Reset if they let go
        LOGI("Screen released. Shake count reset.");
    } else {
        LOGI("Screen held. Ready for shake detection.");
    }
}

// Core algorithm to process raw accelerometer data
bool processAccelerometerData(float x, float y, float z) {
    if (!is_screen_held) return false;

    // Calculate G-Force magnitude
    float gX = x / ASENSOR_STANDARD_GRAVITY;
    float gY = y / ASENSOR_STANDARD_GRAVITY;
    float gZ = z / ASENSOR_STANDARD_GRAVITY;
    float gForce = sqrt(gX * gX + gY * gY + gZ * gZ);

    if (gForce > SHAKE_THRESHOLD_GRAVITY) {
        long now = getCurrentTimeMs();

        // Prevent counting the same continuous shake multiple times (debounce)
        if (now - last_shake_time > 500) { 
            // Reset if they took too long between shakes
            if (now - last_shake_time > SHAKE_WINDOW_MS) {
                shake_count = 0;
            }

            shake_count++;
            last_shake_time = now;
            LOGI("Shake detected! Count: %d, G-Force: %f", shake_count, gForce);

            if (shake_count >= SHAKE_COUNT_REQUIRED) {
                shake_count = 0; // Reset for future
                LOGI("EMERGENCY TRIGGERED!");
                return true; // Return true to trigger the SOS
            }
        }
    }
    return false;
}