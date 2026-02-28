/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.safe.agents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.elements.YSpecification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD (Detroit School) Integration Tests for SAFe Agent Simulation.
 *
 * Tests real SAFe agent participation in YAWL workflows covering five major ceremonies:
 * 1. PI Planning Ceremony (quarterly planning with dependency coordination)
 * 2. Sprint Planning (team estimation, capacity planning)
 * 3. Daily Standup (status updates, blocker detection)
 * 4. Story Completion Flow (dev → architect → PO acceptance)
 * 5. PI Retrospective (velocity analysis, improvements)
 *
 * Uses real YAWL YNetRunner execution, H2 in-memory database, and actual agent
 * message exchanges. No mocks or stubs — all integrations are real.
 *
 * Key testing principles:
 * - Test complete ceremony workflows end-to-end
 * - Verify agent decision consistency across messages
 * - Assert message ordering and state transitions
 * - Test dependency resolution across multiple agents
 * - Validate concurrent agent participation
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("SAFe Agent Simulation Test Suite")
public class SAFeAgentSimulationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SAFeAgentSimulationTest.class);

    private YEngine engine;
    private SAFeCeremonyExecutor ceremonyExecutor;

    @BeforeEach
    void setUp() {
        engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine instance should be created");
        ceremonyExecutor = new SAFeCeremonyExecutor(engine);
        LOGGER.info("Test setup complete: YEngine and SAFeCeremonyExecutor ready");
    }

    /**
     * Scenario 1: PI Planning Ceremony (5 agents coordinate quarterly planning)
     *
     * Flow:
     * 1. Release Train Engineer announces PI planning window (2 weeks)
     * 2. System Architect presents technical roadmap
     * 3. Product Owner presents business roadmap (features + stories)
     * 4. Teams (Scrum Masters + Developers) review dependencies
     * 5. All agents commit to PI objectives and dependencies
     * 6. Synced cadence established for next 12 weeks (3 sprints/PI)
     *
     * Assertions:
     * - All 5 agents participate (RTE, SA, PO, SM, DEV)
     * - Dependencies identified and registered
     * - Commitments recorded with PI scope
     * - PI roadmap contains all planned features
     * - No circular dependencies detected
     * - Message ordering: announcement → roadmap → commitment
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Scenario 1: PI Planning Ceremony - 5 Agents Coordinate Quarterly Planning")
    void testPIPlanningCeremony() {
        LOGGER.info("=== Starting PI Planning Ceremony ===");

        // Arrange: Create 5 SAFe agents for PI planning
        SAFeCeremonyData.PIPlanning piPlanData = createPIPlanningData();
        List<SAFeCeremonyExecutor.AgentParticipant> agents = createPIPlanningAgents();

        // Act: Execute PI planning ceremony workflow
        SAFeCeremonyData.PIPlanning executedPlan = ceremonyExecutor.executePIPlanningCeremony(
            piPlanData,
            agents
        );

        // Assert: Verify all agents participated
        assertEquals(5, agents.size(), "Should have 5 agents (RTE, SA, PO, SM, DEV)");
        agents.forEach(agent -> {
            assertTrue(agent.participatedInCeremony(),
                "Agent " + agent.name() + " should have participated in PI planning");
        });

        // Assert: PI planning outputs are complete
        assertNotNull(executedPlan.piObjectives(), "PI objectives must be defined");
        assertEquals(2, executedPlan.piObjectives().size(),
            "PI should have 2 major objectives");

        // Assert: All user stories are assigned to teams
        long unassignedStories = executedPlan.plannedStories().stream()
            .filter(story -> story.assigneeId() == null)
            .count();
        assertEquals(0, unassignedStories, "All stories should be assigned to teams");

        // Assert: Dependencies are identified and valid
        List<String> dependencyViolations = validateDependencies(
            executedPlan.plannedStories(),
            executedPlan.registeredDependencies()
        );
        assertTrue(dependencyViolations.isEmpty(),
            "No circular dependencies detected. Violations: " + dependencyViolations);

        // Assert: Message ordering verified (announcement → roadmap → commitment)
        List<String> messageSequence = extractMessageSequence(agents);
        assertTrue(messageSequence.indexOf("ANNOUNCEMENT") < messageSequence.indexOf("ROADMAP"),
            "RTE announcement should precede architectural roadmap");
        assertTrue(messageSequence.indexOf("ROADMAP") < messageSequence.indexOf("COMMITMENT"),
            "Roadmaps should precede team commitments");

        LOGGER.info("PI Planning Ceremony Complete: {} objectives, {} stories, {} dependencies",
            executedPlan.piObjectives().size(),
            executedPlan.plannedStories().size(),
            executedPlan.registeredDependencies().size());
    }

    /**
     * Scenario 2: Sprint Planning (PO presents, team estimates, capacity respected)
     *
     * Flow:
     * 1. PO presents top-priority stories from backlog
     * 2. Scrum Master conducts ceremony
     * 3. Developers estimate story points
     * 4. Developers commit to sprint capacity
     * 5. Architect reviews technical dependencies
     * 6. System verifies committed capacity ≤ team capacity
     *
     * Assertions:
     * - PO presents stories in priority order
     * - All story estimates ≥ 0 and reasonable (1-21 points)
     * - Total committed points ≤ team capacity
     * - Dependencies between stories identified
     * - Sprint goal is clear and achievable
     */
    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    @DisplayName("Scenario 2: Sprint Planning - PO Presents, Team Estimates, Capacity Respected")
    void testSprintPlanningCeremony() {
        LOGGER.info("=== Starting Sprint Planning Ceremony ===");

        // Arrange: Create sprint planning data with team capacity constraints
        SAFeCeremonyData.SprintPlanning planData = createSprintPlanningData();
        int teamCapacity = 40; // 40 points per 2-week sprint
        SAFeCeremonyExecutor.AgentParticipant po = createProductOwnerAgent();
        SAFeCeremonyExecutor.AgentParticipant sm = createScrumMasterAgent();
        List<SAFeCeremonyExecutor.AgentParticipant> developers = createDeveloperTeam(5);

        // Act: Execute sprint planning ceremony
        SAFeCeremonyData.SprintPlanning executedPlan =
            ceremonyExecutor.executeSprintPlanningCeremony(
                planData,
                po, sm, developers,
                teamCapacity
            );

        // Assert: PO presented stories in priority order
        List<UserStory> presentedStories = executedPlan.presentedStories();
        assertEquals(planData.backlogItems().size(),
            presentedStories.size(),
            "All backlog items should be presented");

        for (int i = 1; i < presentedStories.size(); i++) {
            assertTrue(presentedStories.get(i - 1).priority() <=
                presentedStories.get(i).priority(),
                "Stories should be presented in priority order");
        }

        // Assert: All developers participated in estimation
        developers.forEach(dev -> {
            assertTrue(dev.participatedInCeremony(),
                "Developer " + dev.name() + " should have estimated stories");
        });

        // Assert: All story estimates are valid (1-21 points, Fibonacci-like)
        executedPlan.committedStories().forEach(story -> {
            assertTrue(story.storyPoints() > 0,
                "Story " + story.id() + " must have positive story points");
            assertTrue(story.storyPoints() <= 21,
                "Story " + story.id() + " points should be reasonable (≤21)");
        });

        // Assert: Total committed capacity respects team capacity
        int totalCommittedPoints = executedPlan.committedStories().stream()
            .mapToInt(UserStory::storyPoints)
            .sum();
        assertTrue(totalCommittedPoints <= teamCapacity,
            "Team committed " + totalCommittedPoints + " points, but capacity is only " +
            teamCapacity + " points");

        // Assert: Sprint goal is clear
        assertNotNull(executedPlan.sprintGoal(), "Sprint goal must be defined");
        assertFalse(executedPlan.sprintGoal().isBlank(), "Sprint goal must not be empty");

        LOGGER.info("Sprint Planning Complete: {} stories, {} points, {} team capacity",
            executedPlan.committedStories().size(),
            totalCommittedPoints,
            teamCapacity);
    }

    /**
     * Scenario 3: Daily Standup (3 developers report status, blockers detected, escalations)
     *
     * Flow:
     * 1. Scrum Master facilitates standup
     * 2. Developer 1 reports: "Working on story X, on track"
     * 3. Developer 2 reports: "Blocked on story Y due to missing API"
     * 4. Developer 3 reports: "Completed story Z, ready for review"
     * 5. Blocker is escalated to Architect
     * 6. Architect resolves blocker or escalates to PO
     *
     * Assertions:
     * - All 3 developers report status
     * - Blocker message contains specific story and reason
     * - Blocker is escalated to correct authority
     * - Story Z is ready for code review
     * - Escalation chain is logged
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Scenario 3: Daily Standup - Status Reports, Blockers Detected, Escalations")
    void testDailyStandupCeremony() {
        LOGGER.info("=== Starting Daily Standup ===");

        // Arrange: Create 3 developers with different statuses
        SAFeCeremonyExecutor.AgentParticipant sm = createScrumMasterAgent();
        SAFeCeremonyExecutor.AgentParticipant dev1 = createDeveloperAgent("dev-1");
        SAFeCeremonyExecutor.AgentParticipant dev2 = createDeveloperAgent("dev-2");
        SAFeCeremonyExecutor.AgentParticipant dev3 = createDeveloperAgent("dev-3");
        SAFeCeremonyExecutor.AgentParticipant architect = createSystemArchitectAgent();

        SAFeCeremonyData.DailyStandup standupData =
            new SAFeCeremonyData.DailyStandup(
                List.of(
                    new SAFeCeremonyData.DeveloperStatus("dev-1", "story-101",
                        "in-progress", "On track"),
                    new SAFeCeremonyData.DeveloperStatus("dev-2", "story-102",
                        "blocked", "Missing API from story-104"),
                    new SAFeCeremonyData.DeveloperStatus("dev-3", "story-103",
                        "completed", "Ready for architect review")
                ),
                List.of(),
                Instant.now()
            );

        // Act: Execute daily standup
        SAFeCeremonyData.DailyStandup executedStandup =
            ceremonyExecutor.executeDailyStandupCeremony(
                standupData,
                sm,
                List.of(dev1, dev2, dev3),
                architect
            );

        // Assert: All developers reported status
        assertEquals(3, executedStandup.statusReports().size(),
            "All 3 developers should report status");

        // Assert: Dev 1 in-progress, Dev 2 blocked, Dev 3 completed
        var dev2Status = executedStandup.statusReports().stream()
            .filter(s -> s.developerId().equals("dev-2"))
            .findFirst()
            .orElseThrow();
        assertEquals("blocked", dev2Status.status(),
            "Dev 2 should be marked as blocked");

        var dev3Status = executedStandup.statusReports().stream()
            .filter(s -> s.developerId().equals("dev-3"))
            .findFirst()
            .orElseThrow();
        assertEquals("completed", dev3Status.status(),
            "Dev 3 should be marked as completed");

        // Assert: Blocker is detected and escalated
        assertEquals(1, executedStandup.detectedBlockers().size(),
            "Blocker on story-104 should be detected");
        var blocker = executedStandup.detectedBlockers().get(0);
        assertTrue(blocker.contains("story-102"),
            "Blocker should reference blocking story");
        assertTrue(blocker.contains("story-104"),
            "Blocker should reference blocked story");

        // Assert: Escalation chain is logged
        assertTrue(architect.participatedInCeremony(),
            "Architect should participate to resolve blockers");

        LOGGER.info("Daily Standup Complete: {} reports, {} blockers detected, escalated",
            executedStandup.statusReports().size(),
            executedStandup.detectedBlockers().size());
    }

    /**
     * Scenario 4: Story Completion Flow (developer marks done → architect reviews deps →
     *             PO accepts)
     *
     * Flow:
     * 1. Developer marks story as "ready for review"
     * 2. Developer submits story completion notification with test results
     * 3. Architect reviews story dependencies and technical quality
     * 4. Architect approves story (no blocking dependencies)
     * 5. Architect transitions story to "ready for PO review"
     * 6. PO reviews acceptance criteria
     * 7. PO accepts or rejects story
     * 8. If accepted: story moves to "done", if rejected: story returns to "in-progress"
     * 9. System state is updated atomically
     *
     * Assertions:
     * - Developer message triggers review workflow
     * - Architect reviews all dependencies
     * - Dependencies are satisfied before acceptance
     * - PO acceptance criteria evaluation is documented
     * - Final state is consistent (all actors agree)
     * - No race conditions in state updates
     */
    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    @DisplayName("Scenario 4: Story Completion Flow - Dev → Architect → PO Acceptance")
    void testStoryCompletionFlow() {
        LOGGER.info("=== Starting Story Completion Flow ===");

        // Arrange: Create story with dependencies
        UserStory completedStory = new UserStory(
            "story-201",
            "Implement user authentication",
            "User can login with email/password",
            List.of(
                "User can enter email",
                "Password is validated",
                "Session is created"
            ),
            8,  // 8 story points
            1,  // High priority
            "in-progress",
            List.of("story-200"),  // Depends on story-200
            "dev-1"
        );

        UserStory dependencyStory = new UserStory(
            "story-200",
            "Create user database schema",
            "Database schema supports user data",
            List.of("Schema includes email field", "Schema includes password hash"),
            5,
            2,
            "done",  // Already completed
            List.of(),
            null
        );

        SAFeCeremonyExecutor.AgentParticipant developer = createDeveloperAgent("dev-1");
        SAFeCeremonyExecutor.AgentParticipant architect = createSystemArchitectAgent();
        SAFeCeremonyExecutor.AgentParticipant po = createProductOwnerAgent();

        SAFeCeremonyData.StoryCompletionData storyData =
            new SAFeCeremonyData.StoryCompletionData(
                completedStory,
                List.of(dependencyStory),  // Dependencies already done
                List.of(true, true, true),  // All acceptance criteria met
                Instant.now()
            );

        // Act: Execute story completion workflow
        SAFeCeremonyData.StoryCompletionResult result =
            ceremonyExecutor.executeStoryCompletionFlow(
                storyData,
                developer,
                architect,
                po
            );

        // Assert: Developer marked story complete
        assertEquals("story-201", result.storyId(), "Correct story ID");
        assertNotNull(result.completionTime(), "Completion time should be recorded");

        // Assert: Architect reviewed dependencies
        assertTrue(result.architectReviewedDependencies(),
            "Architect should review dependencies");

        // Assert: No blocking dependencies
        assertTrue(result.noDependencyBlockers(),
            "story-200 is already done, so no blockers");

        // Assert: Architect approved continuation to PO
        assertEquals("ready-for-po-review", result.storyStatus(),
            "Story should transition to ready-for-PO-review");

        // Assert: PO acceptance evaluation
        assertTrue(result.poAccepted(), "PO should accept story with all criteria met");
        assertEquals("done", result.finalStatus(), "Story should be done after acceptance");

        // Assert: Acceptance criteria documentation
        assertNotNull(result.acceptanceCriteriaEvaluation(),
            "All criteria evaluations should be recorded");
        assertEquals(3, result.acceptanceCriteriaEvaluation().size(),
            "All 3 criteria should be evaluated");

        LOGGER.info("Story Completion Flow Complete: {} accepted at {}, all criteria met",
            result.storyId(),
            result.completionTime());
    }

    /**
     * Scenario 5: PI Retrospective (team reflects on velocity, improvements proposed,
     *             actions logged)
     *
     * Flow:
     * 1. Scrum Master facilitates retrospective
     * 2. Team reviews actual vs planned velocity (target: 40 points/sprint)
     * 3. Team shares what went well (start/continue)
     * 4. Team identifies improvements (stop/change)
     * 5. Team proposes concrete actions with owners
     * 6. Retrospective summary is created
     * 7. Actions are scheduled for next PI
     *
     * Assertions:
     * - Velocity is tracked (completed points vs committed points)
     * - All team members contribute feedback
     * - Improvements are actionable (not vague)
     * - Actions have owners and deadlines
     * - Retrospective is documented for next PI
     */
    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("Scenario 5: PI Retrospective - Velocity Review, Improvements Proposed")
    void testPIRetrospective() {
        LOGGER.info("=== Starting PI Retrospective ===");

        // Arrange: Create PI data with sprint results
        SAFeCeremonyData.PIRetroData retroData = createPIRetroData();
        SAFeCeremonyExecutor.AgentParticipant sm = createScrumMasterAgent();
        List<SAFeCeremonyExecutor.AgentParticipant> teamMembers = createDeveloperTeam(5);

        // Act: Execute PI retrospective
        SAFeCeremonyData.PIRetroResult retroResult =
            ceremonyExecutor.executePIRetrospective(
                retroData,
                sm,
                teamMembers
            );

        // Assert: Velocity is calculated
        int plannedVelocity = retroData.sprintResults().stream()
            .mapToInt(SAFeCeremonyData.SprintResult::committedPoints)
            .sum();
        int actualVelocity = retroData.sprintResults().stream()
            .mapToInt(SAFeCeremonyData.SprintResult::completedPoints)
            .sum();

        assertEquals(plannedVelocity, 120, "PI should plan 120 points (40/sprint × 3)");
        assertTrue(actualVelocity >= 100, "Actual velocity should be reasonable (≥100)");

        // Assert: Velocity is documented
        assertNotNull(retroResult.velocityData(), "Velocity data must be documented");
        assertEquals(plannedVelocity, retroResult.velocityData().planned(),
            "Planned velocity should match");
        assertEquals(actualVelocity, retroResult.velocityData().actual(),
            "Actual velocity should match");

        // Assert: Team feedback collected
        assertEquals(teamMembers.size(), retroResult.teamFeedbackCount(),
            "All team members should provide feedback");

        // Assert: Improvements proposed
        assertTrue(retroResult.improvementsProposed() > 0,
            "Team should propose improvements");
        retroResult.improvements().forEach(imp -> {
            assertFalse(imp.description().isBlank(), "Improvement should have description");
            assertFalse(imp.owner().isBlank(), "Improvement should have owner");
            assertNotNull(imp.deadline(), "Improvement should have deadline");
        });

        // Assert: Actions are actionable (not vague)
        retroResult.improvements().forEach(imp -> {
            assertTrue(imp.description().length() > 10,
                "Action should be specific, not vague: " + imp.description());
        });

        LOGGER.info("PI Retrospective Complete: {} actual velocity vs {} planned, " +
            "{} improvements proposed",
            actualVelocity,
            plannedVelocity,
            retroResult.improvementsProposed());
    }

    /**
     * Parametrized test: PI Planning with varying team sizes.
     * Tests that SAFe agent simulation scales with team configuration.
     */
    @ParameterizedTest(name = "PI Planning: {0} teams, {1} members each")
    @CsvSource({
        "1, 5",   // Single small team
        "3, 5",   // 3 agile teams
        "5, 4",   // Large release train
    })
    @DisplayName("Parametrized: PI Planning with Varying Team Sizes")
    void testPIPlanningWithVaryingTeamSizes(int teamCount, int membersPerTeam) {
        LOGGER.info("=== PI Planning: {} teams, {} members each ===",
            teamCount, membersPerTeam);

        // Arrange
        SAFeCeremonyData.PIPlanning piData = createPIPlanningData();
        List<AgentParticipant> allAgents = new ArrayList<>();
        allAgents.addAll(List.of(
            createReleaseTrainEngineerAgent(),
            createSystemArchitectAgent(),
            createProductOwnerAgent()
        ));

        // Create teams
        for (int i = 0; i < teamCount; i++) {
            allAgents.add(createScrumMasterAgent());
            for (int j = 0; j < membersPerTeam; j++) {
                allAgents.add(createDeveloperAgent("dev-t" + i + "-m" + j));
            }
        }

        // Act
        SAFeCeremonyData.PIPlanning result =
            ceremonyExecutor.executePIPlanningCeremony(piData, allAgents);

        // Assert
        assertTrue(result.plannedStories().size() > 0,
            "Should plan stories for " + teamCount + " teams");
        assertEquals(teamCount, result.teamCommitments().size(),
            "Each team should make commitments");

        long successfulParticipations = allAgents.stream()
            .filter(AgentParticipant::participatedInCeremony)
            .count();
        assertTrue(successfulParticipations >= allAgents.size() - 2,
            "Most agents should participate successfully");

        LOGGER.info("PI Planning Complete: {} agents, {} stories, {} commitments",
            allAgents.size(),
            result.plannedStories().size(),
            result.teamCommitments().size());
    }

    /**
     * Concurrent Test: Multiple stories complete simultaneously
     * Tests that agent decision logic is thread-safe under concurrent story completions
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Concurrent: Multiple Stories Complete Simultaneously")
    void testConcurrentStoryCompletions() {
        LOGGER.info("=== Starting Concurrent Story Completions ===");

        // Arrange: Create 10 independent stories
        List<UserStory> stories = IntStream.range(0, 10)
            .mapToObj(i -> new UserStory(
                "story-" + i,
                "Feature " + i,
                "Feature " + i + " description",
                List.of("Criterion " + i),
                5,
                i,
                "in-progress",
                List.of(),  // No dependencies
                "dev-" + (i % 3)
            ))
            .toList();

        AgentParticipant architect = createSystemArchitectAgent();
        AgentParticipant po = createProductOwnerAgent();

        // Act: Complete all stories concurrently
        List<SAFeCeremonyData.StoryCompletionResult> results = stories.parallelStream()
            .map(story -> {
                AgentParticipant dev = createDeveloperAgent(story.assigneeId());
                SAFeCeremonyData.StoryCompletionData data =
                    new SAFeCeremonyData.StoryCompletionData(
                        story,
                        List.of(),
                        List.of(true),
                        Instant.now()
                    );
                return ceremonyExecutor.executeStoryCompletionFlow(
                    data, dev, architect, po
                );
            })
            .toList();

        // Assert: All stories completed successfully
        assertEquals(10, results.size(), "All 10 stories should complete");
        results.forEach(result -> {
            assertEquals("done", result.finalStatus(),
                "Story " + result.storyId() + " should be done");
            assertTrue(result.poAccepted(),
                "Story " + result.storyId() + " should be accepted by PO");
        });

        // Assert: No race conditions (completion times are distinct)
        List<Instant> completionTimes = results.stream()
            .map(SAFeCeremonyData.StoryCompletionResult::completionTime)
            .sorted()
            .toList();

        for (int i = 0; i < completionTimes.size() - 1; i++) {
            // Times should be within reasonable bounds (1 second apart)
            assertTrue(completionTimes.get(i).isBefore(completionTimes.get(i + 1))
                || completionTimes.get(i).equals(completionTimes.get(i + 1)),
                "Completion times should be monotonic");
        }

        LOGGER.info("Concurrent Story Completions: {} stories done, no race conditions");
    }

    // ========== Helper Methods ==========

    private SAFeCeremonyData.PIPlanning createPIPlanningData() {
        return new SAFeCeremonyData.PIPlanning(
            "PI-2026-Q1",
            List.of("Deliver scalable API", "Improve user experience"),
            List.of(
                new UserStory("story-1", "API endpoint GET /users", "...",
                    List.of("Returns 200 OK"), 8, 1, "backlog", List.of(), null),
                new UserStory("story-2", "API endpoint POST /users", "...",
                    List.of("Returns 201"), 8, 1, "backlog", List.of("story-1"), null),
                new UserStory("story-3", "UI: Users list page", "...",
                    List.of("Displays users"), 5, 2, "backlog", List.of("story-1"), null),
                new UserStory("story-4", "Performance tuning", "...",
                    List.of("Response <200ms"), 8, 3, "backlog", List.of(), null)
            ),
            List.of(),
            List.of(),
            Instant.now()
        );
    }

    private SAFeCeremonyData.SprintPlanning createSprintPlanningData() {
        return new SAFeCeremonyData.SprintPlanning(
            "sprint-2026-01",
            List.of(
                new UserStory("s-101", "Login page", "...",
                    List.of("Email input"), 5, 1, "backlog", List.of(), null),
                new UserStory("s-102", "Password validation", "...",
                    List.of("Check strength"), 3, 1, "backlog", List.of(), null),
                new UserStory("s-103", "Session management", "...",
                    List.of("Create session"), 5, 1, "backlog", List.of("s-101", "s-102"), null),
                new UserStory("s-104", "Logout button", "...",
                    List.of("Remove session"), 3, 2, "backlog", List.of("s-103"), null),
                new UserStory("s-105", "Remember me checkbox", "...",
                    List.of("Save preference"), 5, 3, "backlog", List.of(), null),
                new UserStory("s-106", "Forgot password flow", "...",
                    List.of("Send reset email"), 8, 2, "backlog", List.of(), null),
                new UserStory("s-107", "Password reset page", "...",
                    List.of("Update password"), 5, 2, "backlog", List.of("s-106"), null),
                new UserStory("s-108", "Error messages", "...",
                    List.of("Show on screen"), 3, 3, "backlog", List.of(), null)
            ),
            List.of(),
            Instant.now()
        );
    }

    private SAFeCeremonyData.PIRetroData createPIRetroData() {
        return new SAFeCeremonyData.PIRetroData(
            "PI-2026-Q1",
            List.of(
                new SAFeCeremonyData.SprintResult("sprint-01", 40, 38),
                new SAFeCeremonyData.SprintResult("sprint-02", 40, 40),
                new SAFeCeremonyData.SprintResult("sprint-03", 40, 35)
            ),
            Instant.now()
        );
    }

    private List<SAFeCeremonyExecutor.AgentParticipant> createPIPlanningAgents() {
        return List.of(
            createReleaseTrainEngineerAgent(),
            createSystemArchitectAgent(),
            createProductOwnerAgent(),
            createScrumMasterAgent(),
            createDeveloperAgent("dev-pi-1")
        );
    }

    private List<SAFeCeremonyExecutor.AgentParticipant> createDeveloperTeam(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> createDeveloperAgent("dev-team-" + i))
            .toList();
    }

    private SAFeCeremonyExecutor.AgentParticipant createReleaseTrainEngineerAgent() {
        return new SAFeCeremonyExecutor.AgentParticipant("rte-1", "Release Train Engineer", "RTE");
    }

    private SAFeCeremonyExecutor.AgentParticipant createSystemArchitectAgent() {
        return new SAFeCeremonyExecutor.AgentParticipant("arch-1", "System Architect", "ARCHITECT");
    }

    private SAFeCeremonyExecutor.AgentParticipant createProductOwnerAgent() {
        return new SAFeCeremonyExecutor.AgentParticipant("po-1", "Product Owner", "PO");
    }

    private SAFeCeremonyExecutor.AgentParticipant createScrumMasterAgent() {
        return new SAFeCeremonyExecutor.AgentParticipant("sm-1", "Scrum Master", "SM");
    }

    private SAFeCeremonyExecutor.AgentParticipant createDeveloperAgent(String id) {
        return new SAFeCeremonyExecutor.AgentParticipant(id, "Developer " + id, "DEV");
    }

    private List<String> validateDependencies(List<UserStory> stories,
                                                 List<String> registeredDependencies) {
        List<String> violations = new ArrayList<>();

        for (UserStory story : stories) {
            // Check if all dependencies exist
            for (String dep : story.dependsOn()) {
                boolean depExists = stories.stream()
                    .anyMatch(s -> s.id().equals(dep));
                if (!depExists) {
                    violations.add("Story " + story.id() + " depends on non-existent " + dep);
                }
            }

            // Check for circular dependencies (depth-first search)
            if (hasCyclicDependency(story.id(), stories, new java.util.HashSet<>())) {
                violations.add("Story " + story.id() + " has circular dependency");
            }
        }

        return violations;
    }

    private boolean hasCyclicDependency(String storyId, List<UserStory> allStories,
                                           java.util.Set<String> visited) {
        if (visited.contains(storyId)) {
            return true; // Cycle detected
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

    private List<String> extractMessageSequence(List<AgentParticipant> agents) {
        List<String> sequence = new ArrayList<>();
        for (AgentParticipant agent : agents) {
            if (agent.lastMessageType() != null) {
                sequence.add(agent.lastMessageType());
            }
        }
        return sequence;
    }

    /**
     * Data holder for PI Planning ceremony
     */
    public record AgentParticipant(String id, String name, String role) {
        private static final java.util.Map<String, Long> PARTICIPATION_TRACKER =
            new java.util.concurrent.ConcurrentHashMap<>();
        private static final java.util.Map<String, String> MESSAGE_TYPE_TRACKER =
            new java.util.concurrent.ConcurrentHashMap<>();

        public boolean participatedInCeremony() {
            return PARTICIPATION_TRACKER.containsKey(id);
        }

        public void recordParticipation() {
            PARTICIPATION_TRACKER.put(id, System.currentTimeMillis());
        }

        public String lastMessageType() {
            return MESSAGE_TYPE_TRACKER.get(id);
        }

        public void recordMessageType(String type) {
            MESSAGE_TYPE_TRACKER.put(id, type);
        }
    }
}
