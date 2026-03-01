package org.yawlfoundation.yawl.observability.actor;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.observability.YawlMetrics;
import org.yawlfoundation.yawl.observability.OpenTelemetryInitializer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central service for YAWL actor observability integration.
 *
 * Orchestrates all monitoring components:
 * - Health metrics collection
 * - Distributed tracing
 * - Alert management
 * - Dashboard data provisioning
 * - Anomaly detection
 * - Performance monitoring
 *
 * Provides unified APIs for monitoring and integrates with existing YAWL observability.
 */
public class ActorObservabilityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorObservabilityService.class);
    private static ActorObservabilityService instance;

    private final MeterRegistry meterRegistry;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicLong serviceStartTime = new AtomicLong(0);

    // Components
    private ActorHealthMetrics healthMetrics;
    private ActorTracer tracer;
    private ActorAlertManager alertManager;
    private ActorDashboardData dashboardData;
    private ActorAnomalyDetector anomalyDetector;

    // Schedulers
    private ScheduledExecutorService metricsScheduler;
    private ScheduledExecutorService healthCheckScheduler;
    private ScheduledExecutorService cleanupScheduler;

    // Configuration
    private final int metricsIntervalSeconds;
    private final int healthCheckIntervalSeconds;
    private final int cleanupIntervalSeconds;
    private final boolean enableAlerting;
    private final boolean enableAnomalyDetection;
    private final boolean enableDashboard;

    /**
     * Creates a new ActorObservabilityService instance.
     */
    public ActorObservabilityService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.metricsIntervalSeconds = 10;
        this.healthCheckIntervalSeconds = 30;
        this.cleanupIntervalSeconds = 300;
        this.enableAlerting = true;
        this.enableAnomalyDetection = true;
        this.enableDashboard = true;
    }

    /**
     * Initializes the singleton instance.
     */
    public static synchronized void initialize(MeterRegistry meterRegistry) {
        if (instance == null) {
            instance = new ActorObservabilityService(meterRegistry);
            instance.start();
        }
    }

    /**
     * Gets the singleton instance.
     */
    public static ActorObservabilityService getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ActorObservabilityService not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Starts the service and all components.
     */
    public synchronized void start() {
        if (initialized.get()) {
            LOGGER.warn("ActorObservabilityService already started");
            return;
        }

        try {
            // Initialize OpenTelemetry
            OpenTelemetryInitializer.initialize();

            // Initialize components
            initializeComponents();

            // Start schedulers
            startSchedulers();

            serviceStartTime.set(System.currentTimeMillis());
            initialized.set(true);

            LOGGER.info("ActorObservabilityService started successfully");

        } catch (Exception e) {
            LOGGER.error("Failed to start ActorObservabilityService", e);
            throw new RuntimeException("Failed to start ActorObservabilityService", e);
        }
    }

    /**
     * Stops the service and all components.
     */
    public synchronized void stop() {
        if (!initialized.get()) {
            return;
        }

        try {
            // Stop schedulers
            stopSchedulers();

            // Stop components
            if (alertManager != null) {
                alertManager.stop();
            }
            if (anomalyDetector != null) {
                anomalyDetector.stop();
            }

            initialized.set(false);

            LOGGER.info("ActorObservabilityService stopped successfully");

        } catch (Exception e) {
            LOGGER.error("Error while stopping ActorObservabilityService", e);
        }
    }

    /**
     * Initializes all monitoring components.
     */
    private void initializeComponents() {
        try {
            // Initialize base YAWL metrics
            YawlMetrics.initialize(meterRegistry);

            // Initialize Actor Health Metrics
            ActorHealthMetrics.initialize(meterRegistry);
            healthMetrics = ActorHealthMetrics.getInstance();

            // Initialize Tracer
            Tracer tracer = GlobalOpenTelemetry.getTracer("yawl-actor");
            ActorTracer.initialize(tracer);
            this.tracer = ActorTracer.getInstance();

            // Initialize Alert Manager
            if (enableAlerting) {
                alertManager = new ActorAlertManager(meterRegistry, healthMetrics);
                alertManager.start();
                addDefaultAlertRules(alertManager);
            }

            // Initialize Dashboard Data
            if (enableDashboard) {
                dashboardData = new ActorDashboardData(healthMetrics, alertManager);
            }

            // Initialize Anomaly Detector
            if (enableAnomalyDetection) {
                ActorAnomalyDetector.initialize(meterRegistry, healthMetrics);
                anomalyDetector = ActorAnomalyDetector.getInstance();
                anomalyDetector.start();
            }

            LOGGER.info("All monitoring components initialized successfully");

        } catch (Exception e) {
            LOGGER.error("Error initializing monitoring components", e);
            throw e;
        }
    }

    /**
     * Starts background schedulers.
     */
    private void startSchedulers() {
        // Metrics collection scheduler
        metricsScheduler = Executors.newSingleThreadScheduledExecutor();
        metricsScheduler.scheduleAtFixedRate(
                this::collectMetrics,
                metricsIntervalSeconds,
                metricsIntervalSeconds,
                TimeUnit.SECONDS
        );

        // Health check scheduler
        healthCheckScheduler = Executors.newSingleThreadScheduledExecutor();
        healthCheckScheduler.scheduleAtFixedRate(
                this::performHealthChecks,
                healthCheckIntervalSeconds,
                healthCheckIntervalSeconds,
                TimeUnit.SECONDS
        );

        // Cleanup scheduler
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        cleanupScheduler.scheduleAtFixedRate(
                this::performCleanup,
                cleanupIntervalSeconds,
                cleanupIntervalSeconds,
                TimeUnit.SECONDS
        );

        LOGGER.info("Background schedulers started");
    }

    /**
     * Stops all schedulers.
     */
    private void stopSchedulers() {
        if (metricsScheduler != null) {
            metricsScheduler.shutdown();
        }
        if (healthCheckScheduler != null) {
            healthCheckScheduler.shutdown();
        }
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
        }
    }

    /**
     * Collects periodic metrics.
     */
    private void collectMetrics() {
        if (!initialized.get()) return;

        try {
            // Record system-level metrics
            recordSystemMetrics();

            // Update dashboard data
            if (dashboardData != null) {
                dashboardData.recordMetric("actor_count", healthMetrics.getActiveActorCount());
                dashboardData.recordMetric("queue_depth", healthMetrics.getQueueDepth());
                dashboardData.recordMetric("memory_usage", healthMetrics.getTotalActorMemory());
                dashboardData.recordMetric("health_score", calculateHealthScore());
            }

            LOGGER.debug("Metrics collected successfully");

        } catch (Exception e) {
            LOGGER.error("Error collecting metrics", e);
        }
    }

    /**
     * Performs health checks.
     */
    private void performHealthChecks() {
        if (!initialized.get()) return;

        try {
            // Perform health checks on actors
            healthMetrics.performHealthChecks();

            // Update anomaly detection
            if (anomalyDetector != null) {
                for (ActorHealthMetrics.ActorHealthStatus actor : healthMetrics.getAllActiveActors()) {
                    anomalyDetector.recordMetrics(actor.getActorId(), collectActorMetrics(actor));
                }
            }

            LOGGER.debug("Health checks performed successfully");

        } catch (Exception e) {
            LOGGER.error("Error performing health checks", e);
        }
    }

    /**
     * Performs cleanup operations.
     */
    private void performCleanup() {
        if (!initialized.get()) return;

        try {
            // Clean up old spans
            tracer.cleanupOldSpans();

            // Clean up old anomalies
            if (anomalyDetector != null) {
                // Anomaly detector already handles cleanup internally
            }

            // Clean up old alerts
            if (alertManager != null) {
                alertManager.performCleanup();
            }

            LOGGER.debug("Cleanup performed successfully");

        } catch (Exception e) {
            LOGGER.error("Error performing cleanup", e);
        }
    }

    /**
     * Records system-level metrics.
     */
    private void recordSystemMetrics() {
        // This would integrate with system metrics
        // For now, record service uptime
        long uptime = System.currentTimeMillis() - serviceStartTime.get();
        YawlMetrics.getInstance().recordEngineLatencyMs(uptime);

        // Record service health
        healthMetrics.setActiveThreads(Thread.activeCount());
    }

    /**
     * Calculates system health score.
     */
    private double calculateHealthScore() {
        ActorHealthMetrics.ActorHealthSummary summary = healthMetrics.getHealthSummary();
        return summary.getSystemHealthScore();
    }

    /**
     * Collects metrics for an actor.
     */
    private java.util.Map<String, Double> collectActorMetrics(ActorHealthMetrics.ActorHealthStatus actor) {
        java.util.Map<String, Double> metrics = new java.util.HashMap<>();
        metrics.put("message_count", (double) actor.getMessagesReceived());
        metrics.put("processing_time", actor.getAverageProcessingTime());
        metrics.put("error_rate", actor.getErrorRate());
        metrics.put("queue_depth", (double) actor.getQueueDepth());
        metrics.put("memory_usage", (double) actor.getMemoryUsage());
        return metrics;
    }

    /**
     * Adds default alert rules.
     */
    private void addDefaultAlertRules(ActorAlertManager alertManager) {
        // Add memory-based alert rule
        alertManager.addAlertRule(new ActorAlertManager.AlertRule() {
            @Override
            public String getRuleId() {
                return "high_memory_usage";
            }

            @Override
            public String getDescription() {
                return "Alert on high memory usage per actor";
            }

            @Override
            public java.util.List<ActorAlertManager.Alert> evaluate(
                    ActorHealthMetrics healthMetrics, java.util.Map<String, Object> context) {
                java.util.List<ActorAlertManager.Alert> alerts = new java.util.ArrayList<>();

                for (ActorHealthMetrics.ActorHealthStatus actor : healthMetrics.getAllActiveActors()) {
                    if (actor.getMemoryUsage() > 8 * 1024 * 1024) { // 8MB threshold
                        ActorAlertManager.Alert alert = new ActorAlertManager.Alert(
                                UUID.randomUUID().toString(),
                                ActorAlertManager.AlertType.ACTOR_MEMORY_HIGH,
                                ActorAlertManager.AlertSeverity.CRITICAL,
                                actor.getActorId(),
                                "Actor memory usage exceeds threshold: " + actor.getMemoryUsage(),
                                actor.getMemoryUsage(),
                                Map.of("threshold", 8 * 1024 * 1024),
                                null
                        );
                        alerts.add(alert);
                    }
                }

                return alerts;
            }
        });

        // Add queue backlog alert rule
        alertManager.addAlertRule(new ActorAlertManager.AlertRule() {
            @Override
            public String getRuleId() {
                return "queue_backlog";
            }

            @Override
            public String getDescription() {
                return "Alert on actor queue backlog";
            }

            @Override
            public java.util.List<ActorAlertManager.Alert> evaluate(
                    ActorHealthMetrics healthMetrics, java.util.Map<String, Object> context) {
                java.util.List<ActorAlertManager.Alert> alerts = new java.util.ArrayList<>();

                for (ActorHealthMetrics.ActorHealthStatus actor : healthMetrics.getAllActiveActors()) {
                    if (actor.getQueueDepth() > 1000) {
                        ActorAlertManager.Alert alert = new ActorAlertManager.Alert(
                                UUID.randomUUID().toString(),
                                ActorAlertManager.AlertType.ACTOR_QUEUE_BACKLOG,
                                ActorAlertManager.AlertSeverity.WARNING,
                                actor.getActorId(),
                                "Actor queue backlog detected: " + actor.getQueueDepth(),
                                actor.getQueueDepth(),
                                Map.of("threshold", 1000),
                                null
                        );
                        alerts.add(alert);
                    }
                }

                return alerts;
            }
        });

        LOGGER.info("Default alert rules added");
    }

    // Public API Methods

    /**
     * Records actor creation.
     */
    public void recordActorCreated(String actorId, String actorType) {
        healthMetrics.recordActorCreated(actorId, actorType);
    }

    /**
     * Records actor destruction.
     */
    public void recordActorDestroyed(String actorId) {
        healthMetrics.recordActorDestroyed(actorId);
    }

    /**
     * Records message processing.
     */
    public void recordMessageProcessing(String actorId, String messageType,
                                      long processingTimeNanos, long messageSize) {
        healthMetrics.recordMessageProcessed(actorId, messageType, processingTimeNanos);
        tracer.recordMessageProcessing(actorId, messageType, processingTimeNanos, messageSize);
    }

    /**
     * Records message error.
     */
    public void recordMessageError(String actorId, String messageType, String errorType, String errorMessage) {
        healthMetrics.recordActorError(actorId, errorType, errorMessage);
        tracer.recordMessageError(actorId, messageType, errorType, errorMessage, null);
    }

    /**
     * Updates actor queue depth.
     */
    public void updateQueueDepth(String actorId, int queueDepth) {
        healthMetrics.updateQueueDepth(actorId, queueDepth);
    }

    /**
     * Updates actor memory usage.
     */
    public void updateMemoryUsage(String actorId, long memoryBytes) {
        healthMetrics.updateMemoryUsage(actorId, memoryBytes);
    }

    /**
     * Records metrics for anomaly detection.
     */
    public void recordMetricsForAnomalyDetection(String actorId, java.util.Map<String, Double> metrics) {
        if (anomalyDetector != null) {
            anomalyDetector.recordMetrics(actorId, metrics);
        }
    }

    /**
     * Starts a message flow trace.
     */
    public ActorTracer.ActorSpan startMessageFlow(String flowId, String sourceActor, String targetActor,
                                               String messageType, java.util.Map<String, String> baggage) {
        return tracer.startMessageFlow(flowId, sourceActor, targetActor, messageType, baggage);
    }

    /**
     * Continues a message flow trace.
     */
    public ActorTracer.ActorSpan continueMessageFlow(String parentSpanId, String sourceActor, String targetActor,
                                                  String messageType, java.util.Map<String, String> baggage) {
        return tracer.continueMessageFlow(parentSpanId, sourceActor, targetActor, messageType, baggage);
    }

    /**
     * Finishes a message flow trace.
     */
    public void finishMessageFlow(String spanId, java.time.Duration totalDuration) {
        tracer.finishMessageFlow(spanId, totalDuration);
    }

    /**
     * Triggers an alert.
     */
    public void triggerAlert(ActorAlertManager.AlertType type, ActorAlertManager.AlertSeverity severity,
                           String actorId, String message, double value) {
        if (alertManager != null) {
            alertManager.triggerAlert(type, severity, actorId, message, value, Map.of());
        }
    }

    /**
     * Gets dashboard overview.
     */
    public ActorDashboardData.DashboardOverview getDashboardOverview() {
        if (dashboardData != null) {
            return dashboardData.getDashboardOverview();
        }
        throw new IllegalStateException("Dashboard not enabled");
    }

    /**
     * Gets actor health dashboard.
     */
    public ActorDashboardData.ActorHealthDashboard getActorHealthDashboard() {
        if (dashboardData != null) {
            return dashboardData.getActorHealthDashboard();
        }
        throw new IllegalStateException("Dashboard not enabled");
    }

    /**
     * Gets performance dashboard.
     */
    public ActorDashboardData.PerformanceDashboard getPerformanceDashboard() {
        if (dashboardData != null) {
            return dashboardData.getPerformanceDashboard();
        }
        throw new IllegalStateException("Dashboard not enabled");
    }

    /**
     * Gets alert dashboard.
     */
    public ActorDashboardData.AlertDashboard getAlertDashboard() {
        if (dashboardData != null) {
            return dashboardData.getAlertDashboard();
        }
        throw new IllegalStateException("Dashboard not enabled");
    }

    /**
     * Gets real-time metrics.
     */
    public java.util.Map<String, Object> getRealTimeMetrics() {
        if (dashboardData != null) {
            return dashboardData.getRealTimeMetrics();
        }
        throw new IllegalStateException("Dashboard not enabled");
    }

    /**
     * Gets anomaly statistics.
     */
    public ActorAnomalyDetector.AnomalyStatistics getAnomalyStatistics() {
        if (anomalyDetector != null) {
            return anomalyDetector.getAnomalyStatistics();
        }
        throw new IllegalStateException("Anomaly detection not enabled");
    }

    /**
     * Gets service status.
     */
    public ServiceStatus getServiceStatus() {
        return new ServiceStatus(
                initialized.get(),
                System.currentTimeMillis() - serviceStartTime.get(),
                healthMetrics.getActiveActorCount(),
                healthMetrics.getQueueDepth(),
                calculateHealthScore(),
                alertManager != null ? alertManager.getAlertStatistics() : null,
                anomalyDetector != null ? anomalyDetector.getAnomalyStatistics() : null
        );
    }

    /**
     * Service status record.
     */
    public static final class ServiceStatus {
        private final boolean running;
        private final long uptimeMillis;
        private final int activeActorCount;
        private final long queueDepth;
        private final double healthScore;
        private final ActorAlertManager.AlertStatistics alertStats;
        private final ActorAnomalyDetector.AnomalyStatistics anomalyStats;

        public ServiceStatus(boolean running, long uptimeMillis, int activeActorCount,
                           long queueDepth, double healthScore,
                           ActorAlertManager.AlertStatistics alertStats,
                           ActorAnomalyDetector.AnomalyStatistics anomalyStats) {
            this.running = running;
            this.uptimeMillis = uptimeMillis;
            this.activeActorCount = activeActorCount;
            this.queueDepth = queueDepth;
            this.healthScore = healthScore;
            this.alertStats = alertStats;
            this.anomalyStats = anomalyStats;
        }

        // Getters
        public boolean isRunning() { return running; }
        public long getUptimeMillis() { return uptimeMillis; }
        public int getActiveActorCount() { return activeActorCount; }
        public long getQueueDepth() { return queueDepth; }
        public double getHealthScore() { return healthScore; }
        public ActorAlertManager.AlertStatistics getAlertStats() { return alertStats; }
        public ActorAnomalyDetector.AnomalyStatistics getAnomalyStats() { return anomalyStats; }
    }
}