package org.yawlfoundation.yawl.integration;

/**
 * Thrown when a GDPR right-to-erasure operation fails.
 * @since YAWL 6.0
 */
public class GdprErasureException extends Exception {
    public GdprErasureException(String message) { super(message); }
    public GdprErasureException(String message, Throwable cause) { super(message, cause); }
}
