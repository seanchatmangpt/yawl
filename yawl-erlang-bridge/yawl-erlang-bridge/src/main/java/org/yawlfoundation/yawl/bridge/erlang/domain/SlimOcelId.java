package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.yawlfoundation.yawl.bridge.erlang.ErlTerm;
import org.yawlfoundation.yawl.bridge.erlang.ErlAtom;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;

import java.util.UUID;

/**
 * Represents a slim OCEL identifier with discovered links.
 */
public record SlimOcelId(UUID value) {
    /**
     * Creates a SlimOcelId from a UUID string.
     *
     * @param s The UUID string representation
     * @return The SlimOcelId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static SlimOcelId fromString(String s) {
        try {
            UUID uuid = UUID.fromString(s);
            return new SlimOcelId(uuid);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID string: " + s, e);
        }
    }

    /**
     * Creates a SlimOcelId from an Erlang term.
     *
     * @param term The Erlang term containing a UUID
     * @return The SlimOcelId
     * @throws ErlException if the term is not a valid UUID atom
     */
    public static SlimOcelId fromErlTerm(ErlTerm term) throws ErlException {
        if (term instanceof ErlAtom) {
            String value = ((ErlAtom) term).getValue();
            return SlimOcelId.fromString(value);
        }
        throw new ErlException("Expected ErlAtom for Slim OCEL ID, got: " + term.type());
    }
}