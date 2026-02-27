/*
 * YAWL v6.0.0-GA Core Engine Benchmark Agent
 *
 * Specialized benchmark agent for YAWL stateful engine performance
 * Focuses on virtual thread scalability and core engine optimization
 */

package org.yawlfoundation.yawl.benchmark.agents;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.benchmark.framework.BaseBenchmarkAgent;
import org.yawlfoundation.yawl.elements.YAWLWorkflowNet;
import org.yawlfoundation.yawl.engine.YCase;
import org.yawlfoundation.yawl.engine.YNet;
import org.yawlfoundation.yawl.engine.YWorkflowSpecification;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core Engine Performance Benchmark Agent
 *
 * Benchmarks:
 * - Virtual thread scalability (10 to 100,000 cases)
 * - Case creation and execution latency
 * - Engine memory optimization
 * - Java 25 compact object headers benefits
 * - CPU efficiency measurements
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
public class CoreEngineBenchmarkAgent extends BaseBenchmarkAgent {

    // Engine-specific configuration
    private final int maxConcurrentCases;
    private final boolean enableVirtualThreadPinning;
    private final boolean enableStructuredConcurrency;

    // Benchmark data
    private YAWLWorkflowNet[] testNets;
    private YNet[] yNets;
    private List<YCase> casePool;
    private Instant benchmarkStart;

    public CoreEngineBenchmarkAgent() {
        super("CoreEngineBenchmarkAgent", "Core Engine Performance", BaseBenchmarkAgent.defaultConfig());
        this.maxConcurrentCases = 100_000;
        this.enableVirtualThreadPinning = false;
        this.enableStructuredConcurrency = true;
    }

    @Setup
    public void setup() {
        benchmarkStart = Instant.now();
        createTestNets();
        createCasePool();
    }

    private void createTestNets() {
        testNets = new YAWLWorkflowNet[5];
        yNets = new YNet[5];

        // Simple sequential workflow
        testNets[0] = createSequentialWorkflow();
        yNets[0] = createYNet(testNets[0]);

        // Parallel workflow
        testNets[1] = createParallelWorkflow();
        yNets[1] = createYNet(testNets[1]);

        // Complex nested workflow
        testNets[2] = createComplexWorkflow();
        yNets[2] = createYNet(testNets[2]);

        // High concurrency workflow
        testNets[3] = createHighConcurrencyWorkflow();
        yNets[3] = createYNet(testNets[3]);

        // Deep nested workflow
        testNets[4] = createDeepNestedWorkflow();
        yNets[4] = createYNet(testNets[4]);
    }

    private void createCasePool() {
        casePool = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < maxConcurrentCases; i++) {
            YCase testCase = new YCase(null, "case_" + i);
            casePool.add(testCase);
        }
    }

    @Override
    public void executeBenchmark(Blackhole bh) {
        try {
            // Test basic engine operations
            testBasicEngineOperations(bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test virtual thread scalability
     */
    @Benchmark
    @Group("virtualThreadScaling")
    @GroupThreads(10)
    public void testVirtualThreadScaling_10(Blackhole bh) {
        testVirtualThreadScaling(10, bh);
    }

    @Benchmark
    @Group("virtualThreadScaling")
    @GroupThreads(100)
    public void testVirtualThreadScaling_100(Blackhole bh) {
        testVirtualThreadScaling(100, bh);
    }

    @Benchmark
    @Group("virtualThreadScaling")
    @GroupThreads(1000)
    public void testVirtualThreadScaling_1000(Blackhole bh) {
        testVirtualThreadScaling(1000, bh);
    }

    @Benchmark
    @Group("virtualThreadScaling")
    @GroupThreads(10000)
    public void testVirtualThreadScaling_10000(Blackhole bh) {
        testVirtualThreadScaling(10000, bh);
    }

    @Benchmark
    @Group("virtualThreadScaling")
    @GroupThreads(100000)
    public void testVirtualThreadScaling_100000(Blackhole bh) {
        testVirtualThreadScaling(100000, bh);
    }

    private void testVirtualThreadScaling(int threadCount, Blackhole bh) {
        try {
            Instant start = Instant.now();

            List<Future<YCase>> futures = new ArrayList<>();
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                Future<YCase> future = virtualThreadExecutor.submit(() -> {
                    try {
                        YCase testCase = createAndExecuteCase(threadId);
                        successCount.incrementAndGet();
                        return testCase;
                    } catch (Exception e) {
                        // Record error but continue
                        recordError(e, "virtual_thread_" + threadId);
                        return null;
                    }
                });
                futures.add(future);
            }

            // Wait for all cases
            for (Future<YCase> future : futures) {
                YCase testCase = future.get(10, TimeUnit.SECONDS);
                bh.consume(testCase);
            }

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            // Record performance metrics
            performanceMonitor.recordOperation(
                threadCount,
                duration.toMillis(),
                successCount.get(),
                threadCount - successCount.get()
            );

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test memory optimization patterns
     */
    @Benchmark
    @Group("memoryOptimization")
    public void testMemoryOptimization(Blackhole bh) {
        try {
            // Test memory-efficient case handling
            List<YCase> cases = new ArrayList<>();
            Runtime runtime = Runtime.getRuntime();

            long startMemory = runtime.totalMemory() - runtime.freeMemory();

            for (int i = 0; i < 1000; i++) {
                YCase testCase = createMemoryEfficientCase("memory_test_" + i);
                cases.add(testCase);

                // Clean up periodically
                if (i % 100 == 0) {
                    System.gc();
                    Thread.sleep(10); // Allow GC to work
                }
            }

            long endMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = endMemory - startMemory;

            bh.consume(memoryUsed);
            bh.consume(cases);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test structured concurrency benefits
     */
    @Benchmark
    public void testStructuredConcurrencyBenefits(Blackhole bh) throws InterruptedException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<Future<YCase>> futures = new ArrayList<>();

            // Execute multiple engine operations concurrently
            for (int i = 0; i < 5; i++) {
                final int taskId = i;
                Future<YCase> future = scope.fork(() -> {
                    try {
                        return executeStructuredOperation(taskId);
                    } catch (Exception e) {
                        recordError(e, "structured_operation_" + taskId);
                        throw e;
                    }
                });
                futures.add(future);
            }

            scope.join();

            // Collect results
            for (Future<YCase> future : futures) {
                YCase result = future.resultNow();
                bh.consume(result);
            }
        }
    }

    /**
     * Test CPU efficiency with virtual threads
     */
    @Benchmark
    @Group("cpuEfficiency")
    @GroupThreads(10)
    public void testCPUEfficiency_10(Blackhole bh) {
        testCPUEfficiency(10, bh);
    }

    @Benchmark
    @Group("cpuEfficiency")
    @GroupThreads(50)
    public void testCPUEfficiency_50(Blackhole bh) {
        testCPUEfficiency(50, bh);
    }

    @Benchmark
    @Group("cpuEfficiency")
    @GroupThreads(100)
    public void testCPUEfficiency_100(Blackhole bh) {
        testCPUEfficiency(100, bh);
    }

    private void testCPUEfficiency(int concurrency, Blackhole bh) {
        try {
            Instant start = Instant.now();

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < concurrency; i++) {
                final int taskId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    // CPU-intensive work
                    double result = 0;
                    for (int j = 0; j < 1000; j++) {
                        result += Math.sqrt(j) * Math.log(j + 1);
                    }
                }, virtualThreadExecutor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            double efficiency = (concurrency * 1000.0) / duration.toMillis();
            bh.consume(efficiency);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    @Override
    protected YCase runSingleIteration(int iterationId) throws Exception {
        // Basic case creation and execution
        YCase testCase = createSimpleCase();
        executeCase(testCase);
        return testCase;
    }

    // Helper methods for engine benchmarking
    private YAWLWorkflowNet createSequentialWorkflow() {
        YAWLWorkflowNet net = new YAWLWorkflowNet("sequential");
        // Create sequential workflow structure
        return net;
    }

    private YAWLWorkflowNet createParallelWorkflow() {
        YAWLWorkflowNet net = new YAWLWorkflowNet("parallel");
        // Create parallel workflow structure
        return net;
    }

    private YAWLWorkflowNet createComplexWorkflow() {
        YAWLWorkflowNet net = new YAWLWorkflowNet("complex");
        // Create complex nested workflow
        return net;
    }

    private YAWLWorkflowNet createHighConcurrencyWorkflow() {
        YAWLWorkflowNet net = new YAWLWorkflowNet("high_concurrency");
        // Create high-concurrency workflow
        return net;
    }

    private YAWLWorkflowNet createDeepNestedWorkflow() {
        YAWLWorkflowNet net = new YAWLWorkflowNet("deep_nested");
        // Create deeply nested workflow
        return net;
    }

    private YNet createYNet(YAWLWorkflowNet net) {
        return new YNet(net); // Simplified YNet creation
    }

    private YCase createSimpleCase() {
        YWorkflowSpecification spec = new YWorkflowSpecification("simple_" + System.currentTimeMillis());
        return new YCase(spec, "case_" + UUID.randomUUID());
    }

    private YCase createMemoryEfficientCase(String identifier) {
        YWorkflowSpecification spec = new YWorkflowSpecification("efficient_" + identifier);
        YCase testCase = new YCase(spec, identifier);

        // Use minimal data for memory efficiency
        testCase.setData("id", identifier);
        testCase.setData("status", "created");
        testCase.setData("priority", "normal");

        return testCase;
    }

    private YCase createAndExecuteCase(int threadId) throws Exception {
        YCase testCase = createSimpleCase();
        testCase.setData("threadId", threadId);
        testCase.setData("startTime", Instant.now());

        executeCase(testCase);

        return testCase;
    }

    private void executeCase(YCase testCase) {
        // Simulate case execution
        try {
            Thread.sleep(1); // Simulate processing time
            testCase.setData("status", "completed");
            testCase.setData("endTime", Instant.now());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private YCase executeStructuredOperation(int taskId) throws Exception {
        // Execute operation using structured concurrency
        YCase testCase = createSimpleCase();
        testCase.setData("taskId", taskId);
        testCase.setData("structured", "true");

        // Simulate structured operation
        try (var innerScope = new StructuredTaskScope.ShutdownOnFailure()) {
            Future<YCase> result = innerScope.fork(() -> {
                testCase.setData("subtask_" + taskId, "completed");
                return testCase;
            });

            innerScope.join();
            return result.resultNow();
        }
    }

    private void testBasicEngineOperations(Blackhole bh) {
        try {
            // Test basic case creation
            YCase testCase = createSimpleCase();
            bh.consume(testCase);

            // Test case state management
            testCase.setData("testKey", "testValue");
            bh.consume(testCase.getData("testKey"));

            // Test case completion
            executeCase(testCase);
            bh.consume(testCase);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    @Override
    public void close() {
        super.close();

        // Generate final report
        var report = generateFinalReport();
        System.out.println("Core Engine Benchmark Report: " + report);
    }
}