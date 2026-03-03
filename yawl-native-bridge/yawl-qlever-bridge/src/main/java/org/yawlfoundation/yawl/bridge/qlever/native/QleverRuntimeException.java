/**
 * QLever Runtime Exception - Unchecked Exception for QLever Operations
 *
 * This runtime exception is thrown when QLever operations fail in situations
 * where checked exceptions are not appropriate or desired.
 */
public class QleverRuntimeException extends RuntimeException {

    private final QleverStatus status;

    /**
     * Creates a new QLever runtime exception with the given status
     */
    public QleverRuntimeException(QleverStatus status) {
        super(status != null ? status.getDetailedMessage() : "QLever operation failed");
        this.status = status != null ? status :
            new QleverStatus(QleverStatus.ERROR_INVALID_ARGUMENT, "Null status");
    }

    /**
     * Creates a new QLever runtime exception with a custom message and status
     */
    public QleverRuntimeException(String message, QleverStatus status) {
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
     * Gets a user-friendly error message for display to end users
     */
    public String getUserFriendlyMessage() {
        return status.getUserFriendlyMessage();
    }
}