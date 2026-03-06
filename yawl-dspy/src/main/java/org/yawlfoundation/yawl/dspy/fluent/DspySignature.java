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

package org.yawlfoundation.yawl.dspy.fluent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent builder for DSPy signatures.
 *
 * <p>Signatures define the input/output contract of an LLM call.
 * They separate WHAT you want from HOW to prompt for it.
 *
 * <h2>Python Equivalent:</h2>
 * <pre>{@code
 * # Python (class-based)
 * class MySignature(dspy.Signature):
 *     """Predict the answer to a question."""
 *     question: str = dspy.InputField(desc="The question to answer")
 *     answer: str = dspy.OutputField(desc="The predicted answer")
 *
 * # Python (string-based)
 * sig = dspy.Signature("question -> answer")
 * }</pre>
 *
 * <h2>Java Fluent API:</h2>
 * <pre>{@code
 * // Java (fluent builder)
 * DspySignature sig = DspySignature.builder()
 *     .description("Predict the answer to a question")
 *     .input("question", "The question to answer")
 *     .output("answer", "The predicted answer")
 *     .build();
 *
 * // Java (string shorthand)
 * DspySignature sig = DspySignature.of("question -> answer");
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DspySignature {

    private final String description;
    private final List<Field> inputs;
    private final List<Field> outputs;
    private final @Nullable String instructions;

    private DspySignature(Builder builder) {
        this.description = Objects.requireNonNull(builder.description, "description is required");
        this.inputs = List.copyOf(builder.inputs);
        this.outputs = List.copyOf(builder.outputs);
        this.instructions = builder.instructions;

        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("At least one input field is required");
        }
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("At least one output field is required");
        }
    }

    /**
     * Field in a signature (input or output).
     */
    public record Field(String name, @Nullable String description, @Nullable Class<?> type) {
        public Field {
            Objects.requireNonNull(name, "field name is required");
        }

        public static Field of(String name) {
            return new Field(name, null, null);
        }

        public static Field of(String name, String description) {
            return new Field(name, description, null);
        }

        public static Field of(String name, String description, Class<?> type) {
            return new Field(name, description, type);
        }
    }

    /**
     * Create a signature from a string shorthand.
     *
     * <p>Python: {@code dspy.Signature("question -> answer")}
     * <p>Python: {@code dspy.Signature("context, question -> answer, confidence")}
     *
     * @param shorthand Input/output specification (e.g., "question -> answer")
     * @return the signature
     */
    public static DspySignature of(String shorthand) {
        Objects.requireNonNull(shorthand, "shorthand is required");

        String[] parts = shorthand.split("->");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                "Invalid signature shorthand: '" + shorthand + "'. Expected format: 'input -> output'");
        }

        String[] inputNames = parts[0].trim().split(",");
        String[] outputNames = parts[1].trim().split(",");

        Builder builder = builder().description(shorthand);

        for (String name : inputNames) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                builder.input(trimmed);
            }
        }

        for (String name : outputNames) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                builder.output(trimmed);
            }
        }

        return builder.build();
    }

    /**
     * Create a new builder for DspySignature.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Human-readable description of what this signature does.
     */
    public String description() {
        return description;
    }

    /**
     * Input fields.
     */
    public List<Field> inputs() {
        return inputs;
    }

    /**
     * Output fields.
     */
    public List<Field> outputs() {
        return outputs;
    }

    /**
     * Optional instructions for the LLM.
     */
    public @Nullable String instructions() {
        return instructions;
    }

    /**
     * Get input field names as a list.
     */
    public List<String> inputNames() {
        return inputs.stream().map(Field::name).toList();
    }

    /**
     * Get output field names as a list.
     */
    public List<String> outputNames() {
        return outputs.stream().map(Field::name).toList();
    }

    /**
     * Convert to the internal Signature type for execution.
     */
    public org.yawlfoundation.yawl.dspy.signature.Signature toSignature() {
        var builder = org.yawlfoundation.yawl.dspy.signature.Signature.builder()
            .description(description);

        for (Field input : inputs) {
            builder.input(input.name(),
                input.description != null ? input.description : input.name,
                input.type != null ? input.type : String.class);
        }

        for (Field output : outputs) {
            builder.output(output.name(),
                output.description != null ? output.description : output.name,
                output.type != null ? output.type : String.class);
        }

        if (instructions != null) {
            builder.instructions(instructions);
        }

        return builder.build();
    }

    /**
     * Convert to map for serialization to Python/Erlang.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("description", description);

        List<Map<String, Object>> inputList = new ArrayList<>();
        for (Field f : inputs) {
            Map<String, Object> fieldMap = new LinkedHashMap<>();
            fieldMap.put("name", f.name);
            if (f.description != null) fieldMap.put("description", f.description);
            inputList.add(fieldMap);
        }
        map.put("inputs", inputList);

        List<Map<String, Object>> outputList = new ArrayList<>();
        for (Field f : outputs) {
            Map<String, Object> fieldMap = new LinkedHashMap<>();
            fieldMap.put("name", f.name);
            if (f.description != null) fieldMap.put("description", f.description);
            outputList.add(fieldMap);
        }
        map.put("outputs", outputList);

        if (instructions != null) {
            map.put("instructions", instructions);
        }

        return map;
    }

    /**
     * Generate a unique ID for this signature.
     */
    public String id() {
        return "%s(%s)->(%s)".formatted(
            description.replaceAll("\\s+", "_").toLowerCase(),
            String.join(",", inputNames()),
            String.join(",", outputNames())
        );
    }

    @Override
    public String toString() {
        return String.join(", ", inputNames()) + " -> " + String.join(", ", outputNames());
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    public static final class Builder {
        private String description;
        private final List<Field> inputs = new ArrayList<>();
        private final List<Field> outputs = new ArrayList<>();
        private @Nullable String instructions;

        private Builder() {}

        /**
         * Set the description.
         *
         * <p>Python docstring in class-based signature.
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Add an input field.
         *
         * <p>Python: {@code question: str = dspy.InputField()}
         */
        public Builder input(String name) {
            this.inputs.add(Field.of(name));
            return this;
        }

        /**
         * Add an input field with description.
         *
         * <p>Python: {@code question: str = dspy.InputField(desc="The question")}
         */
        public Builder input(String name, String description) {
            this.inputs.add(Field.of(name, description));
            return this;
        }

        /**
         * Add an input field with description and type.
         */
        public Builder input(String name, String description, Class<?> type) {
            this.inputs.add(Field.of(name, description, type));
            return this;
        }

        /**
         * Add an output field.
         *
         * <p>Python: {@code answer: str = dspy.OutputField()}
         */
        public Builder output(String name) {
            this.outputs.add(Field.of(name));
            return this;
        }

        /**
         * Add an output field with description.
         *
         * <p>Python: {@code answer: str = dspy.OutputField(desc="The answer")}
         */
        public Builder output(String name, String description) {
            this.outputs.add(Field.of(name, description));
            return this;
        }

        /**
         * Add an output field with description and type.
         */
        public Builder output(String name, String description, Class<?> type) {
            this.outputs.add(Field.of(name, description, type));
            return this;
        }

        /**
         * Set instructions for the LLM.
         */
        public Builder instructions(@Nullable String instructions) {
            this.instructions = instructions;
            return this;
        }

        public DspySignature build() {
            return new DspySignature(this);
        }
    }
}
