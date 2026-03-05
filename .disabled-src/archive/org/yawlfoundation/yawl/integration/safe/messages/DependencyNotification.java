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
 * Message for cross-team dependency notifications in architecture reviews.
 *
 * <p>Communicates dependencies between teams, services, or features that
 * require coordination during release train and PI planning.</p>
 *
 * @since YAWL 6.0
 */
public record DependencyNotification(
    String messageId,
    String ceremonyId,
    String fromAgentId,
    Instant createdAt,
    String dependencyId,
    String dependencyType,
    String sourceTeamId,
    String targetTeamId,
    String description,
    String status,
    Instant targetCompletionDate,
    String riskLevel,
    String mitigation,
    String messageType,
    Set<SAFeAgentRole> targetRoles,
    Map<String, Object> payload,
    String correlationId,
    String messagePriority
) implements CeremonyMessage {

    /**
     * Creates a new dependency notification.
     *
     * @return builder for constructing the notification
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DependencyNotification.
     */
    public static class Builder {
        private String messageId = UUID.randomUUID().toString();
        private String ceremonyId;
        private String fromAgentId;
        private Instant createdAt = Instant.now();
        private String dependencyId = UUID.randomUUID().toString();
        private String dependencyType;
        private String sourceTeamId;
        private String targetTeamId;
        private String description;
        private String status = "OPEN";
        private Instant targetCompletionDate;
        private String riskLevel = "MEDIUM";
        private String mitigation;
        private String messageType = "DEPENDENCY_NOTIFICATION";
        private Set<SAFeAgentRole> targetRoles = new HashSet<>();
        private Map<String, Object> payload = new HashMap<>();
        private String correlationId;
        private String messagePriority = "NORMAL";

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
         * Sets dependency type (FEATURE, SERVICE, DATA, INFRASTRUCTURE, etc.).
         */
        public Builder dependencyType(String type) {
            this.dependencyType = type;
            return this;
        }

        /**
         * Sets source team ID.
         */
        public Builder sourceTeamId(String teamId) {
            this.sourceTeamId = teamId;
            return this;
        }

        /**
         * Sets target team ID.
         */
        public Builder targetTeamId(String teamId) {
            this.targetTeamId = teamId;
            return this;
        }

        /**
         * Sets dependency description.
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets status (OPEN, IN_PROGRESS, RESOLVED, BLOCKED).
         */
        public Builder status(String status) {
            this.status = status;
            return this;
        }

        /**
         * Sets target completion date.
         */
        public Builder targetCompletionDate(Instant date) {
            this.targetCompletionDate = date;
            return this;
        }

        /**
         * Sets risk level (LOW, MEDIUM, HIGH, CRITICAL).
         */
        public Builder riskLevel(String level) {
            this.riskLevel = level;
            return this;
        }

        /**
         * Sets mitigation strategy.
         */
        public Builder mitigation(String mitigation) {
            this.mitigation = mitigation;
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
         * Builds the notification.
         */
        public DependencyNotification build() {
            Objects.requireNonNull(ceremonyId, "ceremonyId is required");
            Objects.requireNonNull(fromAgentId, "fromAgentId is required");

            // Build payload
            payload.putIfAbsent("dependencyId", dependencyId);
            payload.putIfAbsent("dependencyType", dependencyType);
            payload.putIfAbsent("sourceTeamId", sourceTeamId);
            payload.putIfAbsent("targetTeamId", targetTeamId);
            payload.putIfAbsent("description", description);
            payload.putIfAbsent("status", status);
            payload.putIfAbsent("targetCompletionDate", targetCompletionDate);
            payload.putIfAbsent("riskLevel", riskLevel);
            payload.putIfAbsent("mitigation", mitigation);

            return new DependencyNotification(
                messageId, ceremonyId, fromAgentId, createdAt,
                dependencyId, dependencyType, sourceTeamId, targetTeamId,
                description, status, targetCompletionDate, riskLevel,
                mitigation, messageType, targetRoles, payload,
                correlationId, messagePriority
            );
        }
    }
}
