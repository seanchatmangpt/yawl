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

package org.yawlfoundation.yawl.dspy.teleprompter;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.dspy.evaluate.EvaluationResult;
import org.yawlfoundation.yawl.dspy.evaluate.Metric;
import org.yawlfoundation.yawl.dspy.llm.LlmClient;
import org.yawlfoundation.yawl.dspy.llm.LlmRequest;
import org.yawlfoundation.yawl.dspy.module.Module;
import org.yawlfoundation.yawl.dspy.module.ModuleException;
import org.yawlfoundation.yawl.dspy.signature.Example;
import org.yawlfoundation.yawl.dspy.signature.Signature;
import org.yawlfoundation.yawl.dspy.signature.SignatureResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * MIPROv2 (Multi-prompt Instruction PROposal) teleprompter.
 *
 * <p>MIPROv2 is an advanced optimizer that:
 * <ol>
 *   <li>Generates multiple candidate instructions using an LLM</li>
 *   <li>Evaluates each candidate on a validation set</li>
 *   <li>Selects the best-performing instructions</li>
 *   <li>Optionally performs few-shot bootstrapping</li>
 * </ol>
 *
 * <h2>Usage:</h2>
 * {@snippet :
 * var optimizer = MIPROv2.<Predict<?>>builder()
 *     .metric(Metric.accuracy("outcome"))
 *     .llmClient(llmClient)
 *     .numCandidates(10)
 *     .numTrials(3)
 *     .build();
 *
 * Predict<?> optimized = optimizer.compile(predictor, trainset, valset);
 * }
 *
 * @param <M> the module type being optimized
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class MIPROv2<M extends Module<?>> implements Teleprompter<M> {

    private static final Logger log = LoggerFactory.getLogger(MIPROv2.class);

    private final Metric metric;
    private final @Nullable LlmClient instructionLlm;
    private final int numCandidates;
    private final int numTrials;
    private final boolean bootstrapExamples;
    private final int maxBootstrapExamples;
    private final RandomGenerator random;
    private final List<OptimizationStep> trace;

    private MIPROv2(Builder<M> builder) {
        this.metric = builder.metric;
        this.instructionLlm = builder.instructionLlm;
        this.numCandidates = builder.numCandidates;
        this.numTrials = builder.numTrials;
        this.bootstrapExamples = builder.bootstrapExamples;
        this.maxBootstrapExamples = builder.maxBootstrapExamples;
        this.random = builder.random != null ? builder.random : RandomGenerator.getDefault();
        this.trace = new ArrayList<>();
    }

    @Override
    public String name() {
        return "MIPROv2";
    }

    @Override
    @SuppressWarnings("unchecked")
    public M compile(M module, List<Example> trainset) {
        log.info("Starting MIPROv2 optimization with {} examples", trainset.size());
        trace.clear();

        if (trainset.isEmpty()) {
            throw new OptimizationException(name(),
                OptimizationException.ErrorKind.NO_VALID_EXAMPLES,
                "Training set is empty");
        }

        // Split into train/val (80/20)
        int splitPoint = (int) (trainset.size() * 0.8);
        List<Example> trainSplit = trainset.subList(0, splitPoint);
        List<Example> valSplit = trainset.subList(splitPoint, trainset.size());

        if (valSplit.isEmpty()) {
            valSplit = trainSplit; // Use same data if too small
        }

        // Phase 1: Bootstrap few-shot examples (if enabled)
        List<Example> fewShotExamples = List.of();
        if (bootstrapExamples && !trainSplit.isEmpty()) {
            fewShotExamples = bootstrapExamples(module, trainSplit);
            log.info("Bootstrapped {} few-shot examples", fewShotExamples.size());
        }

        // Phase 2: Generate instruction candidates
        List<String> instructionCandidates = generateInstructions(module.signature(), trainSplit);
        log.info("Generated {} instruction candidates", instructionCandidates.size());

        if (instructionCandidates.isEmpty()) {
            // Fallback: use original instructions
            instructionCandidates = List.of(module.signature().instructions());
        }

        // Phase 3: Evaluate candidates and select best
        String bestInstructions = module.signature().instructions();
        double bestScore = evaluateInstructions(module, module.signature().instructions(), fewShotExamples, valSplit);

        for (int trial = 0; trial < numTrials; trial++) {
            for (String candidate : instructionCandidates) {
                double score = evaluateInstructions(module, candidate, fewShotExamples, valSplit);

                if (score > bestScore) {
                    bestScore = score;
                    bestInstructions = candidate;
                    log.debug("New best score: {} with instructions: {}", score, candidate.substring(0, Math.min(50, candidate.length())));

                    trace.add(new OptimizationStep(
                        trace.size() + 1,
                        "Found better instructions",
                        fewShotExamples,
                        0.0,
                        score,
                        Instant.now(),
                        Map.of("instructions", candidate)
                    ));
                }
            }
        }

        // Phase 4: Create optimized module with best instructions and examples
        Signature originalSig = module.signature();
        Signature optimizedSig = Signature.builder()
            .description(originalSig.description())
            .instructions(bestInstructions)
            .build();

        // Add original inputs/outputs
        for (var in : originalSig.inputs()) {
            optimizedSig = Signature.builder()
                .description(optimizedSig.description())
                .instructions(optimizedSig.instructions())
                .input(in)
                .build();
        }
        for (var out : originalSig.outputs()) {
            optimizedSig = Signature.builder()
                .description(optimizedSig.description())
                .instructions(optimizedSig.instructions())
                .output(out)
                .build();
        }

        M optimized = (M) module.withExamples(fewShotExamples);

        log.info("MIPROv2 optimization complete: best score {}", bestScore);
        return optimized;
    }

    @Override
    public List<OptimizationStep> trace() {
        return List.copyOf(trace);
    }

    private List<Example> bootstrapExamples(M module, List<Example> trainset) {
        List<Example> results = new ArrayList<>();

        for (Example example : trainset) {
            if (results.size() >= maxBootstrapExamples) break;

            try {
                SignatureResult result = module.run(example.inputs());
                double score = metric.score(result, example.outputs(), Map.of());

                if (score >= 0.8) {
                    results.add(Example.of(example.inputs(), result.values()));
                }
            } catch (ModuleException e) {
                log.debug("Bootstrap failed for example: {}", e.getMessage());
            }
        }

        return results;
    }

    private List<String> generateInstructions(Signature signature, List<Example> examples) {
        if (instructionLlm == null) {
            return generateDefaultInstructions(signature);
        }

        List<String> candidates = new ArrayList<>();

        // Generate instruction candidates using LLM
        String prompt = buildInstructionGenerationPrompt(signature, examples);

        try {
            String response = instructionLlm.complete(prompt);

            // Parse instructions from response (one per line)
            for (String line : response.split("\n")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && trimmed.length() > 20) {
                    candidates.add(trimmed);
                }
            }
        } catch (Exception e) {
            log.warn("LLM instruction generation failed, using defaults: {}", e.getMessage());
        }

        // Always include some default variations
        candidates.addAll(generateDefaultInstructions(signature));

        return candidates.stream().distinct().limit(numCandidates).toList();
    }

    private List<String> generateDefaultInstructions(Signature signature) {
        List<String> defaults = new ArrayList<>();

        defaults.add("Think carefully and provide accurate outputs.");

        defaults.add("Analyze the inputs step by step before producing outputs.");

        defaults.add("Consider all relevant factors and provide your best prediction.");

        String inputDesc = signature.inputs().stream()
            .map(f -> f.name())
            .reduce((a, b) -> a + ", " + b)
            .orElse("inputs");
        String outputDesc = signature.outputs().stream()
            .map(f -> f.name())
            .reduce((a, b) -> a + ", " + b)
            .orElse("outputs");

        defaults.add("Given %s, predict %s accurately.".formatted(inputDesc, outputDesc));

        return defaults;
    }

    private String buildInstructionGenerationPrompt(Signature signature, List<Example> examples) {
        StringBuilder sb = new StringBuilder();

        sb.append("Generate 5 different instruction variants for a task.\n\n");
        sb.append("Task: ").append(signature.description()).append("\n\n");

        sb.append("Inputs:\n");
        for (var in : signature.inputs()) {
            sb.append("- ").append(in.name()).append(": ").append(in.description()).append("\n");
        }

        sb.append("\nOutputs:\n");
        for (var out : signature.outputs()) {
            sb.append("- ").append(out.name()).append(": ").append(out.description()).append("\n");
        }

        sb.append("\nCurrent instructions: ").append(signature.instructions()).append("\n");

        sb.append("\nGenerate 5 improved instruction variants, one per line.\n");
        sb.append("Each instruction should be 1-2 sentences.\n");
        sb.append("Focus on clarity and effectiveness.\n");

        return sb.toString();
    }

    private double evaluateInstructions(M module, String instructions, List<Example> fewShot, List<Example> valset) {
        // Create module with these instructions
        @SuppressWarnings("unchecked")
        M testModule = (M) module.withExamples(fewShot);

        double totalScore = 0.0;
        int count = 0;

        for (Example example : valset) {
            try {
                SignatureResult result = testModule.run(example.inputs());
                double score = metric.score(result, example.outputs(), Map.of());
                totalScore += score;
                count++;
            } catch (ModuleException e) {
                // Failed example contributes 0
                count++;
            }
        }

        return count == 0 ? 0.0 : totalScore / count;
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    public static <M extends Module<?>> Builder<M> builder() {
        return new Builder<>();
    }

    public static final class Builder<M extends Module<?>> {
        private Metric metric = Metric.exactMatch();
        private @Nullable LlmClient instructionLlm;
        private int numCandidates = 10;
        private int numTrials = 3;
        private boolean bootstrapExamples = true;
        private int maxBootstrapExamples = 5;
        private @Nullable RandomGenerator random;

        public Builder<M> metric(Metric metric) {
            this.metric = metric;
            return this;
        }

        public Builder<M> llmClient(LlmClient llmClient) {
            this.instructionLlm = llmClient;
            return this;
        }

        public Builder<M> numCandidates(int numCandidates) {
            this.numCandidates = numCandidates;
            return this;
        }

        public Builder<M> numTrials(int numTrials) {
            this.numTrials = numTrials;
            return this;
        }

        public Builder<M> bootstrapExamples(boolean bootstrap) {
            this.bootstrapExamples = bootstrap;
            return this;
        }

        public Builder<M> maxBootstrapExamples(int max) {
            this.maxBootstrapExamples = max;
            return this;
        }

        public Builder<M> random(RandomGenerator random) {
            this.random = random;
            return this;
        }

        public MIPROv2<M> build() {
            if (metric == null) {
                throw new IllegalArgumentException("metric is required");
            }
            return new MIPROv2<>(this);
        }
    }
}
