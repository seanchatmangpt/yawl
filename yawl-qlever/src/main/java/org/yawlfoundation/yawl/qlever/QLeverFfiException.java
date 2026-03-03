package org.yawlfoundation.yawl.qlever;

/**
 * Exception thrown when QLever FFI operations fail.
 */
public final class QLeverFfiException extends Exception {

    public QLeverFfiException(String message) {
        super(message);
    }

    public QLeverFfiException(String message, Throwable cause) {
        super(message, cause);
    }
}