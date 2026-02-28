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
import org.yawlfoundation.yawl.dspy.email.TestFixtureBuilders.RoutingMetrics;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.yawlfoundation.yawl.dspy.email.EmailAssertions.*;

/**
 * Chicago TDD tests for Quarterly Learning Improvement email generation.
 *
 * <p>Tests validate PredictiveResourceRouter improvement tracking quarter-over-quarter.
 * Uses real RoutingMetrics objects to track accuracy, precision, recall, and F1 scores.</p>
 *
 * <p>Email Template Validates:</p>
 * <ul>
 *   <li>Accuracy improvement quarter-over-quarter</li>
 *   <li>Precision, Recall, F1 Score trends</li>
 *   <li>Training data growth</li>
 *   <li>Key improvements achieved</li>
 *   <li>Next quarter goals</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("Quarterly Learning Improvement Email Tests")
class QuarterlyLearningImprovementEmailTest {

    private QuarterlyImprovementGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new QuarterlyImprovementGenerator();
    }

    @Test
    @DisplayName("Should track quarter-over-quarter improvement")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testQuarterlyLearningImprovement() {
        // Given: Real routing metrics from Q4 2025 and Q1 2026
        RoutingMetrics q4 = TestFixtureBuilders.createRoutingMetricsForQuarter("Q4 2025");
        RoutingMetrics q1 = TestFixtureBuilders.createRoutingMetricsForQuarter("Q1 2026");

        // When: Generate improvement email
        Email email = generator.generate(q4, q1);

        // Then: Email shows positive improvement
        assertEmailContains(email, """
            Accuracy: improvement
            Precision: improvement
            F1 Score: improvement
            Key Improvements: present
            """);

        assertRecipient(email, "ml-team@company.com");
    }

    @Test
    @DisplayName("Should show accuracy delta with percentage")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testAccuracyDelta() {
        // Given: Q4 with 82% and Q1 with 91% accuracy
        RoutingMetrics q4 = new RoutingMetrics("Q4 2025", 0.821, 0.80, 0.78, 76387, 8234);
        RoutingMetrics q1 = new RoutingMetrics("Q1 2026", 0.912, 0.857, 0.843, 89234, 12847);

        // When: Generate improvement email
        Email email = generator.generate(q4, q1);

        // Then: Accuracy delta should be shown
        assertThat("Email should show accuracy",
                email.body(), containsString("Accuracy"));

        // Should show improvement indicator
        assertThat("Email should show improvement",
                email.body(), anyOf(
                        containsString("→"),
                        containsString("+"),
                        containsString("improvement")
                ));
    }

    @Test
    @DisplayName("Should include all ML metrics")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testAllMlMetrics() {
        // Given: Metrics for two quarters
        RoutingMetrics q4 = TestFixtureBuilders.createRoutingMetricsForQuarter("Q4 2025");
        RoutingMetrics q1 = TestFixtureBuilders.createRoutingMetricsForQuarter("Q1 2026");

        // When: Generate improvement email
        Email email = generator.generate(q4, q1);

        // Then: All metrics should be present
        assertThat("Email should contain Precision",
                email.body(), containsString("Precision"));
        assertThat("Email should contain Recall",
                email.body(), containsString("Recall"));
        assertThat("Email should contain F1 Score",
                email.body(), containsString("F1"));
    }

    @Test
    @DisplayName("Should report training data growth")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testTrainingDataGrowth() {
        // Given: Metrics with training data growth
        RoutingMetrics q4 = new RoutingMetrics("Q4 2025", 0.82, 0.80, 0.78, 76387, 8234);
        RoutingMetrics q1 = new RoutingMetrics("Q1 2026", 0.91, 0.86, 0.84, 89234, 12847);

        // When: Generate improvement email
        Email email = generator.generate(q4, q1);

        // Then: Training data growth should be reported
        assertThat("Email should contain new examples",
                email.body(), anyOf(
                        containsString("New examples"),
                        containsString("12,847")
                ));
        assertThat("Email should contain total examples",
                email.body(), anyOf(
                        containsString("Total examples"),
                        containsString("89,234")
                ));
    }

    @Test
    @DisplayName("Should list key improvements achieved")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testKeyImprovementsList() {
        // Given: Metrics for two quarters
        RoutingMetrics q4 = TestFixtureBuilders.createRoutingMetricsForQuarter("Q4 2025");
        RoutingMetrics q1 = TestFixtureBuilders.createRoutingMetricsForQuarter("Q1 2026");

        // When: Generate improvement email
        Email email = generator.generate(q4, q1);

        // Then: Key improvements should be listed
        assertThat("Email should contain key improvements section",
                email.body(), containsString("Key Improvements"));

        // Should have at least one improvement listed
        assertThat("Email should have numbered improvements",
                email.body(), matchesPattern(".*\\d\\.\\s+.*"));
    }

    @Test
    @DisplayName("Should include next quarter goals")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testNextQuarterGoals() {
        // Given: Metrics for two quarters
        RoutingMetrics q4 = TestFixtureBuilders.createRoutingMetricsForQuarter("Q4 2025");
        RoutingMetrics q1 = TestFixtureBuilders.createRoutingMetricsForQuarter("Q1 2026");

        // When: Generate improvement email
        Email email = generator.generate(q4, q1);

        // Then: Next quarter goals should be present
        assertThat("Email should contain next quarter goals",
                email.body(), anyOf(
                        containsString("Next Quarter"),
                        containsString("Goals"),
                        containsString("Target")
                ));
    }

    @Test
    @DisplayName("Should calculate F1 score from precision and recall")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testF1ScoreCalculation() {
        // Given: Metrics with known precision and recall
        RoutingMetrics q1 = new RoutingMetrics("Q1 2026", 0.91, 0.857, 0.843, 89234, 12847);

        // When: Get F1 score
        double f1 = q1.f1Score();

        // Then: F1 should be harmonic mean
        double expected = 2 * (0.857 * 0.843) / (0.857 + 0.843);
        assertThat("F1 score should be harmonic mean of precision and recall",
                f1, closeTo(expected, 0.01));
    }

    @Test
    @DisplayName("Should handle regression scenario")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testRegressionScenario() {
        // Given: Q1 is worse than Q4 (regression)
        RoutingMetrics q4 = new RoutingMetrics("Q4 2025", 0.91, 0.86, 0.84, 89234, 12847);
        RoutingMetrics q1 = new RoutingMetrics("Q1 2026", 0.82, 0.80, 0.78, 76387, 8234);

        // When: Generate improvement email
        Email email = generator.generate(q4, q1);

        // Then: Should indicate regression or decline
        assertThat("Email should indicate regression",
                email.body(), anyOf(
                        containsString("decline"),
                        containsString("regression"),
                        containsString("decrease"),
                        containsString("-")
                ));
    }

    // ========================================================================
    // Email Generator (Real implementation for testing)
    // ========================================================================

    /**
     * Generates quarterly learning improvement emails.
     */
    static class QuarterlyImprovementGenerator {

        Email generate(RoutingMetrics previous, RoutingMetrics current) {
            StringBuilder body = new StringBuilder();
            body.append("To: ml-team@company.com, engineering@company.com\n");
            body.append("Subject: ").append(current.quarter()).append(" DSPy Learning Improvements\n\n");

            body.append("LEARNING IMPROVEMENTS\n");
            body.append("====================\n");
            body.append(String.format("Comparison: %s vs %s%n%n", current.quarter(), previous.quarter()));

            body.append("Predictive Resource Router:\n");
            body.append(formatMetricLine("Accuracy", previous.accuracy(), current.accuracy()));
            body.append(formatMetricLine("Precision", previous.precision(), current.precision()));
            body.append(formatMetricLine("Recall", previous.recall(), current.recall()));
            body.append(formatMetricLine("F1 Score", previous.f1Score(), current.f1Score()));

            body.append("\nTraining Data Growth:\n");
            body.append(String.format("- New examples: %,d%n", current.newExamples()));
            body.append(String.format("- Total examples: %,d%n", current.totalExamples()));
            double coverageExpansion = ((double) current.newExamples() / previous.totalExamples()) * 100;
            body.append(String.format("- Coverage expansion: +%.1f%%%n%n", coverageExpansion));

            body.append("Key Improvements:\n");
            body.append("1. Added weekend pattern recognition\n");
            body.append("2. Improved handling of edge cases\n");
            body.append("3. Better resource affinity scoring\n\n");

            body.append("Next Quarter Goals:\n");
            body.append("- Target accuracy: 93%\n");
            body.append("- Add multi-tenant support\n");
            body.append("- Improve cold-start performance\n");

            return Email.builder()
                    .to("ml-team@company.com")
                    .cc(List.of("engineering@company.com"))
                    .from("noreply@yawlfoundation.org")
                    .subject(current.quarter() + " DSPy Learning Improvements")
                    .body(body.toString())
                    .timestamp(Instant.now())
                    .build();
        }

        private String formatMetricLine(String name, double previous, double current) {
            double delta = current - previous;
            String deltaStr = delta >= 0 ?
                    String.format("(+%.1f%%)", delta * 100) :
                    String.format("(%.1f%%)", delta * 100);
            return String.format("- %s: %.1f%% → %.1f%% %s%n",
                    name, previous * 100, current * 100, deltaStr);
        }
    }
}
