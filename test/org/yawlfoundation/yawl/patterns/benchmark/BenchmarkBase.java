package org.yawlfoundation.yawl.patterns.benchmark;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Base class for pattern benchmarks using JMH.
 * Provides utilities for memory tracking, GC analysis, and metrics collection.
 */
public class BenchmarkBase {
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    protected List<MemorySnapshot> snapshots = new ArrayList<>();

    /**
     * Record current heap state for analysis
     */
    protected void recordMemory(String label) {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        snapshots.add(new MemorySnapshot(
            label,
            Instant.now(),
            heapUsage.getUsed(),
            heapUsage.getMax(),
            heapUsage.getCommitted()
        ));
    }

    /**
     * Force full GC and wait for completion
     */
    protected void fullGC() {
        System.gc();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Calculate heap growth between snapshots
     */
    protected long getHeapGrowth(String fromLabel, String toLabel) {
        MemorySnapshot from = snapshots.stream()
            .filter(s -> s.label.equals(fromLabel))
            .findFirst()
            .orElse(null);
        MemorySnapshot to = snapshots.stream()
            .filter(s -> s.label.equals(toLabel))
            .findFirst()
            .orElse(null);

        if (from == null || to == null) {
            return 0;
        }
        return to.used - from.used;
    }

    /**
     * Get current heap usage in MB
     */
    protected long getHeapUsedMB() {
        return memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
    }

    /**
     * Get max heap available in MB
     */
    protected long getHeapMaxMB() {
        return memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
    }

    /**
     * Check if heap is growing unbounded (regression test)
     * Detects if heap growth continues despite GC attempts
     *
     * @param thresholdMB Maximum acceptable growth in MB
     * @return true if growth exceeds threshold
     */
    protected boolean isHeapGrowingUnbounded(long thresholdMB) {
        if (snapshots.isEmpty()) return false;

        MemorySnapshot latest = snapshots.get(snapshots.size() - 1);
        if (snapshots.size() < 2) return false;

        MemorySnapshot previous = snapshots.get(snapshots.size() - 2);
        long growthMB = (latest.used - previous.used) / (1024 * 1024);

        return growthMB > thresholdMB;
    }

    /**
     * Snapshot of memory state at point in time
     */
    public static class MemorySnapshot {
        public final String label;
        public final Instant time;
        public final long used;
        public final long max;
        public final long committed;

        public MemorySnapshot(String label, Instant time, long used, long max, long committed) {
            this.label = label;
            this.time = time;
            this.used = used;
            this.max = max;
            this.committed = committed;
        }

        public long usedMB() {
            return used / (1024 * 1024);
        }

        @Override
        public String toString() {
            return String.format("%s: %d MB / %d MB (committed: %d MB)",
                label, usedMB(), max / (1024 * 1024), committed / (1024 * 1024));
        }
    }
}
