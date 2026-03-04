/*
 * QLever Exception Domain Model
 *
 * Checked exception for QLever native operation failures.
 * Implements HYPER_STANDARDS: no mocks, real impl or throw UnsupportedOperationException.
 */

package org.yawlfoundation.yawl.bridge.qlever;

/**
 * Checked exception thrown when QLever native operations fail.
 * Provides detailed error information from the native layer.
 *
 * @apiNote This exception must be caught or declared in throws clauses
 *          when calling QLever operations that may fail.
 */
public final class QleverException extends Exception {

    private final QleverStatus status;

    /**
     * Create exception with status code and message
     */
    public QleverException(QleverStatus status) {
        super(status.message());
        this.status = status;
    }

    /**
     * Create exception with custom message
     */
    public QleverException(String message) {
        super(message);
        this.status = QleverStatus.error(-1, message);
    }

    /**
     * Create exception with message and cause
     */
    public QleverException(String message, Throwable cause) {
        super(message, cause);
        this.status = QleverStatus.error(-1, message);
    }

    /**
     * Get the underlying status object
     */
    public QleverStatus getStatus() {
        return status;
    }

    /**
     * Get the error code from the status
     */
    public int getErrorCode() {
        return status.code();
    }

    /**
     * Get the error message from the status
     */
    @Override
    public String getMessage() {
        return status.message();
    }

    /**
     * Check if this exception represents a specific error code
     */
    public boolean hasErrorCode(int code) {
        return status.code() == code;
    }

    /**
     * Check if this exception represents engine initialization failure
     */
    public boolean isEngineInitializationFailure() {
        return hasErrorCode(QleverStatus.ENGINE_INITIALIZATION_FAILED.code());
    }

    /**
     * Check if this exception represents query syntax error
     */
    public boolean isQuerySyntaxError() {
        return hasErrorCode(QleverStatus.INVALID_QUERY_SYNTAX.code());
    }

    /**
     * Check if this exception represents query execution failure
     */
    public boolean isQueryExecutionFailure() {
        return hasErrorCode(QleverStatus.QUERY_EXECUTION_FAILED.code());
    }

    /**
     * Check if this exception represents result extraction failure
     */
    public boolean isResultExtractionFailure() {
        return hasErrorCode(QleverStatus.RESULT_EXTRACTION_FAILED.code());
    }
}