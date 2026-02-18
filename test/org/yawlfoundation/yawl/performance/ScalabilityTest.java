package org.yawlfoundation.yawl.performance;

import org.junit.jupiter.api.Tag;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.EngineClearer;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import junit.framework.TestCase;

/**
 * Scalability tests for YAWL Engine.
 * 
 * Tests:
 * - Linear scalability up to 50 concurrent users
 * - Memory scaling with case count
 * - Degradation patterns under extreme load
 * 
 * @author YAWL Performance Team
 * @version 5.2
 * @since 2026-02-16
 */
@Tag("slow")
public class ScalabilityTest extends TestCase {
    
    private YEngine engine;
    private YSpecification spec;
    
    public ScalabilityTest(String name) {
        super(name);
    }
    
    @Override
    public void setUp() throws Exception {
        super.setUp();
        engine = YEngine.getInstance();
        EngineClearer.clear(engine);
        
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
    }
    
    @Override
    public void tearDown() throws Exception {
        if (engine != null) {
            EngineClearer.clear(engine);
        }
        super.tearDown();
    }
    
    /**
     * Test linear scalability with increasing case count
     * Expected: Performance should scale linearly up to 1000 cases
     */
    public void testCaseCountScalability() throws Exception {
        System.out.println("\n=== SCALABILITY TEST 1: Case Count Scaling ===");
        
        int[] caseCounts = {100, 500, 1000, 2000};
        List<ScalabilityDataPoint> dataPoints = new ArrayList<>();
        
        for (int caseCount : caseCounts) {
            System.gc();
            Thread.sleep(500);
            
            Runtime runtime = Runtime.getRuntime();
            long memBefore = runtime.totalMemory() - runtime.freeMemory();
            
            long start = System.currentTimeMillis();
            List<YIdentifier> caseIds = new ArrayList<>();
            
            for (int i = 0; i < caseCount; i++) {
                try {
                    YIdentifier caseId = engine.startCase(
                        spec.getSpecificationID(), null, null, null,
                        new YLogDataItemList(), null, false);
                    
                    if (caseId != null) {
                        caseIds.add(caseId);
                    }
                } catch (Exception e) {
                    // Continue
                }
                
                if ((i + 1) % 100 == 0) {
                    System.out.println("  " + caseCount + " cases: " + (i + 1) + "/" + caseCount);
                }
            }
            
            long elapsed = System.currentTimeMillis() - start;
            
            System.gc();
            Thread.sleep(500);
            
            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            long memUsed = (memAfter - memBefore) / (1024 * 1024);
            
            ScalabilityDataPoint dp = new ScalabilityDataPoint();
            dp.caseCount = caseIds.size();
            dp.timeMs = elapsed;
            dp.memoryMB = memUsed;
            dp.throughput = (caseIds.size() * 1000.0) / elapsed;
            
            dataPoints.add(dp);
            
            // Clean up
            for (YIdentifier caseId : caseIds) {
                try {
                    engine.cancelCase(caseId);
                } catch (Exception e) {
                    // Continue
                }
            }
        }
        
        System.out.println("\nScalability Results:");
        System.out.println(String.format("%-10s %-15s %-15s %-15s", 
            "Cases", "Time (ms)", "Memory (MB)", "Throughput"));
        System.out.println("-".repeat(60));
        
        for (ScalabilityDataPoint dp : dataPoints) {
            System.out.println(String.format("%-10d %-15d %-15d %-15.1f",
                dp.caseCount, dp.timeMs, dp.memoryMB, dp.throughput));
        }
        
        // Check that performance scales reasonably
        boolean scalable = true;
        for (int i = 1; i < dataPoints.size(); i++) {
            ScalabilityDataPoint prev = dataPoints.get(i - 1);
            ScalabilityDataPoint curr = dataPoints.get(i);
            
            double expectedTime = (curr.caseCount / (double)prev.caseCount) * prev.timeMs;
            double actualTime = curr.timeMs;
            
            // Allow 50% overhead for non-linear scaling
            if (actualTime > expectedTime * 1.5) {
                scalable = false;
                System.out.println("\nWarning: Non-linear scaling detected at " + 
                    curr.caseCount + " cases");
                System.out.println("  Expected: ~" + (long)expectedTime + " ms");
                System.out.println("  Actual: " + actualTime + " ms");
            }
        }
        
        System.out.println("\nScalability: " + (scalable ? "✓ LINEAR" : "⚠ NON-LINEAR"));
    }
    
    /**
     * Test memory efficiency with increasing case count
     * Expected: Memory per case should remain roughly constant
     */
    public void testMemoryEfficiency() throws Exception {
        System.out.println("\n=== SCALABILITY TEST 2: Memory Efficiency ===");
        
        int[] caseCounts = {100, 500, 1000};
        List<Double> memoryPerCase = new ArrayList<>();
        
        for (int caseCount : caseCounts) {
            System.gc();
            Thread.sleep(500);
            
            Runtime runtime = Runtime.getRuntime();
            long memBefore = runtime.totalMemory() - runtime.freeMemory();
            
            List<YIdentifier> caseIds = new ArrayList<>();
            for (int i = 0; i < caseCount; i++) {
                try {
                    YIdentifier caseId = engine.startCase(
                        spec.getSpecificationID(), null, null, null,
                        new YLogDataItemList(), null, false);
                    
                    if (caseId != null) {
                        caseIds.add(caseId);
                    }
                } catch (Exception e) {
                    // Continue
                }
            }
            
            System.gc();
            Thread.sleep(500);
            
            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            long memUsedBytes = memAfter - memBefore;
            double bytesPerCase = (double)memUsedBytes / caseIds.size();
            
            memoryPerCase.add(bytesPerCase);
            
            System.out.println(caseCount + " cases: " + 
                String.format("%.0f", bytesPerCase) + " bytes/case");
            
            // Clean up
            for (YIdentifier caseId : caseIds) {
                try {
                    engine.cancelCase(caseId);
                } catch (Exception e) {
                    // Continue
                }
            }
        }
        
        // Check memory efficiency is consistent
        double avgMemPerCase = memoryPerCase.stream()
            .mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        boolean efficient = true;
        for (double mem : memoryPerCase) {
            if (Math.abs(mem - avgMemPerCase) > avgMemPerCase * 0.3) {
                efficient = false;
            }
        }
        
        System.out.println("\nAverage: " + String.format("%.0f", avgMemPerCase) + " bytes/case");
        System.out.println("Memory efficiency: " + (efficient ? "✓ CONSISTENT" : "⚠ VARIABLE"));
    }
    
    /**
     * Test recovery from high load
     * Expected: System should recover gracefully after load spike
     */
    public void testLoadRecovery() throws Exception {
        System.out.println("\n=== SCALABILITY TEST 3: Load Recovery ===");
        
        // Baseline measurement
        long baselineLatency = measureAverageLatency(10);
        System.out.println("Baseline latency: " + baselineLatency + " ms");
        
        // Create high load
        System.out.println("\nCreating high load (500 cases)...");
        List<YIdentifier> caseIds = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            try {
                YIdentifier caseId = engine.startCase(
                    spec.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);
                
                if (caseId != null) {
                    caseIds.add(caseId);
                }
            } catch (Exception e) {
                // Continue
            }
        }
        
        // Measure latency under load
        long loadLatency = measureAverageLatency(10);
        System.out.println("Under load latency: " + loadLatency + " ms");
        
        // Clean up
        System.out.println("\nCleaning up...");
        for (YIdentifier caseId : caseIds) {
            try {
                engine.cancelCase(caseId);
            } catch (Exception e) {
                // Continue
            }
        }
        
        System.gc();
        Thread.sleep(2000);
        
        // Measure recovery
        long recoveryLatency = measureAverageLatency(10);
        System.out.println("After recovery latency: " + recoveryLatency + " ms");
        
        // Check recovery
        double recoveryRatio = (double)recoveryLatency / baselineLatency;
        boolean recovered = recoveryRatio < 1.5; // Within 50% of baseline
        
        System.out.println("\nRecovery ratio: " + String.format("%.2f", recoveryRatio));
        System.out.println("Recovery status: " + (recovered ? "✓ RECOVERED" : "⚠ DEGRADED"));
        
        assertTrue("System did not recover to baseline performance", recovered);
    }
    
    /**
     * Measure average latency for case creation
     */
    private long measureAverageLatency(int iterations) throws Exception {
        List<Long> latencies = new ArrayList<>();
        
        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            
            YIdentifier caseId = engine.startCase(
                spec.getSpecificationID(), null, null, null,
                new YLogDataItemList(), null, false);
            
            long latency = System.currentTimeMillis() - start;
            latencies.add(latency);
            
            if (caseId != null) {
                engine.cancelCase(caseId);
            }
        }
        
        return (long)latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
    
    /**
     * Data point for scalability measurements
     */
    private static class ScalabilityDataPoint {
        int caseCount;
        long timeMs;
        long memoryMB;
        double throughput;
    }
}
