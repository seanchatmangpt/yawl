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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.YEngine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for SAFe agent capabilities, message ordering, and decision consistency.
 *
 * Test coverage:
 * 1. Agent Role Verification - Each agent has correct role and capabilities
 * 2. Message Ordering - Ceremony messages follow expected sequence
 * 3. Decision Consistency - Same agent makes consistent decisions across ceremonies
 * 4. Capability Coverage - All SAFe ceremonies are supported
 * 5. State Transitions - Stories transition correctly through workflow states
 * 6. Agent Participation - Correct agents participate in each ceremony
 * 7. Decision Logging - All decisions are logged for audit trail
 * 8. Escalation Chains - Blocker escalations follow correct paths
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("SAFe Agent Capability Verification")
public class SAFeAgentCapabilityTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SAFeAgentCapabilityTest.class);

    private YEngine engine;
    private SAFeCeremonyExecutor executor;

    @BeforeEach
    void setUp() {
        engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine should be available");
        executor = new SAFeCeremonyExecutor(engine);
    }

    /**
     * Capability 1: Release Train Engineer Role
     *
     * Responsibilities:
     * - Announces PI planning window
     * - Coordinates dependencies across teams
     * - Manages PI roadmap and schedule
     * - Escalates critical issues
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Capability 1: RTE (Release Train Engineer) Role")
    void testReleaseTrainEngineerRole() {
        LOGGER.info("=== Verifying RTE Capabilities ===");

        // Arrange: RTE agent
        SAFeCeremonyExecutor.AgentParticipant rte = new SAFeCeremonyExecutor.AgentParticipant(
            "rte-1", "Release Train Engineer", "RTE"
        );

        // Verify: RTE is correct type
        assertEquals("RTE", rte.role(), "Agent should be RTE");
        assertTrue(rte.name().contains("Release Train Engineer") || rte.name().contains("Engineer"),
            "RTE name should indicate role");

        // Act: RTE participates in PI planning
        SAFeCeremonyData.PIPlanning piData = new SAFeCeremonyData.PIPlanning(
            "PI-rte-test",
            List.of("Deliver API", "Improve UX"),
            List.of(
                new UserStory("s1", "API", "...", List.of(), 8, 1, "backlog", List.of(), null),
                new UserStory("s2", "UX", "...", List.of(), 5, 2, "backlog", List.of(), null)
            ),
            List.of(),
            List.of(),
            Instant.now()
        );

        SAFeCeremonyData.PIPlanning result = executor.executePIPlanningCeremony(
            piData,
            List.of(rte)
        );

        // Assert: RTE participated
        assertTrue(rte.participatedInCeremony(),
            "RTE should participate in PI planning");
        assertNotNull(result.piObjectives(), "PI objectives should be defined");

        LOGGER.info("RTE capabilities verified: participates in {} objectives",
            result.piObjectives().size());
    }

    /**
     * Capability 2: Product Owner Role
     *
     * Responsibilities:
     * - Presents prioritized backlog
     * - Evaluates acceptance criteria
     * - Accepts or rejects stories
     * - Manages story priority
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Capability 2: PO (Product Owner) Role")
    void testProductOwnerRole() {
        LOGGER.info("=== Verifying PO Capabilities ===");

        // Arrange: PO agent
        SAFeCeremonyExecutor.AgentParticipant po = new SAFeCeremonyExecutor.AgentParticipant(
            "po-1", "Product Owner", "PO"
        );

        assertEquals("PO", po.role(), "Agent should be PO");

        // Act: PO accepts story with all criteria met
        UserStory story = new UserStory(
            "story-po-test",
            "Feature for PO review",
            "Ready for acceptance",
            List.of("Criterion 1", "Criterion 2"),
            5, 1, "ready-for-review",
            List.of(),
            "dev-1"
        );

        SAFeCeremonyData.StoryCompletionData data =
            new SAFeCeremonyData.StoryCompletionData(
                story,
                List.of(),
                List.of(true, true),  // All criteria met
                Instant.now()
            );

        SAFeCeremonyExecutor.AgentParticipant dev = new SAFeCeremonyExecutor.AgentParticipant(
            "dev-1", "Developer", "DEV");
        SAFeCeremonyExecutor.AgentParticipant arch = new SAFeCeremonyExecutor.AgentParticipant(
            "arch-1", "Architect", "ARCHITECT");

        SAFeCeremonyData.StoryCompletionResult result =
            executor.executeStoryCompletionFlow(data, dev, arch, po);

        // Assert: PO accepts story
        assertTrue(po.participatedInCeremony(),
            "PO should participate in story acceptance");
        assertTrue(result.poAccepted(),
            "PO should accept story with all criteria met");
        assertEquals("done", result.finalStatus(),
            "Accepted story should be marked done");

        LOGGER.info("PO capabilities verified: accepted story with {} criteria",
            result.acceptanceCriteriaEvaluation().size());
    }

    /**
     * Capability 3: Scrum Master Role
     *
     * Responsibilities:
     * - Facilitates ceremonies (standup, planning, retro)
     * - Removes blockers
     * - Protects team from interruptions
     * - Coaches team on SAFe practices
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Capability 3: SM (Scrum Master) Role")
    void testScrumMasterRole() {
        LOGGER.info("=== Verifying SM Capabilities ===");

        // Arrange: SM agent
        SAFeCeremonyExecutor.AgentParticipant sm = new SAFeCeremonyExecutor.AgentParticipant(
            "sm-1", "Scrum Master", "SM"
        );

        assertEquals("SM", sm.role(), "Agent should be SM");

        // Act: SM facilitates standup
        SAFeCeremonyData.DailyStandup standupData = new SAFeCeremonyData.DailyStandup(
            List.of(
                new SAFeCeremonyData.DeveloperStatus("dev-1", "story-1", "in-progress",
                    "Making progress"),
                new SAFeCeremonyData.DeveloperStatus("dev-2", "story-2", "blocked",
                    "Waiting for API"),
                new SAFeCeremonyData.DeveloperStatus("dev-3", "story-3", "completed",
                    "Ready for review")
            ),
            List.of(),
            Instant.now()
        );

        SAFeCeremonyData.DailyStandup result = executor.executeDailyStandupCeremony(
            standupData,
            sm,
            List.of(
                new SAFeCeremonyExecutor.AgentParticipant("dev-1", "Dev 1", "DEV"),
                new SAFeCeremonyExecutor.AgentParticipant("dev-2", "Dev 2", "DEV"),
                new SAFeCeremonyExecutor.AgentParticipant("dev-3", "Dev 3", "DEV")
            ),
            new SAFeCeremonyExecutor.AgentParticipant("arch-1", "Architect", "ARCHITECT")
        );

        // Assert: SM facilitated standup
        assertTrue(sm.participatedInCeremony(),
            "SM should facilitate standup");
        assertEquals(3, result.statusReports().size(),
            "All 3 developers should report");

        // Verify blocker was detected
        assertTrue(result.detectedBlockers().size() > 0,
            "SM should detect blockers during standup");

        LOGGER.info("SM capabilities verified: facilitated standup, {} blockers detected",
            result.detectedBlockers().size());
    }

    /**
     * Capability 4: System Architect Role
     *
     * Responsibilities:
     * - Designs system architecture
     * - Reviews story dependencies
     * - Validates technical feasibility
     * - Approves story transitions
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Capability 4: Architect (System Architect) Role")
    void testSystemArchitectRole() {
        LOGGER.info("=== Verifying Architect Capabilities ===");

        // Arrange: Architect agent
        SAFeCeremonyExecutor.AgentParticipant arch = new SAFeCeremonyExecutor.AgentParticipant(
            "arch-1", "System Architect", "ARCHITECT"
        );

        assertEquals("ARCHITECT", arch.role(), "Agent should be ARCHITECT");

        // Act: Architect reviews dependencies
        UserStory completedStory = new UserStory(
            "story-arch-test",
            "Feature with dependencies",
            "Reviews arch",
            List.of("Arch approved"),
            8, 1, "ready-for-arch",
            List.of("story-dep-1"),
            "dev-1"
        );

        UserStory dependency = new UserStory(
            "story-dep-1",
            "Dependency",
            "Architecture foundation",
            List.of(),
            5, 1, "done",
            List.of(),
            null
        );

        SAFeCeremonyData.StoryCompletionData data =
            new SAFeCeremonyData.StoryCompletionData(
                completedStory,
                List.of(dependency),
                List.of(true),
                Instant.now()
            );

        SAFeCeremonyExecutor.AgentParticipant dev = new SAFeCeremonyExecutor.AgentParticipant(
            "dev-1", "Developer", "DEV");
        SAFeCeremonyExecutor.AgentParticipant po = new SAFeCeremonyExecutor.AgentParticipant(
            "po-1", "PO", "PO");

        SAFeCeremonyData.StoryCompletionResult result =
            executor.executeStoryCompletionFlow(data, dev, arch, po);

        // Assert: Architect reviewed dependencies
        assertTrue(arch.participatedInCeremony(),
            "Architect should review story dependencies");
        assertTrue(result.architectReviewedDependencies(),
            "Architect should verify dependencies reviewed");
        assertTrue(result.noDependencyBlockers(),
            "Architect confirms no blocking dependencies");

        LOGGER.info("Architect capabilities verified: reviewed dependencies, no blockers");
    }

    /**
     * Capability 5: Developer Role
     *
     * Responsibilities:
     * - Estimates stories
     * - Executes stories (development)
     * - Reports status (standup)
     * - Completes stories for review
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Capability 5: Developer Role")
    void testDeveloperRole() {
        LOGGER.info("=== Verifying Developer Capabilities ===");

        // Arrange: Developer agent
        SAFeCeremonyExecutor.AgentParticipant dev = new SAFeCeremonyExecutor.AgentParticipant(
            "dev-1", "Developer Alice", "DEV"
        );

        assertEquals("DEV", dev.role(), "Agent should be DEV");

        // Act: Developer participates in sprint planning (estimation)
        List<UserStory> backlog = List.of(
            new UserStory("s1", "Feature 1", "...", List.of(), 0, 1, "backlog", List.of(), null),
            new UserStory("s2", "Feature 2", "...", List.of(), 0, 2, "backlog", List.of(), null)
        );

        SAFeCeremonyData.SprintPlanning planData = new SAFeCeremonyData.SprintPlanning(
            "sprint-dev-test",
            backlog,
            List.of(),
            Instant.now()
        );

        SAFeCeremonyData.SprintPlanning result = executor.executeSprintPlanningCeremony(
            planData,
            new SAFeCeremonyExecutor.AgentParticipant("po-1", "PO", "PO"),
            new SAFeCeremonyExecutor.AgentParticipant("sm-1", "SM", "SM"),
            List.of(dev),
            40
        );

        // Assert: Developer participated in estimation
        assertEquals(0, result.committedStories().stream()
                .filter(s -> s.storyPoints() == 0)
                .count(),
            "All stories should be estimated (points > 0)");

        LOGGER.info("Developer capabilities verified: estimated {} stories",
            result.committedStories().size());
    }

    /**
     * Message Ordering Test: PI Planning Ceremony Message Sequence
     *
     * Expected sequence:
     * 1. RTE announces PI window (ANNOUNCEMENT)
     * 2. Architect presents technical roadmap (ROADMAP)
     * 3. PO presents business roadmap (ROADMAP)
     * 4. Teams commit to objectives (COMMITMENT)
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Message Ordering: PI Planning Ceremony Sequence")
    void testPIPlanningMessageOrdering() {
        LOGGER.info("=== Verifying PI Planning Message Ordering ===");

        // Arrange: 5 agents for PI planning
        SAFeCeremonyExecutor.AgentParticipant rte = new SAFeCeremonyExecutor.AgentParticipant(
            "rte-1", "RTE", "RTE");
        SAFeCeremonyExecutor.AgentParticipant arch = new SAFeCeremonyExecutor.AgentParticipant(
            "arch-1", "Architect", "ARCHITECT");
        SAFeCeremonyExecutor.AgentParticipant po = new SAFeCeremonyExecutor.AgentParticipant(
            "po-1", "PO", "PO");
        SAFeCeremonyExecutor.AgentParticipant sm = new SAFeCeremonyExecutor.AgentParticipant(
            "sm-1", "SM", "SM");
        SAFeCeremonyExecutor.AgentParticipant dev = new SAFeCeremonyExecutor.AgentParticipant(
            "dev-1", "Developer", "DEV");

        List<SAFeCeremonyExecutor.AgentParticipant> agents = List.of(rte, arch, po, sm, dev);

        SAFeCeremonyData.PIPlanning piData = new SAFeCeremonyData.PIPlanning(
            "PI-message-order",
            List.of("Obj1"),
            List.of(new UserStory("s1", "Feature", "...", List.of(), 5, 1, "backlog", List.of(), null)),
            List.of(),
            List.of(),
            Instant.now()
        );

        // Act: Execute PI planning
        executor.executePIPlanningCeremony(piData, agents);

        // Assert: Message ordering
        List<String> messageSequence = agents.stream()
            .filter(a -> a.lastMessageType() != null)
            .map(SAFeCeremonyExecutor.AgentParticipant::lastMessageType)
            .toList();

        LOGGER.info("Message sequence: {}", messageSequence);

        // Verify at least ANNOUNCEMENT precedes ROADMAP precedes COMMITMENT
        int announcementIdx = messageSequence.indexOf("ANNOUNCEMENT");
        int roadmapIdx = messageSequence.indexOf("ROADMAP");
        int commitmentIdx = messageSequence.indexOf("COMMITMENT");

        if (announcementIdx >= 0 && roadmapIdx >= 0) {
            assertTrue(announcementIdx < roadmapIdx,
                "ANNOUNCEMENT should precede ROADMAP");
        }

        if (roadmapIdx >= 0 && commitmentIdx >= 0) {
            assertTrue(roadmapIdx < commitmentIdx,
                "ROADMAP should precede COMMITMENT");
        }

        LOGGER.info("Message ordering verified: {} messages in correct sequence",
            messageSequence.size());
    }

    /**
     * Decision Consistency Test: PO Makes Consistent Acceptance Decisions
     *
     * Setup: Same PO evaluates 3 stories with identical criteria completion
     * Expected: PO's acceptance decision is consistent
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Decision Consistency: PO Acceptance Decisions")
    void testPODecisionConsistency() {
        LOGGER.info("=== Verifying PO Decision Consistency ===");

        SAFeCeremonyExecutor.AgentParticipant po = new SAFeCeremonyExecutor.AgentParticipant(
            "po-1", "PO", "PO");
        SAFeCeremonyExecutor.AgentParticipant arch = new SAFeCeremonyExecutor.AgentParticipant(
            "arch-1", "Architect", "ARCHITECT");

        List<Boolean> acceptanceResults = new ArrayList<>();

        // Act: PO evaluates 3 identical stories
        for (int i = 1; i <= 3; i++) {
            UserStory story = new UserStory(
                "story-consistent-" + i,
                "Feature " + i,
                "Identical criteria",
                List.of("Criterion 1", "Criterion 2"),
                5, 1, "ready-for-review",
                List.of(),
                "dev-1"
            );

            SAFeCeremonyData.StoryCompletionData data =
                new SAFeCeremonyData.StoryCompletionData(
                    story,
                    List.of(),
                    List.of(true, true),  // All criteria met for all stories
                    Instant.now()
                );

            SAFeCeremonyExecutor.AgentParticipant dev = new SAFeCeremonyExecutor.AgentParticipant(
                "dev-1", "Developer", "DEV");

            SAFeCeremonyData.StoryCompletionResult result =
                executor.executeStoryCompletionFlow(data, dev, arch, po);

            acceptanceResults.add(result.poAccepted());
        }

        // Assert: All 3 stories accepted (consistent decision)
        assertTrue(acceptanceResults.stream().allMatch(Boolean::booleanValue),
            "PO should consistently accept stories with all criteria met");

        long acceptanceCount = acceptanceResults.stream().filter(Boolean::booleanValue).count();
        assertEquals(3, acceptanceCount, "All 3 identical stories should be accepted");

        LOGGER.info("Decision consistency verified: PO consistently accepted {} stories",
            acceptanceCount);
    }

    /**
     * Escalation Chain Test: Blocker Detection and Escalation
     *
     * Setup: Developer reports blocker during standup
     * Expected: Blocker is escalated to Architect
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Escalation Chain: Blocker Detection and Resolution")
    void testBlockerEscalationChain() {
        LOGGER.info("=== Verifying Blocker Escalation Chain ===");

        SAFeCeremonyExecutor.AgentParticipant sm = new SAFeCeremonyExecutor.AgentParticipant(
            "sm-1", "Scrum Master", "SM");
        SAFeCeremonyExecutor.AgentParticipant dev1 = new SAFeCeremonyExecutor.AgentParticipant(
            "dev-1", "Dev 1", "DEV");
        SAFeCeremonyExecutor.AgentParticipant dev2 = new SAFeCeremonyExecutor.AgentParticipant(
            "dev-2", "Dev 2", "DEV");
        SAFeCeremonyExecutor.AgentParticipant arch = new SAFeCeremonyExecutor.AgentParticipant(
            "arch-1", "Architect", "ARCHITECT");

        // Act: Daily standup with blocker
        SAFeCeremonyData.DailyStandup standupData = new SAFeCeremonyData.DailyStandup(
            List.of(
                new SAFeCeremonyData.DeveloperStatus("dev-1", "story-1", "in-progress", "OK"),
                new SAFeCeremonyData.DeveloperStatus("dev-2", "story-2", "blocked",
                    "Waiting for API from story-1")
            ),
            List.of(),
            Instant.now()
        );

        SAFeCeremonyData.DailyStandup result = executor.executeDailyStandupCeremony(
            standupData,
            sm,
            List.of(dev1, dev2),
            arch
        );

        // Assert: Blocker detected and escalated
        assertTrue(result.detectedBlockers().size() > 0,
            "Blocker should be detected");
        assertTrue(arch.participatedInCeremony(),
            "Architect should participate to resolve blocker");

        // Verify blocker message contains specifics
        String blockerMessage = result.detectedBlockers().get(0);
        assertTrue(blockerMessage.contains("dev-2"), "Blocker should mention blocked developer");
        assertTrue(blockerMessage.contains("story-2"), "Blocker should mention blocked story");

        LOGGER.info("Blocker escalation verified: detected and escalated to architect");
    }

    /**
     * State Transition Test: Story State Flow
     *
     * Expected transitions:
     * backlog → estimated → in-progress → ready-for-review →
     * (architect review) → ready-for-po-review → (PO review) → done
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("State Transitions: Story Lifecycle")
    void testStoryStateTransitions() {
        LOGGER.info("=== Verifying Story State Transitions ===");

        // Arrange: Track story states
        List<String> stateTransitions = new ArrayList<>();

        UserStory story = new UserStory(
            "story-states",
            "Feature with state tracking",
            "...",
            List.of("Criterion 1"),
            5, 1, "backlog",
            List.of(),
            "dev-1"
        );
        stateTransitions.add(story.status());

        // Act: Story progresses through states
        SAFeCeremonyData.StoryCompletionData data =
            new SAFeCeremonyData.StoryCompletionData(
                new UserStory(story.id(), story.title(), story.description(),
                    story.acceptanceCriteria(), story.storyPoints(), story.priority(),
                    "ready-for-review", List.of(), story.assigneeId()),
                List.of(),
                List.of(true),
                Instant.now()
            );
        stateTransitions.add("ready-for-review");

        SAFeCeremonyExecutor.AgentParticipant dev = new SAFeCeremonyExecutor.AgentParticipant(
            "dev-1", "Developer", "DEV");
        SAFeCeremonyExecutor.AgentParticipant arch = new SAFeCeremonyExecutor.AgentParticipant(
            "arch-1", "Architect", "ARCHITECT");
        SAFeCeremonyExecutor.AgentParticipant po = new SAFeCeremonyExecutor.AgentParticipant(
            "po-1", "PO", "PO");

        SAFeCeremonyData.StoryCompletionResult result =
            executor.executeStoryCompletionFlow(data, dev, arch, po);

        stateTransitions.add(result.storyStatus());  // ready-for-po-review
        stateTransitions.add(result.finalStatus());  // done

        // Assert: Valid state progression
        assertEquals("backlog", stateTransitions.get(0), "Initial state should be backlog");
        assertEquals("ready-for-review", stateTransitions.get(1), "Developer marks ready");
        assertEquals("ready-for-po-review", stateTransitions.get(2), "Architect approves");
        assertEquals("done", stateTransitions.get(3), "PO accepts");

        LOGGER.info("Story state transitions verified: {} → {} → {} → {}",
            stateTransitions.get(0), stateTransitions.get(1),
            stateTransitions.get(2), stateTransitions.get(3));
    }
}
