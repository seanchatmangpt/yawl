package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.yawlfoundation.yawl.bridge.erlang.ErlTerm;
import org.yawlfoundation.yawl.bridge.erlang.ErlAtom;
import org.yawlfoundation.yawl.bridge.erlang.ErlTuple;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;

/**
 * Represents a Petri net discovered from event log data.
 */
public record PetriNet(String pnmlXml) {
    /**
     * Creates a Petri net with the specified PNML XML.
     *
     * @param pnmlXml The PNML XML representation of the Petri net
     */
    public PetriNet {
        if (pnmlXml == null || pnmlXml.trim().isEmpty()) {
            throw new IllegalArgumentException("PNML XML cannot be null or empty");
        }
    }

    /**
     * Creates a PetriNet from an Erlang term.
     *
     * @param term The Erlang term containing Petri net data
     * @return The PetriNet
     * @throws ErlException if the term is not a valid Petri net
     */
    public static PetriNet fromErlTerm(ErlTerm term) throws ErlException {
        if (term instanceof ErlAtom) {
            String atomValue = ((ErlAtom) term).getValue();
            if ("error".equals(atomValue)) {
                throw new ErlException("Petri net mining failed");
            }
            throw new ErlException("Expected tuple for Petri net, got atom");
        }

        if (term instanceof ErlTuple tuple && tuple.hasArity(2)) {
            ErlTerm okTag = tuple.get(0);
            if (!(okTag instanceof ErlAtom) || !"ok".equals(((ErlAtom) okTag).getValue())) {
                throw new ErlException("Expected 'ok' tag in Petri net result");
            }

            ErlTerm xmlTerm = tuple.get(1);
            String pnmlXml = extractString(xmlTerm);

            if (pnmlXml == null || pnmlXml.trim().isEmpty()) {
                throw new ErlException("PNML XML cannot be empty");
            }

            return new PetriNet(pnmlXml);
        }

        throw new ErlException("Expected tuple for Petri net, got: " + term.type());
    }

    private static String extractString(ErlTerm term) throws ErlException {
        if (term instanceof ErlAtom) {
            return ((ErlAtom) term).getValue();
        }
        throw new ErlException("Expected string atom, got: " + term.type());
    }
}