package org.yawlfoundation.yawl.integration.a2a;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * A2A HTTP Client
 *
 * Direct HTTP client for A2A (Agent-to-Agent) protocol communication.
 * Implements the A2A specification without external SDK dependencies.
 *
 * Features:
 * - Fetch AgentCard from /.well-known/agent.json
 * - Send messages via JSON-RPC 2.0
 * - Task subscription with polling
 * - Proper error handling with actionable messages
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see <a href="https://a2a-protocol.org">A2A Protocol Specification</a>
 */
public class A2AHttpClient {

    private static final String AGENT_CARD_PATH = "/.well-known/agent.json";
    private static final String A2A_ENDPOINT = "/a2a";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final int DEFAULT_CONNECT_TIMEOUT = 30000;
    private static final int DEFAULT_READ_TIMEOUT = 60000;
    private static final long DEFAULT_POLL_INTERVAL = 1000;

    private final String baseUrl;
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private int readTimeout = DEFAULT_READ_TIMEOUT;
    private A2ATypes.AgentCard cachedAgentCard;
    private String lastETag;

    /**
     * Create A2A HTTP client for an agent
     *
     * @param baseUrl the base URL of the agent (e.g., "http://localhost:8082")
     * @throws IllegalArgumentException if baseUrl is null or empty
     */
    public A2AHttpClient(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent base URL is required");
        }
        // Remove trailing slash for consistent URL building
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Fetch the agent's capability card
     *
     * Retrieves the AgentCard from the well-known endpoint.
     * Results are cached for subsequent calls.
     *
     * @return the agent's capability card
     * @throws A2AException if the card cannot be retrieved
     */
    public A2ATypes.AgentCard fetchAgentCard() throws A2AException {
        String url = baseUrl + AGENT_CARD_PATH;

        try {
            HttpURLConnection conn = createConnection(url, "GET");
            conn.setRequestProperty("Accept", CONTENT_TYPE_JSON);

            if (lastETag != null) {
                conn.setRequestProperty("If-None-Match", lastETag);
            }

            int responseCode = conn.getResponseCode();

            if (responseCode == 304) {
                // Not modified, return cached version
                if (cachedAgentCard != null) {
                    return cachedAgentCard;
                }
            }

            if (responseCode == 404) {
                throw A2AException.agentCardNotFound(baseUrl);
            }

            if (responseCode >= 400) {
                String errorBody = readErrorStream(conn);
                throw new A2AException(
                    A2AException.ErrorCode.SERVER_ERROR,
                    "Failed to fetch agent card (HTTP " + responseCode + "): " + errorBody,
                    "Verify the agent is running and the URL is correct.\n" +
                    "Check that the agent exposes /.well-known/agent.json endpoint."
                );
            }

            String response = readResponse(conn);
            String etag = conn.getHeaderField("ETag");
            if (etag != null) {
                lastETag = etag;
            }

            cachedAgentCard = parseAgentCard(response);
            return cachedAgentCard;

        } catch (A2AException e) {
            throw e;
        } catch (IOException e) {
            throw A2AException.connectionFailed(baseUrl, e);
        }
    }

    /**
     * Send a message to the agent and create a new task
     *
     * @param message the message to send
     * @return the created task
     * @throws A2AException if the message cannot be sent
     */
    public A2ATypes.Task sendMessage(A2ATypes.Message message) throws A2AException {
        return sendMessage(message, null);
    }

    /**
     * Send a message with a specific skill
     *
     * @param message the message to send
     * @param skillId optional skill ID to invoke
     * @return the created task
     * @throws A2AException if the message cannot be sent
     */
    public A2ATypes.Task sendMessage(A2ATypes.Message message, String skillId) throws A2AException {
        String url = baseUrl + A2A_ENDPOINT;

        Map<String, Object> params = new HashMap<>();
        params.put("message", message.toJson());
        if (skillId != null && !skillId.isEmpty()) {
            params.put("skillId", skillId);
        }

        A2ATypes.A2ARequest request = new A2ATypes.A2ARequest("tasks/send", params, null);

        try {
            HttpURLConnection conn = createConnection(url, "POST");
            conn.setRequestProperty("Content-Type", CONTENT_TYPE_JSON);
            conn.setDoOutput(true);

            writeRequest(conn, request.toJson());

            int responseCode = conn.getResponseCode();

            if (responseCode >= 400) {
                String errorBody = readErrorStream(conn);
                A2ATypes.A2AError error = parseError(errorBody);
                throw new A2AException(
                    A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                    "Failed to send message: " + error.getMessage(),
                    "Check message format and skill parameters."
                );
            }

            String response = readResponse(conn);
            return parseTaskResponse(response);

        } catch (A2AException e) {
            throw e;
        } catch (IOException e) {
            throw A2AException.connectionFailed(baseUrl, e);
        }
    }

    /**
     * Get the current status of a task
     *
     * @param taskId the task ID
     * @return the current task state
     * @throws A2AException if the task cannot be retrieved
     */
    public A2ATypes.Task getTask(String taskId) throws A2AException {
        String url = baseUrl + A2A_ENDPOINT;

        Map<String, Object> params = new HashMap<>();
        params.put("taskId", taskId);

        A2ATypes.A2ARequest request = new A2ATypes.A2ARequest("tasks/get", params, null);

        try {
            HttpURLConnection conn = createConnection(url, "POST");
            conn.setRequestProperty("Content-Type", CONTENT_TYPE_JSON);
            conn.setDoOutput(true);

            writeRequest(conn, request.toJson());

            int responseCode = conn.getResponseCode();

            if (responseCode == 404) {
                throw new A2AException(
                    A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                    "Task not found: " + taskId,
                    "The task may have expired or never existed."
                );
            }

            if (responseCode >= 400) {
                String errorBody = readErrorStream(conn);
                throw new A2AException(
                    A2AException.ErrorCode.SERVER_ERROR,
                    "Failed to get task (HTTP " + responseCode + "): " + errorBody,
                    null
                );
            }

            String response = readResponse(conn);
            return parseTaskResponse(response);

        } catch (A2AException e) {
            throw e;
        } catch (IOException e) {
            throw A2AException.connectionFailed(baseUrl, e);
        }
    }

    /**
     * Cancel a running task
     *
     * @param taskId the task ID to cancel
     * @return the canceled task
     * @throws A2AException if the task cannot be canceled
     */
    public A2ATypes.Task cancelTask(String taskId) throws A2AException {
        String url = baseUrl + A2A_ENDPOINT;

        Map<String, Object> params = new HashMap<>();
        params.put("taskId", taskId);

        A2ATypes.A2ARequest request = new A2ATypes.A2ARequest("tasks/cancel", params, null);

        try {
            HttpURLConnection conn = createConnection(url, "POST");
            conn.setRequestProperty("Content-Type", CONTENT_TYPE_JSON);
            conn.setDoOutput(true);

            writeRequest(conn, request.toJson());

            int responseCode = conn.getResponseCode();

            if (responseCode >= 400) {
                String errorBody = readErrorStream(conn);
                throw new A2AException(
                    A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                    "Failed to cancel task: " + errorBody,
                    null
                );
            }

            String response = readResponse(conn);
            return parseTaskResponse(response);

        } catch (A2AException e) {
            throw e;
        } catch (IOException e) {
            throw A2AException.connectionFailed(baseUrl, e);
        }
    }

    /**
     * Subscribe to task updates via polling
     *
     * Polls the task status until completion or timeout.
     *
     * @param taskId the task ID to monitor
     * @param handler callback for task updates
     * @param timeoutMs maximum time to wait
     * @throws A2AException if polling fails or times out
     */
    public void subscribeToTask(String taskId, Consumer<A2ATypes.TaskUpdate> handler, long timeoutMs)
            throws A2AException {
        long startTime = System.currentTimeMillis();
        A2ATypes.TaskStatus lastStatus = null;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            A2ATypes.Task task = getTask(taskId);

            if (task.getStatus() != lastStatus) {
                A2ATypes.TaskUpdate update = new A2ATypes.TaskUpdate(
                    taskId,
                    task.getStatus(),
                    task.getHistory().isEmpty() ? null : task.getHistory().get(task.getHistory().size() - 1)
                );
                handler.accept(update);
                lastStatus = task.getStatus();
            }

            if (task.isCompleted()) {
                return;
            }

            try {
                Thread.sleep(DEFAULT_POLL_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new A2AException(
                    A2AException.ErrorCode.TIMEOUT,
                    "Task subscription interrupted",
                    null,
                    e
                );
            }
        }

        throw A2AException.timeout("task subscription", timeoutMs);
    }

    /**
     * Wait for task completion
     *
     * Blocks until the task completes or times out.
     *
     * @param taskId the task ID
     * @param timeoutMs maximum time to wait
     * @return the completed task
     * @throws A2AException if polling fails or times out
     */
    public A2ATypes.Task waitForCompletion(String taskId, long timeoutMs) throws A2AException {
        final A2ATypes.Task[] result = new A2ATypes.Task[1];
        final boolean[] completed = new boolean[1];

        subscribeToTask(taskId, update -> {
            if (update.getNewStatus() == A2ATypes.TaskStatus.COMPLETED ||
                update.getNewStatus() == A2ATypes.TaskStatus.FAILED ||
                update.getNewStatus() == A2ATypes.TaskStatus.CANCELED) {
                result[0] = getTask(taskId);
                completed[0] = true;
            }
        }, timeoutMs);

        if (!completed[0] || result[0] == null) {
            throw A2AException.timeout("task completion", timeoutMs);
        }

        return result[0];
    }

    /**
     * Invoke a skill on the agent
     *
     * Convenience method to invoke a skill and wait for completion.
     *
     * @param skillId the skill ID to invoke
     * @param inputData input data for the skill
     * @param timeoutMs maximum time to wait
     * @return the task result
     * @throws A2AException if invocation fails
     */
    public String invokeSkill(String skillId, String inputData, long timeoutMs) throws A2AException {
        // Verify skill exists
        A2ATypes.AgentCard card = fetchAgentCard();
        A2ATypes.AgentSkill skill = card.getSkillById(skillId);
        if (skill == null) {
            List<String> availableSkills = new ArrayList<>();
            for (A2ATypes.AgentSkill s : card.getSkills()) {
                availableSkills.add(s.getId());
            }
            throw A2AException.skillNotFound(skillId, availableSkills);
        }

        // Create and send message
        A2ATypes.Message message = A2ATypes.Message.userMessage(
            "Execute skill: " + skillId + "\nInput: " + inputData
        );

        A2ATypes.Task task = sendMessage(message, skillId);

        // Wait for completion
        task = waitForCompletion(task.getId(), timeoutMs);

        if (task.getStatus() == A2ATypes.TaskStatus.FAILED) {
            String errorMsg = task.getHistory().isEmpty() ? "Unknown error" :
                task.getHistory().get(task.getHistory().size() - 1).getTextContent();
            throw A2AException.taskExecutionFailed(task.getId(), errorMsg);
        }

        // Extract result
        if (!task.getArtifacts().isEmpty()) {
            A2ATypes.Artifact artifact = task.getArtifacts().get(0);
            return extractArtifactContent(artifact);
        }

        if (!task.getHistory().isEmpty()) {
            return task.getHistory().get(task.getHistory().size() - 1).getTextContent();
        }

        return task.toJson();
    }

    /**
     * Test connection to the agent
     *
     * @return true if connection is successful
     */
    public boolean testConnection() {
        try {
            fetchAgentCard();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the base URL
     *
     * @return the agent base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Get the cached agent card (if any)
     *
     * @return cached agent card, or null if not fetched yet
     */
    public A2ATypes.AgentCard getCachedAgentCard() {
        return cachedAgentCard;
    }

    /**
     * Clear cached agent card
     */
    public void clearCache() {
        cachedAgentCard = null;
        lastETag = null;
    }

    /**
     * Set connection timeout
     *
     * @param timeoutMs timeout in milliseconds
     */
    public void setConnectTimeout(int timeoutMs) {
        this.connectTimeout = timeoutMs;
    }

    /**
     * Set read timeout
     *
     * @param timeoutMs timeout in milliseconds
     */
    public void setReadTimeout(int timeoutMs) {
        this.readTimeout = timeoutMs;
    }

    // Private helper methods

    private HttpURLConnection createConnection(String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        return conn;
    }

    private void writeRequest(HttpURLConnection conn, String body) throws IOException {
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        try (InputStream is = conn.getInputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private String readErrorStream(HttpURLConnection conn) {
        try (InputStream es = conn.getErrorStream()) {
            if (es == null) {
                return "No error details available";
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } catch (IOException e) {
            return "Failed to read error: " + e.getMessage();
        }
    }

    private A2ATypes.AgentCard parseAgentCard(String json) throws A2AException {
        try {
            String name = extractJsonString(json, "name");
            String description = extractJsonString(json, "description");
            String url = extractJsonString(json, "url");
            String version = extractJsonString(json, "version");
            String protocolVersion = extractJsonString(json, "protocolVersion");

            // Parse capabilities
            String capabilitiesJson = extractJsonObject(json, "capabilities");
            boolean streaming = extractJsonBoolean(capabilitiesJson, "streaming");
            boolean pushNotifications = extractJsonBoolean(capabilitiesJson, "pushNotifications");
            A2ATypes.AgentCapabilities capabilities = new A2ATypes.AgentCapabilities(streaming, pushNotifications);

            // Parse skills
            List<A2ATypes.AgentSkill> skills = parseSkills(json);

            return new A2ATypes.AgentCard(
                name, description, url, version, capabilities, skills, protocolVersion, null
            );
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.INVALID_AGENT_CARD,
                "Failed to parse agent card: " + e.getMessage(),
                "The agent card format may be invalid or incompatible.",
                e
            );
        }
    }

    private List<A2ATypes.AgentSkill> parseSkills(String json) {
        List<A2ATypes.AgentSkill> skills = new ArrayList<>();

        int skillsStart = json.indexOf("\"skills\":[");
        if (skillsStart == -1) {
            return skills;
        }

        skillsStart += "\"skills\":[".length();
        int braceDepth = 1;
        int skillStart = skillsStart;

        for (int i = skillsStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (braceDepth == 1) {
                    skillStart = i;
                }
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 1) {
                    String skillJson = json.substring(skillStart, i + 1);
                    skills.add(parseSkill(skillJson));
                }
            } else if (c == ']' && braceDepth == 1) {
                break;
            }
        }

        return skills;
    }

    private A2ATypes.AgentSkill parseSkill(String json) {
        String id = extractJsonString(json, "id");
        String name = extractJsonString(json, "name");
        String description = extractJsonString(json, "description");
        List<String> tags = parseJsonStringArray(json, "tags");
        List<String> examples = parseJsonStringArray(json, "examples");

        return new A2ATypes.AgentSkill(id, name, description, tags, examples, null);
    }

    private A2ATypes.Task parseTaskResponse(String json) throws A2AException {
        try {
            // Extract result object
            String resultJson = extractJsonObject(json, "result");
            if (resultJson == null) {
                resultJson = json;
            }

            String id = extractJsonString(resultJson, "id");
            String statusStr = extractJsonString(resultJson, "status");
            A2ATypes.TaskStatus status = A2ATypes.TaskStatus.fromValue(statusStr);

            // Parse history
            List<A2ATypes.Message> history = parseMessages(resultJson, "history");

            // Parse artifacts
            List<A2ATypes.Artifact> artifacts = parseArtifacts(resultJson);

            return new A2ATypes.Task(id, status, history, artifacts);
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.INVALID_MESSAGE,
                "Failed to parse task response: " + e.getMessage(),
                null,
                e
            );
        }
    }

    private List<A2ATypes.Message> parseMessages(String json, String arrayName) {
        List<A2ATypes.Message> messages = new ArrayList<>();

        int arrayStart = json.indexOf("\"" + arrayName + "\":[");
        if (arrayStart == -1) {
            return messages;
        }

        arrayStart += ("\"" + arrayName + "\":[").length();
        int braceDepth = 1;
        int msgStart = arrayStart;

        for (int i = arrayStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (braceDepth == 1) {
                    msgStart = i;
                }
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 1) {
                    String msgJson = json.substring(msgStart, i + 1);
                    messages.add(parseMessage(msgJson));
                }
            } else if (c == ']' && braceDepth == 1) {
                break;
            }
        }

        return messages;
    }

    private A2ATypes.Message parseMessage(String json) {
        String role = extractJsonString(json, "role");
        String messageId = extractJsonString(json, "messageId");

        // Simple text extraction from parts
        String text = "";
        int textIdx = json.indexOf("\"text\":\"");
        if (textIdx != -1) {
            textIdx += "\"text\":\"".length();
            int textEnd = json.indexOf("\"", textIdx);
            if (textEnd != -1) {
                text = unescapeJson(json.substring(textIdx, textEnd));
            }
        }

        return new A2ATypes.Message(role, Collections.singletonList(new A2ATypes.TextPart(text)), messageId);
    }

    private List<A2ATypes.Artifact> parseArtifacts(String json) {
        List<A2ATypes.Artifact> artifacts = new ArrayList<>();

        int arrayStart = json.indexOf("\"artifacts\":[");
        if (arrayStart == -1) {
            return artifacts;
        }

        arrayStart += "\"artifacts\":[".length();
        int braceDepth = 1;
        int artStart = arrayStart;

        for (int i = arrayStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (braceDepth == 1) {
                    artStart = i;
                }
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 1) {
                    String artJson = json.substring(artStart, i + 1);
                    artifacts.add(parseArtifact(artJson));
                }
            } else if (c == ']' && braceDepth == 1) {
                break;
            }
        }

        return artifacts;
    }

    private A2ATypes.Artifact parseArtifact(String json) {
        String name = extractJsonString(json, "name");
        String description = extractJsonString(json, "description");

        // Extract text from parts
        String text = "";
        int textIdx = json.indexOf("\"text\":\"");
        if (textIdx != -1) {
            textIdx += "\"text\":\"".length();
            int textEnd = json.indexOf("\"", textIdx);
            if (textEnd != -1) {
                text = unescapeJson(json.substring(textIdx, textEnd));
            }
        }

        return new A2ATypes.Artifact(name, description,
            Collections.singletonList(new A2ATypes.TextPart(text)));
    }

    private String extractArtifactContent(A2ATypes.Artifact artifact) {
        StringBuilder sb = new StringBuilder();
        for (A2ATypes.Part part : artifact.getParts()) {
            if (part instanceof A2ATypes.TextPart) {
                sb.append(((A2ATypes.TextPart) part).getText());
            }
        }
        return sb.toString();
    }

    private A2ATypes.A2AError parseError(String json) {
        String message = extractJsonString(json, "message");
        int code = extractJsonInt(json, "code");
        return A2ATypes.A2AError.custom(code, message != null ? message : "Unknown error");
    }

    // JSON utility methods (no external dependencies)

    private String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey);
        if (start == -1) {
            return null;
        }
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        if (end == -1) {
            return null;
        }
        return unescapeJson(json.substring(start, end));
    }

    private boolean extractJsonBoolean(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int start = json.indexOf(searchKey);
        if (start == -1) {
            return false;
        }
        start += searchKey.length();
        String rest = json.substring(start).trim();
        return rest.startsWith("true");
    }

    private int extractJsonInt(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int start = json.indexOf(searchKey);
        if (start == -1) {
            return 0;
        }
        start += searchKey.length();
        String rest = json.substring(start).trim();
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (Character.isDigit(c) || c == '-') {
                num.append(c);
            } else {
                break;
            }
        }
        try {
            return Integer.parseInt(num.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractJsonObject(String json, String key) {
        String searchKey = "\"" + key + "\":{";
        int start = json.indexOf(searchKey);
        if (start == -1) {
            return null;
        }
        start += ("\"" + key + "\":").length();
        int braceDepth = 0;
        int objectStart = -1;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (braceDepth == 0) {
                    objectStart = i;
                }
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 0) {
                    return json.substring(objectStart, i + 1);
                }
            }
        }
        return null;
    }

    private List<String> parseJsonStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        String searchKey = "\"" + key + "\":[";
        int start = json.indexOf(searchKey);
        if (start == -1) {
            return result;
        }
        start += searchKey.length();

        boolean inString = false;
        StringBuilder current = new StringBuilder();

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
                if (!inString && current.length() > 0) {
                    result.add(unescapeJson(current.toString()));
                    current = new StringBuilder();
                }
            } else if (inString) {
                current.append(c);
            } else if (c == ']') {
                break;
            }
        }

        return result;
    }

    private String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        String agentUrl = args.length > 0 ? args[0] : "http://localhost:8082";

        System.out.println("A2A HTTP Client Test");
        System.out.println("====================");
        System.out.println("Agent URL: " + agentUrl);
        System.out.println();

        A2AHttpClient client = new A2AHttpClient(agentUrl);

        // Test connection
        System.out.println("Testing connection...");
        if (client.testConnection()) {
            System.out.println("Connection: SUCCESS");
        } else {
            System.out.println("Connection: FAILED");
            System.out.println("Make sure the A2A server is running at " + agentUrl);
            return;
        }

        // Fetch agent card
        System.out.println("\nFetching Agent Card...");
        try {
            A2ATypes.AgentCard card = client.fetchAgentCard();
            System.out.println("Agent Name: " + card.getName());
            System.out.println("Agent URL: " + card.getUrl());
            System.out.println("Protocol Version: " + card.getProtocolVersion());
            System.out.println("Capabilities: streaming=" + card.getCapabilities().isStreaming() +
                ", pushNotifications=" + card.getCapabilities().isPushNotifications());
            System.out.println("Skills:");
            for (A2ATypes.AgentSkill skill : card.getSkills()) {
                System.out.println("  - " + skill.getId() + ": " + skill.getName());
            }
        } catch (A2AException e) {
            System.err.println("Error: " + e.getFullReport());
        }
    }
}
