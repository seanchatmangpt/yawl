/**
 * QLever Status - Native Bridge Layer 2
 *
 * Represents the result of QLever operations with error codes and messages.
 * This is a typed wrapper around the jextract-generated status struct.
 *
 * @see org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus
 */
public record QleverStatus(
    int code,
    String message
) {

    // Success status
    public static final QleverStatus SUCCESS = new QleverStatus(0, "Success");

    // Error code definitions
    public static final int ERROR_ENGINE_INIT_FAILED = 1;
    public static final int ERROR_QUERY_PARSE_FAILED = 2;
    public static final int ERROR_QUERY_EXECUTION_FAILED = 3;
    public static final int ERROR_MEMORY_ALLOCATION_FAILED = 4;
    public static final int ERROR_INVALID_ARGUMENT = 5;
    public static final int ERROR_ENGINE_NOT_INITIALIZED = 6;
    public static final int ERROR_RESULT_NOT_AVAILABLE = 7;

    /**
     * Creates a success status
     */
    public QleverStatus() {
        this(0, "Success");
    }

    /**
     * Creates an error status with the given code and message
     */
    public QleverStatus(int code, String message) {
        this.code = code;
        this.message = message != null ? message : "";
    }

    /**
     * Checks if the status indicates success
     */
    public boolean isSuccess() {
        return code == 0;
    }

    /**
     * Checks if the status indicates failure
     */
    public boolean isFailure() {
        return code != 0;
    }

    /**
     * Converts this status to a checked exception if it indicates failure
     */
    public QleverException toException() {
        if (isFailure()) {
            return new QleverException(this);
        }
        return null;
    }

    /**
     * Converts this status to a runtime exception if it indicates failure
     */
    public QleverException toRuntimeException() {
        if (isFailure()) {
            return new QleverRuntimeException(this);
        }
        return null;
    }

    /**
     * Creates a status from the jextract-generated status struct
     */
    public static QleverStatus fromJextract(
        org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus jextractStatus
    ) {
        if (jextractStatus == null) {
            return new QleverStatus(ERROR_INVALID_ARGUMENT, "Null status");
        }

        return new QleverStatus(
            jextractStatus.getCode(),
            jextractStatus.getMessage()
        );
    }

    /**
     * Converts this status to the jextract struct for passing to native code
     */
    public org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus toJextract() {
        return org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.error(code, message);
    }

    /**
     * Gets the error message with additional context
     */
    public String getDetailedMessage() {
        if (isSuccess()) {
            return "Operation succeeded";
        }
        return "Error " + code + ": " + message;
    }

    /**
     * Formats the status for logging
     */
    public String toLogString() {
        return String.format("QleverStatus[code=%d, message='%s']", code, message);
    }

    @Override
    public String toString() {
        return getDetailedMessage();
    }
}