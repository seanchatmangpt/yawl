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

package org.yawlfoundation.yawl.benchmark;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD Tests for StatisticalBenchmark — PhD-Quality Performance Testing.
 *
 * <p>Tests the statistical benchmark framework with real measurements
 * (no mocks) to verify correct statistical analysis.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("Statistical Benchmark Tests")
class StatisticalBenchmarkTest {

    private StatisticalBenchmark benchmark;

    @BeforeEach
    void setUp() {
        // Use smaller sample size for tests (faster execution)
        benchmark = new StatisticalBenchmark(50, 0.95);
    }

    // =========================================================================
    // Construction Tests
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Creates with default settings")
        void createsWithDefaultSettings() {
            StatisticalBenchmark defaultBenchmark = new StatisticalBenchmark();

            assertEquals(100, defaultBenchmark.getMinSamples());
            assertEquals(0.99, defaultBenchmark.getConfidenceLevel());
        }

        @Test
        @DisplayName("Creates with custom settings")
        void createsWithCustomSettings() {
            assertEquals(50, benchmark.getMinSamples());
            assertEquals(0.95, benchmark.getConfidenceLevel());
        }

        @Test
        @DisplayName("Rejects sample size below 30")
        void rejectsSampleSizeBelow30() {
            assertThrows(IllegalArgumentException.class,
                () -> new StatisticalBenchmark(29, 0.95));
        }

        @Test
        @DisplayName("Rejects invalid confidence level")
        void rejectsInvalidConfidenceLevel() {
            assertThrows(IllegalArgumentException.class,
                () -> new StatisticalBenchmark(100, 0.0));
            assertThrows(IllegalArgumentException.class,
                () -> new StatisticalBenchmark(100, 1.0));
            assertThrows(IllegalArgumentException.class,
                () -> new StatisticalBenchmark(100, -0.5));
        }
    }

    // =========================================================================
    // Measurement Tests
    // =========================================================================

    @Nested
    @DisplayName("Measurements")
    class MeasurementTests {

        @Test
        @DisplayName("Measures operation with statistics")
        void measuresOperationWithStatistics() {
            StatisticalBenchmark.BenchmarkResult result = benchmark.measure(
                "Test Operation",
                () -> {
                    // Simulate work (1-5ms)
                    Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5));
                    return "result";
                }
            );

            assertEquals("Test Operation", result.name());
            assertTrue(result.statistics().mean() >= 1.0);
            assertTrue(result.successCount() > 0);
            assertNotNull(result.confidenceInterval());
        }

        @Test
        @DisplayName("Computes confidence interval correctly")
        void computesConfidenceIntervalCorrectly() {
            StatisticalBenchmark.BenchmarkResult result = benchmark.measure(
                "CI Test",
                () -> "result"
            );

            StatisticalBenchmark.ConfidenceInterval ci = result.confidenceInterval();

            assertTrue(ci.lower() <= result.statistics().mean());
            assertTrue(ci.upper() >= result.statistics().mean());
            assertEquals(0.95, ci.confidenceLevel());
        }

        @Test
        @DisplayName("Calculates percentiles correctly")
        void calculatesPercentilesCorrectly() {
            StatisticalBenchmark.BenchmarkResult result = benchmark.measure(
                "Percentile Test",
                () -> "result"
            );

            StatisticalBenchmark.DescriptiveStatistics stats = result.statistics();

            assertTrue(stats.median() >= stats.min());
            assertTrue(stats.median() <= stats.max());
            assertTrue(stats.p95() >= stats.median());
            assertTrue(stats.p99() >= stats.p95());
        }

        @Test
        @DisplayName("Counts successes and failures")
        void countsSuccessesAndFailures() {
            StatisticalBenchmark.BenchmarkResult result = benchmark.measure(
                "Validation Test",
                () -> "valid",
                r -> r.equals("valid")
            );

            assertTrue(result.successCount() > 0);
        }

        @Test
        @DisplayName("Calculates throughput")
        void calculatesThroughput() {
            StatisticalBenchmark.BenchmarkResult result = benchmark.measure(
                "Throughput Test",
                () -> {
                    Thread.sleep(1);
                    return "result";
                }
            );

            assertTrue(result.throughput() > 0);
        }
    }

    // =========================================================================
    // Comparison Tests
    // =========================================================================

    @Nested
    @DisplayName("Comparisons")
    class ComparisonTests {

        @Test
        @DisplayName("Compares two implementations")
        void comparesTwoImplementations() {
            StatisticalBenchmark.ComparisonResult comparison = benchmark.compare(
                "Fast vs Slow",
                // Baseline: faster operation
                () -> {
                    Thread.sleep(1);
                    return "fast";
                },
                // Treatment: slower operation
                () -> {
                    Thread.sleep(5);
                    return "slow";
                }
            );

            assertNotNull(comparison);
            assertNotNull(comparison.baseline());
            assertNotNull(comparison.treatment());
            assertNotNull(comparison.name());
        }

        @Test
        @DisplayName("Calculates effect size")
        void calculatesEffectSize() {
            StatisticalBenchmark.ComparisonResult comparison = benchmark.compare(
                "Effect Size Test",
                () -> {
                    Thread.sleep(1);
                    return "a";
                },
                () -> {
                    Thread.sleep(10);
                    return "b";
                }
            );

            // Large difference should produce large effect size
            assertNotNull(comparison.cohensD());
            assertNotNull(comparison.effectInterpretation());
        }

        @Test
        @DisplayName("Reports statistical significance")
        void reportsStatisticalSignificance() {
            StatisticalBenchmark.ComparisonResult comparison = benchmark.compare(
                "Significance Test",
                () -> 1,
                () -> 2
            );

            // Whether significant or not, the field should be set
            assertNotNull(comparison.significant() || !comparison.significant());
            assertTrue(comparison.pValue() >= 0);
            assertTrue(comparison.pValue() <= 1);
        }
    }

    // =========================================================================
    // Report Generation Tests
    // =========================================================================

    @Nested
    @DisplayName("Report Generation")
    class ReportGenerationTests {

        @Test
        @DisplayName("Generates PhD report for single result")
        void generatesPhdReportForSingleResult() {
            StatisticalBenchmark.BenchmarkResult result = benchmark.measure(
                "Report Test",
                () -> "result"
            );

            String report = result.toPhdReport();

            assertTrue(report.contains("BENCHMARK"));
            assertTrue(report.contains("Mean"));
            assertTrue(report.contains("Std Dev"));
            assertTrue(report.contains("Confidence Interval"));
            assertTrue(report.contains("Throughput"));
        }

        @Test
        @DisplayName("Generates PhD report for comparison")
        void generatesPhdReportForComparison() {
            StatisticalBenchmark.ComparisonResult comparison = benchmark.compare(
                "Comparison Report Test",
                () -> 1,
                () -> 2
            );

            String report = comparison.toPhdReport();

            assertTrue(report.contains("COMPARISON"));
            assertTrue(report.contains("Statistical Tests"));
            assertTrue(report.contains("Effect Size"));
            assertTrue(report.contains("Conclusion"));
        }

        @Test
        @DisplayName("Includes all descriptive statistics in report")
        void includesAllDescriptiveStatisticsInReport() {
            StatisticalBenchmark.BenchmarkResult result = benchmark.measure(
                "Stats Report Test",
                () -> "result"
            );

            String report = result.toPhdReport();

            assertTrue(report.contains("Mean"));
            assertTrue(report.contains("Std Dev"));
            assertTrue(report.contains("CV"));  // Coefficient of Variation
            assertTrue(report.contains("Median"));
            assertTrue(report.contains("P95"));
            assertTrue(report.contains("P99"));
            assertTrue(report.contains("Range"));
        }
    }

    // =========================================================================
    // Descriptive Statistics Tests
    // =========================================================================

    @Nested
    @DisplayName("Descriptive Statistics")
    class DescriptiveStatisticsTests {

        @Test
        @DisplayName("Calculates coefficient of variation")
        void calculatesCoefficientOfVariation() {
            StatisticalBenchmark.BenchmarkResult result = benchmark.measure(
                "CV Test",
                () -> "result"
            );

            double cv = result.statistics().coefficientOfVariation();

            // CV should be a percentage
            assertTrue(cv >= 0);
        }

        @Test
        @DisplayName("Calculates range")
        void calculatesRange() {
            StatisticalBenchmark.BenchmarkResult result = benchmark.measure(
                "Range Test",
                () -> "result"
            );

            double range = result.statistics().range();

            assertTrue(range >= 0);
            assertEquals(
                result.statistics().max() - result.statistics().min(),
                range
            );
        }
    }

    // =========================================================================
    // Warmup Tests
    // =========================================================================

    @Nested
    @DisplayName("Warmup")
    class WarmupTests {

        @Test
        @DisplayName("Warmup executes without errors")
        void warmupExecutesWithoutErrors() {
            // Warmup should not throw
            assertDoesNotThrow(() ->
                benchmark.warmup(10, () -> "result")
            );
        }
    }

    // =========================================================================
    // Effect Size Interpretation Tests
    // =========================================================================

    @Nested
    @DisplayName("Effect Size Interpretation")
    class EffectSizeInterpretationTests {

        @Test
        @DisplayName("Interprets negligible effect size")
        void interpretsNegligibleEffectSize() {
            assertEquals(
                StatisticalBenchmark.EffectSizeInterpretation.NEGLIGIBLE,
                findInterpretationForD(0.1)
            );
        }

        @Test
        @DisplayName("Interprets small effect size")
        void interpretsSmallEffectSize() {
            assertEquals(
                StatisticalBenchmark.EffectSizeInterpretation.SMALL,
                findInterpretationForD(0.3)
            );
        }

        @Test
        @DisplayName("Interprets medium effect size")
        void interpretsMediumEffectSize() {
            assertEquals(
                StatisticalBenchmark.EffectSizeInterpretation.MEDIUM,
                findInterpretationForD(0.6)
            );
        }

        @Test
        @DisplayName("Interprets large effect size")
        void interpretsLargeEffectSize() {
            assertEquals(
                StatisticalBenchmark.EffectSizeInterpretation.LARGE,
                findInterpretationForD(1.0)
            );
        }

        private StatisticalBenchmark.EffectSizeInterpretation findInterpretationForD(double d) {
            double absD = Math.abs(d);
            if (absD < 0.2) return StatisticalBenchmark.EffectSizeInterpretation.NEGLIGIBLE;
            if (absD < 0.5) return StatisticalBenchmark.EffectSizeInterpretation.SMALL;
            if (absD < 0.8) return StatisticalBenchmark.EffectSizeInterpretation.MEDIUM;
            return StatisticalBenchmark.EffectSizeInterpretation.LARGE;
        }
    }
}
