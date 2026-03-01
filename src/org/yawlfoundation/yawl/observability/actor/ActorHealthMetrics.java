package org.yawlfoundation.yawl.observability.actor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.observability.YawlMetrics;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Duration;
import java.time.Instant;

/**
 * Comprehensive health metrics for YAWL actor systems.
 *
 * Tracks actor-specific metrics including:
 * - Actor lifecycle events (creation, destruction, state changes)
 * - Message processing rates and latencies
 * - Memory usage per actor
 * - Health status and error rates
 * - Queue depths and backlog
 * - Resource utilization
 *
 * Integrates with existing YAWL observability infrastructure.
 */
public class ActorHealthMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorHealthMetrics.class);
    private static ActorHealthMetrics instance;

    private final MeterRegistry meterRegistry;
    private final YawlMetrics yawlMetrics;

    // Actor lifecycle counters
    private final Counter actorCreatedCounter;
    private final Counter actorDestroyedCounter;
    private final Counter actorErrorCounter;

    // Actor status gauges
    private final AtomicInteger activeActorCount;
    private final AtomicInteger unhealthyActorCount;
    private final AtomicInteger suspendedActorCount;

    // Message processing metrics
    private final Counter messagesSentCounter;
    private final Counter messagesReceivedCounter;
    private final Counter messagesProcessedCounter;
    private final Counter messagesFailedCounter;
    private final Timer messageProcessingTimer;

    // Queue metrics
    private final AtomicLong totalQueueDepth;
    private final AtomicLong maxQueueDepth;
    private final Timer queueWaitTimer;

    // Memory metrics
    private final AtomicLong totalActorMemory;
    private final AtomicLong maxActorMemory;
    private final ConcurrentMap<String, Long> actorMemoryMap;

    // Health tracking
    private final ConcurrentMap<String, ActorHealthStatus> actorHealthMap;
    private final AtomicLong lastHealthCheckTime;

    // Performance thresholds
    private final Duration maxProcessingTime;
    private final Duration maxQueueWaitTime;
    private final long maxMemoryPerActor;
    private final double maxErrorRate;

    /**
     * Creates a new ActorHealthMetrics instance.
     */
    public ActorHealthMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        this.yawlMetrics = YawlMetrics.getInstance();
        this.activeActorCount = new AtomicInteger(0);
        this.unhealthyActorCount = new AtomicInteger(0);
        this.suspendedActorCount = new AtomicInteger(0);
        this.totalQueueDepth = new AtomicLong(0);
        this.maxQueueDepth = new AtomicLong(0);
        this.totalActorMemory = new AtomicLong(0);
        this.maxActorMemory = new AtomicLong(0);
        this.lastHealthCheckTime = new AtomicLong(System.currentTimeMillis());
        this.actorMemoryMap = new ConcurrentHashMap<>();
        this.actorHealthMap = new ConcurrentHashMap<>();
        this.maxProcessingTime = Duration.ofMillis(5000); // 5 seconds
        this.maxQueueWaitTime = Duration.ofMillis(10000); // 10 seconds
        this.maxMemoryPerActor = 10 * 1024 * 1024; // 10MB per actor
        this.maxErrorRate = 0.05; // 5% error rate threshold

        initializeMetrics();
        LOGGER.info("ActorHealthMetrics initialized");
    }

    /**
     * Initializes the singleton instance.
     */
    public static synchronized void initialize(MeterRegistry meterRegistry) {
        if (instance == null) {
            instance = new ActorHealthMetrics(meterRegistry);
        }
    }

    /**
     * Gets the singleton instance.
     */
    public static ActorHealthMetrics getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ActorHealthMetrics not initialized. Call initialize() first.");
        }
        return instance;
    }

    private void initializeMetrics() {
        // Initialize counters
        this.actorCreatedCounter = Counter.builder("yawl.actor.created")
                .description("Total actors created")
                .register(meterRegistry);

        this.actorDestroyedCounter = Counter.builder("yawl.actor.destroyed")
                .description("Total actors destroyed")
                .register(meterRegistry);

        this.actorErrorCounter = Counter.builder("yawl.actor.error")
                .description("Total actor errors")
                .register(meterRegistry);

        this.messagesSentCounter = Counter.builder("yawl.actor.message.sent")
                .description("Total messages sent by actors")
                .register(meterRegistry);

        this.messagesReceivedCounter = Counter.builder("yawl.actor.message.received")
                .description("Total messages received by actors")
                .register(meterRegistry);

        this.messagesProcessedCounter = Counter.builder("yawl.actor.message.processed")
                .description("Total messages processed by actors")
                .register(meterRegistry);

        this.messagesFailedCounter = Counter.builder("yawl.actor.message.failed")
                .description("Total message processing failures")
                .register(meterRegistry);

        // Initialize timers
        this.messageProcessingTimer = Timer.builder("yawl.actor.message.processing.duration")
                .description("Time to process actor messages")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);

        this.queueWaitTimer = Timer.builder("yawl.actor.queue.wait.duration")
                .description("Time messages wait in actor queues")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);

        // Initialize gauges
        Gauge.builder("yawl.actor.count", activeActorCount::get)
                .description("Number of active actors")
                .register(meterRegistry);

        Gauge.builder("yawl.actor.unhealthy.count", unhealthyActorCount::get)
                .description("Number of unhealthy actors")
                .register(meterRegistry);

        Gauge.builder("yawl.actor.suspended.count", suspendedActorCount::get)
                .description("Number of suspended actors")
                .register(meterRegistry);

        Gauge.builder("yawl.actor.queue.total_depth", totalQueueDepth::get)
                .description("Total queue depth across all actors")
                .register(meterRegistry);

        Gauge.builder("yawl.actor.queue.max_depth", maxQueueDepth::get)
                .description("Maximum queue depth across all actors")
                .register(meterRegistry);

        Gauge.builder("yawl.actor.memory.total_bytes", totalActorMemory::get)
                .description("Total memory used by all actors")
                .register(meterRegistry);

        Gauge.builder("yawl.actor.memory.max_bytes", maxActorMemory::get)
                .description("Maximum memory used by any single actor")
                .register(meterRegistry);

        // Health check timer
        Gauge.builder("yawl.actor.health.last_check_timestamp_ms",
                () -> System.currentTimeMillis() - lastHealthCheckTime.get())
                .description("Time since last health check in milliseconds")
                .register(meterRegistry);
    }

    // Actor lifecycle methods

    /**
     * Records actor creation event.
     */
    public void recordActorCreated(String actorId, String actorType) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(actorType);

        actorCreatedCounter.increment();
        activeActorCount.incrementAndGet();

        actorHealthMap.put(actorId, new ActorHealthStatus(actorId, actorType));
        meterRegistry.counter("yawl.actor.created.total",
                "actor_type", actorType).increment();

        LOGGER.debug("Actor created: {} ({})", actorId, actorType);
    }

    /**
     * Records actor destruction event.
     */
    public void recordActorDestroyed(String actorId) {
        Objects.requireNonNull(actorId);

        actorDestroyedCounter.increment();
        activeActorCount.decrementAndGet();
        unhealthyActorCount.decrementAndGet();

        ActorHealthStatus health = actorHealthMap.remove(actorId);
        if (health != null) {
            totalActorMemory.addAndGet(-health.getMemoryUsage());
            actorMemoryMap.remove(actorId);
        }

        LOGGER.debug("Actor destroyed: {}", actorId);
    }

    /**
     * Records actor error event.
     */
    public void recordActorError(String actorId, String errorType, String errorMessage) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(errorType);

        actorErrorCounter.increment();
        ActorHealthStatus health = actorHealthMap.get(actorId);
        if (health != null) {
            health.recordError(errorType, errorMessage);
            if (health.getErrorCount() > 10) { // Mark unhealthy after 10 errors
                unhealthyActorCount.incrementAndGet();
            }
        }

        meterRegistry.counter("yawl.actor.error.total",
                "actor_id", actorId,
                "error_type", errorType).increment();

        LOGGER.warn("Actor error: {} - {} - {}", actorId, errorType, errorMessage);
    }

    /**
     * Records actor state change.
     */
    public void recordActorStateChange(String actorId, String oldState, String newState) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(oldState);
        Objects.requireNonNull(newState);

        ActorHealthStatus health = actorHealthMap.get(actorId);
        if (health != null) {
            health.setState(newState);
            health.setLastActivity(System.currentTimeMillis());

            // Track state changes
            if (newState.equals("suspended")) {
                suspendedActorCount.incrementAndGet();
            } else if (oldState.equals("suspended")) {
                suspendedActorCount.decrementAndGet();
            }
        }

        meterRegistry.counter("yawl.actor.state.changed",
                "actor_id", actorId,
                "old_state", oldState,
                "new_state", newState).increment();
    }

    // Message processing methods

    /**
     * Records message sent event.
     */
    public void recordMessageSent(String actorId, String messageType, long messageSize) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(messageType);

        messagesSentCounter.increment();
        ActorHealthStatus health = actorHealthMap.get(actorId);
        if (health != null) {
            health.recordMessageSent(messageType, messageSize);
        }

        meterRegistry.counter("yawl.actor.message.sent.total",
                "actor_id", actorId,
                "message_type", messageType).increment();
    }

    /**
     * Records message received event.
     */
    public void recordMessageReceived(String actorId, String messageType, long messageSize) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(messageType);

        messagesReceivedCounter.increment();
        ActorHealthStatus health = actorHealthMap.get(actorId);
        if (health != null) {
            health.recordMessageReceived(messageType, messageSize);
        }

        meterRegistry.counter("yawl.actor.message.received.total",
                "actor_id", actorId,
                "message_type", messageType).increment();
    }

    /**
     * Records message processing completion.
     */
    public void recordMessageProcessed(String actorId, String messageType, long processingTimeNanos) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(messageType);

        messagesProcessedCounter.increment();
        messageProcessingTimer.record(processingTimeNanos, java.util.concurrent.TimeUnit.NANOSECONDS);

        ActorHealthStatus health = actorHealthMap.get(actorId);
        if (health != null) {
            health.recordMessageProcessed(messageType, processingTimeNanos);
        }

        // Check for performance anomalies
        if (Duration.ofNanos(processingTimeNanos).compareTo(maxProcessingTime) > 0) {
            meterRegistry.counter("yawl.actor.message.processing.slow",
                    "actor_id", actorId,
                    "message_type", messageType).increment();
        }
    }

    /**
     * Records message processing failure.
     */
    public void recordMessageFailed(String actorId, String messageType, String errorType) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(messageType);
        Objects.requireNonNull(errorType);

        messagesFailedCounter.increment();

        ActorHealthStatus health = actorHealthMap.get(actorId);
        if (health != null) {
            health.recordMessageFailed(messageType, errorType);
        }

        meterRegistry.counter("yawl.actor.message.failed.total",
                "actor_id", actorId,
                "message_type", messageType,
                "error_type", errorType).increment();

        LOGGER.warn("Message processing failed: {} - {} - {}", actorId, messageType, errorType);
    }

    // Queue management methods

    /**
     * Updates queue depth for an actor.
     */
    public void updateQueueDepth(String actorId, int queueDepth) {
        Objects.requireNonNull(actorId);

        long currentTotal = totalQueueDepth.get();
        long oldActorDepth = actorHealthMap.getOrDefault(actorId,
                new ActorHealthStatus(actorId, "unknown")).getQueueDepth();

        long newTotal = currentTotal - oldActorDepth + queueDepth;
        totalQueueDepth.set(Math.max(0, newTotal));

        // Update max queue depth
        long currentMax = maxQueueDepth.get();
        if (queueDepth > currentMax) {
            maxQueueDepth.set(queueDepth);
        }

        ActorHealthStatus health = actorHealthMap.get(actorId);
        if (health != null) {
            health.setQueueDepth(queueDepth);
        }

        // Check for queue congestion
        if (queueDepth > 1000) { // Threshold for high queue depth
            meterRegistry.counter("yawl.actor.queue.congestion",
                    "actor_id", actorId).increment();
        }
    }

    /**
     * Records queue wait time.
     */
    public void recordQueueWaitTime(String actorId, long waitTimeNanos) {
        Objects.requireNonNull(actorId);

        queueWaitTimer.record(waitTimeNanos, java.util.concurrent.TimeUnit.NANOSECONDS);

        ActorHealthStatus health = actorHealthMap.get(actorId);
        if (health != null) {
            health.recordQueueWaitTime(waitTimeNanos);
        }

        // Check for long queue waits
        if (Duration.ofNanos(waitTimeNanos).compareTo(maxQueueWaitTime) > 0) {
            meterRegistry.counter("yawl.actor.queue.wait.long",
                    "actor_id", actorId).increment();
        }
    }

    // Memory management methods

    /**
     * Updates memory usage for an actor.
     */
    public void updateMemoryUsage(String actorId, long memoryBytes) {
        Objects.requireNonNull(actorId);

        // Update per-actor memory tracking
        Long oldMemory = actorMemoryMap.get(actorId);
        if (oldMemory != null) {
            totalActorMemory.addAndGet(-oldMemory);
        }

        actorMemoryMap.put(actorId, memoryBytes);
        totalActorMemory.addAndGet(memoryBytes);

        // Update max memory usage
        long currentMax = maxActorMemory.get();
        if (memoryBytes > currentMax) {
            maxActorMemory.set(memoryBytes);
        }

        ActorHealthStatus health = actorHealthMap.get(actorId);
        if (health != null) {
            health.setMemoryUsage(memoryBytes);
        }

        // Check for excessive memory usage
        if (memoryBytes > maxMemoryPerActor) {
            meterRegistry.counter("yawl.actor.memory.high",
                    "actor_id", actorId).increment();
            recordActorError(actorId, "MEMORY_EXCEEDED",
                    "Memory usage exceeds threshold: " + memoryBytes);
        }
    }

    // Health check methods

    /**
     * Performs health checks for all actors.
     */
    public void performHealthChecks() {
        long currentTime = System.currentTimeMillis();
        lastHealthCheckTime.set(currentTime);

        int unhealthyCount = 0;
        int suspendedCount = 0;

        for (ActorHealthStatus health : actorHealthMap.values()) {
            // Check if actor is idle
            if (currentTime - health.getLastActivity() > 300000) { // 5 minutes
                health.setState("idle");
            }

            // Check error rate
            if (health.getErrorRate() > maxErrorRate) {
                health.setHealthStatus(HealthStatus.UNHEALTHY);
                unhealthyCount++;
            }

            // Check queue backlog
            if (health.getQueueDepth() > 500) {
                health.setHealthStatus(HealthStatus.WARNING);
            }

            // Update status counters
            if (health.getHealthStatus() == HealthStatus.UNHEALTHY) {
                unhealthyCount++;
            }
            if (health.getState().equals("suspended")) {
                suspendedCount++;
            }
        }

        unhealthyActorCount.set(unhealthyCount);
        suspendedActorCount.set(suspendedCount);

        meterRegistry.gauge("yawl.actor.health.check.duration",
                currentTime - (lastHealthCheckTime.get() - (currentTime - lastHealthCheckTime.get())));
    }

    /**
     * Gets actor health status.
     */
    public ActorHealthStatus getActorHealth(String actorId) {
        Objects.requireNonNull(actorId);
        return actorHealthMap.get(actorId);
    }

    /**
     * Gets all active actors.
     */
    public java.util.List<ActorHealthStatus> getAllActiveActors() {
        return new java.util.ArrayList<>(actorHealthMap.values());
    }

    /**
     * Gets health summary.
     */
    public ActorHealthSummary getHealthSummary() {
        return new ActorHealthSummary(
                activeActorCount.get(),
                unhealthyActorCount.get(),
                suspendedActorCount.get(),
                totalQueueDepth.get(),
                totalActorMemory.get(),
                calculateSystemHealthScore()
        );
    }

    /**
     * Calculates system health score (0-100).
     */
    private int calculateSystemHealthScore() {
        int activeCount = activeActorCount.get();
        if (activeCount == 0) return 100; // Healthy with no actors

        double unhealthyRatio = (double) unhealthyActorCount.get() / activeCount;
        double suspendedRatio = (double) suspendedActorCount.get() / activeCount;
        double queueLoadRatio = Math.min(1.0, (double) totalQueueDepth.get() / 1000.0);

        // Calculate score with weights
        int score = 100;
        score -= (int) (unhealthyRatio * 40); // 40% weight
        score -= (int) (suspendedRatio * 20); // 20% weight
        score -= (int) (queueLoadRatio * 30); // 30% weight
        score -= (int) (calculateErrorRate() * 10); // 10% weight

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Calculates overall error rate.
     */
    private double calculateErrorRate() {
        long totalMessages = messagesSentCounter.count();
        long failedMessages = messagesFailedCounter.count();
        return totalMessages > 0 ? (double) failedMessages / totalMessages : 0.0;
    }

    // Supporting classes

    /**
     * Health status enumeration.
     */
    public enum HealthStatus {
        HEALTHY,
        WARNING,
        UNHEALTHY,
        UNKNOWN
    }

    /**
     * Actor health status record.
     */
    public static final class ActorHealthStatus {
        private final String actorId;
        private final String actorType;
        private String state;
        private HealthStatus healthStatus;
        private long lastActivity;
        private long queueDepth;
        private long memoryUsage;

        // Message statistics
        private final AtomicInteger messagesSent;
        private final AtomicInteger messagesReceived;
        private final AtomicInteger messagesProcessed;
        private final AtomicInteger messageErrors;

        // Error tracking
        private final ConcurrentHashMap<String, AtomicInteger> errorCounts;

        public ActorHealthStatus(String actorId, String actorType) {
            this.actorId = actorId;
            this.actorType = actorType;
            this.state = "active";
            this.healthStatus = HealthStatus.HEALTHY;
            this.lastActivity = System.currentTimeMillis();
            this.queueDepth = 0;
            this.memoryUsage = 0;
            this.messagesSent = new AtomicInteger(0);
            this.messagesReceived = new AtomicInteger(0);
            this.messagesProcessed = new AtomicInteger(0);
            this.messageErrors = new AtomicInteger(0);
            this.errorCounts = new ConcurrentHashMap<>();
        }

        // Getters and setters
        public String getActorId() { return actorId; }
        public String getActorType() { return actorType; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public HealthStatus getHealthStatus() { return healthStatus; }
        public void setHealthStatus(HealthStatus healthStatus) { this.healthStatus = healthStatus; }
        public long getLastActivity() { return lastActivity; }
        public void setLastActivity(long lastActivity) { this.lastActivity = lastActivity; }
        public int getQueueDepth() { return queueDepth; }
        public void setQueueDepth(int queueDepth) { this.queueDepth = queueDepth; }
        public long getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(long memoryUsage) { this.memoryUsage = memoryUsage; }

        // Message tracking methods
        public void recordMessageSent(String messageType, long size) {
            messagesSent.incrementAndGet();
        }

        public void recordMessageReceived(String messageType, long size) {
            messagesReceived.incrementAndGet();
        }

        public void recordMessageProcessed(String messageType, long processingTime) {
            messagesProcessed.incrementAndGet();
        }

        public void recordMessageFailed(String messageType, String errorType) {
            messageErrors.incrementAndGet();
            errorCounts.computeIfAbsent(errorType, k -> new AtomicInteger(0)).incrementAndGet();
        }

        public void recordError(String errorType, String errorMessage) {
            errorCounts.computeIfAbsent(errorType, k -> new AtomicInteger(0)).incrementAndGet();
        }

        // Public getters for statistics
        public int getMessagesSent() { return messagesSent.get(); }
        public int getMessagesReceived() { return messagesReceived.get(); }
        public int getMessagesProcessed() { return messagesProcessed.get(); }
        public int getMessageErrors() { return messageErrors.get(); }
        public int getErrorCount() { return errorCounts.values().stream().mapToInt(AtomicInteger::get).sum(); }
        public double getErrorRate() {
            int processed = getMessagesProcessed();
            return processed > 0 ? (double) getMessageErrors() / processed : 0.0;
        }
        public java.util.Map<String, Integer> getErrorCounts() {
            java.util.Map<String, Integer> result = new java.util.HashMap<>();
            errorCounts.forEach((k, v) -> result.put(k, v.get()));
            return result;
        }
    }

    /**
     * Actor health summary record.
     */
    public static final class ActorHealthSummary {
        private final int activeActorCount;
        private final int unhealthyActorCount;
        private final int suspendedActorCount;
        private final long totalQueueDepth;
        private final long totalMemoryBytes;
        private final int systemHealthScore;

        public ActorHealthSummary(int activeActorCount, int unhealthyActorCount,
                                int suspendedActorCount, long totalQueueDepth,
                                long totalMemoryBytes, int systemHealthScore) {
            this.activeActorCount = activeActorCount;
            this.unhealthyActorCount = unhealthyActorCount;
            this.suspendedActorCount = suspendedActorCount;
            this.totalQueueDepth = totalQueueDepth;
            this.totalMemoryBytes = totalMemoryBytes;
            this.systemHealthScore = systemHealthScore;
        }

        // Getters
        public int getActiveActorCount() { return activeActorCount; }
        public int getUnhealthyActorCount() { return unhealthyActorCount; }
        public int getSuspendedActorCount() { return suspendedActorCount; }
        public long getTotalQueueDepth() { return totalQueueDepth; }
        public long getTotalMemoryBytes() { return totalMemoryBytes; }
        public int getSystemHealthScore() { return systemHealthScore; }

        @Override
        public String toString() {
            return String.format(
                "ActorHealthSummary{active=%d, unhealthy=%d, suspended=%d, queueDepth=%d, memoryBytes=%d, healthScore=%d}",
                activeActorCount, unhealthyActorCount, suspendedActorCount,
                totalQueueDepth, totalMemoryBytes, systemHealthScore);
        }
    }
}