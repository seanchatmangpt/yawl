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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.conscience;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable record representing a single agent decision event.
 *
 * <p>Records agent decisions (e.g., routing choices, rejection reasons) with context
 * for later recall and explanation via SPARQL queries. Each decision is timestamped
 * and includes a confidence measure.</p>
 *
 * <p>Use the {@link Builder} for ergonomic construction:
 * <pre>
 *   AgentDecision decision = AgentDecision.builder()
 *       .agentId("agent-1")
 *       .sessionId("session-xyz")
 *       .taskType("routing")
 *       .choiceKey("marketplace_find_for_slot")
 *       .rationale("Selected agent with lowest latency")
 *       .confidence(0.95)
 *       .withContext("selected_agent", "agent-42")
 *       .withContext("latency_ms", "125")
 *       .build();
 * </pre>
 * </p>
 *
 * @since YAWL 6.0
 */
public record AgentDecision(
    String agentId,
    String sessionId,
    String taskType,
    String choiceKey,
    String rationale,
    double confidence,
    Instant timestamp,
    Map<String, String> context
) {

    /**
     * Compact constructor: validates parameters and ensures immutability.
     */
    public AgentDecision {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(taskType, "taskType must not be null");
        Objects.requireNonNull(choiceKey, "choiceKey must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");

        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }

        context = Collections.unmodifiableMap(
            context == null ? Collections.emptyMap() : new HashMap<>(context)
        );
    }

    /**
     * Factory method: creates an AgentDecision with the current time as timestamp.
     *
     * @param agentId    identifier of the agent making this decision
     * @param sessionId  identifier of the session context
     * @param taskType   type of task (e.g., "routing", "selection", "rejection")
     * @param choiceKey  identifier of the choice within the task
     * @param rationale  explanation of the decision
     * @param confidence confidence level (0.0 to 1.0)
     * @param ctx        context key-value pairs
     * @return new AgentDecision with current timestamp
     */
    public static AgentDecision withTimestampNow(
        String agentId,
        String sessionId,
        String taskType,
        String choiceKey,
        String rationale,
        double confidence,
        Map<String, String> ctx
    ) {
        return new AgentDecision(
            agentId,
            sessionId,
            taskType,
            choiceKey,
            rationale,
            confidence,
            Instant.now(),
            ctx == null ? Collections.emptyMap() : ctx
        );
    }

    /**
     * Creates a new builder for constructing AgentDecision instances.
     *
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ergonomic construction of AgentDecision records.
     */
    public static final class Builder {
        private String agentId;
        private String sessionId;
        private String taskType;
        private String choiceKey;
        private String rationale;
        private double confidence;
        private Instant timestamp;
        private Map<String, String> context;

        public Builder() {
            this.confidence = 1.0;
            this.timestamp = Instant.now();
            this.context = new HashMap<>();
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder taskType(String taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder choiceKey(String choiceKey) {
            this.choiceKey = choiceKey;
            return this;
        }

        public Builder rationale(String rationale) {
            this.rationale = rationale;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder withContext(String key, String value) {
            this.context.put(key, value);
            return this;
        }

        public Builder context(Map<String, String> context) {
            this.context = context == null ? new HashMap<>() : new HashMap<>(context);
            return this;
        }

        public AgentDecision build() {
            return new AgentDecision(
                agentId,
                sessionId,
                taskType,
                choiceKey,
                rationale,
                confidence,
                timestamp,
                context
            );
        }
    }
}
