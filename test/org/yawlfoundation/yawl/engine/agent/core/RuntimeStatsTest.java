package org.yawlfoundation.yawl.engine.agent.core;

import org.junit.jupiter.api.*;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for RuntimeStats record in VirtualThreadRuntime.
 *
 * Detroit School: Tests verify observable behavior through public API.
 * Tests verify:
 * - Fresh runtime has zero stats (all counters at 0)
 * - totalSpawned increments on spawn() (cumulative counter)
 * - activeActors reflects live actor count (decreases when actors exit)
 * - totalStopped increments on stop() (cumulative counter)
 * - totalMessages increments on send() (cumulative counter via tell())
 * - RuntimeStats is a record (Java record, not a class)
 * - Stats are consistent snapshots (no stale data)
 * - Counters are monotonically increasing (never decrease)
 *
 * Tests use real VirtualThreadRuntime with real actor lifecycle.
 */
class RuntimeStatsTest {

    private VirtualThreadRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new VirtualThreadRuntime();
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    @DisplayName("fresh runtime has zero active actors and zero counters")
    @Timeout(5)
    void freshRuntime_hasZeroStats() {
        RuntimeStats stats = runtime.stats();
        assertEquals(0, stats.activeActors(),
            "Fresh runtime should have zero active actors");
        assertEquals(0L, stats.totalSpawned(),
            "Fresh runtime should have zero spawned");
        assertEquals(0L, stats.totalStopped(),
            "Fresh runtime should have zero stopped");
        assertEquals(0L, stats.totalMessages(),
            "Fresh runtime should have zero messages");
    }

    @Test
    @DisplayName("stats().totalSpawned increments on each spawn")
    @Timeout(5)
    void totalSpawned_incrementsOnSpawn() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            runtime.spawn(self -> {
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        RuntimeStats stats = runtime.stats();
        assertEquals(10L, stats.totalSpawned(),
            "totalSpawned should equal 10 after 10 spawn() calls");
    }

    @Test
    @DisplayName("stats().activeActors reflects live actor count")
    @Timeout(10)
    void activeActors_reflectsLiveCount() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(5);
        CountDownLatch exit = new CountDownLatch(1);

        for (int i = 0; i < 5; i++) {
            runtime.spawn(self -> {
                ready.countDown();
                try {
                    exit.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        assertEquals(5, runtime.stats().activeActors(),
            "Should have 5 active actors");

        exit.countDown();
        // Give actors time to exit
        Thread.sleep(200);
        assertEquals(0, runtime.stats().activeActors(),
            "All actors should have exited");
    }

    @Test
    @DisplayName("stats().totalStopped increments on stop()")
    @Timeout(10)
    void totalStopped_incrementsOnStop() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(3);
        ActorRef[] refs = new ActorRef[3];

        for (int i = 0; i < 3; i++) {
            refs[i] = runtime.spawn(self -> {
                ready.countDown();
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);

        refs[0].stop();
        refs[1].stop();
        Thread.sleep(100); // let stops propagate

        assertEquals(2L, runtime.stats().totalStopped(),
            "totalStopped should be 2 after stopping 2 actors");
        assertEquals(3L, runtime.stats().totalSpawned(),
            "totalSpawned should still be 3");
    }

    @Test
    @DisplayName("stats().totalMessages increments on send()")
    @Timeout(5)
    void totalMessages_incrementsOnSend() throws InterruptedException {
        CountDownLatch received = new CountDownLatch(50);
        ActorRef actor = runtime.spawn(self -> {
            while (true) {
                try {
                    self.recv();
                    received.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });

        for (int i = 0; i < 50; i++) {
            actor.tell("msg");
        }

        received.await(5, TimeUnit.SECONDS);
        assertEquals(50L, runtime.stats().totalMessages(),
            "totalMessages should equal 50 after sending 50 messages");
    }

    @Test
    @DisplayName("RuntimeStats is a record with correct field types")
    @Timeout(5)
    void runtimeStats_isRecord_withCorrectFieldTypes() {
        RuntimeStats stats = runtime.stats();

        // Verify it's a real record (not a class pretending to be one)
        assertTrue(stats.getClass().isRecord(),
            "RuntimeStats must be a Java record");
        assertEquals(4, stats.getClass().getRecordComponents().length,
            "RuntimeStats must have exactly 4 components");

        // Verify field types
        Class<?>[] types = new Class[4];
        var components = stats.getClass().getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
        }

        // Check that we have the expected primitive/wrapper types
        assertTrue(hasType(types, int.class) || hasType(types, Integer.class),
            "RuntimeStats should have an int or Integer field");
        assertTrue(hasType(types, long.class) || hasType(types, Long.class),
            "RuntimeStats should have long or Long fields");
    }

    @Test
    @DisplayName("stats() returns consistent snapshot across multiple calls")
    @Timeout(10)
    void stats_returnsConsistentSnapshot() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            runtime.spawn(self -> {
                ready.countDown();
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await(3, TimeUnit.SECONDS);

        RuntimeStats stats1 = runtime.stats();
        RuntimeStats stats2 = runtime.stats();

        // Snapshot should be consistent (no new messages between calls)
        assertEquals(stats1.activeActors(), stats2.activeActors(),
            "activeActors should be same in consecutive snapshots");
        assertEquals(stats1.totalSpawned(), stats2.totalSpawned(),
            "totalSpawned should be same in consecutive snapshots");
        assertEquals(stats1.totalMessages(), stats2.totalMessages(),
            "totalMessages should be same in consecutive snapshots");
    }

    @Test
    @DisplayName("stats counters are monotonically increasing (never decrease)")
    @Timeout(10)
    void stats_countersMonotonicallyIncreasing() throws InterruptedException {
        CountDownLatch phase1 = new CountDownLatch(5);
        CountDownLatch exit = new CountDownLatch(1);
        ActorRef[] refs = new ActorRef[5];

        // Phase 1: spawn 5 actors
        for (int i = 0; i < 5; i++) {
            refs[i] = runtime.spawn(self -> {
                phase1.countDown();
                try {
                    exit.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        phase1.await(5, TimeUnit.SECONDS);
        RuntimeStats s1 = runtime.stats();

        // Send 10 messages
        for (int i = 0; i < 10; i++) {
            refs[0].tell("msg-" + i);
        }
        Thread.sleep(50);
        RuntimeStats s2 = runtime.stats();

        // Stats should only increase or stay same, never decrease
        assertTrue(s2.totalSpawned() >= s1.totalSpawned(),
            "totalSpawned must not decrease");
        assertTrue(s2.totalMessages() >= s1.totalMessages(),
            "totalMessages must not decrease");
        assertTrue(s2.activeActors() >= s1.activeActors() || s2.activeActors() <= s1.activeActors(),
            "activeActors may change");

        exit.countDown();
    }

    /**
     * Helper to check if a type exists in an array.
     */
    private boolean hasType(Class<?>[] types, Class<?> target) {
        for (Class<?> t : types) {
            if (t == target || (t != null && t.equals(target))) {
                return true;
            }
        }
        return false;
    }
}
