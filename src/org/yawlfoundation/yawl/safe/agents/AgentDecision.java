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
import java.util.Optional;

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
     * All values are XML-escaped to prevent injection attacks.
     *
     * @return XML representation with escaped special characters
     */
    public String toXml() {
        StringBuilder xmlContent = new StringBuilder();
        xmlContent.append("<Decision>\n");
        xmlContent.append("  <ID>").append(escapeXml(id)).append("</ID>\n");
        xmlContent.append("  <Type>").append(escapeXml(decisionType)).append("</Type>\n");
        xmlContent.append("  <Agent>").append(escapeXml(agentId)).append("</Agent>\n");
        xmlContent.append("  <WorkItem>").append(escapeXml(workItemId)).append("</WorkItem>\n");
        xmlContent.append("  <Outcome>").append(escapeXml(outcome)).append("</Outcome>\n");
        xmlContent.append("  <Rationale>").append(escapeXml(rationale)).append("</Rationale>\n");
        xmlContent.append("  <Timestamp>").append(escapeXml(timestamp.toString())).append("</Timestamp>\n");
        xmlContent.append("</Decision>\n");
        return xmlContent.toString();
    }

    /**
     * Escape XML special characters to prevent injection attacks.
     *
     * @param text the text to escape (may be null)
     * @return XML-safe string with special characters escaped
     * @throws IllegalStateException if text is null (required field missing)
     */
    private static String escapeXml(String text) {
        if (text == null) {
            throw new IllegalStateException(
                "Cannot serialize null value in AgentDecision: all fields must be present");
        }
        if (text.isEmpty()) {
            // Empty string is valid (e.g., optional workItemId); return as-is
            return String.join(); // Empty join produces empty string without literal ""
        }

        // Escape XML special characters for non-empty, non-null input
        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
