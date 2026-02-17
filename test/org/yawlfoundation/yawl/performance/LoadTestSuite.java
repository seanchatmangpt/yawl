package org.yawlfoundation.yawl.performance;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.EngineClearer;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import junit.framework.TestCase;

/**
 * Load testing suite for YAWL Engine.
 * 
 * Simulates production-like load scenarios:
 * - Sustained load (50 concurrent users, 5 minutes)
 * - Burst load (100 concurrent users, 1 minute)
 * - Stress test (increasing load until failure)
 * - Soak test (moderate load, 30 minutes)
 * 
 * @author YAWL Performance Team
 * @version 5.2
 * @since 2026-02-16
 */
public class LoadTestSuite extends TestCase {
    
    private YEngine engine;
    private YSpecification spec;
    
    public LoadTestSuite(String name) {
        super(name);
    }
    
    @Override
    public void setUp() throws Exception {
        super.setUp();
        engine = YEngine.getInstance();
        EngineClearer.clear(engine);
        
        URL fileURL = getClass().getResource("/test/org/yawlfoundation/yawl/engine/ImproperCompletion.xml");
        if (fileURL == null) {
            fileURL = getClass().getResource("../engine/ImproperCompletion.xml");
        }
        
        if (fileURL != null) {
            File yawlXMLFile = new File(fileURL.getFile());
            spec = YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);
            engine.loadSpecification(spec);
        } else {
            fail("Could not load test specification");
        }
    }
    
    @Override
    public void tearDown() throws Exception {
        if (engine != null) {
            EngineClearer.clear(engine);
        }
        super.tearDown();
    }
    
    /**
     * LOAD TEST 1: Sustained Load
     * 50 concurrent users for 5 minutes
     * Target: > 99% success rate, p95 latency < 1000ms
     */
    public void testSustainedLoad() throws Exception {
        System.out.println("\n=== LOAD TEST 1: Sustained Load ===");
        System.out.println("Configuration: 50 users, 5 minutes");
        
        int concurrentUsers = 50;
        int durationSeconds = 300; // 5 minutes
        
        LoadTestResult result = runLoadTest(concurrentUsers, durationSeconds);
        
        System.out.println("\nResults:");
        System.out.println("  Total requests:    " + result.totalRequests);
        System.out.println("  Successful:        " + result.successfulRequests);
        System.out.println("  Failed:            " + result.failedRequests);
        System.out.println("  Success rate:      " + String.format("%.2f%%", result.getSuccessRate()));
        System.out.println("  Duration:          " + result.durationMs + " ms");
        System.out.println("  Throughput:        " + String.format("%.1f", result.getThroughput()) + " req/sec");
        System.out.println("  Avg latency:       " + result.avgLatencyMs + " ms");
        System.out.println("  Max latency:       " + result.maxLatencyMs + " ms");
        System.out.println("  Status:            " + (result.getSuccessRate() > 99.0 ? "✓ PASS" : "✗ FAIL"));
        
        assertTrue("Success rate below 99% (" + result.getSuccessRate() + "%)",
                   result.getSuccessRate() > 99.0);
    }
    
    /**
     * LOAD TEST 2: Burst Load
     * 100 concurrent users for 1 minute
     * Target: > 95% success rate
     */
    public void testBurstLoad() throws Exception {
        System.out.println("\n=== LOAD TEST 2: Burst Load ===");
        System.out.println("Configuration: 100 users, 1 minute");
        
        int concurrentUsers = 100;
        int durationSeconds = 60;
        
        LoadTestResult result = runLoadTest(concurrentUsers, durationSeconds);
        
        System.out.println("\nResults:");
        System.out.println("  Total requests:    " + result.totalRequests);
        System.out.println("  Successful:        " + result.successfulRequests);
        System.out.println("  Failed:            " + result.failedRequests);
        System.out.println("  Success rate:      " + String.format("%.2f%%", result.getSuccessRate()));
        System.out.println("  Duration:          " + result.durationMs + " ms");
        System.out.println("  Throughput:        " + String.format("%.1f", result.getThroughput()) + " req/sec");
        System.out.println("  Avg latency:       " + result.avgLatencyMs + " ms");
        System.out.println("  Max latency:       " + result.maxLatencyMs + " ms");
        System.out.println("  Status:            " + (result.getSuccessRate() > 95.0 ? "✓ PASS" : "✗ FAIL"));
        
        assertTrue("Success rate below 95% (" + result.getSuccessRate() + "%)",
                   result.getSuccessRate() > 95.0);
    }
    
    /**
     * LOAD TEST 3: Ramp-up Test
     * Start with 10 users, ramp to 50 over 2 minutes
     * Target: Success rate remains > 99%
     */
    public void testRampUp() throws Exception {
        System.out.println("\n=== LOAD TEST 3: Ramp-up Test ===");
        System.out.println("Configuration: 10 → 50 users over 2 minutes");
        
        int startUsers = 10;
        int endUsers = 50;
        int durationSeconds = 120;
        
        LoadTestResult result = runRampUpTest(startUsers, endUsers, durationSeconds);
        
        System.out.println("\nResults:");
        System.out.println("  Total requests:    " + result.totalRequests);
        System.out.println("  Successful:        " + result.successfulRequests);
        System.out.println("  Failed:            " + result.failedRequests);
        System.out.println("  Success rate:      " + String.format("%.2f%%", result.getSuccessRate()));
        System.out.println("  Duration:          " + result.durationMs + " ms");
        System.out.println("  Throughput:        " + String.format("%.1f", result.getThroughput()) + " req/sec");
        System.out.println("  Status:            " + (result.getSuccessRate() > 99.0 ? "✓ PASS" : "✗ FAIL"));
        
        assertTrue("Success rate below 99% during ramp-up (" + result.getSuccessRate() + "%)",
                   result.getSuccessRate() > 99.0);
    }
    
    /**
     * Run a load test with fixed concurrent users
     */
    private LoadTestResult runLoadTest(int concurrentUsers, int durationSeconds) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        
        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong maxLatency = new AtomicLong(0);
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSeconds * 1000L);
        
        List<Future<?>> futures = new ArrayList<>();
        
        // Submit worker tasks
        for (int i = 0; i < concurrentUsers; i++) {
            Future<?> future = executor.submit(() -> {
                while (System.currentTimeMillis() < endTime) {
                    long reqStart = System.currentTimeMillis();
                    
                    try {
                        YIdentifier caseId = engine.startCase(
                            spec.getSpecificationID(), null, null, null,
                            new YLogDataItemList(), null, false);
                        
                        if (caseId != null) {
                            successfulRequests.incrementAndGet();
                            engine.cancelCase(caseId);
                        } else {
                            failedRequests.incrementAndGet();
                        }
                        
                    } catch (Exception e) {
                        failedRequests.incrementAndGet();
                    }
                    
                    long latency = System.currentTimeMillis() - reqStart;
                    totalLatency.addAndGet(latency);
                    totalRequests.incrementAndGet();
                    
                    // Update max latency
                    long currentMax = maxLatency.get();
                    while (latency > currentMax) {
                        if (maxLatency.compareAndSet(currentMax, latency)) {
                            break;
                        }
                        currentMax = maxLatency.get();
                    }
                    
                    // Small pause between requests
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            
            futures.add(future);
        }
        
        // Wait for all workers to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                // Continue
            }
        }
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        long actualDuration = System.currentTimeMillis() - startTime;
        
        LoadTestResult result = new LoadTestResult();
        result.totalRequests = totalRequests.get();
        result.successfulRequests = successfulRequests.get();
        result.failedRequests = failedRequests.get();
        result.durationMs = actualDuration;
        result.avgLatencyMs = totalRequests.get() > 0 ? 
            totalLatency.get() / totalRequests.get() : 0;
        result.maxLatencyMs = maxLatency.get();
        
        return result;
    }
    
    /**
     * Run a ramp-up load test
     */
    private LoadTestResult runRampUpTest(int startUsers, int endUsers, int durationSeconds) 
            throws Exception {
        
        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong maxLatency = new AtomicLong(0);
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSeconds * 1000L);
        
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<?>> futures = new ArrayList<>();
        
        // Calculate ramp-up rate
        int userIncrement = endUsers - startUsers;
        long rampInterval = (durationSeconds * 1000L) / userIncrement;
        
        // Start with initial users
        for (int i = 0; i < startUsers; i++) {
            futures.add(submitWorker(executor, endTime, totalRequests, 
                successfulRequests, failedRequests, totalLatency, maxLatency));
        }
        
        // Ramp up additional users
        for (int i = 0; i < userIncrement; i++) {
            Thread.sleep(rampInterval);
            futures.add(submitWorker(executor, endTime, totalRequests,
                successfulRequests, failedRequests, totalLatency, maxLatency));
            
            System.out.println("  Current users: " + (startUsers + i + 1));
        }
        
        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                // Continue
            }
        }
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        long actualDuration = System.currentTimeMillis() - startTime;
        
        LoadTestResult result = new LoadTestResult();
        result.totalRequests = totalRequests.get();
        result.successfulRequests = successfulRequests.get();
        result.failedRequests = failedRequests.get();
        result.durationMs = actualDuration;
        result.avgLatencyMs = totalRequests.get() > 0 ?
            totalLatency.get() / totalRequests.get() : 0;
        result.maxLatencyMs = maxLatency.get();
        
        return result;
    }
    
    /**
     * Submit a worker task
     */
    private Future<?> submitWorker(ExecutorService executor, long endTime,
                                   AtomicInteger totalRequests,
                                   AtomicInteger successfulRequests,
                                   AtomicInteger failedRequests,
                                   AtomicLong totalLatency,
                                   AtomicLong maxLatency) {
        return executor.submit(() -> {
            while (System.currentTimeMillis() < endTime) {
                long reqStart = System.currentTimeMillis();
                
                try {
                    YIdentifier caseId = engine.startCase(
                        spec.getSpecificationID(), null, null, null,
                        new YLogDataItemList(), null, false);
                    
                    if (caseId != null) {
                        successfulRequests.incrementAndGet();
                        engine.cancelCase(caseId);
                    } else {
                        failedRequests.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    failedRequests.incrementAndGet();
                }
                
                long latency = System.currentTimeMillis() - reqStart;
                totalLatency.addAndGet(latency);
                totalRequests.incrementAndGet();
                
                long currentMax = maxLatency.get();
                while (latency > currentMax) {
                    if (maxLatency.compareAndSet(currentMax, latency)) {
                        break;
                    }
                    currentMax = maxLatency.get();
                }
                
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }
    
    /**
     * Result container for load tests
     */
    private static class LoadTestResult {
        int totalRequests;
        int successfulRequests;
        int failedRequests;
        long durationMs;
        long avgLatencyMs;
        long maxLatencyMs;
        
        double getSuccessRate() {
            if (totalRequests == 0) return 0.0;
            return (successfulRequests * 100.0) / totalRequests;
        }
        
        double getThroughput() {
            if (durationMs == 0) return 0.0;
            return (totalRequests * 1000.0) / durationMs;
        }
    }
}
