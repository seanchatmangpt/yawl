package org.yawlfoundation.yawl.ggen.mining.cloud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Signavio Process Governance Cloud API client.
 * Integrates with Signavio API to extract process models and BPMN exports.
 * Supports:
 * - Authentication (email/password cookie-based)
 * - Process model listing
 * - BPMN 2.0 export (Signavio is BPMN-native)
 * - Note: Signavio is model-first, not process-mining, so conformance metrics are theoretical
 * - Note: Signavio does not provide event logs (use Celonis or UiPath for that)
 */
public class SignavioClient implements CloudMiningClient {
    private static final String API_BASE_URL = "https://editor.signavio.com/g";
    private static final String LOGIN_ENDPOINT = "/rest/repository/login";
    private static final String DIRECTORY_ENDPOINT = "/rest/repository/directory";
    private static final String EXPORT_ENDPOINT = "/rest/repository/model";

    private String email;
    private String password;
    private String serverUrl;
    private String authToken;
    private String workspaceId;

    /**
     * Constructor for Signavio authentication.
     * @param email Signavio account email
     * @param password Signavio account password
     * @param serverUrl Base URL (e.g., https://editor.signavio.com/g)
     */
    public SignavioClient(String email, String password, String serverUrl) {
        this.email = email;
        this.password = password;
        this.serverUrl = serverUrl;
    }

    /**
     * Authenticate with Signavio using email and password.
     * Stores auth token from response headers.
     */
    @Override
    public void authenticate() throws IOException {
        URL url = new URL(serverUrl + LOGIN_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");

        String body = "{\"username\":\"" + email + "\",\"password\":\"" + password + "\"}";
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        int statusCode = conn.getResponseCode();
        if (statusCode != 200 && statusCode != 302) {
            throw new IOException("Signavio authentication failed with status " + statusCode);
        }

        String cookieHeader = conn.getHeaderField("Set-Cookie");
        if (cookieHeader != null && cookieHeader.contains("JSESSIONID")) {
            String[] parts = cookieHeader.split(";")[0].split("=");
            if (parts.length == 2) {
                this.authToken = parts[1];
            }
        }

        if (authToken == null) {
            String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            if (jsonResponse.has("sessionId")) {
                this.authToken = jsonResponse.get("sessionId").getAsString();
            }
        }

        if (authToken == null) {
            throw new IOException("Failed to obtain Signavio auth token");
        }
    }

    /**
     * Retrieve list of process models from Signavio workspace.
     */
    @Override
    public Map<String, Object> listProcessModels() throws IOException {
        if (authToken == null) {
            throw new IllegalStateException("Not authenticated. Call authenticate() first.");
        }

        if (workspaceId == null) {
            discoverWorkspaceId();
        }

        URL url = new URL(serverUrl + DIRECTORY_ENDPOINT + "/" + workspaceId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", "JSESSIONID=" + authToken);
        conn.setRequestProperty("Content-Type", "application/json");

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            throw new IOException("Failed to list process models: HTTP " + statusCode);
        }

        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

        Map<String, Object> models = new HashMap<>();
        parseModelsFromDirectory(jsonResponse, models);

        return models;
    }

    private void discoverWorkspaceId() throws IOException {
        URL url = new URL(serverUrl + DIRECTORY_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", "JSESSIONID=" + authToken);

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            throw new IOException("Failed to discover workspace: HTTP " + statusCode);
        }

        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

        if (jsonResponse.has("directories") && jsonResponse.getAsJsonArray("directories").size() > 0) {
            JsonObject firstDir = jsonResponse.getAsJsonArray("directories").get(0).getAsJsonObject();
            if (firstDir.has("id")) {
                this.workspaceId = firstDir.get("id").getAsString();
            }
        }

        if (workspaceId == null) {
            throw new IOException("Could not discover Signavio workspace ID");
        }
    }

    private void parseModelsFromDirectory(JsonObject directory, Map<String, Object> models) {
        if (directory.has("models")) {
            JsonArray modelsArray = directory.getAsJsonArray("models");
            for (JsonElement item : modelsArray) {
                JsonObject model = item.getAsJsonObject();
                if (model.has("id") && model.has("name")) {
                    String modelId = model.get("id").getAsString();
                    String modelName = model.get("name").getAsString();
                    models.put(modelId, modelName);
                }
            }
        }

        if (directory.has("directories")) {
            JsonArray directories = directory.getAsJsonArray("directories");
            for (JsonElement item : directories) {
                JsonObject subdir = item.getAsJsonObject();
                parseModelsFromDirectory(subdir, models);
            }
        }
    }

    /**
     * Get conformance metrics for a Signavio process model.
     * Returns theoretical metrics (Signavio is model-first, not mined).
     * fitness=1.0, precision=1.0, generalization=1.0
     */
    @Override
    public CelonicsMiningClient.ConformanceMetrics getConformanceMetrics(String processId) throws IOException {
        if (authToken == null) {
            throw new IllegalStateException("Not authenticated");
        }

        CelonicsMiningClient.ConformanceMetrics metrics = new CelonicsMiningClient.ConformanceMetrics();
        metrics.setFitness(1.0);
        metrics.setPrecision(1.0);
        metrics.setGeneralization(1.0);

        return metrics;
    }

    /**
     * Export process model as BPMN 2.0 XML.
     * Signavio is BPMN-native, so export is direct standard BPMN 2.0.
     */
    @Override
    public String exportProcessAsBpmn(String processId) throws IOException {
        if (authToken == null) {
            throw new IllegalStateException("Not authenticated");
        }

        String exportUrl = serverUrl + EXPORT_ENDPOINT + "/" + processId + "/bpmn2_0_export";

        URL url = new URL(exportUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", "JSESSIONID=" + authToken);
        conn.setRequestProperty("Accept", "application/xml");

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            throw new IOException("Failed to export process as BPMN: HTTP " + statusCode);
        }

        return new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Signavio does not provide event logs (model-first platform).
     * Use Celonis or UiPath for event log access.
     * @throws UnsupportedOperationException always
     */
    @Override
    public String getEventLog(String processId) throws IOException {
        throw new UnsupportedOperationException(
            "Signavio does not provide event logs. " +
            "Signavio is a model-first platform (design-time) rather than a mining platform. " +
            "Use Celonis or UiPath to access event logs from actual process executions."
        );
    }
}
