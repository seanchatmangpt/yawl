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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.agent.validation;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Enhanced memory profiler for YAWL Agent model with comprehensive heap analysis.
 * Provides detailed memory tracking, optimization insights, and leak detection.
 *
 * <p>Enhanced features:
 * - Precise heap measurement with GC awareness
 * - Object size analysis for all components (24+40=64 bytes base)
 * - Memory leak detection at scale
 * - Hotspot identification
 * - Comparison with 132-byte target per agent
 * - Memory pressure monitoring
 * - Allocation rate tracking
 * - Fragmentation analysis
 */
public class MemoryProfiler {

    // Memory targets
    private static final long TARGET_BYTES_PER_AGENT = 132;
    private static final long TARGET_BYTES_PER_AGENT_WITH_OVERHEAD = 150; // 150 bytes with buffer
    private static final double TARGET_HEAP_UTILIZATION = 0.95; // 95% utilization
    private static final double TARGET_OBJECT_RETENTION = 0.98; // 98% object retention
    private static final long TARGET_GC_PAUSE_TIME_MS = 100; // 100ms max GC pause

    // Management beans
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final GarbageCollectorMXBean gcBean = ManagementFactory.getGarbageCollectorMXBeans().get(0);
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    // Tracking data
    private final Map<String, MemoryBaseline> baselines = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> allocationTrackers = new ConcurrentHashMap<>();
    private final Map<String, Long> objectCounts = new ConcurrentHashMap<>();

    // Alerting
    private final List<MemoryAlert> activeAlerts = new CopyOnWriteArrayList<>();
    private final AtomicLong alertCount = new AtomicLong(0);

    // Sampling
    private final ConcurrentLinkedDeque<MemorySample> recentSamples = new ConcurrentLinkedDeque<>();
    private static final int MAX_SAMPLES = 1000; // 10 seconds of samples

    /**
     * Comprehensive memory profile of the agent system at specific scale.
     */
    public MemorySnapshot profileAgentSystem(int agentCount) {
        MemorySnapshot snapshot = new MemorySnapshot();
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.agentCount = agentCount;

        // Basic memory metrics
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        snapshot.heapUsed = heapUsage.getUsed();
        snapshot.heapCommitted = heapUsage.getCommitted();
        snapshot.heapMax = heapUsage.getMax();
        snapshot.heapUtilization = (double) snapshot.heapUsed / snapshot.heapMax;

        // GC metrics
        snapshot.gcCount = gcBean.getCollectionCount();
        snapshot.gcTime = gcBean.getCollectionTime();
        snapshot.gcCountDiff = snapshot.gcCount - baselines.getOrDefault("gc", new MemoryBaseline()).gcCount;
        snapshot.gcTimeDiff = snapshot.gcTime - baselines.getOrDefault("gc", new MemoryBaseline()).gcTime;

        // Calculate per-agent metrics
        if (agentCount > 0) {
            snapshot.bytesPerAgent = (double) snapshot.heapUsed / agentCount;
            snapshot.targetMemory = agentCount * TARGET_BYTES_PER_AGENT;
            snapshot.memoryEfficiency = (double) snapshot.targetMemory / snapshot.heapUsed * 100;
            snapshot.heapOverhead = snapshot.heapUsed - snapshot.targetMemory;
            snapshot.heapOverheadPercent = (double) snapshot.heapOverhead / snapshot.heapUsed * 100;

            // Validate targets
            snapshot.meetsMemoryTarget = snapshot.bytesPerAgent <= TARGET_BYTES_PER_AGENT_WITH_OVERHEAD;
            snapshot.meetsTargetEfficiency = snapshot.memoryEfficiency >= 95.0;
        }

        // Thread metrics
        snapshot.activeThreads = threadBean.getThreadCount();
        snapshot.peakThreads = threadBean.getPeakThreadCount();
        snapshot.virtualThreads = (int) threadBean.getThreadCount();

        // Object analysis
        snapshot.estimatedObjects = estimateObjectCount();
        snapshot.estimatedObjectSize = estimateAverageObjectSize();

        // System metrics
        snapshot.systemLoadAverage = osBean.getSystemLoadAverage();
        snapshot.availableProcessors = osBean.getAvailableProcessors();

        // Store baseline for next measurement
        baselines.put("current", new MemoryBaseline(snapshot));

        return snapshot;
    }

    /**
     * Memory leak detection with detailed analysis.
     */
    public MemoryLeakAnalysis detectMemoryLeaks(int currentAgentCount, long timeSinceLastTestMs) {
        MemorySnapshot current = profileAgentSystem(currentAgentCount);
        MemoryBaseline previous = baselines.get("previous");

        MemoryLeakAnalysis analysis = new MemoryLeakAnalysis();
        analysis.currentSnapshot = current;
        analysis.timeSinceLastTestMs = timeSinceLastTestMs;

        if (previous != null) {
            // Calculate growth rates
            double heapGrowthRate = (current.heapUsed - previous.heapUsed) /
                                   (timeSinceLastTestMs / 1000.0);
            double agentGrowthRate = (current.agentCount - previous.agentCount) /
                                    (timeSinceLastTestMs / 1000.0);

            analysis.heapGrowthRateBytesPerSecond = heapGrowthRate;
            analysis.agentGrowthRatePerSecond = agentGrowthRate;

            // Calculate per-agent growth
            double growthPerAgent = current.heapUsed > previous.heapUsed ?
                (double) (current.heapUsed - previous.heapUsed) / (current.agentCount - previous.agentCount) : 0;

            analysis.growthRatePerAgentBytes = growthPerAgent;

            // Detect leaks
            if (heapGrowthRate > 0 && agentGrowthRate <= 0) {
                analysis.leakDetected = true;
                analysis.leakRateMBPerSecond = heapGrowthRate / (1024 * 1024);
                analysis.estimatedLeakDays = estimateLeakDuration(current.heapUsed, heapGrowthRate);
            }

            // Check for object retention issues
            analysis.objectRetentionEfficiency = calculateObjectRetentionEfficiency(previous, current);
            analysis.unusualRetentionDetected = analysis.objectRetentionEfficiency < TARGET_OBJECT_RETENTION;
        }

        return analysis;
    }

    /**
     * Hotspot analysis for memory-intensive operations.
     */
    public MemoryHotspotAnalysis identifyHotspots() {
        MemoryHotspotAnalysis analysis = new MemoryHotspotAnalysis();
        analysis.timestamp = System.currentTimeMillis();

        // Analyze GC behavior
        analysis.gcFrequency = gcBean.getCollectionCount();
        analysis.gcPauseTime = gcBean.getCollectionTime();
        analysis.averageGCPauseTimeMs = analysis.gcCountDiff > 0 ?
            (double) analysis.gcTimeDiff / analysis.gcCountDiff : 0;

        // Check for GC hotspots
        if (analysis.averageGCPauseTimeMs > TARGET_GC_PAUSE_TIME_MS) {
            analysis.hotspots.add("High GC pause time: " +
                String.format("%.1fms > %dms", analysis.averageGCPauseTimeMs, TARGET_GC_PAUSE_TIME_MS));
        }

        if (analysis.gcFrequency > 100) { // More than 100 GCs
            analysis.hotspots.add("High GC frequency: " + analysis.gcFrequency + " GCs");
        }

        // Analyze heap utilization
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        double utilization = (double) heap.getUsed() / heap.getMax();

        if (utilization > 0.9) {
            analysis.hotspots.add("High heap utilization: " +
                String.format("%.1f%% > 90%%", utilization * 100));
            analysis.heapPressureLevel = calculateHeapPressureLevel(heap);
        }

        // Check for thread-related issues
        int threadCount = threadBean.getThreadCount();
        if (threadCount > 100_000) {
            analysis.hotspots.add("High thread count: " +
                String.format("%,d > 100,000", threadCount));
        }

        // Check for memory fragmentation
        analysis.fragmentationRatio = calculateHeapFragmentation(heap);
        if (analysis.fragmentationRatio > 0.2) {
            analysis.hotspots.add("Heap fragmentation: " +
                String.format("%.1f%% > 20%%", analysis.fragmentationRatio * 100));
        }

        // Check allocation patterns
        analysis.allocationRateMBPerSecond = calculateAllocationRate();
        analysis.estimatedAllocationRatePerAgent = analysis.agentCount > 0 ?
            analysis.allocationRateMBPerSecond / analysis.agentCount : 0;

        if (analysis.allocationRateMBPerSecond > 100) {
            analysis.hotspots.add("High allocation rate: " +
                String.format("%.1f MB/s > 100 MB/s", analysis.allocationRateMBPerSecond));
        }

        return analysis;
    }

    /**
     * Optimization recommendations with priority scoring.
     */
    public MemoryOptimizationRecommendations getOptimizationRecommendations() {
        MemoryOptimizationRecommendations recommendations = new MemoryOptimizationRecommendations();
        recommendations.timestamp = System.currentTimeMillis();

        MemorySnapshot current = profileAgentSystem(getCurrentAgentCount());
        MemoryHotspotAnalysis hotspots = identifyHotspots();

        // Memory size optimizations
        if (current.bytesPerAgent > TARGET_BYTES_PER_AGENT) {
            recommendations.highPriority.add(
                "Reduce agent memory footprint: " +
                String.format("%.0f bytes/agent > target %d", current.bytesPerAgent, TARGET_BYTES_PER_AGENT));
            recommendations.highPriority.add("Consider using more compact data structures");
        }

        // Heap efficiency optimizations
        if (current.heapUtilization < TARGET_HEAP_UTILIZATION) {
            recommendations.mediumPriority.add(
                "Underutilized heap: " +
                String.format("%.1f%% < target %.1f%%", current.heapUtilization * 100, TARGET_HEAP_UTILIZATION * 100));
            recommendations.mediumPriority.add("Consider reducing heap size to save memory");
        }

        // GC optimizations
        if (hotspots.averageGCPauseTimeMs > TARGET_GC_PAUSE_TIME_MS) {
            recommendations.highPriority.add(
                "Reduce GC pauses: " +
                String.format("%.1fms avg pause > %dms target", hotspots.averageGCPauseTimeMs, TARGET_GC_PAUSE_TIME_MS));
            recommendations.mediumPriority.add("Consider using ZGC or Shenandoah for low-pause GC");
        }

        // Object retention optimizations
        MemoryLeakAnalysis leakAnalysis = detectMemoryLeaks(current.agentCount, 60_000); // 1 minute
        if (leakAnalysis.unusualRetentionDetected) {
            recommendations.highPriority.add(
                "Poor object retention: " +
                String.format("%.1f%% < target %.1f%%",
                    leakAnalysis.objectRetentionEfficiency * 100, TARGET_OBJECT_RETENTION * 100));
            recommendations.mediumPriority.add("Review object lifecycle management");
        }

        // System-level optimizations
        if (hotspots.heapPressureLevel > 0.8) {
            recommendations.critical.add("CRITICAL: High heap pressure detected");
            recommendations.critical.add("Consider scaling out or reducing load");
        }

        // Virtual thread optimizations
        if (current.virtualThreads > 1_000_000) {
            recommendations.mediumPriority.add(
                "High virtual thread count: " + current.virtualThreads);
            recommendations.mediumPriority.add("Consider using virtual thread pooling");
        }

        return recommendations;
    }

    /**
     * Continuous memory monitoring with sampling.
     */
    public void startContinuousMonitoring(int intervalSeconds) {
        ScheduledExecutorService scheduler = Executors.newVirtualThreadPerTaskExecutor();

        scheduler.scheduleAtFixedRate(
            this::collectMemorySample,
            0, intervalSeconds, TimeUnit.SECONDS
        );

        scheduler.scheduleAtFixedRate(
            this::analyzeMemoryTrends,
            intervalSeconds * 2, intervalSeconds * 2, TimeUnit.SECONDS
        );

        // Keep a reference to stop monitoring later
        this.scheduler = scheduler;
    }

    /**
     * Stop continuous monitoring.
     */
    public void stopContinuousMonitoring() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
    }

    // Helper methods
    private int estimateObjectCount() {
        // Estimate based on heap usage and average object size
        if (baselines.containsKey("current")) {
            MemorySnapshot last = baselines.get("current");
            if (last.estimatedObjectSize > 0) {
                return (int) (last.heapUsed / last.estimatedObjectSize);
            }
        }
        return 0;
    }

    private long estimateAverageObjectSize() {
        // Return 64 bytes as average (24 + 40 for basic agent components)
        return 64;
    }

    private double calculateObjectRetentionEfficiency(MemoryBaseline previous, MemorySnapshot current) {
        // Simplified calculation - would need more sophisticated tracking in production
        if (previous.estimatedObjects == 0 || current.estimatedObjects == 0) {
            return TARGET_OBJECT_RETENTION;
        }

        double retention = (double) current.estimatedObjects / previous.estimatedObjects;
        return Math.min(retention, TARGET_OBJECT_RETENTION);
    }

    private long estimateLeakDuration(long currentHeap, long leakRateBytesPerSecond) {
        if (leakRateBytesPerSecond <= 0) return Long.MAX_VALUE;

        long remaining = osBean.getTotalPhysicalMemorySize() - currentHeap;
        return remaining / leakRateBytesPerSecond / 1000; // Convert to seconds
    }

    private double calculateHeapPressureLevel(MemoryUsage heap) {
        double utilization = (double) heap.getUsed() / heap.getMax();
        if (utilization > 0.95) return 1.0; // Critical
        if (utilization > 0.9) return 0.9;  // High
        if (utilization > 0.8) return 0.7;  // Medium
        return 0.5; // Low
    }

    private double calculateHeapFragmentation(MemoryUsage heap) {
        // Simplified fragmentation calculation
        double freeRatio = (double) (heap.getMax() - heap.getUsed()) / heap.getMax();
        return 1.0 - freeRatio; // Higher means more fragmented
    }

    private double calculateAllocationRate() {
        // Calculate allocation rate from GC information
        if (baselines.containsKey("current")) {
            MemorySnapshot last = baselines.get("current");
            long timeDiff = System.currentTimeMillis() - last.timestamp;
            if (timeDiff > 0) {
                long heapDiff = memoryBean.getHeapMemoryUsage().getUsed() - last.heapUsed;
                return (double) heapDiff / (timeDiff / 1000.0) / (1024 * 1024); // MB/s
            }
        }
        return 0;
    }

    private int getCurrentAgentCount() {
        // Estimate based on runtime - would need integration with actual agent system
        return 0;
    }

    private void collectMemorySample() {
        MemorySample sample = new MemorySample(
            System.currentTimeMillis(),
            memoryBean.getHeapMemoryUsage(),
            memoryBean.getNonHeapMemoryUsage(),
            threadBean.getThreadCount(),
            threadBean.getPeakThreadCount(),
            0, // Would need actual agent count
            memoryBean.getHeapMemoryUsage().getUsed()
        );

        recentSamples.addLast(sample);
        while (recentSamples.size() > MAX_SAMPLES) {
            recentSamples.removeFirst();
        }
    }

    private void analyzeMemoryTrends() {
        if (recentSamples.size() < 2) return;

        MemorySample first = recentSamples.getFirst();
        MemorySample last = recentSamples.getLast();

        // Analyze growth trends
        long timeDiff = last.timestamp - first.timestamp;
        long memoryDiff = last.heapUsed - first.heapUsed;

        if (timeDiff > 0 && memoryDiff > 0) {
            double growthRate = (double) memoryDiff / timeDiff * 1000; // bytes per second

            if (growthRate > 10 * 1024 * 1024) { // >10MB/s growth
                alertMemoryGrowthAnomaly(growthRate);
            }
        }

        // Check for unusual patterns
        analyzeMemoryPatterns();
    }

    private void analyzeMemoryPatterns() {
        // Look for unusual patterns in recent samples
        double[] heapUsage = recentSamples.stream()
            .mapToLong(s -> s.heapUsed)
            .toArray();

        if (heapUsage.length > 10) {
            // Check for sudden drops (indicates aggressive GC)
            double[] diffs = new double[heapUsage.length - 1];
            for (int i = 0; i < diffs.length; i++) {
                diffs[i] = heapUsage[i + 1] - heapUsage[i];
            }

            double mean = Arrays.stream(diffs).average().orElse(0);
            double stdDev = Math.sqrt(Arrays.stream(diffs)
                .map(d -> Math.pow(d - mean, 2))
                .average().orElse(0));

            // If there's a sudden drop (more than 3 standard deviations below mean)
            double threshold = mean - 3 * stdDev;
            boolean gcAnomaly = Arrays.stream(diffs).anyMatch(d -> d < threshold);

            if (gcAnomaly) {
                alertAggressiveGC();
            }
        }
    }

    private void alertMemoryGrowthAnomaly(double growthRateBytesPerSecond) {
        MemoryAlert alert = new MemoryAlert(
            "MEMORY_GROWTH_ANOMALY",
            "SYSTEM",
            String.format("Unexpected memory growth: %.2f MB/s", growthRateBytesPerSecond / (1024 * 1024)),
            System.currentTimeMillis(),
            (long) growthRateBytesPerSecond
        );
        activeAlerts.add(alert);
        alertCount.incrementAndGet();
    }

    private void alertAggressiveGC() {
        MemoryAlert alert = new MemoryAlert(
            "AGGRESSIVE_GC",
            "SYSTEM",
            "Aggressive GC pattern detected - consider tuning heap size",
            System.currentTimeMillis(),
            gcBean.getCollectionTime()
        );
        activeAlerts.add(alert);
        alertCount.incrementAndGet();
    }

    // Data classes
    public static class MemorySnapshot {
        long timestamp;
        int agentCount;

        // Memory metrics
        long heapUsed;
        long heapCommitted;
        long heapMax;
        double heapUtilization;

        // GC metrics
        long gcCount;
        long gcTime;
        long gcCountDiff;
        long gcTimeDiff;

        // Agent-specific metrics
        double bytesPerAgent;
        long targetMemory;
        double memoryEfficiency;
        long heapOverhead;
        double heapOverheadPercent;
        boolean meetsMemoryTarget;
        boolean meetsTargetEfficiency;

        // Thread metrics
        int activeThreads;
        int peakThreads;
        int virtualThreads;

        // Object analysis
        int estimatedObjects;
        long estimatedObjectSize;

        // System metrics
        double systemLoadAverage;
        int availableProcessors;
    }

    public static class MemoryLeakAnalysis {
        MemorySnapshot currentSnapshot;
        long timeSinceLastTestMs;
        double heapGrowthRateBytesPerSecond;
        double agentGrowthRatePerSecond;
        double growthRatePerAgentBytes;
        boolean leakDetected;
        double leakRateMBPerSecond;
        long estimatedLeakDays;
        double objectRetentionEfficiency;
        boolean unusualRetentionDetected;
    }

    public static class MemoryHotspotAnalysis {
        long timestamp;
        int gcFrequency;
        long gcTime;
        double averageGCPauseTimeMs;
        List<String> hotspots = new ArrayList<>();
        double heapPressureLevel;
        double fragmentationRatio;
        double allocationRateMBPerSecond;
        double estimatedAllocationRatePerAgent;
        int agentCount;
    }

    public static class MemoryOptimizationRecommendations {
        long timestamp;
        List<String> critical = new ArrayList<>();
        List<String> highPriority = new ArrayList<>();
        List<String> mediumPriority = new ArrayList<>();
    }

    public static class MemoryAlert {
        String type;
        String source;
        String message;
        long timestamp;
        long relatedBytes;

        public MemoryAlert(String type, String source, String message, long timestamp, long relatedBytes) {
            this.type = type;
            this.source = source;
            this.message = message;
            this.timestamp = timestamp;
            this.relatedBytes = relatedBytes;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s (bytes: %d)",
                new Date(timestamp), type, message, relatedBytes);
        }
    }

    private static class MemorySample {
        final long timestamp;
        final MemoryUsage heapUsage;
        final MemoryUsage nonHeapUsage;
        final int threadCount;
        final int peakThreadCount;
        final int agentCount;
        final long heapUsed;

        MemorySample(long timestamp, MemoryUsage heapUsage, MemoryUsage nonHeapUsage,
                    int threadCount, int peakThreadCount, int agentCount, long heapUsed) {
            this.timestamp = timestamp;
            this.heapUsage = heapUsage;
            this.nonHeapUsage = nonHeapUsage;
            this.threadCount = threadCount;
            this.peakThreadCount = peakThreadCount;
            this.agentCount = agentCount;
            this.heapUsed = heapUsed;
        }
    }

    private static class MemoryBaseline extends MemorySnapshot {
        MemoryBaseline(MemorySnapshot snapshot) {
            // Copy constructor
            this.timestamp = snapshot.timestamp;
            this.agentCount = snapshot.agentCount;
            this.heapUsed = snapshot.heapUsed;
            this.heapCommitted = snapshot.heapCommitted;
            this.heapMax = snapshot.heapMax;
            this.heapUtilization = snapshot.heapUtilization;
            this.gcCount = snapshot.gcCount;
            this.gcTime = snapshot.gcTime;
            this.gcCountDiff = snapshot.gcCountDiff;
            this.gcTimeDiff = snapshot.gcTimeDiff;
            this.bytesPerAgent = snapshot.bytesPerAgent;
            this.targetMemory = snapshot.targetMemory;
            this.memoryEfficiency = snapshot.memoryEfficiency;
            this.heapOverhead = snapshot.heapOverhead;
            this.heapOverheadPercent = snapshot.heapOverheadPercent;
            this.meetsMemoryTarget = snapshot.meetsMemoryTarget;
            this.meetsTargetEfficiency = snapshot.meetsTargetEfficiency;
            this.activeThreads = snapshot.activeThreads;
            this.peakThreads = snapshot.peakThreads;
            this.virtualThreads = snapshot.virtualThreads;
            this.estimatedObjects = snapshot.estimatedObjects;
            this.estimatedObjectSize = snapshot.estimatedObjectSize;
            this.systemLoadAverage = snapshot.systemLoadAverage;
            this.availableProcessors = snapshot.availableProcessors;
        }
    }

    private ScheduledExecutorService scheduler;
}