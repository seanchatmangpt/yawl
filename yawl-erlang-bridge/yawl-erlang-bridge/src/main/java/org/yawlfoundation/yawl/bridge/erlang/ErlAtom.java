package org.yawlfoundation.yawl.bridge.erlang;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Represents an Erlang atom in the YAWL Erlang bridge.
 *
 * <p>An atom is a symbolic name in Erlang, similar to an identifier.
 * Examples: 'hello', 'yawl_case_123', 'true'</p>
 *
 * @since 1.0.0
 */
public final class ErlAtom implements ErlTerm {

    private final String value;

    /**
     * Constructs an ErlAtom with the given value.
     *
     * @param value The atom value (must not be null)
     * @throws IllegalArgumentException if value is null
     */
    public ErlAtom(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Atom value cannot be null");
        }
        this.value = value;
    }

    /**
     * Returns the atom value.
     *
     * @return The atom value
     */
    public String getValue() {
        return value;
    }

    /**
     * Creates a new ErlAtom with the given value.
     *
     * @param value The atom value
     * @return A new ErlAtom instance
     */
    public static ErlAtom of(String value) {
        return new ErlAtom(value);
    }

    @Override
    public void encodeToEiBuffer(EiBuffer buffer) throws ErlangException {
        try {
            buffer.encodeAtom(value);
        } catch (IOException e) {
            throw new ErlangException("Failed to encode atom: " + value, e);
        }
    }

    @Override
    public byte[] encodeETF() throws ErlangException {
        try {
            EiBuffer buffer = new EiBuffer();
            // Add external term tag
            buffer.put((byte) 131); // EXTERNAL_TERM_TAG

            // Use small atom extension for atoms <= 255 bytes
            byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
            if (bytes.length <= 255) {
                buffer.put((byte) 119); // SMALL_ATOM_EXT
                buffer.put((byte) bytes.length);
                buffer.put(bytes);
            } else {
                buffer.put((byte) 100); // ATOM_EXT
                buffer.putShort((short) bytes.length);
                buffer.put(bytes);
            }
            return buffer.toArray();
        } catch (IOException e) {
            throw new ErlangException("Failed to encode atom to ETF: " + value, e);
        }
    }

    @Override
    public String asString() {
        return "'" + value + "'";
    }

    @Override
    public String type() {
        return "atom";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErlAtom erlAtom = (ErlAtom) o;
        return value.equals(erlAtom.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return asString();
    }
}