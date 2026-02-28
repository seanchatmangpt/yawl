package org.yawlfoundation.yawl.engine.agent.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AgentTest {

    private Runtime runtime;

    @BeforeEach
    void setUp() {
        runtime = new Runtime();
    }

    @AfterEach
    void tearDown() throws Exception {
        runtime.close();
    }

    @Test
    @Timeout(5)
    void testSpawnOne() {
        runtime.spawn(msg -> {});
        // Wait briefly for registration
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        assertEquals(1, runtime.size());
    }

    @Test
    @Timeout(5)
    void testReceivesMessage() throws InterruptedException {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<Object> got = new AtomicReference<>();

        Agent agent = runtime.spawn(msg -> {
            got.set(msg);
            received.countDown();
        });

        agent.send("ping");
        assertTrue(received.await(100, TimeUnit.MILLISECONDS),
            "Agent must receive message within 100ms");
        assertEquals("ping", got.get());
    }

    @Test
    @Timeout(5)
    void testIdentityViaScopedValue() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger capturedId = new AtomicInteger(-1);

        Agent agent = runtime.spawn(msg -> {
            Agent current = Runtime.CURRENT.get();
            capturedId.set(current.id);
            latch.countDown();
        });

        agent.send("check");
        assertTrue(latch.await(200, TimeUnit.MILLISECONDS),
            "ScopedValue identity must resolve within 200ms");
        assertEquals(agent.id, capturedId.get(),
            "CURRENT.get().id must match spawned agent id");
    }

    @Test
    @Timeout(30)
    void testTenThousandAgents() throws InterruptedException {
        int N = 10_000;
        CountDownLatch allSpawned = new CountDownLatch(N);

        for (int i = 0; i < N; i++) {
            runtime.spawn(msg -> allSpawned.countDown());
        }

        // Give virtual thread executor time to register all agents
        Thread.sleep(200);
        assertEquals(N, runtime.size(), "All 10K agents must be in registry");
    }

    @Test
    @Timeout(5)
    void testPingPong() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Agent> refA = new AtomicReference<>();
        AtomicReference<Agent> refB = new AtomicReference<>();

        // Agents created with forward reference via AtomicReference
        // A receives "ping" → forwards "ping" to B; A receives "pong" → countdown
        // B receives "ping" → sends "pong" to A → countdown
        Agent a = runtime.spawn(msg -> {
            if ("ping".equals(msg)) {
                refB.get().send("ping");  // forward ping to B
            } else if ("pong".equals(msg)) {
                done.countDown();          // A got the reply
            }
        });
        Agent b = runtime.spawn(msg -> {
            if ("ping".equals(msg)) {
                refA.get().send("pong");  // B replies pong to A
                done.countDown();          // B done
            }
        });
        refA.set(a);
        refB.set(b);

        a.send("ping");

        assertTrue(done.await(500, TimeUnit.MILLISECONDS),
            "Ping-pong between agents must complete within 500ms");
    }

    @Test
    @Timeout(60)
    void testMemoryFootprint() throws InterruptedException {
        int N = 10_000;
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();

        System.gc();
        Thread.sleep(200);
        long before = memBean.getHeapMemoryUsage().getUsed();

        for (int i = 0; i < N; i++) {
            runtime.spawn(msg -> {});
        }

        Thread.sleep(500); // allow all virtual threads to start
        System.gc();
        Thread.sleep(200);
        long after = memBean.getHeapMemoryUsage().getUsed();

        long bytesPerAgent = (after - before) / N;
        System.out.printf("Memory footprint: %d bytes/agent (target < 500)%n", bytesPerAgent);

        assertTrue(bytesPerAgent < 500,
            "Must use < 500 bytes/agent, got: " + bytesPerAgent + " bytes/agent");
    }

    @Test
    @Timeout(15)
    void testClose() throws InterruptedException {
        int N = 1000;
        for (int i = 0; i < N; i++) {
            runtime.spawn(msg -> {});
        }
        Thread.sleep(100); // allow threads to start

        long start = System.currentTimeMillis();
        runtime.close();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 5000,
            "Runtime.close() must complete within 5 seconds, took: " + elapsed + "ms");
    }

    @Test
    @Timeout(10)
    void testNoStarvation() throws InterruptedException {
        int N = 1000;
        CountDownLatch allExecuted = new CountDownLatch(N);

        for (int i = 0; i < N; i++) {
            Agent agent = runtime.spawn(msg -> allExecuted.countDown());
            agent.send("run");
        }

        assertTrue(allExecuted.await(100, TimeUnit.MILLISECONDS),
            "All 1000 agents must execute within 100ms (no starvation)");
    }
}
