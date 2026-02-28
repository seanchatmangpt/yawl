/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.dspy.performance;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance metrics tracking for GEPA optimization operations.
 *
 * <p>Provides fine-grained performance tracking with automatic threshold enforcement.
 * Critical metrics are tracked with p50/p95/p99 percentiles and real-time monitoring.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class GepaPerformanceMetrics {

    private static final Logger log = LoggerFactory.getLogger(GepaPerformanceMetrics.class);

    // Performance thresholds (milliseconds)
    private static final long OPTIMIZATION_P50_THRESHOLD_MS = 500;
    private static final long OPTIMIZATION_P95_THRESHOLD_MS = 2000;
    private static final long OPTIMIZATION_P99_THRESHOLD_MS = 5000;
    private static final long PYTHON_EXECUTION_THRESHOLD_MS = 3000;
    private static final long CACHE_RETRIEVAL_THRESHOLD_MS = 10;

    // Thread-safe metric collection
    private final Map<String, Histogram> optimizationLatencies = new ConcurrentHashMap<>();
    private final Map<String, Histogram> pythonExecutionTimes = new ConcurrentHashMap<>();
    private final Map<String, Histogram> cacheRetrievalTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> optimizationCounts = new ConcurrentHashMap<>();

    private final String optimizationTarget;

    /**
     * Creates performance metrics for a specific optimization target.
     */
    public GepaPerformanceMetrics(String optimizationTarget) {
        this.optimizationTarget = optimizationTarget;
        initializeMetrics();
    }

    /**
     * Records optimization latency.
     */
    public void recordOptimization(Instant start, Instant end) {
        long durationMs = Duration.between(start, end).toMillis();
        Histogram histogram = optimizationLatencies.computeIfAbsent(
                optimizationTarget, k -> new Histogram());
        histogram.recordValue(durationMs);
        
        optimizationCounts.computeIfAbsent(optimizationTarget, k -> new AtomicLong(0))
                         .incrementAndGet();

        // Check thresholds and log warnings
        if (durationMs > OPTIMIZATION_P99_THRESHOLD_MS) {
            log.warn("GEPA optimization ({}): P99 exceeded! Duration: {}ms", 
                    optimizationTarget, durationMs);
        } else if (durationMs > OPTIMIZATION_P95_THRESHOLD_MS) {
            log.info("GEPA optimization ({}): P95 latency: {}ms", 
                    optimizationTarget, durationMs);
        }
    }

    /**
     * Records Python execution time.
     */
    public void recordPythonExecution(Instant start, Instant end, String pythonCode) {
        long durationMs = Duration.between(start, end).toMillis();
        String codeHash = hashPythonCode(pythonCode);
        
        Histogram histogram = pythonExecutionTimes.computeIfAbsent(
                codeHash, k -> new Histogram());
        histogram.recordValue(durationMs);

        if (durationMs > PYTHON_EXECUTION_THRESHOLD_MS) {
            log.warn("Python execution slow: {}ms for hash {}", durationMs, codeHash);
        }
    }

    /**
     * Records cache retrieval time.
     */
    public void recordCacheRetrieval(Instant start, Instant end) {
        long durationMs = Duration.between(start, end).toMillis();
        Histogram histogram = cacheRetrievalTimes.computeIfAbsent(
                "global", k -> new Histogram());
        histogram.recordValue(durationMs);

        if (durationMs > CACHE_RETRIEVAL_THRESHOLD_MS) {
            log.warn("Cache retrieval slow: {}ms", durationMs);
        }
    }

    /**
     * Returns performance summary.
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new ConcurrentHashMap<>();
        
        summary.put("target", optimizationTarget);
        summary.put("optimization_count", optimizationCounts.getOrDefault(
                optimizationTarget, new AtomicLong(0)).get());
        
        summary.put("optimization_percentiles", getPercentiles(
                optimizationLatencies.get(optimizationTarget), "optimization"));
        summary.put("python_execution_percentiles", getPercentiles(
                getAggregatedPythonHistogram(), "python_execution"));
        summary.put("cache_retrieval_percentiles", getPercentiles(
                cacheRetrievalTimes.get("global"), "cache_retrieval"));
        
        summary.put("thresholds", Map.of(
                "optimization_p50_ms", OPTIMIZATION_P50_THRESHOLD_MS,
                "optimization_p95_ms", OPTIMIZATION_P95_THRESHOLD_MS,
                "optimization_p99_ms", OPTIMIZATION_P99_THRESHOLD_MS,
                "python_execution_ms", PYTHON_EXECUTION_THRESHOLD_MS,
                "cache_retrieval_ms", CACHE_RETRIEVAL_THRESHOLD_MS
        ));

        return Map.copyOf(summary);
    }

    /**
     * Resets all metrics.
     */
    public void reset() {
        optimizationLatencies.clear();
        pythonExecutionTimes.clear();
        cacheRetrievalTimes.clear();
        optimizationCounts.clear();
        log.info("GEPA performance metrics reset for target: {}", optimizationTarget);
    }

    private void initializeMetrics() {
        optimizationLatencies.putIfAbsent(optimizationTarget, new Histogram());
        cacheRetrievalTimes.putIfAbsent("global", new Histogram());
    }

    private Map<String, Object> getPercentiles(@Nullable Histogram histogram, String type) {
        if (histogram == null) {
            Map<String, Object> empty = new ConcurrentHashMap<>();
            empty.put("count", 0L);
            empty.put("p50_ms", 0L);
            empty.put("p95_ms", 0L);
            empty.put("p99_ms", 0L);
            return empty;
        }

        Map<String, Object> percentiles = new ConcurrentHashMap<>();
        percentiles.put("count", histogram.getCount());
        percentiles.put("p50_ms", histogram.getValueAtPercentile(50));
        percentiles.put("p95_ms", histogram.getValueAtPercentile(95));
        percentiles.put("p99_ms", histogram.getValueAtPercentile(99));
        return percentiles;
    }

    private Histogram getAggregatedPythonHistogram() {
        Histogram aggregated = new Histogram();
        pythonExecutionTimes.values().forEach(histogram -> {
            for (long value : histogram.getValues()) {
                aggregated.recordValue(value);
            }
        });
        return aggregated;
    }

    private String hashPythonCode(String pythonCode) {
        // Simple hash for performance tracking
        return Integer.toHexString(pythonCode.hashCode());
    }

    /**
     * Simple histogram implementation for percentile calculation.
     */
    private static class Histogram {
        private final Long[] values = new Long[1000]; // Fixed-size array
        private int count = 0;

        public void recordValue(long value) {
            if (count < values.length) {
                values[count++] = value;
            } else {
                // Handle array overflow
                System.arraycopy(values, 1, values, 0, values.length - 1);
                values[values.length - 1] = value;
            }
        }

        public long getCount() {
            return count;
        }

        public long[] getValues() {
            long[] result = new long[count];
            for (int i = 0; i < count; i++) {
                result[i] = values[i];
            }
            return result;
        }

        public long getValueAtPercentile(int percentile) {
            if (count == 0) return 0L;
            
            long[] sorted = getValues();
            java.util.Arrays.sort(sorted);
            
            int index = (int) ((percentile / 100.0) * (count - 1));
            return sorted[index];
        }
    }
}
