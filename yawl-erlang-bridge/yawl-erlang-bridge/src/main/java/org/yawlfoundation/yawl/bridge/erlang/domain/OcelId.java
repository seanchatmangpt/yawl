package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.yawlfoundation.yawl.bridge.erlang.ErlTerm;
import org.yawlfoundation.yawl.bridge.erlang.ErlAtom;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;

import java.util.UUID;

/**
 * Represents an OCEL (Object-Centric Event Log) identifier.
 */
public record OcelId(UUID value) {
    /**
     * Creates an OcelId from a UUID string.
     *
     * @param s The UUID string representation
     * @return The OcelId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static OcelId fromString(String s) {
        try {
            UUID uuid = UUID.fromString(s);
            return new OcelId(uuid);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID string: " + s, e);
        }
    }

    /**
     * Creates an OcelId from an Erlang term.
     *
     * @param term The Erlang term containing a UUID
     * @return The OcelId
     * @throws ErlException if the term is not a valid UUID atom
     */
    public static OcelId fromErlTerm(ErlTerm term) throws ErlException {
        if (term instanceof ErlAtom) {
            String value = ((ErlAtom) term).getValue();
            return OcelId.fromString(value);
        }
        throw new ErlException("Expected ErlAtom for OCEL ID, got: " + term.type());
    }
}