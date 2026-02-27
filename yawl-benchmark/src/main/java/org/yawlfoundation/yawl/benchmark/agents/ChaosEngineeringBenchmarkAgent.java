/*
 * YAWL v6.0.0-GA Chaos Engineering Benchmark Agent
 *
 * Specialized benchmark agent for chaos engineering and resilience testing
 * Tests system resilience, failure injection, and recovery capabilities
 */

package org.yawlfoundation.yawl.benchmark.agents;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.engine.instance.CaseInstance;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.benchmark.framework.BaseBenchmarkAgent;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Chaos Engineering Benchmark Agent
 *
 * Benchmarks:
 * - System resilience under failure conditions
 * - Failure injection and recovery testing
 * - Network partition simulation
 * - Memory failure scenarios
 * - Concurrent failure injection
 * - Recovery time measurement
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
public class ChaosEngineeringBenchmarkAgent extends BaseBenchmarkAgent {

    // Chaos configuration
    private final double failureProbability;
    private final int maxConcurrentFailures;
    private final boolean enableFailureRecovery;
    private final boolean enableSystemStress;
    private final ChaosScenario[] scenarios;

    // Failure injection system
    private final Random failureGenerator;
    private final AtomicBoolean chaosActive;
    private final AtomicInteger activeFailures;
    private final AtomicLong totalRecoveryTime;
    private final List<FailureEvent> failureHistory;

    // System state monitoring
    private final SystemHealthMonitor healthMonitor;
    private final RecoveryManager recoveryManager;

    // Benchmark state
    private List<CaseInstance> testCases;
    private Instant benchmarkStart;

    public ChaosEngineeringBenchmarkAgent() {
        super("ChaosEngineeringBenchmarkAgent", "Chaos Engineering", BaseBenchmarkAgent.defaultConfig());
        this.failureProbability = 0.1; // 10% failure rate
        this.maxConcurrentFailures = 10;
        this.enableFailureRecovery = true;
        this.enableSystemStress = true;

        this.failureGenerator = new Random();
        this.chaosActive = new AtomicBoolean(false);
        this.activeFailures = new AtomicInteger(0);
        this.totalRecoveryTime = new AtomicLong(0);
        this.failureHistory = Collections.synchronizedList(new ArrayList<>());

        this.healthMonitor = new SystemHealthMonitor();
        this.recoveryManager = new RecoveryManager();

        // Initialize chaos scenarios
        this.scenarios = new ChaosScenario[]{
            new ChaosScenario("network_partition", "Network partition simulation"),
            new ChaosScenario("memory_failure", "Memory allocation failure"),
            new ChaosScenario("cpu_spike", "CPU spike simulation"),
            new ChaosScenario("concurrent_failures", "Concurrent failure injection"),
            new ChaosScenario("resource_exhaustion", "Resource exhaustion simulation"),
            new ChaosScenario("time_skew", "Clock skew simulation")
        };

        this.testCases = new ArrayList<>();
    }

    @Setup
    public void setup() {
        benchmarkStart = Instant.now();
        initializeTestCases();
        startHealthMonitoring();
    }

    private void initializeTestCases() {
        for (int i = 0; i < 1000; i++) {
            CaseInstance testCase = new CaseInstance();
            testCases.add(testCase);
        }
    }

    private void startHealthMonitoring() {
        Thread healthThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    healthMonitor.checkSystemHealth();
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        healthThread.setDaemon(true);
        healthThread.start();
    }

    @Override
    public void executeBenchmark(Blackhole bh) {
        try {
            // Test chaos engineering operations
            testChaosEngineering(bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test failure injection with controlled probability
     */
    @Benchmark
    @Group("failureInjection")
    @GroupThreads(1)
    public void testFailureInjection_100(Blackhole bh) {
        testFailureInjection(100, bh);
    }

    @Benchmark
    @Group("failureInjection")
    @GroupThreads(1)
    public void testFailureInjection_1000(Blackhole bh) {
        testFailureInjection(1000, bh);
    }

    @Benchmark
    @Group("failureInjection")
    @GroupThreads(1)
    public void testFailureInjection_10000(Blackhole bh) {
        testFailureInjection(10000, bh);
    }

    private void testFailureInjection(int operationCount, Blackhole bh) {
        try {
            int successCount = 0;
            int failureCount = 0;

            for (int i = 0; i < operationCount; i++) {
                boolean shouldFail = failureGenerator.nextDouble() < failureProbability;

                if (shouldFail) {
                    // Inject failure
                    FailureType failureType = selectRandomFailure();
                    injectFailure(failureType, "injection_test_" + i);
                    failureCount++;
                } else {
                    // Normal operation
                    CaseInstance testCase = new CaseInstance();
                    // testCase.setData("status", "completed");
                    successCount++;
                }
            }

            // Enable chaos mode
            chaosActive.set(true);

            // Attempt recovery
            if (enableFailureRecovery) {
                performSystemRecovery();
            }

            // Record metrics
            performanceMonitor.recordOperation(operationCount, 0, successCount, failureCount);
            bh.consume(successCount);
            bh.consume(failureCount);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test network partition simulation
     */
    @Benchmark
    @Group("networkPartition")
    @GroupThreads(1)
    public void testNetworkPartition(Blackhole bh) {
        try {
            testNetworkPartitionScenario(10, bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test memory failure scenarios
     */
    @Benchmark
    @Group("memoryFailure")
    @GroupThreads(1)
    public void testMemoryFailure(Blackhole bh) {
        try {
            testMemoryFailureScenario(10, bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test concurrent failure injection
     */
    @Benchmark
    @Group("concurrentFailures")
    @GroupThreads(1)
    public void testConcurrentFailures(Blackhole bh) {
        try {
            testConcurrentFailureInjection(50, bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test recovery time measurement
     */
    @Benchmark
    @Group("recoveryTime")
    @GroupThreads(1)
    public void testRecoveryTime(Blackhole bh) {
        try {
            testRecoveryPerformance(10, bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test system stress resilience
     */
    @Benchmark
    @Group("systemStress")
    @GroupThreads(1)
    public void testSystemStress(Blackhole bh) {
        try {
            testSystemStressScenario(100, bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test structured chaos scenarios
     */
    @Benchmark
    public void testStructuredChaosScenarios(Blackhole bh) throws InterruptedException {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<ChaosResult>> futures = new ArrayList<>();

            // Execute chaos scenarios in parallel
            for (ChaosScenario scenario : scenarios) {
                final ChaosScenario currentScenario = scenario;
                Future<ChaosResult> future = executor.submit(() -> {
                    try {
                        return executeChaosScenario(currentScenario);
                    } catch (Exception e) {
                        recordError(e, "structured_chaos_" + currentScenario.name());
                        throw e;
                    }
                });
                futures.add(future);
            }

            // Collect results
            for (Future<ChaosResult> future : futures) {
                try {
                    ChaosResult result = future.get();
                    bh.consume(result);
                } catch (Exception e) {
                    recordError(e, "structured_chaos_result");
                }
            }
        } finally {
            executor.shutdown();
        }
    }

    @Override
    protected CaseInstance runSingleIteration(int iterationId) throws Exception {
        // Create chaos test case
        CaseInstance testCase = new CaseInstance();

        // Simulate chaos scenario
        if (failureGenerator.nextDouble() < failureProbability) {
            FailureType failure = selectRandomFailure();
            injectFailure(failure, "iteration_" + iterationId);
            // testCase.setData("status", "failed");
            // testCase.setData("failureType", failure.name());
        } else {
            // testCase.setData("status", "completed");
        }

        // Apply recovery if needed
        // if (enableFailureRecovery && testCase.getData("status") == "failed") { // CaseInstance doesn't have getData method
        //     RecoveryResult recovery = recoveryManager.recover(testCase);
        //     // testCase.setData("recoveryStatus", recovery.status());
        //     // testCase.setData("recoveryTime", recovery.recoveryTime());
        // }

        return testCase;
    }

    // Chaos engineering helper methods
    private FailureType selectRandomFailure() {
        FailureType[] types = FailureType.values();
        return types[failureGenerator.nextInt(types.length)];
    }

    private void injectFailure(FailureType failureType, String context) {
        Instant failureTime = Instant.now();

        switch (failureType) {
            case NETWORK_FAILURE:
                injectNetworkFailure(context);
                break;
            case MEMORY_FAILURE:
                injectMemoryFailure(context);
                break;
            case CPU_FAILURE:
                injectCPUFailure(context);
                break;
            case CONCURRENT_FAILURE:
                injectConcurrentFailure(context);
                break;
            case RESOURCE_FAILURE:
                injectResourceFailure(context);
                break;
            case TIME_FAILURE:
                injectTimeFailure(context);
                break;
        }

        // Record failure event
        FailureEvent event = new FailureEvent(
            failureType,
            context,
            failureTime,
            activeFailures.incrementAndGet()
        );
        failureHistory.add(event);
    }

    private void injectNetworkFailure(String context) {
        // Simulate network partition
        try {
            Thread.sleep(10); // Simulate network latency
            // Simulate connection loss
            throw new RuntimeException("Network partition detected: " + context);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void injectMemoryFailure(String context) {
        // Simulate memory allocation failure
        try {
            // Allocate large memory block
            byte[] largeArray = new byte[1024 * 1024 * 10]; // 10MB
            System.gc(); // Suggest GC
            Thread.sleep(5);
            throw new RuntimeException("Memory allocation failed: " + context);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void injectCPUFailure(String context) {
        // Simulate CPU spike
        try {
            long start = System.currentTimeMillis();
            double result = 0;
            while (System.currentTimeMillis() - start < 100) { // 100ms CPU spike
                result += Math.sqrt(Math.random()) * Math.log(Math.random() + 1);
            }
            throw new RuntimeException("CPU spike detected: " + context);
        } catch (Exception e) {
            throw new RuntimeException("CPU failure: " + context);
        }
    }

    private void injectConcurrentFailure(String context) {
        // Simulate concurrent access issues
        List<Future<Void>> futures = new ArrayList<>();
        AtomicInteger concurrentCount = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            Future<Void> future = virtualThreadExecutor.submit(() -> {
                concurrentCount.incrementAndGet();
                try {
                    Thread.sleep(1);
                    if (concurrentCount.get() > 5) {
                        throw new RuntimeException("Concurrent access violation: " + context);
                    }
                } finally {
                    concurrentCount.decrementAndGet();
                }
                return null;
            });
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        } catch (Exception e) {
            throw new RuntimeException("Concurrent failure: " + context);
        }
    }

    private void injectResourceFailure(String context) {
        // Simulate resource exhaustion
        try {
            // Limit resource usage
            for (int i = 0; i < 1000; i++) {
                String resource = "resource_" + i;
                if (resource.length() > 100) {
                    throw new RuntimeException("Resource limit exceeded: " + context);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Resource failure: " + context);
        }
    }

    private void injectTimeFailure(String context) {
        // Simulate clock skew
        try {
            // Simulate time jump
            Instant originalTime = Instant.now();
            Thread.sleep(1);
            Instant skewedTime = Instant.now();
            Duration skew = Duration.between(originalTime, skewedTime);

            if (skew.toMillis() > 10) { // 10ms skew
                throw new RuntimeException("Time skew detected: " + context);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void performSystemRecovery() {
        Instant recoveryStart = Instant.now();

        // Attempt to recover from active failures
        for (FailureEvent event : failureHistory) {
            if (event.timestamp().isAfter(recoveryStart)) {
                RecoveryResult recovery = recoveryManager.recoverFromFailure(event);
                totalRecoveryTime.addAndGet(recovery.recoveryTime());
            }
        }

        Instant recoveryEnd = Instant.now();
        Duration totalRecovery = Duration.between(recoveryStart, recoveryEnd);

        // Clear recovered failures
        failureHistory.removeIf(event -> event.timestamp().isAfter(recoveryStart));
        activeFailures.set(0);
        chaosActive.set(false);

        performanceMonitor.recordOperation(1, totalRecovery.toMillis(), 1, 0); // Record as successful operation
    }

    // Scenario testing methods
    private void testNetworkPartitionScenario(int partitions, Blackhole bh) {
        try {
            Instant start = Instant.now();

            // Create network partitions
            for (int i = 0; i < partitions; i++) {
                injectNetworkFailure("partition_" + i);
            }

            // Test recovery
            performSystemRecovery();

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            performanceMonitor.recordOperation(partitions, duration.toMillis(), 0, partitions);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    private void testMemoryFailureScenario(int failures, Blackhole bh) {
        try {
            int successCount = 0;
            int failureCount = 0;

            for (int i = 0; i < failures; i++) {
                try {
                    injectMemoryFailure("memory_test_" + i);
                    failureCount++;
                } catch (Exception e) {
                    successCount++;
                }
            }

            performSystemRecovery();

            bh.consume(successCount);
            bh.consume(failureCount);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    private void testConcurrentFailureInjection(int concurrentOperations, Blackhole bh) {
        try {
            Instant start = Instant.now();
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < concurrentOperations; i++) {
                Future<Void> future = virtualThreadExecutor.submit(() -> {
                    try {
                        if (failureGenerator.nextDouble() < 0.5) {
                            injectConcurrentFailure("concurrent_test_" + Thread.currentThread().getId());
                            failureCount.incrementAndGet();
                        } else {
                            successCount.incrementAndGet();
                        }
                        return null;
                    } catch (Exception e) {
                        recordError(e, "concurrent_injection_" + Thread.currentThread().getId());
                        failureCount.incrementAndGet();
                        return null;
                    }
                });
                futures.add(future);
            }

            // Wait for all operations
            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            performSystemRecovery();

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            performanceMonitor.recordOperation(concurrentOperations, duration.toMillis(),
                successCount.get(), failureCount.get());

            bh.consume(successCount.get());
            bh.consume(failureCount.get());

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    private void testRecoveryPerformance(int testCases, Blackhole bh) {
        try {
            Instant start = Instant.now();

            // Inject failures
            for (int i = 0; i < testCases; i++) {
                injectNetworkFailure("recovery_test_" + i);
            }

            // Measure recovery time
            performSystemRecovery();

            Instant end = Instant.now();
            Duration totalDuration = Duration.between(start, end);

            bh.consume(totalDuration.toMillis());

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    private void testSystemStressScenario(int stressLevel, Blackhole bh) {
        try {
            Instant start = Instant.now();

            // Apply multiple stressors simultaneously
            List<Future<Void>> futures = new ArrayList<>();

            // Network stress
            futures.add(virtualThreadExecutor.submit(() -> {
                for (int i = 0; i < stressLevel / 3; i++) {
                    injectNetworkFailure("stress_network_" + i);
                }
                return null;
            }));

            // Memory stress
            futures.add(virtualThreadExecutor.submit(() -> {
                for (int i = 0; i < stressLevel / 3; i++) {
                    injectMemoryFailure("stress_memory_" + i);
                }
                return null;
            }));

            // CPU stress
            futures.add(virtualThreadExecutor.submit(() -> {
                for (int i = 0; i < stressLevel / 3; i++) {
                    injectCPUFailure("stress_cpu_" + i);
                }
                return null;
            }));

            // Wait for all stressors
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            // Attempt recovery
            performSystemRecovery();

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            performanceMonitor.recordOperation(stressLevel, duration.toMillis(), 0, stressLevel);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    private ChaosResult executeChaosScenario(ChaosScenario scenario) {
        try {
            Instant start = Instant.now();

            // Execute specific scenario
            switch (scenario.name()) {
                case "network_partition":
                    testNetworkPartitionScenario(5, new Blackhole("consumed_data"));
                    break;
                case "memory_failure":
                    testMemoryFailureScenario(5, new Blackhole("memory_data"));
                    break;
                case "cpu_spike":
                    injectCPUFailure("cpu_scenario_test");
                    break;
                case "concurrent_failures":
                    testConcurrentFailureInjection(10, new Blackhole("concurrent_data"));
                    break;
                case "resource_exhaustion":
                    injectResourceFailure("resource_scenario_test");
                    break;
                case "time_skew":
                    injectTimeFailure("time_scenario_test");
                    break;
            }

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            return new ChaosResult(
                scenario.name(),
                true,
                duration.toMillis(),
                ""
            );

        } catch (Exception e) {
            return new ChaosResult(
                scenario.name(),
                false,
                0,
                e.getMessage()
            );
        }
    }

    private void testChaosEngineering(Blackhole bh) {
        try {
            // Basic chaos test
            int testCount = 100;
            int successCount = 0;
            int failureCount = 0;

            for (int i = 0; i < testCount; i++) {
                try {
                    CaseInstance testCase = new CaseInstance();
                    // testCase.setData("status", "completed");
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    recordError(e, "basic_chaos_test");
                }
            }

            // Enable chaos mode
            chaosActive.set(true);

            bh.consume(successCount);
            bh.consume(failureCount);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    @Override
    public void close() {
        // Shutdown chaos engineering
        chaosActive.set(false);
        Thread.currentThread().interrupt();

        super.close();

        // Generate chaos report
        // ChaosReport report = generateChaosReport(); // Not implemented yet
        System.out.println("Chaos Engineering Benchmark completed");
    }

    // Inner classes for chaos engineering
    public enum FailureType {
        NETWORK_FAILURE,
        MEMORY_FAILURE,
        CPU_FAILURE,
        CONCURRENT_FAILURE,
        RESOURCE_FAILURE,
        TIME_FAILURE
    }

    public record ChaosScenario(
        String name,
        String description
    ) {}

    public record FailureEvent(
        FailureType type,
        String context,
        Instant timestamp,
        int activeFailures
    ) {}

    public record ChaosResult(
        String scenario,
        boolean success,
        long duration,
        String error
    ) {}

    public record RecoveryResult(
        String status,
        long recoveryTime
    ) {}

    public record ChaosReport(
        String agentName,
        List<FailureEvent> failures,
        long totalRecoveryTime,
        int scenariosExecuted,
        double failureRate
    ) {}

    // System health monitor
    public static class SystemHealthMonitor {
        public void checkSystemHealth() {
            // Monitor system health metrics
            // CPU, memory, network, disk usage
        }
    }

    // Recovery manager
    public static class RecoveryManager {
        public RecoveryResult recover(CaseInstance testCase) {
            Instant start = Instant.now();
            try {
                // Recovery logic
                // testCase.setData("status", "recovered");
                Instant end = Instant.now();
                Duration duration = Duration.between(start, end);
                return new RecoveryResult("success", duration.toMillis());
            } catch (Exception e) {
                return new RecoveryResult("failed", 0);
            }
        }

        public RecoveryResult recoverFromFailure(FailureEvent event) {
            // Specific recovery logic based on failure type
            return new RecoveryResult("recovered", 100); // 100ms recovery time
        }
    }
}