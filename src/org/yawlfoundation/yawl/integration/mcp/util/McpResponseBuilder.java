package org.yawlfoundation.yawl.integration.mcp.util;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Utility for building MCP tool responses consistently.
 * Used across all MCP tool specifications.
 */
public final class McpResponseBuilder {

    private McpResponseBuilder() {} // Utility class

    /** Build success response with text content */
    public static CallToolResult success(String content) {
        return new CallToolResult(
            List.of(new TextContent(content)),
            false, // isError
            null,  // toolName
            null   // context
        );
    }

    /** Build success response with timing info */
    public static CallToolResult successWithTiming(String content, String operationName, long elapsedMs) {
        String response = content + "\n\n---\n" + operationName + " completed in " + elapsedMs + "ms";
        return success(response);
    }

    /** Build success response with structured data */
    public static CallToolResult successWithData(Map<String, Object> data, long elapsedMs) {
        StringBuilder sb = new StringBuilder();
        StringJoiner joiner = new StringJoiner("\n");
        data.forEach((key, value) -> joiner.add(key + ": " + value));
        joiner.add("elapsed_ms: " + elapsedMs);
        return success(joiner.toString());
    }

    /** Build error response */
    public static CallToolResult error(String errorMessage) {
        return new CallToolResult(
            List.of(new TextContent("ERROR: " + errorMessage)),
            true,  // isError
            null,  // toolName
            null   // context
        );
    }

    /** Build error response with exception details */
    public static CallToolResult error(String operationName, Exception e) {
        return new CallToolResult(
            List.of(new TextContent(
                "ERROR in " + operationName + ": " + e.getClass().getSimpleName() +
                " - " + e.getMessage()
            )),
            true,  // isError
            null,  // toolName
            null   // context
        );
    }

    /** Build error response with full stack trace (for debugging) */
    public static CallToolResult errorWithStackTrace(String operationName, Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append("ERROR in ").append(operationName).append(": ");
        sb.append(e.getClass().getSimpleName()).append(" - ").append(e.getMessage());
        sb.append("\n\nStack trace:\n");
        for (StackTraceElement ste : e.getStackTrace()) {
            sb.append("  at ").append(ste.toString()).append("\n");
        }
        return new CallToolResult(
            List.of(new TextContent(sb.toString())),
            true,  // isError
            null,  // toolName
            null   // context
        );
    }

    /** Format key-value pairs for response */
    public static String formatKeyValue(Map<String, ?> data) {
        StringJoiner joiner = new StringJoiner("\n");
        data.forEach((key, value) -> joiner.add(key + ": " + value));
        return joiner.toString();
    }

    /** Create header with operation name */
    public static String header(String operationName) {
        return "=== " + operationName + " ===\n";
    }
}