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

package org.yawlfoundation.yawl.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Toyota Production System style Andon Cord for production alerting.
 *
 * <p>Implements the Andon principle: immediately surface problems so they
 * can be addressed. Provides a centralized alert system with severity-based
 * response SLAs, escalation paths, and external integrations.</p>
 *
 * <h2>Alert Severity Taxonomy</h2>
 * <ul>
 *   <li><b>P0 (CRITICAL)</b> - &lt;1 min response: deadlock, JWKS stale, engine down</li>
 *   <li><b>P1 (HIGH)</b> - &lt;4 hours response: lock contention &gt;500ms, circuit breaker OPEN</li>
 *   <li><b>P2 (MEDIUM)</b> - &lt;24 hours response: queue depth &gt;70%, SLA breach</li>
 *   <li><b>P3 (LOW)</b> - Weekly report: interface latency, deprecated schemas</li>
 * </ul>
 *
 * <h2>Integration</h2>
 * <ul>
 *   <li>OTEL spans for distributed tracing</li>
 *   <li>Micrometer metrics: yawl_andon_alerts_fired (by severity, alert name)</li>
 *   <li>PagerDuty Events API v2 for on-call notification</li>
 *   <li>Slack Webhooks for team notification</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
public final class AndonCord {

    private static final Logger LOGGER = LogManager.getLogger(AndonCord.class);

    /**
     * Alert severity levels with response SLA definitions.
     */
    public enum Severity {
        /** P0 - CRITICAL: System-stopping, &lt;1 min response required */
        P0_CRITICAL(0, "P0", "CRITICAL", Duration.ofMinutes(1)),
        /** P1 - HIGH: Significant degradation, &lt;4 hours response required */
        P1_HIGH(1, "P1", "HIGH", Duration.ofHours(4)),
        /** P2 - MEDIUM: Elevated metrics, &lt;24 hours response required */
        P2_MEDIUM(2, "P2", "MEDIUM", Duration.ofHours(24)),
        /** P3 - LOW: Informational, weekly review */
        P3_LOW(3, "P3", "LOW", Duration.ofDays(7));

        private final int level;
        private final String code;
        private final String label;
        private final Duration responseSla;

        Severity(int level, String code, String label, Duration responseSla) {
            this.level = level;
            this.code = code;
            this.label = label;
            this.responseSla = responseSla;
        }

        public int getLevel() { return level; }
        public String getCode() { return code; }
        public String getLabel() { return label; }
        public Duration getResponseSla() { return responseSla; }
    }

    /**
     * Alert categories for classification and routing.
     */
    public enum Category {
        DEADLOCK("deadlock"),
        LOCK_CONTENTION("lock_contention"),
        JWKS_STALE("jwks_stale"),
        ENGINE_DOWN("engine_down"),
        CIRCUIT_BREAKER("circuit_breaker"),
        QUEUE_DEPTH("queue_depth"),
        SLA_BREACH("sla_breach"),
        INTERFACE_LATENCY("interface_latency"),
        DEPRECATED_SCHEMA("deprecated_schema"),
        RESOURCE_EXHAUSTION("resource_exhaustion");

        private final String value;

        Category(String value) { this.value = value; }

        public String getValue() { return value; }
    }

    /**
     * Alert state tracking.
     */
    public enum State {
        FIRING,
        ACKNOWLEDGED,
        RESOLVED
    }

    /**
     * Immutable alert record with full context.
     */
    public static final class Alert {
        private final String id;
        private final Severity severity;
        private final Category category;
        private final String name;
        private final String message;
        private final Map<String, Object> context;
        private final Instant createdAt;
        private final Instant acknowledgedAt;
        private final Instant resolvedAt;
        private final State state;
        private final Throwable cause;

        private Alert(Builder builder) {
            this.id = builder.id;
            this.severity = builder.severity;
            this.category = builder.category;
            this.name = builder.name;
            this.message = builder.message;
            this.context = Map.copyOf(builder.context);
            this.createdAt = builder.createdAt;
            this.acknowledgedAt = builder.acknowledgedAt;
            this.resolvedAt = builder.resolvedAt;
            this.state = builder.state;
            this.cause = builder.cause;
        }

        public String getId() { return id; }
        public Severity getSeverity() { return severity; }
        public Category getCategory() { return category; }
        public String getName() { return name; }
        public String getMessage() { return message; }
        public Map<String, Object> getContext() { return context; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getAcknowledgedAt() { return acknowledgedAt; }
        public Instant getResolvedAt() { return resolvedAt; }
        public State getState() { return state; }
        public Throwable getCause() { return cause; }

        public Duration getAge() {
            return Duration.between(createdAt, Instant.now());
        }

        public boolean isOverdue() {
            return state == State.FIRING && getAge().compareTo(severity.getResponseSla()) > 0;
        }

        public Builder toBuilder() {
            return new Builder()
                .id(id)
                .severity(severity)
                .category(category)
                .name(name)
                .message(message)
                .context(new ConcurrentHashMap<>(context))
                .createdAt(createdAt)
                .acknowledgedAt(acknowledgedAt)
                .resolvedAt(resolvedAt)
                .state(state)
                .cause(cause);
        }

        @Override
        public String toString() {
            return String.format("Alert[id=%s, severity=%s, category=%s, state=%s, name=%s]",
                id, severity.getCode(), category.getValue(), state, name);
        }
    }

    /**
     * Builder for creating alerts.
     */
    public static final class Builder {
        private String id = java.util.UUID.randomUUID().toString();
        private Severity severity = Severity.P3_LOW;
        private Category category = Category.RESOURCE_EXHAUSTION;
        private String name = "unknown";
        private String message = "";
        private Map<String, Object> context = new ConcurrentHashMap<>();
        private Instant createdAt = Instant.now();
        private Instant acknowledgedAt;
        private Instant resolvedAt;
        private State state = State.FIRING;
        private Throwable cause;

        public Builder id(String id) { this.id = Objects.requireNonNull(id); return this; }
        public Builder severity(Severity severity) { this.severity = Objects.requireNonNull(severity); return this; }
        public Builder category(Category category) { this.category = Objects.requireNonNull(category); return this; }
        public Builder name(String name) { this.name = Objects.requireNonNull(name); return this; }
        public Builder message(String message) { this.message = Objects.requireNonNull(message); return this; }
        public Builder context(Map<String, Object> context) { this.context = new ConcurrentHashMap<>(context); return this; }
        public Builder context(String key, Object value) { this.context.put(key, value); return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = Objects.requireNonNull(createdAt); return this; }
        public Builder acknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; return this; }
        public Builder resolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; return this; }
        public Builder state(State state) { this.state = Objects.requireNonNull(state); return this; }
        public Builder cause(Throwable cause) { this.cause = cause; return this; }

        public Alert build() {
            return new Alert(this);
        }
    }

    /**
     * Configuration for external integrations.
     */
    public static final class Configuration {
        private final String pagerDutyRoutingKey;
        private final String pagerDutyApiUrl;
        private final String slackWebhookUrl;
        private final Duration escalationInterval;
        private final boolean enableMetrics;
        private final boolean enableTracing;

        private Configuration(Builder builder) {
            this.pagerDutyRoutingKey = builder.pagerDutyRoutingKey;
            this.pagerDutyApiUrl = builder.pagerDutyApiUrl;
            this.slackWebhookUrl = builder.slackWebhookUrl;
            this.escalationInterval = builder.escalationInterval;
            this.enableMetrics = builder.enableMetrics;
            this.enableTracing = builder.enableTracing;
        }

        public String getPagerDutyRoutingKey() { return pagerDutyRoutingKey; }
        public String getPagerDutyApiUrl() { return pagerDutyApiUrl; }
        public String getSlackWebhookUrl() { return slackWebhookUrl; }
        public Duration getEscalationInterval() { return escalationInterval; }
        public boolean isMetricsEnabled() { return enableMetrics; }
        public boolean isTracingEnabled() { return enableTracing; }
        public boolean isPagerDutyConfigured() { return pagerDutyRoutingKey != null && !pagerDutyRoutingKey.isEmpty(); }
        public boolean isSlackConfigured() { return slackWebhookUrl != null && !slackWebhookUrl.isEmpty(); }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String pagerDutyRoutingKey;
            private String pagerDutyApiUrl = "https://events.pagerduty.com/v2/enqueue";
            private String slackWebhookUrl;
            private Duration escalationInterval = Duration.ofMinutes(5);
            private boolean enableMetrics = true;
            private boolean enableTracing = true;

            public Builder pagerDutyRoutingKey(String key) { this.pagerDutyRoutingKey = key; return this; }
            public Builder pagerDutyApiUrl(String url) { this.pagerDutyApiUrl = url; return this; }
            public Builder slackWebhookUrl(String url) { this.slackWebhookUrl = url; return this; }
            public Builder escalationInterval(Duration interval) { this.escalationInterval = interval; return this; }
            public Builder enableMetrics(boolean enable) { this.enableMetrics = enable; return this; }
            public Builder enableTracing(boolean enable) { this.enableTracing = enable; return this; }

            public Configuration build() { return new Configuration(this); }
        }
    }

    /**
     * Health matrix entry for real-time monitoring widgets.
     */
    public static final class HealthMatrixEntry {
        private final String component;
        private final Severity severity;
        private final String status;
        private final Instant lastUpdated;
        private final String message;

        public HealthMatrixEntry(String component, Severity severity, String status, String message) {
            this.component = component;
            this.severity = severity;
            this.status = status;
            this.lastUpdated = Instant.now();
            this.message = message;
        }

        public String getComponent() { return component; }
        public Severity getSeverity() { return severity; }
        public String getStatus() { return status; }
        public Instant getLastUpdated() { return lastUpdated; }
        public String getMessage() { return message; }
    }

    /**
     * Lock contention heat map entry.
     */
    public static final class LockContentionEntry {
        private final String lockName;
        private final long contentionCount;
        private final double avgWaitMs;
        private final double maxWaitMs;
        private final Instant lastContention;

        public LockContentionEntry(String lockName, long contentionCount,
                                   double avgWaitMs, double maxWaitMs, Instant lastContention) {
            this.lockName = lockName;
            this.contentionCount = contentionCount;
            this.avgWaitMs = avgWaitMs;
            this.maxWaitMs = maxWaitMs;
            this.lastContention = lastContention;
        }

        public String getLockName() { return lockName; }
        public long getContentionCount() { return contentionCount; }
        public double getAvgWaitMs() { return avgWaitMs; }
        public double getMaxWaitMs() { return maxWaitMs; }
        public Instant getLastContention() { return lastContention; }
    }

    /**
     * Circuit breaker status entry.
     */
    public static final class CircuitBreakerEntry {
        private final String interfaceName;
        private final String state;
        private final long failureCount;
        private final long successCount;
        private final Instant lastStateChange;

        public CircuitBreakerEntry(String interfaceName, String state,
                                   long failureCount, long successCount, Instant lastStateChange) {
            this.interfaceName = interfaceName;
            this.state = state;
            this.failureCount = failureCount;
            this.successCount = successCount;
            this.lastStateChange = lastStateChange;
        }

        public String getInterfaceName() { return interfaceName; }
        public String getState() { return state; }
        public long getFailureCount() { return failureCount; }
        public long getSuccessCount() { return successCount; }
        public Instant getLastStateChange() { return lastStateChange; }
    }

    // Singleton instance
    private static volatile AndonCord instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Configuration
    private final Configuration config;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    // OpenTelemetry
    private final Tracer tracer;
    private final Meter meter;
    private final LongCounter alertsFiredCounter;

    // Alert storage
    private final Map<String, Alert> activeAlerts = new ConcurrentHashMap<>();
    private final List<Alert> alertHistory = new CopyOnWriteArrayList<>();
    private final List<Consumer<Alert>> alertListeners = new CopyOnWriteArrayList<>();

    // Health matrix tracking
    private final Map<String, HealthMatrixEntry> healthMatrix = new ConcurrentHashMap<>();
    private final Map<String, LockContentionEntry> lockContentionMap = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerEntry> circuitBreakerMap = new ConcurrentHashMap<>();

    // Escalation tracking
    private final Map<String, Integer> escalationCount = new ConcurrentHashMap<>();
    private final AtomicBoolean escalationTaskRunning = new AtomicBoolean(false);

    /**
     * Private constructor - use getInstance().
     */
    private AndonCord(Configuration config) {
        this.config = Objects.requireNonNull(config);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "andon-escalation");
            t.setDaemon(true);
            return t;
        });

        // Initialize OpenTelemetry
        io.opentelemetry.api.OpenTelemetry otel = io.opentelemetry.api.GlobalOpenTelemetry.get();
        this.tracer = otel.getTracer("org.yawlfoundation.yawl.observability.andon", "6.0.0");
        this.meter = otel.getMeter("org.yawlfoundation.yawl.observability.andon");

        // Initialize metrics
        this.alertsFiredCounter = meter.counterBuilder("yawl_andon_alerts_fired")
            .setDescription("Count of Andon alerts fired by severity and category")
            .build();

        // Initialize health matrix
        initializeHealthMatrix();

        // Start escalation task
        startEscalationTask();

        LOGGER.info("AndonCord initialized with config: pagerDuty={}, slack={}, metrics={}, tracing={}",
            config.isPagerDutyConfigured(), config.isSlackConfigured(),
            config.isMetricsEnabled(), config.isTracingEnabled());
    }

    /**
     * Gets the singleton instance with default configuration.
     */
    public static AndonCord getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new AndonCord(Configuration.builder().build());
                }
            }
        }
        return instance;
    }

    /**
     * Initializes the singleton with custom configuration.
     */
    public static void initialize(Configuration config) {
        synchronized (INSTANCE_LOCK) {
            if (instance != null) {
                LOGGER.warn("AndonCord already initialized, shutting down and reinitializing");
                instance.shutdown();
            }
            instance = new AndonCord(config);
        }
    }

    /**
     * Shuts down the AndonCord and releases resources.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("AndonCord shut down");
    }

    /**
     * Pulls the Andon cord - fires an alert.
     *
     * @param severity the alert severity
     * @param alertName the alert name
     * @param context additional context (case_id, task_id, etc.)
     * @return the created alert
     */
    public Alert pull(Severity severity, String alertName, Map<String, Object> context) {
        Alert alert = new Builder()
            .severity(severity)
            .name(alertName)
            .message(String.format("Alert %s fired at %s", alertName, Instant.now()))
            .context(context != null ? context : Map.of())
            .build();

        return fireAlert(alert);
    }

    /**
     * Fires a P0 deadlock alert.
     *
     * @param caseId the case identifier
     * @param specId the specification identifier
     * @param deadlockedTasks the list of deadlocked task IDs
     * @return the created alert
     */
    public Alert deadlockDetected(String caseId, String specId, List<String> deadlockedTasks) {
        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("case_id", caseId);
        context.put("spec_id", specId);
        context.put("deadlocked_tasks", deadlockedTasks);
        context.put("deadlocked_task_count", deadlockedTasks.size());

        Alert alert = new Builder()
            .severity(Severity.P0_CRITICAL)
            .category(Category.DEADLOCK)
            .name("deadlock_detected")
            .message(String.format("Deadlock detected in case %s with %d tasks blocked",
                caseId, deadlockedTasks.size()))
            .context(context)
            .build();

        return fireAlert(alert);
    }

    /**
     * Fires a P1/P2 lock contention alert based on wait time.
     *
     * @param caseId the case identifier
     * @param lockName the lock name
     * @param waitMs the wait time in milliseconds
     * @return the created alert
     */
    public Alert lockContentionHigh(String caseId, String lockName, long waitMs) {
        Severity severity = waitMs >= 500 ? Severity.P1_HIGH : Severity.P2_MEDIUM;

        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("case_id", caseId);
        context.put("lock_name", lockName);
        context.put("wait_ms", waitMs);

        // Update lock contention heat map
        updateLockContentionMap(lockName, waitMs);

        Alert alert = new Builder()
            .severity(severity)
            .category(Category.LOCK_CONTENTION)
            .name("lock_contention_high")
            .message(String.format("Lock contention %.2fms on %s in case %s",
                (double) waitMs, lockName, caseId))
            .context(context)
            .build();

        return fireAlert(alert);
    }

    /**
     * Fires a P0 JWKS cache stale alert.
     *
     * @param cacheAgeSeconds the age of the JWKS cache in seconds
     * @return the created alert
     */
    public Alert jwksCacheStale(long cacheAgeSeconds) {
        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("cache_age_seconds", cacheAgeSeconds);
        context.put("cache_age_hours", cacheAgeSeconds / 3600.0);

        Alert alert = new Builder()
            .severity(Severity.P0_CRITICAL)
            .category(Category.JWKS_STALE)
            .name("jwks_cache_stale")
            .message(String.format("JWKS cache is stale: %d seconds old", cacheAgeSeconds))
            .context(context)
            .build();

        // Update health matrix
        updateHealthMatrixEntry("jwks", Severity.P0_CRITICAL, "STALE",
            String.format("Cache %ds old", cacheAgeSeconds));

        return fireAlert(alert, false);
    }

    /**
     * Fires a P1 circuit breaker open alert.
     *
     * @param interfaceName the interface name
     * @return the created alert
     */
    public Alert circuitBreakerOpen(String interfaceName) {
        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("interface_name", interfaceName);

        // Update circuit breaker status
        updateCircuitBreakerMap(interfaceName, "OPEN", 0, 0);

        Alert alert = new Builder()
            .severity(Severity.P1_HIGH)
            .category(Category.CIRCUIT_BREAKER)
            .name("circuit_breaker_open")
            .message(String.format("Circuit breaker OPEN for interface %s", interfaceName))
            .context(context)
            .build();

        // Update health matrix
        updateHealthMatrixEntry(interfaceName, Severity.P1_HIGH, "OPEN",
            "Circuit breaker tripped");

        return fireAlert(alert, false);
    }

    /**
     * Fires a P2 queue depth alert.
     *
     * @param queueDepth the current queue depth
     * @param queueCapacity the queue capacity
     * @return the created alert
     */
    public Alert queueDepthExceeded(long queueDepth, long queueCapacity) {
        double percentage = (double) queueDepth / queueCapacity * 100;

        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("queue_depth", queueDepth);
        context.put("queue_capacity", queueCapacity);
        context.put("percentage", percentage);

        Alert alert = new Builder()
            .severity(Severity.P2_MEDIUM)
            .category(Category.QUEUE_DEPTH)
            .name("queue_depth_exceeded")
            .message(String.format("Queue depth at %.1f%% capacity (%d/%d)",
                percentage, queueDepth, queueCapacity))
            .context(context)
            .build();

        // Update health matrix
        updateHealthMatrixEntry("queue", Severity.P2_MEDIUM, "WARNING",
            String.format("%.1f%% full", percentage));

        return fireAlert(alert, false);
    }

    /**
     * Fires a P2 SLA breach alert.
     *
     * @param caseId the case identifier
     * @param slaType the SLA type
     * @param actualDuration the actual duration
     * @param slaDuration the SLA duration
     * @return the created alert
     */
    public Alert slaBreach(String caseId, String slaType, Duration actualDuration, Duration slaDuration) {
        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("case_id", caseId);
        context.put("sla_type", slaType);
        context.put("actual_ms", actualDuration.toMillis());
        context.put("sla_ms", slaDuration.toMillis());
        context.put("breach_ms", actualDuration.toMillis() - slaDuration.toMillis());

        Alert alert = new Builder()
            .severity(Severity.P2_MEDIUM)
            .category(Category.SLA_BREACH)
            .name("sla_breach")
            .message(String.format("SLA breach for %s: actual %dms vs SLA %dms",
                slaType, actualDuration.toMillis(), slaDuration.toMillis()))
            .context(context)
            .build();

        return fireAlert(alert);
    }

    /**
     * Fires a P3 interface latency alert.
     *
     * @param interfaceName the interface name
     * @param latencyMs the latency in milliseconds
     * @return the created alert
     */
    public Alert interfaceLatency(String interfaceName, long latencyMs) {
        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("interface_name", interfaceName);
        context.put("latency_ms", latencyMs);

        Alert alert = new Builder()
            .severity(Severity.P3_LOW)
            .category(Category.INTERFACE_LATENCY)
            .name("interface_latency")
            .message(String.format("Interface %s latency: %dms", interfaceName, latencyMs))
            .context(context)
            .build();

        return fireAlert(alert);
    }

    /**
     * Fires a P3 deprecated schema alert.
     *
     * @param schemaVersion the deprecated schema version
     * @param specId the specification identifier
     * @return the created alert
     */
    public Alert deprecatedSchema(String schemaVersion, String specId) {
        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("schema_version", schemaVersion);
        context.put("spec_id", specId);

        Alert alert = new Builder()
            .severity(Severity.P3_LOW)
            .category(Category.DEPRECATED_SCHEMA)
            .name("deprecated_schema")
            .message(String.format("Deprecated schema %s used in spec %s", schemaVersion, specId))
            .context(context)
            .build();

        return fireAlert(alert);
    }

    /**
     * Fires a P0 engine down alert.
     *
     * @param engineId the engine identifier
     * @param reason the reason for the engine being down
     * @return the created alert
     */
    public Alert engineDown(String engineId, String reason) {
        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("engine_id", engineId);
        context.put("reason", reason);

        Alert alert = new Builder()
            .severity(Severity.P0_CRITICAL)
            .category(Category.ENGINE_DOWN)
            .name("engine_down")
            .message(String.format("Engine %s is DOWN: %s", engineId, reason))
            .context(context)
            .build();

        // Update health matrix
        updateHealthMatrixEntry("engine", Severity.P0_CRITICAL, "DOWN", reason);

        return fireAlert(alert, false);
    }

    /**
     * Acknowledges an alert.
     *
     * @param alertId the alert ID
     * @return the updated alert, or null if not found
     */
    public Alert acknowledge(String alertId) {
        Alert alert = activeAlerts.get(alertId);
        if (alert == null) {
            LOGGER.warn("Attempted to acknowledge non-existent alert: {}", alertId);
            return null;
        }

        Alert acknowledged = alert.toBuilder()
            .state(State.ACKNOWLEDGED)
            .acknowledgedAt(Instant.now())
            .build();

        activeAlerts.put(alertId, acknowledged);
        notifyListeners(acknowledged);

        LOGGER.info("Alert acknowledged: {}", acknowledged);

        // Send acknowledgment to external systems
        sendAcknowledgmentToExternalSystems(acknowledged);

        return acknowledged;
    }

    /**
     * Resolves an alert.
     *
     * @param alertId the alert ID
     * @return the resolved alert, or null if not found
     */
    public Alert resolve(String alertId) {
        Alert alert = activeAlerts.remove(alertId);
        if (alert == null) {
            LOGGER.warn("Attempted to resolve non-existent alert: {}", alertId);
            return null;
        }

        Alert resolved = alert.toBuilder()
            .state(State.RESOLVED)
            .resolvedAt(Instant.now())
            .build();

        alertHistory.add(resolved);
        escalationCount.remove(alertId);
        notifyListeners(resolved);

        LOGGER.info("Alert resolved: {}", resolved);

        // Send resolution to external systems
        sendResolutionToExternalSystems(resolved);

        // Update health matrix if applicable
        updateHealthMatrixOnResolve(resolved);

        return resolved;
    }

    /**
     * Gets all active alerts.
     */
    public List<Alert> getActiveAlerts() {
        return List.copyOf(activeAlerts.values());
    }

    /**
     * Gets active alerts by severity.
     */
    public List<Alert> getActiveAlertsBySeverity(Severity severity) {
        return activeAlerts.values().stream()
            .filter(a -> a.getSeverity() == severity)
            .toList();
    }

    /**
     * Gets overdue alerts (firing beyond SLA).
     */
    public List<Alert> getOverdueAlerts() {
        return activeAlerts.values().stream()
            .filter(Alert::isOverdue)
            .toList();
    }

    /**
     * Gets alert history (last 1000 alerts).
     */
    public List<Alert> getAlertHistory() {
        int size = alertHistory.size();
        return alertHistory.subList(Math.max(0, size - 1000), size);
    }

    /**
     * Gets the health matrix for real-time monitoring widgets.
     */
    public Map<String, HealthMatrixEntry> getHealthMatrix() {
        return Map.copyOf(healthMatrix);
    }

    /**
     * Gets the lock contention heat map.
     */
    public Map<String, LockContentionEntry> getLockContentionHeatMap() {
        return Map.copyOf(lockContentionMap);
    }

    /**
     * Gets the circuit breaker status.
     */
    public Map<String, CircuitBreakerEntry> getCircuitBreakerStatus() {
        return Map.copyOf(circuitBreakerMap);
    }

    /**
     * Registers an alert listener.
     */
    public void addListener(Consumer<Alert> listener) {
        alertListeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Removes an alert listener.
     */
    public void removeListener(Consumer<Alert> listener) {
        alertListeners.remove(listener);
    }

    // ==================== Private Implementation ====================

    private void initializeHealthMatrix() {
        healthMatrix.put("engine", new HealthMatrixEntry("engine", Severity.P3_LOW, "UP", "Operational"));
        healthMatrix.put("cases", new HealthMatrixEntry("cases", Severity.P3_LOW, "UP", "Normal"));
        healthMatrix.put("locks", new HealthMatrixEntry("locks", Severity.P3_LOW, "UP", "No contention"));
        healthMatrix.put("jwks", new HealthMatrixEntry("jwks", Severity.P3_LOW, "UP", "Cache fresh"));
        healthMatrix.put("db", new HealthMatrixEntry("db", Severity.P3_LOW, "UP", "Connected"));
    }

    private Alert fireAlert(Alert alert, boolean updateHealthMatrix) {
        // Store alert
        activeAlerts.put(alert.getId(), alert);
        escalationCount.put(alert.getId(), 0);

        // Create OTEL span
        Span span = createAlertSpan(alert);
        try {
            // Record metrics
            if (config.isMetricsEnabled()) {
                recordMetrics(alert);
            }

            // Log the alert
            logAlert(alert);

            // Send to external systems
            sendToExternalSystems(alert, span);

            // Notify listeners
            notifyListeners(alert);

            // Update health matrix only for generic alerts
            if (updateHealthMatrix) {
                updateHealthMatrixOnAlert(alert);
            }

            return alert;
        } finally {
            span.end();
        }
    }

    private Alert fireAlert(Alert alert) {
        return fireAlert(alert, true);
    }

    private Span createAlertSpan(Alert alert) {
        if (!config.isTracingEnabled()) {
            return Span.getInvalid();
        }

        AttributesBuilder attrBuilder = Attributes.builder()
            .put("yawl.andon.alert.id", alert.getId())
            .put("yawl.andon.alert.severity", alert.getSeverity().getCode())
            .put("yawl.andon.alert.category", alert.getCategory().getValue())
            .put("yawl.andon.alert.name", alert.getName())
            .put("yawl.andon.alert.message", alert.getMessage())
            .put("yawl.andon.alert.state", alert.getState().name());

        // Add context attributes
        for (Map.Entry<String, Object> entry : alert.getContext().entrySet()) {
            attrBuilder.put("yawl.andon.context." + entry.getKey(),
                String.valueOf(entry.getValue()));
        }

        Span span = tracer.spanBuilder("andon.alert." + alert.getName())
            .setSpanKind(SpanKind.INTERNAL)
            .setAllAttributes(attrBuilder.build())
            .startSpan();

        if (alert.getSeverity() == Severity.P0_CRITICAL || alert.getSeverity() == Severity.P1_HIGH) {
            span.setStatus(StatusCode.ERROR, alert.getMessage());
        }

        return span;
    }

    private void recordMetrics(Alert alert) {
        Attributes attributes = Attributes.builder()
            .put("severity", alert.getSeverity().getCode())
            .put("category", alert.getCategory().getValue())
            .put("alert_name", alert.getName())
            .build();

        alertsFiredCounter.add(1, attributes);
    }

    private void logAlert(Alert alert) {
        String logMessage = String.format("[ANDON-%s] [%s] %s - %s",
            alert.getSeverity().getCode(), alert.getCategory().getValue(),
            alert.getName(), alert.getMessage());

        switch (alert.getSeverity()) {
            case P0_CRITICAL -> LOGGER.fatal(logMessage);
            case P1_HIGH -> LOGGER.error(logMessage);
            case P2_MEDIUM -> LOGGER.warn(logMessage);
            case P3_LOW -> LOGGER.info(logMessage);
        }

        if (alert.getCause() != null) {
            LOGGER.error("Alert cause:", alert.getCause());
        }
    }

    private void sendToExternalSystems(Alert alert, Span span) {
        // Send to PagerDuty for P0/P1
        if (alert.getSeverity().getLevel() <= Severity.P1_HIGH.getLevel() && config.isPagerDutyConfigured()) {
            sendToPagerDuty(alert, "trigger", span);
        }

        // Send to Slack for all severities
        if (config.isSlackConfigured()) {
            sendToSlack(alert, span);
        }
    }

    private void sendAcknowledgmentToExternalSystems(Alert alert) {
        if (config.isPagerDutyConfigured()) {
            sendToPagerDuty(alert, "acknowledge", Span.getInvalid());
        }
    }

    private void sendResolutionToExternalSystems(Alert alert) {
        if (config.isPagerDutyConfigured()) {
            sendToPagerDuty(alert, "resolve", Span.getInvalid());
        }
    }

    private void sendToPagerDuty(Alert alert, String action, Span span) {
        if (!config.isPagerDutyConfigured()) {
            return;
        }

        String dedupKey = "yawl-" + alert.getCategory().getValue() + "-" + alert.getName();

        StringBuilder payloadBuilder = new StringBuilder();
        payloadBuilder.append("{\"routing_key\":\"").append(config.getPagerDutyRoutingKey()).append("\",");
        payloadBuilder.append("\"dedup_key\":\"").append(dedupKey).append("\",");
        payloadBuilder.append("\"event_action\":\"").append(action).append("\",");

        if (!"resolve".equals(action)) {
            payloadBuilder.append("\"payload\":{");
            payloadBuilder.append("\"summary\":\"").append(escapeJson(alert.getMessage())).append("\",");
            payloadBuilder.append("\"severity\":\"").append(toPagerDutySeverity(alert.getSeverity())).append("\",");
            payloadBuilder.append("\"source\":\"yawl-engine\",");
            payloadBuilder.append("\"timestamp\":\"").append(alert.getCreatedAt().toString()).append("\",");
            payloadBuilder.append("\"custom_details\":{");
            payloadBuilder.append("\"category\":\"").append(alert.getCategory().getValue()).append("\",");
            payloadBuilder.append("\"context\":").append(contextToJson(alert.getContext()));
            payloadBuilder.append("}}");
        }
        payloadBuilder.append("}");

        String payload = payloadBuilder.toString();

        sendHttpPost(config.getPagerDutyApiUrl(), payload, "application/json", span);
    }

    private void sendToSlack(Alert alert, Span span) {
        if (!config.isSlackConfigured()) {
            return;
        }

        String color = toSlackColor(alert.getSeverity());
        StringBuilder payloadBuilder = new StringBuilder();
        payloadBuilder.append("{\"attachments\":[{");
        payloadBuilder.append("\"color\":\"").append(color).append("\",");
        payloadBuilder.append("\"title\":\"[ANDON-").append(alert.getSeverity().getCode()).append("] ").append(alert.getName()).append("\",");
        payloadBuilder.append("\"text\":\"").append(escapeJson(alert.getMessage())).append("\",");
        payloadBuilder.append("\"fields\":[");

        // Add context fields
        int fieldCount = 0;
        for (Map.Entry<String, Object> entry : alert.getContext().entrySet()) {
            if (fieldCount > 0) payloadBuilder.append(",");
            payloadBuilder.append("{\"title\":\"").append(entry.getKey()).append("\",");
            payloadBuilder.append("\"value\":\"").append(escapeJson(String.valueOf(entry.getValue()))).append("\",");
            payloadBuilder.append("\"short\":true}");
            fieldCount++;
            if (fieldCount >= 4) break; // Limit to 4 fields
        }

        payloadBuilder.append("],");
        payloadBuilder.append("\"footer\":\"YAWL AndonCord\",");
        payloadBuilder.append("\"ts\":").append(alert.getCreatedAt().getEpochSecond());
        payloadBuilder.append("}]}");

        String payload = payloadBuilder.toString();

        sendHttpPost(config.getSlackWebhookUrl(), payload, "application/json", span);
    }

    private void sendHttpPost(String url, String payload, String contentType, Span span) {
        scheduler.submit(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(10))
                    .build();

                HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    LOGGER.warn("Failed to send alert to {}: status {}", url, response.statusCode());
                    if (span.isRecording()) {
                        span.addEvent("notification_failed", Attributes.builder()
                            .put("url", url)
                            .put("status_code", response.statusCode())
                            .build());
                    }
                } else {
                    LOGGER.debug("Alert sent to {} successfully", url);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to send alert to {}: {}", url, e.getMessage());
                if (span.isRecording()) {
                    span.recordException(e);
                }
            }
        });
    }

    private void notifyListeners(Alert alert) {
        for (Consumer<Alert> listener : alertListeners) {
            try {
                listener.accept(alert);
            } catch (Exception e) {
                LOGGER.error("Alert listener threw exception", e);
            }
        }
    }

    private void updateHealthMatrixOnAlert(Alert alert) {
        String component = mapCategoryToComponent(alert.getCategory());
        if (component != null) {
            updateHealthMatrixEntry(component, alert.getSeverity(), "ALERT", alert.getMessage());
        }
    }

    private void updateHealthMatrixOnResolve(Alert alert) {
        String component = mapCategoryToComponent(alert.getCategory());
        if (component != null) {
            updateHealthMatrixEntry(component, Severity.P3_LOW, "UP", "Resolved");
        }
    }

    private void updateHealthMatrixEntry(String component, Severity severity, String status, String message) {
        healthMatrix.put(component, new HealthMatrixEntry(component, severity, status, message));
    }

    private String mapCategoryToComponent(Category category) {
        return switch (category) {
            case DEADLOCK, LOCK_CONTENTION -> "locks";
            case JWKS_STALE -> "jwks";
            case ENGINE_DOWN -> "engine";
            case CIRCUIT_BREAKER, INTERFACE_LATENCY -> "interfaces";
            case QUEUE_DEPTH -> "queue";
            case RESOURCE_EXHAUSTION -> "resources";
            default -> null;
        };
    }

    private void updateLockContentionMap(String lockName, long waitMs) {
        LockContentionEntry existing = lockContentionMap.get(lockName);
        if (existing == null) {
            lockContentionMap.put(lockName, new LockContentionEntry(
                lockName, 1, waitMs, waitMs, Instant.now()));
        } else {
            long newCount = existing.getContentionCount() + 1;
            double newAvg = (existing.getAvgWaitMs() * existing.getContentionCount() + waitMs) / newCount;
            double newMax = Math.max(existing.getMaxWaitMs(), waitMs);
            lockContentionMap.put(lockName, new LockContentionEntry(
                lockName, newCount, newAvg, newMax, Instant.now()));
        }
    }

    private void updateCircuitBreakerMap(String interfaceName, String state, long failureCount, long successCount) {
        CircuitBreakerEntry existing = circuitBreakerMap.get(interfaceName);
        long newFailureCount = existing != null ? existing.getFailureCount() + failureCount : failureCount;
        long newSuccessCount = existing != null ? existing.getSuccessCount() + successCount : successCount;

        circuitBreakerMap.put(interfaceName, new CircuitBreakerEntry(
            interfaceName, state, newFailureCount, newSuccessCount, Instant.now()));
    }

    private void startEscalationTask() {
        if (!escalationTaskRunning.compareAndSet(false, true)) {
            return;
        }

        final long intervalMs = config.getEscalationInterval().toMillis();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                escalateOverdueAlerts();
            } catch (Exception e) {
                LOGGER.error("Error during alert escalation", e);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void escalateOverdueAlerts() {
        for (Alert alert : activeAlerts.values()) {
            if (alert.getState() == State.FIRING && alert.isOverdue()) {
                int count = escalationCount.getOrDefault(alert.getId(), 0) + 1;
                escalationCount.put(alert.getId(), count);

                LOGGER.warn("Alert {} overdue (escalation #{}) - {} past SLA of {}",
                    alert.getId(), count, alert.getAge(), alert.getSeverity().getResponseSla());

                // Re-notify external systems
                if (config.isPagerDutyConfigured() && alert.getSeverity().getLevel() <= Severity.P1_HIGH.getLevel()) {
                    sendToPagerDuty(alert, "trigger", Span.getInvalid());
                }
            }
        }
    }

    // ==================== Utility Methods ====================

    private static String toPagerDutySeverity(Severity severity) {
        return switch (severity) {
            case P0_CRITICAL -> "critical";
            case P1_HIGH -> "error";
            case P2_MEDIUM -> "warning";
            case P3_LOW -> "info";
        };
    }

    private static String toSlackColor(Severity severity) {
        return switch (severity) {
            case P0_CRITICAL -> "#FF0000"; // Red
            case P1_HIGH -> "#FFA500";     // Orange
            case P2_MEDIUM -> "#FFFF00";   // Yellow
            case P3_LOW -> "#36A64F";      // Green
        };
    }

    private static String escapeJson(String value) {
        if (value == null) return "null";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private static String contextToJson(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append("\"").append(escapeJson(String.valueOf(entry.getValue()))).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
