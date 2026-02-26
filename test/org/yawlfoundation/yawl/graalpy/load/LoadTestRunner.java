package org.yawlfoundation.yawl.graalpy.load;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Main orchestrator for load testing framework
 */
public class LoadTestRunner {
    
    private final MetricsCollector metrics;
    private final MemoryMonitor memoryMonitor;
    private final ConcurrencyTester concurrencyTester;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public LoadTestRunner() {
        this.metrics = new MetricsCollector(100);
        this.memoryMonitor = new MemoryMonitor();
        this.concurrencyTester = new ConcurrencyTester(
            Runtime.getRuntime().availableProcessors() * 2,
            metrics
        );
    }
    
    /**
     * Runs the complete load test suite
     */
    public LoadTestReport runCompleteLoadTest(Supplier<Long> workload) {
        running.set(true);
        LoadTestReport.Builder reportBuilder = new LoadTestReport.Builder();
        
        try {
            System.out.println("Starting load test suite...");
            
            // Test 1: Concurrent execution
            System.out.println("Running concurrent execution test...");
            ConcurrencyTester.ConcurrencyResult concurrencyResult = 
                testConcurrentExecution(workload);
            reportBuilder.withConcurrencyResult(concurrencyResult);
            
            // Test 2: Memory usage
            System.out.println("Running memory usage test...");
            MemoryMonitor.MemorySnapshot memoryResult = 
                testMemoryUsage(workload);
            reportBuilder.withMemoryResult(memoryResult);
            
            // Test 3: Throughput
            System.out.println("Running throughput test...");
            ThroughputResult throughputResult = 
                testThroughput(workload);
            reportBuilder.withThroughputResult(throughputResult);
            
            // Test 4: Latency distribution
            System.out.println("Running latency distribution test...");
            LatencyResult latencyResult = 
                testLatencyDistribution(workload);
            reportBuilder.withLatencyResult(latencyResult);
            
            // Test 5: Degradation analysis
            System.out.println("Running degradation analysis...");
            DegradationResult degradationResult = 
                testDegradation(workload);
            reportBuilder.withDegradationResult(degradationResult);
            
            // Build final report
            LoadTestReport report = reportBuilder.build();
            
            System.out.println("Load test completed. Summary:");
            System.out.println(report.generateSummary());
            
            return report;
            
        } finally {
            running.set(false);
            memoryMonitor.stopMonitoring();
            concurrencyTester.shutdown();
        }
    }
    
    /**
     * Tests concurrent execution performance
     */
    private ConcurrencyTester.ConcurrencyResult testConcurrentExecution(Supplier<Long> workload) {
        memoryMonitor.startMonitoring();
        
        try {
            return concurrencyTester.testConcurrentExecution(
                workload,
                PerformanceTargets.MEASUREMENT_ITERATIONS,
                PerformanceTargets.WARMUP_ITERATIONS
            );
        } finally {
            memoryMonitor.stopMonitoring();
        }
    }
    
    /**
     * Tests memory usage under load
     */
    private MemoryMonitor.MemorySnapshot testMemoryUsage(Supplier<Long> workload) {
        memoryMonitor.startMonitoring();
        
        try {
            // Run workload for sustained period
            long endTime = System.currentTimeMillis() + 
                (PerformanceTargets.MEASUREMENT_ITERATIONS * 10); // 10ms per operation
            
            int operations = 0;
            while (System.currentTimeMillis() < endTime && running.get()) {
                try {
                    workload.get();
                    operations++;
                    
                    // Take memory snapshot periodically
                    if (operations % 10 == 0) {
                        MemoryMonitor.MemorySnapshot snapshot = memoryMonitor.takeSnapshot();
                        metrics.recordMemorySnapshot(operations / 10, snapshot.getUsedBytes());
                    }
                    
                    Thread.sleep(1);
                } catch (Exception e) {
                    metrics.recordFailure();
                }
            }
            
            return memoryMonitor.takeSnapshot();
            
        } finally {
            memoryMonitor.stopMonitoring();
        }
    }
    
    /**
     * Tests throughput performance
     */
    private ThroughputResult testThroughput(Supplier<Long> workload) {
        memoryMonitor.startMonitoring();
        
        try {
            long testDuration = 30 * 1000; // 30 seconds
            long startTime = System.currentTimeMillis();
            int operations = 0;
            
            while (System.currentTimeMillis() - startTime < testDuration && running.get()) {
                try {
                    workload.get();
                    operations++;
                    metrics.recordSuccess();
                } catch (Exception e) {
                    metrics.recordFailure();
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            double throughput = (double) operations / (duration / 1000.0);
            boolean meetsTarget = throughput >= PerformanceTargets.MIN_THROUGHPUT_OPS_PER_SEC;
            
            return new ThroughputResult(operations, duration, throughput, meetsTarget);
            
        } finally {
            memoryMonitor.stopMonitoring();
        }
    }
    
    /**
     * Tests latency distribution
     */
    private LatencyResult testLatencyDistribution(Supplier<Long> workload) {
        memoryMonitor.startMonitoring();
        
        try {
            // Collect latency data
            for (int i = 0; i < PerformanceTargets.MEASUREMENT_ITERATIONS && running.get(); i++) {
                try {
                    long startTime = System.currentTimeMillis();
                    workload.get();
                    long duration = System.currentTimeMillis() - startTime;
                    
                    metrics.recordExecutionTime(duration);
                    metrics.recordSuccess();
                    
                } catch (Exception e) {
                    metrics.recordFailure();
                }
            }
            
            // Calculate percentiles
            long p50 = metrics.calculateP50Latency();
            long p95 = metrics.calculateP95Latency();
            long p99 = metrics.calculateP99Latency();
            
            boolean meetsTargets = 
                p50 <= PerformanceTargets.TARGET_P50_LATENCY_MS &&
                p95 <= PerformanceTargets.TARGET_P95_LATENCY_MS &&
                p99 <= PerformanceTargets.TARGET_P99_LATENCY_MS;
            
            return new LatencyResult(p50, p95, p99, meetsTargets);
            
        } finally {
            memoryMonitor.stopMonitoring();
        }
    }
    
    /**
     * Tests performance degradation under stress
     */
    private DegradationResult testDegradation(Supplier<Long> workload) {
        memoryMonitor.startMonitoring();
        
        try {
            // Run baseline at normal load
            ConcurrencyTester.ConcurrencyResult baseline = 
                testConcurrentExecution(workload);
            
            // Run at stress load (2x normal)
            int stressIterations = PerformanceTargets.MEASUREMENT_ITERATIONS * 
                PerformanceTargets.STRESS_TEST_MULTIPLIER;
            
            ConcurrencyTester.ConcurrencyResult stress = 
                concurrencyTester.testConcurrentExecution(
                    workload,
                    stressIterations,
                    PerformanceTargets.WARMUP_ITERATIONS
                );
            
            // Calculate degradation
            double degradation = calculateDegradation(baseline.throughput(), stress.throughput());
            boolean acceptable = PerformanceTargets.isPerformanceDegradationAcceptable(degradation);
            
            return new DegradationResult(
                baseline.throughput(),
                stress.throughput(),
                degradation,
                acceptable
            );
            
        } finally {
            memoryMonitor.stopMonitoring();
        }
    }
    
    /**
     * Calculates performance degradation percentage
     */
    private double calculateDegradation(double baselineThroughput, double stressThroughput) {
        if (baselineThroughput == 0) return Double.MAX_VALUE;
        
        // Degradation is calculated as (baseline - stress) / baseline
        // Positive values indicate performance degradation
        double degradation = (baselineThroughput - stressThroughput) / baselineThroughput;
        return Math.max(0, degradation);
    }
    
    /**
     * Stops all running tests
     */
    public void stop() {
        running.set(false);
        memoryMonitor.stopMonitoring();
        concurrencyTester.shutdown();
    }
    
    /**
     * Load test result container
     */
    public static class LoadTestReport {
        private final ConcurrencyTester.ConcurrencyResult concurrencyResult;
        private final MemoryMonitor.MemorySnapshot memoryResult;
        private final ThroughputResult throughputResult;
        private final LatencyResult latencyResult;
        private final DegradationResult degradationResult;
        
        private LoadTestReport(Builder builder) {
            this.concurrencyResult = builder.concurrencyResult;
            this.memoryResult = builder.memoryResult;
            this.throughputResult = builder.throughputResult;
            this.latencyResult = builder.latencyResult;
            this.degradationResult = builder.degradationResult;
        }
        
        public Builder toBuilder() {
            return new Builder(this);
        }
        
        public String generateSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Load Test Summary ===\n");
            
            if (concurrencyResult != null) {
                sb.append(String.format("Concurrency: %.2f ops/sec, speedup: %.2f\n", 
                    concurrencyResult.throughput(), concurrencyResult.speedup()));
            }
            
            if (memoryResult != null) {
                sb.append(String.format("Memory: %d MB used, %d MB growth\n", 
                    memoryResult.getUsedMB(), memoryResult.getUsedMB()));
            }
            
            if (throughputResult != null) {
                sb.append(String.format("Throughput: %.2f ops/sec (target: >= %d)\n", 
                    throughputResult.throughput(), PerformanceTargets.MIN_THROUGHPUT_OPS_PER_SEC));
            }
            
            if (latencyResult != null) {
                sb.append(String.format("Latency: P50=%dms, P95=%dms, P99=%dms\n", 
                    latencyResult.p50(), latencyResult.p95(), latencyResult.p99()));
            }
            
            if (degradationResult != null) {
                sb.append(String.format("Degradation: %.1f%% (acceptable: %s)\n", 
                    degradationResult.degradation() * 100, degradationResult.acceptable()));
            }
            
            return sb.toString();
        }
        
        public static class Builder {
            private ConcurrencyTester.ConcurrencyResult concurrencyResult;
            private MemoryMonitor.MemorySnapshot memoryResult;
            private ThroughputResult throughputResult;
            private LatencyResult latencyResult;
            private DegradationResult degradationResult;
            
            public Builder() {}
            
            public Builder(LoadTestReport report) {
                this.concurrencyResult = report.concurrencyResult;
                this.memoryResult = report.memoryResult;
                this.throughputResult = report.throughputResult;
                this.latencyResult = report.latencyResult;
                this.degradationResult = report.degradationResult;
            }
            
            public Builder withConcurrencyResult(ConcurrencyTester.ConcurrencyResult result) {
                this.concurrencyResult = result;
                return this;
            }
            
            public Builder withMemoryResult(MemoryMonitor.MemorySnapshot result) {
                this.memoryResult = result;
                return this;
            }
            
            public Builder withThroughputResult(ThroughputResult result) {
                this.throughputResult = result;
                return this;
            }
            
            public Builder withLatencyResult(LatencyResult result) {
                this.latencyResult = result;
                return this;
            }
            
            public Builder withDegradationResult(DegradationResult result) {
                this.degradationResult = result;
                return this;
            }
            
            public LoadTestReport build() {
                return new LoadTestReport(this);
            }
        }
    }
    
    /**
     * Throughput test result
     */
    public record ThroughputResult(
        int operations,
        long durationMs,
        double throughput,
        boolean meetsTarget
    ) {}
    
    /**
     * Latency test result
     */
    public record LatencyResult(
        long p50,
        long p95,
        long p99,
        boolean meetsTargets
    ) {}
    
    /**
     * Degradation test result
     */
    public record DegradationResult(
        double baselineThroughput,
        double stressThroughput,
        double degradation,
        boolean acceptable
    ) {}
}
