package org.yawlfoundation.yawl.bridge.erlang.transport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.yawlfoundation.yawl.bridge.erlang.ErlangException;
import org.yawlfoundation.yawl.bridge.erlang.ErlTerm;
import org.yawlfoundation.yawl.bridge.erlang.nativ.EiNativeLibrary;
import org.yawlfoundation.yawl.bridge.erlang.ErlangNode;

/**
 * Unix domain socket transport for JVM↔BEAM boundary communication.
 *
 * <p>This class manages the creation of Unix domain socket connections between
 * Java and Erlang nodes, handling socket directory creation, node name generation,
 * and ei configuration for -proto_dist local connections.</p>
 *
 * <p>HYPER_STANDARDS: No mock code, only real socket connections or throw.</p>
 *
 * @since 1.0.0
 */
public final class UnixSocketTransport implements AutoCloseable {

    private final Path socketDirectory;
    private final String nodeName;
    private final String cookie;
    private final String hostname;
    private ErlangNode connection;
    private volatile boolean closed = false;

    /**
     * Creates a Unix domain socket transport with default configuration.
     *
     * @param cookie The Erlang cookie for authentication
     * @throws ErlangException if transport initialization fails
     */
    public UnixSocketTransport(String cookie) throws ErlangException {
        this(cookie, getDefaultHostname(), getDefaultSocketDirectory());
    }

    /**
     * Creates a Unix domain socket transport with specified configuration.
     *
     * @param cookie The Erlang cookie for authentication
     * @param hostname The hostname for the Erlang node
     * @param socketDirectory The directory for Unix domain sockets
     * @throws ErlangException if transport initialization fails
     */
    public UnixSocketTransport(String cookie, String hostname, Path socketDirectory) throws ErlangException {
        if (cookie == null || cookie.isEmpty()) {
            throw new ErlangException("Cookie cannot be null or empty");
        }
        if (hostname == null || hostname.isEmpty()) {
            throw new ErlangException("Hostname cannot be null or empty");
        }
        if (socketDirectory == null) {
            throw new ErlangException("Socket directory cannot be null");
        }

        this.cookie = cookie;
        this.hostname = hostname;
        this.socketDirectory = socketDirectory;

        // Ensure socket directory exists
        ensureSocketDirectory();

        // Generate unique node name
        this.nodeName = generateNodeName();

        // Initialize connection
        initializeConnection();
    }

    /**
     * Initializes the ei connection to the Erlang node using Unix domain socket.
     *
     * @throws ErlangException if connection initialization fails
     */
    private void initializeConnection() {
        try {
            // Initialize ei system
            if (!EiNativeLibrary.initialize()) {
                throw new ErlangException("Failed to initialize ei system");
            }

            // Initialize connection parameters
            if (!EiNativeLibrary.initConnection(hostname, nodeName, cookie)) {
                throw new ErlangException("Failed to initialize connection parameters");
            }

            // Create socket path
            Path socketPath = socketDirectory.resolve(nodeName + ".sock");

            // Connect using Unix domain socket
            long connectionHandle = EiNativeLibrary.connectUnixDomain(
                socketPath.toString(), nodeName, cookie);

            if (connectionHandle == 0) {
                throw new ErlangException("Failed to establish Unix domain socket connection");
            }

            // Create Erlang node wrapper
            this.connection = new ErlangNode(nodeName, cookie, socketPath);

        } catch (Exception e) {
            throw new ErlangException("Failed to initialize Unix domain socket transport", e);
        }
    }

    /**
     * Ensures the socket directory exists and is writable.
     *
     * @throws ErlangException if directory cannot be created or is not writable
     */
    private void ensureSocketDirectory() throws ErlangException {
        try {
            if (!Files.exists(socketDirectory)) {
                Files.createDirectories(socketDirectory);
            }

            // Check if directory is writable
            Path testFile = socketDirectory.resolve(".test");
            Files.createFile(testFile);
            Files.delete(testFile);
        } catch (IOException e) {
            throw new ErlangException(
                "Failed to create or access socket directory: " + socketDirectory, e);
        }
    }

    /**
     * Generates a unique node name for this transport.
     *
     * @return Unique node name
     */
    private String generateNodeName() {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "yawl_java_" + uuid + "@" + hostname;
    }

    /**
     * Gets the default hostname for the local machine.
     *
     * @return hostname or "localhost" if lookup fails
     */
    public static String getDefaultHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    /**
     * Gets the default socket directory path.
     *
     * @return Path to /tmp/yawl-erlang
     */
    public static Path getDefaultSocketDirectory() {
        return Paths.get("/tmp/yawl-erlang");
    }

    /**
     * Performs an RPC call to the specified Erlang module and function.
     *
     * @param module The Erlang module name
     * @param function The Erlang function name
     * @param args The function arguments
     * @return The result as an ErlTerm
     * @throws ErlangException if RPC call fails
     * @throws IllegalStateException if transport is closed
     */
    public ErlTerm rpc(String module, String function, ErlTerm... args) throws ErlangException {
        checkClosed();
        return connection.rpc(module, function, args);
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
     * @throws IllegalStateException if transport is closed
     */
    public ErlTerm rpc(String module, String function,
                     long timeout, TimeUnit unit,
                     ErlTerm... args) throws ErlangException {
        checkClosed();
        return connection.rpc(module, function, timeout, unit, args);
    }

    /**
     * Checks if the transport is connected and operational.
     *
     * @return true if connected and operational
     */
    public boolean isConnected() {
        return !closed && connection != null && connection.isConnected();
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
     * Returns the socket directory path.
     *
     * @return The socket directory path
     */
    public Path getSocketDirectory() {
        return socketDirectory;
    }

    /**
     * Returns the hostname.
     *
     * @return The hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Closes the transport and releases resources.
     */
    @Override
    public void close() {
        if (!closed) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    // Log warning but don't propagate
                    System.err.println("Warning: Error closing transport connection: " + e.getMessage());
                }
            }
            closed = true;
        }
    }

    /**
     * Checks if the transport is closed and throws if so.
     *
     * @throws IllegalStateException if transport is closed
     */
    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("Unix domain socket transport is closed");
        }
    }

    /**
     * Converts Java objects to ErlTerms (placeholder implementation).
     *
     * @param args Java objects to convert
     * @return Array of ErlTerms
     * @throws UnsupportedOperationException until ErlTerm conversion is implemented
     */
    private Object[] convertToErlTerms(Object[] args) {
        // This is a placeholder - in practice, you'd need full ErlTerm conversion
        // For now, throw to prevent misuse with incomplete implementation
        throw new UnsupportedOperationException(
            "ErlTerm conversion not yet implemented. " +
            "This requires integration with the ErlTerm codec system."
        );
    }

    /**
     * Factory for creating Unix domain socket transports with common configurations.
     */
    public static final class Factory {
        private static final String DEFAULT_COOKIE = "yawl";
        private static final Path DEFAULT_SOCKET_DIR = getDefaultSocketDirectory();

        /**
         * Creates a transport with default configuration.
         *
         * @return configured UnixSocketTransport
         * @throws ErlangException if initialization fails
         */
        public static UnixSocketTransport create() throws ErlangException {
            return new UnixSocketTransport(DEFAULT_COOKIE);
        }

        /**
         * Creates a transport with custom cookie.
         *
         * @param cookie The Erlang cookie
         * @return configured UnixSocketTransport
         * @throws ErlangException if initialization fails
         */
        public static UnixSocketTransport createWithCookie(String cookie) throws ErlangException {
            return new UnixSocketTransport(cookie);
        }

        /**
         * Creates a transport with custom socket directory.
         *
         * @param socketDirectory The directory for Unix domain sockets
         * @return configured UnixSocketTransport
         * @throws ErlangException if initialization fails
         */
        public static UnixSocketTransport createWithSocketDirectory(Path socketDirectory) throws ErlangException {
            return new UnixSocketTransport(DEFAULT_COOKIE, getDefaultHostname(), socketDirectory);
        }

        /**
         * Creates a transport with custom configuration.
         *
         * @param cookie The Erlang cookie
         * @param hostname The hostname
         * @param socketDirectory The socket directory
         * @return configured UnixSocketTransport
         * @throws ErlangException if initialization fails
         */
        public static UnixSocketTransport create(String cookie, String hostname, Path socketDirectory) throws ErlangException {
            return new UnixSocketTransport(cookie, hostname, socketDirectory);
        }
    }
}