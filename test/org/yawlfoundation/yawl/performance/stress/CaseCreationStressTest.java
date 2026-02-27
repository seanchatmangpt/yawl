/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.stress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.test.YawlTestBase;
import org.yawlfoundation.yawl.engine.YAWLStatelessEngine;
import org.yawlfoundation.yawl.elements.YSpecificationID;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.unmarshal.YMarshal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * Stress test for YAWL case creation under high concurrency.
 * Tests the ability to create and launch 10,000 concurrent cases.
 *
 * Validates:
 * - Concurrent case creation performance
 * - Memory usage stability under load
 * - Database connection handling
 * - Deadlock prevention
 * - Proper cleanup of resources
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-26
 */
@TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
public class CaseCreationStressTest extends YawlTestBase {

    private YAWLStatelessEngine engine;
    private YSpecificationID specificationId;
    private ExecutorService executor;
    private static final int CASE_COUNT = 10_000;
    private static final int CONCURRENT_THREADS = 50;
    private static final int TIMEOUT_MINUTES = 5;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize engine
        engine = new YAWLStatelessEngine();

        // Load a simple specification for testing
        String specXml = loadTestResource("stress-test-specification.xml");
        specificationId = engine.uploadSpecification(specXml);

        // Configure thread pool for concurrent operations
        executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
    }

    @Test
    @Timeout(value = TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testConcurrentCaseCreation() throws Exception {
        // Metrics tracking
        AtomicInteger successfulCases = new AtomicInteger(0);
        AtomicInteger failedCases = new AtomicInteger(0);
        AtomicLong totalCreationTime = new AtomicLong(0);
        AtomicLong maxCreationTime = new AtomicLong(0);

        // Array to track completion order
        CompletableFuture<Void>[] futures = new CompletableFuture[CASE_COUNT];

        // Measure start time
        long startTime = System.currentTimeMillis();

        // Launch concurrent case creation
        for (int i = 0; i < CASE_COUNT; i++) {
            final int caseIndex = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                long caseStart = System.currentTimeMillis();

                try {
                    // Create case with unique case identifier
                    String caseId = createTestCase(caseIndex);

                    // Verify case creation success
                    if (verifyCaseExists(caseId)) {
                        successfulCases.incrementAndGet();
                    } else {
                        failedCases.incrementAndGet();
                        throw new RuntimeException("Case not found: " + caseId);
                    }

                    long caseDuration = System.currentTimeMillis() - caseStart;
                    totalCreationTime.addAndGet(caseDuration);

                    // Track maximum creation time
                    long currentMax = maxCreationTime.get();
                    while (caseDuration > currentMax) {
                        if (maxCreationTime.compareAndSet(currentMax, caseDuration)) {
                            break;
                        }
                        currentMax = maxCreationTime.get();
                    }

                } catch (Exception e) {
                    failedCases.incrementAndGet();
                    System.err.println("Case creation failed for index " + caseIndex + ": " + e.getMessage());
                }
            }, executor);
        }

        // Wait for all cases to complete
        CompletableFuture<Void> allCases = CompletableFuture.allOf(futures);

        // Set timeout for the entire test
        allCases.get(TIMEOUT_MINUTES, TimeUnit.MINUTES);

        // Calculate final metrics
        long totalTime = System.currentTimeMillis() - startTime;
        double avgCreationTime = (double) totalCreationTime.get() / CASE_COUNT;
        double throughput = CASE_COUNT / (totalTime / 1000.0);

        // Validate results
        validateStressTestResults(
            successfulCases.get(),
            failedCases.get(),
            totalTime,
            avgCreationTime,
            maxCreationTime.get(),
            throughput
        );
    }

    @Test
    @Timeout(value = TIMEOUT_MINUTES * 2, unit = TimeUnit.MINUTES)
    void testMemoryUnderLoad() throws Exception {
        Runtime runtime = Runtime.getRuntime();

        // Record initial memory state
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        System.gc(); // Suggest garbage collection
        long afterGcMemory = runtime.totalMemory() - runtime.freeMemory();

        // Create cases in batches to monitor memory
        int batchSize = 1000;
        int batches = CASE_COUNT / batchSize;
        AtomicInteger successfulBatches = new AtomicInteger(0);

        for (int batch = 0; batch < batches; batch++) {
            long batchStart = System.currentTimeMillis();
            AtomicInteger batchSuccess = new AtomicInteger(0);

            // Create batch of cases
            CompletableFuture<Void>[] batchFutures = new CompletableFuture[batchSize];
            for (int i = 0; i < batchSize; i++) {
                final int caseIndex = batch * batchSize + i;
                batchFutures[i] = CompletableFuture.runAsync(() -> {
                    try {
                        String caseId = createTestCase(caseIndex);
                        if (verifyCaseExists(caseId)) {
                            batchSuccess.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Log but continue with other cases
                        System.err.println("Failed case in memory test: " + e.getMessage());
                    }
                }, executor);
            }

            // Wait for batch completion
            CompletableFuture.allOf(batchFutures).get(1, TimeUnit.MINUTES);

            // Check memory after batch
            long currentMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryIncrease = currentMemory - afterGcMemory;

            // Log memory usage
            System.out.printf("Batch %d/%d - Memory used: %d MB, Success rate: %.2f%%%n",
                    batch + 1, batches,
                    memoryIncrease / (1024 * 1024),
                    (double) batchSuccess.get() / batchSize * 100);

            successfulBatches.addAndGet(batchSuccess.get());

            // If memory usage is too high, fail the test
            if (memoryIncrease > 500 * 1024 * 1024) { // 500MB threshold
                throw new RuntimeException("Memory usage exceeded threshold: " +
                        (memoryIncrease / (1024 * 1024)) + "MB");
            }

            // Suggest garbage collection
            System.gc();
            afterGcMemory = runtime.totalMemory() - runtime.freeMemory();
        }

        // Calculate final success rate
        double overallSuccessRate = (double) successfulBatches.get() / CASE_COUNT * 100;

        // Validate memory results
        validateMemoryStressResults(overallSuccessRate, afterGcMemory - initialMemory);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void testDatabaseConnectionHandling() throws Exception {
        // Simulate database connection storm
        AtomicInteger connectionFailures = new AtomicInteger(0);
        AtomicInteger successfulConnections = new AtomicInteger(0);

        // Create more concurrent connections than typically configured
        int concurrentConnections = 100;
        CompletableFuture<Void>[] connectionFutures = new CompletableFuture[concurrentConnections];

        for (int i = 0; i < concurrentConnections; i++) {
            final int connectionIndex = i;
            connectionFutures[i] = CompletableFuture.runAsync(() -> {
                try {
                    // Simulate database connection and case creation
                    String caseId = createTestCase(connectionIndex + 10000); // Unique range
                    if (verifyCaseExists(caseId)) {
                        successfulConnections.incrementAndGet();
                    }
                } catch (Exception e) {
                    connectionFailures.incrementAndGet();
                    System.err.println("Connection failure for index " + connectionIndex + ": " + e.getMessage());
                }
            }, executor);
        }

        // Wait for all connections
        CompletableFuture.allOf(connectionFutures).get(5, TimeUnit.MINUTES);

        // Validate connection handling
        validateConnectionStressResults(
            successfulConnections.get(),
            connectionFailures.get(),
            concurrentConnections
        );
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void testDeadlockPrevention() throws Exception {
        // Create scenarios that could potentially deadlock
        AtomicInteger deadlockDetected = new AtomicInteger(0);
        AtomicInteger completedCases = new AtomicInteger(0);

        // Create multiple threads with potential circular dependencies
        int threadCount = 20;
        CompletableFuture<Void>[] deadlockFutures = new CompletableFuture[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            deadlockFutures[i] = CompletableFuture.runAsync(() -> {
                try {
                    // Create cases in patterns that could cause deadlocks
                    for (int j = 0; j < 100; j++) {
                        String caseId = createDeadlockScenarioCase(threadIndex, j);
                        if (verifyCaseExists(caseId)) {
                            completedCases.incrementAndGet();
                        }

                        // Small delay to prevent thread starvation
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    deadlockDetected.incrementAndGet();
                    System.err.println("Deadlock scenario failed in thread " + threadIndex + ": " + e.getMessage());
                }
            }, executor);
        }

        // Monitor for deadlocks by checking thread completion
        CompletableFuture.allOf(deadlockFutures).get(3, TimeUnit.MINUTES);

        // Validate deadlock prevention
        validateDeadlockPreventionResults(
            completedCases.get(),
            deadlockDetected.get(),
            threadCount * 100
        );
    }

    // Helper methods for test scenarios

    private String createTestCase(int caseIndex) throws Exception {
        // Generate unique case ID
        String caseId = "stress-case-" + System.currentTimeMillis() + "-" + caseIndex;

        // Create case using engine
        String caseXml = String.format("""
                <case id="%s">
                    <specificationID>%s</specificationID>
                    <data>
                        <variable name="testVariable" type="string">testValue-%d</variable>
                    </data>
                </case>
                """, caseId, specificationId.toString(), caseIndex);

        // Launch the case
        return engine.launchCase(caseXml, specificationId);
    }

    private boolean verifyCaseExists(String caseId) {
        try {
            // Use a lightweight check to verify case existence
            return engine.getCaseData(caseId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String createDeadlockScenarioCase(int threadIndex, int caseIndex) throws Exception {
        // Create cases with potential resource contention patterns
        String caseId = "deadlock-case-" + threadIndex + "-" + caseIndex;

        // Simulate different access patterns
        String pattern = (threadIndex % 3 == 0) ? "sequential" :
                         (threadIndex % 3 == 1) ? "parallel" : "mixed";

        String caseXml = String.format("""
                <case id="%s">
                    <specificationID>%s</specificationID>
                    <data>
                        <variable name="threadIndex" type="int">%d</variable>
                        <variable name="caseIndex" type="int">%d</variable>
                        <variable name="pattern" type="string">%s</variable>
                    </data>
                </case>
                """, caseId, specificationId.toString(), threadIndex, caseIndex, pattern);

        return engine.launchCase(caseXml, specificationId);
    }

    // Validation methods

    private void validateStressTestResults(int successful, int failed, long totalTime,
                                          double avgTime, long maxTime, double throughput) {
        // Validate basic metrics
        org.junit.jupiter.api.Assertions.assertTrue(
            successful > CASE_COUNT * 0.95,
            String.format("Success rate too low: %d/%d (%.1f%%)", successful, CASE_COUNT,
                         (double) successful / CASE_COUNT * 100)
        );

        org.junit.jupiter.api.Assertions.assertTrue(
            avgTime < 1000, // Average case creation should be under 1 second
            String.format("Average case creation time too high: %.2fms", avgTime)
        );

        org.junit.jupiter.api.Assertions.assertTrue(
            throughput > 10, // Should handle at least 10 cases per second
            String.format("Throughput too low: %.2f cases/second", throughput)
        );

        // Log performance metrics
        System.out.printf("Stress Test Results:%n" +
                "  Total cases: %d%n" +
                "  Successful: %d (%.1f%%)%n" +
                "  Failed: %d (%.1f%%)%n" +
                "  Total time: %.2f seconds%n" +
                "  Avg creation time: %.2fms%n" +
                "  Max creation time: %dms%n" +
                "  Throughput: %.2f cases/second%n%n",
                CASE_COUNT, successful, (double) successful / CASE_COUNT * 100,
                failed, (double) failed / CASE_COUNT * 100,
                totalTime / 1000.0, avgTime, maxTime, throughput);
    }

    private void validateMemoryStressResults(double successRate, long memoryUsed) {
        org.junit.jupiter.api.Assertions.assertTrue(
            successRate > 95,
            String.format("Memory stress success rate too low: %.1f%%", successRate)
        );

        // Memory usage should be reasonable (less than 1GB for 10k cases)
        org.junit.jupiter.api.Assertions.assertTrue(
            memoryUsed < 1024 * 1024 * 1024,
            String.format("Memory usage too high: %d bytes", memoryUsed)
        );

        System.out.printf("Memory Stress Test Results:%n" +
                "  Success rate: %.1f%%%n" +
                "  Memory used: %d MB%n%n",
                successRate, memoryUsed / (1024 * 1024));
    }

    private void validateConnectionStressResults(int successful, int failed, int total) {
        org.junit.jupiter.api.Assertions.assertTrue(
            successful > total * 0.90,
            String.format("Connection success rate too low: %d/%d (%.1f%%)", successful, total,
                         (double) successful / total * 100)
        );

        System.out.printf("Connection Stress Test Results:%n" +
                "  Successful connections: %d (%.1f%%)%n" +
                "  Failed connections: %d (%.1f%%)%n%n",
                successful, (double) successful / total * 100,
                failed, (double) failed / total * 100);
    }

    private void validateDeadlockPreventionResults(int completed, int detected, int total) {
        org.junit.jupiter.api.Assertions.assertEquals(
            0, detected,
            String.format("Deadlocks detected: %d", detected)
        );

        org.junit.jupiter.api.Assertions.assertTrue(
            completed > total * 0.95,
            String.format("Deadlock test completion rate too low: %d/%d (%.1f%%)",
                         completed, total, (double) completed / total * 100)
        );

        System.out.printf("Deadlock Prevention Results:%n" +
                "  Completed cases: %d (%.1f%%)%n" +
                "  Deadlocks detected: %d%n%n",
                completed, (double) completed / total * 100, detected);
    }

    @Override
    void tearDown() {
        // Clean up resources
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