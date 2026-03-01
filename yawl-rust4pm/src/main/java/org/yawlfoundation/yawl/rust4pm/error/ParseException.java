package org.yawlfoundation.yawl.rust4pm.error;

/**
 * Thrown when OCEL2 JSON parsing fails in the Rust library.
 * The message contains the serde_json error from Rust.
 */
public final class ParseException extends ProcessMiningException {

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
