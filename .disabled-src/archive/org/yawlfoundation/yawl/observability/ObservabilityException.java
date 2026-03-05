package org.yawlfoundation.yawl.observability;

/**
 * Thrown when observability infrastructure operations fail.
 */
public class ObservabilityException extends RuntimeException {

    public ObservabilityException(String message) {
        super(message);
    }

    public ObservabilityException(String message, Throwable cause) {
        super(message, cause);
    }

    public ObservabilityException(Throwable cause) {
        super(cause);
    }
}
