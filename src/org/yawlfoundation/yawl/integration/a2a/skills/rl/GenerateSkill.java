/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.a2a.skills.rl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.ggen.rl.CurriculumStage;
import org.yawlfoundation.yawl.ggen.rl.PowlParseException;
import org.yawlfoundation.yawl.ggen.rl.RlConfig;
import org.yawlfoundation.yawl.ggen.rl.RlGenerationEngine;
import org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;

/**
 * A2A Skill for generating POWL process models via RL-optimized inference.
 *
 * <p>Uses the RlGenerationEngine to synthesize POWL models from natural language
 * process descriptions. Supports two curriculum stages:
 * <ul>
 *   <li>Stage A (VALIDITY_GAP): LLM-as-judge reward for validity</li>
 *   <li>Stage B (BEHAVIORAL_CONSOLIDATION): Footprints agreement reward</li>
 * </ul>
 *
 * <p><b>Required Permission:</b> {@code code:generate}
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class GenerateSkill implements A2ASkill {

    private static final Logger _logger = LogManager.getLogger(GenerateSkill.class);
    private static final String SKILL_ID = "rl_generate";
    private static final String SKILL_NAME = "Generate POWL Model";
    private static final String SKILL_DESCRIPTION =
        "Generate a POWL process model from a natural language description using " +
        "RL-optimized GRPO inference. Supports Stage A (validity) and Stage B (behavioral) curriculum.";

    @Override
    public String getId() {
        return SKILL_ID;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        return SKILL_DESCRIPTION;
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("code:generate");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String processDescription = request.getParameter("processDescription");
        if (processDescription == null || processDescription.isBlank()) {
            _logger.warn("processDescription parameter is required");
            return SkillResult.error("processDescription parameter is required (non-empty string)");
        }

        String stageParam = request.getParameter("stage", "A");
        CurriculumStage stage = "B".equals(stageParam)
            ? CurriculumStage.BEHAVIORAL_CONSOLIDATION
            : CurriculumStage.VALIDITY_GAP;

        _logger.info("Generating POWL model for stage: {}", stage);

        long startTime = System.currentTimeMillis();

        try {
            RlConfig baseConfig = RlConfig.defaults();
            RlConfig config = new RlConfig(
                baseConfig.k(),
                stage,
                baseConfig.maxValidations(),
                baseConfig.ollamaBaseUrl(),
                baseConfig.ollamaModel(),
                baseConfig.timeoutSecs()
            );

            RlGenerationEngine engine = new RlGenerationEngine(config);
            String yawlSpec = engine.generateYawlSpec(processDescription);
            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new HashMap<>();
            result.put("yawlSpec", yawlSpec);
            result.put("stage", stageParam);
            result.put("status", "success");
            result.put("durationMs", String.valueOf(duration));

            _logger.info("POWL generation completed successfully in {}ms", duration);
            return SkillResult.success(result, duration);

        } catch (PowlParseException e) {
            _logger.error("POWL parsing failed: {}", e.getMessage());
            return SkillResult.error("POWL parsing failed: " + e.getMessage());
        } catch (Exception e) {
            _logger.error("Generation failed: {}", e.getMessage());
            return SkillResult.error("Generation failed: " + e.getMessage());
        }
    }
}
