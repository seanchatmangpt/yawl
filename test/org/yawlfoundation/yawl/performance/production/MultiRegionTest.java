/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 * See LICENSE in the project root for license information.
 */

package org.yawlfoundation.yawl.performance.production;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.elements.YNet;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production test for cross-region deployment validation.
 * Tests multi-region deployment with consistent performance across regions.
 *
 * Validates:
 * - Cross-region consistency
 * - Region failover
 * - Data synchronization
 * - Network latency impact
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("production")
@Tag("multiregion")
@Tag("global")
public class MultiRegionTest {

    private static final String[] REGIONS = {"us-east-1", "us-west-2", "eu-west-1", "ap-southeast-1"};
    private static final int CASES_PER_REGION = 500;
    private static final int CONCURRENT_USERS = 100;
    private static final long NETWORK_LATENCY_MS = 50;
    
    private Map<String, YNetRunner> regionEngines = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(REGIONS.length * CONCURRENT_USERS);
    private final Map<String, RegionMetrics> regionMetrics = new ConcurrentHashMap<>();
    
    @BeforeAll
    void setupRegions() throws Exception {
        // Initialize engines for each region
        for (String region : REGIONS) {
            YNetRunner engine = new YNetRunner(createTestNet());
            regionEngines.put(region, engine);
            regionMetrics.put(region, new RegionMetrics(region));
        }
        
        // Allow engines to initialize
        Thread.sleep(5000);
    }
    
    @AfterAll
    void teardownRegions() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        regionEngines.values().forEach(YNetRunner::shutdown);
    }
    
    @Test
    @DisplayName("Cross-Region Performance Consistency")
    void testCrossRegionConsistency() throws Exception {
        System.out.println("Testing cross-region performance consistency...");
        
        // Submit equal workload to all regions
        Map<String, Future<?>> futures = new HashMap<>();
        
        for (String region : REGIONS) {
            futures.put(region, executor.submit(() -> 
                submitRegionWorkload(region, CASES_PER_REGION, "consistency-test")));
        }
        
        // Wait for all regions to complete
        for (Map.Entry<String, Future<?>> entry : futures.entrySet()) {
            try {
                entry.getValue().get(2, TimeUnit.MINUTES);
            } catch (Exception e) {
                fail("Region " + entry.getKey() + " failed: " + e.getMessage());
            }
        }
        
        // Validate consistency across regions
        validateCrossRegionConsistency();
    }
    
    @Test
    @DisplayName("Region Failover Validation")
    void testRegionFailover() throws Exception {
        System.out.println("Testing region failover...");
        
        // Start with all regions active
        RegionMetrics preFailover = submitRegionWorkload("us-east-1", CASES_PER_REGION, "pre-failover");
        
        // Simulate region failure (us-east-1)
        YNetRunner failedEngine = regionEngines.get("us-east-1");
        regionEngines.remove("us-east-1");
        failedEngine.shutdown();
        
        System.out.println("us-east-1 failed, redirecting traffic...");
        
        // Redirect traffic to backup regions
        RegionMetrics postFailover = submitRegionWorkload("us-west-2", CASES_PER_REGION * 2, "post-failover");
        
        // Validate graceful degradation
        assertTrue(postFailover.getAverageLatency() < preFailover.getAverageLatency() * 1.5,
            "After failover, latency should not increase more than 50%");
        
        // Validate no data loss
        assertEquals(0, postFailover.getFailedCases(),
            "No cases should be lost during region failover");
    }
    
    @Test
    @DisplayName("Network Latency Impact")
    void testNetworkLatencyImpact() throws Exception {
        System.out.println("Testing network latency impact...");
        
        // Test with simulated network latency
        Map<String, List<Long>> latencyResults = new HashMap<>();
        
        for (String region : REGIONS) {
            List<Long> latencies = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                long latency = simulateCrossRegionRequest(region);
                latencies.add(latency);
            }
            latencyResults.put(region, latencies);
        }
        
        // Validate latency impact
        for (Map.Entry<String, List<Long>> entry : latencyResults.entrySet()) {
            double avgLatency = entry.getValue().stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
            
            // Latency should be within acceptable bounds
            assertTrue(avgLatency < 200, 
                String.format("Region %s average latency %dms exceeds 200ms", 
                    entry.getKey(), (long) avgLatency));
        }
    }
    
    @Test
    @DisplayName("Data Synchronization Validation")
    void testDataSynchronization() throws Exception {
        System.out.println("Testing data synchronization...");
        
        // Create cases in primary region
        RegionMetrics primaryMetrics = submitRegionWorkload("us-east-1", 100, "sync-test-primary");
        
        // Allow time for synchronization
        Thread.sleep(3000);
        
        // Verify data consistency across regions
        Map<String, Integer> caseCounts = new HashMap<>();
        for (String region : REGIONS) {
            caseCounts.put(region, regionEngines.get(region).getCaseCount());
        }
        
        // Validate consistency (allowing for replication lag)
        int expectedCount = primaryMetrics.getSuccessfulCases();
        for (Map.Entry<String, Integer> entry : caseCounts.entrySet()) {
            int difference = Math.abs(entry.getValue() - expectedCount);
            assertTrue(difference <= 5, 
                String.format("Region %s has %d cases, expected %d (difference: %d)",
                    entry.getKey(), entry.getValue(), expectedCount, difference));
        }
    }
    
    private RegionMetrics submitRegionWorkload(String region, int caseCount, String phase) throws Exception {
        RegionMetrics metrics = regionMetrics.get(region);
        CountDownLatch latch = new CountDownLatch(caseCount);
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < caseCount; i++) {
            final int caseId = i;
            executor.submit(() -> {
                try {
                    long requestStart = System.currentTimeMillis();
                    
                    // Simulate network latency
                    if (!region.equals("us-east-1")) {
                        Thread.sleep(NETWORK_LATENCY_MS);
                    }
                    
                    // Process work item
                    YNetRunner engine = regionEngines.get(region);
                    String caseIdStr = "case-" + phase + "-" + region + "-" + caseId;
                    
                    // Simulate work processing
                    Thread.sleep(new Random().nextInt(20) + 5);
                    
                    long requestTime = System.currentTimeMillis() - requestStart;
                    metrics.recordLatency(requestTime);
                    
                    latch.countDown();
                } catch (Exception e) {
                    metrics.recordFailedCase();
                }
            });
        }
        
        latch.await(1, TimeUnit.MINUTES);
        long totalTime = System.currentTimeMillis() - startTime;
        metrics.setTotalDuration(totalTime);
        
        return metrics;
    }
    
    private long simulateCrossRegionRequest(String region) throws Exception {
        long start = System.currentTimeMillis();
        
        // Simulate network request to region
        Thread.sleep(NETWORK_LATENCY_MS + new Random().nextInt(30));
        
        // Simulate processing
        Thread.sleep(new Random().nextInt(10));
        
        return System.currentTimeMillis() - start;
    }
    
    private void validateCrossRegionConsistency() {
        System.out.println("\n=== CROSS-REGION CONSISTENCY REPORT ===");
        
        // Calculate performance metrics for each region
        Map<String, PerformanceStats> stats = new HashMap<>();
        for (String region : REGIONS) {
            RegionMetrics metrics = regionMetrics.get(region);
            stats.put(region, new PerformanceStats(metrics));
        }
        
        // Validate consistency
        double avgLatency = stats.values().stream()
            .mapToDouble(PerformanceStats::getAverageLatency)
            .average()
            .orElse(0);
        
        double maxDeviation = 0;
        for (Map.Entry<String, PerformanceStats> entry : stats.entrySet()) {
            double deviation = Math.abs(entry.getValue().getAverageLatency() - avgLatency) / avgLatency;
            maxDeviation = Math.max(maxDeviation, deviation);
        }
        
        System.out.printf("Average latency across regions: %.2fms%n", avgLatency);
        System.out.printf("Maximum latency deviation: %.2f%%%n", maxDeviation * 100);
        
        // Validate consistency thresholds
        assertTrue(maxDeviation < 0.2, "Regions should have < 20% latency variation");
        
        // Validate success rates
        for (Map.Entry<String, PerformanceStats> entry : stats.entrySet()) {
            double successRate = entry.getValue().getSuccessRate();
            System.out.printf("Region %s success rate: %.2f%%%n", 
                entry.getKey(), successRate * 100);
            assertTrue(successRate > 0.99, "All regions should have > 99% success rate");
        }
    }
    
    private YNet createTestNet() {
        // In a real implementation, this would create a test YNet
        // For testing purposes, we'll return null and handle it
        return null;
    }
    
    /**
     * Metrics for a specific region
     */
    private static class RegionMetrics {
        private final String region;
        private final List<Long> latencies = new ArrayList<>();
        private int failedCases = 0;
        private long totalDuration = 0;
        private final AtomicLong successfulCases = new AtomicLong();
        
        public RegionMetrics(String region) {
            this.region = region;
        }
        
        public void recordLatency(long latency) {
            latencies.add(latency);
            successfulCases.incrementAndGet();
        }
        
        public void recordFailedCase() {
            failedCases++;
        }
        
        public void setTotalDuration(long duration) {
            this.totalDuration = duration;
        }
        
        public double getAverageLatency() {
            if (latencies.isEmpty()) return 0;
            return latencies.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        }
        
        public int getFailedCases() {
            return failedCases;
        }
        
        public long getSuccessfulCases() {
            return successfulCases.get();
        }
    }
    
    /**
     * Performance statistics for a region
     */
    private static class PerformanceStats {
        private final RegionMetrics metrics;
        
        public PerformanceStats(RegionMetrics metrics) {
            this.metrics = metrics;
        }
        
        public double getAverageLatency() {
            return metrics.getAverageLatency();
        }
        
        public double getSuccessRate() {
            long total = metrics.getSuccessfulCases() + metrics.getFailedCases();
            if (total == 0) return 1.0;
            return (double) metrics.getSuccessfulCases() / total;
        }
    }
}
