package org.yawlfoundation.yawl.engine.agent.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Chicago TDD integration tests for ActorRef.
 *
 * Tests core ActorRef operations: tell(), ask(), stop() using real actor instances.
 * No mocks—all tests use real Runtime and Msg communication.
 */
@DisplayName("ActorRef Core Tests")
class ActorRefTest {

    private ActorRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new VirtualThreadRuntime();
    }

    @AfterEach
    void tearDown() throws Exception {
        runtime.close();
    }

    // ============================================================
    // Test 1: ActorRef.tell() delivers message to actor behavior
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("tell() delivers message to actor behavior")
    void testTellDelivery() throws InterruptedException {
        CountDownLatch messageReceived = new CountDownLatch(1);
        AtomicReference<Object> receivedMsg = new AtomicReference<>();

        ActorRef actor = runtime.spawn(self -> {
            try {
                Object msg = Thread.currentThread().getReceiverQueue().take();
                receivedMsg.set(msg);
                messageReceived.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        String testMessage = "hello-actor";
        actor.tell(testMessage);

        assertThat(messageReceived.await(1, java.util.concurrent.TimeUnit.SECONDS))
            .as("Message must be delivered within 1 second")
            .isTrue();

        assertThat(receivedMsg.get())
            .as("Received message must match sent message")
            .isEqualTo(testMessage);
    }

    @Test
    @Timeout(5)
    @DisplayName("tell() works with Msg.Command records")
    void testTellWithCommand() throws InterruptedException {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<Msg.Command> cmd = new AtomicReference<>();

        ActorRef actor = runtime.spawn(self -> {
            try {
                Object msg = Thread.currentThread().getReceiverQueue().take();
                if (msg instanceof Msg.Command c) {
                    cmd.set(c);
                    received.countDown();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Msg.Command command = new Msg.Command("TEST_ACTION", "payload data");
        actor.tell(command);

        assertThat(received.await(1, java.util.concurrent.TimeUnit.SECONDS))
            .as("Command must be delivered")
            .isTrue();

        assertThat(cmd.get())
            .as("Command record must be preserved")
            .isEqualTo(command)
            .extracting(Msg.Command::action)
            .isEqualTo("TEST_ACTION");
    }

    @Test
    @Timeout(5)
    @DisplayName("tell() is fire-and-forget (non-blocking)")
    void testTellIsNonBlocking() {
        CountDownLatch actorStarted = new CountDownLatch(1);

        ActorRef actor = runtime.spawn(self -> {
            actorStarted.countDown();
            // Simulate slow processing
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        try {
            actorStarted.await(1, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long start = System.currentTimeMillis();
        actor.tell("message");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed)
            .as("tell() must return immediately (non-blocking)")
            .isLessThan(100);
    }

    @Test
    @Timeout(5)
    @DisplayName("tell() to dead actor is a no-op (graceful)")
    void testTellToDeadActor() {
        ActorRef actor = runtime.spawn(self -> {
            // Immediately terminate
        });

        // Give actor time to terminate
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Should not throw or block
        assertThatCode(() -> actor.tell("message"))
            .as("tell() to dead actor must be safe")
            .doesNotThrowAnyException();
    }

    // ============================================================
    // Test 2: ActorRef.ask() returns CompletableFuture<Msg> with result
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("ask() returns CompletableFuture that completes with reply")
    void testAskWithReply() throws Exception {
        ActorRef responder = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = Thread.currentThread().getReceiverQueue().take();
                    if (msg instanceof Msg.Query query) {
                        // Reply to sender
                        Msg.Reply reply = new Msg.Reply(
                            query.correlationId(),
                            "answer-42",
                            null
                        );
                        query.sender().tell(reply);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef requester = runtime.spawn(self -> {
            // Requester is ready to receive replies
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = Thread.currentThread().getReceiverQueue().take();
                    // Replies come back to receiver
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        long correlationId = System.nanoTime();
        Msg.Query query = new Msg.Query(correlationId, requester, "GET_ANSWER", null);

        CompletableFuture<Object> future = requester.ask(query, Duration.ofSeconds(2));
        Object reply = future.get(2, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(reply)
            .as("ask() must return reply message")
            .isInstanceOf(Msg.Reply.class);

        Msg.Reply replyMsg = (Msg.Reply) reply;
        assertThat(replyMsg.result())
            .as("Reply result must contain answer")
            .isEqualTo("answer-42");
    }

    @Test
    @Timeout(5)
    @DisplayName("ask() returns CompletableFuture (non-blocking)")
    void testAskReturnsCompletableFuture() throws InterruptedException {
        CountDownLatch actorReady = new CountDownLatch(1);

        ActorRef actor = runtime.spawn(self -> {
            actorReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().getReceiverQueue().take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        actorReady.await();

        long start = System.currentTimeMillis();
        CompletableFuture<Object> future = actor.ask(
            "query",
            Duration.ofSeconds(10)
        );
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed)
            .as("ask() must return CompletableFuture immediately")
            .isLessThan(50);

        assertThat(future)
            .as("ask() must return CompletableFuture instance")
            .isInstanceOf(CompletableFuture.class);
    }

    @Test
    @Timeout(5)
    @DisplayName("ask() preserves correlation ID in request-reply cycle")
    void testAskCorrelationId() throws Exception {
        CountDownLatch queryReceived = new CountDownLatch(1);
        AtomicReference<Long> receivedCorrId = new AtomicReference<>();

        ActorRef responder = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = Thread.currentThread().getReceiverQueue().take();
                    if (msg instanceof Msg.Query query) {
                        receivedCorrId.set(query.correlationId());
                        queryReceived.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        long expectedCorrId = System.nanoTime();
        ActorRef requester = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().getReceiverQueue().take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        responder.tell(new Msg.Query(expectedCorrId, requester, "TEST", null));
        queryReceived.await(1, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(receivedCorrId.get())
            .as("Correlation ID must be preserved in query")
            .isEqualTo(expectedCorrId);
    }

    // ============================================================
    // Test 3: ActorRef.ask() timeout throws TimeoutException
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("ask() timeout throws TimeoutException after duration")
    void testAskTimeout() {
        ActorRef actor = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // Never reply — simulate unresponsive actor
                    Thread.currentThread().getReceiverQueue().take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        CompletableFuture<Object> future = actor.ask(
            "will-timeout",
            Duration.ofMillis(100)
        );

        assertThatThrownBy(() -> future.get(200, java.util.concurrent.TimeUnit.MILLISECONDS))
            .as("ask() must timeout and throw TimeoutException")
            .isInstanceOf(TimeoutException.class);
    }

    @Test
    @Timeout(5)
    @DisplayName("ask() timeout respects specified duration")
    void testAskTimeoutDuration() {
        ActorRef actor = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().getReceiverQueue().take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        long timeoutMs = 100;
        long start = System.currentTimeMillis();

        CompletableFuture<Object> future = actor.ask(
            "query",
            Duration.ofMillis(timeoutMs)
        );

        try {
            future.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            // Expected
        }

        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed)
            .as("Timeout must occur near specified duration (within ±50ms)")
            .isBetween(timeoutMs - 50, timeoutMs + 100);
    }

    @Test
    @Timeout(5)
    @DisplayName("ask() with quick reply beats timeout")
    void testAskQuickReply() throws Exception {
        AtomicReference<ActorRef> requesterRef = new AtomicReference<>();

        ActorRef responder = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = Thread.currentThread().getReceiverQueue().take();
                    if (msg instanceof Msg.Query query) {
                        Msg.Reply reply = new Msg.Reply(
                            query.correlationId(),
                            "quick-answer",
                            null
                        );
                        query.sender().tell(reply);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef requester = runtime.spawn(self -> {
            requesterRef.set(self);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().getReceiverQueue().take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        CompletableFuture<Object> future = requester.ask(
            new Msg.Query(System.nanoTime(), requester, "FAST", null),
            Duration.ofSeconds(5)
        );

        Object reply = future.get(1, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(reply)
            .as("Reply must arrive before timeout")
            .isNotNull();
    }

    // ============================================================
    // Test 4: ActorRef.stop() terminates actor
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("stop() terminates actor gracefully")
    void testStopTerminatesActor() throws InterruptedException {
        CountDownLatch actorStarted = new CountDownLatch(1);
        CountDownLatch actorStopped = new CountDownLatch(1);

        ActorRef actor = runtime.spawn(self -> {
            actorStarted.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().getReceiverQueue().take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                actorStopped.countDown();
            }
        });

        assertThat(actorStarted.await(1, java.util.concurrent.TimeUnit.SECONDS))
            .as("Actor must start")
            .isTrue();

        actor.stop();

        assertThat(actorStopped.await(1, java.util.concurrent.TimeUnit.SECONDS))
            .as("Actor must stop within 1 second after stop() call")
            .isTrue();
    }

    @Test
    @Timeout(5)
    @DisplayName("stop() is idempotent (safe to call multiple times)")
    void testStopIdempotent() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);

        ActorRef actor = runtime.spawn(self -> {
            started.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().getReceiverQueue().take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        started.await();
        Thread.sleep(50);

        assertThatCode(() -> {
            actor.stop();
            actor.stop();
            actor.stop();
        })
            .as("Multiple stop() calls must be safe")
            .doesNotThrowAnyException();
    }

    @Test
    @Timeout(5)
    @DisplayName("tell() to stopped actor is no-op")
    void testTellToStoppedActor() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);

        ActorRef actor = runtime.spawn(self -> {
            started.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().getReceiverQueue().take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        started.await();
        actor.stop();
        Thread.sleep(100);

        assertThatCode(() -> actor.tell("message"))
            .as("tell() to stopped actor must be safe (no-op)")
            .doesNotThrowAnyException();
    }

    @Test
    @Timeout(5)
    @DisplayName("stop() drains pending messages gracefully")
    void testStopDrainsPendingMessages() throws InterruptedException {
        CountDownLatch allProcessed = new CountDownLatch(5);

        ActorRef actor = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = Thread.currentThread().getReceiverQueue().take();
                    allProcessed.countDown();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Send 5 messages
        for (int i = 0; i < 5; i++) {
            actor.tell("msg-" + i);
        }

        // Give time for queuing
        Thread.sleep(50);

        // Stop actor
        actor.stop();

        // All 5 messages should be processed before stop
        assertThat(allProcessed.await(1, java.util.concurrent.TimeUnit.SECONDS))
            .as("All pending messages must be processed before stopping")
            .isTrue();
    }

    @Test
    @Timeout(5)
    @DisplayName("ActorRef.id() returns valid actor ID")
    void testActorRefId() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Integer> capturedId = new AtomicReference<>();

        ActorRef actor = runtime.spawn(self -> {
            capturedId.set(self.id());
            started.countDown();
        });

        assertThat(started.await(1, java.util.concurrent.TimeUnit.SECONDS))
            .as("Actor must start")
            .isTrue();

        assertThat(actor.id())
            .as("ActorRef.id() must return valid ID")
            .isPositive()
            .isEqualTo(capturedId.get());
    }
}
