/**
 * QLever Exception - Checked Exception for QLever Operations
 *
 * This exception is thrown when QLever operations fail. It wraps the status
 * information that provides details about the failure.
 */
public class QleverException extends Exception {

    private final QleverStatus status;

    /**
     * Creates a new QLever exception with the given status
     */
    public QleverException(QleverStatus status) {
        super(status != null ? status.getDetailedMessage() : "QLever operation failed");
        this.status = status != null ? status :
            new QleverStatus(QleverStatus.ERROR_INVALID_ARGUMENT, "Null status");
    }

    /**
     * Creates a new QLever exception with a custom message and status
     */
    public QleverException(String message, QleverStatus status) {
        super(message);
        this.status = status != null ? status :
            new QleverStatus(QleverStatus.ERROR_INVALID_ARGUMENT, "Null status");
    }

    /**
     * Gets the status associated with this exception
     */
    public QleverStatus getStatus() {
        return status;
    }

    /**
     * Gets the error code from the status
     */
    public int getErrorCode() {
        return status.code();
    }

    /**
     * Gets the error message from the status
     */
    public String getErrorMessage() {
        return status.message();
    }

    /**
     * Checks if this exception represents a specific error type
     */
    public boolean isOfType(int errorCode) {
        return status.code() == errorCode;
    }

    /**
     * Checks if this exception is due to a query parse error
     */
    public boolean isQueryParseError() {
        return status.code() == QleverStatus.ERROR_QUERY_PARSE_FAILED;
    }

    /**
     * Checks if this exception is due to a query execution error
     */
    public boolean isQueryExecutionError() {
        return status.code() == QleverStatus.ERROR_QUERY_EXECUTION_FAILED;
    }

    /**
     * Checks if this exception is due to an engine initialization error
     */
    public boolean isEngineInitError() {
        return status.code() == QleverStatus.ERROR_ENGINE_INIT_FAILED;
    }

    /**
     * Checks if this exception is due to a memory allocation error
     */
    public boolean isMemoryAllocationError() {
        return status.code() == QleverStatus.ERROR_MEMORY_ALLOCATION_FAILED;
    }

    /**
     * Gets a user-friendly error message for display to end users
     */
    public String getUserFriendlyMessage() {
        return switch (status.code()) {
            case QleverStatus.ERROR_ENGINE_INIT_FAILED ->
                "Failed to initialize QLever engine. Please check the index path and configuration.";
            case QleverStatus.ERROR_QUERY_PARSE_FAILED ->
                "Invalid SPARQL query syntax. Please check your query and try again.";
            case QleverStatus.ERROR_QUERY_EXECUTION_FAILED ->
                "Failed to execute the SPARQL query. Please verify the query and data availability.";
            case QleverStatus.ERROR_MEMORY_ALLOCATION_FAILED ->
                "Memory allocation failed. The system may be under memory pressure.";
            case QleverStatus.ERROR_INVALID_ARGUMENT ->
                "Invalid argument provided to QLever operation.";
            case QleverStatus.ERROR_ENGINE_NOT_INITIALIZED ->
                "QLever engine is not initialized. Please call initialize() first.";
            case QleverStatus.ERROR_RESULT_NOT_AVAILABLE ->
                "Query result is not available. The query may have failed.";
            default ->
                "QLever operation failed: " + status.message();
        };
    }

    /**
     * Gets technical details for debugging
     */
    public String getTechnicalDetails() {
        return String.format(
            "QLever Exception: %s\n" +
            "Error Code: %d\n" +
            "Status: %s",
            getMessage(),
            status.code(),
            status.toLogString()
        );
    }

    @Override
    public String toString() {
        return getTechnicalDetails();
    }
}