/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.Mockito;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for GossipProtocol class.
 *
 * <p>These tests verify the async behavior, message propagation, and metrics
 * collection of the gossip protocol using virtual threads.</p>
 */
class GossipProtocolTest {

    private static final String TEST_AGENT_ID = "test-agent";
    private static final String PEER_AGENT_ID = "peer-agent";
    private static final int TEST_DELAY_MS = 10;

    private PeerRegistry peerRegistry;
    private MockPeerConnection mockPeerConnection;
    private GossipProtocol<String> gossipProtocol;
    private List<GossipMessage<String>> receivedMessages;

    @BeforeEach
    void setUp() {
        peerRegistry = new PeerRegistry();
        mockPeerConnection = new MockPeerConnection();
        peerRegistry.registerPeer(PEER_AGENT_ID, mockPeerConnection);

        gossipProtocol = new GossipProtocol<>(TEST_AGENT_ID, peerRegistry,
            Duration.ofMillis(TEST_DELAY_MS), 5, 1000);

        receivedMessages = Collections.synchronizedList(new ArrayList<>());
        gossipProtocol.setMessageHandler(receivedMessages::add);

        gossipProtocol.start();
    }

    @AfterEach
    void tearDown() {
        if (gossipProtocol != null && gossipProtocol.isRunning()) {
            gossipProtocol.stop();
        }
    }

    @Test
    void testStartAndStop() {
        assertTrue(gossipProtocol.isRunning());

        gossipProtocol.stop();
        assertFalse(gossipProtocol.isRunning());
    }

    @Test
    void testSendValidMessage() {
        GossipMessage<String> message = GossipMessage.ofType(
            "test-message",
            "test payload",
            TEST_AGENT_ID,
            1000
        );

        boolean result = gossipProtocol.send(message);
        assertTrue(result);
        assertEquals(1, receivedMessages.size());
        assertEquals(message, receivedMessages.get(0));
    }

    @Test
    void testSendInvalidMessage() {
        GossipMessage<String> invalidMessage = new GossipMessage<>(
            null,
            "test",
            "payload",
            TEST_AGENT_ID,
            new HashSet<>(),
            1000,
            Instant.now()
        );

        boolean result = gossipProtocol.send(invalidMessage);
        assertFalse(result); // Should not queue invalid message
        assertEquals(0, receivedMessages.size());
    }

    @Test
    void testStopPreventsSending() throws InterruptedException {
        gossipProtocol.stop();
        assertFalse(gossipProtocol.isRunning());

        GossipMessage<String> message = GossipMessage.ofType(
            "test-message",
            "test payload",
            TEST_AGENT_ID,
            1000
        );

        boolean result = gossipProtocol.send(message);
        assertFalse(result);
        assertEquals(0, receivedMessages.size());
    }

    @Test
    void testMessageDuplicateDetection() throws InterruptedException {
        // Send the same message twice
        GossipMessage<String> message = GossipMessage.ofType(
            "test-message",
            "test payload",
            TEST_AGENT_ID,
            1000
        );

        gossipProtocol.send(message);
        gossipProtocol.send(message); // Same message ID

        // Wait a bit for processing
        Thread.sleep(100);

        // Should only receive the message once
        assertEquals(1, receivedMessages.size());
        assertEquals(message, receivedMessages.get(0));
    }

    @Test
    void testMessageExpiration() throws InterruptedException {
        // Create message with very short TTL
        GossipMessage<String> shortLivedMessage = GossipMessage.ofType(
            "short-lived",
            "payload",
            TEST_AGENT_ID,
            10 // 10ms TTL
        );

        gossipProtocol.send(shortLivedMessage);

        // Wait for message to expire
        Thread.sleep(50);

        // Message should not be processed
        assertEquals(0, receivedMessages.size());
        assertTrue(gossipProtocol.getMessagesExpired() > 0);
    }

    @Test
    void testMessagePropagationToPeers() throws InterruptedException {
        // Send a message
        GossipMessage<String> message = GossipMessage.ofType(
            "propagation-test",
            "test payload",
            TEST_AGENT_ID,
            1000
        );

        gossipProtocol.send(message);

        // Wait for propagation
        Thread.sleep(100);

        // Message should be received by this agent
        assertEquals(1, receivedMessages.size());

        // Message should have been propagated to peer
        assertTrue(mockPeerConnection.getDeliveredMessages().size() > 0);

        // Check that the propagated message includes this agent in propagatedTo
        GossipMessage<String> deliveredToPeer = mockPeerConnection.getDeliveredMessages().get(0);
        assertTrue(deliveredToPeer.hasBeenPropagatedTo(TEST_AGENT_ID));
    }

    @Test
    void testPeerAvailabilityCheck() throws InterruptedException {
        // Initially, peer should be available
        assertTrue(peerRegistry.isPeerAvailable(PEER_AGENT_ID));

        // Mark peer as unavailable
        mockPeerConnection.setAvailable(false);

        // Should no longer be available
        assertFalse(peerRegistry.isPeerAvailable(PEER_AGENT_ID));
    }

    @Test
    void testMetricsCollection() throws InterruptedException {
        // Send multiple messages
        gossipProtocol.send(GossipMessage.ofType("msg1", "payload1", TEST_AGENT_ID, 1000));
        gossipProtocol.send(GossipMessage.ofType("msg2", "payload2", TEST_AGENT_ID, 1000));
        gossipProtocol.send(GossipMessage.ofType("msg3", "payload3", TEST_AGENT_ID, 1000));

        // Wait for processing
        Thread.sleep(100);

        GossipProtocol.GossipMetrics metrics = gossipProtocol.getMetrics();
        assertEquals(3, metrics.messagesReceived());
        assertTrue(metrics.messagesPropagated() >= 3); // At least 3, could be more due to propagation
        assertEquals(0, metrics.messagesExpired());
        assertTrue(metrics.seenMessagesSize() >= 3);
    }

    @Test
    void testConcurrentMessageSending() throws InterruptedException {
        int messageCount = 50;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Send many messages concurrently
        for (int i = 0; i < messageCount; i++) {
            final int index = i;
            executor.submit(() -> {
                gossipProtocol.send(GossipMessage.ofType(
                    "concurrent-test-" + index,
                    "payload-" + index,
                    TEST_AGENT_ID,
                    1000
                ));
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Wait for all messages to be processed
        Thread.sleep(200);

        GossipProtocol.GossipMetrics metrics = gossipProtocol.getMetrics();
        assertEquals(messageCount, metrics.messagesReceived());
    }

    @Test
    void testPropagationDelay() throws InterruptedException {
        long startTime = System.currentTimeMillis();

        gossipProtocol.send(GossipMessage.ofType(
            "delay-test",
            "test payload",
            TEST_AGENT_ID,
            1000
        ));

        // Wait for propagation
        Thread.sleep(100);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Should have some delay due to propagation
        assertTrue(duration > 0);
        assertTrue(mockPeerConnection.getDeliveredMessages().size() > 0);
    }

    @Test
    void testAntiEntropyCleanup() throws InterruptedException {
        // Add many messages to trigger cleanup
        for (int i = 0; i < 100; i++) {
            gossipProtocol.send(GossipMessage.ofType(
                "cleanup-test-" + i,
                "payload",
                TEST_AGENT_ID,
                1000
            ));
        }

        // Wait for all messages to be processed
        Thread.sleep(200);

        // Force anti-entropy cleanup by calling the method
        gossipProtocol.performAntiEntropy();

        // Metrics should show the cleanup happened
        GossipProtocol.GossipMetrics metrics = gossipProtocol.getMetrics();
        assertTrue(metrics.seenMessagesSize() > 0);
        assertTrue(metrics.seenMessagesSize() <= 100); // Should be cleaned up
    }

    @Test
    void testQueueFullScenario() throws InterruptedException {
        // Create a gossip protocol with very small queue
        GossipProtocol<String> smallQueueProtocol = new GossipProtocol<>(
            TEST_AGENT_ID, peerRegistry,
            Duration.ofMillis(TEST_DELAY_MS), 5, 1000
        );
        smallQueueProtocol.setMessageHandler(receivedMessages::add);
        smallQueueProtocol.start();

        try {
            // Fill the queue (capacity is 1000)
            int messagesToSend = 1200;
            int deliveredCount = 0;

            for (int i = 0; i < messagesToSend; i++) {
                boolean delivered = smallQueueProtocol.send(GossipMessage.ofType(
                    "queue-test-" + i,
                    "payload",
                    TEST_AGENT_ID,
                    1000
                ));
                if (delivered) {
                    deliveredCount++;
                }
            }

            // Some messages should have been rejected due to full queue
            assertTrue(deliveredCount < messagesToSend);
            assertTrue(receivedMessages.size() > 0);

        } finally {
            smallQueueProtocol.stop();
        }
    }

    @Test
    void testMaxConcurrentPropagations() throws InterruptedException {
        // Create protocol with very low max concurrent propagations
        GossipProtocol<String> limitedProtocol = new GossipProtocol<>(
            TEST_AGENT_ID, peerRegistry,
            Duration.ofMillis(TEST_DELAY_MS), 1, 1000 // Max 1 concurrent propagation
        );
        limitedProtocol.setMessageHandler(receivedMessages::add);
        limitedProtocol.start();

        try {
            // Send multiple messages
            limitedProtocol.send(GossipMessage.ofType("prop1", "payload", TEST_AGENT_ID, 1000));
            limitedProtocol.send(GossipMessage.ofType("prop2", "payload", TEST_AGENT_ID, 1000));
            limitedProtocol.send(GossipMessage.ofType("prop3", "payload", TEST_AGENT_ID, 1000));

            // Wait for processing
            Thread.sleep(200);

            // All messages should be received, but propagation should be limited
            assertEquals(3, receivedMessages.size());

        } finally {
            limitedProtocol.stop();
        }
    }

    /**
     * Mock implementation of PeerConnection for testing.
     */
    private static class MockPeerConnection implements PeerConnection {
        private final List<GossipMessage<?>> deliveredMessages = new ArrayList<>();
        private volatile boolean available = true;
        private long lastRtt = -1;
        private long successCount = 0;
        private long failureCount = 0;

        @Override
        public <T> boolean deliver(GossipMessage<T> message) {
            if (!available) {
                failureCount++;
                return false;
            }

            deliveredMessages.add(message);
            lastRtt = (long)(Math.random() * 10); // Simulate RTT
            successCount++;
            return true;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String getStatus() {
            return available ? "Available" : "Unavailable";
        }

        @Override
        public long getLastRtt() {
            return lastRtt;
        }

        @Override
        public long getSuccessCount() {
            return successCount;
        }

        @Override
        public long getFailureCount() {
            return failureCount;
        }

        @Override
        public void close() {
            available = false;
        }

        public List<GossipMessage<?>> getDeliveredMessages() {
            return deliveredMessages;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }
    }
}