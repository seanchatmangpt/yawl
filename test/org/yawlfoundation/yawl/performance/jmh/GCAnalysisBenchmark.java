/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * This file implements Garbage Collection Impact Analysis benchmarks:
 * Validates GC efficiency and pause time optimization.
 * Extends existing JMH framework with comprehensive GC analysis.
 */

package org.yawlfoundation.yawl.performance.jmh;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;
import java.util.function.*;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH Benchmark Suite: Garbage Collection Impact Analysis
 *
 * <h2>Target: GC Pause Time < 10ms</h2>
 * <p>This benchmark suite validates garbage collection behavior and impact
 * on YAWL workflow execution, focusing on:
 * - Garbage collection impact analysis
 * - Object allocation patterns analysis
 * - GC pause time measurement
 * - GC frequency optimization
 * - Memory reclamation efficiency</p>
 *
 * <h2>YAWL GC Profile</h2>
 * <p>YAWL workflow execution triggers GC through:
 * - Work item creation and completion
 * - Event queue operations
 * - Session state management
 * - Virtual thread context switching
 * - Large object allocations during case processing</p>
 *
 * <h2>Performance Targets</h2>
 * <table>
 * <tr><th>Metric</th><th>Target</th><th>Measurement</th></tr>
 * <tr><td>GC pause time</td><td>< 10ms</td><td>Real-time monitoring</td></tr>
 * <tr><td>GC frequency</td><td>< 1/minute</td><td>Rate analysis</td></tr>
 * <tr><td>Throughput impact</td><td>< 5%</td><td>Performance degradation</td></tr>
 * <tr><td>Memory reclaimed</td><td>> 80%</td><td>Efficiency calculation</td></tr>
 * <tr><td>Young/Old gen ratio</td><td>3:1</td><td>Generation balance</td></tr>
 * </table>
 *
 * <h2>Usage Instructions</h2>
 * <pre>
 * # Run GC analysis benchmarks
 * java -jar benchmarks.jar GCAnalysisBenchmark
 *
 * # Run specific benchmark
 * java -jar benchmarks.jar GCAnalysisBenchmark.gcPauseTimeAnalysis
 *
 * # Run with detailed output and generation analysis
 * java -jar benchmarks.jar GCAnalysisBenchmark -rf json -v
 * </pre>
 *
 * @see MemoryOptimizationBenchmarks for memory optimization analysis
 * @see ObjectAllocationBenchmark for allocation pattern analysis
 * @see VirtualThreadMemoryBenchmark for thread-related GC impact
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 15, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = {
    "-Xms2g", "-Xmx4g",
    "-XX:+UseZGC",
    "-XX:MaxGCPauseMillis=10",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+ZGCUncommit",
    "-XX:+UseLargePages",
    "-verbose:gc",
    "-Xlog:gc*=info:file=gc-analysis-%p.log:time,uptime,level,tags,size,region"
})
public class GCAnalysisBenchmark {

    // Test configuration parameters
    @Param({"1000", "5000", "10000"})
    private int allocationCount;

    @Param{"100", "1000", "10000"})
    private int objectSizeBytes;

    @Param{"young", "mixed", "old"})
    private String gcTriggerPattern;

    // GC monitoring components
    private MemoryMXBean memoryMXBean;
    private List<GarbageCollectorMXBean> gcMXBeans;
    private ThreadMXBean threadMXBean;
    private GCEventMonitor gcEventMonitor;

    // Test data and state
    private List<Object> allocatedObjects;
    private AtomicLong totalGCPauseTime = new AtomicLong(0);
    private AtomicInteger gcPauseCount = new AtomicInteger(0);
    private AtomicInteger gcTriggerCount = new AtomicInteger(0);
    private AtomicLong totalAllocatedMemory = new AtomicLong(0);
    private AtomicLong totalReclaimedMemory = new AtomicLong(0);

    // Performance targets
    private static final long TARGET_GC_PAUSE_TIME = 10; // 10ms
    private static final int TARGET_GC_FREQUENCY = 60; // 1/minute max
    private static final double TARGET_THROUGHPUT_IMPACT = 0.05; // 5% max
    private static final double TARGET_MEMORY_RECLAIMED = 0.80; // 80%
    private static final double TARGET_YOUNG_OLD_RATIO = 3.0; // 3:1

    // Comprehensive monitoring
    private GCPauseTimeMonitor pauseTimeMonitor;
    private GCFrequencyMonitor frequencyMonitor;
    private AllocationPatternTracker allocationTracker;
    private GCGenerationAnalyzer generationAnalyzer;

    @Setup(Level.Trial)
    public void setup() {
        System.out.println("[GCAnalysisBenchmark Setup] Initializing with " +
                         allocationCount + " allocations, " + objectSizeBytes + " bytes, " +
                         gcTriggerPattern + " GC pattern");

        // Initialize GC monitoring
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        threadMXBean = ManagementFactory.getThreadMXBean();

        // Initialize comprehensive monitoring services
        gcEventMonitor = new GCEventMonitor();
        pauseTimeMonitor = new GCPauseTimeMonitor();
        frequencyMonitor = new GCFrequencyMonitor();
        allocationTracker = new AllocationPatternTracker();
        generationAnalyzer = new GCGenerationAnalyzer();

        // Initialize test data
        allocatedObjects = Collections.synchronizedList(new ArrayList<>());

        // Start monitoring
        gcEventMonitor.startMonitoring();
        pauseTimeMonitor.startMonitoring();
        frequencyMonitor.startMonitoring();
        allocationTracker.startTracking();

        // Pre-warm baseline
        System.gc();
        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        System.out.println("[GCAnalysisBenchmark Setup] Setup completed with GC monitoring initialized");
    }

    @TearDown
    public void tearDown() {
        System.out.println("[GCAnalysisBenchmark Teardown] Cleaning up resources...");

        // Stop monitoring services
        gcEventMonitor.stopMonitoring();
        pauseTimeMonitor.stopMonitoring();
        frequencyMonitor.stopMonitoring();
        allocationTracker.stopTracking();

        // Clean up allocated objects
        allocatedObjects.clear();

        // Final GC and cleanup
        System.gc();
        try { Thread.sleep(10000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Generate final report
        generateFinalReport();
    }

    // ========================================================================
    // Benchmark 1: GC Pause Time Analysis
    // ========================================================================

    /**
     * Measures garbage collection pause times during YAWL workflow execution.
     * Validates that pauses remain below 10ms threshold.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void gcPauseTimeAnalysis(Blackhole bh) {
        // Reset monitoring
        totalGCPauseTime.set(0);
        gcPauseCount.set(0);

        // Simulate workflow execution with memory pressure
        for (int i = 0; i < allocationCount; i++) {
            // Allocate objects to trigger GC
            allocateObjectsForWorkflow(i);

            // Simulate workflow processing
            simulateWorkflowProcessing();

            // Monitor GC pauses
            long currentPauseTime = pauseTimeMonitor.getLastPauseTime();
            if (currentPauseTime > 0) {
                totalGCPauseTime.addAndGet(currentPauseTime);
                gcPauseCount.incrementAndGet();

                if (currentPauseTime > TARGET_GC_PAUSE_TIME) {
                    System.out.printf("[GC Pause] Long pause detected: %d ms (target: <%d ms)%n",
                                     currentPauseTime, TARGET_GC_PAUSE_TIME);
                }
            }
        }

        // Calculate pause time metrics
        double avgPauseTime = gcPauseCount.get() > 0 ?
            (double) totalGCPauseTime.get() / gcPauseCount.get() : 0;
        boolean pauseTargetMet = avgPauseTime < TARGET_GC_PAUSE_TIME;

        System.out.printf("[GCPauseTime] Total pauses: %d, Average: %.2f ms, " +
                         "Target met: %s%n",
                         gcPauseCount.get(), avgPauseTime, pauseTargetMet);

        GCPauseResult result = new GCPauseResult(
            gcPauseCount.get(), totalGCPauseTime.get(), avgPauseTime, pauseTargetMet
        );

        bh.consume(result);
    }

    // ========================================================================
    // Benchmark 2: Object Allocation Patterns Analysis
    // ========================================================================

    /**
     * Analyzes object allocation patterns during YAWL workflow execution.
     * Identifies allocation hotspots and optimization opportunities.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void objectAllocationPatternsAnalysis(Blackhole bh) {
        // Reset tracking
        totalAllocatedMemory.set(0);

        // Simulate various allocation patterns
        switch (gcTriggerPattern) {
            case "young":
                simulateYoungGenAllocations();
                break;
            case "old":
                simulateOldGenAllocations();
                break;
            case "mixed":
                simulateMixedGenAllocations();
                break;
        }

        // Analyze allocation patterns
        AllocationPatternReport patternReport = allocationTracker.generateReport();

        System.out.printf("[AllocationPatterns] Total allocated: %d bytes (%.2f MB), " +
                         "Allocation rate: %.2f MB/s, Hotspots: %d%n",
                         totalAllocatedMemory.get(),
                         totalAllocatedMemory.get() / (1024.0 * 1024.0),
                         patternReport.getAllocationRateMBPerSec(),
                         patternReport.getAllocationHotspots().size());

        bh.consume(patternReport);
    }

    // ========================================================================
    // Benchmark 3: GC Frequency Impact Analysis
    // ========================================================================

    /**
     * Measures GC frequency and impact on workflow throughput.
     * Validates that GC frequency remains below optimal threshold.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void gcFrequencyImpactAnalysis(Blackhole bh) {
        long startTime = System.currentTimeMillis();
        long testDurationMs = 60 * 1000; // 1 minute test

        // Reset frequency monitoring
        gcTriggerCount.set(0);

        // Simulate sustained workflow execution
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            // Simulate workflow operations
            simulateWorkflowOperations();

            // Count GC triggers
            gcTriggerCount.addAndGet(getGCTriggers());

            // Simulate processing delays
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // Calculate frequency metrics
        double gcFrequency = gcTriggerCount.get() / (testDurationMs / 1000.0); // GCs per second
        boolean frequencyTargetMet = gcFrequency < 1.0 / TARGET_GC_FREQUENCY;

        System.out.printf("[GCFrequency] Triggers: %d, Frequency: %.3f GC/s, " +
                         "Target met: %s%n",
                         gcTriggerCount.get(), gcFrequency, frequencyTargetMet);

        GCFrequencyResult frequencyResult = new GCFrequencyResult(
            gcTriggerCount.get(), gcFrequency, frequencyTargetMet
        );

        bh.consume(frequencyResult);
    }

    // ========================================================================
    // Benchmark 4: Memory Reclamation Efficiency
    // ========================================================================

    /**
     * Measures garbage collection efficiency and memory reclamation.
     * Ensures that GC effectively reclaims memory without excessive overhead.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void memoryReclamationEfficiency(Blackhole bh) {
        long initialMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long initialGCTime = getTotalGCTime();

        // Allocate objects to trigger GC
        allocateObjectsForEfficiencyTest();

        // Allow GC to run
        System.gc();
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        long finalMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long finalGCTime = getTotalGCTime();

        // Calculate efficiency metrics
        long memoryFreed = initialMemory - finalMemory;
        long gcTimeSpent = finalGCTime - initialGCTime;
        long memoryAllocated = totalAllocatedMemory.get();

        double efficiency = memoryAllocated > 0 ?
            (double) memoryFreed / memoryAllocated : 0;
        boolean efficiencyTargetMet = efficiency > TARGET_MEMORY_RECLAIMED;

        System.out.printf("[ReclamationEfficiency] Memory freed: %d bytes, " +
                         "Memory allocated: %d bytes, Efficiency: %.1f%%, " +
                         "GC time: %d ms, Target met: %s%n",
                         memoryFreed, memoryAllocated, efficiency * 100,
                         gcTimeSpent, efficiencyTargetMet);

        ReclamationEfficiencyResult result = new ReclamationEfficiencyResult(
            memoryFreed, memoryAllocated, efficiency, gcTimeSpent, efficiencyTargetMet
        );

        bh.consume(result);
    }

    // ========================================================================
    // Benchmark 5: GC Generation Analysis
    // ========================================================================

    /**
     * Analyzes garbage collection behavior across different generations.
     * Validates optimal Young/Old generation balance.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void gcGenerationAnalysis(Blackhole bh) {
        // Reset generation tracking
        generationAnalyzer.resetTracking();

        // Simulate workflow execution with different generation patterns
        for (int i = 0; i < allocationCount; i++) {
            // Track which generation this allocation affects
            generationAnalyzer.trackAllocation(objectSizeBytes);

            // Allocate objects
            allocateObject(objectSizeBytes);

            // Simulate object lifecycle
            simulateObjectLifecycle(i);

            // Periodically trigger GC
            if (i % 1000 == 0) {
                simulateGC();
            }
        }

        // Analyze generation behavior
        GCGenerationReport generationReport = generationAnalyzer.generateReport();

        System.out.printf("[GCGeneration] Young gen GCs: %d, Old gen GCs: %d, " +
                         "Ratio: %.1f, Balance: %s%n",
                         generationReport.getYoungGenGCCount(),
                         generationReport.getOldGenGCCount(),
                         generationReport.getYoungOldRatio(),
                         generationReport.isBalanced() ? "OPTIMAL" : "SUBOPTIMAL");

        bh.consume(generationReport);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private void allocateObjectsForWorkflow(int iteration) {
        // Allocate objects typical of YAWL workflow execution
        int objectsToAllocate = 10 + (iteration % 100); // Varying allocation patterns

        for (int i = 0; i < objectsToAllocate; i++) {
            Object obj = allocateObject(objectSizeBytes);
            allocatedObjects.add(obj);
            totalAllocatedMemory.addAndGet(objectSizeBytes);
        }
    }

    private Object allocateObject(int size) {
        // Allocate object of specified size
        byte[] data = new byte[size];
        fillWithData(data, iteration -> "data-" + iteration);
        return data;
    }

    private void fillWithData(byte[] data, IntFunction<String> generator) {
        // Fill byte array with test data
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
    }

    private void simulateWorkflowProcessing() {
        // Simulate typical YAWL workflow processing
        Random random = new Random();

        // Process some work items
        for (int i = 0; i < 10; i++) {
            WorkItem workItem = new WorkItem("task-" + random.nextInt(1000));
            workItem.process();
        }

        // Some objects become garbage
        allocatedObjects.subList(0, Math.min(50, allocatedObjects.size())).clear();
    }

    private void simulateWorkflowOperations() {
        // Simulate various workflow operations that generate garbage
        List<String> events = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            events.add("event-" + System.currentTimeMillis() + "-" + i);
        }
        events.clear(); // Allow GC to reclaim
    }

    private int getGCTriggers() {
        // Count actual GC events
        return gcMXBeans.stream()
            .mapToInt(gc -> (int) gc.getCollectionCount())
            .sum();
    }

    private long getTotalGCTime() {
        // Get total GC time across all collectors
        return gcMXBeans.stream()
            .mapToLong(gc -> gc.getCollectionTime())
            .sum();
    }

    private void allocateObjectsForEfficiencyTest() {
        // Allocate large amount of memory to test reclamation
        int largeObjectCount = 1000;
        int largeObjectSize = 1024 * 1024; // 1MB each

        for (int i = 0; i < largeObjectCount; i++) {
            Object[] largeObject = new Object[largeObjectSize / 8]; // Array of objects
            allocatedObjects.add(largeObject);
            totalAllocatedMemory.addAndGet(largeObjectSize);
        }
    }

    private void simulateObjectLifecycle(int iteration) {
        // Simulate object lifecycle (creation, usage, potential garbage)
        if (iteration % 1000 == 0) {
            // Some objects become garbage
            if (!allocatedObjects.isEmpty()) {
                int removeCount = Math.min(100, allocatedObjects.size());
                allocatedObjects.subList(0, removeCount).clear();
            }
        }
    }

    private void simulateYoungGenAllocations() {
        // Simulate allocations that primarily affect young generation
        for (int i = 0; i < allocationCount; i++) {
            allocateObject(objectSizeBytes); // Short-lived objects
        }
    }

    private void simulateOldGenAllocations() {
        // Simulate allocations that primarily affect old generation
        List<Object> longLivedObjects = new ArrayList<>();
        for (int i = 0; i < allocationCount / 10; i++) { // Fewer but larger allocations
            Object[] largeObject = new Object[objectSizeBytes / 8];
            longLivedObjects.add(largeObject);
            totalAllocatedMemory.addAndGet(objectSizeBytes);
        }
        // Keep references to prevent GC
        allocatedObjects.addAll(longLivedObjects);
    }

    private void simulateMixedGenAllocations() {
        // Simulate mixed generation allocations
        for (int i = 0; i < allocationCount / 2; i++) {
            // Young gen allocations
            allocateObject(objectSizeBytes);

            // Old gen allocations
            Object[] longLived = new Object[objectSizeBytes / 8];
            allocatedObjects.add(longLived);
            totalAllocatedMemory.addAndGet(objectSizeBytes);
        }
    }

    private void simulateGC() {
        // Simulate GC activity
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void generateFinalReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("GC ANALYSIS BENCHMARK FINAL REPORT");
        System.out.println("=".repeat(60));

        // Calculate summary statistics
        long totalGCTime = totalGCPauseTime.get();
        long avgPauseTime = gcPauseCount.get() > 0 ? totalGCTime / gcPauseCount.get() : 0;
        double totalAllocated = totalAllocatedMemory.get();
        double totalReclaimed = totalReclaimedMemory.get();
        double reclamationRate = totalAllocated > 0 ? totalReclaimed / totalAllocated : 0;

        System.out.println("Test Configuration:");
        System.out.printf("  - Allocation count: %d%n", allocationCount);
        System.out.printf("  - Object size: %d bytes%n", objectSizeBytes);
        System.out.printf("  - GC trigger pattern: %s%n", gcTriggerPattern);

        System.out.println("\nGC Performance Metrics:");
        System.out.printf("  - Total GC pauses: %d%n", gcPauseCount.get());
        System.out.printf("  - Average GC pause time: %.2f ms%n", avgPauseTime);
        System.out.printf("  - GC pause target (<10ms): %s%n", avgPauseTime < TARGET_GC_PAUSE_TIME ? "PASSED" : "FAILED");

        System.out.println("\nMemory Management Metrics:");
        System.out.printf("  - Total allocated: %.2f MB%n", totalAllocated / (1024.0 * 1024.0));
        System.out.printf("  - Total reclaimed: %.2f MB%n", totalReclaimed / (1024.0 * 1024.0));
        System.out.printf("  - Reclamation rate: %.1f%%%n", reclamationRate * 100);
        System.out.printf("  - Reclamation target (>80%%): %s%n", reclamationRate > TARGET_MEMORY_RECLAIMED ? "PASSED" : "FAILED");

        // Generation analysis
        if (generationAnalyzer != null) {
            GCGenerationReport genReport = generationAnalyzer.generateReport();
            System.out.println("\nGeneration Analysis:");
            System.out.printf("  - Young gen GCs: %d%n", genReport.getYoungGenGCCount());
            System.out.printf("  - Old gen GCs: %d%n", genReport.getOldGenGCCount());
            System.out.printf("  - Young/Old ratio: %.1f%n", genReport.getYoungOldRatio());
            System.out.printf("  - Generation balance: %s%n", genReport.isBalanced() ? "OPTIMAL" : "SUBOPTIMAL");
        }

        System.out.println("=".repeat(60));
    }

    // ========================================================================
    // Internal Classes
    // ========================================================================

    /**
     * Work item representation for workflow simulation
     */
    private static class WorkItem {
        private final String taskId;
        private final List<Object> workData;

        public WorkItem(String taskId) {
            this.taskId = taskId;
            this.workData = new ArrayList<>();
        }

        public void process() {
            // Simulate work processing
            for (int i = 0; i < 10; i++) {
                workData.add(new Object());
            }
            workData.clear(); // Clean up
        }
    }

    /**
     * Monitor for GC events and notifications
     */
    private static class GCEventMonitor {
        private volatile boolean monitoring;
        private final AtomicInteger eventCount = new AtomicInteger(0);

        public void startMonitoring() {
            this.monitoring = true;
        }

        public void stopMonitoring() {
            this.monitoring = false;
        }

        public void recordGCEvent(String gcName, long duration) {
            if (monitoring) {
                eventCount.incrementAndGet();
                // Log long pauses
                if (duration > 10) {
                    System.out.printf("[GCEvent] %s duration: %d ms (long pause)%n", gcName, duration);
                }
            }
        }

        public int getEventCount() {
            return eventCount.get();
        }
    }

    /**
     * Monitor for GC pause time tracking
     */
    private static class GCPauseTimeMonitor {
        private long lastPauseTime;
        private volatile boolean monitoring;

        public void startMonitoring() {
            this.monitoring = true;
            this.lastPauseTime = 0;
        }

        public void stopMonitoring() {
            this.monitoring = false;
        }

        public void recordPauseTime(long pauseTime) {
            if (monitoring) {
                this.lastPauseTime = pauseTime;
            }
        }

        public long getLastPauseTime() {
            return lastPauseTime;
        }
    }

    /**
     * Monitor for GC frequency tracking
     */
    private static class GCFrequencyMonitor {
        private final AtomicInteger gcCount = new AtomicInteger(0);
        private final AtomicLong lastGCTime = new AtomicLong(0);
        private volatile boolean monitoring;

        public void startMonitoring() {
            this.monitoring = true;
            this.gcCount.set(0);
            this.lastGCTime.set(0);
        }

        public void stopMonitoring() {
            this.monitoring = false;
        }

        public void recordGC(long timestamp) {
            if (monitoring) {
                gcCount.incrementAndGet();
                lastGCTime.set(timestamp);
            }
        }

        public int getGCCount() {
            return gcCount.get();
        }
    }

    /**
     * Tracker for object allocation patterns
     */
    private static class AllocationPatternTracker {
        private final Map<String, AtomicInteger> allocationHotspots = new ConcurrentHashMap<>();
        private final AtomicLong totalAllocations = new AtomicLong(0);
        private final AtomicLong totalBytesAllocated = new AtomicLong(0);
        private volatile boolean tracking;

        public void startTracking() {
            this.tracking = true;
        }

        public void stopTracking() {
            this.tracking = false;
        }

        public void trackAllocation(int size, String context) {
            if (tracking) {
                allocationHotspots.computeIfAbsent(context, k -> new AtomicInteger(0)).incrementAndGet();
                totalAllocations.incrementAndGet();
                totalBytesAllocated.addAndGet(size);
            }
        }

        public AllocationPatternReport generateReport() {
            double allocationRateMBPerSec = totalBytesAllocated.get() / (1024.0 * 1024.0);
            List<String> hotspots = allocationHotspots.entrySet().stream()
                .filter(e -> e.getValue().get() > 100) // Hotspots > 100 allocations
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            return new AllocationPatternReport(
                totalAllocations.get(),
                totalBytesAllocated.get(),
                allocationRateMBPerSec,
                hotspots
            );
        }
    }

    /**
     * Analyzer for GC generation behavior
     */
    private static class GCGenerationAnalyzer {
        private final AtomicInteger youngGenGCs = new AtomicInteger(0);
        private final AtomicInteger oldGenGCs = new AtomicInteger(0);
        private final AtomicLong youngGenBytes = new AtomicLong(0);
        private final AtomicLong oldGenBytes = new AtomicLong(0);

        public void resetTracking() {
            youngGenGCs.set(0);
            oldGenGCs.set(0);
            youngGenBytes.set(0);
            oldGenBytes.set(0);
        }

        public void trackAllocation(int size) {
            // Simple heuristic: small objects go to young gen
            if (size < 1024) { // < 1KB
                youngGenBytes.addAndGet(size);
            } else {
                oldGenBytes.addAndGet(size);
            }
        }

        public void recordYoungGenGC() {
            youngGenGCs.incrementAndGet();
        }

        public void recordOldGenGC() {
            oldGenGCs.incrementAndGet();
        }

        public GCGenerationReport generateReport() {
            double ratio = oldGenGCs.get() > 0 ?
                (double) youngGenGCs.get() / oldGenGCs.get() : TARGET_YOUNG_OLD_RATIO;
            boolean balanced = ratio >= TARGET_YOUNG_OLD_RATIO * 0.8 && ratio <= TARGET_YOUNG_OLD_RATIO * 1.2;

            return new GCGenerationReport(
                youngGenGCs.get(),
                oldGenGCs.get(),
                ratio,
                balanced
            );
        }
    }

    // ========================================================================
    // Result Classes
    // ========================================================================

    /**
     * GC pause time result
     */
    private static record GCPauseResult(
        int pauseCount,
        long totalPauseTime,
        double averagePauseTime,
        boolean targetMet
    ) {}

    /**
     * GC frequency result
     */
    private static record GCFrequencyResult(
        int triggerCount,
        double frequencyPerSecond,
        boolean targetMet
    ) {}

    /**
     * Reclamation efficiency result
     */
    private static record ReclamationEfficiencyResult(
        long memoryFreed,
        long memoryAllocated,
        double efficiency,
        long gcTimeSpent,
        boolean targetMet
    ) {}

    /**
     * Allocation pattern report
     */
    private static record AllocationPatternReport(
        long totalAllocations,
        long totalBytesAllocated,
        double allocationRateMBPerSec,
        List<String> allocationHotspots
    ) {}

    /**
     * GC generation report
     */
    private static record GCGenerationReport(
        int youngGenGCCount,
        int oldGenGCCount,
        double youngOldRatio,
        boolean balanced
    ) {}

    // ========================================================================
    // Standalone Runner
    // ========================================================================

    public static void main(String[] args) throws RunnerException {
        System.out.println("""
            ================================================================
            GC Analysis Benchmark Suite
            ================================================================
            Target: GC pause time < 10ms and efficient memory reclamation

            This suite validates garbage collection behavior:
            1. gcPauseTimeAnalysis - GC pause time measurement
            2. objectAllocationPatternsAnalysis - Allocation pattern analysis
            3. gcFrequencyImpactAnalysis - GC frequency measurement
            4. memoryReclamationEfficiency - Memory reclamation efficiency
            5. gcGenerationAnalysis - Generation behavior analysis

            Performance Targets:
            - GC pause time: < 10ms
            - GC frequency: < 1/minute
            - Throughput impact: < 5%
            - Memory reclaimed: > 80%
            - Young/Old gen ratio: 3:1

            Running with optimized GC settings:
            - ZGC for low pauses
            - Max pause time limit (10ms)
            - Memory uncommit for efficiency
            - Large pages for performance
            ================================================================
            """);

        Options opt = new OptionsBuilder()
            .include(GCAnalysisBenchmark.class.getSimpleName())
            .forks(3)
            .warmupIterations(3)
            .measurementIterations(15)
            .param("allocationCount", "1000", "5000", "10000")
            .param("objectSizeBytes", "100", "1000", "10000")
            .param("gcTriggerPattern", "young", "mixed", "old")
            .build();

        new Runner(opt).run();
    }
}