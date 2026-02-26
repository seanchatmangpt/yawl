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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Unified SLO Integration Service - Facade for all SLO components.
 *
 * <p>Provides a single entry point for all SLO-related operations with:
 * <ul>
 *   <li>Event queue with batching (100 events, 1s flush)</li>
 *   <li>Builder pattern for configuration</li>
 *   <li>Integration with all observability components</li>
 *   <li>Automatic alert generation</li>
 * </ul>
 *
 * <h2>Components Integrated</h2>
 * <ul>
 *   <li>{@link SLOTracker} - Compliance tracking</li>
 *   <li>{@link SLODashboard} - Real-time dashboard</li>
 *   <li>{@link SLOPredictiveAnalytics} - Predictive analytics</li>
 *   <li>{@link SLOAlertManager} - Alert management</li>
 *   <li>{@link LockContentionTracker} - Lock contention tracking</li>
 *   <li>{@link AndonCord} - Alert escalation</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create service with builder
 * SLOIntegrationService service = SLOIntegrationService.builder()
 *     .batchSize(100)
 *     .flushInterval(Duration.ofSeconds(1))
 *     .enablePredictiveAnalytics(true)
 *     .build();
 *
 * // Start service
 * service.start();
 *
 * // Record events
 * service.recordEvent(SLOIntegrationService.EventType.TASK_COMPLETED, Map.of(
 *     "task_id", "task-123",
 *     "duration_ms", 45000
 * ));
 *
 * // Get compliance snapshot
 * ComplianceSnapshot snapshot = service.getComplianceSnapshot();
 *
 * // Check predictions
 * List<BreachPrediction> predictions = service.getBreachPredictions();
 *
 * // Stop service
 * service.stop();
 * }</pre>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
public final class SLOIntegrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SLOIntegrationService.class);

    // Default configuration
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofSeconds(1);
    private static final int DEFAULT_HISTORY_SIZE = 1000;

    /**
     * Event types for SLO tracking.
     */
    public enum EventType {
        CASE_STARTED,
        CASE_COMPLETED,
        CASE_CANCELLED,
        TASK_STARTED,
        TASK_COMPLETED,
        TASK_FAILED,
        TASK_TIMEOUT,
        QUEUE_ENQUEUED,
        QUEUE_DEQUEUED,
        LOCK_ACQUIRED,
        LOCK_RELEASED,
        VT_PINNED,
        VT_UNPINNED,
        ERROR_OCCURRED,
        RETRY_ATTEMPTED,
        CIRCUIT_BREAKER_OPENED,
        CIRCUIT_BREAKER_CLOSED
    }

    /**
     * SLO event for batching.
     */
    public static final class SLOEvent {
        private final EventType type;
        private final Instant timestamp;
        private final Map<String, Object> context;
        private final boolean metSLO;
        private final long durationMs;

        public SLOEvent(EventType type, Map<String, Object> context, boolean metSLO, long durationMs) {
            this.type = type;
            this.timestamp = Instant.now();
            this.context = context != null ? Map.copyOf(context) : Map.of();
            this.metSLO = metSLO;
            this.durationMs = durationMs;
        }

        public EventType getType() { return type; }
        public Instant getTimestamp() { return timestamp; }
        public Map<String, Object> getContext() { return context; }
        public boolean isMetSLO() { return metSLO; }
        public long getDurationMs() { return durationMs; }
    }

    /**
     * Aggregated compliance snapshot.
     */
    public static final class ComplianceSnapshot {
        private final Instant timestamp;
        private final Map<SLOTracker.SLOType, Double> complianceRates;
        private final Map<SLOTracker.SLOType, SLOTracker.ComplianceStatus> statuses;
        private final Map<SLOTracker.SLOType, SLOTracker.TrendDirection> trends;
        private final int totalEvents;
        private final int compliantEvents;
        private final int violatingEvents;
        private final double overallCompliance;

        public ComplianceSnapshot(Map<SLOTracker.SLOType, Double> complianceRates,
                                 Map<SLOTracker.SLOType, SLOTracker.ComplianceStatus> statuses,
                                 Map<SLOTracker.SLOType, SLOTracker.TrendDirection> trends,
                                 int totalEvents, int compliantEvents, int violatingEvents) {
            this.timestamp = Instant.now();
            this.complianceRates = Map.copyOf(complianceRates);
            this.statuses = Map.copyOf(statuses);
            this.trends = Map.copyOf(trends);
            this.totalEvents = totalEvents;
            this.compliantEvents = compliantEvents;
            this.violatingEvents = violatingEvents;
            this.overallCompliance = complianceRates.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        }

        public Instant getTimestamp() { return timestamp; }
        public Map<SLOTracker.SLOType, Double> getComplianceRates() { return complianceRates; }
        public Map<SLOTracker.SLOType, SLOTracker.ComplianceStatus> getStatuses() { return statuses; }
        public Map<SLOTracker.SLOType, SLOTracker.TrendDirection> getTrends() { return trends; }
        public int getTotalEvents() { return totalEvents; }
        public int getCompliantEvents() { return compliantEvents; }
        public int getViolatingEvents() { return violatingEvents; }
        public double getOverallCompliance() { return overallCompliance; }

        public double getComplianceRate(SLOTracker.SLOType type) {
            return complianceRates.getOrDefault(type, 0.0);
        }

        public SLOTracker.ComplianceStatus getStatus(SLOTracker.SLOType type) {
            return statuses.getOrDefault(type, SLOTracker.ComplianceStatus.COMPLIANT);
        }
    }

    /**
     * Builder for SLOIntegrationService configuration.
     */
    public static final class Builder {
        private int batchSize = DEFAULT_BATCH_SIZE;
        private Duration flushInterval = DEFAULT_FLUSH_INTERVAL;
        private int historySize = DEFAULT_HISTORY_SIZE;
        private boolean enablePredictiveAnalytics = true;
        private boolean enableDashboard = true;
        private boolean enableAlerts = true;
        private MeterRegistry meterRegistry = Metrics.globalRegistry;

        public Builder batchSize(int size) {
            this.batchSize = size;
            return this;
        }

        public Builder flushInterval(Duration interval) {
            this.flushInterval = interval;
            return this;
        }

        public Builder historySize(int size) {
            this.historySize = size;
            return this;
        }

        public Builder enablePredictiveAnalytics(boolean enable) {
            this.enablePredictiveAnalytics = enable;
            return this;
        }

        public Builder enableDashboard(boolean enable) {
            this.enableDashboard = enable;
            return this;
        }

        public Builder enableAlerts(boolean enable) {
            this.enableAlerts = enable;
            return this;
        }

        public Builder meterRegistry(MeterRegistry registry) {
            this.meterRegistry = registry;
            return this;
        }

        public SLOIntegrationService build() {
            return new SLOIntegrationService(this);
        }
    }

    // Configuration
    private final int batchSize;
    private final Duration flushInterval;
    private final boolean predictiveAnalyticsEnabled;
    private final boolean dashboardEnabled;
    private final boolean alertsEnabled;

    // Components
    private final SLOTracker sloTracker;
    private final SLODashboard dashboard;
    private final SLOPredictiveAnalytics predictiveAnalytics;
    private final SLOAlertManager alertManager;
    private final LockContentionTracker lockContentionTracker;
    private final AndonCord andonCord;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    // Event queue and batching
    private final ConcurrentLinkedQueue<SLOEvent> eventQueue;
    private final List<Consumer<SLOEvent>> eventListeners;
    private final AtomicBoolean running;
    private ScheduledExecutorService scheduler;
    private final ReentrantLock flushLock;

    // Metrics
    private final AtomicLong eventsProcessed = new AtomicLong();
    private final AtomicLong batchesFlushed = new AtomicLong();
    private final AtomicLong alertsTriggered = new AtomicLong();

    // Singleton instance
    private static volatile SLOIntegrationService instance;
    private static final ReentrantLock INSTANCE_LOCK = new ReentrantLock();

    private SLOIntegrationService(Builder builder) {
        this.batchSize = builder.batchSize;
        this.flushInterval = builder.flushInterval;
        this.predictiveAnalyticsEnabled = builder.enablePredictiveAnalytics;
        this.dashboardEnabled = builder.enableDashboard;
        this.alertsEnabled = builder.enableAlerts;

        this.meterRegistry = builder.meterRegistry;
        this.tracer = io.opentelemetry.api.GlobalOpenTelemetry.get()
            .getTracer("org.yawlfoundation.yawl.observability.SLOIntegrationService", "6.0.0");

        // Initialize components
        this.sloTracker = new SLOTracker(meterRegistry);
        this.lockContentionTracker = LockContentionTracker.getInstance();
        this.andonCord = AndonCord.getInstance();
        this.alertManager = SLOAlertManager.getInstance();

        if (dashboardEnabled) {
            this.dashboard = SLODashboard.builder()
                .sloTracker(sloTracker)
                .historySize(builder.historySize)
                .build();
        } else {
            this.dashboard = null;
        }

        if (predictiveAnalyticsEnabled) {
            this.predictiveAnalytics = new SLOPredictiveAnalytics();
        } else {
            this.predictiveAnalytics = null;
        }

        // Initialize event handling
        this.eventQueue = new ConcurrentLinkedQueue<>();
        this.eventListeners = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);
        this.flushLock = new ReentrantLock();

        LOGGER.info("SLOIntegrationService created: batchSize={}, flushInterval={}s, predictive={}, dashboard={}",
            batchSize, flushInterval.toSeconds(), predictiveAnalyticsEnabled, dashboardEnabled);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Gets the singleton instance with default configuration.
     */
    public static SLOIntegrationService getInstance() {
        if (instance == null) {
            INSTANCE_LOCK.lock();
            try {
                if (instance == null) {
                    instance = builder().build();
                }
            } finally {
                INSTANCE_LOCK.unlock();
            }
        }
        return instance;
    }

    /**
     * Starts the integration service.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "slo-integration");
                t.setDaemon(true);
                return t;
            });

            // Schedule batch flush
            scheduler.scheduleAtFixedRate(
                this::flushBatch,
                flushInterval.toMillis(),
                flushInterval.toMillis(),
                TimeUnit.MILLISECONDS
            );

            // Schedule predictive analytics update
            if (predictiveAnalyticsEnabled && predictiveAnalytics != null) {
                scheduler.scheduleAtFixedRate(
                    this::updatePredictions,
                    Duration.ofMinutes(5).toMillis(),
                    Duration.ofMinutes(5).toMillis(),
                    TimeUnit.MILLISECONDS
                );
            }

            // Start dashboard if enabled
            if (dashboardEnabled && dashboard != null) {
                dashboard.start();
            }

            LOGGER.info("SLOIntegrationService started");
        }
    }

    /**
     * Stops the integration service.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            // Flush remaining events
            flushBatch();

            // Stop dashboard
            if (dashboard != null) {
                dashboard.stop();
            }

            // Shutdown scheduler
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            LOGGER.info("SLOIntegrationService stopped. Stats: eventsProcessed={}, batchesFlushed={}, alertsTriggered={}",
                eventsProcessed.get(), batchesFlushed.get(), alertsTriggered.get());
        }
    }

    /**
     * Checks if the service is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Records an SLO event.
     *
     * @param type the event type
     * @param context additional context
     */
    public void recordEvent(EventType type, Map<String, Object> context) {
        recordEvent(type, context, true, 0);
    }

    /**
     * Records an SLO event with duration and SLO compliance.
     *
     * @param type the event type
     * @param context additional context
     * @param metSLO whether the event met SLO targets
     * @param durationMs the duration in milliseconds
     */
    public void recordEvent(EventType type, Map<String, Object> context, boolean metSLO, long durationMs) {
        SLOEvent event = new SLOEvent(type, context, metSLO, durationMs);
        eventQueue.offer(event);

        // Notify listeners immediately
        notifyListeners(event);

        // Flush if batch size reached
        if (eventQueue.size() >= batchSize) {
            scheduler.submit(this::flushBatch);
        }
    }

    /**
     * Flushes the current batch of events.
     */
    private void flushBatch() {
        flushLock.lock();
        try {
            List<SLOEvent> batch = new ArrayList<>();
            SLOEvent event;
            while ((event = eventQueue.poll()) != null) {
                batch.add(event);
            }

            if (batch.isEmpty()) {
                return;
            }

            // Process batch
            Span span = tracer.spanBuilder("slo.batch.process")
                .setAttribute("batch.size", batch.size())
                .startSpan();

            try {
                processBatch(batch);
                batchesFlushed.incrementAndGet();
                eventsProcessed.addAndGet(batch.size());
            } finally {
                span.end();
            }
        } finally {
            flushLock.unlock();
        }
    }

    /**
     * Processes a batch of events.
     */
    private void processBatch(List<SLOEvent> batch) {
        for (SLOEvent event : batch) {
            // Map event type to SLO type and record
            SLOTracker.SLOType sloType = mapToSLOType(event.getType());
            if (sloType != null) {
                sloTracker.recordMetric(sloType, event.isMetSLO(), event.getDurationMs());
            }

            // Update predictive analytics
            if (predictiveAnalytics != null && sloType != null) {
                double compliance = sloTracker.getComplianceRate(sloType, Duration.ofHours(1));
                predictiveAnalytics.addDataPoint(sloType, compliance, event.getTimestamp());
            }

            // Handle specific event types
            handleSpecialEvents(event);
        }

        // Check for alerts after batch processing
        if (alertsEnabled) {
            checkAndGenerateAlerts();
        }
    }

    /**
     * Maps event types to SLO types.
     */
    private SLOTracker.SLOType mapToSLOType(EventType eventType) {
        return switch (eventType) {
            case CASE_STARTED, CASE_COMPLETED, CASE_CANCELLED -> SLOTracker.SLOType.CASE_COMPLETION;
            case TASK_STARTED, TASK_COMPLETED, TASK_FAILED, TASK_TIMEOUT -> SLOTracker.SLOType.TASK_EXECUTION;
            case QUEUE_ENQUEUED, QUEUE_DEQUEUED -> SLOTracker.SLOType.QUEUE_RESPONSE;
            case LOCK_ACQUIRED, LOCK_RELEASED -> SLOTracker.SLOType.LOCK_CONTENTION;
            case VT_PINNED, VT_UNPINNED -> SLOTracker.SLOType.VT_PINNING;
            default -> null;
        };
    }

    /**
     * Handles special events that need additional processing.
     */
    private void handleSpecialEvents(SLOEvent event) {
        switch (event.getType()) {
            case LOCK_ACQUIRED -> {
                String lockName = (String) event.getContext().getOrDefault("lock_name", "unknown");
                lockContentionTracker.recordContention(lockName, event.getDurationMs());
            }
            case CIRCUIT_BREAKER_OPENED -> {
                String interfaceName = (String) event.getContext().getOrDefault("interface", "unknown");
                andonCord.circuitBreakerOpen(interfaceName);
            }
            case VT_PINNED -> {
                long pinnedMs = ((Number) event.getContext().getOrDefault("pinned_ms", 0L)).longValue();
                if (pinnedMs > 100) {
                    andonCord.pull(AndonCord.Severity.P1_HIGH, "vt_pinning_high", Map.of(
                        "pinned_ms", pinnedMs,
                        "thread", event.getContext().getOrDefault("thread", "unknown")
                    ));
                }
            }
            default -> {}
        }
    }

    /**
     * Checks SLO compliance and generates alerts.
     */
    private void checkAndGenerateAlerts() {
        for (SLOTracker.SLOType type : SLOTracker.SLOType.values()) {
            if (sloTracker.isViolating(type)) {
                double compliance = sloTracker.getComplianceRate(type, Duration.ofHours(1));

                // Create alert
                SLOAlertManager.ViolationDetails details = new SLOAlertManager.ViolationDetails(
                    UUID.randomUUID().toString(),
                    "yawl-engine",
                    "slo_tracker",
                    SLOAlertManager.SLOType.valueOf(type.name()),
                    compliance,
                    type.getTargetPercentage(),
                    String.format("SLO violation: %s compliance at %.2f%% vs target %.1f%%",
                        type.name(), compliance, type.getTargetPercentage()),
                    Map.of("compliance_rate", String.valueOf(compliance)),
                    Instant.now()
                );

                alertManager.createAlert(SLOAlertManager.SLOType.valueOf(type.name()), details);
                alertsTriggered.incrementAndGet();
            }
        }
    }

    /**
     * Updates predictive analytics with current data.
     */
    private void updatePredictions() {
        if (predictiveAnalytics == null) return;

        // Check for predicted breaches
        List<SLOPredictiveAnalytics.BreachPrediction> predictions = predictiveAnalytics.getAllBreachPredictions();

        for (SLOPredictiveAnalytics.BreachPrediction prediction : predictions) {
            if (prediction.isImminent()) {
                andonCord.pull(
                    AndonCord.Severity.P2_MEDIUM,
                    "predicted_slo_breach",
                    Map.of(
                        "slo_type", prediction.getSloType().name(),
                        "breach_probability", prediction.getBreachProbability(),
                        "hours_until_breach", prediction.getHoursUntilBreach()
                    )
                );
            }
        }
    }

    /**
     * Gets the current compliance snapshot.
     */
    public ComplianceSnapshot getComplianceSnapshot() {
        Map<SLOTracker.SLOType, Double> rates = new EnumMap<>(SLOTracker.SLOType.class);
        Map<SLOTracker.SLOType, SLOTracker.ComplianceStatus> statuses = new EnumMap<>(SLOTracker.SLOType.class);
        Map<SLOTracker.SLOType, SLOTracker.TrendDirection> trends = new EnumMap<>(SLOTracker.SLOType.class);

        int total = 0, compliant = 0, violating = 0;

        for (SLOTracker.SLOType type : SLOTracker.SLOType.values()) {
            double rate = sloTracker.getComplianceRate(type, Duration.ofHours(1));
            rates.put(type, rate);

            SLOTracker.ComplianceStatus status = SLOTracker.ComplianceStatus.fromValues(
                rate, type.getTargetPercentage(), type.isHigherBetter()
            );
            statuses.put(type, status);

            trends.put(type, sloTracker.getTrend(type));

            total += sloTracker.getTotalMetrics(type);
            if (status == SLOTracker.ComplianceStatus.COMPLIANT) {
                compliant++;
            } else if (status == SLOTracker.ComplianceStatus.VIOLATION) {
                violating++;
            }
        }

        return new ComplianceSnapshot(rates, statuses, trends, total, compliant, violating);
    }

    /**
     * Gets breach predictions from predictive analytics.
     */
    public List<SLOPredictiveAnalytics.BreachPrediction> getBreachPredictions() {
        if (predictiveAnalytics == null) {
            return List.of();
        }
        return predictiveAnalytics.getAllBreachPredictions();
    }

    /**
     * Gets the forecast for an SLO type.
     */
    public SLOPredictiveAnalytics.ForecastResult getForecast(SLOTracker.SLOType type, int horizonHours) {
        if (predictiveAnalytics == null) {
            return null;
        }
        return predictiveAnalytics.forecast(type, horizonHours);
    }

    /**
     * Generates a JSON report.
     */
    public String generateJsonReport(Instant from, Instant to) {
        if (dashboard == null) {
            return "{}";
        }
        return dashboard.generateJsonReport(from, to);
    }

    /**
     * Generates an HTML dashboard.
     */
    public String generateHtmlReport() {
        if (dashboard == null) {
            return "<html><body>Dashboard disabled</body></html>";
        }
        return dashboard.generateHtmlReport();
    }

    /**
     * Adds an event listener.
     */
    public void addEventListener(Consumer<SLOEvent> listener) {
        eventListeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Removes an event listener.
     */
    public void removeEventListener(Consumer<SLOEvent> listener) {
        eventListeners.remove(listener);
    }

    /**
     * Notifies all listeners of an event.
     */
    private void notifyListeners(SLOEvent event) {
        for (Consumer<SLOEvent> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOGGER.error("Event listener threw exception", e);
            }
        }
    }

    /**
     * Gets the SLO tracker instance.
     */
    public SLOTracker getSloTracker() {
        return sloTracker;
    }

    /**
     * Gets the dashboard instance.
     */
    public SLODashboard getDashboard() {
        return dashboard;
    }

    /**
     * Gets the predictive analytics instance.
     */
    public SLOPredictiveAnalytics getPredictiveAnalytics() {
        return predictiveAnalytics;
    }

    /**
     * Gets service statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("running", running.get());
        stats.put("eventsProcessed", eventsProcessed.get());
        stats.put("batchesFlushed", batchesFlushed.get());
        stats.put("alertsTriggered", alertsTriggered.get());
        stats.put("queueSize", eventQueue.size());
        stats.put("batchSize", batchSize);
        stats.put("flushIntervalMs", flushInterval.toMillis());
        return stats;
    }

    /**
     * Resets all statistics.
     */
    public void resetStats() {
        eventsProcessed.set(0);
        batchesFlushed.set(0);
        alertsTriggered.set(0);
        sloTracker.reset();
        if (dashboard != null) {
            dashboard.clearHistory();
        }
        if (predictiveAnalytics != null) {
            predictiveAnalytics.clearAllData();
        }
        LOGGER.info("SLOIntegrationService statistics reset");
    }
}
