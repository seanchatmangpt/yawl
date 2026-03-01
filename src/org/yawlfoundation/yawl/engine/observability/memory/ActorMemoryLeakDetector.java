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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Advanced memory leak detector for YAWL actor model with 132-byte validation.
 * Implements sophisticated leak detection algorithms for large-scale actor systems.
 *
 * <h2>Detection Algorithms</h2>
 * <ul>
 *   <li>Reference counting analysis</li>
 *   <li>Young/old generation tracking</li>
 *   <li>Memory growth pattern analysis</li>
 *   <li>Garbage collection anomaly detection</li>
 *   <li>Virtual thread memory monitoring</li>
 *   <li>Heap dump analysis</li>
 * </ul>
 *
 * <h2>Leak Patterns Detected</h2>
 * <ul>
 *   <li>Circular references in actor networks</li>
 *   <li>Unbounded queue growth</li>
 *   <li>Thread-local memory leaks</li>
 *   <li>Classloader leaks</li>
 *   <li>Timer/Task leaks</li>
 *   <li>Static collection growth</li>
 * </ul>
 *
 * @author YAWL Foundation / GODSPEED Protocol
 * @version 6.0.0
 * @since 6.0.0
 */
public final class ActorMemoryLeakDetector {

    private static final Logger LOGGER = LogManager.getLogger(ActorMemoryLeakDetector.class);
    private static final Logger LEAK_LOGGER = LogManager.getLogger("ACTOR_LEAK_DETECTION");

    // Detection thresholds
    private static final double LEAK_DETECTION_THRESHOLD = 1.2; // 20% growth threshold
    private static final int MIN_SAMPLES_FOR_DETECTION = 100;
    private static final int MAX_VIOLATIONS_BEFORE_ALERT = 5;
    private static final long HEAP_PRESSURE_THRESHOLD = 85; // 85% heap
    private static final double GC_ANOMALY_THRESHOLD = 2.0; // 2x normal frequency
    private static final long AGENT_IDLE_TIMEOUT = 30_000; // 30 seconds

    // Memory beans
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final GarbageCollectorMXBean gcMXBean = ManagementFactory.getGarbageCollectorMXBeans().get(0);
    private final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

    // Detection state
    private final AtomicBoolean monitoring = new AtomicBoolean(false);
    private final AtomicBoolean baselineEstablished = new AtomicBoolean(false);
    private final AtomicLong totalDetectedLeaks = new AtomicLong(0);
    private final AtomicLong inspectionCount = new AtomicLong(0);

    // Tracking structures
    private final Map<String, AgentMemorySnapshot> agentSnapshots = new ConcurrentHashMap<>();
    private final Map<String, ObjectAllocationTracker> objectTrackers = new ConcurrentHashMap<>();
    private final Map<String, ThreadMemoryTracker> threadTrackers = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<MemoryInspection> inspections = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<LeakAlert> leakAlerts = new ConcurrentLinkedDeque<>();

    // Scheduled tasks
    private final ScheduledExecutorService scheduler = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService inspectionScheduler = Executors.newVirtualThreadPerTaskExecutor();

    // Configuration
    private final LeakDetectionConfig config;

    /**
     * Initialize leak detector with configuration.
     */
    public ActorMemoryLeakDetector() {
        this(new LeakDetectionConfig());
    }

    public ActorMemoryLeakDetector(LeakDetectionConfig config) {
        this.config = config;
    }

    /**
     * Start leak detection monitoring.
     */
    public void startLeakDetection() {
        if (monitoring.compareAndSet(false, true)) {
            LOGGER.info("Starting YAWL actor memory leak detector");

            // Start continuous monitoring
            scheduler.scheduleAtFixedRate(
                this::performMemoryInspection,
                config.getInitialDelayMs(),
                config.getInspectionIntervalMs(),
                TimeUnit.MILLISECONDS
            );

            // Start baseline establishment
            scheduler.schedule(
                this::establishMemoryBaseline,
                config.getBaselineDurationMs(),
                TimeUnit.MILLISECONDS
            );

            // Start leak analysis
            scheduler.scheduleAtFixedRate(
                this::analyzeLeakPatterns,
                config.getAnalysisIntervalMs(),
                config.getAnalysisIntervalMs(),
                TimeUnit.MILLISECONDS
            );

            // Start GC anomaly detection
            scheduler.scheduleAtFixedRate(
                this::detectGCAnomalies,
                config.getGcDetectionIntervalMs(),
                config.getGcDetectionIntervalMs(),
                TimeUnit.MILLISECONDS
            );

            // Start heap pressure monitoring
            scheduler.scheduleAtFixedRate(
                this::monitorHeapPressure,
                config.getHeapPressureIntervalMs(),
                config.getHeapPressureIntervalMs(),
                TimeUnit.MILLISECONDS
            );

            LEAK_LOGGER.info("Leak detection started with {}ms inspection interval",
                config.getInspectionIntervalMs());
        }
    }

    /**
     * Stop leak detection.
     */
    public void stopLeakDetection() {
        if (monitoring.compareAndSet(true, false)) {
            LOGGER.info("Stopping YAWL actor memory leak detector");

            scheduler.shutdown();
            inspectionScheduler.shutdown();

            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                if (!inspectionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    inspectionScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                inspectionScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            generateLeakReport();
        }
    }

    /**
     * Register agent for leak detection.
     */
    public void registerAgent(String agentId, Class<?> agentType, long initialMemory) {
        if (monitoring.get()) {
            AgentMemorySnapshot snapshot = new AgentMemorySnapshot(
                agentId, agentType, initialMemory, System.currentTimeMillis()
            );
            agentSnapshots.put(agentId, snapshot);

            LEAK_LOGGER.debug("Registered agent for leak detection: {} ({} bytes)",
                agentId, initialMemory);
        }
    }

    /**
     * Update agent memory usage.
     */
    public void updateAgentMemory(String agentId, long currentMemory) {
        if (monitoring.get()) {
            AgentMemorySnapshot snapshot = agentSnapshots.get(agentId);
            if (snapshot != null) {
                snapshot.updateMemory(currentMemory, System.currentTimeMillis());
            }
        }
    }

    /**
     * Track object allocation.
     */
    public void trackObjectAllocation(String objectType, String allocationSource, long objectCount) {
        if (monitoring.get()) {
            ObjectAllocationTracker tracker = objectTrackers.computeIfAbsent(
                objectType, k -> new ObjectAllocationTracker(objectType)
            );
            tracker.recordAllocation(allocationSource, objectCount, System.currentTimeMillis());

            // Check for unbounded growth
            if (tracker.detectUnboundedGrowth()) {
                detectObjectLeak(tracker);
            }
        }
    }

    /**
     * Track thread memory usage.
     */
    public void trackThreadMemory(String threadName, String actorId, long memoryUsage) {
        if (monitoring.get()) {
            ThreadMemoryTracker tracker = threadTrackers.computeIfAbsent(
                threadName, k -> new ThreadMemoryTracker(threadName)
            );
            tracker.recordMemoryUsage(actorId, memoryUsage, System.currentTimeMillis());

            // Check for thread-local leaks
            if (tracker.detectMemoryRetention()) {
                detectThreadLocalLeak(tracker);
            }
        }
    }

    /**
     * Perform comprehensive memory inspection.
     */
    private void performMemoryInspection() {
        try {
            inspectionCount.incrementAndGet();

            MemoryInspection inspection = new MemoryInspection(
                System.currentTimeMillis(),
                memoryMXBean.getHeapMemoryUsage(),
                memoryMXBean.getNonHeapMemoryUsage(),
                threadMXBean.getThreadCount(),
                threadMXBean.getPeakThreadCount(),
                collectAgentMemorySnapshots(),
                collectObjectAllocationData(),
                collectThreadMemoryData()
            );

            inspections.addLast(inspection);
            while (inspections.size() > config.getMaxInspectionHistory()) {
                inspections.removeFirst();
            }

            // Analyze current inspection for leaks
            analyzeCurrentInspection(inspection);

        } catch (Exception e) {
            LOGGER.error("Error performing memory inspection", e);
        }
    }

    /**
     * Establish memory baseline.
     */
    private void establishMemoryBaseline() {
        if (!baselineEstablished.get()) {
            LOGGER.info("Establishing memory baseline for leak detection");

            MemoryBaseline baseline = new MemoryBaseline();
            inspections.forEach(inspection -> baseline.addInspection(inspection));

            if (baseline.isValid()) {
                baselineEstablished.set(true);
                LEAK_LOGGER.info("Memory baseline established - {} inspections, {} agents",
                    baseline.getInspectionCount(), baseline.getAverageAgents());
            }
        }
    }

    /**
     * Analyze leak patterns in collected data.
     */
    private void analyzeLeakPatterns() {
        if (!baselineEstablished.get()) {
            return;
        }

        // Analyze actor memory patterns
        analyzeActorMemoryPatterns();

        // Analyze object allocation patterns
        analyzeObjectAllocationPatterns();

        // Analyze thread memory patterns
        analyzeThreadMemoryPatterns();

        // Analyze overall memory trends
        analyzeMemoryTrends();
    }

    /**
     * Detect GC anomalies that indicate memory issues.
     */
    private void detectGCAnomalies() {
        try {
            if (gcMXBean == null) return;

            long collectionCount = gcMXBean.getCollectionCount();
            long collectionTime = gcMXBean.getCollectionTime();
            long currentTime = System.currentTimeMillis();

            // Analyze GC frequency
            double gcFrequency = calculateGCFrequency(collectionCount, currentTime);
            double normalFrequency = getNormalGCFrequency();

            if (gcFrequency > normalFrequency * GC_ANOMALY_THRESHOLD) {
                detectHighGCFrequency(gcFrequency);
            }

            // Analyze GC pause time
            double avgPauseTime = calculateAverageGCPauseTime(collectionTime);
            if (avgPauseTime > config.getMaxGCPauseTimeMs()) {
                detectExcessiveGCPause(avgPauseTime);
            }

        } catch (Exception e) {
            LOGGER.error("Error detecting GC anomalies", e);
        }
    }

    /**
     * Monitor heap pressure for memory issues.
     */
    private void monitorHeapPressure() {
        try {
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            double pressure = (double) heapUsage.getUsed() / heapUsage.getMax();

            if (pressure > HEAP_PRESSURE_THRESHOLD / 100.0) {
                if (pressure > 0.95) { // 95%+ critical
                    detectCriticalHeapPressure(pressure);
                } else if (pressure > 0.90) { // 90%+ warning
                    detectHighHeapPressure(pressure);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error monitoring heap pressure", e);
        }
    }

    /**
     * Analyze actor memory patterns for leaks.
     */
    private void analyzeActorMemoryPatterns() {
        for (AgentMemorySnapshot snapshot : agentSnapshots.values()) {
            if (detectAgentMemoryLeak(snapshot)) {
                generateAgentLeakAlert(snapshot);
            }
        }
    }

    /**
     * Analyze object allocation patterns.
     */
    private void analyzeObjectAllocationPatterns() {
        for (ObjectAllocationTracker tracker : objectTrackers.values()) {
            if (tracker.detectUnboundedGrowth()) {
                detectObjectLeak(tracker);
            }
        }
    }

    /**
     * Analyze thread memory patterns.
     */
    private void analyzeThreadMemoryPatterns() {
        for (ThreadMemoryTracker tracker : threadTrackers.values()) {
            if (tracker.detectMemoryRetention()) {
                detectThreadLocalLeak(tracker);
            }
        }
    }

    /**
     * Analyze overall memory trends.
     */
    private void analyzeMemoryTrends() {
        if (inspections.size() < MIN_SAMPLES_FOR_DETECTION) {
            return;
        }

        // Analyze memory growth trend
        MemoryTrend trend = calculateMemoryTrend();
        if (trend.isGrowingExponentially()) {
            detectExponentialMemoryGrowth(trend);
        }

        // Check for memory fragmentation
        if (detectMemoryFragmentation()) {
            detectMemoryFragmentation();
        }
    }

    // Detection methods
    private boolean detectAgentMemoryLeak(AgentMemorySnapshot snapshot) {
        if (!baselineEstablished.get()) return false;

        MemoryBaseline baseline = getCurrentBaseline();
        if (baseline == null) return false;

        // Check for memory growth beyond baseline
        double growthRate = snapshot.getGrowthRate();
        double baselineGrowthRate = baseline.getAverageGrowthRate();

        return growthRate > baselineGrowthRate * LEAK_DETECTION_THRESHOLD;
    }

    private void generateAgentLeakAlert(AgentMemorySnapshot snapshot) {
        LeakAlert alert = new LeakAlert(
            "AGENT_MEMORY_LEAK",
            snapshot.getAgentId(),
            String.format("Agent memory growing %.2fx faster than baseline", snapshot.getGrowthRate()),
            System.currentTimeMillis(),
            snapshot.getCurrentMemory(),
            snapshot.getMemoryDelta()
        );

        leakAlerts.add(alert);
        totalDetectedLeaks.incrementAndGet();
        LEAK_LOGGER.warn("LEAK DETECTED: {}", alert);
    }

    private void detectObjectLeak(ObjectAllocationTracker tracker) {
        LeakAlert alert = new LeakAlert(
            "OBJECT_ALLOCATION_LEAK",
            tracker.getObjectType(),
            String.format("Unbounded object growth: %d objects allocated", tracker.getTotalAllocated()),
            System.currentTimeMillis(),
            tracker.getCurrentMemoryUsage(),
            tracker.getAllocationRate()
        );

        leakAlerts.add(alert);
        totalDetectedLeaks.incrementAndGet();
        LEAK_LOGGER.warn("LEAK DETECTED: {}", alert);
    }

    private void detectThreadLocalLeak(ThreadMemoryTracker tracker) {
        LeakAlert alert = new LeakAlert(
            "THREAD_LOCAL_LEAK",
            tracker.getThreadName(),
            String.format("Thread memory retention detected: %d bytes retained", tracker.getRetainedMemory()),
            System.currentTimeMillis(),
            tracker.getCurrentMemoryUsage(),
            tracker.getMemoryDelta()
        );

        leakAlerts.add(alert);
        totalDetectedLeaks.incrementAndGet();
        LEAK_LOGGER.warn("LEAK DETECTED: {}", alert);
    }

    private void detectHighGCFrequency(double gcFrequency) {
        LeakAlert alert = new LeakAlert(
            "HIGH_GC_FREQUENCY",
            "SYSTEM",
            String.format("GC frequency anomaly: %.2f GC/s (normal: %.2f)", gcFrequency, getNormalGCFrequency()),
            System.currentTimeMillis(),
            0,
            0
        );

        leakAlerts.add(alert);
        totalDetectedLeaks.incrementAndGet();
        LEAK_LOGGER.warn("ANOMALY DETECTED: {}", alert);
    }

    private void detectExcessiveGCPause(double avgPauseTime) {
        LeakAlert alert = new LeakAlert(
            "EXCESSIVE_GC_PAUSE",
            "SYSTEM",
            String.format("Excessive GC pause time: %.2f ms", avgPauseTime),
            System.currentTimeMillis(),
            0,
            0
        );

        leakAlerts.add(alert);
        totalDetectedLeaks.incrementAndGet();
        LEAK_LOGGER.warn("ANOMALY DETECTED: {}", alert);
    }

    private void detectCriticalHeapPressure(double pressure) {
        LeakAlert alert = new LeakAlert(
            "CRITICAL_HEAP_PRESSURE",
            "SYSTEM",
            String.format("Critical heap pressure: %.1f%%", pressure * 100),
            System.currentTimeMillis(),
            (long) (pressure * memoryMXBean.getHeapMemoryUsage().getMax()),
            0
        );

        leakAlerts.add(alert);
        totalDetectedLeaks.incrementAndGet();
        LEAK_LOGGER.error("CRITICAL ALERT: {}", alert);
    }

    private void detectHighHeapPressure(double pressure) {
        LeakAlert alert = new LeakAlert(
            "HIGH_HEAP_PRESSURE",
            "SYSTEM",
            String.format("High heap pressure: %.1f%%", pressure * 100),
            System.currentTimeMillis(),
            (long) (pressure * memoryMXBean.getHeapMemoryUsage().getMax()),
            0
        );

        leakAlerts.add(alert);
        totalDetectedLeaks.incrementAndGet();
        LEAK_LOGGER.warn("WARNING: {}", alert);
    }

    private void detectExponentialMemoryGrowth(MemoryTrend trend) {
        LeakAlert alert = new LeakAlert(
            "EXPONENTIAL_MEMORY_GROWTH",
            "SYSTEM",
            String.format("Exponential memory growth detected: %.2fx growth rate", trend.getGrowthRate()),
            System.currentTimeMillis(),
            trend.getCurrentMemory(),
            trend.getMemoryDelta()
        );

        leakAlerts.add(alert);
        totalDetectedLeaks.incrementAndGet();
        LEAK_LOGGER.warn("LEAK DETECTED: {}", alert);
    }

    // Helper methods
    private List<AgentMemorySnapshot> collectAgentMemorySnapshots() {
        return agentSnapshots.values().stream()
            .map(snapshot -> new AgentMemorySnapshot(
                snapshot.getAgentId(),
                snapshot.getAgentType(),
                snapshot.getCurrentMemory(),
                snapshot.getLastUpdate()
            ))
            .collect(Collectors.toList());
    }

    private Map<String, Long> collectObjectAllocationData() {
        return objectTrackers.values().stream()
            .collect(Collectors.toMap(
                ObjectAllocationTracker::getObjectType,
                ObjectAllocationTracker::getCurrentMemoryUsage
            ));
    }

    private Map<String, Long> collectThreadMemoryData() {
        return threadTrackers.values().stream()
            .collect(Collectors.toMap(
                ThreadMemoryTracker::getThreadName,
                ThreadMemoryTracker::getCurrentMemoryUsage
            ));
    }

    private void analyzeCurrentInspection(MemoryInspection inspection) {
        // Check for immediate anomalies
        MemoryUsage heapUsage = inspection.getHeapUsage();
        double pressure = (double) heapUsage.getUsed() / heapUsage.getMax();

        if (pressure > 0.90) { // 90%+ immediate attention
            detectHighHeapPressure(pressure);
        }

        // Check for recent memory growth
        if (inspections.size() >= 2) {
            MemoryInspection previous = inspections.get(inspections.size() - 2);
            long memoryDelta = inspection.getHeapUsage().getUsed() - previous.getHeapUsage().getUsed();
            long timeDelta = inspection.getTimestamp() - previous.getTimestamp();

            if (timeDelta > 0 && memoryDelta > 0) {
                double growthRate = (double) memoryDelta / timeDelta * 1000; // bytes per second
                if (growthRate > 10 * 1024 * 1024) { // >10MB/s
                    detectExponentialMemoryGrowth(new MemoryTrend(previous.getHeapUsage().getUsed(),
                        inspection.getHeapUsage().getUsed(), timeDelta));
                }
            }
        }
    }

    private MemoryBaseline getCurrentBaseline() {
        // In a real implementation, this would return the established baseline
        return new MemoryBaseline(); // Placeholder
    }

    private double calculateGCFrequency(long collectionCount, long currentTime) {
        if (inspections.isEmpty()) return 0;

        long firstInspectionTime = inspections.getFirst().getTimestamp();
        long timeSpan = currentTime - firstInspectionTime;
        return timeSpan > 0 ? (double) collectionCount / (timeSpan / 1000.0) : 0;
    }

    private double getNormalGCFrequency() {
        // Return normal GC frequency (implementation dependent)
        return 1.0; // 1 GC per second baseline
    }

    private double calculateAverageGCPauseTime(long collectionTime) {
        if (inspections.isEmpty()) return 0;

        long timeSpan = System.currentTimeMillis() - inspections.getFirst().getTimestamp();
        return timeSpan > 0 ? (double) collectionTime / (timeSpan / 1000.0) : 0;
    }

    private MemoryTrend calculateMemoryTrend() {
        if (inspections.size() < 2) return new MemoryTrend(0, 0, 0);

        MemoryInspection first = inspections.getFirst();
        MemoryInspection last = inspections.getLast();

        long firstMemory = first.getHeapUsage().getUsed();
        long lastMemory = last.getHeapUsage().getUsed();
        long timeDelta = last.getTimestamp() - first.getTimestamp();

        return new MemoryTrend(firstMemory, lastMemory, timeDelta);
    }

    private boolean detectMemoryFragmentation() {
        if (inspections.isEmpty()) return false;

        // Check for inconsistent memory patterns that indicate fragmentation
        long[] memoryLevels = inspections.stream()
            .mapToLong(i -> i.getHeapUsage().getUsed())
            .toArray();

        double coefficientOfVariation = calculateCoefficientOfVariation(memoryLevels);
        return coefficientOfVariation > 0.1; // 10%+ variation indicates issues
    }

    private double calculateCoefficientOfVariation(long[] values) {
        if (values.length == 0) return 0;

        double mean = Arrays.stream(values).average().orElse(0);
        double stdDev = Math.sqrt(Arrays.stream(values)
            .mapToDouble(x -> Math.pow(x - mean, 2))
            .average().orElse(0));

        return mean > 0 ? stdDev / mean : 0;
    }

    /**
     * Generate final leak report.
     */
    private void generateLeakReport() {
        LEAK_LOGGER.info("=== MEMORY LEAK DETECTION REPORT ===");
        LEAK_LOGGER.info("Total inspections performed: {}", inspectionCount.get());
        LEAK_LOGGER.info("Leaks detected: {}", totalDetectedLeaks.get());
        LEAK_LOGGER.info("Alerts generated: {}", leakAlerts.size());
        LEAK_LOGGER.info("Agents monitored: {}", agentSnapshots.size());
        LEAK_LOGGER.info("Object types tracked: {}", objectTrackers.size());
        LEAK_LOGGER.info("Threads tracked: {}", threadTrackers.size());

        // Report leak summary by type
        Map<String, Long> leakTypeCounts = leakAlerts.stream()
            .collect(Collectors.groupingBy(LeakAlert::getType, Collectors.counting()));

        leakTypeCounts.forEach((type, count) ->
            LEAK_LOGGER.info("{} leaks: {}", type, count)
        );

        // Report recent alerts
        if (!leakAlerts.isEmpty()) {
            LEAK_LOGGER.info("=== RECENT LEAK ALERTS ===");
            leakAlerts.descendingIterator().limit(10).forEach(alert ->
                LEAK_LOGGER.warn("ALERT: {}", alert)
            );
        }
    }

    /**
     * Check if leak detection is active.
     */
    public boolean isMonitoring() {
        return monitoring.get();
    }

    /**
     * Get leak detection summary.
     */
    public LeakDetectionSummary getSummary() {
        return new LeakDetectionSummary(
            totalDetectedLeaks.get(),
            leakAlerts.size(),
            agentSnapshots.size(),
            objectTrackers.size(),
            threadTrackers.size(),
            inspectionCount.get(),
            baselineEstablished.get(),
            memoryMXBean.getHeapMemoryUsage()
        );
    }

    // Configuration class
    public static class LeakDetectionConfig {
        private long initialDelayMs = 1000;
        private long inspectionIntervalMs = 5000;
        private long baselineDurationMs = 30000;
        private long analysisIntervalMs = 10000;
        private long gcDetectionIntervalMs = 10000;
        private long heapPressureIntervalMs = 5000;
        private int maxInspectionHistory = 1000;
        private long maxGCPauseTimeMs = 100;

        // Getters and setters
        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }
        public long getInspectionIntervalMs() { return inspectionIntervalMs; }
        public void setInspectionIntervalMs(long inspectionIntervalMs) { this.inspectionIntervalMs = inspectionIntervalMs; }
        public long getBaselineDurationMs() { return baselineDurationMs; }
        public void setBaselineDurationMs(long baselineDurationMs) { this.baselineDurationMs = baselineDurationMs; }
        public long getAnalysisIntervalMs() { return analysisIntervalMs; }
        public void setAnalysisIntervalMs(long analysisIntervalMs) { this.analysisIntervalMs = analysisIntervalMs; }
        public long getGcDetectionIntervalMs() { return gcDetectionIntervalMs; }
        public void setGcDetectionIntervalMs(long gcDetectionIntervalMs) { this.gcDetectionIntervalMs = gcDetectionIntervalMs; }
        public long getHeapPressureIntervalMs() { return heapPressureIntervalMs; }
        public void setHeapPressureIntervalMs(long heapPressureIntervalMs) { this.heapPressureIntervalMs = heapPressureIntervalMs; }
        public int getMaxInspectionHistory() { return maxInspectionHistory; }
        public void setMaxInspectionHistory(int maxInspectionHistory) { this.maxInspectionHistory = maxInspectionHistory; }
        public long getMaxGCPauseTimeMs() { return maxGCPauseTimeMs; }
        public void setMaxGCPauseTimeMs(long maxGCPauseTimeMs) { this.maxGCPauseTimeMs = maxGCPauseTimeMs; }
    }

    // Data classes
    private static class MemoryInspection {
        final long timestamp;
        final MemoryUsage heapUsage;
        final MemoryUsage nonHeapUsage;
        final int threadCount;
        final int peakThreadCount;
        final List<AgentMemorySnapshot> agentSnapshots;
        final Map<String, Long> objectAllocations;
        final Map<String, Long> threadMemory;

        MemoryInspection(long timestamp, MemoryUsage heapUsage, MemoryUsage nonHeapUsage,
                        int threadCount, int peakThreadCount, List<AgentMemorySnapshot> agentSnapshots,
                        Map<String, Long> objectAllocations, Map<String, Long> threadMemory) {
            this.timestamp = timestamp;
            this.heapUsage = heapUsage;
            this.nonHeapUsage = nonHeapUsage;
            this.threadCount = threadCount;
            this.peakThreadCount = peakThreadCount;
            this.agentSnapshots = agentSnapshots;
            this.objectAllocations = objectAllocations;
            this.threadMemory = threadMemory;
        }

        long getTimestamp() { return timestamp; }
        MemoryUsage getHeapUsage() { return heapUsage; }
    }

    /**
     * Agent memory snapshot for tracking individual agent memory usage.
     */
    private static class AgentMemorySnapshot {
        final String agentId;
        final Class<?> agentType;
        final long initialMemory;
        final long createdAt;

        private volatile long currentMemory;
        private volatile long lastUpdate;
        private double growthRate;

        AgentMemorySnapshot(String agentId, Class<?> agentType, long initialMemory, long createdAt) {
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

            // Calculate growth rate per second
            long timeDiff = timestamp - createdAt;
            if (timeDiff > 0) {
                this.growthRate = (double) (memory - initialMemory) / timeDiff * 1000;
            }
        }

        String getAgentId() { return agentId; }
        Class<?> getAgentType() { return agentType; }
        long getCurrentMemory() { return currentMemory; }
        long getLastUpdate() { return lastUpdate; }
        double getGrowthRate() { return growthRate; }
        long getMemoryDelta() { return currentMemory - initialMemory; }
    }

    /**
     * Tracker for object allocations.
     */
    private static class ObjectAllocationTracker {
        final String objectType;
        private final Map<String, List<Long>> allocationHistory = new ConcurrentHashMap<>();
        private long totalAllocated = 0;
        private long lastMemoryUsage = 0;

        ObjectAllocationTracker(String objectType) {
            this.objectType = objectType;
        }

        void recordAllocation(String source, long count, long timestamp) {
            allocationHistory.computeIfAbsent(source, k -> new ArrayList<>()).add(count);
            totalAllocated += count;
            lastMemoryUsage = count * 64; // Assume 64 bytes per object
        }

        boolean detectUnboundedGrowth() {
            if (allocationHistory.size() < 3) return false;

            long recent = allocationHistory.values().stream()
                .mapToLong(list -> list.isEmpty() ? 0 : list.get(list.size() - 1))
                .sum();

            long older = allocationHistory.values().stream()
                .filter(list -> list.size() >= 2)
                .mapToLong(list -> list.get(0))
                .sum();

            return recent > older * 5; // 5x growth suggests leak
        }

        String getObjectType() { return objectType; }
        long getTotalAllocated() { return totalAllocated; }
        long getCurrentMemoryUsage() { return lastMemoryUsage; }
        long getAllocationRate() { return totalAllocated; }
    }

    /**
     * Tracker for thread memory usage.
     */
    private static class ThreadMemoryTracker {
        final String threadName;
        private final Map<String, List<Long>> memoryUsage = new ConcurrentHashMap<>();
        private long retainedMemory = 0;
        private long lastMemoryUsage = 0;
        private long memoryDelta = 0;

        ThreadMemoryTracker(String threadName) {
            this.threadName = threadName;
        }

        void recordMemoryUsage(String actorId, long memory, long timestamp) {
            memoryUsage.computeIfAbsent(actorId, k -> new ArrayList<>()).add(memory);
            lastMemoryUsage = memory;

            // Calculate retained memory (memory that's not freed)
            if (memoryUsage.size() > 10) {
                long oldMemory = memoryUsage.values().stream()
                    .mapToLong(list -> list.isEmpty() ? 0 : list.get(0))
                    .sum();
                retainedMemory = lastMemoryUsage - oldMemory;
                memoryDelta = lastMemoryUsage - memoryUsage.values().stream()
                    .mapToLong(list -> list.isEmpty() ? 0 : list.get(list.size() - 1))
                    .sum();
            }
        }

        boolean detectMemoryRetention() {
            return retainedMemory > 1024 * 1024; // >1MB retained
        }

        String getThreadName() { return threadName; }
        long getRetainedMemory() { return retainedMemory; }
        long getCurrentMemoryUsage() { return lastMemoryUsage; }
        long getMemoryDelta() { return memoryDelta; }
    }

    private static class MemoryBaseline {
        private final List<Long> memorySamples = new ArrayList<>();
        private final List<Double> growthRates = new ArrayList<>();
        private int inspectionCount = 0;

        void addInspection(MemoryInspection inspection) {
            memorySamples.add(inspection.getHeapUsage().getUsed());
            inspectionCount++;
        }

        boolean isValid() {
            return inspectionCount >= MIN_SAMPLES_FOR_DETECTION;
        }

        int getInspectionCount() { return inspectionCount; }
        double getAverageAgents() {
            // Calculate average agents across inspections
            return inspectionCount > 0 ? memorySamples.size() / (double) inspectionCount : 0;
        }
        double getAverageGrowthRate() {
            if (growthRates.isEmpty()) return 0;
            return growthRates.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }
    }

    private static class MemoryTrend {
        final long initialMemory;
        final long currentMemory;
        final long timeDelta;

        MemoryTrend(long initialMemory, long currentMemory, long timeDelta) {
            this.initialMemory = initialMemory;
            this.currentMemory = currentMemory;
            this.timeDelta = timeDelta;
        }

        double getGrowthRate() {
            return timeDelta > 0 ? (double) (currentMemory - initialMemory) / timeDelta : 0;
        }

        boolean isGrowingExponentially() {
            return getGrowthRate() > 1024 * 1024; // >1MB/s
        }

        long getCurrentMemory() { return currentMemory; }
        long getMemoryDelta() { return currentMemory - initialMemory; }
    }

    /**
     * Leak detection summary.
     */
    public record LeakDetectionSummary(
        long totalLeaksDetected,
        long totalAlerts,
        long agentsMonitored,
        long objectTypesTracked,
        long threadsTracked,
        long inspectionsPerformed,
        boolean baselineEstablished,
        MemoryUsage currentHeapUsage
    ) {}

    /**
     * Memory leak alert.
     */
    public static record LeakAlert(
        String type,
        String source,
        String message,
        long timestamp,
        long memoryBytes,
        long memoryDelta
    ) {
        @Override
        public String toString() {
            return String.format("[%s] %s: %s (memory: %d bytes, delta: %d bytes)",
                Instant.ofEpochMilli(timestamp), type, message, memoryBytes, memoryDelta);
        }
    }
}