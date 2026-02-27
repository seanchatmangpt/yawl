package org.yawlfoundation.yawl.integration.mcp.util;

/**
 * Logger utility for MCP operations.
 */
public final class McpLogger {

    private McpLogger() {}

    public static void logInfo(String message) {
        System.out.println("[MCP-INFO] " + message);
    }

    public static void logInfo(String message, Object... args) {
        System.out.printf("[MCP-INFO] " + message + "%n", args);
    }

    public static void logDebug(String message) {
        System.out.println("[MCP-DEBUG] " + message);
    }

    public static void logDebug(String message, Object... args) {
        System.out.printf("[MCP-DEBUG] " + message + "%n", args);
    }

    public static void logError(String message) {
        System.err.println("[MCP-ERROR] " + message);
    }

    public static void logError(String message, Object... args) {
        System.err.printf("[MCP-ERROR] " + message + "%n", args);
    }

    public static void logWarn(String message) {
        System.out.println("[MCP-WARN] " + message);
    }

    public static void logWarn(String message, Object... args) {
        System.out.printf("[MCP-WARN] " + message + "%n", args);
    }

    public static void logTrace(String message) {
        System.out.println("[MCP-TRACE] " + message);
    }

    public static void logTrace(String message, Object... args) {
        System.out.printf("[MCP-TRACE] " + message + "%n", args);
    }

    public static void logMcpInfo(String message) {
        System.out.println("[MCP] " + message);
    }

    public static void logMcpInfo(String message, Object... args) {
        System.out.printf("[MCP] " + message + "%n", args);
    }

    public static void logMcpError(String message) {
        System.err.println("[MCP] ERROR: " + message);
    }

    public static void logMcpError(String message, Object... args) {
        System.err.printf("[MCP] ERROR: " + message + "%n", args);
    }

    public static void logInvocation(String method, String params) {
        System.out.println("[MCP] INVOKING " + method + "(" + params + ")");
    }

    public static void logSuccess(String method, String message) {
        System.out.println("[MCP] SUCCESS: " + method + " - " + message);
    }

    public static void logError(String method, String message) {
        System.err.println("[MCP] ERROR: " + method + " - " + message);
    }
}