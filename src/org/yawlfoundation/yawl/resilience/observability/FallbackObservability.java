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

package org.yawlfoundation.yawl.resilience.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Observability for fallback operations with OTEL spans and P1 Andon alerts.
 *
 * <p>Tracks fallback invocations across the YAWL system, providing:</p>
 * <ul>
 *   <li>Micrometer metrics for fallback invocations by operation and component</li>
 *   <li>OTEL spans for distributed tracing of fallback operations</li>
 *   <li>Stale data detection with P1 Andon alerts when data freshness exceeds threshold</li>
 *   <li>Integration points for connection pools, circuit breakers, and cache fallbacks</li>
 * </ul>
 *
 * <h2>Metrics Exposed</h2>
 * <ul>
 *   <li>{@code yawl_fallback_invocations_total} - Counter by operation, component, reason</li>
 *   <li>{@code yawl_fallback_stale_data_served_total} - Counter of stale data served</li>
 *   <li>{@code yawl_fallback_data_freshness_seconds} - Gauge of data age when fallback used</li>
 *   <li>{@code yawl_fallback_operation_duration} - Timer for fallback operation duration</li>
 * </ul>
 *
 * <h2>OTEL Spans</h2>
 * <p>Each fallback operation creates a span named {@code {component}.{operation}.fallback}
 * with attributes for:</p>
 * <ul>
 *   <li>{@code fallback.reason} - Why fallback was used (timeout, error, circuit_open, etc.)</li>
 *   <li>{@code fallback.source} - Where fallback data came from (cache, default, stale)</li>
 *   <li>{@code fallback.data_age_ms} - Age of fallback data in milliseconds</li>
 *   <li>{@code fallback.is_stale} - Whether data exceeded staleness threshold</li>
 * </ul>
 *
 * <h2>P1 Andon Alerts</h2>
 * <p>When stale data (> 5 minutes old by default) is served via fallback, a P1 Andon
 * alert is fired via the {@link AndonAlertService}. This enables immediate visibility
 * into degraded system behavior.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * FallbackObservability fallbackObs = FallbackObservability.getInstance();
 *
 * // Record a fallback operation
 * FallbackResult result = fallbackObs.recordFallback(
 *     "connection-pool",
 *     "getSession",
 *     FallbackReason.CONNECTION_TIMEOUT,
 *     () -> cachedSession,
 *     Instant.now().minusSeconds(120)  // Data is 2 minutes old
 * );
 *
 * if (result.isStale()) {
 *     // Andon P1 alert already fired
 *     logger.warn("Serving stale session data");
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @see AndonAlertService
 */
public class FallbackObservability {

    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackObservability.class);

    /** Default staleness threshold: 5 minutes */
    public static final Duration DEFAULT_STALENESS_THRESHOLD = Duration.ofMinutes(5);

    /** P1 severity for Andon alerts */
    private static final String ANDON_SEVERITY_P1 = "P1";

    private static volatile FallbackObservability instance;
    private static final Object INSTANCE_LOCK = new Object();

    private final MeterRegistry meterRegistry;
    private final Tracer tracer;
    private final AndonAlertService andonService;
    private final Duration stalenessThreshold;

    // Metrics
    private final Counter fallbackInvocationsCounter;
    private final Counter staleDataServedCounter;
    private final AtomicLong currentDataFreshnessMs;
    private final Timer fallbackOperationTimer;

    // Tracking for active fallbacks
    private final ConcurrentHashMap<String, FallbackRecord> activeFallbacks = new ConcurrentHashMap<>();

    // Cumulative statistics
    private final AtomicLong totalFallbacks = new AtomicLong(0);
    private final AtomicLong totalStaleDataServed = new AtomicLong(0);
    private final AtomicLong totalAndonAlertsFired = new AtomicLong(0);

    /**
     * Reason for fallback invocation.
     */
    public enum FallbackReason {
        /** Connection timeout to primary service */
        CONNECTION_TIMEOUT("connection_timeout"),
        /** Primary service returned an error */
        SERVICE_ERROR("service_error"),
        /** Circuit breaker is open */
        CIRCUIT_OPEN("circuit_open"),
        /** Rate limiter triggered */
        RATE_LIMITED("rate_limited"),
        /** Primary service unavailable */
        SERVICE_UNAVAILABLE("service_unavailable"),
        /** Cache miss on primary, using fallback cache */
        CACHE_FALLBACK("cache_fallback"),
        /** Primary data too old, using backup */
        STALE_DATA("stale_data"),
        /** Retry exhausted */
        RETRY_EXHAUSTED("retry_exhausted"),
        /** Bulkhead full */
        BULKHEAD_FULL("bulkhead_full"),
        /** Graceful degradation */
        DEGRADATION("degradation"),
        /** Unknown reason */
        UNKNOWN("unknown");

        private final String value;

        FallbackReason(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Source of fallback data.
     */
    public enum FallbackSource {
        /** Data from local cache */
        LOCAL_CACHE("local_cache"),
        /** Data from distributed cache */
        DISTRIBUTED_CACHE("distributed_cache"),
        /** Default/empty value */
        DEFAULT_VALUE("default_value"),
        /** Stale data from previous successful call */
        STALE_DATA("stale_data"),
        /** Secondary service */
        SECONDARY_SERVICE("secondary_service"),
        /** Hardcoded fallback */
        HARDCODED("hardcoded");

        private final String value;

        FallbackSource(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Result of a fallback operation.
     */
    public static final class FallbackResult {
        private final boolean usedFallback;
        private final boolean isStale;
        private final long dataAgeMs;
        private final FallbackReason reason;
        private final FallbackSource source;
        private final Object value;
        private final String andonAlertId;
        private final Throwable primaryError;

        private FallbackResult(boolean usedFallback, boolean isStale, long dataAgeMs,
                              FallbackReason reason, FallbackSource source, Object value,
                              String andonAlertId, Throwable primaryError) {
            this.usedFallback = usedFallback;
            this.isStale = isStale;
            this.dataAgeMs = dataAgeMs;
            this.reason = reason;
            this.source = source;
            this.value = value;
            this.andonAlertId = andonAlertId;
            this.primaryError = primaryError;
        }

        public boolean usedFallback() { return usedFallback; }
        public boolean isStale() { return isStale; }
        public long getDataAgeMs() { return dataAgeMs; }
        public FallbackReason getReason() { return reason; }
        public FallbackSource getSource() { return source; }
        public Object getValue() { return value; }
        public String getAndonAlertId() { return andonAlertId; }
        public Throwable getPrimaryError() { return primaryError; }
    }

    /**
     * Record of an active fallback operation.
     */
    private static final class FallbackRecord {
        final String component;
        final String operation;
        final FallbackReason reason;
        final Instant startTime;
        final long dataTimestamp;

        FallbackRecord(String component, String operation, FallbackReason reason,
                      Instant startTime, long dataTimestamp) {
            this.component = component;
            this.operation = operation;
            this.reason = reason;
            this.startTime = startTime;
            this.dataTimestamp = dataTimestamp;
        }
    }

    /**
     * Service interface for firing Andon alerts.
     * Implementations integrate with the actual Andon/alerting system.
     */
    public interface AndonAlertService {
        /**
         * Fire an Andon alert.
         *
         * @param severity Alert severity (P1, P2, P3, etc.)
         * @param component Component that triggered the alert
         * @param operation Operation that was using fallback
         * @param message Alert message
         * @param context Additional context for the alert
         * @return Alert ID for tracking
         */
        String fireAlert(String severity, String component, String operation,
                        String message, Map<String, Object> context);

        /**
         * Check if Andon service is healthy.
         */
        boolean isHealthy();
    }

    /**
     * Default Andon alert service that logs alerts.
     * Production implementations should integrate with actual alerting systems.
     */
    public static class LoggingAndonAlertService implements AndonAlertService {

        private final AtomicLong alertCounter = new AtomicLong(0);

        @Override
        public String fireAlert(String severity, String component, String operation,
                               String message, Map<String, Object> context) {
            String alertId = "andon-" + System.currentTimeMillis() + "-" + alertCounter.incrementAndGet();

            LOGGER.error("[ANDON {}] Component: {}, Operation: {}, Message: {}, Context: {}",
                severity, component, operation, message, context);

            return alertId;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }
    }

    // ------------------------------------------------------------------ construction

    private FallbackObservability(MeterRegistry meterRegistry, Tracer tracer,
                                 AndonAlertService andonService, Duration stalenessThreshold) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry is required");
        this.tracer = tracer;  // Can be null if OTEL not configured
        this.andonService = andonService != null ? andonService : new LoggingAndonAlertService();
        this.stalenessThreshold = stalenessThreshold != null ? stalenessThreshold : DEFAULT_STALENESS_THRESHOLD;

        // Initialize metrics
        this.fallbackInvocationsCounter = Counter.builder("yawl_fallback_invocations_total")
            .description("Total fallback invocations by operation and component")
            .tags(Tags.of("component", "unknown", "operation", "unknown", "reason", "unknown"))
            .register(meterRegistry);

        this.staleDataServedCounter = Counter.builder("yawl_fallback_stale_data_served_total")
            .description("Total times stale data was served via fallback")
            .tags(Tags.of("component", "unknown"))
            .register(meterRegistry);

        this.currentDataFreshnessMs = new AtomicLong(0);
        Gauge.builder("yawl_fallback_data_freshness_seconds", this, fo -> fo.currentDataFreshnessMs.get() / 1000.0)
            .description("Current fallback data freshness in seconds")
            .register(meterRegistry);

        this.fallbackOperationTimer = Timer.builder("yawl_fallback_operation_duration")
            .description("Duration of fallback operations")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        LOGGER.info("FallbackObservability initialized with stalenessThreshold={}s", this.stalenessThreshold.getSeconds());
    }

    /**
     * Get the singleton instance with default configuration.
     *
     * @return the singleton FallbackObservability instance
     */
    public static FallbackObservability getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    // Use simple meter registry as default
                    io.micrometer.core.instrument.simple.SimpleMeterRegistry simpleRegistry =
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
                    instance = new FallbackObservability(simpleRegistry, null, null, null);
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the singleton with custom configuration.
     *
     * @param meterRegistry Micrometer meter registry
     * @param openTelemetry OpenTelemetry instance (can be null)
     * @param andonService Andon alert service (can be null for default logging)
     * @param stalenessThreshold Threshold for stale data detection
     * @return the initialized instance
     */
    public static FallbackObservability initialize(MeterRegistry meterRegistry,
                                                   OpenTelemetry openTelemetry,
                                                   AndonAlertService andonService,
                                                   Duration stalenessThreshold) {
        synchronized (INSTANCE_LOCK) {
            Tracer tracer = null;
            if (openTelemetry != null) {
                tracer = openTelemetry.getTracer("yawl-fallback-observability", "6.0");
            }
            instance = new FallbackObservability(meterRegistry, tracer, andonService, stalenessThreshold);
            return instance;
        }
    }

    // ------------------------------------------------------------------ public API

    /**
     * Record a fallback operation with data freshness tracking.
     *
     * @param component Component name (e.g., "connection-pool", "circuit-breaker")
     * @param operation Operation name (e.g., "getSession", "executeQuery")
     * @param reason Reason for fallback
     * @param fallbackSupplier Supplier that provides the fallback value
     * @param dataTimestamp When the fallback data was originally created
     * @param <T> Return type
     * @return FallbackResult containing the result and metadata
     */
    public <T> FallbackResult recordFallback(String component, String operation,
                                             FallbackReason reason,
                                             Supplier<T> fallbackSupplier,
                                             Instant dataTimestamp) {
        return recordFallback(component, operation, reason, FallbackSource.STALE_DATA,
                             fallbackSupplier, dataTimestamp, null);
    }

    /**
     * Record a fallback operation with full context.
     *
     * @param component Component name
     * @param operation Operation name
     * @param reason Reason for fallback
     * @param source Source of fallback data
     * @param fallbackSupplier Supplier that provides the fallback value
     * @param dataTimestamp When the fallback data was created (null = unknown)
     * @param primaryError The error that triggered the fallback (can be null)
     * @param <T> Return type
     * @return FallbackResult containing the result and metadata
     */
    public <T> FallbackResult recordFallback(String component, String operation,
                                             FallbackReason reason,
                                             FallbackSource source,
                                             Supplier<T> fallbackSupplier,
                                             Instant dataTimestamp,
                                             Throwable primaryError) {
        Objects.requireNonNull(component, "component is required");
        Objects.requireNonNull(operation, "operation is required");
        Objects.requireNonNull(reason, "reason is required");
        Objects.requireNonNull(fallbackSupplier, "fallbackSupplier is required");

        String fallbackId = generateFallbackId(component, operation);
        Instant startTime = Instant.now();
        long dataAgeMs = dataTimestamp != null ? Duration.between(dataTimestamp, startTime).toMillis() : -1;
        boolean isStale = dataAgeMs > stalenessThreshold.toMillis();

        // Create OTEL span
        Span span = createFallbackSpan(component, operation, reason, source, dataAgeMs, isStale);
        Context spanContext = span != null ? span.storeInContext(Context.current()) : null;

        // Track active fallback
        FallbackRecord record = new FallbackRecord(component, operation, reason, startTime,
            dataTimestamp != null ? dataTimestamp.toEpochMilli() : 0);
        activeFallbacks.put(fallbackId, record);

        String andonAlertId = null;
        T value = null;
        Throwable fallbackError = null;

        try (Scope scope = spanContext != null ? spanContext.makeCurrent() : null) {
            // Execute fallback with timing
            long timerStart = System.nanoTime();
            try {
                value = fallbackSupplier.get();
            } catch (Exception e) {
                fallbackError = e;
                if (span != null) {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, "Fallback failed: " + e.getMessage());
                }
            }
            long durationNanos = System.nanoTime() - timerStart;
            fallbackOperationTimer.record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);

            // Update metrics
            recordFallbackMetrics(component, operation, reason, source, dataAgeMs, isStale);

            // Fire Andon P1 alert if stale
            if (isStale) {
                andonAlertId = fireStaleDataAndonAlert(component, operation, reason, source, dataAgeMs);
            }

            if (span != null) {
                span.setStatus(StatusCode.OK);
            }

        } finally {
            if (span != null) {
                span.end();
            }
            activeFallbacks.remove(fallbackId);
        }

        // If fallback itself failed, rethrow
        if (fallbackError != null) {
            if (fallbackError instanceof RuntimeException) {
                throw (RuntimeException) fallbackError;
            }
            throw new RuntimeException("Fallback operation failed", fallbackError);
        }

        return new FallbackResult(true, isStale, dataAgeMs, reason, source, value, andonAlertId, primaryError);
    }

    /**
     * Record a fallback that used a default value.
     *
     * @param component Component name
     * @param operation Operation name
     * @param reason Reason for fallback
     * @param defaultValue The default value used
     * @param <T> Value type
     * @return FallbackResult
     */
    public <T> FallbackResult recordDefaultFallback(String component, String operation,
                                                    FallbackReason reason, T defaultValue) {
        return recordFallback(component, operation, reason, FallbackSource.DEFAULT_VALUE,
                             () -> defaultValue, null, null);
    }

    /**
     * Record a cache fallback operation.
     *
     * @param component Component name
     * @param operation Operation name
     * @param cacheSupplier Supplier that retrieves from cache
     * @param cacheTimestamp When the cache entry was created
     * @param <T> Value type
     * @return FallbackResult
     */
    public <T> FallbackResult recordCacheFallback(String component, String operation,
                                                  Supplier<T> cacheSupplier,
                                                  Instant cacheTimestamp) {
        return recordFallback(component, operation, FallbackReason.CACHE_FALLBACK,
                             FallbackSource.LOCAL_CACHE, cacheSupplier, cacheTimestamp, null);
    }

    /**
     * Record a circuit breaker fallback.
     *
     * @param component Component name
     * @param operation Operation name
     * @param fallbackSupplier Supplier for fallback value
     * @param <T> Value type
     * @return FallbackResult
     */
    public <T> FallbackResult recordCircuitBreakerFallback(String component, String operation,
                                                           Supplier<T> fallbackSupplier) {
        return recordFallback(component, operation, FallbackReason.CIRCUIT_OPEN,
                             FallbackSource.SECONDARY_SERVICE, fallbackSupplier, Instant.now(), null);
    }

    /**
     * Record a retry exhausted fallback.
     *
     * @param component Component name
     * @param operation Operation name
     * @param fallbackSupplier Supplier for fallback value
     * @param lastError The last error from retry attempts
     * @param <T> Value type
     * @return FallbackResult
     */
    public <T> FallbackResult recordRetryExhaustedFallback(String component, String operation,
                                                           Supplier<T> fallbackSupplier,
                                                           Throwable lastError) {
        return recordFallback(component, operation, FallbackReason.RETRY_EXHAUSTED,
                             FallbackSource.DEFAULT_VALUE, fallbackSupplier, null, lastError);
    }

    /**
     * Record a graceful degradation fallback.
     *
     * @param component Component name
     * @param operation Operation name
     * @param degradedSupplier Supplier for degraded but functional value
     * @param <T> Value type
     * @return FallbackResult
     */
    public <T> FallbackResult recordDegradationFallback(String component, String operation,
                                                        Supplier<T> degradedSupplier) {
        return recordFallback(component, operation, FallbackReason.DEGRADATION,
                             FallbackSource.SECONDARY_SERVICE, degradedSupplier, Instant.now(), null);
    }

    /**
     * Check if data at the given timestamp is considered stale.
     *
     * @param dataTimestamp When the data was created
     * @return true if data is older than staleness threshold
     */
    public boolean isStale(Instant dataTimestamp) {
        if (dataTimestamp == null) {
            return true;  // Unknown timestamp = potentially stale
        }
        return Duration.between(dataTimestamp, Instant.now()).compareTo(stalenessThreshold) > 0;
    }

    /**
     * Get current fallback statistics.
     *
     * @return FallbackStats snapshot
     */
    public FallbackStats getStats() {
        return new FallbackStats(
            totalFallbacks.get(),
            totalStaleDataServed.get(),
            totalAndonAlertsFired.get(),
            activeFallbacks.size(),
            stalenessThreshold
        );
    }

    /**
     * Get the configured staleness threshold.
     */
    public Duration getStalenessThreshold() {
        return stalenessThreshold;
    }

    /**
     * Get the Andon alert service.
     */
    public AndonAlertService getAndonService() {
        return andonService;
    }

    // ------------------------------------------------------------------ private helpers

    private String generateFallbackId(String component, String operation) {
        return component + ":" + operation + ":" + System.nanoTime();
    }

    private Span createFallbackSpan(String component, String operation, FallbackReason reason,
                                   FallbackSource source, long dataAgeMs, boolean isStale) {
        if (tracer == null) {
            return null;
        }

        String spanName = component + "." + operation + ".fallback";
        return tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("fallback.component", component)
            .setAttribute("fallback.operation", operation)
            .setAttribute("fallback.reason", reason.getValue())
            .setAttribute("fallback.source", source.getValue())
            .setAttribute("fallback.data_age_ms", dataAgeMs)
            .setAttribute("fallback.is_stale", isStale)
            .setAttribute("fallback.staleness_threshold_ms", stalenessThreshold.toMillis())
            .startSpan();
    }

    private void recordFallbackMetrics(String component, String operation, FallbackReason reason,
                                      FallbackSource source, long dataAgeMs, boolean isStale) {
        // Increment fallback counter with tags
        Counter.builder("yawl_fallback_invocations_total")
            .tags(Tags.of(
                Tag.of("component", component),
                Tag.of("operation", operation),
                Tag.of("reason", reason.getValue()),
                Tag.of("source", source.getValue())
            ))
            .register(meterRegistry)
            .increment();

        totalFallbacks.incrementAndGet();

        // Update freshness gauge
        if (dataAgeMs >= 0) {
            currentDataFreshnessMs.set(dataAgeMs);
        }

        // Track stale data
        if (isStale) {
            Counter.builder("yawl_fallback_stale_data_served_total")
                .tags(Tags.of(
                    Tag.of("component", component),
                    Tag.of("operation", operation)
                ))
                .register(meterRegistry)
                .increment();

            totalStaleDataServed.incrementAndGet();
        }
    }

    private String fireStaleDataAndonAlert(String component, String operation,
                                           FallbackReason reason, FallbackSource source,
                                           long dataAgeMs) {
        String message = String.format(
            "Stale data served via fallback: component=%s, operation=%s, data_age=%dms, threshold=%dms",
            component, operation, dataAgeMs, stalenessThreshold.toMillis()
        );

        Map<String, Object> context = Map.of(
            "component", component,
            "operation", operation,
            "reason", reason.getValue(),
            "source", source.getValue(),
            "data_age_ms", dataAgeMs,
            "staleness_threshold_ms", stalenessThreshold.toMillis(),
            "timestamp", Instant.now().toString()
        );

        String alertId = andonService.fireAlert(ANDON_SEVERITY_P1, component, operation, message, context);
        totalAndonAlertsFired.incrementAndGet();

        LOGGER.warn("P1 Andon alert fired for stale fallback data: alertId={}, component={}, operation={}",
            alertId, component, operation);

        return alertId;
    }

    // ------------------------------------------------------------------ inner types

    /**
     * Snapshot of fallback statistics.
     */
    public static final class FallbackStats {
        private final long totalFallbacks;
        private final long totalStaleDataServed;
        private final long totalAndonAlertsFired;
        private final int activeFallbacks;
        private final Duration stalenessThreshold;

        public FallbackStats(long totalFallbacks, long totalStaleDataServed,
                            long totalAndonAlertsFired, int activeFallbacks,
                            Duration stalenessThreshold) {
            this.totalFallbacks = totalFallbacks;
            this.totalStaleDataServed = totalStaleDataServed;
            this.totalAndonAlertsFired = totalAndonAlertsFired;
            this.activeFallbacks = activeFallbacks;
            this.stalenessThreshold = stalenessThreshold;
        }

        public long getTotalFallbacks() { return totalFallbacks; }
        public long getTotalStaleDataServed() { return totalStaleDataServed; }
        public long getTotalAndonAlertsFired() { return totalAndonAlertsFired; }
        public int getActiveFallbacks() { return activeFallbacks; }
        public Duration getStalenessThreshold() { return stalenessThreshold; }

        public double getStaleDataRatio() {
            return totalFallbacks > 0 ? (double) totalStaleDataServed / totalFallbacks : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "FallbackStats{totalFallbacks=%d, staleDataServed=%d (%.1f%%), " +
                "andonAlertsFired=%d, activeFallbacks=%d, stalenessThreshold=%ds}",
                totalFallbacks, totalStaleDataServed, getStaleDataRatio() * 100,
                totalAndonAlertsFired, activeFallbacks, stalenessThreshold.getSeconds()
            );
        }
    }
}
