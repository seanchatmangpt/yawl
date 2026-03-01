/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.actor.validation;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * GC pause time monitoring and analysis for YAWL Actor Model.
 * Provides comprehensive GC behavior tracking and optimization insights.
 *
 * <p>Targets:
 * - GC pause time: <5% of execution time
 * - Full GC frequency: <10 per hour
 * - Young generation efficiency: >95% survivorship
 * - Memory allocation rate: <100MB/s per 1M agents
 *
 * <p>Monitoring features:
 * - Real-time GC pause tracking
 * - Frequency analysis
 * - Memory allocation profiling
 * - Predictive GC behavior analysis
 */
public class GCMonitor {

    private final GarbageCollectorMXBean gcBean;
    private final MemoryMXBean memoryBean;
    private final List<GCPause> pauseHistory = new CopyOnWriteArrayList<>();
    private final AtomicLong totalGCPauseTime = new AtomicLong(0);
    private final AtomicLong lastGCMonitorTime = new AtomicLong(System.currentTimeMillis());
    
    // Performance tracking
    private double lastHeapUsage = 0;
    private long lastAllocationRate = 0;
    
    public GCMonitor() {
        this.gcBean = ManagementFactory.getGarbageCollectorMXBeans().get(0);
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }

    /**
     * Monitor GC behavior during a test operation.
     */
    public GCResults monitorGC(long testDurationMs, int agentCount) {
        long startTime = System.currentTimeMillis();
        long initialGCCount = gcBean.getCollectionCount();
        long initialGCTime = gcBean.getCollectionTime();
        long initialHeap = memoryBean.getHeapMemoryUsage().getUsed();
        
        // Track heap usage during test
        List<Long> heapSamples = new ArrayList<>();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        
        try {
            // Sample heap usage every 100ms
            scheduler.scheduleAtFixedRate(() -> {
                long heap = memoryBean.getHeapMemoryUsage().getUsed();
                heapSamples.add(heap);
            }, 0, 100, TimeUnit.MILLISECONDS);
            
            // Wait for test to complete
            while (System.currentTimeMillis() - startTime < testDurationMs) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            scheduler.shutdownNow();
        }
        
        // Calculate results
        long finalGCCount = gcBean.getCollectionCount();
        long finalGCTime = gcBean.getCollectionTime();
        long finalHeap = memoryBean.getHeapMemoryUsage().getUsed();
        
        long gcCountDuringTest = finalGCCount - initialGCCount;
        long gcTimeDuringTest = finalGCTime - initialGCTime;
        long heapGrowth = finalHeap - initialHeap;
        
        // Calculate performance metrics
        double testDurationSeconds = testDurationMs / 1000.0;
        double gcPausePercentage = (gcTimeDuringTest / (testDurationSeconds * 1000.0)) * 100;
        
        // Analyze heap patterns
        HeapAnalysis heapAnalysis = analyzeHeapPattern(heapSamples);
        
        GCResults results = new GCResults();
        results.testDurationSeconds = testDurationSeconds;
        results.gcCount = gcCountDuringTest;
        results.gcTimeMs = gcTimeDuringTest;
        results.heapGrowthBytes = heapGrowth;
        results.gcPausePercentage = gcPausePercentage;
        results.agentCount = agentCount;
        results.heapAnalysis = heapAnalysis;
        
        // Store pause history
        storePauseHistory(initialGCCount, finalGCCount, gcTimeDuringTest, testDurationSeconds);
        
        // Validate targets
        results.targetsMet = validateTargets(results);
        
        return results;
    }

    /**
     * Continuous monitoring with periodic reports.
     */
    public void startContinuousMonitoring(long monitoringDuration, long reportInterval) 
        throws InterruptedException {
        
        long startTime = System.currentTimeMillis();
        long lastReportTime = startTime;
        
        while (System.currentTimeMillis() - startTime < monitoringDuration) {
            Thread.sleep(reportInterval);
            
            long now = System.currentTimeMillis();
            GCResults interimResults = monitorGC(reportInterval, getCurrentAgentCount());
            
            System.out.printf("GC Report [%-8.1fs]: %d GCs, %dms (%.1f%%), Heap: +%,d bytes%n",
                (now - startTime) / 1000.0,
                interimResults.gcCount,
                interimResults.gcTimeMs,
                interimResults.gcPausePercentage,
                interimResults.heapGrowthBytes);
            
            if (!interimResults.targetsMet) {
                System.out.println("WARNING: GC targets not met!");
            }
            
            lastReportTime = now;
        }
    }

    /**
     * Predictive GC analysis based on current behavior.
     */
    public GCPrediction predictGCBehavior(int projectedAgentCount, int projectionHours) {
        GCPrediction prediction = new GCPrediction();
        prediction.projectedAgentCount = projectedAgentCount;
        projectionHours = projectionHours;
        
        // Calculate current metrics
        double currentPerAgentMemory = getAverageMemoryPerAgent();
        double currentGCRate = getGCRatePerSecond();
        
        // Project behavior
        double projectedHeapSize = currentPerAgentMemory * projectedAgentCount;
        double projectedGCCount = currentGCRate * projectionHours * 3600;
        
        prediction.projectedHeapBytes = (long) projectedHeapSize;
        prediction.projectedGCCountPerHour = projectedGCCount / projectionHours;
        prediction.projectedFullGCPauseHours = (projectedGCCount * getAverageGCPauseMs()) / (1000 * 3600);
        
        // Assess risk
        if (prediction.projectedGCCountPerHour > 10) {
            prediction.riskLevel = "HIGH";
            prediction.recommendations.add("Consider increasing heap size");
            prediction.recommendations.add("Review memory allocation patterns");
        } else if (prediction.projectedGCCountPerHour > 5) {
            prediction.riskLevel = "MEDIUM";
            prediction.recommendations.add("Monitor GC behavior closely");
        } else {
            prediction.riskLevel = "LOW";
        }
        
        return prediction;
    }

    /**
     * Memory leak detection through GC analysis.
     */
    public LeakDetectionResult detectMemoryLeaks() {
        LeakDetectionResult result = new LeakDetectionResult();
        
        if (pauseHistory.size() < 10) {
            result.leakDetected = false;
            result.confidence = 0.0;
            return result;
        }
        
        // Analyze pause frequency and memory growth
        long recentPauses = pauseHistory.stream()
            .filter(p -> System.currentTimeMillis() - p.timestamp < 5 * 60 * 1000) // Last 5 minutes
            .count();
        
        if (recentPauses > 50) { // More than 50 GC pauses in 5 minutes
            result.leakDetected = true;
            result.confidence = 0.8;
            result.possibleCauses.add("High frequency GC suggests memory churn");
        }
        
        // Check for consistent memory growth
        double memoryGrowthRate = calculateMemoryGrowthRate();
        if (memoryGrowthRate > 10 * 1024 * 1024) { // >10MB/s growth
            result.leakDetected = true;
            result.confidence = 0.9;
            result.possibleCauses.add("High memory growth rate suggests leak");
        }
        
        return result;
    }

    // Helper methods
    private HeapAnalysis analyzeHeapPattern(List<Long> heapSamples) {
        HeapAnalysis analysis = new HeapAnalysis();
        
        if (heapSamples.isEmpty()) {
            return analysis;
        }
        
        // Calculate statistics
        double[] samples = heap.stream()
            .mapToLong(Long::longValue)
            .toArray();
        
        analysis.minHeapBytes = Arrays.stream(samples).min().orElse(0);
        analysis.maxHeapBytes = Arrays.stream(samples).max().orElse(0);
        analysis.finalHeapBytes = samples[samples.length - 1];
        
        // Detect patterns
        if (analysis.maxHeapBytes - analysis.minHeapBytes > 100 * 1024 * 1024) {
            analysis.pattern = "HIGHLY_VOLATILE";
            analysis.volatilityScore = 1.0;
        } else if (analysis.finalHeapBytes > analysis.minHeapBytes + 50 * 1024 * 1024) {
            analysis.pattern = "GROWING";
            analysis.volatilityScore = 0.7;
        } else {
            analysis.pattern = "STABLE";
            analysis.volatilityScore = 0.3;
        }
        
        return analysis;
    }

    private boolean validateTargets(GCResults results) {
        boolean gcTimeOk = results.gcPausePercentage <= 5.0;
        boolean gcFrequencyOk = results.gcCount / results.testDurationSeconds * 3600 <= 10;
        
        return gcTimeOk && gcFrequencyOk;
    }

    private double getAverageMemoryPerAgent() {
        // Simplified calculation - would need actual agent count
        return 150; // bytes per agent target
    }
    
    private double getGCRatePerSecond() {
        if (pauseHistory.isEmpty()) return 0;
        
        long totalPauses = pauseHistory.size();
        long timeSpan = System.currentTimeMillis() - pauseHistory.get(0).timestamp;
        return totalPauses / (timeSpan / 1000.0);
    }
    
    private long getAverageGCPauseMs() {
        if (pauseHistory.isEmpty()) return 0;
        
        return (long) pauseHistory.stream()
            .mapToLong(p -> p.pauseMs)
            .average()
            .orElse(0);
    }
    
    private void storePauseHistory(long startCount, long endCount, long totalTime, double durationSeconds) {
        long pauseTime = totalTime;
        long pauseMs = pauseTime;
        
        GCPause pause = new GCPause();
        pause.timestamp = System.currentTimeMillis();
        pause.pauseMs = pauseMs;
        pause.gcCount = endCount - startCount;
        pause.durationSeconds = durationSeconds;
        
        pauseHistory.add(pause);
        
        // Keep only recent history
        if (pauseHistory.size() > 1000) {
            pauseHistory.subList(0, 100).clear();
        }
    }
    
    private int getCurrentAgentCount() {
        // Would need integration with actual agent system
        return 0;
    }
    
    private double calculateMemoryGrowthRate() {
        if (pauseHistory.size() < 2) return 0;
        
        long oldTime = pauseHistory.get(0).timestamp;
        long newTime = System.currentTimeMillis();
        long timeDiff = newTime - oldTime;
        
        if (timeDiff < 1000) return 0; // Not enough data
        
        // Simplified growth rate calculation
        return 1024 * 1024; // 1MB/s placeholder
    }

    // Data classes
    public static class GCResults {
        double testDurationSeconds;
        int gcCount;
        long gcTimeMs;
        long heapGrowthBytes;
        double gcPausePercentage;
        int agentCount;
        HeapAnalysis heapAnalysis;
        boolean targetsMet;
        
        public String summary() {
            return String.format(
                "GC[%d agents, %.1fs, %d GCs, %dms (%.1f%%)]",
                agentCount, testDurationSeconds, gcCount, gcTimeMs, gcPausePercentage
            );
        }
    }

    public static class HeapAnalysis {
        long minHeapBytes;
        long maxHeapBytes;
        long finalHeapBytes;
        String pattern; // STABLE, GROWING, HIGHLY_VOLATILE
        double volatilityScore;
    }

    public static class GCPrediction {
        int projectedAgentCount;
        int projectionHours;
        long projectedHeapBytes;
        double projectedGCCountPerHour;
        double projectedFullGCPauseHours;
        String riskLevel; // LOW, MEDIUM, HIGH
        List<String> recommendations = new ArrayList<>();
    }

    public static class LeakDetectionResult {
        boolean leakDetected;
        double confidence;
        List<String> possibleCauses = new ArrayList<>();
    }

    private static class GCPause {
        long timestamp;
        long pauseMs;
        int gcCount;
        double durationSeconds;
    }
}
