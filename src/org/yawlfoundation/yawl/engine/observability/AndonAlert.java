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

package org.yawlfoundation.yawl.engine.observability;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

/**
 * Andon alert system for YAWL engine - implements Toyota Production System style alerts.
 *
 * <p>Andon alerts follow the Toyota Production System principle of immediately surfacing
 * problems so they can be addressed. Alerts are classified by severity:</p>
 *
 * <ul>
 *   <li><b>P0 (CRITICAL)</b> - System-stopping issues requiring immediate attention:
 *       deadlocks, data corruption, unrecoverable errors</li>
 *   <li><b>P1 (HIGH)</b> - Significant degradation requiring prompt attention:
 *       lock contention >500ms, resource exhaustion, performance degradation</li>
 *   <li><b>P2 (MEDIUM)</b> - Issues requiring monitoring: elevated latency,
 *       capacity warnings</li>
 *   <li><b>P3 (LOW)</b> - Informational: routine events, minor anomalies</li>
 * </ul>
 *
 * <p>All alerts are recorded as OTEL spans with appropriate attributes and are
 * logged at the corresponding level (ERROR for P0/P1, WARN for P2, INFO for P3).</p>
 *
 * @author YAWL Foundation
 * @since 6.0
 */
public final class AndonAlert {

    private static final Logger logger = LogManager.getLogger(AndonAlert.class);

    /** Alert severity levels following Toyota Production System Andon principles. */
    public enum Level {
        /** P0 - CRITICAL: System-stopping, immediate attention required (deadlock, corruption) */
        P0_CRITICAL(0, "CRITICAL"),
        /** P1 - HIGH: Significant degradation, prompt attention required (contention >500ms) */
        P1_HIGH(1, "HIGH"),
        /** P2 - MEDIUM: Elevated metrics requiring monitoring */
        P2_MEDIUM(2, "MEDIUM"),
        /** P3 - LOW: Informational events */
        P3_LOW(3, "LOW");

        private final int severity;
        private final String label;

        Level(int severity, String label) {
            this.severity = severity;
            this.label = label;
        }

        public int getSeverity() { return severity; }
        public String getLabel() { return label; }
    }

    /** OTEL attribute keys for Andon alerts */
    public static final AttributeKey<String> ANDON_LEVEL = AttributeKey.stringKey("yawl.andon.level");
    public static final AttributeKey<String> ANDON_CATEGORY = AttributeKey.stringKey("yawl.andon.category");
    public static final AttributeKey<String> ANDON_MESSAGE = AttributeKey.stringKey("yawl.andon.message");
    public static final AttributeKey<String> ANDON_CASE_ID = AttributeKey.stringKey("yawl.andon.case_id");
    public static final AttributeKey<String> ANDON_SPEC_ID = AttributeKey.stringKey("yawl.andon.spec_id");
    public static final AttributeKey<Long> ANDON_TIMESTAMP = AttributeKey.longKey("yawl.andon.timestamp");

    /** Alert categories for classification */
    public static final String CATEGORY_DEADLOCK = "deadlock";
    public static final String CATEGORY_LOCK_CONTENTION = "lock_contention";
    public static final String CATEGORY_RESOURCE_EXHAUSTION = "resource_exhaustion";
    public static final String CATEGORY_DATA_CORRUPTION = "data_corruption";
    public static final String CATEGORY_PERFORMANCE = "performance";

    /** Threshold for P1 lock contention alert (500ms in nanoseconds) */
    public static final long P1_LOCK_CONTENTION_THRESHOLD_NS = 500_000_000L;

    /** Threshold for P2 lock contention warning (100ms in nanoseconds) */
    public static final long P2_LOCK_CONTENTION_THRESHOLD_NS = 100_000_000L;

    private final Level level;
    private final String category;
    private final String message;
    private final Instant timestamp;
    private final Map<String, Object> context;
    private final Throwable cause;

    private AndonAlert(Level level, String category, String message,
                       Map<String, Object> context, Throwable cause) {
        this.level = level;
        this.category = category;
        this.message = message;
        this.timestamp = Instant.now();
        this.context = context != null ? new ConcurrentHashMap<>(context) : new ConcurrentHashMap<>();
        this.cause = cause;
    }

    /**
     * Creates a P0 CRITICAL alert for deadlock situations.
     *
     * @param caseId the case identifier where deadlock occurred
     * @param specId the specification identifier
     * @param deadlockedTasks description of deadlocked tasks
     * @return the Andon alert
     */
    public static AndonAlert deadlock(String caseId, String specId, String deadlockedTasks) {
        Map<String, Object> ctx = new ConcurrentHashMap<>();
        ctx.put("case_id", caseId);
        ctx.put("spec_id", specId);
        ctx.put("deadlocked_tasks", deadlockedTasks);

        return new AndonAlert(
            Level.P0_CRITICAL,
            CATEGORY_DEADLOCK,
            String.format("Deadlock detected in case %s: %s", caseId, deadlockedTasks),
            ctx,
            null
        );
    }

    /**
     * Creates a P1 HIGH alert for lock contention exceeding threshold.
     *
     * @param caseId the case identifier experiencing contention
     * @param waitTimeNanos the lock wait time in nanoseconds
     * @param operation the operation that was waiting
     * @return the Andon alert
     */
    public static AndonAlert lockContention(String caseId, long waitTimeNanos, String operation) {
        Map<String, Object> ctx = new ConcurrentHashMap<>();
        ctx.put("case_id", caseId);
        ctx.put("wait_time_ms", waitTimeNanos / 1_000_000.0);
        ctx.put("operation", operation);

        return new AndonAlert(
            Level.P1_HIGH,
            CATEGORY_LOCK_CONTENTION,
            String.format("Lock contention %.2fms in case %s during %s",
                waitTimeNanos / 1_000_000.0, caseId, operation),
            ctx,
            null
        );
    }

    /**
     * Creates a P2 MEDIUM alert for elevated lock contention.
     *
     * @param caseId the case identifier experiencing contention
     * @param waitTimeNanos the lock wait time in nanoseconds
     * @param operation the operation that was waiting
     * @return the Andon alert
     */
    public static AndonAlert elevatedContention(String caseId, long waitTimeNanos, String operation) {
        Map<String, Object> ctx = new ConcurrentHashMap<>();
        ctx.put("case_id", caseId);
        ctx.put("wait_time_ms", waitTimeNanos / 1_000_000.0);
        ctx.put("operation", operation);

        return new AndonAlert(
            Level.P2_MEDIUM,
            CATEGORY_LOCK_CONTENTION,
            String.format("Elevated lock contention %.2fms in case %s during %s",
                waitTimeNanos / 1_000_000.0, caseId, operation),
            ctx,
            null
        );
    }

    /**
     * Fires the alert - logs it and records it as an OTEL span event.
     *
     * @param span the current OTEL span to record the alert on
     */
    public void fire(Span span) {
        String logMessage = String.format("[ANDON-%s] [%s] %s",
            level.getLabel(), category, message);

        switch (level) {
            case P0_CRITICAL -> {
                logger.fatal(logMessage);
                if (cause != null) {
                    logger.fatal("Root cause:", cause);
                }
            }
            case P1_HIGH -> logger.error(logMessage);
            case P2_MEDIUM -> logger.warn(logMessage);
            case P3_LOW -> logger.info(logMessage);
        }

        // Record on OTEL span
        if (span != null && span.isRecording()) {
            var attrBuilder = Attributes.builder()
                .put(ANDON_LEVEL, level.getLabel())
                .put(ANDON_CATEGORY, category)
                .put(ANDON_MESSAGE, message)
                .put(ANDON_TIMESTAMP, timestamp.toEpochMilli());

            // Add context attributes
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                String key = "yawl.andon.context." + entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String s) {
                    attrBuilder.put(key, s);
                } else if (value instanceof Long l) {
                    attrBuilder.put(key, l);
                } else if (value instanceof Double d) {
                    attrBuilder.put(key, d);
                } else if (value instanceof Boolean b) {
                    attrBuilder.put(key, b);
                } else {
                    attrBuilder.put(key, String.valueOf(value));
                }
            }

            span.addEvent("andon.alert", attrBuilder.build());

            if (level == Level.P0_CRITICAL || level == Level.P1_HIGH) {
                span.setStatus(StatusCode.ERROR, message);
                if (cause != null) {
                    span.recordException(cause);
                }
            }
        }

        // Record metrics
        recordMetrics();
    }

    /**
     * Fires the alert without a span - logs only.
     */
    public void fire() {
        fire(null);
    }

    /**
     * Records alert metrics via YAWLTelemetry.
     */
    private void recordMetrics() {
        YAWLTelemetry telemetry = YAWLTelemetry.getInstance();
        if (telemetry.isEnabled()) {
            Attributes attributes = Attributes.builder()
                .put("yawl.andon.level", level.getLabel())
                .put("yawl.andon.category", category)
                .build();

            telemetry.getMeter()
                .counterBuilder("yawl.andon.alerts")
                .setDescription("Count of Andon alerts fired")
                .build()
                .add(1, attributes);
        }
    }

    // Getters

    public Level getLevel() { return level; }
    public String getCategory() { return category; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getContext() { return new ConcurrentHashMap<>(context); }
    public Throwable getCause() { return cause; }

    @Override
    public String toString() {
        return String.format("AndonAlert[level=%s, category=%s, message=%s, timestamp=%s]",
            level, category, message, timestamp);
    }
}
