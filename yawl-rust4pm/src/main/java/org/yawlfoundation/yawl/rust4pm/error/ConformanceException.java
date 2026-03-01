package org.yawlfoundation.yawl.rust4pm.error;

/**
 * Thrown when token-based replay conformance checking fails.
 * Common causes: invalid PNML, unreachable Petri net states.
 */
public final class ConformanceException extends ProcessMiningException {

    public ConformanceException(String message) {
        super(message);
    }

    public ConformanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
