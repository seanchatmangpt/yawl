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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable descriptor for an A2A (Agent-to-Agent) skill.
 *
 * <p>Describes a capability exposed by an A2A agent, following the A2A protocol
 * skill definition format. Skills are discovered during the DISCOVERY phase and
 * matched to workflow tasks during the A2A_CONFIG phase.
 *
 * @param skillId unique identifier for the skill (e.g. "launch_workflow")
 * @param displayName human-readable skill name (e.g. "Launch Workflow")
 * @param description what this skill does and when to use it
 * @param inputModes list of input modes supported (e.g. ["text", "json"])
 * @param outputModes list of output modes produced (e.g. ["text"])
 * @param category functional domain of the skill
 * @param requiresAuthentication whether the skill requires authentication
 * @param supportsHandoff whether this skill can trigger a work item handoff
 * @param exampleInputs map of example input → expected output for documentation
 *
 * @since YAWL 6.0
 */
public record A2ASkillDescriptor(
    String skillId,
    String displayName,
    String description,
    List<String> inputModes,
    List<String> outputModes,
    A2ASkillCategory category,
    boolean requiresAuthentication,
    boolean supportsHandoff,
    Map<String, String> exampleInputs
) {
    /**
     * Compact constructor ensures immutability of mutable fields.
     */
    public A2ASkillDescriptor {
        Objects.requireNonNull(skillId, "skillId cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(inputModes, "inputModes cannot be null");
        Objects.requireNonNull(outputModes, "outputModes cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(exampleInputs, "exampleInputs cannot be null");

        if (skillId.isBlank()) {
            throw new IllegalArgumentException("skillId cannot be blank");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }

        inputModes = Collections.unmodifiableList(List.copyOf(inputModes));
        outputModes = Collections.unmodifiableList(List.copyOf(outputModes));
        exampleInputs = Collections.unmodifiableMap(Map.copyOf(exampleInputs));
    }

    /**
     * Creates a new skill descriptor with modified example inputs.
     *
     * @param newExamples map of new examples
     * @return new descriptor with updated examples
     */
    public A2ASkillDescriptor withExamples(Map<String, String> newExamples) {
        return new A2ASkillDescriptor(
            this.skillId,
            this.displayName,
            this.description,
            this.inputModes,
            this.outputModes,
            this.category,
            this.requiresAuthentication,
            this.supportsHandoff,
            newExamples
        );
    }

    /**
     * Creates a new skill descriptor with modified category.
     *
     * @param newCategory new category
     * @return new descriptor with updated category
     */
    public A2ASkillDescriptor withCategory(A2ASkillCategory newCategory) {
        return new A2ASkillDescriptor(
            this.skillId,
            this.displayName,
            this.description,
            this.inputModes,
            this.outputModes,
            newCategory,
            this.requiresAuthentication,
            this.supportsHandoff,
            this.exampleInputs
        );
    }
}
