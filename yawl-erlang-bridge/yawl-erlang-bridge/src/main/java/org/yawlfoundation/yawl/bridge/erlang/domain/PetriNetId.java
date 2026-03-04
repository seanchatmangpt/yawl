package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.yawlfoundation.yawl.bridge.erlang.ErlTerm;
import org.yawlfoundation.yawl.bridge.erlang.ErlAtom;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;

import java.util.UUID;

/**
 * Represents a Petri net identifier.
 */
public record PetriNetId(UUID value) {
    /**
     * Creates a PetriNetId from a UUID string.
     *
     * @param s The UUID string representation
     * @return The PetriNetId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static PetriNetId fromString(String s) {
        try {
            UUID uuid = UUID.fromString(s);
            return new PetriNetId(uuid);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID string: " + s, e);
        }
    }

    /**
     * Creates a PetriNetId from an Erlang term.
     *
     * @param term The Erlang term containing a UUID
     * @return The PetriNetId
     * @throws ErlException if the term is not a valid UUID atom
     */
    public static PetriNetId fromErlTerm(ErlTerm term) throws ErlException {
        if (term instanceof ErlAtom) {
            String value = ((ErlAtom) term).getValue();
            return PetriNetId.fromString(value);
        }
        throw new ErlException("Expected ErlAtom for Petri net ID, got: " + term.type());
    }
}