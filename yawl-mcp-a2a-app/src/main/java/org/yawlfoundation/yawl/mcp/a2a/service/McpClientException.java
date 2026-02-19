package org.yawlfoundation.yawl.mcp.a2a.service;

/**
 * Exception thrown when MCP client operations fail after exhausting retries.
 *
 * <p>This exception wraps the underlying cause and provides context about
 * which MCP server and operation failed. It is used by the circuit breaker
 * to track failures and determine when to open the circuit.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class McpClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String serverId;
    private final String operation;
    private final int attemptNumber;

    /**
     * Creates a new MCP client exception.
     *
     * @param message the error message
     * @param serverId the MCP server identifier
     * @param operation the operation that failed
     * @param cause the underlying cause
     */
    public McpClientException(String message, String serverId, String operation, Throwable cause) {
        super(message, cause);
        this.serverId = serverId;
        this.operation = operation;
        this.attemptNumber = 0;
    }

    /**
     * Creates a new MCP client exception with attempt number.
     *
     * @param message the error message
     * @param serverId the MCP server identifier
     * @param operation the operation that failed
     * @param attemptNumber the retry attempt number (0 for initial attempt)
     * @param cause the underlying cause
     */
    public McpClientException(String message, String serverId, String operation,
                              int attemptNumber, Throwable cause) {
        super(message, cause);
        this.serverId = serverId;
        this.operation = operation;
        this.attemptNumber = attemptNumber;
    }

    /**
     * Creates a new MCP client exception without a cause.
     *
     * @param message the error message
     * @param serverId the MCP server identifier
     * @param operation the operation that failed
     */
    public McpClientException(String message, String serverId, String operation) {
        super(message);
        this.serverId = serverId;
        this.operation = operation;
        this.attemptNumber = 0;
    }

    /**
     * Gets the MCP server identifier.
     *
     * @return the server identifier
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Gets the operation that failed.
     *
     * @return the operation name
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Gets the retry attempt number.
     *
     * @return the attempt number (0 for initial attempt)
     */
    public int getAttemptNumber() {
        return attemptNumber;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("McpClientException{");
        sb.append("serverId='").append(serverId).append('\'');
        sb.append(", operation='").append(operation).append('\'');
        sb.append(", attemptNumber=").append(attemptNumber);
        sb.append(", message='").append(getMessage()).append('\'');
        if (getCause() != null) {
            sb.append(", cause=").append(getCause().getClass().getSimpleName());
        }
        sb.append('}');
        return sb.toString();
    }
}
