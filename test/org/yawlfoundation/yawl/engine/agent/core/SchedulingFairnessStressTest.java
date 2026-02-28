package org.yawlfoundation.yawl.engine.agent.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test: Prove no starvation at 1M agent scale.
 * Tag: stress — run separately from smoke tests.
 *
 * Methodology:
 * - Spawn 1 million real agents
 * - Send one message to each agent
 * - Wait 60 seconds for all agents to execute
 * - Analyze distribution: ensure min runs >= 1, stddev < 50% of mean
 * - Verify no agent is starved (has 0 runs)
 */
@Tag("stress")
class SchedulingFairnessStressTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void noStarvationAtOneMillion() throws InterruptedException {
        int N = 1_000_000;
        AtomicIntegerArray runCounts = new AtomicIntegerArray(N);
        Agent[] agents = new Agent[N];
        Runtime rt = new Runtime();

        System.out.printf("Spawning %,d agents...%n", N);
        for (int i = 0; i < N; i++) {
            final int idx = i;
            agents[i] = rt.spawn(msg -> runCounts.incrementAndGet(idx));
        }

        // Send one message to each agent to trigger a run
        System.out.println("Sending trigger messages...");
        for (int i = 0; i < N; i++) {
            agents[i].send("run");
        }

        System.out.println("Waiting 60 seconds for all agents to execute...");
        Thread.sleep(60_000);

        // Analyze distribution
        long minRuns = Long.MAX_VALUE, maxRuns = 0, total = 0, starvation = 0;
        for (int i = 0; i < N; i++) {
            int r = runCounts.get(i);
            if (r < minRuns) minRuns = r;
            if (r > maxRuns) maxRuns = r;
            total += r;
            if (r == 0) starvation++;
        }
        double mean = (double) total / N;
        double variance = 0;
        for (int i = 0; i < N; i++) {
            double diff = runCounts.get(i) - mean;
            variance += diff * diff;
        }
        double stddev = Math.sqrt(variance / N);

        System.out.println("=== Scheduling Fairness Results ===");
        System.out.printf("Min runs:   %d%n", minRuns);
        System.out.printf("Max runs:   %d%n", maxRuns);
        System.out.printf("Mean runs:  %.2f%n", mean);
        System.out.printf("StdDev:     %.2f (%.1f%% of mean)%n", stddev, (stddev/mean)*100);
        System.out.printf("Starvation: %d / %,d agents%n", starvation, N);

        assertEquals(0, starvation, "No agent should be starved (0 runs)");
        assertTrue(stddev / mean < 0.5, "StdDev should be < 50% of mean");

        rt.close();
    }
}
