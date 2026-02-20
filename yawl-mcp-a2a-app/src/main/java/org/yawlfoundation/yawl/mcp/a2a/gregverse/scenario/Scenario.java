/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a multi-agent scenario consisting of coordinated steps.
 *
 * <p>A Scenario defines a workflow where multiple Greg-Verse agents collaborate
 * to accomplish a complex task. Each step in the scenario is assigned to a
 * specific agent and may have dependencies on other steps.</p>
 *
 * <h2>Scenario Properties</h2>
 * <ul>
 *   <li>{@code id} - Unique identifier for the scenario</li>
 *   <li>{@code name} - Human-readable name</li>
 *   <li>{@code description} - Detailed description of the scenario purpose</li>
 *   <li>{@code steps} - Ordered list of steps to execute</li>
 *   <li>{@code agentIds} - Set of agent IDs participating in this scenario</li>
 *   <li>{@code timeout} - Global timeout for the entire scenario in milliseconds</li>
 *   <li>{@code compensationEnabled} - Whether to enable compensation on failures</li>
 * </ul>
 *
 * @param id unique identifier for this scenario
 * @param name human-readable name
 * @param description detailed description of the scenario
 * @param steps ordered list of steps to execute
 * @param agentIds set of participating agent IDs
 * @param timeout global timeout in milliseconds
 * @param compensationEnabled whether compensation is enabled on failures
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record Scenario(
    String id,
    String name,
    String description,
    List<ScenarioStep> steps,
    Set<String> agentIds,
    long timeout,
    boolean compensationEnabled
) {

    /**
     * Compact constructor that validates and normalizes scenario properties.
     *
     * @throws IllegalArgumentException if id is null or blank, or steps is null
     */
    public Scenario {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Scenario id cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            name = id;
        }
        steps = steps != null ? List.copyOf(steps) : List.of();
        agentIds = agentIds != null ? Set.copyOf(agentIds) : Set.of();
    }

    /**
     * Creates a builder for constructing Scenario instances.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns all steps assigned to a specific agent.
     *
     * <p>This method filters the scenario steps to return only those
     * that are assigned to execute on the specified agent.</p>
     *
     * @param agentId the agent identifier to filter by
     * @return a list of steps for the specified agent, empty if none found
     */
    public List<ScenarioStep> getStepsForAgent(String agentId) {
        if (agentId == null) {
            return List.of();
        }
        return steps.stream()
            .filter(step -> agentId.equals(step.agentId()))
            .toList();
    }

    /**
     * Returns the step dependencies as a map.
     *
     * <p>The dependency map represents which steps must complete before
     * other steps can begin. The key is a step ID, and the value is the
     * set of step IDs that depend on it.</p>
     *
     * <p>Dependencies are inferred from the step ordering and target agent
     * relationships. A step that specifies a target agent creates a dependency
     * where the target step must exist in the scenario.</p>
     *
     * @return a map of step ID to set of dependent step IDs
     */
    public Map<String, Set<String>> getDependencies() {
        Map<String, Set<String>> dependencies = new HashMap<>();

        Map<String, Integer> stepIndex = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            stepIndex.put(steps.get(i).id(), i);
        }

        for (int i = 0; i < steps.size(); i++) {
            ScenarioStep currentStep = steps.get(i);
            String currentId = currentStep.id();

            if (i > 0) {
                ScenarioStep previousStep = steps.get(i - 1);
                String previousId = previousStep.id();

                if (previousStep.getTargetAgent().isPresent()
                        && previousStep.getTargetAgent().get().equals(currentStep.agentId())) {
                    dependencies.computeIfAbsent(previousId, k -> new HashSet<>()).add(currentId);
                }

                if (currentStep.agentId().equals(previousStep.agentId())) {
                    dependencies.computeIfAbsent(previousId, k -> new HashSet<>()).add(currentId);
                }
            }

            final int currentIndex = i;
            currentStep.getTargetAgent().ifPresent(targetAgent -> {
                for (int j = currentIndex + 1; j < steps.size(); j++) {
                    if (steps.get(j).agentId().equals(targetAgent)) {
                        dependencies.computeIfAbsent(currentId, k -> new HashSet<>()).add(steps.get(j).id());
                        break;
                    }
                }
            });
        }

        return Collections.unmodifiableMap(dependencies);
    }

    /**
     * Returns the first step in the scenario.
     *
     * @return the first step, or null if the scenario has no steps
     */
    public ScenarioStep getFirstStep() {
        return steps.isEmpty() ? null : steps.getFirst();
    }

    /**
     * Returns the last step in the scenario.
     *
     * @return the last step, or null if the scenario has no steps
     */
    public ScenarioStep getLastStep() {
        return steps.isEmpty() ? null : steps.getLast();
    }

    /**
     * Returns a step by its identifier.
     *
     * @param stepId the step identifier
     * @return the step, or null if not found
     */
    public ScenarioStep getStep(String stepId) {
        return steps.stream()
            .filter(step -> stepId.equals(step.id()))
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns the total number of steps in the scenario.
     *
     * @return the step count
     */
    public int getStepCount() {
        return steps.size();
    }

    /**
     * Returns the number of unique agents participating in this scenario.
     *
     * @return the agent count
     */
    public int getAgentCount() {
        return agentIds.size();
    }

    /**
     * Checks if a specific agent participates in this scenario.
     *
     * @param agentId the agent identifier to check
     * @return true if the agent participates in any step
     */
    public boolean hasAgent(String agentId) {
        return agentIds.contains(agentId);
    }

    /**
     * Builder class for constructing Scenario instances.
     */
    public static class Builder {
        private String id;
        private String name;
        private String description;
        private final List<ScenarioStep> steps = new ArrayList<>();
        private final Set<String> agentIds = new HashSet<>();
        private long timeout = 60000;
        private boolean compensationEnabled = true;

        /**
         * Sets the scenario identifier.
         *
         * @param id the unique scenario identifier
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the scenario name.
         *
         * @param name the human-readable name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the scenario description.
         *
         * @param description the detailed description
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Adds a step to the scenario.
         *
         * @param step the step to add
         * @return this builder
         */
        public Builder addStep(ScenarioStep step) {
            this.steps.add(step);
            this.agentIds.add(step.agentId());
            return this;
        }

        /**
         * Sets all steps for the scenario.
         *
         * @param steps the list of steps
         * @return this builder
         */
        public Builder steps(List<ScenarioStep> steps) {
            this.steps.clear();
            if (steps != null) {
                for (ScenarioStep step : steps) {
                    addStep(step);
                }
            }
            return this;
        }

        /**
         * Adds an agent to the scenario.
         *
         * @param agentId the agent identifier
         * @return this builder
         */
        public Builder addAgent(String agentId) {
            this.agentIds.add(agentId);
            return this;
        }

        /**
         * Sets the participating agents.
         *
         * @param agentIds the set of agent identifiers
         * @return this builder
         */
        public Builder agentIds(Set<String> agentIds) {
            this.agentIds.clear();
            if (agentIds != null) {
                this.agentIds.addAll(agentIds);
            }
            return this;
        }

        /**
         * Sets the global timeout.
         *
         * @param timeout timeout in milliseconds
         * @return this builder
         */
        public Builder timeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets whether compensation is enabled.
         *
         * @param enabled true to enable compensation
         * @return this builder
         */
        public Builder compensationEnabled(boolean enabled) {
            this.compensationEnabled = enabled;
            return this;
        }

        /**
         * Builds the Scenario instance.
         *
         * @return a new Scenario
         * @throws IllegalArgumentException if required fields are missing
         */
        public Scenario build() {
            return new Scenario(id, name, description, steps, agentIds, timeout, compensationEnabled);
        }
    }
}
