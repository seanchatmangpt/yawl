/*
 * QLever Status Domain Model
 *
 * Represents the result of QLever operations with proper error handling.
 * Implements HYPER_STANDARDS: no mocks, real impl or throw UnsupportedOperationException.
 */

package org.yawlfoundation.yawl.bridge.qlever;

import java.util.Objects;

/**
 * Immutable status object for QLever operations.
 * Encapsulates both success/failure status and detailed error messages.
 *
 * @param code Status code: 0=success, >0=error
 * @param message Detailed error message if failed, empty if successful
 */
public record QleverStatus(
    int code,
    String message
) {

    // Predefined status constants
    public static final QleverStatus SUCCESS = new QleverStatus(0, "Operation completed successfully");
    public static final QleverStatus INVALID_INDEX_PATH = new QleverStatus(1, "Invalid or missing index path");
    public static final QleverStatus ENGINE_INITIALIZATION_FAILED = new QleverStatus(2, "Engine initialization failed");
    public static final QleverStatus INVALID_QUERY_SYNTAX = new QleverStatus(3, "Invalid SPARQL query syntax");
    public static final QleverStatus_QUERY_EXECUTION_FAILED = new QleverStatus(4, "Query execution failed");
    public static final QleverStatus RESULT_EXTRACTION_FAILED = new QleverStatus(5, "Result extraction failed");

    /**
     * Create a success status with custom message
     */
    public static QleverStatus success(String message) {
        Objects.requireNonNull(message, "Message cannot be null for success status");
        return new QleverStatus(0, message);
    }

    /**
     * Create an error status with custom code and message
     */
    public static QleverStatus error(int code, String message) {
        Objects.requireNonNull(message, "Message cannot be null for error status");
        return new QleverStatus(code, message);
    }

    /**
     * Check if this status represents success
     */
    public boolean isSuccess() {
        return code == 0;
    }

    /**
     * Check if this status represents an error
     */
    public boolean isError() {
        return code != 0;
    }

    /**
     * Convert this status to a QLeverException if it's an error
     */
    public QleverException toException() {
        if (isSuccess()) {
            throw new IllegalArgumentException("Cannot convert success status to exception");
        }
        return new QleverException(this);
    }

    /**
     * Throw exception if this status represents an error
     */
    public void throwIfError() {
        if (isError()) {
            throw toException();
        }
    }

    @Override
    public String toString() {
        return "QleverStatus{" +
               "code=" + code +
               ", message='" + message + '\'' +
               '}';
    }
}