package org.yawlfoundation.yawl.integration.a2a;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * YAWL Agent Executor
 *
 * Bridges A2A protocol requests to YAWL engine operations.
 * Handles incoming A2A messages and executes corresponding YAWL skills.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlAgentExecutor {

    private static final String SKILL_ID_PARAM = "skillId";
    private static final String CASE_ID_PARAM = "caseId";
    private static final String TASK_ID_PARAM = "taskId";
    private static final String WORK_ITEM_ID_PARAM = "workItemId";
    private static final String SPEC_ID_PARAM = "specId";
    private static final String DATA_PARAM = "data";
    private static final String OUTPUT_DATA_PARAM = "outputData";

    private final YawlEngineAdapter engineAdapter;
    private final YawlAgentCard agentCardProducer;
    private final Map<String, A2ATypes.Task> activeTasks;
    private final AtomicLong taskCounter;

    /**
     * Create YAWL Agent Executor
     *
     * @param engineAdapter the YAWL engine adapter
     * @param serverUrl the A2A server URL for the agent card
     */
    public YawlAgentExecutor(YawlEngineAdapter engineAdapter, String serverUrl) {
        if (engineAdapter == null) {
            throw new IllegalArgumentException("YAWL engine adapter is required");
        }
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Server URL is required");
        }

        this.engineAdapter = engineAdapter;
        this.agentCardProducer = new YawlAgentCard(serverUrl, "5.2");
        this.activeTasks = new ConcurrentHashMap<>();
        this.taskCounter = new AtomicLong(0);
    }

    /**
     * Create executor from environment variables
     *
     * @param serverUrl the A2A server URL
     * @return configured executor
     */
    public static YawlAgentExecutor fromEnvironment(String serverUrl) {
        YawlEngineAdapter adapter = YawlEngineAdapter.fromEnvironment();
        return new YawlAgentExecutor(adapter, serverUrl);
    }

    /**
     * Get the agent card
     *
     * @return the agent card describing YAWL capabilities
     */
    public A2ATypes.AgentCard getAgentCard() {
        return agentCardProducer.createCard();
    }

    /**
     * Get available skill IDs
     *
     * @return list of skill IDs
     */
    public List<String> getAvailableSkills() {
        return agentCardProducer.getSkillIds();
    }

    /**
     * Execute an A2A request
     *
     * @param request the A2A request
     * @return the A2A response
     */
    public A2ATypes.A2AResponse execute(A2ATypes.A2ARequest request) {
        String method = request.getMethod();
        String id = request.getId();
        Map<String, Object> params = request.getParams();

        try {
            Object result = dispatchMethod(method, params);
            return new A2ATypes.A2AResponse(result, id);

        } catch (A2AException e) {
            A2ATypes.A2AError error = A2ATypes.A2AError.custom(
                mapErrorCode(e.getErrorCode()),
                e.getMessage()
            );
            return new A2ATypes.A2AResponse(error, id);

        } catch (Exception e) {
            A2ATypes.A2AError error = A2ATypes.A2AError.internalError();
            return new A2ATypes.A2AResponse(error, id);
        }
    }

    /**
     * Execute a skill with parameters
     *
     * @param skillId the skill ID to execute
     * @param params the skill parameters
     * @return execution result
     * @throws A2AException if execution fails
     */
    public Map<String, Object> executeSkill(String skillId, Map<String, Object> params) throws A2AException {
        if (skillId == null || skillId.isEmpty()) {
            throw new A2AException(
                A2AException.ErrorCode.SKILL_NOT_FOUND,
                "Skill ID is required",
                "Provide a valid skill ID. Available skills: " + getAvailableSkills()
            );
        }

        if (!getAvailableSkills().contains(skillId)) {
            throw A2AException.skillNotFound(skillId, getAvailableSkills());
        }

        // Ensure engine connection
        engineAdapter.ensureConnected();

        return switch (skillId) {
            case "launchCase" -> executeLaunchCase(params);
            case "getWorkItems" -> executeGetWorkItems(params);
            case "getCaseData" -> executeGetCaseData(params);
            case "completeTask" -> executeCompleteTask(params);
            case "cancelCase" -> executeCancelCase(params);
            case "getSpecifications" -> executeGetSpecifications(params);
            case "checkOutWorkItem" -> executeCheckOutWorkItem(params);
            case "checkInWorkItem" -> executeCheckInWorkItem(params);
            case "getCaseStatus" -> executeGetCaseStatus(params);
            default -> throw A2AException.skillNotFound(skillId, getAvailableSkills());
        };
    }

    /**
     * Process a text message and extract skill invocation
     *
     * @param messageText the message text
     * @return execution result
     * @throws A2AException if processing fails
     */
    public Map<String, Object> processMessage(String messageText) throws A2AException {
        // Parse skill ID and parameters from message
        ParsedMessage parsed = parseMessage(messageText);
        return executeSkill(parsed.skillId, parsed.params);
    }

    /**
     * Create a new task from a message
     *
     * @param message the A2A message
     * @param skillId optional skill ID
     * @return the created task
     */
    public A2ATypes.Task createTask(A2ATypes.Message message, String skillId) {
        String taskId = "task-" + taskCounter.incrementAndGet() + "-" + System.currentTimeMillis();

        A2ATypes.Task task = new A2ATypes.Task(
            taskId,
            A2ATypes.TaskStatus.SUBMITTED,
            Collections.singletonList(message),
            Collections.emptyList()
        );

        activeTasks.put(taskId, task);
        return task;
    }

    /**
     * Get task by ID
     *
     * @param taskId the task ID
     * @return the task, or null if not found
     */
    public A2ATypes.Task getTask(String taskId) {
        return activeTasks.get(taskId);
    }

    /**
     * Execute a task to completion
     *
     * @param taskId the task ID
     * @return the completed task
     * @throws A2AException if execution fails
     */
    public A2ATypes.Task executeTask(String taskId) throws A2AException {
        A2ATypes.Task task = activeTasks.get(taskId);
        if (task == null) {
            throw new A2AException(
                A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                "Task not found: " + taskId,
                "The task may have expired or never existed."
            );
        }

        // Get the message content
        String messageText = "";
        if (!task.getHistory().isEmpty()) {
            messageText = task.getHistory().get(0).getTextContent();
        }

        // Execute the skill
        Map<String, Object> result = processMessage(messageText);

        // Create artifacts
        List<A2ATypes.Artifact> artifacts = new ArrayList<>();
        artifacts.add(new A2ATypes.Artifact(
            "result",
            "Execution result",
            Collections.singletonList(new A2ATypes.TextPart(mapToJson(result)))
        ));

        // Update task
        A2ATypes.Task completedTask = new A2ATypes.Task(
            taskId,
            A2ATypes.TaskStatus.COMPLETED,
            task.getHistory(),
            artifacts
        );

        activeTasks.put(taskId, completedTask);
        return completedTask;
    }

    /**
     * Cancel a task
     *
     * @param taskId the task ID
     * @return the canceled task
     */
    public A2ATypes.Task cancelTask(String taskId) {
        A2ATypes.Task task = activeTasks.get(taskId);
        if (task == null) {
            return null;
        }

        A2ATypes.Task canceledTask = new A2ATypes.Task(
            taskId,
            A2ATypes.TaskStatus.CANCELED,
            task.getHistory(),
            task.getArtifacts()
        );

        activeTasks.put(taskId, canceledTask);
        return canceledTask;
    }

    /**
     * Check if connected to YAWL engine
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return engineAdapter.isConnected();
    }

    /**
     * Connect to YAWL engine
     *
     * @throws A2AException if connection fails
     */
    public void connect() throws A2AException {
        engineAdapter.connect();
    }

    /**
     * Disconnect from YAWL engine
     */
    public void disconnect() {
        engineAdapter.disconnect();
    }

    // ==================== Private Methods ====================

    private Object dispatchMethod(String method, Map<String, Object> params) throws A2AException {
        return switch (method) {
            case "tasks/send" -> handleTasksSend(params);
            case "tasks/get" -> handleTasksGet(params);
            case "tasks/cancel" -> handleTasksCancel(params);
            case "agent/card" -> getAgentCard();
            default -> throw new A2AException(
                A2AException.ErrorCode.SKILL_NOT_FOUND,
                "Unknown method: " + method,
                "Available methods: tasks/send, tasks/get, tasks/cancel, agent/card"
            );
        };
    }

    private A2ATypes.Task handleTasksSend(Map<String, Object> params) throws A2AException {
        String messageJson = (String) params.get("message");
        String skillId = (String) params.get(SKILL_ID_PARAM);

        // Parse message
        A2ATypes.Message message = parseMessageFromJson(messageJson);

        // Create task
        A2ATypes.Task task = createTask(message, skillId);

        // Execute asynchronously (in real implementation)
        // For now, execute synchronously
        task = executeTask(task.getId());

        return task;
    }

    private A2ATypes.Task handleTasksGet(Map<String, Object> params) {
        String taskId = (String) params.get("taskId");
        return activeTasks.get(taskId);
    }

    private A2ATypes.Task handleTasksCancel(Map<String, Object> params) {
        String taskId = (String) params.get("taskId");
        return cancelTask(taskId);
    }

    // ==================== Skill Execution Methods ====================

    private Map<String, Object> executeLaunchCase(Map<String, Object> params) throws A2AException {
        String specId = getStringParam(params, SPEC_ID_PARAM, true);
        String data = getStringParam(params, DATA_PARAM, false);

        String caseId = engineAdapter.launchCase(specId, data);

        Map<String, Object> result = new HashMap<>();
        result.put("caseId", caseId);
        result.put("specId", specId);
        result.put("status", "launched");
        return result;
    }

    private Map<String, Object> executeGetWorkItems(Map<String, Object> params) throws A2AException {
        String caseId = getStringParam(params, CASE_ID_PARAM, false);

        List<WorkItemRecord> items;
        if (caseId != null) {
            items = engineAdapter.getWorkItemsForCase(caseId);
        } else {
            items = engineAdapter.getWorkItems();
        }

        List<Map<String, Object>> itemsList = new ArrayList<>();
        for (WorkItemRecord item : items) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("id", item.getID());
            itemMap.put("caseId", item.getCaseID());
            itemMap.put("taskId", item.getTaskID());
            itemMap.put("status", item.getStatus());
            itemMap.put("enablementTime", item.getEnablementTimeMs());
            itemsList.add(itemMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("workItems", itemsList);
        result.put("count", itemsList.size());
        return result;
    }

    private Map<String, Object> executeGetCaseData(Map<String, Object> params) throws A2AException {
        String caseId = getStringParam(params, CASE_ID_PARAM, true);

        String data = engineAdapter.getCaseData(caseId);

        Map<String, Object> result = new HashMap<>();
        result.put("caseId", caseId);
        result.put("data", data);
        return result;
    }

    private Map<String, Object> executeCompleteTask(Map<String, Object> params) throws A2AException {
        String caseId = getStringParam(params, CASE_ID_PARAM, true);
        String taskId = getStringParam(params, TASK_ID_PARAM, true);
        String outputData = getStringParam(params, OUTPUT_DATA_PARAM, false);

        return engineAdapter.completeTask(caseId, taskId, outputData);
    }

    private Map<String, Object> executeCancelCase(Map<String, Object> params) throws A2AException {
        String caseId = getStringParam(params, CASE_ID_PARAM, true);

        engineAdapter.cancelCase(caseId);

        Map<String, Object> result = new HashMap<>();
        result.put("caseId", caseId);
        result.put("status", "canceled");
        return result;
    }

    private Map<String, Object> executeGetSpecifications(Map<String, Object> params) throws A2AException {
        List<String> specs = engineAdapter.getSpecifications();

        Map<String, Object> result = new HashMap<>();
        result.put("specifications", specs);
        result.put("count", specs.size());
        return result;
    }

    private Map<String, Object> executeCheckOutWorkItem(Map<String, Object> params) throws A2AException {
        String workItemId = getStringParam(params, WORK_ITEM_ID_PARAM, true);

        String data = engineAdapter.checkOutWorkItem(workItemId);

        Map<String, Object> result = new HashMap<>();
        result.put("workItemId", workItemId);
        result.put("data", data);
        result.put("status", "checkedOut");
        return result;
    }

    private Map<String, Object> executeCheckInWorkItem(Map<String, Object> params) throws A2AException {
        String workItemId = getStringParam(params, WORK_ITEM_ID_PARAM, true);
        String outputData = getStringParam(params, OUTPUT_DATA_PARAM, false);

        engineAdapter.checkInWorkItem(workItemId, outputData);

        Map<String, Object> result = new HashMap<>();
        result.put("workItemId", workItemId);
        result.put("status", "checkedIn");
        return result;
    }

    private Map<String, Object> executeGetCaseStatus(Map<String, Object> params) throws A2AException {
        String caseId = getStringParam(params, CASE_ID_PARAM, true);

        List<WorkItemRecord> items = engineAdapter.getWorkItemsForCase(caseId);
        String caseData = engineAdapter.getCaseData(caseId);

        List<Map<String, Object>> tasksList = new ArrayList<>();
        for (WorkItemRecord item : items) {
            Map<String, Object> taskMap = new HashMap<>();
            taskMap.put("taskId", item.getTaskID());
            taskMap.put("status", item.getStatus());
            taskMap.put("enablementTime", item.getEnablementTimeMs());
            tasksList.add(taskMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("caseId", caseId);
        result.put("status", items.isEmpty() ? "completed" : "running");
        result.put("activeTasks", tasksList);
        result.put("data", caseData);
        return result;
    }

    // ==================== Helper Methods ====================

    private String getStringParam(Map<String, Object> params, String key, boolean required) throws A2AException {
        Object value = params.get(key);
        if (value == null) {
            if (required) {
                throw new A2AException(
                    A2AException.ErrorCode.INVALID_MESSAGE,
                    "Missing required parameter: " + key,
                    "Provide the '" + key + "' parameter in the request."
                );
            }
            return null;
        }
        return value.toString();
    }

    private ParsedMessage parseMessage(String messageText) throws A2AException {
        String lower = messageText.toLowerCase();
        Map<String, Object> params = new HashMap<>();

        // Try to extract skill ID from message
        String skillId = detectSkillFromMessage(lower);

        // Extract case ID if present
        String caseId = extractIdentifier(messageText, "case-", "case ");
        if (caseId != null) {
            params.put(CASE_ID_PARAM, caseId);
        }

        // Extract work item ID if present
        String workItemId = extractIdentifier(messageText, "item-", "item ");
        if (workItemId != null) {
            params.put(WORK_ITEM_ID_PARAM, workItemId);
        }

        // Extract spec ID if present
        String specId = extractSpecId(messageText);
        if (specId != null) {
            params.put(SPEC_ID_PARAM, specId);
        }

        // Extract task ID if present
        String taskId = extractTaskId(messageText);
        if (taskId != null) {
            params.put(TASK_ID_PARAM, taskId);
        }

        // Extract JSON data if present
        String data = extractJsonData(messageText);
        if (data != null) {
            params.put(DATA_PARAM, data);
        }

        return new ParsedMessage(skillId, params);
    }

    private String detectSkillFromMessage(String lowerMessage) throws A2AException {
        if (lowerMessage.contains("launch") || lowerMessage.contains("start workflow") || lowerMessage.contains("start case")) {
            return "launchCase";
        }
        if (lowerMessage.contains("work item") || lowerMessage.contains("get work") || lowerMessage.contains("active task")) {
            return "getWorkItems";
        }
        if (lowerMessage.contains("case data") || lowerMessage.contains("get data")) {
            return "getCaseData";
        }
        if (lowerMessage.contains("complete task") || lowerMessage.contains("finish task")) {
            return "completeTask";
        }
        if (lowerMessage.contains("cancel case") || lowerMessage.contains("cancel workflow")) {
            return "cancelCase";
        }
        if (lowerMessage.contains("specification") || lowerMessage.contains("workflow") && lowerMessage.contains("list")) {
            return "getSpecifications";
        }
        if (lowerMessage.contains("checkout") || lowerMessage.contains("check out")) {
            return "checkOutWorkItem";
        }
        if (lowerMessage.contains("checkin") || lowerMessage.contains("check in")) {
            return "checkInWorkItem";
        }
        if (lowerMessage.contains("status")) {
            return "getCaseStatus";
        }

        throw new A2AException(
            A2AException.ErrorCode.SKILL_NOT_FOUND,
            "Could not determine skill from message: " + lowerMessage,
            "Include a clear action in your message like 'launch', 'get work items', 'complete task', etc."
        );
    }

    private String extractIdentifier(String text, String prefix1, String prefix2) {
        int idx1 = text.indexOf(prefix1);
        if (idx1 != -1) {
            int start = idx1 + prefix1.length();
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                    sb.append(c);
                } else {
                    break;
                }
            }
            if (sb.length() > 0) {
                return prefix1 + sb;
            }
        }

        int idx2 = text.toLowerCase().indexOf(prefix2);
        if (idx2 != -1) {
            int start = idx2 + prefix2.length();
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                    sb.append(c);
                } else {
                    break;
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }

        return null;
    }

    private String extractSpecId(String text) {
        // Look for quoted spec ID
        int quoteIdx = text.indexOf("'");
        if (quoteIdx != -1) {
            int endIdx = text.indexOf("'", quoteIdx + 1);
            if (endIdx != -1) {
                return text.substring(quoteIdx + 1, endIdx);
            }
        }
        return null;
    }

    private String extractTaskId(String text) {
        // Look for "task X" pattern
        int taskIdx = text.toLowerCase().indexOf("task ");
        if (taskIdx != -1) {
            int start = taskIdx + 5;
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                    sb.append(c);
                } else {
                    break;
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return null;
    }

    private String extractJsonData(String text) {
        int braceIdx = text.indexOf("{");
        if (braceIdx != -1) {
            int depth = 0;
            for (int i = braceIdx; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(braceIdx, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private A2ATypes.Message parseMessageFromJson(String json) {
        if (json == null || json.isEmpty()) {
            return A2ATypes.Message.userMessage("");
        }

        // Simple extraction of text content
        String text = "";
        int textIdx = json.indexOf("\"text\":\"");
        if (textIdx != -1) {
            textIdx += "\"text\":\"".length();
            int textEnd = json.indexOf("\"", textIdx);
            if (textEnd != -1) {
                text = json.substring(textIdx, textEnd)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            }
        } else {
            // Try content field
            int contentIdx = json.indexOf("\"content\":\"");
            if (contentIdx != -1) {
                contentIdx += "\"content\":\"".length();
                int contentEnd = json.indexOf("\"", contentIdx);
                if (contentEnd != -1) {
                    text = json.substring(contentIdx, contentEnd)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
                }
            }
        }

        return A2ATypes.Message.userMessage(text);
    }

    private String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else if (value instanceof List) {
                sb.append(listToJson((List<?>) value));
            } else if (value instanceof Map) {
                sb.append(mapToJson((Map<String, Object>) value));
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String listToJson(List<?> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(",");
            if (item instanceof String) {
                sb.append("\"").append(escapeJson((String) item)).append("\"");
            } else if (item instanceof Number || item instanceof Boolean) {
                sb.append(item);
            } else if (item instanceof Map) {
                sb.append(mapToJson((Map<String, Object>) item));
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(item))).append("\"");
            }
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private int mapErrorCode(A2AException.ErrorCode code) {
        return switch (code) {
            case AGENT_CARD_NOT_FOUND, SKILL_NOT_FOUND -> -32601;
            case INVALID_AGENT_CARD, INVALID_MESSAGE -> -32602;
            case AUTHENTICATION_REQUIRED, AUTHENTICATION_FAILED -> -32500;
            case CONNECTION_FAILED, NETWORK_ERROR -> -32501;
            case TASK_EXECUTION_FAILED -> -32502;
            case TIMEOUT -> -32503;
            default -> -32603;
        };
    }

    private static class ParsedMessage {
        final String skillId;
        final Map<String, Object> params;

        ParsedMessage(String skillId, Map<String, Object> params) {
            this.skillId = skillId;
            this.params = params;
        }
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        String serverUrl = args.length > 0 ? args[0] : "http://localhost:8082";

        System.out.println("YAWL Agent Executor Test");
        System.out.println("========================");
        System.out.println();

        try {
            YawlAgentExecutor executor = YawlAgentExecutor.fromEnvironment(serverUrl);

            System.out.println("Agent Card:");
            System.out.println(executor.getAgentCard().toJson());

            System.out.println("\n\nAvailable Skills:");
            for (String skillId : executor.getAvailableSkills()) {
                System.out.println("  - " + skillId);
            }

            System.out.println("\n\nConnecting to YAWL engine...");
            executor.connect();
            System.out.println("Connected: " + executor.isConnected());

            // Test message processing
            System.out.println("\n\nTesting message processing...");
            String testMessage = "Get available work items";
            System.out.println("Message: " + testMessage);

            Map<String, Object> result = executor.processMessage(testMessage);
            System.out.println("Result: " + result);

            executor.disconnect();

        } catch (IllegalStateException e) {
            System.err.println("Configuration error: " + e.getMessage());
        } catch (A2AException e) {
            System.err.println("Error: " + e.getFullReport());
        }
    }
}
