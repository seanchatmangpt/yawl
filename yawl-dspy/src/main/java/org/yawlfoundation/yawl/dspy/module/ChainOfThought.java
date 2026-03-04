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
import org.yawlfoundation.yawl.dspy.signature.InputField;
import org.yawlfoundation.yawl.dspy.signature.OutputField;
import org.yawlfoundation.yawl.dspy.signature.Signature;
import org.yawlfoundation.yawl.dspy.signature.SignatureResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chain-of-thought reasoning module - prompts LLM to reason step by step.
 *
 * <p>ChainOfThought extends Predict by adding a reasoning field that
 * encourages the LLM to explain its thought process before producing
 * the final answer. This typically improves performance on complex tasks.
 *
 * <h2>How it works:</h2>
 * <ol>
 *   <li>Adds a "reasoning" output field to the signature</li>
 *   <li>Instructs the LLM to think step-by-step</li>
 *   <li>Parses reasoning and final outputs separately</li>
 * </ol>
 *
 * <h2>Usage:</h2>
 * {@snippet :
 * // Define signature
 * var sig = Signature.builder()
 *     .description("Analyze workflow bottleneck")
 *     .input("taskDurations", "duration of each task", Map.class)
 *     .output("bottleneck", "the bottleneck task", String.class)
 *     .output("suggestion", "improvement suggestion", String.class)
 *     .build();
 *
 * // Create chain-of-thought module
 * var cot = new ChainOfThought<>(sig, llmClient);
 *
 * // Run with reasoning
 * var result = cot.run(Map.of("taskDurations", durations));
 *
 * // Access reasoning
 * String reasoning = result.getString("reasoning");
 * String bottleneck = result.getString("bottleneck");
 * }
 *
 * @param <T> self-referential type for fluent API
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class ChainOfThought<T extends ChainOfThought<T>> implements Module<T> {

    private static final Logger log = LoggerFactory.getLogger(ChainOfThought.class);
    private static final Pattern REASONING_PATTERN = Pattern.compile(
        "(?s)reasoning\\s*:\\s*(.+?)(?=\\n\\s*[a-z_]+\\s*:|$)",
        Pattern.CASE_INSENSITIVE
    );

    private final Signature baseSignature;
    private final Signature cotSignature;
    private final LlmClient llm;
    private final List<Example> examples;
    private final @Nullable Double temperature;
    private final @Nullable Integer maxTokens;

    /**
     * Create a new ChainOfThought module.
     *
     * @param signature the base signature (reasoning field added automatically)
     * @param llm the LLM client for inference
     */
    public ChainOfThought(Signature signature, LlmClient llm) {
        this(signature, llm, List.of(), null, null);
    }

    private ChainOfThought(
        Signature signature,
        LlmClient llm,
        List<Example> examples,
        @Nullable Double temperature,
        @Nullable Integer maxTokens
    ) {
        this.baseSignature = signature;
        this.llm = llm;
        this.examples = examples;
        this.temperature = temperature;
        this.maxTokens = maxTokens;

        // Extend signature with reasoning field
        this.cotSignature = extendWithReasoning(signature);
    }

    private static Signature extendWithReasoning(Signature base) {
        var builder = Signature.builder()
            .description(base.description())
            .instructions(base.instructions() + "\n\nThink step by step. First provide your reasoning, then the final answer.");

        for (InputField in : base.inputs()) {
            builder.input(in);
        }

        // Add reasoning as first output field
        builder.output(OutputField.of("reasoning", "step-by-step reasoning process", String.class, true));

        for (OutputField out : base.outputs()) {
            builder.output(out);
        }

        return builder.build();
    }

    @Override
    public Signature signature() {
        return cotSignature;
    }

    /**
     * Get the base signature without the reasoning field.
     */
    public Signature baseSignature() {
        return baseSignature;
    }

    @Override
    public List<Example> examples() {
        return examples;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T withExamples(List<Example> examples) {
        return (T) new ChainOfThought<>(baseSignature, llm, examples, temperature, maxTokens);
    }

    /**
     * Set the temperature for LLM calls.
     */
    @SuppressWarnings("unchecked")
    public T withTemperature(double temperature) {
        return (T) new ChainOfThought<>(baseSignature, llm, examples, temperature, maxTokens);
    }

    /**
     * Set the maximum tokens for LLM responses.
     */
    @SuppressWarnings("unchecked")
    public T withMaxTokens(int maxTokens) {
        return (T) new ChainOfThought<>(baseSignature, llm, examples, temperature, maxTokens);
    }

    @Override
    public SignatureResult run(Map<String, Object> inputs) {
        log.debug("Running ChainOfThought module: {}", baseSignature.id());

        // Generate prompt with reasoning instructions
        String prompt = buildCotPrompt(inputs);
        log.trace("Generated CoT prompt:\n{}", prompt);

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

            log.debug("LLM call completed in {}ms", response.latencyMs());

        } catch (LlmException e) {
            throw new ModuleException(
                cotSignature.id(),
                cotSignature,
                ModuleException.ErrorKind.LLM_ERROR,
                "LLM call failed: " + e.getMessage(),
                e
            );
        }

        // Parse output with reasoning extraction
        return parseCotOutput(llmOutput);
    }

    private String buildCotPrompt(Map<String, Object> inputs) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Task\n").append(baseSignature.description()).append("\n\n");

        sb.append("# Instructions\n");
        sb.append("Think through this problem step by step.\n");
        sb.append("1. First, analyze the inputs and understand what's being asked.\n");
        sb.append("2. Consider the relevant factors and their relationships.\n");
        sb.append("3. Work through your reasoning step by step.\n");
        sb.append("4. Only after reasoning, provide the final answer.\n\n");

        if (!baseSignature.instructions().isBlank()) {
            sb.append(baseSignature.instructions()).append("\n\n");
        }

        sb.append("# Inputs\n");
        for (InputField in : baseSignature.inputs()) {
            sb.append(in.name()).append(": ");
            if (inputs.containsKey(in.name())) {
                sb.append(formatValue(inputs.get(in.name())));
            }
            sb.append("\n");
        }

        // Add examples with reasoning
        if (!examples.isEmpty()) {
            sb.append("\n# Examples\n");
            for (Example ex : examples) {
                sb.append("\n## Example\n");
                sb.append("Inputs:\n");
                ex.inputs().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
                sb.append("\nReasoning:\n");
                sb.append("  [Step-by-step reasoning here]\n");
                sb.append("\nOutputs:\n");
                ex.outputs().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
            }
        }

        sb.append("\n# Response Format\n");
        sb.append("First provide your reasoning, then provide the outputs.\n\n");
        sb.append("reasoning: [your step-by-step reasoning]\n");
        for (OutputField out : baseSignature.outputs()) {
            sb.append(out.name()).append(": [").append(out.description()).append("]\n");
        }

        return sb.toString();
    }

    private SignatureResult parseCotOutput(String llmOutput) {
        Map<String, Object> values = new LinkedHashMap<>();

        // Extract reasoning
        Matcher reasoningMatcher = REASONING_PATTERN.matcher(llmOutput);
        if (reasoningMatcher.find()) {
            values.put("reasoning", reasoningMatcher.group(1).strip());
        } else {
            // If no explicit reasoning section, treat first paragraph as reasoning
            String[] paragraphs = llmOutput.split("\n\n");
            if (paragraphs.length > 1) {
                values.put("reasoning", paragraphs[0].strip());
            }
        }

        // Parse remaining outputs using base signature
        SignatureResult baseResult = baseSignature.parse(llmOutput);
        values.putAll(baseResult.values());

        return new SignatureResult(values, llmOutput, cotSignature);
    }

    @Override
    public CompiledModule compile() {
        String optimizedPrompt = buildCotPrompt(Map.of());

        List<Map<String, Object>> fewShotExamples = examples.stream()
            .map(ex -> {
                Map<String, Object> combined = new LinkedHashMap<>();
                combined.putAll(ex.inputs());
                combined.put("reasoning", "[reasoning extracted during optimization]");
                combined.putAll(ex.outputs());
                return combined;
            })
            .toList();

        int estimatedTokens = llm.countTokens(optimizedPrompt);

        return new CompiledModule.Single(cotSignature, optimizedPrompt, fewShotExamples, estimatedTokens);
    }

    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) {
            return map.toString();
        }
        if (value instanceof List<?> list) {
            return "[%d items]".formatted(list.size());
        }
        return value.toString();
    }
}
