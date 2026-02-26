/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import org.yawlfoundation.yawl.ggen.powl.*;
import java.util.*;

/**
 * Parses POWL models from LLM text output in s-expression format.
 *
 * <p>Supported format examples:
 * <ul>
 *   <li>{@code ACTIVITY(label)} — a single activity</li>
 *   <li>{@code SEQUENCE(ACTIVITY(a), ACTIVITY(b), ACTIVITY(c))}</li>
 *   <li>{@code XOR(ACTIVITY(b), ACTIVITY(c))}</li>
 *   <li>{@code PARALLEL(ACTIVITY(x), ACTIVITY(y))}</li>
 *   <li>{@code LOOP(ACTIVITY(do), ACTIVITY(redo))}</li>
 *   <li>Nested: {@code SEQUENCE(ACTIVITY(a), XOR(ACTIVITY(b), ACTIVITY(c)), ACTIVITY(d))}</li>
 * </ul>
 *
 * <p>Plain activity labels (no operator) are treated as bare ACTIVITY(label).
 */
public class PowlTextParser {

    private static final Set<String> OPERATORS = Set.of("SEQUENCE", "XOR", "PARALLEL", "LOOP");

    /**
     * Parses a POWL model from LLM text output.
     * Extracts the first POWL expression found in the text.
     *
     * @param text   the raw LLM output text
     * @param modelId the ID to assign to the parsed model
     * @return the parsed PowlModel
     * @throws PowlParseException if no valid POWL expression can be found or parsed
     */
    public PowlModel parse(String text, String modelId) throws PowlParseException {
        if (text == null || text.isBlank()) {
            throw new PowlParseException("Input text is null or blank", text == null ? "" : text);
        }

        // Extract POWL expression from text (first complete balanced expression)
        String expression = extractExpression(text);
        PowlNode root = parseNode(expression, new int[]{0}, modelId);
        return PowlModel.of(modelId, root);
    }

    /**
     * Extracts the first balanced POWL expression from text.
     * Looks for known operator names followed by parentheses.
     */
    private String extractExpression(String text) throws PowlParseException {
        // Find first occurrence of a known operator or a bare label
        for (String op : OPERATORS) {
            int idx = text.indexOf(op + "(");
            if (idx >= 0) {
                // Extract from this position to the matching close paren
                return extractBalanced(text, idx);
            }
        }
        // No operator found — treat entire trimmed text as a bare activity label
        String trimmed = text.trim();
        if (!trimmed.isEmpty()) {
            return "ACTIVITY(" + trimmed.split("[\\n,;]")[0].trim() + ")";
        }
        throw new PowlParseException("No POWL expression found in text", text);
    }

    /**
     * Extracts a balanced parenthesized expression starting at startIdx.
     */
    private String extractBalanced(String text, int startIdx) throws PowlParseException {
        int openParen = text.indexOf('(', startIdx);
        if (openParen < 0) throw new PowlParseException("No opening parenthesis found", text);

        int depth = 0;
        for (int i = openParen; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return text.substring(startIdx, i + 1);
            }
        }
        throw new PowlParseException("Unbalanced parentheses in POWL expression", text);
    }

    /**
     * Recursively parses a POWL node from the expression string.
     * Uses a position array (int[1]) to track current parse position.
     */
    private PowlNode parseNode(String expr, int[] pos, String idPrefix) throws PowlParseException {
        skipWhitespace(expr, pos);
        if (pos[0] >= expr.length()) {
            throw new PowlParseException("Unexpected end of expression", expr);
        }

        // Read operator or label
        String token = readToken(expr, pos);
        skipWhitespace(expr, pos);

        if (OPERATORS.contains(token)) {
            return parseOperator(token, expr, pos, idPrefix);
        } else if ("ACTIVITY".equals(token)) {
            return parseActivity(expr, pos, idPrefix);
        } else {
            // Bare label without ACTIVITY wrapper
            return new PowlActivity(idPrefix + "_act_" + sanitize(token), token);
        }
    }

    private PowlNode parseOperator(String opName, String expr, int[] pos, String idPrefix) throws PowlParseException {
        expect(expr, pos, '(');
        PowlOperatorType type;
        try {
            type = PowlOperatorType.valueOf(opName);
        } catch (IllegalArgumentException e) {
            throw new PowlParseException("Unknown operator: " + opName, expr);
        }

        List<PowlNode> children = new ArrayList<>();
        int childNum = 0;
        while (pos[0] < expr.length() && expr.charAt(pos[0]) != ')') {
            skipWhitespace(expr, pos);
            if (pos[0] < expr.length() && expr.charAt(pos[0]) == ',') {
                pos[0]++; // consume comma
                skipWhitespace(expr, pos);
            }
            if (pos[0] >= expr.length() || expr.charAt(pos[0]) == ')') break;
            children.add(parseNode(expr, pos, idPrefix + "_" + opName.toLowerCase() + childNum));
            childNum++;
        }
        expect(expr, pos, ')');

        if (children.isEmpty()) {
            throw new PowlParseException(opName + " has no children", expr);
        }

        String nodeId = idPrefix + "_" + opName.toLowerCase();
        return new PowlOperatorNode(nodeId, type, children);
    }

    private PowlNode parseActivity(String expr, int[] pos, String idPrefix) throws PowlParseException {
        expect(expr, pos, '(');
        StringBuilder label = new StringBuilder();
        while (pos[0] < expr.length() && expr.charAt(pos[0]) != ')') {
            label.append(expr.charAt(pos[0]++));
        }
        expect(expr, pos, ')');
        String labelStr = label.toString().trim();
        if (labelStr.isEmpty()) {
            throw new PowlParseException("ACTIVITY has empty label", expr);
        }
        return new PowlActivity(idPrefix + "_" + sanitize(labelStr), labelStr);
    }

    private String readToken(String expr, int[] pos) {
        int start = pos[0];
        while (pos[0] < expr.length()) {
            char c = expr.charAt(pos[0]);
            if (c == '(' || c == ')' || c == ',' || Character.isWhitespace(c)) break;
            pos[0]++;
        }
        return expr.substring(start, pos[0]);
    }

    private void skipWhitespace(String expr, int[] pos) {
        while (pos[0] < expr.length() && Character.isWhitespace(expr.charAt(pos[0]))) pos[0]++;
    }

    private void expect(String expr, int[] pos, char expected) throws PowlParseException {
        skipWhitespace(expr, pos);
        if (pos[0] >= expr.length() || expr.charAt(pos[0]) != expected) {
            throw new PowlParseException(
                "Expected '" + expected + "' at position " + pos[0] + " but got: "
                    + (pos[0] < expr.length() ? expr.charAt(pos[0]) : "EOF"), expr);
        }
        pos[0]++;
    }

    private String sanitize(String label) {
        return label.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
    }
}
