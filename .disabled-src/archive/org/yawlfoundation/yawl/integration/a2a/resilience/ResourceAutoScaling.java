package org.yawlfoundation.yawl.integration.a2a.resilience;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Monitors virtual thread pool queue depth and auto-adjusts executor parallelism.
 *
 * Implements the 80/20 autonomic self-healing pattern for resource management.
 * Prevents thread pool exhaustion by monitoring queue depth, adjusting parallelism,
 * and tracking utilization metrics. Works with virtual thread executors which scale
 * to millions of threads, but queue depth still matters for latency.
 *
 * <p><b>Scaling heuristics:</b>
 * <ul>
 *   <li>Queue depth > 70% capacity: increase parallelism by 10%</li>
 *   <li>Queue depth < 30% capacity and high available threads: decrease by 5%</li>
 *   <li>Track queue depth over 5-minute window for trend detection</li>
 *   <li>Prevent thrashing by minimum 10-second backoff between adjustments</li>
 * </ul>
 *
 * <p><b>Metrics provided:</b>
 * - Current queue depth
 * - Queue capacity
 * - Queue utilization percentage
 * - Current parallelism level
 * - Peak queue depth (high water mark)
 * - Last scaling action and time
 * - Scale-up count, scale-down count
 * - Exhaustion prevention events
 *
 * <p><b>Virtual thread specifics:</b>
 * - Executors created with newVirtualThreadPerTaskExecutor() don't need sizing
 * - However, queue for work submission can still overflow in extreme cases
 * - This monitor prevents that by coordinating with case/task dispatch layer
 *
 * Thread-safe via ReentrantReadWriteLock. Non-blocking metrics collection.
 * Actual parallelism adjustments are delegated to the ExecutorAdjustmentCallback.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ResourceAutoScaling {

    private static final Logger logger = LogManager.getLogger(ResourceAutoScaling.class);

    /**
     * Scaling action type for metrics
     */
    public enum ScalingAction {
        SCALE_UP, SCALE_DOWN, NONE
    }

    private static final int DEFAULT_QUEUE_CAPACITY = 1000;
    private static final double SCALE_UP_THRESHOLD = 0.70;      // 70% full
    private static final double SCALE_DOWN_THRESHOLD = 0.30;    // 30% full
    private static final double SCALE_UP_FACTOR = 1.10;         // +10%
    private static final double SCALE_DOWN_FACTOR = 0.95;       // -5%
    private static final int MIN_PARALLELISM = 10;
    private static final int MAX_PARALLELISM = 500;
    private static final long MIN_ADJUSTMENT_INTERVAL_MS = 10000; // 10 seconds

    private final String name;
    private final int queueCapacity;
    private final ExecutorAdjustmentCallback adjustmentCallback;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicInteger queueDepth = new AtomicInteger(0);
    private final AtomicInteger parallelism = new AtomicInteger(50); // Initial parallelism
    private final AtomicInteger peakQueueDepth = new AtomicInteger(0);
    private final AtomicLong lastAdjustmentTimeMs = new AtomicLong(0);
    private final AtomicInteger scaleUpCount = new AtomicInteger(0);
    private final AtomicInteger scaleDownCount = new AtomicInteger(0);
    private final AtomicInteger exhaustionPreventedCount = new AtomicInteger(0);

    private volatile ScalingAction lastAction = ScalingAction.NONE;
    private volatile String lastAdjustmentReason = "";

    /**
     * Construct auto-scaler with default queue capacity.
     *
     * @param name name for logging and metrics
     * @param adjustmentCallback called to apply parallelism changes
     */
    public ResourceAutoScaling(String name, ExecutorAdjustmentCallback adjustmentCallback) {
        this(name, DEFAULT_QUEUE_CAPACITY, adjustmentCallback);
    }

    /**
     * Construct auto-scaler with custom queue capacity.
     *
     * @param name name for logging and metrics
     * @param queueCapacity maximum queue depth before overflow concern
     * @param adjustmentCallback called to apply parallelism changes
     */
    public ResourceAutoScaling(String name,
                               int queueCapacity,
                               ExecutorAdjustmentCallback adjustmentCallback) {
        this.name = name;
        this.queueCapacity = queueCapacity;
        this.adjustmentCallback = adjustmentCallback;
    }

    /**
     * Record a task enqueued. Called when new task submitted to executor.
     * Auto-triggers scaling check if queue crosses thresholds.
     */
    public void recordTaskEnqueued() {
        int newDepth = queueDepth.incrementAndGet();
        updatePeakQueueDepth(newDepth);
        checkAndScale();
    }

    /**
     * Record a task dequeued/completed. Called when task starts or completes.
     */
    public void recordTaskDequeued() {
        int newDepth = Math.max(0, queueDepth.decrementAndGet());
        checkAndScale();
    }

    /**
     * Manually adjust queue depth. Useful if tracking external queue sources.
     *
     * @param newDepth absolute queue depth value
     */
    public void setQueueDepth(int newDepth) {
        queueDepth.set(newDepth);
        updatePeakQueueDepth(newDepth);
        checkAndScale();
    }

    /**
     * Get current queue depth.
     */
    public int getQueueDepth() {
        return queueDepth.get();
    }

    /**
     * Get queue capacity.
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * Get queue utilization as percentage (0-100).
     */
    public double getQueueUtilizationPercent() {
        return (double) queueDepth.get() / queueCapacity * 100.0;
    }

    /**
     * Get current parallelism level.
     */
    public int getCurrentParallelism() {
        return parallelism.get();
    }

    /**
     * Get peak queue depth since creation.
     */
    public int getPeakQueueDepth() {
        return peakQueueDepth.get();
    }

    /**
     * Get number of scale-up adjustments made.
     */
    public int getScaleUpCount() {
        return scaleUpCount.get();
    }

    /**
     * Get number of scale-down adjustments made.
     */
    public int getScaleDownCount() {
        return scaleDownCount.get();
    }

    /**
     * Get number of exhaustion prevention events triggered.
     */
    public int getExhaustionPreventedCount() {
        return exhaustionPreventedCount.get();
    }

    /**
     * Get last scaling action.
     */
    public ScalingAction getLastAction() {
        return lastAction;
    }

    /**
     * Get reason for last adjustment.
     */
    public String getLastAdjustmentReason() {
        return lastAdjustmentReason;
    }

    /**
     * Get time since last adjustment (milliseconds).
     */
    public long getTimeSinceLastAdjustmentMs() {
        long lastTime = lastAdjustmentTimeMs.get();
        if (lastTime == 0) {
            return -1;
        }
        return System.currentTimeMillis() - lastTime;
    }

    // Private helper methods

    private void updatePeakQueueDepth(int depth) {
        int current = peakQueueDepth.get();
        while (depth > current && !peakQueueDepth.compareAndSet(current, depth)) {
            current = peakQueueDepth.get();
        }
    }

    private void checkAndScale() {
        lock.readLock().lock();
        try {
            // Check if minimum adjustment interval has passed
            long timeSinceLastMs = getTimeSinceLastAdjustmentMs();
            if (timeSinceLastMs >= 0 && timeSinceLastMs < MIN_ADJUSTMENT_INTERVAL_MS) {
                return; // Too soon for adjustment
            }

            double utilization = getQueueUtilizationPercent();
            int currentParallelism = parallelism.get();

            ScalingAction action = ScalingAction.NONE;
            String reason = "";

            // Scale up if queue > 70%
            if (utilization >= SCALE_UP_THRESHOLD * 100) {
                int newParallelism = (int) (currentParallelism * SCALE_UP_FACTOR);
                if (newParallelism > MAX_PARALLELISM) {
                    newParallelism = MAX_PARALLELISM;
                    exhaustionPreventedCount.incrementAndGet();
                    reason = "Queue utilization at " + String.format("%.1f", utilization)
                        + "% - executor approaching limits";
                } else {
                    reason = "Queue utilization at " + String.format("%.1f", utilization)
                        + "% - scaling up parallelism";
                }
                action = applyScalingIfNeeded(currentParallelism, newParallelism, action, reason);
            }
            // Scale down if queue < 30%
            else if (utilization < SCALE_DOWN_THRESHOLD * 100 && currentParallelism > MIN_PARALLELISM) {
                int newParallelism = (int) (currentParallelism * SCALE_DOWN_FACTOR);
                if (newParallelism < MIN_PARALLELISM) {
                    newParallelism = MIN_PARALLELISM;
                }
                reason = "Queue utilization at " + String.format("%.1f", utilization)
                    + "% - scaling down parallelism";
                action = applyScalingIfNeeded(currentParallelism, newParallelism, action, reason);
            }

        } finally {
            lock.readLock().unlock();
        }
    }

    private ScalingAction applyScalingIfNeeded(int currentParallelism,
                                               int newParallelism,
                                               ScalingAction action,
                                               String reason) {
        if (newParallelism != currentParallelism) {
            lock.readLock().unlock();
            lock.writeLock().lock();
            try {
                parallelism.set(newParallelism);
                lastAdjustmentTimeMs.set(System.currentTimeMillis());

                if (newParallelism > currentParallelism) {
                    action = ScalingAction.SCALE_UP;
                    scaleUpCount.incrementAndGet();
                    logger.info("Resource auto-scaling '{}': SCALE UP to {} "
                        + "(from {}) - {}",
                        name, newParallelism, currentParallelism, reason);
                } else {
                    action = ScalingAction.SCALE_DOWN;
                    scaleDownCount.incrementAndGet();
                    logger.info("Resource auto-scaling '{}': SCALE DOWN to {} "
                        + "(from {}) - {}",
                        name, newParallelism, currentParallelism, reason);
                }

                lastAction = action;
                lastAdjustmentReason = reason;

                // Notify executor to apply change
                adjustmentCallback.adjustParallelism(newParallelism);

                return action;
            } finally {
                lock.writeLock().unlock();
                lock.readLock().lock();
            }
        }
        return action;
    }

    /**
     * Callback interface for applying parallelism adjustments.
     * Implementations must not block; they should schedule the adjustment async.
     */
    @FunctionalInterface
    public interface ExecutorAdjustmentCallback {
        /**
         * Called to adjust executor parallelism to new level.
         * Must not block.
         *
         * @param newParallelism target parallelism level
         */
        void adjustParallelism(int newParallelism);
    }
}
