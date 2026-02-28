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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.dspy.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.dspy.DspyExecutionMetrics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.yawlfoundation.yawl.dspy.email.EmailAssertions.*;

/**
 * Chicago TDD tests for Daily Board Summary email generation.
 *
 * <p>Tests validate aggregation of DSPy confidence scores and cost metrics
 * for CFO reporting. Uses real DspyExecutionMetrics objects, not mocks.</p>
 *
 * <p>Email Template Validates:</p>
 * <ul>
 *   <li>DSPy confidence metrics (average, low confidence events)</li>
 *   <li>Cost attribution (tokens, estimated cost, cache hit rate)</li>
 *   <li>Anomalies detected with priority levels</li>
 *   <li>Recommendations for scaling</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("Daily Board Summary Email Tests (CFO Report)")
class DailyBoardSummaryEmailTest {

    private DailyBoardSummaryGenerator generator;
    private TestCostAttributor costAttributor;

    @BeforeEach
    void setUp() {
        costAttributor = new TestCostAttributor();
        generator = new DailyBoardSummaryGenerator(costAttributor);
    }

    @Test
    @DisplayName("Should generate daily CFO summary with real DSPy metrics")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testDailyCfoSummary() {
        // Given: Real DspyExecutionMetrics from 24 hours
        List<DspyExecutionMetrics> metrics = TestFixtureBuilders.createMetricsFor24Hours(100);

        // When: Generate summary email
        Email email = generator.generate(metrics, LocalDate.of(2026, 2, 27));

        // Then: Email assertions validate business outcomes
        assertEmailContains(email, """
            Subject: Daily AI Operations Summary
            Average confidence: ≥0.70
            Cache hit rate: ≥80%
            Total executions: numeric
            """);

        assertRecipient(email, "cfo@company.com");
        assertThat("Email should contain date", email.body(),
                containsString("2026-02-27"));
    }

    @Test
    @DisplayName("Should report low confidence events below threshold")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testLowConfidenceEventsReporting() {
        // Given: Mix of high and low confidence metrics
        List<DspyExecutionMetrics> metrics = List.of(
                TestFixtureBuilders.createMetricsWithConfidence(0.92),
                TestFixtureBuilders.createMetricsWithConfidence(0.88),
                TestFixtureBuilders.createLowConfidenceMetrics(), // 0.55
                TestFixtureBuilders.createLowConfidenceMetrics(), // 0.55
                TestFixtureBuilders.createLowConfidenceMetrics()  // 0.55
        );

        // When: Generate summary
        Email email = generator.generate(metrics, LocalDate.now());

        // Then: Low confidence events should be reported
        assertEmailContains(email, """
            Low confidence events: numeric
            """);

        // Should contain warning about low confidence
        assertThat("Email should note low confidence events",
                email.body(), containsString("Low confidence events: 3"));
    }

    @Test
    @DisplayName("Should calculate token costs accurately")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testTokenCostCalculation() {
        // Given: Metrics with known token counts
        List<DspyExecutionMetrics> metrics = List.of(
                DspyExecutionMetrics.builder()
                        .compilationTimeMs(100)
                        .executionTimeMs(500)
                        .inputTokens(1000)
                        .outputTokens(500)
                        .qualityScore(0.90)
                        .cacheHit(true)
                        .timestamp(Instant.now())
                        .build()
        );

        // When: Generate summary
        Email email = generator.generate(metrics, LocalDate.now());

        // Then: Token costs should be calculated
        assertEmailContains(email, """
            LLM tokens: numeric
            Estimated cost: numeric
            """);

        assertThat("Email should show input tokens",
                email.body(), containsString("1,000"));
        assertThat("Email should show output tokens",
                email.body(), containsString("500"));
    }

    @Test
    @DisplayName("Should report anomalies when detected")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testAnomalyReporting() {
        // Given: Metrics that triggered anomalies
        List<DspyExecutionMetrics> metrics = TestFixtureBuilders.createMetricsFor24Hours(50);
        List<String> anomalies = List.of(
                "[P1] case_completion_latency spike (3.2σ deviation)",
                "[P2] queue_depth elevated for 15 minutes"
        );

        // When: Generate summary with anomalies
        Email email = generator.generateWithAnomalies(metrics, LocalDate.now(), anomalies);

        // Then: Anomalies should be reported
        assertEmailContains(email, """
            Anomalies Detected: numeric
            """);

        assertThat("Email should contain P1 anomaly",
                email.body(), containsString("[P1]"));
        assertThat("Email should contain P2 anomaly",
                email.body(), containsString("[P2]"));
    }

    @Test
    @DisplayName("Should include scaling recommendation")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testScalingRecommendation() {
        // Given: High load metrics
        List<DspyExecutionMetrics> metrics = TestFixtureBuilders.createMetricsFor24Hours(200);

        // When: Generate summary
        Email email = generator.generate(metrics, LocalDate.now());

        // Then: Recommendation should be present
        assertThat("Email should contain recommendation",
                email.body(), anyOf(
                        containsString("Recommendation"),
                        containsString("Scale"),
                        containsString("agent pool")
                ));
    }

    @Test
    @DisplayName("Should format cache hit rate as percentage")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testCacheHitRateFormatting() {
        // Given: Metrics with 90% cache hit rate
        List<DspyExecutionMetrics> metrics = TestFixtureBuilders.createMetricsFor24Hours(100);

        // When: Generate summary
        Email email = generator.generate(metrics, LocalDate.now());

        // Then: Cache hit rate should be formatted as percentage
        assertThat("Cache hit rate should be percentage",
                email.body(), matchesPattern(".*Cache hit rate:.*%.*"));
    }

    @Test
    @DisplayName("Should handle empty metrics gracefully")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testEmptyMetrics() {
        // Given: Empty metrics list
        List<DspyExecutionMetrics> metrics = List.of();

        // When: Generate summary
        Email email = generator.generate(metrics, LocalDate.now());

        // Then: Should still generate valid email
        assertThat("Email should have subject", email.subject(), notNullValue());
        assertThat("Email should indicate no data",
                email.body(), anyOf(
                        containsString("No executions"),
                        containsString("0"),
                        containsString("No data")
                ));
    }

    @Test
    @DisplayName("Should calculate average confidence correctly")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testAverageConfidenceCalculation() {
        // Given: Metrics with known confidence scores
        List<DspyExecutionMetrics> metrics = List.of(
                TestFixtureBuilders.createMetricsWithConfidence(0.80),
                TestFixtureBuilders.createMetricsWithConfidence(0.90),
                TestFixtureBuilders.createMetricsWithConfidence(0.85)
        );

        // When: Generate summary
        Email email = generator.generate(metrics, LocalDate.now());

        // Then: Average should be ~0.85
        assertThat("Email should show average confidence",
                email.body(), containsString("Average confidence"));
        assertThat("Average confidence should be around 0.85",
                email.body(), anyOf(
                        containsString("0.85"),
                        containsString("85%")
                ));
    }

    // ========================================================================
    // Test Doubles (Real implementations, not mocks)
    // ========================================================================

    /**
     * Test double for cost attribution service.
     */
    static class TestCostAttributor {
        private static final double COST_PER_1K_INPUT_TOKENS = 0.01;
        private static final double COST_PER_1K_OUTPUT_TOKENS = 0.03;

        double calculateCost(long inputTokens, long outputTokens) {
            return (inputTokens / 1000.0 * COST_PER_1K_INPUT_TOKENS) +
                   (outputTokens / 1000.0 * COST_PER_1K_OUTPUT_TOKENS);
        }
    }

    // ========================================================================
    // Email Generator (Real implementation for testing)
    // ========================================================================

    /**
     * Generates daily board summary emails for CFO reporting.
     */
    static class DailyBoardSummaryGenerator {
        private final TestCostAttributor costAttributor;

        DailyBoardSummaryGenerator(TestCostAttributor costAttributor) {
            this.costAttributor = costAttributor;
        }

        Email generate(List<DspyExecutionMetrics> metrics, LocalDate date) {
            return generateWithAnomalies(metrics, date, List.of());
        }

        Email generateWithAnomalies(List<DspyExecutionMetrics> metrics,
                                     LocalDate date, List<String> anomalies) {
            String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);

            // Calculate aggregates
            long totalInputTokens = metrics.stream()
                    .mapToLong(DspyExecutionMetrics::inputTokens)
                    .sum();
            long totalOutputTokens = metrics.stream()
                    .mapToLong(DspyExecutionMetrics::outputTokens)
                    .sum();
            double avgConfidence = metrics.stream()
                    .filter(m -> m.qualityScore() != null)
                    .mapToDouble(DspyExecutionMetrics::qualityScore)
                    .average()
                    .orElse(0.0);
            long cacheHits = metrics.stream()
                    .filter(DspyExecutionMetrics::cacheHit)
                    .count();
            double cacheHitRate = metrics.isEmpty() ? 0.0 :
                    (double) cacheHits / metrics.size() * 100;
            long lowConfidenceCount = metrics.stream()
                    .filter(m -> m.qualityScore() != null && m.qualityScore() < 0.70)
                    .count();
            double estimatedCost = costAttributor.calculateCost(totalInputTokens, totalOutputTokens);

            StringBuilder body = new StringBuilder();
            body.append("To: cfo@company.com\n");
            body.append("Subject: Daily AI Operations Summary - ").append(dateStr).append("\n\n");
            body.append("DSPy Confidence Metrics:\n");
            body.append(String.format("- Average confidence: %.2f (target: ≥0.70)%n", avgConfidence));
            body.append(String.format("- Low confidence events: %d (threshold: <5)%n", lowConfidenceCount));
            body.append(String.format("- Total executions: %,d%n%n", metrics.size()));
            body.append("Cost Attribution:\n");
            body.append(String.format("- LLM tokens: %,dK input / %,dK output%n",
                    totalInputTokens / 1000, totalOutputTokens / 1000));
            body.append(String.format("- Estimated cost: $%.2f%n", estimatedCost));
            body.append(String.format("- Cache hit rate: %.1f%%%n%n", cacheHitRate));

            if (!anomalies.isEmpty()) {
                body.append("Anomalies Detected: ").append(anomalies.size()).append("\n");
                for (String anomaly : anomalies) {
                    body.append("- ").append(anomaly).append("\n");
                }
                body.append("\n");
            }

            if (cacheHitRate < 85 || metrics.size() > 150) {
                body.append("Recommendation: Scale agent pool +").append(metrics.size() / 50).append(" units\n");
            }

            return Email.builder()
                    .to("cfo@company.com")
                    .from("noreply@yawlfoundation.org")
                    .subject("Daily AI Operations Summary - " + dateStr)
                    .body(body.toString())
                    .timestamp(Instant.now())
                    .build();
        }
    }
}
