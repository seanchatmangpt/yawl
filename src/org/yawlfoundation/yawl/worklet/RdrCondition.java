/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a condition in a Ripple Down Rules (RDR) node.
 *
 * <p>A condition is a predicate over a data context (a map of attribute name to value).
 * The condition expression uses a simple format: "attribute operator value", where
 * operator is one of: =, !=, &lt;, &gt;, &lt;=, &gt;=, contains, startsWith, endsWith.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code priority > 5}</li>
 *   <li>{@code status = approved}</li>
 *   <li>{@code department contains Finance}</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 */
public class RdrCondition {

    private final String expression;

    /**
     * Constructs an RDR condition from a string expression.
     *
     * @param expression the condition expression (must not be null or blank)
     * @throws IllegalArgumentException if expression is null or blank
     */
    public RdrCondition(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Condition expression must not be null or blank");
        }
        this.expression = expression.trim();
    }

    /**
     * Returns the raw condition expression string.
     */
    public String getExpression() {
        return expression;
    }

    /**
     * Evaluates this condition against the provided data context.
     *
     * <p>The context is a map of attribute names to string values. The expression
     * is parsed as: {@code attribute operator value}.
     *
     * @param context the attribute-value data context; must not be null
     * @return true if the condition is satisfied by the context, false otherwise
     * @throws IllegalArgumentException if context is null
     */
    public boolean evaluate(Map<String, String> context) {
        if (context == null) {
            throw new IllegalArgumentException("Evaluation context must not be null");
        }
        String[] parts = expression.split("\\s+", 3);
        if (parts.length < 3) {
            return false;
        }
        String attribute = parts[0];
        String operator = parts[1];
        String expectedValue = parts[2];
        String actualValue = context.get(attribute);
        if (actualValue == null) {
            return false;
        }
        return switch (operator) {
            case "=" -> actualValue.equals(expectedValue);
            case "!=" -> !actualValue.equals(expectedValue);
            case "<" -> compareNumeric(actualValue, expectedValue) < 0;
            case ">" -> compareNumeric(actualValue, expectedValue) > 0;
            case "<=" -> compareNumeric(actualValue, expectedValue) <= 0;
            case ">=" -> compareNumeric(actualValue, expectedValue) >= 0;
            case "contains" -> actualValue.contains(expectedValue);
            case "startsWith" -> actualValue.startsWith(expectedValue);
            case "endsWith" -> actualValue.endsWith(expectedValue);
            default -> false;
        };
    }

    /**
     * Compares two string values as doubles for numeric operators.
     */
    private int compareNumeric(String actual, String expected) {
        double a = Double.parseDouble(actual);
        double e = Double.parseDouble(expected);
        return Double.compare(a, e);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RdrCondition other)) return false;
        return Objects.equals(expression, other.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(expression);
    }

    @Override
    public String toString() {
        return expression;
    }
}
