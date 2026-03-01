package org.yawlfoundation.yawl.datamodelling;

/**
 * Thrown when ODPS validation fails (dm_validate_odps returns a non-null error).
 */
public class OdpsValidationException extends DataModellingException {
    public OdpsValidationException(String message) {
        super(message);
    }

    public OdpsValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
