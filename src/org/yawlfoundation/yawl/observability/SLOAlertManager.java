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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * SLO Alert Manager with AndonCord integration.
 *
 * <p>Manages Service Level Objective violations and integrates with AndonCord for
 * real-time alert escalation. Provides thread-safe alert management with proper
 * escalation paths and maintenance window support.</p>
 *
 * <h2>Alert Flow</h2>
 * <ol>
 *   <li>SLO violation detected → createAlert()</li>
 *   <li>Alert severity evaluated → pullAndonCord() if critical</li>
 *   <li>Alert resolved → releaseAndonCord()</li>
 *   <li>Maintenance mode → suppress alerts</li>
 * </ol>
 *
 * <h2>AndonCord Integration</h2>
 * <ul>
 *   <li>WARNING alerts: Log only, no AndonCord pull</li>
 *   <li>CRITICAL alerts: Pull AndonCord immediately</li>
 *   <li>EMERGENCY alerts: Pull AndonCord + immediate escalation</li>
 * </ul>
 *
 * <h2>Maintenance Windows</h2>
 * <ul>
 *   <li>Enter maintenance mode → suppress new alerts</li>
 *   <li>Active in maintenance → suppress alerts unless emergency</li>
 *   <li>Exit maintenance mode → resume normal alerting</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
public final class SLOAlertManager {

    private static final Logger LOGGER = LogManager.getLogger(SLOAlertManager.class);

    /**
     * Service Level Objective types for categorizing alerts.
     */
    public enum SLOType {
        CASE_COMPLETION("case_completion", "Case completion time SLA"),
        TASK_EXECUTION("task_execution", "Task execution time SLA"),
        RESOURCE_UTILIZATION("resource_utilization", "Resource utilization threshold"),
        ERROR_RATE("error_rate", "Error rate threshold"),
        THROUGHPUT("throughput", "System throughput SLA"),
        RESPONSE_TIME("response_time", "API response time SLA"),
        AVAILABILITY("availability", "System availability SLA");

        private final String code;
        private final String description;

        SLOType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getDescription() { return description; }

        public static SLOType fromCode(String code) {
            for (SLOType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown SLO type: " + code);
        }
    }

    /**
     * Alert severity levels.
     */
    public enum AlertSeverity {
        WARNING(1, "WARNING", "Approaching threshold"),
        CRITICAL(2, "CRITICAL", "Threshold breached"),
        EMERGENCY(3, "EMERGENCY", "Multiple breaches or extended violation");

        private final int level;
        private final String code;
        private final String description;

        AlertSeverity(int level, String code, String description) {
            this.level = level;
            this.code = code;
            this.description = description;
        }

        public int getLevel() { return level; }
        public String getCode() { return code; }
        public String getDescription() { return description; }

        /**
         * Determines if this severity should pull AndonCord.
         */
        public boolean shouldPullAndonCord() {
            return this != WARNING;
        }

        /**
         * Converts to AndonCord Severity for integration.
         */
        public AndonCord.Severity toAndonSeverity() {
            return switch (this) {
                case WARNING -> AndonCord.Severity.P3_LOW;
                case CRITICAL -> AndonCord.Severity.P2_MEDIUM;
                case EMERGENCY -> AndonCord.Severity.P1_HIGH;
            };
        }
    }

    /**
     * Details about SLO violation.
     */
    public record ViolationDetails(
        String violationId,
        String sourceId,
        String sourceType,
        SLOType sloType,
        Double actualValue,
        Double thresholdValue,
        String errorMessage,
        Map<String, String> context,
        Instant detectedAt
    ) {
        public ViolationDetails {
            Objects.requireNonNull(violationId);
            Objects.requireNonNull(sloType);
            Objects.requireNonNull(detectedAt);
            context = context != null ? Map.copyOf(context) : Map.of();
        }
    }

    /**
     * SLO Alert record.
     */
    public record SLOAlert(
        String alertId,
        SLOType sloType,
        AlertSeverity severity,
        Instant createdAt,
        Instant resolvedAt,
        String resolution,
        ViolationDetails details
    ) {}

    // Singleton instance
    private static volatile SLOAlertManager instance;
    private static final ReentrantLock _lock = new ReentrantLock();

    // Dependencies
    private final AndonCord andonCord;
    private final Tracer tracer;
    private final HttpClient httpClient;

    // Alert storage (thread-safe)
    private final ConcurrentMap<String, SLOAlert> activeAlerts = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SLOAlert> alertHistory = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<SLOAlert>> alertListeners = new CopyOnWriteArrayList<>();

    // Maintenance mode state
    private final AtomicReference<Instant> maintenanceStart = new AtomicReference<>();
    private final AtomicBoolean inMaintenanceMode = new AtomicBoolean(false);
    private final ScheduledExecutorService maintenanceScheduler;
    private final Map<String, ScheduledFuture<?>> scheduledMaintenanceExits = new ConcurrentHashMap<>();

    // Metrics
    private final AtomicLong alertsCreated = new AtomicLong();
    private final AtomicLong alertsResolved = new AtomicLong();
    private final AtomicLong andonCordPulled = new AtomicLong();

    /**
     * Private constructor - use getInstance().
     */
    private SLOAlertManager() {
        this.andonCord = AndonCord.getInstance();
        this.tracer = io.opentelemetry.api.GlobalOpenTelemetry.get()
            .getTracer("org.yawlfoundation.yawl.observability.SLOAlertManager", "6.0.0");
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        this.maintenanceScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "slo-maintenance-timer");
            t.setDaemon(true);
            return t;
        });

        LOGGER.info("SLOAlertManager initialized");
    }

    /**
     * Gets the singleton instance.
     */
    public static SLOAlertManager getInstance() {
        if (instance == null) {
            _lock.lock();
            try {
                if (instance == null) {
                    instance = new SLOAlertManager();
                }
            } finally {
                _lock.unlock();
            }
        }
        return instance;
    }

    // ==================== Alert Creation ====================

    /**
     * Creates a new SLO alert.
     *
     * @param type the SLO type
     * @param details violation details
     * @return the created alert
     */
    public SLOAlert createAlert(SLOType type, ViolationDetails details) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(details);

        // Check if alert should be suppressed
        if (shouldSuppressAlert(type, details)) {
            LOGGER.info("Alert suppressed due to maintenance mode: {}", type.getCode());
            return null;
        }

        String alertId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        SLOAlert alert = new SLOAlert(
            alertId,
            type,
            determineSeverity(details),
            now,
            null,
            null,
            details
        );

        // Store alert
        activeAlerts.put(alertId, alert);
        alertsCreated.incrementAndGet();

        // Create OTEL span
        Span span = tracer.spanBuilder("slo.alert.create")
            .setAttribute("slo.type", type.getCode())
            .setAttribute("alert.severity", alert.severity().getCode())
            .setAttribute("alert.id", alertId)
            .startSpan();

        try {
            // Log alert creation
            logAlertCreation(alert);

            // Check if AndonCord should be pulled
            if (alert.severity().shouldPullAndonCord()) {
                pullAndonCord(alert);
            }

            // Notify listeners
            notifyListeners(alert);

            // Record metrics
            recordAlertMetrics(alert);

            return alert;
        } finally {
            span.end();
        }
    }

    /**
     * Resolves an alert.
     *
     * @param alertId the alert ID
     * @param resolution resolution details
     * @return the resolved alert, or null if not found
     */
    public SLOAlert resolveAlert(String alertId, String resolution) {
        Objects.requireNonNull(alertId);

        SLOAlert alert = activeAlerts.get(alertId);
        if (alert == null) {
            LOGGER.warn("Attempted to resolve non-existent alert: {}", alertId);
            return null;
        }

        // Update alert state
        SLOAlert resolved = new SLOAlert(
            alert.alertId(),
            alert.sloType(),
            alert.severity(),
            alert.createdAt(),
            Instant.now(),
            resolution != null ? resolution : "Resolved",
            alert.details()
        );

        // Remove from active and add to history
        activeAlerts.remove(alertId);
        alertHistory.add(resolved);
        alertsResolved.incrementAndGet();

        // Create OTEL span
        Span span = tracer.spanBuilder("slo.alert.resolve")
            .setAttribute("alert.id", alertId)
            .setAttribute("alert.severity", resolved.severity().getCode())
            .setAttribute("resolution_time", resolved.resolvedAt().toString())
            .startSpan();

        try {
            // Release AndonCord if needed
            if (alert.severity().shouldPullAndonCord()) {
                releaseAndonCord(alertId);
            }

            // Log resolution
            LOGGER.info("Alert resolved: {} - {}", alertId, resolution);

            // Notify listeners
            notifyListeners(resolved);

            // Record metrics
            recordResolutionMetrics(resolved);

            return resolved;
        } finally {
            span.end();
        }
    }

    // ==================== Alert Queries ====================

    /**
     * Gets all active alerts.
     */
    public List<SLOAlert> getActiveAlerts() {
        return List.copyOf(activeAlerts.values());
    }

    /**
     * Gets active alerts by SLO type.
     */
    public List<SLOAlert> getActiveAlertsByType(SLOType type) {
        Objects.requireNonNull(type);
        return activeAlerts.values().stream()
            .filter(alert -> alert.sloType() == type)
            .toList();
    }

    /**
     * Gets active alerts by severity.
     */
    public List<SLOAlert> getActiveAlertsBySeverity(AlertSeverity severity) {
        Objects.requireNonNull(severity);
        return activeAlerts.values().stream()
            .filter(alert -> alert.severity() == severity)
            .toList();
    }

    /**
     * Gets alerts within a time window.
     *
     * @param window the time window to search
     * @return alerts created within the window
     */
    public List<SLOAlert> getAlerts(Duration window) {
        Objects.requireNonNull(window);
        Instant cutoff = Instant.now().minus(window);

        return Stream.concat(
            activeAlerts.values().stream(),
            alertHistory.stream()
        )
        .filter(alert -> !alert.createdAt().isBefore(cutoff))
        .toList();
    }

    /**
     * Gets a specific alert by ID.
     *
     * @param alertId the alert ID
     * @return optional containing the alert if found
     */
    public Optional<SLOAlert> getAlert(String alertId) {
        Objects.requireNonNull(alertId);
        SLOAlert active = activeAlerts.get(alertId);
        if (active != null) {
            return Optional.of(active);
        }
        return alertHistory.stream()
            .filter(alert -> alert.alertId().equals(alertId))
            .findFirst();
    }

    // ==================== AndonCord Integration ====================

    /**
     * Pulls the AndonCord for an alert.
     *
     * @param alert the alert
     */
    public void pullAndonCord(SLOAlert alert) {
        Objects.requireNonNull(alert);

        // Check if AndonCord is already pulled for this alert
        if (andonCord.getActiveAlerts().stream()
            .anyMatch(a -> a.getContext().containsKey("slo_alert_id") &&
                         a.getContext().get("slo_alert_id").equals(alert.alertId()))) {
            LOGGER.debug("AndonCord already pulled for alert: {}", alert.alertId());
            return;
        }

        try {
            // Create AndonCord alert context
            Map<String, Object> context = new HashMap<>(alert.details().context());
            context.put("slo_alert_id", alert.alertId());
            context.put("slo_type", alert.sloType().getCode());
            context.put("alert_created_at", alert.createdAt().toString());
            context.put("alert_severity", alert.severity().getCode());
            context.put("source_id", alert.details().sourceId());
            context.put("source_type", alert.details().sourceType());

            // Fire AndonCord alert
            AndonCord.Alert andonAlert = andonCord.pull(
                alert.severity().toAndonSeverity(),
                "slo_violation_" + alert.sloType().getCode(),
                context
            );

            LOGGER.info("AndonCord pulled for alert {}: {}", alert.alertId(), andonAlert.getId());
            andonCordPulled.incrementAndGet();

        } catch (Exception e) {
            LOGGER.error("Failed to pull AndonCord for alert {}: {}", alert.alertId(), e.getMessage());
            // Do not rethrow - alert creation should not fail due to external system issues
        }
    }

    /**
     * Releases the AndonCord for an alert.
     *
     * @param alertId the alert ID
     */
    public void releaseAndonCord(String alertId) {
        Objects.requireNonNull(alertId);

        try {
            // Find and acknowledge the corresponding AndonCord alert
            andonCord.getActiveAlerts().stream()
                .filter(a -> a.getContext().containsKey("slo_alert_id") &&
                             a.getContext().get("slo_alert_id").equals(alertId))
                .findFirst()
                .ifPresent(andonAlert -> {
                    andonCord.resolve(andonAlert.getId());
                    LOGGER.info("AndonCord released for alert: {}", alertId);
                });

        } catch (Exception e) {
            LOGGER.error("Failed to release AndonCord for alert {}: {}", alertId, e.getMessage());
            // Do not rethrow - alert resolution should not fail due to external system issues
        }
    }

    // ==================== Maintenance Mode ====================

    /**
     * Enters maintenance mode.
     *
     * @param reason reason for maintenance
     * @param expectedDuration expected duration of maintenance
     */
    public void enterMaintenanceMode(String reason, Duration expectedDuration) {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(expectedDuration);

        if (inMaintenanceMode.compareAndSet(false, true)) {
            maintenanceStart.set(Instant.now());
            LOGGER.warn("Entered maintenance mode: {} (expected duration: {})", reason, expectedDuration);

            // Schedule automatic exit if duration is specified
            if (expectedDuration.compareTo(Duration.ZERO) > 0) {
                ScheduledFuture<?> exitTask = maintenanceScheduler.schedule(
                    () -> exitMaintenanceMode(),
                    expectedDuration.toMillis(),
                    TimeUnit.MILLISECONDS
                );
                scheduledMaintenanceExits.put("auto_exit", exitTask);
            }
        }
    }

    /**
     * Exits maintenance mode.
     */
    public void exitMaintenanceMode() {
        if (inMaintenanceMode.compareAndSet(true, false)) {
            maintenanceStart.set(null);
            LOGGER.info("Exited maintenance mode");

            // Cancel any scheduled exit
            ScheduledFuture<?> exitTask = scheduledMaintenanceExits.remove("auto_exit");
            if (exitTask != null) {
                exitTask.cancel(false);
            }
        }
    }

    /**
     * Checks if currently in maintenance mode.
     */
    public boolean isInMaintenanceMode() {
        return inMaintenanceMode.get();
    }

    /**
     * Gets maintenance mode details if active.
     *
     * @return optional containing maintenance start time and reason
     */
    public Optional<Map<String, Object>> getMaintenanceDetails() {
        if (!isInMaintenanceMode()) {
            return Optional.empty();
        }

        Map<String, Object> details = new HashMap<>();
        details.put("start_time", maintenanceStart.get().toString());
        details.put("duration_minutes", Duration.between(maintenanceStart.get(), Instant.now()).toMinutes());

        return Optional.of(details);
    }

    // ==================== Alert Listeners ====================

    /**
     * Registers an alert listener.
     *
     * @param listener the listener to register
     */
    public void addAlertListener(Consumer<SLOAlert> listener) {
        Objects.requireNonNull(listener);
        alertListeners.add(listener);
    }

    /**
     * Removes an alert listener.
     *
     * @param listener the listener to remove
     */
    public void removeAlertListener(Consumer<SLOAlert> listener) {
        alertListeners.remove(listener);
    }

    // ==================== Private Implementation ====================

    /**
     * Determines alert severity based on violation details.
     */
    private AlertSeverity determineSeverity(ViolationDetails details) {
        if (details.actualValue() == null || details.thresholdValue() == null) {
            return AlertSeverity.WARNING;
        }

        double ratio = details.actualValue() / details.thresholdValue();

        // Check for emergency conditions
        if (details.sloType() == SLOType.ERROR_RATE && ratio > 2.0) {
            return AlertSeverity.EMERGENCY;
        }
        if (details.sloType() == SLOType.AVAILABILITY && ratio > 0.95) {
            return AlertSeverity.EMERGENCY;
        }
        if (details.sloType() == SLOType.CASE_COMPLETION && ratio > 3.0) {
            return AlertSeverity.EMERGENCY;
        }

        // Check for critical conditions
        if (ratio > 1.5) {
            return AlertSeverity.CRITICAL;
        }
        if (ratio > 1.2) {
            return AlertSeverity.CRITICAL;
        }

        return AlertSeverity.WARNING;
    }

    /**
     * Checks if an alert should be suppressed due to maintenance mode.
     */
    private boolean shouldSuppressAlert(SLOType type, ViolationDetails details) {
        if (!isInMaintenanceMode()) {
            return false;
        }

        // Emergency alerts are never suppressed
        AlertSeverity severity = determineSeverity(details);
        if (severity == AlertSeverity.EMERGENCY) {
            return false;
        }

        // Critical errors might not be suppressed in maintenance mode
        if (severity == AlertSeverity.CRITICAL && type == SLOType.ERROR_RATE) {
            return false;
        }

        return true;
    }

    /**
     * Logs alert creation.
     */
    private void logAlertCreation(SLOAlert alert) {
        String logMessage = String.format("[SLO-ALERT] [%s] %s - %s",
            alert.severity().getCode(),
            alert.sloType().getCode(),
            alert.details().errorMessage());

        switch (alert.severity()) {
            case EMERGENCY -> LOGGER.fatal(logMessage);
            case CRITICAL -> LOGGER.error(logMessage);
            case WARNING -> LOGGER.warn(logMessage);
        }

        // Log context details
        alert.details().context().forEach((key, value) -> {
            LOGGER.debug("[SLO-ALERT] Context: {} = {}", key, value);
        });
    }

    /**
     * Notifies alert listeners.
     */
    private void notifyListeners(SLOAlert alert) {
        for (Consumer<SLOAlert> listener : alertListeners) {
            try {
                listener.accept(alert);
            } catch (Exception e) {
                LOGGER.error("Alert listener threw exception", e);
            }
        }
    }

    /**
     * Records alert creation metrics.
     */
    private void recordAlertMetrics(SLOAlert alert) {
        // In a real implementation, this would record to a metrics registry
        LOGGER.debug("Alert creation metrics recorded: {}", alert.alertId());
    }

    /**
     * Records alert resolution metrics.
     */
    private void recordResolutionMetrics(SLOAlert alert) {
        // In a real implementation, this would record to a metrics registry
        LOGGER.debug("Alert resolution metrics recorded: {}", alert.alertId());
    }

    // ==================== Utility Methods ====================

    /**
     * Gets total alerts created.
     */
    public long getTotalAlertsCreated() {
        return alertsCreated.get();
    }

    /**
     * Gets total alerts resolved.
     */
    public long getTotalAlertsResolved() {
        return alertsResolved.get();
    }

    /**
     * Gets total AndonCord pulls.
     */
    public long getTotalAndonCordPulled() {
        return andonCordPulled.get();
    }

    /**
     * Gets maintenance scheduler.
     */
    public ScheduledExecutorService getMaintenanceScheduler() {
        return maintenanceScheduler;
    }

    /**
     * Shuts down the alert manager.
     */
    public void shutdown() {
        // Cancel all scheduled maintenance exits
        scheduledMaintenanceExits.forEach((key, task) -> task.cancel(false));
        scheduledMaintenanceExits.clear();

        // Shutdown scheduler
        maintenanceScheduler.shutdown();
        try {
            if (!maintenanceScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                maintenanceScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            maintenanceScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("SLOAlertManager shut down");
    }
}