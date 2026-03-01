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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Chicago TDD integration tests for Request-Reply pattern.
 *
 * Setup: Two actors (requester, responder).
 * Pattern: Requester asks responder, waits for CompletableFuture reply.
 *
 * Tests:
 * - Request-reply communication works end-to-end
 * - Timeout if responder doesn't reply in 100ms
 * - Multiple concurrent requests don't interfere (correlation ID isolation)
 */
@DisplayName("Request-Reply Pattern Tests")
class RequestReplyTest {

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
    // Test 1: Basic request-reply works end-to-end
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("Request-reply communication completes successfully")
    void testBasicRequestReply() throws Exception {
        CountDownLatch responderReady = new CountDownLatch(1);
        AtomicReference<ActorRef> requesterRef = new AtomicReference<>();

        // Responder actor: listens for Query, sends Reply
        ActorRef responder = runtime.spawn(self -> {
            responderReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        String answer = "response-to-" + query.question();
                        Msg.Reply reply = new Msg.Reply(
                            query.correlationId(),
                            answer,
                            null
                        );
                        query.sender().tell(reply);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Requester actor: sends Query, receives Reply
        ActorRef requester = runtime.spawn(self -> {
            requesterRef.set(self);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(responderReady.await(1, java.util.concurrent.TimeUnit.SECONDS))
            .as("Responder must be ready")
            .isTrue();

        // Send request and wait for reply
        long corrId = System.nanoTime();
        Msg.Query query = new Msg.Query(corrId, requester, "WHAT_IS_ANSWER", null);
        CompletableFuture<Object> future = requester.ask(query, Duration.ofSeconds(2));

        Object reply = future.get(3, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(reply)
            .as("Must receive reply from responder")
            .isInstanceOf(Msg.Reply.class);

        Msg.Reply replyMsg = (Msg.Reply) reply;
        assertThat(replyMsg.correlationId())
            .as("Reply correlation ID must match request")
            .isEqualTo(corrId);

        assertThat(replyMsg.result())
            .as("Reply must contain answer")
            .isEqualTo("response-to-WHAT_IS_ANSWER");
    }

    @Test
    @Timeout(5)
    @DisplayName("Request-reply with structured payload")
    void testRequestReplyWithPayload() throws Exception {
        CountDownLatch responderReady = new CountDownLatch(1);

        ActorRef responder = runtime.spawn(self -> {
            responderReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        String param = (String) query.params();
                        String result = "processed-" + param;

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

        ActorRef requester = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        responderReady.await();

        Msg.Query query = new Msg.Query(
            System.nanoTime(),
            requester,
            "PROCESS",
            "input-data"
        );
        CompletableFuture<Object> future = requester.ask(query, Duration.ofSeconds(2));
        Object reply = future.get(3, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(reply)
            .isInstanceOf(Msg.Reply.class);

        Msg.Reply replyMsg = (Msg.Reply) reply;
        assertThat(replyMsg.result())
            .as("Result must contain processed data")
            .isEqualTo("processed-input-data");
    }

    // ============================================================
    // Test 2: Timeout if responder doesn't reply in 100ms
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("Request timeout throws TimeoutException after duration")
    void testRequestTimeoutAfter100ms() {
        AtomicReference<ActorRef> requesterRef = new AtomicReference<>();
        CountDownLatch responderStarted = new CountDownLatch(1);

        // Non-responsive responder (receives but doesn't reply)
        ActorRef responder = runtime.spawn(self -> {
            responderStarted.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage(); // Consume but ignore
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef requester = runtime.spawn(self -> {
            requesterRef.set(self);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        try {
            responderStarted.await(1, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Duration timeout = Duration.ofMillis(100);
        Msg.Query query = new Msg.Query(System.nanoTime(), requester, "NO_REPLY", null);

        long start = System.currentTimeMillis();
        CompletableFuture<Object> future = requester.ask(query, timeout);

        assertThatThrownBy(() -> future.get(2, java.util.concurrent.TimeUnit.SECONDS))
            .as("Request without reply must timeout")
            .isInstanceOf(TimeoutException.class);

        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed)
            .as("Timeout must occur near specified duration")
            .isBetween(100L - 50L, 150L);
    }

    @Test
    @Timeout(5)
    @DisplayName("Multiple requests don't block each other on timeout")
    void testMultipleTimeoutsIndependent() {
        AtomicReference<ActorRef> requesterRef = new AtomicReference<>();

        // Non-responsive responder
        ActorRef responder = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef requester = runtime.spawn(self -> {
            requesterRef.set(self);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Duration timeout = Duration.ofMillis(50);

        // Send 3 requests concurrently
        CompletableFuture<Object> future1 = requester.ask(
            new Msg.Query(System.nanoTime(), requester, "Q1", null),
            timeout
        );
        CompletableFuture<Object> future2 = requester.ask(
            new Msg.Query(System.nanoTime(), requester, "Q2", null),
            timeout
        );
        CompletableFuture<Object> future3 = requester.ask(
            new Msg.Query(System.nanoTime(), requester, "Q3", null),
            timeout
        );

        // All should timeout independently (not block each other)
        assertThatThrownBy(() -> future1.get(2, java.util.concurrent.TimeUnit.SECONDS))
            .isInstanceOf(TimeoutException.class);

        assertThatThrownBy(() -> future2.get(2, java.util.concurrent.TimeUnit.SECONDS))
            .isInstanceOf(TimeoutException.class);

        assertThatThrownBy(() -> future3.get(2, java.util.concurrent.TimeUnit.SECONDS))
            .isInstanceOf(TimeoutException.class);
    }

    // ============================================================
    // Test 3: Multiple concurrent requests don't interfere
    //         (correlation ID isolation)
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("Concurrent requests with correlation ID isolation")
    void testConcurrentRequestsWithCorrelationId() throws Exception {
        CountDownLatch responderReady = new CountDownLatch(1);

        // Responder: slow processing, different replies per request
        ActorRef responder = runtime.spawn(self -> {
            responderReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        // Simulate different processing times
                        long delay = 10 + (query.correlationId() % 50);
                        Thread.sleep(delay);

                        String answer = "answer-for-" + query.question();
                        Msg.Reply reply = new Msg.Reply(
                            query.correlationId(),
                            answer,
                            null
                        );
                        query.sender().tell(reply);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        AtomicReference<ActorRef> requesterRef = new AtomicReference<>();
        ActorRef requester = runtime.spawn(self -> {
            requesterRef.set(self);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        responderReady.await();

        // Send 5 concurrent requests
        CompletableFuture<Object>[] futures = new CompletableFuture[5];
        long[] corrIds = new long[5];

        for (int i = 0; i < 5; i++) {
            corrIds[i] = System.nanoTime() + i * 1000;
            Msg.Query query = new Msg.Query(
                corrIds[i],
                requester,
                "Q" + i,
                null
            );
            futures[i] = requester.ask(query, Duration.ofSeconds(2));
        }

        // Verify all replies matched request correlation IDs
        for (int i = 0; i < 5; i++) {
            Object reply = futures[i].get(3, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(reply)
                .as("Must receive reply " + i)
                .isInstanceOf(Msg.Reply.class);

            Msg.Reply replyMsg = (Msg.Reply) reply;
            assertThat(replyMsg.correlationId())
                .as("Reply " + i + " correlation ID must match request")
                .isEqualTo(corrIds[i]);

            assertThat(replyMsg.result())
                .as("Reply " + i + " must contain correct answer")
                .isEqualTo("answer-for-Q" + i);
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("Out-of-order replies routed correctly by correlation ID")
    void testOutOfOrderRepliesRoutedByCorrelationId() throws Exception {
        CountDownLatch responderReady = new CountDownLatch(1);
        AtomicLong processingOrder = new AtomicLong(0);

        // Responder: replies in reverse order (out-of-order)
        ActorRef responder = runtime.spawn(self -> {
            responderReady.countDown();

            // Queue requests first
            Msg.Query[] queries = new Msg.Query[3];
            int qCount = 0;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        queries[qCount++] = query;
                        if (qCount == 3) break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Reply in reverse order (3, 2, 1)
            for (int i = 2; i >= 0; i--) {
                Msg.Query q = queries[i];
                if (q != null) {
                    Msg.Reply reply = new Msg.Reply(
                        q.correlationId(),
                        "answer-" + i,
                        null
                    );
                    q.sender().tell(reply);
                }
            }
        });

        CountDownLatch requesterReady = new CountDownLatch(1);
        AtomicReference<ActorRef> requesterRef = new AtomicReference<>();

        ActorRef requester = runtime.spawn(self -> {
            requesterRef.set(self);
            requesterReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        responderReady.await();
        requesterReady.await();

        // Send 3 requests
        CompletableFuture<Object>[] futures = new CompletableFuture[3];
        long[] corrIds = new long[3];

        for (int i = 0; i < 3; i++) {
            corrIds[i] = System.nanoTime() + i;
            Msg.Query query = new Msg.Query(corrIds[i], requester, "Q" + i, null);
            futures[i] = requester.ask(query, Duration.ofSeconds(2));
        }

        // Wait for replies (they arrive out-of-order but should be routed correctly)
        for (int i = 0; i < 3; i++) {
            Object reply = futures[i].get(3, java.util.concurrent.TimeUnit.SECONDS);
            Msg.Reply replyMsg = (Msg.Reply) reply;

            assertThat(replyMsg.correlationId())
                .as("Reply for request " + i + " must have matching correlation ID")
                .isEqualTo(corrIds[i]);

            assertThat(replyMsg.result())
                .as("Reply for request " + i + " must contain correct answer")
                .isEqualTo("answer-" + (2 - i)); // Replies were sent in reverse
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("Request with error in reply")
    void testRequestReplyWithError() throws Exception {
        CountDownLatch responderReady = new CountDownLatch(1);

        ActorRef responder = runtime.spawn(self -> {
            responderReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        Exception error = new IllegalArgumentException("Invalid request");
                        Msg.Reply reply = new Msg.Reply(
                            query.correlationId(),
                            null,
                            error
                        );
                        query.sender().tell(reply);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        AtomicReference<ActorRef> requesterRef = new AtomicReference<>();
        ActorRef requester = runtime.spawn(self -> {
            requesterRef.set(self);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        responderReady.await();

        Msg.Query query = new Msg.Query(System.nanoTime(), requester, "BAD_REQ", null);
        CompletableFuture<Object> future = requester.ask(query, Duration.ofSeconds(2));
        Object reply = future.get(3, java.util.concurrent.TimeUnit.SECONDS);

        Msg.Reply replyMsg = (Msg.Reply) reply;
        assertThat(replyMsg.error())
            .as("Reply must contain error")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid request");
    }

    // Helper methods

    private ActorRuntime createRuntime() {
        try {
            // Dynamically load VirtualThreadRuntime if available
            Class<?> cls = Class.forName("org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime");
            return (ActorRuntime) cls.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load VirtualThreadRuntime", e);
        }
    }

    private Object receiveMessage() throws InterruptedException {
        // Placeholder: would use actual actor message queue
        // This will be replaced by real implementation
        Thread.sleep(10);
        return null;
    }
}
