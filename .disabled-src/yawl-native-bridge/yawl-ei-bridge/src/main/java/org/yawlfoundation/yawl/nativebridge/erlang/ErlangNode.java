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

// These imports require jextract-generated code
// import org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_cnode_t;
// import org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_buff_t;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages Erlang node connection and RPC operations.
 * This class provides a type-safe bridge between Java and Erlang nodes
 * using the erl_interface library.
 */
public class ErlangNode implements AutoCloseable {

    private final String nodeName;
    private final String cookie;
    private final short creation;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private ei_cnode_t cnode;
    private int connectionFd = -1;

    /**
     * Creates a new ErlangNode instance.
     *
     * @param nodeName The Erlang node name (e.g., "yawl@localhost")
     * @param cookie The connection cookie
     * @param creation The creation number (use 1-3)
     */
    public ErlangNode(String nodeName, String cookie, short creation) {
        if (nodeName == null || nodeName.trim().isEmpty()) {
            throw ErlangException.invalidArgument("node name");
        }
        if (cookie == null || cookie.trim().isEmpty()) {
            throw ErlangException.invalidArgument("cookie");
        }
        if (creation < 1 || creation > 3) {
            throw ErlangException.invalidArgument("creation number (1-3)");
        }

        this.nodeName = nodeName.trim();
        this.cookie = cookie.trim();
        this.creation = creation;
    }

    /**
     * Creates an ErlangNode with default creation number 1.
     *
     * @param nodeName The Erlang node name
     * @param cookie The connection cookie
     */
    public ErlangNode(String nodeName, String cookie) {
        this(nodeName, cookie, (short) 1);
    }

    /**
     * Initializes the Erlang node connection.
     * Must be called before any RPC operations.
     *
     * @throws ErlangException if initialization fails
     */
    public void initialize() throws ErlangException {
        if (closed.get()) {
            throw ErlangException.connectionClosed();
        }

        cnode = new ei_cnode_t();
        int result = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_connect_init(cnode, nodeName, cookie, creation);

        if (result == -1) {
            throw new ErlangException("Failed to initialize Erlang node: " + nodeName);
        }
    }

    /**
     * Connects to the Erlang node using Unix domain socket.
     *
     * @throws ErlangException if connection fails
     */
    public void connect() throws ErlangException {
        if (closed.get()) {
            throw ErlangException.connectionClosed();
        }

        if (cnode == null) {
            initialize();
        }

        // Connect via Unix domain socket (local protocol)
        connectionFd = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_connect(cnode, nodeName);

        if (connectionFd == -1) {
            throw ErlangException.connectionFailed(nodeName);
        }

        connected.set(true);
    }

    /**
     * Executes an RPC call to the Erlang node.
     *
     * @param module The Erlang module name
     * @param function The Erlang function name
     * @param arguments The arguments as an Erlang list
     * @return The result as an Erlang term
     * @throws ErlangException if RPC fails
     */
    public ErlTerm rpc(String module, String function, ErlList arguments) throws ErlangException {
        if (closed.get()) {
            throw ErlangException.connectionClosed();
        }

        if (!connected.get()) {
            throw ErlangException.connectionFailed(nodeName);
        }

        try {
            // Encode arguments - requires jextract-generated code
            // ei_x_buff_t argBuffer = new ei_x_buff_t();
            // org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_new_with_size(argBuffer, 1024);

            // int encodeResult = arguments.encodeTo(argBuffer);
            // if (encodeResult == -1) {
            //     throw ErlangException.encodingFailed(arguments);
            // }

            // Execute RPC - requires jextract-generated code
            // ei_x_buff_t resultBuffer = new ei_x_buff_t();
            // org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_new_with_size(resultBuffer, 1024);

            // int rpcResult = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_rpc(
            //     cnode, connectionFd, module, function,
            //     argBuffer.buff, argBuffer.index, resultBuffer
            // );

            // org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_free(argBuffer);

            throw new UnsupportedOperationException(
                "RPC functionality requires jextract-generated native code. " +
                "See IMPLEMENTATION_SUMMARY.md for build instructions."
            );

            if (rpcResult == -1) {
                throw ErlangException.rpcFailed(module, function, "ei_rpc failed");
            }

            // Decode result
            ErlTerm decodedResult = decodeResult(resultBuffer);
            // Native call requires jextract-generated code
            // org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_free(resultBuffer);

            return decodedResult;

        } catch (Exception e) {
            throw new ErlangException("RPC failed: " + module + ":" + function, e);
        }
    }

    /**
     * Executes an RPC call with timeout.
     *
     * @param module The Erlang module name
     * @param function The Erlang function name
     * @param arguments The arguments as an Erlang list
     * @param timeoutMs Timeout in milliseconds
     * @return The result as an Erlang term
     * @throws ErlangException if RPC times out or fails
     */
    public ErlTerm rpcWithTimeout(String module, String function, ErlList arguments, long timeoutMs) throws ErlangException {
        if (timeoutMs <= 0) {
            throw ErlangException.invalidArgument("timeout must be positive");
        }

        long startTime = System.currentTimeMillis();

        try {
            ErlTerm result = rpc(module, function, arguments);

            if (System.currentTimeMillis() - startTime > timeoutMs) {
                throw ErlangException.timeout("RPC", timeoutMs);
            }

            return result;
        } catch (ErlangException e) {
            if (e.getMessage().contains("timed out")) {
                throw e;
            }
            throw new ErlangException("RPC with timeout failed: " + module + ":" + function, e);
        }
    }

    /**
     * Decodes the RPC result buffer.
     *
     * NOTE: This method requires jextract-generated code and is disabled for compilation.
     *
     * @param buffer The result buffer
     * @return The decoded Erlang term
     * @throws ErlangException if decoding fails
     */
    private ErlTerm decodeResult(ei_x_buff_t buffer) throws ErlangException {
        throw new UnsupportedOperationException(
            "decodeResult requires jextract-generated native code. " +
            "See IMPLEMENTATION_SUMMARY.md for build instructions."
        );
        /*
        // The following code requires jextract-generated bindings:
        int termType = 0;
        int index = 0;

        int decodeResult = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_decode_ei_term(buffer, &termType, &index);
        if (decodeResult == -1) {
            throw ErlangException.decodingFailed("unknown");
        }

        switch (termType) {
            case org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_type_t.ERL_ATOM:
                char[] atomBuffer = new char[256];
                decodeResult = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_decode_atom(buffer, &termType, &index, atomBuffer);
                if (decodeResult == -1) {
                    throw ErlangException.decodingFailed("atom");
                }
                return ErlAtom.atom(new String(atomBuffer));

            case org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_type_t.ERL_INTEGER:
            case org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_type_t.ERL_SMALL_INTEGER:
                int intResult = 0;
                decodeResult = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_decode_int(buffer, &termType, &index, &intResult);
                if (decodeResult == -1) {
                    throw ErlangException.decodingFailed("integer");
                }
                return ErlLong.longValue(intResult);

            case org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_type_t.ERL_STRING:
                String stringResult = "";
                int len = 0;
                decodeResult = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_decode_string(buffer, &termType, &index, stringResult, &len);
                if (decodeResult == -1) {
                    throw ErlangException.decodingFailed("string");
                }
                return ErlBinary.fromString(stringResult);

            case org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_type_t.ERL_BINARY:
                char[] binaryData = null;
                int binaryLen = 0;
                decodeResult = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_decode_binary(buffer, &termType, &index, &binaryData, &binaryLen);
                if (decodeResult == -1) {
                    throw ErlangException.decodingFailed("binary");
                }
                return new ErlBinary(binaryData);

            case org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_type_t.ERL_SMALL_TUPLE:
            case org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_type_t.ERL_LARGE_TUPLE:
                // Simplified tuple handling - would need more complex decoding
                return ErlTuple.of(new ErlTerm[]{ErlAtom.atom("tuple")});

            case org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_type_t.ERL_LIST:
                // Simplified list handling - would need more complex decoding
                return ErlList.empty();

            default:
                throw ErlangException.decodingFailed("unknown term type: " + termType);
        }
        */
    }

    /**
     * Checks if the node is connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected.get() && !closed.get();
    }

    /**
     * Checks if the connection is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Gets the node name.
     *
     * @return The node name
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Gets the connection cookie.
     *
     * @return The cookie
     */
    public String getCookie() {
        return cookie;
    }

    @Override
    public void close() throws ErlangException {
        if (closed.compareAndSet(false, true)) {
            try {
                if (connectionFd != -1) {
                    // Native call requires jextract-generated code
                    // org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_close_connection(connectionFd);
                    connectionFd = -1;
                }
                connected.set(false);
            } catch (Exception e) {
                throw new ErlangException("Failed to close connection", e);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErlangNode that = (ErlangNode) o;
        return creation == that.creation &&
                Objects.equals(nodeName, that.nodeName) &&
                Objects.equals(cookie, that.cookie);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeName, cookie, creation);
    }

    @Override
    public String toString() {
        return "ErlangNode{" +
                "nodeName='" + nodeName + '\'' +
                ", connected=" + connected.get() +
                ", closed=" + closed.get() +
                '}';
    }

    /**
     * Factory method to create an ErlangNode with Unix domain socket path.
     *
     * @param nodeName The Erlang node name
     * @param cookie The connection cookie
     * @param socketPath The Unix domain socket path
     * @return A new ErlangNode instance
     */
    public static ErlangNode createWithSocket(String nodeName, String cookie, Path socketPath) {
        // Unix domain socket would require additional setup in ei.h
        // For now, just create a regular node
        return new ErlangNode(nodeName, cookie);
    }
}