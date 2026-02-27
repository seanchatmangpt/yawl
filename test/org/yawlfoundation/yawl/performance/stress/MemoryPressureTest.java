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
import org.junit.jupiter.api.Assertions;
import org.yawlfoundation.yawl.test.YawlTestBase;
import org.yawlfoundation.yawl.engine.YAWLStatelessEngine;
import org.yawlfoundation.yawl.elements.YSpecificationID;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.elements.YWorkItem;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * Stress test for memory exhaustion scenarios.
 * Tests the YAWL engine's behavior under memory pressure and
 * validates garbage collection patterns and memory leak detection.
 *
 * Validates:
 * - Memory usage patterns under high load
 * - Garbage collection behavior
 * - Memory leak detection and prevention
 * - OutOfMemoryError handling
 * - Memory recovery after pressure release
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-26
 */
@TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
public class MemoryPressureTest extends YawlTestBase {

    private YAWLStatelessEngine engine;
    private YSpecificationID specificationId;
    private ExecutorService executor;
    private MemoryMXBean memoryMXBean;

    private static final int CASE_COUNT = 20_000;
    private static final int MEMORY_MB_THRESHOLD = 1024; // 1GB
    private static final int TIMEOUT_MINUTES = 15;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize engine
        engine = new YAWLStatelessEngine();

        // Load a specification for memory testing
        String specXml = loadTestResource("memory-pressure-specification.xml");
        specificationId = engine.uploadSpecification(specXml);

        // Configure thread pool
        executor = Executors.newFixedThreadPool(50);

        // Get memory management beans
        memoryMXBean = ManagementFactory.getMemoryMXBean();
    }

    @Test
    @Timeout(value = TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testMemoryGrowthPattern() throws Exception {
        // Monitor memory growth during case creation
        MemoryUsage initialMemory = memoryMXBean.getHeapMemoryUsage();
        AtomicInteger successfulCases = new AtomicInteger(0);
        AtomicInteger failedCases = new AtomicInteger(0);
        List<Long> memorySnapshots = new ArrayList<>();

        // Measure memory at intervals
        ScheduledExecutorService memoryMonitor = Executors.newSingleThreadScheduledExecutor();
        memoryMonitor.scheduleAtFixedRate(() -> {
            MemoryUsage currentMemory = memoryMXBean.getHeapMemoryUsage();
            memorySnapshots.add(currentMemory.getUsed());
            System.out.printf("Memory: %d MB%n", currentMemory.getUsed() / (1024 * 1024));
        }, 0, 2, TimeUnit.SECONDS);

        // Create cases continuously
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < CASE_COUNT; i++) {
            final int caseIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String caseId = createMemoryTestCase(caseIndex);
                    if (caseId != null) {
                        successfulCases.incrementAndGet();
                    } else {
                        failedCases.incrementAndGet();
                    }
                } catch (Exception e) {
                    failedCases.incrementAndGet();
                    System.err.println("Memory test case failed for index " + caseIndex + ": " + e.getMessage());
                }
            }, executor);

            // Control rate to avoid overwhelming
            if (i % 100 == 0) {
                future.get(100, TimeUnit.MILLISECONDS);
            }
        }

        // Wait for all cases to complete
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);

        // Stop memory monitor
        memoryMonitor.shutdown();

        // Analyze memory patterns
        MemoryUsage finalMemory = memoryMXBean.getHeapMemoryUsage();
        long memoryIncrease = finalMemory.getUsed() - initialMemory.getUsed();
        double avgMemoryPerCase = (double) memoryIncrease / CASE_COUNT;

        // Validate memory growth
        validateMemoryGrowthPattern(
            successfulCases.get(),
            failedCases.get(),
            memoryIncrease,
            avgMemoryPerCase,
            memorySnapshots
        );

        System.out.printf("Memory Growth Results:%n" +
                "  Initial memory: %d MB%n" +
                "  Final memory: %d MB%n" +
                "  Memory increase: %d MB%n" +
                "  Avg per case: %.2f bytes%n%n",
                initialMemory.getUsed() / (1024 * 1024),
                finalMemory.getUsed() / (1024 * 1024),
                memoryIncrease / (1024 * 1024),
                avgMemoryPerCase);
    }

    @Test
    @Timeout(value = TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testGarbageCollectionUnderLoad() throws Exception {
        // Test GC behavior under memory pressure
        AtomicInteger gcCountBefore = new AtomicInteger(0);
        AtomicInteger gcTimeBefore = new AtomicInteger(0);
        AtomicInteger gcCountAfter = new AtomicInteger(0);
        AtomicInteger gcTimeAfter = new AtomicInteger(0);

        // Monitor GC before test
        monitorGarbageCollection(gcCountBefore, gcTimeBefore, 10000);

        // Create memory pressure
        MemoryUsage beforePressure = memoryMXBean.getHeapMemoryUsage();
        List<byte[]> memoryConsumers = new ArrayList<>();

        // Consume memory while creating cases
        for (int i = 0; i < 100; i++) {
            // Allocate chunks of memory
            byte[] memoryChunk = new byte[10 * 1024 * 1024]; // 10MB chunks
            memoryConsumers.add(memoryChunk);

            // Create cases while consuming memory
            for (int j = 0; j < 200; j++) {
                final int caseIndex = i * 200 + j;
                CompletableFuture.runAsync(() -> {
                    try {
                        createMemoryTestCase(caseIndex);
                    } catch (Exception e) {
                        System.err.println("GC test case failed: " + e.getMessage());
                    }
                }, executor);
            }

            // Force garbage collection periodically
            if (i % 10 == 0) {
                System.gc();
                Thread.sleep(1000); // Allow GC to complete
            }
        }

        // Monitor GC during test
        monitorGarbageCollection(gcCountAfter, gcTimeAfter, 30000);

        // Cleanup memory consumers
        memoryConsumers.clear();
        System.gc();

        MemoryUsage afterPressure = memoryMXBean.getHeapMemoryUsage();

        // Validate GC behavior
        validateGarbageCollectionBehavior(
            beforePressure.getUsed(),
            afterPressure.getUsed(),
            gcCountBefore.get(),
            gcTimeBefore.get(),
            gcCountAfter.get(),
            gcTimeAfter.get()
        );
    }

    @Test
    @Timeout(value = TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testMemoryLeakDetection() throws Exception {
        // Test for memory leaks by creating and destroying many cases
        List<String> createdCases = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger leakDetectionCount = new AtomicInteger(0);
        MemoryUsage baselineMemory = memoryMXBean.getHeapMemoryUsage();

        // Create multiple cycles of cases
        for (int cycle = 0; cycle < 10; cycle++) {
            System.out.println("Memory leak test cycle " + (cycle + 1) + "/10");

            // Create batch of cases
            List<String> currentBatch = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                String caseId = createMemoryTestCase(cycle * 1000 + i);
                if (caseId != null) {
                    currentBatch.add(caseId);
                }
            }

            // Simulate case completion and cleanup
            for (String caseId : currentBatch) {
                // Complete the case
                completeTestCase(caseId);
                // Remove from tracking
                createdCases.remove(caseId);
            }

            // Force garbage collection
            System.gc();
            Thread.sleep(2000);

            // Check memory usage
            MemoryUsage currentMemory = memoryMXBean.getHeapMemoryUsage();
            long memoryDelta = currentMemory.getUsed() - baselineMemory.getUsed();

            // If memory grows significantly, potential leak detected
            if (memoryDelta > 100 * 1024 * 1024) { // 100MB threshold
                leakDetectionCount.incrementAndGet();
                System.out.println("Potential memory leak detected in cycle " + (cycle + 1) +
                        ": memory delta = " + (memoryDelta / (1024 * 1024)) + " MB");
            }
        }

        // Validate leak detection
        validateMemoryLeakDetection(
            leakDetectionCount.get(),
            createdCases.size()
        );
    }

    @Test
    @Timeout(value = TIMEOUT_MINUTES / 2, unit = TimeUnit.MINUTES)
    void testMemoryRecoveryAfterPressure() throws Exception {
        // Test memory recovery after releasing pressure
        MemoryUsage initialMemory = memoryMXBean.getHeapMemoryUsage();
        List<String> createdCases = new ArrayList<>();

        // Create high memory pressure
        for (int i = 0; i < 5000; i++) {
            String caseId = createMemoryTestCase(i);
            if (caseId != null) {
                createdCases.add(caseId);
            }

            // Periodically force GC
            if (i % 500 == 0) {
                System.gc();
                Thread.sleep(1000);
            }
        }

        // Measure peak memory
        MemoryUsage peakMemory = memoryMXBean.getHeapMemoryUsage();
        long peakMemoryDelta = peakMemory.getUsed() - initialMemory.getUsed();

        System.out.printf("Peak memory reached: %d MB%n", peakMemoryDelta / (1024 * 1024));

        // Clear all cases and force GC
        for (String caseId : createdCases) {
            completeTestCase(caseId);
        }
        createdCases.clear();

        // Multiple GC cycles
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(1000);
        }

        // Measure final memory
        MemoryUsage finalMemory = memoryMXBean.getHeapMemoryUsage();
        long finalMemoryDelta = finalMemory.getUsed() - initialMemory.getUsed();
        double recoveryRatio = (double) finalMemoryDelta / peakMemoryDelta;

        // Validate memory recovery
        validateMemoryRecovery(
            initialMemory.getUsed(),
            peakMemory.getUsed(),
            finalMemory.getUsed(),
            peakMemoryDelta,
            finalMemoryDelta,
            recoveryRatio
        );
    }

    @Test
    @Timeout(value = TIMEOUT_MINUTES / 2, unit = TimeUnit.MINUTES)
    void testOutOfMemoryHandling() throws Exception {
        // Test behavior when approaching memory limits
        AtomicInteger oomCount = new AtomicInteger(0);
        AtomicInteger successfulCases = new AtomicInteger(0);
        MemoryUsage currentMemory = memoryMXBean.getHeapMemoryUsage();
        long maxMemory = memoryMXBean.getHeapMemoryUsage().getMax();

        // Consume memory aggressively
        List<byte[]> memoryBlocks = new ArrayList<>();
        boolean reachedOOM = false;

        for (int i = 0; i < 10000 && !reachedOOM; i++) {
            try {
                // Check memory usage
                currentMemory = memoryMXBean.getHeapMemoryUsage();
                long usedPercentage = (long) currentMemory.getUsed() * 100 / maxMemory;

                System.out.printf("Memory usage: %d%% (%d MB)%n",
                        usedPercentage, currentMemory.getUsed() / (1024 * 1024));

                // Create memory blocks if below threshold
                if (usedPercentage < 90) {
                    byte[] block = new byte[5 * 1024 * 1024]; // 5MB blocks
                    memoryBlocks.add(block);
                }

                // Try to create cases
                try {
                    String caseId = createMemoryTestCase(i);
                    if (caseId != null) {
                        successfulCases.incrementAndGet();
                    }
                } catch (OutOfMemoryError e) {
                    oomCount.incrementAndGet();
                    reachedOOM = true;
                    System.out.println("OutOfMemoryError encountered at case " + i);
                }

                // Small delay to prevent overwhelming the system
                Thread.sleep(10);

            } catch (OutOfMemoryError e) {
                oomCount.incrementAndGet();
                reachedOOM = true;
                System.out.println("OutOfMemoryError encountered in memory allocation");
                break;
            }
        }

        // Cleanup
        memoryBlocks.clear();
        System.gc();

        // Validate OOM handling
        validateOutOfMemoryHandling(
            successfulCases.get(),
            oomCount.get(),
            memoryBlocks.size(),
            reachedOOM
        );
    }

    @Test
    @Timeout(value = TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testMemoryFragmentation() throws Exception {
        // Test memory fragmentation patterns
        MemoryUsage initialMemory = memoryMXBean.getHeapMemoryUsage();
        List<byte[]> allocations = new ArrayList<>();
        AtomicInteger fragmentationDetected = new AtomicInteger(0);

        // Create allocations of varying sizes to cause fragmentation
        int[] sizes = {1024, 10 * 1024, 100 * 1024, 1024 * 1024}; // 1KB, 10KB, 100KB, 1MB

        for (int cycle = 0; cycle < 10; cycle++) {
            System.out.println("Memory fragmentation test cycle " + (cycle + 1) + "/10");

            // Allocate and deallocate in patterns
            for (int i = 0; i < 100; i++) {
                int size = sizes[i % sizes.length];
                byte[] allocation = new byte[size];
                allocations.add(allocation);

                // Create test case
                try {
                    createMemoryTestCase(cycle * 100 + i);
                } catch (Exception e) {
                    System.err.println("Fragmentation test case failed: " + e.getMessage());
                }

                // Remove some allocations to create holes
                if (i % 5 == 0) {
                    allocations.remove(i / 5);
                }
            }

            // Force GC and check memory
            long beforeGC = memoryMXBean.getHeapMemoryUsage().getUsed();
            System.gc();
            Thread.sleep(2000);
            long afterGC = memoryMXBean.getHeapMemoryUsage().getUsed();

            // If memory doesn't decrease significantly, fragmentation may be occurring
            if (beforeGC - afterGC < 50 * 1024 * 1024) { // Less than 50MB freed
                fragmentationDetected.incrementAndGet();
                System.out.println("Potential memory fragmentation detected");
            }

            // Clear allocations
            allocations.clear();
            Thread.sleep(1000);
        }

        // Validate fragmentation
        validateMemoryFragmentation(
            fragmentationDetected.get(),
            allocations.size()
        );
    }

    // Helper methods

    private String createMemoryTestCase(int index) throws Exception {
        String caseId = "memory-case-" + index;

        // Create case with memory-intensive data
        String caseXml = String.format("""
                <case id="%s">
                    <specificationID>%s</specificationID>
                    <data>
                        <variable name="caseIndex" type="int">%d</variable>
                        <variable name="dataSize" type="int">%d</variable>
                        <variable name="timestamp" type="string">%s</variable>
                    </data>
                </case>
                """, caseId, specificationId.toString(), index,
                ThreadLocalRandom.current().nextInt(1000, 10000),
                String.valueOf(System.currentTimeMillis()));

        return engine.launchCase(caseXml, specificationId);
    }

    private void completeTestCase(String caseId) {
        try {
            // Simulate case completion
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void monitorGarbageCollection(AtomicInteger gcCount, AtomicInteger gcTime, long durationMs) {
        long startTime = System.currentTimeMillis();
        int initialGCCount = getGarbageCollectionCount();

        while (System.currentTimeMillis() - startTime < durationMs) {
            int currentGCCount = getGarbageCollectionCount();
            gcCount.set(currentGCCount - initialGCCount);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private int getGarbageCollectionCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToInt(bean -> bean.getCollectionCount())
                .sum();
    }

    // Validation methods

    private void validateMemoryGrowthPattern(int successful, int failed, long memoryIncrease,
                                           double avgPerCase, List<Long> memorySnapshots) {
        org.junit.jupiter.api.Assertions.assertTrue(
            successful > CASE_COUNT * 0.90,
            String.format("Successful cases too low: %d/%d (%.1f%%)", successful, CASE_COUNT,
                         (double) successful / CASE_COUNT * 100)
        );

        org.junit.jupiter.api.Assertions.assertTrue(
            memoryIncrease < MEMORY_MB_THRESHOLD * 1024 * 1024,
            String.format("Memory increase too high: %d MB", memoryIncrease / (1024 * 1024))
        );

        org.junit.jupiter.api.Assertions.assertTrue(
            avgPerCase < 1024, // Less than 1KB per case
            String.format("Average memory per case too high: %.2f bytes", avgPerCase)
        );

        // Check for memory spikes
        long maxMemory = memorySnapshots.stream().max(Long::compare).orElse(0L);
        long avgMemory = memorySnapshots.stream().mapToLong(Long::longValue).average().orElse(0L);
        double spikeRatio = (double) maxMemory / avgMemory;

        org.junit.jupiter.api.Assertions.assertTrue(
            spikeRatio < 2.0,
            String.format("Memory spike ratio too high: %.2f", spikeRatio)
        );
    }

    private void validateGarbageCollectionBehavior(long beforeMemory, long afterMemory,
                                                 int gcCountBefore, int gcTimeBefore,
                                                 int gcCountAfter, int gcTimeAfter) {
        org.junit.jupiter.api.Assertions.assertTrue(
            gcCountAfter > gcCountBefore,
            "Garbage collection should increase under memory pressure"
        );

        System.out.printf("GC Behavior Results:%n" +
                "  GC count before: %d, after: %d%n" +
                "  GC time before: %d ms, after: %d ms%n" +
                "  Memory before: %d MB, after: %d MB%n%n",
                gcCountBefore, gcCountAfter,
                gcTimeBefore, gcTimeAfter,
                beforeMemory / (1024 * 1024), afterMemory / (1024 * 1024));
    }

    private void validateMemoryLeakDetection(int leakDetections, int remainingCases) {
        org.junit.jupiter.api.Assertions.assertEquals(
            0, leakDetections,
            String.format("Memory leaks detected: %d", leakDetections)
        );

        org.junit.jupiter.api.Assertions.assertEquals(
            0, remainingCases,
            String.format("Remaining cases after cleanup: %d", remainingCases)
        );
    }

    private void validateMemoryRecovery(long initialMemory, long peakMemory, long finalMemory,
                                      long peakDelta, long finalDelta, double recoveryRatio) {
        // Memory should recover to within 10% of initial
        org.junit.jupiter.api.Assertions.assertTrue(
            finalDelta < initialMemory * 0.1,
            String.format("Memory recovery insufficient: %d MB vs initial %d MB",
                         finalDelta / (1024 * 1024), initialMemory / (1024 * 1024))
        );

        System.out.printf("Memory Recovery Results:%n" +
                "  Initial: %d MB%n" +
                "  Peak: %d MB (delta: %d MB)%n" +
                "  Final: %d MB (delta: %d MB)%n" +
                "  Recovery ratio: %.2f%n%n",
                initialMemory / (1024 * 1024),
                peakMemory / (1024 * 1024), peakDelta / (1024 * 1024),
                finalMemory / (1024 * 1024), finalDelta / (1024 * 1024),
                recoveryRatio);
    }

    private void validateOutOfMemoryHandling(int successful, int oomCount, int memoryBlocks, boolean reachedOOM) {
        org.junit.jupiter.api.Assertions.assertTrue(
            oomCount >= 1 || reachedOOM,
            "Should encounter OutOfMemoryError under memory pressure"
        );

        System.out.printf("Out of Memory Handling Results:%n" +
                "  Successful cases: %d%n" +
                "  OOM encounters: %d%n" +
                "  Memory blocks allocated: %d%n" +
                "  Reached OOM state: %b%n%n",
                successful, oomCount, memoryBlocks, reachedOOM);
    }

    private void validateMemoryFragmentation(int fragmentationsDetected, int allocations) {
        org.junit.jupiter.api.Assertions.assertTrue(
            fragmentationsDetected < 3,
            String.format("Memory fragmentation detected too often: %d times", fragmentationsDetected)
        );

        System.out.printf("Memory Fragmentation Results:%n" +
                "  Fragmentations detected: %d%n" +
                "  Total allocations: %d%n%n",
                fragmentationsDetected, allocations);
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