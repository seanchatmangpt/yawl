package org.yawlfoundation.yawl.bridge.erlang;

import java.io.IOException;

/**
 * Represents an Erlang float (double) in the YAWL Erlang bridge.
 *
 * <p>An Erlang float is a 64-bit IEEE 754 floating-point number.
 * Example: 3.14159, -42.0, 1.23e10</p>
 *
 * @since 1.0.0
 */
public final class ErlDouble implements ErlTerm {

    private final double value;

    /**
     * Constructs an ErlDouble with the given value.
     *
     * @param value The double value
     */
    public ErlDouble(double value) {
        this.value = value;
    }

    /**
     * Returns the double value.
     *
     * @return The double value
     */
    public double getValue() {
        return value;
    }

    @Override
    public void encodeToEiBuffer(EiBuffer buffer) throws ErlangException {
        try {
            buffer.encodeDouble(value);
        } catch (IOException e) {
            throw new ErlangException("Failed to encode double: " + value, e);
        }
    }

    @Override
    public byte[] encodeETF() throws ErlangException {
        try {
            EiBuffer buffer = new EiBuffer();
            // Add external term tag
            buffer.put((byte) 131); // EXTERNAL_TERM_TAG

            buffer.put((byte) 70); // NEW_FLOAT_EXT
            buffer.putLong(Double.doubleToLongBits(value));
            return buffer.toArray();
        } catch (IOException e) {
            throw new ErlangException("Failed to encode double to ETF: " + value, e);
        }
    }

    @Override
    public String asString() {
        return String.valueOf(value);
    }

    @Override
    public String type() {
        return "float";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErlDouble erlDouble = (ErlDouble) o;
        return Double.compare(erlDouble.value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }

    @Override
    public String toString() {
        return asString();
    }

    /**
     * Creates a new ErlDouble from the given value.
     *
     * @param value The double value
     * @return A new ErlDouble instance
     */
    public static ErlDouble of(double value) {
        return new ErlDouble(value);
    }

    /**
     * Creates a new ErlDouble from the given string.
     *
     * @param value The string representation of the double
     * @return A new ErlDouble instance
     * @throws NumberFormatException if the string cannot be parsed
     */
    public static ErlDouble of(String value) {
        return new ErlDouble(Double.parseDouble(value));
    }
}