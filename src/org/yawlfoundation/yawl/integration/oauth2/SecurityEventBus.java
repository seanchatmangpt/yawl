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

package org.yawlfoundation.yawl.integration.oauth2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Publish-subscribe event bus for OAuth2 security events with OTEL spans,
 * Micrometer counters, and SLF4J logging integration.
 *
 * <p>This event bus implements the Andon cord pattern for security-critical
 * events. Subscribers receive events for monitoring, alerting, and incident
 * response. All events are correlated via OTEL tracing and logged for audit.
 *
 * <h2>Event Types</h2>
 * <ul>
 *   <li>{@link JwksRefreshSuccess} - JWKS cache successfully refreshed</li>
 *   <li>{@link JwksRefreshFailure} - JWKS refresh failed (P0 Andon alert)</li>
 *   <li>{@link JwksStaleCacheWarning} - JWKS cache is stale (P1 warning)</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li><b>OTEL Spans</b>: Each event creates a span for distributed tracing</li>
 *   <li><b>Micrometer Counters</b>: Events increment counters for Prometheus/Grafana</li>
 *   <li><b>SLF4J Logging</b>: Structured logging for SIEM integration</li>
 *   <li><b>Subscribers</b>: Register via {@link #subscribe(Subscriber)}</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * SecurityEventBus eventBus = SecurityEventBus.getInstance();
 *
 * // Subscribe to events
 * eventBus.subscribe((event) -> {
 *     if (event.severity() == Severity.P0) {
 *         pagerDuty.trigger(event);
 *     }
 * });
 *
 * // Publish an event
 * eventBus.publish(new JwksRefreshFailure(
 *     "https://auth.example.com/certs",
 *     new IOException("Connection refused"),
 *     12,  // cache size
 *     3600 // cache age seconds
 * ));
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class SecurityEventBus {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventBus.class);
    private static final String INSTRUMENTATION_NAME = "org.yawlfoundation.yawl.oauth2";

    private static volatile SecurityEventBus instance;

    private final Tracer tracer;
    private final Meter meter;
    private final List<Subscriber> subscribers;
    private final LongCounter jwksRefreshSuccessCounter;
    private final LongCounter jwksRefreshFailureCounter;
    private final LongCounter jwksStaleCacheCounter;

    private SecurityEventBus() {
        this.tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME, "6.0.0");
        this.meter = GlobalOpenTelemetry.getMeter(INSTRUMENTATION_NAME);
        this.subscribers = new CopyOnWriteArrayList<>();

        // Initialize OTEL counters
        this.jwksRefreshSuccessCounter = meter
            .counterBuilder("yawl_oauth2_jwks_refresh_success")
            .setDescription("Number of successful JWKS cache refreshes")
            .setUnit("refreshes")
            .build();

        this.jwksRefreshFailureCounter = meter
            .counterBuilder("yawl_oauth2_jwks_refresh_failure")
            .setDescription("Number of failed JWKS cache refreshes")
            .setUnit("failures")
            .build();

        this.jwksStaleCacheCounter = meter
            .counterBuilder("yawl_oauth2_jwks_stale")
            .setDescription("Number of times stale JWKS cache was detected")
            .setUnit("warnings")
            .build();

        log.info("SecurityEventBus initialized with OTEL instrumentation");
    }

    /**
     * Get the singleton instance of SecurityEventBus.
     *
     * @return the SecurityEventBus instance
     */
    public static SecurityEventBus getInstance() {
        if (instance == null) {
            synchronized (SecurityEventBus.class) {
                if (instance == null) {
                    instance = new SecurityEventBus();
                }
            }
        }
        return instance;
    }

    /**
     * Subscribe to security events.
     *
     * @param subscriber the subscriber to add
     * @throws NullPointerException if subscriber is null
     */
    public void subscribe(Subscriber subscriber) {
        Objects.requireNonNull(subscriber, "subscriber must not be null");
        subscribers.add(subscriber);
        log.debug("Subscriber registered: {}", subscriber.getClass().getName());
    }

    /**
     * Unsubscribe from security events.
     *
     * @param subscriber the subscriber to remove
     */
    public void unsubscribe(Subscriber subscriber) {
        subscribers.remove(subscriber);
        log.debug("Subscriber unregistered: {}", subscriber.getClass().getName());
    }

    /**
     * Publish a security event to all subscribers.
     *
     * <p>This method:
     * <ol>
     *   <li>Creates an OTEL span for the event</li>
     *   <li>Increments appropriate Micrometer counters</li>
     *   <li>Logs the event via SLF4J</li>
     *   <li>Notifies all subscribers</li>
     * </ol>
     *
     * @param event the event to publish
     * @throws NullPointerException if event is null
     */
    public void publish(SecurityEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        Span span = tracer.spanBuilder(event.spanName())
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("event.id", event.eventId())
            .setAttribute("event.type", event.eventType())
            .setAttribute("event.severity", event.severity().name())
            .setAttribute("event.timestamp", event.timestamp().toString())
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Increment counters
            incrementCounter(event);

            // Log the event
            logEvent(event);

            // Notify subscribers
            notifySubscribers(event);

            // Set span status based on event severity
            if (event.severity() == Severity.P0) {
                span.setStatus(StatusCode.ERROR, event.message());
            } else if (event.severity() == Severity.P1) {
                span.setStatus(StatusCode.UNSET, event.message());
            } else {
                span.setStatus(StatusCode.OK);
            }

            // Add event-specific attributes
            event.addSpanAttributes(span);

        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, "Failed to process event: " + e.getMessage());
            log.error("Error processing security event: {}", e.getMessage(), e);
        } finally {
            span.end();
        }
    }

    private void incrementCounter(SecurityEvent event) {
        switch (event) {
            case JwksRefreshSuccess s -> jwksRefreshSuccessCounter.add(1,
                Attributes.builder()
                    .put("jwks.uri", s.jwksUri())
                    .put("cache.size", s.cacheSize())
                    .build());
            case JwksRefreshFailure f -> jwksRefreshFailureCounter.add(1,
                Attributes.builder()
                    .put("jwks.uri", f.jwksUri())
                    .put("cache.size", f.cacheSize())
                    .put("cache.age.seconds", f.cacheAgeSeconds())
                    .put("error.type", f.cause().getClass().getSimpleName())
                    .build());
            case JwksStaleCacheWarning w -> jwksStaleCacheCounter.add(1,
                Attributes.builder()
                    .put("jwks.uri", w.jwksUri())
                    .put("cache.age.seconds", w.cacheAgeSeconds())
                    .put("threshold.seconds", w.thresholdSeconds())
                    .build());
            default -> log.warn("Unknown event type: {}", event.getClass().getName());
        }
    }

    private void logEvent(SecurityEvent event) {
        switch (event.severity()) {
            case P0 -> log.error("[P0 ANDON] {}: {}", event.eventType(), event.message());
            case P1 -> log.warn("[P1] {}: {}", event.eventType(), event.message());
            case P2 -> log.info("[P2] {}: {}", event.eventType(), event.message());
            case P3 -> log.debug("[P3] {}: {}", event.eventType(), event.message());
        }
    }

    private void notifySubscribers(SecurityEvent event) {
        List<Exception> errors = new ArrayList<>();
        for (Subscriber subscriber : subscribers) {
            try {
                subscriber.onEvent(event);
            } catch (Exception e) {
                errors.add(e);
                log.warn("Subscriber {} failed to process event: {}",
                    subscriber.getClass().getName(), e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            log.warn("{} subscriber(s) failed to process event {}",
                errors.size(), event.eventId());
        }
    }

    // =========================================================================
    // Event Severity Levels
    // =========================================================================

    /**
     * Severity levels for security events.
     *
     * <p>Implements the Andon cord pattern:
     * <ul>
     *   <li><b>P0</b>: Critical - immediate response required (page on-call)</li>
     *   <li><b>P1</b>: Warning - attention needed within 1 hour</li>
     *   <li><b>P2</b>: Info - normal operations logged for audit</li>
     *   <li><b>P3</b>: Debug - detailed diagnostics only</li>
     * </ul>
     */
    public enum Severity {
        /** Critical - immediate response required (Andon cord pulled) */
        P0,
        /** Warning - attention needed within 1 hour */
        P1,
        /** Info - normal operations logged for audit */
        P2,
        /** Debug - detailed diagnostics only */
        P3
    }

    // =========================================================================
    // Event Interface and Implementations
    // =========================================================================

    /**
     * Base interface for all security events.
     */
    public interface SecurityEvent {

        /** Unique identifier for this event instance. */
        String eventId();

        /** Event type name for routing and filtering. */
        String eventType();

        /** Human-readable message describing the event. */
        String message();

        /** Severity level for alerting and routing. */
        Severity severity();

        /** Timestamp when the event occurred. */
        Instant timestamp();

        /** Span name for OTEL tracing. */
        String spanName();

        /** Add event-specific attributes to the OTEL span. */
        void addSpanAttributes(Span span);

        /** Convert event to a map for serialization. */
        Map<String, Object> toMap();
    }

    /**
     * Event emitted when JWKS cache refresh succeeds.
     *
     * @param eventId     unique event identifier
     * @param jwksUri     the JWKS endpoint URI
     * @param cacheSize   number of keys in the refreshed cache
     * @param timestamp   when the refresh completed
     */
    public record JwksRefreshSuccess(
        String eventId,
        String jwksUri,
        int cacheSize,
        Instant timestamp
    ) implements SecurityEvent {

        /**
         * Construct a success event with generated ID and current timestamp.
         */
        public JwksRefreshSuccess(String jwksUri, int cacheSize) {
            this(UUID.randomUUID().toString(), jwksUri, cacheSize, Instant.now());
        }

        @Override
        public String eventType() {
            return "jwks.refresh.success";
        }

        @Override
        public String message() {
            return String.format("JWKS cache refreshed successfully: %d keys loaded from %s",
                cacheSize, jwksUri);
        }

        @Override
        public Severity severity() {
            return Severity.P2;
        }

        @Override
        public String spanName() {
            return "jwks.refresh.success";
        }

        @Override
        public void addSpanAttributes(Span span) {
            span.setAttribute("jwks.uri", jwksUri);
            span.setAttribute("cache.size", cacheSize);
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of(
                "eventId", eventId,
                "eventType", eventType(),
                "jwksUri", jwksUri,
                "cacheSize", cacheSize,
                "timestamp", timestamp.toString()
            );
        }
    }

    /**
     * Event emitted when JWKS cache refresh fails.
     *
     * <p>This is a P0 Andon alert requiring immediate attention. The system
     * continues operating with a stale cache, but token validation may fail
     * if keys have rotated.
     *
     * @param eventId         unique event identifier
     * @param jwksUri         the JWKS endpoint URI that failed
     * @param cause           the exception that caused the failure
     * @param cacheSize       current cache size (may be stale)
     * @param cacheAgeSeconds age of the cache in seconds
     * @param timestamp       when the failure occurred
     */
    public record JwksRefreshFailure(
        String eventId,
        String jwksUri,
        Throwable cause,
        int cacheSize,
        long cacheAgeSeconds,
        Instant timestamp
    ) implements SecurityEvent {

        /**
         * Construct a failure event with generated ID and current timestamp.
         */
        public JwksRefreshFailure(String jwksUri, Throwable cause, int cacheSize, long cacheAgeSeconds) {
            this(UUID.randomUUID().toString(), jwksUri, cause, cacheSize, cacheAgeSeconds, Instant.now());
        }

        @Override
        public String eventType() {
            return "jwks.refresh.failure";
        }

        @Override
        public String message() {
            return String.format(
                "JWKS refresh failed from %s: %s. Operating with stale cache (%d keys, %d seconds old). " +
                "Token validation may fail if keys have rotated.",
                jwksUri, cause.getMessage(), cacheSize, cacheAgeSeconds);
        }

        @Override
        public Severity severity() {
            return Severity.P0;
        }

        @Override
        public String spanName() {
            return "jwks.refresh.failure";
        }

        @Override
        public void addSpanAttributes(Span span) {
            span.setAttribute("jwks.uri", jwksUri);
            span.setAttribute("cache.size", cacheSize);
            span.setAttribute("cache.age.seconds", cacheAgeSeconds);
            span.setAttribute("error.type", cause.getClass().getName());
            span.setAttribute("error.message", cause.getMessage() != null ? cause.getMessage() : "unknown");
            span.recordException(cause);
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of(
                "eventId", eventId,
                "eventType", eventType(),
                "jwksUri", jwksUri,
                "errorType", cause.getClass().getName(),
                "errorMessage", cause.getMessage() != null ? cause.getMessage() : "unknown",
                "cacheSize", cacheSize,
                "cacheAgeSeconds", cacheAgeSeconds,
                "timestamp", timestamp.toString()
            );
        }
    }

    /**
     * Event emitted when JWKS cache age exceeds a threshold.
     *
     * <p>This is a P1 warning indicating the cache may be stale. While not immediately
     * critical, sustained stale cache conditions can lead to authentication failures.
     *
     * @param eventId          unique event identifier
     * @param jwksUri          the JWKS endpoint URI
     * @param cacheAgeSeconds  age of the cache in seconds
     * @param thresholdSeconds the threshold that was exceeded
     * @param timestamp        when the warning was generated
     */
    public record JwksStaleCacheWarning(
        String eventId,
        String jwksUri,
        long cacheAgeSeconds,
        long thresholdSeconds,
        Instant timestamp
    ) implements SecurityEvent {

        /**
         * Construct a warning event with generated ID and current timestamp.
         */
        public JwksStaleCacheWarning(String jwksUri, long cacheAgeSeconds, long thresholdSeconds) {
            this(UUID.randomUUID().toString(), jwksUri, cacheAgeSeconds, thresholdSeconds, Instant.now());
        }

        @Override
        public String eventType() {
            return "jwks.cache.stale";
        }

        @Override
        public String message() {
            return String.format(
                "JWKS cache age (%d seconds) exceeds threshold (%d seconds) for %s. " +
                "Refresh attempts may be failing silently.",
                cacheAgeSeconds, thresholdSeconds, jwksUri);
        }

        @Override
        public Severity severity() {
            return Severity.P1;
        }

        @Override
        public String spanName() {
            return "jwks.cache.stale";
        }

        @Override
        public void addSpanAttributes(Span span) {
            span.setAttribute("jwks.uri", jwksUri);
            span.setAttribute("cache.age.seconds", cacheAgeSeconds);
            span.setAttribute("threshold.seconds", thresholdSeconds);
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of(
                "eventId", eventId,
                "eventType", eventType(),
                "jwksUri", jwksUri,
                "cacheAgeSeconds", cacheAgeSeconds,
                "thresholdSeconds", thresholdSeconds,
                "timestamp", timestamp.toString()
            );
        }
    }

    // =========================================================================
    // Subscriber Interface
    // =========================================================================

    /**
     * Interface for receiving security events.
     *
     * <p>Implementations should handle events quickly and not throw exceptions.
     * Long-running operations should be offloaded to a separate thread.
     */
    @FunctionalInterface
    public interface Subscriber {

        /**
         * Called when a security event is published.
         *
         * @param event the security event
         */
        void onEvent(SecurityEvent event);
    }

    /**
     * Get an unmodifiable view of current subscribers.
     *
     * @return list of current subscribers
     */
    public List<Subscriber> getSubscribers() {
        return Collections.unmodifiableList(subscribers);
    }

    /**
     * Reset the singleton instance. For testing only.
     */
    static void resetForTesting() {
        synchronized (SecurityEventBus.class) {
            instance = null;
        }
    }
}
