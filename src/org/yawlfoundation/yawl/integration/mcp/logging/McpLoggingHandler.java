package org.yawlfoundation.yawl.integration.mcp.logging;

import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP Logging Handler for YAWL.
 *
 * Provides structured logging with MCP notification support.
 * Log messages can be sent to connected MCP clients for debugging
 * and monitoring purposes via the MCP logging/message notification.
 *
 * Uses McpSyncServer.loggingNotification() to push log messages
 * to all connected clients in real time.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class McpLoggingHandler {

    private static final Logger LOGGER = Logger.getLogger(McpLoggingHandler.class.getName());

    private final ObjectMapper mapper;
    private McpSchema.LoggingLevel currentLevel = McpSchema.LoggingLevel.INFO;

    /**
     * Creates a new logging handler with default ObjectMapper.
     */
    public McpLoggingHandler() {
        this.mapper = new ObjectMapper();
        this.mapper.findAndRegisterModules();
    }

    /**
     * Creates a new logging handler with custom ObjectMapper.
     *
     * @param mapper the ObjectMapper to use
     */
    public McpLoggingHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Sets the current logging level.
     *
     * @param level the new logging level
     */
    public void setLevel(McpSchema.LoggingLevel level) {
        this.currentLevel = level;
        LOGGER.info("Logging level set to: " + level);
    }

    /**
     * Gets the current logging level.
     *
     * @return the current logging level
     */
    public McpSchema.LoggingLevel getLevel() {
        return currentLevel;
    }

    /**
     * Sends a log notification to all connected MCP clients via the server.
     *
     * @param server the MCP sync server instance
     * @param level the log level
     * @param loggerName the logger name
     * @param message the log message
     */
    public void sendLogNotification(
            McpSyncServer server,
            McpSchema.LoggingLevel level,
            String loggerName,
            String message) {

        if (!shouldLog(level)) {
            return;
        }

        try {
            McpSchema.LoggingMessageNotification notification =
                McpSchema.LoggingMessageNotification.builder()
                    .level(level)
                    .logger(loggerName)
                    .data(message)
                    .build();

            server.loggingNotification(notification);

            logLocally(level, loggerName, message);
        } catch (Exception e) {
            LOGGER.warning("Failed to send log notification: " + e.getMessage());
        }
    }

    /**
     * Logs tool execution with parameters.
     *
     * @param server the MCP sync server instance
     * @param toolName the tool name
     * @param args the tool arguments
     */
    public void logToolExecution(
            McpSyncServer server,
            String toolName,
            Map<String, Object> args) {

        try {
            String argsJson = mapper.writeValueAsString(args);
            sendLogNotification(
                    server,
                    McpSchema.LoggingLevel.DEBUG,
                    "yawl-mcp.tools",
                    "Executing tool: " + toolName + " with args: " + argsJson
            );
        } catch (Exception e) {
            LOGGER.fine("Tool execution: " + toolName);
        }
    }

    /**
     * Logs tool completion with result.
     *
     * @param server the MCP sync server instance
     * @param toolName the tool name
     * @param success whether the tool succeeded
     * @param durationMs the execution duration in milliseconds
     */
    public void logToolCompletion(
            McpSyncServer server,
            String toolName,
            boolean success,
            long durationMs) {

        sendLogNotification(
                server,
                success ? McpSchema.LoggingLevel.DEBUG : McpSchema.LoggingLevel.WARNING,
                "yawl-mcp.tools",
                "Tool completed: " + toolName
                    + " (success=" + success + ", duration=" + durationMs + "ms)"
        );
    }

    /**
     * Logs an error with context.
     *
     * @param server the MCP sync server instance
     * @param context the error context
     * @param error the error
     */
    public void logError(
            McpSyncServer server,
            String context,
            Throwable error) {

        String message = context + ": " + error.getMessage();
        sendLogNotification(server, McpSchema.LoggingLevel.ERROR, "yawl-mcp.errors", message);
        LOGGER.severe(context + ": " + error.getMessage());
    }

    /**
     * Logs an info message.
     */
    public void info(McpSyncServer server, String message) {
        sendLogNotification(server, McpSchema.LoggingLevel.INFO, "yawl-mcp", message);
    }

    /**
     * Logs a debug message.
     */
    public void debug(McpSyncServer server, String message) {
        sendLogNotification(server, McpSchema.LoggingLevel.DEBUG, "yawl-mcp", message);
    }

    /**
     * Logs a warning message.
     */
    public void warning(McpSyncServer server, String message) {
        sendLogNotification(server, McpSchema.LoggingLevel.WARNING, "yawl-mcp", message);
    }

    /**
     * Logs an error message.
     */
    public void error(McpSyncServer server, String message) {
        sendLogNotification(server, McpSchema.LoggingLevel.ERROR, "yawl-mcp", message);
    }

    /**
     * Checks if a message at the given level should be logged.
     *
     * @param level the level to check
     * @return true if the message should be logged
     */
    private boolean shouldLog(McpSchema.LoggingLevel level) {
        return level.level() >= currentLevel.level();
    }

    /**
     * Logs a message locally using java.util.logging.
     */
    private void logLocally(McpSchema.LoggingLevel level, String loggerName, String message) {
        String fullMessage = "[" + loggerName + "] " + message;

        switch (level) {
            case DEBUG:
                LOGGER.fine(fullMessage);
                break;
            case INFO:
            case NOTICE:
                LOGGER.info(fullMessage);
                break;
            case WARNING:
                LOGGER.warning(fullMessage);
                break;
            case ERROR:
            case CRITICAL:
            case ALERT:
            case EMERGENCY:
                LOGGER.severe(fullMessage);
                break;
        }
    }
}
