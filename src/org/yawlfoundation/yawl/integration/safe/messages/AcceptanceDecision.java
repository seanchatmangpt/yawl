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

package org.yawlfoundation.yawl.integration.safe.messages;

import java.time.Instant;
import java.util.*;

import org.yawlfoundation.yawl.integration.safe.agent.SAFeAgentRole;

/**
 * Message for acceptance decisions on completed work items.
 *
 * <p>Communicates story/task acceptance status, quality metrics, and
 * acceptance feedback from product owners and QA roles.</p>
 *
 * @since YAWL 6.0
 */
public record AcceptanceDecision(
    String messageId,
    String ceremonyId,
    String fromAgentId,
    Instant createdAt,
    String workItemId,
    String storyId,
    String decisionStatus,
    String decisionReason,
    List<String> acceptanceCriteriaMet,
    List<String> acceptanceCriteriaFailed,
    Double qualityScore,
    List<String> defectsFound,
    String feedbackSummary,
    Instant decisionTime,
    String reviewerRole,
    String messageType,
    Set<SAFeAgentRole> targetRoles,
    Map<String, Object> payload,
    String correlationId,
    String messagePriority
) implements CeremonyMessage {

    /**
     * Creates a new acceptance decision.
     *
     * @return builder for constructing the decision
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AcceptanceDecision.
     */
    public static class Builder {
        private String messageId = UUID.randomUUID().toString();
        private String ceremonyId;
        private String fromAgentId;
        private Instant createdAt = Instant.now();
        private String workItemId;
        private String storyId;
        private String decisionStatus = "PENDING";
        private String decisionReason;
        private List<String> acceptanceCriteriaMet = new ArrayList<>();
        private List<String> acceptanceCriteriaFailed = new ArrayList<>();
        private Double qualityScore = 0.0;
        private List<String> defectsFound = new ArrayList<>();
        private String feedbackSummary;
        private Instant decisionTime = Instant.now();
        private String reviewerRole;
        private String messageType = "ACCEPTANCE_DECISION";
        private Set<SAFeAgentRole> targetRoles = new HashSet<>();
        private Map<String, Object> payload = new HashMap<>();
        private String correlationId;
        private String messagePriority = "HIGH";

        /**
         * Sets ceremony ID.
         */
        public Builder ceremonyId(String ceremonyId) {
            this.ceremonyId = ceremonyId;
            return this;
        }

        /**
         * Sets originating agent ID.
         */
        public Builder fromAgentId(String fromAgentId) {
            this.fromAgentId = fromAgentId;
            return this;
        }

        /**
         * Sets work item ID.
         */
        public Builder workItemId(String workItemId) {
            this.workItemId = workItemId;
            return this;
        }

        /**
         * Sets story ID.
         */
        public Builder storyId(String storyId) {
            this.storyId = storyId;
            return this;
        }

        /**
         * Sets decision status (ACCEPTED, REJECTED, CONDITIONALLY_ACCEPTED).
         */
        public Builder decisionStatus(String status) {
            this.decisionStatus = status;
            return this;
        }

        /**
         * Sets decision reason/explanation.
         */
        public Builder decisionReason(String reason) {
            this.decisionReason = reason;
            return this;
        }

        /**
         * Adds an acceptance criterion that was met.
         */
        public Builder addCriteriaMet(String criterion) {
            this.acceptanceCriteriaMet.add(criterion);
            return this;
        }

        /**
         * Sets all acceptance criteria met.
         */
        public Builder acceptanceCriteriaMet(List<String> criteria) {
            this.acceptanceCriteriaMet = new ArrayList<>(criteria);
            return this;
        }

        /**
         * Adds an acceptance criterion that failed.
         */
        public Builder addCriteriaFailed(String criterion) {
            this.acceptanceCriteriaFailed.add(criterion);
            return this;
        }

        /**
         * Sets all acceptance criteria failed.
         */
        public Builder acceptanceCriteriaFailed(List<String> criteria) {
            this.acceptanceCriteriaFailed = new ArrayList<>(criteria);
            return this;
        }

        /**
         * Sets quality score (0.0-1.0).
         */
        public Builder qualityScore(Double score) {
            this.qualityScore = score;
            return this;
        }

        /**
         * Adds a defect found.
         */
        public Builder addDefect(String defect) {
            this.defectsFound.add(defect);
            return this;
        }

        /**
         * Sets all defects found.
         */
        public Builder defectsFound(List<String> defects) {
            this.defectsFound = new ArrayList<>(defects);
            return this;
        }

        /**
         * Sets feedback summary.
         */
        public Builder feedbackSummary(String summary) {
            this.feedbackSummary = summary;
            return this;
        }

        /**
         * Sets reviewer role.
         */
        public Builder reviewerRole(String role) {
            this.reviewerRole = role;
            return this;
        }

        /**
         * Adds a target role.
         */
        public Builder addTargetRole(SAFeAgentRole role) {
            this.targetRoles.add(role);
            return this;
        }

        /**
         * Sets correlation ID for threading.
         */
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        /**
         * Sets message priority.
         */
        public Builder messagePriority(String priority) {
            this.messagePriority = priority;
            return this;
        }

        /**
         * Builds the decision.
         */
        public AcceptanceDecision build() {
            Objects.requireNonNull(ceremonyId, "ceremonyId is required");
            Objects.requireNonNull(fromAgentId, "fromAgentId is required");

            // Build payload
            payload.putIfAbsent("workItemId", workItemId);
            payload.putIfAbsent("storyId", storyId);
            payload.putIfAbsent("decisionStatus", decisionStatus);
            payload.putIfAbsent("decisionReason", decisionReason);
            payload.putIfAbsent("acceptanceCriteriaMet", acceptanceCriteriaMet);
            payload.putIfAbsent("acceptanceCriteriaFailed", acceptanceCriteriaFailed);
            payload.putIfAbsent("qualityScore", qualityScore);
            payload.putIfAbsent("defectsFound", defectsFound);
            payload.putIfAbsent("feedbackSummary", feedbackSummary);
            payload.putIfAbsent("reviewerRole", reviewerRole);

            return new AcceptanceDecision(
                messageId, ceremonyId, fromAgentId, createdAt,
                workItemId, storyId, decisionStatus, decisionReason,
                acceptanceCriteriaMet, acceptanceCriteriaFailed,
                qualityScore, defectsFound, feedbackSummary,
                decisionTime, reviewerRole, messageType, targetRoles,
                payload, correlationId, messagePriority
            );
        }
    }
}
