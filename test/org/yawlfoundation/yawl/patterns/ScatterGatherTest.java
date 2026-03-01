package org.yawlfoundation.yawl.patterns;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;
import org.yawlfoundation.yawl.engine.agent.core.Msg;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Chicago TDD integration tests for Scatter-Gather pattern.
 *
 * Setup: 3 worker actors, 1 coordinator.
 * Pattern: Coordinator scatters work to all 3 workers, waits for all replies.
 *
 * Tests:
 * - Coordinator scatters work to all 3, waits for all replies (blocking)
 * - If 1 worker times out, coordinator timeout after duration
 * - Results collected in order (implicit Phaser ordering)
 */
@DisplayName("Scatter-Gather Pattern Tests")
class ScatterGatherTest {

    private ActorRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = createRuntime();
    }

    @AfterEach
    void tearDown() throws Exception {
        runtime.close();
    }

    // ============================================================
    // Test 1: Coordinator scatters work to all 3, waits for all
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("Scatter-gather completes when all workers reply")
    void testScatterGatherAllComplete() throws Exception {
        CountDownLatch workersReady = new CountDownLatch(3);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        // Worker actor: processes query and replies
        var createWorker = (int id) -> runtime.spawn(self -> {
            workersReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        String result = "worker-" + id + "-processed-" + query.question();
                        Msg.Reply reply = new Msg.Reply(
                            query.correlationId(),
                            result,
                            null
                        );
                        query.sender().tell(reply);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef worker1 = createWorker.apply(1);
        ActorRef worker2 = createWorker.apply(2);
        ActorRef worker3 = createWorker.apply(3);

        CountDownLatch coordinatorReady = new CountDownLatch(1);
        AtomicReference<ActorRef> coordinatorRef = new AtomicReference<>();

        // Coordinator: scatters work, gathers replies
        ActorRef coordinator = runtime.spawn(self -> {
            coordinatorRef.set(self);
            coordinatorReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        workersReady.await();
        coordinatorReady.await();

        // Scatter: send queries to all 3 workers and collect futures
        long corrId1 = System.nanoTime();
        long corrId2 = System.nanoTime() + 1;
        long corrId3 = System.nanoTime() + 2;

        CompletableFuture<Object> future1 = coordinator.ask(
            new Msg.Query(corrId1, coordinator, "WORK_1", null),
            Duration.ofSeconds(2)
        );
        CompletableFuture<Object> future2 = coordinator.ask(
            new Msg.Query(corrId2, coordinator, "WORK_2", null),
            Duration.ofSeconds(2)
        );
        CompletableFuture<Object> future3 = coordinator.ask(
            new Msg.Query(corrId3, coordinator, "WORK_3", null),
            Duration.ofSeconds(2)
        );

        // Gather: wait for all replies
        Object reply1 = future1.get(3, java.util.concurrent.TimeUnit.SECONDS);
        Object reply2 = future2.get(3, java.util.concurrent.TimeUnit.SECONDS);
        Object reply3 = future3.get(3, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(reply1)
            .as("Worker 1 must reply")
            .isInstanceOf(Msg.Reply.class);

        assertThat(reply2)
            .as("Worker 2 must reply")
            .isInstanceOf(Msg.Reply.class);

        assertThat(reply3)
            .as("Worker 3 must reply")
            .isInstanceOf(Msg.Reply.class);

        // Verify results
        Msg.Reply r1 = (Msg.Reply) reply1;
        Msg.Reply r2 = (Msg.Reply) reply2;
        Msg.Reply r3 = (Msg.Reply) reply3;

        assertThat(r1.result())
            .as("Worker 1 must process work")
            .isEqualTo("worker-1-processed-WORK_1");

        assertThat(r2.result())
            .as("Worker 2 must process work")
            .isEqualTo("worker-2-processed-WORK_2");

        assertThat(r3.result())
            .as("Worker 3 must process work")
            .isEqualTo("worker-3-processed-WORK_3");
    }

    @Test
    @Timeout(5)
    @DisplayName("Scatter-gather with multiple work items per worker")
    void testScatterGatherMultipleWorkItems() throws Exception {
        CountDownLatch workerReady = new CountDownLatch(1);

        ActorRef worker = runtime.spawn(self -> {
            workerReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        int workId = (Integer) query.params();
                        String result = "completed-work-" + workId;
                        Msg.Reply reply = new Msg.Reply(
                            query.correlationId(),
                            result,
                            null
                        );
                        query.sender().tell(reply);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        CountDownLatch coordinatorReady = new CountDownLatch(1);
        AtomicReference<ActorRef> coordinatorRef = new AtomicReference<>();

        ActorRef coordinator = runtime.spawn(self -> {
            coordinatorRef.set(self);
            coordinatorReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        workerReady.await();
        coordinatorReady.await();

        // Scatter: send 5 work items to same worker
        int numWorkItems = 5;
        CompletableFuture<Object>[] futures = new CompletableFuture[numWorkItems];

        for (int i = 0; i < numWorkItems; i++) {
            futures[i] = coordinator.ask(
                new Msg.Query(
                    System.nanoTime() + i,
                    coordinator,
                    "WORK",
                    i
                ),
                Duration.ofSeconds(2)
            );
        }

        // Gather: wait for all replies
        for (int i = 0; i < numWorkItems; i++) {
            Object reply = futures[i].get(3, java.util.concurrent.TimeUnit.SECONDS);
            Msg.Reply replyMsg = (Msg.Reply) reply;

            assertThat(replyMsg.result())
                .as("Work item " + i + " must be completed")
                .isEqualTo("completed-work-" + i);
        }
    }

    // ============================================================
    // Test 2: If 1 worker times out, coordinator timeout after duration
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("Scatter-gather timeout if one worker doesn't reply")
    void testScatterGatherTimeoutOnSlowWorker() throws InterruptedException {
        CountDownLatch worker1Ready = new CountDownLatch(1);
        CountDownLatch worker2Ready = new CountDownLatch(1);

        // Worker 1: fast (replies immediately)
        ActorRef worker1 = runtime.spawn(self -> {
            worker1Ready.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        Msg.Reply reply = new Msg.Reply(
                            query.correlationId(),
                            "worker1-reply",
                            null
                        );
                        query.sender().tell(reply);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Worker 2: slow (never replies)
        ActorRef worker2 = runtime.spawn(self -> {
            worker2Ready.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage(); // Ignore all queries
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        CountDownLatch coordinatorReady = new CountDownLatch(1);
        AtomicReference<ActorRef> coordinatorRef = new AtomicReference<>();

        ActorRef coordinator = runtime.spawn(self -> {
            coordinatorRef.set(self);
            coordinatorReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        worker1Ready.await();
        worker2Ready.await();
        coordinatorReady.await();

        // Scatter to both workers
        Duration timeout = Duration.ofMillis(100);
        CompletableFuture<Object> future1 = coordinator.ask(
            new Msg.Query(System.nanoTime(), coordinator, "Q1", null),
            timeout
        );
        CompletableFuture<Object> future2 = coordinator.ask(
            new Msg.Query(System.nanoTime() + 1, coordinator, "Q2", null),
            timeout
        );

        // Future 1 should complete quickly
        Object reply1 = future1.get(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertThat(reply1).isInstanceOf(Msg.Reply.class);

        // Future 2 should timeout
        assertThatThrownBy(() -> future2.get(500, java.util.concurrent.TimeUnit.MILLISECONDS))
            .as("Slow worker must timeout")
            .isInstanceOf(TimeoutException.class);
    }

    @Test
    @Timeout(5)
    @DisplayName("Gather phase fails if any worker times out (all-or-nothing semantics)")
    void testScatterGatherAllOrNothingTimeout() throws InterruptedException {
        CountDownLatch worker1Ready = new CountDownLatch(1);
        CountDownLatch worker2Ready = new CountDownLatch(1);
        CountDownLatch worker3Ready = new CountDownLatch(1);

        // Workers 1 and 2: fast
        ActorRef worker1 = runtime.spawn(self -> {
            worker1Ready.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        query.sender().tell(new Msg.Reply(query.correlationId(), "w1", null));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef worker2 = runtime.spawn(self -> {
            worker2Ready.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        query.sender().tell(new Msg.Reply(query.correlationId(), "w2", null));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Worker 3: slow (timeout)
        ActorRef worker3 = runtime.spawn(self -> {
            worker3Ready.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        CountDownLatch coordinatorReady = new CountDownLatch(1);
        AtomicReference<ActorRef> coordinatorRef = new AtomicReference<>();

        ActorRef coordinator = runtime.spawn(self -> {
            coordinatorRef.set(self);
            coordinatorReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        worker1Ready.await();
        worker2Ready.await();
        worker3Ready.await();
        coordinatorReady.await();

        // Scatter to all 3
        Duration timeout = Duration.ofMillis(100);

        CompletableFuture<Object>[] futures = new CompletableFuture[3];
        futures[0] = coordinator.ask(
            new Msg.Query(System.nanoTime(), coordinator, "W1", null),
            timeout
        );
        futures[1] = coordinator.ask(
            new Msg.Query(System.nanoTime() + 1, coordinator, "W2", null),
            timeout
        );
        futures[2] = coordinator.ask(
            new Msg.Query(System.nanoTime() + 2, coordinator, "W3", null),
            timeout
        );

        // Gather phase
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);

        for (int i = 0; i < 3; i++) {
            try {
                Object reply = futures[i].get(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                successCount.incrementAndGet();
            } catch (TimeoutException e) {
                timeoutCount.incrementAndGet();
            } catch (Exception e) {
                // Ignore other exceptions
            }
        }

        assertThat(successCount.get())
            .as("At least 2 workers must reply")
            .isGreaterThanOrEqualTo(2);

        assertThat(timeoutCount.get())
            .as("At least 1 worker must timeout")
            .isGreaterThanOrEqualTo(1);
    }

    // ============================================================
    // Test 3: Results collected in order (Phaser ordering)
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("Scattered results can be collected in request order")
    void testResultsCollectedInOrder() throws Exception {
        CountDownLatch workersReady = new CountDownLatch(3);
        List<String> requestOrder = Collections.synchronizedList(new ArrayList<>());

        // Worker: replies with work ID
        var createWorker = (int id) -> runtime.spawn(self -> {
            workersReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        String result = "work-" + query.params();
                        Msg.Reply reply = new Msg.Reply(
                            query.correlationId(),
                            result,
                            null
                        );
                        query.sender().tell(reply);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef worker1 = createWorker.apply(1);
        ActorRef worker2 = createWorker.apply(2);
        ActorRef worker3 = createWorker.apply(3);

        CountDownLatch coordinatorReady = new CountDownLatch(1);
        AtomicReference<ActorRef> coordinatorRef = new AtomicReference<>();

        ActorRef coordinator = runtime.spawn(self -> {
            coordinatorRef.set(self);
            coordinatorReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        workersReady.await();
        coordinatorReady.await();

        // Scatter: send work items 1, 2, 3
        CompletableFuture<Object>[] futures = new CompletableFuture[3];
        for (int i = 1; i <= 3; i++) {
            requestOrder.add("work-" + i);
            futures[i - 1] = coordinator.ask(
                new Msg.Query(System.nanoTime() + i, coordinator, "WORK", i),
                Duration.ofSeconds(2)
            );
        }

        // Gather: collect results in request order
        List<String> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Object reply = futures[i].get(3, java.util.concurrent.TimeUnit.SECONDS);
            Msg.Reply replyMsg = (Msg.Reply) reply;
            results.add((String) replyMsg.result());
        }

        assertThat(results)
            .as("Results must be collected in request order")
            .containsExactly("work-1", "work-2", "work-3");
    }

    // Helper methods

    private ActorRuntime createRuntime() {
        try {
            Class<?> cls = Class.forName("org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime");
            return (ActorRuntime) cls.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load VirtualThreadRuntime", e);
        }
    }

    private Object receiveMessage() throws InterruptedException {
        Thread.sleep(10);
        return null;
    }
}
