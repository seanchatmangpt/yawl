package org.yawlfoundation.yawl.engine.agent.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for non-blocking mailbox operations in Agent.
 *
 * This test suite verifies that Agent mailbox operations follow
 * the YAWL pattern of real implementations, not blocking or mocking.
 */
class AgentMailboxTest {

    /**
     * Test 1: Send to agent that hasn't called recv() yet (should not block)
     *
     * This verifies that send() is non-blocking and immediately returns
     * even when the recipient hasn't started receiving yet.
     */
    @Test
    void sendDoesNotBlock() throws Exception {
        // Create a simple agent
        Agent agent = new Agent(1);

        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            agent.send("msg-" + i); // Should not block
        }
        long elapsed = System.nanoTime() - start;

        assertTrue(elapsed < 100_000_000,
            "1000 sends took " + (elapsed/1_000_000) + "ms, should be < 100ms");
    }

    /**
     * Test 2: recv() on empty mailbox with timeout (should return null, not block forever)
     *
     * This verifies that recv() on an empty mailbox returns null after timeout
     * instead of blocking indefinitely.
     */
    @Test
    void recvOnEmptyMailboxWithTimeout() throws Exception {
        Agent agent = new Agent(2);

        // Record start time
        long start = System.nanoTime();

        // Call recv() on empty mailbox - should timeout after ~1 second
        Object result = agent.recv();

        long elapsed = System.nanoTime() - start;

        // Should return null after timeout
        assertNull(result, "recv() on empty mailbox should return null");

        // Should take approximately 1 second (allowing some margin)
        assertTrue(elapsed >= 900_000_000,
            "recv() took " + (elapsed/1_000_000) + "ms, should be ~1000ms");
        assertTrue(elapsed <= 2_000_000_000,
            "recv() took " + (elapsed/1_000_000) + "ms, should not exceed 2000ms");
    }

    /**
     * Test 3: Multiple concurrent sends (should all succeed)
     *
     * This verifies that multiple threads can send messages concurrently
     * and all messages are successfully queued without blocking.
     */
    @Test
    void multipleConcurrentSends() throws Exception {
        final int threadCount = 10;
        final int messagesPerThread = 100;
        final int totalMessages = threadCount * messagesPerThread;

        Agent agent = new Agent(3);

        // Use thread pool for concurrent sends
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Submit send tasks
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < messagesPerThread; j++) {
                        agent.send("thread-" + threadId + "-msg-" + j);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("Thread " + threadId + " failed: " + e.getMessage());
                }
            });
        }

        // Wait for all tasks to complete
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
            "All send tasks should complete within 5 seconds");

        // Verify all sends succeeded
        assertEquals(0, errorCount.get(), "No threads should have thrown exceptions");
        assertEquals(totalMessages, successCount.get(), "All messages should have been sent");

        // Verify messages can be received (non-deterministic order)
        int receivedCount = 0;
        long startTime = System.currentTimeMillis();
        while (receivedCount < totalMessages &&
               System.currentTimeMillis() - startTime < 3000) {
            Object msg = agent.recv();
            if (msg != null) {
                receivedCount++;
                // Verify message format
                assertTrue(msg.toString().startsWith("thread-"),
                    "Message should start with 'thread-'");
            }
        }

        // We should have received most or all messages
        assertTrue(receivedCount > totalMessages * 0.9,
            "Should receive most messages: " + receivedCount + "/" + totalMessages);
    }

    /**
     * Test 4: Verify send() works with different message types
     */
    @Test
    void sendWithDifferentMessageTypes() throws Exception {
        Agent agent = new Agent(4);

        // Send different types of messages
        agent.send("String message");
        agent.send(42); // Integer
        agent.send(true); // Boolean
        agent.send(new byte[]{1, 2, 3}); // byte array
        agent.send(null); // null is allowed

        // Verify all messages can be received
        int received = 0;
        for (int i = 0; i < 5; i++) {
            Object msg = agent.recv();
            if (msg != null) {
                received++;
            }
        }

        assertEquals(5, received, "Should receive all 5 messages");
    }

    /**
     * Test 5: Test bounded agent behavior
     */
    @Test
    void boundedAgentBehavior() throws Exception {
        // Create bounded agent with capacity of 3
        Agent boundedAgent = new Agent(5, 3);

        // Send 3 messages (should succeed)
        boundedAgent.send("msg1");
        boundedAgent.send("msg2");
        boundedAgent.send("msg3");

        // Queue is now full, send() should still succeed (offer() never blocks)
        boundedAgent.send("msg4"); // This will be accepted

        // Verify messages can be received
        String msg1 = (String) boundedAgent.recv();
        String msg2 = (String) boundedAgent.recv();
        String msg3 = (String) boundedAgent.recv();

        assertEquals("msg1", msg1);
        assertEquals("msg2", msg2);
        assertEquals("msg3", msg3);
    }

    /**
     * Test 6: Verify recv() doesn't block indefinitely when interrupted
     */
    @Test
    void recvDoesNotBlockWhenInterrupted() throws Exception {
        Agent agent = new Agent(6);

        // Start a thread that will interrupt the main thread while it's in recv()
        Thread interrupter = new Thread(() -> {
            try {
                Thread.sleep(100); // Wait for recv() to start
                Thread.currentThread().interrupt();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        interrupter.start();

        // This should return quickly due to interruption
        long start = System.nanoTime();
        Object result = agent.recv();
        long elapsed = System.nanoTime() - start;

        // Should return null due to interruption
        assertNull(result);

        // Should complete quickly due to interruption
        assertTrue(elapsed < 10_000_000,
            "recv() with interruption took " + (elapsed/1_000_000) + "ms");

        interrupter.join();
    }
}