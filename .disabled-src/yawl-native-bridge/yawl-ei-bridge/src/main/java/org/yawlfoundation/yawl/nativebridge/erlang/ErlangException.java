/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.nativebridge.erlang;

/**
 * Checked exception wrapping Erlang communication errors.
 * This exception represents failures in the ei bridge operations,
 * including connection errors, encoding/decoding errors, and RPC failures.
 */
public class ErlangException extends Exception {

    /**
     * Creates a new ErlangException with the specified detail message.
     *
     * @param message The detail message
     */
    public ErlangException(String message) {
        super(message);
    }

    /**
     * Creates a new ErlangException with the specified detail message and cause.
     *
     * @param message The detail message
     * @param cause The cause of the exception
     */
    public ErlangException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new ErlangException with the specified cause.
     *
     * @param cause The cause of the exception
     */
    public ErlangException(Throwable cause) {
        super(cause);
    }

    /**
     * Factory method to create an exception for connection failures.
     *
     * @param node The node that failed to connect
     * @return A new ErlangException
     */
    public static ErlangException connectionFailed(String node) {
        return new ErlangException("Failed to connect to Erlang node: " + node);
    }

    /**
     * Factory method to create an exception for RPC failures.
     *
     * @param module The module where RPC failed
     * @param function The function that failed
     * @param reason The reason for failure
     * @return A new ErlangException
     */
    public static ErlangException rpcFailed(String module, String function, String reason) {
        return new ErlangException("RPC call failed: " + module + ":" + function + " - " + reason);
    }

    /**
     * Factory method to create an exception for encoding failures.
     *
     * @param term The term that failed to encode
     * @return A new ErlangException
     */
    public static ErlangException encodingFailed(ErlTerm term) {
        return new ErlangException("Failed to encode Erlang term: " + term);
    }

    /**
     * Factory method to create an exception for decoding failures.
     *
     * @param type The expected type
     * @return A new ErlangException
     */
    public static ErlangException decodingFailed(String type) {
        return new ErlangException("Failed to decode Erlang term, expected: " + type);
    }

    /**
     * Factory method to create an exception for timeout.
     *
     * @param operation The operation that timed out
     * @param timeoutMs The timeout in milliseconds
     * @return A new ErlangException
     */
    public static ErlangException timeout(String operation, long timeoutMs) {
        return new ErlangException("Operation timed out: " + operation + " after " + timeoutMs + "ms");
    }

    /**
     * Factory method to create an exception for invalid arguments.
     *
     * @param argument The invalid argument
     * @return A new ErlangException
     */
    public static ErlangException invalidArgument(String argument) {
        return new ErlangException("Invalid argument: " + argument);
    }

    /**
     * Factory method to create an exception for closed connection.
     *
     * @return A new ErlangException
     */
    public static ErlangException connectionClosed() {
        return new ErlangException("Connection to Erlang node is closed");
    }

    /**
     * Factory method to create an exception for protocol errors.
     *
     * @param protocol The protocol that failed
     * @return A new ErlangException
     */
    public static ErlangException protocolError(String protocol) {
        return new ErlangException("Protocol error in " + protocol);
    }
}