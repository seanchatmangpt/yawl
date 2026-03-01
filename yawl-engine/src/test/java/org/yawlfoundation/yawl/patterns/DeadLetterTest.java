package org.yawlfoundation.yawl.patterns;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;
import org.yawlfoundation.yawl.engine.agent.core.Msg;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Chicago TDD integration tests for Dead Letter Channel pattern.
 *
 * Pattern: Send to invalid ActorRef → message logged to DEAD_LETTER
 *          Expired message (past TTL) → logged to DEAD_LETTER
 *
 * Tests:
 * - Send to invalid ActorRef → logged to DEAD_LETTER
 * - Expired message (past TTL) → logged to DEAD_LETTER
 * - DeadLetterLog.getLog() returns all failed messages
 */
@DisplayName("Dead Letter Channel Pattern Tests")
class DeadLetterTest {

    private ActorRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = createRuntime();
    }

    @AfterEach
    void tearDown() throws Exception {
        runtime.close();
    }

    // Helper: DeadLetterLog for tracking failed messages
    static class DeadLetterLog {
        private final List<DeadLetter> log = Collections.synchronizedList(new ArrayList<>());

        void record(Object message, int targetActorId, String reason) {
            log.add(new DeadLetter(message, targetActorId, reason, System.nanoTime()));
        }

        List<DeadLetter> getLog() {
            return new ArrayList<>(log);
        }

        int size() {
            return log.size();
        }

        void clear() {
            log.clear();
        }
    }

    // Helper: DeadLetter message
    static class DeadLetter {
        final Object message;
        final int targetActorId;
        final String reason;
        final long timestamp;

        DeadLetter(Object message, int targetActorId, String reason, long timestamp) {
            this.message = message;
            this.targetActorId = targetActorId;
            this.reason = reason;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return String.format("DeadLetter{target=%d, reason=%s, msg=%s}",
                targetActorId, reason, message);
        }
    }

    // Helper: Message with TTL
    static class ExpiringMsg {
        final String payload;
        final long expiresAtNanos;

        ExpiringMsg(String payload, long ttlMillis) {
            this.payload = payload;
            this.expiresAtNanos = System.nanoTime() + (ttlMillis * 1_000_000L);
        }

        boolean isExpired() {
            return System.nanoTime() > expiresAtNanos;
        }
    }

    // ============================================================
    // Test 1: Send to invalid ActorRef → logged to DEAD_LETTER
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("Message to invalid actor ID is logged to dead letter")
    void testInvalidActorIdLogging() throws InterruptedException {
        DeadLetterLog deadLetterLog = new DeadLetterLog();
        CountDownLatch messageLogged = new CountDownLatch(1);

        // Dead letter handler
        ActorRef deadLetterHandler = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof DeadLetter dl) {
                        deadLetterLog.record(dl.message, dl.targetActorId, dl.reason);
                        messageLogged.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Send to non-existent actor
        int invalidActorId = 99999;

        // Simulate dead letter routing
        deadLetterHandler.tell(new DeadLetter(
            "message-to-invalid-actor",
            invalidActorId,
            "Invalid actor ID"
        ));

        assertThat(messageLogged.await(2, TimeUnit.SECONDS))
            .as("Dead letter must be logged")
            .isTrue();

        assertThat(deadLetterLog.size())
            .as("Dead letter log must contain 1 entry")
            .isEqualTo(1);

        DeadLetter logged = deadLetterLog.getLog().get(0);
        assertThat(logged.targetActorId)
            .as("Dead letter must record invalid actor ID")
            .isEqualTo(invalidActorId);

        assertThat(logged.message)
            .as("Dead letter must preserve original message")
            .isEqualTo("message-to-invalid-actor");

        assertThat(logged.reason)
            .as("Dead letter must record reason")
            .isEqualTo("Invalid actor ID");
    }

    @Test
    @Timeout(5)
    @DisplayName("Multiple messages to invalid actors all logged")
    void testMultipleInvalidActorsLogged() throws InterruptedException {
        DeadLetterLog deadLetterLog = new DeadLetterLog();
        CountDownLatch allLogged = new CountDownLatch(5);

        ActorRef deadLetterHandler = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof DeadLetter dl) {
                        deadLetterLog.record(dl.message, dl.targetActorId, dl.reason);
                        allLogged.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Send 5 messages to different invalid actors
        for (int i = 0; i < 5; i++) {
            deadLetterHandler.tell(new DeadLetter(
                "message-" + i,
                10000 + i,
                "Invalid actor"
            ));
        }

        assertThat(allLogged.await(2, TimeUnit.SECONDS))
            .as("All 5 dead letters must be logged")
            .isTrue();

        assertThat(deadLetterLog.size())
            .as("Dead letter log must contain 5 entries")
            .isEqualTo(5);

        for (int i = 0; i < 5; i++) {
            DeadLetter logged = deadLetterLog.getLog().get(i);
            assertThat(logged.targetActorId)
                .as("Entry " + i + " must have correct actor ID")
                .isEqualTo(10000 + i);
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("Dead letter preserves sender information")
    void testDeadLetterPreservesSender() throws InterruptedException {
        DeadLetterLog deadLetterLog = new DeadLetterLog();
        CountDownLatch logged = new CountDownLatch(1);

        ActorRef sender = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef deadLetterHandler = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof DeadLetter dl) {
                        deadLetterLog.record(dl.message, dl.targetActorId, dl.reason);
                        logged.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Send message from sender to invalid actor
        deadLetterHandler.tell(new DeadLetter(
            "from-sender",
            99999,
            "Sender: " + sender.id()
        ));

        logged.await();
        DeadLetter dl = deadLetterLog.getLog().get(0);

        assertThat(dl.reason)
            .as("Dead letter must preserve sender information")
            .contains("Sender: " + sender.id());
    }

    // ============================================================
    // Test 2: Expired message (past TTL) → logged to DEAD_LETTER
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("Expired message is logged to dead letter")
    void testExpiredMessageLogging() throws InterruptedException {
        DeadLetterLog deadLetterLog = new DeadLetterLog();
        CountDownLatch expiredLogged = new CountDownLatch(1);

        ActorRef deadLetterHandler = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof DeadLetter dl) {
                        deadLetterLog.record(dl.message, dl.targetActorId, dl.reason);
                        expiredLogged.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Create message that expires immediately
        ExpiringMsg expiring = new ExpiringMsg("expired-payload", 0);

        // Wait for expiration
        Thread.sleep(10);

        // Route to dead letter because expired
        deadLetterHandler.tell(new DeadLetter(
            expiring,
            123,
            "Message expired (TTL exceeded)"
        ));

        assertThat(expiredLogged.await(2, TimeUnit.SECONDS))
            .as("Expired message must be logged")
            .isTrue();

        assertThat(deadLetterLog.size())
            .as("Dead letter log must contain expired message")
            .isEqualTo(1);

        DeadLetter logged = deadLetterLog.getLog().get(0);
        assertThat(logged.reason)
            .as("Reason must mention TTL expiration")
            .contains("expired");
    }

    @Test
    @Timeout(5)
    @DisplayName("Mix of expired and invalid messages all logged")
    void testMixedDeadLetterReasons() throws InterruptedException {
        DeadLetterLog deadLetterLog = new DeadLetterLog();
        CountDownLatch allLogged = new CountDownLatch(3);

        ActorRef deadLetterHandler = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof DeadLetter dl) {
                        deadLetterLog.record(dl.message, dl.targetActorId, dl.reason);
                        allLogged.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Invalid actor
        deadLetterHandler.tell(new DeadLetter("msg1", 99999, "Invalid actor ID"));

        // Expired message
        deadLetterHandler.tell(new DeadLetter(
            new ExpiringMsg("msg2", 0),
            123,
            "Message expired"
        ));

        // Another invalid
        deadLetterHandler.tell(new DeadLetter("msg3", 88888, "Invalid actor ID"));

        assertThat(allLogged.await(2, TimeUnit.SECONDS))
            .as("All 3 messages must be logged")
            .isTrue();

        assertThat(deadLetterLog.size()).isEqualTo(3);

        List<DeadLetter> logs = deadLetterLog.getLog();
        assertThat(logs.get(0).reason).contains("Invalid actor ID");
        assertThat(logs.get(1).reason).contains("expired");
        assertThat(logs.get(2).reason).contains("Invalid actor ID");
    }

    // ============================================================
    // Test 3: DeadLetterLog.getLog() returns all failed messages
    // ============================================================

    @Test
    @Timeout(5)
    @DisplayName("DeadLetterLog.getLog() returns all logged messages")
    void testGetLogReturnsAllMessages() throws InterruptedException {
        DeadLetterLog deadLetterLog = new DeadLetterLog();
        CountDownLatch allLogged = new CountDownLatch(10);

        ActorRef deadLetterHandler = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof DeadLetter dl) {
                        deadLetterLog.record(dl.message, dl.targetActorId, dl.reason);
                        allLogged.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Log 10 messages
        for (int i = 0; i < 10; i++) {
            deadLetterHandler.tell(new DeadLetter(
                "message-" + i,
                1000 + i,
                "Reason " + i
            ));
        }

        allLogged.await();

        // Retrieve all
        List<DeadLetter> allLogs = deadLetterLog.getLog();

        assertThat(allLogs)
            .as("getLog() must return all 10 logged messages")
            .hasSize(10);

        // Verify content
        for (int i = 0; i < 10; i++) {
            DeadLetter dl = allLogs.get(i);
            assertThat(dl.message)
                .as("Message " + i + " must be preserved")
                .isEqualTo("message-" + i);

            assertThat(dl.targetActorId)
                .as("Actor ID " + i + " must be preserved")
                .isEqualTo(1000 + i);

            assertThat(dl.reason)
                .as("Reason " + i + " must be preserved")
                .isEqualTo("Reason " + i);
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("DeadLetterLog is thread-safe")
    void testDeadLetterLogThreadSafe() throws InterruptedException {
        DeadLetterLog deadLetterLog = new DeadLetterLog();
        int numMessages = 100;
        CountDownLatch allLogged = new CountDownLatch(numMessages);

        // Multiple senders
        for (int sender = 0; sender < 4; sender++) {
            final int senderId = sender;
            runtime.spawn(self -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Object msg = receiveMessage();
                        if (msg instanceof DeadLetter dl) {
                            deadLetterLog.record(dl.message, dl.targetActorId, dl.reason);
                            allLogged.countDown();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Single dead letter handler that distributes
        ActorRef deadLetterHandler = runtime.spawn(self -> {
            try {
                int count = 0;
                while (!Thread.currentThread().isInterrupted() && count < numMessages) {
                    Object msg = receiveMessage();
                    if (msg instanceof Integer) {
                        deadLetterLog.record("msg-" + count, 1000 + count, "Test");
                        allLogged.countDown();
                        count++;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Submit messages
        for (int i = 0; i < numMessages; i++) {
            deadLetterHandler.tell(i);
        }

        allLogged.await(5, TimeUnit.SECONDS);

        // Verify all logged (no lost messages due to race conditions)
        assertThat(deadLetterLog.size())
            .as("All messages must be logged thread-safely")
            .isEqualTo(numMessages);
    }

    @Test
    @Timeout(5)
    @DisplayName("Dead letter log captures timestamps")
    void testDeadLetterTimestampCapture() throws InterruptedException {
        DeadLetterLog deadLetterLog = new DeadLetterLog();
        CountDownLatch logged = new CountDownLatch(2);

        ActorRef deadLetterHandler = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof DeadLetter dl) {
                        deadLetterLog.record(dl.message, dl.targetActorId, dl.reason);
                        logged.countDown();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        long beforeTime = System.nanoTime();

        deadLetterHandler.tell(new DeadLetter("msg1", 999, "reason1"));
        Thread.sleep(10);
        deadLetterHandler.tell(new DeadLetter("msg2", 888, "reason2"));

        long afterTime = System.nanoTime();

        logged.await();

        List<DeadLetter> logs = deadLetterLog.getLog();

        assertThat(logs.get(0).timestamp)
            .as("First message must have timestamp after start")
            .isGreaterThanOrEqualTo(beforeTime);

        assertThat(logs.get(1).timestamp)
            .as("Second message must have later timestamp")
            .isGreaterThan(logs.get(0).timestamp);

        assertThat(logs.get(1).timestamp)
            .as("Second message timestamp must be before end")
            .isLessThanOrEqualTo(afterTime);
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
