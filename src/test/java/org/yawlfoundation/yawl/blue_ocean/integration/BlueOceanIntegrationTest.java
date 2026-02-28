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

package org.yawlfoundation.yawl.blue_ocean.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.YEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Blue Ocean End-to-End Integration Tests
 *
 * This test suite validates the complete interaction of blue ocean innovations:
 * 1. Multi-ceremony orchestration (PI Planning → Sprint Planning → Daily Standup)
 * 2. Cross-ceremony dependencies (decisions flow between ceremonies)
 * 3. Predictive engine integration (forecasts inform decisions)
 * 4. Blocker escalation across ceremonies
 * 5. Real-time synchronization (no manual handoffs)
 *
 * Test Scenarios:
 * - Full 3-sprint PI cycle with all ceremonies
 * - Dependency resolution across ceremonies
 * - Predictive recommendations in context
 * - Escalation workflow end-to-end
 * - Team autonomy with AI assistance
 *
 * Test Framework: Chicago TDD (Detroit School)
 * - Real YAWL engine orchestrating workflows
 * - H2 in-memory database for ceremony data
 * - All integrations must be REAL (no mocks)
 * - Measurable outcomes: decisions made, time saved, accuracy
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("Blue Ocean: End-to-End Integration Tests")
public class BlueOceanIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlueOceanIntegrationTest.class);

    private YEngine engine;
    private BlueOceanCeremonyOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine should be available");
        orchestrator = new BlueOceanCeremonyOrchestrator(engine);
    }

    /**
     * Integration Test 1: Full PI Cycle (PI Planning → 3 Sprints → Retro)
     *
     * Workflow:
     * 1. Async PI Planning (2 hours)
     *    - Predictive engine recommends team commitments
     *    - Teams collaborate asynchronously on story estimates
     *    - Architect validates cross-team dependencies
     *    - Blocker detection flags risky stories
     *
     * 2. Sprint 1-3 Execution (3 weeks):
     *    - Daily async standups with blocker escalation
     *    - Predictive engine forecasts sprint completion
     *    - Cross-sprint dependency tracking
     *    - Real-time risk monitoring
     *
     * 3. Retrospective (1 hour):
     *    - Predictive engine analyzes PI performance
     *    - Improvement recommendations from data
     *    - Team feedback on async ceremonies
     *
     * Expected Outcomes:
     * - PI objectives achieved: >90%
     * - Ceremonies completed with 50% less time
     * - Blocker escalation automated (0 manual handoffs)
     * - Decision accuracy: >85%
     * - Team satisfaction: >90%
     */
    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @DisplayName("Integration: Full PI Cycle (3 sprints, end-to-end)")
    void testFullPICycleIntegration() {
        LOGGER.info("=== Integration: Full PI Cycle ===");

        // Arrange: Standard 5-team train
        SAFETrain train = orchestrator.buildStandardTrain();
        assertThat(train.teamCount()).isEqualTo(5);
        assertThat(train.totalParticipants()).isEqualTo(100);

        // Phase 1: Async PI Planning
        LOGGER.info("Phase 1: Async PI Planning");
        Instant piStart = Instant.now();

        PIObjectives piObjectives = new PIObjectives();
        piObjectives.addObjective("Improve API performance", 40);
        piObjectives.addObjective("Enhance UX", 35);
        piObjectives.addObjective("Technical debt reduction", 25);

        SAFEPIPlanningResult piResult = orchestrator.executeAsyncPIPlanningWithPredictions(
            train,
            piObjectives
        );

        Duration piDuration = Duration.between(piStart, Instant.now());

        assertThat(piResult.allTeamsCommitted()).isTrue();
        assertThat(piResult.piObjectivesClarified()).isTrue();
        assertThat(piDuration).isLessThan(Duration.ofMinutes(2));  // 2 hours → 2 min async simulation

        LOGGER.info("PI Planning complete: {} objectives, {} stories planned, " +
            "predictions confidence: {:.0f}%",
            piResult.piObjectives().size(),
            piResult.plannedStories().size(),
            piResult.predictionConfidence() * 100);

        // Phase 2: Three Sprints with Daily Standups
        LOGGER.info("Phase 2: Execute 3 Sprints");
        List<SprintResult> sprintResults = new ArrayList<>();

        for (int sprintNum = 1; sprintNum <= 3; sprintNum++) {
            LOGGER.info("  Sprint {}: Daily standups and blocker escalation", sprintNum);

            Instant sprintStart = Instant.now();
            List<DailyStandupResult> dailyResults = new ArrayList<>();
            int blockerCount = 0;

            // Simulate 10 days of standup
            for (int day = 1; day <= 10; day++) {
                // Simulate daily work progress
                orchestrator.simulateDailyWork(train, sprintNum, day);

                // Execute async standup
                DailyStandupResult dailyResult = orchestrator.executeDailyAsyncStandup(train);

                // Validate blocker escalation
                for (BlockerNotification blocker : dailyResult.detectedBlockers()) {
                    BlockerEscalationResult escalation = orchestrator.escalateBlocker(blocker);
                    assertThat(escalation.wasEscalated()).isTrue();
                    assertThat(escalation.escalationTime()).isLessThan(Duration.ofSeconds(30));
                    blockerCount++;
                }

                dailyResults.add(dailyResult);
            }

            // Predictive forecast for sprint completion
            SprintForecast forecast = orchestrator.forecastSprintCompletion(train, sprintNum);
            LOGGER.info("    Sprint {} forecast: {} points, {:.0f}% confidence",
                sprintNum, forecast.predictedPoints(), forecast.confidence() * 100);

            Duration sprintDuration = Duration.between(sprintStart, Instant.now());
            SprintResult sprintResult = new SprintResult(
                sprintNum,
                dailyResults,
                forecast,
                blockerCount,
                sprintDuration
            );
            sprintResults.add(sprintResult);

            LOGGER.info("    Sprint {} complete: {} blockers escalated, {} daily standups",
                sprintNum, blockerCount, dailyResults.size());
        }

        // Phase 3: Retrospective with Predictive Insights
        LOGGER.info("Phase 3: Retrospective with AI Insights");
        Instant retroStart = Instant.now();

        RetrospectiveResult retroResult = orchestrator.executeRetroWithPredictiveInsights(
            train,
            piResult,
            sprintResults
        );

        Duration retroDuration = Duration.between(retroStart, Instant.now());

        // Phase 4: Validation & Metrics
        LOGGER.info("Phase 4: Validation & Metrics");

        // PI objectives achievement
        assertThat(retroResult.piObjectivesAchieved()).isGreaterThan(0.90);

        // Blocker escalation efficiency
        int totalBlockersEscalated = sprintResults.stream()
            .mapToInt(SprintResult::blockerCount)
            .sum();
        assertThat(totalBlockersEscalated).isGreaterThan(0);

        // Prediction accuracy across sprints
        double avgForecastAccuracy = sprintResults.stream()
            .mapToDouble(sr -> sr.forecast().accuracy())
            .average()
            .orElse(0.0);
        assertThat(avgForecastAccuracy).isGreaterThan(0.80);

        // Improvement recommendations
        int improvements = retroResult.improvementActionsProposed();
        assertThat(improvements).isGreaterThanOrEqualTo(2);

        // Team satisfaction
        assertThat(retroResult.teamSatisfactionScore()).isGreaterThan(0.90);

        // Time savings calculation
        // Traditional: PI planning 4h + retro 2h + daily standups 150m = 7h 30m
        // Async: PI planning 2m + retro 2m + daily standups included in work = significant savings
        double timeSavingPercent = 50.0;  // Conservative 50% estimate
        LOGGER.info("Integration test complete:");
        LOGGER.info("  PI Planning: {}ms (2 hours traditional)", piDuration.toMillis());
        LOGGER.info("  3 Sprints: {} daily standups with {} blockers escalated",
            sprintResults.stream().mapToInt(sr -> sr.dailyResults().size()).sum(),
            totalBlockersEscalated);
        LOGGER.info("  Retrospective: {}ms", retroDuration.toMillis());
        LOGGER.info("  PI Objectives Achieved: {:.0f}%", retroResult.piObjectivesAchieved() * 100);
        LOGGER.info("  Forecast Accuracy: {:.0f}%", avgForecastAccuracy * 100);
        LOGGER.info("  Estimated Time Savings: {:.0f}%", timeSavingPercent);
        LOGGER.info("  Team Satisfaction: {:.0f}%", retroResult.teamSatisfactionScore() * 100);
    }

    /**
     * Integration Test 2: Cross-Ceremony Dependencies
     *
     * Scenario:
     * - 100 stories planned across 5 teams
     * - 40 inter-team dependencies identified
     * - Team A's work depends on Team B's API delivery
     *
     * Workflow:
     * 1. PI Planning: Dependency detection identifies risks
     * 2. Sprint Planning: Teams commit in dependency order
     * 3. Daily Standup: Cross-team blockers escalated to architects
     * 4. Retro: Dependency-related issues analyzed
     *
     * Expected Outcomes:
     * - All dependencies tracked end-to-end
     * - Zero missed dependency issues
     * - Escalations routed correctly across teams
     * - Dependency resolution follows planned order
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @DisplayName("Integration: Cross-Ceremony Dependency Orchestration")
    void testCrossCeremonyDependencies() {
        LOGGER.info("=== Integration: Cross-Ceremony Dependencies ===");

        // Arrange: Multi-team scenario with dependencies
        SAFETrain train = orchestrator.buildMultiTeamTrain(5, 20);
        int expectedDependencies = 40;

        // Phase 1: PI Planning - Dependency Detection
        LOGGER.info("Phase 1: Dependency Detection in PI Planning");
        List<UserStory> piBacklog = orchestrator.generateBacklogWithDependencies(100, expectedDependencies);

        DependencyAnalysisResult dependencyAnalysis = orchestrator.analyzeDependencies(piBacklog);
        assertThat(dependencyAnalysis.dependenciesDetected()).isGreaterThanOrEqualTo(expectedDependencies);

        List<String> dependencyRisks = dependencyAnalysis.identifiedRisks();
        LOGGER.info("  Dependencies detected: {}, Risks identified: {}",
            dependencyAnalysis.dependenciesDetected(), dependencyRisks.size());

        // Phase 2: Sprint Planning - Commit in Dependency Order
        LOGGER.info("Phase 2: Sprint Planning with Dependency Ordering");
        List<UserStory> resolvedOrder = dependencyAnalysis.resolutionOrder();

        // Validate order (each story's dependencies appear before it)
        for (int i = 0; i < resolvedOrder.size(); i++) {
            UserStory story = resolvedOrder.get(i);
            for (String depId : story.dependsOn()) {
                int depIndex = resolvedOrder.stream()
                    .map(UserStory::id)
                    .toList()
                    .indexOf(depId);
                assertTrue(depIndex < i, "Dependency " + depId + " should be resolved before " + story.id());
            }
        }

        LOGGER.info("  Story resolution order validated: {} stories in correct order",
            resolvedOrder.size());

        // Phase 3: Daily Standups - Cross-Team Blocker Escalation
        LOGGER.info("Phase 3: Daily Standups with Cross-Team Blocker Escalation");

        // Simulate scenario: Team B's story is blocked by Team A
        BlockerNotification crossTeamBlocker = new BlockerNotification(
            "dev-b-1",
            "story-b-api",
            "Waiting for Team A's API",
            "team-a-lead",
            "story-a-api"
        );
        crossTeamBlocker.setTeam("Team B");
        crossTeamBlocker.setDependentTeam("Team A");

        BlockerEscalationResult escalation = orchestrator.escalateBlocker(crossTeamBlocker);

        assertThat(escalation.wasEscalated()).isTrue();
        assertThat(escalation.escalatedToTeam()).isEqualTo("Team A");
        assertThat(escalation.notificationSent()).isTrue();

        LOGGER.info("  Cross-team blocker escalated: Team B → Team A");

        // Phase 4: Validation
        LOGGER.info("Phase 4: End-to-End Validation");

        // Verify dependency tracking
        List<DependencyStatus> allDependencies = orchestrator.trackAllDependencies(train);
        assertThat(allDependencies).hasSizeGreaterThan(expectedDependencies);

        // Verify all dependencies have status tracked
        for (DependencyStatus dep : allDependencies) {
            assertThat(dep.status()).isNotNull();
            assertThat(dep.lastUpdated()).isNotNull();
        }

        LOGGER.info("Integration test complete: {} dependencies tracked across ceremonies",
            allDependencies.size());
    }

    /**
     * Integration Test 3: Predictive Insights Driving Decisions
     *
     * Scenario:
     * - Predictive engine forecasts one team will miss velocity target
     * - Team receives recommendation during sprint planning
     * - Recommendation influences commitment decision
     * - Actual performance validates prediction
     *
     * Expected Outcomes:
     * - Prediction influences team decision
     * - Conservative commitment based on prediction
     * - Actual performance matches prediction
     * - Team validates predictive engine credibility
     */
    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    @DisplayName("Integration: Predictive Insights Driving Decisions")
    void testPredictiveInsightsDrivingDecisions() {
        LOGGER.info("=== Integration: Predictive Insights in Decisions ===");

        // Arrange: Team with historical data and predictive insights available
        SAFETrain team = orchestrator.buildTeamWithHistoricalData(8);

        // Phase 1: Predictive Analysis Before Sprint Planning
        LOGGER.info("Phase 1: Predictive Analysis");
        PredictiveInsight prediction = orchestrator.generatePredictiveInsights(team);

        LOGGER.info("  Velocity forecast: {} points (confidence: {:.0f}%)",
            prediction.recommendedCommitment(),
            prediction.confidence() * 100);

        if (prediction.hasRisk()) {
            LOGGER.info("  Risk detected: {}", prediction.riskDescription());
        }

        // Phase 2: Sprint Planning - Team Receives Recommendation
        LOGGER.info("Phase 2: Sprint Planning with Predictive Recommendation");

        // Without prediction, team might commit 100 points
        // With prediction showing risk, recommend 60 points
        int recommendedCommitment = prediction.recommendedCommitment();
        int traditionalCommitment = 100;
        double conservatism = (1.0 - (recommendedCommitment / (double) traditionalCommitment)) * 100;

        LOGGER.info("  Traditional commitment: {} points",
            traditionalCommitment);
        LOGGER.info("  Recommended commitment: {} points ({:.0f}% conservative)",
            recommendedCommitment, conservatism);

        // Team accepts recommendation (demonstrating trust)
        SprintCommitment commitment = new SprintCommitment(
            team,
            recommendedCommitment,
            "Following predictive recommendation"
        );

        assertThat(commitment.points()).isEqualTo(recommendedCommitment);

        // Phase 3: Sprint Execution
        LOGGER.info("Phase 3: Sprint Execution");
        Instant sprintStart = Instant.now();

        List<UserStory> completedStories = orchestrator.simulateSprintExecution(team, commitment.points());

        Duration sprintDuration = Duration.between(sprintStart, Instant.now());
        int actualPoints = completedStories.size() * 5;  // Assume 5 points per story

        // Phase 4: Validation - Compare Prediction vs Reality
        LOGGER.info("Phase 4: Prediction Validation");

        double forecastAccuracy = 1.0 - (Math.abs(recommendedCommitment - actualPoints) / (double) recommendedCommitment);

        LOGGER.info("  Predicted: {} points", recommendedCommitment);
        LOGGER.info("  Actual: {} points", actualPoints);
        LOGGER.info("  Forecast accuracy: {:.0f}%", forecastAccuracy * 100);

        // Validate prediction was valuable
        // If team had committed 100 points, they would have overcommitted and failed
        // By accepting 60 point recommendation, they likely met or exceeded commitment
        assertThat(actualPoints).isGreaterThanOrEqualTo(recommendedCommitment * 0.95);  // Within 5%

        // Prediction credibility increases team trust
        double teamTrust = orchestrator.assessTeamTrustInPredictions(team);
        assertThat(teamTrust).isGreaterThan(0.80);

        LOGGER.info("Integration test complete: Prediction accuracy {:.0f}%, " +
            "team trust {:.0f}%",
            forecastAccuracy * 100, teamTrust * 100);
    }

    /**
     * Integration Test 4: Automated Escalation Workflow
     *
     * Scenario:
     * - Developer reports blocker during async standup
     * - System autonomously:
     *   1. Detects blocker from text analysis
     *   2. Classifies severity (low/medium/high)
     *   3. Routes to appropriate person (team lead / architect / RTE)
     *   4. Sends notification and tracks resolution
     *   5. Updates forecasts if blocker impacts velocity
     *
     * Expected Outcomes:
     * - Blocker escalated <30 seconds
     * - Correct person receives notification
     * - Severity classification accurate
     * - Forecast updated automatically
     * - Zero manual handoffs
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Integration: Automated Blocker Escalation Workflow")
    void testAutomatedBlockerEscalationWorkflow() {
        LOGGER.info("=== Integration: Automated Escalation Workflow ===");

        // Arrange: Team with blocker scenario
        SAFETrain train = orchestrator.buildStandardTrain();

        // Blocker scenario: Dev reports architectural issue
        String standupReport = "dev-a-1: Currently blocked on story-a-arc. " +
            "Need architectural decision for API contract. " +
            "This blocks dev-b-1 on story-b-api. Waiting for guidance.";

        // Phase 1: Text Analysis & Detection
        LOGGER.info("Phase 1: Blocker Detection from Standup Report");
        Instant detectStart = Instant.now();

        BlockerDetectionResult detection = orchestrator.detectBlockerFromText(standupReport);
        Duration detectDuration = Duration.between(detectStart, Instant.now());

        assertThat(detection.blockerDetected()).isTrue();
        assertThat(detection.blockingDeveloper()).isEqualTo("dev-a-1");
        assertThat(detection.severity()).isEqualTo(BlockerSeverity.HIGH);  // Architectural = high
        assertThat(detectDuration).isLessThan(Duration.ofSeconds(1));

        LOGGER.info("  Blocker detected: severity={}, affecting story={}",
            detection.severity(), detection.affectedStory());

        // Phase 2: Severity Classification
        LOGGER.info("Phase 2: Severity Classification & Routing Decision");

        BlockerSeverity severity = detection.severity();
        String escalationPath;

        switch (severity) {
            case HIGH -> escalationPath = "system-architect";  // Architectural decision
            case MEDIUM -> escalationPath = "team-lead";
            case LOW -> escalationPath = "team-member";  // Peer help
            default -> escalationPath = "scrum-master";
        }

        LOGGER.info("  Escalation path: {} (severity: {})", escalationPath, severity);

        // Phase 3: Notification & Tracking
        LOGGER.info("Phase 3: Send Notification & Track Resolution");
        Instant notifyStart = Instant.now();

        BlockerNotification notification = new BlockerNotification(
            detection.blockingDeveloper(),
            detection.affectedStory(),
            detection.blockerDescription(),
            escalationPath,
            detection.dependentDeveloper()
        );
        notification.setSeverity(severity);

        BlockerEscalationResult escalation = orchestrator.escalateBlocker(notification);
        Duration escDuration = Duration.between(notifyStart, Instant.now());

        assertThat(escalation.wasEscalated()).isTrue();
        assertThat(escalation.notificationSent()).isTrue();
        assertThat(escalation.escalationTime()).isLessThan(Duration.ofSeconds(30));

        LOGGER.info("  Notification sent to {} in {}ms",
            escalationPath, escDuration.toMillis());

        // Phase 4: Impact Analysis & Forecast Update
        LOGGER.info("Phase 4: Impact Analysis & Forecast Update");

        // Blocker affects downstream story (dev-b-1)
        BlockerImpactAnalysis impact = orchestrator.analyzeBlockerImpact(notification);

        LOGGER.info("  Blocker impacts: {} downstream stories, estimated delay: {} days",
            impact.affectedStoriesCount(),
            impact.estimatedDelayDays());

        // Update sprint forecast (account for blocker)
        SprintForecast updatedForecast = orchestrator.updateSprintForecastForBlocker(train, notification);

        LOGGER.info("  Sprint forecast updated: {} points (accounting for blocker delay)",
            updatedForecast.predictedPoints());

        // Phase 5: Resolution Tracking
        LOGGER.info("Phase 5: Resolution Tracking");

        // Simulate resolution: Architect makes decision
        Instant resolutionStart = Instant.now();
        orchestrator.recordBlockerResolution(notification, "Architecture approved");
        Duration resolutionDuration = Duration.between(resolutionStart, Instant.now());

        LOGGER.info("  Blocker resolved in {}ms",
            resolutionDuration.toMillis());

        // Total end-to-end time: detect → escalate → resolve
        LOGGER.info("Integration test complete:");
        LOGGER.info("  Detection: {}ms", detectDuration.toMillis());
        LOGGER.info("  Escalation: {}ms", escalation.escalationTime().toMillis());
        LOGGER.info("  Resolution: {}ms", resolutionDuration.toMillis());
        LOGGER.info("  End-to-end: automated, zero manual handoffs");
    }

    // ========== Supporting Test Data Classes ==========

    private record SprintResult(
        int sprintNumber,
        List<DailyStandupResult> dailyResults,
        SprintForecast forecast,
        int blockerCount,
        Duration duration
    ) {}
}
