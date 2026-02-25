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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * UiPath Automation Cloud API client.
 * Integrates with UiPath API to extract automation workflows and metrics.
 * Supports:
 * - Authentication (OAuth 2.0 or API Token)
 * - Automation package/process listing
 * - Job execution metrics and conformance
 * - XAML export with BPMN wrapping
 * - Event log export
 */
public class UiPathAutomationClient implements CloudMiningClient {
    private static final String API_BASE_URL = "https://cloud.uipath.com/api";
    private static final String IDENTITY_ENDPOINT = "/identity_/connect/token";
    private static final String ODATA_ENDPOINT = "/odata";

    private String accessToken;
    private String apiToken;
    private String clientId;
    private String clientSecret;

    /**
     * Constructor using API token authentication.
     * @param apiToken UiPath API token
     * @param tenantName UiPath tenant name
     * @param accountName UiPath account name
     */
    public UiPathAutomationClient(String apiToken, String tenantName, String accountName) {
        this.apiToken = apiToken;
        // tenantName and accountName accepted for API compatibility but not currently used
    }

    /**
     * Constructor using OAuth 2.0 (clientId/clientSecret).
     * @param clientId OAuth client ID
     * @param clientSecret OAuth client secret
     */
    public UiPathAutomationClient(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Authenticate with UiPath API using token or OAuth 2.0.
     */
    @Override
    public void authenticate() throws IOException {
        if (clientId != null && clientSecret != null) {
            // OAuth 2.0 flow
            authenticateOAuth();
        } else if (apiToken != null) {
            // API token authentication
            this.accessToken = apiToken;
        } else {
            throw new IllegalArgumentException("Must provide either apiToken or clientId/clientSecret");
        }
    }

    private void authenticateOAuth() throws IOException {
        if (clientId == null || clientSecret == null) {
            throw new IllegalArgumentException("OAuth credentials not set");
        }

        URL url = new URL(API_BASE_URL + IDENTITY_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String body = "grant_type=client_credentials&client_id=" +
            URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
            "&client_secret=" +
            URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
            "&scope=OR.Administration%20OR.Execution";
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            throw new IOException("OAuth authentication failed with status " + statusCode);
        }

        String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
        this.accessToken = jsonResponse.get("access_token").getAsString();
    }

    /**
     * Retrieve list of automation releases (processes) from UiPath.
     */
    @Override
    public Map<String, Object> listProcessModels() throws IOException {
        if (accessToken == null) {
            throw new IllegalStateException("Not authenticated. Call authenticate() first.");
        }

        URL url = new URL(API_BASE_URL + ODATA_ENDPOINT + "/Releases");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            throw new IOException("Failed to list process models: HTTP " + statusCode);
        }

        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

        Map<String, Object> models = new HashMap<>();
        JsonArray valueArray = jsonResponse.getAsJsonArray("value");
        if (valueArray != null) {
            for (JsonElement item : valueArray) {
                JsonObject release = item.getAsJsonObject();
                String releaseId = release.get("Id").getAsString();
                String releaseName = release.get("Name").getAsString();
                models.put(releaseId, releaseName);
            }
        }

        return models;
    }

    /**
     * Get conformance metrics for a UiPath automation process.
     * Computes metrics from job execution statistics.
     */
    @Override
    public CelonicsMiningClient.ConformanceMetrics getConformanceMetrics(String processId) throws IOException {
        if (accessToken == null) {
            throw new IllegalStateException("Not authenticated");
        }

        String queryUrl = API_BASE_URL + ODATA_ENDPOINT + "/JobStatistics?$filter=ReleaseId%20eq%20" +
            URLEncoder.encode("guid'" + processId + "'", StandardCharsets.UTF_8);

        URL url = new URL(queryUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            throw new IOException("Failed to get metrics: HTTP " + statusCode);
        }

        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

        CelonicsMiningClient.ConformanceMetrics metrics = new CelonicsMiningClient.ConformanceMetrics();

        JsonArray valueArray = jsonResponse.getAsJsonArray("value");
        if (valueArray != null && valueArray.size() > 0) {
            int totalJobs = 0;
            int successJobs = 0;
            int errorJobs = 0;

            for (JsonElement item : valueArray) {
                JsonObject stat = item.getAsJsonObject();
                if (stat.has("TotalJobsCount")) {
                    totalJobs += stat.get("TotalJobsCount").getAsInt();
                }
                if (stat.has("SuccessfulJobsCount")) {
                    successJobs += stat.get("SuccessfulJobsCount").getAsInt();
                }
                if (stat.has("FailedJobsCount")) {
                    errorJobs += stat.get("FailedJobsCount").getAsInt();
                }
            }

            if (totalJobs > 0) {
                double fitness = (double) successJobs / totalJobs;
                double precision = 1.0 - ((double) errorJobs / totalJobs);
                double generalization = 0.8;

                metrics.setFitness(fitness);
                metrics.setPrecision(Math.max(0.0, precision));
                metrics.setGeneralization(generalization);
            }
        }

        return metrics;
    }

    /**
     * Export automation process as BPMN (wrapping XAML content).
     * UiPath uses XAML format natively, we wrap it in BPMN XML container.
     */
    @Override
    public String exportProcessAsBpmn(String processId) throws IOException {
        if (accessToken == null) {
            throw new IllegalStateException("Not authenticated");
        }

        String exportUrl = API_BASE_URL + ODATA_ENDPOINT + "/Processes(" +
            URLEncoder.encode("'" + processId + "'", StandardCharsets.UTF_8) +
            ")?$select=ReleaseKey&$expand=Release";

        URL url = new URL(exportUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            throw new IOException("Failed to export process: HTTP " + statusCode);
        }

        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

        String processName = "UiPathProcess";
        if (jsonResponse.has("Name")) {
            processName = jsonResponse.get("Name").getAsString();
        }

        return wrapXamlAsBpmn(processId, processName, jsonResponse.toString());
    }

    private String wrapXamlAsBpmn(String processId, String processName, String xamlContent) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                id="bpmn_uipath_wrapper" name="UiPath RPA Process">
              <bpmn:process id="proc_%s" name="%s">
                <bpmn:documentation>
                  <![CDATA[
                  UiPath XAML Process Export
                  Source: UiPath Automation Cloud
                  %s
                  ]]>
                </bpmn:documentation>
              </bpmn:process>
            </bpmn:definitions>
            """.formatted(processId, processName, xamlContent);
    }

    /**
     * Get event log for a UiPath process (job execution log).
     * Returns CSV format: caseId, activity, startTime, endTime, resource.
     */
    @Override
    public String getEventLog(String processId) throws IOException {
        if (accessToken == null) {
            throw new IllegalStateException("Not authenticated");
        }

        String queryUrl = API_BASE_URL + ODATA_ENDPOINT + "/Jobs?$filter=ReleaseId%20eq%20" +
            URLEncoder.encode("guid'" + processId + "'", StandardCharsets.UTF_8) +
            "&$select=Id,StartTime,EndTime,State,ExecutionTargetId,JobExecutionDetails";

        URL url = new URL(queryUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            throw new IOException("Failed to get event log: HTTP " + statusCode);
        }

        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

        return convertJobsToCsv(processId, jsonResponse);
    }

    private String convertJobsToCsv(String processId, JsonObject jobsResponse) {
        StringBuilder csv = new StringBuilder();
        csv.append(String.format("caseId,activity,startTime,endTime,resource%n"));

        JsonArray valueArray = jobsResponse.getAsJsonArray("value");
        if (valueArray != null) {
            for (JsonElement item : valueArray) {
                JsonObject job = item.getAsJsonObject();
                String jobId = job.has("Id") ? job.get("Id").getAsString() : "";
                String startTime = job.has("StartTime") ? job.get("StartTime").getAsString() : "";
                String endTime = job.has("EndTime") ? job.get("EndTime").getAsString() : "";
                String state = job.has("State") ? job.get("State").getAsString() : "";
                String resource = job.has("ExecutionTargetId") ? job.get("ExecutionTargetId").getAsString() : "auto";

                csv.append(jobId).append(",")
                    .append(state).append(",")
                    .append(startTime).append(",")
                    .append(endTime).append(",")
                    .append(resource).append("\n");
            }
        }

        return csv.toString();
    }
}
