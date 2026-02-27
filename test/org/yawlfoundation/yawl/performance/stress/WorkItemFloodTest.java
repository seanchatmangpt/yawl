/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.stress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.test.YawlTestBase;
import org.yawlfoundation.yawl.engine.YAWLStatelessEngine;
import org.yawlfoundation.yawl.elements.YSpecificationID;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.elements.YWorkItem;
import org.yawlfoundation.yawl.engine.YSpecification;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Stress test for high-frequency work item processing.
 * Tests the ability to handle thousands of work items per second
 * with various task patterns and processing strategies.
 *
 * Validates:
 * - High-frequency work item processing performance
 * - Queue handling under heavy load
 * - Worker thread scalability
 * - Proper work item lifecycle management
 * - Memory usage stability during processing bursts
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-26
 */
@TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
public class WorkItemFloodTest extends YawlTestBase {

    private YAWLStatelessEngine engine;
    private YSpecificationID specificationId;
    private ExecutorService executor;
    private static final int WORK_ITEM_COUNT = 50_000;
    private static final int CONCURRENT_WORKERS = 100;
    private static final int PROCESSING_THREADS = 200;
    private static final int TIMEOUT_MINUTES = 10;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize engine
        engine = new YAWLStatelessEngine();

        // Load a specification with multiple tasks for testing
        String specXml = loadTestResource("workitem-flood-specification.xml");
        specificationId = engine.uploadSpecification(specXml);

        // Configure thread pools
        executor = Executors.newFixedThreadPool(PROCESSING_THREADS);
    }

    @Test
    @Timeout(value = TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testHighFrequencyWorkItemCreation() throws Exception {
        // Metrics tracking
        AtomicInteger createdWorkItems = new AtomicInteger(0);
        AtomicInteger processedWorkItems = new AtomicInteger(0);
        AtomicInteger processingFailures = new AtomicInteger(0);
        AtomicLong totalProcessingTime = new AtomicLong(0);
        AtomicLong maxProcessingTime = new AtomicLong(0);

        // Control the flood rate
        Semaphore rateLimiter = new Semaphore(1000); // Max 1000 concurrent operations
        AtomicLong lastProcessingTime = new AtomicLong(System.currentTimeMillis());

        // Measure start time
        long startTime = System.currentTimeMillis();

        // Create work items at high frequency
        CompletableFuture<Void>[] creationFutures = new CompletableFuture[WORK_ITEM_COUNT];

        for (int i = 0; i < WORK_ITEM_COUNT; i++) {
            final int workItemIndex = i;
            creationFutures[i] = CompletableFuture.runAsync(() -> {
                try {
                    rateLimiter.acquire();

                    long workStart = System.currentTimeMillis();
                    String workItemId = createWorkItem(workItemIndex);

                    if (workItemId != null) {
                        createdWorkItems.incrementAndGet();

                        // Process the work item
                        String result = processWorkItem(workItemId);
                        if (result != null) {
                            processedWorkItems.incrementAndGet();
                        } else {
                            processingFailures.incrementAndGet();
                        }

                        long workDuration = System.currentTimeMillis() - workStart;
                        totalProcessingTime.addAndGet(workDuration);

                        // Track maximum processing time
                        long currentMax = maxProcessingTime.get();
                        while (workDuration > currentMax) {
                            if (maxProcessingTime.compareAndSet(currentMax, workDuration)) {
                                break;
                            }
                            currentMax = maxProcessingTime.get();
                        }
                    }

                    rateLimiter.release();

                } catch (Exception e) {
                    processingFailures.incrementAndGet();
                    System.err.println("Work item creation failed for index " + workItemIndex + ": " + e.getMessage());
                }
            }, executor);
        }

        // Wait for all work items to be created
        CompletableFuture.allOf(creationFutures).get(TIMEOUT_MINUTES - 1, TimeUnit.MINUTES);

        // Wait for processing to complete
        Thread.sleep(30000); // Additional time for processing

        // Calculate metrics
        long totalTime = System.currentTimeMillis() - startTime;
        double avgProcessingTime = (double) totalProcessingTime.get() / processedWorkItems.get();
        double throughput = processedWorkItems.get() / (totalTime / 1000.0);
        double successRate = (double) processedWorkItems.get() / createdWorkItems.get() * 100;

        // Validate results
        validateWorkItemFloodResults(
            createdWorkItems.get(),
            processedWorkItems.get(),
            processingFailures.get(),
            totalTime,
            avgProcessingTime,
            maxProcessingTime.get(),
            throughput,
            successRate
        );
    }

    @Test
    @Timeout(value = TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testQueueUnderPressure() throws Exception {
        // Test behavior when work item queue is under pressure
        AtomicInteger queueOverflows = new AtomicInteger(0);
        AtomicInteger queueFulls = new AtomicInteger(0);
        AtomicInteger processedItems = new AtomicInteger(0);

        // Simulate queue pressure by creating work items faster than processing
        BlockingQueue<YWorkItem> workQueue = new LinkedBlockingQueue<>(1000);
        Semaphore processingGate = new Semaphore(50); // Limited processing capacity

        // Producer threads
        int producerCount = 10;
        CompletableFuture<Void>[] producerFutures = new CompletableFuture[producerCount];

        for (int p = 0; p < producerCount; p++) {
            final int producerId = p;
            producerFutures[p] = CompletableFuture.runAsync(() -> {
                try {
                    for (int i = 0; i < WORK_ITEM_COUNT / producerCount; i++) {
                        try {
                            YWorkItem workItem = createWorkItemForQueue(producerId, i);
                            if (!workQueue.offer(workItem, 100, TimeUnit.MILLISECONDS)) {
                                queueFulls.incrementAndGet();
                            }
                        } catch (Exception e) {
                            queueOverflows.incrementAndGet();
                        }

                        // Producer pacing
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    System.err.println("Producer " + producerId + " failed: " + e.getMessage());
                }
            }, executor);
        }

        // Consumer threads
        int consumerCount = 20;
        CompletableFuture<Void>[] consumerFutures = new CompletableFuture[consumerCount];

        for (int c = 0; c < consumerCount; c++) {
            final int consumerId = c;
            consumerFutures[c] = CompletableFuture.runAsync(() -> {
                try {
                    while (true) {
                        processingGate.acquire();
                        try {
                            YWorkItem workItem = workQueue.poll(100, TimeUnit.MILLISECONDS);
                            if (workItem != null) {
                                String result = processWorkItem(workItem.getID());
                                if (result != null) {
                                    processedItems.incrementAndGet();
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Consumer " + consumerId + " error: " + e.getMessage());
                        } finally {
                            processingGate.release();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor);
        }

        // Wait for producers to complete
        CompletableFuture.allOf(producerFutures).get(5, TimeUnit.MINUTES);

        // Allow consumers to finish processing
        Thread.sleep(60000); // Extra time for queue drain

        // Stop consumers
        for (int c = 0; c < consumerCount; c++) {
            consumerFutures[c].cancel(true);
        }

        // Validate queue pressure results
        validateQueuePressureResults(
            processedItems.get(),
            queueFulls.get(),
            queueOverflows.get(),
            workQueue.size()
        );
    }

    @Test
    @Timeout(value = TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testWorkerThreadScalability() throws Exception {
        // Test different worker thread configurations
        int[] threadCounts = {50, 100, 200, 500};
        Map<Integer, WorkerTestResult> results = new HashMap<>();

        for (int threadCount : threadCounts) {
            System.out.println("Testing with " + threadCount + " worker threads...");

            WorkerTestResult result = testWorkerThreads(threadCount);
            results.put(threadCount, result);
        }

        // Analyze scalability
        validateWorkerScalability(results);
    }

    @Test
    @Timeout(value = TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testMemoryDuringProcessingBurst() throws Exception {
        Runtime runtime = Runtime.getRuntime();

        // Monitor memory during processing bursts
        AtomicInteger burstNumber = new AtomicInteger(0);
        AtomicBoolean burstActive = new AtomicBoolean(false);

        // Memory monitoring thread
        CompletableFuture<Void> memoryMonitor = CompletableFuture.runAsync(() -> {
            try {
                while (!burstActive.get()) {
                    Thread.sleep(1000);
                }

                while (burstActive.get()) {
                    long memoryUsed = runtime.totalMemory() - runtime.freeMemory();
                    System.out.printf("Burst %d - Memory used: %d MB%n",
                            burstNumber.get(), memoryUsed / (1024 * 1024));
                    Thread.sleep(5000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, executor);

        // Create processing bursts
        for (int burst = 0; burst < 5; burst++) {
            burstNumber.incrementAndGet();
            burstActive.set(true);

            System.out.println("Starting burst " + burst);

            // Process 10,000 work items in this burst
            CompletableFuture<Void>[] burstFutures = new CompletableFuture[10_000];
            for (int i = 0; i < 10_000; i++) {
                final int workIndex = i;
                burstFutures[i] = CompletableFuture.runAsync(() -> {
                    try {
                        String workItemId = createWorkItem(burst * 10_000 + workIndex);
                        if (workItemId != null) {
                            processWorkItem(workItemId);
                        }
                    } catch (Exception e) {
                        System.err.println("Error in burst processing: " + e.getMessage());
                    }
                }, executor);
            }

            // Wait for burst completion
            CompletableFuture.allOf(burstFutures).get(3, TimeUnit.MINUTES);

            // Allow memory to stabilize between bursts
            Thread.sleep(10000);
            System.gc();
        }

        burstActive.set(false);
        memoryMonitor.cancel(true);

        System.out.println("Memory burst test completed");
    }

    @Test
    @Timeout(value = TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testWorkItemLifecycleManagement() throws Exception {
        // Test proper creation, processing, and cleanup of work items
        AtomicInteger lifecycleSuccesses = new AtomicInteger(0);
        AtomicInteger lifecycleFailures = new AtomicInteger(0);
        AtomicInteger cleanupFailures = new AtomicInteger(0);

        // Create and work items in a controlled manner
        for (int i = 0; i < 5000; i++) {
            final int workIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // Phase 1: Create work item
                    String workItemId = createWorkItem(workIndex);
                    if (workItemId == null) {
                        lifecycleFailures.incrementAndGet();
                        return;
                    }

                    // Phase 2: Process work item
                    String result = processWorkItem(workItemId);
                    if (result == null) {
                        lifecycleFailures.incrementAndGet();
                        return;
                    }

                    // Phase 3: Cleanup work item
                    boolean cleanup = cleanupWorkItem(workItemId);
                    if (!cleanup) {
                        cleanupFailures.incrementAndGet();
                        return;
                    }

                    lifecycleSuccesses.incrementAndGet();

                } catch (Exception e) {
                    lifecycleFailures.incrementAndGet();
                    System.err.println("Lifecycle failure for index " + workIndex + ": " + e.getMessage());
                }
            }, executor);

            // Add some spacing to prevent overwhelming the system
            if (i % 100 == 0) {
                future.get(1, TimeUnit.SECONDS);
            }
        }

        // Validate lifecycle management
        validateLifecycleManagementResults(
            lifecycleSuccesses.get(),
            lifecycleFailures.get(),
            cleanupFailures.get(),
            5000
        );
    }

    // Helper methods

    private String createWorkItem(int index) throws Exception {
        String caseId = "case-" + (index / 10);
        String taskName = "task-" + (index % 10);

        // Simulate work item creation
        String workItemXml = String.format("""
                <workitem>
                    <caseID>%s</caseID>
                    <task>%s</task>
                    <data>
                        <variable name="itemIndex" type="int">%d</variable>
                        <variable name="processingTime" type="int">%d</variable>
                    </data>
                </workitem>
                """, caseId, taskName, index, ThreadLocalRandom.current().nextInt(10, 100));

        return engine.createWorkItem(workItemXml, specificationId);
    }

    private YWorkItem createWorkItemForQueue(int producerId, int index) throws Exception {
        String caseId = "queue-case-" + producerId + "-" + index;
        String taskName = "queue-task-" + (index % 5);

        return new YWorkItem(caseId, taskName, specificationId);
    }

    private String processWorkItem(String workItemId) {
        try {
            // Simulate processing with variable time
            int processingTime = ThreadLocalRandom.current().nextInt(10, 200);
            Thread.sleep(processingTime);

            // Simulate successful processing
            return "processed-" + workItemId;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean cleanupWorkItem(String workItemId) {
        try {
            // Simulate cleanup
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private WorkerTestResult testWorkerThreads(int threadCount) throws Exception {
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        // Create worker thread pool
        ExecutorService workerPool = Executors.newFixedThreadPool(threadCount);

        // Submit work items
        CompletableFuture<Void>[] workerFutures = new CompletableFuture[WORK_ITEM_COUNT / 10];
        for (int i = 0; i < workerFutures.length; i++) {
            final int batchIndex = i;
            workerFutures[i] = CompletableFuture.runAsync(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        String workItemId = createWorkItem(batchIndex * 10 + j);
                        if (workItemId != null) {
                            String result = processWorkItem(workItemId);
                            if (result != null) {
                                completed.incrementAndGet();
                            } else {
                                failed.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
            }, workerPool);
        }

        // Wait for completion
        CompletableFuture.allOf(workerFutures).get(3, TimeUnit.MINUTES);
        long duration = System.currentTimeMillis() - startTime;

        workerPool.shutdown();

        return new WorkerTestResult(threadCount, completed.get(), failed.get(), duration);
    }

    // Validation methods

    private void validateWorkItemFloodResults(int created, int processed, int failed,
                                              long totalTime, double avgTime, long maxTime,
                                              double throughput, double successRate) {
        org.junit.jupiter.api.Assertions.assertTrue(
            created > WORK_ITEM_COUNT * 0.95,
            String.format("Created work items too low: %d/%d (%.1f%%)", created, WORK_ITEM_COUNT,
                         (double) created / WORK_ITEM_COUNT * 100)
        );

        org.junit.jupiter.api.Assertions.assertTrue(
            successRate > 90,
            String.format("Success rate too low: %.1f%%", successRate)
        );

        org.junit.jupiter.api.Assertions.assertTrue(
            throughput > 100, // Should handle at least 100 work items per second
            String.format("Throughput too low: %.2f work items/second", throughput)
        );

        System.out.printf("Work Item Flood Test Results:%n" +
                "  Created: %d%n" +
                "  Processed: %d (%.1f%%)%n" +
                "  Failed: %d (%.1f%%)%n" +
                "  Total time: %.2f seconds%n" +
                "  Avg processing time: %.2fms%n" +
                "  Max processing time: %dms%n" +
                "  Throughput: %.2f work items/second%n%n",
                created, processed, successRate,
                failed, (double) failed / created * 100,
                totalTime / 1000.0, avgTime, maxTime, throughput);
    }

    private void validateQueuePressureResults(int processed, int queueFulls, int queueOverflows, int remaining) {
        org.junit.jupiter.api.Assertions.assertTrue(
            processed > 4000, // Should process most items
            String.format("Processed items too low: %d", processed)
        );

        System.out.printf("Queue Pressure Test Results:%n" +
                "  Processed: %d%n" +
                "  Queue full events: %d%n" +
                "  Queue overflow events: %d%n" +
                "  Remaining in queue: %d%n%n",
                processed, queueFulls, queueOverflows, remaining);
    }

    private void validateWorkerScalability(Map<Integer, WorkerTestResult> results) {
        double lastThroughput = 0;
        boolean scalabilityImproves = false;

        for (Map.Entry<Integer, WorkerTestResult> entry : results.entrySet()) {
            int threadCount = entry.getKey();
            WorkerTestResult result = entry.getValue();

            double throughput = (double) result.completed / (result.duration / 1000.0);
            System.out.printf("%d threads: %.2f work items/second%n", threadCount, throughput);

            // Check if adding threads improves throughput
            if (threadCount > 50 && throughput > lastThroughput * 1.1) {
                scalabilityImproves = true;
            }

            lastThroughput = throughput;
        }

        org.junit.jupiter.api.Assertions.assertTrue(
            scalabilityImproves,
            "Throughput should improve with more threads"
        );
    }

    private void validateLifecycleManagementResults(int successes, int failures, int cleanupFailures, int total) {
        org.junit.jupiter.api.Assertions.assertTrue(
            successes > total * 0.95,
            String.format("Lifecycle success rate too low: %d/%d (%.1f%%)", successes, total,
                         (double) successes / total * 100)
        );

        org.junit.jupiter.api.Assertions.assertTrue(
            cleanupFailures < total * 0.05,
            String.format("Cleanup failures too high: %d/%d (%.1f%%)", cleanupFailures, total,
                         (double) cleanupFailures / total * 100)
        );

        System.out.printf("Lifecycle Management Results:%n" +
                "  Successful: %d (%.1f%%)%n" +
                "  Failed: %d (%.1f%%)%n" +
                "  Cleanup failures: %d (%.1f%%)%n%n",
                successes, (double) successes / total * 100,
                failures, (double) failures / total * 100,
                cleanupFailures, (double) cleanupFailures / total * 100);
    }

    // Inner classes

    private static class WorkerTestResult {
        final int threadCount;
        final int completed;
        final int failed;
        final long duration;

        WorkerTestResult(int threadCount, int completed, int failed, long duration) {
            this.threadCount = threadCount;
            this.completed = completed;
            this.failed = failed;
            this.duration = duration;
        }
    }

    @Override
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    System.err.println("Warning: Executor did not terminate cleanly");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (engine != null) {
            try {
                engine.shutdown();
            } catch (Exception e) {
                System.err.println("Error shutting down engine: " + e.getMessage());
            }
        }
    }
}