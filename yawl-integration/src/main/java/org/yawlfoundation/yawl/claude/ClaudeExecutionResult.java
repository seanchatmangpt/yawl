package org.yawlfoundation.yawl.integration.claude;

import java.time.Duration;
import java.util.Optional;

/**
 * Result record for Claude Code CLI execution.
 *
 * <p>Represents the outcome of executing a Claude Code CLI command,
 * including success/failure status, output, error messages, timing,
 * and session management information.</p>
 *
 * <p>Uses Java 25 record syntax for immutability and pattern matching.</p>
 *
 * @param success    whether the execution completed successfully
 * @param output     the stdout output from Claude Code CLI (may be empty)
 * @param error      the stderr error output (may be empty)
 * @param exitCode   the process exit code (0 = success, non-zero = failure)
 * @param duration   the execution duration
 * @param sessionId  the Claude session ID for multi-turn conversations
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record ClaudeExecutionResult(
    boolean success,
    String output,
    String error,
    int exitCode,
    Duration duration,
    String sessionId
) {

    /**
     * Compact canonical constructor with validation.
     */
    public ClaudeExecutionResult {
        if (duration == null) {
            duration = Duration.ZERO;
        }
        if (output == null) {
            output = "";
        }
        if (error == null) {
            error = "";
        }
    }

    /**
     * Creates a successful execution result.
     *
     * @param output   the output from Claude Code CLI
     * @param duration the execution duration
     * @return a success result with exit code 0
     */
    public static ClaudeExecutionResult success(String output, Duration duration) {
        return new ClaudeExecutionResult(true, output, "", 0, duration, null);
    }

    /**
     * Creates a successful execution result with session ID.
     *
     * @param output    the output from Claude Code CLI
     * @param duration  the execution duration
     * @param sessionId the Claude session ID
     * @return a success result with session tracking
     */
    public static ClaudeExecutionResult success(String output, Duration duration, String sessionId) {
        return new ClaudeExecutionResult(true, output, "", 0, duration, sessionId);
    }

    /**
     * Creates a failure execution result.
     *
     * @param error    the error message
     * @param exitCode the non-zero exit code
     * @return a failure result
     */
    public static ClaudeExecutionResult failure(String error, int exitCode) {
        return new ClaudeExecutionResult(false, "", error, exitCode, Duration.ZERO, null);
    }

    /**
     * Creates a failure execution result with output.
     *
     * @param output   partial output before failure
     * @param error    the error message
     * @param exitCode the non-zero exit code
     * @return a failure result with partial output
     */
    public static ClaudeExecutionResult failure(String output, String error, int exitCode) {
        return new ClaudeExecutionResult(false, output, error, exitCode, Duration.ZERO, null);
    }

    /**
     * Creates a timeout execution result.
     *
     * @param timeout the configured timeout duration
     * @return a timeout result with exit code 124 (standard timeout exit code)
     */
    public static ClaudeExecutionResult timeout(Duration timeout) {
        return new ClaudeExecutionResult(
            false,
            "",
            "Execution timed out after " + timeout.toSeconds() + " seconds",
            124,
            timeout,
            null
        );
    }

    /**
     * Checks if this result has a session ID for continuation.
     *
     * @return true if session ID is present
     */
    public boolean hasSession() {
        return sessionId != null && !sessionId.isEmpty();
    }

    /**
     * Gets the output, or an empty string if none.
     *
     * @return the output (never null)
     */
    public String output() {
        return output != null ? output : "";
    }

    /**
     * Gets the error, or an empty string if none.
     *
     * @return the error (never null)
     */
    public String error() {
        return error != null ? error : "";
    }

    /**
     * Gets the duration in milliseconds.
     *
     * @return duration in milliseconds
     */
    public long durationMs() {
        return duration != null ? duration.toMillis() : 0;
    }

    /**
     * Gets the output as an Optional.
     *
     * @return Optional containing output if non-empty
     */
    public Optional<String> outputOpt() {
        return Optional.ofNullable(output).filter(s -> !s.isEmpty());
    }

    /**
     * Gets the error as an Optional.
     *
     * @return Optional containing error if non-empty
     */
    public Optional<String> errorOpt() {
        return Optional.ofNullable(error).filter(s -> !s.isEmpty());
    }

    /**
     * Gets the session ID as an Optional.
     *
     * @return Optional containing session ID if present
     */
    public Optional<String> sessionIdOpt() {
        return Optional.ofNullable(sessionId).filter(s -> !s.isEmpty());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ClaudeExecutionResult{");
        sb.append("success=").append(success);
        sb.append(", exitCode=").append(exitCode);
        sb.append(", duration=").append(duration != null ? duration.toMillis() + "ms" : "0ms");
        if (hasSession()) {
            sb.append(", sessionId='").append(sessionId).append('\'');
        }
        if (!output.isEmpty()) {
            sb.append(", outputLength=").append(output.length());
        }
        if (!error.isEmpty()) {
            sb.append(", errorLength=").append(error.length());
        }
        sb.append('}');
        return sb.toString();
    }
}
