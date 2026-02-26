/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import org.yawlfoundation.yawl.ggen.mining.generators.YawlSpecExporter;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlToYawlConverter;
import org.yawlfoundation.yawl.ggen.rl.scoring.*;
import java.io.IOException;
import java.util.Objects;

/**
 * Top-level RL generation engine for POWL process model synthesis.
 *
 * <p>Implements a two-stage curriculum:
 * <ul>
 *   <li>Stage A (VALIDITY_GAP): Uses LLM-as-judge reward to close the syntax/validity gap</li>
 *   <li>Stage B (BEHAVIORAL_CONSOLIDATION): Uses footprints agreement reward to align behavior</li>
 * </ul>
 *
 * <p>The engine runs the configured stage's GrpoOptimizer to select the best K=4
 * candidate POWL model, then converts it to a YAWL XML specification via
 * PowlToYawlConverter → YawlSpecExporter.
 */
public class RlGenerationEngine {

    private final GrpoOptimizer stageAOptimizer;   // Stage A: LLM judge reward
    private final GrpoOptimizer stageBOptimizer;   // Stage B: footprints agreement reward
    private final YawlSpecExporter exporter;
    private final RlConfig config;

    /**
     * Constructs an RlGenerationEngine for Stage A (LLM judge) only.
     * Use this when footprint reference is not available.
     *
     * @param config RL configuration (stage A or B)
     * @throws IllegalArgumentException if config is null
     */
    public RlGenerationEngine(RlConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.exporter = new YawlSpecExporter();

        OllamaCandidateSampler sampler = new OllamaCandidateSampler(
            config.ollamaBaseUrl(), config.ollamaModel(), config.timeoutSecs());
        LlmJudgeScorer judgeScorer = new LlmJudgeScorer(config.ollamaModel(), config.timeoutSecs());
        CompositeRewardFunction stageAReward = CompositeRewardFunction.stageA(judgeScorer);
        this.stageAOptimizer = new GrpoOptimizer(sampler, stageAReward, config);
        this.stageBOptimizer = null; // Not configured without reference footprints
    }

    /**
     * Constructs an RlGenerationEngine with both Stage A and Stage B configured.
     *
     * @param config             RL configuration
     * @param referenceFootprint behavioral footprint for Stage B scoring
     * @throws IllegalArgumentException if config or referenceFootprint is null
     */
    public RlGenerationEngine(RlConfig config, FootprintMatrix referenceFootprint) {
        this.config = Objects.requireNonNull(config, "config");
        this.exporter = new YawlSpecExporter();
        Objects.requireNonNull(referenceFootprint, "referenceFootprint");

        OllamaCandidateSampler sampler = new OllamaCandidateSampler(
            config.ollamaBaseUrl(), config.ollamaModel(), config.timeoutSecs());

        LlmJudgeScorer judgeScorer = new LlmJudgeScorer(config.ollamaModel(), config.timeoutSecs());
        FootprintScorer footprintScorer = new FootprintScorer(referenceFootprint);

        CompositeRewardFunction stageAReward = CompositeRewardFunction.stageA(judgeScorer);
        CompositeRewardFunction stageBReward = CompositeRewardFunction.stageB(footprintScorer);

        this.stageAOptimizer = new GrpoOptimizer(sampler, stageAReward, config);
        this.stageBOptimizer = new GrpoOptimizer(sampler, stageBReward, config);
    }

    /**
     * Generates a YAWL XML specification for the given process description.
     *
     * <p>Uses the configured curriculum stage:
     * <ul>
     *   <li>VALIDITY_GAP → Stage A optimizer (LLM judge)</li>
     *   <li>BEHAVIORAL_CONSOLIDATION → Stage B optimizer (footprints)</li>
     * </ul>
     *
     * @param processDescription natural language process description
     * @return YAWL XML specification string (validated by YawlSpecExporter)
     * @throws IOException        if the LLM call fails
     * @throws PowlParseException if candidate generation fails
     * @throws IllegalStateException if Stage B requested but no reference footprint configured
     */
    public String generateYawlSpec(String processDescription) throws IOException, PowlParseException {
        if (processDescription == null || processDescription.isBlank()) {
            throw new IllegalArgumentException("processDescription must not be blank");
        }

        GrpoOptimizer optimizer = selectOptimizer();
        PowlModel bestModel = optimizer.optimize(processDescription);
        return convertToYawlXml(bestModel);
    }

    /**
     * Generates a YAWL spec with an explicit FootprintMatrix for Stage B scoring.
     * Overrides the engine's default stage with Stage B.
     *
     * @param processDescription natural language process description
     * @param referenceFootprint behavioral footprint for Stage B scoring
     * @return YAWL XML specification string
     * @throws IOException        if the LLM call fails
     * @throws PowlParseException if candidate generation fails
     */
    public String generateYawlSpec(String processDescription, FootprintMatrix referenceFootprint)
            throws IOException, PowlParseException {
        Objects.requireNonNull(referenceFootprint, "referenceFootprint");
        if (processDescription == null || processDescription.isBlank()) {
            throw new IllegalArgumentException("processDescription must not be blank");
        }

        // Create ad-hoc Stage B optimizer with the provided reference
        OllamaCandidateSampler sampler = new OllamaCandidateSampler(
            config.ollamaBaseUrl(), config.ollamaModel(), config.timeoutSecs());
        FootprintScorer scorer = new FootprintScorer(referenceFootprint);
        GrpoOptimizer optimizer = new GrpoOptimizer(sampler, CompositeRewardFunction.stageB(scorer), config);

        PowlModel bestModel = optimizer.optimize(processDescription);
        return convertToYawlXml(bestModel);
    }

    private GrpoOptimizer selectOptimizer() {
        return switch (config.stage()) {
            case VALIDITY_GAP -> stageAOptimizer;
            case BEHAVIORAL_CONSOLIDATION -> {
                if (stageBOptimizer == null) {
                    throw new IllegalStateException(
                        "Stage B optimizer requires a reference footprint. " +
                        "Use RlGenerationEngine(RlConfig, FootprintMatrix) constructor.");
                }
                yield stageBOptimizer;
            }
        };
    }

    private String convertToYawlXml(PowlModel model) {
        PowlToYawlConverter converter = new PowlToYawlConverter();
        PetriNet petriNet = converter.convert(model);
        return exporter.export(petriNet);
    }
}
