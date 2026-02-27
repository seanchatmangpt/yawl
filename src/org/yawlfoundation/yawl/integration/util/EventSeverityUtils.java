package org.yawlfoundation.yawl.integration.util;

import java.util.Set;

/**
 * Utility class for parsing and validating event severity values.
 *
 * Provides functionality to parse severity strings, validate them, and manage
 * the predefined severity levels used across A2A skills and MCP tools.
 *
 * @since YAWL v6.0.0
 */
public final class EventSeverityUtils {

    /**
     * The set of valid severity values, case-sensitive after normalization.
     */
    public static final Set<String> VALID_SEVERITIES =
        Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    /**
     * The default severity to use when input is null, empty, or invalid.
     */
    public static final String DEFAULT_SEVERITY = "MEDIUM";

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private EventSeverityUtils() {}

    /**
     * Parses a severity string, normalizing it and validating it against
     * the predefined severity levels.
     *
     * @param severityStr the severity string to parse (case-insensitive)
     * @return the normalized severity string (uppercase)
     * @throws IllegalArgumentException if the severity string is invalid
     */
    public static String parseSeverity(String severityStr) {
        if (severityStr == null || severityStr.isBlank()) {
            return DEFAULT_SEVERITY;
        }
        String normalized = severityStr.trim().toUpperCase();
        if (!VALID_SEVERITIES.contains(normalized)) {
            throw new IllegalArgumentException(
                "Invalid severity: " + severityStr +
                ". Valid values are: " + VALID_SEVERITIES
            );
        }
        return normalized;
    }

    /**
     * Checks if a string represents a valid severity value.
     *
     * @param severityStr the string to validate
     * @return true if the string is a valid severity, false otherwise
     */
    public static boolean isValidSeverity(String severityStr) {
        if (severityStr == null || severityStr.isBlank()) {
            return false;
        }
        String normalized = severityStr.trim().toUpperCase();
        return VALID_SEVERITIES.contains(normalized);
    }

    /**
     * Returns the default severity value.
     *
     * @return "MEDIUM" as the default severity
     */
    public static String defaultSeverity() {
        return DEFAULT_SEVERITY;
    }

    /**
     * Returns the set of all valid severity values.
     *
     * @return an immutable set containing "LOW", "MEDIUM", "HIGH", "CRITICAL"
     */
    public static Set<String> allSeverities() {
        return VALID_SEVERITIES;
    }
}