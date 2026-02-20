/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Evaluates cardinality expressions for multi-instance task configuration.
 *
 * <p>Supports WCP-13 through WCP-18 multi-instance patterns by evaluating:
 * - Static integer cardinalities (WCP-13): min=3, max=3
 * - Dynamic XPath queries (WCP-14): max=/net/data/itemCount
 * - Variable references (WCP-14): max=$itemCount
 * - Keywords (WCP-16+): threshold=all, threshold=unbounded</p>
 *
 * <p>Thread-safe and stateless; instances can be reused across multiple
 * evaluations.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class MultiInstanceCardinalityEvaluator {

    private static final Logger log = LogManager.getLogger(MultiInstanceCardinalityEvaluator.class);

    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");
    private static final Pattern XPATH_PATTERN = Pattern.compile("^/.*");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("^\\$\\w+");

    /**
     * Evaluates a cardinality expression to an integer value.
     *
     * <p>Strategy:
     * <ol>
     *   <li>If expression is a valid integer string, parse and return it</li>
     *   <li>If expression is keyword 'all' or 'unbounded', return Integer.MAX_VALUE</li>
     *   <li>If expression is XPath or variable reference, return provided default</li>
     *   <li>Otherwise, return 1 (minimum safe value)</li>
     * </ol></p>
     *
     * @param expression the cardinality expression (may be null)
     * @param defaultValue default value if expression cannot be statically evaluated
     * @return the evaluated integer cardinality, or defaultValue if dynamic
     * @throws IllegalArgumentException if expression is invalid
     */
    public int evaluate(String expression, int defaultValue) {
        if (expression == null || expression.trim().isEmpty()) {
            return 1;
        }

        String trimmed = expression.trim();

        // Static integer cardinality (WCP-13)
        if (INTEGER_PATTERN.matcher(trimmed).matches()) {
            return Integer.parseInt(trimmed);
        }

        // Keywords
        if (trimmed.equalsIgnoreCase("all") || trimmed.equalsIgnoreCase("unbounded")) {
            return Integer.MAX_VALUE;
        }

        // Dynamic cardinality (XPath or variable) — cannot evaluate statically
        if (XPATH_PATTERN.matcher(trimmed).matches() || VARIABLE_PATTERN.matcher(trimmed).matches()) {
            log.debug("Dynamic cardinality expression: {}. Using default: {}", expression, defaultValue);
            return defaultValue;
        }

        log.warn("Unrecognized cardinality expression: {}. Defaulting to 1", expression);
        return 1;
    }

    /**
     * Checks if a cardinality expression is static (can be evaluated at compile time)
     * or dynamic (requires runtime evaluation).
     *
     * @param expression the cardinality expression
     * @return true if the expression is a static integer or keyword, false if dynamic
     */
    public boolean isStatic(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }

        String trimmed = expression.trim();

        // Static if integer or keyword
        return INTEGER_PATTERN.matcher(trimmed).matches()
                || trimmed.equalsIgnoreCase("all")
                || trimmed.equalsIgnoreCase("unbounded");
    }

    /**
     * Checks if a cardinality expression is dynamic (contains XPath or variable reference).
     *
     * @param expression the cardinality expression
     * @return true if the expression contains dynamic references, false otherwise
     */
    public boolean isDynamic(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }

        String trimmed = expression.trim();
        return XPATH_PATTERN.matcher(trimmed).matches() || VARIABLE_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Extracts the XPath or variable name from a dynamic cardinality expression.
     *
     * <p>Examples:
     * <ul>
     *   <li>"/net/data/itemCount" -> "/net/data/itemCount"</li>
     *   <li>"$itemCount" -> "$itemCount"</li>
     *   <li>"5" -> null (not dynamic)</li>
     * </ul></p>
     *
     * @param expression the cardinality expression
     * @return the XPath or variable reference, or null if not dynamic
     */
    public String extractDynamicReference(String expression) {
        if (!isDynamic(expression)) {
            return null;
        }
        return expression.trim();
    }

    /**
     * Validates a cardinality configuration for multi-instance tasks.
     *
     * <p>Checks:
     * <ul>
     *   <li>min <= max (if both are static)</li>
     *   <li>threshold <= max (if both are static)</li>
     *   <li>min, max, threshold are non-negative (if static)</li>
     * </ul></p>
     *
     * @param min minimum instances expression
     * @param max maximum instances expression
     * @param threshold threshold expression
     * @throws IllegalArgumentException if validation fails
     */
    public void validate(String min, String max, String threshold) {
        if (!isStatic(min) || !isStatic(max) || !isStatic(threshold)) {
            log.debug("Skipping static validation for dynamic cardinality expressions");
            return;
        }

        int minVal = evaluate(min, 1);
        int maxVal = evaluate(max, Integer.MAX_VALUE);
        int threshVal = evaluate(threshold, 1);

        if (minVal < 0) {
            throw new IllegalArgumentException("min must be >= 0, got: " + min);
        }

        if (maxVal < 0) {
            throw new IllegalArgumentException("max must be >= 0, got: " + max);
        }

        if (threshVal < 0) {
            throw new IllegalArgumentException("threshold must be >= 0, got: " + threshold);
        }

        if (minVal > maxVal) {
            throw new IllegalArgumentException(
                String.format("min (%d) must be <= max (%d)", minVal, maxVal)
            );
        }

        if (threshVal > maxVal) {
            throw new IllegalArgumentException(
                String.format("threshold (%d) must be <= max (%d)", threshVal, maxVal)
            );
        }
    }

    /**
     * Evaluates multiple cardinality expressions with context data.
     *
     * <p>For dynamic expressions (XPath queries), uses context map to resolve
     * variable values. Static expressions are evaluated independently.</p>
     *
     * @param expressions map of expression names to expressions
     * @param context context map for variable resolution
     * @return map of expression names to evaluated integer values
     */
    public Map<String, Integer> evaluateWithContext(Map<String, String> expressions,
                                                     Map<String, Object> context) {
        return expressions.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> {
                        if (isStatic(e.getValue())) {
                            return evaluate(e.getValue(), 1);
                        } else {
                            return evaluateXPath(e.getValue(), context, 1);
                        }
                    }
                ));
    }

    /**
     * Evaluates an XPath expression against context data.
     *
     * <p>Real implementation: resolves XPath queries against context map.
     * For WCP-14 support, handles queries like "/net/data/itemCount" by
     * navigating the context structure.</p>
     *
     * @param xpath the XPath expression
     * @param context the context data map
     * @param defaultValue default value if evaluation fails
     * @return the evaluated integer value, or defaultValue on error
     */
    private int evaluateXPath(String xpath, Map<String, Object> context, int defaultValue) {
        if (xpath == null || context == null) {
            return defaultValue;
        }

        try {
            // Simple XPath navigation for "/net/data/itemCount" format
            String[] parts = xpath.split("/");
            Object current = context;

            for (String part : parts) {
                if (part.isEmpty()) {
                    continue; // skip empty parts from leading slash
                }

                if (current instanceof Map) {
                    current = ((Map<?, ?>) current).get(part);
                } else {
                    log.warn("Cannot navigate XPath {} in context", xpath);
                    return defaultValue;
                }
            }

            if (current instanceof Number) {
                return ((Number) current).intValue();
            } else if (current instanceof String) {
                return Integer.parseInt((String) current);
            }

            log.warn("XPath {} resolved to non-numeric value: {}", xpath, current);
            return defaultValue;

        } catch (Exception e) {
            log.warn("Failed to evaluate XPath {}: {}", xpath, e.getMessage());
            return defaultValue;
        }
    }
}
