package org.yawlfoundation.yawl.observability.actor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced memory leak detection for YAWL actors.
 *
 * Monitors actor memory usage patterns and detects:
 * - Exponential memory growth
 * - Unbounded accumulation
 * - Memory leaks in weak references
 * - Resource accumulation without cleanup
 *
 * Integration Points:
 * - ActorHealthMetrics for memory data
 * - MeterRegistry for metrics collection
 * - Alert management for violation notifications
 */
public class ActorLeakDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorLeakDetector.class);

    private final MeterRegistry meterRegistry;
    private final ActorHealthMetrics healthMetrics;
    private final Map<String, ActorMemoryProfile> memoryProfiles;

    // Metrics
    private final Counter leakDetectedCounter;
    private final Timer leakDetectionTimer;
    private final Counter falsePositiveCounter;
    private final AtomicLong totalActorsMonitored;

    // Configuration
    private final long maxMemoryThreshold = 50 * 1024 * 1024; // 50MB
    private final int minSamplesForDetection = 10;
    private final double growthRateThreshold = 0.1; // 10% growth per minute
    private final long profileHistorySize = 100;

    /**
     * Creates a new ActorLeakDetector instance.
     */
    public ActorLeakDetector(MeterRegistry meterRegistry,
                           ActorHealthMetrics healthMetrics) {
        this.meterRegistry = meterRegistry;
        this.healthMetrics = healthMetrics;
        this.memoryProfiles = new ConcurrentHashMap<>();

        // Initialize metrics
        this.leakDetectedCounter = Counter.builder("yawl.actor.leak.detected")
            .description("Number of actor memory leaks detected")
            .register(meterRegistry);

        this.leakDetectionTimer = Timer.builder("yawl.actor.leak.detection.duration")
            .description("Time spent detecting actor memory leaks")
            .publishPercentiles(0.5, 0.9, 0.95, 0.99)
            .register(meterRegistry);

        this.falsePositiveCounter = Counter.builder("yawl.actor.leak.false_positive")
            .description("Number of false positive leak detections")
            .register(meterRegistry);

        this.totalActorsMonitored = new AtomicLong(0);

        // Initialize dashboard metrics
        initializeMetrics();
    }

    /**
     * Initializes all metrics.
     */
    private void initializeMetrics() {
        // Actor monitoring metrics
        io.micrometer.core.instrument.Gauge.builder("yawl.actor.leak.monitoring.actors",
                () -> memoryProfiles.size())
            .description("Number of actors currently being monitored for leaks")
            .register(meterRegistry);

        // Leak severity metrics
        io.micrometer.core.instrument.Gauge.builder("yawl.actor.leak.severity.unknown",
                () -> getActorsBySeverity(Severity.UNKNOWN).size())
            .description("Number of actors with unknown leak severity")
            .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("yawl.actor.leak.severity.low",
                () -> getActorsBySeverity(Severity.LOW).size())
            .description("Number of actors with low severity leaks")
            .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("yawl.actor.leak.severity.medium",
                () -> getActorsBySeverity(Severity.MEDIUM).size())
            .description("Number of actors with medium severity leaks")
            .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("yawl.actor.leak.severity.high",
                () -> getActorsBySeverity(Severity.HIGH).size())
            .description("Number of actors with high severity leaks")
            .register(meterRegistry);
    }

    /**
     * Checks for memory leaks in a specific actor.
     */
    public void checkForLeaks(String actorId) {
        long startTime = System.currentTimeMillis();

        ActorHealthMetrics.ActorHealthStatus actor = healthMetrics.getActorHealth(actorId);
        if (actor == null) return;

        totalActorsMonitored.incrementAndGet();

        ActorMemoryProfile profile = memoryProfiles.computeIfAbsent(actorId,
            id -> new ActorMemoryProfile(id));

        // Record current memory usage
        long currentMemory = actor.getMemoryUsage();
        profile.recordMemoryUsage(currentMemory, Instant.now());

        // Analyze memory patterns
        LeakAnalysisResult result = analyzeMemoryPatterns(profile, actorId);

        if (result.isLeakDetected()) {
            leakDetectedCounter.increment();
            LOGGER.warn("Potential memory leak detected in actor {}: {} bytes, severity: {}",
                       actorId, currentMemory, result.getSeverity());

            // Trigger alert via health metrics
            healthMetrics.recordLeakWarning(actorId);

            // Record severity-based metrics
            recordSeverityMetrics(result.getSeverity());

            // Create detailed alert context
            Map<String, Object> alertContext = createAlertContext(actorId, currentMemory, result);
            triggerLeakAlert(actorId, currentMemory, alertContext);
        } else if (result.isFalsePositive()) {
            falsePositiveCounter.increment();
            LOGGER.debug("False positive leak detection for actor {}: {} bytes",
                        actorId, currentMemory);
        }

        leakDetectionTimer.record(System.currentTimeMillis() - startTime,
                               java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Analyzes memory usage patterns to detect leaks.
     */
    private LeakAnalysisResult analyzeMemoryPatterns(ActorMemoryProfile profile, String actorId) {
        if (profile.getSampleCount() < minSamplesForDetection) {
            return LeakAnalysisResult.noLeak();
        }

        // Check for absolute threshold violations
        long maxMemory = profile.getMaxMemoryUsage();
        if (maxMemory > maxMemoryThreshold) {
            return LeakAnalysisResult.leakDetected(
                determineSeverity(maxMemory, maxMemoryThreshold),
                "Memory usage exceeds absolute threshold"
            );
        }

        // Check for exponential growth
        double growthRate = profile.calculateGrowthRate();
        if (growthRate > growthRateThreshold) {
            return LeakAnalysisResult.leakDetected(
                determineGrowthSeverity(growthRate),
                "Exponential memory growth detected"
            );
        }

        // Check for steady accumulation without bounds
        if (profile.hasUnboundedAccumulation()) {
            return LeakAnalysisResult.leakDetected(
                Severity.MEDIUM,
                "Unbounded memory accumulation detected"
            );
        }

        return LeakAnalysisResult.noLeak();
    }

    /**
     * Determines leak severity based on memory usage.
     */
    private Severity determineSeverity(long currentMemory, long threshold) {
        double ratio = (double) currentMemory / threshold;
        if (ratio > 2.0) return Severity.HIGH;
        if (ratio > 1.5) return Severity.MEDIUM;
        return Severity.LOW;
    }

    /**
     * Determines severity based on growth rate.
     */
    private Severity determineGrowthSeverity(double growthRate) {
        if (growthRate > 0.5) return Severity.HIGH; // 50%+ growth per minute
        if (growthRate > 0.2) return Severity.MEDIUM; // 20%+ growth per minute
        return Severity.LOW; // 10%+ growth per minute
    }

    /**
     * Creates alert context for leak detection.
     */
    private Map<String, Object> createAlertContext(String actorId, long currentMemory,
                                                  LeakAnalysisResult result) {
        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("actor_id", actorId);
        context.put("current_memory_bytes", currentMemory);
        context.put("severity", result.getSeverity().toString());
        context.put("detection_reason", result.getReason());
        context.put("growth_rate", memoryProfiles.get(actorId).calculateGrowthRate());
        context.put("sample_count", memoryProfiles.get(actorId).getSampleCount());
        return context;
    }

    /**
     * Triggers a leak alert.
     */
    private void triggerLeakAlert(String actorId, long memoryBytes,
                               Map<String, Object> context) {
        // Use existing alert manager if available
        ActorObservabilityService service = ActorObservabilityService.getInstance();
        if (service != null) {
            service.triggerAlert(
                ActorAlertManager.AlertType.ACTOR_MEMORY_LEAK,
                ActorAlertManager.AlertSeverity.HIGH,
                actorId,
                "Memory leak detected: " + memoryBytes + " bytes",
                memoryBytes
            );
        }

        // Record the violation
        if (service != null && service.getDashboardData() != null) {
            service.getDashboardData().recordGuardViolation(
                actorId,
                "H_ACTOR_LEAK",
                "Memory leak detected: " + memoryBytes + " bytes"
            );
        }
    }

    /**
     * Records severity-based metrics.
     */
    private void recordSeverityMetrics(Severity severity) {
        String severityTag = severity.toString().toLowerCase();
        meterRegistry.counter("yawl.actor.leak.detected.by_severity", "severity", severityTag)
            .increment();
    }

    /**
     * Gets actors by severity level.
     */
    private Map<String, Severity> getActorsBySeverity(Severity severity) {
        Map<String, Severity> result = new ConcurrentHashMap<>();
        memoryProfiles.forEach((actorId, profile) -> {
            if (profile.getCurrentSeverity() == severity) {
                result.put(actorId, severity);
            }
        });
        return result;
    }

    /**
     * Memory severity enumeration.
     */
    public enum Severity {
        UNKNOWN, LOW, MEDIUM, HIGH
    }

    /**
     * Leak analysis result.
     */
    public static final class LeakAnalysisResult {
        private final boolean leakDetected;
        private final Severity severity;
        private final String reason;
        private final boolean falsePositive;

        private LeakAnalysisResult(boolean leakDetected, Severity severity, String reason,
                                 boolean falsePositive) {
            this.leakDetected = leakDetected;
            this.severity = severity;
            this.reason = reason;
            this.falsePositive = falsePositive;
        }

        public static LeakAnalysisResult noLeak() {
            return new LeakAnalysisResult(false, null, null, false);
        }

        public static LeakAnalysisResult leakDetected(Severity severity, String reason) {
            return new LeakAnalysisResult(true, severity, reason, false);
        }

        public static LeakAnalysisResult falsePositive(String reason) {
            return new LeakAnalysisResult(false, null, reason, true);
        }

        // Getters
        public boolean isLeakDetected() { return leakDetected; }
        public Severity getSeverity() { return severity; }
        public String getReason() { return reason; }
        public boolean isFalsePositive() { return falsePositive; }
    }

    /**
     * Memory profile for actor monitoring.
     */
    public static final class ActorMemoryProfile {
        private final String actorId;
        private final java.util.Deque<Long> memoryHistory;
        private final java.util.Deque<Instant> timestampHistory;
        private final java.util.Deque<Double> growthRates;
        private long maxMemoryUsage;
        private Instant lastUpdate;

        public ActorMemoryProfile(String actorId) {
            this.actorId = actorId;
            this.memoryHistory = new java.util.ArrayDeque<>(profileHistorySize);
            this.timestampHistory = new java.util.ArrayDeque<>(profileHistorySize);
            this.growthRates = new java.util.ArrayDeque<>(20); // Keep last 20 growth rates
            this.maxMemoryUsage = 0;
            this.lastUpdate = Instant.now();
        }

        public void recordMemoryUsage(long memoryBytes, Instant timestamp) {
            memoryHistory.add(memoryBytes);
            timestampHistory.add(timestamp);

            if (memoryHistory.size() > profileHistorySize) {
                memoryHistory.poll();
                timestampHistory.poll();
            }

            if (memoryBytes > maxMemoryUsage) {
                maxMemoryUsage = memoryBytes;
            }

            lastUpdate = timestamp;

            // Calculate growth rate
            if (memoryHistory.size() >= 2) {
                double growthRate = calculateGrowthRate();
                growthRates.add(growthRate);
                if (growthRates.size() > 20) {
                    growthRates.poll();
                }
            }
        }

        public double calculateGrowthRate() {
            if (memoryHistory.size() < 2) return 0;

            Long[] memories = memoryHistory.toArray(new Long[0]);
            Instant[] timestamps = timestampHistory.toArray(new Instant[0]);

            long recentCount = Math.min(10, memories.length);
            long startMem = memories[0];
            long endMem = memories[recentCount - 1];
            long startTime = timestamps[0].toEpochMilli();
            long endTime = timestamps[recentCount - 1].toEpochMilli();

            if (startMem == 0 || endTime - startTime == 0) return 0;

            return (double)(endMem - startMem) / (startMem * (endTime - startTime));
        }

        public boolean hasUnboundedAccumulation() {
            if (memoryHistory.size() < 5) return false;

            // Check for steady increase without bound
            Long[] memories = memoryHistory.toArray(new Long[0]);
            boolean steadilyIncreasing = true;
            for (int i = 1; i < memories.length; i++) {
                if (memories[i] <= memories[i-1]) {
                    steadilyIncreasing = false;
                    break;
                }
            }

            return steadilyIncreasing;
        }

        public int getSampleCount() { return memoryHistory.size(); }
        public long getMaxMemoryUsage() { return maxMemoryUsage; }
        public Severity getCurrentSeverity() {
            double growthRate = calculateGrowthRate();
            if (growthRate > 0.5) return Severity.HIGH;
            if (growthRate > 0.2) return Severity.MEDIUM;
            if (maxMemoryUsage > maxMemoryThreshold * 0.8) return Severity.MEDIUM;
            if (maxMemoryUsage > maxMemoryThreshold * 0.5) return Severity.LOW;
            return Severity.UNKNOWN;
        }
    }
}