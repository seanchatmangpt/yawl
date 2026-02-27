package org.yawlfoundation.yawl.graalpy.load;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive load testing framework for YAWL performance validation
 */
@TestInstance(Lifecycle.PER_CLASS)
@OrderAnnotation(TestMethodOrder.class)
public class LoadTestingFramework {
    
    private LoadTestRunner loadTestRunner;
    private TestWorkloadGenerator workloadGenerator;
    
    @BeforeAll
    void setup() {
        this.loadTestRunner = new LoadTestRunner();
        this.workloadGenerator = new TestWorkloadGenerator();
    }
    
    @AfterAll
    void cleanup() {
        if (loadTestRunner != null) {
            loadTestRunner.stop();
        }
    }
    
    @Test
    @Order(1)
    void testConcurrentExecution() {
        System.out.println("\n=== Testing Concurrent Execution ===");
        
        // Test with 100+ concurrent workflows
        int iterations = 150;
        int warmup = 20;
        
        ConcurrencyTester.ConcurrencyResult result = loadTestRunner.concurrencyTester.testConcurrentExecution(
            workloadGenerator::simulateWorkflow,
            iterations,
            warmup
        );
        
        // Assertions
        assertNotNull(result);
        assertEquals(iterations, result.totalOperations());
        assertTrue(result.successfulOperations() > 0, "Some operations should succeed");
        
        // Performance assertions
        double speedup = result.speedup();
        assertTrue(speedup > 0, "Speedup should be positive");
        
        System.out.printf("Results: %.2f ops/sec, speedup: %.2f, success rate: %.1f%%%n",
            result.throughput(), speedup, 
            (double) result.successfulOperations() / result.totalOperations() * 100);
    }
    
    @Test
    @Order(2)
    void testMemoryUsage() {
        System.out.println("\n=== Testing Memory Usage ===");
        
        MemoryMonitor monitor = loadTestRunner.memoryMonitor;
        monitor.startMonitoring();
        
        try {
            // Simulate memory usage under load
            long initialMemory = monitor.takeSnapshot().getUsedMB();
            
            // Run intensive workload
            for (int i = 0; i < 1000; i++) {
                workloadGenerator.simulateWorkflow();
                
                // Take memory snapshots periodically
                if (i % 100 == 0) {
                    MemoryMonitor.MemorySnapshot snapshot = monitor.takeSnapshot();
                    long growth = snapshot.getUsedMB() - initialMemory;
                    
                    assertTrue(growth >= 0, "Memory should not decrease");
                    System.out.printf("After %d operations: %d MB used, %d MB growth%n",
                        i, snapshot.getUsedMB(), growth);
                }
            }
            
            MemoryMonitor.MemorySnapshot finalSnapshot = monitor.takeSnapshot();
            long totalGrowth = finalSnapshot.getUsedMB() - initialMemory;
            
            // Check memory growth is acceptable
            assertTrue(PerformanceTargets.isMemoryGrowthAcceptable(totalGrowth),
                String.format("Memory growth %d MB exceeds target %d MB", 
                    totalGrowth, PerformanceTargets.MAX_ACCEPTABLE_MEMORY_GROWTH_MB));
            
            System.out.println("Final memory usage: " + finalSnapshot.getUsedMB() + " MB");
            System.out.println("Memory growth: " + totalGrowth + " MB");
            
        } finally {
            monitor.stopMonitoring();
        }
    }
    
    @Test
    @Order(3)
    void testThroughput() {
        System.out.println("\n=== Testing Throughput ===");
        
        // Test operations per second
        LoadTestRunner.ThroughputResult result = loadTestRunner.testThroughput(
            workloadGenerator::simulateWorkflow
        );
        
        assertNotNull(result);
        assertTrue(result.operations() > 0, "Operations should be completed");
        
        // Check throughput meets targets
        assertTrue(result.meetsTarget(),
            String.format("Throughput %.2f ops/sec below minimum target %d ops/sec",
                result.throughput(), PerformanceTargets.MIN_THROUGHPUT_OPS_PER_SEC));
        
        System.out.printf("Throughput: %.2f ops/sec in %d ms (%d operations)%n",
            result.throughput(), result.durationMs(), result.operations());
    }
    
    @Test
    @Order(4)
    void testLatencyDistribution() {
        System.out.println("\n=== Testing Latency Distribution ===");
        
        LoadTestRunner.LatencyResult result = loadTestRunner.testLatencyDistribution(
            workloadGenerator::simulateWorkflow
        );
        
        assertNotNull(result);
        assertTrue(result.p50() > 0, "P50 latency should be positive");
        assertTrue(result.p95() >= result.p50(), "P95 should be >= P50");
        assertTrue(result.p99() >= result.p95(), "P99 should be >= P95");
        
        // Check latency targets
        assertTrue(result.p50() <= PerformanceTargets.TARGET_P50_LATENCY_MS,
            String.format("P50 latency %d ms exceeds target %d ms", 
                result.p50(), PerformanceTargets.TARGET_P50_LATENCY_MS));
        
        assertTrue(result.p95() <= PerformanceTargets.TARGET_P95_LATENCY_MS,
            String.format("P95 latency %d ms exceeds target %d ms", 
                result.p95(), PerformanceTargets.TARGET_P95_LATENCY_MS));
        
        assertTrue(result.p99() <= PerformanceTargets.TARGET_P99_LATENCY_MS,
            String.format("P99 latency %d ms exceeds target %d ms", 
                result.p99(), PerformanceTargets.TARGET_P99_LATENCY_MS));
        
        System.out.printf("Latency: P50=%dms, P95=%dms, P99=%dms%n",
            result.p50(), result.p95(), result.p99());
    }
    
    @Test
    @Order(5)
    void testDegradation() {
        System.out.println("\n=== Testing Degradation Analysis ===");
        
        LoadTestRunner.DegradationResult result = loadTestRunner.testDegradation(
            workloadGenerator::simulateWorkflow
        );
        
        assertNotNull(result);
        assertTrue(result.baselineThroughput() > 0, "Baseline throughput should be positive");
        assertTrue(result.stressThroughput() > 0, "Stress throughput should be positive");
        
        // Check degradation is acceptable
        assertTrue(result.acceptable(),
            String.format("Performance degradation %.1f%% exceeds maximum allowed %.1f%%",
                result.degradation() * 100, PerformanceTargets.MAX_PERFORMANCE_DEGRADATION * 100));
        
        System.out.printf("Performance: baseline=%.2f ops/sec, stress=%.2f ops/sec, degradation=%.1f%%%n",
            result.baselineThroughput(), result.stressThroughput(), 
            result.degradation() * 100);
    }
    
    @Test
    @Order(6)
    void testLinearScalability() {
        System.out.println("\n=== Testing Linear Scalability ===");
        
        ConcurrencyTester.ScalabilityResult scalabilityResult = 
            loadTestRunner.concurrencyTester.testLinearScalability(
                workloadGenerator::simulateWorkflow,
                64, // Max threads
                50  // Iterations per thread level
            );
        
        assertNotNull(scalabilityResult);
        assertTrue(scalabilityResult.threadCounts().length > 0, "Should have thread count data");
        
        // Check that throughput increases with thread count
        double lastThroughput = 0;
        for (double throughput : scalabilityResult.throughputs()) {
            if (throughput > 0) {
                assertTrue(throughput >= lastThroughput || lastThroughput == 0,
                    "Throughput should be non-decreasing with thread count");
                lastThroughput = throughput;
            }
        }
        
        System.out.println("Scalability test completed");
        System.out.println("Baseline throughput: " + scalabilityResult.baselineThroughput() + " ops/sec");
    }
    
    @Test
    @Order(7)
    void testCompleteLoadTestSuite() {
        System.out.println("\n=== Running Complete Load Test Suite ===");
        
        LoadTestRunner.LoadTestReport report = loadTestRunner.runCompleteLoadTest(
            workloadGenerator::simulateWorkflow
        );
        
        assertNotNull(report);
        assertNotNull(report.generateSummary());
        
        // Validate all components
        if (report.concurrencyResult() != null) {
            assertTrue(report.concurrencyResult().meetsTargets(), 
                "Concurrency test should meet performance targets");
        }
        
        if (report.latencyResult() != null) {
            assertTrue(report.latencyResult().meetsTargets(),
                "Latency test should meet targets");
        }
        
        if (report.degradationResult() != null) {
            assertTrue(report.degradationResult().acceptable(),
                "Performance degradation should be acceptable");
        }
        
        System.out.println("Complete load test suite passed!");
        System.out.println(report.generateSummary());
    }
    
    /**
     * Test workload generator simulates YAWL workflow operations
     */
    private static class TestWorkloadGenerator {
        
        /**
         * Simulates a YAWL workflow operation
         */
        public long simulateWorkflow() {
            long startTime = System.currentTimeMillis();
            
            try {
                // Simulate work item processing
                Thread.sleep(1 + (long) (Math.random() * 5)); // 1-6 ms
                
                // Simulate some computational work
                int result = 0;
                for (int i = 0; i < 1000; i++) {
                    result += i * i;
                }
                
                // Simulate database access
                simulateDatabaseOperation();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Workflow interrupted", e);
            }
            
            return System.currentTimeMillis() - startTime;
        }
        
        /**
         * Simulates database operation
         */
        private void simulateDatabaseOperation() {
            // Simulate DB query time
            try {
                Thread.sleep((long) (Math.random() * 2)); // 0-2 ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Database operation interrupted", e);
            }
        }
    }
}
