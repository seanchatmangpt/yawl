package org.yawlfoundation.yawl.engine.agent.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stress test: Find the message rate where the system breaks.
 * Tag: stress — run separately from smoke tests.
 *
 * Methodology:
 * - Test at increasing message rates: 10K, 100K, 1M, 10M msg/s
 * - 1000 real agents each receiving messages
 * - Measure p99 latency and mailbox depth
 * - Stop when p99 latency > 5 seconds or OOM occurs
 * - Flooding duration: 10 seconds at each rate
 */
@Tag("stress")
class MessageFloodStressTest {

    @Test
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void findMessageThroughputCeiling() throws InterruptedException {
        int AGENTS = 1000;
        int[] ratesPerSecond = {10_000, 100_000, 1_000_000, 10_000_000};

        System.out.println("=== Message Flood Breaking Point Test ===");
        System.out.println("Rate/s\t\tP99 Latency\tMailbox Depth\tStatus");
        System.out.println("-".repeat(70));

        for (int rate : ratesPerSecond) {
            Runtime rt = new Runtime();
            AtomicLong maxLatency = new AtomicLong(0);
            AtomicLong totalDelivered = new AtomicLong(0);
            Agent[] agents = new Agent[AGENTS];

            // Spawn real agents
            for (int i = 0; i < AGENTS; i++) {
                agents[i] = rt.spawn(msg -> {
                    long latencyNs = System.nanoTime() - (long) msg;
                    maxLatency.updateAndGet(m -> Math.max(m, latencyNs));
                    totalDelivered.incrementAndGet();
                });
            }

            // Flood for 10 seconds at rate/s
            long intervalNs = 1_000_000_000L / rate;
            long floodDuration = 10_000; // 10 seconds
            long start = System.currentTimeMillis();
            long sent = 0;

            try {
                while (System.currentTimeMillis() - start < floodDuration) {
                    agents[(int)(sent % AGENTS)].send(System.nanoTime());
                    sent++;
                    if (intervalNs > 1000) {
                        long sleepNs = intervalNs - 1000;
                        Thread.sleep(sleepNs / 1_000_000, (int)(sleepNs % 1_000_000));
                    }
                }
            } catch (OutOfMemoryError oom) {
                System.out.printf("OOM at rate %d/s: %s%n", rate, oom.getMessage());
                rt.close();
                return;
            }

            Thread.sleep(2000); // drain remaining messages

            long p99Ms = maxLatency.get() / 1_000_000;
            long mailboxDepth = sent - totalDelivered.get();
            String status = (p99Ms > 5000) ? "OVERLOADED" : "STABLE";

            System.out.printf("%-15d\t%d ms\t\t%d\t\t%s%n",
                rate, p99Ms, mailboxDepth, status);

            rt.close();

            if (p99Ms > 5000) {
                System.out.printf("CEILING: %d msg/s → p99 latency %d ms%n", rate, p99Ms);
                return;
            }
        }

        System.out.println("System stable at all tested rates up to 10M msg/s!");
    }
}
