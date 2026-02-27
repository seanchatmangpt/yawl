package org.yawlfoundation.yawl.integration.util;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility for validating skill and tool parameters.
 * Provides consistent validation across all A2A skills and MCP tools.
 */
public final class ParameterValidator {

    private ParameterValidator() {} // Utility class

    /** Validate required parameter is not null or blank */
    public static String validateRequired(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter '" + key + "' is missing");
        }
        String strValue = value.toString();
        if (strValue.isBlank()) {
            throw new IllegalArgumentException("Required parameter '" + key + "' cannot be blank");
        }
        return strValue;
    }

    /** Validate required parameter with custom error message */
    public static String validateRequired(Map<String, Object> args, String key, String errorMessage) {
        Object value = args.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.toString();
    }

    /** Get optional parameter with default value */
    public static String getOptional(Map<String, Object> args, String key, String defaultValue) {
        Object value = args.get(key);
        return (value == null || value.toString().isBlank()) ? defaultValue : value.toString();
    }

    /** Validate parameter matches pattern */
    public static void validateFormat(String value, Pattern pattern, String paramName) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Parameter '" + paramName + "' has invalid format: " + value
            );
        }
    }

    /** Validate parameter is in allowed set */
    public static <T> void validateInSet(T value, Set<T> allowed, String paramName) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(
                "Parameter '" + paramName + "' must be one of: " + allowed +
                ", but was: " + value
            );
        }
    }

    /** Validate size limit */
    public static void validateSize(String data, String paramName, int maxSizeBytes) {
        if (data != null && data.getBytes().length > maxSizeBytes) {
            throw new IllegalArgumentException(
                paramName + " exceeds maximum size of " + (maxSizeBytes / 1024 / 1024) + "MB"
            );
        }
    }

    /** Validate not null */
    public static <T> T validateNotNull(T value, String paramName) {
        return Objects.requireNonNull(value, paramName + " must not be null");
    }

    /** Check if string is null or blank */
    public static boolean isNullOrBlank(String value) {
        return value == null || value.isBlank();
    }
}