package org.yawlfoundation.yawl.tooling.cli;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Utility methods for CLI exception handling.
 *
 * Provides consistent error formatting with stack trace context
 * for debugging production issues.
 *
 * @since 6.0.0
 */
public final class CliExceptionHelpers {

    private CliExceptionHelpers() {}

    /**
     * Formats an exception with message and truncated stack trace for CLI output.
     *
     * @param e the exception to format
     * @param context optional context (e.g., "parsing specification")
     * @return formatted error string suitable for stderr
     */
    public static String formatError(Exception e, String context) {
        StringBuilder sb = new StringBuilder();
        if (context != null && !context.isBlank()) {
            sb.append(context).append(": ");
        }
        sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());

        // Include first 3 stack frames for debugging
        StackTraceElement[] stack = e.getStackTrace();
        if (stack != null && stack.length > 0) {
            sb.append("\n  at ");
            int frames = Math.min(3, stack.length);
            for (int i = 0; i < frames; i++) {
                sb.append(stack[i].toString());
                if (i < frames - 1) sb.append("\n  at ");
            }
            if (stack.length > frames) {
                sb.append("\n  ... ").append(stack.length - frames).append(" more");
            }
        }

        // Include cause chain
        Throwable cause = e.getCause();
        while (cause != null) {
            sb.append("\nCaused by: ").append(cause.getClass().getSimpleName())
              .append(": ").append(cause.getMessage());
            cause = cause.getCause();
        }

        return sb.toString();
    }

    /**
     * Returns full stack trace as string (for verbose mode).
     *
     * @param e the exception
     * @return full stack trace
     */
    public static String fullStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
