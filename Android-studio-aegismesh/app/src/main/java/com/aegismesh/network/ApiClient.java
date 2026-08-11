package com.aegismesh.network;

import android.util.Log;

import com.aegismesh.models.DispatchResult;
import com.aegismesh.models.Emergency;
import com.aegismesh.models.Hospital;
import com.aegismesh.models.Responder;
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

    public static final String ENDPOINT_EMERGENCY
            = combinePath(BASE_URL, "api/v1/emergency/dispatch");
    public static final String ENDPOINT_LOGIN
            = combinePath(BASE_URL, "api/v1/auth/login");
    public static final String ENDPOINT_REGISTER
            = combinePath(BASE_URL, "api/v1/auth/register");
    public static final String ENDPOINT_HOSPITALS
            = combinePath(BASE_URL, "hospitals");
    public static final String ENDPOINT_TRIAGE
            = combinePath(BASE_URL, "triage");
    public static final String ENDPOINT_PROFILE
            = combinePath(BASE_URL, "api/v1/auth/profile");

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

    /**
     * Resolves the BASE_URL dynamically via reflection from BuildConfig.
     * Fallback to default development URL if not defined.
     */
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

        return "http://10.5.17.182:8000/";
    }

    public static String combinePath(String base, String path) {
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
                JSONObject dispatch
                        = root.getJSONObject("dispatch_data");

                String aiInstructions
                        = dispatch.getString("ai_first_aid_instructions");

                Hospital hospital
                        = Hospital.fromBackendJson(
                                dispatch.getJSONObject("recommended_facility"));

                Responder responder = null;
                if (dispatch.has("responder") && !dispatch.isNull("responder")) {
                    JSONObject respJson = dispatch.getJSONObject("responder");
                    responder = new Responder(
                            respJson.getString("display_name"),
                            respJson.getDouble("trust_score"),
                            respJson.getInt("completed_assists"),
                            respJson.getInt("eta_minutes"),
                            respJson.getBoolean("is_verified")
                    );
                }

                Log.i(TAG, "AI instructions received.");
                Log.i(TAG, "Recommended hospital: "
                        + hospital.getName()
                        + " ("
                        + hospital.getDistance()
                        + ")");

                return new DispatchResult(aiInstructions, hospital, responder);
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

    public static User fetchProfileFromServer() throws Exception {
        java.net.URL url = java.net.URI.create(ENDPOINT_PROFILE).toURL();
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setConnectTimeout(TIMEOUT_MS);
        urlConnection.setReadTimeout(TIMEOUT_MS);

        String token = getSessionToken();
        if (token != null) {
            urlConnection.setRequestProperty("Authorization", "Bearer " + token);
        }

        int responseCode = urlConnection.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(urlConnection.getInputStream(), "utf-8"));
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }

            // Parse the JSON from the Python server
            org.json.JSONObject jsonResponse = new org.json.JSONObject(response.toString());
            return User.fromJson(jsonResponse.getJSONObject("profile"));
        } else {
            throw new Exception("HTTP error code: " + responseCode);
        }
    }

    private static void performPost(String endpoint, String payload) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(endpoint).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            
            if (payload != null) {
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                return;
            }

            StringBuilder error = new StringBuilder();
            if (connection.getErrorStream() != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line);
                    }
                }
            }
            throw new Exception("HTTP " + responseCode + ": " + error.toString());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static void acceptEmergency(String emergencyId, int responderId) throws Exception {
        String url = combinePath(BASE_URL, "api/v1/emergency/" + emergencyId + "/accept");
        JSONObject json = new JSONObject();
        json.put("responder_id", responderId);
        performPost(url, json.toString());
    }

    public static void denyEmergency(String emergencyId, int responderId, String reason) throws Exception {
        String url = combinePath(BASE_URL, "api/v1/emergency/" + emergencyId + "/deny");
        JSONObject json = new JSONObject();
        json.put("responder_id", responderId);
        json.put("reason", reason);
        performPost(url, json.toString());
    }

    public static void markArrived(String emergencyId) throws Exception {
        String url = combinePath(BASE_URL, "api/v1/emergency/" + emergencyId + "/arrived");
        performPost(url, null);
    }

    public static void markCompleted(String emergencyId) throws Exception {
        String url = combinePath(BASE_URL, "api/v1/emergency/" + emergencyId + "/complete");
        performPost(url, null);
    }

    public static void sendLocation(String emergencyId, int responderId, double latitude, double longitude) throws Exception {
        String url = combinePath(BASE_URL, "api/v1/emergency/" + emergencyId + "/location");
        JSONObject json = new JSONObject();
        json.put("responder_id", responderId);
        json.put("latitude", latitude);
        json.put("longitude", longitude);
        performPost(url, json.toString());
    }

    public static double[] getLocation(String emergencyId) throws Exception {
        String endpoint = combinePath(BASE_URL, "api/v1/emergency/" + emergencyId + "/location");
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(endpoint).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                JSONObject jsonResponse = new JSONObject(response.toString());
                double lat = jsonResponse.getDouble("latitude");
                double lng = jsonResponse.getDouble("longitude");
                return new double[]{lat, lng};
            } else {
                throw new Exception("HTTP error code: " + responseCode);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

}
