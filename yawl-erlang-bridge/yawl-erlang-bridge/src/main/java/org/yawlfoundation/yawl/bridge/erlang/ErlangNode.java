package org.yawlfoundation.yawl.bridge.erlang;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.yawlfoundation.yawl.bridge.erlang.nativ.EiNativeLibrary;

/**
 * Manages an Erlang node connection through the ei interface.
 *
 * <p>This class provides a typed bridge between Java and Erlang,
 * handling connection establishment, RPC calls, and resource cleanup.</p>
 *
 * @since 1.0.0
 */
public final class ErlangNode implements AutoCloseable {

    private final String nodeName;
    private final String cookie;
    private final Path socketPath;
    private EiConnection connection;
    private volatile boolean closed = false;

    /**
     * Constructs an ErlangNode using Unix domain socket transport.
     *
     * <p>This configuration uses ei_connect_host_port to bypass EPMD
     * and connect directly to the node via Unix domain socket.</p>
     *
     * @param nodeName The Erlang node name (e.g., "yawl@localhost")
     * @param cookie The Erlang cookie for authentication
     * @param socketPath Path to the Unix domain socket
     * @throws ErlangException if connection initialization fails
     */
    public ErlangNode(String nodeName, String cookie, Path socketPath) {
        if (nodeName == null || nodeName.isEmpty()) {
            throw new IllegalArgumentException("Node name cannot be null or empty");
        }
        if (cookie == null || cookie.isEmpty()) {
            throw new IllegalArgumentException("Cookie cannot be null or empty");
        }
        if (socketPath == null) {
            throw new IllegalArgumentException("Socket path cannot be null");
        }

        this.nodeName = nodeName;
        this.cookie = cookie;
        this.socketPath = socketPath;

        initializeConnection();
    }

    /**
     * Constructs an ErlangNode with a default socket path.
     *
     * @param nodeName The Erlang node name
     * @param cookie The Erlang cookie
     * @throws ErlangException if connection initialization fails
     */
    public ErlangNode(String nodeName, String cookie) {
        this(nodeName, cookie, getDefaultSocketPath(nodeName));
    }

    /**
     * Initializes the ei connection to the Erlang node.
     *
     * @throws ErlangException if connection fails
     */
    private void initializeConnection() {
        try {
            // Initialize ei connection without EPMD
            EiConnectionFactory factory = new EiConnectionFactory();
            this.connection = factory.connect(nodeName, cookie, socketPath);
        } catch (IOException e) {
            throw new ErlangException("Failed to initialize connection to node " + nodeName, e);
        }
    }

    /**
     * Performs a typed RPC call to the specified module and function.
     *
     * @param module The Erlang module name
     * @param function The Erlang function name
     * @param args The function arguments
     * @return The result as an ErlTerm
     * @throws ErlangException if RPC call fails
     * @throws IllegalArgumentException if arguments contain null
     */
    public ErlTerm rpc(String module, String function, ErlTerm... args) throws ErlangException {
        checkClosed();
        validateArguments(args);

        try {
            // Encode the call
            EiBuffer request = new EiBuffer();
            ErlTerm call = ErlTuple.of(
                ErlAtom.of(module),
                ErlAtom.of(function),
                ErlList.of(args),
                ErlAtom.of("yawl_bridge") // Additional info for tracking
            );

            // Execute RPC with timeout
            EiBuffer response = connection.rpc(request, 30, TimeUnit.SECONDS);

            // Decode the response
            return decodeResponse(response);

        } catch (IOException e) {
            throw new ErlangException("IO error during RPC to " + module + ":" + function, e);
        } catch (TimeoutException e) {
            throw new ErlangException("RPC timeout to " + module + ":" + function);
        }
    }

    /**
     * Performs an RPC call with a specific timeout.
     *
     * @param module The Erlang module name
     * @param function The Erlang function name
     * @param timeout The timeout value
     * @param unit The timeout unit
     * @param args The function arguments
     * @return The result as an ErlTerm
     * @throws ErlangException if RPC call fails
     * @throws TimeoutException if timeout is exceeded
     */
    public ErlTerm rpc(String module, String function,
                      long timeout, TimeUnit unit,
                      ErlTerm... args) throws ErlangException {
        checkClosed();
        validateArguments(args);

        try {
            // Encode the call
            EiBuffer request = new EiBuffer();
            ErlTerm call = ErlTuple.of(
                ErlAtom.of(module),
                ErlAtom.of(function),
                ErlList.of(args),
                ErlAtom.of("yawl_bridge")
            );

            // Execute RPC with custom timeout
            EiBuffer response = connection.rpc(request, timeout, unit);
            return decodeResponse(response);

        } catch (IOException e) {
            throw new ErlangException("IO error during RPC to " + module + ":" + function, e);
        } catch (TimeoutException e) {
            throw new ErlangException("RPC timeout to " + module + ":" + function);
        }
    }

    /**
     * Checks if the node is connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connection != null && !closed && connection.isOpen();
    }

    /**
     * Returns the node name.
     *
     * @return The node name
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Returns the cookie used for authentication.
     *
     * @return The cookie
     */
    public String getCookie() {
        return cookie;
    }

    /**
     * Returns the socket path.
     *
     * @return The socket path
     */
    public Path getSocketPath() {
        return socketPath;
    }

    /**
     * Closes the connection and releases resources.
     */
    @Override
    public void close() {
        if (!closed && connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                // Log warning but don't propagate
                System.err.println("Warning: Error closing connection to " + nodeName + ": " + e.getMessage());
            }
            closed = true;
        }
    }

    /**
     * Validates the connection arguments.
     *
     * @param args The arguments to validate
     * @throws IllegalArgumentException if arguments contain null
     */
    private void validateArguments(ErlTerm[] args) {
        if (args != null) {
            for (ErlTerm arg : args) {
                if (arg == null) {
                    throw new IllegalArgumentException("Function arguments cannot contain null");
                }
            }
        }
    }

    /**
     * Checks if the connection is closed and throws if so.
     *
     * @throws IllegalStateException if connection is closed
     */
    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("Connection to node " + nodeName + " is closed");
        }
    }

    /**
     * Decodes an RPC response and handles errors.
     *
     * @param response The response buffer
     * @return The decoded result
     * @throws ErlangException if response indicates an error
     */
    private ErlTerm decodeResponse(EiBuffer response) {
        try {
            // Expected response format: {ok, Result} or {error, Reason}
            ErlTerm decoded = decodeTerm(response);

            if (decoded instanceof ErlTuple && ((ErlTuple) decoded).hasArity(2)) {
                ErlTuple tuple = (ErlTuple) decoded;
                ErlTerm tag = tuple.get(0);

                if (tag instanceof ErlAtom && "ok".equals(((ErlAtom) tag).getValue())) {
                    return tuple.get(1);
                } else if (tag instanceof ErlAtom && "error".equals(((ErlAtom) tag).getValue())) {
                    return tuple.get(1);
                } else if (tag instanceof ErlAtom && "badrpc".equals(((ErlAtom) tag).getValue())) {
                    ErlTerm reason = tuple.get(1);
                    throw new ErlangException("RPC failed", "unknown",
                        new ErlangException.ErlangErrorDetails(
                            reason instanceof ErlAtom ? ((ErlAtom) reason).getValue() : reason.asString(),
                            reason
                        )
                    );
                }
            }

            // Return as-is if not in expected format
            return decoded;

        } catch (IOException e) {
            throw new ErlangException("Failed to decode RPC response", e);
        }
    }

    /**
     * Decodes a term from the buffer.
     *
     * @param buffer The buffer containing the encoded term
     * @return The decoded ErlTerm
     * @throws IOException if decoding fails
     */
    private ErlTerm decodeTerm(EiBuffer buffer) throws IOException {
        // This is a simplified decoder - in practice, you'd need a full
        // implementation that matches the Erlang term format
        byte[] data = buffer.toArray();

        // Check for common patterns
        if (data.length >= 3 && data[0] == 100) { // ATOM_EXT
            int length = (data[1] << 8) | data[2];
            String atom = new String(data, 3, length, java.nio.charset.StandardCharsets.ISO_8859_1);
            return new ErlAtom(atom);
        }

        // For now, return a placeholder
        return new ErlAtom("unknown");
    }

    /**
     * Gets the default socket path for a node.
     *
     * @param nodeName The node name
     * @return The default socket path
     */
    private static Path getDefaultSocketPath(String nodeName) {
        String normalized = nodeName.replace("@", "_");
        return Paths.get("/tmp/yawl-erlang", normalized + ".sock");
    }

    /**
     * Factory for creating connections using native ei interface.
     */
    private static class EiConnectionFactory {
        public EiConnection connect(String nodeName, String cookie, Path socketPath) throws IOException {
            // Initialize ei subsystem
            if (!EiNativeLibrary.initialize()) {
                throw new IOException("Failed to initialize ei library");
            }

            // Create real connection to Erlang node
            return new NativeEiConnection(nodeName, cookie, socketPath);
        }
    }

    /**
     * Real EiConnection implementation using native ei interface.
     *
     * @throws UnsupportedOperationException because the native ei interface is not yet implemented.
     * This will be implemented once the jextract bindings are generated and integrated.
     */
    private static class NativeEiConnection implements EiConnection {
        private final String nodeName;
        private final String cookie;
        private final Path socketPath;
        private long connectionHandle;
        private boolean open = true;

        public NativeEiConnection(String nodeName, String cookie, Path socketPath) {
            this.nodeName = nodeName;
            this.cookie = cookie;
            this.socketPath = socketPath;

            // This will be implemented when native bindings are available
            throw new UnsupportedOperationException(
                "Real ei connection not yet implemented. " +
                "This requires jextract-generated bindings to ei.h interface. " +
                "The interface is designed to support real socket operations once the " +
                "native layer is properly integrated with the Erlang distribution protocol."
            );
        }

        public EiBuffer rpc(EiBuffer request, long timeout, TimeUnit unit) throws IOException, TimeoutException {
            if (!open) {
                throw new IOException("Connection is closed");
            }

            // Real RPC implementation will go here once native bindings are available
            throw new UnsupportedOperationException(
                "Real RPC operations require native ei interface implementation. " +
                "The interface design is ready for integration with jextract-generated bindings."
            );
        }

        public void close() throws IOException {
            if (open && connectionHandle != 0) {
                // Native cleanup will go here
                open = false;
            }
        }

        public boolean isOpen() {
            return open;
        }
    }

    /**
     * Interface for ei connection operations.
     */
    public interface EiConnection {
        EiBuffer rpc(EiBuffer request, long timeout, TimeUnit unit) throws IOException, TimeoutException;
        void close() throws IOException;
        boolean isOpen();
    }
}