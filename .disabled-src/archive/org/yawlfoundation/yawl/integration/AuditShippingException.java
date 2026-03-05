package org.yawlfoundation.yawl.integration;

/**
 * Thrown when an audit log entry cannot be shipped to an external sink.
 * @since YAWL 6.0
 */
public class AuditShippingException extends Exception {
    public AuditShippingException(String message) { super(message); }
    public AuditShippingException(String message, Throwable cause) { super(message, cause); }
}
