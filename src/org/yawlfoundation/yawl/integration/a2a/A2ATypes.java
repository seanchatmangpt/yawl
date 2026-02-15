package org.yawlfoundation.yawl.integration.a2a;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A2A Protocol Types - POJOs for Agent-to-Agent Communication
 *
 * Data structures implementing the A2A protocol specification.
 * No external SDK dependencies - pure Java implementation.
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see <a href="https://a2a-protocol.org">A2A Protocol Specification</a>
 */
public final class A2ATypes {

    private A2ATypes() {
        // Utility class - prevent instantiation
    }

    // Protocol version constant
    public static final String A2A_PROTOCOL_VERSION = "1.0";

    /**
     * Agent Card - describes an agent's capabilities and metadata.
     * Retrieved via /.well-known/agent.json endpoint.
     */
    public static final class AgentCard {
        private final String name;
        private final String description;
        private final String url;
        private final String version;
        private final AgentCapabilities capabilities;
        private final List<AgentSkill> skills;
        private final String protocolVersion;
        private final AuthenticationInfo authentication;

        public AgentCard(String name, String description, String url, String version,
                         AgentCapabilities capabilities, List<AgentSkill> skills,
                         String protocolVersion, AuthenticationInfo authentication) {
            this.name = Objects.requireNonNull(name, "Agent name is required");
            this.description = description != null ? description : "";
            this.url = Objects.requireNonNull(url, "Agent URL is required");
            this.version = version != null ? version : "1.0.0";
            this.capabilities = capabilities != null ? capabilities : new AgentCapabilities(false, false);
            this.skills = skills != null ? Collections.unmodifiableList(new ArrayList<>(skills)) : Collections.emptyList();
            this.protocolVersion = protocolVersion != null ? protocolVersion : A2A_PROTOCOL_VERSION;
            this.authentication = authentication;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getUrl() { return url; }
        public String getVersion() { return version; }
        public AgentCapabilities getCapabilities() { return capabilities; }
        public List<AgentSkill> getSkills() { return skills; }
        public String getProtocolVersion() { return protocolVersion; }
        public AuthenticationInfo getAuthentication() { return authentication; }

        public AgentSkill getSkillById(String skillId) {
            for (AgentSkill skill : skills) {
                if (skill.getId().equals(skillId)) {
                    return skill;
                }
            }
            return null;
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"name\":\"").append(escapeJson(name)).append("\",");
            sb.append("\"description\":\"").append(escapeJson(description)).append("\",");
            sb.append("\"url\":\"").append(escapeJson(url)).append("\",");
            sb.append("\"version\":\"").append(escapeJson(version)).append("\",");
            sb.append("\"protocolVersion\":\"").append(escapeJson(protocolVersion)).append("\",");
            sb.append("\"capabilities\":").append(capabilities.toJson()).append(",");
            sb.append("\"skills\":[");
            for (int i = 0; i < skills.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(skills.get(i).toJson());
            }
            sb.append("]");
            if (authentication != null) {
                sb.append(",\"authentication\":").append(authentication.toJson());
            }
            sb.append("}");
            return sb.toString();
        }

        @Override
        public String toString() {
            return "AgentCard{name='" + name + "', url='" + url + "', skills=" + skills.size() + "}";
        }
    }

    /**
     * Agent Capabilities - describes supported communication modes.
     */
    public static final class AgentCapabilities {
        private final boolean streaming;
        private final boolean pushNotifications;

        public AgentCapabilities(boolean streaming, boolean pushNotifications) {
            this.streaming = streaming;
            this.pushNotifications = pushNotifications;
        }

        public boolean isStreaming() { return streaming; }
        public boolean isPushNotifications() { return pushNotifications; }

        public String toJson() {
            return "{\"streaming\":" + streaming + ",\"pushNotifications\":" + pushNotifications + "}";
        }
    }

    /**
     * Agent Skill - describes a single capability the agent can perform.
     */
    public static final class AgentSkill {
        private final String id;
        private final String name;
        private final String description;
        private final List<String> tags;
        private final List<String> examples;
        private final Map<String, Object> inputSchema;

        public AgentSkill(String id, String name, String description,
                          List<String> tags, List<String> examples, Map<String, Object> inputSchema) {
            this.id = Objects.requireNonNull(id, "Skill ID is required");
            this.name = Objects.requireNonNull(name, "Skill name is required");
            this.description = description != null ? description : "";
            this.tags = tags != null ? Collections.unmodifiableList(new ArrayList<>(tags)) : Collections.emptyList();
            this.examples = examples != null ? Collections.unmodifiableList(new ArrayList<>(examples)) : Collections.emptyList();
            this.inputSchema = inputSchema;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<String> getTags() { return tags; }
        public List<String> getExamples() { return examples; }
        public Map<String, Object> getInputSchema() { return inputSchema; }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"id\":\"").append(escapeJson(id)).append("\",");
            sb.append("\"name\":\"").append(escapeJson(name)).append("\",");
            sb.append("\"description\":\"").append(escapeJson(description)).append("\",");
            sb.append("\"tags\":[");
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(tags.get(i))).append("\"");
            }
            sb.append("],");
            sb.append("\"examples\":[");
            for (int i = 0; i < examples.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(examples.get(i))).append("\"");
            }
            sb.append("]");
            sb.append("}");
            return sb.toString();
        }

        @Override
        public String toString() {
            return "AgentSkill{id='" + id + "', name='" + name + "'}";
        }
    }

    /**
     * Authentication Info - describes authentication requirements.
     */
    public static final class AuthenticationInfo {
        private final String type;
        private final List<String> schemes;

        public AuthenticationInfo(String type, List<String> schemes) {
            this.type = type != null ? type : "none";
            this.schemes = schemes != null ? Collections.unmodifiableList(new ArrayList<>(schemes)) : Collections.emptyList();
        }

        public String getType() { return type; }
        public List<String> getSchemes() { return schemes; }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"").append(escapeJson(type)).append("\"");
            if (!schemes.isEmpty()) {
                sb.append(",\"schemes\":[");
                for (int i = 0; i < schemes.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(escapeJson(schemes.get(i))).append("\"");
                }
                sb.append("]");
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * Message - A2A message structure for communication.
     */
    public static final class Message {
        private final String role;
        private final List<Part> parts;
        private final String messageId;

        public Message(String role, List<Part> parts, String messageId) {
            this.role = Objects.requireNonNull(role, "Message role is required");
            this.parts = parts != null ? Collections.unmodifiableList(new ArrayList<>(parts)) : Collections.emptyList();
            this.messageId = messageId != null ? messageId : generateMessageId();
        }

        public String getRole() { return role; }
        public List<Part> getParts() { return parts; }
        public String getMessageId() { return messageId; }

        public String getTextContent() {
            StringBuilder sb = new StringBuilder();
            for (Part part : parts) {
                if (part instanceof TextPart) {
                    sb.append(((TextPart) part).getText());
                }
            }
            return sb.toString();
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"role\":\"").append(escapeJson(role)).append("\",");
            sb.append("\"messageId\":\"").append(escapeJson(messageId)).append("\",");
            sb.append("\"parts\":[");
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(parts.get(i).toJson());
            }
            sb.append("]");
            sb.append("}");
            return sb.toString();
        }

        private static String generateMessageId() {
            return "msg-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
        }

        public static Message userMessage(String text) {
            return new Message("user", Collections.singletonList(new TextPart(text)), null);
        }

        public static Message agentMessage(String text) {
            return new Message("agent", Collections.singletonList(new TextPart(text)), null);
        }
    }

    /**
     * Part - Sealed interface for message parts.
     */
    public interface Part {
        String toJson();
    }

    /**
     * Text Part - Text content in a message.
     */
    public static final class TextPart implements Part {
        private final String text;

        public TextPart(String text) {
            this.text = text != null ? text : "";
        }

        public String getText() { return text; }

        @Override
        public String toJson() {
            return "{\"type\":\"text\",\"text\":\"" + escapeJson(text) + "\"}";
        }
    }

    /**
     * Data Part - Structured data in a message.
     */
    public static final class DataPart implements Part {
        private final Map<String, Object> data;

        public DataPart(Map<String, Object> data) {
            this.data = data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap();
        }

        public Map<String, Object> getData() { return data; }

        @Override
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"data\",\"data\":{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
                sb.append("\"").append(escapeJson(String.valueOf(entry.getValue()))).append("\"");
                first = false;
            }
            sb.append("}}");
            return sb.toString();
        }
    }

    /**
     * Task - Represents an A2A task with status and history.
     */
    public static final class Task {
        private final String id;
        private final TaskStatus status;
        private final List<Message> history;
        private final List<Artifact> artifacts;

        public Task(String id, TaskStatus status, List<Message> history, List<Artifact> artifacts) {
            this.id = Objects.requireNonNull(id, "Task ID is required");
            this.status = status != null ? status : TaskStatus.SUBMITTED;
            this.history = history != null ? Collections.unmodifiableList(new ArrayList<>(history)) : Collections.emptyList();
            this.artifacts = artifacts != null ? Collections.unmodifiableList(new ArrayList<>(artifacts)) : Collections.emptyList();
        }

        public String getId() { return id; }
        public TaskStatus getStatus() { return status; }
        public List<Message> getHistory() { return history; }
        public List<Artifact> getArtifacts() { return artifacts; }

        public boolean isCompleted() {
            return status == TaskStatus.COMPLETED || status == TaskStatus.FAILED || status == TaskStatus.CANCELED;
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"id\":\"").append(escapeJson(id)).append("\",");
            sb.append("\"status\":\"").append(status.getValue()).append("\",");
            sb.append("\"history\":[");
            for (int i = 0; i < history.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(history.get(i).toJson());
            }
            sb.append("],");
            sb.append("\"artifacts\":[");
            for (int i = 0; i < artifacts.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(artifacts.get(i).toJson());
            }
            sb.append("]");
            sb.append("}");
            return sb.toString();
        }

        @Override
        public String toString() {
            return "Task{id='" + id + "', status=" + status + "}";
        }
    }

    /**
     * Task Status - Possible states of an A2A task.
     */
    public enum TaskStatus {
        SUBMITTED("submitted"),
        WORKING("working"),
        COMPLETED("completed"),
        FAILED("failed"),
        CANCELED("canceled"),
        INPUT_REQUIRED("input-required");

        private final String value;

        TaskStatus(String value) {
            this.value = value;
        }

        public String getValue() { return value; }

        public static TaskStatus fromValue(String value) {
            for (TaskStatus status : values()) {
                if (status.value.equalsIgnoreCase(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown task status: " + value);
        }
    }

    /**
     * Artifact - Output artifact from a task.
     */
    public static final class Artifact {
        private final String name;
        private final String description;
        private final List<Part> parts;

        public Artifact(String name, String description, List<Part> parts) {
            this.name = Objects.requireNonNull(name, "Artifact name is required");
            this.description = description != null ? description : "";
            this.parts = parts != null ? Collections.unmodifiableList(new ArrayList<>(parts)) : Collections.emptyList();
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<Part> getParts() { return parts; }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"name\":\"").append(escapeJson(name)).append("\",");
            sb.append("\"description\":\"").append(escapeJson(description)).append("\",");
            sb.append("\"parts\":[");
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(parts.get(i).toJson());
            }
            sb.append("]");
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * Task Update - Event for task status changes.
     */
    public static final class TaskUpdate {
        private final String taskId;
        private final TaskStatus newStatus;
        private final Message message;
        private final long timestamp;

        public TaskUpdate(String taskId, TaskStatus newStatus, Message message) {
            this.taskId = taskId;
            this.newStatus = newStatus;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }

        public String getTaskId() { return taskId; }
        public TaskStatus getNewStatus() { return newStatus; }
        public Message getMessage() { return message; }
        public long getTimestamp() { return timestamp; }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"taskId\":\"").append(escapeJson(taskId)).append("\",");
            sb.append("\"newStatus\":\"").append(newStatus.getValue()).append("\",");
            sb.append("\"timestamp\":").append(timestamp);
            if (message != null) {
                sb.append(",\"message\":").append(message.toJson());
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * A2A Request - Request structure for task operations.
     */
    public static final class A2ARequest {
        private final String jsonrpc;
        private final String method;
        private final Map<String, Object> params;
        private final String id;

        public A2ARequest(String method, Map<String, Object> params, String id) {
            this.jsonrpc = "2.0";
            this.method = Objects.requireNonNull(method, "Method is required");
            this.params = params != null ? Collections.unmodifiableMap(params) : Collections.emptyMap();
            this.id = id != null ? id : generateRequestId();
        }

        public String getJsonrpc() { return jsonrpc; }
        public String getMethod() { return method; }
        public Map<String, Object> getParams() { return params; }
        public String getId() { return id; }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"jsonrpc\":\"").append(jsonrpc).append("\",");
            sb.append("\"method\":\"").append(escapeJson(method)).append("\",");
            sb.append("\"id\":\"").append(escapeJson(id)).append("\",");
            sb.append("\"params\":{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
                Object value = entry.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
                } else if (value instanceof Number) {
                    sb.append(value);
                } else if (value instanceof Boolean) {
                    sb.append(value);
                } else {
                    sb.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
                }
                first = false;
            }
            sb.append("}}");
            return sb.toString();
        }

        private static String generateRequestId() {
            return "req-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
        }
    }

    /**
     * A2A Response - Response structure for JSON-RPC 2.0.
     */
    public static final class A2AResponse {
        private final String jsonrpc;
        private final Object result;
        private final A2AError error;
        private final String id;

        public A2AResponse(Object result, String id) {
            this.jsonrpc = "2.0";
            this.result = result;
            this.error = null;
            this.id = id;
        }

        public A2AResponse(A2AError error, String id) {
            this.jsonrpc = "2.0";
            this.result = null;
            this.error = error;
            this.id = id;
        }

        public String getJsonrpc() { return jsonrpc; }
        public Object getResult() { return result; }
        public A2AError getError() { return error; }
        public String getId() { return id; }
        public boolean isSuccess() { return error == null; }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"jsonrpc\":\"").append(jsonrpc).append("\",");
            sb.append("\"id\":\"").append(escapeJson(id)).append("\",");
            if (error != null) {
                sb.append("\"error\":").append(error.toJson());
            } else if (result != null) {
                if (result instanceof String) {
                    sb.append("\"result\":\"").append(escapeJson(String.valueOf(result))).append("\"");
                } else if (result instanceof Task) {
                    sb.append("\"result\":").append(((Task) result).toJson());
                } else if (result instanceof AgentCard) {
                    sb.append("\"result\":").append(((AgentCard) result).toJson());
                } else {
                    sb.append("\"result\":\"").append(escapeJson(String.valueOf(result))).append("\"");
                }
            } else {
                sb.append("\"result\":null");
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * A2A Error - Error structure for JSON-RPC 2.0.
     */
    public static final class A2AError {
        private final int code;
        private final String message;
        private final Object data;

        public A2AError(int code, String message, Object data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        public int getCode() { return code; }
        public String getMessage() { return message; }
        public Object getData() { return data; }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"code\":").append(code).append(",");
            sb.append("\"message\":\"").append(escapeJson(message)).append("\"");
            if (data != null) {
                sb.append(",\"data\":\"").append(escapeJson(String.valueOf(data))).append("\"");
            }
            sb.append("}");
            return sb.toString();
        }

        // Standard JSON-RPC 2.0 error codes
        public static A2AError invalidRequest() { return new A2AError(-32600, "Invalid Request", null); }
        public static A2AError methodNotFound() { return new A2AError(-32601, "Method not found", null); }
        public static A2AError invalidParams() { return new A2AError(-32602, "Invalid params", null); }
        public static A2AError internalError() { return new A2AError(-32603, "Internal error", null); }
        public static A2AError parseError() { return new A2AError(-32700, "Parse error", null); }
        public static A2AError custom(int code, String message) { return new A2AError(code, message, null); }
    }

    // Utility method for JSON escaping
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
