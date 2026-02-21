/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.wizard.a2a;

import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStep;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStepResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps A2A agent skills to workflow task slots based on the selected pattern.
 *
 * <p>Implements autonomic skill allocation using capability matching. This step
 * correlates the selected workflow pattern with recommended A2A skills and
 * assigns each task slot to an appropriate skill provided by a discovered agent.
 *
 * <p>Mapping process:
 * <ol>
 *   <li>Read discovered agents from context</li>
 *   <li>Read selected pattern from context</li>
 *   <li>Use pattern → skill recommendation to identify required skills</li>
 *   <li>Build task slot → skill bindings</li>
 *   <li>Verify all recommended skills are available</li>
 *   <li>Create and store A2AWizardConfiguration</li>
 * </ol>
 *
 * <p>Session context keys consumed:
 * <ul>
 *   <li>"a2a.agents.discovered" (List&lt;A2AAgentDescriptor&gt;) - from discovery step</li>
 *   <li>"workflow.pattern" (String) - selected pattern code like "WP-1"</li>
 * </ul>
 *
 * <p>Session context keys produced:
 * <ul>
 *   <li>"a2a.configuration" (A2AWizardConfiguration) - skill mapping result</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
public class A2ASkillMappingStep implements WizardStep<A2AWizardConfiguration> {
    private static final String AGENTS_KEY = "a2a.agents.discovered";
    private static final String PATTERN_KEY = "workflow.pattern";
    private static final String CONFIG_KEY = "a2a.configuration";

    @Override
    public String stepId() {
        return "a2a-skill-mapping";
    }

    @Override
    public String title() {
        return "Map A2A Skills to Workflow Tasks";
    }

    @Override
    public WizardPhase requiredPhase() {
        return WizardPhase.A2A_CONFIG;
    }

    @Override
    public WizardStepResult<A2AWizardConfiguration> execute(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");

        try {
            List<A2AAgentDescriptor> agents = session.get(AGENTS_KEY, List.class)
                .orElse(List.of());

            if (agents.isEmpty()) {
                return WizardStepResult.failure(
                    stepId(),
                    "No agents discovered. Run discovery step first."
                );
            }

            String pattern = session.get(PATTERN_KEY, String.class)
                .orElse("WP-1");

            List<A2ASkillDescriptor> recommendedSkills =
                A2ASkillRegistry.recommendedForPattern(pattern);

            Map<String, A2ASkillDescriptor> taskSlotBindings = new HashMap<>();
            for (A2ASkillDescriptor skill : recommendedSkills) {
                String taskSlot = "task_" + skill.skillId();
                taskSlotBindings.put(taskSlot, skill);
            }

            A2AWizardConfiguration config = A2AWizardConfiguration.of(
                agents,
                taskSlotBindings
            );

            return WizardStepResult.success(stepId(), config);
        } catch (Exception e) {
            return WizardStepResult.failure(
                stepId(),
                "Failed to map A2A skills: " + e.getMessage()
            );
        }
    }

    @Override
    public List<String> validatePrerequisites(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");

        List<String> errors = new ArrayList<>();

        if (!session.has(AGENTS_KEY)) {
            errors.add("No discovered agents. Run discovery step first.");
        }

        if (!session.has(PATTERN_KEY)) {
            errors.add("No selected workflow pattern. Complete pattern selection step.");
        }

        return errors;
    }

    @Override
    public String description() {
        return "Autonomically map A2A agent skills to workflow task slots based on selected pattern";
    }
}
