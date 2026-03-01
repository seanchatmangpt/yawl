/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.validation;

import java.lang.ScopedValue;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Virtual Thread Lifecycle Manager
 *
 * Validates virtual thread lifecycle management for the YAWL actor model,
 * ensuring proper creation, execution, termination, and resource cleanup.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Lifecycle state tracking (NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED)</li>
 *   <li>Resource leak detection and prevention</li>
 *   <li>Lifecycle event correlation</li>
 *   <li>Virtual thread pool management</li>
 *   <li>Graceful shutdown coordination</li>
 *   <li>Memory usage monitoring</li>
 *   <li>Performance impact analysis</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public class VirtualThreadLifecycleManager {

    private static final Logger _logger = LogManager.getLogger(VirtualThreadLifecycleManager.class);

    // Configuration
    private static final long LIFECYCLE_TEST_DURATION_SECONDS = 120;
    private static final int MONITORING_INTERVAL_MS = 100;
    private static final int MAX_TRACKED_THREADS = 10000;
    private static final long THREAD_TIMEOUT_MS = 30000; // 30 seconds timeout

    // Lifecycle states
    private static final Thread.State[] ALL_STATES = Thread.State.values();

    // Tracking metrics
    private final Map<String, ThreadLifecycle> trackedThreads = new ConcurrentHashMap<>();
    private final Map<Thread.State, AtomicLong> stateCounts = new EnumMap<>(Thread.State.class);
    private final AtomicLong totalThreadsCreated = new AtomicLong(0);
    private final AtomicLong totalThreadsTerminated = new AtomicLong(0);
    private final AtomicLong totalLeaksDetected = new AtomicLong(0);
    private final AtomicLong resourceLeaks = new AtomicLong(0);
    private final AtomicLong memoryLeaks = new AtomicLong(0);
    private final AtomicLong contextLeakage = new AtomicLong(0);

    // Event tracking
    private final List<LifecycleEvent> lifecycleEvents = new ArrayList<>();
    private final BlockingQueue<LifecycleEvent> eventQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean monitoringActive = new AtomicBoolean(false);

    // Executors
    private ExecutorService lifecycleExecutor;
    private ScheduledExecutorService monitoringExecutor;
    private ExecutorService cleanupExecutor;

    // Context management
    private static final ScopedValue<String> THREAD_ID = ScopedValue.newInstance();
    private static final ScopedValue<Instant> CREATED_AT = ScopedValue.newInstance();
    private static final ScopedValue<String> THREAD_GROUP = ScopedValue.newInstance();

    public VirtualThreadLifecycleManager() {
        initializeStateCounts();
    }

    /**
     * Initialize state counters.
     */
    private void initializeStateCounts() {
        for (Thread.State state : ALL_STATES) {
            stateCounts.put(state, new AtomicLong(0));
        }
    }

    /**
     * Start comprehensive lifecycle management validation.
     */
    public void startLifecycleValidation() {
        if (monitoringActive.get()) {
            throw new IllegalStateException("Lifecycle validation already active");
        }

        monitoringActive.set(true);
        resetMetrics();

        _logger.info("Starting virtual thread lifecycle management validation");

        // Start lifecycle monitoring
        startLifecycleMonitoring();

        // Start event processing
        startEventProcessing();

        // Start lifecycle stress tests
        startLifecycleStressTests();

        // Start resource leak detection
        startLeakDetection();

        // Start validation scenarios
        startValidationScenarios();
    }

    /**
     * Stop lifecycle validation and generate report.
     */
    public LifecycleManagementReport stopLifecycleValidation() {
        if (!monitoringActive.get()) {
            throw new IllegalStateException("No lifecycle validation active");
        }

        monitoringActive.set(false);

        // Shutdown executors
        shutdownExecutors();

        _logger.info("Virtual thread lifecycle management validation complete");

        return generateLifecycleReport();
    }

    /**
     * Start lifecycle monitoring.
     */
    private void startLifecycleMonitoring() {
        monitoringExecutor = Executors.newSingleThreadScheduledExecutor();
        monitoringExecutor.scheduleAtFixedRate(
            this::monitorLifecycleStates,
            0, MONITORING_INTERVAL_MS, TimeUnit.MILLISECONDS
        );
    }

    /**
     * Start event processing.
     */
    private void startEventProcessing() {
        lifecycleExecutor = Executors.newSingleThreadExecutor();
        lifecycleExecutor.submit(this::processLifecycleEvents);
    }

    /**
     * Start lifecycle stress tests.
     */
    private void startLifecycleStressTests() {
        // High churn test
        new Thread(() -> {
            while (monitoringActive.get()) {
                highChurnTest();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();

        // Rapid start/stop test
        new Thread(() -> {
            while (monitoringActive.get()) {
                rapidLifecycleTest();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();

        // Long-running threads test
        new Thread(() -> {
            while (monitoringActive.get()) {
                longRunningThreadTest();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    /**
     * Start leak detection.
     */
    private void startLeakDetection() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        cleanupExecutor.scheduleAtFixedRate(
            this::detectResourceLeaks,
            5000, 5000, TimeUnit.MILLISECONDS
        );

        // Context leakage detection
        cleanupExecutor.scheduleAtFixedRate(
            this::detectContextLeakage,
            10000, 10000, TimeUnit.MILLISECONDS
        );
    }

    /**
     * Start validation scenarios.
     */
    private void startValidationScenarios() {
        // Normal lifecycle scenario
        new Thread(() -> {
            while (monitoringActive.get()) {
                normalLifecycleScenario();
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();

        // Error handling scenario
        new Thread(() -> {
            while (monitoringActive.get()) {
                errorHandlingScenario();
                try {
                    Thread.sleep(4000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();

        // Memory pressure scenario
        new Thread(() -> {
            while (monitoringActive.get()) {
                memoryPressureScenario();
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    /**
     * Monitor lifecycle states of tracked threads.
     */
    private void monitorLifecycleStates() {
        List<String> threadsToClean = new ArrayList<>();

        trackedThreads.forEach((threadId, lifecycle) -> {
            Thread.State currentState = lifecycle.currentState();
            Thread.State previousState = lifecycle.previousState();

            // Check if thread is still alive and not timed out
            if (!lifecycle.thread().isAlive()) {
                threadsToClean.add(threadId);
                totalThreadsTerminated.incrementAndGet();
                lifecycleEvents.add(new LifecycleEvent(
                    Instant.now(),
                    "TERMINATED",
                    threadId,
                    null,
                    lifecycle.resourceUsage()
                ));
            } else if (currentState != previousState) {
                // State transition detected
                updateStateCounts(previousState, currentState);
                lifecycleEvents.add(new LifecycleEvent(
                    Instant.now(),
                    "STATE_CHANGE",
                    threadId,
                    previousState + "->" + currentState,
                    lifecycle.resourceUsage()
                ));
            }

            // Update timeout
            if (Duration.between(lifecycle.createdAt(), Instant.now()).toMillis() > THREAD_TIMEOUT_MS) {
                threadsToClean.add(threadId);
                totalLeaksDetected.incrementAndGet();
            }
        });

        // Clean up timed out threads
        for (String threadId : threadsToClean) {
            trackedThreads.remove(threadId);
        }
    }

    /**
     * Process lifecycle events.
     */
    private void processLifecycleEvents() {
        while (monitoringActive.get() || !eventQueue.isEmpty()) {
            try {
                LifecycleEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    processEvent(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Process a single lifecycle event.
     */
    private void processEvent(LifecycleEvent event) {
        switch (event.type()) {
            case "CREATED":
                handleThreadCreation(event);
                break;
            case "TERMINATED":
                handleThreadTermination(event);
                break;
            case "STATE_CHANGE":
                handleStateChange(event);
                break;
            case "RESOURCE_LEAK":
                handleResourceLeak(event);
                break;
            case "CONTEXT_LEAK":
                handleContextLeak(event);
                break;
        }
    }

    /**
     * Handle thread creation event.
     */
    private void handleThreadCreation(LifecycleEvent event) {
        String threadId = event.threadId();
        Thread thread = event.thread();

        ThreadLifecycle lifecycle = new ThreadLifecycle(
            threadId,
            thread,
            thread.getState(),
            Instant.now(),
            new ConcurrentHashMap<>()
        );

        trackedThreads.put(threadId, lifecycle);
        totalThreadsCreated.incrementAndGet();

        _logger.debug("Thread created: {} in state {}", threadId, thread.getState());
    }

    /**
     * Handle thread termination event.
     */
    private void handleThreadTermination(LifecycleEvent event) {
        String threadId = event.threadId();
        ThreadLifecycle lifecycle = trackedThreads.remove(threadId);

        if (lifecycle != null) {
            _logger.debug("Thread terminated: {}", threadId);
        }
    }

    /**
     * Handle state change event.
     */
    private void handleStateChange(LifecycleEvent event) {
        String threadId = event.threadId();
        ThreadLifecycle lifecycle = trackedThreads.get(threadId);

        if (lifecycle != null) {
            lifecycle.transitionTo(Thread.State.valueOf(event.details().split("->")[1]));
        }
    }

    /**
     * Handle resource leak event.
     */
    private void handleResourceLeak(LifecycleEvent event) {
        resourceLeaks.incrementAndGet();
        _logger.warn("Resource leak detected for thread {}: {}", event.threadId(), event.details());
    }

    /**
     * Handle context leak event.
     */
    private void handleContextLeak(LifecycleEvent event) {
        contextLeakage.incrementAndGet();
        _logger.warn("Context leak detected for thread {}: {}", event.threadId(), event.details());
    }

    /**
     * Update state counts.
     */
    private void updateStateCounts(Thread.State previous, Thread.State current) {
        if (previous != null) {
            stateCounts.get(previous).decrementAndGet();
        }
        if (current != null) {
            stateCounts.get(current).incrementAndGet();
        }
    }

    /**
     * High churn test - create/destroy many threads rapidly.
     */
    private void highChurnTest() {
        String batchId = "churn-" + System.currentTimeMillis();
        int batchSize = 100;

        for (int i = 0; i < batchSize; i++) {
            final int threadId = i;
            String threadName = "vthread-churn-" + batchId + "-" + threadId;

            Thread thread = Thread.ofVirtual()
                .name(threadName)
                .start(() -> {
                    try {
                        String currentThreadId = ScopedValue.where(THREAD_ID, threadName)
                            .where(CREATED_AT, Instant.now())
                            .where(THREAD_GROUP, "churn-test")
                            .call(() -> {
                                // Short-lived work
                                Thread.sleep(ThreadLocalRandom.current().nextInt(10, 50));
                                return threadName;
                            });
                    } catch (Exception e) {
                        _logger.debug("Churn thread failed: {}", e.getMessage());
                    } finally {
                        lifecycleEvents.add(new LifecycleEvent(
                            Instant.now(),
                            "TERMINATED",
                            threadName,
                            null,
                            new ConcurrentHashMap<>()
                        ));
                    }
                });

            // Track the thread
            lifecycleEvents.add(new LifecycleEvent(
                Instant.now(),
                "CREATED",
                threadName,
                null,
                new ConcurrentHashMap<>()
            ));
        }
    }

    /**
     * Rapid lifecycle test - quick start/stop cycles.
     */
    private void rapidLifecycleTest() {
        for (int i = 0; i < 10; i++) {
            final String threadName = "rapid-test-" + System.currentTimeMillis() + "-" + i;

            Thread thread = Thread.ofVirtual()
                .name(threadName)
                .start(() -> {
                    try {
                        // Very short execution
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

            lifecycleEvents.add(new LifecycleEvent(
                Instant.now(),
                "CREATED",
                threadName,
                null,
                new ConcurrentHashMap<>()
            ));
        }
    }

    /**
     * Long-running thread test.
     */
    private void longRunningThreadTest() {
        String threadName = "long-running-" + System.currentTimeMillis();

        Thread thread = Thread.ofVirtual()
            .name(threadName)
            .start(() -> {
                try {
                    String currentThreadId = ScopedValue.where(THREAD_ID, threadName)
                        .where(CREATED_AT, Instant.now())
                        .where(THREAD_GROUP, "long-running-test")
                        .call(() -> {
                            // Long-running work with periodic state changes
                            for (int i = 0; i < 10; i++) {
                                Thread.sleep(1000);
                                // Simulate some computation
                                computeSomething();
                            }
                            return threadName;
                        });
                } catch (Exception e) {
                    _logger.debug("Long-running thread failed: {}", e.getMessage());
                }
            });

        lifecycleEvents.add(new LifecycleEvent(
            Instant.now(),
            "CREATED",
            threadName,
            null,
            new ConcurrentHashMap<>()
        ));
    }

    /**
     * Normal lifecycle scenario.
     */
    private void normalLifecycleScenario() {
        String threadName = "normal-" + System.currentTimeMillis();

        Thread thread = Thread.ofVirtual()
            .name(threadName)
            .start(() -> {
                try {
                    ScopedValue.where(THREAD_ID, threadName)
                        .where(CREATED_AT, Instant.now())
                        .where(THREAD_GROUP, "normal-test")
                        .call(() -> {
                            // Normal workflow
                            initialize();
                            process();
                            cleanup();
                            return threadName;
                        });
                } catch (Exception e) {
                    _logger.debug("Normal scenario failed: {}", e.getMessage());
                }
            });

        lifecycleEvents.add(new LifecycleEvent(
            Instant.now(),
            "CREATED",
            threadName,
            null,
            new ConcurrentHashMap<>()
        ));
    }

    /**
     * Error handling scenario.
     */
    private void errorHandlingScenario() {
        String threadName = "error-handling-" + System.currentTimeMillis();

        Thread thread = Thread.ofVirtual()
            .name(threadName)
            .start(() -> {
                try {
                    ScopedValue.where(THREAD_ID, threadName)
                        .where(CREATED_AT, Instant.now())
                        .where(THREAD_GROUP, "error-test")
                        .call(() -> {
                            try {
                                // Work that might fail
                                riskyOperation();
                            } catch (Exception e) {
                                // Handle error gracefully
                                handleError(e);
                            }
                            return threadName;
                        });
                } catch (Exception e) {
                    _logger.debug("Error handling scenario failed: {}", e.getMessage());
                }
            });

        lifecycleEvents.add(new LifecycleEvent(
            Instant.now(),
            "CREATED",
            threadName,
            null,
            new ConcurrentHashMap<>()
        ));
    }

    /**
     * Memory pressure scenario.
     */
    private void memoryPressureScenario() {
        String threadName = "memory-pressure-" + System.currentTimeMillis();

        Thread thread = Thread.ofVirtual()
            .name(threadName)
            .start(() -> {
                try {
                    ScopedValue.where(THREAD_ID, threadName)
                        .where(CREATED_AT, Instant.now())
                        .where(THREAD_GROUP, "memory-test")
                        .call(() -> {
                            // Memory-intensive work
                            List<byte[]> memoryChunks = new ArrayList<>();
                            for (int i = 0; i < 1000; i++) {
                                memoryChunks.add(new byte[1024]); // 1KB chunks
                                if (i % 100 == 0) {
                                    System.gc(); // Suggest GC periodically
                                }
                            }
                            return threadName;
                        });
                } catch (Exception e) {
                    _logger.debug("Memory pressure scenario failed: {}", e.getMessage());
                }
            });

        lifecycleEvents.add(new LifecycleEvent(
            Instant.now(),
            "CREATED",
            threadName,
            null,
            new ConcurrentHashMap<>()
        ));
    }

    /**
     * Detect resource leaks.
     */
    private void detectResourceLeaks() {
        // Check for threads that have been running too long
        trackedThreads.forEach((threadId, lifecycle) -> {
            Duration age = Duration.between(lifecycle.createdAt(), Instant.now());
            if (age.toMillis() > THREAD_TIMEOUT_MS) {
                lifecycleEvents.add(new LifecycleEvent(
                    Instant.now(),
                    "RESOURCE_LEAK",
                    threadId,
                    "Thread running for " + age.toSeconds() + " seconds",
                    lifecycle.resourceUsage()
                ));
                totalLeaksDetected.incrementAndGet();
            }
        });
    }

    /**
     * Detect context leakage.
     */
    private void detectContextLeakage() {
        // Check for ScopedValue bindings that persist too long
        // This is a simplified detection - in practice, more sophisticated tracking would be needed
        if (trackedThreads.size() > MAX_TRACKED_THREADS) {
            contextLeakage.incrementAndGet();
            lifecycleEvents.add(new LifecycleEvent(
                Instant.now(),
                "CONTEXT_LEAK",
                "system",
                "Too many tracked threads: " + trackedThreads.size(),
                new ConcurrentHashMap<>()
            ));
        }
    }

    /**
     * Helper methods for test scenarios.
     */
    private void initialize() {
        Thread.sleep(100);
    }

    private void process() {
        for (int i = 0; i < 5; i++) {
            computeSomething();
            Thread.sleep(200);
        }
    }

    private void cleanup() {
        Thread.sleep(50);
    }

    private void computeSomething() {
        int sum = 0;
        for (int i = 0; i < 1000; i++) {
            sum += i;
        }
    }

    private void riskyOperation() {
        if (ThreadLocalRandom.current().nextBoolean()) {
            throw new RuntimeException("Simulated error");
        }
        computeSomething();
    }

    private void handleError(Exception e) {
        _logger.debug("Error handled: {}", e.getMessage());
    }

    /**
     * Shutdown executors gracefully.
     */
    private void shutdownExecutors() {
        // Shutdown monitoring executor
        if (monitoringExecutor != null) {
            monitoringExecutor.shutdown();
            try {
                if (!monitoringExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    monitoringExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                monitoringExecutor.shutdownNow();
            }
        }

        // Shutdown lifecycle executor
        if (lifecycleExecutor != null) {
            lifecycleExecutor.shutdown();
            try {
                if (!lifecycleExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    lifecycleExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lifecycleExecutor.shutdownNow();
            }
        }

        // Shutdown cleanup executor
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cleanupExecutor.shutdownNow();
            }
        }
    }

    /**
     * Generate comprehensive lifecycle management report.
     */
    private LifecycleManagementReport generateLifecycleReport() {
        // Calculate statistics
        Map<Thread.State, Long> finalStateCounts = new EnumMap<>();
        stateCounts.forEach((state, count) -> finalStateCounts.put(state, count.get()));

        // Analyze patterns
        LifecyclePatterns patterns = analyzeLifecyclePatterns();

        // Generate recommendations
        List<String> recommendations = generateLifecycleRecommendations(patterns);

        return new LifecycleManagementReport(
            Instant.now(),
            trackedThreads.size(),
            totalThreadsCreated.get(),
            totalThreadsTerminated.get(),
            totalLeaksDetected.get(),
            resourceLeaks.get(),
            memoryLeaks.get(),
            contextLeakage.get(),
            finalStateCounts,
            lifecycleEvents.size(),
            patterns,
            recommendations
        );
    }

    /**
     * Analyze lifecycle patterns.
     */
    private LifecyclePatterns analyzeLifecyclePatterns() {
        Map<Thread.State, Long> stateCountsMap = new EnumMap<>();
        stateCounts.forEach((state, count) -> stateCountsMap.put(state, count.get()));

        int totalThreads = trackedThreads.size();
        double blockedPercentage = totalThreads > 0 ?
            (double) stateCountsMap.getOrDefault(Thread.State.BLOCKED, 0L) / totalThreads * 100 : 0;
        double waitingPercentage = totalThreads > 0 ?
            (double) stateCountsMap.getOrDefault(Thread.State.WAITING, 0L) / totalThreads * 100 : 0;

        return new LifecyclePatterns(
            blockedPercentage,
            waitingPercentage,
            calculateAverageThreadAge(),
            detectAnomalies()
        );
    }

    /**
     * Calculate average thread age.
     */
    private double calculateAverageThreadAge() {
        if (trackedThreads.isEmpty()) return 0;

        return trackedThreads.values().stream()
            .mapToDouble(lifecycle -> Duration.between(lifecycle.createdAt(), Instant.now()).toSeconds())
            .average()
            .orElse(0);
    }

    /**
     * Detect lifecycle anomalies.
     */
    private List<String> detectAnomalies() {
        List<String> anomalies = new ArrayList<>();

        // Check for too many blocked threads
        long blockedCount = stateCounts.get(Thread.State.BLOCKED).get();
        if (blockedCount > trackedThreads.size() * 0.1) {
            anomalies.add("High thread blocking detected: " + blockedCount + " blocked threads");
        }

        // Check for thread leaks
        if (trackedThreads.size() > MAX_TRACKED_THREADS * 0.9) {
            anomalies.add("Near thread limit: " + trackedThreads.size() + " threads tracked");
        }

        // Check for unusual state distributions
        long runningCount = stateCounts.get(Thread.State.RUNNABLE).get();
        if (runningCount > trackedThreads.size() * 0.8) {
            anomalies.add("High runnable thread count may indicate CPU contention");
        }

        return anomalies;
    }

    /**
     * Generate lifecycle recommendations.
     */
    private List<String> generateLifecycleRecommendations(LifecyclePatterns patterns) {
        List<String> recommendations = new ArrayList<>();

        if (patterns.blockedPercentage() > 10) {
            recommendations.add("Reduce blocking operations to improve throughput");
        }

        if (patterns.waitingPercentage() > 20) {
            recommendations.add("Consider using non-blocking I/O operations");
        }

        if (patterns.averageThreadAgeSeconds() > 60) {
            recommendations.add("Some threads are running too long - investigate potential leaks");
        }

        if (!patterns.anomalies().isEmpty()) {
            recommendations.add("Address detected lifecycle anomalies to improve stability");
        }

        return recommendations;
    }

    /**
     * Reset all metrics.
     */
    private void resetMetrics() {
        trackedThreads.clear();
        stateCounts.forEach((state, count) -> count.set(0));
        lifecycleEvents.clear();
        eventQueue.clear();
        totalThreadsCreated.set(0);
        totalThreadsTerminated.set(0);
        totalLeaksDetected.set(0);
        resourceLeaks.set(0);
        memoryLeaks.set(0);
        contextLeakage.set(0);
    }

    // Record classes
    public record LifecycleEvent(
        Instant timestamp,
        String type,
        String threadId,
        String details,
        Map<String, Object> metadata
    ) {}

    public record ThreadLifecycle(
        String threadId,
        Thread thread,
        Thread.State currentState,
        Instant createdAt,
        Map<String, Object> resourceUsage,
        Thread.State previousState
    ) {
        public ThreadLifecycle transitionTo(Thread.State newState) {
            return new ThreadLifecycle(
                threadId,
                thread,
                newState,
                createdAt,
                resourceUsage,
                currentState
            );
        }
    }

    public record LifecycleManagementReport(
        Instant generatedAt,
        int activeThreads,
        long totalCreated,
        long totalTerminated,
        long totalLeaksDetected,
        long resourceLeaks,
        long memoryLeaks,
        long contextLeaks,
        Map<Thread.State, Long> finalStateCounts,
        int totalEvents,
        LifecyclePatterns patterns,
        List<String> recommendations
    ) {}

    public record LifecyclePatterns(
        double blockedPercentage,
        double waitingPercentage,
        double averageThreadAgeSeconds,
        List<String> anomalies
    ) {}
}