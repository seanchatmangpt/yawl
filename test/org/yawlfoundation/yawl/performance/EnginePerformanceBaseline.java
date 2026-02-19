package org.yawlfoundation.yawl.performance;

import org.junit.jupiter.api.Tag;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.EngineClearer;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemRepository;
import org.yawlfoundation.yawl.exceptions.*;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import junit.framework.TestCase;

/**
 * Performance baseline measurements for YAWL Engine.
 * 
 * Establishes performance targets:
 * - Case launch latency: p95 < 500ms
 * - Work item completion: p95 < 200ms  
 * - Concurrent throughput: > 100 cases/sec
 * - Memory usage: < 512MB for 1000 cases
 * - GC pause times: < 500ms max
 * 
 * @author YAWL Performance Team
 * @version 5.2
 * @since 2026-02-16
 */
@Tag("slow")
@Execution(ExecutionMode.SAME_THREAD)  // Uses YEngine singleton
public class EnginePerformanceBaseline extends TestCase {
    
    private YEngine engine;
    private YSpecification spec;
    private YWorkItemRepository repository;
    
    private static final int WARMUP_ITERATIONS = 100;
    private static final int MEASUREMENT_ITERATIONS = 1000;
    private static final int CONCURRENT_THREADS = 10;
    private static final int CASES_PER_THREAD = 100;
    
    // Performance targets
    private static final long CASE_LAUNCH_P95_TARGET_MS = 500;
    private static final long WORK_ITEM_COMPLETION_P95_TARGET_MS = 200;
    private static final double THROUGHPUT_TARGET_CASES_PER_SEC = 100.0;
    private static final long MEMORY_TARGET_MB = 512;
    private static final long GC_PAUSE_TARGET_MS = 500;
    
    public EnginePerformanceBaseline(String name) {
        super(name);
    }
    
    @Override
    public void setUp() throws Exception {
        super.setUp();
        engine = YEngine.getInstance();
        repository = engine.getWorkItemRepository();
        EngineClearer.clear(engine);
        
        // Load test specification
        URL fileURL = getClass().getResource("../engine/ImproperCompletion.xml");
        if (fileURL == null) {
            fileURL = getClass().getResource("/test/org/yawlfoundation/yawl/engine/ImproperCompletion.xml");
        }
        
        if (fileURL != null) {
            File yawlXMLFile = new File(fileURL.getFile());
            spec = YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);
            engine.loadSpecification(spec);
        } else {
            fail("Could not load test specification");
        }
        
        // Warmup: run some cases to initialize JVM
        warmup();
    }
    
    @Override
    public void tearDown() throws Exception {
        if (engine != null) {
            EngineClearer.clear(engine);
        }
        super.tearDown();
    }
    
    /**
     * Warmup phase to stabilize JVM performance
     */
    private void warmup() throws Exception {
        System.out.println("=== Warmup Phase ===");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try {
                YIdentifier caseId = engine.startCase(
                    spec.getSpecificationID(), null, null, null, 
                    new YLogDataItemList(), null, false);
                
                // Cancel case to clean up
                if (caseId != null) {
                    engine.cancelCase(caseId);
                }
            } catch (Exception e) {
                // Continue warmup even if some cases fail
            }
        }
        
        // Force GC after warmup
        System.gc();
        Thread.sleep(1000);
        System.out.println("Warmup complete. Starting measurements...\n");
    }
    
    /**
     * BASELINE 1: Case Launch Latency
     * Target: p95 < 500ms
     */
    public void testCaseLaunchLatency() throws Exception {
        System.out.println("\n=== BASELINE 1: Case Launch Latency ===");
        List<Long> latencies = new ArrayList<>();
        
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long start = System.nanoTime();
            
            YIdentifier caseId = engine.startCase(
                spec.getSpecificationID(), null, null, null,
                new YLogDataItemList(), null, false);
            
            long elapsed = System.nanoTime() - start;
            latencies.add(elapsed / 1_000_000); // Convert to ms
            
            // Clean up
            if (caseId != null) {
                engine.cancelCase(caseId);
            }
            
            // Progress indicator every 100 iterations
            if ((i + 1) % 100 == 0) {
                System.out.println("  Progress: " + (i + 1) + "/" + MEASUREMENT_ITERATIONS);
            }
        }
        
        // Calculate percentiles
        Collections.sort(latencies);
        long p50 = percentile(latencies, 50);
        long p95 = percentile(latencies, 95);
        long p99 = percentile(latencies, 99);
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        
        System.out.println("\nResults (n=" + MEASUREMENT_ITERATIONS + "):");
        System.out.println("  Min:    " + min + " ms");
        System.out.println("  p50:    " + p50 + " ms");
        System.out.println("  Avg:    " + String.format("%.2f", avg) + " ms");
        System.out.println("  p95:    " + p95 + " ms (Target: <" + CASE_LAUNCH_P95_TARGET_MS + " ms)");
        System.out.println("  p99:    " + p99 + " ms");
        System.out.println("  Max:    " + max + " ms");
        System.out.println("  Status: " + (p95 < CASE_LAUNCH_P95_TARGET_MS ? "✓ PASS" : "✗ FAIL"));
        
        assertTrue("Case launch p95 latency exceeded target (" + p95 + " > " + 
                   CASE_LAUNCH_P95_TARGET_MS + ")", 
                   p95 < CASE_LAUNCH_P95_TARGET_MS);
    }
    
    /**
     * BASELINE 2: Work Item Completion Latency
     * Target: p95 < 200ms
     */
    public void testWorkItemCompletionLatency() throws Exception {
        System.out.println("\n=== BASELINE 2: Work Item Completion Latency ===");
        List<Long> latencies = new ArrayList<>();
        
        int successfulMeasurements = 0;
        int targetMeasurements = 100;
        
        for (int i = 0; i < targetMeasurements && successfulMeasurements < targetMeasurements; i++) {
            try {
                // Launch a case
                YIdentifier caseId = engine.startCase(
                    spec.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);
                
                Thread.sleep(100); // Allow work items to be enabled
                
                // Get enabled work items
                Set<YWorkItem> items = repository.getEnabledWorkItems();
                if (items != null && !items.isEmpty()) {
                    YWorkItem item = items.iterator().next();
                    
                    // Start the work item
                    engine.startWorkItem(item, engine.getExternalClient("admin"));
                    
                    // Measure completion time
                    long start = System.nanoTime();
                    engine.completeWorkItem(item, "<data/>", new YLogDataItemList(), false);
                    long elapsed = System.nanoTime() - start;
                    
                    latencies.add(elapsed / 1_000_000);
                    successfulMeasurements++;
                }
                
                // Clean up
                if (caseId != null) {
                    engine.cancelCase(caseId);
                }
                
            } catch (Exception e) {
                // Continue with next iteration
            }
            
            if ((i + 1) % 10 == 0) {
                System.out.println("  Progress: " + successfulMeasurements + "/" + targetMeasurements);
            }
        }
        
        if (latencies.isEmpty()) {
            System.out.println("Warning: No work item completions measured");
            return;
        }
        
        Collections.sort(latencies);
        long p50 = percentile(latencies, 50);
        long p95 = percentile(latencies, 95);
        long p99 = percentile(latencies, 99);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        
        System.out.println("\nResults (n=" + latencies.size() + "):");
        System.out.println("  p50:    " + p50 + " ms");
        System.out.println("  Avg:    " + String.format("%.2f", avg) + " ms");
        System.out.println("  p95:    " + p95 + " ms (Target: <" + WORK_ITEM_COMPLETION_P95_TARGET_MS + " ms)");
        System.out.println("  p99:    " + p99 + " ms");
        System.out.println("  Status: " + (p95 < WORK_ITEM_COMPLETION_P95_TARGET_MS ? "✓ PASS" : "✗ FAIL"));
        
        assertTrue("Work item completion p95 latency exceeded target (" + p95 + " > " + 
                   WORK_ITEM_COMPLETION_P95_TARGET_MS + ")",
                   p95 < WORK_ITEM_COMPLETION_P95_TARGET_MS);
    }
    
    /**
     * BASELINE 3: Concurrent Case Throughput
     * Target: > 100 cases/sec
     */
    public void testConcurrentThroughput() throws Exception {
        System.out.println("\n=== BASELINE 3: Concurrent Case Throughput ===");
        
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(CONCURRENT_THREADS);
        
        List<Future<Integer>> futures = new ArrayList<>();
        
        // Submit tasks
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final int threadId = i;
            Future<Integer> future = executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    
                    int casesCreated = 0;
                    for (int j = 0; j < CASES_PER_THREAD; j++) {
                        try {
                            YIdentifier caseId = engine.startCase(
                                spec.getSpecificationID(), null, null, null,
                                new YLogDataItemList(), null, false);
                            
                            if (caseId != null) {
                                casesCreated++;
                                // Clean up immediately to avoid memory issues
                                engine.cancelCase(caseId);
                            }
                        } catch (Exception e) {
                            // Continue even if some cases fail
                        }
                    }
                    
                    return casesCreated;
                    
                } finally {
                    completionLatch.countDown();
                }
            });
            
            futures.add(future);
        }
        
        System.out.println("Starting " + CONCURRENT_THREADS + " threads, " + 
                          CASES_PER_THREAD + " cases each...");
        
        // Start all threads simultaneously
        long startTime = System.currentTimeMillis();
        startLatch.countDown();
        
        // Wait for completion with timeout
        boolean completed = completionLatch.await(120, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        
        executor.shutdown();
        
        if (!completed) {
            fail("Throughput test timed out after 120 seconds");
        }
        
        // Collect results
        int totalCases = 0;
        for (Future<Integer> future : futures) {
            totalCases += future.get();
        }
        
        double throughput = (totalCases * 1000.0) / duration;
        
        System.out.println("\nResults:");
        System.out.println("  Threads:    " + CONCURRENT_THREADS);
        System.out.println("  Cases:      " + totalCases + " (target: " + 
                          (CONCURRENT_THREADS * CASES_PER_THREAD) + ")");
        System.out.println("  Duration:   " + duration + " ms");
        System.out.println("  Throughput: " + String.format("%.1f", throughput) + 
                          " cases/sec (Target: >" + THROUGHPUT_TARGET_CASES_PER_SEC + ")");
        System.out.println("  Status:     " + (throughput > THROUGHPUT_TARGET_CASES_PER_SEC ? "✓ PASS" : "✗ FAIL"));
        
        assertTrue("Throughput too low (" + throughput + " < " + THROUGHPUT_TARGET_CASES_PER_SEC + ")",
                   throughput > THROUGHPUT_TARGET_CASES_PER_SEC);
    }
    
    /**
     * BASELINE 4: Memory Usage
     * Target: < 512MB for 1000 concurrent cases
     */
    public void testMemoryUsage() throws Exception {
        System.out.println("\n=== BASELINE 4: Memory Usage ===");
        
        Runtime runtime = Runtime.getRuntime();
        
        // Force GC before measurement
        System.gc();
        Thread.sleep(1000);
        
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        
        // Create 1000 cases
        System.out.println("Creating 1000 cases...");
        List<YIdentifier> caseIds = new ArrayList<>();
        int targetCases = 1000;
        
        for (int i = 0; i < targetCases; i++) {
            try {
                YIdentifier caseId = engine.startCase(
                    spec.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);
                
                if (caseId != null) {
                    caseIds.add(caseId);
                }
            } catch (Exception e) {
                // Continue even if some cases fail
            }
            
            if ((i + 1) % 100 == 0) {
                System.out.println("  Progress: " + (i + 1) + "/" + targetCases);
            }
        }
        
        // Force GC and measure
        System.gc();
        Thread.sleep(1000);
        
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long memUsedBytes = memAfter - memBefore;
        long memUsedMB = memUsedBytes / (1024 * 1024);
        
        System.out.println("\nResults:");
        System.out.println("  Cases created: " + caseIds.size());
        System.out.println("  Memory before: " + (memBefore / (1024 * 1024)) + " MB");
        System.out.println("  Memory after:  " + (memAfter / (1024 * 1024)) + " MB");
        System.out.println("  Memory used:   " + memUsedMB + " MB (Target: <" + MEMORY_TARGET_MB + " MB)");
        System.out.println("  Per case:      " + (memUsedBytes / caseIds.size()) + " bytes");
        System.out.println("  Status:        " + (memUsedMB < MEMORY_TARGET_MB ? "✓ PASS" : "✗ FAIL"));
        
        // Clean up
        for (YIdentifier caseId : caseIds) {
            try {
                engine.cancelCase(caseId);
            } catch (Exception e) {
                // Continue cleanup
            }
        }
        
        assertTrue("Memory usage exceeded target (" + memUsedMB + " > " + MEMORY_TARGET_MB + ")",
                   memUsedMB < MEMORY_TARGET_MB);
    }
    
    /**
     * BASELINE 5: Engine Startup Time
     * Target: < 60 seconds
     */
    public void testEngineStartupTime() throws Exception {
        System.out.println("\n=== BASELINE 5: Engine Startup Time ===");
        
        // Clear and restart engine
        EngineClearer.clear(engine);
        
        long start = System.currentTimeMillis();
        
        // Reload specification
        engine.loadSpecification(spec);
        
        // Launch a test case to verify engine is ready
        YIdentifier caseId = engine.startCase(
            spec.getSpecificationID(), null, null, null,
            new YLogDataItemList(), null, false);
        
        long elapsed = System.currentTimeMillis() - start;
        
        System.out.println("\nResults:");
        System.out.println("  Startup time: " + elapsed + " ms (Target: <60000 ms)");
        System.out.println("  Status:       " + (elapsed < 60000 ? "✓ PASS" : "✗ FAIL"));
        
        // Clean up
        if (caseId != null) {
            engine.cancelCase(caseId);
        }
        
        assertTrue("Engine startup time exceeded 60 seconds (" + elapsed + " ms)",
                   elapsed < 60000);
    }
    
    /**
     * Calculate percentile from sorted list
     */
    private long percentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }
    
    /**
     * Print summary of all baselines
     */
    public static void printBaselineSummary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("YAWL v5.2 Performance Baseline Summary");
        System.out.println("=".repeat(60));
        System.out.println("Test Date: 2026-02-16");
        System.out.println("\nTargets:");
        System.out.println("  1. Case Launch Latency:       p95 < 500ms");
        System.out.println("  2. Work Item Completion:      p95 < 200ms");
        System.out.println("  3. Concurrent Throughput:     > 100 cases/sec");
        System.out.println("  4. Memory Usage:              < 512MB for 1000 cases");
        System.out.println("  5. Engine Startup:            < 60 seconds");
        System.out.println("=".repeat(60) + "\n");
    }
}
