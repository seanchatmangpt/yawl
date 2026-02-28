package org.yawlfoundation.yawl.examples.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * TEMPLATE: YAWL REST API Client
 * PURPOSE: Call YAWL Engine APIs from Java applications
 * CUSTOMIZATION: Update baseUrl, authentication, and add more endpoints
 * LINK: docs/API-GUIDE.md#rest-client
 *
 * Usage:
 * YawlRestApiClient client = new YawlRestApiClient("http://localhost:8080", "user", "password");
 *
 * // Create and start a case
 * String caseId = client.launchCase("MyWorkflowSpec", "{\"data\": \"value\"}");
 *
 * // Get case status
 * CaseInfo caseInfo = client.getCaseInfo(caseId);
 *
 * // Complete a work item
 * client.completeWorkItem(caseId, "task123", "{\"result\": \"approved\"}");
 */
public class YawlRestApiClient {

    private static final Logger log = Logger.getLogger(YawlRestApiClient.class.getSimpleName());

    private final String baseUrl;
    private final String username;
    private final String password;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String sessionToken;

    /**
     * Constructor: Initialize YAWL API client
     * @param baseUrl YAWL Engine URL (e.g., http://localhost:8080)
     * @param username YAWL user account
     * @param password YAWL password
     */
    public YawlRestApiClient(String baseUrl, String username, String password) {
        this.baseUrl = baseUrl.replaceAll("/$", "");  // Remove trailing slash
        this.username = username;
        this.password = password;
        this.objectMapper = new ObjectMapper();

        // Create HTTP client with connection pooling and timeouts
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        // Authenticate on construction
        try {
            authenticate();
        } catch (Exception e) {
            log.severe("Failed to authenticate: " + e.getMessage());
            throw new RuntimeException("Authentication failed", e);
        }
    }

    /**
     * Authenticate with YAWL Engine and obtain session token
     * Session token is used for subsequent API calls
     */
    private void authenticate() throws Exception {
        String url = baseUrl + "/yawl/gateway/authenticate";
        String body = "userId=" + username + "&password=" + password;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            // Parse session token from response
            // Example: <sessionToken>TOKEN123</sessionToken>
            String responseBody = response.body();
            this.sessionToken = extractXmlValue(responseBody, "sessionToken");
            log.info("Authentication successful. Session token: " + sessionToken);
        } else {
            throw new Exception("Authentication failed: HTTP " + response.statusCode());
        }
    }

    /**
     * Launch a new workflow case
     * @param specificationId workflow specification identifier
     * @param initialData JSON string with initial workflow data
     * @return Case ID of the newly created case
     */
    public String launchCase(String specificationId, String initialData) throws Exception {
        String url = baseUrl + "/yawl/gateway/launchCase";

        Map<String, String> params = new HashMap<>();
        params.put("sessionToken", sessionToken);
        params.put("specificationId", specificationId);
        params.put("initialData", initialData);

        String body = encodeFormData(params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String caseId = extractXmlValue(response.body(), "caseIdentifier");
            log.info("Case launched: " + caseId);
            return caseId;
        } else {
            throw new Exception("Failed to launch case: HTTP " + response.statusCode() + "\n" + response.body());
        }
    }

    /**
     * Get information about a case
     * @param caseId the case identifier
     * @return CaseInfo object with case details
     */
    public CaseInfo getCaseInfo(String caseId) throws Exception {
        String url = baseUrl + "/yawl/gateway/getCaseInformation";

        Map<String, String> params = new HashMap<>();
        params.put("sessionToken", sessionToken);
        params.put("caseId", caseId);

        String body = encodeFormData(params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return parseCaseInfo(response.body());
        } else {
            throw new Exception("Failed to get case info: HTTP " + response.statusCode());
        }
    }

    /**
     * Get all enabled work items (tasks ready for execution)
     * @param caseId the case identifier
     * @return list of WorkItem objects
     */
    public java.util.List<WorkItem> getEnabledWorkItems(String caseId) throws Exception {
        String url = baseUrl + "/yawl/gateway/getEnabledWorkItems";

        Map<String, String> params = new HashMap<>();
        params.put("sessionToken", sessionToken);
        params.put("caseId", caseId);

        String body = encodeFormData(params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return parseWorkItems(response.body());
        } else {
            throw new Exception("Failed to get work items: HTTP " + response.statusCode());
        }
    }

    /**
     * Complete a work item (task) with output data
     * @param caseId the case identifier
     * @param taskId the task identifier
     * @param outputData JSON string with output data
     */
    public void completeWorkItem(String caseId, String taskId, String outputData) throws Exception {
        String url = baseUrl + "/yawl/gateway/completeWorkItem";

        Map<String, String> params = new HashMap<>();
        params.put("sessionToken", sessionToken);
        params.put("caseId", caseId);
        params.put("taskId", taskId);
        params.put("outputData", outputData);

        String body = encodeFormData(params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            log.info("Work item completed: " + caseId + "/" + taskId);
        } else {
            throw new Exception("Failed to complete work item: HTTP " + response.statusCode());
        }
    }

    /**
     * Skip a work item (mark as complete without executing)
     * Useful for conditional logic or alternative paths
     */
    public void skipWorkItem(String caseId, String taskId) throws Exception {
        String url = baseUrl + "/yawl/gateway/skipWorkItem";

        Map<String, String> params = new HashMap<>();
        params.put("sessionToken", sessionToken);
        params.put("caseId", caseId);
        params.put("taskId", taskId);

        String body = encodeFormData(params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            log.info("Work item skipped: " + caseId + "/" + taskId);
        } else {
            throw new Exception("Failed to skip work item: HTTP " + response.statusCode());
        }
    }

    /**
     * Cancel (abort) a case
     */
    public void cancelCase(String caseId) throws Exception {
        String url = baseUrl + "/yawl/gateway/cancelCase";

        Map<String, String> params = new HashMap<>();
        params.put("sessionToken", sessionToken);
        params.put("caseId", caseId);

        String body = encodeFormData(params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            log.info("Case cancelled: " + caseId);
        } else {
            throw new Exception("Failed to cancel case: HTTP " + response.statusCode());
        }
    }

    /**
     * Logout and invalidate session token
     */
    public void logout() throws Exception {
        String url = baseUrl + "/yawl/gateway/logout";

        Map<String, String> params = new HashMap<>();
        params.put("sessionToken", sessionToken);

        String body = encodeFormData(params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Logged out from YAWL");
    }

    /**
     * Helper methods
     */

    private String encodeFormData(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(java.net.URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
              .append("=")
              .append(java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private String extractXmlValue(String xml, String tagName) {
        String pattern = "<" + tagName + ">(.*?)</" + tagName + ">";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private CaseInfo parseCaseInfo(String xml) {
        // Parse XML response into CaseInfo object
        String caseId = extractXmlValue(xml, "caseId");
        String status = extractXmlValue(xml, "status");
        String netId = extractXmlValue(xml, "netId");

        return new CaseInfo(caseId, status, netId);
    }

    private java.util.List<WorkItem> parseWorkItems(String xml) {
        // Parse XML response into list of WorkItem objects
        java.util.List<WorkItem> items = new java.util.ArrayList<>();

        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "<workItem>(.*?)</workItem>", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(xml);

        while (m.find()) {
            String workItemXml = m.group(1);
            String taskId = extractXmlValue(workItemXml, "taskId");
            String caseId = extractXmlValue(workItemXml, "caseId");
            String taskName = extractXmlValue(workItemXml, "taskName");

            items.add(new WorkItem(taskId, caseId, taskName));
        }

        return items;
    }

    /**
     * Data classes
     */

    public static class CaseInfo {
        public String caseId;
        public String status;  // active, completed, suspended, etc.
        public String netId;

        public CaseInfo(String caseId, String status, String netId) {
            this.caseId = caseId;
            this.status = status;
            this.netId = netId;
        }

        @Override
        public String toString() {
            return "CaseInfo{" +
                "caseId='" + caseId + '\'' +
                ", status='" + status + '\'' +
                ", netId='" + netId + '\'' +
                '}';
        }
    }

    public static class WorkItem {
        public String taskId;
        public String caseId;
        public String taskName;

        public WorkItem(String taskId, String caseId, String taskName) {
            this.taskId = taskId;
            this.caseId = caseId;
            this.taskName = taskName;
        }

        @Override
        public String toString() {
            return "WorkItem{" +
                "taskId='" + taskId + '\'' +
                ", caseId='" + caseId + '\'' +
                ", taskName='" + taskName + '\'' +
                '}';
        }
    }
}
