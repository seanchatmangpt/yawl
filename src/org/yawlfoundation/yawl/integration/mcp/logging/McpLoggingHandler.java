package org.yawlfoundation.yawl.integration.mcp.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.LoggingLevel;
import io.modelcontextprotocol.spec.LoggingMessageNotification;

import java.util.Map;
import java.util.logging.Logger;

/**
 * MCP Logging Handler for YAWL.
 *
 * Provides structured logging with MCP notification support.
 * Log messages can be sent to connected MCP clients for debugging
 * and monitoring purposes.
 *
 * Features:
 * - Structured logging with levels
 * - MCP client notifications
 * - Tool execution logging
 * - Error logging with context
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class McpLoggingHandler {

    private static final Logger LOGGER = Logger.getLogger(McpLoggingHandler.class.getName());

    private final ObjectMapper mapper;
    private LoggingLevel currentLevel = LoggingLevel.INFO;

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
    public void setLevel(LoggingLevel level) {
        this.currentLevel = level;
        LOGGER.info("Logging level set to: " + level);
    }

    /**
     * Gets the current logging level.
     *
     * @return the current logging level
     */
    public LoggingLevel getLevel() {
        return currentLevel;
    }

    /**
     * Sends a log notification to the MCP client.
     *
     * @param exchange the MCP server exchange
     * @param level the log level
     * @param logger the logger name
     * @param message the log message
     */
    public void sendLogNotification(
            McpSyncServerExchange exchange,
            LoggingLevel level,
            String logger,
            String message) {

        if (!shouldLog(level)) {
            return;
        }

        try {
            LoggingMessageNotification notification = LoggingMessageNotification.builder()
                    .level(level)
                    .logger(logger)
                    .data(message)
                    .build();

            exchange.loggingNotification(notification);

            // Also log locally
            logLocally(level, logger, message);
        } catch (Exception e) {
            LOGGER.warning("Failed to send log notification: " + e.getMessage());
        }
    }

    /**
     * Logs tool execution with parameters.
     *
     * @param exchange the MCP server exchange
     * @param toolName the tool name
     * @param args the tool arguments
     */
    public void logToolExecution(
            McpSyncServerExchange exchange,
            String toolName,
            Map<String, Object> args) {

        try {
            String argsJson = mapper.writeValueAsString(args);
            sendLogNotification(
                    exchange,
                    LoggingLevel.DEBUG,
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
     * @param exchange the MCP server exchange
     * @param toolName the tool name
     * @param success whether the tool succeeded
     * @param durationMs the execution duration in milliseconds
     */
    public void logToolCompletion(
            McpSyncServerExchange exchange,
            String toolName,
            boolean success,
            long durationMs) {

        sendLogNotification(
                exchange,
                success ? LoggingLevel.DEBUG : LoggingLevel.WARNING,
                "yawl-mcp.tools",
                "Tool completed: " + toolName + " (success=" + success + ", duration=" + durationMs + "ms)"
        );
    }

    /**
     * Logs an error with context.
     *
     * @param exchange the MCP server exchange
     * @param context the error context
     * @param error the error
     */
    public void logError(
            McpSyncServerExchange exchange,
            String context,
            Throwable error) {

        String message = context + ": " + error.getMessage();
        sendLogNotification(exchange, LoggingLevel.ERROR, "yawl-mcp.errors", message);

        // Log full stack trace locally
        LOGGER.severe(context + ": " + error.getMessage());
    }

    /**
     * Logs an info message.
     *
     * @param exchange the MCP server exchange
     * @param message the message
     */
    public void info(McpSyncServerExchange exchange, String message) {
        sendLogNotification(exchange, LoggingLevel.INFO, "yawl-mcp", message);
    }

    /**
     * Logs a debug message.
     *
     * @param exchange the MCP server exchange
     * @param message the message
     */
    public void debug(McpSyncServerExchange exchange, String message) {
        sendLogNotification(exchange, LoggingLevel.DEBUG, "yawl-mcp", message);
    }

    /**
     * Logs a warning message.
     *
     * @param exchange the MCP server exchange
     * @param message the message
     */
    public void warning(McpSyncServerExchange exchange, String message) {
        sendLogNotification(exchange, LoggingLevel.WARNING, "yawl-mcp", message);
    }

    /**
     * Logs an error message.
     *
     * @param exchange the MCP server exchange
     * @param message the message
     */
    public void error(McpSyncServerExchange exchange, String message) {
        sendLogNotification(exchange, LoggingLevel.ERROR, "yawl-mcp", message);
    }

    /**
     * Checks if a message at the given level should be logged.
     *
     * @param level the level to check
     * @return true if the message should be logged
     */
    private boolean shouldLog(LoggingLevel level) {
        return level.ordinal() >= currentLevel.ordinal();
    }

    /**
     * Logs a message locally using java.util.logging.
     *
     * @param level the log level
     * @param logger the logger name
     * @param message the message
     */
    private void logLocally(LoggingLevel level, String logger, String message) {
        String fullMessage = "[" + logger + "] " + message;

        switch (level) {
            case DEBUG:
                LOGGER.fine(fullMessage);
                break;
            case INFO:
                LOGGER.info(fullMessage);
                break;
            case NOTICE:
                LOGGER.info(fullMessage);
                break;
            case WARNING:
                LOGGER.warning(fullMessage);
                break;
            case ERROR:
                LOGGER.severe(fullMessage);
                break;
            case CRITICAL:
                LOGGER.severe(fullMessage);
                break;
            case ALERT:
                LOGGER.severe(fullMessage);
                break;
            case EMERGENCY:
                LOGGER.severe(fullMessage);
                break;
            default:
                LOGGER.info(fullMessage);
        }
    }
}
