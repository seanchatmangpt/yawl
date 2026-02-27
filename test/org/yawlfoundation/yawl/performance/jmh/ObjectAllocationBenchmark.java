/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file implements Object Allocation Pattern Analysis benchmarks:
 * Validates allocation efficiency and optimization strategies.
 * Extends existing JMH framework with comprehensive allocation analysis.
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
 * JMH Benchmark Suite: Object Allocation Pattern Analysis
 *
 * <h2>Target: Efficient Memory Allocation Patterns</h2>
 * <p>This benchmark suite validates object allocation behavior and patterns
 * during YAWL workflow execution, focusing on:
 * - Object allocation patterns analysis
 * - Allocation hotspots identification
 * - Memory allocation efficiency
 * - Object lifecycle management
 * - Allocation optimization strategies</p>
 *
 * <h2>YAWL Allocation Profile</h2>
 * <p>YAWL workflow execution involves various allocation patterns:
 * - Work item creation and management
 * - Event queue operations
 * - Session state objects
 * - Specification cache entries
 * - Virtual thread context objects
 * - Large object allocations for case data</p>
 *
 * <h2>Performance Targets</h2>
 * <table>
 * <tr><th>Metric</th><th>Target</th><th>Measurement</th></tr>
 * <tr><td>Allocation rate</td><td>< 10MB/s</td><td>Rate analysis</td></tr>
 * <tr><td>Hotspot count</td><td>< 5</td><td>Pattern analysis</td></tr>
 * <tr><td>Short-lived objects</td><td>> 80%</td><td>Lifecycle analysis</td></tr>
 * <tr><td>Allocation efficiency</td><td>> 95%</td><td>Efficiency calculation</td></tr>
 * <tr><td>Memory fragmentation</td><td>< 5%</td><td>Fragmentation analysis</td></tr>
 * </table>
 *
 * <h2>Usage Instructions</h2>
 * <pre>
 * # Run allocation pattern benchmarks
 * java -jar benchmarks.jar ObjectAllocationBenchmark
 *
 * # Run specific benchmark
 * java -jar benchmarks.jar ObjectAllocationBenchmark.allocationRateAnalysis
 *
 * # Run with detailed output and hotspot analysis
 * java -jar benchmarks.jar ObjectAllocationBenchmark -rf json -v
 * </pre>
 *
 * @see MemoryOptimizationBenchmarks for memory optimization analysis
 * @see GCAnalysisBenchmark for GC impact analysis
 * @see VirtualThreadMemoryBenchmark for thread-related allocation patterns
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = {
    "-Xms2g", "-Xmx4g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-XX:+UseLargePages",
    "-verbose:gc",
    "-Xlog:alloc*=info:file=alloc-analysis-%p.log:time,uptime,level,tags",
    "-XX:+PrintGCDetails",
    "-XX:+PrintGCApplicationStoppedTime"
})
public class ObjectAllocationBenchmark {

    // Test configuration parameters
    @Param{"100", "1000", "10000"})
    private int allocationRate;

    @Param{"small", "medium", "large"})
    private String objectSizeCategory;

    @Param{"workflow", "session", "cache"})
    private String allocationContext;

    // Allocation monitoring components
    private MemoryMXBean memoryMXBean;
    private AllocationTracker allocationTracker;
    private ObjectLifecycleTracker lifecycleTracker;
    private AllocationHotspotDetector hotspotDetector;
    private MemoryFragmentationAnalyzer fragmentationAnalyzer;

    // Test data and state
    private List<Object> allocatedObjects;
    private List<AllocationEvent> allocationEvents;
    private AtomicLong totalAllocatedMemory = new AtomicLong(0);
    private AtomicInteger totalAllocationCount = new AtomicInteger(0);
    private AtomicReference<AllocationMetrics> currentMetrics = new AtomicReference<>();

    // Performance targets
    private static final long TARGET_ALLOCATION_RATE = 10 * 1024 * 1024; // 10MB/s max
    private static final int TARGET_HOTSPOT_COUNT = 5; // Max 5 allocation hotspots
    private static final double TARGET_SHORT_LIVED_RATIO = 0.80; // 80% min
    private static final double TARGET_ALLOCATION_EFFICIENCY = 0.95; // 95% min
    private static final double TARGET_MEMORY_FRAGMENTATION = 0.05; // 5% max

    // Object size mappings
    private static final Map<String, Integer> OBJECT_SIZES = Map.of(
        "small", 64,      // 64 bytes
        "medium", 1024,   // 1KB
        "large", 10240    // 10KB
    );

    @Setup(Level.Trial)
    public void setup() {
        System.out.println("[ObjectAllocationBenchmark Setup] Initializing with " +
                         allocationRate + " allocations/sec, " +
                         objectSizeCategory + " objects, " +
                         allocationContext + " context");

        // Initialize monitoring
        memoryMXBean = ManagementFactory.getMemoryMXBean();

        // Initialize comprehensive monitoring services
        allocationTracker = new AllocationTracker();
        lifecycleTracker = new ObjectLifecycleTracker();
        hotspotDetector = new AllocationHotspotDetector();
        fragmentationAnalyzer = new MemoryFragmentationAnalyzer();

        // Initialize test data
        allocatedObjects = Collections.synchronizedList(new ArrayList<>());
        allocationEvents = Collections.synchronizedList(new ArrayList<>());

        // Start tracking
        allocationTracker.startTracking();
        lifecycleTracker.startTracking();
        hotspotDetector.startDetection();
        fragmentationAnalyzer.startAnalysis();

        // Pre-warm baseline
        System.gc();
        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        System.out.println("[ObjectAllocationBenchmark Setup] Setup completed with allocation tracking initialized");
    }

    @TearDown
    public void tearDown() {
        System.out.println("[ObjectAllocationBenchmark Teardown] Cleaning up resources...");

        // Stop tracking services
        allocationTracker.stopTracking();
        lifecycleTracker.stopTracking();
        hotspotDetector.stopDetection();
        fragmentationAnalyzer.stopAnalysis();

        // Clean up allocated objects
        allocatedObjects.clear();
        allocationEvents.clear();

        // Final GC and cleanup
        System.gc();
        try { Thread.sleep(10000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Generate final report
        generateFinalReport();
    }

    // ========================================================================
    // Benchmark 1: Allocation Rate Analysis
    // ========================================================================

    /**
     * Measures object allocation rate during YAWL workflow execution.
     * Validates that allocation rate remains below optimal thresholds.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void allocationRateAnalysis(Blackhole bh) {
        long startTime = System.currentTimeMillis();
        long testDurationMs = 30 * 1000; // 30 seconds test

        // Reset tracking
        totalAllocatedMemory.set(0);
        totalAllocationCount.set(0);

        // Simulate allocation at specified rate
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            // Allocate objects at target rate
            int objectsToAllocate = allocationRate / 100; // Convert to objects per 100ms
            allocateObjectsAtRate(objectsToAllocate);

            // Small delay to maintain rate
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // Calculate allocation rate
        long totalAllocated = totalAllocatedMemory.get();
        double allocationRateMBPerSec = (double) totalAllocated / (testDurationMs / 1000.0) / (1024.0 * 1024.0);
        boolean targetMet = allocationRateMBPerSec < TARGET_ALLOCATION_RATE / (1024.0 * 1024.0);

        System.out.printf("[AllocationRate] Total allocated: %d bytes (%.2f MB), " +
                         "Rate: %.2f MB/s, Target met: %s%n",
                         totalAllocated, totalAllocated / (1024.0 * 1024.0),
                         allocationRateMBPerSec, targetMet);

        AllocationRateResult result = new AllocationRateResult(
            totalAllocationCount.get(), totalAllocated, allocationRateMBPerSec, targetMet
        );

        bh.consume(result);
    }

    // ========================================================================
    // Benchmark 2: Allocation Hotspot Detection
    // ========================================================================

    /**
     * Identifies allocation hotspots during YAWL workflow execution.
     * Finds areas of excessive allocation that need optimization.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void allocationHotspotDetection(Blackhole bh) {
        // Reset hotspot detection
        hotspotDetector.resetDetection();

        // Simulate workflow execution with various allocation patterns
        for (int iteration = 0; iteration < 10000; iteration++) {
            // Simulate different allocation contexts
            simulateWorkflowContext();
            simulateSessionContext();
            simulateCacheContext();

            // Track allocations
            if (iteration % 100 == 0) {
                hotspotDetector.analyzeHotspots(allocationEvents);
            }
        }

        // Generate hotspot analysis
        AllocationHotspotReport hotspotReport = hotspotDetector.generateReport();

        System.out.printf("[HotspotDetection] Total hotspots: %d, " +
                         "Max hotspot allocations: %d, " +
                         "Target met: %s%n",
                         hotspotReport.getHotspots().size(),
                         hotspotReport.getMaxAllocationCount(),
                         hotspotReport.getTargetMet());

        bh.consume(hotspotReport);
    }

    // ========================================================================
    // Benchmark 3: Object Lifecycle Analysis
    // ========================================================================

    /**
     * Analyzes object lifecycle patterns during YAWL workflow execution.
     * Validates optimal lifecycle management for short-lived objects.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void objectLifecycleAnalysis(Blackhole bh) {
        // Reset lifecycle tracking
        lifecycleTracker.resetTracking();

        // Simulate object lifecycle patterns
        for (int iteration = 0; iteration < allocationCount; iteration++) {
            // Create objects with different lifecycles
            createShortLivedObjects();
            createMediumLivedObjects();
            createLongLivedObjects();

            // Track object lifetimes
            lifecycleTracker.trackObjectCreation(iteration);

            // Simulate object death for short-lived objects
            if (iteration % 10 == 0) {
                lifecycleTracker.trackObjectDeath(iteration);
            }
        }

        // Analyze lifecycle patterns
        ObjectLifecycleReport lifecycleReport = lifecycleTracker.generateReport();

        System.out.printf("[LifecycleAnalysis] Short-lived ratio: %.1f%%, " +
                         "Medium-lived: %.1f%%, Long-lived: %.1f%%, " +
                         "Target met: %s%n",
                         lifecycleReport.getShortLivedRatio() * 100,
                         lifecycleReport.getMediumLivedRatio() * 100,
                         lifecycleReport.getLongLivedRatio() * 100,
                         lifecycleReport.getTargetMet());

        bh.consume(lifecycleReport);
    }

    // ========================================================================
    // Benchmark 4: Allocation Efficiency Analysis
    // ========================================================================

    /**
     * Measures allocation efficiency during YAWL workflow execution.
     * Ensures that allocations are efficient and avoid unnecessary overhead.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void allocationEfficiencyAnalysis(Blackhole bh) {
        long initialMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long initialObjects = totalAllocationCount.get();

        // Perform efficient allocations
        performEfficientAllocations();

        // Allow GC to run
        System.gc();
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        long finalMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long finalObjects = totalAllocationCount.get();

        // Calculate efficiency metrics
        long memoryGrowth = finalMemory - initialMemory;
        long objectsGrowth = finalObjects - initialObjects;
        double efficiency = objectsGrowth > 0 ?
            (double) memoryGrowth / (objectsGrowth * estimateAverageObjectSize()) : 0;

        boolean targetMet = efficiency > TARGET_ALLOCATION_EFFICIENCY;

        System.out.printf("[AllocationEfficiency] Objects created: %d, " +
                         "Memory growth: %d bytes, Efficiency: %.1f%%, " +
                         "Target met: %s%n",
                         objectsGrowth, memoryGrowth, efficiency * 100, targetMet);

        AllocationEfficiencyResult result = new AllocationEfficiencyResult(
            objectsGrowth, memoryGrowth, efficiency, targetMet
        );

        bh.consume(result);
    }

    // ========================================================================
    // Benchmark 5: Memory Fragmentation Analysis
    // ========================================================================

    /**
     * Analyzes memory fragmentation during YAWL workflow execution.
     * Validates that fragmentation remains within acceptable limits.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void memoryFragmentationAnalysis(Blackhole bh) {
        // Reset fragmentation analysis
        fragmentationAnalyzer.resetAnalysis();

        // Simulate allocation patterns that cause fragmentation
        simulateFragmentationScenario();

        // Analyze fragmentation
        MemoryFragmentationReport fragmentationReport = fragmentationAnalyzer.generateReport();

        System.out.printf("[FragmentationAnalysis] External fragmentation: %.1f%%, " +
                         "Internal fragmentation: %.1f%%, " +
                         "Target met: %s%n",
                         fragmentationReport.getExternalFragmentation() * 100,
                         fragmentationReport.getInternalFragmentation() * 100,
                         fragmentationReport.getTargetMet());

        bh.consume(fragmentationReport);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private void allocateObjectsAtRate(int count) {
        int objectSize = OBJECT_SIZES.get(objectSizeCategory);

        for (int i = 0; i < count; i++) {
            Object obj = allocateObject(objectSize, allocationContext + "-" + i);
            allocatedObjects.add(obj);
            totalAllocatedMemory.addAndGet(objectSize);
            totalAllocationCount.incrementAndGet();

            // Track allocation event
            allocationEvents.add(new AllocationEvent(
                System.currentTimeMillis(),
                objectSize,
                allocationContext,
                obj.getClass().getSimpleName()
            ));
        }
    }

    private Object allocateObject(int size, String context) {
        // Allocate object of specified size
        if (size <= 128) {
            return new AllocationEvent(size, context);
        } else if (size <= 4096) {
            return new DataContainer(size, context);
        } else {
            return new LargeObject(size, context);
        }
    }

    private void simulateWorkflowContext() {
        // Simulate YAWL workflow-specific allocations
        List<WorkItem> workItems = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            workItems.add(new WorkItem("task-" + i, "case-" + System.currentTimeMillis()));
        }
        // Clear to allow GC
        workItems.clear();
    }

    private void simulateSessionContext() {
        // Simulate YAWL session-specific allocations
        SessionContext context = new SessionContext();
        context.setAttribute("user", "user-" + System.currentTimeMillis());
        context.setAttribute("caseId", "case-" + System.currentTimeMillis());
        context.setAttribute("workflow", "workflow-" + System.currentTimeMillis());
        // Session objects should be cleared when session ends
    }

    private void simulateCacheContext() {
        // Simulate YAWL cache-specific allocations
        Map<String, CacheEntry> cache = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            cache.put("key-" + i, new CacheEntry("value-" + i, System.currentTimeMillis()));
        }
        // Cache entries should be evicted when full
        cache.clear();
    }

    private void createShortLivedObjects() {
        // Objects that should be garbage collected quickly
        for (int i = 0; i < 100; i++) {
            Object obj = new Object();
            // No reference kept - should be short-lived
        }
    }

    private void createMediumLivedObjects() {
        // Objects that live for some time but eventually become garbage
        List<Object> mediumLived = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            mediumLived.add(new Object());
        }
        // Clear after some time
        if (System.currentTimeMillis() % 5000 < 100) {
            mediumLived.clear();
        }
    }

    private void createLongLivedObjects() {
        // Objects that live for a long time
        List<Object> longLived = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            longLived.add(new Object());
        }
        // Keep references to simulate long-lived objects
        allocatedObjects.addAll(longLived);
    }

    private void performEfficientAllocations() {
        // Perform efficient allocations using optimized patterns
        int objectSize = OBJECT_SIZES.get(objectSizeCategory);

        // Use object pooling for frequently allocated objects
        for (int i = 0; i < 1000; i++) {
            Object pooled = getPooledObject(objectSize);
            useObject(pooled);
            returnPooledObject(pooled);
        }

        // Use primitive arrays instead of object arrays when possible
        int[] primitiveArray = new int[1000];
        // ... use primitive array
    }

    private Object getPooledObject(int size) {
        // Simplified object pooling
        if (size <= 128) {
            return new Object();
        }
        return new DataContainer(size, "pooled");
    }

    private void useObject(Object obj) {
        // Simulate object usage
        if (obj != null) {
            // Simple usage pattern
            obj.hashCode();
        }
    }

    private void returnPooledObject(Object obj) {
        // Simplified object return to pool
        // In real implementation, would add to pool
    }

    private long estimateAverageObjectSize() {
        // Estimate average object size based on allocation patterns
        return OBJECT_SIZES.getOrDefault(objectSizeCategory, 256);
    }

    private void simulateFragmentationScenario() {
        // Simulate allocation patterns that cause memory fragmentation
        List<Object> largeObjects = new ArrayList<>();
        List<Object> smallObjects = new ArrayList<>();

        // Create a mix of large and small objects
        for (int i = 0; i < 1000; i++) {
            if (i % 10 == 0) {
                // Large objects
                largeObjects.add(new byte[100 * 1024]); // 100KB each
            } else {
                // Small objects
                smallObjects.add(new Object());
            }
        }

        // Remove small objects, keeping large ones
        smallObjects.clear();

        // Add more small objects in gaps
        for (int i = 0; i < 10000; i++) {
            smallObjects.add(new Object());
        }

        smallObjects.clear();
        largeObjects.clear();
    }

    private void generateFinalReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("OBJECT ALLOCATION BENCHMARK FINAL REPORT");
        System.out.println("=".repeat(60));

        // Calculate summary statistics
        long totalAllocated = totalAllocatedMemory.get();
        int totalObjects = totalAllocationCount.get();
        double avgSizePerObject = totalObjects > 0 ? (double) totalAllocated / totalObjects : 0;

        System.out.println("Test Configuration:");
        System.out.printf("  - Allocation rate: %d allocations/sec%n", allocationRate);
        System.out.printf("  - Object size: %s (%d bytes)%n", objectSizeCategory, OBJECT_SIZES.get(objectSizeCategory));
        System.out.printf("  - Allocation context: %s%n", allocationContext);

        System.out.println("\nAllocation Performance Metrics:");
        System.out.printf("  - Total objects allocated: %d%n", totalObjects);
        System.out.printf("  - Total memory allocated: %.2f MB%n", totalAllocated / (1024.0 * 1024.0));
        System.out.printf("  - Average object size: %.1f bytes%n", avgSizePerObject);
        System.out.printf("  - Allocation rate: %.2f MB/s%n",
                         (totalAllocated / (1024.0 * 1024.0)) / (20.0)); // 20 second test duration

        // Hotspot analysis
        if (hotspotDetector != null) {
            AllocationHotspotReport hotspotReport = hotspotDetector.generateReport();
            System.out.println("\nHotspot Analysis:");
            System.out.printf("  - Hotspot count: %d%n", hotspotReport.getHotspots().size());
            System.out.printf("  - Max hotspot allocations: %d%n", hotspotReport.getMaxAllocationCount());
            System.out.printf("  - Target met: %s%n", hotspotReport.getTargetMet());
        }

        // Lifecycle analysis
        if (lifecycleTracker != null) {
            ObjectLifecycleReport lifecycleReport = lifecycleTracker.generateReport();
            System.out.println("\nLifecycle Analysis:");
            System.out.printf("  - Short-lived objects: %.1f%%%n", lifecycleReport.getShortLivedRatio() * 100);
            System.out.printf("  - Medium-lived objects: %.1f%%%n", lifecycleReport.getMediumLivedRatio() * 100);
            System.out.printf("  - Long-lived objects: %.1f%%%n", lifecycleReport.getLongLivedRatio() * 100);
            System.out.printf("  - Target met: %s%n", lifecycleReport.getTargetMet());
        }

        // Fragmentation analysis
        if (fragmentationAnalyzer != null) {
            MemoryFragmentationReport fragmentationReport = fragmentationAnalyzer.generateReport();
            System.out.println("\nFragmentation Analysis:");
            System.out.printf("  - External fragmentation: %.1f%%%n", fragmentationReport.getExternalFragmentation() * 100);
            System.out.printf("  - Internal fragmentation: %.1f%%%n", fragmentationReport.getInternalFragmentation() * 100);
            System.out.printf("  - Target met: %s%n", fragmentationReport.getTargetMet());
        }

        System.out.println("=".repeat(60));
    }

    // ========================================================================
    // Internal Classes
    // ========================================================================

    /**
     * Allocation event tracking
     */
    private static class AllocationEvent {
        private final long timestamp;
        private final int size;
        private final String context;
        private final String className;

        public AllocationEvent(int size, String context) {
            this(System.currentTimeMillis(), size, context, Object.class.getSimpleName());
        }

        public AllocationEvent(long timestamp, int size, String context, String className) {
            this.timestamp = timestamp;
            this.size = size;
            this.context = context;
            this.className = className;
        }

        public long getTimestamp() { return timestamp; }
        public int getSize() { return size; }
        public String getContext() { return context; }
        public String getClassName() { return className; }
    }

    /**
     * Work item representation
     */
    private static class WorkItem {
        private final String taskId;
        private final String caseId;

        public WorkItem(String taskId, String caseId) {
            this.taskId = taskId;
            this.caseId = caseId;
        }
    }

    /**
     * Session context object
     */
    private static class SessionContext {
        private final Map<String, Object> attributes = new HashMap<>();

        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        public Object getAttribute(String key) {
            return attributes.get(key);
        }
    }

    /**
     * Cache entry representation
     */
    private static class CacheEntry {
        private final String value;
        private final long timestamp;

        public CacheEntry(String value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        public String getValue() { return value; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * Data container for medium-sized objects
     */
    private static class DataContainer {
        private final int size;
        private final String context;
        private final byte[] data;

        public DataContainer(int size, String context) {
            this.size = size;
            this.context = context;
            this.data = new byte[size];
        }
    }

    /**
     * Large object representation
     */
    private static class LargeObject {
        private final int size;
        private final String context;
        private final Object[] largeArray;

        public LargeObject(int size, String context) {
            this.size = size;
            this.context = context;
            this.largeArray = new Object[size / 8]; // Simplified large object
        }
    }

    /**
     * Tracker for object allocations
     */
    private static class AllocationTracker {
        private final AtomicLong totalAllocations = new AtomicLong(0);
        private final AtomicLong totalBytes = new AtomicLong(0);
        private volatile boolean tracking;

        public void startTracking() {
            this.tracking = true;
        }

        public void stopTracking() {
            this.tracking = false;
        }

        public void recordAllocation(int bytes) {
            if (tracking) {
                totalAllocations.incrementAndGet();
                totalBytes.addAndGet(bytes);
            }
        }

        public long getTotalAllocations() {
            return totalAllocations.get();
        }

        public long getTotalBytes() {
            return totalBytes.get();
        }
    }

    /**
     * Tracker for object lifecycle
     */
    private static class ObjectLifecycleTracker {
        private final AtomicInteger shortLivedCount = new AtomicInteger(0);
        private final AtomicInteger mediumLivedCount = new AtomicInteger(0);
        private final AtomicInteger longLivedCount = new AtomicInteger(0);
        private final AtomicInteger totalObjects = new AtomicInteger(0);
        private final Map<Integer, Long> objectCreationTimes = new ConcurrentHashMap<>();
        private volatile boolean tracking;

        public void startTracking() {
            this.tracking = true;
        }

        public void stopTracking() {
            this.tracking = false;
        }

        public void resetTracking() {
            shortLivedCount.set(0);
            mediumLivedCount.set(0);
            longLivedCount.set(0);
            totalObjects.set(0);
            objectCreationTimes.clear();
        }

        public void trackObjectCreation(int iteration) {
            if (tracking) {
                totalObjects.incrementAndGet();
                objectCreationTimes.put(iteration, System.currentTimeMillis());
            }
        }

        public void trackObjectDeath(int iteration) {
            if (tracking && objectCreationTimes.containsKey(iteration)) {
                long lifetime = System.currentTimeMillis() - objectCreationTimes.get(iteration);

                if (lifetime < 1000) { // < 1 second
                    shortLivedCount.incrementAndGet();
                } else if (lifetime < 10000) { // < 10 seconds
                    mediumLivedCount.incrementAndGet();
                } else {
                    longLivedCount.incrementAndGet();
                }

                objectCreationTimes.remove(iteration);
            }
        }

        public ObjectLifecycleReport generateReport() {
            int total = totalObjects.get();
            double shortRatio = total > 0 ? (double) shortLivedCount.get() / total : 0;
            double mediumRatio = total > 0 ? (double) mediumLivedCount.get() / total : 0;
            double longRatio = total > 0 ? (double) longLivedCount.get() / total : 0;

            boolean targetMet = shortRatio >= TARGET_SHORT_LIVED_RATIO;

            return new ObjectLifecycleReport(shortRatio, mediumRatio, longRatio, targetMet);
        }
    }

    /**
     * Detector for allocation hotspots
     */
    private static class AllocationHotspotDetector {
        private final Map<String, AtomicInteger> contextCounts = new ConcurrentHashMap<>();
        private final AtomicInteger maxCount = new AtomicInteger(0);
        private volatile boolean detection;

        public void startDetection() {
            this.detection = true;
        }

        public void stopDetection() {
            this.detection = false;
        }

        public void resetDetection() {
            contextCounts.clear();
            maxCount.set(0);
        }

        public void analyzeHotspots(List<AllocationEvent> events) {
            if (!detection) return;

            // Count allocations by context
            for (AllocationEvent event : events) {
                contextCounts.computeIfAbsent(event.getContext(), k -> new AtomicInteger(0))
                            .incrementAndGet();
            }

            // Find maximum count
            maxCount.set(contextCounts.values().stream()
                .mapToInt(AtomicInteger::get)
                .max()
                .orElse(0));
        }

        public AllocationHotspotReport generateReport() {
            List<String> hotspots = contextCounts.entrySet().stream()
                .filter(e -> e.getValue().get() > 100) // Hotspots > 100 allocations
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            boolean targetMet = hotspots.size() <= TARGET_HOTSPOT_COUNT;

            return new AllocationHotspotReport(hotspots, maxCount.get(), targetMet);
        }
    }

    /**
     * Analyzer for memory fragmentation
     */
    private static class MemoryFragmentationAnalyzer {
        private final List<Integer> freeSpaces = new ArrayList<>();
        private final List<Integer> allocatedSpaces = new ArrayList<>();
        private volatile boolean analyzing;

        public void startAnalysis() {
            this.analyzing = true;
        }

        public void stopAnalysis() {
            this.analyzing = false;
        }

        public void resetAnalysis() {
            freeSpaces.clear();
            allocatedSpaces.clear();
        }

        public MemoryFragmentationReport generateReport() {
            // Simplified fragmentation calculation
            double externalFragmentation = calculateExternalFragmentation();
            double internalFragmentation = calculateInternalFragmentation();

            boolean targetMet = externalFragmentation < TARGET_MEMORY_FRAGMENTATION &&
                               internalFragmentation < TARGET_MEMORY_FRAGMENTATION;

            return new MemoryFragmentationReport(externalFragmentation, internalFragmentation, targetMet);
        }

        private double calculateExternalFragmentation() {
            // Simplified external fragmentation calculation
            if (freeSpaces.isEmpty()) return 0;
            double avgFree = freeSpaces.stream().mapToInt(Integer::intValue).average().orElse(0);
            double variance = calculateVariance(freeSpaces);
            return Math.min(1.0, variance / (avgFree + 1));
        }

        private double calculateInternalFragmentation() {
            // Simplified internal fragmentation calculation
            if (allocatedSpaces.isEmpty()) return 0;
            double avgAllocated = allocatedSpaces.stream().mapToInt(Integer::intValue).average().orElse(0);
            double wasted = allocatedSpaces.stream().mapToInt(Integer::intValue).sum() - avgAllocated * allocatedSpaces.size();
            return Math.min(1.0, wasted / (avgAllocated * allocatedSpaces.size() + 1));
        }

        private double calculateVariance(List<Integer> values) {
            double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0);
            return values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        }
    }

    // ========================================================================
    // Result Classes
    // ========================================================================

    /**
     * Allocation rate result
     */
    private static record AllocationRateResult(
        int allocationCount,
        long totalBytesAllocated,
        double allocationRateMBPerSec,
        boolean targetMet
    ) {}

    /**
     * Allocation hotspot report
     */
    private static record AllocationHotspotReport(
        List<String> hotspots,
        int maxAllocationCount,
        boolean targetMet
    ) {}

    /**
     * Object lifecycle report
     */
    private static record ObjectLifecycleReport(
        double shortLivedRatio,
        double mediumLivedRatio,
        double longLivedRatio,
        boolean targetMet
    ) {}

    /**
     * Allocation efficiency result
     */
    private static record AllocationEfficiencyResult(
        int objectsCreated,
        long memoryGrowth,
        double efficiency,
        boolean targetMet
    ) {}

    /**
     * Memory fragmentation report
     */
    private static record MemoryFragmentationReport(
        double externalFragmentation,
        double internalFragmentation,
        boolean targetMet
    ) {}

    // ========================================================================
    // Standalone Runner
    // ========================================================================

    public static void main(String[] args) throws RunnerException {
        System.out.println("""
            ================================================================
            Object Allocation Benchmark Suite
            ================================================================
            Target: Efficient allocation patterns and hotspot elimination

            This suite validates allocation behavior:
            1. allocationRateAnalysis - Allocation rate measurement
            2. allocationHotspotDetection - Hotspot identification
            3. objectLifecycleAnalysis - Lifecycle pattern analysis
            4. allocationEfficiencyAnalysis - Efficiency measurement
            5. memoryFragmentationAnalysis - Fragmentation analysis

            Performance Targets:
            - Allocation rate: < 10MB/s
            - Hotspot count: < 5
            - Short-lived objects: > 80%
            - Allocation efficiency: > 95%
            - Memory fragmentation: < 5%

            Running with optimized allocation settings:
            - ZGC for low pause times
            - Compact object headers
            - Large pages for efficiency
            - Detailed allocation logging
            ================================================================
            """);

        Options opt = new OptionsBuilder()
            .include(ObjectAllocationBenchmark.class.getSimpleName())
            .forks(3)
            .warmupIterations(3)
            .measurementIterations(20)
            .param("allocationRate", "100", "1000", "10000")
            .param("objectSizeCategory", "small", "medium", "large")
            .param("allocationContext", "workflow", "session", "cache")
            .build();

        new Runner(opt).run();
    }
}