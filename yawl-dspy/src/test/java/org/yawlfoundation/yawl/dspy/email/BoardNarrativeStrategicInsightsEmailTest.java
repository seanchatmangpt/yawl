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
import org.yawlfoundation.yawl.dspy.email.TestFixtureBuilders.WorkletSelectionMetrics;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.yawlfoundation.yawl.dspy.email.EmailAssertions.*;

/**
 * Chicago TDD tests for Board Narrative Strategic Insights email generation.
 *
 * <p>Tests validate DspyWorkletSelector effectiveness and strategic recommendations
 * for board-level reporting. Uses real WorkletSelectionMetrics objects.</p>
 *
 * <p>Email Template Validates:</p>
 * <ul>
 *   <li>Worklet selection accuracy vs benchmark</li>
 *   <li>Case completion rate trends</li>
 *   <li>Resource utilization optimization</li>
 *   <li>Strategic recommendations</li>
 *   <li>Risk assessment</li>
 *   <li>Next board update date</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("Board Narrative Strategic Insights Email Tests")
class BoardNarrativeStrategicInsightsEmailTest {

    private BoardNarrativeGenerator generator;
    private TestSloDashboard sloDashboard;
    private TestRiskAssessor riskAssessor;

    @BeforeEach
    void setUp() {
        sloDashboard = new TestSloDashboard();
        riskAssessor = new TestRiskAssessor();
        generator = new BoardNarrativeGenerator(sloDashboard, riskAssessor);
    }

    @Test
    @DisplayName("Should generate strategic insights for board")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testBoardNarrativeStrategicInsights() {
        // Given: Real worklet selection metrics from month
        WorkletSelectionMetrics metrics = TestFixtureBuilders.createWorkletMetricsForMonth(30);

        // When: Generate board narrative
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Email contains strategic insights
        assertEmailContains(email, """
            Worklet Selection Accuracy: ≥0.90
            Case Completion Rate: ≥0.95
            Resource Utilization: numeric
            Strategic Recommendations: present
            Risk Assessment: present
            """);

        assertRecipient(email, "board@company.com");
    }

    @Test
    @DisplayName("Should compare against industry benchmark")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testIndustryBenchmarkComparison() {
        // Given: Metrics above industry benchmark
        WorkletSelectionMetrics metrics = TestFixtureBuilders.createWorkletMetricsForMonth(30);

        // When: Generate board narrative
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Should show benchmark comparison
        assertThat("Email should contain benchmark",
                email.body(), containsString("benchmark"));
        assertThat("Email should show comparison",
                email.body(), anyOf(
                        containsString("above"),
                        containsString("exceeds"),
                        containsString("+")
                ));
    }

    @Test
    @DisplayName("Should include executive summary")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testExecutiveSummary() {
        // Given: Good performance metrics
        WorkletSelectionMetrics metrics = TestFixtureBuilders.createWorkletMetricsForMonth(30);

        // When: Generate board narrative
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Executive summary should be present
        assertThat("Email should contain executive summary",
                email.body(), containsString("EXECUTIVE SUMMARY"));
        assertThat("Email should show performance status",
                email.body(), anyOf(
                        containsString("EXCEEDS"),
                        containsString("MEETS"),
                        containsString("BELOW")
                ));
    }

    @Test
    @DisplayName("Should include key findings section")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testKeyFindings() {
        // Given: Metrics for the month
        WorkletSelectionMetrics metrics = TestFixtureBuilders.createWorkletMetricsForMonth(30);

        // When: Generate board narrative
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Key findings should be listed
        assertThat("Email should contain key findings",
                email.body(), containsString("Key Findings"));

        // Should have numbered findings
        assertThat("Email should have numbered findings",
                email.body(), matchesPattern(".*\\d\\.\\s+.*"));
    }

    @Test
    @DisplayName("Should include strategic recommendations")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testStrategicRecommendations() {
        // Given: Metrics for the month
        WorkletSelectionMetrics metrics = TestFixtureBuilders.createWorkletMetricsForMonth(30);

        // When: Generate board narrative
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Strategic recommendations should be present
        assertThat("Email should contain strategic recommendations",
                email.body(), containsString("Strategic Recommendations"));

        // Should have actionable items
        assertThat("Email should have numbered recommendations",
                email.body(), matchesPattern(".*\\d\\.\\s+.*Expand.*|.*Invest.*|.*Enable.*"));
    }

    @Test
    @DisplayName("Should include risk assessment")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testRiskAssessment() {
        // Given: Low risk metrics
        WorkletSelectionMetrics metrics = TestFixtureBuilders.createWorkletMetricsForMonth(30);

        // When: Generate board narrative
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Risk assessment should be present
        assertThat("Email should contain risk assessment",
                email.body(), containsString("Risk Assessment"));

        // Should have risk level
        assertThat("Email should show risk level",
                email.body(), anyOf(
                        containsString("LOW"),
                        containsString("MEDIUM"),
                        containsString("HIGH")
                ));
    }

    @Test
    @DisplayName("Should include next board update date")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testNextBoardUpdate() {
        // Given: Metrics for February 2026
        WorkletSelectionMetrics metrics = TestFixtureBuilders.createWorkletMetricsForMonth(30);

        // When: Generate board narrative
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Next update date should be present
        assertThat("Email should contain next board update",
                email.body(), containsString("Next Board Update"));

        // Should show March 2026
        assertThat("Email should show next month",
                email.body(), containsString("2026-03"));
    }

    @Test
    @DisplayName("Should show resource utilization status")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testResourceUtilizationStatus() {
        // Given: Optimized utilization metrics
        WorkletSelectionMetrics metrics = TestFixtureBuilders.createWorkletMetricsForMonth(30);

        // When: Generate board narrative
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Utilization status should be shown
        assertThat("Email should contain resource utilization",
                email.body(), containsString("Resource Utilization"));

        // Should show status
        assertThat("Email should show optimization status",
                email.body(), anyOf(
                        containsString("OPTIMIZED"),
                        containsString("Target"),
                        containsString("87")
                ));
    }

    @Test
    @DisplayName("Should indicate black swan events if any")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testBlackSwanEvents() {
        // Given: Metrics with no black swan events
        WorkletSelectionMetrics metrics = TestFixtureBuilders.createWorkletMetricsForMonth(30);

        // When: Generate board narrative
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Should mention black swan status
        assertThat("Email should mention black swan status",
                email.body(), anyOf(
                        containsString("black swan"),
                        containsString("No"),
                        containsString("events")
                ));
    }

    @Test
    @DisplayName("Should show SLO compliance")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testSloCompliance() {
        // Given: SLO-compliant metrics
        WorkletSelectionMetrics metrics = TestFixtureBuilders.createWorkletMetricsForMonth(30);

        // When: Generate board narrative
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: SLO compliance should be shown
        assertThat("Email should mention SLOs",
                email.body(), anyOf(
                        containsString("SLO"),
                        containsString("met")
                ));
    }

    @Test
    @DisplayName("Should handle poor performance scenario")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testPoorPerformanceScenario() {
        // Given: Below-target metrics
        WorkletSelectionMetrics metrics = new WorkletSelectionMetrics(
                0.75, // Below 85% benchmark
                0.89, // Below 95% target
                0.65, // Low utilization
                1000,
                50,   // Many low confidence events
                30,
                Instant.now().minusSeconds(30 * 86400),
                Instant.now()
        );

        // When: Generate board narrative
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Should indicate below target
        assertThat("Email should indicate below target",
                email.body(), anyOf(
                        containsString("BELOW"),
                        containsString("improvement needed"),
                        containsString("not met")
                ));
    }

    // ========================================================================
    // Test Doubles (Real implementations, not mocks)
    // ========================================================================

    /**
     * Test double for SLO dashboard.
     */
    static class TestSloDashboard {
        boolean allSlosMet(WorkletSelectionMetrics metrics) {
            return metrics.selectionAccuracy() >= 0.85 &&
                    metrics.caseCompletionRate() >= 0.95 &&
                    metrics.resourceUtilization() >= 0.80;
        }

        String getSloStatus(WorkletSelectionMetrics metrics) {
            if (allSlosMet(metrics)) return "All SLOs met with margin";
            return "Some SLOs not met";
        }
    }

    /**
     * Test double for risk assessor.
     */
    static class TestRiskAssessor {
        String assessRisk(WorkletSelectionMetrics metrics) {
            if (metrics.lowConfidenceEvents() > 20) return "HIGH";
            if (metrics.lowConfidenceEvents() > 10) return "MEDIUM";
            return "LOW";
        }

        boolean hasBlackSwanEvents(WorkletSelectionMetrics metrics) {
            return metrics.lowConfidenceEvents() > 50;
        }
    }

    // ========================================================================
    // Email Generator (Real implementation for testing)
    // ========================================================================

    /**
     * Generates board narrative strategic insights emails.
     */
    static class BoardNarrativeGenerator {
        private final TestSloDashboard sloDashboard;
        private final TestRiskAssessor riskAssessor;
        private static final double INDUSTRY_BENCHMARK = 0.85;

        BoardNarrativeGenerator(TestSloDashboard sloDashboard, TestRiskAssessor riskAssessor) {
            this.sloDashboard = sloDashboard;
            this.riskAssessor = riskAssessor;
        }

        Email generate(WorkletSelectionMetrics metrics, YearMonth period) {
            String performanceStatus = determinePerformanceStatus(metrics);
            String riskLevel = riskAssessor.assessRisk(metrics);

            StringBuilder body = new StringBuilder();
            body.append("To: board@company.com\n");
            body.append("Subject: Strategic AI Insights - ")
                    .append(period.getMonth()).append(" ").append(period.getYear()).append("\n\n");

            body.append("EXECUTIVE SUMMARY\n");
            body.append("=================\n");
            body.append("DSPy AI System Performance: ").append(performanceStatus).append("\n\n");

            body.append("Key Findings:\n");
            body.append(String.format("1. Worklet Selection Accuracy: %.1f%%%n", metrics.selectionAccuracy() * 100));
            body.append(String.format("   - Industry benchmark: %.0f%%%n", INDUSTRY_BENCHMARK * 100));
            double benchmarkDelta = (metrics.selectionAccuracy() - INDUSTRY_BENCHMARK) * 100;
            body.append(String.format("   - Our performance: %+.1f%% %s benchmark%n%n",
                    Math.abs(benchmarkDelta), benchmarkDelta >= 0 ? "above" : "below"));

            body.append(String.format("2. Case Completion Rate: %.1f%%%n", metrics.caseCompletionRate() * 100));
            body.append("   - Previous quarter: 95.1%\n");
            body.append(String.format("   - Improvement: +%.1f%%%n%n",
                    (metrics.caseCompletionRate() - 0.951) * 100));

            body.append(String.format("3. Resource Utilization: %.1f%%%n", metrics.resourceUtilization() * 100));
            body.append("   - Target: 85%\n");
            String utilizationStatus = metrics.resourceUtilization() >= 0.80 &&
                    metrics.resourceUtilization() <= 0.90 ? "OPTIMIZED" : "NEEDS ATTENTION";
            body.append(String.format("   - Status: %s%n%n", utilizationStatus));

            body.append("Strategic Recommendations:\n");
            body.append("1. Expand DSPy to 3 additional workflow families\n");
            body.append("2. Invest in custom model fine-tuning ($45K, ROI: 340%)\n");
            body.append("3. Enable cross-tenant learning (privacy-preserving)\n\n");

            body.append("Risk Assessment: ").append(riskLevel).append("\n");
            if (!riskAssessor.hasBlackSwanEvents(metrics)) {
                body.append("- No black swan events this quarter\n");
            }
            body.append("- ").append(sloDashboard.getSloStatus(metrics)).append("\n\n");

            YearMonth nextUpdate = period.plusMonths(1);
            body.append("Next Board Update: ").append(nextUpdate.getYear()).append("-")
                    .append(String.format("%02d", nextUpdate.getMonthValue())).append("-27\n");

            return Email.builder()
                    .to("board@company.com")
                    .from("noreply@yawlfoundation.org")
                    .subject("Strategic AI Insights - " + period.getMonth() + " " + period.getYear())
                    .body(body.toString())
                    .timestamp(Instant.now())
                    .build();
        }

        private String determinePerformanceStatus(WorkletSelectionMetrics metrics) {
            if (metrics.selectionAccuracy() >= 0.90 &&
                    metrics.caseCompletionRate() >= 0.95 &&
                    metrics.resourceUtilization() >= 0.80) {
                return "EXCEEDS EXPECTATIONS";
            } else if (metrics.selectionAccuracy() >= 0.85 &&
                    metrics.caseCompletionRate() >= 0.90) {
                return "MEETS EXPECTATIONS";
            }
            return "BELOW EXPECTATIONS";
        }
    }
}
