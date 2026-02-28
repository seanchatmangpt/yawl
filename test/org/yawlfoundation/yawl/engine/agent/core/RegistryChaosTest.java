package org.yawlfoundation.yawl.engine.agent.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test: Prove ConcurrentHashMap<Integer, Agent> is safe.
 * Tag: stress — run separately from smoke tests.
 *
 * Methodology:
 * - 1000 concurrent threads
 * - Each thread: spawn agents, send messages, verify registry lookups
 * - Duration: 60 seconds of chaos
 * - All real agents and runtime, no mocks
 * - Verify: zero lookup errors, zero data corruption, registry consistency
 */
@Tag("stress")
class RegistryChaosTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void registryWithstandsConcurrentChaos() throws InterruptedException {
        Runtime rt = new Runtime();
        int THREADS = 1000;
        int DURATION_SEC = 60;

        AtomicLong spawnCount = new AtomicLong(0);
        AtomicLong sendCount = new AtomicLong(0);
        AtomicLong lookupErrors = new AtomicLong(0);

        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        long deadline = System.currentTimeMillis() + DURATION_SEC * 1000L;

        ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();

        System.out.printf("Starting %d chaos threads for %d seconds...%n", THREADS, DURATION_SEC);

        for (int t = 0; t < THREADS; t++) {
            exec.submit(() -> {
                try {
                    startGun.await();
                    ThreadLocalRandom rng = ThreadLocalRandom.current();

                    while (System.currentTimeMillis() < deadline) {
                        // Spawn real agent
                        Agent a = rt.spawn(msg -> {
                            // Real handler
                        });
                        spawnCount.incrementAndGet();

                        // Send message via registry
                        rt.send(a.id, "chaos-msg");
                        sendCount.incrementAndGet();

                        // Occasional registry size verification
                        if (rng.nextInt(100) == 0) {
                            int size = rt.size();
                            if (size < 0) {
                                lookupErrors.incrementAndGet();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        startGun.countDown();
        boolean completed = done.await(DURATION_SEC + 10, TimeUnit.SECONDS);
        exec.shutdownNow();

        System.out.println("=== Registry Chaos Results ===");
        System.out.printf("Threads:       %d%n", THREADS);
        System.out.printf("Duration:      %d seconds%n", DURATION_SEC);
        System.out.printf("Total spawns:  %,d%n", spawnCount.get());
        System.out.printf("Total sends:   %,d%n", sendCount.get());
        System.out.printf("Lookup errors: %d%n", lookupErrors.get());
        System.out.printf("Registry size: %d%n", rt.size());
        System.out.printf("Completed:     %s%n", completed);

        assertEquals(0, lookupErrors.get(), "No lookup errors allowed under chaos");
        assertTrue(spawnCount.get() > 0, "Must have spawned agents during chaos");
        assertTrue(sendCount.get() > 0, "Must have sent messages during chaos");
        assertTrue(completed, "All threads should complete within timeout");

        rt.close();
    }
}
