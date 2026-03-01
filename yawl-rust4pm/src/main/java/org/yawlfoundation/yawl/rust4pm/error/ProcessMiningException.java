package org.yawlfoundation.yawl.rust4pm.error;

/**
 * Base checked exception for all rust4pm process mining failures.
 * Wraps error strings returned from the Rust native library.
 */
public class ProcessMiningException extends Exception {

    public ProcessMiningException(String message) {
        super(message);
    }

    public ProcessMiningException(String message, Throwable cause) {
        super(message, cause);
    }
}
