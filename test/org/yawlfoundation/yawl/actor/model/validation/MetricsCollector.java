/*
 * YAWL - Yet Another Workflow Language
 * Copyright (C) 2003-2006, 2008-2011, 2014-2019 National University of Ireland, Galway
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.yawlfoundation.yawl.actor.model.validation;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;
import java.lang.management.*;
import java.time.*;

/**
 * Comprehensive Metrics Collector for YAWL Actor Model Validation
 *
 * Collects and analyzes performance metrics for scale testing,
 * performance validation, and stress testing scenarios.
 *
 * Metrics Categories:
 * - Memory Metrics (heap, GC, per-agent consumption)
 * - Performance Metrics (latency, throughput, message rates)
 * - Stress Metrics (stability, recovery, flood handling)
 * - Resource Utilization (CPU, threads, I/O)
 */
public class MetricsCollector {

    // Time tracking
    private final AtomicLong testStartTime = new AtomicLong(0);
    private final AtomicLong testEndTime = new AtomicLong(0);

    // Memory metrics
    private final AtomicLong heapUsed = new AtomicLong(0);
    private final AtomicLong heapCommitted = new AtomicLong(0);
    private final AtomicLong heapMax = new AtomicLong(0);
    private final AtomicLong gcCount = new AtomicLong(0);
    private final AtomicLong gcTime = new AtomicLong(0);
    private final ConcurrentLinkedQueue<MemorySnapshot> memorySnapshots = new ConcurrentLinkedQueue<>();

    // Performance metrics
    private final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
    private final AtomicLong totalMessagesSent = new AtomicLong(0);
    private final AtomicLong totalMessagesDelivered = new AtomicLong(0);
    private final AtomicLong totalMessagesLost = new AtomicLong(0);
    private final AtomicLong messageProcessingTime = new AtomicLong(0);

    // Stress metrics
    private final AtomicLong loadSpikes = new AtomicLong(0);
    private final AtomicLong recoveryEvents = new AtomicLong(0);
    private final AtomicLong errorEvents = new AtomicLong(0);
    private final ConcurrentLinkedQueue<StressSnapshot> stressSnapshots = new ConcurrentLinkedQueue<>();

    // Resource utilization
    private final ConcurrentLinkedQueue<ThreadSnapshot> threadSnapshots = new ConcurrentLinkedQueue<>();
    private final AtomicLong cpuTime = new AtomicLong(0);
    private final AtomicLong gcPauseTime = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> ioOperations = new ConcurrentHashMap<>();

    // Agent-specific metrics
    private final ConcurrentHashMap<String, AgentMetrics> agentMetricsMap = new ConcurrentHashMap<>();

    // Configuration
    private final boolean detailedTracking;
    private final int sampleIntervalMs;

    public MetricsCollector() {
        this(true, 1000); // Detailed tracking with 1-second samples
    }

    public MetricsCollector(boolean detailedTracking, int sampleIntervalMs) {
        this.detailedTracking = detailedTracking;
        this.sampleIntervalMs = sampleIntervalMs;

        // Start background sampling
        if (detailedTracking) {
            startBackgroundSampling();
        }
    }

    /**
     * Start test timing
     */
    public void startTest() {
        testStartTime.set(System.currentTimeMillis());
        if (detailedTracking) {
            collectInitialMetrics();
        }
    }

    /**
     * Stop test timing
     */
    public void endTest() {
        testEndTime.set(System.currentTimeMillis());
        if (detailedTracking) {
            collectFinalMetrics();
        }
    }

    /**
     * Record a latency measurement
     */
    public void recordLatency(long nanoLatency) {
        latencies.add(nanoLatency);

        if (detailedTracking) {
            // Update agent-specific metrics
            updateAgentMetrics("latency", nanoLatency);
        }
    }

    /**
     * Record a message event
     */
    public void recordMessageEvent(String type, String agentID, long processingTime) {
        switch (type) {
            case "sent":
                totalMessagesSent.incrementAndGet();
                break;
            case "delivered":
                totalMessagesDelivered.incrementAndGet();
                break;
            case "lost":
                totalMessagesLost.incrementAndGet();
                break;
        }

        if (processingTime > 0) {
            messageProcessingTime.addAndGet(processingTime);
        }

        if (detailedTracking && agentID != null) {
            AgentMetrics agentMetrics = agentMetricsMap.computeIfAbsent(agentID, k -> new AgentMetrics(agentID));
            agentMetrics.recordMessage(type);
            if (processingTime > 0) {
                agentMetrics.recordProcessingTime(processingTime);
            }
        }
    }

    /**
     * Record a GC event
     */
    public void recordGcEvent(long durationNanos) {
        gcCount.incrementAndGet();
        gcTime.addAndGet(durationNanos);
        gcPauseTime.addAndGet(durationNanos);
    }

    /**
     * Record a load spike
     */
    public void recordLoadSpike(int multiplier) {
        loadSpikes.incrementAndGet();

        if (detailedTracking) {
            // Record spike details
            stressSnapshots.add(new StressSnapshot(
                System.currentTimeMillis(),
                "load_spike",
                Map.of("multiplier", multiplier)
            ));
        }
    }

    /**
     * Record a recovery event
     */
    public void recordRecoveryEvent(String agentID, long recoveryTime) {
        recoveryEvents.incrementAndGet();

        if (detailedTracking && agentID != null) {
            AgentMetrics agentMetrics = agentMetricsMap.computeIfAbsent(agentID, k -> new AgentMetrics(agentID));
            agentMetrics.recordRecovery(recoveryTime);
        }
    }

    /**
     * Record an error event
     */
    public void recordErrorEvent(String agentID, String errorType) {
        errorEvents.incrementAndGet();

        if (detailedTracking && agentID != null) {
            AgentMetrics agentMetrics = agentMetricsMap.computeIfAbsent(agentID, k -> new AgentMetrics(agentID));
            agentMetrics.recordError(errorType);
        }
    }

    /**
     * Update agent-specific metrics
     */
    public void updateAgentMetrics(String metricType, long value) {
        // This would update metrics for specific agents
        // Implementation depends on agent identification scheme
    }

    /**
     * Get test duration in milliseconds
     */
    public long getTestDurationMs() {
        if (testStartTime.get() == 0 || testEndTime.get() == 0) {
            return 0;
        }
        return testEndTime.get() - testStartTime.get();
    }

    /**
     * Get memory metrics summary
     */
    public MemoryMetrics getMemoryMetrics() {
        MemorySnapshot snapshot = getCurrentMemorySnapshot();

        return new MemoryMetrics(
            snapshot.heapUsed,
            snapshot.heapCommitted,
            snapshot.heapMax,
            calculateHeapPerAgent(),
            getGcStats(),
            getMemoryTrend()
        );
    }

    /**
     * Get performance metrics summary
     */
    public PerformanceMetrics getPerformanceMetrics() {
        return new PerformanceMetrics(
            calculateLatencyPercentiles(),
            calculateMessageThroughput(),
            calculateMessageLossRate(),
            getMessageProcessingStats()
        );
    }

    /**
     * Get stress metrics summary
     */
    public StressMetrics getStressMetrics() {
        return new StressMetrics(
            loadSpikes.get(),
            recoveryEvents.get(),
            errorEvents.get(),
            getStressTrend(),
            calculateRecoveryRate()
        );
    }

    /**
     * Get resource utilization summary
     */
    public ResourceMetrics getResourceMetrics() {
        return new ResourceMetrics(
            calculateThreadUtilization(),
            getCpuUtilization(),
            calculateIoThroughput(),
            getSystemLoad()
        );
    }

    /**
     * Generate comprehensive metrics report
     */
    public String generateReport(String testName, String testType) {
        double durationSeconds = getTestDurationMs() / 1000.0;

        return String.format(
            "{\n" +
            "  \"testName\": \"%s\",\n" +
            "  \"testType\": \"%s\",\n" +
            "  \"durationSeconds\": %.2f,\n" +
            "  \"memoryMetrics\": {\n" +
            "    \"heapUsedMB\": %d,\n" +
            "    \"heapCommittedMB\": %d,\n" +
            "    \"heapMaxMB\": %d,\n" +
            "    \"heapPerAgentBytes\": %.2f,\n" +
            "    \"gcCount\": %d,\n" +
            "    \"gcTimeSeconds\": %.2f,\n" +
            "    \"memoryTrend\": \"%s\"\n" +
            "  },\n" +
            "  \"performanceMetrics\": {\n" +
            "    \"p50LatencyMs\": %.2f,\n" +
            "    \"p90LatencyMs\": %.2f,\n" +
            "    \"p99LatencyMs\": %.2f,\n" +
            "    \"avgLatencyMs\": %.2f,\n" +
            "    \"messagesDelivered\": %d,\n" +
            "    \"messagesLost\": %d,\n" +
            "    \"messageLossRate\": %.6f,\n" +
            "    \"throughputPerSecond\": %.2f\n" +
            "  },\n" +
            "  \"stressMetrics\": {\n" +
            "    \"loadSpikes\": %d,\n" +
            "    \"recoveryEvents\": %d,\n" +
            "    \"errorEvents\": %d,\n" +
            "    \"recoveryRate\": %.2f\n" +
            "  },\n" +
            "  \"resourceMetrics\": {\n" +
            "    \"avgThreadUtilization\": %.2f,\n" +
            "    \"maxThreadUtilization\": %.2f,\n" +
            "    \"cpuUtilization\": %.2f,\n" +
            "    \"systemLoad\": %.2f\n" +
            "  },\n" +
            "  \"timestamp\": \"%s\"\n" +
            "}",
            testName,
            testType,
            durationSeconds,
            getCurrentMemorySnapshot().heapUsed / (1024 * 1024),
            getCurrentMemorySnapshot().heapCommitted / (1024 * 1024),
            getCurrentMemorySnapshot().heapMax / (1024 * 1024),
            calculateHeapPerAgent(),
            gcCount.get(),
            gcTime.get() / 1_000_000_000.0,
            getMemoryTrend(),
            calculateLatencyPercentiles().p50 / 1_000_000.0,
            calculateLatencyPercentiles().p90 / 1_000_000.0,
            calculateLatencyPercentiles().p99 / 1_000_000.0,
            calculateLatencyPercentiles().avg / 1_000_000.0,
            totalMessagesDelivered.get(),
            totalMessagesLost.get(),
            calculateMessageLossRate(),
            calculateMessageThroughput(),
            loadSpikes.get(),
            recoveryEvents.get(),
            errorEvents.get(),
            calculateRecoveryRate(),
            calculateThreadUtilization().avgUtilization,
            calculateThreadUtilization().maxUtilization,
            getCpuUtilization(),
            getSystemLoad(),
            System.currentTimeMillis()
        );
    }

    /**
     * Calculate heap per agent
     */
    private double calculateHeapPerAgent() {
        // This would need to know the number of active agents
        // For now, return 0 as placeholder
        return 0.0;
    }

    /**
     * Get GC statistics
     */
    private GCStats getGcStats() {
        long gcCount = this.gcCount.get();
        long gcTimeNanos = this.gcTime.get();

        return new GCStats(
            gcCount,
            gcTimeNanos / 1_000_000_000.0, // Convert to seconds
            gcCount > 0 ? (double) gcTimeNanos / gcCount : 0.0 // Average GC time
        );
    }

    /**
     * Calculate memory trend
     */
    private String getMemoryTrend() {
        if (memorySnapshots.size() < 2) {
            return "insufficient_data";
        }

        // Calculate growth rate
        MemorySnapshot first = memorySnapshots.peek();
        MemorySnapshot last = memorySnapshots.getLast();

        double growth = last.heapUsed - first.heapUsed;
        double growthRate = growth / (double) first.heapUsed;

        if (growthRate > 0.05) { // 5% growth
            return "growing";
        } else if (growthRate < -0.05) { // 5% reduction
            return "declining";
        } else {
            return "stable";
        }
    }

    /**
     * Calculate latency percentiles
     */
    private LatencyPercentiles calculateLatencyPercentiles() {
        List<Long> sortedLatencies = latencies.stream()
            .sorted()
            .collect(Collectors.toList());

        if (sortedLatencies.isEmpty()) {
            return new LatencyPercentiles(0, 0, 0, 0);
        }

        int size = sortedLatencies.size();

        return new LatencyPercentiles(
            getPercentile(sortedLatencies, 50),
            getPercentile(sortedLatencies, 90),
            getPercentile(sortedLatencies, 99),
            sortedLatencies.stream().mapToLong(l -> l).average().orElse(0)
        );
    }

    /**
     * Calculate message throughput
     */
    private double calculateMessageThroughput() {
        double durationSeconds = getTestDurationMs() / 1000.0;
        return durationSeconds > 0 ? (double) totalMessagesDelivered.get() / durationSeconds : 0.0;
    }

    /**
     * Calculate message loss rate
     */
    private double calculateMessageLossRate() {
        long total = totalMessagesSent.get();
        long lost = totalMessagesLost.get();
        return total > 0 ? (double) lost / total : 0.0;
    }

    /**
     * Get message processing statistics
     */
    private MessageProcessingStats getMessageProcessingStats() {
        long delivered = totalMessagesDelivered.get();
        double totalTime = messageProcessingTime.get() / 1_000_000_000.0; // Convert to seconds

        return new MessageProcessingStats(
            delivered,
            totalTime,
            delivered > 0 ? totalTime / delivered : 0.0,
            getPercentile(latencies, 95) / 1_000_000.0 // p95 processing time in ms
        );
    }

    /**
     * Calculate stress trend
     */
    private String getStressTrend() {
        if (stressSnapshots.size() < 2) {
            return "insufficient_data";
        }

        // Analyze stress pattern
        long recentSpikes = stressSnapshots.stream()
            .filter(s -> s.type.equals("load_spike"))
            .count();

        if (recentSpikes > 10) {
            return "high_stress";
        } else if (recentSpikes > 5) {
            return "moderate_stress";
        } else {
            return "low_stress";
        }
    }

    /**
     * Calculate recovery rate
     */
    private double calculateRecoveryRate() {
        long totalEvents = recoveryEvents.get() + errorEvents.get();
        return totalEvents > 0 ? (double) recoveryEvents.get() / totalEvents : 0.0;
    }

    /**
     * Calculate thread utilization
     */
    private ThreadUtilization calculateThreadUtilization() {
        // Get current thread metrics
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long threadCount = threadBean.getThreadCount();
        long peakThreadCount = threadBean.getPeakThreadCount();

        // Calculate utilization based on samples
        double avgUtilization = threadSnapshots.stream()
            .mapToDouble(s -> s.utilization)
            .average()
            .orElse(0.0);

        double maxUtilization = threadSnapshots.stream()
            .mapToDouble(s -> s.utilization)
            .max()
            .orElse(0.0);

        return new ThreadUtilization(
            avgUtilization,
            maxUtilization,
            threadCount,
            peakThreadCount
        );
    }

    /**
     * Calculate CPU utilization
     */
    private double getCpuUtilization() {
        // Get CPU metrics
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean sunOsBean =
            (com.sun.management.OperatingSystemMXBean) osBean;

        return sunOsBean.getSystemCpuLoad();
    }

    /**
     * Calculate I/O throughput
     */
    private double calculateIoThroughput() {
        // Calculate I/O operations per second
        long totalOps = ioOperations.values().stream()
            .mapToLong(l -> l)
            .sum();

        double durationSeconds = getTestDurationMs() / 1000.0;
        return durationSeconds > 0 ? totalOps / durationSeconds : 0.0;
    }

    /**
     * Get system load
     */
    private double getSystemLoad() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean) osBean).getSystemLoadAverage();
        }

        return 0.0;
    }

    /**
     * Helper method to calculate percentile
     */
    private long getPercentile(List<Long> values, int percentile) {
        if (values.isEmpty()) return 0;

        int index = (int) Math.ceil((percentile / 100.0) * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    /**
     * Start background sampling
     */
    private void startBackgroundSampling() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            // Memory snapshot
            MemorySnapshot memorySnapshot = createMemorySnapshot();
            memorySnapshots.add(memorySnapshot);

            // Thread snapshot
            ThreadSnapshot threadSnapshot = createThreadSnapshot();
            threadSnapshots.add(threadSnapshot);

            // Stress snapshot
            if (!stressSnapshots.isEmpty()) {
                stressSnapshots.add(new StressSnapshot(
                    System.currentTimeMillis(),
                    "periodic_sample",
                    Map.of()
                ));
            }

        }, sampleIntervalMs, sampleIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Collect initial metrics
     */
    private void collectInitialMetrics() {
        memorySnapshots.add(createMemorySnapshot());
        threadSnapshots.add(createThreadSnapshot());
    }

    /**
     * Collect final metrics
     */
    private void collectFinalMetrics() {
        memorySnapshots.add(createMemorySnapshot());
        threadSnapshots.add(createThreadSnapshot());
    }

    /**
     * Create memory snapshot
     */
    private MemorySnapshot createMemorySnapshot() {
        Runtime runtime = Runtime.getRuntime();

        return new MemorySnapshot(
            System.currentTimeMillis(),
            runtime.totalMemory() - runtime.freeMemory(), // heapUsed
            runtime.totalMemory(), // heapCommitted
            runtime.maxMemory() // heapMax
        );
    }

    /**
     * Create thread snapshot
     */
    private ThreadSnapshot createThreadSnapshot() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        return new ThreadSnapshot(
            System.currentTimeMillis(),
            threadBean.getThreadCount(),
            threadBean.getPeakThreadCount(),
            calculateThreadUtilization().avgUtilization
        );
    }

    /**
     * Get current memory snapshot
     */
    private MemorySnapshot getCurrentMemorySnapshot() {
        if (memorySnapshots.isEmpty()) {
            return createMemorySnapshot();
        }
        return memorySnapshots.getLast();
    }

    // Data classes for metrics
    public static class MemoryMetrics {
        final long heapUsed;
        final long heapCommitted;
        final long heapMax;
        final double heapPerAgent;
        final GCStats gcStats;
        final String memoryTrend;

        public MemoryMetrics(long heapUsed, long heapCommitted, long heapMax,
                           double heapPerAgent, GCStats gcStats, String memoryTrend) {
            this.heapUsed = heapUsed;
            this.heapCommitted = heapCommitted;
            this.heapMax = heapMax;
            this.heapPerAgent = heapPerAgent;
            this.gcStats = gcStats;
            this.memoryTrend = memoryTrend;
        }
    }

    public static class PerformanceMetrics {
        final LatencyPercentiles latencies;
        final double throughputPerSecond;
        final double messageLossRate;
        final MessageProcessingStats processingStats;

        public PerformanceMetrics(LatencyPercentiles latencies, double throughputPerSecond,
                                double messageLossRate, MessageProcessingStats processingStats) {
            this.latencies = latencies;
            this.throughputPerSecond = throughputPerSecond;
            this.messageLossRate = messageLossRate;
            this.processingStats = processingStats;
        }
    }

    public static class StressMetrics {
        final long loadSpikes;
        final long recoveryEvents;
        final long errorEvents;
        final String stressTrend;
        final double recoveryRate;

        public StressMetrics(long loadSpikes, long recoveryEvents, long errorEvents,
                           String stressTrend, double recoveryRate) {
            this.loadSpikes = loadSpikes;
            this.recoveryEvents = recoveryEvents;
            this.errorEvents = errorEvents;
            this.stressTrend = stressTrend;
            this.recoveryRate = recoveryRate;
        }
    }

    public static class ResourceMetrics {
        final ThreadUtilization threadUtilization;
        final double cpuUtilization;
        final double ioThroughput;
        final double systemLoad;

        public ResourceMetrics(ThreadUtilization threadUtilization, double cpuUtilization,
                              double ioThroughput, double systemLoad) {
            this.threadUtilization = threadUtilization;
            this.cpuUtilization = cpuUtilization;
            this.ioThroughput = ioThroughput;
            this.systemLoad = systemLoad;
        }
    }

    // Supporting data classes
    public static class LatencyPercentiles {
        final long p50;
        final long p90;
        final long p99;
        final long avg;

        public LatencyPercentiles(long p50, long p90, long p99, long avg) {
            this.p50 = p50;
            this.p90 = p90;
            this.p99 = p99;
            this.avg = avg;
        }
    }

    public static class GCStats {
        final long gcCount;
        final double gcTimeSeconds;
        final double avgGcTime;

        public GCStats(long gcCount, double gcTimeSeconds, double avgGcTime) {
            this.gcCount = gcCount;
            this.gcTimeSeconds = gcTimeSeconds;
            this.avgGcTime = avgGcTime;
        }
    }

    public static class MessageProcessingStats {
        final long totalMessages;
        final double totalTimeSeconds;
        final double avgTimePerMessage;
        final double p95Time;

        public MessageProcessingStats(long totalMessages, double totalTimeSeconds,
                                    double avgTimePerMessage, double p95Time) {
            this.totalMessages = totalMessages;
            this.totalTimeSeconds = totalTimeSeconds;
            this.avgTimePerMessage = avgTimePerMessage;
            this.p95Time = p95Time;
        }
    }

    public static class ThreadUtilization {
        final double avgUtilization;
        final double maxUtilization;
        final int currentThreadCount;
        final int peakThreadCount;

        public ThreadUtilization(double avgUtilization, double maxUtilization,
                               int currentThreadCount, int peakThreadCount) {
            this.avgUtilization = avgUtilization;
            this.maxUtilization = maxUtilization;
            this.currentThreadCount = currentThreadCount;
            this.peakThreadCount = peakThreadCount;
        }
    }

    // Snapshot classes for detailed tracking
    private static class MemorySnapshot {
        final long timestamp;
        final long heapUsed;
        final long heapCommitted;
        final long heapMax;

        MemorySnapshot(long timestamp, long heapUsed, long heapCommitted, long heapMax) {
            this.timestamp = timestamp;
            this.heapUsed = heapUsed;
            this.heapCommitted = heapCommitted;
            this.heapMax = heapMax;
        }
    }

    private static class ThreadSnapshot {
        final long timestamp;
        final int threadCount;
        final int peakThreadCount;
        final double utilization;

        ThreadSnapshot(long timestamp, int threadCount, int peakThreadCount, double utilization) {
            this.timestamp = timestamp;
            this.threadCount = threadCount;
            this.peakThreadCount = peakThreadCount;
            this.utilization = utilization;
        }
    }

    private static class StressSnapshot {
        final long timestamp;
        final String type;
        final Map<String, Object> details;

        StressSnapshot(long timestamp, String type, Map<String, Object> details) {
            this.timestamp = timestamp;
            this.type = type;
            this.details = details;
        }
    }

    // Agent-specific metrics
    private static class AgentMetrics {
        final String agentID;
        final AtomicLong messageCount = new AtomicLong(0);
        final AtomicLong processingTime = new AtomicLong(0);
        final AtomicLong recoveryCount = new AtomicLong(0);
        final ConcurrentHashMap<String, AtomicLong> errors = new ConcurrentHashMap<>();

        AgentMetrics(String agentID) {
            this.agentID = agentID;
        }

        void recordMessage(String type) {
            messageCount.incrementAndGet();
        }

        void recordProcessingTime(long time) {
            processingTime.addAndGet(time);
        }

        void recordRecovery(long recoveryTime) {
            recoveryCount.incrementAndGet();
        }

        void recordError(String errorType) {
            errors.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
        }
    }
}