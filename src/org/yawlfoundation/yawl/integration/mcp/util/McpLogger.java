package org.yawlfoundation.yawl.integration.mcp.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.StringJoiner;

/**
 * A shared utility class for consistent logging across MCP tool specifications.
 *
 * This class wraps standard Logger with MCP-specific formatting, including:
 * - Tool name prefix for easy identification
 * - Timing information for performance tracking
 * - Structured logging with key-value pairs
 * - Tool invocation and response logging
 */
public final class McpLogger {

    private final Logger logger;
    private final String toolName;

    /**
     * Creates a new McpLogger instance for the specified tool.
     *
     * @param toolName the name of the tool (used in log prefixes)
     */
    public McpLogger(String toolName) {
        this.toolName = toolName;
        this.logger = LogManager.getLogger("yawl.mcp." + toolName);
    }

    /**
     * Factory method to create a McpLogger for a specific tool.
     *
     * @param toolName the name of the tool
     * @return a new McpLogger instance
     */
    public static McpLogger forTool(String toolName) {
        return new McpLogger(toolName);
    }

    /**
     * Gets the log prefix for this tool.
     *
     * @return the prefix string including tool name and brackets
     */
    private String prefix() {
        return "[" + toolName + "] ";
    }

    // ─── Basic Logging ──────────────────────────────────────────────────────

    /**
     * Logs an informational message.
     *
     * @param message the message to log
     */
    public void info(String message) {
        logger.info(prefix() + message);
    }

    /**
     * Logs a warning message.
     *
     * @param message the message to log
     */
    public void warn(String message) {
        logger.warn(prefix() + message);
    }

    /**
     * Logs an error message.
     *
     * @param message the message to log
     */
    public void error(String message) {
        logger.error(prefix() + message);
    }

    /**
     * Logs an error message with an exception.
     *
     * @param message the message to log
     * @param t the exception to include in the log
     */
    public void error(String message, Throwable t) {
        logger.error(prefix() + message, t);
    }

    /**
     * Logs a debug message.
     *
     * @param message the message to log
     */
    public void debug(String message) {
        logger.debug(prefix() + message);
    }

    // ─── Tool Invocation Logging ────────────────────────────────────────────

    /**
     * Logs tool invocation with provided arguments.
     *
     * @param arguments a map of arguments passed to the tool
     */
    public void logInvocation(Map<String, Object> arguments) {
        debug("Invoked with arguments: " + arguments.keySet());
    }

    /**
     * Logs successful tool execution with timing information.
     *
     * @param elapsedMs the execution time in milliseconds
     */
    public void logSuccess(long elapsedMs) {
        info("Tool execution succeeded in " + elapsedMs + "ms");
    }

    /**
     * Logs failed tool execution with timing information.
     *
     * @param errorMessage the error message describing the failure
     * @param elapsedMs the execution time in milliseconds
     */
    public void logError(String errorMessage, long elapsedMs) {
        error("Tool execution failed in " + elapsedMs + "ms: " + errorMessage);
    }

    // ─── Timing Logging ─────────────────────────────────────────────────────

    /**
     * Logs completion of an operation with timing information.
     *
     * @param operation the name of the operation that completed
     * @param elapsedMs the time taken in milliseconds
     */
    public void logTiming(String operation, long elapsedMs) {
        info(operation + " completed in " + elapsedMs + "ms");
    }

    // ─── Structured Logging ─────────────────────────────────────────────────

    /**
     * Logs an informational message with structured data.
     *
     * @param message the main message
     * @param data key-value pairs to include in the log
     */
    public void infoStructured(String message, Map<String, ?> data) {
        StringJoiner joiner = new StringJoiner(", ", message + " [", "]");
        data.forEach((k, v) -> joiner.add(k + "=" + v));
        logger.info(prefix() + joiner.toString());
    }

    /**
     * Logs a warning message with structured data.
     *
     * @param message the main message
     * @param data key-value pairs to include in the log
     */
    public void warnStructured(String message, Map<String, ?> data) {
        StringJoiner joiner = new StringJoiner(", ", message + " [", "]");
        data.forEach((k, v) -> joiner.add(k + "=" + v));
        logger.warn(prefix() + joiner.toString());
    }

    // ─── Performance Logging ────────────────────────────────────────────────

    /**
     * Logs performance metrics for an operation.
     *
     * @param operation the name of the operation
     * @param elapsedMs the execution time in milliseconds
     * @param memoryDeltaBytes the change in memory usage in bytes
     */
    public void logPerformance(String operation, long elapsedMs, long memoryDeltaBytes) {
        infoStructured(operation + " performance", Map.of(
            "elapsed_ms", elapsedMs,
            "memory_delta_bytes", memoryDeltaBytes
        ));
    }
}