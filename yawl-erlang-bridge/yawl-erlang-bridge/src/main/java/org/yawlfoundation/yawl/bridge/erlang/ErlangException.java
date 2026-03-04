package org.yawlfoundation.yawl.bridge.erlang;

import java.io.IOException;

/**
 * Checked exception representing Erlang protocol errors in the YAWL bridge.
 *
 * <p>This exception wraps various Erlang protocol errors including:
 * - {badrpc, Reason} from RPC calls
 * - Connection failures
 * - Encoding/ decoding errors
 * - Timeout errors</p>
 *
 * @since 1.0.0
 */
public final class ErlangException extends Exception {

    private final String erlangModule;
    private final String erlangFunction;
    private final ErlangErrorDetails errorDetails;

    /**
     * Constructs an ErlangException with a descriptive message.
     *
     * @param message The detail message
     */
    public ErlangException(String message) {
        super(message);
        this.erlangModule = null;
        this.erlangFunction = null;
        this.errorDetails = null;
    }

    /**
     * Constructs an ErlangException with a message and cause.
     *
     * @param message The detail message
     * @param cause The cause of the exception
     */
    public ErlangException(String message, Throwable cause) {
        super(message, cause);
        this.erlangModule = null;
        this.erlangFunction = null;
        this.errorDetails = null;
    }

    /**
     * Constructs an ErlangException for a specific {badrpc, Reason} error.
     *
     * @param module The module where the error occurred
     * @param function The function where the error occurred
     * @param erlangDetails Details of the Erlang error
     */
    public ErlangException(String module, String function, ErlangErrorDetails erlangDetails) {
        super(createBadRpcMessage(module, function, erlangDetails));
        this.erlangModule = module;
        this.erlangFunction = function;
        this.errorDetails = erlangDetails;
    }

    /**
     * Constructs an ErlangException for connection failures.
     *
     * @param message Connection error message
     * @param cause The underlying IO exception
     */
    public ErlangException(String message, IOException cause) {
        super(message, cause);
        this.erlangModule = null;
        this.erlangFunction = null;
        this.errorDetails = null;
    }

    private static String createBadRpcMessage(String module, String function, ErlangErrorDetails details) {
        return String.format("RPC call to %s:%s failed: %s",
                             module, function, details.getMessage());
    }

    /**
     * Returns the module where the error occurred, if available.
     *
     * @return The module name or null
     */
    public String getErlangModule() {
        return erlangModule;
    }

    /**
     * Returns the function where the error occurred, if available.
     *
     * @return The function name or null
     */
    public String getErlangFunction() {
        return erlangFunction;
    }

    /**
     * Returns the detailed error information from Erlang, if available.
     *
     * @return The error details or null
     */
    public ErlangErrorDetails getErrorDetails() {
        return errorDetails;
    }

    /**
     * Returns whether this exception represents a badrpc error.
     *
     * @return true if this is a badrpc error
     */
    public boolean isBadRpc() {
        return errorDetails != null;
    }

    /**
     * Error details for Erlang errors.
     */
    public static final class ErlangErrorDetails {
        private final String reason;
        private final ErlTerm errorTerm;

        public ErlangErrorDetails(String reason) {
            this(reason, null);
        }

        public ErlangErrorDetails(String reason, ErlTerm errorTerm) {
            this.reason = reason;
            this.errorTerm = errorTerm;
        }

        public String getReason() {
            return reason;
        }

        public String getMessage() {
            return errorTerm != null ? errorTerm.asString() : reason;
        }

        public ErlTerm getErrorTerm() {
            return errorTerm;
        }
    }
}