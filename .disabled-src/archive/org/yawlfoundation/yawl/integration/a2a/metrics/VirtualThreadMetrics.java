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

package org.yawlfoundation.yawl.integration.a2a.metrics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.DoubleSummaryStatistics;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Metrics collector for virtual thread usage in A2A server.
 *
 * <p>This class tracks key metrics for monitoring virtual thread behavior:</p>
 * <ul>
 *   <li><b>Request latency</b>: Distribution of request processing times</li>
 *   <li><b>Active requests</b>: Current number of in-flight requests</li>
 *   <li><b>Thread counts</b>: Platform vs virtual thread statistics</li>
 *   <li><b>Server uptime</b>: Time since server start</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * VirtualThreadMetrics metrics = new VirtualThreadMetrics();
 *
 * // Record request
 * long startTime = System.nanoTime();
 * metrics.recordRequestStart();
 * // ... process request ...
 * metrics.recordRequestComplete(System.nanoTime() - startTime);
 *
 * // Get snapshot
 * VirtualThreadMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
 * System.out.println("P99 latency: " + snapshot.p99LatencyMillis() + "ms");
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All methods are thread-safe and can be called from concurrent virtual threads.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public class VirtualThreadMetrics {

    private static final Logger _logger = LogManager.getLogger(VirtualThreadMetrics.class);

    // Maximum number of latency samples to retain
    private static final int MAX_LATENCY_SAMPLES = 10000;

    // Track request latencies (nanoseconds)
    private final ConcurrentLinkedDeque<Long> latencySamples = new ConcurrentLinkedDeque<>();

    // Counters
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final AtomicLong activeRequests = new AtomicLong(0);

    // Server state
    private volatile Instant serverStartTime;
    private volatile Instant serverStopTime;
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    /**
     * Record that the server has started.
     */
    public void recordServerStart() {
        this.serverStartTime = Instant.now();
        this.serverStopTime = null;
        _logger.info("Metrics collection started at {}", serverStartTime);
    }

    /**
     * Record that the server has stopped.
     */
    public void recordServerStop() {
        this.serverStopTime = Instant.now();
        _logger.info("Metrics collection stopped at {}", serverStopTime);
    }

    /**
     * Record the start of a request.
     */
    public void recordRequestStart() {
        activeRequests.incrementAndGet();
        totalRequests.increment();
    }

    /**
     * Record the completion of a request.
     *
     * @param latencyNanos request latency in nanoseconds
     */
    public void recordRequestComplete(long latencyNanos) {
        activeRequests.decrementAndGet();
        successfulRequests.increment();

        // Add latency sample (with bounded memory)
        latencySamples.addLast(latencyNanos);
        while (latencySamples.size() > MAX_LATENCY_SAMPLES) {
            latencySamples.removeFirst();
        }
    }

    /**
     * Record a failed request.
     */
    public void recordRequestFailure() {
        activeRequests.decrementAndGet();
        failedRequests.increment();
    }

    /**
     * Get a snapshot of current metrics.
     *
     * @return immutable snapshot of current metrics
     */
    public MetricsSnapshot getSnapshot() {
        // Calculate latency statistics
        DoubleSummaryStatistics stats = latencySamples.stream()
            .mapToDouble(Long::doubleValue)
            .summaryStatistics();

        double p50 = calculatePercentile(50);
        double p90 = calculatePercentile(90);
        double p95 = calculatePercentile(95);
        double p99 = calculatePercentile(99);

        // Get thread information
        int totalThreads = threadMXBean.getThreadCount();
        int daemonThreads = threadMXBean.getDaemonThreadCount();
        int peakThreads = threadMXBean.getPeakThreadCount();
        long totalStartedThreads = threadMXBean.getTotalStartedThreadCount();

        // Calculate uptime
        long uptimeSeconds = 0;
        if (serverStartTime != null) {
            Instant end = serverStopTime != null ? serverStopTime : Instant.now();
            uptimeSeconds = end.getEpochSecond() - serverStartTime.getEpochSecond();
        }

        return new MetricsSnapshot(
            totalRequests.sum(),
            successfulRequests.sum(),
            failedRequests.sum(),
            activeRequests.get(),
            stats.getCount(),
            stats.getAverage() / 1_000_000.0,  // Convert to milliseconds
            stats.getMin() / 1_000_000.0,
            stats.getMax() / 1_000_000.0,
            p50,
            p90,
            p95,
            p99,
            totalThreads,
            daemonThreads,
            peakThreads,
            totalStartedThreads,
            uptimeSeconds,
            serverStartTime,
            serverStopTime
        );
    }

    /**
     * Calculate the percentile value from latency samples.
     *
     * @param percentile the percentile to calculate (0-100)
     * @return the latency in milliseconds at the given percentile
     */
    private double calculatePercentile(int percentile) {
        if (latencySamples.isEmpty()) {
            return 0.0;
        }

        Long[] sorted = latencySamples.stream()
            .sorted()
            .toArray(Long[]::new);

        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        index = Math.max(0, Math.min(index, sorted.length - 1));

        return sorted[index] / 1_000_000.0;  // Convert to milliseconds
    }

    /**
     * Get a human-readable summary of current metrics.
     *
     * @return formatted summary string
     */
    public String getSummary() {
        MetricsSnapshot snapshot = getSnapshot();
        return String.format(
            "VirtualThreadMetrics{requests=%d, success=%d, failed=%d, active=%d, " +
            "p50=%.2fms, p99=%.2fms, threads=%d, uptime=%ds}",
            snapshot.totalRequests(),
            snapshot.successfulRequests(),
            snapshot.failedRequests(),
            snapshot.activeRequests(),
            snapshot.p50LatencyMillis(),
            snapshot.p99LatencyMillis(),
            snapshot.totalThreads(),
            snapshot.uptimeSeconds()
        );
    }

    /**
     * Export metrics as JSON for monitoring systems.
     *
     * @return JSON representation of current metrics
     */
    public String toJson() {
        MetricsSnapshot s = getSnapshot();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"requests\": {\n");
        sb.append("    \"total\": ").append(s.totalRequests()).append(",\n");
        sb.append("    \"successful\": ").append(s.successfulRequests()).append(",\n");
        sb.append("    \"failed\": ").append(s.failedRequests()).append(",\n");
        sb.append("    \"active\": ").append(s.activeRequests()).append("\n");
        sb.append("  },\n");
        sb.append("  \"latency\": {\n");
        sb.append("    \"samples\": ").append(s.latencySampleCount()).append(",\n");
        sb.append("    \"avg_ms\": ").append(String.format("%.3f", s.avgLatencyMillis())).append(",\n");
        sb.append("    \"min_ms\": ").append(String.format("%.3f", s.minLatencyMillis())).append(",\n");
        sb.append("    \"max_ms\": ").append(String.format("%.3f", s.maxLatencyMillis())).append(",\n");
        sb.append("    \"p50_ms\": ").append(String.format("%.3f", s.p50LatencyMillis())).append(",\n");
        sb.append("    \"p90_ms\": ").append(String.format("%.3f", s.p90LatencyMillis())).append(",\n");
        sb.append("    \"p95_ms\": ").append(String.format("%.3f", s.p95LatencyMillis())).append(",\n");
        sb.append("    \"p99_ms\": ").append(String.format("%.3f", s.p99LatencyMillis())).append("\n");
        sb.append("  },\n");
        sb.append("  \"threads\": {\n");
        sb.append("    \"total\": ").append(s.totalThreads()).append(",\n");
        sb.append("    \"daemon\": ").append(s.daemonThreads()).append(",\n");
        sb.append("    \"peak\": ").append(s.peakThreads()).append(",\n");
        sb.append("    \"total_started\": ").append(s.totalStartedThreads()).append("\n");
        sb.append("  },\n");
        sb.append("  \"server\": {\n");
        sb.append("    \"uptime_seconds\": ").append(s.uptimeSeconds()).append(",\n");
        sb.append("    \"start_time\": \"").append(s.serverStartTime()).append("\"");
        if (s.serverStopTime() != null) {
            sb.append(",\n    \"stop_time\": \"").append(s.serverStopTime()).append("\"");
        }
        sb.append("\n  }\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Immutable snapshot of metrics at a point in time.
     *
     * @param totalRequests total requests since server start
     * @param successfulRequests requests that completed successfully
     * @param failedRequests requests that failed
     * @param activeRequests currently in-flight requests
     * @param latencySampleCount number of latency samples collected
     * @param avgLatencyMillis average request latency in milliseconds
     * @param minLatencyMillis minimum request latency in milliseconds
     * @param maxLatencyMillis maximum request latency in milliseconds
     * @param p50LatencyMillis 50th percentile latency in milliseconds
     * @param p90LatencyMillis 90th percentile latency in milliseconds
     * @param p95LatencyMillis 95th percentile latency in milliseconds
     * @param p99LatencyMillis 99th percentile latency in milliseconds
     * @param totalThreads current total thread count
     * @param daemonThreads current daemon thread count
     * @param peakThreads peak thread count since JVM start
     * @param totalStartedThreads total threads started since JVM start
     * @param uptimeSeconds server uptime in seconds
     * @param serverStartTime time when server started
     * @param serverStopTime time when server stopped, null if running
     */
    public record MetricsSnapshot(
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        long activeRequests,
        long latencySampleCount,
        double avgLatencyMillis,
        double minLatencyMillis,
        double maxLatencyMillis,
        double p50LatencyMillis,
        double p90LatencyMillis,
        double p95LatencyMillis,
        double p99LatencyMillis,
        int totalThreads,
        int daemonThreads,
        int peakThreads,
        long totalStartedThreads,
        long uptimeSeconds,
        Instant serverStartTime,
        Instant serverStopTime
    ) {
        /**
         * Calculate requests per second based on uptime.
         *
         * @return requests per second, or 0 if uptime is 0
         */
        public double requestsPerSecond() {
            if (uptimeSeconds <= 0) {
                return 0.0;
            }
            return (double) totalRequests / uptimeSeconds;
        }

        /**
         * Calculate success rate as a percentage.
         *
         * @return success rate (0-100)
         */
        public double successRate() {
            if (totalRequests == 0) {
                return 100.0;
            }
            return (double) successfulRequests / totalRequests * 100.0;
        }
    }
}
