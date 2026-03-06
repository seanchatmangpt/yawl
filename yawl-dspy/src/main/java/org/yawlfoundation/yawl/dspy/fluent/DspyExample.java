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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent builder for DSPy examples (few-shot training data).
 *
 * <p>Examples are input/output pairs used for:
 * <ul>
 *   <li>Few-shot prompting (providing examples to the LLM)</li>
 *   <li>BootstrapFewShot optimization</li>
 *   <li>Evaluation and testing</li>
 * </ul>
 *
 * <h2>Python Equivalent:</h2>
 * <pre>{@code
 * # Python
 * example = dspy.Example(
 *     question="What is YAWL?",
 *     answer="YAWL is a workflow language"
 * )
 *
 * # With input/output split
 * example = dspy.Example(
 *     question="What is YAWL?",
 *     answer="YAWL is a workflow language"
 * ).with_inputs("question")
 * }</pre>
 *
 * <h2>Java Fluent API:</h2>
 * <pre>{@code
 * // Java
 * DspyExample example = DspyExample.create()
 *     .input("question", "What is YAWL?")
 *     .output("answer", "YAWL is a workflow language");
 *
 * // Or with builder
 * DspyExample example = DspyExample.builder()
 *     .input("question", "What is YAWL?")
 *     .output("answer", "YAWL is a workflow language")
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DspyExample {

    private final Map<String, Object> inputs;
    private final Map<String, Object> outputs;

    private DspyExample(Map<String, Object> inputs, Map<String, Object> outputs) {
        this.inputs = Map.copyOf(inputs);
        this.outputs = Map.copyOf(outputs);
    }

    /**
     * Create a new example builder.
     *
     * <p>Python: {@code dspy.Example(...)}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an empty example (for fluent building).
     */
    public static DspyExample create() {
        return new DspyExample(Map.of(), Map.of());
    }

    /**
     * Create an example from input/output maps.
     */
    public static DspyExample of(Map<String, Object> inputs, Map<String, Object> outputs) {
        return new DspyExample(inputs, outputs);
    }

    /**
     * Create a simple example with single input/output.
     */
    public static DspyExample of(String inputKey, Object inputValue,
                                  String outputKey, Object outputValue) {
        return new DspyExample(
            Map.of(inputKey, inputValue),
            Map.of(outputKey, outputValue)
        );
    }

    /**
     * Input values.
     */
    public Map<String, Object> inputs() {
        return inputs;
    }

    /**
     * Output values.
     */
    public Map<String, Object> outputs() {
        return outputs;
    }

    /**
     * Get a specific input value.
     */
    public Object input(String key) {
        return inputs.get(key);
    }

    /**
     * Get a specific output value.
     */
    public Object output(String key) {
        return outputs.get(key);
    }

    /**
     * Get input as string.
     */
    public String inputString(String key) {
        Object value = inputs.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Get output as string.
     */
    public String outputString(String key) {
        Object value = outputs.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Add an input field (returns new example).
     */
    public DspyExample withInput(String key, Object value) {
        Map<String, Object> newInputs = new LinkedHashMap<>(inputs);
        newInputs.put(key, value);
        return new DspyExample(newInputs, outputs);
    }

    /**
     * Add an output field (returns new example).
     */
    public DspyExample withOutput(String key, Object value) {
        Map<String, Object> newOutputs = new LinkedHashMap<>(outputs);
        newOutputs.put(key, value);
        return new DspyExample(inputs, newOutputs);
    }

    /**
     * Convert to internal Example type.
     */
    public org.yawlfoundation.yawl.dspy.signature.Example toExample() {
        return org.yawlfoundation.yawl.dspy.signature.Example.of(inputs, outputs);
    }

    /**
     * Convert to map for serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("inputs", inputs);
        map.put("outputs", outputs);
        return map;
    }

    @Override
    public String toString() {
        return "DspyExample{" +
            "inputs=" + inputs +
            ", outputs=" + outputs +
            '}';
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    public static final class Builder {
        private final Map<String, Object> inputs = new LinkedHashMap<>();
        private final Map<String, Object> outputs = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Add an input field.
         *
         * <p>Python: {@code question="What is YAWL?"}
         */
        public Builder input(String key, Object value) {
            this.inputs.put(key, value);
            return this;
        }

        /**
         * Add an output field.
         *
         * <p>Python: {@code answer="YAWL is a workflow language"}
         */
        public Builder output(String key, Object value) {
            this.outputs.put(key, value);
            return this;
        }

        /**
         * Add all inputs from a map.
         */
        public Builder inputs(Map<String, Object> inputs) {
            this.inputs.putAll(inputs);
            return this;
        }

        /**
         * Add all outputs from a map.
         */
        public Builder outputs(Map<String, Object> outputs) {
            this.outputs.putAll(outputs);
            return this;
        }

        public DspyExample build() {
            return new DspyExample(inputs, outputs);
        }
    }
}
