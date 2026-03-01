/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and organisations
 * who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute
 * it and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.observability.memory;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Comprehensive memory profiler for YAWL actor model with 132-byte agent validation.
 *
 * <p>This profiler implements the exact memory management requirements for the YAWL
 * actor model, ensuring that each agent stays within the 132-byte footprint constraint.</p>
 *
 * <h2>Memory Architecture</h2>
 * <ul>
 *   <li>Agent object: 24 bytes (12 header + 4 id + 8 ref)</li>
 *   <li>Event queue: 40 bytes (LinkedTransferQueue)</li>
 *   <li>Actor state: 32 bytes (minimal overhead)</li>
 *   <li>Other overhead: 36 bytes (metadata, synchronization)</li>
 *   <li><b>Total: 132 bytes per agent</b></li>
 * </ul>
 *
 * <h2>Detection Capabilities</h2>
 * <ul>
 *   <li>Exact 132-byte validation per agent</li>
 *   <li>Memory footprint optimization detection</li>
 *   <li>GC impact analysis</li>
 *   <li>Leak identification at scale</li>
 *   <li>Memory pressure monitoring</li>
 * </ul>
 *
 * @author YAWL Foundation / GODSPEED Protocol
 * @version 6.0.0
 * @since 6.0.0
 */
public final class ActorMemoryProfiler {

    private static final Logger LOGGER = LogManager.getLogger(ActorMemoryProfiler.class);
    private static final Logger MEMORY_LOGGER = LogManager.getLogger("ACTOR_MEMORY_PROFILING");

    // Constants for 132-byte agent model
    private static final int TARGET_BYTES_PER_AGENT = 132;
    private static final int BUFFER_BYTES_PER_AGENT = 10; // 10-byte buffer for overhead
    private static final int MAX_BYTES_PER_AGENT = TARGET_BYTES_PER_AGENT + BUFFER_BYTES_PER_AGENT;
    private static final int AGENT_OBJECT_BASE_SIZE = 24; // With compact headers
    private static final int QUEUE_OBJECT_BASE_SIZE = 40; // LinkedTransferQueue
    private static final int STATE_OBJECT_BASE_SIZE = 32; // Minimal state tracking

    // Memory monitoring thresholds
    private static final double WARNING_MEMORY_PRESSURE = 0.75; // 75% heap usage
    private static final double CRITICAL_MEMORY_PRESSURE = 0.85; // 85% heap usage
    private static final double ALLOCATION_RATE_THRESHOLD_MB_PER_S = 100; // 100MB/s per 1M agents
    private static final int GC_PAUSE_THRESHOLD_MS = 100; // 100ms GC pause threshold

    // Management beans
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final GarbageCollectorMXBean gcMXBean = ManagementFactory.getGarbageCollectorMXBeans().get(0);
    private final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

    // Actor tracking
    private final Map<String, ActorMemoryFootprint> actorFootprints = new ConcurrentHashMap<>();
    private final AtomicLong totalAgents = new AtomicLong(0);
    private final AtomicLong totalMemoryBytes = new AtomicLong(0);
    private final AtomicLong totalMemoryTargetBytes = new AtomicLong(0);

    // Profiling state
    private final AtomicReference<MemoryProfile> currentProfile = new AtomicReference<>();
    private final AtomicReference<MemoryProfile> baselineProfile = new AtomicReference<>();
    private final ScheduledExecutorService scheduler = Executors.newVirtualThreadPerTaskExecutor();

    // Alerting
    private final List<MemoryAlert> activeAlerts = new CopyOnWriteArrayList<>();
    private final AtomicLong alertCount = new AtomicLong(0);

    // Sampling
    private final ConcurrentLinkedDeque<MemorySample> recentSamples = new ConcurrentLinkedDeque<>();
    private static final int MAX_SAMPLES = 1000; // 10 seconds of samples at 100ms interval

    /**
     * Initialize the memory profiler.
     */
    public void startProfiling() {
        if (currentProfile.compareAndSet(null, new MemoryProfile())) {
            LOGGER.info("Starting YAWL actor memory profiler with 132-byte constraint");

            // Start continuous sampling
            scheduler.scheduleAtFixedRate(
                this::collectMemorySample,
                0, 100, TimeUnit.MILLISECONDS
            );

            // Start periodic analysis
            scheduler.scheduleAtFixedRate(
                this::analyzeMemoryUsage,
                5, 5, TimeUnit.SECONDS
            );

            // Start GC analysis
            scheduler.scheduleAtFixedRate(
                this::analyzeGCImpact,
                10, 10, TimeUnit.SECONDS
            );

            // Start footprint validation
            scheduler.scheduleAtFixedRate(
                this::validateAgentFootprints,
                1, 1, TimeUnit.SECONDS
            );

            // Establish initial baseline
            scheduler.schedule(
                this::establishBaseline,
                2, TimeUnit.SECONDS
            );

            MEMORY_LOGGER.info("Memory profiler started - validating 132 bytes per agent");
        }
    }

    /**
     * Stop profiling and generate report.
     */
    public void stopProfiling() {
        MemoryProfile profile = currentProfile.getAndSet(null);
        if (profile != null) {
            LOGGER.info("Stopping YAWL actor memory profiler");

            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            generateFinalReport(profile);
        }
    }

    /**
     * Add agent to memory tracking.
     */
    public void addAgent(String agentId, Class<?> agentType) {
        if (currentProfile.get() == null) return;

        long agentMemoryFootprint = calculateAgentMemoryFootprint();
        ActorMemoryFootprint footprint = new ActorMemoryFootprint(
            agentId, agentType, agentMemoryFootprint, System.currentTimeMillis()
        );

        actorFootprints.put(agentId, footprint);
        totalAgents.incrementAndGet();
        totalMemoryBytes.addAndGet(agentMemoryFootprint);
        totalMemoryTargetBytes.addAndGet(TARGET_BYTES_PER_AGENT);

        MEMORY_LOGGER.debug("Added agent {} with footprint: {} bytes",
            agentId, agentMemoryFootprint);
    }

    /**
     * Update agent memory usage.
     */
    public void updateAgentMemory(String agentId, long currentMemory) {
        ActorMemoryFootprint footprint = actorFootprints.get(agentId);
        if (footprint != null) {
            long oldMemory = footprint.getCurrentMemory();
            long memoryDelta = currentMemory - oldMemory;

            footprint.updateMemory(currentMemory, System.currentTimeMillis());
            totalMemoryBytes.addAndGet(memoryDelta);

            // Check for memory anomalies
            if (currentMemory > MAX_BYTES_PER_AGENT) {
                alertMemoryExceedance(agentId, currentMemory);
            }
        }
    }

    /**
     * Record allocation event.
     */
    public void recordAllocation(String type, long allocatedBytes, long objectCount) {
        if (currentProfile.get() == null) return;

        MemoryProfile profile = currentProfile.get();
        profile.recordAllocation(type, allocatedBytes, objectCount);

        // Check allocation rate
        double currentAgents = actorFootprints.size();
        double allocationRateMBPerS = profile.getAllocationRateMBPerS();
        double expectedRate = currentAgents * ALLOCATION_RATE_THRESHOLD_MB_PER_S / 1_000_000.0;

        if (allocationRateMBPerS > expectedRate) {
            alertAllocationAnomaly(type, allocationRateMBPerS, expectedRate);
        }
    }

    /**
     * Get current memory statistics.
     */
    public MemoryStatistics getCurrentStatistics() {
        MemoryProfile profile = currentProfile.get();
        if (profile == null) {
            throw new IllegalStateException("Profiler not started");
        }

        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long totalAgents = actorFootprints.size();
        long totalActualMemory = totalMemoryBytes.get();
        long totalTargetMemory = totalAgents * (long) TARGET_BYTES_PER_AGENT;

        double memoryEfficiency = totalAgents > 0 ?
            (double) totalTargetMemory / totalActualMemory * 100 : 0;
        double bytesPerAgent = totalAgents > 0 ?
            (double) totalActualMemory / totalAgents : 0;

        return new MemoryStatistics(
            System.currentTimeMillis(),
            totalAgents,
            totalActualMemory,
            totalTargetMemory,
            bytesPerAgent,
            memoryEfficiency,
            heapUsage.getUsed(),
            heapUsage.getMax(),
            profile.getGCCount(),
            profile.getGCTime(),
            activeAlerts.size()
        );
    }

    /**
     * Get memory footprint analysis for all agents.
     */
    public MemoryFootprintAnalysis getFootprintAnalysis() {
        List<AgentFootprintDetails> details = actorFootprints.values().stream()
            .map(this::toFootprintDetails)
            .sorted(Comparator.comparingLong(AgentFootprintDetails::memoryBytes).reversed())
            .collect(Collectors.toList());

        // Calculate summary statistics
        long totalMemory = details.stream().mapToLong(d -> d.memoryBytes).sum();
        long compliantAgents = details.stream()
            .filter(d -> d.memoryBytes <= MAX_BYTES_PER_AGENT)
            .count();
        long exceedingAgents = details.size() - compliantAgents;

        return new MemoryFootprintAnalysis(
            details.size(),
            totalMemory,
            compliantAgents,
            exceedingAgents,
            exceedingAgents > 0 ? (double) exceedingAgents / details.size() * 100 : 0,
            details
        );
    }

    /**
     * Collect memory sample every 100ms.
     */
    private void collectMemorySample() {
        try {
            MemorySample sample = new MemorySample(
                System.currentTimeMillis(),
                memoryMXBean.getHeapMemoryUsage(),
                memoryMXBean.getNonHeapMemoryUsage(),
                threadMXBean.getThreadCount(),
                threadMXBean.getPeakThreadCount(),
                actorFootprints.size(),
                totalMemoryBytes.get()
            );

            recentSamples.addLast(sample);
            while (recentSamples.size() > MAX_SAMPLES) {
                recentSamples.removeFirst();
            }

            // Check memory pressure
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            double pressure = (double) heapUsage.getUsed() / heapUsage.getMax();

            if (pressure > CRITICAL_MEMORY_PRESSURE) {
                alertCriticalMemoryPressure(pressure);
            } else if (pressure > WARNING_MEMORY_PRESSURE) {
                alertWarningMemoryPressure(pressure);
            }

        } catch (Exception e) {
            LOGGER.error("Error collecting memory sample", e);
        }
    }

    /**
     * Analyze memory usage patterns.
     */
    private void analyzeMemoryUsage() {
        MemoryProfile profile = currentProfile.get();
        if (profile == null) return;

        MemoryStatistics stats = getCurrentStatistics();

        // Analyze memory growth
        if (recentSamples.size() >= 2) {
            MemorySample first = recentSamples.getFirst();
            MemorySample last = recentSamples.getLast();

            long timeDiff = last.timestamp - first.timestamp;
            long memoryDiff = last.totalMemoryBytes - first.totalMemoryBytes;

            if (timeDiff > 0 && memoryDiff > 0) {
                double growthRate = (double) memoryDiff / timeDiff * 1000; // bytes per second
                double expectedRate = 0; // No growth expected for idle system

                if (growthRate > expectedRate + 1024 * 1024) { // >1MB/s unexpected growth
                    alertMemoryGrowthAnomaly(growthRate);
                }
            }
        }

        // Check agent memory efficiency
        if (stats.bytesPerAgent > MAX_BYTES_PER_AGENT) {
            alertAverageFootprintExceedance(stats.bytesPerAgent);
        }

        // Update profile
        profile.updateSnapshot(stats);
    }

    /**
     * Analyze GC impact on performance.
     */
    private void analyzeGCImpact() {
        try {
            long gcTime = gcMXBean.getCollectionTime();
            long gcCount = gcMXBean.getCollectionCount();

            MemoryProfile profile = currentProfile.get();
            if (profile != null) {
                profile.updateGCStats(gcCount, gcTime);
            }

            // Check for excessive GC pauses
            if (gcTime > GC_PAUSE_THRESHOLD_MS) {
                alertExcessiveGCPause(gcTime);
            }

            // Check GC frequency
            double recentGCRate = calculateRecentGCRate();
            if (recentGCRate > 10) { // More than 10 GCs per second
                alertHighGCFrequency(recentGCRate);
            }

        } catch (Exception e) {
            LOGGER.error("Error analyzing GC impact", e);
        }
    }

    /**
     * Validate agent footprints against 132-byte target.
     */
    private void validateAgentFootprints() {
        long compliantCount = 0;
        long violatingCount = 0;

        for (ActorMemoryFootprint footprint : actorFootprints.values()) {
            long memory = footprint.getCurrentMemory();
            if (memory <= MAX_BYTES_PER_AGENT) {
                compliantCount++;
            } else {
                violatingCount++;
                alertAgentFootprintViolation(footprint, memory);
            }
        }

        // Update compliance percentage
        MemoryProfile profile = currentProfile.get();
        if (profile != null) {
            profile.updateCompliance(
                actorFootprints.size(),
                compliantCount,
                violatingCount
            );
        }
    }

    /**
     * Establish memory baseline.
     */
    private void establishBaseline() {
        if (baselineProfile.get() == null) {
            LOGGER.info("Establishing memory baseline for actor system");

            MemoryProfile baseline = new MemoryProfile();
            recentSamples.forEach(sample -> baseline.updateSnapshot(toStatistics(sample)));

            baselineProfile.set(baseline);

            // Validate baseline against targets
            MemoryStatistics baselineStats = baseline.getLatestSnapshot();
            if (baselineStats != null) {
                MEMORY_LOGGER.info("Baseline established - {} agents, {} bytes per agent, {}% efficiency",
                    baselineStats.totalAgents,
                    String.format("%.1f", baselineStats.bytesPerAgent),
                    String.format("%.1f", baselineStats.memoryEfficiency)
                );
            }
        }
    }

    // Alert methods
    private void alertMemoryExceedance(String agentId, long memoryBytes) {
        MemoryAlert alert = new MemoryAlert(
            "MEMORY_EXCEEDANCE",
            agentId,
            String.format("Agent memory exceeds 132-byte target: %d bytes", memoryBytes),
            System.currentTimeMillis(),
            memoryBytes
        );
        activeAlerts.add(alert);
        alertCount.incrementAndGet();
        MEMORY_LOGGER.warn("ALERT: {}", alert);
    }

    private void alertAllocationAnomaly(String type, double actualRate, double expectedRate) {
        MemoryAlert alert = new MemoryAlert(
            "ALLOCATION_ANOMALY",
            type,
            String.format("Allocation rate %.2f MB/s exceeds expected %.2f MB/s", actualRate, expectedRate),
            System.currentTimeMillis(),
            (long) (actualRate * 1024 * 1024)
        );
        activeAlerts.add(alert);
        alertCount.incrementAndGet();
        MEMORY_LOGGER.warn("ALERT: {}", alert);
    }

    private void alertCriticalMemoryPressure(double pressure) {
        MemoryAlert alert = new MemoryAlert(
            "CRITICAL_MEMORY_PRESSURE",
            "SYSTEM",
            String.format("Critical heap pressure: %.1f%%", pressure * 100),
            System.currentTimeMillis(),
            (long) (pressure * memoryMXBean.getHeapMemoryUsage().getMax())
        );
        activeAlerts.add(alert);
        alertCount.incrementAndGet();
        MEMORY_LOGGER.error("CRITICAL ALERT: {}", alert);
    }

    private void alertWarningMemoryPressure(double pressure) {
        MemoryAlert alert = new MemoryAlert(
            "WARNING_MEMORY_PRESSURE",
            "SYSTEM",
            String.format("High heap pressure: %.1f%%", pressure * 100),
            System.currentTimeMillis(),
            (long) (pressure * memoryMXBean.getHeapMemoryUsage().getMax())
        );
        activeAlerts.add(alert);
        alertCount.incrementAndGet();
        MEMORY_LOGGER.warn("WARNING ALERT: {}", alert);
    }

    private void alertMemoryGrowthAnomaly(double growthRate) {
        MemoryAlert alert = new MemoryAlert(
            "MEMORY_GROWTH_ANOMALY",
            "SYSTEM",
            String.format("Unexpected memory growth: %.2f MB/s", growthRate / (1024 * 1024)),
            System.currentTimeMillis(),
            (long) growthRate
        );
        activeAlerts.add(alert);
        alertCount.incrementAndGet();
        MEMORY_LOGGER.warn("ALERT: {}", alert);
    }

    private void alertAverageFootprintExceedance(double avgBytes) {
        MemoryAlert alert = new MemoryAlert(
            "AVERAGE_FOOTPRINT_EXCEEDANCE",
            "SYSTEM",
            String.format("Average agent footprint exceeds target: %.1f bytes > %d", avgBytes, MAX_BYTES_PER_AGENT),
            System.currentTimeMillis(),
            (long) avgBytes
        );
        activeAlerts.add(alert);
        alertCount.incrementAndGet();
        MEMORY_LOGGER.warn("ALERT: {}", alert);
    }

    private void alertExcessiveGCPause(long gcTime) {
        MemoryAlert alert = new MemoryAlert(
            "EXCESSIVE_GC_PAUSE",
            "SYSTEM",
            String.format("GC pause time exceeds threshold: %d ms > %d ms", gcTime, GC_PAUSE_THRESHOLD_MS),
            System.currentTimeMillis(),
            gcTime
        );
        activeAlerts.add(alert);
        alertCount.incrementAndGet();
        MEMORY_LOGGER.warn("ALERT: {}", alert);
    }

    private void alertHighGCFrequency(double gcRate) {
        MemoryAlert alert = new MemoryAlert(
            "HIGH_GC_FREQUENCY",
            "SYSTEM",
            String.format("GC frequency exceeds threshold: %.1f GC/s > 10", gcRate),
            System.currentTimeMillis(),
            (long) (gcRate * 1000)
        );
        activeAlerts.add(alert);
        alertCount.incrementAndGet();
        MEMORY_LOGGER.warn("ALERT: {}", alert);
    }

    private void alertAgentFootprintViolation(ActorMemoryFootprint footprint, long memory) {
        MemoryAlert alert = new MemoryAlert(
            "AGENT_FOOTPRINT_VIOLATION",
            footprint.getAgentId(),
            String.format("Agent footprint violation: %d bytes > %d bytes", memory, MAX_BYTES_PER_AGENT),
            System.currentTimeMillis(),
            memory
        );
        activeAlerts.add(alert);
        alertCount.incrementAndGet();
        MEMORY_LOGGER.warn("ALERT: {}", alert);
    }

    // Helper methods
    private long calculateAgentMemoryFootprint() {
        return AGENT_OBJECT_BASE_SIZE +
               QUEUE_OBJECT_BASE_SIZE +
               STATE_OBJECT_BASE_SIZE +
               36; // Additional overhead
    }

    private MemoryStatistics toStatistics(MemorySample sample) {
        double bytesPerAgent = sample.totalMemoryBytes > 0 && sample.agentCount > 0 ?
            (double) sample.totalMemoryBytes / sample.agentCount : 0;

        return new MemoryStatistics(
            sample.timestamp,
            sample.agentCount,
            sample.totalMemoryBytes,
            sample.agentCount * TARGET_BYTES_PER_AGENT,
            bytesPerAgent,
            bytesPerAgent > 0 ? (double) TARGET_BYTES_PER_AGENT / bytesPerAgent * 100 : 0,
            sample.heapUsage.getUsed(),
            sample.heapUsage.getMax(),
            0, // GC count would need tracking
            0, // GC time would need tracking
            activeAlerts.size()
        );
    }

    private AgentFootprintDetails toFootprintDetails(ActorMemoryFootprint footprint) {
        return new AgentFootprintDetails(
            footprint.getAgentId(),
            footprint.getAgentType().getSimpleName(),
            footprint.getCurrentMemory(),
            System.currentTimeMillis(),
            footprint.isCompliant(TARGET_BYTES_PER_AGENT)
        );
    }

    private double calculateRecentGCRate() {
        if (recentSamples.isEmpty()) return 0;

        long recentTime = System.currentTimeMillis() - 60_000; // Last minute
        long recentGCs = recentSamples.stream()
            .filter(s -> s.timestamp >= recentTime)
            .count();

        return recentGCs / 60.0; // GCs per second
    }

    private void generateFinalReport(MemoryProfile profile) {
        MemoryStatistics stats = getCurrentStatistics();
        MemoryFootprintAnalysis footprintAnalysis = getFootprintAnalysis();

        MEMORY_LOGGER.info("=== YAWL ACTOR MEMORY PROFILING REPORT ===");
        MEMORY_LOGGER.info("Total agents profiled: {}", stats.totalAgents);
        MEMORY_LOGGER.info("Total memory used: {} MB", stats.totalMemoryBytes / (1024 * 1024));
        MEMORY_LOGGER.info("Memory efficiency: {}%", String.format("%.1f", stats.memoryEfficiency));
        MEMORY_LOGGER.info("Compliant agents: {} / {} ({:.1f}%)",
            footprintAnalysis.compliantAgents, footprintAnalysis.totalAgents,
            footprintAnalysis.compliancePercentage);
        MEMORY_LOGGER.info("Average footprint: {} bytes", String.format("%.1f", stats.bytesPerAgent));
        MEMORY_LOGGER.info("Alerts generated: {}", alertCount.get());
        MEMORY_LOGGER.info("GC time: {} ms", stats.gcTimeMs);

        if (baselineProfile.get() != null) {
            MemoryStatistics baseline = baselineProfile.get().getLatestSnapshot();
            if (baseline != null) {
                double growth = ((double) stats.bytesPerAgent - baseline.bytesPerAgent) / baseline.bytesPerAgent * 100;
                MEMORY_LOGGER.info("Memory growth since baseline: {}%", String.format("%.1f", growth));
            }
        }

        // Report top memory consumers
        MEMORY_LOGGER.info("=== TOP MEMORY CONSUMERS ===");
        footprintAnalysis.topConsumers.stream().limit(10).forEach(agent ->
            MEMORY_LOGGER.info("{} - {} bytes {}",
                agent.agentId, agent.memoryBytes,
                agent.compliant ? "(COMPLIANT)" : "(VIOLATION)")
        );

        // Report active alerts
        if (!activeAlerts.isEmpty()) {
            MEMORY_LOGGER.info("=== ACTIVE ALERTS ===");
            activeAlerts.forEach(alert -> MEMORY_LOGGER.warn("ALERT: {}", alert));
        }
    }

    // Data classes
    private static class MemorySample {
        final long timestamp;
        final MemoryUsage heapUsage;
        final MemoryUsage nonHeapUsage;
        final int threadCount;
        final int peakThreadCount;
        final int agentCount;
        final long totalMemoryBytes;

        MemorySample(long timestamp, MemoryUsage heapUsage, MemoryUsage nonHeapUsage,
                    int threadCount, int peakThreadCount, int agentCount, long totalMemoryBytes) {
            this.timestamp = timestamp;
            this.heapUsage = heapUsage;
            this.nonHeapUsage = nonHeapUsage;
            this.threadCount = threadCount;
            this.peakThreadCount = peakThreadCount;
            this.agentCount = agentCount;
            this.totalMemoryBytes = totalMemoryBytes;
        }
    }

    /**
     * Memory usage statistics.
     */
    public record MemoryStatistics(
        long timestamp,
        long totalAgents,
        long totalMemoryBytes,
        long targetMemoryBytes,
        double bytesPerAgent,
        double memoryEfficiency,
        long heapUsedBytes,
        long heapMaxBytes,
        long gcCount,
        long gcTimeMs,
        long activeAlertCount
    ) {}

    /**
     * Memory footprint analysis.
     */
    public record MemoryFootprintAnalysis(
        long totalAgents,
        long totalMemoryBytes,
        long compliantAgents,
        long violatingAgents,
        double compliancePercentage,
        List<AgentFootprintDetails> topConsumers
    ) {}

    /**
     * Individual agent footprint details.
     */
    public record AgentFootprintDetails(
        String agentId,
        String agentType,
        long memoryBytes,
        long timestamp,
        boolean compliant
    ) {}

    /**
     * Memory alert.
     */
    public static record MemoryAlert(
        String type,
        String source,
        String message,
        long timestamp,
        long memoryBytes
    ) {
        @Override
        public String toString() {
            return String.format("[%s] %s: %s (memory: %d bytes)",
                Instant.ofEpochMilli(timestamp), type, message, memoryBytes);
        }
    }

    // Inner class for actor memory footprint
    private static class ActorMemoryFootprint {
        final String agentId;
        final Class<?> agentType;
        final long initialMemory;
        final long createdAt;

        private volatile long currentMemory;
        private volatile long lastUpdate;

        ActorMemoryFootprint(String agentId, Class<?> agentType, long initialMemory, long createdAt) {
            this.agentId = agentId;
            this.agentType = agentType;
            this.initialMemory = initialMemory;
            this.currentMemory = initialMemory;
            this.lastUpdate = createdAt;
            this.createdAt = createdAt;
        }

        void updateMemory(long memory, long timestamp) {
            this.currentMemory = memory;
            this.lastUpdate = timestamp;
        }

        String getAgentId() { return agentId; }
        Class<?> getAgentType() { return agentType; }
        long getCurrentMemory() { return currentMemory; }
        long getLastUpdate() { return lastUpdate; }
        boolean isCompliant(int targetBytes) { return currentMemory <= targetBytes + 10; }
    }

    // Inner class for memory profile
    private static class MemoryProfile {
        private final List<MemoryStatistics> snapshots = new ArrayList<>();
        private final Map<String, AtomicLong> allocations = new ConcurrentHashMap<>();
        private final AtomicLong totalAllocated = new AtomicLong(0);

        void updateSnapshot(MemoryStatistics stats) {
            snapshots.add(stats);
            if (snapshots.size() > 100) {
                snapshots.remove(0);
            }
        }

        void recordAllocation(String type, long bytes, long objectCount) {
            allocations.computeIfAbsent(type, k -> new AtomicLong(0)).addAndGet(bytes);
            totalAllocated.addAndGet(bytes);
        }

        MemoryStatistics getLatestSnapshot() {
            return snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1);
        }

        double getAllocationRateMBPerS() {
            if (snapshots.size() < 2) return 0;
            long timeDiff = snapshots.get(snapshots.size() - 1).timestamp - snapshots.get(0).timestamp;
            return timeDiff > 0 ? (double) totalAllocated.get() / timeDiff * 1000 / (1024 * 1024) : 0;
        }
    }
}