package org.yawlfoundation.yawl.integration.util;

/**
 * Utility class for parsing event severity levels.
 */
public final class EventSeverityUtils {

    private EventSeverityUtils() {}

    public static String parseSeverity(String severity) {
        if (severity == null || severity.trim().isEmpty()) {
            return "MEDIUM";
        }

        String normalized = severity.trim().toUpperCase();

        switch (normalized) {
            case "HIGH":
            case "CRITICAL":
            case "MEDIUM":
            case "LOW":
            case "INFO":
                return normalized;
            default:
                throw new IllegalArgumentException("Invalid severity level: " + severity);
        }
    }

    public static boolean isValidSeverity(String severity) {
        if (severity == null || severity.trim().isEmpty()) {
            return false;
        }

        String normalized = severity.trim().toUpperCase();

        return normalized.equals("HIGH") ||
               normalized.equals("CRITICAL") ||
               normalized.equals("MEDIUM") ||
               normalized.equals("LOW") ||
               normalized.equals("INFO");
    }
}