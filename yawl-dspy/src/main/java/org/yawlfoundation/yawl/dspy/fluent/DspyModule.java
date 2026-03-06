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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A compiled DSPy module that can make predictions.
 *
 * <p>This is the executable form of a DSPy program. It wraps a signature
 * and optional examples, and can be invoked with inputs to produce outputs.
 *
 * <h2>Python Equivalent:</h2>
 * <pre>{@code
 * # Python
 * predictor = dspy.Predict(signature)
 * result = predictor(question="What is YAWL?")
 *
 * cot = dspy.ChainOfThought(signature)
 * result = cot(question="What is YAWL?")
 * }</pre>
 *
 * <h2>Java Fluent API:</h2>
 * <pre>{@code
 * // Java
 * DspyModule predictor = Dspy.predict(signature);
 * DspyResult result = predictor.predict("question", "What is YAWL?");
 *
 * DspyModule cot = Dspy.chainOfThought(signature);
 * DspyResult result = cot.predict("question", "What is YAWL?");
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DspyModule {

    private static final Logger log = LoggerFactory.getLogger(DspyModule.class);

    /**
     * Module type (mirrors DSPy Python module types).
     */
    public enum Type {
        /**
         * Basic prediction module.
         * Python: {@code dspy.Predict(signature)}
         */
        PREDICT,

        /**
         * Chain-of-thought reasoning.
         * Python: {@code dspy.ChainOfThought(signature)}
         */
        CHAIN_OF_THOUGHT,

        /**
         * ReAct agent with tool use.
         * Python: {@code dspy.ReAct(signature, tools=[])}
         */
        REACT,

        /**
         * Multi-chain comparison.
         * Python: {@code dspy.MultiChainComparison(signature)}
         */
        MULTI_CHAIN,

        /**
         * Custom module.
         */
        CUSTOM
    }

    private final Type type;
    private final DspySignature signature;
    private final List<DspyExample> examples;
    private final @Nullable DspyLM lm;
    private final @Nullable Double temperature;
    private final @Nullable Integer maxTokens;
    private volatile org.yawlfoundation.yawl.dspy.module.Module<?> compiledModule;

    private DspyModule(Builder builder) {
        this.type = builder.type;
        this.signature = Objects.requireNonNull(builder.signature, "signature is required");
        this.examples = List.copyOf(builder.examples);
        this.lm = builder.lm;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
    }

    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Module type.
     */
    public Type type() {
        return type;
    }

    /**
     * Signature defining input/output contract.
     */
    public DspySignature signature() {
        return signature;
    }

    /**
     * Few-shot examples.
     */
    public List<DspyExample> examples() {
        return examples;
    }

    /**
     * Language model configuration (if overridden).
     */
    public @Nullable DspyLM lm() {
        return lm;
    }

    /**
     * Run a prediction with the given inputs.
     *
     * <p>Python: {@code result = predictor(question="What is YAWL?")}
     *
     * @param inputs Map of input field name to value
     * @return The prediction result
     */
    public DspyResult predict(Map<String, Object> inputs) {
        log.debug("Running {} module: {}", type, signature.id());

        long startMs = System.currentTimeMillis();

        try {
            // Get or compile the internal module
            org.yawlfoundation.yawl.dspy.module.Module<?> module = getOrCompileModule();

            // Convert examples
            List<org.yawlfoundation.yawl.dspy.signature.Example> internalExamples =
                examples.stream()
                    .map(DspyExample::toExample)
                    .toList();

            // Add examples if not already set
            if (!internalExamples.isEmpty() && module.examples().isEmpty()) {
                module = module.withExamples(internalExamples);
            }

            // Run the prediction
            org.yawlfoundation.yawl.dspy.signature.SignatureResult result =
                module.run(inputs);

            long latencyMs = System.currentTimeMillis() - startMs;

            // Convert to fluent result
            return DspyResult.builder()
                .values(result.values())
                .rawOutput(result.rawOutput())
                .latencyMs(latencyMs)
                .complete(result.isComplete())
                .build();

        } catch (Exception e) {
            log.error("Prediction failed: {}", e.getMessage(), e);
            throw new DspyException("Prediction failed: " + e.getMessage(), e);
        }
    }

    /**
     * Run a prediction with a single key-value pair.
     *
     * <p>Convenience method for single-input signatures.
     *
     * <p>Python: {@code result = predictor(question="What is YAWL?")}
     */
    public DspyResult predict(String key, Object value) {
        return predict(Map.of(key, value));
    }

    /**
     * Run a prediction with two key-value pairs.
     *
     * <p>Python: {@code result = predictor(question="...", context="...")}
     */
    public DspyResult predict(String key1, Object value1, String key2, Object value2) {
        return predict(Map.of(key1, value1, key2, value2));
    }

    /**
     * Run a prediction with variable key-value pairs.
     */
    public DspyResult predict(Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException(
                "Arguments must be key-value pairs (even number of arguments)");
        }

        Map<String, Object> inputs = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (!(keyValuePairs[i] instanceof String key)) {
                throw new IllegalArgumentException(
                    "Key at position " + i + " must be a String, got: " +
                    (keyValuePairs[i] != null ? keyValuePairs[i].getClass() : "null"));
            }
            inputs.put(key, keyValuePairs[i + 1]);
        }

        return predict(inputs);
    }

    /**
     * Add few-shot examples to this module (returns new module).
     *
     * <p>Python: {@code predictor = predictor.with_examples([...])}
     */
    public DspyModule withExamples(List<DspyExample> examples) {
        return new Builder()
            .type(type)
            .signature(signature)
            .examples(examples)
            .lm(lm)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .build();
    }

    /**
     * Add a single few-shot example (returns new module).
     */
    public DspyModule withExample(DspyExample example) {
        List<DspyExample> newExamples = new ArrayList<>(examples);
        newExamples.add(example);
        return withExamples(newExamples);
    }

    /**
     * Set the temperature for this module (returns new module).
     */
    public DspyModule withTemperature(double temperature) {
        return new Builder()
            .type(type)
            .signature(signature)
            .examples(examples)
            .lm(lm)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .build();
    }

    /**
     * Set max tokens for this module (returns new module).
     */
    public DspyModule withMaxTokens(int maxTokens) {
        return new Builder()
            .type(type)
            .signature(signature)
            .examples(examples)
            .lm(lm)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .build();
    }

    private org.yawlfoundation.yawl.dspy.module.Module<?> getOrCompileModule() {
        if (compiledModule == null) {
            synchronized (this) {
                if (compiledModule == null) {
                    compiledModule = compileModule();
                }
            }
        }
        return compiledModule;
    }

    private org.yawlfoundation.yawl.dspy.module.Module<?> compileModule() {
        org.yawlfoundation.yawl.dspy.signature.Signature internalSig = signature.toSignature();

        // For now, use Predict module (ChainOfThought would be a different implementation)
        // In a full implementation, this would switch based on type
        return switch (type) {
            case PREDICT -> new org.yawlfoundation.yawl.dspy.module.Predict<>(internalSig, null);
            case CHAIN_OF_THOUGHT -> new org.yawlfoundation.yawl.dspy.module.ChainOfThought<>(internalSig, null);
            case REACT, MULTI_CHAIN, CUSTOM ->
                throw new UnsupportedOperationException(
                    "Module type " + type + " not yet implemented in fluent API");
        };
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    public static final class Builder {
        private Type type = Type.PREDICT;
        private DspySignature signature;
        private final List<DspyExample> examples = new ArrayList<>();
        private @Nullable DspyLM lm;
        private @Nullable Double temperature;
        private @Nullable Integer maxTokens;

        private Builder() {}

        /**
         * Set the module type.
         */
        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        /**
         * Set the signature.
         */
        public Builder signature(DspySignature signature) {
            this.signature = signature;
            return this;
        }

        /**
         * Set few-shot examples.
         */
        public Builder examples(List<DspyExample> examples) {
            this.examples.clear();
            this.examples.addAll(examples);
            return this;
        }

        /**
         * Add a few-shot example.
         */
        public Builder example(DspyExample example) {
            this.examples.add(example);
            return this;
        }

        /**
         * Set the language model.
         */
        public Builder lm(@Nullable DspyLM lm) {
            this.lm = lm;
            return this;
        }

        /**
         * Set the temperature.
         */
        public Builder temperature(@Nullable Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Set max tokens.
         */
        public Builder maxTokens(@Nullable Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public DspyModule build() {
            return new DspyModule(this);
        }
    }
}
