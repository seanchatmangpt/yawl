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

package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lock contention tracking with heat map visualization support.
 *
 * <p>Tracks lock acquisition times across the YAWL engine and generates
 * heat map data for identifying contention hotspots. Integrates with
 * SLOTracker for SLO violation detection.</p>
 *
 * <h2>Heat Levels</h2>
 * <ul>
 *   <li><b>CRITICAL</b> - &gt;500ms wait time</li>
 *   <li><b>HIGH</b> - 200-500ms wait time</li>
 *   <li><b>MEDIUM</b> - 50-200ms wait time</li>
 *   <li><b>LOW</b> - &lt;50ms wait time</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * LockContentionTracker tracker = LockContentionTracker.getInstance();
 *
 * // Track lock acquisition
 * try (LockAcquisitionContext ctx = tracker.trackAcquisition("YNetRunner-case-123")) {
 *     // ... perform locked operation ...
 * }
 *
 * // Get heat map data
 * HeatMapData heatMap = tracker.getHeatMapData();
 * for (HeatMapEntry entry : heatMap.getEntries()) {
 *     System.out.println(entry.getLockName() + ": " + entry.getHeatLevel());
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
public final class LockContentionTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(LockContentionTracker.class);

    // Heat level thresholds (in milliseconds)
    private static final long CRITICAL_THRESHOLD_MS = 500;
    private static final long HIGH_THRESHOLD_MS = 200;
    private static final long MEDIUM_THRESHOLD_MS = 50;

    // Maximum history entries per lock
    private static final int MAX_HISTORY_PER_LOCK = 1000;

    // Singleton instance
    private static volatile LockContentionTracker instance;
    private static final ReentrantLock INSTANCE_LOCK = new ReentrantLock();

    /**
     * Heat level classification for lock contention.
     */
    public enum HeatLevel {
        CRITICAL("critical", 4),
        HIGH("high", 3),
        MEDIUM("medium", 2),
        LOW("low", 1),
        NONE("none", 0);

        private final String label;
        private final int severity;

        HeatLevel(String label, int severity) {
            this.label = label;
            this.severity = severity;
        }

        public String getLabel() { return label; }
        public int getSeverity() { return severity; }

        /**
         * Determines heat level from wait time in milliseconds.
         */
        public static HeatLevel fromWaitTimeMs(long waitMs) {
            if (waitMs >= CRITICAL_THRESHOLD_MS) return CRITICAL;
            if (waitMs >= HIGH_THRESHOLD_MS) return HIGH;
            if (waitMs >= MEDIUM_THRESHOLD_MS) return MEDIUM;
            if (waitMs > 0) return LOW;
            return NONE;
        }
    }

    /**
     * Context for tracking lock acquisition lifecycle.
     * Implements AutoCloseable for try-with-resources pattern.
     */
    public static final class LockAcquisitionContext implements AutoCloseable {
        private final LockContentionTracker tracker;
        private final String lockName;
        private final Instant startTime;
        private final long startNanoTime;
        private volatile boolean closed = false;

        private LockAcquisitionContext(LockContentionTracker tracker, String lockName) {
            this.tracker = tracker;
            this.lockName = lockName;
            this.startTime = Instant.now();
            this.startNanoTime = System.nanoTime();
        }

        /**
         * Gets the lock name being tracked.
         */
        public String getLockName() { return lockName; }

        /**
         * Gets the acquisition start time.
         */
        public Instant getStartTime() { return startTime; }

        /**
         * Gets the elapsed duration so far.
         */
        public Duration getElapsed() {
            return Duration.ofNanos(System.nanoTime() - startNanoTime);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                long waitNanos = System.nanoTime() - startNanoTime;
                tracker.recordRelease(lockName, waitNanos);
            }
        }
    }

    /**
     * Individual lock contention entry in the heat map.
     */
    public static final class HeatMapEntry {
        private final String lockName;
        private final long contentionCount;
        private final double avgWaitMs;
        private final double maxWaitMs;
        private final double p95WaitMs;
        private final HeatLevel heatLevel;
        private final Instant lastContention;
        private final long totalWaitMs;

        public HeatMapEntry(String lockName, long contentionCount, double avgWaitMs,
                           double maxWaitMs, double p95WaitMs, long totalWaitMs,
                           Instant lastContention) {
            this.lockName = lockName;
            this.contentionCount = contentionCount;
            this.avgWaitMs = avgWaitMs;
            this.maxWaitMs = maxWaitMs;
            this.p95WaitMs = p95WaitMs;
            this.totalWaitMs = totalWaitMs;
            this.heatLevel = HeatLevel.fromWaitTimeMs((long) maxWaitMs);
            this.lastContention = lastContention;
        }

        public String getLockName() { return lockName; }
        public long getContentionCount() { return contentionCount; }
        public double getAvgWaitMs() { return avgWaitMs; }
        public double getMaxWaitMs() { return maxWaitMs; }
        public double getP95WaitMs() { return p95WaitMs; }
        public long getTotalWaitMs() { return totalWaitMs; }
        public HeatLevel getHeatLevel() { return heatLevel; }
        public Instant getLastContention() { return lastContention; }

        /**
         * Gets the color code for visualization (CSS hex color).
         */
        public String getColorCode() {
            return switch (heatLevel) {
                case CRITICAL -> "#FF0000"; // Red
                case HIGH -> "#FF6600";     // Orange
                case MEDIUM -> "#FFCC00";   // Yellow
                case LOW -> "#99CC00";      // Light green
                case NONE -> "#00CC00";     // Green
            };
        }

        @Override
        public String toString() {
            return String.format("HeatMapEntry[%s: level=%s, count=%d, avg=%.2fms, max=%.2fms]",
                lockName, heatLevel.getLabel(), contentionCount, avgWaitMs, maxWaitMs);
        }
    }

    /**
     * Aggregated heat map data for all tracked locks.
     */
    public static final class HeatMapData {
        private final Instant generatedAt;
        private final List<HeatMapEntry> entries;
        private final Map<String, HeatMapEntry> entryMap;
        private final long totalContentionCount;
        private final double overallAvgWaitMs;
        private final int criticalCount;
        private final int highCount;
        private final int mediumCount;
        private final int lowCount;

        public HeatMapData(List<HeatMapEntry> entries) {
            this.generatedAt = Instant.now();
            this.entries = List.copyOf(entries);
            this.entryMap = new ConcurrentHashMap<>();
            for (HeatMapEntry entry : entries) {
                entryMap.put(entry.getLockName(), entry);
            }

            // Calculate aggregates
            long total = 0;
            long totalWait = 0;
            int critical = 0, high = 0, medium = 0, low = 0;

            for (HeatMapEntry entry : entries) {
                total += entry.getContentionCount();
                totalWait += entry.getTotalWaitMs();
                switch (entry.getHeatLevel()) {
                    case CRITICAL -> critical++;
                    case HIGH -> high++;
                    case MEDIUM -> medium++;
                    case LOW -> low++;
                    default -> {}
                }
            }

            this.totalContentionCount = total;
            this.overallAvgWaitMs = total > 0 ? (double) totalWait / total : 0;
            this.criticalCount = critical;
            this.highCount = high;
            this.mediumCount = medium;
            this.lowCount = low;
        }

        public Instant getGeneratedAt() { return generatedAt; }
        public List<HeatMapEntry> getEntries() { return entries; }
        public HeatMapEntry getEntry(String lockName) { return entryMap.get(lockName); }
        public long getTotalContentionCount() { return totalContentionCount; }
        public double getOverallAvgWaitMs() { return overallAvgWaitMs; }
        public int getCriticalCount() { return criticalCount; }
        public int getHighCount() { return highCount; }
        public int getMediumCount() { return mediumCount; }
        public int getLowCount() { return lowCount; }
        public int size() { return entries.size(); }

        /**
         * Gets entries sorted by heat level (critical first).
         */
        public List<HeatMapEntry> getSortedBySeverity() {
            return entries.stream()
                .sorted((a, b) -> Integer.compare(b.getHeatLevel().getSeverity(),
                                                   a.getHeatLevel().getSeverity()))
                .toList();
        }

        /**
         * Gets entries sorted by average wait time (highest first).
         */
        public List<HeatMapEntry> getSortedByAvgWait() {
            return entries.stream()
                .sorted((a, b) -> Double.compare(b.getAvgWaitMs(), a.getAvgWaitMs()))
                .toList();
        }
    }

    // Lock statistics storage
    private final Map<String, LockStats> lockStats = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final SLOTracker sloTracker;
    private final AndonCord andonCord;

    // Metrics
    private final Counter contentionCounter;
    private final Timer contentionTimer;

    /**
     * Internal lock statistics tracking.
     */
    private static final class LockStats {
        final AtomicLong contentionCount = new AtomicLong();
        final AtomicLong totalWaitNanos = new AtomicLong();
        final AtomicLong maxWaitNanos = new AtomicLong();
        final ConcurrentLinkedQueue<Long> recentWaits = new ConcurrentLinkedQueue<>();
        volatile Instant lastContention;

        void recordWait(long waitNanos) {
            contentionCount.incrementAndGet();
            totalWaitNanos.addAndGet(waitNanos);
            maxWaitNanos.accumulateAndGet(waitNanos, Math::max);
            lastContention = Instant.now();

            // Maintain recent history for P95 calculation
            recentWaits.offer(waitNanos);
            while (recentWaits.size() > MAX_HISTORY_PER_LOCK) {
                recentWaits.poll();
            }
        }

        double getP95WaitMs() {
            if (recentWaits.isEmpty()) return 0;

            List<Long> sorted = new ArrayList<>(recentWaits);
            Collections.sort(sorted);
            int p95Index = (int) (sorted.size() * 0.95);
            return sorted.get(p95Index) / 1_000_000.0;
        }
    }

    /**
     * Private constructor - use getInstance().
     */
    private LockContentionTracker() {
        this(Metrics.globalRegistry);
    }

    /**
     * Creates tracker with custom metrics registry.
     */
    public LockContentionTracker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.sloTracker = new SLOTracker(meterRegistry);
        this.andonCord = AndonCord.getInstance();

        // Initialize metrics
        this.contentionCounter = Counter.builder("yawl.lock.contention.count")
            .description("Total lock contention events")
            .register(meterRegistry);

        this.contentionTimer = Timer.builder("yawl.lock.contention.wait")
            .description("Lock acquisition wait time")
            .register(meterRegistry);

        // Register gauges
        Gauge.builder("yawl.lock.contention.active", this, t -> t.lockStats.size())
            .description("Number of actively tracked locks")
            .register(meterRegistry);

        LOGGER.info("LockContentionTracker initialized");
    }

    /**
     * Gets the singleton instance.
     */
    public static LockContentionTracker getInstance() {
        if (instance == null) {
            INSTANCE_LOCK.lock();
            try {
                if (instance == null) {
                    instance = new LockContentionTracker();
                }
            } finally {
                INSTANCE_LOCK.unlock();
            }
        }
        return instance;
    }

    /**
     * Tracks a lock acquisition and returns a context for automatic release tracking.
     *
     * <p>Usage with try-with-resources:</p>
     * <pre>{@code
     * try (LockAcquisitionContext ctx = tracker.trackAcquisition("myLock")) {
     *     // ... perform locked operation ...
     * }
     * }</pre>
     *
     * @param lockName the name of the lock being acquired
     * @return context that tracks the acquisition lifecycle
     */
    public LockAcquisitionContext trackAcquisition(String lockName) {
        Objects.requireNonNull(lockName, "Lock name cannot be null");
        return new LockAcquisitionContext(this, lockName);
    }

    /**
     * Records a lock release with wait time.
     *
     * @param lockName the lock name
     * @param waitNanos the wait time in nanoseconds
     */
    private void recordRelease(String lockName, long waitNanos) {
        LockStats stats = lockStats.computeIfAbsent(lockName, k -> new LockStats());
        stats.recordWait(waitNanos);

        long waitMs = TimeUnit.NANOSECONDS.toMillis(waitNanos);

        // Record metrics
        contentionCounter.increment();
        contentionTimer.record(waitNanos, TimeUnit.NANOSECONDS);

        // Check for SLO violation
        boolean met = waitMs < CRITICAL_THRESHOLD_MS;
        sloTracker.recordMetric(SLOTracker.SLOType.LOCK_CONTENTION, met, waitMs);

        // Fire AndonCord alert for critical contention
        if (waitMs >= CRITICAL_THRESHOLD_MS) {
            andonCord.lockContentionHigh("system", lockName, waitMs);
            LOGGER.warn("Critical lock contention detected: {} waited {}ms", lockName, waitMs);
        }

        LOGGER.debug("Lock released: {} waited {}ms", lockName, waitMs);
    }

    /**
     * Manually records a contention event (for cases where try-with-resources is not applicable).
     *
     * @param lockName the lock name
     * @param waitMs the wait time in milliseconds
     */
    public void recordContention(String lockName, long waitMs) {
        recordRelease(lockName, TimeUnit.MILLISECONDS.toNanos(waitMs));
    }

    /**
     * Gets the current heat map data for all tracked locks.
     *
     * @return aggregated heat map data
     */
    public HeatMapData getHeatMapData() {
        List<HeatMapEntry> entries = new ArrayList<>();

        for (Map.Entry<String, LockStats> entry : lockStats.entrySet()) {
            String name = entry.getKey();
            LockStats stats = entry.getValue();

            long count = stats.contentionCount.get();
            if (count == 0) continue;

            long totalMs = TimeUnit.NANOSECONDS.toMillis(stats.totalWaitNanos.get());
            double avgMs = (double) totalMs / count;
            double maxMs = TimeUnit.NANOSECONDS.toMillis(stats.maxWaitNanos.get());
            double p95Ms = stats.getP95WaitMs();

            entries.add(new HeatMapEntry(name, count, avgMs, maxMs, p95Ms, totalMs, stats.lastContention));
        }

        return new HeatMapData(entries);
    }

    /**
     * Gets heat map entries for a specific lock name pattern.
     *
     * @param pattern the lock name pattern (supports wildcards: * and ?)
     * @return filtered heat map data
     */
    public HeatMapData getHeatMapData(String pattern) {
        Objects.requireNonNull(pattern, "Pattern cannot be null");

        String regex = pattern.replace(".", "\\.")
                              .replace("*", ".*")
                              .replace("?", ".");

        List<HeatMapEntry> filtered = getHeatMapData().getEntries().stream()
            .filter(e -> e.getLockName().matches(regex))
            .toList();

        return new HeatMapData(filtered);
    }

    /**
     * Gets contention statistics for a specific lock.
     *
     * @param lockName the lock name
     * @return optional containing the heat map entry, or empty if not found
     */
    public Optional<HeatMapEntry> getLockStats(String lockName) {
        return Optional.ofNullable(getHeatMapData().getEntry(lockName));
    }

    /**
     * Clears all tracked statistics.
     */
    public void clear() {
        lockStats.clear();
        LOGGER.info("LockContentionTracker statistics cleared");
    }

    /**
     * Gets the number of actively tracked locks.
     */
    public int getTrackedLockCount() {
        return lockStats.size();
    }

    /**
     * Checks if any locks have critical heat level.
     */
    public boolean hasCriticalContention() {
        return getHeatMapData().getCriticalCount() > 0;
    }

    /**
     * Gets summary statistics as a formatted string.
     */
    public String getSummary() {
        HeatMapData data = getHeatMapData();
        return String.format(
            "Lock Contention Summary: %d locks tracked, %d total contentions, %.2fms avg wait, " +
            "%d critical, %d high, %d medium, %d low",
            data.size(), data.getTotalContentionCount(), data.getOverallAvgWaitMs(),
            data.getCriticalCount(), data.getHighCount(), data.getMediumCount(), data.getLowCount()
        );
    }
}
