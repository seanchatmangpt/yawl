package org.yawlfoundation.yawl.bridge.erlang;

import java.io.IOException;

/**
 * Represents an Erlang integer (long) in the YAWL Erlang bridge.
 *
 * <p>Erlang uses a single integer type that can handle arbitrarily large
 * values, but for interoperability with Java we use long for most cases.</p>
 *
 * @since 1.0.0
 */
public final class ErlLong implements ErlTerm {

    private final long value;

    /**
     * Constructs an ErlLong with the given value.
     *
     * @param value The integer value
     */
    public ErlLong(long value) {
        this.value = value;
    }

    /**
     * Returns the integer value.
     *
     * @return The integer value
     */
    public long getValue() {
        return value;
    }

    @Override
    public void encodeToEiBuffer(EiBuffer buffer) throws ErlangException {
        try {
            buffer.encodeLong(value);
        } catch (IOException e) {
            throw new ErlangException("Failed to encode long: " + value, e);
        }
    }

    @Override
    public String asString() {
        return String.valueOf(value);
    }

    @Override
    public String type() {
        return "integer";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErlLong erlLong = (ErlLong) o;
        return value == erlLong.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return asString();
    }

    /**
     * Creates a new ErlLong from the given value.
     *
     * @param value The integer value
     * @return A new ErlLong instance
     */
    public static ErlLong of(long value) {
        return new ErlLong(value);
    }

    /**
     * Creates a new ErlLong from the given string.
     *
     * @param value The string representation of the integer
     * @return A new ErlLong instance
     * @throws NumberFormatException if the string cannot be parsed
     */
    public static ErlLong of(String value) {
        return new ErlLong(Long.parseLong(value));
    }
}