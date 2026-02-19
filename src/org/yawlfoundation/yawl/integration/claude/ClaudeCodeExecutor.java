package org.yawlfoundation.yawl.integration.claude;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes Claude Code CLI commands with pipe-based communication.
 *
 * <p>This executor uses ProcessBuilder with stdin/stdout pipes to communicate
 * with the Claude Code CLI, supporting JSON mode for structured output.
 * Uses Java 25's Structured Concurrency for timeout handling.</p>
 *
 * <p>Configuration options:</p>
 * <ul>
 *   <li>{@code cliPath} - Path to Claude CLI executable (default: "claude")</li>
 *   <li>{@code defaultTimeout} - Default execution timeout (default: 5 minutes)</li>
 *   <li>{@code maxTimeout} - Maximum allowed timeout (default: 1 hour)</li>
 *   <li>{@code maxPromptLength} - Maximum prompt length (default: 100KB)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ClaudeCodeExecutor {

    private static final Logger LOGGER = Logger.getLogger(ClaudeCodeExecutor.class.getName());

    /** Default Claude CLI executable name */
    private static final String DEFAULT_CLI_PATH = "claude";

    /** Default timeout for CLI operations */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    /** Maximum allowed timeout */
    private static final Duration MAX_TIMEOUT = Duration.ofHours(1);

    /** Maximum prompt length in characters */
    private static final int MAX_PROMPT_LENGTH = 100_000;

    /** Allowed operations for security filtering */
    private static final Set<String> ALLOWED_OPERATIONS = Set.of(
        "read", "write", "edit", "bash", "git", "test"
    );

    private final String cliPath;
    private final Duration defaultTimeout;
    private final Duration maxTimeout;
    private final int maxPromptLength;
    private final ClaudePromptSanitizer sanitizer;
    private final ClaudeSessionManager sessionManager;
    private final Map<String, Process> activeProcesses;

    /**
     * Creates a new ClaudeCodeExecutor with default configuration.
     */
    public ClaudeCodeExecutor() {
        this(DEFAULT_CLI_PATH, DEFAULT_TIMEOUT, MAX_TIMEOUT, MAX_PROMPT_LENGTH);
    }

    /**
     * Creates a new ClaudeCodeExecutor with custom configuration.
     *
     * @param cliPath         path to Claude CLI executable
     * @param defaultTimeout  default execution timeout
     * @param maxTimeout      maximum allowed timeout
     * @param maxPromptLength maximum prompt length in characters
     */
    public ClaudeCodeExecutor(String cliPath, Duration defaultTimeout,
                              Duration maxTimeout, int maxPromptLength) {
        this.cliPath = Objects.requireNonNull(cliPath, "cliPath must not be null");
        this.defaultTimeout = Objects.requireNonNull(defaultTimeout, "defaultTimeout must not be null");
        this.maxTimeout = Objects.requireNonNull(maxTimeout, "maxTimeout must not be null");
        this.maxPromptLength = maxPromptLength;
        this.sanitizer = new ClaudePromptSanitizer();
        this.sessionManager = new ClaudeSessionManager();
        this.activeProcesses = new ConcurrentHashMap<>();

        LOGGER.info("ClaudeCodeExecutor initialized with CLI path: " + cliPath);
    }

    /**
     * Executes a Claude Code CLI command with the given prompt.
     *
     * @param prompt the natural language prompt
     * @return the execution result
     * @throws IllegalArgumentException if prompt is invalid
     */
    public ClaudeExecutionResult execute(String prompt) {
        return execute(prompt, null, null, defaultTimeout, null);
    }

    /**
     * Executes a Claude Code CLI command with full options.
     *
     * @param prompt             the natural language prompt
     * @param sessionId          optional session ID for multi-turn conversations
     * @param workingDirectory   optional working directory
     * @param timeout            execution timeout (uses default if null)
     * @param allowedOperations  optional set of allowed operations
     * @return the execution result
     * @throws IllegalArgumentException if prompt is invalid or timeout exceeds maximum
     */
    public ClaudeExecutionResult execute(String prompt, String sessionId,
                                         Path workingDirectory, Duration timeout,
                                         Set<String> allowedOperations) {
        // Validate and sanitize prompt
        String sanitizedPrompt = validateAndSanitize(prompt);

        // Determine timeout
        Duration effectiveTimeout = timeout != null ? timeout : defaultTimeout;
        if (effectiveTimeout.toMillis() > maxTimeout.toMillis()) {
            throw new IllegalArgumentException(
                "Timeout " + effectiveTimeout + " exceeds maximum " + maxTimeout);
        }

        // Validate allowed operations
        if (allowedOperations != null) {
            validateOperations(allowedOperations);
        }

        // Build command
        List<String> command = buildCommand(sanitizedPrompt, sessionId);

        // Create process builder
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        if (workingDirectory != null) {
            pb.directory(workingDirectory.toFile());
        }

        // Set environment
        pb.environment().put("CLAUDE_CODE_MODE", "json");

        LOGGER.info("Executing Claude CLI: " + String.join(" ", command));

        Instant start = Instant.now();
        String processKey = sessionId != null ? sessionId : "single-" + System.nanoTime();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            Future<ClaudeExecutionResult> future = scope.fork(() -> {
                Process process = pb.start();
                activeProcesses.put(processKey, process);

                try {
                    // Read stdout
                    String output = readStream(process.inputReader(StandardCharsets.UTF_8));

                    // Read stderr
                    String error = readStream(process.errorReader(StandardCharsets.UTF_8));

                    // Wait for completion
                    boolean completed = process.waitFor(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);

                    if (!completed) {
                        process.destroyForcibly();
                        return ClaudeExecutionResult.timeout(effectiveTimeout);
                    }

                    int exitCode = process.exitValue();
                    Duration duration = Duration.between(start, Instant.now());

                    if (exitCode == 0) {
                        // Extract session ID from output if present
                        String extractedSessionId = extractSessionId(output, sessionId);
                        return ClaudeExecutionResult.success(output, duration, extractedSessionId);
                    } else {
                        return ClaudeExecutionResult.failure(output, error, exitCode);
                    }

                } finally {
                    activeProcesses.remove(processKey);
                }
            });

            scope.join();
            scope.throwIfFailed();

            return future.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Duration duration = Duration.between(start, Instant.now());
            return ClaudeExecutionResult.failure("Execution interrupted", -1);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String errorMsg = cause != null ? cause.getMessage() : e.getMessage();
            LOGGER.log(Level.SEVERE, "Execution failed", cause);
            return ClaudeExecutionResult.failure(errorMsg, -1);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to execute Claude CLI", e);
            return ClaudeExecutionResult.failure("Failed to start Claude CLI: " + e.getMessage(), -1);
        }
    }

    /**
     * Cancels an ongoing execution by session ID.
     *
     * @param sessionId the session ID to cancel
     * @return true if a process was cancelled
     */
    public boolean cancel(String sessionId) {
        Process process = activeProcesses.remove(sessionId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            LOGGER.info("Cancelled execution for session: " + sessionId);
            return true;
        }
        return false;
    }

    /**
     * Gets the session manager for multi-turn conversations.
     *
     * @return the session manager
     */
    public ClaudeSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Validates and sanitizes the input prompt.
     */
    private String validateAndSanitize(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be null or blank");
        }
        if (prompt.length() > maxPromptLength) {
            throw new IllegalArgumentException(
                "Prompt length " + prompt.length() + " exceeds maximum " + maxPromptLength);
        }
        return sanitizer.sanitize(prompt);
    }

    /**
     * Validates the allowed operations set.
     */
    private void validateOperations(Set<String> operations) {
        for (String op : operations) {
            if (!ALLOWED_OPERATIONS.contains(op)) {
                throw new IllegalArgumentException(
                    "Operation '" + op + "' is not allowed. Allowed: " + ALLOWED_OPERATIONS);
            }
        }
    }

    /**
     * Builds the command line for Claude CLI.
     */
    private List<String> buildCommand(String prompt, String sessionId) {
        List<String> command = new ArrayList<>();
        command.add(cliPath);
        command.add("--json");
        command.add("--no-progress");
        command.add("-p");
        command.add(prompt);

        if (sessionId != null && !sessionId.isEmpty()) {
            command.add("--resume");
            command.add(sessionId);
        }

        return command;
    }

    /**
     * Reads all content from a BufferedReader.
     */
    private String readStream(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Extracts session ID from output if present.
     */
    private String extractSessionId(String output, String existingSessionId) {
        if (existingSessionId != null) {
            return existingSessionId;
        }
        // Try to extract session ID from JSON output
        if (output != null && output.contains("\"session_id\"")) {
            try {
                int start = output.indexOf("\"session_id\"") + 14;
                int end = output.indexOf("\"", start);
                if (start > 14 && end > start) {
                    return output.substring(start, end);
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to extract session ID", e);
            }
        }
        return null;
    }

    /**
     * Shuts down all active processes.
     */
    public void shutdown() {
        activeProcesses.values().forEach(process -> {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        });
        activeProcesses.clear();
        LOGGER.info("ClaudeCodeExecutor shutdown complete");
    }
}
