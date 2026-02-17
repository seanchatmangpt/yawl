package org.yawlfoundation.yawl.util;

/**
 * Detects the current runtime environment (local vs Claude Code Web).
 *
 * This utility enables dual-track architecture where the codebase adapts
 * behavior based on whether it's running in:
 * - Local development (persistent databases, full services)
 * - Claude Code Web (ephemeral databases, unit tests only)
 *
 * Usage:
 * <pre>
 * if (EnvironmentDetector.isClaudeCodeRemote()) {
 *     // Use in-memory database, skip integration tests
 *     return new EphemeralWorkflowEngine();
 * } else {
 *     // Use PostgreSQL, run full integration tests
 *     return new ProductionWorkflowEngine();
 * }
 * </pre>
 *
 * @author YAWL Team
 * @version 5.2
 */
public class EnvironmentDetector {

    /**
     * Primary indicator: Claude Code Web sets CLAUDE_CODE_REMOTE=true
     */
    private static final String CLAUDE_CODE_REMOTE = System.getenv("CLAUDE_CODE_REMOTE");

    /**
     * Secondary indicator: General Claude Code environment
     */
    private static final String CLAUDECODE = System.getenv("CLAUDECODE");

    /**
     * Environment type: cloud_default, local, etc.
     */
    private static final String ENVIRONMENT_TYPE = System.getenv("CLAUDE_CODE_REMOTE_ENVIRONMENT_TYPE");

    /**
     * Session ID for remote sessions (useful for logging)
     */
    private static final String REMOTE_SESSION_ID = System.getenv("CLAUDE_CODE_REMOTE_SESSION_ID");

    /**
     * Detects if running in Claude Code Web (remote ephemeral environment).
     *
     * @return true if in Claude Code Web, false if local development
     */
    public static boolean isClaudeCodeRemote() {
        return "true".equalsIgnoreCase(CLAUDE_CODE_REMOTE);
    }

    /**
     * Detects if running in any Claude Code environment (local or remote).
     *
     * @return true if in Claude Code (CLI or Web), false otherwise
     */
    public static boolean isClaudeCode() {
        return "1".equals(CLAUDECODE) || isClaudeCodeRemote();
    }

    /**
     * Gets the environment type (cloud_default, local, etc.).
     *
     * @return environment type string, or "local" if not set
     */
    public static String getEnvironmentType() {
        return ENVIRONMENT_TYPE != null ? ENVIRONMENT_TYPE : "local";
    }

    /**
     * Gets the remote session ID (useful for logging and diagnostics).
     *
     * @return session ID, or null if not in remote session
     */
    public static String getRemoteSessionId() {
        return REMOTE_SESSION_ID;
    }

    /**
     * Determines if database should be ephemeral (in-memory) or persistent.
     *
     * @return true if should use ephemeral database (H2/Derby in-memory)
     */
    public static boolean useEphemeralDatabase() {
        return isClaudeCodeRemote();
    }

    /**
     * Determines if integration tests should be skipped.
     * Integration tests require persistent databases and multiple services.
     *
     * @return true if should skip integration tests
     */
    public static boolean skipIntegrationTests() {
        return isClaudeCodeRemote();
    }

    /**
     * Gets recommended database type based on environment.
     *
     * @return "h2" for remote, "postgres" for local
     */
    public static String getRecommendedDatabaseType() {
        return isClaudeCodeRemote() ? "h2" : "postgres";
    }

    /**
     * Prints environment information (useful for debugging).
     *
     * @return multi-line string with environment details
     */
    public static String getEnvironmentInfo() {
        var base = """
                YAWL Environment Information:
                  Environment: %s
                  Type: %s
                  Database: %s
                  Ephemeral DB: %s
                  Skip Integration Tests: %s
                """.formatted(
                isClaudeCodeRemote() ? "Claude Code Web (Remote)" : "Local Development",
                getEnvironmentType(),
                getRecommendedDatabaseType(),
                useEphemeralDatabase(),
                skipIntegrationTests());
        return REMOTE_SESSION_ID != null
                ? base + "  Session ID: " + REMOTE_SESSION_ID + "\n"
                : base;
    }

    /**
     * Main method for testing environment detection.
     */
    public static void main(String[] args) {
        System.out.println(getEnvironmentInfo());
    }
}
