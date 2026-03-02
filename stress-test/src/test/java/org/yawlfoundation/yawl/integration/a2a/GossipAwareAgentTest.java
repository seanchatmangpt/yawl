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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for GossipAwareAgent class.
 *
 * <p>These tests verify the integration between agents and the gossip protocol,
 * including message handling, peer management, and lifecycle management.</p>
 */
class GossipAwareAgentTest {

    private static final String AGENT_ID = "test-agent";
    private static final String PEER_ID = "peer-agent";
    private static final String TEST_PAYLOAD = "test-message";

    private PeerRegistry peerRegistry;
    private MockAgent mockAgent;
    private MockPeerConnection mockPeerConnection;

    @BeforeEach
    void setUp() {
        peerRegistry = new PeerRegistry();
        mockPeerConnection = new MockPeerConnection();
        peerRegistry.registerPeer(PEER_ID, mockPeerConnection);
    }

    @Test
    void testAgentCreationAndStartStop() {
        mockAgent = new MockAgent(AGENT_ID, peerRegistry);

        assertFalse(mockAgent.isRunning());

        mockAgent.start();
        assertTrue(mockAgent.isRunning());

        mockAgent.stop();
        assertFalse(mockAgent.isRunning());
    }

    @Test
    void testMessageReception() throws InterruptedException {
        mockAgent = new MockAgent(AGENT_ID, peerRegistry);
        mockAgent.start();

        // Send a message to the agent
        GossipMessage<String> message = GossipMessage.ofType(
            "test-message",
            TEST_PAYLOAD,
            PEER_ID,
            1000
        );

        // Directly send to the agent's gossip protocol
        assertTrue(mockAgent.send(message));

        // Wait for message processing
        Thread.sleep(50);

        // Agent should have received the message
        assertEquals(1, mockAgent.getReceivedMessages().size());
        assertEquals(message, mockAgent.getReceivedMessages().get(0));
    }

    @Test
    void testPeerRegistration() {
        mockAgent = new MockAgent(AGENT_ID, peerRegistry);
        mockAgent.start();

        // Agent should be able to access peer registry
        assertEquals(1, mockAgent.getAvailablePeers().size());
        assertTrue(mockAgent.getAvailablePeers().contains(PEER_ID));

        // Test peer registration through agent
        MockPeerConnection anotherPeer = new MockPeerConnection();
        mockAgent.registerPeer("another-peer", anotherPeer);

        assertEquals(2, mockAgent.getAvailablePeers().size());
        assertTrue(mockAgent.getAvailablePeers().contains("another-peer"));
    }

    @Test
    void testMetricsAccess() throws InterruptedException {
        mockAgent = new MockAgent(AGENT_ID, peerRegistry);
        mockAgent.start();

        // Send some messages
        mockAgent.send(GossipMessage.ofType("msg1", TEST_PAYLOAD, AGENT_ID, 1000));
        mockAgent.send(GossipMessage.ofType("msg2", TEST_PAYLOAD, AGENT_ID, 1000));

        // Wait for processing
        Thread.sleep(50);

        // Check metrics
        GossipProtocol.GossipMetrics metrics = mockAgent.getMetrics();
        assertEquals(2, metrics.messagesReceived());
        assertTrue(metrics.messagesPropagated() >= 2);
    }

    @Test
    void testConcurrentMessageHandling() throws InterruptedException {
        mockAgent = new ConcurrentTestAgent(AGENT_ID, peerRegistry);
        mockAgent.start();

        int messageCount = 100;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Send many messages concurrently
        CountDownLatch latch = new CountDownLatch(messageCount);
        AtomicInteger receivedCount = new AtomicInteger(0);

        for (int i = 0; i < messageCount; i++) {
            final int index = i;
            executor.submit(() -> {
                GossipMessage<String> message = GossipMessage.ofType(
                    "concurrent-msg-" + index,
                    "payload-" + index,
                    AGENT_ID,
                    1000
                );
                mockAgent.send(message);
                latch.countDown();
            });
        }

        executor.shutdown();
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Wait for all messages to be processed
        Thread.sleep(200);

        // Check that all messages were received
        assertEquals(messageCount, ((ConcurrentTestAgent)mockAgent).getReceivedCount());
    }

    @Test
    void testPeerUnregistration() {
        mockAgent = new MockAgent(AGENT_ID, peerRegistry);
        mockAgent.start();

        // Initially peer should be available
        assertEquals(1, mockAgent.getAvailablePeers().size());

        // Unregister peer
        mockAgent.unregisterPeer(PEER_ID);

        // Peer should no longer be available
        assertEquals(0, mockAgent.getAvailablePeers().size());
    }

    @Test
    void testProtocolIntegration() {
        mockAgent = new MockAgent(AGENT_ID, peerRegistry);
        mockAgent.start();

        // Get the underlying gossip protocol
        GossipProtocol<String> protocol = mockAgent.getGossipProtocol();

        // Verify it's the same instance
        assertTrue(protocol.isRunning());

        // Test protocol operations through agent
        GossipMessage<String> message = GossipMessage.ofType(
            "integration-test",
            TEST_PAYLOAD,
            AGENT_ID,
            1000
        );

        assertTrue(mockAgent.send(message));
    }

    @Test
    void testAgentLifecycleHooks() {
        mockAgent = new LifecycleTestAgent(AGENT_ID, peerRegistry);
        mockAgent.start();

        // Check that lifecycle hooks were called
        assertTrue(((LifecycleTestAgent)mockAgent).isAfterStartCalled());
        assertFalse(((LifecycleTestAgent)mockAgent).isBeforeStopCalled());

        mockAgent.stop();

        // Check that stop hook was called
        assertTrue(((LifecycleTestAgent)mockAgent).isBeforeStopCalled());
    }

    @Test
    void testMessageHandlerOverride() throws InterruptedException {
        mockAgent = new MockAgent(AGENT_ID, peerRegistry);
        mockAgent.start();

        // Set custom message handler
        List<GossipMessage<String>> customMessages = Collections.synchronizedList(new ArrayList<>());
        mockAgent.setMessageHandler(customMessages::add);

        // Send a message
        GossipMessage<String> message = GossipMessage.ofType(
            "custom-handler-test",
            TEST_PAYLOAD,
            PEER_ID,
            1000
        );
        mockAgent.send(message);

        // Wait for processing
        Thread.sleep(50);

        // Custom handler should have received the message
        assertEquals(1, customMessages.size());
        assertEquals(message, customMessages.get(0));

        // Default handler should not have received it
        assertEquals(0, mockAgent.getReceivedMessages().size());
    }

    @Test
    void testErrorInMessageHandling() throws InterruptedException {
        mockAgent = new ErrorTestAgent(AGENT_ID, peerRegistry);
        mockAgent.start();

        // Send a message that will cause an error
        GossipMessage<String> message = GossipMessage.ofType(
            "error-test",
            "cause-error",
            PEER_ID,
            1000
        );
        mockAgent.send(message);

        // Wait for processing (error should be logged but not crash the agent)
        Thread.sleep(50);

        // Agent should still be running
        assertTrue(mockAgent.isRunning());

        // Error messages should not be processed
        assertEquals(0, ((ErrorTestAgent)mockAgent).getReceivedCount());
    }

    /**
     * Mock agent implementation for testing.
     */
    private static class MockAgent extends GossipAwareAgent<String> {
        private final List<GossipMessage<String>> receivedMessages = Collections.synchronizedList(new ArrayList<>());

        public MockAgent(String agentId, PeerRegistry peerRegistry) {
            super(agentId, peerRegistry);
        }

        @Override
        public void receive(GossipMessage<String> message) {
            receivedMessages.add(message);
        }

        public List<GossipMessage<String>> getReceivedMessages() {
            return receivedMessages;
        }
    }

    /**
     * Mock agent that handles concurrent messages.
     */
    private static class ConcurrentTestAgent extends GossipAwareAgent<String> {
        private final AtomicInteger receivedCount = new AtomicInteger(0);

        public ConcurrentTestAgent(String agentId, PeerRegistry peerRegistry) {
            super(agentId, peerRegistry);
        }

        @Override
        public void receive(GossipMessage<String> message) {
            // Simulate some processing time
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            receivedCount.incrementAndGet();
        }

        public int getReceivedCount() {
            return receivedCount.get();
        }
    }

    /**
     * Mock agent that tests lifecycle hooks.
     */
    private static class LifecycleTestAgent extends GossipAwareAgent<String> {
        private boolean afterStartCalled = false;
        private boolean beforeStopCalled = false;

        public LifecycleTestAgent(String agentId, PeerRegistry peerRegistry) {
            super(agentId, peerRegistry);
        }

        @Override
        public void receive(GossipMessage<String> message) {
            // No-op
        }

        @Override
        protected void onAfterStart() {
            afterStartCalled = true;
        }

        @Override
        protected void onBeforeStop() {
            beforeStopCalled = true;
        }

        public boolean isAfterStartCalled() {
            return afterStartCalled;
        }

        public boolean isBeforeStopCalled() {
            return beforeStopCalled;
        }
    }

    /**
     * Mock agent that tests error handling.
     */
    private static class ErrorTestAgent extends GossipAwareAgent<String> {
        private final AtomicInteger receivedCount = new AtomicInteger(0);

        public ErrorTestAgent(String agentId, PeerRegistry peerRegistry) {
            super(agentId, peerRegistry);
        }

        @Override
        public void receive(GossipMessage<String> message) {
            receivedCount.incrementAndGet();
            if ("cause-error".equals(message.payload())) {
                throw new RuntimeException("Test error");
            }
        }

        public int getReceivedCount() {
            return receivedCount.get();
        }
    }

    /**
     * Mock implementation of PeerConnection for testing.
     */
    private static class MockPeerConnection implements PeerConnection {
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

            lastRtt = (long)(Math.random() * 10);
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
    }
}