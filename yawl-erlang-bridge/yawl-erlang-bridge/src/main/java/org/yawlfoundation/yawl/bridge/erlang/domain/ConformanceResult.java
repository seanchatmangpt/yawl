package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.yawlfoundation.yawl.bridge.erlang.ErlTerm;
import org.yawlfoundation.yawl.bridge.erlang.ErlAtom;
import org.yawlfoundation.yawl.bridge.erlang.ErlLong;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;

/**
 * Represents the result of token replay conformance checking.
 */
public record ConformanceResult(double fitness, int missing, int remaining, int consumed) {
    /**
     * Creates a conformance result with the specified metrics.
     *
     * @param fitness The fitness value (0.0 to 1.0)
     * @param missing The number of missing tokens
     * @param remaining The number of remaining tokens
     * @param consumed The number of consumed tokens
     */
    public ConformanceResult {
        if (fitness < 0.0 || fitness > 1.0) {
            throw new IllegalArgumentException("Fitness must be between 0.0 and 1.0");
        }
        if (missing < 0 || remaining < 0 || consumed < 0) {
            throw new IllegalArgumentException("Token counts cannot be negative");
        }
    }

    /**
     * Creates a ConformanceResult from an Erlang term.
     *
     * @param term The Erlang term containing conformance metrics
     * @return The ConformanceResult
     * @throws ErlException if the term is not a valid conformance result
     */
    public static ConformanceResult fromErlTerm(ErlTerm term) throws ErlException {
        if (term instanceof ErlAtom) {
            // Handle special cases like error terms
            String atomValue = ((ErlAtom) term).getValue();
            if ("error".equals(atomValue)) {
                throw new ErlException("Conformance analysis failed");
            }
            throw new ErlException("Expected tuple for conformance result, got atom");
        }

        if (term instanceof org.yawlfoundation.yawl.bridge.erlang.ErlTuple tuple && tuple.hasArity(5)) {
            ErlTerm okTag = tuple.get(0);
            if (!(okTag instanceof ErlAtom) || !"ok".equals(((ErlAtom) okTag).getValue())) {
                throw new ErlException("Expected 'ok' tag in conformance result");
            }

            ErlTerm fitnessTerm = tuple.get(1);
            ErlTerm missingTerm = tuple.get(2);
            ErlTerm remainingTerm = tuple.get(3);
            ErlTerm consumedTerm = tuple.get(4);

            double fitness = extractDouble(fitnessTerm);
            int missing = extractInt(missingTerm);
            int remaining = extractInt(remainingTerm);
            int consumed = extractInt(consumedTerm);

            return new ConformanceResult(fitness, missing, remaining, consumed);
        }

        throw new ErlException("Expected tuple for conformance result, got: " + term.type());
    }

    private static double extractDouble(ErlTerm term) throws ErlException {
        if (term instanceof ErlLong) {
            return ((ErlLong) term).getValue();
        } else if (term instanceof org.yawlfoundation.yawl.bridge.erlang.ErlDouble) {
            return ((org.yawlfoundation.yawl.bridge.erlang.ErlDouble) term).getValue();
        }
        throw new ErlException("Expected number, got: " + term.type());
    }

    private static int extractInt(ErlTerm term) throws ErlException {
        if (term instanceof ErlLong) {
            return (int) ((ErlLong) term).getValue();
        }
        throw new ErlException("Expected integer, got: " + term.type());
    }
}