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
 * Message for blocker notifications in daily standups.
 *
 * <p>Communicates impediments, blockers, and risks during standup meetings
 * to enable quick escalation and resolution.</p>
 *
 * @since YAWL 6.0
 */
public record BlockerNotification(
    String messageId,
    String ceremonyId,
    String fromAgentId,
    Instant createdAt,
    String blockerId,
    String title,
    String description,
    String severity,
    String status,
    String impactedWorkItemId,
    String requesterRole,
    Instant reportedAt,
    String proposedSolution,
    String assignedToRole,
    String messageType,
    Set<SAFeAgentRole> targetRoles,
    Map<String, Object> payload,
    String correlationId,
    String messagePriority
) implements CeremonyMessage {

    /**
     * Creates a new blocker notification.
     *
     * @return builder for constructing the notification
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for BlockerNotification.
     */
    public static class Builder {
        private String messageId = UUID.randomUUID().toString();
        private String ceremonyId;
        private String fromAgentId;
        private Instant createdAt = Instant.now();
        private String blockerId = UUID.randomUUID().toString();
        private String title;
        private String description;
        private String severity = "MEDIUM";
        private String status = "OPEN";
        private String impactedWorkItemId;
        private String requesterRole;
        private Instant reportedAt = Instant.now();
        private String proposedSolution;
        private String assignedToRole;
        private String messageType = "BLOCKER_NOTIFICATION";
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
         * Sets blocker title.
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Sets blocker description.
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets severity (LOW, MEDIUM, HIGH, CRITICAL).
         */
        public Builder severity(String severity) {
            this.severity = severity;
            return this;
        }

        /**
         * Sets status (OPEN, IN_PROGRESS, RESOLVED, ESCALATED).
         */
        public Builder status(String status) {
            this.status = status;
            return this;
        }

        /**
         * Sets impacted work item ID.
         */
        public Builder impactedWorkItemId(String workItemId) {
            this.impactedWorkItemId = workItemId;
            return this;
        }

        /**
         * Sets requester role.
         */
        public Builder requesterRole(String role) {
            this.requesterRole = role;
            return this;
        }

        /**
         * Sets proposed solution.
         */
        public Builder proposedSolution(String solution) {
            this.proposedSolution = solution;
            return this;
        }

        /**
         * Sets role assigned to resolve blocker.
         */
        public Builder assignedToRole(String role) {
            this.assignedToRole = role;
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
        public BlockerNotification build() {
            Objects.requireNonNull(ceremonyId, "ceremonyId is required");
            Objects.requireNonNull(fromAgentId, "fromAgentId is required");

            // Build payload
            payload.putIfAbsent("blockerId", blockerId);
            payload.putIfAbsent("title", title);
            payload.putIfAbsent("description", description);
            payload.putIfAbsent("severity", severity);
            payload.putIfAbsent("status", status);
            payload.putIfAbsent("impactedWorkItemId", impactedWorkItemId);
            payload.putIfAbsent("requesterRole", requesterRole);
            payload.putIfAbsent("reportedAt", reportedAt);
            payload.putIfAbsent("proposedSolution", proposedSolution);
            payload.putIfAbsent("assignedToRole", assignedToRole);

            return new BlockerNotification(
                messageId, ceremonyId, fromAgentId, createdAt,
                blockerId, title, description, severity, status,
                impactedWorkItemId, requesterRole, reportedAt,
                proposedSolution, assignedToRole, messageType,
                targetRoles, payload, correlationId, messagePriority
            );
        }
    }
}
