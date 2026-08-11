package com.aegismesh.network;

import android.util.Log;

import com.aegismesh.models.MedicalProfile;
import com.aegismesh.models.User;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Service to handle user-related network operations.
 */
public class UserService {
    private static final String TAG = "UserService";

    public void getCurrentUser(ApiCallback<User> callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiClient.ENDPOINT_PROFILE);
                Log.d(TAG, "REQ: POST " + url);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                
                String token = ApiClient.getSessionToken();
                if (token != null) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    Log.d(TAG, "RES: 200 | Body: " + response.toString());
                    User user = User.fromJson(jsonResponse.getJSONObject("profile"));
                    callback.onSuccess(user);
                } else {
                    Log.e(TAG, "RES: " + responseCode + " | Error updating profile");
                    callback.onError(new Exception("HTTP Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching user profile", e);
                callback.onError(e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    public void updateProfile(String fullName, MedicalProfile profile, ApiCallback<User> callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiClient.ENDPOINT_PROFILE);
                Log.d(TAG, "REQ: POST " + url);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);
                
                String token = ApiClient.getSessionToken();
                if (token != null) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }

                // Create a temporary User object to use its toBackendJson helper
                User tempUser = new User();
                tempUser.setFullName(fullName);
                tempUser.setMedicalProfile(profile);
                
                JSONObject payload = tempUser.toBackendJson();
                Log.d(TAG, "Payload: " + payload.toString());

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    Log.d(TAG, "RES: 200 | Body: " + response.toString());
                    User user = User.fromJson(jsonResponse.getJSONObject("profile"));
                    callback.onSuccess(user);
                } else {
                    Log.e(TAG, "RES: " + responseCode + " | Error updating profile");
                    callback.onError(new Exception("HTTP Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating profile", e);
                callback.onError(e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }
}
