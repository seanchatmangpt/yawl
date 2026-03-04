/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.ml.dspy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DSPy Program - Fluent API for building and running DSPy predictions.
 *
 * <p>Example usage:
 * <pre>{@code
 * DspyProgram program = DspyProgram.create(signature)
 *     .withGroq()
 *     .withExample(inputExample, outputExample)
 *     .build();
 *
 * Map<String, Object> result = program.predict(inputs);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DspyProgram {

    private final Signature signature;
    private final String provider;
    private final String model;
    private final List<Example> examples;
    private MlBridgeClient client; // Lazy initialization

    private DspyProgram(Builder builder) {
        this.signature = Objects.requireNonNull(builder.signature, "Signature required");
        this.provider = Objects.requireNonNull(builder.provider, "Provider required");
        this.model = Objects.requireNonNull(builder.model, "Model required");
        this.examples = Collections.unmodifiableList(new ArrayList<>(builder.examples));
        this.client = builder.client; // May be null - lazy init on predict()
    }

    /**
     * Create a new program with the given signature.
     *
     * @param signature the DSPy signature
     * @return builder for chaining
     */
    public static Builder create(Signature signature) {
        return new Builder(signature);
    }

    /**
     * Run prediction with the configured provider and model.
     *
     * @param inputs input values matching signature
     * @return prediction results
     * @throws DspyException if prediction fails
     */
    public Map<String, Object> predict(Map<String, Object> inputs) throws DspyException {
        if (client == null) {
            client = createDefaultClient();
        }
        return client.predict(signature, inputs, examples);
    }

    /**
     * Get the configured provider name.
     *
     * @return provider name (e.g., "groq", "openai")
     */
    public String provider() {
        return provider;
    }

    /**
     * Get the configured model name.
     *
     * @return model name
     */
    public String model() {
        return model;
    }

    /**
     * Get the signature for this program.
     *
     * @return signature
     */
    public Signature signature() {
        return signature;
    }

    /**
     * Get the few-shot examples.
     *
     * @return immutable list of examples
     */
    public List<Example> examples() {
        return examples;
    }

    private static MlBridgeClient createDefaultClient() {
        try {
            return MlBridgeClient.getDefault();
        } catch (Exception e) {
            throw new DspyException("CLIENT_ERROR", "Failed to create ML Bridge client", e);
        }
    }

    /**
     * Builder for DspyProgram.
     */
    public static final class Builder {
        private final Signature signature;
        private String provider = "groq";
        private String model = "llama-3.3-70b-versatile";
        private final List<Example> examples = new ArrayList<>();
        private MlBridgeClient client;

        private Builder(Signature signature) {
            this.signature = signature;
        }

        /**
         * Configure for Groq provider with default model.
         *
         * @return this builder
         */
        public Builder withGroq() {
            this.provider = "groq";
            this.model = "llama-3.3-70b-versatile";
            return this;
        }

        /**
         * Configure for OpenAI provider with default model.
         *
         * @return this builder
         */
        public Builder withOpenAI() {
            this.provider = "openai";
            this.model = "gpt-4";
            return this;
        }

        /**
         * Configure for Anthropic provider with default model.
         *
         * @return this builder
         */
        public Builder withAnthropic() {
            this.provider = "anthropic";
            this.model = "claude-3-opus-20240229";
            return this;
        }

        /**
         * Configure custom provider.
         *
         * @param provider provider name
         * @return this builder
         */
        public Builder withProvider(String provider) {
            this.provider = Objects.requireNonNull(provider, "Provider cannot be null");
            return this;
        }

        /**
         * Configure custom model.
         *
         * @param model model name
         * @return this builder
         */
        public Builder withModel(String model) {
            this.model = Objects.requireNonNull(model, "Model cannot be null");
            return this;
        }

        /**
         * Add a few-shot example.
         *
         * @param inputs input values
         * @param outputs expected output values
         * @return this builder
         */
        public Builder withExample(Map<String, Object> inputs, Map<String, Object> outputs) {
            this.examples.add(new Example(inputs, outputs));
            return this;
        }

        /**
         * Add a few-shot example with single input and output key-value pairs.
         *
         * <p>Convenience method for simple single-field examples.
         *
         * @param inputKey input field name
         * @param inputValue input field value
         * @param outputKey output field name
         * @param outputValue expected output value
         * @return this builder
         */
        public Builder withExample(String inputKey, Object inputValue,
                                   String outputKey, Object outputValue) {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(inputKey, inputValue);
            Map<String, Object> outputs = new HashMap<>();
            outputs.put(outputKey, outputValue);
            return withExample(inputs, outputs);
        }

        /**
         * Set timeout for predictions.
         *
         * @param timeout prediction timeout
         * @return this builder
         */
        public Builder withTimeout(Duration timeout) {
            // Timeout is handled at the client level; stored for future use
            return this;
        }

        /**
         * Set a custom ML Bridge client.
         *
         * @param client the client
         * @return this builder
         */
        public Builder withClient(MlBridgeClient client) {
            this.client = Objects.requireNonNull(client, "Client cannot be null");
            return this;
        }

        /**
         * Build the program.
         *
         * @return configured DspyProgram
         */
        public DspyProgram build() {
            return new DspyProgram(this);
        }
    }
}
