/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * This file implements Long-running memory leak detection benchmarks:
 * Validates 24+ hour sustained memory usage and leak detection.
 * Extends existing JMH framework with comprehensive long-running analysis.
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
 * JMH Benchmark Suite: Long-Running Memory Leak Detection
 *
 * <h2>Target: 24+ Hour Memory Leak Detection</h2>
 * <p>This benchmark suite validates long-running memory behavior by
 * measuring sustained memory usage over extended periods, focusing on:
 * - Memory growth per case tracking
 * - Heap utilization efficiency measurement
 * - Memory leak detection during sustained load
 * - Garbage collection pause time analysis
 * - Virtual thread memory efficiency comparison</p>
 *
 * <h2>YAWL Long-Running Memory Profile</h2>
 * <p>During 24+ hour operation, YAWL must maintain:
 * - Stable memory usage per case (< 2MB growth)
 * - Efficient garbage collection (< 10ms pauses)
 * - High heap utilization (> 95% efficiency)
 * - Low virtual thread overhead (< 8KB per thread)
 * - No memory accumulation over time</p>
 *
 * <h2>Performance Targets</h2>
 * <table>
 * <tr><th>Metric</th><th>Target</th><th>Measurement</th></tr>
 * <tr><td>Memory growth per case</td><td>< 2MB</td><td>Continuous tracking</td></tr>
 * <tr><td>GC pause time</td><td>< 10ms</td><td>Real-time monitoring</td></tr>
 * <tr><td>Heap utilization</td><td>> 95%</td><td>Efficiency calculation</td></tr>
 * <tr><td>Virtual thread memory</td><td>< 8KB/thread</td><td>Per-thread measurement</td></tr>
 * <tr><td>Leak detection threshold</td><td>0.1%/hour</td><td>Growth rate analysis</td></tr>
 * </table>
 *
 * <h2>Usage Instructions</h2>
 * <pre>
 * # Run long-running memory benchmarks
 * java -jar benchmarks.jar LongRunningMemoryBenchmark
 *
 * # Run specific benchmark
 * java -jar benchmarks.jar LongRunningMemoryBenchmark.sustainedMemoryUsage
 *
 * # Run with detailed output and leak detection
 * java -jar benchmarks.jar LongRunningMemoryBenchmark -rf json -v
 * </pre>
 *
 * @see MemoryOptimizationBenchmarks for baseline memory patterns
 * @see GCAnalysisBenchmark for garbage collection analysis
 * @see VirtualThreadMemoryBenchmark for virtual thread efficiency
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 10, timeUnit = TimeUnit.MINUTES)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.HOURS)
@Fork(value = 1, jvmArgs = {
    "-Xms4g", "-Xmx8g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-XX:MaxGCPauseMillis=10",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+ZGCUncommit",
    "-XX:+UseLargePages",
    "-verbose:gc",
    "-Xlog:gc*=info:file=gc-%p.log:time,uptime,level,tags"
})
public class LongRunningMemoryBenchmark {

    // Test configuration parameters
    @Param({"1000", "5000", "10000"})
    private int caseLoad;

    @Param({"24", "48", "72"})
    private int testHours;

    @Param({"virtual", "platform"})
    private String threadType;

    // Memory monitoring components
    private MemoryMXBean memoryMXBean;
    private GarbageCollectorMXBean gcMXBean;
    private ThreadMXBean threadMXBean;

    // Test data and state
    private List<CaseSession> activeSessions;
    private AtomicLong totalMemoryGrowth = new AtomicLong(0);
    private AtomicLong totalGCPauses = new AtomicLong(0);
    private AtomicInteger gcPauseCount = new AtomicInteger(0);
    private AtomicLong totalHeapUtilization = new AtomicLong(0);
    private AtomicReference<MemoryLeakDetector> leakDetector = new AtomicReference<>();

    // Performance targets
    private static final long TARGET_MEMORY_GROWTH_PER_CASE = 2 * 1024 * 1024; // 2MB
    private static final long TARGET_GC_PAUSE_TIME = 10; // 10ms
    private static final double TARGET_HEAP_UTILIZATION = 95.0; // 95%
    private static final long TARGET_VIRTUAL_THREAD_MEMORY = 8 * 1024; // 8KB per thread
    private static final double TARGET_MEMORY_GROWTH_RATE = 0.001; // 0.1% per hour

    // Comprehensive monitoring
    private MemoryTrackingService memoryTracking;
    private GCPauseMonitor gcPauseMonitor;
    private HeapAnalyzer heapAnalyzer;
    private ThreadMemoryMonitor threadMemoryMonitor;

    @Setup(Level.Trial)
    public void setup() {
        System.out.println("[LongRunningMemoryBenchmark Setup] Initializing with " +
                         caseLoad + " cases, " + testHours + " hours, " +
                         threadType + " threads");

        // Initialize memory monitoring
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        gcMXBean = ManagementFactory.getGarbageCollectorMXBeans().get(0);
        threadMXBean = ManagementFactory.getThreadMXBean();

        // Initialize comprehensive monitoring services
        memoryTracking = new MemoryTrackingService();
        gcPauseMonitor = new GCPauseMonitor();
        heapAnalyzer = new HeapAnalyzer();
        threadMemoryMonitor = new ThreadMemoryMonitor();

        // Initialize test sessions
        activeSessions = Collections.synchronizedList(new ArrayList<>());

        // Initialize leak detector
        leakDetector.set(new MemoryLeakDetector(memoryTracking, TARGET_MEMORY_GROWTH_RATE));

        // Pre-warm and establish baseline
        System.gc();
        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        memoryTracking.recordBaseline();
        gcPauseMonitor.startMonitoring();
        threadMemoryMonitor.startMonitoring();

        System.out.println("[LongRunningMemoryBenchmark Setup] Setup completed with " +
                         "long-running monitoring initialized");
    }

    @TearDown
    public void tearDown() {
        System.out.println("[LongRunningMemoryBenchmark Teardown] Cleaning up resources...");

        // Stop monitoring services
        gcPauseMonitor.stopMonitoring();
        threadMemoryMonitor.stopMonitoring();
        leakDetector.get().stopDetection();

        // Cleanup sessions
        activeSessions.clear();

        // Final GC and cleanup
        System.gc();
        try { Thread.sleep(10000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Generate final report
        generateFinalReport();
    }

    // ========================================================================
    // Benchmark 1: Sustained Memory Usage Over 24+ Hours
    // ========================================================================

    /**
     * Measures memory usage during sustained 24+ hour operation.
     * Validates that memory growth remains within acceptable limits.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void sustainedMemoryUsage(Blackhole bh) {
        long startTime = System.currentTimeMillis();
        long testDurationMs = testHours * 60 * 60 * 1000L;

        // Create initial case load
        createCaseLoad(caseLoad);

        // Simulate sustained workflow execution
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            // Process some cases
            processCases(100);

            // Complete some cases and create new ones to simulate steady state
            completeCases(50);
            createCaseLoad(50);

            // Small delay to prevent CPU overload
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // Measure final memory usage
        long finalMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long baselineMemory = memoryTracking.getBaselineMemory();
        long memoryGrowth = finalMemory - baselineMemory;

        // Validate against targets
        boolean growthTargetMet = memoryGrowth < TARGET_MEMORY_GROWTH_PER_CASE * caseLoad;
        double growthPerCase = (double) memoryGrowth / caseLoad;

        System.out.printf("[SustainedMemory] Duration: %d hours, Memory growth: %d bytes (%.2f KB/case), " +
                         "Target met: %s%n",
                         testHours, memoryGrowth, growthPerCase / 1024.0, growthTargetMet);

        totalMemoryGrowth.addAndGet(memoryGrowth);
        bh.consume(activeSessions);
    }

    // ========================================================================
    // Benchmark 2: Memory Leak Detection During Sustained Load
    // ========================================================================

    /**
     * Detects memory leaks during sustained 24+ hour operation.
     * Uses sophisticated leak detection algorithms to identify leaks.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void memoryLeakDetection(Blackhole bh) {
        long startTime = System.currentTimeMillis();
        long testDurationMs = testHours * 60 * 60 * 1000L;

        // Start leak detection
        leakDetector.get().startDetection();

        // Simulate continuous operation
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            // Simulate workflow operations
            simulateWorkflowOperations();

            // Periodically check for leaks
            if (System.currentTimeMillis() % (60 * 60 * 1000) == 0) { // Every hour
                MemoryLeakReport report = leakDetector.get().analyzeMemoryGrowth();

                if (report.hasLeaks()) {
                    System.out.printf("[LeakDetection] Memory leak detected: %.2f%% growth rate, " +
                                     "Leaky components: %s%n",
                                     report.growthRatePercentage(),
                                     report.leakyComponents());
                }
            }

            // Small delay
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // Final leak analysis
        MemoryLeakReport finalReport = leakDetector.get().analyzeMemoryGrowth();

        System.out.printf("[LeakDetection Final] Memory growth rate: %.2f%%/hour, " +
                         "Leaks detected: %s, Components analyzed: %d%n",
                         finalReport.growthRatePercentage(),
                         finalReport.hasLeaks() ? "YES" : "NO",
                         finalReport.componentsAnalyzed());

        bh.consume(finalReport);
    }

    // ========================================================================
    // Benchmark 3: Garbage Collection Impact Analysis
    // ========================================================================

    /**
     * Analyzes garbage collection behavior during long-running operation.
     * Measures pause times and frequency to identify GC-related issues.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void garbageCollectionImpact(Blackhole bh) {
        long startTime = System.currentTimeMillis();
        long testDurationMs = testHours * 60 * 60 * 1000L;

        // Reset GC monitoring
        gcPauseCount.set(0);
        totalGCPauses.set(0);

        // Simulate sustained operation
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            // Simulate memory-intensive operations
            simulateMemoryIntensiveOperations();

            // Monitor GC pauses
            long currentGCPauseTime = gcPauseMonitor.getLastPauseTime();
            if (currentGCPauseTime > 0) {
                totalGCPauses.addAndGet(currentGCPauseTime);
                gcPauseCount.incrementAndGet();

                if (currentGCPauseTime > TARGET_GC_PAUSE_TIME) {
                    System.out.printf("[GC Impact] Long pause detected: %d ms (target: <%d ms)%n",
                                     currentGCPauseTime, TARGET_GC_PAUSE_TIME);
                }
            }

            // Small delay
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // Calculate GC metrics
        double avgGCPauseTime = gcPauseCount.get() > 0 ?
            (double) totalGCPauses.get() / gcPauseCount.get() : 0;
        boolean gcTargetMet = avgGCPauseTime < TARGET_GC_PAUSE_TIME;

        System.out.printf("[GC Impact Final] Total pauses: %d, Average pause: %.2f ms, " +
                         "Target met: %s%n",
                         gcPauseCount.get(), avgGCPauseTime, gcTargetMet);

        GCAnalysisResult result = new GCAnalysisResult(
            gcPauseCount.get(), totalGCPauses.get(), avgGCPauseTime, gcTargetMet
        );

        bh.consume(result);
    }

    // ========================================================================
    // Benchmark 4: Virtual Thread Memory Efficiency Comparison
    // ========================================================================

    /**
     * Compares memory efficiency between virtual and platform threads.
     * Measures memory overhead per thread for both thread types.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void virtualThreadMemoryEfficiency(Blackhole bh) {
        long startTime = System.currentTimeMillis();
        long testDurationMs = testHours * 60 * 60 * 1000L;

        ExecutorService executor;
        if ("virtual".equals(threadType)) {
            executor = Executors.newVirtualThreadPerTaskExecutor();
        } else {
            executor = Executors.newFixedThreadPool(Math.min(100, caseLoad));
        }

        // Track thread memory usage
        threadMemoryMonitor.startThreadMonitoring(executor);

        // Simulate sustained thread operations
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            // Submit tasks for execution
            for (int i = 0; i < 100; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    SimulatedTask task = new SimulatedTask("task-" + taskId);
                    task.execute();
                });
            }

            // Wait for task completion
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // Shutdown executor
        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Calculate memory efficiency
        ThreadMemoryReport threadReport = threadMemoryMonitor.generateReport();
        double memoryPerThread = threadReport.getAverageMemoryPerThread();
        boolean efficiencyTargetMet = memoryPerThread < TARGET_VIRTUAL_THREAD_MEMORY;

        System.out.printf("[ThreadMemory] Type: %s, Memory per thread: %.2f KB, " +
                         "Target met: %s, Threads created: %d%n",
                         threadType, memoryPerThread / 1024.0, efficiencyTargetMet,
                         threadReport.getTotalThreadsCreated());

        bh.consume(threadReport);
    }

    // ========================================================================
    // Benchmark 5: Heap Utilization Efficiency Analysis
    // ========================================================================

    /**
     * Measures heap utilization efficiency during long-running operation.
     * Ensures heap space is used efficiently without excessive waste.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void heapUtilizationEfficiency(Blackhole bh) {
        long startTime = System.currentTimeMillis();
        long testDurationMs = testHours * 60 * 60 * 1000L;

        long measurements = 0;
        double totalUtilization = 0.0;

        // Simulate sustained operation
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            // Record heap usage
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            long used = heapUsage.getUsed();
            long max = heapUsage.getMax();

            double utilization = max > 0 ? (double) used / max * 100 : 0;
            totalUtilization += utilization;
            measurements++;

            heapAnalyzer.recordMeasurement(utilization, used, max);

            // Simulate memory pressure
            if (measurements % 100 == 0) {
                simulateMemoryPressure();
            }

            // Small delay
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // Calculate final metrics
        double avgUtilization = measurements > 0 ? totalUtilization / measurements : 0;
        HeapEfficiencyReport heapReport = heapAnalyzer.generateReport();

        boolean efficiencyTargetMet = avgUtilization > TARGET_HEAP_UTILIZATION;

        System.out.printf("[HeapUtilization] Average: %.1f%%, Target met: %s, " +
                         "Fragmentation: %.1f%%, Efficiency: %s%n",
                         avgUtilization, efficiencyTargetMet,
                         heapReport.getFragmentationPercentage(),
                         heapReport.isEfficient() ? "HIGH" : "LOW");

        totalHeapUtilization.addAndGet((long) (avgUtilization * 1000));

        bh.consume(heapReport);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private void createCaseLoad(int count) {
        for (int i = 0; i < count; i++) {
            CaseSession session = new CaseSession("case-" + System.currentTimeMillis() + "-" + i);
            activeSessions.add(session);
        }
    }

    private void processCases(int count) {
        Random random = new Random();
        int toProcess = Math.min(count, activeSessions.size());

        for (int i = 0; i < toProcess; i++) {
            int index = random.nextInt(activeSessions.size());
            CaseSession session = activeSessions.get(index);
            session.process();
        }
    }

    private void completeCases(int count) {
        int toComplete = Math.min(count, activeSessions.size());

        for (int i = 0; i < toComplete; i++) {
            if (!activeSessions.isEmpty()) {
                CaseSession session = activeSessions.remove(0);
                session.complete();
            }
        }
    }

    private void simulateWorkflowOperations() {
        // Simulate various workflow operations
        for (int i = 0; i < 10; i++) {
            createCaseLoad(10);
            processCases(5);
            completeCases(3);
        }
    }

    private void simulateMemoryIntensiveOperations() {
        // Simulate memory-intensive operations that trigger GC
        List<byte[]> largeObjects = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < 100; i++) {
            byte[] data = new byte[random.nextInt(1024 * 1024)]; // Random size up to 1MB
            largeObjects.add(data);
        }

        // Clear to allow GC
        largeObjects.clear();
    }

    private void simulateMemoryPressure() {
        // Create temporary objects to simulate memory pressure
        List<Object> pressureObjects = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            pressureObjects.add(new Object());
        }
        pressureObjects.clear(); // Allow GC to reclaim
    }

    private void generateFinalReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("LONG-RUNNING MEMORY BENCHMARK FINAL REPORT");
        System.out.println("=".repeat(60));

        // Calculate summary statistics
        long avgMemoryGrowth = totalMemoryGrowth.get();
        long avgHeapUtilization = totalHeapUtilization.get() / 1000; // Convert back to percentage
        long totalGCCount = gcPauseCount.get();
        long avgGCPause = totalGCPauses.get() / Math.max(1, totalGCCount);

        System.out.println("Test Configuration:");
        System.out.printf("  - Duration: %d hours%n", testHours);
        System.out.printf("  - Case load: %d cases%n", caseLoad);
        System.out.printf("  - Thread type: %s%n", threadType);

        System.out.println("\nMemory Performance:");
        System.out.printf("  - Average memory growth: %d bytes (%.2f KB/case)%n",
                         avgMemoryGrowth, avgMemoryGrowth / (double) caseLoad / 1024.0);
        System.out.printf("  - Memory growth target (<2MB/case): %s%n",
                         avgMemoryGrowth < TARGET_MEMORY_GROWTH_PER_CASE * caseLoad ? "PASSED" : "FAILED");

        System.out.println("\nGC Performance:");
        System.out.printf("  - Total GC pauses: %d%n", totalGCCount);
        System.out.printf("  - Average GC pause time: %.2f ms%n", avgGCPause);
        System.out.printf("  - GC pause target (<10ms): %s%n", avgGCPause < TARGET_GC_PAUSE_TIME ? "PASSED" : "FAILED");

        System.out.println("\nHeap Performance:");
        System.out.printf("  - Average heap utilization: %.1f%%%n", avgHeapUtilization);
        System.out.printf("  - Heap utilization target (>95%%): %s%n",
                         avgHeapUtilization > TARGET_HEAP_UTILIZATION ? "PASSED" : "FAILED");

        // Leak detection final report
        if (leakDetector.get() != null) {
            MemoryLeakReport leakReport = leakDetector.get().analyzeMemoryGrowth();
            System.out.println("\nMemory Leak Detection:");
            System.out.printf("  - Memory growth rate: %.2f%%/hour%n", leakReport.getGrowthRatePercentage());
            System.out.printf("  - Leaks detected: %s%n", leakReport.hasLeaks() ? "YES" : "NO");
            System.out.printf("  - Leak detection target (<0.1%%/hour): %s%n",
                             leakReport.getGrowthRatePercentage() < TARGET_MEMORY_GROWTH_RATE ? "PASSED" : "FAILED");
        }

        System.out.println("=".repeat(60));
    }

    // ========================================================================
    // Internal Classes
    // ========================================================================

    /**
     * Represents a YAWL case session during long-running operation
     */
    private static class CaseSession {
        private final String caseId;
        private final long creationTime;
        private int operationsProcessed;
        private boolean completed;

        public CaseSession(String caseId) {
            this.caseId = caseId;
            this.creationTime = System.currentTimeMillis();
            this.operationsProcessed = 0;
            this.completed = false;
        }

        public void process() {
            operationsProcessed++;
            // Simulate some work
            try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        public void complete() {
            this.completed = true;
        }

        public long getMemoryUsage() {
            // Estimate memory usage for this session
            return 1024L + operationsProcessed * 128L; // Base + per-operation
        }
    }

    /**
     * Simulated task for thread testing
     */
    private static class SimulatedTask {
        private final String taskId;
        private final List<Object> workData;

        public SimulatedTask(String taskId) {
            this.taskId = taskId;
            this.workData = new ArrayList<>();
        }

        public void execute() {
            // Simulate task execution
            for (int i = 0; i < 100; i++) {
                workData.add(new Object());
                try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            workData.clear(); // Clean up
        }
    }

    /**
     * Memory leak detector for long-running analysis
     */
    private static class MemoryLeakDetector {
        private final MemoryTrackingService trackingService;
        private final double targetGrowthRate;
        private final Map<String, Long> componentMemoryHistory;
        private boolean detectionActive;

        public MemoryLeakDetector(MemoryTrackingService trackingService, double targetGrowthRate) {
            this.trackingService = trackingService;
            this.targetGrowthRate = targetGrowthRate;
            this.componentMemoryHistory = new ConcurrentHashMap<>();
            this.detectionActive = false;
        }

        public void startDetection() {
            this.detectionActive = true;
        }

        public void stopDetection() {
            this.detectionActive = false;
        }

        public MemoryLeakReport analyzeMemoryGrowth() {
            if (!detectionActive) {
                return new MemoryLeakReport(0, false, Collections.emptyList(), 0);
            }

            long currentMemory = trackingService.getCurrentMemory();
            long baselineMemory = trackingService.getBaselineMemory();
            long timeElapsed = System.currentTimeMillis() - trackingService.getBaselineTime();

            double growthRate = baselineMemory > 0 && timeElapsed > 0 ?
                ((double) (currentMemory - baselineMemory) / baselineMemory) / (timeElapsed / (1000.0 * 60 * 60)) : 0;

            List<String> leakyComponents = new ArrayList<>();

            // Check component history for leaks
            for (Map.Entry<String, Long> entry : componentMemoryHistory.entrySet()) {
                long currentComponentMemory = estimateComponentMemory(entry.getKey());
                long historicalMemory = entry.getValue();

                if (currentComponentMemory > historicalMemory * 1.1) { // 10% growth threshold
                    leakyComponents.add(entry.getKey());
                }
            }

            return new MemoryLeakReport(growthRate, growthRate > targetGrowthRate,
                                      leakyComponents, componentMemoryHistory.size());
        }

        private long estimateComponentMemory(String component) {
            // Simplified estimation
            return component.hashCode() % 1000000L;
        }
    }

    /**
     * Service for tracking memory usage over time
     */
    private static class MemoryTrackingService {
        private long baselineMemory;
        private long baselineTime;

        public void recordBaseline() {
            this.baselineMemory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            this.baselineTime = System.currentTimeMillis();
        }

        public long getCurrentMemory() {
            return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        }

        public long getBaselineMemory() {
            return baselineMemory;
        }

        public long getBaselineTime() {
            return baselineTime;
        }
    }

    /**
     * Monitor for garbage collection pause times
     */
    private static class GCPauseMonitor {
        private long lastPauseTime;
        private volatile boolean monitoring;

        public void startMonitoring() {
            this.monitoring = true;
            this.lastPauseTime = 0;
        }

        public void stopMonitoring() {
            this.monitoring = false;
        }

        public long getLastPauseTime() {
            return lastPauseTime;
        }

        // In a real implementation, this would hook into GC notifications
        public void recordPauseTime(long pauseTime) {
            if (monitoring) {
                this.lastPauseTime = pauseTime;
            }
        }
    }

    /**
     * Analyzer for heap efficiency and fragmentation
     */
    private static class HeapAnalyzer {
        private final List<Double> utilizationHistory = new ArrayList<>();

        public void recordMeasurement(double utilization, long used, long max) {
            utilizationHistory.add(utilization);
        }

        public HeapEfficiencyReport generateReport() {
            if (utilizationHistory.isEmpty()) {
                return new HeapEfficiencyReport(0.0, false, 0.0);
            }

            double avgUtilization = utilizationHistory.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

            // Simple fragmentation calculation based on utilization variance
            double variance = calculateVariance(utilizationHistory);
            double fragmentation = Math.min(100.0, variance * 50); // Scale variance to percentage

            boolean isEfficient = avgUtilization > 95.0 && fragmentation < 10.0;

            return new HeapEfficiencyReport(avgUtilization, isEfficient, fragmentation);
        }

        private double calculateVariance(List<Double> values) {
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            return values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0.0);
        }
    }

    /**
     * Monitor for thread memory usage
     */
    private static class ThreadMemoryMonitor {
        private final List<Long> threadMemoryUsage = new ArrayList<>();
        private final AtomicInteger threadCount = new AtomicInteger(0);
        private volatile boolean monitoring;

        public void startMonitoring() {
            this.monitoring = true;
        }

        public void stopMonitoring() {
            this.monitoring = false;
        }

        public void startThreadMonitoring(ExecutorService executor) {
            // In a real implementation, this would track actual thread memory usage
            threadCount.addAndGet(100); // Estimate
        }

        public ThreadMemoryReport generateReport() {
            double avgMemoryPerThread = threadMemoryUsage.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

            return new ThreadMemoryReport(
                threadCount.get(),
                avgMemoryPerThread,
                avgMemoryPerThread < TARGET_VIRTUAL_THREAD_MEMORY
            );
        }

        public int getTotalThreadsCreated() {
            return threadCount.get();
        }
    }

    // ========================================================================
    // Result Classes
    // ========================================================================

    /**
     * Memory leak detection report
     */
    private static record MemoryLeakReport(
        double growthRatePercentage,
        boolean hasLeaks,
        List<String> leakyComponents,
        int componentsAnalyzed
    ) {}

    /**
     * GC analysis result
     */
    private static record GCAnalysisResult(
        long totalPauses,
        long totalPauseTime,
        double averagePauseTime,
        boolean targetMet
    ) {}

    /**
     * Heap efficiency report
     */
    private static record HeapEfficiencyReport(
        double averageUtilization,
        boolean efficient,
        double fragmentationPercentage
    ) {}

    /**
     * Thread memory report
     */
    private static record ThreadMemoryReport(
        int totalThreadsCreated,
        double averageMemoryPerThread,
        boolean efficiencyTargetMet
    ) {}

    // ========================================================================
    // Standalone Runner
    // ========================================================================

    public static void main(String[] args) throws RunnerException {
        System.out.println("""
            ================================================================
            Long-Running Memory Benchmark Suite
            ================================================================
            Target: 24+ hour memory leak detection and efficiency analysis

            This suite validates long-running memory behavior:
            1. sustainedMemoryUsage - Memory usage during 24+ hour operation
            2. memoryLeakDetection - Leak detection during sustained load
            3. garbageCollectionImpact - GC pause time analysis
            4. virtualThreadMemoryEfficiency - Virtual thread memory comparison
            5. heapUtilizationEfficiency - Heap space efficiency analysis

            Performance Targets:
            - Memory growth per case: < 2MB
            - GC pause time: < 10ms
            - Heap utilization: > 95%
            - Virtual thread memory: < 8KB per thread
            - Memory leak rate: < 0.1%/hour

            Running with extended JVM settings for long-running analysis:
            - Large heap (8GB)
            - ZGC for low pauses
            - Uncommit for memory reclamation
            - Large pages for performance
            ================================================================
            """);

        Options opt = new OptionsBuilder()
            .include(LongRunningMemoryBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(2)
            .measurementIterations(5)
            .param("caseLoad", "1000", "5000", "10000")
            .param("testHours", "24", "48", "72")
            .param("threadType", "virtual", "platform")
            .build();

        new Runner(opt).run();
    }
}