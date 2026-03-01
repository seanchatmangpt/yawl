package org.yawlfoundation.yawl.patterns;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;
import org.yawlfoundation.yawl.engine.agent.core.Msg;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Chicago TDD integration tests for Routing Slip pattern.
 *
 * Setup: 3 actors in a pipeline (A → B → C).
 * Pattern: Message with routing slip [A, B, C] flows through all 3.
 *
 * Tests:
 * - Message with routing slip [A, B, C] flows through all 3
 * - Final actor can call completion handler
 * - Routing slip is immutable (new Deque per forward)
 */
@DisplayName("Routing Slip Pattern Tests")
class RoutingSlipTest {

    private ActorRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = createRuntime();
    }

    @AfterEach
    void tearDown() throws Exception {
        runtime.close();
    }

    // Helper: Message with routing slip
    static final class WorkWithSlip {
        final String workId;
        final Deque<ActorRef> slip;
        final AtomicInteger visitCount;

        WorkWithSlip(String workId, Deque<ActorRef> slip) {
            this.workId = workId;
            this.slip = slip;
            this.visitCount = new AtomicInteger(0);
        }

        WorkWithSlip withSlipAdvanced() {
            Deque<ActorRef> newSlip = new ArrayDeque<>(slip);
            newSlip.removeFirst();
            return new WorkWithSlip(workId, newSlip);
        }

        ActorRef nextRecipient() {
            return slip.peekFirst();
        }

        boolean hasMoreStops() {
            return !slip.isEmpty();
        }
    }

    // ============================================================
    // Test 1: Message with routing slip flows through all 3
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("Routing slip routes message through all actors")
    void testRoutingSlipFlowsThrough() throws InterruptedException {
        CountDownLatch processedCount = new CountDownLatch(3);
        AtomicInteger visitOrder = new AtomicInteger(0);

        // Actor A: first stop
        AtomicReference<ActorRef> actorARef = new AtomicReference<>();
        ActorRef actorA = runtime.spawn(self -> {
            actorARef.set(self);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof WorkWithSlip work) {
                        int visitNum = work.visitCount.incrementAndGet();
                        assertThat(visitNum).isEqualTo(1); // A is first

                        // Forward to next
                        if (work.hasMoreStops()) {
                            ActorRef next = work.nextRecipient();
                            next.tell(work.withSlipAdvanced());
                        }
                        processedCount.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Actor B: second stop
        AtomicReference<ActorRef> actorBRef = new AtomicReference<>();
        ActorRef actorB = runtime.spawn(self -> {
            actorBRef.set(self);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof WorkWithSlip work) {
                        int visitNum = work.visitCount.incrementAndGet();
                        assertThat(visitNum).isEqualTo(2); // B is second

                        // Forward to next
                        if (work.hasMoreStops()) {
                            ActorRef next = work.nextRecipient();
                            next.tell(work.withSlipAdvanced());
                        }
                        processedCount.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Actor C: third stop (final)
        AtomicReference<ActorRef> actorCRef = new AtomicReference<>();
        ActorRef actorC = runtime.spawn(self -> {
            actorCRef.set(self);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof WorkWithSlip work) {
                        int visitNum = work.visitCount.incrementAndGet();
                        assertThat(visitNum).isEqualTo(3); // C is third

                        // No more routing
                        assertThat(work.slip.size()).isEqualTo(1); // Only C left
                        processedCount.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Create routing slip [A, B, C]
        Deque<ActorRef> slip = new ArrayDeque<>();
        slip.addLast(actorA);
        slip.addLast(actorB);
        slip.addLast(actorC);

        // Send work with routing slip to A
        actorA.tell(new WorkWithSlip("work-1", slip));

        // Verify all 3 processed the message
        assertThat(processedCount.await(2, java.util.concurrent.TimeUnit.SECONDS))
            .as("All 3 actors must process message in order")
            .isTrue();
    }

    @Test
    @Timeout(5)
    @DisplayName("Routing slip with 5 stops flows through all in order")
    void testRoutingSlipMultipleStops() throws InterruptedException {
        int numStops = 5;
        CountDownLatch allProcessed = new CountDownLatch(numStops);
        AtomicInteger orderCheck = new AtomicInteger(0);

        // Create pipeline of 5 actors
        ActorRef[] actors = new ActorRef[numStops];

        for (int i = 0; i < numStops; i++) {
            final int index = i;
            actors[i] = runtime.spawn(self -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Object msg = receiveMessage();
                        if (msg instanceof WorkWithSlip work) {
                            int visitNum = work.visitCount.incrementAndGet();

                            // Verify visit order
                            assertThat(visitNum).isEqualTo(index + 1);

                            // Forward to next if available
                            if (work.hasMoreStops()) {
                                ActorRef next = work.nextRecipient();
                                next.tell(work.withSlipAdvanced());
                            }
                            allProcessed.countDown();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Create routing slip with all actors
        Deque<ActorRef> slip = new ArrayDeque<>();
        for (ActorRef actor : actors) {
            slip.addLast(actor);
        }

        // Send to first actor
        actors[0].tell(new WorkWithSlip("multi-stop", slip));

        assertThat(allProcessed.await(2, java.util.concurrent.TimeUnit.SECONDS))
            .as("All " + numStops + " actors must process")
            .isTrue();
    }

    // ============================================================
    // Test 2: Final actor can call completion handler
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("Final actor executes completion handler")
    void testRoutingSlipCompletionHandler() throws InterruptedException {
        CountDownLatch completionExecuted = new CountDownLatch(1);

        // Actor A
        ActorRef actorA = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof WorkWithSlip work) {
                        if (work.hasMoreStops()) {
                            ActorRef next = work.nextRecipient();
                            next.tell(work.withSlipAdvanced());
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Actor B
        ActorRef actorB = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof WorkWithSlip work) {
                        if (work.hasMoreStops()) {
                            ActorRef next = work.nextRecipient();
                            next.tell(work.withSlipAdvanced());
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Final actor: executes completion handler
        ActorRef actorC = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof WorkWithSlip work) {
                        // Check if this is the final stop
                        if (!work.hasMoreStops()) {
                            // Execute completion handler
                            onWorkCompleted(work);
                            completionExecuted.countDown();
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Create routing slip [A, B, C]
        Deque<ActorRef> slip = new ArrayDeque<>();
        slip.addLast(actorA);
        slip.addLast(actorB);
        slip.addLast(actorC);

        // Send work
        actorA.tell(new WorkWithSlip("work-with-completion", slip));

        assertThat(completionExecuted.await(2, java.util.concurrent.TimeUnit.SECONDS))
            .as("Final actor must execute completion handler")
            .isTrue();
    }

    @Test
    @Timeout(5)
    @DisplayName("Completion handler receives final work state")
    void testCompletionHandlerReceivesFinalState() throws InterruptedException {
        CountDownLatch workCompleted = new CountDownLatch(1);
        AtomicReference<WorkWithSlip> finalWork = new AtomicReference<>();

        ActorRef actorA = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof WorkWithSlip work) {
                        work.visitCount.incrementAndGet();
                        if (work.hasMoreStops()) {
                            ActorRef next = work.nextRecipient();
                            next.tell(work.withSlipAdvanced());
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef actorB = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof WorkWithSlip work) {
                        work.visitCount.incrementAndGet();
                        if (work.hasMoreStops()) {
                            ActorRef next = work.nextRecipient();
                            next.tell(work.withSlipAdvanced());
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef actorC = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof WorkWithSlip work) {
                        work.visitCount.incrementAndGet();
                        if (!work.hasMoreStops()) {
                            finalWork.set(work);
                            workCompleted.countDown();
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Deque<ActorRef> slip = new ArrayDeque<>();
        slip.addLast(actorA);
        slip.addLast(actorB);
        slip.addLast(actorC);

        actorA.tell(new WorkWithSlip("final-state-test", slip));

        assertThat(workCompleted.await(2, java.util.concurrent.TimeUnit.SECONDS))
            .as("Work must complete")
            .isTrue();

        assertThat(finalWork.get())
            .as("Final actor must have work with state")
            .isNotNull();

        assertThat(finalWork.get().visitCount.get())
            .as("Work must have been visited 3 times")
            .isEqualTo(3);

        assertThat(finalWork.get().workId)
            .as("Work ID must be preserved")
            .isEqualTo("final-state-test");
    }

    // ============================================================
    // Test 3: Routing slip is immutable (new Deque per forward)
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("Routing slip is immutable between forwards")
    void testRoutingSlipImmutable() throws InterruptedException {
        CountDownLatch verificationsComplete = new CountDownLatch(3);

        ActorRef actorA = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof WorkWithSlip work) {
                        // Original slip should have 3 entries [A, B, C]
                        assertThat(work.slip.size()).isEqualTo(3);

                        // Forward creates new deque
                        if (work.hasMoreStops()) {
                            ActorRef next = work.nextRecipient();
                            WorkWithSlip forwarded = work.withSlipAdvanced();

                            // Original slip unchanged
                            assertThat(work.slip.size()).isEqualTo(3);

                            // Forwarded has new slip [B, C]
                            assertThat(forwarded.slip.size()).isEqualTo(2);

                            next.tell(forwarded);
                        }
                        verificationsComplete.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef actorB = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof WorkWithSlip work) {
                        // Should have 2 entries [B, C]
                        assertThat(work.slip.size()).isEqualTo(2);

                        if (work.hasMoreStops()) {
                            ActorRef next = work.nextRecipient();
                            WorkWithSlip forwarded = work.withSlipAdvanced();

                            // Original unchanged
                            assertThat(work.slip.size()).isEqualTo(2);

                            // Forwarded has [C]
                            assertThat(forwarded.slip.size()).isEqualTo(1);

                            next.tell(forwarded);
                        }
                        verificationsComplete.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef actorC = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof WorkWithSlip work) {
                        // Should have 1 entry [C]
                        assertThat(work.slip.size()).isEqualTo(1);
                        verificationsComplete.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Deque<ActorRef> slip = new ArrayDeque<>();
        slip.addLast(actorA);
        slip.addLast(actorB);
        slip.addLast(actorC);

        actorA.tell(new WorkWithSlip("immutable-test", slip));

        assertThat(verificationsComplete.await(2, java.util.concurrent.TimeUnit.SECONDS))
            .as("All immutability checks must pass")
            .isTrue();
    }

    @Test
    @Timeout(5)
    @DisplayName("Original routing slip unchanged after forwards")
    void testOriginalSlipUnchangedAfterForward() throws InterruptedException {
        CountDownLatch originalSlipVerified = new CountDownLatch(1);

        ActorRef actorA = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof WorkWithSlip work) {
                        Deque<ActorRef> originalSlip = work.slip;
                        int originalSize = originalSlip.size();

                        // Forward (creates new slip)
                        if (work.hasMoreStops()) {
                            ActorRef next = work.nextRecipient();
                            next.tell(work.withSlipAdvanced());
                        }

                        // Original slip must be unchanged
                        assertThat(originalSlip.size())
                            .as("Original slip size must not change")
                            .isEqualTo(originalSize);

                        originalSlipVerified.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef actorB = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Deque<ActorRef> slip = new ArrayDeque<>();
        slip.addLast(actorA);
        slip.addLast(actorB);

        actorA.tell(new WorkWithSlip("original-unchanged", slip));

        assertThat(originalSlipVerified.await(2, java.util.concurrent.TimeUnit.SECONDS))
            .as("Original slip must remain unchanged")
            .isTrue();
    }

    // Helper methods

    private void onWorkCompleted(WorkWithSlip work) {
        // Handler called when routing slip is exhausted
        // Can perform final processing, cleanup, or signal completion
    }

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
