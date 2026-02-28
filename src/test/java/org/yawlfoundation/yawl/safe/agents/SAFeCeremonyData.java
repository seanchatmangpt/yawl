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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Test data containers for SAFe ceremony scenarios.
 * Uses Java 25 records for immutable test data with automatic equals/hashCode.
 *
 * @since YAWL 6.0
 */
public class SAFeCeremonyData {

    /**
     * PI Planning ceremony data input and output.
     */
    public record PIPlanning(
            String piId,
            List<String> piObjectives,
            List<UserStory> plannedStories,
            List<String> registeredDependencies,
            List<String> teamCommitments,
            Instant timestamp
    ) {
        public PIPlanning {
            if (piId == null || piId.isBlank()) {
                throw new IllegalArgumentException("PI ID is required");
            }
            if (piObjectives == null) {
                piObjectives = List.of();
            }
            if (plannedStories == null) {
                plannedStories = List.of();
            }
            if (registeredDependencies == null) {
                registeredDependencies = List.of();
            }
            if (teamCommitments == null) {
                teamCommitments = List.of();
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }
    }

    /**
     * Sprint planning ceremony data.
     */
    public record SprintPlanning(
            String sprintId,
            List<UserStory> backlogItems,
            List<UserStory> committedStories,
            Instant timestamp
    ) {
        public SprintPlanning {
            if (sprintId == null || sprintId.isBlank()) {
                throw new IllegalArgumentException("Sprint ID is required");
            }
            if (backlogItems == null) {
                backlogItems = List.of();
            }
            if (committedStories == null) {
                committedStories = List.of();
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }

        public List<UserStory> presentedStories() {
            return backlogItems;
        }

        public String sprintGoal() {
            return "Deliver sprint objectives from committed stories";
        }
    }

    /**
     * Daily standup ceremony data.
     */
    public record DailyStandup(
            List<DeveloperStatus> statusReports,
            List<String> detectedBlockers,
            Instant timestamp
    ) {
        public DailyStandup {
            if (statusReports == null) {
                statusReports = List.of();
            }
            if (detectedBlockers == null) {
                detectedBlockers = List.of();
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }
    }

    /**
     * Individual developer status report.
     */
    public record DeveloperStatus(
            String developerId,
            String assignedStoryId,
            String status,
            String notes
    ) {
        public DeveloperStatus {
            if (developerId == null || developerId.isBlank()) {
                throw new IllegalArgumentException("Developer ID is required");
            }
            if (status == null || status.isBlank()) {
                throw new IllegalArgumentException("Status is required");
            }
        }
    }

    /**
     * Story completion flow data.
     */
    public record StoryCompletionData(
            UserStory completedStory,
            List<UserStory> dependencies,
            List<Boolean> criteriaEvaluations,
            Instant completionTime
    ) {
        public StoryCompletionData {
            if (completedStory == null) {
                throw new IllegalArgumentException("Completed story is required");
            }
            if (dependencies == null) {
                dependencies = List.of();
            }
            if (criteriaEvaluations == null) {
                criteriaEvaluations = List.of();
            }
            if (completionTime == null) {
                completionTime = Instant.now();
            }
        }
    }

    /**
     * Story completion result.
     */
    public record StoryCompletionResult(
            String storyId,
            Instant completionTime,
            boolean architectReviewedDependencies,
            boolean noDependencyBlockers,
            String storyStatus,
            boolean poAccepted,
            String finalStatus,
            List<Boolean> acceptanceCriteriaEvaluation
    ) {
        public StoryCompletionResult {
            if (storyId == null || storyId.isBlank()) {
                throw new IllegalArgumentException("Story ID is required");
            }
            if (completionTime == null) {
                completionTime = Instant.now();
            }
            if (acceptanceCriteriaEvaluation == null) {
                acceptanceCriteriaEvaluation = List.of();
            }
        }
    }

    /**
     * PI retrospective data.
     */
    public record PIRetroData(
            String piId,
            List<SprintResult> sprintResults,
            Instant timestamp
    ) {
        public PIRetroData {
            if (piId == null || piId.isBlank()) {
                throw new IllegalArgumentException("PI ID is required");
            }
            if (sprintResults == null) {
                sprintResults = List.of();
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }
    }

    /**
     * Individual sprint result for retrospective.
     */
    public record SprintResult(
            String sprintId,
            int committedPoints,
            int completedPoints
    ) {
        public SprintResult {
            if (sprintId == null || sprintId.isBlank()) {
                throw new IllegalArgumentException("Sprint ID is required");
            }
            if (committedPoints < 0 || completedPoints < 0) {
                throw new IllegalArgumentException("Points must be non-negative");
            }
        }
    }

    /**
     * PI retrospective result.
     */
    public record PIRetroResult(
            VelocityData velocityData,
            int teamFeedbackCount,
            int improvementsProposed,
            List<Improvement> improvements
    ) {
        public PIRetroResult {
            if (velocityData == null) {
                throw new IllegalArgumentException("Velocity data is required");
            }
            if (improvements == null) {
                improvements = List.of();
            }
        }
    }

    /**
     * Velocity tracking data.
     */
    public record VelocityData(
            int planned,
            int actual
    ) {
        public VelocityData {
            if (planned < 0 || actual < 0) {
                throw new IllegalArgumentException("Velocity must be non-negative");
            }
        }

        public double velocityRatio() {
            return planned > 0 ? (double) actual / planned : 0.0;
        }
    }

    /**
     * Improvement action from retrospective.
     */
    public record Improvement(
            String description,
            String owner,
            LocalDate deadline
    ) {
        public Improvement {
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("Description is required");
            }
            if (owner == null || owner.isBlank()) {
                throw new IllegalArgumentException("Owner is required");
            }
            if (deadline == null) {
                throw new IllegalArgumentException("Deadline is required");
            }
        }
    }
}
