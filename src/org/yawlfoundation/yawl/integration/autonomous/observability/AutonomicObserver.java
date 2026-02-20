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

package org.yawlfoundation.yawl.integration.autonomous.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Observability pipeline for autonomous workflow execution telemetry.
 *
 * <p>Collects structured telemetry (spans, metrics, health scores) from
 * autonomous agents and provides sliding-window aggregation for real-time
 * health scoring used by the autonomic control loop.</p>
 *
 * <h2>Telemetry Types</h2>
 * <ul>
 *   <li>{@link SpanEvent}: Distributed tracing spans</li>
 *   <li>{@link MetricSample}: Numeric metric observations</li>
 *   <li>{@link HealthScore}: Computed component health (0.0 to 1.0)</li>
 * </ul>
 *
 * <h2>Health Score Computation</h2>
 * <p>Weighted average: error_rate (0.4) + latency_p99 (0.3) + throughput_deviation (0.3)</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class AutonomicObserver {

    private static final Logger LOG = LogManager.getLogger(AutonomicObserver.class);

    /**
     * Status of a traced span.
     */
    public enum SpanStatus { OK, ERROR, TIMEOUT }

    /**
     * Telemetry event types.
     */
    public sealed interface TelemetryEvent {
        Instant timestamp();
    }

    /**
     * A distributed tracing span.
     */
    public record SpanEvent(
        String traceId,
        String spanId,
        String operation,
        Instant start,
        Duration duration,
        SpanStatus status,
        Map<String, String> attributes,
        Instant timestamp
    ) implements TelemetryEvent {
        public SpanEvent {
            Objects.requireNonNull(traceId);
            Objects.requireNonNull(spanId);
            Objects.requireNonNull(operation);
            Objects.requireNonNull(status);
            attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
            if (timestamp == null) timestamp = Instant.now();
        }
    }

    /**
     * A numeric metric observation.
     */
    public record MetricSample(
        String name,
        double value,
        Instant timestamp,
        Map<String, String> labels
    ) implements TelemetryEvent {
        public MetricSample {
            Objects.requireNonNull(name);
            labels = labels != null ? Map.copyOf(labels) : Map.of();
            if (timestamp == null) timestamp = Instant.now();
        }
    }

    /**
     * Computed health score for a component.
     */
    public record HealthScore(
        String componentId,
        double score,
        Instant computedAt,
        Map<String, Double> factors
    ) implements TelemetryEvent {
        public Instant timestamp() { return computedAt; }

        public HealthScore {
            Objects.requireNonNull(componentId);
            if (score < 0.0 || score > 1.0) throw new IllegalArgumentException("score must be in [0.0, 1.0]");
            factors = factors != null ? Map.copyOf(factors) : Map.of();
            if (computedAt == null) computedAt = Instant.now();
        }
    }

    /**
     * Sliding window for aggregating metric data over time.
     */
    private static final class SlidingWindow {
        private final ReentrantLock lock = new ReentrantLock();
        private final long[] timestamps;
        private final double[] values;
        private int head;
        private int size;

        SlidingWindow(int capacity) {
            this.timestamps = new long[capacity];
            this.values = new double[capacity];
        }

        void add(double value, Instant timestamp) {
            lock.lock();
            try {
                timestamps[head] = timestamp.toEpochMilli();
                values[head] = value;
                head = (head + 1) % timestamps.length;
                if (size < timestamps.length) size++;
            } finally {
                lock.unlock();
            }
        }

        double average(Duration window) {
            lock.lock();
            try {
                long cutoff = Instant.now().minus(window).toEpochMilli();
                double sum = 0;
                int count = 0;
                for (int i = 0; i < size; i++) {
                    int idx = (head - 1 - i + timestamps.length) % timestamps.length;
                    if (timestamps[idx] >= cutoff) {
                        sum += values[idx];
                        count++;
                    }
                }
                return count > 0 ? sum / count : 0.0;
            } finally {
                lock.unlock();
            }
        }

        double percentile(Duration window, double p) {
            lock.lock();
            try {
                long cutoff = Instant.now().minus(window).toEpochMilli();
                double[] windowValues = new double[size];
                int count = 0;
                for (int i = 0; i < size; i++) {
                    int idx = (head - 1 - i + timestamps.length) % timestamps.length;
                    if (timestamps[idx] >= cutoff) {
                        windowValues[count++] = values[idx];
                    }
                }
                if (count == 0) return 0.0;
                double[] sorted = Arrays.copyOf(windowValues, count);
                Arrays.sort(sorted);
                int index = Math.min((int) Math.ceil(p / 100.0 * count) - 1, count - 1);
                return sorted[Math.max(0, index)];
            } finally {
                lock.unlock();
            }
        }

        int count(Duration window) {
            lock.lock();
            try {
                long cutoff = Instant.now().minus(window).toEpochMilli();
                int count = 0;
                for (int i = 0; i < size; i++) {
                    int idx = (head - 1 - i + timestamps.length) % timestamps.length;
                    if (timestamps[idx] >= cutoff) count++;
                }
                return count;
            } finally {
                lock.unlock();
            }
        }
    }

    private static final int WINDOW_CAPACITY = 10_000;
    private static final Duration DEFAULT_WINDOW = Duration.ofSeconds(60);

    private static final double HEALTH_WEIGHT_ERROR_RATE = 0.4;
    private static final double HEALTH_WEIGHT_LATENCY = 0.3;
    private static final double HEALTH_WEIGHT_THROUGHPUT = 0.3;

    private final Map<String, SlidingWindow> latencyWindows = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindow> errorWindows = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindow> throughputWindows = new ConcurrentHashMap<>();
    private final Map<String, Double> baselineThroughput = new ConcurrentHashMap<>();
    private final AtomicLong totalEvents = new AtomicLong();
    private volatile Consumer<TelemetryEvent> eventSink;

    /**
     * Records a span event from an agent execution.
     */
    public void recordSpan(SpanEvent span) {
        totalEvents.incrementAndGet();

        String component = span.attributes().getOrDefault("agent.id", span.operation());

        // Track latency
        latencyWindows.computeIfAbsent(component, _ -> new SlidingWindow(WINDOW_CAPACITY))
            .add(span.duration().toMillis(), span.timestamp());

        // Track error rate (1.0 for error, 0.0 for success)
        double errorValue = span.status() == SpanStatus.OK ? 0.0 : 1.0;
        errorWindows.computeIfAbsent(component, _ -> new SlidingWindow(WINDOW_CAPACITY))
            .add(errorValue, span.timestamp());

        // Track throughput
        throughputWindows.computeIfAbsent(component, _ -> new SlidingWindow(WINDOW_CAPACITY))
            .add(1.0, span.timestamp());

        if (eventSink != null) eventSink.accept(span);

        LOG.trace("Recorded span: {} {} [{}/{}ms]",
                  span.operation(), span.status(), component, span.duration().toMillis());
    }

    /**
     * Records a metric sample.
     */
    public void recordMetric(MetricSample sample) {
        totalEvents.incrementAndGet();
        if (eventSink != null) eventSink.accept(sample);
    }

    /**
     * Computes the health score for a component.
     *
     * @param componentId the component to score
     * @return the computed health score
     */
    public HealthScore computeHealth(String componentId) {
        SlidingWindow errors = errorWindows.get(componentId);
        SlidingWindow latencies = latencyWindows.get(componentId);
        SlidingWindow throughput = throughputWindows.get(componentId);

        double errorRate = errors != null ? errors.average(DEFAULT_WINDOW) : 0.0;
        double latencyP99 = latencies != null ? latencies.percentile(DEFAULT_WINDOW, 99) : 0.0;
        int currentThroughput = throughput != null ? throughput.count(DEFAULT_WINDOW) : 0;

        // Normalize: lower error rate = better, lower latency = better
        double errorScore = 1.0 - errorRate;
        double latencyScore = Math.max(0.0, 1.0 - (latencyP99 / 30_000.0)); // 30s reference max
        double throughputScore = computeThroughputScore(componentId, currentThroughput);

        double healthScore = errorScore * HEALTH_WEIGHT_ERROR_RATE
                           + latencyScore * HEALTH_WEIGHT_LATENCY
                           + throughputScore * HEALTH_WEIGHT_THROUGHPUT;

        healthScore = Math.max(0.0, Math.min(1.0, healthScore));

        HealthScore result = new HealthScore(componentId, healthScore, Instant.now(), Map.of(
            "errorRate", errorRate,
            "latencyP99Ms", latencyP99,
            "throughput", (double) currentThroughput,
            "errorScore", errorScore,
            "latencyScore", latencyScore,
            "throughputScore", throughputScore
        ));

        if (eventSink != null) eventSink.accept(result);

        return result;
    }

    private double computeThroughputScore(String componentId, int current) {
        Double baseline = baselineThroughput.get(componentId);
        if (baseline == null || baseline <= 0) {
            baselineThroughput.put(componentId, (double) Math.max(current, 1));
            return 1.0;
        }
        double deviation = Math.abs(current - baseline) / baseline;
        return Math.max(0.0, 1.0 - deviation);
    }

    /**
     * Sets the baseline throughput for a component.
     */
    public void setBaselineThroughput(String componentId, double baseline) {
        baselineThroughput.put(componentId, baseline);
    }

    /**
     * Sets an external event sink for forwarding telemetry.
     */
    public void setEventSink(Consumer<TelemetryEvent> sink) {
        this.eventSink = sink;
    }

    /**
     * Returns the total number of events processed.
     */
    public long getTotalEvents() { return totalEvents.get(); }

    /**
     * Returns the set of known component IDs.
     */
    public java.util.Set<String> getKnownComponents() {
        java.util.Set<String> components = ConcurrentHashMap.newKeySet();
        components.addAll(errorWindows.keySet());
        components.addAll(latencyWindows.keySet());
        return components;
    }
}
