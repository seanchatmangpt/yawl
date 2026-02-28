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
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD (Detroit School) Integration Tests for PredictiveSprintAgent.
 *
 * Tests real sprint velocity analysis, capacity recommendation algorithms,
 * risk factor detection, and real-time adjustment logic. No mocks or stubs —
 * all integration tests with actual data analysis.
 *
 * Test coverage:
 * - Velocity trend analysis (12-sprint lookback)
 * - Capacity recommendations at different confidence levels
 * - Risk factor detection (declining velocity, high volatility, etc.)
 * - Vacation/team change adjustments
 * - Real-time sprint burn-down tracking
 * - Capacity report generation
 *
 * @since YAWL 6.0
 */
@DisplayName("Predictive Sprint Planning Test Suite")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
public class PredictiveSprintTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PredictiveSprintTest.class);

    private PredictiveSprintAgent agent;
    private List<SAFeSprint> historicalSprints;

    @BeforeEach
    void setUp() {
        LOGGER.info("Setting up PredictiveSprintTest");
        historicalSprints = createHistoricalSprints();
        agent = new PredictiveSprintAgent(historicalSprints);
    }

    /**
     * Create 12 mock historical sprints with realistic velocity data.
     * Sprints show slight improving trend: 35 -> 40 points over 12 sprints.
     */
    private List<SAFeSprint> createHistoricalSprints() {
        return IntStream.range(0, 12)
            .mapToObj(i -> {
                // Velocity trend: start at 35, gradually improve to 40
                int baseVelocity = 35 + (int) (i * 0.5);
                // Add realistic variation (±3 points)
                int velocity = baseVelocity + (i % 3) - 1;

                return new SAFeSprint(
                    String.format("S-%d", i + 1),
                    LocalDate.now().minusWeeks(2L * (12 - i)),
                    LocalDate.now().minusWeeks(2L * (11 - i)),
                    velocity + 5,  // committed
                    velocity,      // completed
                    8              // team size
                );
            })
            .toList();
    }

    @Test
    @DisplayName("Should calculate velocity statistics from historical data")
    void testVelocityStatistics() {
        PredictiveSprintAgent.VelocityStats stats = agent.calculateVelocityStats();

        assertAll(
            () -> assertTrue(stats.min() > 0, "Minimum velocity should be positive"),
            () -> assertTrue(stats.max() >= stats.min(), "Maximum should be >= minimum"),
            () -> assertTrue(stats.average() >= stats.min() && stats.average() <= stats.max(),
                "Average should be within bounds"),
            () -> assertTrue(stats.standardDeviation() >= 0, "Standard deviation should be non-negative"),
            () -> assertEquals(12, stats.dataPoints(), "Should have 12 data points"),
            () -> assertTrue(stats.trend() > 0, "Should show improving trend")
        );

        LOGGER.info("Velocity stats: avg={}, min={}, max={}, stddev={}, trend={}",
            stats.average(), stats.min(), stats.max(), stats.standardDeviation(), stats.trend());
    }

    @Test
    @DisplayName("Should recommend conservative capacity below average velocity")
    void testConservativeCapacityRecommendation() {
        PredictiveSprintAgent.SprintRecommendation rec = agent.recommendCapacity(
            PredictiveSprintAgent.ConfidenceLevel.CONSERVATIVE, 0);

        PredictiveSprintAgent.VelocityStats stats = agent.calculateVelocityStats();
        int expectedCapacity = (int) Math.round(stats.average * 0.85);

        assertEquals(expectedCapacity, rec.recommendedCapacity(),
            "Conservative should be 85% of average");
        assertTrue(rec.recommendedCapacity() <= stats.average(),
            "Conservative capacity should be below average");
    }

    @Test
    @DisplayName("Should recommend moderate capacity at average velocity")
    void testModerateCapacityRecommendation() {
        PredictiveSprintAgent.SprintRecommendation rec = agent.recommendCapacity(
            PredictiveSprintAgent.ConfidenceLevel.MODERATE, 0);

        PredictiveSprintAgent.VelocityStats stats = agent.calculateVelocityStats();
        int expectedCapacity = (int) Math.round(stats.average * 1.0);

        assertEquals(expectedCapacity, rec.recommendedCapacity(),
            "Moderate should be 100% of average");
    }

    @Test
    @DisplayName("Should recommend aggressive capacity above average velocity")
    void testAggressiveCapacityRecommendation() {
        PredictiveSprintAgent.SprintRecommendation rec = agent.recommendCapacity(
            PredictiveSprintAgent.ConfidenceLevel.AGGRESSIVE, 0);

        PredictiveSprintAgent.VelocityStats stats = agent.calculateVelocityStats();
        int expectedCapacity = (int) Math.round(stats.average * 1.15);

        assertEquals(expectedCapacity, rec.recommendedCapacity(),
            "Aggressive should be 115% of average");
        assertTrue(rec.recommendedCapacity() >= stats.average(),
            "Aggressive capacity should be above average");
    }

    @ParameterizedTest
    @CsvSource({
        "0, 1.0",    // 0 vacation days = no adjustment
        "1, 0.9",    // 1 vacation day = 10% reduction
        "2, 0.8",    // 2 vacation days = 20% reduction
        "5, 0.5",    // 5 vacation days = 50% reduction
        "10, 0.5",   // 10 vacation days = 50% (floor)
    })
    @DisplayName("Should adjust capacity for vacation days")
    void testVacationAdjustment(int vacationDays, double expectedFactor) {
        PredictiveSprintAgent.SprintRecommendation rec = agent.recommendCapacity(
            PredictiveSprintAgent.ConfidenceLevel.MODERATE, vacationDays);

        PredictiveSprintAgent.VelocityStats stats = agent.calculateVelocityStats();
        double expectedCapacity = stats.average * 1.0 * expectedFactor;
        int tolerance = 2; // Allow ±2 points rounding

        assertTrue(Math.abs(rec.recommendedCapacity() - expectedCapacity) <= tolerance,
            String.format("Capacity with %d vacation days should be adjusted by %.2f%%",
                vacationDays, expectedFactor * 100));

        LOGGER.info("Vacation adjustment: {} days -> {:.0f}% of capacity",
            vacationDays, expectedFactor * 100);
    }

    @Test
    @DisplayName("Should detect declining velocity as risk factor")
    void testDecliningVelocityRiskDetection() {
        // Create sprints with obvious decline
        List<SAFeSprint> decliningData = List.of(
            new SAFeSprint("S-1", LocalDate.now().minusWeeks(24), LocalDate.now().minusWeeks(22), 50, 50, 8),
            new SAFeSprint("S-2", LocalDate.now().minusWeeks(22), LocalDate.now().minusWeeks(20), 48, 48, 8),
            new SAFeSprint("S-3", LocalDate.now().minusWeeks(20), LocalDate.now().minusWeeks(18), 45, 45, 8),
            new SAFeSprint("S-4", LocalDate.now().minusWeeks(18), LocalDate.now().minusWeeks(16), 40, 40, 8),
            new SAFeSprint("S-5", LocalDate.now().minusWeeks(16), LocalDate.now().minusWeeks(14), 35, 35, 8),
            new SAFeSprint("S-6", LocalDate.now().minusWeeks(14), LocalDate.now().minusWeeks(12), 30, 30, 8)
        );

        PredictiveSprintAgent decliningAgent = new PredictiveSprintAgent(decliningData);
        PredictiveSprintAgent.SprintRecommendation rec = decliningAgent.recommendCapacity(
            PredictiveSprintAgent.ConfidenceLevel.MODERATE, 0);

        assertTrue(rec.riskFactors().stream()
                .anyMatch(r -> r.name().equals("Declining Velocity")),
            "Should detect declining velocity as risk");

        LOGGER.info("Declining velocity risk detected: {}", rec.riskFactors());
    }

    @Test
    @DisplayName("Should detect high volatility as risk factor")
    void testHighVolatilityRiskDetection() {
        // Create sprints with high variance
        List<SAFeSprint> volatileData = List.of(
            new SAFeSprint("S-1", LocalDate.now().minusWeeks(24), LocalDate.now().minusWeeks(22), 50, 50, 8),
            new SAFeSprint("S-2", LocalDate.now().minusWeeks(22), LocalDate.now().minusWeeks(20), 20, 20, 8),
            new SAFeSprint("S-3", LocalDate.now().minusWeeks(20), LocalDate.now().minusWeeks(18), 60, 60, 8),
            new SAFeSprint("S-4", LocalDate.now().minusWeeks(18), LocalDate.now().minusWeeks(16), 15, 15, 8),
            new SAFeSprint("S-5", LocalDate.now().minusWeeks(16), LocalDate.now().minusWeeks(14), 55, 55, 8),
            new SAFeSprint("S-6", LocalDate.now().minusWeeks(14), LocalDate.now().minusWeeks(12), 25, 25, 8)
        );

        PredictiveSprintAgent volatileAgent = new PredictiveSprintAgent(volatileData);
        PredictiveSprintAgent.SprintRecommendation rec = volatileAgent.recommendCapacity(
            PredictiveSprintAgent.ConfidenceLevel.MODERATE, 0);

        assertTrue(rec.riskFactors().stream()
                .anyMatch(r -> r.name().contains("Volatility")),
            "Should detect high volatility as risk");

        LOGGER.info("Volatility risk detected: {}", rec.riskFactors());
    }

    @Test
    @DisplayName("Should recommend confidence interval based on standard deviation")
    void testConfidenceInterval() {
        PredictiveSprintAgent.SprintRecommendation rec = agent.recommendCapacity(
            PredictiveSprintAgent.ConfidenceLevel.MODERATE, 0);

        assertTrue(rec.minCapacity() >= 0, "Min capacity should be non-negative");
        assertTrue(rec.maxCapacity() >= rec.minCapacity(),
            "Max capacity should be >= min capacity");
        assertTrue(rec.recommendedCapacity() >= rec.minCapacity() &&
                   rec.recommendedCapacity() <= rec.maxCapacity(),
            "Recommended capacity should be within interval");

        LOGGER.info("Confidence interval: {}-{} (recommended: {})",
            rec.minCapacity(), rec.maxCapacity(), rec.recommendedCapacity());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 5})
    @DisplayName("Should handle various vacation day scenarios")
    void testMultipleVacationScenarios(int vacationDays) {
        PredictiveSprintAgent.SprintRecommendation rec = agent.recommendCapacity(
            PredictiveSprintAgent.ConfidenceLevel.MODERATE, vacationDays);

        assertNotNull(rec.rationale(), "Should provide rationale");
        assertTrue(rec.rationale().contains("adjusted"), "Rationale should mention adjustment");
        assertFalse(rec.rationale().isBlank(), "Rationale should not be empty");
    }

    @Test
    @DisplayName("Should track real-time sprint burn-down and suggest adjustments")
    void testRealTimeSprintAdjustment() {
        SAFeSprint currentSprint = new SAFeSprint(
            "S-CURRENT",
            LocalDate.now(),
            LocalDate.now().plusDays(10),
            40,    // committed
            0,     // completed (progress will be updated)
            8
        );

        // Day 3: completed 12 points (on track for 40)
        String recommendation1 = agent.autoAdjustCommitment(currentSprint, 3, 12);
        assertEquals("MAINTAIN_SCOPE", recommendation1, "On-track velocity should maintain scope");

        // Day 3: completed 20 points (ahead, 66% projected)
        String recommendation2 = agent.autoAdjustCommitment(currentSprint, 3, 20);
        assertEquals("EXPAND_SCOPE", recommendation2, "Ahead-of-schedule should expand scope");

        // Day 3: completed 8 points (behind, 26% projected)
        String recommendation3 = agent.autoAdjustCommitment(currentSprint, 3, 8);
        assertEquals("REDUCE_SCOPE", recommendation3, "Behind-schedule should reduce scope");

        LOGGER.info("Sprint adjustments: on-track={}, ahead={}, behind={}",
            recommendation1, recommendation2, recommendation3);
    }

    @Test
    @DisplayName("Should estimate team size impact on velocity")
    void testTeamSizeImpact() {
        // Losing 1 person from 8-person team
        double shrinkImpact = agent.estimateTeamSizeImpact(8, 7);
        assertEquals(7.0 / 8.0, shrinkImpact, 0.01, "Shrinking team should scale linearly");

        // Growing 1 person on 8-person team (70% onboarding efficiency)
        double growthImpact = agent.estimateTeamSizeImpact(8, 9);
        double expected = 1.0 + (0.7 / 8.0);
        assertEquals(expected, growthImpact, 0.01, "Growing team should account for onboarding");

        // No change
        double noChange = agent.estimateTeamSizeImpact(8, 8);
        assertEquals(1.0, noChange, 0.01, "Same team size should have no impact");
    }

    @Test
    @DisplayName("Should generate comprehensive capacity report")
    void testCapacityReportGeneration() {
        SAFeSprint upcomingSprint = new SAFeSprint(
            "S-NEXT",
            LocalDate.now(),
            LocalDate.now().plusDays(14),
            0, 0, 8
        );

        String report = agent.generateCapacityReport(upcomingSprint, 2, 8);

        assertAll(
            () -> assertTrue(report.contains("SPRINT CAPACITY ANALYSIS"), "Should have title"),
            () -> assertTrue(report.contains("Conservative"), "Should show conservative option"),
            () -> assertTrue(report.contains("Moderate"), "Should show moderate option"),
            () -> assertTrue(report.contains("Aggressive"), "Should show aggressive option"),
            () -> assertTrue(report.contains("CONFIDENCE INTERVAL"), "Should show confidence interval"),
            () -> assertTrue(report.contains("RECOMMENDATIONS"), "Should have recommendations")
        );

        LOGGER.info("Generated capacity report:\n{}", report);
    }

    @Test
    @DisplayName("Should estimate sprints needed to complete backlog")
    void testBacklogSprintEstimation() {
        int backlogPoints = 150;
        int estimatedSprints = agent.estimateSprintsNeeded(backlogPoints);

        PredictiveSprintAgent.VelocityStats stats = agent.calculateVelocityStats();
        int expectedSprints = (int) Math.ceil(backlogPoints / stats.average());

        assertEquals(expectedSprints, estimatedSprints,
            "Should estimate sprints based on average velocity");

        LOGGER.info("Backlog {} points needs {} sprints (avg velocity: {:.0f})",
            backlogPoints, estimatedSprints, stats.average());
    }

    @Test
    @DisplayName("Should handle empty historical data gracefully")
    void testEmptyHistoricalData() {
        PredictiveSprintAgent emptyAgent = new PredictiveSprintAgent(List.of());

        PredictiveSprintAgent.VelocityStats stats = emptyAgent.calculateVelocityStats();
        assertEquals(0, stats.min(), "Empty data should return 0 min");
        assertEquals(0, stats.average(), "Empty data should return 0 average");

        int sprints = emptyAgent.estimateSprintsNeeded(100);
        assertEquals(Integer.MAX_VALUE, sprints, "Empty data should indicate no estimate");
    }

    @Test
    @DisplayName("Should provide actionable rationale in recommendations")
    void testRecommendationRationale() {
        PredictiveSprintAgent.SprintRecommendation rec = agent.recommendCapacity(
            PredictiveSprintAgent.ConfidenceLevel.MODERATE, 1);

        assertNotNull(rec.rationale(), "Should provide rationale");
        assertFalse(rec.rationale().isBlank(), "Rationale should not be empty");
        assertTrue(rec.rationale().contains(String.valueOf(rec.recommendedCapacity())),
            "Rationale should mention recommended capacity");
        assertTrue(rec.rationale().contains("velocity") || rec.rationale().contains("average"),
            "Rationale should explain basis");

        LOGGER.info("Recommendation rationale: {}", rec.rationale());
    }
}
