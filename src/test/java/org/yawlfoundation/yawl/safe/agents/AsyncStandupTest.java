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
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD (Detroit School) Integration Tests for AsyncStandupCoordinator.
 *
 * Tests real asynchronous status collection, auto-report generation,
 * blocker tracking, and anomaly detection. No mocks or stubs — all integration
 * tests with actual status data processing.
 *
 * Test coverage:
 * - Status update recording and history tracking
 * - Blocker persistence and escalation
 * - Standup report generation with metrics
 * - Anomaly detection (same blocker, high utilization, silent members)
 * - Action item generation
 * - Team utilization calculation
 * - Report formatting for communication
 *
 * @since YAWL 6.0
 */
@DisplayName("Async Standup Coordination Test Suite")
@Timeout(value = 10, unit = TimeUnit.SECONDS)
public class AsyncStandupTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncStandupTest.class);

    private AsyncStandupCoordinator coordinator;
    private static final String TEAM_ID = "TEAM-ALPHA";
    private static final int TEAM_SIZE = 6;

    @BeforeEach
    void setUp() {
        LOGGER.info("Setting up AsyncStandupTest for team: {}", TEAM_ID);
        coordinator = new AsyncStandupCoordinator(TEAM_ID, TEAM_SIZE);
    }

    @Test
    @DisplayName("Should record developer status updates")
    void testRecordStatusUpdate() {
        AsyncStandupCoordinator.StatusUpdate update = new AsyncStandupCoordinator.StatusUpdate(
            "dev-1",
            "Alice",
            Instant.now(),
            "Implemented user login feature",
            "Writing unit tests for login",
            List.of(),
            21,
            null
        );

        String updateId = coordinator.recordUpdate(update);

        assertNotNull(updateId, "Should return unique update ID");
        assertTrue(updateId.contains("dev-1"), "Update ID should contain developer ID");

        List<AsyncStandupCoordinator.StatusUpdate> history = coordinator.getAllUpdates("dev-1");
        assertEquals(1, history.size(), "Should have one update recorded");
        assertEquals(update.whatIDid(), history.getFirst().whatIDid(), "Update should be stored");
    }

    @Test
    @DisplayName("Should retrieve latest status update for developer")
    void testGetLatestUpdate() {
        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-2", "Bob", Instant.now().minusSeconds(3600),
            "Old work", "Old task", List.of(), 10, null));

        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-2", "Bob", Instant.now(),
            "Current work", "Current task", List.of(), 20, null));

        var latest = coordinator.getLatestUpdate("dev-2");

        assertTrue(latest.isPresent(), "Should have latest update");
        assertEquals("Current work", latest.get().whatIDid(), "Should return most recent update");
    }

    @Test
    @DisplayName("Should track blocker as it persists across updates")
    void testBlockerPersistence() {
        String blockDescription = "Waiting for API documentation from Platform team";

        // Day 1: Blocker reported
        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-3", "Charlie", Instant.now(),
            "Worked on feature", "Blocked", List.of(blockDescription), 15, null));

        List<AsyncStandupCoordinator.Blocker> day1Blockers = coordinator.getDevBlockers("dev-3");
        assertEquals(1, day1Blockers.size(), "Should have 1 blocker on day 1");
        assertEquals(1, day1Blockers.getFirst().daysPersistent(), "Should be day 1");

        // Day 2: Same blocker reported again
        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-3", "Charlie", Instant.now().plus(1, ChronoUnit.DAYS),
            "Still working on feature", "Still blocked", List.of(blockDescription), 0, null));

        List<AsyncStandupCoordinator.Blocker> day2Blockers = coordinator.getDevBlockers("dev-3");
        assertEquals(1, day2Blockers.size(), "Should still have 1 blocker");
        assertEquals(2, day2Blockers.getFirst().daysPersistent(), "Should be day 2");

        LOGGER.info("Blocker tracked: {} days persistent", day2Blockers.getFirst().daysPersistent());
    }

    @Test
    @DisplayName("Should escalate blocker severity after 3 days")
    void testBlockerEscalation() {
        String blockDescription = "Critical infrastructure issue";

        // Record same blocker for 3 consecutive days
        for (int day = 0; day < 3; day++) {
            coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
                "dev-4", "Diana", Instant.now().plus(day, ChronoUnit.DAYS),
                "Waiting on infrastructure team", "Blocked",
                List.of(blockDescription), 0, null));
        }

        List<AsyncStandupCoordinator.Blocker> blockers = coordinator.getDevBlockers("dev-4");
        AsyncStandupCoordinator.Blocker escalated = blockers.getFirst();

        assertEquals(3, escalated.daysPersistent(), "Should be persistent for 3 days");
        assertTrue(escalated.severity().equals("HIGH") || escalated.severity().equals("CRITICAL"),
            "Should be escalated to HIGH or CRITICAL");
        assertTrue(escalated.suggestedEscalation().contains("ESCALATE"),
            "Should recommend escalation");

        LOGGER.info("Blocker escalated: severity={}, suggestion={}", escalated.severity(), escalated.suggestedEscalation());
    }

    @Test
    @DisplayName("Should resolve blocker when fixed")
    void testBlockerResolution() {
        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-5", "Eve", Instant.now(),
            "Working on feature", "Blocked",
            List.of("Missing test data"), 10, null));

        List<AsyncStandupCoordinator.Blocker> beforeFix = coordinator.getDevBlockers("dev-5");
        assertEquals(1, beforeFix.size(), "Should have blocker before resolution");

        String blockerId = beforeFix.getFirst().id();
        coordinator.resolveBlocker(blockerId, "Test data provided by QA team");

        List<AsyncStandupCoordinator.Blocker> afterFix = coordinator.getDevBlockers("dev-5");
        assertEquals(0, afterFix.size(), "Should have no blockers after resolution");
    }

    @Test
    @DisplayName("Should generate standup report with progress summary")
    void testStandupReportGeneration() {
        // Record updates from multiple developers
        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-1", "Alice", Instant.now(),
            "Completed login feature", "Writing tests", List.of(), 13, null));

        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-2", "Bob", Instant.now(),
            "Refactored database layer", "Optimizing queries", List.of(), 21, null));

        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-3", "Charlie", Instant.now(),
            "Fixed authentication bug", "Documenting fix", List.of(), 8, null));

        AsyncStandupCoordinator.StandupReport report = coordinator.generateStandupReport();

        assertAll(
            () -> assertEquals(3, report.updatesReceived(), "Should have 3 updates"),
            () -> assertEquals(42, report.pointsCompleted(), "Should sum 13+21+8"),
            () -> assertTrue(report.teamUtilization() > 0, "Should calculate utilization"),
            () -> assertFalse(report.progressSummary().isBlank(), "Should have progress summary"),
            () -> assertNotNull(report.actionItems(), "Should have action items")
        );

        LOGGER.info("Report generated: {} updates, {} points, {:.0f}% utilization",
            report.updatesReceived(), report.pointsCompleted(), report.teamUtilization() * 100);
    }

    @Test
    @DisplayName("Should detect same blocker persisting as anomaly")
    void testSameBlockerAnomaly() {
        String persistentBlockDescription = "Waiting for API authentication service";

        // Multiple developers report same blocker over 3 days
        for (int day = 0; day < 3; day++) {
            coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
                "dev-a", "Alice", Instant.now().plus(day, ChronoUnit.DAYS),
                "Blocked", "Still blocked", List.of(persistentBlockDescription), 0, null));

            coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
                "dev-b", "Bob", Instant.now().plus(day, ChronoUnit.DAYS),
                "Blocked", "Still blocked", List.of(persistentBlockDescription), 0, null));
        }

        AsyncStandupCoordinator.StandupReport report = coordinator.generateStandupReport();

        // Should have escalated blocker in report
        assertTrue(report.escalatedBlockers().size() > 0,
            "Should have escalated blockers");

        LOGGER.info("Same blocker anomaly detected: {} escalations",
            report.escalatedBlockers().size());
    }

    @Test
    @DisplayName("Should detect high team utilization as anomaly")
    void testHighUtilizationAnomaly() {
        // Record updates with high point completions (over-capacity)
        for (int i = 0; i < 6; i++) {
            coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
                String.format("dev-%d", i), String.format("Dev%d", i), Instant.now(),
                "Heavy work", "Continuing", List.of(), 95, null));  // High points
        }

        AsyncStandupCoordinator.StandupReport report = coordinator.generateStandupReport();

        assertTrue(report.teamUtilization() > 0.9,
            "Should calculate high utilization");
        assertTrue(report.riskSummary().contains("utilization") || report.riskSummary().contains("WARNING"),
            "Should include utilization warning in report");

        LOGGER.info("High utilization detected: {:.0f}%", report.teamUtilization() * 100);
    }

    @Test
    @DisplayName("Should flag escalated blockers in report")
    void testEscalatedBlockersInReport() {
        String blockDescription = "Production database connection issue";

        // Simulate blocker persisting for 3+ days
        for (int day = 0; day < 3; day++) {
            coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
                "dev-6", "Frank", Instant.now().plus(day, ChronoUnit.DAYS),
                "Production issue response", "In progress",
                List.of(blockDescription), 0, "CRITICAL ISSUE"));
        }

        AsyncStandupCoordinator.StandupReport report = coordinator.generateStandupReport();

        assertTrue(report.escalatedBlockers().size() > 0,
            "Should have escalated blockers");
        assertFalse(report.actionItems().isEmpty(),
            "Should have action items for escalations");

        // Action items should reference escalations
        boolean hasEscalationAction = report.actionItems().stream()
            .anyMatch(a -> a.contains("ESCALATE"));
        assertTrue(hasEscalationAction, "Should have escalation action items");

        LOGGER.info("Report with escalations: {} escalated blockers",
            report.escalatedBlockers().size());
    }

    @Test
    @DisplayName("Should calculate accurate team velocity")
    void testTeamVelocityCalculation() {
        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-1", "Alice", Instant.now(), "Work", "Task", List.of(), 13, null));

        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-2", "Bob", Instant.now(), "Work", "Task", List.of(), 21, null));

        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-3", "Charlie", Instant.now(), "Work", "Task", List.of(), 8, null));

        double velocity = coordinator.calculateTeamVelocity();

        assertEquals((13.0 + 21.0 + 8.0) / 3.0, velocity, 0.01,
            "Velocity should be average points per developer");

        LOGGER.info("Team velocity: {:.1f} points/dev", velocity);
    }

    @Test
    @DisplayName("Should format report as readable text")
    void testReportFormatting() {
        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-1", "Alice", Instant.now(), "Completed feature", "Writing docs",
            List.of("Need API spec"), 13, null));

        AsyncStandupCoordinator.StandupReport report = coordinator.generateStandupReport();
        String formatted = coordinator.formatReportAsText(report);

        assertAll(
            () -> assertTrue(formatted.contains("ASYNC STANDUP REPORT"), "Should have title"),
            () -> assertTrue(formatted.contains("Progress"), "Should have progress section"),
            () -> assertTrue(formatted.contains("SUMMARY"), "Should have summary"),
            () -> assertTrue(formatted.contains("ACTION ITEMS"), "Should have action items"),
            () -> assertFalse(formatted.isBlank(), "Should not be empty")
        );

        LOGGER.info("Formatted report:\n{}", formatted);
    }

    @Test
    @DisplayName("Should handle multiple blockers from single developer")
    void testMultipleBlockers() {
        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-7", "Grace", Instant.now(), "Multiple issues", "Investigating",
            List.of("Missing database schema", "API timeout in production", "Security audit pending"),
            5,
            "Multiple critical issues"));

        List<AsyncStandupCoordinator.Blocker> blockers = coordinator.getDevBlockers("dev-7");

        assertEquals(3, blockers.size(), "Should track all 3 blockers");
        assertTrue(blockers.stream().allMatch(b -> b.developerId().equals("dev-7")),
            "All blockers should be assigned to dev-7");

        LOGGER.info("Developer Grace has {} blockers", blockers.size());
    }

    @Test
    @DisplayName("Should track risk notes from updates")
    void testRiskNoteTracking() {
        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-8", "Henry", Instant.now(), "Features", "More features",
            List.of(), 18, "Database migration script needs review before production"));

        AsyncStandupCoordinator.StandupReport report = coordinator.generateStandupReport();

        assertTrue(report.riskSummary().contains("migration") || report.riskSummary().contains("database"),
            "Should include risk notes in report");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    @DisplayName("Should handle various team sizes")
    void testVariousTeamSizes(int teamSize) {
        AsyncStandupCoordinator multiSizeCoordinator = new AsyncStandupCoordinator(
            String.format("TEAM-SIZE-%d", teamSize), teamSize);

        // Record one update
        multiSizeCoordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-1", "Developer", Instant.now(), "Work", "More work", List.of(), 10, null));

        AsyncStandupCoordinator.StandupReport report = multiSizeCoordinator.generateStandupReport();

        assertEquals(teamSize, report.totalParticipants(), "Should respect team size");
    }

    @Test
    @DisplayName("Should provide team summary string")
    void testTeamSummary() {
        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-1", "Alice", Instant.now(), "Work", "More", List.of(), 20, null));

        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-2", "Bob", Instant.now(), "Work", "More", List.of("Blocked"), 15, null));

        String summary = coordinator.getTeamSummary();

        assertAll(
            () -> assertTrue(summary.contains(TEAM_ID), "Should include team ID"),
            () -> assertTrue(summary.contains("members="), "Should show team size"),
            () -> assertTrue(summary.contains("updates=2"), "Should show update count"),
            () -> assertTrue(summary.contains("blockers=1"), "Should show blocker count"),
            () -> assertTrue(summary.contains("velocity="), "Should show velocity")
        );

        LOGGER.info("Team summary: {}", summary);
    }

    @Test
    @DisplayName("Should generate actionable recommendations")
    void testActionableRecommendations() {
        // Create scenario with multiple issues
        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-1", "Alice", Instant.now(), "Work", "Blocked",
            List.of("Critical database issue"), 5, null));

        coordinator.recordUpdate(new AsyncStandupCoordinator.StatusUpdate(
            "dev-2", "Bob", Instant.now(), "Work", "Blocked",
            List.of("Critical database issue"), 5, null));

        AsyncStandupCoordinator.StandupReport report = coordinator.generateStandupReport();

        assertFalse(report.actionItems().isEmpty(), "Should have action items");
        assertTrue(report.actionItems().stream()
                .anyMatch(a -> a.contains("ESCALATE") || a.contains("Escalation")),
            "Should recommend escalation for critical issues");

        LOGGER.info("Action items: {}", report.actionItems());
    }

    @Test
    @DisplayName("Should handle empty coordinator gracefully")
    void testEmptyCoordinator() {
        AsyncStandupCoordinator empty = new AsyncStandupCoordinator("EMPTY-TEAM", 5);

        AsyncStandupCoordinator.StandupReport report = empty.generateStandupReport();

        assertAll(
            () -> assertEquals(0, report.updatesReceived(), "Should have 0 updates"),
            () -> assertEquals(0, report.pointsCompleted(), "Should have 0 points"),
            () -> assertEquals(0.0, report.teamUtilization(), "Should have 0 utilization"),
            () -> assertTrue(report.progressSummary().contains("No progress"), "Should indicate no updates")
        );
    }
}
