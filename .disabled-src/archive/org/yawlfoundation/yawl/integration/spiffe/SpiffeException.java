package org.yawlfoundation.yawl.integration.spiffe;

import java.io.Serial;

/**
 * Exception for SPIFFE/SPIRE workload identity operations.
 *
 * <p>This exception is thrown when SPIFFE-related operations fail, including:
 * <ul>
 *   <li>SVID fetch failures from SPIRE Agent</li>
 *   <li>Certificate validation errors</li>
 *   <li>Workload attestation failures</li>
 *   <li>Trust domain verification failures</li>
 *   <li>mTLS configuration errors</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @since 5.2
 */
public class SpiffeException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Error code indicating the SPIRE Agent is unreachable */
    public static final String ERR_AGENT_UNREACHABLE = "SPIFFE_AGENT_UNREACHABLE";

    /** Error code indicating SVID validation failed */
    public static final String ERR_SVID_INVALID = "SPIFFE_SVID_INVALID";

    /** Error code indicating trust domain mismatch */
    public static final String ERR_TRUST_DOMAIN = "SPIFFE_TRUST_DOMAIN_MISMATCH";

    /** Error code indicating certificate expiry */
    public static final String ERR_CERT_EXPIRED = "SPIFFE_CERT_EXPIRED";

    /** Error code indicating JWT validation failure */
    public static final String ERR_JWT_INVALID = "SPIFFE_JWT_INVALID";

    private final String errorCode;
    private final String spiffeId;

    /**
     * Creates a new SPIFFE exception with a message.
     *
     * @param message the error message
     */
    public SpiffeException(String message) {
        super(message);
        this.errorCode = null;
        this.spiffeId = null;
    }

    /**
     * Creates a new SPIFFE exception with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public SpiffeException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.spiffeId = null;
    }

    /**
     * Creates a new SPIFFE exception with a cause.
     *
     * @param cause the underlying cause
     */
    public SpiffeException(Throwable cause) {
        super(cause);
        this.errorCode = null;
        this.spiffeId = null;
    }

    /**
     * Creates a new SPIFFE exception with error code, message, and optional SPIFFE ID.
     *
     * @param errorCode the error code for programmatic handling
     * @param message the error message
     * @param spiffeId the related SPIFFE ID (may be null)
     */
    public SpiffeException(String errorCode, String message, String spiffeId) {
        super(message);
        this.errorCode = errorCode;
        this.spiffeId = spiffeId;
    }

    /**
     * Creates a new SPIFFE exception with all parameters.
     *
     * @param errorCode the error code for programmatic handling
     * @param message the error message
     * @param spiffeId the related SPIFFE ID (may be null)
     * @param cause the underlying cause
     */
    public SpiffeException(String errorCode, String message, String spiffeId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.spiffeId = spiffeId;
    }

    /**
     * Gets the error code for this exception.
     *
     * @return the error code, or null if not specified
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Gets the SPIFFE ID related to this exception.
     *
     * @return the SPIFFE ID, or null if not specified
     */
    public String getSpiffeId() {
        return spiffeId;
    }

    /**
     * Checks if this exception indicates the SPIRE Agent is unreachable.
     *
     * @return true if agent is unreachable
     */
    public boolean isAgentUnreachable() {
        return ERR_AGENT_UNREACHABLE.equals(errorCode);
    }

    /**
     * Checks if this exception indicates an expired certificate.
     *
     * @return true if certificate is expired
     */
    public boolean isCertExpired() {
        return ERR_CERT_EXPIRED.equals(errorCode);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SpiffeException");
        if (errorCode != null) {
            sb.append("[").append(errorCode).append("]");
        }
        sb.append(": ").append(getMessage());
        if (spiffeId != null) {
            sb.append(" (spiffeId=").append(spiffeId).append(")");
        }
        if (getCause() != null) {
            sb.append(" - caused by: ").append(getCause().getClass().getSimpleName())
              .append(": ").append(getCause().getMessage());
        }
        return sb.toString();
    }
}
