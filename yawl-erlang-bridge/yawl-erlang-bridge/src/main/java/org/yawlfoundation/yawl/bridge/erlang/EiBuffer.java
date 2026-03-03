package org.yawlfoundation.yawl.bridge.erlang;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Wrapper for the ei_x_buff structure from the Erlang interface.
 *
 * <p>This class provides a type-safe interface to the ei buffer operations,
 * making it easier to encode Erlang terms from Java.</p>
 *
 * @since 1.0.0
 */
public final class EiBuffer {

    private final ByteBuffer buffer;
    private int size;

    /**
     * Creates a new EiBuffer with the specified initial capacity.
     *
     * @param initialCapacity The initial buffer capacity
     */
    public EiBuffer(int initialCapacity) {
        this.buffer = ByteBuffer.allocate(initialCapacity);
        this.size = 0;
    }

    /**
     * Creates a new EiBuffer with default capacity.
     */
    public EiBuffer() {
        this(1024);
    }

    /**
     * Returns the current size of the buffer.
     *
     * @return The buffer size in bytes
     */
    public int size() {
        return size;
    }

    /**
     * Returns the capacity of the buffer.
     *
     * @return The buffer capacity in bytes
     */
    public int capacity() {
        return buffer.capacity();
    }

    /**
     * Returns the buffer data as a byte array.
     *
     * @return The buffer data
     */
    public byte[] toArray() {
        byte[] result = new byte[size];
        System.arraycopy(buffer.array(), 0, result, 0, size);
        return result;
    }

    /**
     * Encodes an Erlang atom.
     *
     * @param atom The atom to encode
     * @throws IOException if encoding fails
     */
    public void encodeAtom(String atom) throws IOException {
        byte[] bytes = atom.getBytes(StandardCharsets.ISO_8859_1);
        ensureCapacity(bytes.length);

        // Atom header: 1 byte type + 2 byte length + atom data
        buffer.put((byte) 100); // ATOM_EXT
        putShort(bytes.length);
        buffer.put(bytes);
        size += bytes.length + 3;
    }

    /**
     * Encodes an Erlang binary.
     *
     * @param bytes The binary data to encode
     * @throws IOException if encoding fails
     */
    public void encodeBinary(byte[] bytes) throws IOException {
        ensureCapacity(bytes.length);

        // Binary header: 1 byte type + 4 byte length + binary data
        buffer.put((byte) 109); // BIN_EXT
        putInt(bytes.length);
        buffer.put(bytes);
        size += bytes.length + 5;
    }

    /**
     * Encodes an Erlang boolean.
     *
     * @param value The boolean value
     * @throws IOException if encoding fails
     */
    public void encodeBoolean(boolean value) throws IOException {
        encodeAtom(value ? "true" : "false");
    }

    /**
     * Encodes an Erlang character.
     *
     * @param value The character value
     * @throws IOException if encoding fails
     */
    public void encodeChar(char value) throws IOException {
        encodeLong(value);
    }

    /**
     * Encodes an Erlang integer (long).
     *
     * @param value The integer value
     * @throws IOException if encoding fails
     */
    public void encodeLong(long value) throws IOException {
        if (value >= -2147483648L && value <= 2147483647L) {
            encodeInt((int) value);
        } else {
            encodeBigInt(value);
        }
    }

    /**
     * Encodes an int value in big-endian order.
     *
     * @param value The int value
     */
    private void encodeInt(int value) throws IOException {
        ensureCapacity(4);
        buffer.put((byte) ((value >> 24) & 0xFF));
        buffer.put((byte) ((value >> 16) & 0xFF));
        buffer.put((byte) ((value >> 8) & 0xFF));
        buffer.put((byte) (value & 0xFF));
        size += 4;
    }

    /**
     * Encodes an Erlang string.
     *
     * @param string The string to encode
     * @throws IOException if encoding fails
     */
    public void encodeString(String string) throws IOException {
        byte[] bytes = string.getBytes(StandardCharsets.ISO_8859_1);
        ensureCapacity(bytes.length);

        // String header: 1 byte type + 2 byte length + string data
        buffer.put((byte) 107); // STRING_EXT
        putShort(bytes.length);
        buffer.put(bytes);
        size += bytes.length + 3;
    }

    /**
     * Encodes an empty list.
     *
     * @throws IOException if encoding fails
     */
    public void encodeEmptyList() throws IOException {
        buffer.put((byte) 106); // NIL_EXT
        size += 1;
    }

    /**
     * Encodes a list header for a list of the specified length.
     *
     * @param length The number of elements in the list
     * @throws IOException if encoding fails
     */
    public void encodeListHeader(int length) throws IOException {
        ensureCapacity(length * 2 + 1);
        buffer.put((byte) 108); // LIST_EXT
        putInt(length);
        size += 5;
    }

    /**
     * Encodes a tuple header for a tuple of the specified arity.
     *
     * @param arity The number of elements in the tuple
     * @throws IOException if encoding fails
     */
    public void encodeTupleHeader(int arity) throws IOException {
        ensureCapacity(arity * 2 + 1);
        buffer.put((byte) 104); // TUPLE_EXT
        putShort(arity);
        size += 3;
    }

    /**
     * Encodes a list with a list header.
     *
     * @param elements The list elements
     * @throws ErlangException if encoding fails
     */
    public void encodeList(ErlTerm[] elements) throws ErlangException {
        try {
            encodeListHeader(elements.length);
            for (ErlTerm element : elements) {
                element.encodeToEiBuffer(this);
            }
            encodeEmptyList(); // Terminate with nil
        } catch (IOException e) {
            throw new ErlangException("Failed to encode list", e);
        }
    }

    /**
     * Encodes a tuple with a tuple header.
     *
     * @param elements The tuple elements
     * @throws ErlangException if encoding fails
     */
    public void encodeTuple(ErlTerm[] elements) throws ErlangException {
        try {
            encodeTupleHeader(elements.length);
            for (ErlTerm element : elements) {
                element.encodeToEiBuffer(this);
            }
        } catch (IOException e) {
            throw new ErlangException("Failed to encode tuple", e);
        }
    }

    /**
     * Appends raw data to the buffer.
     *
     * @param data The data to append
     * @throws IOException if buffer capacity exceeded
     */
    public void append(byte[] data) throws IOException {
        ensureCapacity(data.length);
        buffer.put(data);
        size += data.length;
    }

    /**
     * Expands the buffer if needed.
     *
     * @param required The required additional space
     * @throws IOException if buffer cannot be expanded
     */
    private void ensureCapacity(int required) throws IOException {
        if (size + required > buffer.capacity()) {
            int newCapacity = Math.max(buffer.capacity() * 2, size + required);
            ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);
            newBuffer.put(buffer.array(), 0, size);
            buffer.clear();
            buffer.put(newBuffer);
        }
    }

    /**
     * Puts a short value in big-endian order.
     *
     * @param value The short value
     */
    private void putShort(int value) {
        buffer.put((byte) ((value >> 8) & 0xFF));
        buffer.put((byte) (value & 0xFF));
    }

    /**
     * Puts an int value in big-endian order.
     *
     * @param value The int value
     */
    private void putInt(int value) {
        buffer.put((byte) ((value >> 24) & 0xFF));
        buffer.put((byte) ((value >> 16) & 0xFF));
        buffer.put((byte) ((value >> 8) & 0xFF));
        buffer.put((byte) (value & 0xFF));
    }

    /**
     * Encodes a big integer for large numbers outside int range.
     *
     * @param value The long value
     * @throws IOException if encoding fails
     */
    private void encodeBigInt(long value) throws IOException {
        ensureCapacity(12);
        buffer.put((byte) 110); // SMALL_BIG_EXT

        int sign = value < 0 ? 1 : 0;
        long absValue = value < 0 ? -value : value;
        int n = 0;
        int digits = 0;

        while (absValue > 0) {
            digits++;
            absValue >>>= 8;
        }

        buffer.put((byte) digits);
        buffer.put((byte) sign);

        long temp = value;
        for (int i = 0; i < digits; i++) {
            buffer.put((byte) (temp & 0xFF));
            temp >>>= 8;
        }

        size += digits + 3;
    }
}