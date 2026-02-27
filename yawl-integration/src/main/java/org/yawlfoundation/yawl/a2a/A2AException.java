package org.yawlfoundation.yawl.integration.a2a;

/**
 * A2A Protocol Exception
 *
 * Exception for A2A protocol errors with actionable error messages.
 * Contains context for debugging and resolution guidance.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class A2AException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String resolution;

    /**
     * Error codes for A2A protocol failures
     */
    public enum ErrorCode {
        /** Agent card not found at /.well-known/agent.json */
        AGENT_CARD_NOT_FOUND("Agent card not found"),

        /** Invalid agent card format */
        INVALID_AGENT_CARD("Invalid agent card format"),

        /** Agent does not support the requested skill */
        SKILL_NOT_FOUND("Skill not found"),

        /** Failed to connect to agent */
        CONNECTION_FAILED("Connection failed"),

        /** Task execution failed */
        TASK_EXECUTION_FAILED("Task execution failed"),

        /** Invalid message format */
        INVALID_MESSAGE("Invalid message format"),

        /** Authentication required but not provided */
        AUTHENTICATION_REQUIRED("Authentication required"),

        /** Authentication failed */
        AUTHENTICATION_FAILED("Authentication failed"),

        /** Timeout waiting for response */
        TIMEOUT("Operation timed out"),

        /** Protocol version mismatch */
        VERSION_MISMATCH("Protocol version mismatch"),

        /** Server error */
        SERVER_ERROR("Server error"),

        /** Network error */
        NETWORK_ERROR("Network error");

        private final String description;

        ErrorCode(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Create A2A exception with error code and message
     *
     * @param errorCode the error code
     * @param message detailed error message
     */
    public A2AException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.resolution = null;
    }

    /**
     * Create A2A exception with error code, message, and resolution guidance
     *
     * @param errorCode the error code
     * @param message detailed error message
     * @param resolution actionable resolution steps
     */
    public A2AException(ErrorCode errorCode, String message, String resolution) {
        super(message);
        this.errorCode = errorCode;
        this.resolution = resolution;
    }

    /**
     * Create A2A exception with error code, message, and cause
     *
     * @param errorCode the error code
     * @param message detailed error message
     * @param cause the underlying cause
     */
    public A2AException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.resolution = null;
    }

    /**
     * Create A2A exception with error code, message, resolution, and cause
     *
     * @param errorCode the error code
     * @param message detailed error message
     * @param resolution actionable resolution steps
     * @param cause the underlying cause
     */
    public A2AException(ErrorCode errorCode, String message, String resolution, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.resolution = resolution;
    }

    /**
     * Get the error code
     *
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Get resolution guidance
     *
     * @return actionable resolution steps, or null if not available
     */
    public String getResolution() {
        return resolution;
    }

    /**
     * Check if resolution guidance is available
     *
     * @return true if resolution is available
     */
    public boolean hasResolution() {
        return resolution != null && !resolution.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("A2AException{");
        sb.append("errorCode=").append(errorCode);
        sb.append(", message='").append(getMessage()).append("'");
        if (hasResolution()) {
            sb.append(", resolution='").append(resolution).append("'");
        }
        if (getCause() != null) {
            sb.append(", cause=").append(getCause().getClass().getSimpleName());
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Get full error report with all details
     *
     * @return formatted error report
     */
    public String getFullReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("A2A Protocol Error: ").append(errorCode.getDescription()).append("\n");
        sb.append("Details: ").append(getMessage()).append("\n");
        if (hasResolution()) {
            sb.append("Resolution: ").append(resolution).append("\n");
        }
        if (getCause() != null) {
            sb.append("Cause: ").append(getCause().getMessage()).append("\n");
        }
        return sb.toString();
    }

    // Factory methods for common errors

    /**
     * Create exception for agent card not found
     *
     * @param agentUrl the agent URL
     * @return configured exception
     */
    public static A2AException agentCardNotFound(String agentUrl) {
        return new A2AException(
            ErrorCode.AGENT_CARD_NOT_FOUND,
            "Agent card not found at " + agentUrl + "/.well-known/agent.json",
            "Verify the agent URL is correct and the agent is running.\n" +
            "Check that the agent exposes its capabilities at the well-known endpoint."
        );
    }

    /**
     * Create exception for skill not found
     *
     * @param skillId the requested skill ID
     * @param availableSkills list of available skill IDs
     * @return configured exception
     */
    public static A2AException skillNotFound(String skillId, java.util.List<String> availableSkills) {
        return new A2AException(
            ErrorCode.SKILL_NOT_FOUND,
            "Skill '" + skillId + "' not found. Available skills: " + availableSkills,
            "Use one of the available skills or check the skill ID spelling."
        );
    }

    /**
     * Create exception for connection failure
     *
     * @param agentUrl the agent URL
     * @param cause the underlying cause
     * @return configured exception
     */
    public static A2AException connectionFailed(String agentUrl, Throwable cause) {
        return new A2AException(
            ErrorCode.CONNECTION_FAILED,
            "Failed to connect to agent at " + agentUrl,
            "Verify the agent is running and accessible.\n" +
            "Check network connectivity and firewall settings.\n" +
            "Ensure the URL is correct (include protocol, e.g., http:// or https://)",
            cause
        );
    }

    /**
     * Create exception for timeout
     *
     * @param operation the operation that timed out
     * @param timeoutMs the timeout in milliseconds
     * @return configured exception
     */
    public static A2AException timeout(String operation, long timeoutMs) {
        return new A2AException(
            ErrorCode.TIMEOUT,
            "Operation '" + operation + "' timed out after " + timeoutMs + "ms",
            "The agent may be slow to respond or experiencing issues.\n" +
            "Try increasing the timeout or check agent health."
        );
    }

    /**
     * Create exception for authentication required
     *
     * @param schemes supported authentication schemes
     * @return configured exception
     */
    public static A2AException authenticationRequired(java.util.List<String> schemes) {
        return new A2AException(
            ErrorCode.AUTHENTICATION_REQUIRED,
            "Authentication required. Supported schemes: " + schemes,
            "Provide authentication credentials using one of the supported schemes.\n" +
            "Set credentials via environment variables or client configuration."
        );
    }

    /**
     * Create exception for task execution failure
     *
     * @param taskId the task ID
     * @param reason the failure reason
     * @return configured exception
     */
    public static A2AException taskExecutionFailed(String taskId, String reason) {
        return new A2AException(
            ErrorCode.TASK_EXECUTION_FAILED,
            "Task '" + taskId + "' execution failed: " + reason,
            "Check the task parameters and agent logs for details.\n" +
            "Verify the skill input schema matches your parameters."
        );
    }
}
