package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.yawlfoundation.yawl.bridge.erlang.ErlTerm;
import org.yawlfoundation.yawl.bridge.erlang.ErlAtom;
import org.yawlfoundation.yawl.bridge.erlang.ErlTuple;
import org.yawlfoundation.yawl.bridge.erlang.ErlList;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;
import org.yawlfoundation.yawl.bridge.erlang.ErlLong;

import java.util.Map;
import java.util.HashMap;

/**
 * Represents a Directly Follows Graph (DFG) discovered from event log data.
 */
public record DirectlyFollowsGraph(Map<String, Map<String, Integer>> edges) {
    /**
     * Creates a directly follows graph with the specified edges.
     *
     * @param edges The edges mapping from source activity to target activity with counts
     */
    public DirectlyFollowsGraph {
        if (edges == null) {
            throw new IllegalArgumentException("Edges cannot be null");
        }
    }

    /**
     * Creates a DirectlyFollowsGraph from an Erlang term.
     *
     * @param term The Erlang term containing DFG data
     * @return The DirectlyFollowsGraph
     * @throws ErlException if the term is not a valid DFG
     */
    public static DirectlyFollowsGraph fromErlTerm(ErlTerm term) throws ErlException {
        if (term instanceof ErlAtom) {
            String atomValue = ((ErlAtom) term).getValue();
            if ("error".equals(atomValue)) {
                throw new ErlException("DFG discovery failed");
            }
            throw new ErlException("Expected tuple for DFG, got atom");
        }

        if (term instanceof ErlTuple tuple && tuple.hasArity(2)) {
            ErlTerm okTag = tuple.get(0);
            if (!(okTag instanceof ErlAtom) || !"ok".equals(((ErlAtom) okTag).getValue())) {
                throw new ErlException("Expected 'ok' tag in DFG result");
            }

            ErlTerm edgesTerm = tuple.get(1);
            if (!(edgesTerm instanceof ErlList)) {
                throw new ErlException("Expected list of edges in DFG result");
            }

            Map<String, Map<String, Integer>> edges = new HashMap<>();
            ErlList edgesList = (ErlList) edgesTerm;

            for (ErlTerm edgeTerm : edgesList.getElements()) {
                if (edgeTerm instanceof ErlTuple edgeTuple && edgeTuple.hasArity(3)) {
                    ErlTerm sourceTerm = edgeTuple.get(0);
                    ErlTerm targetTerm = edgeTuple.get(1);
                    ErlTerm countTerm = edgeTuple.get(2);

                    String source = extractString(sourceTerm);
                    String target = extractString(targetTerm);
                    int count = extractInt(countTerm);

                    edges.computeIfAbsent(source, k -> new HashMap<>())
                         .put(target, count);
                }
            }

            return new DirectlyFollowsGraph(edges);
        }

        throw new ErlException("Expected tuple for DFG, got: " + term.type());
    }

    private static String extractString(ErlTerm term) throws ErlException {
        if (term instanceof ErlAtom) {
            return ((ErlAtom) term).getValue();
        }
        throw new ErlException("Expected string atom, got: " + term.type());
    }

    private static int extractInt(ErlTerm term) throws ErlException {
        if (term instanceof ErlLong) {
            return (int) ((ErlLong) term).getValue();
        }
        throw new ErlException("Expected integer, got: " + term.type());
    }
}