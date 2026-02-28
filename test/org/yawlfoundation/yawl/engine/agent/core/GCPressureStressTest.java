package org.yawlfoundation.yawl.engine.agent.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Stress test: Find the agent count where GC pauses exceed 10ms.
 * Tag: stress — run separately from smoke tests.
 *
 * Methodology:
 * - Spawn agents at increasing densities: 100K, 500K, 1M, 5M, 10M
 * - Monitor GC collection count and total collection time
 * - Calculate average pause time per collection
 * - Stop when average pause > 10ms
 * - Each density sustained for 2 minutes to stabilize GC
 */
@Tag("stress")
class GCPressureStressTest {

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void findGCBreakingPoint() throws InterruptedException {
        int[] agentCounts = {100_000, 500_000, 1_000_000, 5_000_000, 10_000_000};
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        System.out.println("=== GC Pressure Breaking Point Test ===");
        System.out.printf("%-15s\t%-15s\t%-15s\t%s%n",
            "Agents", "GC Count", "GC Time(ms)", "Status");
        System.out.println("-".repeat(70));

        for (int count : agentCounts) {
            Runtime rt = new Runtime();

            // Baseline GC stats
            long gcCountBefore = gcBeans.stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .sum();
            long gcTimeBefore = gcBeans.stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .sum();

            // Spawn agents at target density
            for (int i = 0; i < count; i++) {
                rt.spawn(msg -> {
                    // Empty handler — just measure GC overhead
                });
            }

            // Sustain for 2 minutes to stabilize GC
            System.out.printf("Sustaining %,d agents for 2 minutes...%n", count);
            Thread.sleep(120_000);

            long gcCountAfter = gcBeans.stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .sum();
            long gcTimeAfter = gcBeans.stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .sum();

            long gcCount = gcCountAfter - gcCountBefore;
            long gcTotalMs = gcTimeAfter - gcTimeBefore;
            long gcAvgMs = gcCount > 0 ? gcTotalMs / gcCount : 0;
            String status = gcAvgMs > 10 ? "DEGRADED (>10ms avg pause)" : "STABLE";

            System.out.printf("%-15d\t%-15d\t%-15d\t%s%n",
                count, gcCount, gcAvgMs, status);

            rt.close();
            System.gc();
            Thread.sleep(2000);

            if (gcAvgMs > 10) {
                System.out.printf("GC BREAKING POINT: %d agents → avg pause %d ms%n",
                    count, gcAvgMs);
                return;
            }
        }

        System.out.println("GC stable at all tested agent counts!");
    }
}
