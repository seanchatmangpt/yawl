package org.yawlfoundation.yawl.bridge.erlang;

/**
 * Sealed interface representing Erlang terms in the YAWL Erlang bridge.
 *
 * <p>This interface forms the foundation of typed Erlang term representation,
 * allowing safe encoding and decoding between Java and Erlang data types.</p>
 *
 * @since 1.0.0
 */
public sealed interface ErlTerm permits ErlAtom, ErlList, ErlTuple, ErlBinary, ErlLong {

    /**
     * Encodes this Erlang term to an ei buffer for transmission.
     *
     * @param buffer The ei buffer to encode into
     * @throws ErlangException if encoding fails
     */
    void encodeToEiBuffer(EiBuffer buffer) throws ErlangException;

    /**
     * Returns the string representation of this Erlang term.
     *
     * <p>Note: This is primarily for debugging and logging purposes.
     * The exact format may not match Erlang's string representation.</p>
     *
     * @return String representation of the term
     */
    String asString();

    /**
     * Returns the type of this Erlang term.
     *
     * @return The term type as a string
     */
    String type();

    /**
     * Returns whether this term has at least the specified arity.
     *
     * @param minArity The minimum arity to check for
     * @return true if this term has at least the specified arity
     * @throws UnsupportedOperationException if term doesn't support arity check
     */
    default boolean hasArity(int minArity) {
        throw new UnsupportedOperationException("Arity check not supported for this term type");
    }
}