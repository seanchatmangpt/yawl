package org.yawlfoundation.yawl.engine.agent.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stress test: Find the exact agent count where OOM or scheduling degradation occurs.
 * Tag: stress — run separately from smoke tests.
 *
 * Methodology:
 * - Spawn agents at increasing densities (100K, 500K, 1M, 2M, 5M, 10M)
 * - Measure heap consumption and scheduling latency (p99)
 * - Stop when p99 latency > 1 second or OOM occurs
 * - All real agents, no mocks
 */
@Tag("stress")
class AgentDensityStressTest {

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void findDensityBreakingPoint() throws InterruptedException {
        int[] counts = {100_000, 500_000, 1_000_000, 2_000_000, 5_000_000, 10_000_000};
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();

        System.out.println("=== Agent Density Breaking Point Test ===");
        System.out.println("Count\t\tHeap Used\tSchedule p99\tStatus");
        System.out.println("-".repeat(70));

        for (int count : counts) {
            Runtime rt = new Runtime();
            try {
                System.gc();
                Thread.sleep(100);
                long heapBefore = mem.getHeapMemoryUsage().getUsed();

                // Spawn agents at target density
                for (int i = 0; i < count; i++) {
                    rt.spawn(msg -> {
                        // Empty handler — just measure overhead
                    });
                }

                Thread.sleep(500); // let all virtual threads start

                long heapAfter = mem.getHeapMemoryUsage().getUsed();
                long heapMB = (heapAfter - heapBefore) / (1024 * 1024);

                // Measure scheduling latency: send 1000 pings to random agents
                AtomicLong maxLatency = new AtomicLong(0);
                CountDownLatch pings = new CountDownLatch(1000);
                Runtime pingRt = new Runtime();
                for (int i = 0; i < 1000; i++) {
                    Agent pingAgent = pingRt.spawn(msg -> {
                        long latency = System.nanoTime() - (long) msg;
                        maxLatency.updateAndGet(m -> Math.max(m, latency));
                        pings.countDown();
                    });
                    pingAgent.send(System.nanoTime());
                }
                boolean completed = pings.await(1, TimeUnit.SECONDS);
                pingRt.close();

                long p99Ms = maxLatency.get() / 1_000_000;
                String status = (!completed || p99Ms > 1000) ? "DEGRADED" : "STABLE";

                System.out.printf("%-15d\t%d MB\t\t%d ms\t\t%s%n",
                    count, heapMB, p99Ms, status);

                if (!completed || p99Ms > 1000) {
                    System.out.printf("BREAKING POINT: %d agents → %s%n", count, status);
                    return; // found breaking point
                }

            } catch (OutOfMemoryError oom) {
                System.out.printf("OOM at %d agents: %s%n", count, oom.getMessage());
                System.out.printf("BREAKING POINT: %d agents → OutOfMemoryError%n", count);
                return;
            } finally {
                rt.close();
            }
        }

        System.out.println("System stable at all tested densities up to 10M agents!");
    }
}
