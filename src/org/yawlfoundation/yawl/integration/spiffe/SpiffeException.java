package org.yawlfoundation.yawl.integration.spiffe;

/**
 * Exception for SPIFFE/SPIRE workload identity operations
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SpiffeException extends Exception {

    private static final long serialVersionUID = 1L;

    public SpiffeException(String message) {
        super(message);
    }

    public SpiffeException(String message, Throwable cause) {
        super(message, cause);
    }

    public SpiffeException(Throwable cause) {
        super(cause);
    }
}
