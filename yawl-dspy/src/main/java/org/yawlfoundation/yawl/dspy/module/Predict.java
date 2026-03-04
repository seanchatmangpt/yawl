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

package org.yawlfoundation.yawl.dspy.module;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.dspy.llm.LlmClient;
import org.yawlfoundation.yawl.dspy.llm.LlmException;
import org.yawlfoundation.yawl.dspy.llm.LlmRequest;
import org.yawlfoundation.yawl.dspy.llm.LlmResponse;
import org.yawlfoundation.yawl.dspy.signature.Example;
import org.yawlfoundation.yawl.dspy.signature.Signature;
import org.yawlfoundation.yawl.dspy.signature.SignatureResult;

import java.util.List;
import java.util.Map;

/**
 * Basic DSPy predictor module - executes a signature against an LLM.
 *
 * <p>Predict is the fundamental DSPy module that:
 * <ul>
 *   <li>Takes a signature defining input/output contract</li>
 *   <li>Generates a prompt from the signature and input values</li>
 *   <li>Calls an LLM with the prompt</li>
 *   <li>Parses the LLM output into structured results</li>
 * </ul>
 *
 * <h2>Basic Usage:</h2>
 * {@snippet :
 * // Define signature
 * var sig = Signature.builder()
 *     .description("Predict case outcome")
 *     .input("caseEvents", "list of events", List.class)
 *     .output("outcome", "predicted outcome", String.class)
 *     .build();
 *
 * // Create predictor
 * var predictor = new Predict<>(sig, llmClient);
 *
 * // Run prediction
 * var result = predictor.run(Map.of("caseEvents", events));
 * String outcome = result.getString("outcome");
 * }
 *
 * <h2>With Few-Shot Examples:</h2>
 * {@snippet :
 * var predictor = new Predict<>(sig, llmClient)
 *     .addExample(Example.of(
 *         Map.of("caseEvents", completedEvents),
 *         Map.of("outcome", "completed")
 *     ))
 *     .addExample(Example.of(
 *         Map.of("caseEvents", failedEvents),
 *         Map.of("outcome", "failed")
 *     ));
 * }
 *
 * @param <T> self-referential type for fluent API
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class Predict<T extends Predict<T>> implements Module<T> {

    private static final Logger log = LoggerFactory.getLogger(Predict.class);

    private final Signature signature;
    private final LlmClient llm;
    private final List<Example> examples;
    private final @Nullable Double temperature;
    private final @Nullable Integer maxTokens;

    /**
     * Create a new Predict module.
     *
     * @param signature the signature defining input/output contract
     * @param llm the LLM client for inference
     */
    public Predict(Signature signature, LlmClient llm) {
        this(signature, llm, List.of(), null, null);
    }

    private Predict(
        Signature signature,
        LlmClient llm,
        List<Example> examples,
        @Nullable Double temperature,
        @Nullable Integer maxTokens
    ) {
        this.signature = signature;
        this.llm = llm;
        this.examples = examples;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    @Override
    public Signature signature() {
        return signature;
    }

    @Override
    public List<Example> examples() {
        return examples;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T withExamples(List<Example> examples) {
        return (T) new Predict<>(signature, llm, examples, temperature, maxTokens);
    }

    /**
     * Set the temperature for LLM calls.
     */
    @SuppressWarnings("unchecked")
    public T withTemperature(double temperature) {
        return (T) new Predict<>(signature, llm, examples, temperature, maxTokens);
    }

    /**
     * Set the maximum tokens for LLM responses.
     */
    @SuppressWarnings("unchecked")
    public T withMaxTokens(int maxTokens) {
        return (T) new Predict<>(signature, llm, examples, temperature, maxTokens);
    }

    @Override
    public SignatureResult run(Map<String, Object> inputs) {
        log.debug("Running Predict module: {}", signature.id());

        // Validate inputs
        validateInputs(inputs);

        // Build signature with examples
        Signature effectiveSig = signature;
        if (!examples.isEmpty()) {
            effectiveSig = Signature.builder()
                .description(signature.description())
                .instructions(signature.instructions())
                .build();
            for (var in : signature.inputs()) {
                effectiveSig = Signature.builder()
                    .description(effectiveSig.description())
                    .input(in)
                    .build();
            }
            for (var out : signature.outputs()) {
                effectiveSig = Signature.builder()
                    .description(effectiveSig.description())
                    .output(out)
                    .build();
            }
        }

        // Generate prompt
        String prompt = signature.toPrompt(inputs);
        log.trace("Generated prompt:\n{}", prompt);

        // Call LLM
        long startTime = System.currentTimeMillis();
        String llmOutput;
        try {
            var requestBuilder = LlmRequest.builder()
                .prompt(prompt);

            if (temperature != null) {
                requestBuilder.temperature(temperature);
            }
            if (maxTokens != null) {
                requestBuilder.maxTokens(maxTokens);
            }

            LlmResponse response = llm.complete(requestBuilder.build());
            llmOutput = response.text();

            log.debug("LLM call completed in {}ms, {} tokens",
                response.latencyMs(), response.totalTokens());

        } catch (LlmException e) {
            throw new ModuleException(
                signature.id(),
                signature,
                ModuleException.ErrorKind.LLM_ERROR,
                "LLM call failed: " + e.getMessage(),
                e
            );
        }

        // Parse output
        SignatureResult result = signature.parse(llmOutput);

        // Validate result
        if (!result.isComplete()) {
            log.warn("Incomplete result for {}: missing {}",
                signature.id(), result.missingFields());
        }

        return result;
    }

    @Override
    public CompiledModule compile() {
        // Build optimized prompt with examples
        StringBuilder optimizedPrompt = new StringBuilder();
        optimizedPrompt.append("# Task\n").append(signature.description()).append("\n\n");

        if (!signature.instructions().isBlank()) {
            optimizedPrompt.append("# Instructions\n").append(signature.instructions()).append("\n\n");
        }

        // Add input/output format
        optimizedPrompt.append("# Format\n");
        optimizedPrompt.append("Inputs:\n");
        signature.inputs().forEach(in ->
            optimizedPrompt.append("- ").append(in.name()).append(": ").append(in.description()).append("\n"));
        optimizedPrompt.append("\nOutputs:\n");
        signature.outputs().forEach(out ->
            optimizedPrompt.append("- ").append(out.name()).append(": ").append(out.description()).append("\n"));

        // Convert examples to map format for compiled module
        List<Map<String, Object>> fewShotExamples = examples.stream()
            .map(ex -> {
                Map<String, Object> combined = new java.util.LinkedHashMap<>();
                combined.putAll(ex.inputs());
                combined.putAll(ex.outputs());
                return combined;
            })
            .toList();

        int estimatedTokens = llm.countTokens(optimizedPrompt.toString());

        return new CompiledModule.Single(
            signature,
            optimizedPrompt.toString(),
            fewShotExamples,
            estimatedTokens
        );
    }

    private void validateInputs(Map<String, Object> inputs) {
        for (var inputField : signature.inputs()) {
            if (!inputField.optional() && !inputs.containsKey(inputField.name())) {
                throw new ModuleException(
                    signature.id(),
                    signature,
                    ModuleException.ErrorKind.INVALID_INPUT,
                    "Missing required input: " + inputField.name()
                );
            }
        }
    }
}
