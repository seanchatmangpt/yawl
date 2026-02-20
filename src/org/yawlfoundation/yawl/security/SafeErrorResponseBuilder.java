/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Builds safe error responses by removing sensitive data before returning to clients.
 *
 * This utility prevents information disclosure attacks by:
 * - Removing file paths and system information
 * - Sanitizing stack traces
 * - Redacting credentials and API keys
 * - Normalizing error messages to generic descriptions
 *
 * Sensitive data is logged privately for debugging while clients receive safe, generic messages.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class SafeErrorResponseBuilder {

    private static final Logger log = LogManager.getLogger(SafeErrorResponseBuilder.class);

    // Patterns for detecting sensitive data
    private static final Pattern SQL_PATTERN = Pattern.compile(
            "(password|secret|api[_-]?key|token|credential)\\s*[=:]\\s*['\"]?[^\\s'\"]*['\"]?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "(/[a-zA-Z0-9._-]+)+|[a-zA-Z]:\\\\[^\\s]+"
    );

    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
            "at\\s+[a-zA-Z0-9.$_]+\\([^)]*\\)"
    );

    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"
    );

    /**
     * Record representing a safe error response.
     *
     * @param statusCode HTTP status code (e.g., 400, 429, 500)
     * @param message safe, user-facing error message
     * @param requestId unique request identifier for support team tracking
     * @param timestamp ISO 8601 timestamp of error occurrence
     */
    public record SafeErrorResponse(
            int statusCode,
            String message,
            String requestId,
            String timestamp
    ) {
        public SafeErrorResponse {
            Objects.requireNonNull(message, "message cannot be null");
            Objects.requireNonNull(requestId, "requestId cannot be null");
            Objects.requireNonNull(timestamp, "timestamp cannot be null");
            if (statusCode < 100 || statusCode >= 600) {
                throw new IllegalArgumentException("Invalid HTTP status code: " + statusCode);
            }
        }

        /**
         * Returns this error response as JSON string.
         *
         * @return JSON representation suitable for HTTP response body
         */
        public String toJson() {
            return String.format(
                    "{\"error\":\"%s\",\"status\":%d,\"requestId\":\"%s\",\"timestamp\":\"%s\"}",
                    escapeJson(message), statusCode, requestId, timestamp
            );
        }

        /**
         * Returns this error response as XML string.
         *
         * @return XML representation suitable for HTTP response body
         */
        public String toXml() {
            return String.format(
                    "<error><status>%d</status><message>%s</message><requestId>%s</requestId><timestamp>%s</timestamp></error>",
                    statusCode, escapeXml(message), requestId, timestamp
            );
        }

        private static String escapeJson(String s) {
            Objects.requireNonNull(s, "string to escape cannot be null");
            return s.replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        private static String escapeXml(String s) {
            Objects.requireNonNull(s, "string to escape cannot be null");
            return s.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
        }
    }

    private SafeErrorResponseBuilder() {
        // Utility class - no instances
    }

    /**
     * Builds a safe error response from an exception.
     *
     * Logs the full exception details privately while returning a generic message to the client.
     *
     * @param statusCode HTTP status code
     * @param exception the exception that occurred
     * @param requestId unique request identifier
     * @return a SafeErrorResponse suitable for returning to client
     */
    public static SafeErrorResponse fromException(int statusCode, Throwable exception, String requestId) {
        Objects.requireNonNull(exception, "exception cannot be null");
        Objects.requireNonNull(requestId, "requestId cannot be null");

        // Log full details privately for debugging
        log.error("Exception [requestId={}]: {} - {}",
                requestId, exception.getClass().getSimpleName(), exception.getMessage(),
                exception);

        String safeMessage = getSafeMessage(statusCode);

        return new SafeErrorResponse(
                statusCode,
                safeMessage,
                requestId,
                java.time.Instant.now().toString()
        );
    }

    /**
     * Builds a safe error response from a custom message.
     *
     * Sanitizes the provided message before returning to client.
     *
     * @param statusCode HTTP status code
     * @param message custom error message (may contain sensitive data)
     * @param requestId unique request identifier
     * @return a SafeErrorResponse with sanitized message
     */
    public static SafeErrorResponse fromMessage(int statusCode, String message, String requestId) {
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(requestId, "requestId cannot be null");

        // Log original message privately
        log.warn("Error [requestId={}]: {}", requestId, message);

        String sanitized = sanitizeMessage(message);
        // If sanitization removes all content, use safe default message
        String safeMessage = sanitized.isBlank() ? getSafeMessage(statusCode) : sanitized;

        return new SafeErrorResponse(
                statusCode,
                safeMessage,
                requestId,
                java.time.Instant.now().toString()
        );
    }

    /**
     * Builds a rate limit exceeded error response.
     *
     * @param clientId the client that hit the rate limit
     * @param requestId unique request identifier
     * @return a SafeErrorResponse with 429 status code
     */
    public static SafeErrorResponse rateLimitExceeded(String clientId, String requestId) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        Objects.requireNonNull(requestId, "requestId cannot be null");

        log.warn("Rate limit exceeded for client: {} [requestId={}]", clientId, requestId);

        return new SafeErrorResponse(
                429,
                "Rate limit exceeded. Please try again later.",
                requestId,
                java.time.Instant.now().toString()
        );
    }

    /**
     * Builds an input validation error response.
     *
     * @param fieldName the field that failed validation
     * @param reason the validation error reason
     * @param requestId unique request identifier
     * @return a SafeErrorResponse with 400 status code
     */
    public static SafeErrorResponse validationError(String fieldName, String reason, String requestId) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(requestId, "requestId cannot be null");

        log.warn("Validation error for field '{}': {} [requestId={}]", fieldName, reason, requestId);

        return new SafeErrorResponse(
                400,
                "Request validation failed. Please check your input.",
                requestId,
                java.time.Instant.now().toString()
        );
    }

    /**
     * Sanitizes a message by removing sensitive data patterns.
     *
     * Removes credentials, file paths, IP addresses, and stack traces.
     *
     * @param message the message to sanitize (must not be null)
     * @return the sanitized message with safe content, never null
     * @throws IllegalArgumentException if message is null
     */
    public static String sanitizeMessage(String message) {
        Objects.requireNonNull(message, "message cannot be null");

        String sanitized = message;

        // Remove SQL injection patterns and credentials
        sanitized = SQL_PATTERN.matcher(sanitized).replaceAll("[REDACTED]");

        // Remove file paths
        sanitized = FILE_PATH_PATTERN.matcher(sanitized).replaceAll("[PATH]");

        // Remove stack traces
        sanitized = STACK_TRACE_PATTERN.matcher(sanitized).replaceAll("[STACK]");

        // Remove IP addresses
        sanitized = IP_ADDRESS_PATTERN.matcher(sanitized).replaceAll("[IP]");

        // Limit message length to prevent information disclosure
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200) + "...";
        }

        return sanitized;
    }

    /**
     * Returns a safe, generic error message for the given status code.
     *
     * @param statusCode HTTP status code
     * @return a generic error message appropriate for the status code (never null or empty)
     */
    public static String getSafeMessage(int statusCode) {
        return switch (statusCode) {
            case 400 -> "Invalid request format";
            case 401 -> "Unauthorized access";
            case 403 -> "Access forbidden";
            case 404 -> "Resource not found";
            case 409 -> "Conflict in resource state";
            case 429 -> "Rate limit exceeded";
            case 500 -> "Internal server error";
            case 502 -> "Service gateway error";
            case 503 -> "Service temporarily unavailable";
            case 504 -> "Service timeout";
            default -> "An error occurred";
        };
    }

    /**
     * Logs an error with context without exposing sensitive data to the client.
     *
     * @param requestId unique request identifier
     * @param exception the exception to log
     * @param additionalContext any additional context for debugging (may be null)
     */
    public static void logSecurely(String requestId, Throwable exception, String additionalContext) {
        Objects.requireNonNull(requestId, "requestId cannot be null");
        Objects.requireNonNull(exception, "exception cannot be null");

        String context = additionalContext != null ? additionalContext : "none";
        log.error("Secure error log [requestId={}]. Context: {}. Exception: {}",
                requestId, context,
                exception.getClass().getName(), exception);
    }
}
