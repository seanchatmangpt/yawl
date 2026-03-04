package org.yawlfoundation.yawl.bridge.erlang.nativ;

/**
 * Native library interface for the Erlang ei functions.
 *
 * <p>This class provides the bridge between Java and the native C implementation
 * of the Erlang ei interface. The actual implementations will be generated
 * using jextract from the ei.h header file.</p>
 *
 * @since 1.0.0
 */
public final class EiNativeLibrary {

    static {
        // Load the native library
        try {
            System.loadLibrary("yawl_erl_ei");
        } catch (UnsatisfiedLinkError e) {
            throw new RuntimeException(
                "Failed to load native yawl_erl_ei library. " +
                "Ensure the library is properly built and available in the library path.", e);
        }
    }

    /**
     * Initializes the ei system.
     *
     * <p>This must be called before any other ei functions.</p>
     *
     * @return true if initialization was successful
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native boolean initialize();

    /**
     * Initializes the ei connection parameters.
     *
     * @param hostname The hostname of the Erlang node
     * @param nodename The name of the Erlang node
     * @param cookie The cookie for authentication
     * @return true if initialization was successful
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native boolean initConnection(String hostname, String nodename, String cookie);

    /**
     * Establishes a connection to an Erlang node.
     *
     * @param hostname The hostname to connect to
     * @param nodename The name of the Erlang node
     * @param port The port number
     * @return connection handle, or 0 if failed
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native long connect(String hostname, String nodename, int port);

    /**
     * Establishes a connection to an Erlang node using Unix domain socket.
     *
     * @param socketPath The path to the Unix domain socket
     * @param nodename The name of the Erlang node
     * @param cookie The cookie for authentication
     * @return connection handle, or 0 if failed
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native long connectUnixDomain(String socketPath, String nodename, String cookie);

    /**
     * Performs an RPC call to an Erlang node.
     *
     * @param connection The connection handle
     * @param buffer The encoded term buffer
     * @param timeout The timeout in milliseconds
     * @return response buffer handle
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native long rpc(long connection, byte[] buffer, int timeout);

    /**
     * Creates a new ei buffer.
     *
     * @param initialSize The initial buffer size
     * @return buffer handle, or 0 if failed
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native long newEiBuffer(int initialSize);

    /**
     * Frees an ei buffer.
     *
     * @param buffer The buffer handle
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native void freeEiBuffer(long buffer);

    /**
     * Encodes an atom into an ei buffer.
     *
     * @param buffer The buffer handle
     * @param atom The atom to encode
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native void encodeAtom(long buffer, String atom);

    /**
     * Encodes a binary into an ei buffer.
     *
     * @param buffer The buffer handle
     * @param data The binary data
     * @param length The length of the data
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native void encodeBinary(long buffer, byte[] data, int length);

    /**
     * Encodes an integer into an ei buffer.
     *
     * @param buffer The buffer handle
     * @param value The integer value
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native void encodeLong(long buffer, long value);

    /**
     * Encodes a string into an ei buffer.
     *
     * @param buffer The buffer handle
     * @param string The string to encode
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native void encodeString(long buffer, String string);

    /**
     * Encodes a list header into an ei buffer.
     *
     * @param buffer The buffer handle
     * @param length The number of elements in the list
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native void encodeListHeader(long buffer, int length);

    /**
     * Encodes a tuple header into an ei buffer.
     *
     * @param buffer The buffer handle
     * @param arity The number of elements in the tuple
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native void encodeTupleHeader(long buffer, int arity);

    /**
     * Encodes an empty list (nil) into an ei buffer.
     *
     * @param buffer The buffer handle
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native void encodeEmptyList(long buffer);

    /**
     * Gets the data from an ei buffer.
     *
     * @param buffer The buffer handle
     * @return the buffer data
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native byte[] getBufferData(long buffer);

    /**
     * Gets the size of an ei buffer.
     *
     * @param buffer The buffer handle
     * @return the buffer size
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native int getBufferSize(long buffer);

    /**
     * Closes an Erlang connection.
     *
     * @param connection The connection handle
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native void closeConnection(long connection);

    /**
     * Checks if a connection is still open.
     *
     * @param connection The connection handle
     * @return true if connection is open
     * @throws UnsupportedOperationException until native implementation is available
     */
    public static native boolean isConnectionOpen(long connection);
}