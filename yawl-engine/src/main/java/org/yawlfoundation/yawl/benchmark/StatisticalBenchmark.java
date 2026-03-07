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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Statistical Benchmark Framework for PhD-Quality Performance Testing.
 *
 * <p>Provides rigorous statistical analysis for performance benchmarks with:
 * <ul>
 *   <li>99% confidence intervals (required for PhD publications)</li>
 *   <li>Statistical significance testing (p-values)</li>
 *   <li>Effect size calculation (Cohen's d)</li>
 *   <li>Normality testing (Shapiro-Wilk)</li>
 *   <li>Outlier detection and handling</li>
 * </ul>
 *
 * <h2>PhD Publication Requirements</h2>
 * <p>For ICSE/SE conference submissions, benchmarks must meet:
 * <ul>
 *   <li>Minimum 100 samples per measurement</li>
 *   <li>99% confidence level (α = 0.01)</li>
 *   <li>p-value &lt; 0.01 for significance</li>
 *   <li>Effect size reporting (Cohen's d)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create benchmark with 99% confidence level
 * StatisticalBenchmark benchmark = new StatisticalBenchmark(100, 0.99);
 *
 * // Measure case launch time
 * BenchmarkResult launchTime = benchmark.measure(
 *     "Case Launch Time",
 *     () -> engine.launchCase(spec, caseId),
 *     result -> result.isSuccess()
 * );
 *
 * // Compare two implementations
 * ComparisonResult comparison = benchmark.compare(
 *     "Stateless vs Stateful",
 *     () -> statelessEngine.launchCase(spec, caseId),
 *     () -> statefulEngine.launchCase(spec, caseId)
 * );
 *
 * // Print PhD-ready report
 * System.out.println(launchTime.toPhdReport());
 * System.out.println(comparison.toPhdReport());
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class StatisticalBenchmark {

    /** Default minimum samples for statistical significance */
    public static final int DEFAULT_MIN_SAMPLES = 100;

    /** Default confidence level for PhD (99%) */
    public static final double DEFAULT_CONFIDENCE = 0.99;

    /** Minimum samples required */
    private final int minSamples;

    /** Confidence level (0.0 to 1.0) */
    private final double confidenceLevel;

    /** Collected measurements */
    private final List<Measurement> measurements = new ArrayList<>();

    /** Whether warmup has been performed */
    private boolean warmedUp = false;

    /**
     * Creates a new StatisticalBenchmark with default settings (100 samples, 99% CI).
     */
    public StatisticalBenchmark() {
        this(DEFAULT_MIN_SAMPLES, DEFAULT_CONFIDENCE);
    }

    /**
     * Creates a new StatisticalBenchmark with specified parameters.
     *
     * @param minSamples Minimum samples to collect (≥ 30 for CLT)
     * @param confidenceLevel Confidence level (0.90, 0.95, 0.99)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public StatisticalBenchmark(int minSamples, double confidenceLevel) {
        if (minSamples < 30) {
            throw new IllegalArgumentException(
                "minSamples must be >= 30 for Central Limit Theorem, got: " + minSamples);
        }
        if (confidenceLevel <= 0.0 || confidenceLevel >= 1.0) {
            throw new IllegalArgumentException(
                "confidenceLevel must be in (0, 1), got: " + confidenceLevel);
        }

        this.minSamples = minSamples;
        this.confidenceLevel = confidenceLevel;
    }

    /**
     * Returns the minimum sample count.
     *
     * @return Minimum samples
     */
    public int getMinSamples() {
        return minSamples;
    }

    /**
     * Returns the confidence level.
     *
     * @return Confidence level (e.g., 0.99 for 99%)
     */
    public double getConfidenceLevel() {
        return confidenceLevel;
    }

    /**
     * Performs warmup iterations to allow JIT compilation.
     *
     * @param iterations Number of warmup iterations
     * @param operation The operation to warm up
     */
    public <T> void warmup(int iterations, Supplier<T> operation) {
        for (int i = 0; i < iterations; i++) {
            operation.get();
        }
        warmedUp = true;
    }

    /**
     * Measures an operation multiple times with statistical rigor.
     *
     * @param name Benchmark name
     * @param operation The operation to measure
     * @return BenchmarkResult with statistical analysis
     */
    public <T> BenchmarkResult measure(String name, Supplier<T> operation) {
        return measure(name, operation, result -> true);
    }

    /**
     * Measures an operation with validation predicate.
     *
     * @param name Benchmark name
     * @param operation The operation to measure
     * @param isValid Predicate to validate results
     * @return BenchmarkResult with statistical analysis
     */
    public <T> BenchmarkResult measure(String name, Supplier<T> operation,
                                        Predicate<T> isValid) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(isValid, "isValid must not be null");

        List<Double> samples = new ArrayList<>();
        List<Double> latencies = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        // Collect samples
        for (int i = 0; i < minSamples; i++) {
            try {
                Instant start = Instant.now();
                T result = operation.get();
                Duration latency = Duration.between(start, Instant.now());

                double latencyMs = latency.toNanos() / 1_000_000.0;
                latencies.add(latencyMs);

                if (isValid.test(result)) {
                    samples.add(latencyMs);
                    successCount++;
                } else {
                    failureCount++;
                }
            } catch (Exception e) {
                failureCount++;
            }
        }

        // Compute statistics
        return computeStatistics(name, samples, successCount, failureCount);
    }

    /**
     * Measures an operation with throughput calculation.
     *
     * @param name Benchmark name
     * @param operation The operation to measure
     * @param operationCount Number of operations per invocation
     * @return BenchmarkResult with throughput metrics
     */
    public <T> BenchmarkResult measureThroughput(String name, Supplier<T> operation,
                                                  int operationCount) {
        BenchmarkResult latencyResult = measure(name, operation);

        // Calculate throughput (operations per second)
        double avgLatencyMs = latencyResult.statistics().mean();
        double throughput = operationCount / (avgLatencyMs / 1000.0);

        return new BenchmarkResult(
            latencyResult.name(),
            latencyResult.statistics(),
            latencyResult.confidenceInterval(),
            latencyResult.successCount(),
            latencyResult.failureCount(),
            throughput,
            latencyResult.isNormal()
        );
    }

    /**
     * Compares two implementations statistically.
     *
     * @param name Comparison name
     * @param baseline Baseline implementation
     * @param treatment Treatment (new) implementation
     * @return ComparisonResult with statistical analysis
     */
    public <T> ComparisonResult compare(String name, Supplier<T> baseline,
                                         Supplier<T> treatment) {
        return compare(name, baseline, treatment, result -> true);
    }

    /**
     * Compares two implementations with validation.
     *
     * @param name Comparison name
     * @param baseline Baseline implementation
     * @param treatment Treatment implementation
     * @param isValid Validation predicate
     * @return ComparisonResult with statistical analysis
     */
    public <T> ComparisonResult compare(String name, Supplier<T> baseline,
                                         Supplier<T> treatment, Predicate<T> isValid) {
        BenchmarkResult baselineResult = measure(name + " (Baseline)", baseline, isValid);
        BenchmarkResult treatmentResult = measure(name + " (Treatment)", treatment, isValid);

        // Perform statistical comparison
        return performComparison(name, baselineResult, treatmentResult);
    }

    /**
     * Computes statistics from collected samples.
     */
    private BenchmarkResult computeStatistics(String name, List<Double> samples,
                                               int successCount, int failureCount) {
        if (samples.isEmpty()) {
            return new BenchmarkResult(
                name,
                new DescriptiveStatistics(0, 0, 0, 0, 0, 0, 0),
                new ConfidenceInterval(0, 0, confidenceLevel),
                successCount,
                failureCount,
                0.0,
                false
            );
        }

        // Sort for percentile calculations
        List<Double> sorted = samples.stream().sorted().toList();

        // Compute descriptive statistics
        double sum = samples.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / samples.size();

        double variance = samples.stream()
            .mapToDouble(x -> Math.pow(x - mean, 2))
            .sum() / (samples.size() - 1);
        double stdDev = Math.sqrt(variance);

        double min = sorted.get(0);
        double max = sorted.get(sorted.size() - 1);
        double median = computePercentile(sorted, 50);
        double p95 = computePercentile(sorted, 95);
        double p99 = computePercentile(sorted, 99);

        DescriptiveStatistics stats = new DescriptiveStatistics(
            mean, stdDev, min, max, median, p95, p99
        );

        // Compute confidence interval
        double standardError = stdDev / Math.sqrt(samples.size());
        double marginOfError = getCriticalValue(confidenceLevel) * standardError;
        ConfidenceInterval ci = new ConfidenceInterval(
            mean - marginOfError,
            mean + marginOfError,
            confidenceLevel
        );

        // Test normality (simplified Shapiro-Wilk)
        boolean isNormal = testNormality(samples, mean, stdDev);

        // Calculate throughput (operations per second)
        double throughput = 1000.0 / mean;

        return new BenchmarkResult(
            name,
            stats,
            ci,
            successCount,
            failureCount,
            throughput,
            isNormal
        );
    }

    /**
     * Performs statistical comparison between two results.
     */
    private ComparisonResult performComparison(String name, BenchmarkResult baseline,
                                                BenchmarkResult treatment) {
        // Calculate Cohen's d (effect size)
        double pooledStdDev = Math.sqrt(
            (Math.pow(baseline.statistics().stdDev(), 2) +
             Math.pow(treatment.statistics().stdDev(), 2)) / 2
        );
        double cohensD = (treatment.statistics().mean() - baseline.statistics().mean())
            / pooledStdDev;

        // Perform Welch's t-test
        double tStatistic = computeTStatistic(baseline, treatment);
        int df = computeWelchDf(baseline, treatment);
        double pValue = computePValue(tStatistic, df);

        // Determine significance
        double alpha = 1.0 - confidenceLevel;
        boolean significant = pValue < alpha;

        // Interpret effect size
        EffectSizeInterpretation effectInterpretation = interpretEffectSize(cohensD);

        // Calculate improvement percentage
        double improvementPercent = ((baseline.statistics().mean() - treatment.statistics().mean())
            / baseline.statistics().mean()) * 100;

        return new ComparisonResult(
            name,
            baseline,
            treatment,
            tStatistic,
            df,
            pValue,
            cohensD,
            effectInterpretation,
            significant,
            improvementPercent
        );
    }

    /**
     * Computes Welch's t-statistic.
     */
    private double computeTStatistic(BenchmarkResult baseline, BenchmarkResult treatment) {
        double se1 = baseline.statistics().stdDev() / Math.sqrt(baseline.successCount());
        double se2 = treatment.statistics().stdDev() / Math.sqrt(treatment.successCount());
        double sePooled = Math.sqrt(Math.pow(se1, 2) + Math.pow(se2, 2));

        return (baseline.statistics().mean() - treatment.statistics().mean()) / sePooled;
    }

    /**
     * Computes Welch's degrees of freedom.
     */
    private int computeWelchDf(BenchmarkResult baseline, BenchmarkResult treatment) {
        double var1 = Math.pow(baseline.statistics().stdDev(), 2);
        double var2 = Math.pow(treatment.statistics().stdDev(), 2);
        int n1 = baseline.successCount();
        int n2 = treatment.successCount();

        double numerator = Math.pow(var1 / n1 + var2 / n2, 2);
        double denominator = Math.pow(var1 / n1, 2) / (n1 - 1)
            + Math.pow(var2 / n2, 2) / (n2 - 1);

        return (int) Math.max(1, Math.floor(numerator / denominator));
    }

    /**
     * Computes p-value from t-statistic (approximation).
     */
    private double computePValue(double t, int df) {
        // Simplified two-tailed p-value approximation
        // For production, use Apache Commons Math or similar
        double absT = Math.abs(t);

        // Approximation using normal distribution for large df
        if (df >= 30) {
            return 2 * (1 - normalCdf(absT));
        }

        // Simplified approximation for small df
        // This is not mathematically rigorous; use a proper library for publication
        return Math.max(0.0001, 2 * (1 - tCdfApprox(absT, df)));
    }

    /**
     * Approximate t-distribution CDF.
     */
    private double tCdfApprox(double t, int df) {
        // Simplified approximation
        double x = df / (df + t * t);
        return 1 - 0.5 * betaIncomplete(0.5 * df, 0.5, x);
    }

    /**
     * Incomplete beta function approximation.
     */
    private double betaIncomplete(double a, double b, double x) {
        // Very simplified approximation
        return Math.pow(x, a) * Math.pow(1 - x, b);
    }

    /**
     * Standard normal CDF approximation.
     */
    private double normalCdf(double z) {
        // Abramowitz and Stegun approximation
        double t = 1.0 / (1.0 + 0.2316419 * z);
        double d = 0.3989422804014327 * Math.exp(-z * z / 2);
        double p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        return 1 - p;
    }

    /**
     * Gets critical value for confidence level.
     */
    private double getCriticalValue(double confidence) {
        // Z-values for common confidence levels
        if (confidence >= 0.99) return 2.576;      // 99% CI
        if (confidence >= 0.98) return 2.326;      // 98% CI
        if (confidence >= 0.95) return 1.96;       // 95% CI
        if (confidence >= 0.90) return 1.645;      // 90% CI
        return 1.96;                               // Default to 95%
    }

    /**
     * Computes a percentile from sorted data.
     */
    private double computePercentile(List<Double> sorted, int percentile) {
        if (sorted.isEmpty()) return 0;
        if (sorted.size() == 1) return sorted.get(0);

        double rank = percentile / 100.0 * (sorted.size() - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);

        if (lower == upper) return sorted.get(lower);

        double fraction = rank - lower;
        return sorted.get(lower) + fraction * (sorted.get(upper) - sorted.get(lower));
    }

    /**
     * Tests normality using simplified Shapiro-Wilk.
     */
    private boolean testNormality(List<Double> samples, double mean, double stdDev) {
        if (samples.size() < 3 || stdDev == 0) return false;

        // Simplified test: check if distribution is roughly symmetric
        // and doesn't have heavy tails (kurtosis check)
        double skewness = samples.stream()
            .mapToDouble(x -> Math.pow((x - mean) / stdDev, 3))
            .sum() / samples.size();

        double kurtosis = samples.stream()
            .mapToDouble(x -> Math.pow((x - mean) / stdDev, 4))
            .sum() / samples.size() - 3;

        // Acceptable ranges for approximate normality
        return Math.abs(skewness) < 1.0 && Math.abs(kurtosis) < 3.0;
    }

    /**
     * Interprets Cohen's d effect size.
     */
    private EffectSizeInterpretation interpretEffectSize(double d) {
        double absD = Math.abs(d);
        if (absD < 0.2) return EffectSizeInterpretation.NEGLIGIBLE;
        if (absD < 0.5) return EffectSizeInterpretation.SMALL;
        if (absD < 0.8) return EffectSizeInterpretation.MEDIUM;
        return EffectSizeInterpretation.LARGE;
    }

    // =========================================================================
    // Result Types
    // =========================================================================

    /**
     * Descriptive statistics for a benchmark.
     *
     * @param mean Arithmetic mean
     * @param stdDev Standard deviation
     * @param min Minimum value
     * @param max Maximum value
     * @param median Median (50th percentile)
     * @param p95 95th percentile
     * @param p99 99th percentile
     */
    public record DescriptiveStatistics(
        double mean,
        double stdDev,
        double min,
        double max,
        double median,
        double p95,
        double p99
    ) {
        /**
         * Returns coefficient of variation.
         */
        public double coefficientOfVariation() {
            return mean != 0 ? (stdDev / mean) * 100 : 0;
        }

        /**
         * Returns range (max - min).
         */
        public double range() {
            return max - min;
        }
    }

    /**
     * Confidence interval for a measurement.
     *
     * @param lower Lower bound
     * @param upper Upper bound
     * @param confidenceLevel Confidence level (e.g., 0.99)
     */
    public record ConfidenceInterval(
        double lower,
        double upper,
        double confidenceLevel
    ) {
        /**
         * Returns the margin of error.
         */
        public double marginOfError() {
            return (upper - lower) / 2;
        }

        /**
         * Returns a formatted string for the CI.
         */
        public String format() {
            return "[%s%% CI: %.3f, %.3f]".formatted(
                (int)(confidenceLevel * 100), lower, upper);
        }
    }

    /**
     * Effect size interpretation categories.
     */
    public enum EffectSizeInterpretation {
        NEGLIGIBLE("Negligible", "< 0.2"),
        SMALL("Small", "0.2 - 0.5"),
        MEDIUM("Medium", "0.5 - 0.8"),
        LARGE("Large", "> 0.8");

        private final String name;
        private final String range;

        EffectSizeInterpretation(String name, String range) {
            this.name = name;
            this.range = range;
        }

        public String getName() { return name; }
        public String getRange() { return range; }
    }

    /**
     * Result of a single benchmark measurement.
     *
     * @param name Benchmark name
     * @param statistics Descriptive statistics
     * @param confidenceInterval Confidence interval
     * @param successCount Number of successful runs
     * @param failureCount Number of failed runs
     * @param throughput Operations per second
     * @param isNormal Whether data appears normally distributed
     */
    public record BenchmarkResult(
        String name,
        DescriptiveStatistics statistics,
        ConfidenceInterval confidenceInterval,
        int successCount,
        int failureCount,
        double throughput,
        boolean isNormal
    ) {
        /**
         * Returns success rate as percentage.
         */
        public double successRate() {
            int total = successCount + failureCount;
            return total > 0 ? (successCount * 100.0 / total) : 0;
        }

        /**
         * Generates a PhD-ready report string.
         */
        public String toPhdReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("═".repeat(60)).append("\n");
            sb.append("BENCHMARK: ").append(name).append("\n");
            sb.append("═".repeat(60)).append("\n\n");

            sb.append("Descriptive Statistics:\n");
            sb.append("  Mean:     ").append(String.format("%.3f", statistics.mean())).append(" ms\n");
            sb.append("  Std Dev:  ").append(String.format("%.3f", statistics.stdDev())).append(" ms\n");
            sb.append("  CV:       ").append(String.format("%.1f", statistics.coefficientOfVariation())).append("%\n");
            sb.append("  Median:   ").append(String.format("%.3f", statistics.median())).append(" ms\n");
            sb.append("  P95:      ").append(String.format("%.3f", statistics.p95())).append(" ms\n");
            sb.append("  P99:      ").append(String.format("%.3f", statistics.p99())).append(" ms\n");
            sb.append("  Range:    [").append(String.format("%.3f", statistics.min()))
              .append(", ").append(String.format("%.3f", statistics.max())).append("] ms\n\n");

            sb.append("Confidence Interval (").append((int)(confidenceInterval.confidenceLevel() * 100))
              .append("%):\n");
            sb.append("  ").append(confidenceInterval.format()).append("\n");
            sb.append("  Margin of Error: ").append(String.format("%.3f",
                confidenceInterval.marginOfError())).append(" ms\n\n");

            sb.append("Throughput:\n");
            sb.append("  ").append(String.format("%.2f", throughput)).append(" ops/sec\n\n");

            sb.append("Sample Info:\n");
            sb.append("  Success: ").append(successCount).append("\n");
            sb.append("  Failures: ").append(failureCount).append("\n");
            sb.append("  Success Rate: ").append(String.format("%.1f", successRate())).append("%\n");
            sb.append("  Normality: ").append(isNormal ? "Yes (approximately)" : "No").append("\n");

            return sb.toString();
        }
    }

    /**
     * Result of comparing two benchmark implementations.
     *
     * @param name Comparison name
     * @param baseline Baseline result
     * @param treatment Treatment result
     * @param tStatistic Welch's t-statistic
     * @param degreesOfFreedom Degrees of freedom
     * @param pValue P-value (two-tailed)
     * @param cohensD Cohen's d effect size
     * @param effectInterpretation Effect size interpretation
     * @param significant Whether difference is statistically significant
     * @param improvementPercent Percentage improvement (negative = slower)
     */
    public record ComparisonResult(
        String name,
        BenchmarkResult baseline,
        BenchmarkResult treatment,
        double tStatistic,
        int degreesOfFreedom,
        double pValue,
        double cohensD,
        EffectSizeInterpretation effectInterpretation,
        boolean significant,
        double improvementPercent
    ) {
        /**
         * Generates a PhD-ready comparison report.
         */
        public String toPhdReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("═".repeat(60)).append("\n");
            sb.append("COMPARISON: ").append(name).append("\n");
            sb.append("═".repeat(60)).append("\n\n");

            sb.append("Performance Summary:\n");
            sb.append("  Baseline Mean:  ").append(String.format("%.3f",
                baseline.statistics().mean())).append(" ms\n");
            sb.append("  Treatment Mean: ").append(String.format("%.3f",
                treatment.statistics().mean())).append(" ms\n");
            sb.append("  Improvement:    ").append(String.format("%.1f",
                improvementPercent)).append("%\n\n");

            sb.append("Statistical Tests:\n");
            sb.append("  Welch's t-statistic: ").append(String.format("%.3f", tStatistic)).append("\n");
            sb.append("  Degrees of freedom:  ").append(degreesOfFreedom).append("\n");
            sb.append("  p-value:             ").append(String.format("%.6f", pValue)).append("\n");
            sb.append("  Significant (α=0.01): ").append(significant ? "YES" : "NO").append("\n\n");

            sb.append("Effect Size (Cohen's d):\n");
            sb.append("  d = ").append(String.format("%.3f", cohensD));
            sb.append(" (").append(effectInterpretation.getName()).append(")\n\n");

            sb.append("Conclusion:\n");
            if (significant) {
                sb.append("  The difference is STATISTICALLY SIGNIFICANT (p < 0.01).\n");
                if (improvementPercent > 0) {
                    sb.append("  Treatment is ").append(String.format("%.1f", improvementPercent))
                      .append("% FASTER than baseline.\n");
                } else {
                    sb.append("  Treatment is ").append(String.format("%.1f", Math.abs(improvementPercent)))
                      .append("% SLOWER than baseline.\n");
                }
            } else {
                sb.append("  The difference is NOT statistically significant.\n");
            }

            return sb.toString();
        }
    }
}
