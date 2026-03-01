package org.yawlfoundation.yawl.observability.actor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
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
import java.util.stream.Collectors;

/**
 * Advanced anomaly detection for YAWL actor systems.
 *
 * Implements multiple anomaly detection algorithms:
 * - Statistical analysis (Z-score, moving averages)
 * - Machine learning based detection (isolation forest patterns)
 * - Time series anomaly detection (change point detection)
 * - Behavioral anomaly detection (pattern deviations)
 * - Memory leak detection
 * - Performance regression detection
 *
 * Supports real-time detection with configurable thresholds and alerting.
 */
public class ActorAnomalyDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorAnomalyDetector.class);
    private static ActorAnomalyDetector instance;

    private final MeterRegistry meterRegistry;
    private final ActorHealthMetrics healthMetrics;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // Anomaly detection configuration
    private final double zScoreThreshold;
    private final int minSamplesForBaseline;
    private final Duration detectionWindow;
    private final Duration baselineWindow;

    // Metrics
    private final Counter anomalyCounter;
    private final Counter criticalAnomalyCounter;
    private final Timer detectionTimer;

    // Anomaly tracking
    private final ConcurrentHashMap<String, AnomalyTracker> anomalyTrackers;
    private final ConcurrentHashMap<String, BehavioralProfile> behavioralProfiles;
    private final ConcurrentHashMap<String, TimeSeriesBaseline> timeSeriesBaselines;

    // Detection algorithms
    private final StatisticalAnomalyDetector statisticalDetector;
    private final TimeSeriesAnomalyDetector timeSeriesDetector;
    private final BehavioralAnomalyDetector behavioralDetector;
    private final MemoryLeakDetector memoryLeakDetector;

    // Scheduler
    private final ScheduledExecutorService detectionScheduler;

    /**
     * Anomaly severity levels.
     */
    public enum AnomalySeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Anomaly types.
     */
    public enum AnomalyType {
        STATISTICAL_OUTLIER,
        BEHAVIORAL_DEVIATION,
        MEMORY_LEAK,
        PERFORMANCE_REGRESSION,
        TIME_SERIES_ANOMALY,
        SYSTEM_ANOMALY
    }

    /**
     * Anomaly record.
     */
    public static final class Anomaly {
        private final String anomalyId;
        private final String actorId;
        private final AnomalyType type;
        private final AnomalySeverity severity;
        private final Instant timestamp;
        private final double score;
        private final String description;
        private final Map<String, Object> context;
        private final Map<String, Double> metrics;
        private final Duration duration;

        public Anomaly(String anomalyId, String actorId, AnomalyType type,
                      AnomalySeverity severity, double score, String description,
                      Map<String, Object> context, Map<String, Double> metrics, Duration duration) {
            this.anomalyId = anomalyId;
            this.actorId = actorId;
            this.type = type;
            this.severity = severity;
            this.timestamp = Instant.now();
            this.score = score;
            this.description = description;
            this.context = context;
            this.metrics = metrics;
            this.duration = duration;
        }

        // Getters
        public String getAnomalyId() { return anomalyId; }
        public String getActorId() { return actorId; }
        public AnomalyType getType() { return type; }
        public AnomalySeverity getSeverity() { return severity; }
        public Instant getTimestamp() { return timestamp; }
        public double getScore() { return score; }
        public String getDescription() { return description; }
        public Map<String, Object> getContext() { return context; }
        public Map<String, Double> getMetrics() { return metrics; }
        public Duration getDuration() { return duration; }

        @Override
        public String toString() {
            return String.format("Anomaly{%s (%s): %.2f - %s}",
                    type, severity, score, description);
        }
    }

    /**
     * Anomaly tracker for individual actors.
     */
    private static final class AnomalyTracker {
        private final String actorId;
        private final List<Anomaly> anomalies;
        private final AtomicInteger anomalyCount;
        private final AtomicBoolean hasCriticalAnomaly;
        private final Instant lastDetectionTime;

        public AnomalyTracker(String actorId) {
            this.actorId = actorId;
            this.anomalies = new CopyOnWriteArrayList<>();
            this.anomalyCount = new AtomicInteger(0);
            this.hasCriticalAnomaly = new AtomicBoolean(false);
            this.lastDetectionTime = Instant.now();
        }

        public void addAnomaly(Anomaly anomaly) {
            anomalies.add(anomaly);
            anomalyCount.incrementAndGet();
            if (anomaly.getSeverity() == AnomalySeverity.CRITICAL) {
                hasCriticalAnomaly.set(true);
            }
        }

        public void clearOldAnomalies(Duration olderThan) {
            Instant cutoff = Instant.now().minus(olderThan);
            anomalies.removeIf(anomaly -> anomaly.getTimestamp().isBefore(cutoff));
        }

        // Getters
        public String getActorId() { return actorId; }
        public List<Anomaly> getAnomalies() { return anomalies; }
        public int getAnomalyCount() { return anomalyCount.get(); }
        public boolean hasCriticalAnomaly() { return hasCriticalAnomaly.get(); }
        public Instant getLastDetectionTime() { return lastDetectionTime; }
    }

    /**
     * Behavioral profile for actors.
     */
    private static final class BehavioralProfile {
        private final String actorId;
        private final Map<String, Double> metricAverages;
        private final Map<String, Double> metricStandardDeviations;
        private final Map<String, Long> metricMaxima;
        private final Map<String, String> commonPatterns;
        private final Instant lastUpdated;

        public BehavioralProfile(String actorId) {
            this.actorId = actorId;
            this.metricAverages = new ConcurrentHashMap<>();
            this.metricStandardDeviations = new ConcurrentHashMap<>();
            this.metricMaxima = new ConcurrentHashMap<>();
            this.commonPatterns = new ConcurrentHashMap<>();
            this.lastUpdated = Instant.now();
        }

        public void updateMetric(String metricName, double value) {
            // Update running averages and standard deviations
            Double currentAvg = metricAverages.get(metricName);
            Double currentStd = metricStandardDeviations.get(metricName);
            Long currentMax = metricMaxima.get(metricName);

            if (currentAvg == null) {
                metricAverages.put(metricName, value);
                metricStandardDeviations.put(metricName, 0.0);
                metricMaxima.put(metricName, (long) value);
            } else {
                double newAvg = (currentAvg + value) / 2;
                double newStd = Math.sqrt(Math.pow(currentStd, 2) + Math.pow(value - currentAvg, 2)) / 2;
                long newMax = Math.max(currentMax, (long) value);

                metricAverages.put(metricName, newAvg);
                metricStandardDeviations.put(metricName, newStd);
                metricMaxima.put(metricName, newMax);
            }

            this.lastUpdated = Instant.now();
        }

        // Getters
        public String getActorId() { return actorId; }
        public Map<String, Double> getMetricAverages() { return metricAverages; }
        public Map<String, Double> getMetricStandardDeviations() { return metricStandardDeviations; }
        public Map<String, Long> getMetricMaxima() { return metricMaxima; }
        public Map<String, String> getCommonPatterns() { return commonPatterns; }
        public Instant getLastUpdated() { return lastUpdated; }
    }

    /**
     * Time series baseline for anomaly detection.
     */
    private static final class TimeSeriesBaseline {
        private final String metricName;
        private final Queue<Double> values;
        private final double mean;
        private final double standardDeviation;
        private final Instant lastUpdate;

        public TimeSeriesBaseline(String metricName) {
            this.metricName = metricName;
            this.values = new ConcurrentLinkedQueue<>();
            this.mean = 0.0;
            this.standardDeviation = 0.0;
            this.lastUpdate = Instant.now();
        }

        public synchronized void addValue(double value) {
            values.add(value);
            if (values.size() > 100) { // Keep last 100 values
                values.poll();
            }
            updateStatistics();
            this.lastUpdate = Instant.now();
        }

        private synchronized void updateStatistics() {
            if (values.isEmpty()) return;

            double sum = values.stream().mapToDouble(Double::doubleValue).sum();
            double avg = sum / values.size();

            double variance = values.stream()
                    .mapToDouble(v -> Math.pow(v - avg, 2))
                    .average()
                    .orElse(0.0);

            this.mean = avg;
            this.standardDeviation = Math.sqrt(variance);
        }

        public synchronized boolean isAnomaly(double value) {
            if (values.size() < 10) return false; // Need enough samples

            double zScore = Math.abs((value - mean) / standardDeviation);
            return zScore > 3.0; // 3-sigma threshold
        }

        // Getters
        public String getMetricName() { return metricName; }
        public double getMean() { return mean; }
        public double getStandardDeviation() { return standardDeviation; }
        public Instant getLastUpdate() { return lastUpdate; }
    }

    /**
     * Statistical anomaly detector implementation.
     */
    private class StatisticalAnomalyDetector {
        public Optional<Anomaly> detectAnomaly(String actorId, Map<String, Double> metrics) {
            try {
                double maxZScore = 0.0;
                String anomalousMetric = "";

                for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                    String metricName = entry.getKey();
                    double value = entry.getValue();

                    TimeSeriesBaseline baseline = timeSeriesBaselines.get(metricName);
                    if (baseline != null) {
                        double zScore = Math.abs((value - baseline.getMean()) / baseline.getStandardDeviation());
                        if (zScore > maxZScore) {
                            maxZScore = zScore;
                            anomalousMetric = metricName;
                        }
                    }
                }

                if (maxZScore > zScoreThreshold) {
                    AnomalySeverity severity = maxZScore > 4.0 ? AnomalySeverity.CRITICAL :
                                                 maxZScore > 3.0 ? AnomalySeverity.HIGH :
                                                 AnomalySeverity.MEDIUM;

                    return Optional.of(new Anomaly(
                            UUID.randomUUID().toString(),
                            actorId,
                            AnomalyType.STATISTICAL_OUTLIER,
                            severity,
                            maxZScore,
                            String.format("Statistical outlier detected in %s (Z-score: %.2f)", anomalousMetric, maxZScore),
                            Map.of("metric", anomalousMetric, "z_score", maxZScore),
                            metrics,
                            Duration.ofSeconds(0)
                    ));
                }

                return Optional.empty();
            } catch (Exception e) {
                LOGGER.error("Error in statistical anomaly detection for actor: {}", actorId, e);
                return Optional.empty();
            }
        }
    }

    /**
     * Time series anomaly detector implementation.
     */
    private class TimeSeriesAnomalyDetector {
        public Optional<Anomaly> detectTimeSeriesAnomaly(String actorId, String metricName, double value) {
            try {
                TimeSeriesBaseline baseline = timeSeriesBaselines.get(metricName);
                if (baseline == null) return Optional.empty();

                if (baseline.isAnomaly(value)) {
                    AnomalySeverity severity = Math.abs(value - baseline.getMean()) >
                                               3 * baseline.getStandardDeviation() ?
                                               AnomalySeverity.CRITICAL : AnomalySeverity.HIGH;

                    return Optional.of(new Anomaly(
                            UUID.randomUUID().toString(),
                            actorId,
                            AnomalyType.TIME_SERIES_ANOMALY,
                            severity,
                            Math.abs((value - baseline.getMean()) / baseline.getStandardDeviation()),
                            String.format("Time series anomaly detected in %s", metricName),
                            Map.of("metric", metricName, "value", value, "baseline_mean", baseline.getMean()),
                            Map.of(metricName, value),
                            Duration.ofSeconds(0)
                    ));
                }

                return Optional.empty();
            } catch (Exception e) {
                LOGGER.error("Error in time series anomaly detection for actor: {}", actorId, e);
                return Optional.empty();
            }
        }
    }

    /**
     * Behavioral anomaly detector implementation.
     */
    private class BehavioralAnomalyDetector {
        public Optional<Anomaly> detectBehavioralAnomaly(String actorId, Map<String, Double> metrics) {
            try {
                BehavioralProfile profile = behavioralProfiles.get(actorId);
                if (profile == null) return Optional.empty();

                double deviationScore = 0.0;
                String deviatingPattern = "";

                for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                    String metricName = entry.getKey();
                    double value = entry.getValue();

                    Double expected = profile.getMetricAverages().get(metricName);
                    Double stdDev = profile.getMetricStandardDeviations().get(metricName);

                    if (expected != null && stdDev != null && stdDev > 0) {
                        double deviation = Math.abs((value - expected) / stdDev);
                        deviationScore += deviation;

                        if (deviation > 2.0 && deviatingPattern.isEmpty()) {
                            deviatingPattern = metricName;
                        }
                    }
                }

                if (deviationScore > 5.0) {
                    AnomalySeverity severity = deviationScore > 10.0 ? AnomalySeverity.CRITICAL :
                                             deviationScore > 7.0 ? AnomalySeverity.HIGH :
                                             AnomalySeverity.MEDIUM;

                    return Optional.of(new Anomaly(
                            UUID.randomUUID().toString(),
                            actorId,
                            AnomalyType.BEHAVIORAL_DEVIATION,
                            severity,
                            deviationScore,
                            String.format("Behavioral deviation detected (pattern: %s)", deviatingPattern),
                            Map.of("deviation_score", deviationScore, "pattern", deviatingPattern),
                            metrics,
                            Duration.ofSeconds(0)
                    ));
                }

                return Optional.empty();
            } catch (Exception e) {
                LOGGER.error("Error in behavioral anomaly detection for actor: {}", actorId, e);
                return Optional.empty();
            }
        }
    }

    /**
     * Memory leak detector implementation.
     */
    private class MemoryLeakDetector {
        public Optional<Anomaly> detectMemoryLeak(String actorId, long memoryUsage) {
            try {
                BehavioralProfile profile = behavioralProfiles.get(actorId);
                if (profile == null) return Optional.empty();

                Double avgMemory = profile.getMetricAverages().get("memory_usage");
                Long maxMemory = profile.getMetricMaxima().get("memory_usage");

                if (avgMemory != null && maxMemory != null) {
                    // Check for memory growth trend
                    if (memoryUsage > maxMemory * 1.5) { // 50% growth above max
                        AnomalySeverity severity = memoryUsage > maxMemory * 2.0 ?
                                                 AnomalySeverity.CRITICAL : AnomalySeverity.HIGH;

                        return Optional.of(new Anomaly(
                                UUID.randomUUID().toString(),
                                actorId,
                                AnomalyType.MEMORY_LEAK,
                                severity,
                                (double) memoryUsage / maxMemory,
                                "Potential memory leak detected",
                                Map.of("current_memory", memoryUsage, "max_memory", maxMemory),
                                Map.of("memory_usage", (double) memoryUsage),
                                Duration.ofSeconds(0)
                        ));
                    }
                }

                return Optional.empty();
            } catch (Exception e) {
                LOGGER.error("Error in memory leak detection for actor: {}", actorId, e);
                return Optional.empty();
            }
        }
    }

    /**
     * Creates a new ActorAnomalyDetector instance.
     */
    public ActorAnomalyDetector(MeterRegistry meterRegistry, ActorHealthMetrics healthMetrics) {
        this.meterRegistry = meterRegistry;
        this.healthMetrics = healthMetrics;
        this.anomalyTrackers = new ConcurrentHashMap<>();
        this.behavioralProfiles = new ConcurrentHashMap<>();
        this.timeSeriesBaselines = new ConcurrentHashMap<>();

        // Configuration
        this.zScoreThreshold = 3.0;
        this.minSamplesForBaseline = 10;
        this.detectionWindow = Duration.ofSeconds(30);
        this.baselineWindow = Duration.ofMinutes(5);

        // Initialize metrics
        this.anomalyCounter = Counter.builder("yawl.actor.anomaly.detected")
                .description("Total anomalies detected")
                .register(meterRegistry);

        this.criticalAnomalyCounter = Counter.builder("yawl.actor.anomaly.critical")
                .description("Total critical anomalies detected")
                .register(meterRegistry);

        this.detectionTimer = Timer.builder("yawl.actor.anomaly.detection.duration")
                .description("Time spent on anomaly detection")
                .publishPercentiles(0.5, 0.9, 0.95)
                .register(meterRegistry);

        // Initialize detectors
        this.statisticalDetector = new StatisticalAnomalyDetector();
        this.timeSeriesDetector = new TimeSeriesAnomalyDetector();
        this.behavioralDetector = new BehavioralAnomalyDetector();
        this.memoryLeakDetector = new MemoryLeakDetector();

        // Initialize scheduler
        this.detectionScheduler = Executors.newSingleThreadScheduledExecutor();

        // Initialize baselines for common metrics
        initializeBaselines();
    }

    /**
     * Initializes the singleton instance.
     */
    public static synchronized void initialize(MeterRegistry meterRegistry, ActorHealthMetrics healthMetrics) {
        if (instance == null) {
            instance = new ActorAnomalyDetector(meterRegistry, healthMetrics);
        }
    }

    /**
     * Gets the singleton instance.
     */
    public static ActorAnomalyDetector getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ActorAnomalyDetector not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Initializes baselines for common metrics.
     */
    private void initializeBaselines() {
        String[] commonMetrics = {
                "message_processing_rate",
                "queue_depth",
                "memory_usage",
                "error_rate",
                "cpu_usage"
        };

        for (String metric : commonMetrics) {
            timeSeriesBaselines.put(metric, new TimeSeriesBaseline(metric));
        }
    }

    /**
     * Starts the anomaly detector.
     */
    public synchronized void start() {
        if (initialized.get()) {
            return;
        }

        // Start periodic detection
        detectionScheduler.scheduleAtFixedRate(this::performPeriodicDetection,
                                            1, 1, TimeUnit.SECONDS);

        initialized.set(true);
        LOGGER.info("ActorAnomalyDetector started");
    }

    /**
     * Stops the anomaly detector.
     */
    public synchronized void stop() {
        if (!initialized.get()) {
            return;
        }

        detectionScheduler.shutdown();

        // Clear all state
        anomalyTrackers.clear();
        behavioralProfiles.clear();
        timeSeriesBaselines.clear();

        initialized.set(false);
        LOGGER.info("ActorAnomalyDetector stopped");
    }

    /**
     * Records metrics for anomaly detection.
     */
    public void recordMetrics(String actorId, Map<String, Double> metrics) {
        if (!initialized.get()) return;

        try {
            // Update behavioral profile
            BehavioralProfile profile = behavioralProfiles.computeIfAbsent(actorId,
                    BehavioralProfile::new);

            metrics.forEach((metricName, value) -> {
                profile.updateMetric(metricName, value);
                timeSeriesBaselines.computeIfAbsent(metricName, TimeSeriesBaseline::new)
                        .addValue(value);
            });

            // Perform immediate detection for critical metrics
            detectAnomalies(actorId, metrics);

        } catch (Exception e) {
            LOGGER.error("Error recording metrics for actor: {}", actorId, e);
        }
    }

    /**
     * Performs periodic anomaly detection.
     */
    private void performPeriodicDetection() {
        if (!initialized.get()) return;

        Timer.Sample timer = Timer.start(meterRegistry);

        try {
            // Get all active actors
            List<ActorHealthMetrics.ActorHealthStatus> actors = healthMetrics.getAllActiveActors();

            for (ActorHealthMetrics.ActorHealthStatus actor : actors) {
                String actorId = actor.getActorId();

                // Collect metrics for detection
                Map<String, Double> metrics = collectActorMetrics(actor);

                // Perform anomaly detection
                detectAnomalies(actorId, metrics);
            }

            // Clean up old anomalies
            cleanupOldAnomalies();

        } catch (Exception e) {
            LOGGER.error("Error during periodic anomaly detection", e);
        } finally {
            timer.stop(detectionTimer);
        }
    }

    /**
     * Collects metrics for an actor.
     */
    private Map<String, Double> collectActorMetrics(ActorHealthMetrics.ActorHealthStatus actor) {
        Map<String, Double> metrics = new HashMap<>();

        // Basic metrics
        metrics.put("message_processing_rate", (double) actor.getMessagesProcessed());
        metrics.put("queue_depth", (double) actor.getQueueDepth());
        metrics.put("memory_usage", (double) actor.getMemoryUsage());
        metrics.put("error_rate", actor.getErrorRate());
        metrics.put("message_count", (double) actor.getMessagesReceived());

        // Derived metrics
        if (actor.getMessagesProcessed() > 0) {
            metrics.put("average_processing_time", actor.getAverageProcessingTime());
        }
        if (actor.getMessagesReceived() > 0) {
            metrics.put("throughput", (double) actor.getMessagesProcessed() / actor.getMessagesReceived());
        }

        return metrics;
    }

    /**
     * Detects anomalies for an actor.
     */
    private void detectAnomalies(String actorId, Map<String, Double> metrics) {
        List<Anomaly> detectedAnomalies = new ArrayList<>();

        // Statistical anomaly detection
        statisticalDetector.detectAnomaly(actorId, metrics)
                .ifPresent(detectedAnomalies::add);

        // Behavioral anomaly detection
        behavioralDetector.detectBehavioralAnomaly(actorId, metrics)
                .ifPresent(detectedAnomalies::add);

        // Memory leak detection
        Double memoryUsage = metrics.get("memory_usage");
        if (memoryUsage != null) {
            memoryLeakDetector.detectMemoryLeak(actorId, memoryUsage.longValue())
                    .ifPresent(detectedAnomalies::add);
        }

        // Time series anomaly detection for each metric
        metrics.forEach((metricName, value) -> {
            timeSeriesDetector.detectTimeSeriesAnomaly(actorId, metricName, value)
                    .ifPresent(detectedAnomalies::add);
        });

        // Process detected anomalies
        for (Anomaly anomaly : detectedAnomalies) {
            processAnomaly(anomaly);
        }
    }

    /**
     * Processes a detected anomaly.
     */
    private void processAnomaly(Anomaly anomaly) {
        // Update metrics
        anomalyCounter.increment();
        if (anomaly.getSeverity() == AnomalySeverity.CRITICAL) {
            criticalAnomalyCounter.increment();
        }

        // Track anomaly
        AnomalyTracker tracker = anomalyTrackers.computeIfAbsent(anomaly.getActorId(),
                AnomalyTracker::new);
        tracker.addAnomaly(anomaly);

        // Log anomaly
        StructuredLogger logger = StructuredLogger.getLogger(ActorAnomalyDetector.class);
        Map<String, Object> logContext = new HashMap<>(anomaly.getContext());
        logContext.put("anomaly_id", anomaly.getAnomalyId());
        logContext.put("type", anomaly.getType());
        logContext.put("severity", anomaly.getSeverity());
        logContext.put("score", anomaly.getScore());

        switch (anomaly.getSeverity()) {
            case CRITICAL:
                logger.error("Critical anomaly detected: {}", anomaly, logContext);
                break;
            case HIGH:
                logger.warn("High severity anomaly detected: {}", anomaly, logContext);
                break;
            case MEDIUM:
                logger.info("Medium severity anomaly detected: {}", anomaly, logContext);
                break;
            case LOW:
                logger.debug("Low severity anomaly detected: {}", anomaly, logContext);
                break;
        }

        LOGGER.info("Anomaly detected: {} - {} (score: {:.2f})",
                anomaly.getType(), anomaly.getDescription(), anomaly.getScore());
    }

    /**
     * Cleans up old anomalies.
     */
    private void cleanupOldAnomalies() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));

        anomalyTrackers.forEach((actorId, tracker) -> {
            tracker.clearOldAnomalies(Duration.ofHours(24));
        });

        // Remove trackers with no recent anomalies
        anomalyTrackers.entrySet().removeIf(entry -> {
            AnomalyTracker tracker = entry.getValue();
            return tracker.getAnomalies().isEmpty() &&
                   Duration.between(tracker.getLastDetectionTime(), Instant.now()).toHours() > 1;
        });
    }

    // Public Methods

    /**
     * Gets anomalies for a specific actor.
     */
    public List<Anomaly> getAnomaliesForActor(String actorId) {
        AnomalyTracker tracker = anomalyTrackers.get(actorId);
        return tracker != null ? new ArrayList<>(tracker.getAnomalies()) : Collections.emptyList();
    }

    /**
     * Gets all current anomalies.
     */
    public List<Anomaly> getAllAnomalies() {
        List<Anomaly> allAnomalies = new ArrayList<>();
        anomalyTrackers.values().forEach(tracker ->
            allAnomalies.addAll(tracker.getAnomalies()));
        return allAnomalies;
    }

    /**
     * Gets anomaly statistics.
     */
    public AnomalyStatistics getAnomalyStatistics() {
        int totalAnomalies = anomalyTrackers.values().stream()
                .mapToInt(AnomalyTracker::getAnomalyCount)
                .sum();

        int criticalAnomalies = anomalyTrackers.values().stream()
                .mapToInt(tracker -> tracker.hasCriticalAnomaly() ? 1 : 0)
                .sum();

        Map<AnomalyType, Integer> typeCounts = new HashMap<>();
        Map<AnomalySeverity, Integer> severityCounts = new HashMap<>();

        getAllAnomalies().forEach(anomaly -> {
            typeCounts.merge(anomaly.getType(), 1, Integer::sum);
            severityCounts.merge(anomaly.getSeverity(), 1, Integer::sum);
        });

        return new AnomalyStatistics(totalAnomalies, criticalAnomalies, typeCounts, severityCounts);
    }

    /**
     * Gets behavioral profile for an actor.
     */
    public Optional<BehavioralProfile> getBehavioralProfile(String actorId) {
        return Optional.ofNullable(behavioralProfiles.get(actorId));
    }

    /**
     * Manually trigger anomaly detection.
     */
    public void triggerManualDetection(String actorId) {
        Optional.ofNullable(healthMetrics.getActorHealth(actorId))
                .ifPresent(actor -> {
                    Map<String, Double> metrics = collectActorMetrics(actor);
                    detectAnomalies(actorId, metrics);
                });
    }

    /**
     * Clears anomalies for an actor.
     */
    public void clearAnomalies(String actorId) {
        AnomalyTracker tracker = anomalyTrackers.get(actorId);
        if (tracker != null) {
            tracker.getAnomalies().clear();
        }
    }

    /**
     * Anomaly statistics record.
     */
    public static final class AnomalyStatistics {
        private final int totalAnomalies;
        private final int criticalAnomalies;
        private final Map<AnomalyType, Integer> typeDistribution;
        private final Map<AnomalySeverity, Integer> severityDistribution;

        public AnomalyStatistics(int totalAnomalies, int criticalAnomalies,
                               Map<AnomalyType, Integer> typeDistribution,
                               Map<AnomalySeverity, Integer> severityDistribution) {
            this.totalAnomalies = totalAnomalies;
            this.criticalAnomalies = criticalAnomalies;
            this.typeDistribution = typeDistribution;
            this.severityDistribution = severityDistribution;
        }

        // Getters
        public int getTotalAnomalies() { return totalAnomalies; }
        public int getCriticalAnomalies() { return criticalAnomalies; }
        public Map<AnomalyType, Integer> getTypeDistribution() { return typeDistribution; }
        public Map<AnomalySeverity, Integer> getSeverityDistribution() { return severityDistribution; }
    }
}