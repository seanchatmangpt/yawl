package org.yawlfoundation.yawl.ggen.mining.cloud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Celonis Process Mining Cloud API client.
 * Integrates with Celonis API to extract discovered process models.
 * Supports:
 * - Authentication (OAuth 2.0)
 * - Process discovery retrieval
 * - Conformance metrics (fitness, precision)
 * - Event log export
 */
public class CelonicsMiningClient implements CloudMiningClient {
    private static final String API_BASE_URL = "https://api.celonis.cloud";
    private static final String AUTH_ENDPOINT = "/api/v1/oauth/token";
    private static final String PROCESSES_ENDPOINT = "/api/v2/analyses";

    private String accessToken;
    private String clientId;
    private String clientSecret;
    private String apiKey;

    /** Construct using direct API key (simplest auth mode). */
    public CelonicsMiningClient(String apiKey, String teamId) {
        this.apiKey = apiKey;
    }

    /** Construct using OAuth 2.0 client credentials (enterprise auth mode). */
    public CelonicsMiningClient(String clientId, String clientSecret, String scope) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        // scope is recorded but not stored separately; clientId/Secret presence signals OAuth
    }

    /** Factory: create from API key. */
    public static CelonicsMiningClient forApiKey(String apiKey, String teamId) {
        return new CelonicsMiningClient(apiKey, teamId);
    }

    /** Factory: create from OAuth client credentials. */
    public static CelonicsMiningClient forOAuth(String clientId, String clientSecret) {
        return new CelonicsMiningClient(clientId, clientSecret, "api");
    }

    /**
     * Authenticate with Celonis API using API key or OAuth.
     */
    @Override
    public void authenticate() throws IOException {
        if (apiKey != null) {
            // API key authentication (simpler for direct access)
            this.accessToken = apiKey;
        } else if (clientId != null && clientSecret != null) {
            // OAuth 2.0 flow
            authenticateOAuth();
        } else {
            throw new IllegalArgumentException("Must provide either apiKey or clientId/clientSecret");
        }
    }

    private void authenticateOAuth() throws IOException {
        String auth = clientId + ":" + clientSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        URL url = new URL(API_BASE_URL + AUTH_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String body = "grant_type=client_credentials&scope=api";
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            throw new IOException("OAuth authentication failed: " + statusCode);
        }

        String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
        this.accessToken = jsonResponse.get("access_token").getAsString();
    }

    /**
     * Retrieve list of discovered process models from Celonis.
     */
    @Override
    public Map<String, Object> listProcessModels() throws IOException {
        if (accessToken == null) {
            throw new IllegalStateException("Not authenticated. Call authenticate() first.");
        }

        URL url = new URL(API_BASE_URL + PROCESSES_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            throw new IOException("Failed to list process models: " + statusCode);
        }

        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

        Map<String, Object> models = new HashMap<>();
        JsonArray itemsArray = jsonResponse.getAsJsonArray("items");
        if (itemsArray != null) {
            for (JsonElement item : itemsArray) {
                JsonObject model = item.getAsJsonObject();
                String modelId = model.get("id").getAsString();
                String modelName = model.get("name").getAsString();
                models.put(modelId, modelName);
            }
        }

        return models;
    }

    /**
     * Get conformance metrics for a discovered process.
     * Returns: fitness, precision, generalization scores.
     */
    @Override
    public ConformanceMetrics getConformanceMetrics(String processId) throws IOException {
        if (accessToken == null) {
            throw new IllegalStateException("Not authenticated");
        }

        URL url = new URL(API_BASE_URL + PROCESSES_ENDPOINT + "/" + processId + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            throw new IOException("Failed to get metrics: " + statusCode);
        }

        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

        ConformanceMetrics metrics = new ConformanceMetrics();
        if (jsonResponse.has("fitness")) {
            metrics.setFitness(jsonResponse.get("fitness").getAsDouble());
        }
        if (jsonResponse.has("precision")) {
            metrics.setPrecision(jsonResponse.get("precision").getAsDouble());
        }
        if (jsonResponse.has("generalization")) {
            metrics.setGeneralization(jsonResponse.get("generalization").getAsDouble());
        }

        return metrics;
    }

    /**
     * Export discovered process as BPMN XML.
     * Celonis can export discovered models in standard formats.
     */
    @Override
    public String exportProcessAsBpmn(String processId) throws IOException {
        if (accessToken == null) {
            throw new IllegalStateException("Not authenticated");
        }

        // Export process model in BPMN format
        String exportUrl = API_BASE_URL + PROCESSES_ENDPOINT + "/" + processId +
            "/export?format=bpmn";

        URL url = new URL(exportUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/xml");

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            throw new IOException("Failed to export process: " + statusCode);
        }

        return new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Get event log for a process (for reprocessing).
     */
    @Override
    public String getEventLog(String processId) throws IOException {
        if (accessToken == null) {
            throw new IllegalStateException("Not authenticated");
        }

        URL url = new URL(API_BASE_URL + PROCESSES_ENDPOINT + "/" + processId + "/eventlog");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/csv");

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            throw new IOException("Failed to get event log: " + statusCode);
        }

        return new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Data class for conformance metrics.
     */
    public static class ConformanceMetrics {
        private double fitness;
        private double precision;
        private double generalization;

        public double getFitness() { return fitness; }
        public void setFitness(double fitness) { this.fitness = fitness; }

        public double getPrecision() { return precision; }
        public void setPrecision(double precision) { this.precision = precision; }

        public double getGeneralization() { return generalization; }
        public void setGeneralization(double generalization) { this.generalization = generalization; }

        @Override
        public String toString() {
            return String.format("ConformanceMetrics(fitness=%.2f, precision=%.2f, generalization=%.2f)",
                fitness, precision, generalization);
        }
    }
}
