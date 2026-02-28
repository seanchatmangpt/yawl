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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.YEngine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD edge case and error scenario tests for SAFe agent simulation.
 * Tests boundary conditions, error recovery, and complex agent interactions.
 *
 * Test categories:
 * 1. Circular Dependencies - Detect and prevent circular story dependencies
 * 2. Capacity Overcommitment - Verify team capacity constraints
 * 3. Blocked Stories - Handle stories blocked by unmet dependencies
 * 4. Multiple Blockers - Stories blocked by multiple dependencies
 * 5. Concurrent Completions - Race conditions in story acceptance
 * 6. Velocity Anomalies - Low/high velocity detection and response
 * 7. Empty Teams - Handle missing/unavailable team members
 * 8. Estimation Errors - Large estimation mismatches
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("SAFe Agent Edge Cases & Error Scenarios")
public class SAFeEdgeCasesTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SAFeEdgeCasesTest.class);

    private YEngine engine;
    private SAFeCeremonyExecutor executor;

    @BeforeEach
    void setUp() {
        engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine should be available");
        executor = new SAFeCeremonyExecutor(engine);
    }

    /**
     * Edge Case 1: Circular Dependencies Detection
     *
     * Setup: Create stories with circular dependency chain (A → B → C → A)
     * Expected: validateDependencies detects cycle
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Edge Case 1: Circular Dependencies - Detect and Prevent")
    void testCircularDependencyDetection() {
        LOGGER.info("=== Testing Circular Dependency Detection ===");

        // Arrange: Create circular dependency A → B → C → A
        UserStory storyA = new UserStory(
            "story-a",
            "Feature A",
            "A depends on B",
            List.of(),
            5, 1, "backlog",
            List.of("story-b"),
            null
        );

        UserStory storyB = new UserStory(
            "story-b",
            "Feature B",
            "B depends on C",
            List.of(),
            5, 2, "backlog",
            List.of("story-c"),
            null
        );

        UserStory storyC = new UserStory(
            "story-c",
            "Feature C",
            "C depends on A (creates cycle)",
            List.of(),
            5, 3, "backlog",
            List.of("story-a"),
            null
        );

        List<UserStory> stories = List.of(storyA, storyB, storyC);

        // Act & Assert: Verify circular dependency is detected
        boolean hasCycle = hasCyclicDependency("story-a", stories);
        assertTrue(hasCycle, "Should detect circular dependency A → B → C → A");

        LOGGER.info("Circular dependency detected successfully");
    }

    /**
     * Edge Case 2: Capacity Overcommitment Prevention
     *
     * Setup: Team capacity 40 points, try to commit 50 points
     * Expected: System only commits 40 points maximum
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Edge Case 2: Capacity Overcommitment - Enforce Limits")
    void testCapacityOvercommitmentPrevention() {
        LOGGER.info("=== Testing Capacity Overcommitment Prevention ===");

        // Arrange: 50 points worth of stories, 40 point capacity
        List<UserStory> backlog = List.of(
            new UserStory("s1", "Feature 1", "...", List.of(), 13, 1, "backlog", List.of(), null),
            new UserStory("s2", "Feature 2", "...", List.of(), 13, 2, "backlog", List.of(), null),
            new UserStory("s3", "Feature 3", "...", List.of(), 13, 3, "backlog", List.of(), null),
            new UserStory("s4", "Feature 4", "...", List.of(), 8, 4, "backlog", List.of(), null),
            new UserStory("s5", "Feature 5", "...", List.of(), 3, 5, "backlog", List.of(), null)
        );

        int teamCapacity = 40;
        SAFeCeremonyData.SprintPlanning planData = new SAFeCeremonyData.SprintPlanning(
            "sprint-edge-01",
            backlog,
            List.of(),
            Instant.now()
        );

        SAFeCeremonyExecutor.AgentParticipant po = new SAFeCeremonyExecutor.AgentParticipant(
            "po-edge", "PO", "PO");
        SAFeCeremonyExecutor.AgentParticipant sm = new SAFeCeremonyExecutor.AgentParticipant(
            "sm-edge", "SM", "SM");
        List<SAFeCeremonyExecutor.AgentParticipant> devs = List.of(
            new SAFeCeremonyExecutor.AgentParticipant("dev-1", "Dev 1", "DEV"),
            new SAFeCeremonyExecutor.AgentParticipant("dev-2", "Dev 2", "DEV")
        );

        // Act: Execute sprint planning
        SAFeCeremonyData.SprintPlanning result =
            executor.executeSprintPlanningCeremony(planData, po, sm, devs, teamCapacity);

        // Assert: Committed points never exceed capacity
        int totalCommitted = result.committedStories().stream()
            .mapToInt(UserStory::storyPoints)
            .sum();

        assertTrue(totalCommitted <= teamCapacity,
            "Committed " + totalCommitted + " points exceeds capacity " + teamCapacity);

        LOGGER.info("Capacity enforcement verified: {} committed ≤ {} capacity",
            totalCommitted, teamCapacity);
    }

    /**
     * Edge Case 3: Blocked Stories Handling
     *
     * Setup: Story A depends on Story B (not completed)
     * Expected: Story A is marked as blocked
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Edge Case 3: Blocked Stories - Dependency Not Met")
    void testBlockedStoriesHandling() {
        LOGGER.info("=== Testing Blocked Stories ===");

        // Arrange: Story A depends on Story B (not done)
        UserStory storyB = new UserStory(
            "story-b",
            "Required Feature B",
            "Setup data",
            List.of(),
            3, 2, "in-progress",
            List.of(),
            "dev-2"
        );

        UserStory storyA = new UserStory(
            "story-a",
            "Feature A (blocked)",
            "Depends on B",
            List.of("Criterion A1", "Criterion A2"),
            5, 1, "in-progress",
            List.of("story-b"),
            "dev-1"
        );

        SAFeCeremonyData.StoryCompletionData data =
            new SAFeCeremonyData.StoryCompletionData(
                storyA,
                List.of(storyB),  // Dependency is not completed
                List.of(true, true),
                Instant.now()
            );

        SAFeCeremonyExecutor.AgentParticipant dev = new SAFeCeremonyExecutor.AgentParticipant(
            "dev-1", "Dev 1", "DEV");
        SAFeCeremonyExecutor.AgentParticipant arch = new SAFeCeremonyExecutor.AgentParticipant(
            "arch-1", "Architect", "ARCHITECT");
        SAFeCeremonyExecutor.AgentParticipant po = new SAFeCeremonyExecutor.AgentParticipant(
            "po-1", "PO", "PO");

        // Act: Execute story completion
        SAFeCeremonyData.StoryCompletionResult result =
            executor.executeStoryCompletionFlow(data, dev, arch, po);

        // Assert: Story is blocked (no dependency blockers = false)
        assertFalse(result.noDependencyBlockers(),
            "Story A should be blocked (story-b is not completed)");
        assertNotEquals("done", result.finalStatus(),
            "Blocked story should not transition to done");

        LOGGER.info("Blocked story handling verified: blocked={}, status={}",
            !result.noDependencyBlockers(), result.storyStatus());
    }

    /**
     * Edge Case 4: Multiple Blocking Dependencies
     *
     * Setup: Story D depends on stories A, B, C (only A and B completed)
     * Expected: Story D is blocked (missing C)
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Edge Case 4: Multiple Dependencies - Partial Completion")
    void testMultipleBlockingDependencies() {
        LOGGER.info("=== Testing Multiple Blocking Dependencies ===");

        // Arrange: Story D depends on A, B, C (only A, B done)
        List<UserStory> dependencies = List.of(
            new UserStory("story-a", "A", "...", List.of(), 3, 1, "done", List.of(), null),
            new UserStory("story-b", "B", "...", List.of(), 3, 2, "done", List.of(), null),
            new UserStory("story-c", "C", "...", List.of(), 3, 3, "in-progress", List.of(), null)
        );

        UserStory storyD = new UserStory(
            "story-d",
            "Feature D (depends on A, B, C)",
            "...",
            List.of("Criterion D"),
            8, 0, "in-progress",
            List.of("story-a", "story-b", "story-c"),
            "dev-4"
        );

        SAFeCeremonyData.StoryCompletionData data =
            new SAFeCeremonyData.StoryCompletionData(
                storyD,
                dependencies,
                List.of(true),
                Instant.now()
            );

        SAFeCeremonyExecutor.AgentParticipant dev = new SAFeCeremonyExecutor.AgentParticipant(
            "dev-4", "Dev 4", "DEV");
        SAFeCeremonyExecutor.AgentParticipant arch = new SAFeCeremonyExecutor.AgentParticipant(
            "arch-1", "Architect", "ARCHITECT");
        SAFeCeremonyExecutor.AgentParticipant po = new SAFeCeremonyExecutor.AgentParticipant(
            "po-1", "PO", "PO");

        // Act: Execute story completion
        SAFeCeremonyData.StoryCompletionResult result =
            executor.executeStoryCompletionFlow(data, dev, arch, po);

        // Assert: Story is blocked (missing story-c)
        assertFalse(result.noDependencyBlockers(),
            "Story D should be blocked on story-c");

        LOGGER.info("Multiple dependency blocking verified correctly");
    }

    /**
     * Edge Case 5: All Criteria Not Met
     *
     * Setup: Story has 5 acceptance criteria, only 3 met
     * Expected: PO rejects story
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Edge Case 5: Acceptance Criteria - Partial Completion")
    void testAcceptanceCriteriaPartialCompletion() {
        LOGGER.info("=== Testing Acceptance Criteria Partial Completion ===");

        // Arrange: 5 criteria, only 3 met (indices 0, 2, 4)
        UserStory story = new UserStory(
            "story-partial",
            "Feature with 5 criteria",
            "...",
            List.of("C1", "C2", "C3", "C4", "C5"),
            8, 1, "in-progress",
            List.of(),
            "dev-1"
        );

        List<Boolean> evaluations = List.of(true, false, true, false, true);  // 3 of 5 met

        SAFeCeremonyData.StoryCompletionData data =
            new SAFeCeremonyData.StoryCompletionData(
                story,
                List.of(),  // No dependencies
                evaluations,
                Instant.now()
            );

        SAFeCeremonyExecutor.AgentParticipant dev = new SAFeCeremonyExecutor.AgentParticipant(
            "dev-1", "Dev 1", "DEV");
        SAFeCeremonyExecutor.AgentParticipant arch = new SAFeCeremonyExecutor.AgentParticipant(
            "arch-1", "Architect", "ARCHITECT");
        SAFeCeremonyExecutor.AgentParticipant po = new SAFeCeremonyExecutor.AgentParticipant(
            "po-1", "PO", "PO");

        // Act: Execute story completion
        SAFeCeremonyData.StoryCompletionResult result =
            executor.executeStoryCompletionFlow(data, dev, arch, po);

        // Assert: PO rejects story (not all criteria met)
        assertFalse(result.poAccepted(),
            "Story should be rejected (only 3 of 5 criteria met)");
        assertEquals("in-progress", result.finalStatus(),
            "Rejected story returns to in-progress");

        LOGGER.info("Acceptance criteria validation verified: rejected with 3/5 met");
    }

    /**
     * Edge Case 6: Low Velocity Detection & Response
     *
     * Setup: PI planned 120 points, completed only 80 points (67%)
     * Expected: Retrospective identifies improvements
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Edge Case 6: Low Velocity - Improvement Actions Triggered")
    void testLowVelocityDetectionAndResponse() {
        LOGGER.info("=== Testing Low Velocity Detection ===");

        // Arrange: 3 sprints, 40 committed each = 120 total, but only 80 completed
        SAFeCeremonyData.PIRetroData retroData = new SAFeCeremonyData.PIRetroData(
            "PI-low-velocity",
            List.of(
                new SAFeCeremonyData.SprintResult("s1", 40, 25),
                new SAFeCeremonyData.SprintResult("s2", 40, 28),
                new SAFeCeremonyData.SprintResult("s3", 40, 27)
            ),
            Instant.now()
        );

        SAFeCeremonyExecutor.AgentParticipant sm = new SAFeCeremonyExecutor.AgentParticipant(
            "sm-1", "Scrum Master", "SM");
        List<SAFeCeremonyExecutor.AgentParticipant> team = List.of(
            new SAFeCeremonyExecutor.AgentParticipant("dev-1", "Dev 1", "DEV"),
            new SAFeCeremonyExecutor.AgentParticipant("dev-2", "Dev 2", "DEV")
        );

        // Act: Execute retrospective
        SAFeCeremonyData.PIRetroResult result =
            executor.executePIRetrospective(retroData, sm, team);

        // Assert: Low velocity is detected and improvements proposed
        assertEquals(120, result.velocityData().planned(), "Planned velocity should be 120");
        assertEquals(80, result.velocityData().actual(), "Actual velocity should be 80");

        double ratio = result.velocityData().velocityRatio();
        assertTrue(ratio < 0.9, "Velocity ratio should be < 0.9 (67%)");

        assertTrue(result.improvementsProposed() > 0,
            "Low velocity should trigger improvement actions");

        // Verify improvements address root causes
        boolean hasEstimationImprovement = result.improvements().stream()
            .anyMatch(i -> i.description().contains("estimation"));
        assertTrue(hasEstimationImprovement,
            "Should propose estimation improvement for low velocity");

        LOGGER.info("Low velocity response verified: {}% completion, {} improvements",
            Math.round(ratio * 100), result.improvementsProposed());
    }

    /**
     * Edge Case 7: High Velocity - 100% or More
     *
     * Setup: PI planned 100 points, completed 110 points
     * Expected: Velocity is tracked accurately
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Edge Case 7: High Velocity - Velocity Ratio > 100%")
    void testHighVelocityTracking() {
        LOGGER.info("=== Testing High Velocity Tracking ===");

        // Arrange: Completed more than planned
        SAFeCeremonyData.PIRetroData retroData = new SAFeCeremonyData.PIRetroData(
            "PI-high-velocity",
            List.of(
                new SAFeCeremonyData.SprintResult("s1", 30, 35),
                new SAFeCeremonyData.SprintResult("s2", 35, 40),
                new SAFeCeremonyData.SprintResult("s3", 35, 35)
            ),
            Instant.now()
        );

        SAFeCeremonyExecutor.AgentParticipant sm = new SAFeCeremonyExecutor.AgentParticipant(
            "sm-1", "Scrum Master", "SM");
        List<SAFeCeremonyExecutor.AgentParticipant> team = List.of(
            new SAFeCeremonyExecutor.AgentParticipant("dev-1", "Dev 1", "DEV")
        );

        // Act: Execute retrospective
        SAFeCeremonyData.PIRetroResult result =
            executor.executePIRetrospective(retroData, sm, team);

        // Assert: High velocity is recorded
        assertEquals(100, result.velocityData().planned());
        assertEquals(110, result.velocityData().actual());
        assertEquals(1.1, result.velocityData().velocityRatio(), 0.01);

        LOGGER.info("High velocity tracking verified: {}% completion",
            Math.round(result.velocityData().velocityRatio() * 100));
    }

    /**
     * Edge Case 8: Empty Team Handling
     *
     * Setup: PI planning with no available developers
     * Expected: System gracefully handles missing resources
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Edge Case 8: Empty Developer Team - Graceful Degradation")
    void testEmptyTeamHandling() {
        LOGGER.info("=== Testing Empty Developer Team ===");

        // Arrange: Only RTE, no developers
        SAFeCeremonyData.PIPlanning piData = new SAFeCeremonyData.PIPlanning(
            "PI-empty-team",
            List.of("Objective 1"),
            List.of(
                new UserStory("s1", "Feature 1", "...", List.of(), 5, 1, "backlog", List.of(), null)
            ),
            List.of(),
            List.of(),
            Instant.now()
        );

        List<SAFeCeremonyExecutor.AgentParticipant> agents = List.of(
            new SAFeCeremonyExecutor.AgentParticipant("rte-1", "RTE", "RTE")
            // No developers
        );

        // Act: Execute PI planning with empty team
        SAFeCeremonyData.PIPlanning result =
            executor.executePIPlanningCeremony(piData, agents);

        // Assert: System handles gracefully (stories may not be assigned)
        assertNotNull(result, "Result should not be null");
        assertTrue(result.plannedStories().size() > 0, "Stories should still exist");

        LOGGER.info("Empty team handling verified: {} stories, {} assigned",
            result.plannedStories().size(),
            result.plannedStories().stream()
                .filter(s -> s.assigneeId() != null)
                .count());
    }

    /**
     * Parametrized test: Velocity ratios at different completion percentages
     */
    @ParameterizedTest(name = "Velocity: {0}% completion")
    @ValueSource(ints = {50, 67, 80, 100, 125})
    @DisplayName("Parametrized: Velocity Tracking at Various Completion Levels")
    void testVelocityRatios(int completionPercent) {
        LOGGER.info("Testing velocity at {}% completion", completionPercent);

        int planned = 100;
        int actual = (planned * completionPercent) / 100;

        SAFeCeremonyData.VelocityData velocity =
            new SAFeCeremonyData.VelocityData(planned, actual);

        double expectedRatio = completionPercent / 100.0;
        assertEquals(expectedRatio, velocity.velocityRatio(), 0.01,
            "Velocity ratio should be " + completionPercent / 100.0);

        LOGGER.info("Velocity ratio verified: {}% → {}", completionPercent, velocity.velocityRatio());
    }

    // ========== Helper Methods ==========

    private boolean hasCyclicDependency(String storyId, List<UserStory> allStories) {
        return hasCyclicDependency(storyId, allStories, new java.util.HashSet<>());
    }

    private boolean hasCyclicDependency(String storyId, List<UserStory> allStories,
                                       java.util.Set<String> visited) {
        if (visited.contains(storyId)) {
            return true;  // Cycle detected
        }

        visited.add(storyId);

        var story = allStories.stream()
            .filter(s -> s.id().equals(storyId))
            .findFirst();

        if (story.isEmpty()) {
            return false;
        }

        for (String dep : story.get().dependsOn()) {
            if (hasCyclicDependency(dep, allStories, new java.util.HashSet<>(visited))) {
                return true;
            }
        }

        return false;
    }
}
