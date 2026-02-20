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

package org.yawlfoundation.yawl.engine;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Resilience4j-based virtual thread pool with bounded concurrency control.
 *
 * <p>This pool uses Resilience4j's Bulkhead pattern for standardized concurrency management:</p>
 * <ul>
 *   <li><b>Semaphore-based concurrency control</b>: Limits concurrent executions via Bulkhead</li>
 *   <li><b>Unified metrics collection</b>: Reports to Micrometer via Bulkhead registry</li>
 *   <li><b>Configurable rejection policy</b>: Customizable behavior when bulkhead is full</li>
 *   <li><b>Virtual thread execution</b>: Leverages Java 21+ virtual threads for I/O efficiency</li>
 * </ul>
 *
 * <h2>Bulkhead Strategy</h2>
 *
 * <p>Instead of thread pool sizing, this uses semaphore-based isolation:</p>
 * <ul>
 *   <li>Max concurrent calls configured via BulkheadConfig.maxConcurrentCalls()</li>
 *   <li>Wait queue with configurable timeout via BulkheadConfig.maxWaitDuration()</li>
 *   <li>Automatic rejection when capacity exceeded</li>
 *   <li>Metrics exposed via Micrometer integration</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * VirtualThreadPool pool = new VirtualThreadPool(
 *     "workflow-executor",
 *     100,                          // max concurrent calls
 *     Duration.ofSeconds(5)         // max wait duration
 * );
 * pool.start();
 *
 * // Submit work (auto-wrapped with bulkhead)
 * Future<String> result = pool.submit(() -> executeWorkflow());
 *
 * // Monitor concurrency via bulkhead metrics
 * VirtualThreadPool.ConcurrencyMetrics metrics = pool.getMetrics();
 * System.out.println("Available permits: " + metrics.availablePermits());
 * System.out.println("Queued tasks: " + metrics.queuedTasks());
 *
 * pool.shutdown();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public class VirtualThreadPool {

    private static final Logger _logger = LogManager.getLogger(VirtualThreadPool.class);

    private final String name;
    private final int maxConcurrentCalls;
    private final Duration maxWaitDuration;

    private volatile ExecutorService executor;
    private volatile Bulkhead bulkhead;
    private volatile TimeLimiter timeLimiter;
    private volatile boolean running = false;

    // Metrics
    private final LongAdder tasksSubmitted = new LongAdder();
    private final LongAdder tasksCompleted = new LongAdder();
    private final LongAdder totalLatencyNanos = new LongAdder();
    private final AtomicLong rejectedCount = new AtomicLong(0);

    /**
     * Create a new virtual thread pool with Resilience4j Bulkhead control.
     *
     * @param name                 pool name for logging and metrics
     * @param maxConcurrentCalls   maximum concurrent executions (semaphore limit)
     * @param maxWaitDuration      maximum time to wait for a slot
     */
    public VirtualThreadPool(String name, int maxConcurrentCalls, Duration maxWaitDuration) {
        this.name = name;
        this.maxConcurrentCalls = maxConcurrentCalls;
        this.maxWaitDuration = maxWaitDuration;
    }

    /**
     * Create a new virtual thread pool with default wait duration.
     * Convenience constructor for backward compatibility.
     *
     * @param name                 pool name for logging
     * @param maxConcurrentCalls   maximum concurrent executions
     * @param maxWaitSeconds       maximum time to wait in seconds
     */
    public VirtualThreadPool(String name, int maxConcurrentCalls, long maxWaitSeconds) {
        this(name, maxConcurrentCalls, Duration.ofSeconds(maxWaitSeconds));
    }

    /**
     * Start the pool with Resilience4j Bulkhead protection.
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("Pool already started");
        }
        running = true;

        // Create virtual thread executor
        executor = Executors.newVirtualThreadPerTaskExecutor();

        // Create bulkhead with semaphore-based concurrency control
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(maxConcurrentCalls)
            .maxWaitDuration(maxWaitDuration)
            .build();

        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.ofDefaults();
        this.bulkhead = bulkheadRegistry.bulkhead("vthread-pool-" + name, bulkheadConfig);

        // Create time limiter for operation timeouts
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
            .timeoutDuration(maxWaitDuration.multipliedBy(2))
            .cancelRunningFuture(true)
            .build();

        this.timeLimiter = io.github.resilience4j.timelimiter.TimeLimiterRegistry
            .ofDefaults()
            .timeLimiter("vthread-pool-" + name, timeLimiterConfig);

        // Register bulkhead event listeners
        bulkhead.getEventPublisher()
            .onSuccess(event -> _logger.debug("Task completed: {}", event.getBulkheadName()))
            .onError(event -> _logger.warn("Task failed in bulkhead: {}", event.getThrowable()))
            .onCallRejected(event -> {
                rejectedCount.increment();
                _logger.warn("Call rejected by bulkhead: {}", event.getBulkheadName());
            });

        _logger.info("VirtualThreadPool[{}] started with Bulkhead: maxConcurrent={}, maxWait={}",
                     name, maxConcurrentCalls, maxWaitDuration);
    }

    /**
     * Submit a task to the pool with Bulkhead protection.
     *
     * @param <T>  return type
     * @param task the task to execute
     * @return future with result
     * @throws Exception if bulkhead is full
     */
    public <T> Future<T> submit(Callable<T> task) throws Exception {
        if (!running) {
            throw new IllegalStateException("Pool not running");
        }

        if (bulkhead == null) {
            throw new IllegalStateException("Bulkhead not initialized");
        }

        tasksSubmitted.increment();

        // Wrap task with bulkhead and latency tracking
        Callable<T> wrapped = Bulkhead.decorateCallable(bulkhead, () -> {
            long startNanos = System.nanoTime();
            try {
                return task.call();
            } finally {
                long latencyNanos = System.nanoTime() - startNanos;
                totalLatencyNanos.add(latencyNanos);
                tasksCompleted.increment();
            }
        });

        return executor.submit(wrapped);
    }

    /**
     * Submit a runnable to the pool with Bulkhead protection.
     *
     * @param task the task to execute
     * @return future for completion
     * @throws Exception if bulkhead is full
     */
    public Future<?> submit(Runnable task) throws Exception {
        if (!running) {
            throw new IllegalStateException("Pool not running");
        }

        if (bulkhead == null) {
            throw new IllegalStateException("Bulkhead not initialized");
        }

        tasksSubmitted.increment();

        Runnable wrapped = Bulkhead.decorateRunnable(bulkhead, () -> {
            long startNanos = System.nanoTime();
            try {
                task.run();
            } finally {
                long latencyNanos = System.nanoTime() - startNanos;
                totalLatencyNanos.add(latencyNanos);
                tasksCompleted.increment();
            }
        });

        return executor.submit(wrapped);
    }


    /**
     * Get current concurrency metrics from the bulkhead.
     *
     * @return snapshot of concurrency and latency metrics
     */
    public ConcurrencyMetrics getMetrics() {
        long completedTasks = tasksCompleted.sum();
        double avgLatencyMs = completedTasks > 0
            ? (double) totalLatencyNanos.sum() / completedTasks / 1_000_000
            : 0;

        int availablePermits = bulkhead != null ? bulkhead.getMetrics().getAvailableConcurrentCalls() : 0;
        int queuedTasks = bulkhead != null ? bulkhead.getMetrics().getWaitingThreadsCount() : 0;

        return new ConcurrencyMetrics(
            tasksSubmitted.sum(),
            tasksCompleted.sum(),
            rejectedCount.get(),
            avgLatencyMs,
            maxConcurrentCalls,
            availablePermits,
            queuedTasks
        );
    }

    /**
     * Get current cost metrics (for backward compatibility).
     *
     * @return snapshot of cost-related metrics
     * @deprecated Use {@link #getMetrics()} instead
     */
    @Deprecated(since = "6.0", forRemoval = true)
    public CostMetrics getCostMetrics() {
        var concurrency = getMetrics();
        return new CostMetrics(
            concurrency.inUseCalls(),
            concurrency.maxConcurrentCalls(),
            100.0 * (concurrency.maxConcurrentCalls() - concurrency.availablePermits()) / concurrency.maxConcurrentCalls(),
            0,
            concurrency.avgLatencyMs(),
            (double) (concurrency.maxConcurrentCalls() - concurrency.availablePermits()) / concurrency.maxConcurrentCalls(),
            concurrency.tasksSubmitted(),
            concurrency.tasksCompleted()
        );
    }

    /**
     * Gracefully shutdown the pool and bulkhead.
     */
    public void shutdown() {
        if (!running) {
            return;
        }
        running = false;

        // Shutdown executor
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        ConcurrencyMetrics finalMetrics = getMetrics();
        _logger.info("VirtualThreadPool[{}] shutdown: submitted={}, completed={}, rejected={}, avgLatency={:.2f}ms",
                     name, finalMetrics.tasksSubmitted(), finalMetrics.tasksCompleted(),
                     finalMetrics.rejectedCount(), finalMetrics.avgLatencyMs());
    }

    /**
     * Concurrency metrics from Bulkhead and execution tracking.
     *
     * @param tasksSubmitted       total tasks submitted to the pool
     * @param tasksCompleted       total tasks successfully completed
     * @param rejectedCount        tasks rejected by bulkhead
     * @param avgLatencyMs         average task execution latency in milliseconds
     * @param maxConcurrentCalls   maximum configured concurrent calls
     * @param availablePermits     available semaphore permits (available slots)
     * @param queuedTasks          tasks waiting in bulkhead queue
     */
    public record ConcurrencyMetrics(
        long tasksSubmitted,
        long tasksCompleted,
        long rejectedCount,
        double avgLatencyMs,
        int maxConcurrentCalls,
        int availablePermits,
        int queuedTasks
    ) {
        /**
         * Number of calls currently in use (borrowed permits).
         *
         * @return permits in use
         */
        public int inUseCalls() {
            return maxConcurrentCalls - availablePermits;
        }

        /**
         * Utilization percentage of the bulkhead.
         *
         * @return percentage (0-100)
         */
        public double utilizationPercent() {
            return 100.0 * inUseCalls() / maxConcurrentCalls;
        }

        /**
         * Success rate percentage.
         *
         * @return percentage (0-100)
         */
        public double successRatePercent() {
            long total = tasksSubmitted;
            return total > 0 ? 100.0 * (tasksCompleted) / total : 100.0;
        }
    }

    /**
     * Legacy cost metrics for backward compatibility.
     *
     * @param estimatedCarrierThreads current estimated OS threads
     * @param maxCarrierThreads       maximum allowed OS threads
     * @param carrierUtilizationPercent percentage of max carriers currently used
     * @param throughputPerSecond     tasks completed per second
     * @param avgLatencyMs            average task latency in milliseconds
     * @param costFactor              normalized cost (0-1)
     * @param tasksSubmitted          total tasks submitted
     * @param tasksCompleted          total tasks completed
     */
    @Deprecated(since = "6.0", forRemoval = true)
    public record CostMetrics(
        long estimatedCarrierThreads,
        int maxCarrierThreads,
        double carrierUtilizationPercent,
        long throughputPerSecond,
        double avgLatencyMs,
        double costFactor,
        long tasksSubmitted,
        long tasksCompleted
    ) {
        /**
         * Calculate estimated cost savings compared to maximum.
         *
         * @return percentage saved (0-100%)
         */
        public double costSavingsPercent() {
            return (1.0 - costFactor) * 100;
        }

        /**
         * Calculate estimated OS threads freed up (available for other processes).
         *
         * @return number of threads not in use
         */
        public long availableCarrierThreads() {
            return maxCarrierThreads - estimatedCarrierThreads;
        }
    }
}
