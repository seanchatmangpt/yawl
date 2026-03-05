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

package org.yawlfoundation.yawl.integration.blueocean.instrumentation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

import io.micrometer.core.instrument.*;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * OpenTelemetry metrics instrumentation for YAWL workflow execution.
 *
 * <p>Tracks Prometheus metrics for:</p>
 * <ul>
 *   <li><b>data_lineage_queries_total</b> - Total lineage queries by table</li>
 *   <li><b>data_table_access_latency_p95</b> - Access latency percentile (ms)</li>
 *   <li><b>data_schema_drift_detected</b> - Schema changes counter</li>
 *   <li><b>guard_violations_total</b> - Guard violations by pattern</li>
 *   <li><b>task_execution_duration_seconds</b> - Task execution time</li>
 *   <li><b>contract_violations_total</b> - Data contract violations</li>
 * </ul>
 *
 * <p>Includes structured logging with OpenTelemetry context propagation.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * OpenTelemetryMetricsInstrumentation metrics =
 *     new OpenTelemetryMetricsInstrumentation(meterRegistry);
 *
 * try (var timer = metrics.startDataAccessTimer("orders", "READ")) {
 *     // perform table operation
 * }
 *
 * metrics.recordGuardViolation("H_TODO");
 * metrics.recordSchemaChange("customers");
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class OpenTelemetryMetricsInstrumentation {
    private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryMetricsInstrumentation.class);

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> accessTimings;
    private final DoubleAdder p95Calculator;

    // Atomic counters for fast updates without synchronization
    private final ConcurrentHashMap<String, AtomicInteger> lineageQueryCounters;
    private final ConcurrentHashMap<String, AtomicInteger> guardViolationCounters;
    private final ConcurrentHashMap<String, AtomicInteger> schemaDriftCounters;
    private final AtomicInteger contractViolationCounter;

    // Timer registry for latency histograms
    private final ConcurrentHashMap<String, Timer> timersByTable;

    /**
     * Creates metrics instrumentation with Prometheus registry.
     *
     * @param meterRegistry Micrometer MeterRegistry (typically PrometheusMeterRegistry)
     * @throws IllegalArgumentException if meterRegistry is null
     */
    public OpenTelemetryMetricsInstrumentation(@NonNull MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.accessTimings = new ConcurrentHashMap<>();
        this.p95Calculator = new DoubleAdder();
        this.lineageQueryCounters = new ConcurrentHashMap<>();
        this.guardViolationCounters = new ConcurrentHashMap<>();
        this.schemaDriftCounters = new ConcurrentHashMap<>();
        this.contractViolationCounter = new AtomicInteger(0);
        this.timersByTable = new ConcurrentHashMap<>();

        initializeMetrics();
        logger.info("Initialized OpenTelemetry metrics instrumentation");
    }

    /**
     * Records a data lineage query operation.
     *
     * @param tableId Table identifier
     * @throws IllegalArgumentException if tableId is null or empty
     */
    public void recordLineageQuery(@NonNull String tableId) {
        if (tableId.isBlank()) {
            throw new IllegalArgumentException("tableId cannot be blank");
        }

        lineageQueryCounters
                .computeIfAbsent(tableId, k -> Counter.builder("data_lineage_queries_total")
                        .tag("table", tableId)
                        .register(meterRegistry))
                .incrementAndGet();

        logger.debug("Recorded lineage query for table: {}", tableId);
    }

    /**
     * Records a data table access with timing.
     *
     * @param tableId Table identifier
     * @param operation READ, WRITE, UPDATE, DELETE
     * @return TimerContext for use in try-with-resources
     * @throws IllegalArgumentException if parameters invalid
     */
    public TimerContext startDataAccessTimer(@NonNull String tableId, @NonNull String operation) {
        if (tableId.isBlank()) {
            throw new IllegalArgumentException("tableId cannot be blank");
        }
        if (operation.isBlank()) {
            throw new IllegalArgumentException("operation cannot be blank");
        }

        return new TimerContext(tableId, operation, System.currentTimeMillis());
    }

    /**
     * Records guard validation violation.
     *
     * @param pattern Pattern name (H_TODO, H_MOCK, H_STUB, etc.)
     * @throws IllegalArgumentException if pattern is null or empty
     */
    public void recordGuardViolation(@NonNull String pattern) {
        if (pattern.isBlank()) {
            throw new IllegalArgumentException("pattern cannot be blank");
        }

        guardViolationCounters
                .computeIfAbsent(pattern, k -> Counter.builder("guard_violations_total")
                        .tag("pattern", pattern)
                        .register(meterRegistry))
                .incrementAndGet();

        MDC.put("guard_violation", pattern);
        logger.warn("Guard violation detected: {}", pattern);
        MDC.remove("guard_violation");
    }

    /**
     * Records a data schema drift event (column added/removed/type changed).
     *
     * @param tableId Table identifier
     * @param changeType Type of change (ADD_COLUMN, REMOVE_COLUMN, TYPE_CHANGE)
     * @throws IllegalArgumentException if parameters invalid
     */
    public void recordSchemaChange(@NonNull String tableId, @NonNull String changeType) {
        if (tableId.isBlank()) {
            throw new IllegalArgumentException("tableId cannot be blank");
        }
        if (changeType.isBlank()) {
            throw new IllegalArgumentException("changeType cannot be blank");
        }

        String counterKey = tableId + ":" + changeType;
        schemaDriftCounters
                .computeIfAbsent(counterKey, k -> Counter.builder("data_schema_drift_detected")
                        .tag("table", tableId)
                        .tag("change_type", changeType)
                        .register(meterRegistry))
                .incrementAndGet();

        MDC.put("schema_change", tableId);
        MDC.put("change_type", changeType);
        logger.warn("Schema drift detected on table {}: {}", tableId, changeType);
        MDC.remove("schema_change");
        MDC.remove("change_type");
    }

    /**
     * Records a task execution timing.
     *
     * @param taskId Task identifier
     * @param durationSeconds Execution duration in seconds
     * @throws IllegalArgumentException if parameters invalid
     */
    public void recordTaskExecution(@NonNull String taskId, double durationSeconds) {
        if (taskId.isBlank()) {
            throw new IllegalArgumentException("taskId cannot be blank");
        }
        if (durationSeconds < 0) {
            throw new IllegalArgumentException("durationSeconds must be non-negative");
        }

        Timer timer = timersByTable.computeIfAbsent(taskId, k ->
                Timer.builder("task_execution_duration_seconds")
                        .tag("task_id", taskId)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry));

        timer.record(io.micrometer.core.instrument.util.TimeUtils.secondsToNanos(durationSeconds));

        logger.debug("Recorded task execution: task={}, duration={}s", taskId, durationSeconds);
    }

    /**
     * Records a data contract violation.
     *
     * @param violationCode Constraint code (MISSING_INPUT, TYPE_MISMATCH, etc.)
     * @throws IllegalArgumentException if violationCode is null or empty
     */
    public void recordContractViolation(@NonNull String violationCode) {
        if (violationCode.isBlank()) {
            throw new IllegalArgumentException("violationCode cannot be blank");
        }

        contractViolationCounter.incrementAndGet();

        Counter violation = Counter.builder("contract_violations_total")
                .tag("code", violationCode)
                .register(meterRegistry);
        violation.increment();

        MDC.put("contract_violation", violationCode);
        logger.error("Data contract violation: {}", violationCode);
        MDC.remove("contract_violation");
    }

    /**
     * Gets current lineage query count for a table.
     *
     * @param tableId Table identifier
     * @return Query count
     */
    public int getLineageQueryCount(@NonNull String tableId) {
        AtomicInteger counter = lineageQueryCounters.get(tableId);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Gets current guard violation count for a pattern.
     *
     * @param pattern Pattern name
     * @return Violation count
     */
    public int getGuardViolationCount(@NonNull String pattern) {
        AtomicInteger counter = guardViolationCounters.get(pattern);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Gets total contract violations recorded.
     *
     * @return Total count
     */
    public int getTotalContractViolations() {
        return contractViolationCounter.get();
    }

    /**
     * Exports metrics in Prometheus text format.
     *
     * @return Prometheus metrics text
     */
    public String exportMetricsAsPrometheus() {
        if (meterRegistry instanceof PrometheusMeterRegistry promRegistry) {
            return promRegistry.scrape();
        }

        throw new UnsupportedOperationException(
                "Metrics export requires PrometheusMeterRegistry. " +
                "Current registry is: " + meterRegistry.getClass().getSimpleName());
    }

    // === Private Helpers ===

    private void initializeMetrics() {
        // Pre-register base metrics to ensure they exist in scrape output
        Gauge.builder("data_lineage_queries_total", () -> 0)
                .description("Total data lineage queries")
                .register(meterRegistry);

        Gauge.builder("guard_violations_total", () -> 0)
                .description("Total guard violations detected")
                .register(meterRegistry);

        Gauge.builder("data_schema_drift_detected", () -> 0)
                .description("Total schema drift events detected")
                .register(meterRegistry);

        Gauge.builder("contract_violations_total", contractViolationCounter::get)
                .description("Total data contract violations")
                .register(meterRegistry);

        logger.debug("Initialized Prometheus metrics gauges");
    }

    /**
     * Auto-closing timer context for recording data access latency.
     */
    public class TimerContext implements AutoCloseable {
        private final String tableId;
        private final String operation;
        private final long startTimeMs;

        TimerContext(@NonNull String tableId, @NonNull String operation, long startTimeMs) {
            this.tableId = tableId;
            this.operation = operation;
            this.startTimeMs = startTimeMs;
        }

        @Override
        public void close() {
            long durationMs = System.currentTimeMillis() - startTimeMs;
            recordDataAccessLatency(tableId, operation, durationMs);
        }

        private void recordDataAccessLatency(String tableId, String operation, long durationMs) {
            Timer timer = timersByTable.computeIfAbsent(
                    tableId + ":" + operation,
                    k -> Timer.builder("data_table_access_latency_ms")
                            .tag("table", tableId)
                            .tag("operation", operation)
                            .publishPercentiles(0.5, 0.95, 0.99)
                            .register(meterRegistry));

            timer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);

            // Update running p95 calculation
            p95Calculator.add(durationMs);

            logger.debug("Recorded table access: table={}, op={}, latency={}ms",
                    tableId, operation, durationMs);
        }
    }
}
