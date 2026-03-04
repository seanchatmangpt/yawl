/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.ml.dspy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DSPy Signature - Defines input/output contract for LLM predictions.
 *
 * <p>Represents a DSPy signature that describes what a prediction function
 * takes as input and produces as output.
 *
 * <h2>Example usage:</h2>
 * <pre>{@code
 * Signature signature = Signature.builder()
 *     .description("Predict case outcome")
 *     .input("events", "workflow events", String.class)
 *     .input("duration_ms", Long.class)
 *     .output("outcome", "predicted outcome", String.class)
 *     .output("confidence", Double.class)
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class Signature {

    private final String description;
    private final Map<String, Field> inputs;
    private final Map<String, Field> outputs;

    private Signature(Builder builder) {
        this.description = builder.description;
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(builder.inputs));
        this.outputs = Collections.unmodifiableMap(new LinkedHashMap<>(builder.outputs));
    }

    /**
     * Get the signature description.
     * @return description text
     */
    public String description() {
        return description;
    }

    /**
     * Get all input fields.
     * @return unmodifiable map of input fields
     */
    public Map<String, Field> inputs() {
        return inputs;
    }

    /**
     * Get all output fields.
     * @return unmodifiable map of output fields
     */
    public Map<String, Field> outputs() {
        return outputs;
    }

    /**
     * Convert to JSON representation for DSPy.
     * @return JSON string
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"description\":\"").append(escapeJson(description)).append("\"");

        sb.append(",\"inputs\":[");
        boolean first = true;
        for (Map.Entry<String, Field> entry : inputs.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\"");
            first = false;
        }
        sb.append("]");

        sb.append(",\"outputs\":[");
        first = true;
        for (Map.Entry<String, Field> entry : outputs.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\"");
            first = false;
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Create a new signature builder.
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Signature builder - fluent API for constructing signatures.
     */
    public static final class Builder {
        private String description = "";
        private final Map<String, Field> inputs = new LinkedHashMap<>();
        private final Map<String, Field> outputs = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Set the signature description.
         * @param description description text
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Add an input field with description.
         * @param name field name
         * @param description field description
         * @param type field type
         * @return this builder
         */
        public Builder input(String name, String description, Class<?> type) {
            this.inputs.put(name, new Field(name, description, type));
            return this;
        }

        /**
         * Add an input field without description.
         * @param name field name
         * @param type field type
         * @return this builder
         */
        public Builder input(String name, Class<?> type) {
            return input(name, "", type);
        }

        /**
         * Add an output field with description.
         * @param name field name
         * @param description field description
         * @param type field type
         * @return this builder
         */
        public Builder output(String name, String description, Class<?> type) {
            this.outputs.put(name, new Field(name, description, type));
            return this;
        }

        /**
         * Add an output field without description.
         * @param name field name
         * @param type field type
         * @return this builder
         */
        public Builder output(String name, Class<?> type) {
            return output(name, "", type);
        }

        /**
         * Build the signature.
         * @return immutable signature instance
         */
        public Signature build() {
            if (description.isEmpty()) {
                throw new IllegalStateException("Description is required");
            }
            if (inputs.isEmpty()) {
                throw new IllegalStateException("At least one input field is required");
            }
            if (outputs.isEmpty()) {
                throw new IllegalStateException("At least one output field is required");
            }
            return new Signature(this);
        }
    }

    /**
     * Field definition within a signature.
     */
    public static final class Field {
        private final String name;
        private final String description;
        private final Class<?> type;

        private Field(String name, String description, Class<?> type) {
            this.name = name;
            this.description = description;
            this.type = type;
        }

        public String name() { return name; }
        public String description() { return description; }
        public Class<?> type() { return type; }
    }
}
