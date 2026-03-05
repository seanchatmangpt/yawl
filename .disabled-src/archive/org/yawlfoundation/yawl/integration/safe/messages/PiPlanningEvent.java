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
 * Message for Program Increment (PI) planning events.
 *
 * <p>Communicates quarterly planning objectives, team commitments,
 * dependencies, and risks across the entire agile release train.</p>
 *
 * @since YAWL 6.0
 */
public record PiPlanningEvent(
    String messageId,
    String ceremonyId,
    String fromAgentId,
    Instant createdAt,
    String piId,
    String piName,
    Instant piStartDate,
    Instant piEndDate,
    List<String> piObjectives,
    String releaseTrain,
    List<TeamPlanning> teamCommitments,
    List<String> identifiedRisks,
    String planningStatus,
    String messageType,
    Set<SAFeAgentRole> targetRoles,
    Map<String, Object> payload,
    String correlationId,
    String messagePriority
) implements CeremonyMessage {

    /**
     * Team planning information for PI.
     */
    public record TeamPlanning(
        String teamId,
        String teamName,
        Integer plannedCapacity,
        List<String> committedObjectives,
        List<String> dependencies,
        Double confidenceLevel
    ) {}

    /**
     * Creates a new PI planning event.
     *
     * @return builder for constructing the event
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for PiPlanningEvent.
     */
    public static class Builder {
        private String messageId = UUID.randomUUID().toString();
        private String ceremonyId;
        private String fromAgentId;
        private Instant createdAt = Instant.now();
        private String piId = UUID.randomUUID().toString();
        private String piName;
        private Instant piStartDate;
        private Instant piEndDate;
        private List<String> piObjectives = new ArrayList<>();
        private String releaseTrain;
        private List<TeamPlanning> teamCommitments = new ArrayList<>();
        private List<String> identifiedRisks = new ArrayList<>();
        private String planningStatus = "DRAFT";
        private String messageType = "PI_PLANNING_EVENT";
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
         * Sets PI name (e.g., "PI 2025-Q2").
         */
        public Builder piName(String name) {
            this.piName = name;
            return this;
        }

        /**
         * Sets PI start date.
         */
        public Builder piStartDate(Instant startDate) {
            this.piStartDate = startDate;
            return this;
        }

        /**
         * Sets PI end date.
         */
        public Builder piEndDate(Instant endDate) {
            this.piEndDate = endDate;
            return this;
        }

        /**
         * Adds a PI objective.
         */
        public Builder addObjective(String objective) {
            this.piObjectives.add(objective);
            return this;
        }

        /**
         * Sets all PI objectives.
         */
        public Builder piObjectives(List<String> objectives) {
            this.piObjectives = new ArrayList<>(objectives);
            return this;
        }

        /**
         * Sets release train name.
         */
        public Builder releaseTrain(String name) {
            this.releaseTrain = name;
            return this;
        }

        /**
         * Adds team planning commitment.
         */
        public Builder addTeamCommitment(TeamPlanning planning) {
            this.teamCommitments.add(planning);
            return this;
        }

        /**
         * Sets all team commitments.
         */
        public Builder teamCommitments(List<TeamPlanning> commitments) {
            this.teamCommitments = new ArrayList<>(commitments);
            return this;
        }

        /**
         * Adds identified risk.
         */
        public Builder addRisk(String risk) {
            this.identifiedRisks.add(risk);
            return this;
        }

        /**
         * Sets all identified risks.
         */
        public Builder identifiedRisks(List<String> risks) {
            this.identifiedRisks = new ArrayList<>(risks);
            return this;
        }

        /**
         * Sets planning status (DRAFT, FINAL, APPROVED).
         */
        public Builder planningStatus(String status) {
            this.planningStatus = status;
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
         * Builds the event.
         */
        public PiPlanningEvent build() {
            Objects.requireNonNull(ceremonyId, "ceremonyId is required");
            Objects.requireNonNull(fromAgentId, "fromAgentId is required");

            // Build payload
            payload.putIfAbsent("piId", piId);
            payload.putIfAbsent("piName", piName);
            payload.putIfAbsent("piStartDate", piStartDate);
            payload.putIfAbsent("piEndDate", piEndDate);
            payload.putIfAbsent("piObjectives", piObjectives);
            payload.putIfAbsent("releaseTrain", releaseTrain);
            payload.putIfAbsent("teamCommitments", teamCommitments);
            payload.putIfAbsent("identifiedRisks", identifiedRisks);
            payload.putIfAbsent("planningStatus", planningStatus);

            return new PiPlanningEvent(
                messageId, ceremonyId, fromAgentId, createdAt,
                piId, piName, piStartDate, piEndDate, piObjectives,
                releaseTrain, teamCommitments, identifiedRisks,
                planningStatus, messageType, targetRoles,
                payload, correlationId, messagePriority
            );
        }
    }
}
