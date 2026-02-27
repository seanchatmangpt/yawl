/*
 * YAWL v6.0.0-GA Memory Optimization Benchmark Agent
 *
 * Specialized benchmark agent for memory usage optimization
 * Tests Java 25 memory features, garbage collection efficiency, and memory patterns
 */

package org.yawlfoundation.yawl.benchmark.agents;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.engine.YCase;
import org.yawlfoundation.yawl.engine.YWorkflowSpecification;

import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Memory Optimization Benchmark Agent
 *
 * Benchmarks:
 * - Java 25 compact object headers optimization
 * - Memory usage patterns and leaks detection
 * - Garbage collection efficiency
 * - Virtual thread memory footprint
 * - Memory optimization patterns implementation
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 10)
@Measurement(iterations = 5, time = 30)
@Fork(value = 1, jvmArgs = {
    "-Xms4g", "-Xmx8g",
    "-XX:+UseG1GC",
    "-XX:+UseCompactObjectHeaders",
    "--enable-preview",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+UseZGC"
})
@State(Scope.Benchmark)
public class MemoryOptimizationBenchmarkAgent extends BaseBenchmarkAgent {

    // Memory optimization configuration
    private final int maxObjects;
    private final int maxIterations;
    private final boolean enableMemoryProfiling;
    private final boolean enableOptimizationPatterns;

    // Memory monitoring
    private final MemoryMXBean memoryMXBean;
    private final AtomicLong totalAllocatedMemory;
    private final AtomicLong totalFreedMemory;
    private final List<MemorySnapshot> memorySnapshots;

    // Optimization patterns
    private final List<MemoryPattern> memoryPatterns;
    private final Map<String, MemoryEfficientCollection> collections;

    // Benchmark state
    private List<YCase> casePool;
    private List<Object> objectPool;
    private Instant benchmarkStart;

    public MemoryOptimizationBenchmarkAgent() {
        super("MemoryOptimizationBenchmarkAgent", "Memory Optimization", BaseBenchmarkAgent.defaultConfig());
        this.maxObjects = 100_000;
        this.maxIterations = 1000;
        this.enableMemoryProfiling = true;
        this.enableOptimizationPatterns = true;

        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.totalAllocatedMemory = new AtomicLong(0);
        this.totalFreedMemory = new AtomicLong(0);
        this.memorySnapshots = Collections.synchronizedList(new ArrayList<>());

        this.memoryPatterns = Arrays.asList(
            new MemoryPattern("object_pooling", "Reuse objects to reduce allocation"),
            new MemoryPattern("primitive_optimization", "Use primitives instead of objects"),
            new MemoryPattern("lazy_loading", "Load data only when needed"),
            new MemoryPattern("weak_references", "Use weak references for caching"),
            new MemoryPattern("direct_allocation", "Pre-allocate memory in bulk"),
            new MemoryPattern("compression", "Compress large data structures")
        );

        this.collections = new ConcurrentHashMap<>();
        this.casePool = new ArrayList<>();
        this.objectPool = new ArrayList<>();
    }

    @Setup
    public void setup() {
        benchmarkStart = Instant.now();
        initializeCollections();
        createTestPools();
        startMemoryMonitoring();
    }

    private void initializeCollections() {
        collections.put("list", new MemoryEfficientArrayList<>());
        collections.put("map", new MemoryEfficientHashMap<>());
        collections.put("set", new MemoryEfficientHashSet<>());
    }

    private void createTestPools() {
        // Create case pool
        for (int i = 0; i < maxObjects / 10; i++) {
            YCase testCase = new YCase(null, "memory_case_" + i);
            casePool.add(testCase);
        }

        // Create object pool
        for (int i = 0; i < maxObjects; i++) {
            objectPool.add(new Object());
        }
    }

    private void startMemoryMonitoring() {
        Thread monitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    MemoryUsage usage = memoryMXBean.getHeapMemoryUsage();
                    MemorySnapshot snapshot = new MemorySnapshot(
                        Instant.now(),
                        usage.getUsed(),
                        usage.getCommitted(),
                        usage.getMax()
                    );
                    memorySnapshots.add(snapshot);
                    Thread.sleep(1000); // Monitor every second
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    @Override
    public void executeBenchmark(Blackhole bh) {
        try {
            // Test memory optimization operations
            testMemoryOptimization(bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test compact object headers optimization
     */
    @Benchmark
    @Group("compactHeaders")
    @GroupThreads(1)
    public void testCompactObjectHeaders_1000(Blackhole bh) {
        testCompactHeadersOptimization(1000, bh);
    }

    @Benchmark
    @Group("compactHeaders")
    @GroupThreads(1)
    public void testCompactObjectHeaders_10000(Blackhole bh) {
        testCompactHeadersOptimization(10000, bh);
    }

    @Benchmark
    @Group("compactHeaders")
    @GroupThreads(1)
    public void testCompactObjectHeaders_100000(Blackhole bh) {
        testCompactHeadersOptimization(100000, bh);
    }

    private void testCompactHeadersOptimization(int objectCount, Blackhole bh) {
        try {
            Instant start = Instant.now();
            long initialMemory = getCurrentMemoryUsage();

            // Create objects with compact headers
            List<MemoryOptimizedObject> objects = new ArrayList<>();
            for (int i = 0; i < objectCount; i++) {
                MemoryOptimizedObject obj = createOptimizedObject();
                objects.add(obj);
                totalAllocatedMemory.addAndGet(calculateObjectSize(obj));
            }

            // Process objects
            for (MemoryOptimizedObject obj : objects) {
                processOptimizedObject(obj);
            }

            long finalMemory = getCurrentMemoryUsage();
            long memoryUsed = finalMemory - initialMemory;

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            performanceMonitor.recordOperation(objectCount, duration.toMillis(), objectCount, 0);
            bh.consume(memoryUsed);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test virtual thread memory footprint
     */
    @Benchmark
    @Group("virtualThreadMemory")
    @GroupThreads(1)
    public void testVirtualThreadMemory_100(Blackhole bh) {
        testVirtualThreadMemoryFootprint(100, bh);
    }

    @Benchmark
    @Group("virtualThreadMemory")
    @GroupThreads(1)
    public void testVirtualThreadMemory_1000(Blackhole bh) {
        testVirtualThreadMemoryFootprint(1000, bh);
    }

    @Benchmark
    @Group("virtualThreadMemory")
    @GroupThreads(1)
    public void testVirtualThreadMemory_10000(Blackhole bh) {
        testVirtualThreadMemoryFootprint(10000, bh);
    }

    private void testVirtualThreadMemoryFootprint(int threadCount, Blackhole bh) {
        try {
            Instant start = Instant.now();
            long initialMemory = getCurrentMemoryUsage();

            // Create virtual threads and test memory
            List<Future<MemoryUsage>> futures = new ArrayList<>();
            List<MemoryOptimizedObject> results = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                Future<MemoryUsage> future = virtualThreadExecutor.submit(() -> {
                    try {
                        MemoryOptimizedObject obj = createOptimizedObject();
                        results.add(obj);

                        // Simulate work
                        MemoryUsage threadMemory = memoryMXBean.getHeapMemoryUsage();
                        return threadMemory;
                    } catch (Exception e) {
                        recordError(e, "virtual_thread_memory_" + threadId);
                        return null;
                    }
                });
                futures.add(future);
            }

            // Wait for all threads
            for (Future<MemoryUsage> future : futures) {
                MemoryUsage usage = future.get(30, TimeUnit.SECONDS);
                bh.consume(usage);
            }

            long finalMemory = getCurrentMemoryUsage();
            long memoryUsed = finalMemory - initialMemory;

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            performanceMonitor.recordOperation(threadCount, duration.toMillis(), threadCount, 0);
            bh.consume(memoryUsed);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test garbage collection efficiency
     */
    @Benchmark
    @Group("garbageCollection")
    public void testGarbageCollectionEfficiency(Blackhole bh) {
        try {
            testGCPerformance(bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test memory optimization patterns
     */
    @Benchmark
    @Group("optimizationPatterns")
    public void testOptimizationPatterns(Blackhole bh) {
        try {
            testPatternPerformance(bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test memory leak detection
     */
    @Benchmark
    @Group("memoryLeakDetection")
    public void testMemoryLeakDetection(Blackhole bh) {
        try {
            testLeakDetectionPerformance(bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test structured concurrency with memory awareness
     */
    @Benchmark
    public void testStructuredConcurrencyMemoryAware(Blackhole bh) throws InterruptedException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<Future<MemorySnapshot>> futures = new ArrayList<>();

            // Memory-aware structured operations
            for (int i = 0; i < 5; i++) {
                final int operationId = i;
                Future<MemorySnapshot> future = scope.fork(() -> {
                    try {
                        long before = getCurrentMemoryUsage();

                        // Perform memory-intensive operation
                        MemoryOptimizedObject result = performMemoryIntensiveOperation(operationId);

                        long after = getCurrentMemoryUsage();
                        long delta = after - before;

                        return new MemorySnapshot(Instant.now(), delta, delta, delta);
                    } catch (Exception e) {
                        recordError(e, "structured_memory_" + operationId);
                        throw e;
                    }
                });
                futures.add(future);
            }

            scope.join();

            // Collect and report memory snapshots
            for (Future<MemorySnapshot> future : futures) {
                MemorySnapshot snapshot = future.resultNow();
                bh.consume(snapshot);
            }
        }
    }

    @Override
    protected YCase runSingleIteration(int iterationId) throws Exception {
        // Create memory-optimized YCase
        YCase testCase = new YCase(null, "memory_optimized_case_" + iterationId);

        // Apply memory optimizations
        optimizeMemoryUsage(testCase);

        return testCase;
    }

    // Memory optimization helper methods
    private MemoryOptimizedObject createOptimizedObject() {
        return new MemoryOptimizedObject(
            "optimized_" + UUID.randomUUID(),
            System.currentTimeMillis(),
            new byte[100] // Fixed-size data
        );
    }

    private void processOptimizedObject(MemoryOptimizedObject obj) {
        // Process object with memory efficiency
        obj.setData("processed", true);
        obj.setData("timestamp", Instant.now());
    }

    private long getCurrentMemoryUsage() {
        MemoryUsage usage = memoryMXBean.getHeapMemoryUsage();
        return usage.getUsed();
    }

    private long calculateObjectSize(Object obj) {
        // Simplified size calculation
        // In reality, would use Instrumentation API
        return 64; // Average object size in bytes
    }

    private void optimizeMemoryUsage(YCase testCase) {
        // Apply various memory optimization techniques
        testCase.setData("optimized", true);
        testCase.setData("memory_pattern", "object_pooling");

        // Use primitive types where possible
        testCase.setData("primitive_data", 42); // int instead of Integer
        testCase.setData("primitive_flag", true); // boolean instead of Boolean
    }

    private void performMemoryIntensiveOperation(int operationId) {
        // Simulate memory-intensive operation
        List<MemoryOptimizedObject> results = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            results.add(createOptimizedObject());
        }

        // Process results
        for (MemoryOptimizedObject obj : results) {
            processOptimizedObject(obj);
        }

        // Allow garbage collection
        results.clear();
        System.gc();
    }

    // Benchmark helper methods
    private void testGCPerformance(Blackhole bh) {
        try {
            // Create objects that will trigger GC
            List<MemoryOptimizedObject> objects = new ArrayList<>();
            int batchSize = 10_000;

            for (int i = 0; i < 5; i++) {
                long before = getCurrentMemoryUsage();

                // Allocate memory
                for (int j = 0; j < batchSize; j++) {
                    objects.add(createOptimizedObject());
                }

                long after = getCurrentMemoryUsage();
                long allocated = after - before;

                // Clear references to trigger GC
                objects.clear();
                System.gc();

                Thread.sleep(100); // Allow GC to complete

                long cleared = getCurrentMemoryUsage();
                long freed = allocated - (cleared - before);

                performanceMonitor.recordOperation(batchSize, 0, batchSize, 0);
                bh.consume(freed);
            }

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    private void testPatternPerformance(Blackhole bh) {
        try {
            Instant start = Instant.now();

            for (MemoryPattern pattern : memoryPatterns) {
                testIndividualPattern(pattern, 100, bh);
            }

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            performanceMonitor.recordOperation(memoryPatterns.size(), duration.toMillis(),
                memoryPatterns.size(), 0);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    private void testIndividualPattern(MemoryPattern pattern, int iterations, Blackhole bh) {
        try {
            long initialMemory = getCurrentMemoryUsage();

            switch (pattern.name()) {
                case "object_pooling":
                    testObjectPooling(iterations, bh);
                    break;
                case "primitive_optimization":
                    testPrimitiveOptimization(iterations, bh);
                    break;
                case "lazy_loading":
                    testLazyLoading(iterations, bh);
                    break;
                case "weak_references":
                    testWeakReferences(iterations, bh);
                    break;
                case "direct_allocation":
                    testDirectAllocation(iterations, bh);
                    break;
                case "compression":
                    testCompression(iterations, bh);
                    break;
            }

            long finalMemory = getCurrentMemoryUsage();
            long memorySaved = finalMemory - initialMemory;

            bh.consume(pattern.name() + "_memory_" + memorySaved);

        } catch (Exception e) {
            recordError(e, "pattern_test_" + pattern.name());
        }
    }

    private void testObjectPooling(int iterations, Blackhole bh) {
        ObjectPool<MemoryOptimizedObject> pool = new ObjectPool<>();

        for (int i = 0; i < iterations; i++) {
            MemoryOptimizedObject obj = pool.acquire();
            try {
                processOptimizedObject(obj);
            } finally {
                pool.release(obj);
            }
        }

        bh.consume("object_pooling_complete");
    }

    private void testPrimitiveOptimization(int iterations, Blackhole bh) {
        // Use arrays of primitives instead of collections of objects
        int[] intArray = new int[iterations];
        boolean[] boolArray = new boolean[iterations];

        for (int i = 0; i < iterations; i++) {
            intArray[i] = i;
            boolArray[i] = (i % 2) == 0;
        }

        bh.consume("primitive_optimization_complete");
    }

    private void testLazyLoading(int iterations, Blackhole bh) {
        // Simulate lazy loading
        LazyDataLoader loader = new LazyDataLoader();

        for (int i = 0; i < iterations; i++) {
            if (i % 10 == 0) { // Only load 10% of the time
                loader.loadLazyData("data_" + i);
            }
        }

        bh.consume("lazy_loading_complete");
    }

    private void testWeakReferences(int iterations, Blackhole bh) {
        // Use weak references for caching
        WeakHashMap<String, MemoryOptimizedObject> weakCache = new WeakHashMap<>();

        for (int i = 0; i < iterations; i++) {
            MemoryOptimizedObject obj = createOptimizedObject();
            weakCache.put("cache_key_" + i, obj);
        }

        bh.consume("weak_references_complete");
    }

    private void testDirectAllocation(int iterations, Blackhole bh) {
        // Pre-allocate memory in bulk
        List<MemoryOptimizedObject> bulkList = new ArrayList<>(iterations);

        for (int i = 0; i < iterations; i++) {
            bulkList.add(createOptimizedObject());
        }

        bh.consume("direct_allocation_complete");
    }

    private void testCompression(int iterations, Blackhole bh) {
        // Simulate data compression
        List<byte[]> compressedData = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            String data = "some_long_string_data_" + i.repeat(100);
            byte[] compressed = compressData(data);
            compressedData.add(compressed);
        }

        bh.consume("compression_complete");
    }

    private byte[] compressData(String data) {
        // Simplified compression
        return data.substring(0, Math.min(100, data.length())).getBytes();
    }

    private void testLeakDetectionPerformance(Blackhole bh) {
        try {
            // Create objects that could cause memory leaks
            List<MemoryOptimizedObject> leakyObjects = new ArrayList<>();
            int batchSize = 1000;

            for (int i = 0; i < 5; i++) {
                // Create objects without clearing references
                for (int j = 0; j < batchSize; j++) {
                    MemoryOptimizedObject obj = createOptimizedObject();
                    obj.addReference("ref_" + j);
                    leakyObjects.add(obj);
                }

                // Simulate memory leak detection
                MemorySnapshot snapshot = detectMemoryLeak(leakyObjects);
                bh.consume(snapshot);

                // Clear some references
                leakyObjects.subList(0, batchSize / 2).clear();
            }

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    private MemorySnapshot detectMemoryLeak(List<MemoryOptimizedObject> objects) {
        long currentMemory = getCurrentMemoryUsage();
        long expectedMemory = objects.size() * 64; // Estimated size

        return new MemorySnapshot(
            Instant.now(),
            currentMemory,
            expectedMemory,
            Math.max(currentMemory, expectedMemory)
        );
    }

    private void testMemoryOptimization(Blackhole bh) {
        try {
            // Create memory-optimized objects
            MemoryOptimizedObject obj = createOptimizedObject();
            optimizeMemoryUsage(obj);

            bh.consume(obj);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    @Override
    public void close() {
        // Shutdown memory monitoring
        Thread.currentThread().interrupt();

        super.close();

        // Generate memory report
        MemoryReport report = generateMemoryReport();
        System.out.println("Memory Optimization Benchmark Report: " + report);
    }

    // Inner classes for memory optimization
    public static record MemoryOptimizedObject(
        String id,
        long timestamp,
        byte[] data,
        Map<String, Object> metadata
    ) {
        public MemoryOptimizedObject(String id, long timestamp, byte[] data) {
            this(id, timestamp, data, new HashMap<>());
        }

        public MemoryOptimizedObject withMetadata(String key, Object value) {
            var newMetadata = new HashMap<>(metadata);
            newMetadata.put(key, value);
            return new MemoryOptimizedObject(id, timestamp, data, newMetadata);
        }

        public MemoryOptimizedObject withReference(String reference) {
            var newMetadata = new HashMap<>(metadata);
            newMetadata.put(reference, new Object());
            return new MemoryOptimizedObject(id, timestamp, data, newMetadata);
        }
    }

    public static record MemorySnapshot(
        Instant timestamp,
        long usedMemory,
        long committedMemory,
        long maxMemory
    ) {}

    public record MemoryReport(
        String agentName,
        List<MemorySnapshot> snapshots,
        long totalAllocated,
        long totalFreed,
        double averageMemoryUsage
    ) {}

    public record MemoryPattern(String name, String description) {}

    // Memory-efficient collections (simplified implementations)
    public static class MemoryEfficientArrayList<T> extends ArrayList<T> {
        // Optimized for memory usage
    }

    public static class MemoryEfficientHashMap<K, V> extends HashMap<K, V> {
        // Optimized for memory usage
    }

    public static class MemoryEfficientHashSet<T> extends HashSet<T> {
        // Optimized for memory usage
    }

    // Object pooling implementation
    public static class ObjectPool<T> {
        private final Queue<T> pool = new ConcurrentLinkedQueue<>();

        public T acquire() {
            T obj = pool.poll();
            return obj != null ? obj : createNewObject();
        }

        public void release(T obj) {
            pool.offer(obj);
        }

        private T createNewObject() {
            // Would create new instance based on type
            return null;
        }
    }

    // Lazy loading implementation
    public static class LazyDataLoader {
        private final Map<String, Object> cache = new HashMap<>();

        public void loadLazyData(String key) {
            cache.computeIfAbsent(key, k -> {
                // Simulate expensive data loading
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new Object();
            });
        }
    }
}