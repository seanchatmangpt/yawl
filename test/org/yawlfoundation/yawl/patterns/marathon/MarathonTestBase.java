package org.yawlfoundation.yawl.patterns.marathon;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for marathon (sustained load) tests.
 * Monitors heap growth, GC pauses, and system stability over extended periods.
 * Designed to run for 1-24 hours continuously.
 */
public class MarathonTestBase {
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    protected List<MarathonMetrics> metrics = new ArrayList<>();
    protected AtomicBoolean abortTest = new AtomicBoolean(false);

    /**
     * Record metrics snapshot during test execution
     */
    protected void recordMetrics(String phase, long value) {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        metrics.add(new MarathonMetrics(
            phase,
            Instant.now(),
            heapUsage.getUsed(),
            heapUsage.getMax(),
            value
        ));
    }

    /**
     * Check if heap is growing beyond threshold
     */
    protected boolean isHeapGrowingUnbounded(int threshold) {
        if (metrics.size() < 2) return false;

        MarathonMetrics recent = metrics.get(metrics.size() - 1);
        MarathonMetrics previous = metrics.get(0);

        long growthMB = (recent.heapUsed - previous.heapUsed) / (1024 * 1024);
        return growthMB > threshold;
    }

    /**
     * Get heap growth rate in MB/minute
     */
    protected double getHeapGrowthRatePerMinute() {
        if (metrics.size() < 2) return 0;

        MarathonMetrics first = metrics.get(0);
        MarathonMetrics last = metrics.get(metrics.size() - 1);

        long timeDeltaMinutes = Duration.between(first.timestamp, last.timestamp).toMinutes();
        if (timeDeltaMinutes == 0) return 0;

        long heapDeltaMB = (last.heapUsed - first.heapUsed) / (1024 * 1024);
        return (double) heapDeltaMB / timeDeltaMinutes;
    }

    /**
     * Abort test if critical condition detected
     */
    protected void abortIfCritical(String reason) {
        abortTest.set(true);
        throw new AssertionError("Marathon test aborted: " + reason);
    }

    /**
     * Check if test should continue
     */
    protected boolean shouldContinue() {
        return !abortTest.get();
    }

    /**
     * Force full GC and record pause
     */
    protected void gcAndRecord(String label) {
        long beforeTime = System.nanoTime();
        System.gc();
        long afterTime = System.nanoTime();
        long pauseMs = (afterTime - beforeTime) / 1_000_000;

        if (pauseMs > 100) {
            System.err.println("Warning: GC pause exceeded 100ms: " + pauseMs + "ms");
        }

        recordMetrics(label, pauseMs);
    }

    /**
     * Metrics snapshot from marathon test execution
     */
    public static class MarathonMetrics {
        public final String phase;
        public final Instant timestamp;
        public final long heapUsed;
        public final long heapMax;
        public final long customValue;  // Test-specific metric

        public MarathonMetrics(String phase, Instant timestamp, long heapUsed, long heapMax, long customValue) {
            this.phase = phase;
            this.timestamp = timestamp;
            this.heapUsed = heapUsed;
            this.heapMax = heapMax;
            this.customValue = customValue;
        }

        public long heapUsedMB() {
            return heapUsed / (1024 * 1024);
        }

        public long heapMaxMB() {
            return heapMax / (1024 * 1024);
        }

        @Override
        public String toString() {
            return String.format("%s: heap=%d/%d MB (custom=%d)",
                phase, heapUsedMB(), heapMaxMB(), customValue);
        }
    }
}
