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

package org.yawlfoundation.yawl.engine.actor.validation;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;

/**
 * Latency metrics collection and analysis for scheduling performance.
 * Provides precise percentile measurements and performance analysis.
 *
 * <p>Features:
 * - High-precision nanosecond latency tracking
 * - Comprehensive percentile calculation (p50, p90, p95, p99, p99.9)
 * - Sliding window for continuous measurement
 * - Performance degradation detection
 * - Comparison with targets
 *
 * <p>Targets:
 * - p50 < 100μs
 * - p90 < 500μs
 * - p95 < 1ms
 * - p99 < 2ms
 * - p99.9 < 5ms
 */
public class LatencyMetrics {

    // Configuration
    private static final int DEFAULT_SAMPLE_SIZE = 1_000_000;
    private static final int MAX_SAMPLES_PER_WINDOW = 10_000;
    
    // Data storage
    private final CircularBuffer<LatencySample> latencyBuffer;
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

    public LatencyMetrics() {
        this(DEFAULT_SAMPLE_SIZE);
    }

    public LatencyMetrics(int sampleSize) {
        this.latencyBuffer = new CircularBuffer<>(Math.min(sampleSize, MAX_SAMPLES_PER_WINDOW));
    }

    /**
     * Record a latency measurement.
     */
    public void recordLatency(long startTimeNanos, String operationType) {
        long latency = System.nanoTime() - startTimeNanos;
        
        // Update basic statistics
        totalLatency.add(latency);
        sampleCount.incrementAndGet();
        
        // Update min/max
        minLatency.updateAndGet(m -> Math.min(m, latency));
        maxLatency.updateAndGet(m -> Math.max(m, latency));
        
        // Store for percentile calculation
        LatencySample sample = new LatencySample(latency, operationType, System.currentTimeMillis());
        latencyBuffer.add(sample);
        
        // Update throughput
        long now = System.nanoTime();
        long elapsed = now - lastMeasurementTime;
        if (elapsed > TimeUnit.SECONDS.toNanos(1)) {
            double throughput = sampleCount.get() / (elapsed / 1_000_000_000.0);
            throughputCounter.add(throughput);
            lastMeasurementTime = now;
            sampleCount.set(0);
        }
    }

    /**
     * Calculate percentiles from collected data.
     */
    public PercentileResults calculatePercentiles() {
        if (latencyBuffer.isEmpty()) {
            return new PercentileResults();
        }
        
        // Extract all latencies
        long[] latencies = latencyBuffer.stream()
            .mapToLong(s -> s.latencyNanos)
            .sorted()
            .toArray();
        
        latestPercentiles = new PercentileResults();
        latestPercentiles.totalSamples = latencies.length;
        latestPercentiles.minNanos = minLatency.get();
        latestPercentiles.maxNanos = maxLatency.get();
        latestPercentiles.meanNanos = totalLatency.sum() / latencies.length;
        
        // Calculate percentiles
        latestPercentiles.p50Nanos = percentile(latencies, 0.50);
        latestPercentiles.p90Nanos = percentile(latencies, 0.90);
        latestPercentiles.p95Nanos = percentile(latencies, 0.95);
        latestPercentiles.p99Nanos = percentile(latencies, 0.99);
        latestPercentiles.p99_9Nanos = percentile(latencies, 0.999);
        
        // Convert to milliseconds for readability
        latestPercentiles.convertToMilliseconds();
        
        return latestPercentiles;
    }

    /**
     * Check if targets are met.
     */
    public boolean targetsMet() {
        if (latestPercentiles == null) {
            return true; // No data, assume targets are met
        }
        
        // Check each target
        boolean p50Ok = latestPercentiles.p50Millis <= 0.1; // 100μs
        boolean p90Ok = latestPercentiles.p90Millis <= 0.5; // 500μs
        boolean p95Ok = latestPercentiles.p95Millis <= 1.0; // 1ms
        boolean p99Ok = latestPercentiles.p99Millis <= 2.0; // 2ms
        boolean p99_9Ok = latestPercentiles.p99_9Millis <= 5.0; // 5ms
        
        return p50Ok && p90Ok && p95Ok && p99Ok && p99_9Ok;
    }

    /**
     * Analyze performance trends.
     */
    public PerformanceTrend analyzeTrend() {
        // Simple trend analysis - in production would use sliding windows
        if (latencyBuffer.size() < 100) {
            return trend;
        }
        
        // Check for recent degradation
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
        
        if (recentAvg > olderAvg * 1.5) { // 50% degradation
            trend.degradationDetected = true;
            trend.degradationFactor = recentAvg / olderAvg;
        }
        
        return trend;
    }

    /**
     * Get detailed report.
     */
    public String generateReport() {
        PercentileResults results = calculatePercentiles();
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== Latency Metrics Report ===\n");
        sb.append(String.format("Total samples: %,d\n", results.totalSamples));
        sb.append(String.format("Min latency: %.3f ms\n", results.minMillis));
        sb.append(String.format("Max latency: %.3f ms\n", results.maxMillis));
        sb.append(String.format("Mean latency: %.3f ms\n", results.meanMillis));
        sb.append("\n");
        sb.append("=== Percentiles ===\n");
        sb.append(String.format("p50:   %7.3f ms%s\n", results.p50Millis, 
            results.p50Millis <= 0.1 ? " ✓" : ""));
        sb.append(String.format("p90:   %7.3f ms%s\n", results.p90Millis, 
            results.p90Millis <= 0.5 ? " ✓" : ""));
        sb.append(String.format("p95:   %7.3f ms%s\n", results.p95Millis, 
            results.p95Millis <= 1.0 ? " ✓" : ""));
        sb.append(String.format("p99:   %7.3f ms%s\n", results.p99Millis, 
            results.p99Millis <= 2.0 ? " ✓" : ""));
        sb.append(String.format("p99.9: %7.3f ms%s\n", results.p99_9Millis, 
            results.p99_9Millis <= 5.0 ? " ✓" : ""));
        sb.append("\n");
        
        if (!targetsMet()) {
            sb.append("WARNING: Some latency targets not met!\n");
        }
        
        return sb.toString();
    }

    // Helper methods
    private long percentile(long[] sortedValues, double percentile) {
        double index = percentile * (sortedValues.length - 1);
        int lowerIndex = (int) index;
        int upperIndex = lowerIndex + 1;
        
        if (upperIndex >= sortedValues.length) {
            return sortedValues[sortedValues.length - 1];
        }
        
        double fraction = index - lowerIndex;
        return (long) (sortedValues[lowerIndex] + 
                       fraction * (sortedValues[upperIndex] - sortedValues[lowerIndex]));
    }

    // Data classes
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

    private static class LatencySample {
        final long latencyNanos;
        final String operationType;
        final long timestamp;
        
        LatencySample(long latencyNanos, String operationType, long timestamp) {
            this.latencyNanos = latencyNanos;
            this.operationType = operationType;
            this.timestamp = timestamp;
        }
    }

    /**
     * Circular buffer implementation for efficient sliding window.
     */
    private static class CircularBuffer<E> {
        private final E[] buffer;
        private int head = 0;
        private int count = 0;
        
        @SuppressWarnings("unchecked")
        CircularBuffer(int capacity) {
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
    }
}
