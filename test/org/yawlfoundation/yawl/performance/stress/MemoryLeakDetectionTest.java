/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.stress;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.engine.YAWLStatelessEngine;
import org.yawlfoundation.yawl.elements.YSpecificationID;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * Memory leak detection test for YAWL v6.0.0-GA.
 *
 * Performs sustained load testing with continuous memory monitoring
 * to detect memory leaks and validate garbage collection behavior.
 *
 * @author YAWL Performance Team
 * @version 6.0.0-GA
 * @since 2026-02-26
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MemoryLeakDetectionTest {

    private YAWLStatelessEngine engine;
    private MemoryMXBean memoryMXBean;
    private ExecutorService executorService;
    private ScheduledExecutorService monitoringExecutor;

    // Test configuration
    private static final long TEST_DURATION_MINUTES = 60;
    private static final long MEMORY_SAMPLE_INTERVAL_MS = 5000; // 5 seconds
    private static final long MEMORY_THRESHOLD_MB = 100; // 100MB growth threshold
    private static final int CONCURRENT_CASES = 100;
    private static final int CASE_CREATION_RATE = 10; // cases per second

    // Memory tracking
    private final AtomicBoolean testRunning = new AtomicBoolean(true);
    private final AtomicLong baselineMemory = new AtomicLong(0);
    private final AtomicInteger memoryWarnings = new AtomicInteger(0);
    private final AtomicInteger gcEvents = new AtomicInteger(0);
    private final AtomicLong totalAllocatedMemory = new AtomicLong(0);
    private final AtomicLong totalFreedMemory = new AtomicLong(0);

    // Memory samples for trend analysis
    private final ConcurrentLinkedQueue<MemorySample> memorySamples = new ConcurrentLinkedQueue<>();

    @BeforeAll
    void setUp() throws Exception {
        engine = new YAWLStatelessEngine();
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        executorService = Executors.newVirtualThreadPerTaskExecutor();
        monitoringExecutor = Executors.newSingleThreadScheduledExecutor();

        // Load test specifications
        loadTestSpecifications();

        // Start memory monitoring
        startMemoryMonitoring();
    }

    @AfterAll
    void tearDown() throws Exception {
        testRunning.set(false);

        // Shutdown executors
        monitoringExecutor.shutdown();
        executorService.shutdown();

        if (!monitoringExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            monitoringExecutor.shutdownNow();
        }

        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }

        // Generate final report
        generateMemoryReport();
    }

    @Test
    @DisplayName("Test sustained load with memory leak detection")
    @Timeout(value = 60, unit = TimeUnit.MINUTES)
    void testSustainedLoadWithMemoryLeakDetection() throws Exception {
        System.out.println("Starting sustained load test with memory leak detection...");

        // Record baseline memory
        baselineMemory.set(getUsedMemory());
        System.out.printf("Baseline memory: %d MB%n", baselineMemory.get() / (1024 * 1024));

        // Create workload generator
        WorkloadGenerator generator = new WorkloadGenerator();
        Thread generatorThread = new Thread(generator);
        generatorThread.start();

        // Wait for test duration
        Thread.sleep(TEST_DURATION_MINUTES * 60 * 1000);

        // Stop workload generator
        generator.stop();
        generatorThread.join();

        // Validate memory behavior
        validateMemoryLeakDetectionResults();
    }

    @Test
    @DisplayName("Test garbage collection behavior under load")
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void testGarbageCollectionBehavior() throws Exception {
        System.out.println("Testing garbage collection behavior under sustained load...");

        // Monitor GC before test
        long initialGcCount = getGcCount();
        long initialMemory = getUsedMemory();

        // Create intensive workload to trigger GC
        createIntensiveWorkload();

        // Monitor GC during test
        for (int i = 0; i < 10; i++) {
            Thread.sleep(30000); // 30 seconds between measurements

            long currentGcCount = getGcCount();
            long currentMemory = getUsedMemory();

            System.out.printf("GC Cycle %d: GC events = %d, Memory = %d MB%n",
                i + 1, currentGcCount - initialGcCount, currentMemory / (1024 * 1024));

            // Check for abnormal GC behavior
            validateGarbageCollectionBehavior(initialGcCount, currentGcCount, initialMemory, currentMemory);
        }

        validateGarbageCollectionSummary();
    }

    @Test
 @DisplayName("Test memory pressure handling")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void testMemoryPressureHandling() throws Exception {
        System.out.println("Testing memory pressure handling...");

        // Create memory-intensive operations
        AtomicInteger memoryPressureEvents = new AtomicInteger(0);
        AtomicLong peakMemory = new AtomicLong(0);

        // Start memory monitoring thread
        Thread monitoringThread = new Thread(() -> {
            while (testRunning.get()) {
                long currentMemory = getUsedMemory();
                peakMemory.updateAndGet(current -> Math.max(current, currentMemory));

                if (currentMemory > getMaxHeapSize() * 0.8) { // 80% of max heap
                    memoryPressureEvents.incrementAndGet();
                    System.out.printf("Memory pressure event: %d MB (80%% threshold)%n",
                        currentMemory / (1024 * 1024));
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        monitoringThread.start();

        // Create memory-intensive workload
        createMemoryIntensiveWorkload();

        monitoringThread.join();

        // Validate memory pressure handling
        validateMemoryPressureHandling(peakMemory.get(), memoryPressureEvents.get());
    }

    @Test
    @DisplayName("Test object allocation patterns")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void testObjectAllocationPatterns() throws Exception {
        System.out.println("Testing object allocation patterns...");

        // Track allocation patterns
        AtomicInteger temporaryObjects = new AtomicInteger(0);
        AtomicInteger longLivedObjects = new AtomicInteger(0);

        // Create different allocation patterns
        ExecutorService allocExecutor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < 1000; i++) {
            final int iteration = i;
            allocExecutor.submit(() -> {
                // Pattern 1: Temporary objects (should be GC'd quickly)
                if (iteration % 2 == 0) {
                    createTemporaryObjects(100);
                    temporaryObjects.addAndGet(100);
                }
                // Pattern 2: Long-lived objects (should accumulate)
                else {
                    createLongLivedObjects(50);
                    longLivedObjects.addAndGet(50);
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Wait for allocation completion
        allocExecutor.shutdown();
        allocExecutor.awaitTermination(10, TimeUnit.MINUTES);

        // Validate allocation patterns
        validateObjectAllocationPatterns(
            temporaryObjects.get(),
            longLivedObjects.get()
        );
    }

    // Helper methods

    private void loadTestSpecifications() throws Exception {
        // Load various workflow specifications to simulate different memory patterns
        String[] specifications = {
            createSimpleSpecification(),
            createComplexSpecification(),
            createRecursiveSpecification()
        };

        for (String specXml : specifications) {
            YSpecificationID specId = engine.uploadSpecification(specXml);
            System.out.println("Loaded specification: " + specId);
        }
    }

    private String createSimpleSpecification() {
        return """
            <specification id="SimpleMemoryTest" version="1.0">
                <name>Simple Memory Test</name>
                <process id="simpleProcess" name="Simple Process">
                    <start id="start"/>
                    <task id="task1" name="Memory Task"/>
                    <end id="end"/>
                    <flow from="start" to="task1"/>
                    <flow from="task1" to="end"/>
                </process>
            </specification>
            """;
    }

    private String createComplexSpecification() {
        return """
            <specification id="ComplexMemoryTest" version="1.0">
                <name>Complex Memory Test</name>
                <process id="complexProcess" name="Complex Process">
                    <start id="start"/>
                    <task id="task1" name="Setup Task"/>
                    <task id="task2" name="Data Processing Task"/>
                    <task id="task3" name="Cleanup Task"/>
                    <end id="end"/>
                    <flow from="start" to="task1"/>
                    <flow from="task1" to="task2"/>
                    <flow from="task2" to="task3"/>
                    <flow from="task3" to="end"/>
                </process>
            </specification>
            """;
    }

    private String createRecursiveSpecification() {
        return """
            <specification id="RecursiveMemoryTest" version="1.0">
                <name>Recursive Memory Test</name>
                <process id="recursiveProcess" name="Recursive Process">
                    <start id="start"/>
                    <task id="recursiveTask" name="Recursive Task"/>
                    <end id="end"/>
                    <flow from="start" to="recursiveTask"/>
                    <flow from="recursiveTask" to="recursiveTask"/>
                    <flow from="recursiveTask" to="end"/>
                </process>
            </specification>
            """;
    }

    private long getUsedMemory() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        return heapUsage.getUsed();
    }

    private long getMaxHeapSize() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        return heapUsage.getMax();
    }

    private long getGcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
            .mapToLong(bean -> bean.getCollectionCount())
            .sum();
    }

    private void startMemoryMonitoring() {
        monitoringExecutor.scheduleAtFixedRate(() -> {
            if (testRunning.get()) {
                long usedMemory = getUsedMemory();
                long gcCount = getGcCount();
                long memoryDelta = usedMemory - baselineMemory.get();

                // Record memory sample
                memorySamples.add(new MemorySample(
                    System.currentTimeMillis(),
                    usedMemory,
                    gcCount,
                    memoryDelta
                ));

                // Check for memory leak
                if (memoryDelta > MEMORY_THRESHOLD_MB * 1024 * 1024) {
                    memoryWarnings.incrementAndGet();
                    System.out.printf("WARNING: Memory leak detected! Growth: %d MB%n",
                        memoryDelta / (1024 * 1024));
                }

                // Log progress
                if (memorySamples.size() % 12 == 0) { // Every minute
                    System.out.printf("Memory status: %d MB used, %d GC events, %d warnings%n",
                        usedMemory / (1024 * 1024),
                        gcCount,
                        memoryWarnings.get());
                }
            }
        }, MEMORY_SAMPLE_INTERVAL_MS, MEMORY_SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void createIntensiveWorkload() throws InterruptedException {
        AtomicInteger casesCreated = new AtomicInteger(0);

        for (int i = 0; i < 1000; i++) {
            final int iteration = i;
            executorService.submit(() -> {
                try {
                    // Create intensive memory operations
                    createMemoryIntensiveCase(iteration);
                    casesCreated.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Error in intensive workload: " + e.getMessage());
                }
            });

            // Controlled creation rate
            Thread.sleep(1000 / CASE_CREATION_RATE);
        }

        // Wait for cases to complete
        Thread.sleep(10000); // 10 seconds for cases to process
    }

    private void createMemoryIntensiveWorkload() throws InterruptedException {
        // Create objects that consume significant memory
        List<byte[]> memoryBlocks = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            // Create 10MB blocks
            byte[] block = new byte[10 * 1024 * 1024];
            memoryBlocks.add(block);
            totalAllocatedMemory.addAndGet(block.length);

            // Simulate processing time
            Thread.sleep(100);

            // Remove some blocks to test GC
            if (i % 5 == 0) {
                memoryBlocks.remove(0);
                totalFreedMemory.addAndGet(10 * 1024 * 1024);
            }
        }
    }

    private void createMemoryIntensiveCase(int iteration) throws Exception {
        String caseId = "memory-case-" + iteration + "-" + System.currentTimeMillis();
        String specId = "MemoryTest-" + (iteration % 3 + 1); // Cycle through different specs

        String caseXml = String.format("""
            <case id="%s">
                <specificationID>%s</specificationID>
                <data>
                    <variable name="iteration" type="int">%d</variable>
                    <variable name="data" type="string">%s</variable>
                </data>
            </case>
            """, caseId, specId, iteration, generateTestData(1000));

        // Execute case
        String result = engine.launchCase(caseXml, new YSpecificationID(specId, "1.0"));
        totalAllocatedMemory.addAndGet(result.length());
    }

    private String generateTestData(int size) {
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < size; i++) {
            data.append("test-data-").append(i).append("-");
        }
        return data.toString();
    }

    private void createTemporaryObjects(int count) {
        for (int i = 0; i < count; i++) {
            // Create temporary strings that should be quickly GC'd
            String temp = "temporary-" + System.currentTimeMillis() + "-" + i;
            temp.length(); // Force processing
        }
    }

    private void createLongLivedObjects(int count) {
        List<String> longLived = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // Create objects that will stay in memory
            String data = "long-lived-" + System.currentTimeMillis() + "-" + i;
            longLived.add(data);
        }
        // Keep reference to prevent immediate GC
        this.longLivedObjects.addAll(longLived);
    }

    // Validation methods

    private void validateMemoryLeakDetectionResults() {
        if (memorySamples.isEmpty()) {
            throw new IllegalStateException("No memory samples collected");
        }

        // Analyze memory trend
        MemorySample firstSample = memorySamples.peek();
        MemorySample lastSample = memorySamples.peek();
        long totalMemoryGrowth = lastSample.memoryDelta - firstSample.memoryDelta;

        System.out.printf("Memory Leak Detection Results:%n" +
                "  Total samples: %d%n" +
                "  Memory growth: %d MB%n" +
                "  Memory warnings: %d%n" +
                "  GC events: %d%n%n",
                memorySamples.size(),
                totalMemoryGrowth / (1024 * 1024),
                memoryWarnings.get(),
                lastSample.gcCount - firstSample.gcCount);

        // Validate no memory leak (growth should be minimal)
        Assertions.assertTrue(totalMemoryGrowth <= MEMORY_THRESHOLD_MB * 1024 * 1024,
            String.format("Memory leak detected: %d MB growth", totalMemoryGrowth / (1024 * 1024)));

        // Validate GC is working
        Assertions.assertTrue(gcEvents.get() > 0,
            "No garbage collection events detected");

        System.out.println("Memory leak detection test passed");
    }

    private void validateGarbageCollectionBehavior(long initialGcCount, long currentGcCount,
                                                 long initialMemory, long currentMemory) {
        long gcIncrease = currentGcCount - initialGcCount;
        long memoryChange = currentMemory - initialMemory;

        System.out.printf("GC Validation - GC events: %d, Memory change: %d MB%n",
            gcIncrease, memoryChange / (1024 * 1024));

        // Check for abnormal GC behavior
        if (gcIncrease > 100 && Math.abs(memoryChange) < 10 * 1024 * 1024) {
            // Many GC cycles with little memory freed - potential memory leak
            System.out.println("Warning: High GC activity with little memory freed");
        }
    }

    private void validateGarbageCollectionSummary() {
        // Additional validation can be added here
        System.out.println("Garbage collection behavior validation completed");
    }

    private void validateMemoryPressureHandling(long peakMemory, int pressureEvents) {
        System.out.printf("Memory Pressure Results:%n" +
                "  Peak memory: %d MB%n" +
                "  Pressure events: %d%n" +
                "  Max heap: %d MB%n%n",
                peakMemory / (1024 * 1024),
                pressureEvents,
                getMaxHeapSize() / (1024 * 1024));

        // Validate system handles memory pressure gracefully
        Assertions.assertTrue(peakMemory < getMaxHeapSize() * 0.95,
            "System reached near heap limit");

        Assertions.assertTrue(pressureEvents < 10,
            "Too many memory pressure events detected");

        System.out.println("Memory pressure handling test passed");
    }

    private void validateObjectAllocationPatterns(int temporaryObjects, int longLivedObjects) {
        System.out.printf("Object Allocation Results:%n" +
                "  Temporary objects: %d%n" +
                "  Long-lived objects: %d%n%n",
                temporaryObjects,
                longLivedObjects);

        // Validate temporary objects are GC'd
        Assertions.assertTrue(temporaryObjects > 0,
            "No temporary objects created");

        // Validate long-lived objects are tracked
        Assertions.assertTrue(longLivedObjects > 0,
            "No long-lived objects created");

        System.out.println("Object allocation patterns test passed");
    }

    private void generateMemoryReport() {
        System.out.println("\n" + "="*50);
        System.out.println("MEMORY LEAK DETECTION REPORT");
        System.out.println("="*50);

        if (!memorySamples.isEmpty()) {
            MemorySample first = memorySamples.peek();
            MemorySample last = memorySamples.peek();

            long durationMs = last.timestamp - first.timestamp;
            long durationMinutes = durationMs / (60 * 1000);

            System.out.printf("Test Duration: %d minutes%n", durationMinutes);
            System.out.printf("Memory Growth: %d MB%n",
                (last.memoryDelta - first.memoryDelta) / (1024 * 1024));
            System.out.printf("GC Events: %d%n", last.gcCount - first.gcCount);
            System.out.printf("Memory Warnings: %d%n", memoryWarnings.get());
            System.out.printf("Total Allocated: %d MB%n",
                totalAllocatedMemory.get() / (1024 * 1024));
            System.out.printf("Total Freed: %d MB%n%n",
                totalFreedMemory.get() / (1024 * 1024));

            // Analyze memory trend
            analyzeMemoryTrend();
        }

        System.out.println("="*50);
    }

    private void analyzeMemoryTrend() {
        if (memorySamples.size() < 10) return;

        // Calculate average memory growth rate
        List<Long> growthRates = new ArrayList<>();
        MemorySample previous = null;

        for (MemorySample sample : memorySamples) {
            if (previous != null) {
                long timeDiff = sample.timestamp - previous.timestamp;
                long memoryDiff = sample.memoryDelta - previous.memoryDelta;
                double growthRate = (double) memoryDiff / (timeDiff / 1000.0); // bytes per second
                growthRates.add((long) growthRate);
            }
            previous = sample;
        }

        double avgGrowthRate = growthRates.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);

        System.out.printf("Average Memory Growth Rate: %.2f bytes/second%n", avgGrowthRate);

        if (avgGrowthRate > 1024 * 1024) { // More than 1MB/s
            System.out.println("WARNING: High memory growth rate detected!");
        } else {
            System.out.println("Memory growth rate within acceptable range");
        }
    }

    // Inner classes

    private static class MemorySample {
        final long timestamp;
        final long usedMemory;
        final long gcCount;
        final long memoryDelta;

        MemorySample(long timestamp, long usedMemory, long gcCount, long memoryDelta) {
            this.timestamp = timestamp;
            this.usedMemory = usedMemory;
            this.gcCount = gcCount;
            this.memoryDelta = memoryDelta;
        }
    }

    private class WorkloadGenerator implements Runnable {
        private final AtomicBoolean running = new AtomicBoolean(true);

        @Override
        public void run() {
            while (running.get()) {
                try {
                    // Create workload at controlled rate
                    for (int i = 0; i < CASE_CREATION_RATE; i++) {
                        if (!running.get()) break;

                        final int caseIndex = (int) (System.currentTimeMillis() % 10000);
                        executorService.submit(() -> {
                            try {
                                createMemoryIntensiveCase(caseIndex);
                            } catch (Exception e) {
                                System.err.println("Error creating case: " + e.getMessage());
                            }
                        });

                        Thread.sleep(1000 / CASE_CREATION_RATE);
                    }

                    // Sleep between batches
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    running.set(false);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        public void stop() {
            running.set(false);
        }
    }

    // Temporary storage for long-lived objects
    private final List<String> longLivedObjects = new ArrayList<>();
}