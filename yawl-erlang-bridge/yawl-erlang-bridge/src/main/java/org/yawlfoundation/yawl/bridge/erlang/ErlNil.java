package org.yawlfoundation.yawl.bridge.erlang;

import java.io.IOException;

/**
 * Represents the empty list (nil) in the YAWL Erlang bridge.
 *
 * <p>The empty list, denoted as [] in Erlang, is the terminating element
 * of proper lists. It's distinct from null in Java.</p>
 *
 * <p>Example: [], [1, 2, 3 | []]</p>
 *
 * @since 1.0.0
 */
public final class ErlNil implements ErlTerm {

    /**
     * The singleton instance of ErlNil.
     */
    public static final ErlNil INSTANCE = new ErlNil();

    /**
     * Private constructor to ensure singleton pattern.
     */
    private ErlNil() {
    }

    @Override
    public void encodeToEiBuffer(EiBuffer buffer) throws ErlangException {
        try {
            buffer.encodeEmptyList();
        } catch (IOException e) {
            throw new ErlangException("Failed to encode empty list", e);
        }
    }

    @Override
    public byte[] encodeETF() throws ErlangException {
        try {
            EiBuffer buffer = new EiBuffer();
            // Add external term tag
            buffer.put((byte) 131); // EXTERNAL_TERM_TAG
            buffer.put((byte) 106); // NIL_EXT
            return buffer.toArray();
        } catch (IOException e) {
            throw new ErlangException("Failed to encode empty list to ETF", e);
        }
    }

    @Override
    public String asString() {
        return "[]";
    }

    @Override
    public String type() {
        return "nil";
    }

    @Override
    public boolean equals(Object o) {
        // ErlNil is a singleton, so only equal to itself
        return this == o;
    }

    @Override
    public int hashCode() {
        // Singleton has consistent hash code
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return asString();
    }

    /**
     * Returns the singleton instance of ErlNil.
     *
     * @return The ErlNil instance
     */
    public static ErlNil nil() {
        return INSTANCE;
    }
}