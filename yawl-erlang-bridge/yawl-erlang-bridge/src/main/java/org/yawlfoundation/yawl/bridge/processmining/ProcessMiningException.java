package org.yawlfoundation.yawl.bridge.processmining;

/**
 * Exception thrown by the JNI process mining library.
 */
public class ProcessMiningException extends RuntimeException {

    public ProcessMiningException(String message) {
        super(message);
    }

    public ProcessMiningException(String message, Throwable cause) {
        super(message, cause);
    }
}