package com.aegismesh.network;

import android.util.Log;

import com.aegismesh.models.DispatchResult;
import com.aegismesh.models.Emergency;
import com.aegismesh.models.Hospital;
import com.aegismesh.models.User;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Handles HTTP communication with the Aegis Mesh FastAPI backend.
 */
public final class ApiClient {

    private static final String TAG = "ApiClient";
    private static final int TIMEOUT_MS = 10_000;

    public static final String BASE_URL = getBaseUrl();

    public static final String ENDPOINT_EMERGENCY =
            combinePath(BASE_URL, "api/v1/emergency/dispatch");
    public static final String ENDPOINT_LOGIN =
            combinePath(BASE_URL, "api/v1/auth/login");
    public static final String ENDPOINT_REGISTER =
            combinePath(BASE_URL, "api/v1/auth/register");
    public static final String ENDPOINT_HOSPITALS =
            combinePath(BASE_URL, "hospitals");
    public static final String ENDPOINT_TRIAGE =
            combinePath(BASE_URL, "triage");

    private static AuthService authService;
    private static UserService userService;
    private static String sessionToken;

    private ApiClient() {
        // Utility class
    }

    public static synchronized AuthService getAuthService() {
        if (authService == null) {
            authService = new AuthService();
        }
        return authService;
    }

    public static synchronized UserService getUserService() {
        if (userService == null) {
            userService = new UserService();
        }
        return userService;
    }

    public static void setSessionToken(String token) {
        sessionToken = token;
    }

    public static String getSessionToken() {
        return sessionToken;
    }

    private static String getBaseUrl() {
        try {
            Class<?> clazz = Class.forName("com.aegismesh.BuildConfig");
            String url = (String) clazz.getField("BASE_URL").get(null);

            if (url != null && !url.trim().isEmpty()) {
                return url;
            }
        } catch (Exception ignored) {
            Log.w(TAG, "BuildConfig.BASE_URL not found. Using development URL.");
        }

        return "http://192.168.1.15:8000/";
    }

    private static String combinePath(String base, String path) {
        return base.endsWith("/") ? base + path : base + "/" + path;
    }

    /**
     * Sends an emergency report to the backend.
     *
     * Must be called from a background thread.
     */
    public static DispatchResult sendEmergency(Emergency emergency,
                                               User victim) throws Exception {

        if (emergency == null) {
            throw new IllegalArgumentException("Emergency cannot be null.");
        }

        if (victim == null) {
            throw new IllegalArgumentException("User cannot be null.");
        }

        HttpURLConnection connection = null;

        try {
            URL url = URI.create(ENDPOINT_EMERGENCY).toURL();

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            String payload = emergency.toBackendJsonString(victim);

            Log.d(TAG, "POST " + ENDPOINT_EMERGENCY);
            Log.d(TAG, payload);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();

            if (responseCode >= 200 && responseCode < 300) {

                StringBuilder response = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream(),
                                StandardCharsets.UTF_8))) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                JSONObject root = new JSONObject(response.toString());
                JSONObject dispatch =
                        root.getJSONObject("dispatch_data");

                String aiInstructions =
                        dispatch.getString("ai_first_aid_instructions");

                Hospital hospital =
                        Hospital.fromBackendJson(
                                dispatch.getJSONObject("recommended_facility"));

                Log.i(TAG, "AI instructions received.");
                Log.i(TAG, "Recommended hospital: "
                        + hospital.getName()
                        + " ("
                        + hospital.getDistance()
                        + ")");

                return new DispatchResult(aiInstructions, hospital);
            }

            StringBuilder error = new StringBuilder();

            if (connection.getErrorStream() != null) {

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                connection.getErrorStream(),
                                StandardCharsets.UTF_8))) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        error.append(line);
                    }
                }
            }

            throw new Exception(
                    "HTTP "
                            + responseCode
                            + ": "
                            + error);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
