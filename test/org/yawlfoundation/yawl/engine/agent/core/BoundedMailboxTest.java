package org.yawlfoundation.yawl.engine.agent.core;

import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for bounded mailbox capability in VirtualThreadRuntime.
 *
 * Detroit School: Tests verify observable behavior through public API.
 * Tests verify:
 * - Invalid capacity is rejected (IllegalArgumentException)
 * - Messages are received up to capacity without blocking
 * - Producer blocks when mailbox is full (backpressure via tellBlocking)
 * - Bounded actors appear in runtime stats (activeActors, totalSpawned)
 * - Interface default implementation throws UnsupportedOperationException
 * - Multiple bounded actors with different capacities work independently
 *
 * These tests use real VirtualThreadRuntime, real mailboxes, and measure
 * actual timing to verify backpressure behavior.
 */
class BoundedMailboxTest {

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
    @DisplayName("spawnBounded rejects capacity <= 0")
    @Timeout(5)
    void spawnBounded_rejectsInvalidCapacity() {
        assertThrows(IllegalArgumentException.class,
            () -> runtime.spawnBounded(self -> {}, 0),
            "spawnBounded should reject capacity=0");

        assertThrows(IllegalArgumentException.class,
            () -> runtime.spawnBounded(self -> {}, -5),
            "spawnBounded should reject capacity=-5");
    }

    @Test
    @DisplayName("bounded actor receives messages up to capacity without blocking")
    @Timeout(5)
    void boundedActor_receivesMessages_withinCapacity() throws InterruptedException {
        int capacity = 10;
        CountDownLatch processed = new CountDownLatch(capacity);

        ActorRef actor = runtime.spawnBounded(self -> {
            while (true) {
                try {
                    self.recv();
                    processed.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, capacity);

        // Should be able to send 'capacity' messages without blocking
        for (int i = 0; i < capacity; i++) {
            actor.tell("msg-" + i);
        }

        assertTrue(processed.await(5, TimeUnit.SECONDS),
            "All messages should be processed within 5 seconds");
    }

    @Test
    @DisplayName("bounded actor: producer blocks when mailbox full, unblocks on consume")
    @Timeout(15)
    void boundedActor_producerBlocks_whenFull_unblocksOnConsume() throws InterruptedException {
        int capacity = 3;
        CountDownLatch consumerReady = new CountDownLatch(1);
        CountDownLatch releaseConsumer = new CountDownLatch(1);

        ActorRef actor = runtime.spawnBounded(self -> {
            consumerReady.countDown();
            while (true) {
                try {
                    // Wait for signal before consuming
                    releaseConsumer.await();
                    self.recv();
                    return; // exit after consuming one
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, capacity);

        consumerReady.await(3, TimeUnit.SECONDS);

        // Fill mailbox to capacity (non-blocking)
        for (int i = 0; i < capacity; i++) {
            actor.tell("fill-" + i);
        }

        // Now try to send a blocking message in a separate thread
        CountDownLatch producerBlocked = new CountDownLatch(1);
        CountDownLatch producerDone = new CountDownLatch(1);
        Thread producer = Thread.ofVirtual().start(() -> {
            producerBlocked.countDown(); // signal about to block
            try {
                runtime.tellBlocking(actor, "overflow"); // should block (mailbox full)
                producerDone.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Producer should be blocked — verify it doesn't complete immediately
        producerBlocked.await(1, TimeUnit.SECONDS);
        assertFalse(producerDone.await(200, TimeUnit.MILLISECONDS),
            "Producer should be blocked on full mailbox");

        // Release consumer → frees one slot → producer unblocks
        releaseConsumer.countDown();
        assertTrue(producerDone.await(5, TimeUnit.SECONDS),
            "Producer should unblock after consumer drains a slot");

        producer.interrupt();
    }

    @Test
    @DisplayName("bounded actor appears in stats().activeActors")
    @Timeout(5)
    void boundedActor_appearsInRuntimeStats() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch exit = new CountDownLatch(1);

        runtime.spawnBounded(self -> {
            ready.countDown();
            try {
                exit.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, 5);

        ready.await(3, TimeUnit.SECONDS);
        RuntimeStats stats = runtime.stats();
        assertTrue(stats.activeActors() >= 1,
            "Bounded actor should appear in active count");
        assertTrue(stats.totalSpawned() >= 1,
            "Bounded actor should appear in spawn count");

        exit.countDown();
    }

    @Test
    @DisplayName("default spawnBounded on ActorRuntime interface throws UnsupportedOperationException")
    @Timeout(5)
    void actorRuntimeInterface_defaultSpawnBounded_throwsUnsupported() {
        // Anonymous impl that only implements the required methods
        ActorRuntime minimal = new ActorRuntime() {
            @Override
            public ActorRef spawn(ActorBehavior b) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void send(int id, Object msg) {
            }

            @Override
            public void stop(int id) {
            }

            @Override
            public boolean isAlive(int id) {
                return false;
            }

            @Override
            public Object recv(int id) {
                return null;
            }

            @Override
            public void injectException(int id, RuntimeException e) {
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public void close() {
            }
        };

        assertThrows(UnsupportedOperationException.class,
            () -> minimal.spawnBounded(self -> {}, 10),
            "Default spawnBounded should throw UnsupportedOperationException");
    }

    @Test
    @DisplayName("multiple bounded actors with different capacities work independently")
    @Timeout(10)
    void multipleBoundedActors_independentCapacities() throws InterruptedException {
        int cap1 = 5;
        int cap2 = 15;
        CountDownLatch done1 = new CountDownLatch(cap1);
        CountDownLatch done2 = new CountDownLatch(cap2);

        ActorRef actor1 = runtime.spawnBounded(self -> {
            while (true) {
                try {
                    self.recv();
                    done1.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, cap1);

        ActorRef actor2 = runtime.spawnBounded(self -> {
            while (true) {
                try {
                    self.recv();
                    done2.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, cap2);

        // Send to actor1: cap1 messages
        for (int i = 0; i < cap1; i++) {
            actor1.tell("msg1-" + i);
        }

        // Send to actor2: cap2 messages
        for (int i = 0; i < cap2; i++) {
            actor2.tell("msg2-" + i);
        }

        assertTrue(done1.await(5, TimeUnit.SECONDS), "Actor1 should process all messages");
        assertTrue(done2.await(5, TimeUnit.SECONDS), "Actor2 should process all messages");
    }
}
