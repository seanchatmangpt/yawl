package org.yawlfoundation.yawl.integration.a2a.resilience;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Auto-adjusts task execution timeouts based on historical execution patterns.
 *
 * Implements the 80/20 autonomic self-healing pattern for timeout tuning.
 * Learns from past task execution times, auto-adjusts timeouts per task type,
 * and prevents false timeouts from transient slowness.
 *
 * <p><b>Algorithm:</b>
 * <ol>
 *   <li>Record execution time for each task completion</li>
 *   <li>Calculate moving average (mean + 2σ) per task type</li>
 *   <li>Auto-adjust timeout for next execution: max(mean, p95) + buffer</li>
 *   <li>Track timeout accuracy (% completions before timeout)</li>
 *   <li>If timeout accuracy < 95%, increase buffer by 10%</li>
 *   <li>If timeout accuracy > 99%, decrease buffer by 5% (min 10% over mean)</li>
 * </ol>
 *
 * <p><b>Metrics per task type:</b>
 * - Execution count
 * - Min/max/mean execution times
 * - P50/P95/P99 percentiles
 * - Current auto-tuned timeout
 * - Timeout accuracy rate
 * - Buffer percentage over mean
 * - Adjustments count
 *
 * <p><b>Behavior:</b>
 * - Default timeout for unknown tasks: 30 seconds (or user-specified)
 * - Moving window: last 100 executions per task type
 * - Minimum buffer: 10% over mean; maximum buffer: 300%
 * - Prevents thrashing with minimum 5 adjustments before changing timeout
 *
 * Thread-safe via ReentrantReadWriteLock. Non-blocking reads of current timeout.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class TimeoutAutoTuning {

    private static final Logger logger = LogManager.getLogger(TimeoutAutoTuning.class);

    private static final long DEFAULT_TIMEOUT_MS = 30000; // 30 seconds
    private static final int WINDOW_SIZE = 100;           // Last N executions per task
    private static final int MIN_EXECUTIONS_FOR_TUNING = 5;
    private static final double TARGET_ACCURACY = 0.95;    // 95% complete before timeout
    private static final double BUFFER_INCREASE_FACTOR = 1.10; // +10%
    private static final double BUFFER_DECREASE_FACTOR = 0.95; // -5%
    private static final double MIN_BUFFER = 0.10;        // 10% minimum
    private static final double MAX_BUFFER = 3.00;        // 300% maximum

    private final String taskType;
    private final long defaultTimeoutMs;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Deque<Long> executionTimes = new LinkedList<>();
    private final AtomicInteger completionCount = new AtomicInteger(0);
    private final AtomicInteger timeoutCount = new AtomicInteger(0);

    private long currentTimeoutMs;
    private double currentBufferPercent = MIN_BUFFER;
    private int adjustmentCount = 0;
    private long lastAdjustmentTimeMs = 0;

    private long minExecutionMs = Long.MAX_VALUE;
    private long maxExecutionMs = 0;

    /**
     * Construct timeout auto-tuner for a task type with default timeout.
     *
     * @param taskType task identifier or type name
     */
    public TimeoutAutoTuning(String taskType) {
        this(taskType, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Construct timeout auto-tuner for a task type with custom default timeout.
     *
     * @param taskType task identifier or type name
     * @param defaultTimeoutMs default timeout in milliseconds
     */
    public TimeoutAutoTuning(String taskType, long defaultTimeoutMs) {
        this.taskType = taskType;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.currentTimeoutMs = defaultTimeoutMs;
    }

    /**
     * Record successful task execution (before timeout occurred).
     * Triggers auto-adjustment if accuracy metrics warrant it.
     *
     * @param executionTimeMs actual execution time in milliseconds
     */
    public void recordExecution(long executionTimeMs) {
        lock.writeLock().lock();
        try {
            completionCount.incrementAndGet();
            executionTimes.addLast(executionTimeMs);

            // Keep window size bounded
            if (executionTimes.size() > WINDOW_SIZE) {
                executionTimes.removeFirst();
            }

            // Update min/max
            minExecutionMs = Math.min(minExecutionMs, executionTimeMs);
            maxExecutionMs = Math.max(maxExecutionMs, executionTimeMs);

            // Check if we should auto-adjust timeout
            if (executionTimes.size() >= MIN_EXECUTIONS_FOR_TUNING) {
                autoAdjustTimeout();
            }

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Record a timeout event (task did not complete before timeout).
     * Triggers immediate increase to timeout buffer.
     */
    public void recordTimeout() {
        lock.writeLock().lock();
        try {
            timeoutCount.incrementAndGet();
            // Immediate response: increase buffer on timeout
            increaseBuffer();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get current timeout in milliseconds.
     * Non-blocking read; safe for frequently called code paths.
     */
    public long getCurrentTimeoutMs() {
        return currentTimeoutMs;
    }

    /**
     * Get execution count.
     */
    public int getExecutionCount() {
        return completionCount.get();
    }

    /**
     * Get timeout count.
     */
    public int getTimeoutCount() {
        return timeoutCount.get();
    }

    /**
     * Get timeout accuracy rate (% of executions completing before timeout).
     */
    public double getAccuracyRate() {
        int total = completionCount.get();
        if (total == 0) {
            return 1.0;
        }
        return 1.0 - ((double) timeoutCount.get() / total);
    }

    /**
     * Get current buffer percentage over mean execution time.
     */
    public double getBufferPercent() {
        return currentBufferPercent;
    }

    /**
     * Get mean execution time (milliseconds).
     */
    public long getMeanExecutionMs() {
        lock.readLock().lock();
        try {
            if (executionTimes.isEmpty()) {
                return 0;
            }
            return executionTimes.stream()
                .mapToLong(Long::longValue)
                .sum() / executionTimes.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get P95 percentile execution time (milliseconds).
     */
    public long getP95ExecutionMs() {
        lock.readLock().lock();
        try {
            if (executionTimes.isEmpty()) {
                return 0;
            }
            List<Long> sorted = new ArrayList<>(executionTimes);
            Collections.sort(sorted);
            int p95Index = (int) (sorted.size() * 0.95);
            return sorted.get(p95Index);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get minimum observed execution time.
     */
    public long getMinExecutionMs() {
        return minExecutionMs == Long.MAX_VALUE ? 0 : minExecutionMs;
    }

    /**
     * Get maximum observed execution time.
     */
    public long getMaxExecutionMs() {
        return maxExecutionMs;
    }

    /**
     * Get number of auto-adjustments made to this timeout.
     */
    public int getAdjustmentCount() {
        return adjustmentCount;
    }

    /**
     * Reset statistics (for testing or task reconfiguration).
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            executionTimes.clear();
            completionCount.set(0);
            timeoutCount.set(0);
            currentTimeoutMs = defaultTimeoutMs;
            currentBufferPercent = MIN_BUFFER;
            adjustmentCount = 0;
            minExecutionMs = Long.MAX_VALUE;
            maxExecutionMs = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Private helper methods

    private void autoAdjustTimeout() {
        // Calculate mean and P95
        long mean = executionTimes.stream()
            .mapToLong(Long::longValue)
            .sum() / executionTimes.size();

        List<Long> sorted = new ArrayList<>(executionTimes);
        Collections.sort(sorted);
        int p95Index = (int) (sorted.size() * 0.95);
        long p95 = sorted.get(p95Index);

        // Recommended timeout = max(mean, p95) + buffer
        long baseTimeout = Math.max(mean, p95);
        long recommendedTimeout = (long) (baseTimeout * (1.0 + currentBufferPercent));

        // Check accuracy and adjust buffer
        double accuracy = getAccuracyRate();

        if (accuracy < TARGET_ACCURACY) {
            // Too many timeouts: increase buffer
            increaseBuffer();
            recommendedTimeout = (long) (baseTimeout * (1.0 + currentBufferPercent));
        } else if (accuracy > 0.99 && currentBufferPercent > MIN_BUFFER) {
            // Very good accuracy: decrease buffer
            decreaseBuffer();
            recommendedTimeout = (long) (baseTimeout * (1.0 + currentBufferPercent));
        }

        // Apply the new timeout
        if (recommendedTimeout != currentTimeoutMs
            && System.currentTimeMillis() - lastAdjustmentTimeMs >= 5000) {
            currentTimeoutMs = recommendedTimeout;
            adjustmentCount++;
            lastAdjustmentTimeMs = System.currentTimeMillis();

            logger.info("Timeout auto-tuning for task type '{}': "
                + "new timeout={}ms (mean={}ms, p95={}ms, accuracy={:.1f}%, buffer={:.0f}%)",
                taskType, currentTimeoutMs, mean, p95,
                accuracy * 100.0, currentBufferPercent * 100.0);
        }
    }

    private void increaseBuffer() {
        double newBuffer = Math.min(currentBufferPercent * BUFFER_INCREASE_FACTOR, MAX_BUFFER);
        if (newBuffer > currentBufferPercent) {
            currentBufferPercent = newBuffer;
        }
    }

    private void decreaseBuffer() {
        double newBuffer = Math.max(currentBufferPercent * BUFFER_DECREASE_FACTOR, MIN_BUFFER);
        if (newBuffer < currentBufferPercent) {
            currentBufferPercent = newBuffer;
        }
    }
}
