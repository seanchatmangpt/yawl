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

package org.yawlfoundation.yawl.blue_ocean.scenarios;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Blue Ocean Scenario-Based Tests for Real-World SAFe Workflows
 *
 * This test suite validates blue ocean innovations against realistic SAFe scenarios:
 * 1. Standard 5-Team Train (100 people)
 * 2. Distributed Train (Geographic spread, async ceremonies)
 * 3. Complex Dependency Network (100+ stories, cross-team deps)
 * 4. High-Velocity Team (120%+ velocity)
 * 5. Low-Velocity Team (60% velocity, risk detection)
 * 6. Multi-Sprint Orchestration (Full PI cycle)
 *
 * Test Framework: Chicago TDD (Detroit School)
 * - Real YAWL engine instances
 * - H2 in-memory database for test data
 * - No mocks/stubs; test actual workflow behavior
 * - Measurable outcomes: time saved, accuracy, latency
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("Blue Ocean: Real-World SAFe Scenario Tests")
public class BlueOceanScenarioTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlueOceanScenarioTest.class);

    private YEngine engine;
    private SAFEScenarioBuilder scenarioBuilder;

    @BeforeEach
    void setUp() {
        engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine should be available");
        scenarioBuilder = new SAFEScenarioBuilder(engine);
    }

    /**
     * Scenario 1: Standard 5-Team Train (100 people)
     *
     * Setup:
     * - 5 Scrum teams (20 people each)
     * - 1 Release Train Engineer (RTE)
     * - 1 System Architect
     * - 1 Product Manager
     * - 100 user stories distributed across teams
     *
     * PI Planning (Traditional: 4 hours → With Async: 2 hours):
     * 1. RTE presents roadmap (5 min)
     * 2. Architect presents technical constraints (10 min)
     * 3. Teams estimate stories asynchronously (90 min → 30 min with predictive engine)
     * 4. Teams commit to objectives (30 min)
     *
     * Expected Outcomes:
     * - All 100 people synchronized on PI objectives
     * - 0 stories left unestimated
     * - PI objectives clearly defined
     * - Zero scheduling conflicts
     * - 50% time savings (4 hours → 2 hours)
     */
    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    @DisplayName("Scenario 1: Standard 5-Team Train PI Planning (100 people)")
    void testFiveTeamTrainPIPlanning() {
        LOGGER.info("=== Scenario 1: Standard 5-Team Train (100 people) ===");

        // Arrange: Build 5-team train with 100 people
        SAFETrain train = scenarioBuilder.buildStandardFiveTeamTrain();
        assertThat(train.totalParticipants()).isEqualTo(100);
        assertThat(train.teamCount()).isEqualTo(5);

        List<UserStory> piBacklog = scenarioBuilder.generatePIBacklog(100);
        assertThat(piBacklog).hasSize(100);

        // Act: Execute async PI planning
        Instant start = Instant.now();
        SAFEPIPlanningResult result = scenarioBuilder.executeAsyncPIPlanning(train, piBacklog);
        Duration elapsed = Duration.between(start, Instant.now());

        // Assert: All success metrics met
        assertThat(result.storiesEstimated()).isEqualTo(100);
        assertThat(result.piObjectivesClarified()).isTrue();
        assertThat(result.allTeamsCommitted()).isTrue();
        assertThat(result.schedulingConflicts()).isEmpty();

        // Performance: Time savings validation
        assertThat(elapsed).isLessThan(Duration.ofMinutes(5));  // Async is much faster
        double timeSavings = ((4.0 - (elapsed.toMinutes() / 60.0)) / 4.0) * 100;
        LOGGER.info("Time savings: {:.1f}% (4 hours traditional → {} minutes async)",
            timeSavings, elapsed.toMinutes());

        // Decision accuracy
        assertThat(result.decisionAccuracy()).isGreaterThan(0.90);
        assertThat(result.teamConsensusScore()).isGreaterThan(0.95);

        // Coverage validation
        long unestimatedStories = piBacklog.stream()
            .filter(s -> s.storyPoints() == 0)
            .count();
        assertThat(unestimatedStories).isZero();

        LOGGER.info("Scenario 1 PASSED: {} stories estimated, {} time savings, " +
            "{:.0f}% decision accuracy",
            result.storiesEstimated(), timeSavings, result.decisionAccuracy() * 100);
    }

    /**
     * Scenario 2: Distributed Train (Geographic Spread, 3 Time Zones)
     *
     * Setup:
     * - US West (8am-9am Pacific)
     * - US East (11am-12pm Eastern)
     * - EU (9am-10am CET next day)
     * - APAC (2pm-3pm IST next day)
     *
     * Async Daily Standup over 24 hours:
     * - Each region submits standup reports asynchronously
     * - Blocker detection happens autonomously
     * - Escalations routed by time zone proximity
     * - All decisions logged with timestamps
     *
     * Expected Outcomes:
     * - No overlapping meeting times required
     * - Blockers detected within 30 minutes
     * - Escalations routed correctly
     * - 100% meeting time elimination (no synchronous ceremonies)
     * - Team satisfaction: 95%+
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Scenario 2: Distributed Train - Async Standup (3 Time Zones)")
    void testDistributedTrainAsyncStandup() {
        LOGGER.info("=== Scenario 2: Distributed Train (3 time zones) ===");

        // Arrange: Build distributed train
        SAFETrain usWest = scenarioBuilder.buildTeamInTimeZone("US-West", "PST", 25);
        SAFETrain usEast = scenarioBuilder.buildTeamInTimeZone("US-East", "EST", 25);
        SAFETrain eu = scenarioBuilder.buildTeamInTimeZone("EU", "CET", 25);
        SAFETrain apac = scenarioBuilder.buildTeamInTimeZone("APAC", "IST", 25);

        List<SAFETrain> distributedTeams = List.of(usWest, usEast, eu, apac);

        // Act: Execute async standup with blocker detection
        Instant start = Instant.now();
        AsyncStandupResult result = scenarioBuilder.executeAsyncStandup(distributedTeams);
        Duration elapsed = Duration.between(start, Instant.now());

        // Assert: No synchronous meeting required
        assertThat(result.synchronousMeetingsRequired()).isZero();
        assertThat(result.asyncReportsCollected()).isEqualTo(100);  // All 100 people

        // Blocker detection validation
        List<BlockerNotification> blockers = result.detectedBlockers();
        assertThat(blockers).isNotEmpty();  // Some blockers should be detected

        // Escalation validation
        for (BlockerNotification blocker : blockers) {
            assertThat(blocker.escalatedTo()).isNotNull();
            assertThat(blocker.escalationTime()).isLessThan(Duration.ofMinutes(30));
        }

        // Time zone awareness
        assertThat(result.allReportsTimestamped()).isTrue();
        assertThat(result.timeZoneConflicts()).isZero();

        // Performance
        assertThat(elapsed).isLessThan(Duration.ofSeconds(10));

        // Team satisfaction
        assertThat(result.teamSatisfactionScore()).isGreaterThan(0.95);

        LOGGER.info("Scenario 2 PASSED: {} blockers detected, {} escalations, " +
            "{:.0f}% satisfaction",
            blockers.size(), result.escalationsExecuted(),
            result.teamSatisfactionScore() * 100);
    }

    /**
     * Scenario 3: Complex Dependency Network
     *
     * Setup:
     * - 100+ stories with cross-team dependencies
     * - 50 inter-team blockers identified by predictive engine
     * - 3-team dependency chains (A → B → C)
     *
     * PI Planning with Dependency Orchestration:
     * - Predictive engine identifies all dependencies
     * - Architect validates dependency paths
     * - Teams commit in dependency order
     *
     * Expected Outcomes:
     * - All dependencies detected (100% recall)
     * - Zero circular dependencies
     * - Dependency resolution order validated
     * - False positives: <5%
     * - Planning time: 2 hours (vs. 4 hours traditional)
     * - Decision confidence: >85%
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Scenario 3: Complex Dependency Network (100+ stories, cross-team)")
    void testComplexDependencyNetworkResolution() {
        LOGGER.info("=== Scenario 3: Complex Dependency Network ===");

        // Arrange: Build dependency network
        SAFETrain train = scenarioBuilder.buildStandardFiveTeamTrain();
        List<UserStory> stories = scenarioBuilder.generatePIBacklogWithDependencies(100, 50);

        // Validate test setup
        long dependencyCount = stories.stream()
            .mapToLong(s -> s.dependsOn().size())
            .sum();
        assertThat(dependencyCount).isGreaterThan(50);

        // Act: Detect and validate dependencies
        Instant start = Instant.now();
        DependencyAnalysisResult analysis = scenarioBuilder.analyzeDependencyNetwork(stories);
        Duration elapsed = Duration.between(start, Instant.now());

        // Assert: All dependencies detected
        assertThat(analysis.dependenciesDetected()).isGreaterThan(50);
        assertThat(analysis.dependencyRecall()).isGreaterThan(0.95);  // 95%+ recall

        // No circular dependencies
        assertThat(analysis.circularDependencies()).isEmpty();

        // False positive rate validation
        int falsePositives = analysis.falsePositiveCount();
        int totalDetected = analysis.dependenciesDetected();
        double falsePositiveRate = (double) falsePositives / totalDetected;
        assertThat(falsePositiveRate).isLessThan(0.05);  // <5% false positives

        // Dependency resolution order
        List<UserStory> resolvedOrder = analysis.resolutionOrder();
        assertThat(resolvedOrder).hasSize(stories.size());
        validateDependencyOrder(resolvedOrder);

        // Decision confidence
        assertThat(analysis.decisionConfidence()).isGreaterThan(0.85);

        // Performance
        assertThat(elapsed).isLessThan(Duration.ofSeconds(30));

        LOGGER.info("Scenario 3 PASSED: {} dependencies detected, " +
            "{:.0f}% false positive rate, {:.0f}% confidence, " +
            "resolved in {} seconds",
            analysis.dependenciesDetected(), falsePositiveRate * 100,
            analysis.decisionConfidence() * 100, elapsed.toSeconds());
    }

    /**
     * Scenario 4: High-Velocity Team (120%+ Completion)
     *
     * Setup:
     * - Team completing more work than estimated
     * - Last PI: 110 points committed, 130 points completed (118%)
     * - Team velocity trending upward
     * - 8 sprints of historical data
     *
     * PI Planning with Predictive Velocity:
     * - Predictive engine factors historical velocity
     * - Recommends 140 point commitment
     * - Risk assessment: "Team has capacity buffer"
     *
     * Expected Outcomes:
     * - Velocity trend detected
     * - Recommendation confidence: >80%
     * - Teams can make informed decisions
     * - Utilization optimization: +15%
     */
    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    @DisplayName("Scenario 4: High-Velocity Team (120%+ completion)")
    void testHighVelocityTeamPrediction() {
        LOGGER.info("=== Scenario 4: High-Velocity Team ===");

        // Arrange: Build high-velocity team
        SAFETrain team = scenarioBuilder.buildTeamWithVelocityTrend(
            List.of(85, 90, 92, 95, 100, 105, 110, 115)  // Increasing trend
        );

        // Last PI: 110 committed, 130 completed
        team.recordPIPerfomance(110, 130);

        // Act: Predictive engine analyzes velocity
        VelocityPredictionResult prediction = scenarioBuilder.predictTeamVelocity(team);

        // Assert: Upward trend detected
        assertThat(prediction.trendDirection()).isEqualTo(VelocityTrend.INCREASING);
        assertThat(prediction.trendStrength()).isGreaterThan(0.80);

        // Velocity forecast is higher than requested commitment
        int lastVelocity = 115;
        int recommendedCommitment = prediction.recommendedCommitment();
        assertThat(recommendedCommitment).isGreaterThan(lastVelocity);
        assertThat(recommendedCommitment).isLessThanOrEqualTo(140);

        // Recommendation confidence
        assertThat(prediction.confidence()).isGreaterThan(0.80);

        // Risk assessment validates capacity
        assertThat(prediction.hasCapacityBuffer()).isTrue();
        assertThat(prediction.recommendedCapacityBuffer()).isGreaterThan(0.10);  // 10% buffer

        // Utilization improvement
        double utilizationGain = prediction.estimatedUtilizationGain();
        assertThat(utilizationGain).isGreaterThan(0.10);  // 10%+ improvement

        LOGGER.info("Scenario 4 PASSED: Velocity {} → recommended {}, " +
            "{:.0f}% confidence, +{:.0f}% utilization gain",
            lastVelocity, recommendedCommitment,
            prediction.confidence() * 100, utilizationGain * 100);
    }

    /**
     * Scenario 5: Low-Velocity Team (60% Completion)
     *
     * Setup:
     * - Team completing only 60% of committed work
     * - Historical trend: declining velocity
     * - Last PI: 100 committed, 60 completed
     * - 8 sprints of data showing decline
     *
     * PI Planning with Predictive Risk:
     * - Predictive engine identifies low-velocity risk
     * - Recommends 40 point commitment (vs. 100 request)
     * - Proposes 3+ improvement actions
     *
     * Expected Outcomes:
     * - Risk flagged before commitment
     * - Root causes identified
     * - Improvement recommendations provided
     * - Risk detection: 90%+ sensitivity
     * - Improvement recommendations: >3 per team
     * - Team buy-in: 80%+
     */
    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    @DisplayName("Scenario 5: Low-Velocity Team (60% completion, risk detection)")
    void testLowVelocityTeamRiskDetection() {
        LOGGER.info("=== Scenario 5: Low-Velocity Team ===");

        // Arrange: Build low-velocity team with declining trend
        SAFETrain team = scenarioBuilder.buildTeamWithVelocityTrend(
            List.of(100, 95, 88, 82, 75, 68, 62, 60)  // Declining trend
        );

        // Last PI: 100 committed, 60 completed (60% completion)
        team.recordPIPerfomance(100, 60);

        // Act: Risk detection engine analyzes team
        RiskAssessmentResult riskAssessment = scenarioBuilder.assessTeamRisk(team);

        // Assert: Low-velocity risk detected
        assertThat(riskAssessment.hasRisk(RiskType.LOW_VELOCITY)).isTrue();
        assertThat(riskAssessment.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(riskAssessment.riskScore()).isGreaterThan(0.80);  // 80%+ confidence

        // Recommendation is conservative
        int recommendedCommitment = riskAssessment.recommendedCommitment();
        assertThat(recommendedCommitment).isLessThan(70);  // Below recent performance

        // Root causes identified
        List<String> rootCauses = riskAssessment.identifiedRootCauses();
        assertThat(rootCauses).isNotEmpty();
        assertThat(rootCauses.stream().anyMatch(c -> c.contains("velocity") || c.contains("capacity")))
            .isTrue();

        // Improvement actions proposed
        List<ImprovementAction> improvements = riskAssessment.proposedImprovements();
        assertThat(improvements).hasSizeGreaterThanOrEqualTo(3);

        // Verify improvement relevance (address identified causes)
        for (ImprovementAction improvement : improvements) {
            assertThat(improvement.description()).isNotBlank();
            assertThat(improvement.estimatedImpact()).isGreaterThan(0);
        }

        // Team buy-in (predictive engine credibility)
        assertThat(riskAssessment.teamBuyInScore()).isGreaterThan(0.80);

        LOGGER.info("Scenario 5 PASSED: Risk detected at {:.0f}% confidence, " +
            "recommended commitment: {}, {} improvement actions, " +
            "{:.0f}% team buy-in",
            riskAssessment.riskScore() * 100, recommendedCommitment,
            improvements.size(), riskAssessment.teamBuyInScore() * 100);
    }

    /**
     * Scenario 6: Multi-Sprint Orchestration (Full PI Cycle)
     *
     * Setup:
     * - 3-sprint PI (3 weeks × 3 sprints)
     * - 5 teams, 100 people total
     * - Complex dependencies and blockers
     *
     * Full PI Execution:
     * 1. Async PI Planning (2 hours with predictive engine)
     * 2. Sprint 1: Daily async standups + blocker escalation
     * 3. Sprint 2: Continue with predictive velocity tracking
     * 4. Sprint 3: Final sprint with forecast accuracy verification
     * 5. Retrospective with improvement recommendations
     *
     * Expected Outcomes:
     * - PI objectives achieved: >90%
     * - Blocker escalation latency: <30 min
     * - Prediction accuracy: ±10%
     * - Team satisfaction: 90%+
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @DisplayName("Scenario 6: Full PI Cycle (3 sprints, end-to-end)")
    void testFullPICycleOrchestration() {
        LOGGER.info("=== Scenario 6: Full PI Cycle (3-Sprint Orchestration) ===");

        // Arrange: Build train for full PI cycle
        SAFETrain train = scenarioBuilder.buildStandardFiveTeamTrain();
        List<UserStory> piBacklog = scenarioBuilder.generatePIBacklogWithDependencies(100, 30);

        // Phase 1: Async PI Planning
        LOGGER.info("Phase 1: Async PI Planning...");
        SAFEPIPlanningResult piResult = scenarioBuilder.executeAsyncPIPlanning(train, piBacklog);
        assertThat(piResult.allTeamsCommitted()).isTrue();

        AtomicInteger totalBlockersEscalated = new AtomicInteger(0);
        List<Integer> sprintVelocities = new ArrayList<>();

        // Phase 2-4: Three sprints with daily standups
        for (int sprintNum = 1; sprintNum <= 3; sprintNum++) {
            LOGGER.info("Phase {}: Sprint {} (10 days)...", sprintNum + 1, sprintNum);

            List<UserStory> sprintBacklog = scenarioBuilder.getSprintBacklog(train, sprintNum);
            int sprintCapacity = train.getTeamCapacity(sprintNum);

            // Daily async standups (10 days per sprint)
            for (int day = 1; day <= 10; day++) {
                // Simulate daily work progress
                scenarioBuilder.simulateDailyWork(train, sprintBacklog, day);

                // Async standup + blocker detection
                AsyncStandupResult standupResult =
                    scenarioBuilder.executeAsyncStandup(List.of(train));

                // Auto-escalate detected blockers
                for (BlockerNotification blocker : standupResult.detectedBlockers()) {
                    scenarioBuilder.escalateBlocker(train, blocker);
                    totalBlockersEscalated.incrementAndGet();
                }

                // Predictive forecast for sprint
                if (day == 10) {  // Final day of sprint
                    PredictionResult sprintForecast =
                        scenarioBuilder.predictSprintCompletion(train, sprintNum);

                    sprintVelocities.add(sprintForecast.predictedPoints());
                }
            }
        }

        // Phase 5: Retrospective with improvement recommendations
        LOGGER.info("Phase 5: Retrospective...");
        RetrospectiveResult retroResult = scenarioBuilder.executeRetro(train, piResult);

        // Assert: Full cycle validation
        // PI Objectives achievement
        assertThat(retroResult.piObjectivesAchieved()).isGreaterThan(0.90);

        // Blocker escalation
        assertThat(totalBlockersEscalated.get()).isGreaterThan(0);
        LOGGER.info("Total blockers escalated: {}", totalBlockersEscalated.get());

        // Velocity tracking across sprints
        assertThat(sprintVelocities).hasSize(3);
        for (int velocity : sprintVelocities) {
            assertThat(velocity).isGreaterThan(0);
        }

        // Prediction accuracy across sprints
        double avgForecastAccuracy = retroResult.averagePredictionAccuracy();
        assertThat(avgForecastAccuracy).isGreaterThan(0.85);  // 85%+ across 3 sprints

        // Improvement recommendations from predictive engine
        int improvementCount = retroResult.improvementActionsFromPredictions();
        assertThat(improvementCount).isGreaterThanOrEqualTo(2);

        // Team satisfaction
        assertThat(retroResult.teamSatisfactionScore()).isGreaterThan(0.90);

        LOGGER.info("Scenario 6 PASSED: PI objectives {:.0f}% achieved, " +
            "{} blockers escalated, {:.0f}% forecast accuracy, " +
            "{} improvements recommended, {:.0f}% satisfaction",
            retroResult.piObjectivesAchieved() * 100,
            totalBlockersEscalated.get(),
            avgForecastAccuracy * 100,
            improvementCount,
            retroResult.teamSatisfactionScore() * 100);
    }

    // ========== Helper Validation Methods ==========

    private void validateDependencyOrder(List<UserStory> resolvedOrder) {
        for (int i = 0; i < resolvedOrder.size(); i++) {
            UserStory story = resolvedOrder.get(i);

            // All dependencies of this story should appear before it
            for (String depId : story.dependsOn()) {
                UserStory dep = resolvedOrder.stream()
                    .filter(s -> s.id().equals(depId))
                    .findFirst()
                    .orElse(null);

                assertNotNull(dep, "Dependency " + depId + " should exist");

                int depIndex = resolvedOrder.indexOf(dep);
                assertTrue(depIndex < i,
                    "Dependency " + depId + " should be resolved before " + story.id());
            }
        }
    }
}
