package org.yawlfoundation.yawl.observability.actor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central integration point for actor guard pattern detection.
 *
 * Orchestrates H_ACTOR_LEAK and H_ACTOR_DEADLOCK detection while
 * maintaining performance constraints (<1ms latency, <5% overhead).
 *
 * Integration Points:
 * - ActorHealthMetrics for data collection
 * - ActorTracer for interaction tracking
 * - Alert management for violation notifications
 */
public class ActorGuardValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorGuardValidator.class);
    private static ActorGuardValidator instance;

    private final ActorHealthMetrics healthMetrics;
    private final ActorTracer tracer;
    private final ActorLeakDetector leakDetector;
    private final ActorDeadlockDetector deadlockDetector;
    private final ScheduledExecutorService validationScheduler;

    // Performance constraints
    private final Duration maxValidationTime = Duration.ofMillis(1);
    private final int maxViolationsPerCheck = 100;
    private final int validationIntervalSeconds = 30;

    // Metrics
    private final AtomicInteger activeValidations;
    private final AtomicInteger completedValidations;
    private final AtomicInteger violatedValidations;
    private final Timer validationTimer;
    private final AtomicLong totalValidationTime;

    /**
     * Creates a new ActorGuardValidator instance.
     */
    public ActorGuardValidator(MeterRegistry meterRegistry,
                              ActorHealthMetrics healthMetrics,
                              ActorTracer tracer) {
        this.healthMetrics = healthMetrics;
        this.tracer = tracer;
        this.validationScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        this.activeValidations = new AtomicInteger(0);
        this.completedValidations = new AtomicInteger(0);
        this.violatedValidations = new AtomicInteger(0);
        this.totalValidationTime = new AtomicLong(0);

        // Initialize detectors
        this.leakDetector = new ActorLeakDetector(meterRegistry, healthMetrics);
        this.deadlockDetector = new ActorDeadlockDetector(tracer, healthMetrics);

        // Initialize metrics
        this.validationTimer = Timer.builder("yawl.actor.guard.validation.duration")
            .description("Time spent on actor guard validation")
            .publishPercentiles(0.5, 0.9, 0.95, 0.99)
            .register(meterRegistry);

        initializeMetrics();
    }

    /**
     * Initializes the singleton instance.
     */
    public static synchronized void initialize(MeterRegistry meterRegistry,
                                            ActorHealthMetrics healthMetrics,
                                            ActorTracer tracer) {
        if (instance == null) {
            instance = new ActorGuardValidator(meterRegistry, healthMetrics, tracer);
        }
    }

    /**
     * Gets the singleton instance.
     */
    public static ActorGuardValidator getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ActorGuardValidator not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Initializes all metrics.
     */
    private void initializeMetrics() {
        // Validation metrics
        io.micrometer.core.instrument.Gauge.builder("yawl.actor.guard.validations.active",
                activeValidations::get)
            .description("Number of active actor validations")
            .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("yawl.actor.guard.validations.completed",
                completedValidations::get)
            .description("Number of completed actor validations")
            .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("yawl.actor.guard.validations.violated",
                violatedValidations::get)
            .description("Number of violated actor validations")
            .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("yawl.actor.guard.validation.total_time_ms",
                () -> totalValidationTime.get())
            .description("Total validation time in milliseconds")
            .register(meterRegistry);
    }

    /**
     * Starts the validator and background validation scheduler.
     */
    public void start() {
        validationScheduler.scheduleAtFixedRate(
            this::performPeriodicValidation,
            validationIntervalSeconds,
            validationIntervalSeconds,
            TimeUnit.SECONDS
        );

        LOGGER.info("ActorGuardValidator started with {}s interval", validationIntervalSeconds);
    }

    /**
     * Stops the validator and cleanup resources.
     */
    public void stop() {
        validationScheduler.shutdown();
        try {
            if (!validationScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                validationScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            validationScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("ActorGuardValidator stopped");
    }

    /**
     * Performs periodic validation of all active actors.
     */
    private void performPeriodicValidation() {
        long startTime = System.currentTimeMillis();
        activeValidations.set(0);
        completedValidations.set(0);
        violatedValidations.set(0);

        try {
            // Get all active actors
            java.util.List<ActorHealthMetrics.ActorHealthStatus> actors =
                healthMetrics.getAllActiveActors();

            // Process actors within time constraints
            for (ActorHealthMetrics.ActorHealthStatus actor : actors) {
                if (Duration.ofMillis(System.currentTimeMillis() - startTime)
                    .compareTo(maxValidationTime) > 0) {
                    break; // Respect performance constraints
                }

                activeValidations.incrementAndGet();
                validateActor(actor.getActorId());
                completedValidations.incrementAndGet();
            }

            // Record validation time
            long validationTime = System.currentTimeMillis() - startTime;
            totalValidationTime.addAndGet(validationTime);
            validationTimer.record(validationTime, TimeUnit.MILLISECONDS);

            LOGGER.debug("Periodic validation completed: {} actors validated in {}ms",
                        completedValidations.get(), validationTime);

        } catch (Exception e) {
            LOGGER.error("Error during periodic validation", e);
        }
    }

    /**
     * Validates a specific actor for guard violations.
     */
    public void validateActor(String actorId) {
        long startTime = System.currentTimeMillis();

        try {
            // Perform leak detection
            leakDetector.checkForLeaks(actorId);

            // Perform deadlock detection
            deadlockDetector.checkForDeadlocks(actorId);

            // Check overall health metrics
            checkActorHealth(actorId);

        } catch (Exception e) {
            LOGGER.error("Error validating actor {}", actorId, e);
            violatedValidations.incrementAndGet();
        }
    }

    /**
     * Validates actor health based on metrics.
     */
    private void checkActorHealth(String actorId) {
        ActorHealthMetrics.ActorHealthStatus actor = healthMetrics.getActorHealth(actorId);
        if (actor == null) return;

        // Check error rate
        double errorRate = actor.getErrorRate();
        if (errorRate > 0.1) { // 10% error rate threshold
            violatedValidations.incrementAndGet();
            LOGGER.warn("High error rate detected in actor {}: {}", actorId, errorRate);
            healthMetrics.recordLeakWarning(actorId);
        }

        // Check queue depth
        int queueDepth = actor.getQueueDepth();
        if (queueDepth > 1000) {
            violatedValidations.incrementAndGet();
            LOGGER.warn("High queue depth detected in actor {}: {}", actorId, queueDepth);
        }

        // Check memory usage
        long memoryUsage = actor.getMemoryUsage();
        if (memoryUsage > 10 * 1024 * 1024) { // 10MB threshold
            violatedValidations.incrementAndGet();
            LOGGER.warn("High memory usage detected in actor {}: {} bytes",
                       actorId, memoryUsage);
        }
    }

    /**
     * Gets validation statistics.
     */
    public ValidationStatistics getValidationStatistics() {
        return new ValidationStatistics(
            activeValidations.get(),
            completedValidations.get(),
            violatedValidations.get(),
            totalValidationTime.get()
        );
    }

    /**
     * Validation statistics record.
     */
    public static final class ValidationStatistics {
        private final int activeValidations;
        private final int completedValidations;
        private final int violatedValidations;
        private final long totalValidationTimeMs;

        public ValidationStatistics(int activeValidations, int completedValidations,
                                 int violatedValidations, long totalValidationTimeMs) {
            this.activeValidations = activeValidations;
            this.completedValidations = completedValidations;
            this.violatedValidations = violatedValidations;
            this.totalValidationTimeMs = totalValidationTimeMs;
        }

        // Getters
        public int getActiveValidations() { return activeValidations; }
        public int getCompletedValidations() { return completedValidations; }
        public int getViolatedValidations() { return violatedValidations; }
        public long getTotalValidationTimeMs() { return totalValidationTimeMs; }

        public double getViolationRate() {
            return completedValidations > 0 ?
                (double) violatedValidations / completedValidations : 0.0;
        }
    }
}