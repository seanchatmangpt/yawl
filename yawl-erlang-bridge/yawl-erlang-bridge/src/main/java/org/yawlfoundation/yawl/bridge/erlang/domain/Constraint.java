package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.yawlfoundation.yawl.bridge.erlang.ErlTerm;
import org.yawlfoundation.yawl.bridge.erlang.ErlAtom;
import org.yawlfoundation.yawl.bridge.erlang.ErlLong;
import org.yawlfoundation.yawl.bridge.erlang.ErlTuple;
import org.yawlfoundation.yawl.bridge.erlang.ErlList;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;

import java.util.Map;
import java.util.HashMap;

/**
 * Represents a DECLARE constraint discovered from OCEL data.
 */
public record Constraint(String template, Map<String, Object> params, double support) {
    /**
     * Creates a constraint with the specified parameters.
     *
     * @param template The constraint template name
     * @param params The constraint parameters
     * @param support The support value (0.0 to 1.0)
     */
    public Constraint {
        if (template == null || template.trim().isEmpty()) {
            throw new IllegalArgumentException("Template cannot be null or empty");
        }
        if (params == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        if (support < 0.0 || support > 1.0) {
            throw new IllegalArgumentException("Support must be between 0.0 and 1.0");
        }
    }

    /**
     * Creates a Constraint from an Erlang term.
     *
     * @param term The Erlang term containing constraint data
     * @return The Constraint
     * @throws ErlException if the term is not a valid constraint
     */
    public static Constraint fromErlTerm(ErlTerm term) throws ErlException {
        if (term instanceof ErlAtom) {
            String atomValue = ((ErlAtom) term).getValue();
            if ("error".equals(atomValue)) {
                throw new ErlException("Constraint discovery failed");
            }
            throw new ErlException("Expected tuple for constraint, got atom");
        }

        if (term instanceof ErlTuple tuple && tuple.hasArity(3)) {
            ErlTerm templateTerm = tuple.get(0);
            ErlTerm paramsTerm = tuple.get(1);
            ErlTerm supportTerm = tuple.get(2);

            String template = extractString(templateTerm);
            Map<String, Object> params = extractParams(paramsTerm);
            double support = extractDouble(supportTerm);

            return new Constraint(template, params, support);
        }

        throw new ErlException("Expected tuple for constraint, got: " + term.type());
    }

    private static String extractString(ErlTerm term) throws ErlException {
        if (term instanceof ErlAtom) {
            return ((ErlAtom) term).getValue();
        }
        throw new ErlException("Expected string atom, got: " + term.type());
    }

    private static double extractDouble(ErlTerm term) throws ErlException {
        if (term instanceof ErlLong) {
            return ((ErlLong) term).getValue();
        } else if (term instanceof org.yawlfoundation.yawl.bridge.erlang.ErlDouble) {
            return ((org.yawlfoundation.yawl.bridge.erlang.ErlDouble) term).getValue();
        }
        throw new ErlException("Expected number, got: " + term.type());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractParams(ErlTerm term) throws ErlException {
        if (term instanceof ErlList) {
            Map<String, Object> params = new HashMap<>();
            ErlList list = (ErlList) term;

            for (ErlTerm element : list.getElements()) {
                if (element instanceof ErlTuple tuple && tuple.hasArity(2)) {
                    ErlTerm keyTerm = tuple.get(0);
                    ErlTerm valueTerm = tuple.get(1);

                    if (keyTerm instanceof ErlAtom) {
                        String key = ((ErlAtom) keyTerm).getValue();
                        Object value = convertErlTermToJava(valueTerm);
                        params.put(key, value);
                    }
                }
            }

            return params;
        }
        throw new ErlException("Expected list for parameters, got: " + term.type());
    }

    private static Object convertErlTermToJava(ErlTerm term) throws ErlException {
        if (term instanceof ErlAtom) {
            return ((ErlAtom) term).getValue();
        } else if (term instanceof ErlLong) {
            return ((ErlLong) term).getValue();
        } else if (term instanceof org.yawlfoundation.yawl.bridge.erlang.ErlDouble) {
            return ((org.yawlfoundation.yawl.bridge.erlang.ErlDouble) term).getValue();
        } else if (term instanceof ErlList) {
            return ((ErlList) term).getElements();
        } else if (term instanceof ErlTuple) {
            return ((ErlTuple) term).getElements();
        } else {
            return term.toString();
        }
    }
}