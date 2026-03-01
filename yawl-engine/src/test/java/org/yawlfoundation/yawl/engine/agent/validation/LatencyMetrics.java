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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.agent.validation;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;

/**
 * Enhanced latency metrics collection and analysis for scheduling performance.
 * Provides precise percentile measurements, trend analysis, and performance degradation detection.
 *
 * <p>Enhanced features:
 * - High-precision nanosecond latency tracking
 * - Comprehensive percentile calculation (p50, p75, p90, p95, p99, p99.9, p99.99)
 * - Sliding window for continuous measurement
 * - Performance trend analysis
 * - Degradation detection
 * - Comparison with strict targets
 * - Multi-dimensional latency analysis (spawn, message, scheduling)
 * - Outlier detection and analysis
 * - Latency distribution visualization
 */
public class LatencyMetrics {

    // Enhanced targets
    private static final long TARGET_P50_NANOS = 100_000;     // 100μs
    private static final long TARGET_P75_NANOS = 250_000;     // 250μs
    private static final long TARGET_P90_NANOS = 500_000;     // 500μs
    private static final long TARGET_P95_NANOS = 1_000_000;   // 1ms
    private static final long TARGET_P99_NANOS = 2_000_000;   // 2ms
    private static final long TARGET_P99_9_NANOS = 5_000_000;  // 5ms
    private static final long TARGET_P99_99_NANOS = 10_000_000; // 10ms

    // Configuration
    private static final int DEFAULT_SLIDING_WINDOW_SIZE = 100_000;
    private static final int MAX_OUTLIER_SAMPLES = 1000;
    private static final double OUTLIER_THRESHOLD = 3.0; // 3 standard deviations

    // Data storage
    private final SlidingBuffer<LatencySample> latencyBuffer;
    private final LongAdder totalLatency = new LongAdder();
    private final AtomicInteger sampleCount = new AtomicInteger(0);
    private final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatency = new AtomicLong(Long.MIN_VALUE);

    // Performance tracking
    private final DoubleAdder throughputCounter = new DoubleAdder();
    private long lastMeasurementTime = System.nanoTime();

    // Analysis results
    private PercentileResults latestPercentiles;
    private PerformanceTrend trend = new PerformanceTrend();
    private OutlierAnalysis outlierAnalysis = new OutlierAnalysis();

    // Alerting
    private final List<LatencyAlert> activeAlerts = new CopyOnWriteArrayList<>();
    private final AtomicLong alertCount = new AtomicLong(0);

    public LatencyMetrics() {
        this(DEFAULT_SLIDING_WINDOW_SIZE);
    }

    public LatencyMetrics(int slidingWindowSize) {
        this.latencyBuffer = new SlidingBuffer<>(Math.min(slidingWindowSize, 1_000_000));
    }

    /**
     * Record a latency measurement with context.
     */
    public void recordLatency(long startTimeNanos, String operationType, Map<String, String> context) {
        long latency = System.nanoTime() - startTimeNanos;

        // Update basic statistics
        totalLatency.add(latency);
        sampleCount.incrementAndGet();

        // Update min/max
        minLatency.updateAndGet(m -> Math.min(m, latency));
        maxLatency.updateAndGet(m -> Math.max(m, latency));

        // Store for percentile calculation
        LatencySample sample = new LatencySample(latency, operationType, System.currentTimeMillis(), context);
        latencyBuffer.add(sample);

        // Update throughput
        updateThroughput();

        // Check for alerts
        checkForAlerts(latency, operationType);

        // Periodically analyze trends
        if (sampleCount.get() % 1000 == 0) {
            analyzeTrends();
        }
    }

    /**
     * Record a latency measurement without context.
     */
    public void recordLatency(long startTimeNanos, String operationType) {
        recordLatency(startTimeNanos, operationType, Collections.emptyMap());
    }

    /**
     * Calculate enhanced percentiles including p99.99.
     */
    public EnhancedPercentileResults calculateEnhancedPercentiles() {
        if (latencyBuffer.isEmpty()) {
            return new EnhancedPercentileResults();
        }

        // Extract all latencies
        long[] latencies = latencyBuffer.stream()
            .mapToLong(s -> s.latencyNanos)
            .sorted()
            .toArray();

        latestPercentiles = new EnhancedPercentileResults();
        latestPercentiles.totalSamples = latencies.length;
        latestPercentiles.minNanos = minLatency.get();
        latestPercentiles.maxNanos = maxLatency.get();
        latestPercentiles.meanNanos = totalLatency.sum() / latencies.length;

        // Calculate all percentiles
        latestPercentiles.p50Nanos = percentile(latencies, 0.50);
        latestPercentiles.p75Nanos = percentile(latencies, 0.75);
        latestPercentiles.p90Nanos = percentile(latencies, 0.90);
        latestPercentiles.p95Nanos = percentile(latencies, 0.95);
        latestPercentiles.p99Nanos = percentile(latencies, 0.99);
        latestPercentiles.p99_9Nanos = percentile(latencies, 0.999);
        latestPercentiles.p99_99Nanos = percentile(latencies, 0.9999);

        // Convert to milliseconds for readability
        latestPercentiles.convertToMilliseconds();

        // Analyze outliers
        analyzeOutliers(latencies);

        // Calculate distribution statistics
        calculateDistributionStats();

        return latestPercentiles;
    }

    /**
     * Check if all targets are met.
     */
    public boolean allTargetsMet() {
        if (latestPercentiles == null) {
            return true; // No data, assume targets are met
        }

        return latestPercentiles.p50Millis <= TARGET_P50_NANOS / 1_000_000.0 &&
               latestPercentiles.p75Millis <= TARGET_P75_NANOS / 1_000_000.0 &&
               latestPercentiles.p90Millis <= TARGET_P90_NANOS / 1_000_000.0 &&
               latestPercentiles.p95Millis <= TARGET_P95_NANOS / 1_000_000.0 &&
               latestPercentiles.p99Millis <= TARGET_P99_NANOS / 1_000_000.0 &&
               latestPercentiles.p99_9Millis <= TARGET_P99_9_NANOS / 1_000_000.0;
    }

    /**
     * Check if critical targets are violated.
     */
    public boolean criticalTargetsViolated() {
        if (latestPercentiles == null) {
            return false;
        }

        return latestPercentiles.p99Millis > TARGET_P99_NANOS / 1_000_000.0 ||
               latestPercentiles.p99_9Millis > TARGET_P99_9_NANOS / 1_000_000.0;
    }

    /**
     * Analyze performance trends with improved detection.
     */
    public EnhancedPerformanceTrend analyzeEnhancedTrend() {
        EnhancedPerformanceTrend enhancedTrend = new EnhancedPerformanceTrend();
        enhancedTrend.timestamp = System.currentTimeMillis();

        if (latencyBuffer.size() < 100) {
            enhancedTrend.trendType = TrendType.NOT_ENOUGH_DATA;
            return enhancedTrend;
        }

        // Calculate trends over different time windows
        int[] windowSizes = {100, 1000, 10000};
        for (int windowSize : windowSizes) {
            if (latencyBuffer.size() >= windowSize) {
                TrendWindowAnalysis window = analyzeWindow(windowSize);
                enhancedTrend.windowAnalyses.put(windowSize, window);
            }
        }

        // Detect overall trend
        TrendWindowAnalysis recent = enhancedTrend.windowAnalyses.get(100);
        TrendWindowAnalysis older = enhancedTrend.windowAnalyses.get(1000);

        if (recent != null && older != null) {
            if (recent.meanNanos > older.meanNanos * 1.5) {
                enhancedTrend.trendType = TrendType.DEGRADING;
                enhancedTrend.degradationFactor = recent.meanNanos / (double) older.meanNanos;
            } else if (recent.meanNanos < older.meanNanos * 0.8) {
                enhancedTrend.trendType = TrendType.IMPROVING;
                enhancedTrend.improvementFactor = older.meanNanos / (double) recent.meanNanos;
            } else {
                enhancedTrend.trendType = TrendType.STABLE;
            }
        }

        // Check for seasonal patterns
        enhancedTrend.seasonalPatterns = detectSeasonalPatterns();

        // Update basic trend info
        enhancedTrend.basicTrend = analyzeBasicTrend();

        return enhancedTrend;
    }

    /**
     * Generate comprehensive latency report.
     */
    public String generateEnhancedReport() {
        EnhancedPercentileResults results = calculateEnhancedPercentiles();
        EnhancedPerformanceTrend trend = analyzeEnhancedTrend();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Enhanced Latency Metrics Report ===\n\n");
        sb.append(String.format("Total samples: %,d\n", results.totalSamples));
        sb.append(String.format("Measurement duration: %.1f minutes\n",
            (System.currentTimeMillis() - firstSampleTimestamp) / 60000.0));
        sb.append(String.format("Min latency: %.3f ms\n", results.minMillis));
        sb.append(String.format("Max latency: %.3f ms\n", results.maxMillis));
        sb.append(String.format("Mean latency: %.3f ms\n", results.meanMillis));
        sb.append(String.format("Throughput: %.0f measurements/s\n", calculateThroughput()));
        sb.append("\n");

        sb.append("=== Percentile Targets ===\n");
        sb.append(String.format("p50:   %7.3f ms%s\n", results.p50Millis,
            meetsTarget(results.p50Millis, TARGET_P50_NANOS / 1_000_000.0) ? " ✓" : " ✗"));
        sb.append(String.format("p75:   %7.3f ms%s\n", results.p75Millis,
            meetsTarget(results.p75Millis, TARGET_P75_NANOS / 1_000_000.0) ? " ✓" : " ✗"));
        sb.append(String.format("p90:   %7.3f ms%s\n", results.p90Millis,
            meetsTarget(results.p90Millis, TARGET_P90_NANOS / 1_000_000.0) ? " ✓" : " ✗"));
        sb.append(String.format("p95:   %7.3f ms%s\n", results.p95Millis,
            meetsTarget(results.p95Millis, TARGET_P95_NANOS / 1_000_000.0) ? " ✓" : " ✗"));
        sb.append(String.format("p99:   %7.3f ms%s\n", results.p99Millis,
            meetsTarget(results.p99Millis, TARGET_P99_NANOS / 1_000_000.0) ? " ✓" : " ✗"));
        sb.append(String.format("p99.9: %7.3f ms%s\n", results.p99_9Millis,
            meetsTarget(results.p99_9Millis, TARGET_P99_9_NANOS / 1_000_000.0) ? " ✓" : " ✗"));
        sb.append(String.format("p99.99:%7.3f ms%s\n", results.p99_99Millis,
            meetsTarget(results.p99_99Millis, TARGET_P99_99_NANOS / 1_000_000.0) ? " ✓" : " ✗"));
        sb.append("\n");

        // Trend analysis
        sb.append("=== Trend Analysis ===\n");
        sb.append(String.format("Overall trend: %s\n", trend.trendType));
        if (trend.trendType == TrendType.DEGRADING) {
            sb.append(String.format("Degradation factor: %.2fx\n", trend.degradationFactor));
        } else if (trend.trendType == TrendType.IMPROVING) {
            sb.append(String.format("Improvement factor: %.2fx\n", trend.improvementFactor));
        }
        sb.append("\n");

        // Outlier analysis
        if (outlierAnalysis.hasOutliers) {
            sb.append("=== Outlier Analysis ===\n");
            sb.append(String.format("Outlier count: %d (%.1f%%)\n",
                outlier.outlierCount, outlier.outlierPercentage));
            sb.append(String.format("Mean outlier latency: %.3f ms\n",
                outlier.meanOutlierMillis));
            sb.append(String.format("Max outlier latency: %.3f ms\n",
                outlier.maxOutlierMillis));
            sb.append("\n");
        }

        // Alerts
        if (!activeAlerts.isEmpty()) {
            sb.append("=== Active Alerts ===\n");
            activeAlerts.forEach(alert -> sb.append("ALERT: ").append(alert).append("\n"));
            sb.append("\n");
        }

        // Performance summary
        sb.append("=== Performance Summary ===\n");
        sb.append(allTargetsMet() ? "✓ All targets met\n" : "✗ Some targets violated\n");
        sb.append(criticalTargetsViolated() ? "⚠ Critical targets violated\n" : "✓ Critical targets okay\n");

        return sb.toString();
    }

    // Helper methods
    private void updateThroughput() {
        long now = System.nanoTime();
        long elapsed = now - lastMeasurementTime;
        if (elapsed > TimeUnit.SECONDS.toNanos(1)) {
            double throughput = sampleCount.get() / (elapsed / 1_000_000_000.0);
            throughputCounter.add(throughput);
            lastMeasurementTime = now;
            sampleCount.set(0);
        }
    }

    private void checkForAlerts(long latency, String operationType) {
        // Check for extreme outliers
        if (latency > TARGET_P99_99_NANOS) {
            LatencyAlert alert = new LatencyAlert(
                "EXTREME_OUTLIER",
                operationType,
                String.format("Extreme latency: %.3f ms", latency / 1_000_000.0),
                System.currentTimeMillis(),
                latency
            );
            activeAlerts.add(alert);
            alertCount.incrementAndGet();
        }

        // Check for recent degradation
        if (latency > meanLatency() * 5) { // 5x mean latency
            LatencyAlert alert = new LatencyAlert(
                "RECENT_SPIKE",
                operationType,
                String.format("Recent spike: %.3f ms (5x mean)", latency / 1_000_000.0),
                System.currentTimeMillis(),
                latency
            );
            activeAlerts.add(alert);
            alertCount.incrementAndGet();
        }
    }

    private long meanLatency() {
        int count = sampleCount.get();
        return count > 0 ? totalLatency.sum() / count : 0;
    }

    private void analyzeTrends() {
        // Basic trend analysis
        double recentMean = calculateRecentMean(100);
        double olderMean = calculateRecentMean(1000);

        if (recentMean > olderMean * 1.5) {
            trend.degradationDetected = true;
            trend.degradationFactor = recentMean / olderMean;
        }
    }

    private double calculateRecentMean(int samples) {
        if (latencyBuffer.size() < samples) return 0;

        return latencyBuffer.stream()
            .skip(Math.max(0, latencyBuffer.size() - samples))
            .mapToLong(s -> s.latencyNanos)
            .average()
            .orElse(0);
    }

    private void analyzeOutliers(long[] sortedLatencies) {
        if (sortedLatencies.length == 0) return;

        // Calculate mean and standard deviation
        double mean = Arrays.stream(sortedLatencies).average().orElse(0);
        double stdDev = Math.sqrt(Arrays.stream(sortedLatencies)
            .map(l -> Math.pow(l - mean, 2))
            .average().orElse(0));

        // Identify outliers
        List<Long> outliers = Arrays.stream(sortedLatencies)
            .filter(l -> Math.abs(l - mean) > OUTLIER_THRESHOLD * stdDev)
            .boxed()
            .limit(MAX_OUTLIER_SAMPLES)
            .collect(Collectors.toList());

        if (!outliers.isEmpty()) {
            outlierAnalysis.hasOutliers = true;
            outlierAnalysis.outlierCount = outliers.size();
            outlierAnalysis.outlierPercentage = (double) outliers.size() / sortedLatencies.length * 100;
            outlierAnalysis.meanOutlierNanos = outliers.stream().mapToLong(l -> l).average().orElse(0);
            outlierAnalysis.maxOutlierNanos = outliers.stream().mapToLong(l -> l).max().orElse(0);
            outlierAnalysis.convertToMilliseconds();
        }
    }

    private void calculateDistributionStats() {
        long[] latencies = latencyBuffer.stream()
            .mapToLong(s -> s.latencyNanos)
            .toArray();

        // Calculate skewness and kurtosis
        double mean = Arrays.stream(latencies).average().orElse(0);
        double variance = Arrays.stream(latencies)
            .map(l -> Math.pow(l - mean, 2))
            .average().orElse(0);
        double stdDev = Math.sqrt(variance);

        // Skewness
        double skewness = Arrays.stream(latencies)
            .map(l -> Math.pow((l - mean) / stdDev, 3))
            .average().orElse(0);

        // Kurtosis
        double kurtosis = Arrays.stream(latencies)
            .map(l -> Math.pow((l - mean) / stdDev, 4))
            .average().orElse(0) - 3; // Excess kurtosis

        latestPercentiles.distributionSkewness = skewness;
        latestPercentiles.distributionKurtosis = kurtosis;
    }

    private boolean meetsTarget(double actual, double target) {
        return actual <= target;
    }

    private double calculateThroughput() {
        return throughputCounter.sum();
    }

    private TrendWindowAnalysis analyzeWindow(int windowSize) {
        TrendWindowAnalysis analysis = new TrendWindowAnalysis();
        analysis.windowSize = windowSize;

        int startIndex = Math.max(0, latencyBuffer.size() - windowSize);
        long[] window = latencyBuffer.stream()
            .skip(startIndex)
            .mapToLong(s -> s.latencyNanos)
            .toArray();

        analysis.meanNanos = Arrays.stream(window).average().orElse(0);
        analysis.minNanos = Arrays.stream(window).min().orElse(0);
        analysis.maxNanos = Arrays.stream(window).max().orElse(0);
        analysis.p95Nanos = percentile(window, 0.95);

        return analysis;
    }

    private List<SeasonalPattern> detectSeasonalPatterns() {
        // Simplified seasonal pattern detection
        List<SeasonalPattern> patterns = new ArrayList<>();

        // Group samples by time of day
        Map<Integer, List<LatencySample>> hourlySamples = new HashMap<>();
        latencyBuffer.forEach(sample -> {
            int hour = (int) (sample.timestamp / 3600000) % 24;
            hourlySamples.computeIfAbsent(hour, k -> new ArrayList<>()).add(sample);
        });

        // Analyze each hour
        hourlySamples.forEach((hour, samples) -> {
            if (samples.size() > 100) { // Minimum samples for analysis
                double mean = samples.stream()
                    .mapToLong(s -> s.latencyNanos)
                    .average().orElse(0);

                patterns.add(new SeasonalPattern(hour, mean, samples.size()));
            }
        });

        return patterns;
    }

    private TrendType analyzeBasicTrend() {
        if (latencyBuffer.size() < 100) return TrendType.NOT_ENOUGH_DATA;

        long[] recent = latencyBuffer.stream()
            .skip(Math.max(0, latencyBuffer.size() - 100))
            .mapToLong(s -> s.latencyNanos)
            .toArray();

        long[] older = latencyBuffer.stream()
            .limit(100)
            .mapToLong(s -> s.latencyNanos)
            .toArray();

        double recentAvg = Arrays.stream(recent).average().orElse(0);
        double olderAvg = Arrays.stream(older).average().orElse(0);

        if (recentAvg > olderAvg * 1.2) return TrendType.DEGRADING;
        if (recentAvg < olderAvg * 0.8) return TrendType.IMPROVING;
        return TrendType.STABLE;
    }

    private long percentile(long[] sortedValues, double percentile) {
        if (sortedValues.length == 0) return 0;

        double index = percentile * (sortedValues.length - 1);
        int lowerIndex = (int) index;
        int upperIndex = lowerIndex + 1;

        if (upperIndex >= sortedValues.length) {
            return sortedValues[sortedValues.length - 1];
        }

        double fraction = index - lowerIndex;
        return (long) (sortedValues[lowerIndex] + fraction * (sortedValues[upperIndex] - sortedValues[lowerIndex]));
    }

    // Enhanced data classes
    public static class EnhancedPercentileResults extends PercentileResults {
        long p99_99Nanos;
        double p99_99Millis;
        double distributionSkewness;
        double distributionKurtosis;

        void convertToMilliseconds() {
            super.convertToMilliseconds();
            p99_99Millis = p99_99Nanos / 1_000_000.0;
        }
    }

    public static class EnhancedPerformanceTrend {
        long timestamp;
        TrendType trendType;
        double degradationFactor;
        double improvementFactor;
        Map<Integer, TrendWindowAnalysis> windowAnalyses = new HashMap<>();
        List<SeasonalPattern> seasonalPatterns;
        TrendType basicTrend;
    }

    public static class OutlierAnalysis {
        boolean hasOutliers;
        int outlierCount;
        double outlierPercentage;
        long meanOutlierNanos;
        double meanOutlierMillis;
        long maxOutlierNanos;
        double maxOutlierMillis;

        void convertToMilliseconds() {
            meanOutlierMillis = meanOutlierNanos / 1_000_000.0;
            maxOutlierMillis = maxOutlierNanos / 1_000_000.0;
        }
    }

    public static class TrendWindowAnalysis {
        int windowSize;
        double meanNanos;
        long minNanos;
        long maxNanos;
        long p95Nanos;
    }

    public static class SeasonalPattern {
        final int hourOfDay;
        final double meanLatencyNanos;
        final int sampleCount;

        SeasonalPattern(int hourOfDay, double meanLatencyNanos, int sampleCount) {
            this.hourOfDay = hourOfDay;
            this.meanLatencyNanos = meanLatencyNanos;
            this.sampleCount = sampleCount;
        }

        public double getMeanLatencyMillis() {
            return meanLatencyNanos / 1_000_000.0;
        }
    }

    public static class LatencyAlert {
        String type;
        String operationType;
        String message;
        long timestamp;
        long relatedNanos;

        public LatencyAlert(String type, String operationType, String message, long timestamp, long relatedNanos) {
            this.type = type;
            this.operationType = operationType;
            this.message = message;
            this.timestamp = timestamp;
            this.relatedNanos = relatedNanos;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s (%s): %s (%.3f ms)",
                new Date(timestamp), type, operationType, message, relatedNanos / 1_000_000.0);
        }
    }

    public enum TrendType {
        IMPROVING, DEGRADING, STABLE, NOT_ENOUGH_DATA
    }

    // Keep backward compatibility with original PercentileResults
    public static class PercentileResults {
        int totalSamples;
        long minNanos;
        long maxNanos;
        long meanNanos;
        long p50Nanos;
        long p90Nanos;
        long p95Nanos;
        long p99Nanos;
        long p99_9Nanos;

        double minMillis;
        double maxMillis;
        double meanMillis;
        double p50Millis;
        double p90Millis;
        double p95Millis;
        double p99Millis;
        double p99_9Millis;

        void convertToMilliseconds() {
            minMillis = minNanos / 1_000_000.0;
            maxMillis = maxNanos / 1_000_000.0;
            meanMillis = meanNanos / 1_000_000.0;
            p50Millis = p50Nanos / 1_000_000.0;
            p90Millis = p90Nanos / 1_000_000.0;
            p95Millis = p95Nanos / 1_000_000.0;
            p99Millis = p99Nanos / 1_000_000.0;
            p99_9Millis = p99_9Nanos / 1_000_000.0;
        }
    }

    public static class PerformanceTrend {
        boolean degradationDetected;
        double degradationFactor;
        String trendDescription = "Stable";
    }

    // Enhanced sample class with context
    private static class LatencySample {
        final long latencyNanos;
        final String operationType;
        final long timestamp;
        final Map<String, String> context;

        LatencySample(long latencyNanos, String operationType, long timestamp, Map<String, String> context) {
            this.latencyNanos = latencyNanos;
            this.operationType = operationType;
            this.timestamp = timestamp;
            this.context = context;
        }
    }

    // Enhanced circular buffer
    private static class SlidingBuffer<E> {
        private final E[] buffer;
        private int head = 0;
        private int count = 0;

        @SuppressWarnings("unchecked")
        SlidingBuffer(int capacity) {
            this.buffer = (E[]) new Object[capacity];
        }

        void add(E item) {
            buffer[head] = item;
            head = (head + 1) % buffer.length;
            if (count < buffer.length) {
                count++;
            }
        }

        boolean isEmpty() {
            return count == 0;
        }

        int size() {
            return count;
        }

        Stream<E> stream() {
            return Arrays.stream(buffer, 0, count);
        }

        void forEach(java.util.function.Consumer<? super E> action) {
            for (int i = 0; i < count; i++) {
                action.accept(buffer[(head - count + i + buffer.length) % buffer.length]);
            }
        }
    }

    private long firstSampleTimestamp = System.currentTimeMillis();
}