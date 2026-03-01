package org.yawlfoundation.yawl.integration.actor;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Metrics tracking for actor validation
 *
 * Records validation history, memory usage, performance metrics,
 * and detection results for individual actors.
 *
 * @since 6.0.0
 */
public class ActorValidationMetrics {

    private final String caseId;
    private final AtomicLong validationCount = new AtomicLong(0);
    private final AtomicLong memoryLeakCount = new AtomicLong(0);
    private final AtomicLong deadlockCount = new AtomicLong(0);

    private final AtomicReference<Double> currentMemoryUsage = new AtomicReference<>(0.0);
    private final AtomicReference<Duration> lastProcessingTime = new AtomicReference<>(Duration.ZERO);
    private final AtomicReference<Duration> totalProcessingTime = new AtomicReference<>(Duration.ZERO);

    private final AtomicLong slowProcessingCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    public ActorValidationMetrics(String caseId) {
        this.caseId = caseId;
    }

    /**
     * Record a validation run
     */
    public void recordValidation(Duration processingTime, boolean memoryLeakDetected, boolean deadlockDetected) {
        long count = validationCount.incrementAndGet();

        if (memoryLeakDetected) {
            memoryLeakCount.incrementAndGet();
        }

        if (deadlockDetected) {
            deadlockCount.incrementAndGet();
        }

        updateProcessingTime(processingTime);
    }

    /**
     * Update memory usage
     */
    public void updateMemoryUsage(double memoryUsageMB) {
        currentMemoryUsage.set(memoryUsageMB);
    }

    /**
     * Update processing time
     */
    public void updateProcessingTime(Duration processingTime) {
        lastProcessingTime.set(processingTime);
        totalProcessingTime.updateAndGet(total -> total.plus(processingTime));

        if (processingTime.toMillis() > 5000) {
            slowProcessingCount.incrementAndGet();
        }
    }

    /**
     * Get validation count
     */
    public long getValidationCount() {
        return validationCount.get();
    }

    /**
     * Get memory leak count
     */
    public long getMemoryLeakCount() {
        return memoryLeakCount.get();
    }

    /**
     * Get deadlock count
     */
    public long getDeadlockCount() {
        return deadlockCount.get();
    }

    /**
     * Get current memory usage
     */
    public double getCurrentMemoryUsage() {
        return currentMemoryUsage.get();
    }

    /**
     * Get last processing time
     */
    public Duration getLastProcessingTime() {
        return lastProcessingTime.get();
    }

    /**
     * Get average processing time
     */
    public Duration getAverageProcessingTime() {
        long count = validationCount.get();
        if (count == 0) return Duration.ZERO;

        Duration total = totalProcessingTime.get();
        return Duration.ofMillis(total.toMillis() / count);
    }

    /**
     * Get total processing time
     */
    public Duration getTotalProcessingTime() {
        return totalProcessingTime.get();
    }

    /**
     * Get slow processing count
     */
    public long getSlowProcessingCount() {
        return slowProcessingCount.get();
    }

    /**
     * Get error count
     */
    public long getErrorCount() {
        return errorCount.get();
    }

    /**
     * Increment error count
     */
    public void incrementErrorCount() {
        errorCount.incrementAndGet();
    }

    /**
     * Get last memory usage (for leak detection)
     */
    public double getLastMemoryUsage() {
        return currentMemoryUsage.get();
    }

    /**
     * Get case ID
     */
    public String getCaseId() {
        return caseId;
    }

    /**
     * Get summary statistics
     */
    public String getSummary() {
        return String.format(
            "Actor[%s] - Validations: %d, MemoryLeaks: %d, Deadlocks: %d, " +
            "AvgTime: %.2fms, SlowOps: %d, Errors: %d",
            caseId,
            getValidationCount(),
            getMemoryLeakCount(),
            getDeadlockCount(),
            getAverageProcessingTime().toMillis(),
            getSlowProcessingCount(),
            getErrorCount()
        );
    }

    /**
     * Get health status
     */
    public String getHealthStatus() {
        if (getMemoryLeakCount() > 0 || getDeadlockCount() > 0) {
            return "CRITICAL";
        } else if (getSlowProcessingCount() > 5 || getErrorCount() > 3) {
            return "WARNING";
        } else {
            return "HEALTHY";
        }
    }

    /**
     * Reset metrics
     */
    public void reset() {
        validationCount.set(0);
        memoryLeakCount.set(0);
        deadlockCount.set(0);
        currentMemoryUsage.set(0.0);
        lastProcessingTime.set(Duration.ZERO);
        totalProcessingTime.set(Duration.ZERO);
        slowProcessingCount.set(0);
        errorCount.set(0);
    }
}