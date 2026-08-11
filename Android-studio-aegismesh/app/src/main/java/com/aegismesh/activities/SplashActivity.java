package com.aegismesh.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.aegismesh.session.UserSession;
import com.aegismesh.network.ApiClient;

/**
 * Entry point of the application.
 * Responsibility: Check authentication status and route to the appropriate screen.
 */
@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Aegis Mesh Routing Logic:
        // 1. Check SharedPreferences for persistent login state
        // 2. Check UserSession for runtime session state
        
        SharedPreferences prefs = getSharedPreferences("AegisAuthPrefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        String token = prefs.getString("accessToken", null);

        if (isLoggedIn) {
            if (token != null) {
                ApiClient.setSessionToken(token);
            }
            
            // Try loading user profile in background
            new Thread(() -> {
                try {
                    com.aegismesh.models.User user = ApiClient.fetchProfileFromServer();
                    UserSession.getInstance().setCurrentUser(user);
                } catch (Exception e) {
                    Log.e("SplashActivity", "Offline fallback: failed to fetch profile. Loading local cache.");
                    // Offline fallback: Use the locally cached profile
                    com.aegismesh.models.User localUser = ProfileActivity.getSavedUser(this);
                    UserSession.getInstance().setCurrentUser(localUser);
                }
            }).start();
            
            // User is logged in, send to Home
            startActivity(new Intent(this, HomeActivity.class));
        } else {
            // No session found, send to Login
            startActivity(new Intent(this, LoginActivity.class));
        }

        // Close the splash activity so the user can't navigate back to it
        finish();
    }
}

