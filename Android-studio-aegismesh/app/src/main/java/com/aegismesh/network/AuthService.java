package com.aegismesh.network;

import com.aegismesh.models.AuthResult;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AuthService {
    public void requestOtp(String phoneNumber, ApiCallback<AuthResult.OtpRequest> callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = URI.create(ApiClient.combinePath(ApiClient.BASE_URL, "api/v1/auth/login")).toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("phone_number", phoneNumber);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                    JSONObject json = new JSONObject(response.toString());
                    callback.onSuccess(new AuthResult.OtpRequest(json.getString("request_id")));
                } else {
                    callback.onError(new Exception("Server returned code: " + responseCode));
                }
            } catch (Exception e) {
                callback.onError(e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    public void verifyOtp(String requestId, String code, ApiCallback<AuthResult> callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = URI.create(ApiClient.combinePath(ApiClient.BASE_URL, "api/v1/auth/verify")).toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("request_id", requestId);
                payload.put("code", code);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                    JSONObject json = new JSONObject(response.toString());
                    callback.onSuccess(new AuthResult(json.getString("accessToken"), json.getBoolean("isNewUser")));
                } else {
                    callback.onError(new Exception("Server returned code: " + responseCode));
                }
            } catch (Exception e) {
                callback.onError(e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }
}

