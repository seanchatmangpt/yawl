package org.yawlfoundation.yawl.integration.claude;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool for executing Claude Code CLI commands from YAWL workflows.
 *
 * <p>This tool enables YAWL workflows to control Claude Code CLI for
 * autonomous code generation, testing, and deployment. It implements
 * the MCP 2025-11-25 specification with pipe-based communication.</p>
 *
 * <p>Input Schema:</p>
 * <ul>
 *   <li>prompt (required) - Natural language prompt for Claude</li>
 *   <li>session_id (optional) - Session ID for multi-turn conversations</li>
 *   <li>working_directory (optional) - Working directory for execution</li>
 *   <li>timeout_seconds (optional) - Execution timeout (default: 300, max: 3600)</li>
 *   <li>allowed_operations (optional) - Set of allowed operations</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class ExecuteClaudeTool {

    private static final Logger LOGGER = Logger.getLogger(ExecuteClaudeTool.class.getName());

    /** Default timeout in seconds */
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    /** Maximum timeout in seconds */
    private static final int MAX_TIMEOUT_SECONDS = 3600;

    private final ClaudeCodeExecutor executor;

    /**
     * Creates a new ExecuteClaudeTool.
     *
     * @param executor the Claude Code executor
     */
    public ExecuteClaudeTool(ClaudeCodeExecutor executor) {
        this.executor = executor;
    }

    /**
     * Creates the MCP tool specification for registration.
     *
     * @return the SyncToolSpecification for yawl_execute_claude
     */
    public McpServerFeatures.SyncToolSpecification createSpecification() {
        Map<String, Object> props = new LinkedHashMap<>();

        props.put("prompt", Map.of(
            "type", "string",
            "description", "Natural language prompt for Claude Code CLI. " +
                          "Supports code generation, testing, git operations, and file manipulation."
        ));

        props.put("session_id", Map.of(
            "type", "string",
            "description", "Optional session ID for multi-turn conversations. " +
                          "Use the session_id from a previous response to continue."
        ));

        props.put("working_directory", Map.of(
            "type", "string",
            "description", "Optional working directory for execution. " +
                          "Defaults to the YAWL engine's working directory."
        ));

        props.put("timeout_seconds", Map.of(
            "type", "integer",
            "description", "Execution timeout in seconds. " +
                          "Default: " + DEFAULT_TIMEOUT_SECONDS + ", Max: " + MAX_TIMEOUT_SECONDS,
            "default", DEFAULT_TIMEOUT_SECONDS,
            "maximum", MAX_TIMEOUT_SECONDS
        ));

        props.put("allowed_operations", Map.of(
            "type", "array",
            "description", "Optional set of allowed operations for security. " +
                          "Valid values: read, write, edit, bash, git, test",
            "items", Map.of(
                "type", "string",
                "enum", List.of("read", "write", "edit", "bash", "git", "test")
            )
        ));

        List<String> required = List.of("prompt");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_execute_claude")
                .description(
                    "Execute Claude Code CLI for autonomous code generation, testing, and deployment. " +
                    "This tool enables YAWL workflows to control Claude Code for self-upgrading capabilities. " +
                    "Returns execution output, session ID for continuation, and timing information.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    return executeTool(args.arguments());
                } catch (Exception e) {
                    LOGGER.severe("ExecuteClaudeTool failed: " + e.getMessage());
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Claude execution failed: " + e.getMessage())),
                        true, null, null);
                }
            }
        );
    }

    /**
     * Executes the tool with the given arguments.
     */
    private McpSchema.CallToolResult executeTool(Map<String, Object> args) {
        // Extract required prompt
        String prompt = requireStringArg(args, "prompt");

        // Extract optional arguments
        String sessionId = optionalStringArg(args, "session_id", null);
        String workingDir = optionalStringArg(args, "working_directory", null);
        int timeoutSeconds = optionalIntArg(args, "timeout_seconds", DEFAULT_TIMEOUT_SECONDS);
        Set<String> allowedOps = optionalSetArg(args, "allowed_operations");

        // Validate timeout
        if (timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(
                    "Timeout " + timeoutSeconds + "s exceeds maximum " + MAX_TIMEOUT_SECONDS + "s")),
                true, null, null);
        }

        // Build working directory path
        Path workingDirectory = workingDir != null ? Paths.get(workingDir) : null;
        Duration timeout = Duration.ofSeconds(timeoutSeconds);

        LOGGER.info("Executing Claude CLI with prompt length: " + prompt.length() +
                   ", session: " + sessionId + ", timeout: " + timeoutSeconds + "s");

        // Execute
        ClaudeExecutionResult result = executor.execute(
            prompt, sessionId, workingDirectory, timeout, allowedOps);

        // Build response
        StringBuilder response = new StringBuilder();

        if (result.success()) {
            response.append("Claude Execution Successful\n");
            response.append("═".repeat(50)).append("\n\n");
            response.append("Duration: ").append(result.durationMs()).append("ms\n");
            response.append("Exit Code: ").append(result.exitCode()).append("\n");

            if (result.hasSession()) {
                response.append("Session ID: ").append(result.sessionId()).append("\n");
            }

            response.append("\n--- Output ---\n");
            response.append(result.output());
        } else {
            response.append("Claude Execution Failed\n");
            response.append("═".repeat(50)).append("\n\n");
            response.append("Exit Code: ").append(result.exitCode()).append("\n");
            response.append("Duration: ").append(result.durationMs()).append("ms\n");

            if (!result.output().isEmpty()) {
                response.append("\n--- Partial Output ---\n");
                response.append(result.output());
            }

            if (!result.error().isEmpty()) {
                response.append("\n--- Error ---\n");
                response.append(result.error());
            }
        }

        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent(response.toString())),
            !result.success(), null, null);
    }

    /**
     * Creates a standalone tool specification (static factory).
     *
     * @param interfaceBClient the YAWL InterfaceB client (unused but required for consistency)
     * @param sessionHandle    the YAWL session handle (unused but required for consistency)
     * @return the SyncToolSpecification
     */
    public static McpServerFeatures.SyncToolSpecification create(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        ClaudeCodeExecutor executor = new ClaudeCodeExecutor();
        ExecuteClaudeTool tool = new ExecuteClaudeTool(executor);
        return tool.createSpecification();
    }

    // -------------------------------------------------------------------------
    // Argument extraction utilities
    // -------------------------------------------------------------------------

    private static String requireStringArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Required argument missing: " + name);
        }
        return value.toString();
    }

    private static String optionalStringArg(Map<String, Object> args, String name, String defaultValue) {
        Object value = args.get(name);
        return value != null ? value.toString() : defaultValue;
    }

    private static int optionalIntArg(Map<String, Object> args, String name, int defaultValue) {
        Object value = args.get(name);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> optionalSetArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value instanceof List<?> list) {
            return Set.copyOf((List<String>) list);
        }
        return null;
    }
}
