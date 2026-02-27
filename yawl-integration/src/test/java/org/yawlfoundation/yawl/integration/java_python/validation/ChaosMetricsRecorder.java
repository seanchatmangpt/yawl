/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This file is part of YAWL - Yet Another Workflow Language.
 *
 * YAWL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.java_python.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics recorder for chaos engineering tests
 *
 * Captures and records various metrics during chaos tests
 */
public class ChaosMetricsRecorder {

    private static final Logger logger = LoggerFactory.getLogger(ChaosMetricsRecorder.class);
    private static final String METRICS_FILE = "target/chaos-metrics.log";

    private final Map<String, AtomicLong> counters;
    private final Map<String, AtomicLong> timers;
    private final Map<String, AtomicLong> gauges;

    private static ChaosMetricsRecorder instance;

    private ChaosMetricsRecorder() {
        this.counters = new ConcurrentHashMap<>();
        this.timers = new ConcurrentHashMap<>();
        this.gauges = new ConcurrentHashMap<>();
    }

    public static synchronized ChaosMetricsRecorder getInstance() {
        if (instance == null) {
            instance = new ChaosMetricsRecorder();
        }
        return instance;
    }

    /**
     * Increment a counter metric
     */
    public void increment(String name) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Increment a counter metric by a specific value
     */
    public void increment(String name, long value) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(value);
    }

    /**
     * Set a gauge metric
     */
    public void setGauge(String name, long value) {
        gauges.computeIfAbsent(name, k -> new AtomicLong(0)).set(value);
    }

    /**
     * Record a timer measurement
     */
    public void recordTimer(String name, long durationMs) {
        timers.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(durationMs);
    }

    /**
     * Get a counter value
     */
    public long getCounter(String name) {
        AtomicLong counter = counters.get(name);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Get a gauge value
     */
    public long getGauge(String name) {
        AtomicLong gauge = gauges.get(name);
        return gauge != null ? gauge.get() : 0;
    }

    /**
     * Get average timer value
     */
    public double getAverageTimer(String name) {
        AtomicLong timer = timers.get(name);
        if (timer == null) return 0.0;

        // Count how many times this timer was recorded
        long count = getCounter(name + "_count");
        return count > 0 ? (double) timer.get() / count : 0.0;
    }

    /**
     * Reset all metrics
     */
    public void reset() {
        counters.clear();
        timers.clear();
        gauges.clear();
    }

    /**
     * Log current metrics
     */
    public void logMetrics() {
        logger.info("=== Chaos Metrics ===");

        // Log counters
        counters.forEach((name, value) ->
            logger.info("Counter {}: {}", name, value.get()));

        // Log gauges
        gauges.forEach((name, value) ->
            logger.info("Gauge {}: {}", name, value.get()));

        // Log timers (averages)
        timers.forEach((name, value) -> {
            double avg = getAverageTimer(name);
            logger.info("Timer {} (avg): {} ms", name, avg);
        });
    }

    /**
     * Write metrics to file
     */
    public void writeMetricsToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(METRICS_FILE, true))) {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            writer.println("=== Chaos Metrics at " + now.format(formatter) + " ===");

            // Write counters
            writer.println("Counters:");
            counters.forEach((name, value) ->
                writer.println("  " + name + ": " + value.get()));

            // Write gauges
            writer.println("Gauges:");
            gauges.forEach((name, value) ->
                writer.println("  " + name + ": " + value.get()));

            // Write timers
            writer.println("Timers (average in ms):");
            timers.forEach((name, value) -> {
                double avg = getAverageTimer(name);
                writer.println("  " + name + ": " + avg);
            });

            writer.println();

        } catch (IOException e) {
            logger.error("Failed to write metrics to file: " + e.getMessage());
        }
    }

    /**
     * Common metric names
     */
    public static final class Metrics {
        // Circuit Breaker
        public static final String CIRCUIT_BREAKER_TRIPS = "circuit_breaker_trips";
        public static final String CIRCUIT_BREAKER_RESETS = "circuit_breaker_resets";

        // Failures
        public static final String TRANSIENT_FAILURES = "transient_failures";
        public static final String PERSISTENT_FAILURES = "persistent_failures";
        public static final String TIMEOUT_FAILURES = "timeout_failures";

        // Work Items
        public static final String WORK_ITEMS_SUBMITTED = "work_items_submitted";
        public static final String WORK_ITEMS_SUCCESS = "work_items_success";
        public static final String WORK_ITEMS_FAILED = "work_items_failed";
        public static final String WORK_ITEMS_RECOVERED = "work_items_recovered";

        // Performance
        public static final String RESPONSE_TIME_MS = "response_time_ms";
        public static final String THREAD_COUNT = "thread_count";
        public static final String MEMORY_USAGE_MB = "memory_usage_mb";

        // Resource Usage
        public static final String CPU_USAGE_PERCENT = "cpu_usage_percent";
        public static final String ACTIVE_CONNECTIONS = "active_connections";

        // Degradation
        public static final String DEGRADATION_MODE_ENTERED = "degradation_mode_entered";
        public static final String DEGRADATION_MODE_EXITED = "degradation_mode_exited";
    }
}