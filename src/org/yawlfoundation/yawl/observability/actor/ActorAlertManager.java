package org.yawlfoundation.yawl.observability.actor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.observability.StructuredLogger;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive alert management for YAWL actor systems.
 *
 * Manages alert thresholds, notifications, and escalations for actor-related metrics:
 * - Memory usage alerts (per actor and system-wide)
 * - Performance alerts (processing time, queue latency)
 * - Health alerts (unhealthy actors, error rates)
 * - Resource alerts (queue depth, CPU usage)
 * - Custom alert rules
 *
 * Provides alert suppression, throttling, and escalation management.
 */
public class ActorAlertManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorAlertManager.class);
    private static ActorAlertManager instance;

    private final MeterRegistry meterRegistry;
    private final ActorHealthMetrics healthMetrics;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // Alert counters
    private final Counter alertCounter;
    private final Counter criticalAlertCounter;
    private final Counter warningAlertCounter;
    private final Counter infoAlertCounter;

    // Alert state tracking
    private final ConcurrentHashMap<String, AlertState> activeAlerts;
    private final ConcurrentHashMap<String, AlertHistory> alertHistories;
    private final ConcurrentHashMap<String, Long> alertThrottleTimes;

    // Alert suppression
    private final ConcurrentHashMap<String, AlertSuppression> alertSuppressions;

    // Alert thresholds
    private final Map<AlertType, AlertThreshold> alertThresholds;
    private final long alertThrottleMs;
    private final int maxActiveAlerts;
    private final Duration alertHistoryRetention;

    // Alert notification
    private final List<AlertNotifier> notifiers;
    private final ScheduledExecutorService alertScheduler;
    private final ScheduledExecutorService cleanupScheduler;

    // Alert rules
    private final List<AlertRule> alertRules;

    /**
     * Alert severity levels.
     */
    public enum AlertSeverity {
        INFO,
        WARNING,
        CRITICAL,
        FATAL
    }

    /**
     * Alert types.
     */
    public enum AlertType {
        ACTOR_MEMORY_HIGH,
        ACTOR_PROCESSING_SLOW,
        ACTOR_QUEUE_BACKLOG,
        ACTOR_HEALTH_DEGRADED,
        ACTOR_ERROR_RATE_HIGH,
        SYSTEM_MEMORY_LOW,
        SYSTEM_QUEUE_OVERFLOW,
        SYSTEM_HEALTH_CRITICAL,
        CUSTOM
    }

    /**
     * Alert threshold configuration.
     */
    public static final class AlertThreshold {
        private final AlertType type;
        private final AlertSeverity severity;
        private final double value;
        private final String condition;
        private final String description;
        private final Duration durationThreshold;

        public AlertThreshold(AlertType type, AlertSeverity severity,
                            double value, String condition,
                            String description, Duration durationThreshold) {
            this.type = type;
            this.severity = severity;
            this.value = value;
            this.condition = condition;
            this.description = description;
            this.durationThreshold = durationThreshold;
        }

        // Getters
        public AlertType getType() { return type; }
        public AlertSeverity getSeverity() { return severity; }
        public double getValue() { return value; }
        public String getCondition() { return condition; }
        public String getDescription() { return description; }
        public Duration getDurationThreshold() { return durationThreshold; }
    }

    /**
     * Alert state tracking.
     */
    private static final class AlertState {
        private final String alertId;
        private final AlertType type;
        private final AlertSeverity severity;
        private final String actorId;
        private final Instant firstTriggered;
        private final AtomicInteger triggerCount;
        private final AtomicBoolean active;

        public AlertState(String alertId, AlertType type, AlertSeverity severity, String actorId) {
            this.alertId = alertId;
            this.type = type;
            this.severity = severity;
            this.actorId = actorId;
            this.firstTriggered = Instant.now();
            this.triggerCount = new AtomicInteger(0);
            this.active = new AtomicBoolean(true);
        }

        // Getters
        public String getAlertId() { return alertId; }
        public AlertType getType() { return type; }
        public AlertSeverity getSeverity() { return severity; }
        public String getActorId() { return actorId; }
        public Instant getFirstTriggered() { return firstTriggered; }
        public int getTriggerCount() { return triggerCount.get(); }
        public boolean isActive() { return active.get(); }

        // Methods
        public void incrementTriggerCount() { triggerCount.incrementAndGet(); }
        public void deactivate() { active.set(false); }
    }

    /**
     * Alert history record.
     */
    private static final class AlertHistory {
        private final String alertId;
        private final AlertType type;
        private final AlertSeverity severity;
        private final String actorId;
        private final Instant timestamp;
        private final double value;
        private final String message;
        private final Map<String, Object> context;

        public AlertHistory(String alertId, AlertType type, AlertSeverity severity,
                          String actorId, double value, String message,
                          Map<String, Object> context) {
            this.alertId = alertId;
            this.type = type;
            this.severity = severity;
            this.actorId = actorId;
            this.timestamp = Instant.now();
            this.value = value;
            this.message = message;
            this.context = context;
        }

        // Getters
        public String getAlertId() { return alertId; }
        public AlertType getType() { return type; }
        public AlertSeverity getSeverity() { return severity; }
        public String getActorId() { return actorId; }
        public Instant getTimestamp() { return timestamp; }
        public double getValue() { return value; }
        public String getMessage() { return message; }
        public Map<String, Object> getContext() { return context; }
    }

    /**
     * Alert suppression configuration.
     */
    private static final class AlertSuppression {
        private final String alertKey;
        private final Instant until;
        private final String reason;

        public AlertSuppression(String alertKey, Instant until, String reason) {
            this.alertKey = alertKey;
            this.until = until;
            this.reason = reason;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(until);
        }

        public String getAlertKey() { return alertKey; }
        public Instant getUntil() { return until; }
        public String getReason() { return reason; }
    }

    /**
     * Alert notifier interface.
     */
    public interface AlertNotifier {
        void notify(Alert alert);
    }

    /**
     * Alert record.
     */
    public static final class Alert {
        private final String alertId;
        private final AlertType type;
        private final AlertSeverity severity;
        private final String actorId;
        private final Instant timestamp;
        private final String message;
        private final double value;
        private final Map<String, Object> context;
        private final AlertThreshold threshold;

        public Alert(String alertId, AlertType type, AlertSeverity severity,
                    String actorId, String message, double value,
                    Map<String, Object> context, AlertThreshold threshold) {
            this.alertId = alertId;
            this.type = type;
            this.severity = severity;
            this.actorId = actorId;
            this.timestamp = Instant.now();
            this.message = message;
            this.value = value;
            this.context = context;
            this.threshold = threshold;
        }

        // Getters
        public String getAlertId() { return alertId; }
        public AlertType getType() { return type; }
        public AlertSeverity getSeverity() { return severity; }
        public String getActorId() { return actorId; }
        public Instant getTimestamp() { return timestamp; }
        public String getMessage() { return message; }
        public double getValue() { return value; }
        public Map<String, Object> getContext() { return context; }
        public AlertThreshold getThreshold() { return threshold; }

        @Override
        public String toString() {
            return String.format("Alert{%s (%s): %s - %s}",
                    type, severity, actorId, message);
        }
    }

    /**
     * Alert rule interface.
     */
    public interface AlertRule {
        String getRuleId();
        String getDescription();
        List<Alert> evaluate(ActorHealthMetrics healthMetrics, Map<String, Object> context);
    }

    /**
     * Creates a new ActorAlertManager instance.
     */
    public ActorAlertManager(MeterRegistry meterRegistry, ActorHealthMetrics healthMetrics) {
        this.meterRegistry = meterRegistry;
        this.healthMetrics = healthMetrics;
        this.activeAlerts = new ConcurrentHashMap<>();
        this.alertHistories = new ConcurrentHashMap<>();
        this.alertThrottleTimes = new ConcurrentHashMap<>();
        this.alertSuppressions = new ConcurrentHashMap<>();
        this.notifiers = new ArrayList<>();
        this.alertRules = new ArrayList<>();

        // Configuration
        this.alertThrottleMs = 300000; // 5 minutes
        this.maxActiveAlerts = 100;
        this.alertHistoryRetention = Duration.ofDays(7);

        // Initialize alert counters
        this.alertCounter = Counter.builder("yawl.actor.alert.total")
                .description("Total alerts triggered")
                .register(meterRegistry);

        this.criticalAlertCounter = Counter.builder("yawl.actor.alert.critical")
                .description("Total critical alerts")
                .register(meterRegistry);

        this.warningAlertCounter = Counter.builder("yawl.actor.alert.warning")
                .description("Total warning alerts")
                .register(meterRegistry);

        this.infoAlertCounter = Counter.builder("yawl.actor.alert.info")
                .description("Total info alerts")
                .register(meterRegistry);

        // Initialize alert gauges
        Gauge.builder("yawl.actor.alert.active.count", activeAlerts::size)
                .description("Number of active alerts")
                .register(meterRegistry);

        Gauge.builder("yawl.actor.alert.suppressed.count", alertSuppressions::size)
                .description("Number of suppressed alerts")
                .register(meterRegistry);

        // Initialize schedulers
        this.alertScheduler = Executors.newSingleThreadScheduledExecutor();
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

        initializeDefaultThresholds();
        LOGGER.info("ActorAlertManager initialized");
    }

    /**
     * Initializes the singleton instance.
     */
    public static synchronized void initialize(MeterRegistry meterRegistry, ActorHealthMetrics healthMetrics) {
        if (instance == null) {
            instance = new ActorAlertManager(meterRegistry, healthMetrics);
        }
    }

    /**
     * Gets the singleton instance.
     */
    public static ActorAlertManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ActorAlertManager not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Initializes default alert thresholds.
     */
    private void initializeDefaultThresholds() {
        alertThresholds = new HashMap<>();

        // Memory alerts
        alertThresholds.put(AlertType.ACTOR_MEMORY_HIGH,
                new AlertThreshold(AlertType.ACTOR_MEMORY_HIGH, AlertSeverity.CRITICAL,
                        8 * 1024 * 1024, ">", "Actor memory usage exceeds 8MB",
                        Duration.ofSeconds(30)));

        alertThresholds.put(AlertType.SYSTEM_MEMORY_LOW,
                new AlertThreshold(AlertType.SYSTEM_MEMORY_LOW, AlertSeverity.WARNING,
                        0.1, "<", "System memory below 10%",
                        Duration.ofSeconds(60)));

        // Performance alerts
        alertThresholds.put(AlertType.ACTOR_PROCESSING_SLOW,
                new AlertThreshold(AlertType.ACTOR_PROCESSING_SLOW, AlertSeverity.WARNING,
                        5000, ">", "Actor message processing takes longer than 5s",
                        Duration.ofSeconds(120)));

        alertThresholds.put(AlertType.ACTOR_QUEUE_BACKLOG,
                new AlertThreshold(AlertType.ACTOR_QUEUE_BACKLOG, AlertSeverity.WARNING,
                        500, ">", "Actor queue backlog exceeds 500 messages",
                        Duration.ofSeconds(60)));

        // Health alerts
        alertThresholds.put(AlertType.ACTOR_HEALTH_DEGRADED,
                new AlertThreshold(AlertType.ACTOR_HEALTH_DEGRADED, AlertSeverity.WARNING,
                        0.1, ">", "Actor error rate exceeds 10%",
                        Duration.ofSeconds(300)));

        alertThresholds.put(AlertType.SYSTEM_HEALTH_CRITICAL,
                new AlertThreshold(AlertType.SYSTEM_HEALTH_CRITICAL, AlertSeverity.CRITICAL,
                        0.3, ">", "System health score below 30%",
                        Duration.ofSeconds(60)));

        // System alerts
        alertThresholds.put(AlertType.SYSTEM_QUEUE_OVERFLOW,
                new AlertThreshold(AlertType.SYSTEM_QUEUE_OVERFLOW, AlertSeverity.CRITICAL,
                        10000, ">", "System queue depth exceeds 10000",
                        Duration.ofSeconds(30)));
    }

    /**
     * Starts the alert manager.
     */
    public synchronized void start() {
        if (initialized.get()) {
            return;
        }

        // Start periodic alert evaluation
        alertScheduler.scheduleAtFixedRate(this::evaluateAlerts, 1, 1, TimeUnit.MINUTES);

        // Start cleanup job
        cleanupScheduler.scheduleAtFixedRate(this::performCleanup, 1, 1, TimeUnit.HOURS);

        initialized.set(true);
        LOGGER.info("ActorAlertManager started");
    }

    /**
     * Stops the alert manager.
     */
    public synchronized void stop() {
        if (!initialized.get()) {
            return;
        }

        alertScheduler.shutdown();
        cleanupScheduler.shutdown();

        activeAlerts.clear();
        alertSuppressions.clear();

        initialized.set(false);
        LOGGER.info("ActorAlertManager stopped");
    }

    // Alert Management Methods

    /**
     * Triggers an alert if conditions are met.
     */
    public void triggerAlert(AlertType type, AlertSeverity severity, String actorId,
                           String message, double value, Map<String, Object> context) {
        String alertKey = buildAlertKey(type, actorId);

        // Check if alert is suppressed
        if (isAlertSuppressed(alertKey, type, actorId)) {
            LOGGER.debug("Alert suppressed: {} - {}", alertKey, message);
            return;
        }

        // Check if alert is already active
        AlertState existingAlert = activeAlerts.get(alertKey);
        if (existingAlert != null && existingAlert.isActive()) {
            existingAlert.incrementTriggerCount();
            LOGGER.debug("Alert already active, incrementing count: {}", alertKey);
            return;
        }

        // Check alert threshold
        AlertThreshold threshold = alertThresholds.get(type);
        if (threshold != null && !evaluateThreshold(threshold, value)) {
            LOGGER.debug("Alert threshold not met: {} - value: {}, threshold: {}",
                    alertKey, value, threshold.getValue());
            return;
        }

        // Check if alert is throttled
        Long lastThrottleTime = alertThrottleTimes.get(alertKey);
        if (lastThrottleTime != null &&
            System.currentTimeMillis() - lastThrottleTime < alertThrottleMs) {
            LOGGER.debug("Alert throttled: {}", alertKey);
            return;
        }

        // Check max active alerts limit
        if (activeAlerts.size() >= maxActiveAlerts) {
            LOGGER.warn("Max active alerts limit reached, dropping new alert: {}", alertKey);
            return;
        }

        // Create and register alert
        String alertId = UUID.randomUUID().toString();
        AlertState alertState = new AlertState(alertId, type, severity, actorId);
        activeAlerts.put(alertKey, alertState);

        // Create alert object
        Alert alert = new Alert(alertId, type, severity, actorId, message, value, context, threshold);

        // Record in history
        AlertHistory history = new AlertHistory(alertId, type, severity, actorId, value, message, context);
        alertHistories.put(alertId, history);

        // Update throttle time
        alertThrottleTimes.put(alertKey, System.currentTimeMillis());

        // Increment counters
        alertCounter.increment();
        switch (severity) {
            case CRITICAL:
            case FATAL:
                criticalAlertCounter.increment();
                break;
            case WARNING:
                warningAlertCounter.increment();
                break;
            case INFO:
                infoAlertCounter.increment();
                break;
        }

        // Notify notifiers
        notifyNotifiers(alert);

        // Log alert
        StructuredLogger logger = StructuredLogger.getLogger(ActorAlertManager.class);
        logger.warn("Alert triggered: {}", alert,
                Map.of("type", type, "severity", severity, "actorId", actorId,
                       "value", value, "message", message));

        LOGGER.warn("Alert triggered: {} - {} (value: {})", type, message, value);
    }

    /**
     * Evaluates all alert rules.
     */
    private void evaluateAlerts() {
        try {
            Map<String, Object> context = buildEvaluationContext();

            for (AlertRule rule : alertRules) {
                try {
                    List<Alert> alerts = rule.evaluate(healthMetrics, context);
                    for (Alert alert : alerts) {
                        triggerAlert(alert.getType(), alert.getSeverity(),
                                   alert.getActorId(), alert.getMessage(),
                                   alert.getValue(), alert.getContext());
                    }
                } catch (Exception e) {
                    LOGGER.error("Error evaluating alert rule: {}", rule.getRuleId(), e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error during alert evaluation", e);
        }
    }

    /**
     * Builds evaluation context for alert rules.
     */
    private Map<String, Object> buildEvaluationContext() {
        ActorHealthMetrics.ActorHealthSummary summary = healthMetrics.getHealthSummary();
        Map<String, Object> context = new HashMap<>();

        context.put("timestamp", System.currentTimeMillis());
        context.put("activeActorCount", summary.getActiveActorCount());
        context.put("unhealthyActorCount", summary.getUnhealthyActorCount());
        context.put("suspendedActorCount", summary.getSuspendedActorCount());
        context.put("totalQueueDepth", summary.getTotalQueueDepth());
        context.put("totalMemoryBytes", summary.getTotalMemoryBytes());
        context.put("systemHealthScore", summary.getSystemHealthScore());

        return context;
    }

    /**
     * Evaluates threshold conditions.
     */
    private boolean evaluateThreshold(AlertThreshold threshold, double value) {
        switch (threshold.getCondition()) {
            case ">":
                return value > threshold.getValue();
            case ">=":
                return value >= threshold.getValue();
            case "<":
                return value < threshold.getValue();
            case "<=":
                return value <= threshold.getValue();
            case "==":
                return value == threshold.getValue();
            case "!=":
                return value != threshold.getValue();
            default:
                return false;
        }
    }

    /**
     * Checks if alert is suppressed.
     */
    private boolean isAlertSuppressed(String alertKey, AlertType type, String actorId) {
        AlertSuppression suppression = alertSuppressions.get(alertKey);
        if (suppression != null) {
            if (suppression.isExpired()) {
                alertSuppressions.remove(alertKey);
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Builds alert key.
     */
    private String buildAlertKey(AlertType type, String actorId) {
        return type.name() + (actorId != null ? ":" + actorId : "");
    }

    /**
     * Notifies all notifiers of an alert.
     */
    private void notifyNotifiers(Alert alert) {
        for (AlertNotifier notifier : notifiers) {
            try {
                notifier.notify(alert);
            } catch (Exception e) {
                LOGGER.error("Error notifying for alert: {}", alert.getAlertId(), e);
            }
        }
    }

    // Notifier Management

    /**
     * Adds a alert notifier.
     */
    public void addNotifier(AlertNotifier notifier) {
        notifiers.add(notifier);
    }

    /**
     * Removes a alert notifier.
     */
    public void removeNotifier(AlertNotifier notifier) {
        notifiers.remove(notifier);
    }

    // Suppression Management

    /**
     * Suppresses an alert type for a specific actor.
     */
    public void suppressAlert(AlertType type, String actorId, Duration duration, String reason) {
        String alertKey = buildAlertKey(type, actorId);
        Instant until = Instant.now().plus(duration);

        AlertSuppression suppression = new AlertSuppression(alertKey, until, reason);
        alertSuppressions.put(alertKey, suppression);

        LOGGER.info("Alert suppressed: {} for {}ms - {}", alertKey, duration.toMillis(), reason);
    }

    /**
     * Suppresses an alert type for all actors.
     */
    public void suppressAlert(AlertType type, Duration duration, String reason) {
        suppressAlert(type, null, duration, reason);
    }

    /**
     * Removes alert suppression.
     */
    public void removeAlertSuppression(AlertType type, String actorId) {
        String alertKey = buildAlertKey(type, actorId);
        alertSuppressions.remove(alertKey);
    }

    // Rule Management

    /**
     * Adds an alert rule.
     */
    public void addAlertRule(AlertRule rule) {
        alertRules.add(rule);
    }

    /**
     * Removes an alert rule.
     */
    public void removeAlertRule(String ruleId) {
        alertRules.removeIf(rule -> rule.getRuleId().equals(ruleId));
    }

    // Cleanup Methods

    /**
     * Performs periodic cleanup.
     */
    private void performCleanup() {
        try {
            // Clean up old active alerts
            activeAlerts.entrySet().removeIf(entry -> {
                AlertState state = entry.getValue();
                if (!state.isActive()) {
                    return true;
                }
                Duration age = Duration.between(state.getFirstTriggered(), Instant.now());
                return age.toHours() > 24; // Remove inactive alerts older than 24 hours
            });

            // Clean up old history
            long cutoff = System.currentTimeMillis() - alertHistoryRetention.toMillis();
            alertHistories.entrySet().removeIf(entry -> {
                AlertHistory history = entry.getValue();
                return history.getTimestamp().toEpochMilli() < cutoff;
            });

            // Clean up expired suppressions
            alertSuppressions.entrySet().removeIf(entry -> {
                AlertSuppression suppression = entry.getValue();
                return suppression.isExpired();
            });

            LOGGER.debug("Cleanup completed - Active alerts: {}, History: {}, Suppressions: {}",
                    activeAlerts.size(), alertHistories.size(), alertSuppressions.size());

        } catch (Exception e) {
            LOGGER.error("Error during cleanup", e);
        }
    }

    // Public Methods

    /**
     * Gets current alert statistics.
     */
    public AlertStatistics getAlertStatistics() {
        int activeCount = activeAlerts.size();
        int historyCount = alertHistories.size();
        int suppressionCount = alertSuppressions.size();

        Map<AlertSeverity, Integer> severityCounts = new HashMap<>();
        severityCounts.put(AlertSeverity.INFO, 0);
        severityCounts.put(AlertSeverity.WARNING, 0);
        severityCounts.put(AlertSeverity.CRITICAL, 0);
        severityCounts.put(AlertSeverity.FATAL, 0);

        activeAlerts.values().forEach(alert -> {
            AlertSeverity severity = alert.getSeverity();
            severityCounts.put(severity, severityCounts.get(severity) + 1);
        });

        return new AlertStatistics(activeCount, historyCount, suppressionCount, severityCounts);
    }

    /**
     * Gets active alerts for a specific actor.
     */
    public List<Alert> getActiveAlertsForActor(String actorId) {
        List<Alert> result = new ArrayList<>();
        for (AlertState state : activeAlerts.values()) {
            if (state.isActive() &&
                (actorId == null || state.getActorId().equals(actorId))) {
                // Create alert object from state
                result.add(new Alert(
                    state.getAlertId(), state.getType(), state.getSeverity(),
                    state.getActorId(), "Active alert", 0.0, Map.of(), null
                ));
            }
        }
        return result;
    }

    /**
     * Gets alert history for a specific actor.
     */
    public List<AlertHistory> getAlertHistoryForActor(String actorId, int limit) {
        return alertHistories.values().stream()
                .filter(history -> actorId == null || history.getActorId().equals(actorId))
                .sorted((h1, h2) -> h2.getTimestamp().compareTo(h1.getTimestamp()))
                .limit(limit)
                .toList();
    }

    /**
     * Alert statistics record.
     */
    public static final class AlertStatistics {
        private final int activeAlertCount;
        private final int historyCount;
        private final int suppressionCount;
        private final Map<AlertSeverity, Integer> severityCounts;

        public AlertStatistics(int activeAlertCount, int historyCount, int suppressionCount,
                             Map<AlertSeverity, Integer> severityCounts) {
            this.activeAlertCount = activeAlertCount;
            this.historyCount = historyCount;
            this.suppressionCount = suppressionCount;
            this.severityCounts = severityCounts;
        }

        // Getters
        public int getActiveAlertCount() { return activeAlertCount; }
        public int getHistoryCount() { return historyCount; }
        public int getSuppressionCount() { return suppressionCount; }
        public Map<AlertSeverity, Integer> getSeverityCounts() { return severityCounts; }
    }
}