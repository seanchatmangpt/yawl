package org.yawlfoundation.yawl.datamodelling;

/**
 * Thrown when a data-modelling operation fails (native library returns an error string).
 */
public class DataModellingException extends RuntimeException {
    public DataModellingException(String message) {
        super(message);
    }

    public DataModellingException(String message, Throwable cause) {
        super(message, cause);
    }
}
