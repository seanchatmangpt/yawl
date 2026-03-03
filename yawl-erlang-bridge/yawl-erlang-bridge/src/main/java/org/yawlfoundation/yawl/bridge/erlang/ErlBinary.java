package org.yawlfoundation.yawl.bridge.erlang;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents an Erlang binary in the YAWL Erlang bridge.
 *
 * <p>An Erlang binary is a sequence of bytes. It's the preferred way to
 * handle raw binary data in Erlang.</p>
 *
 * @since 1.0.0
 */
public final class ErlBinary implements ErlTerm {

    private final byte[] bytes;

    /**
     * Constructs an ErlBinary from the given byte array.
     *
     * @param bytes The byte array (must not be null)
     * @throws IllegalArgumentException if bytes is null
     */
    public ErlBinary(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Binary bytes cannot be null");
        }
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * Constructs an ErlBinary from a portion of the given byte array.
     *
     * @param bytes The byte array (must not be null)
     * @param offset The starting offset
     * @param length The number of bytes to copy
     * @throws IllegalArgumentException if bytes is null
     * @throws IndexOutOfBoundsException if offset or length are out of bounds
     */
    public ErlBinary(byte[] bytes, int offset, int length) {
        if (bytes == null) {
            throw new IllegalArgumentException("Binary bytes cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > bytes.length) {
            throw new IndexOutOfBoundsException("Invalid offset or length");
        }
        this.bytes = Arrays.copyOfRange(bytes, offset, offset + length);
    }

    /**
     * Returns the binary bytes.
     *
     * @return The binary bytes (copy)
     */
    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * Returns the length of the binary in bytes.
     *
     * @return The length of the binary
     */
    public int length() {
        return bytes.length;
    }

    @Override
    public void encodeToEiBuffer(EiBuffer buffer) throws ErlangException {
        try {
            buffer.encodeBinary(bytes);
        } catch (IOException e) {
            throw new ErlangException("Failed to encode binary", e);
        }
    }

    @Override
    public String asString() {
        // Return hexadecimal representation for readability
        StringBuilder sb = new StringBuilder();
        sb.append("<<");

        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }

        sb.append(">>");
        return sb.toString();
    }

    @Override
    public String type() {
        return "binary";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErlBinary erlBinary = (ErlBinary) o;
        return Arrays.equals(bytes, erlBinary.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return asString();
    }

    /**
     * Creates a new ErlBinary from the given byte array.
     *
     * @param bytes The byte array
     * @return A new ErlBinary instance
     */
    public static ErlBinary of(byte[] bytes) {
        return new ErlBinary(bytes);
    }

    /**
     * Creates a new ErlBinary from a string (UTF-8 encoded).
     *
     * @param string The string to encode
     * @return A new ErlBinary instance
     */
    public static ErlBinary ofString(String string) {
        return new ErlBinary(string.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Creates a new ErlBinary from a hexadecimal string.
     *
     * @param hex The hexadecimal string (e.g., "48656C6C6F")
     * @return A new ErlBinary instance
     */
    public static ErlBinary ofHexString(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            String hexByte = hex.substring(i, i + 2);
            bytes[i / 2] = (byte) Integer.parseInt(hexByte, 16);
        }

        return new ErlBinary(bytes);
    }
}