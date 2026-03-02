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

package org.yawlfoundation.yawl.integration.selfplay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for the fitness measurement system.
 */
class FitnessMetricsTest {

    private FitnessMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new FitnessMetrics("test-session-123");
    }

    @Test
    @DisplayName("Create fitness metrics with valid session ID")
    void testCreationWithValidSessionId() {
        assertEquals("test-session-123", metrics.getSessionId());
        assertNotNull(metrics.getStartTime());
        assertNotNull(metrics.getDuration());
        assertTrue(metrics.getDuration().toNanos() > 0);
        assertTrue(metrics.getCodeQualityMetrics().isEmpty());
        assertTrue(metrics.getPerformanceMetrics().isEmpty());
        assertTrue(metrics.getIntegrationMetrics().isEmpty());
        assertTrue(metrics.getSelfPlayMetrics().isEmpty());
        assertTrue(metrics.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("Record code quality metrics")
    void testCodeQualityMetrics() {
        // Record various code quality metrics
        metrics.recordCodeComplexity(0.75, "Average cyclomatic complexity");
        metrics.recordTestCoverage(0.92, "JUnit coverage");
        metrics.recordMaintainability(0.68, "Code maintainability index");
        metrics.recordCodeReviewQuality(0.74, "Code review quality");

        assertEquals(4, metrics.getCodeQualityMetrics().size());
        assertEquals(0.75, metrics.getCodeQualityMetrics().get("complexity").score());
        assertEquals("Average cyclomatic complexity",
                   metrics.getCodeQualityMetrics().get("complexity").description());
        assertTrue(metrics.getCodeQualityMetrics().get("complexity").isGood());
    }

    @Test
    @DisplayName("Record performance metrics")
    void testPerformanceMetrics() {
        // Record various performance metrics
        metrics.recordLatency(150, "ms", "Average response time");
        metrics.recordThroughput(8500, "req/s", "Requests per second");
        metrics.recordMemoryUsage(2048, "MB", "Memory usage");
        metrics.recordCpuUsage(25, "%", "CPU utilization");

        assertEquals(4, metrics.getPerformanceMetrics().size());
        assertEquals(150, metrics.getPerformanceMetrics().get("latency").value());
        assertEquals("ms", metrics.getPerformanceMetrics().get("latency").unit());
        assertTrue(metrics.getPerformanceMetrics().get("latency").isGood());
    }

    @Test
    @DisplayName("Record integration metrics")
    void testIntegrationMetrics() {
        // Record integration health metrics
        metrics.recordMcpHealth(0.98, "MCP server availability");
        metrics.recordA2aHealth(0.85, "A2A protocol health");
        metrics.recordApiAvailability(0.99, "API availability");

        assertEquals(3, metrics.getIntegrationMetrics().size());
        assertEquals(0.98, metrics.getIntegrationMetrics().get("mcp_health").score());
        assertTrue(metrics.getIntegrationMetrics().get("mcp_health").isExcellent());
        assertEquals("98.0%", metrics.getIntegrationMetrics().get("mcp_health").getPercentage() + "%");
    }

    @Test
    @DisplayName("Record self-play metrics")
    void testSelfPlayMetrics() {
        // Record self-play metrics
        metrics.recordConvergenceSpeed(2.5, "Rounds to convergence");
        metrics.recordProposalQuality(0.78, "Proposal quality");
        metrics.recordConsensusRate(0.92, "Consensus rate");
        metrics.recordKnowledgeGain(0.65, "Knowledge gain");

        assertEquals(4, metrics.getSelfPlayMetrics().size());
        assertEquals(2.5, metrics.getSelfPlayMetrics().get("convergence_speed").score());
        assertTrue(metrics.getSelfPlayMetrics().get("convergence_speed").isGood());
        assertTrue(metrics.getSelfPlayMetrics().get("convergence_speed").isConvergenceMetric());
    }

    @Test
    @DisplayName("Add metadata")
    void testMetadata() {
        metrics.addMetadata("environment", "production");
        metrics.addMetadata("test_duration_ms", 4500);
        metrics.addMetadata("agent_count", 3);

        assertEquals(3, metrics.getMetadata().size());
        assertEquals("production", metrics.getMetadata().get("environment"));
        assertEquals(4500, metrics.getMetadata().get("test_duration_ms"));
        assertEquals(3, metrics.getMetadata().get("agent_count"));
    }

    @Test
    @DisplayName("Calculate aggregated score with all metrics")
    void testCalculateAggregatedScoreWithAllMetrics() {
        // Add metrics for all dimensions
        addTestMetrics();

        FitnessScore score = metrics.calculateAggregatedScore();

        assertNotNull(score);
        assertNotNull(score.total());
        assertTrue(score.total() >= 0.0 && score.total() <= 1.0);
        assertNotNull(score.getPerformanceLevel());
        assertNotNull(score.getSummary());
        assertEquals(4, score.codeQuality().getMetricCount());
        assertEquals(4, score.performance().getMetricCount());
        assertEquals(3, score.integration().getMetricCount());
        assertEquals(4, score.selfPlay().getMetricCount());
    }

    @Test
    @DisplayName("Calculate aggregated score with minimal metrics")
    void testCalculateAggregatedScoreWithMinimalMetrics() {
        // Add only one metric per dimension
        metrics.recordCodeComplexity(0.5, "Basic complexity");
        metrics.recordLatency(100, "ms", "Basic latency");
        metrics.recordMcpHealth(0.5, "Basic MCP health");
        metrics.recordConvergenceSpeed(1.0, "Basic convergence");

        FitnessScore score = metrics.calculateAggregatedScore();

        assertNotNull(score);
        assertNotNull(score.total());
        assertTrue(score.total() >= 0.0 && score.total() <= 1.0);
    }

    @Test
    @DisplayName("Calculate aggregated score with empty metrics")
    void testCalculateAggregatedScoreWithEmptyMetrics() {
        FitnessScore score = metrics.calculateAggregatedScore();

        assertNotNull(score);
        assertEquals(0.0, score.total());
        assertEquals("Poor", score.getPerformanceLevel());
        assertEquals(0, score.countExcellentDimensions());
        assertEquals(4, score.countPoorDimensions());
    }

    @Test
    @DisplayName("Test dirty flag behavior")
    void testDirtyFlagBehavior() {
        // Initial state should be dirty
        assertTrue(metrics.isDirty());

        // Calculate score
        addTestMetrics();
        FitnessScore initialScore = metrics.calculateAggregatedScore();
        double initialTotal = initialScore.total();

        // Mark as clean
        assertFalse(metrics.isDirty());

        // Adding a metric should mark as dirty
        metrics.recordCodeComplexity(0.9, "New complexity metric");
        assertTrue(metrics.isDirty());

        // Score should change when recalculated
        FitnessScore updatedScore = metrics.calculateAggregatedScore();
        assertNotEquals(initialTotal, updatedScore.total());
        assertFalse(metrics.isDirty());
    }

    @Test
    @DisplayName("Test JSON export")
    void testJsonExport() {
        addTestMetrics();
        FitnessScore score = metrics.calculateAggregatedScore();

        String json = score.toJson();
        assertNotNull(json);
        assertTrue(json.contains("\"total\":"));
        assertTrue(json.contains("\"level\":"));
        assertTrue(json.contains("\"code_quality\":"));
        assertTrue(json.contains("\"performance\":"));
        assertTrue(json.contains("\"integration\":"));
        assertTrue(json.contains("\"self_play\":"));
    }

    @Test
    @DisplayName("Test metrics validation")
    void testMetricsValidation() {
        // Test invalid code quality score
        assertThrows(IllegalArgumentException.class, () -> {
            metrics.recordCodeComplexity(1.5, "Invalid score");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            metrics.recordCodeComplexity(-0.1, "Invalid score");
        });

        // Test invalid integration score
        assertThrows(IllegalArgumentException.class, () -> {
            metrics.recordMcpHealth(1.2, "Invalid score");
        });

        // Test invalid performance value
        assertThrows(IllegalArgumentException.class, () -> {
            metrics.recordLatency(-100, "ms", "Invalid value");
        });
    }

    @Test
    @DisplayName("Test score level classifications")
    void testScoreLevelClassifications() {
        // Test with excellent metrics
        metrics.recordCodeComplexity(0.9, "Excellent complexity");
        metrics.recordTestCoverage(0.95, "Excellent coverage");

        FitnessScore excellentScore = metrics.calculateAggregatedScore();
        assertTrue(excellentScore.getPerformanceLevel().equals("Excellent") ||
                 excellentScore.codeQuality().level().equals("Excellent"));

        // Test with poor metrics
        FitnessMetrics poorMetrics = new FitnessMetrics("poor-session");
        poorMetrics.recordCodeComplexity(0.2, "Poor complexity");
        poorMetrics.recordTestCoverage(0.1, "Poor coverage");

        FitnessScore poorScore = poorMetrics.calculateAggregatedScore();
        assertEquals("Poor", poorScore.getPerformanceLevel());
    }

    @Test
    @DisplayName("Test dimension analysis")
    void testDimensionAnalysis() {
        addTestMetrics();

        FitnessScore score = metrics.calculateAggregatedScore();

        // Test dimension analysis methods
        assertNotNull(score.getWeakestDimension());
        assertNotNull(score.getStrongestDimension());
        assertTrue(score.countExcellentDimensions() >= 0 && score.countExcellentDimensions() <= 4);
        assertTrue(score.countPoorDimensions() >= 0 && score.countPoorDimensions() <= 4);

        // Test imbalance detection
        // Create imbalanced metrics
        FitnessMetrics imbalancedMetrics = new FitnessMetrics("imbalanced-session");
        imbalancedMetrics.recordCodeComplexity(0.95, "Excellent complexity");
        imbalancedMetrics.recordTestCoverage(0.1, "Poor coverage");

        FitnessScore imbalancedScore = imbalancedMetrics.calculateAggregatedScore();
        assertTrue(imbalancedScore.hasSignificantImbalance());
    }

    @Test
    @DisplayName("Test fitness score builder")
    void testFitnessScoreBuilder() {
        FitnessDimensionScore codeQuality = new FitnessDimensionScore("code_quality", 0.8, null);
        FitnessDimensionScore performance = new FitnessDimensionScore("performance", 0.7, null);
        FitnessDimensionScore integration = new FitnessDimensionScore("integration", 0.6, null);
        FitnessDimensionScore selfPlay = new FitnessDimensionScore("self_play", 0.9, null);

        FitnessScore score = FitnessScore.builder()
            .total(0.75)
            .codeQuality(codeQuality)
            .performance(performance)
            .integration(integration)
            .selfPlay(selfPlay)
            .build();

        assertEquals(0.75, score.total());
        assertEquals(0.8, score.codeQuality().score());
        assertEquals(0.9, score.selfPlay().score());
    }

    @Test
    @DisplayName("Test dimension score ordering")
    void testDimensionScoreOrdering() {
        FitnessDimensionScore poor = new FitnessDimensionScore("poor", 0.2, null);
        FitnessDimensionScore good = new FitnessDimensionScore("good", 0.6, null);
        FitnessDimensionScore excellent = new FitnessDimensionScore("excellent", 0.9, null);

        // Test compareTo method
        assertTrue(poor.compareTo(good) < 0);
        assertTrue(good.compareTo(excellent) < 0);
        assertTrue(excellent.compareTo(poor) > 0);
        assertEquals(0, excellent.compareTo(excellent));
    }

    // Helper method to add test metrics
    private void addTestMetrics() {
        // Code quality
        metrics.recordCodeComplexity(0.75, "Average cyclomatic complexity");
        metrics.recordTestCoverage(0.92, "JUnit coverage percentage");
        metrics.recordMaintainability(0.68, "Code maintainability index");
        metrics.recordCodeReviewQuality(0.74, "Code review quality");

        // Performance
        metrics.recordLatency(150, "ms", "Average response time");
        metrics.recordThroughput(8500, "req/s", "Requests per second");
        metrics.recordMemoryUsage(2048, "MB", "Peak memory usage");
        metrics.recordCpuUsage(25, "%", "Average CPU utilization");

        // Integration
        metrics.recordMcpHealth(0.98, "MCP server availability");
        metrics.recordA2aHealth(0.85, "A2A protocol health");
        metrics.recordApiAvailability(0.99, "API availability");

        // Self-play
        metrics.recordConvergenceSpeed(2.5, "Average rounds to convergence");
        metrics.recordProposalQuality(0.78, "Proposal quality");
        metrics.recordConsensusRate(0.92, "Consensus achievement rate");
        metrics.recordKnowledgeGain(0.65, "Knowledge acquisition");

        // Metadata
        metrics.addMetadata("environment", "test");
        metrics.addMetadata("test_duration_ms", 5000);
    }
}