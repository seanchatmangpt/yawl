/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import org.yawlfoundation.yawl.ggen.memory.ProcessKnowledgeGraph;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlValidator;
import org.yawlfoundation.yawl.ggen.powl.ValidationReport;
import org.yawlfoundation.yawl.ggen.rl.scoring.RewardFunction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * GRPO (Group Relative Policy Optimization) optimizer for POWL model generation.
 *
 * <p>For each process description:
 * <ol>
 *   <li>Sample K POWL candidates using the configured CandidateSampler</li>
 *   <li>Filter to structurally valid candidates (PowlValidator)</li>
 *   <li>Score each candidate with the RewardFunction</li>
 *   <li>Compute GroupAdvantage: advantage_i = (reward_i - mean) / (std + ε)</li>
 *   <li>Return the candidate with the highest advantage</li>
 *   <li>Update the {@link ProcessKnowledgeGraph} with this round's evaluated candidates</li>
 * </ol>
 *
 * <p>This is inference-time optimization (best-of-K with GRPO scoring).
 * The policy is the LLM + prompt template; the policy update is not performed
 * here — only candidate selection using the GRPO advantage estimator.
 *
 * <h2>OpenSage — ProcessKnowledgeGraph integration</h2>
 * <p>After each {@link #optimize} call, the evaluated {@link CandidateSet} is written to
 * the shared {@link ProcessKnowledgeGraph}. High-reward patterns accumulate in the graph
 * and are recalled by {@link OllamaCandidateSampler} in subsequent rounds to bias
 * generation toward novel, unexplored process patterns. This is OpenSage's "long-term
 * memory" loop: reward → graph → recall → generation → reward.</p>
 */
public class GrpoOptimizer {

    private final CandidateSampler sampler;
    private final RewardFunction rewardFunction;
    private final PowlValidator validator;
    private final RlConfig config;
    private final ProcessKnowledgeGraph knowledgeGraph;

    /**
     * Constructs a GrpoOptimizer without a shared knowledge graph.
     * Cross-round memory is private to this optimizer and not visible to the sampler.
     *
     * @param sampler        candidate sampler; must not be null
     * @param rewardFunction scoring function; must not be null
     * @param config         RL configuration; must not be null
     */
    public GrpoOptimizer(CandidateSampler sampler, RewardFunction rewardFunction, RlConfig config) {
        this(sampler, rewardFunction, config, new ProcessKnowledgeGraph());
    }

    /**
     * Constructs a GrpoOptimizer with an injected knowledge graph.
     *
     * <p>Pass the <em>same</em> {@link ProcessKnowledgeGraph} instance to both this
     * optimizer and the {@link OllamaCandidateSampler} to enable the full OpenSage
     * memory loop: the optimizer writes high-reward patterns, the sampler reads them
     * to bias subsequent generation prompts.
     *
     * @param sampler        candidate sampler; must not be null
     * @param rewardFunction scoring function; must not be null
     * @param config         RL configuration; must not be null
     * @param knowledgeGraph process pattern memory; must not be null
     */
    public GrpoOptimizer(CandidateSampler sampler, RewardFunction rewardFunction,
                         RlConfig config, ProcessKnowledgeGraph knowledgeGraph) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.rewardFunction = Objects.requireNonNull(rewardFunction, "rewardFunction");
        this.config = Objects.requireNonNull(config, "config");
        this.knowledgeGraph = Objects.requireNonNull(knowledgeGraph, "knowledgeGraph");
        this.validator = new PowlValidator();
    }

    /**
     * Optimizes POWL generation for a process description using GRPO selection.
     *
     * <p>After evaluation, the full {@link CandidateSet} (including rewards) is written
     * to the knowledge graph so that future rounds benefit from accumulated memory.</p>
     *
     * @param processDescription natural language process description
     * @return the best POWL model (highest GRPO advantage)
     * @throws IOException        if the LLM call fails
     * @throws PowlParseException if all candidates fail to parse
     * @throws IllegalArgumentException if processDescription is blank
     */
    public PowlModel optimize(String processDescription) throws IOException, PowlParseException {
        CandidateSet evaluated = evaluateCandidates(processDescription);
        // Update cross-round memory with this round's results (OpenSage long-term memory loop)
        knowledgeGraph.remember(evaluated);
        return evaluated.best();
    }

    /**
     * Evaluates all K candidates and returns them with their scores.
     * Useful for diagnostics and understanding the candidate distribution.
     *
     * @param processDescription natural language process description
     * @return CandidateSet with all candidates and their reward scores
     * @throws IOException        if the LLM call fails
     * @throws PowlParseException if all candidates fail to parse
     */
    public CandidateSet evaluateCandidates(String processDescription)
            throws IOException, PowlParseException {
        if (processDescription == null || processDescription.isBlank()) {
            throw new IllegalArgumentException("processDescription must not be blank");
        }

        // Sample K candidates. OllamaCandidateSampler makes the K HTTP calls
        // concurrently via virtual threads internally; GrpoOptimizer stays single-threaded.
        List<PowlModel> rawCandidates = sampler.sample(processDescription, config.k());

        // Filter to structurally valid candidates
        List<PowlModel> validCandidates = new ArrayList<>();
        for (PowlModel candidate : rawCandidates) {
            ValidationReport report = validator.validate(candidate);
            if (report.valid()) {
                validCandidates.add(candidate);
            }
        }

        // If all candidates are invalid, use the raw candidates anyway
        // (better to return something than throw; caller can handle)
        List<PowlModel> candidates = validCandidates.isEmpty() ? rawCandidates : validCandidates;

        // Score each candidate
        List<Double> rewards = new ArrayList<>(candidates.size());
        for (PowlModel candidate : candidates) {
            double reward = rewardFunction.score(candidate, processDescription);
            rewards.add(reward);
        }

        return new CandidateSet(candidates, rewards);
    }
}
