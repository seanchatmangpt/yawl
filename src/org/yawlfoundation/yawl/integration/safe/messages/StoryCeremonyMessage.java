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
 * Message for sprint planning ceremonies with story refinement data.
 *
 * <p>Communicates user stories, acceptance criteria, story points, and
 * task breakdowns during sprint planning sessions.</p>
 *
 * @since YAWL 6.0
 */
public record StoryCeremonyMessage(
    String messageId,
    String ceremonyId,
    String fromAgentId,
    Instant createdAt,
    String storyId,
    String storyTitle,
    String storyDescription,
    List<String> acceptanceCriteria,
    Integer estimatedPoints,
    String priority,
    List<TaskBreakdown> tasks,
    String messageType,
    Set<SAFeAgentRole> targetRoles,
    Map<String, Object> payload,
    String correlationId,
    String messagePriority
) implements CeremonyMessage {

    /**
     * Task breakdown within a story.
     */
    public record TaskBreakdown(
        String taskId,
        String title,
        String description,
        String assigneeRole,
        Integer estimatedHours,
        List<String> dependencies
    ) {}

    /**
     * Creates a new story ceremony message.
     *
     * @return builder for constructing the message
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for StoryCeremonyMessage.
     */
    public static class Builder {
        private String messageId = UUID.randomUUID().toString();
        private String ceremonyId;
        private String fromAgentId;
        private Instant createdAt = Instant.now();
        private String storyId;
        private String storyTitle;
        private String storyDescription;
        private List<String> acceptanceCriteria = new ArrayList<>();
        private Integer estimatedPoints;
        private String priority = "NORMAL";
        private List<TaskBreakdown> tasks = new ArrayList<>();
        private String messageType = "STORY_CEREMONY";
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
         * Sets story ID.
         */
        public Builder storyId(String storyId) {
            this.storyId = storyId;
            return this;
        }

        /**
         * Sets story title.
         */
        public Builder storyTitle(String storyTitle) {
            this.storyTitle = storyTitle;
            return this;
        }

        /**
         * Sets story description.
         */
        public Builder storyDescription(String storyDescription) {
            this.storyDescription = storyDescription;
            return this;
        }

        /**
         * Adds acceptance criteria.
         */
        public Builder addAcceptanceCriterion(String criterion) {
            this.acceptanceCriteria.add(criterion);
            return this;
        }

        /**
         * Sets all acceptance criteria.
         */
        public Builder acceptanceCriteria(List<String> criteria) {
            this.acceptanceCriteria = new ArrayList<>(criteria);
            return this;
        }

        /**
         * Sets estimated story points.
         */
        public Builder estimatedPoints(Integer points) {
            this.estimatedPoints = points;
            return this;
        }

        /**
         * Sets priority (HIGH, NORMAL, LOW).
         */
        public Builder priority(String priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Adds a task breakdown.
         */
        public Builder addTask(TaskBreakdown task) {
            this.tasks.add(task);
            return this;
        }

        /**
         * Sets all tasks.
         */
        public Builder tasks(List<TaskBreakdown> tasks) {
            this.tasks = new ArrayList<>(tasks);
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
         * Builds the message.
         */
        public StoryCeremonyMessage build() {
            Objects.requireNonNull(ceremonyId, "ceremonyId is required");
            Objects.requireNonNull(fromAgentId, "fromAgentId is required");

            // Build payload
            payload.putIfAbsent("storyId", storyId);
            payload.putIfAbsent("storyTitle", storyTitle);
            payload.putIfAbsent("storyDescription", storyDescription);
            payload.putIfAbsent("acceptanceCriteria", acceptanceCriteria);
            payload.putIfAbsent("estimatedPoints", estimatedPoints);
            payload.putIfAbsent("priority", priority);
            payload.putIfAbsent("tasks", tasks);

            return new StoryCeremonyMessage(
                messageId, ceremonyId, fromAgentId, createdAt,
                storyId, storyTitle, storyDescription, acceptanceCriteria,
                estimatedPoints, priority, tasks, messageType, targetRoles,
                payload, correlationId, messagePriority
            );
        }
    }
}
