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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.safe.agents;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable decision record for traceability.
 *
 * <p>Captures all context for a decision made by a SAFe agent with:
 * <ul>
 *   <li>Decision ID and type (acceptance, prioritization, estimate, etc.)</li>
 *   <li>Agent ID making the decision</li>
 *   <li>Work item ID and related data</li>
 *   <li>Decision rationale and supporting evidence</li>
 *   <li>Timestamp for audit trail</li>
 * </ul>
 *
 * @param id unique decision identifier
 * @param decisionType type of decision (ACCEPT, REJECT, PRIORITIZE, ESTIMATE, BLOCK, UNBLOCK)
 * @param agentId ID of agent making decision
 * @param workItemId ID of related work item
 * @param outcome the decision outcome
 * @param rationale explanation for decision
 * @param evidence supporting data (metrics, criteria met, etc.)
 * @param timestamp when decision was made
 * @since YAWL 6.0
 */
public record AgentDecision(
        String id,
        String decisionType,
        String agentId,
        String workItemId,
        String outcome,
        String rationale,
        Map<String, String> evidence,
        Instant timestamp
) {

    /**
     * Canonical constructor with validation.
     */
    public AgentDecision {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Decision id is required");
        }
        if (decisionType == null || decisionType.isBlank()) {
            throw new IllegalArgumentException("Decision type is required");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("Agent id is required");
        }
        if (outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException("Outcome is required");
        }
        if (evidence == null) {
            evidence = Map.of();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    /**
     * Builder for fluent construction.
     */
    public static Builder builder(String id, String decisionType, String agentId) {
        return new Builder(id, decisionType, agentId);
    }

    /**
     * Fluent builder for {@link AgentDecision}.
     */
    public static final class Builder {
        private final String id;
        private final String decisionType;
        private final String agentId;
        private String workItemId = "";
        private String outcome = "";
        private String rationale = "";
        private Map<String, String> evidence = Map.of();
        private Instant timestamp = Instant.now();

        private Builder(String id, String decisionType, String agentId) {
            this.id = id;
            this.decisionType = decisionType;
            this.agentId = agentId;
        }

        public Builder workItemId(String workItemId) {
            this.workItemId = workItemId;
            return this;
        }

        public Builder outcome(String outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder rationale(String rationale) {
            this.rationale = rationale;
            return this;
        }

        public Builder evidence(Map<String, String> evidence) {
            this.evidence = evidence;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AgentDecision build() {
            return new AgentDecision(id, decisionType, agentId, workItemId,
                    outcome, rationale, evidence, timestamp);
        }
    }

    /**
     * Convert decision to XML for work item output.
     *
     * @return XML representation
     */
    public String toXml() {
        return """
            <Decision>
              <ID>%s</ID>
              <Type>%s</Type>
              <Agent>%s</Agent>
              <WorkItem>%s</WorkItem>
              <Outcome>%s</Outcome>
              <Rationale>%s</Rationale>
              <Timestamp>%s</Timestamp>
            </Decision>
            """.formatted(id, decisionType, agentId, workItemId, outcome, rationale, timestamp);
    }
}
