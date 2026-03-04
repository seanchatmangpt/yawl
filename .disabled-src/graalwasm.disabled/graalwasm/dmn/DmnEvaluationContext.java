/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.graalwasm.dmn;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable input context for DMN decision evaluation.
 *
 * <p>Holds a typed map of variable names to values. Variable names correspond
 * to the {@code expressionLanguage} or input expression text in a DMN model
 * (e.g., the input expression {@code "age"} maps to context key {@code "age"}).</p>
 *
 * <h2>Supported value types</h2>
 * <ul>
 *   <li>{@link Number} — integers, doubles, BigDecimal (FEEL numeric)</li>
 *   <li>{@link String} — string values; FEEL string literals without quotes</li>
 *   <li>{@link Boolean} — boolean values</li>
 *   <li>{@code null} — treated as FEEL {@code null}</li>
 * </ul>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * DmnEvaluationContext ctx = DmnEvaluationContext.builder()
 *     .put("age", 35)
 *     .put("riskCategory", "HIGH")
 *     .put("preApproval", true)
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see DmnWasmBridge
 */
public final class DmnEvaluationContext {

    private final Map<String, Object> values;

    private DmnEvaluationContext(Builder builder) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(builder.values));
    }

    /**
     * Returns the value bound to the given variable name.
     *
     * @param name  the variable name; must not be null
     * @return the bound value, or {@code null} if not present
     */
    public @Nullable Object get(String name) {
        return values.get(name);
    }

    /**
     * Returns whether this context contains a binding for the given name.
     *
     * @param name  the variable name; must not be null
     * @return {@code true} if the variable is bound (even to null)
     */
    public boolean contains(String name) {
        return values.containsKey(name);
    }

    /**
     * Returns the set of variable names in this context.
     *
     * @return an unmodifiable set of names; never null
     */
    public Set<String> keySet() {
        return values.keySet();
    }

    /**
     * Returns an unmodifiable view of all variable bindings.
     *
     * @return the bindings map; never null
     */
    public Map<String, Object> asMap() {
        return values;
    }

    /**
     * Returns the number of variable bindings.
     *
     * @return the binding count
     */
    public int size() {
        return values.size();
    }

    /**
     * Returns whether this context has no variable bindings.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Returns a context containing exactly one variable binding.
     *
     * @param name   the variable name; must not be null
     * @param value  the variable value; may be null
     * @return a new DmnEvaluationContext; never null
     */
    public static DmnEvaluationContext of(String name, @Nullable Object value) {
        return builder().put(name, value).build();
    }

    /**
     * Returns an empty evaluation context.
     *
     * @return an empty context; never null
     */
    public static DmnEvaluationContext empty() {
        return builder().build();
    }

    /**
     * Returns a new Builder for constructing a context.
     *
     * @return a new Builder; never null
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmnEvaluationContext that)) return false;
        return Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "DmnEvaluationContext" + values;
    }

    /**
     * Builder for DmnEvaluationContext.
     */
    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Adds or replaces a variable binding.
         *
         * @param name   the variable name; must not be null or blank
         * @param value  the variable value; may be null (FEEL null)
         * @return this builder
         * @throws IllegalArgumentException if name is null or blank
         */
        public Builder put(String name, @Nullable Object value) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Variable name must not be null or blank");
            }
            values.put(name, value);
            return this;
        }

        /**
         * Adds all bindings from the given map.
         *
         * @param bindings  the map of name→value pairs; must not be null
         * @return this builder
         */
        public Builder putAll(Map<String, ?> bindings) {
            Objects.requireNonNull(bindings, "bindings must not be null");
            values.putAll(bindings);
            return this;
        }

        /**
         * Builds the DmnEvaluationContext.
         *
         * @return a new immutable DmnEvaluationContext; never null
         */
        public DmnEvaluationContext build() {
            return new DmnEvaluationContext(this);
        }
    }
}
