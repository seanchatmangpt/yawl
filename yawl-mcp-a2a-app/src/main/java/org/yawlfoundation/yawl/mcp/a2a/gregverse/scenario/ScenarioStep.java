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

import java.util.Map;
import java.util.Optional;

/**
 * Represents a single step in a multi-agent scenario.
 *
 * <p>A scenario step defines an atomic unit of work that a specific agent
 * performs using a particular skill. Steps can have dependencies on other
 * steps and can target specific agents for handoffs.</p>
 *
 * <h2>Step Properties</h2>
 * <ul>
 *   <li>{@code id} - Unique identifier for the step within the scenario</li>
 *   <li>{@code agentId} - The agent responsible for executing this step</li>
 *   <li>{@code skillId} - The skill to invoke for this step</li>
 *   <li>{@code topic} - The topic or subject matter for the step</li>
 *   <li>{@code context} - Additional context data for the step execution</li>
 *   <li>{@code input} - Input data for the step (can reference previous step outputs)</li>
 *   <li>{@code targetAgent} - Optional agent to handoff to after completion</li>
 *   <li>{@code required} - Whether this step must succeed for scenario completion</li>
 *   <li>{@code timeout} - Maximum time in milliseconds for step execution</li>
 * </ul>
 *
 * @param id unique identifier for this step
 * @param agentId the agent that executes this step
 * @param skillId the skill to invoke
 * @param topic the topic or subject for this step
 * @param context additional context data for step execution
 * @param input input data for the step, may reference previous step outputs
 * @param targetAgent optional agent to handoff to after completion
 * @param required whether this step must succeed for scenario success
 * @param timeout maximum execution time in milliseconds
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record ScenarioStep(
    String id,
    String agentId,
    String skillId,
    String topic,
    Map<String, Object> context,
    Map<String, Object> input,
    String targetAgent,
    boolean required,
    long timeout
) {

    /**
     * Compact constructor that validates step properties.
     *
     * @throws IllegalArgumentException if id or agentId is null or blank
     */
    public ScenarioStep {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Step id cannot be null or blank");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("Agent id cannot be null or blank");
        }
        context = context != null ? Map.copyOf(context) : Map.of();
        input = input != null ? Map.copyOf(input) : Map.of();
    }

    /**
     * Creates a builder for constructing ScenarioStep instances.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the target agent if specified.
     *
     * @return an Optional containing the target agent, or empty if not set
     */
    public Optional<String> getTargetAgent() {
        return Optional.ofNullable(targetAgent);
    }

    /**
     * Returns a context value by key.
     *
     * @param key the context key
     * @return an Optional containing the value, or empty if not present
     */
    public Optional<Object> getContextValue(String key) {
        return Optional.ofNullable(context.get(key));
    }

    /**
     * Returns an input value by key.
     *
     * @param key the input key
     * @return an Optional containing the value, or empty if not present
     */
    public Optional<Object> getInputValue(String key) {
        return Optional.ofNullable(input.get(key));
    }

    /**
     * Returns the effective timeout, using the provided default if this step
     * has no explicit timeout set (timeout = 0).
     *
     * @param defaultTimeout the default timeout in milliseconds
     * @return the effective timeout
     */
    public long getEffectiveTimeout(long defaultTimeout) {
        return timeout > 0 ? timeout : defaultTimeout;
    }

    /**
     * Builder class for constructing ScenarioStep instances.
     */
    public static class Builder {
        private String id;
        private String agentId;
        private String skillId;
        private String topic;
        private Map<String, Object> context = Map.of();
        private Map<String, Object> input = Map.of();
        private String targetAgent;
        private boolean required = true;
        private long timeout;

        /**
         * Sets the step identifier.
         *
         * @param id the unique step identifier
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the agent identifier.
         *
         * @param agentId the agent that executes this step
         * @return this builder
         */
        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        /**
         * Sets the skill identifier.
         *
         * @param skillId the skill to invoke
         * @return this builder
         */
        public Builder skillId(String skillId) {
            this.skillId = skillId;
            return this;
        }

        /**
         * Sets the topic.
         *
         * @param topic the topic or subject for this step
         * @return this builder
         */
        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        /**
         * Sets the context data.
         *
         * @param context additional context for step execution
         * @return this builder
         */
        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        /**
         * Sets the input data.
         *
         * @param input input data for the step
         * @return this builder
         */
        public Builder input(Map<String, Object> input) {
            this.input = input;
            return this;
        }

        /**
         * Sets the target agent for handoff.
         *
         * @param targetAgent the agent to handoff to
         * @return this builder
         */
        public Builder targetAgent(String targetAgent) {
            this.targetAgent = targetAgent;
            return this;
        }

        /**
         * Sets whether this step is required.
         *
         * @param required true if the step must succeed
         * @return this builder
         */
        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets the timeout.
         *
         * @param timeout maximum execution time in milliseconds
         * @return this builder
         */
        public Builder timeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Builds the ScenarioStep instance.
         *
         * @return a new ScenarioStep
         * @throws IllegalArgumentException if required fields are missing
         */
        public ScenarioStep build() {
            return new ScenarioStep(id, agentId, skillId, topic, context, input, targetAgent, required, timeout);
        }
    }
}
