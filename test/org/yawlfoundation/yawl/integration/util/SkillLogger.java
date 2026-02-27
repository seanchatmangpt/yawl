package org.yawlfoundation.yawl.integration.util;

import java.time.Duration;

/**
 * Logger utility for skill operations.
 */
public final class SkillLogger {

    private SkillLogger() {}

    public static void logInfo(String message) {
        System.out.println("[INFO] " + message);
    }

    public static void logInfo(String message, Object... args) {
        System.out.printf("[INFO] " + message + "%n", args);
    }

    public static void logDebug(String message) {
        System.out.println("[DEBUG] " + message);
    }

    public static void logDebug(String message, Object... args) {
        System.out.printf("[DEBUG] " + message + "%n", args);
    }

    public static void logError(String message) {
        System.err.println("[ERROR] " + message);
    }

    public static void logError(String message, Object... args) {
        System.err.printf("[ERROR] " + message + "%n", args);
    }

    public static void logWarn(String message) {
        System.out.println("[WARN] " + message);
    }

    public static void logWarn(String message, Object... args) {
        System.out.printf("[WARN] " + message + "%n", args);
    }

    public static void logTrace(String message) {
        System.out.println("[TRACE] " + message);
    }

    public static void logTrace(String message, Object... args) {
        System.out.printf("[TRACE] " + message + "%n", args);
    }

    public static <T> T withTiming(String operation, TimingTask<T> task) {
        long start = System.nanoTime();
        try {
            T result = task.execute();
            long duration = System.nanoTime() - start;
            logInfo("%s completed in %d ms", operation, duration / 1_000_000);
            return result;
        } catch (Exception e) {
            long duration = System.nanoTime() - start;
            logError("%s failed after %d ms: %s", operation, duration / 1_000_000, e.getMessage());
            throw e;
        }
    }

    @FunctionalInterface
    public interface TimingTask<T> {
        T execute() throws Exception;
    }
}