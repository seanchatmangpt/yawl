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
 * Integration test for complete gossip protocol behavior with multiple agents.
 *
 * <p>This test demonstrates epidemic message propagation across multiple
 * YAWL agents using virtual threads for async operations.</p>
 */
class GossipIntegrationTest {

    private static final String AGENT_1 = "agent-1";
    private static final String AGENT_2 = "agent-2";
    private static final String AGENT_3 = "agent-3";
    private static final String TEST_PAYLOAD = "Hello from gossip!";

    private TestAgent agent1;
    private TestAgent agent2;
    private TestAgent agent3;
    private PeerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PeerRegistry();

        // Create agents
        agent1 = new TestAgent(AGENT_1, registry);
        agent2 = new TestAgent(AGENT_2, registry);
        agent3 = new TestAgent(AGENT_3, registry);

        // Connect agents to each other
        agent1.registerPeer(AGENT_2, new InMemoryTestPeerConnection(AGENT_2, agent2.getGossipProtocol()));
        agent1.registerPeer(AGENT_3, new InMemoryTestPeerConnection(AGENT_3, agent3.getGossipProtocol()));

        agent2.registerPeer(AGENT_1, new InMemoryTestPeerConnection(AGENT_1, agent1.getGossipProtocol()));
        agent2.registerPeer(AGENT_3, new InMemoryTestPeerConnection(AGENT_3, agent3.getGossipProtocol()));

        agent3.registerPeer(AGENT_1, new InMemoryTestPeerConnection(AGENT_1, agent1.getGossipProtocol()));
        agent3.registerPeer(AGENT_2, new InMemoryTestPeerConnection(AGENT_2, agent2.getGossipProtocol()));

        // Start all agents
        agent1.start();
        agent2.start();
        agent3.start();

        // Wait for startup
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        agent1.stop();
        agent2.stop();
        agent3.stop();
    }

    @Test
    void testEpidemicMessagePropagation() throws InterruptedException {
        // Agent 1 sends a message
        GossipMessage<String> originalMessage = GossipMessage.ofType(
            "epidemic-test",
            TEST_PAYLOAD,
            AGENT_1,
            5000
        );

        boolean sent = agent1.send(originalMessage);
        assertTrue(sent);

        // Wait for propagation
        Thread.sleep(200);

        // All agents should have received the message
        assertEquals(1, agent1.getReceivedMessages().size());
        assertEquals(1, agent2.getReceivedMessages().size());
        assertEquals(1, agent3.getReceivedMessages().size());

        // Each agent should have propagated to the other two
        assertTrue(agent1.getGossipProtocol().getMessagesPropagated() >= 2);
        assertTrue(agent2.getGossipProtocol().getMessagesPropagated() >= 2);
        assertTrue(agent3.getGossipProtocol().getMessagesPropagated() >= 2);

        // Check that propagated messages include the sender in propagatedTo
        GossipMessage<String> messageAtAgent2 = agent2.getReceivedMessages().get(0);
        assertTrue(messageAtAgent2.hasBeenPropagatedTo(AGENT_1));
    }

    @Test
    void testMultipleSourceMessages() throws InterruptedException {
        // Agent 1 sends message A
        GossipMessage<String> messageA = GossipMessage.ofType(
            "message-A",
            "Hello from Agent 1",
            AGENT_1,
            5000
        );
        agent1.send(messageA);

        // Agent 2 sends message B
        GossipMessage<String> messageB = GossipMessage.ofType(
            "message-B",
            "Hello from Agent 2",
            AGENT_2,
            5000
        );
        agent2.send(messageB);

        // Wait for propagation
        Thread.sleep(200);

        // All agents should have received both messages
        assertEquals(2, agent1.getReceivedMessages().size());
        assertEquals(2, agent2.getReceivedMessages().size());
        assertEquals(2, agent3.getReceivedMessages().size());

        // Check message contents
        Set<String> payloadsAtAgent1 = agent1.getReceivedMessages().stream()
            .map(GossipMessage::payload)
            .collect(java.util.HashSet::new, java.util.Set::add, java.util.Set::addAll);
        assertTrue(payloadsAtAgent1.contains("Hello from Agent 1"));
        assertTrue(payloadsAtAgent1.contains("Hello from Agent 2"));
    }

    @Test
    void testMessageLoopPrevention() throws InterruptedException {
        // Agent 1 sends a message
        GossipMessage<String> message = GossipMessage.ofType(
            "loop-prevention-test",
            TEST_PAYLOAD,
            AGENT_1,
            5000
        );
        agent1.send(message);

        // Wait for propagation
        Thread.sleep(200);

        // No agent should receive the same message multiple times
        assertEquals(1, agent1.getReceivedMessages().size());
        assertEquals(1, agent2.getReceivedMessages().size());
        assertEquals(1, agent3.getReceivedMessages().size());

        // Verify duplicate detection is working
        Set<String> messageIdsAtAgent1 = agent1.getReceivedMessages().stream()
            .map(GossipMessage::messageId)
            .collect(java.util.HashSet::new);
        assertEquals(1, messageIdsAtAgent1.size());
    }

    @Test
    void testPartialNetworkFailure() throws InterruptedException {
        // Disconnect agent 3 from the network
        agent3.unregisterPeer(AGENT_1);
        agent3.unregisterPeer(AGENT_2);

        // Agent 1 sends a message
        GossipMessage<String> message = GossipMessage.ofType(
            "partial-failure-test",
            TEST_PAYLOAD,
            AGENT_1,
            5000
        );
        agent1.send(message);

        // Wait for propagation
        Thread.sleep(200);

        // Agents 1 and 2 should have received the message
        assertEquals(1, agent1.getReceivedMessages().size());
        assertEquals(1, agent2.getReceivedMessages().size());

        // Agent 3 should not have received the message (disconnected)
        assertEquals(0, agent3.getReceivedMessages().size());
    }

    @Test
    void testHighLoadEpidemic() throws InterruptedException {
        int messageCount = 20;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Each agent sends multiple messages concurrently
        for (int i = 0; i < messageCount; i++) {
            final int index = i;
            executor.submit(() -> {
                agent1.send(GossipMessage.ofType(
                    "highload-" + index,
                    "Message " + index + " from agent 1",
                    AGENT_1,
                    5000
                ));
            });

            executor.submit(() -> {
                agent2.send(GossipMessage.ofType(
                    "highload-" + index,
                    "Message " + index + " from agent 2",
                    AGENT_2,
                    5000
                ));
            });

            executor.submit(() -> {
                agent3.send(GossipMessage.ofType(
                    "highload-" + index,
                    "Message " + index + " from agent 3",
                    AGENT_3,
                    5000
                ));
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // Wait for all messages to propagate
        Thread.sleep(500);

        // Each agent should have received all messages from all agents
        assertEquals(3 * messageCount, agent1.getReceivedMessages().size());
        assertEquals(3 * messageCount, agent2.getReceivedMessages().size());
        assertEquals(3 * messageCount, agent3.getReceivedMessages().size());

        // Check that all message types are present
        Set<String> messageTypesAtAgent1 = agent1.getReceivedMessages().stream()
            .map(GossipMessage::messageType)
            .collect(java.util.HashSet::new);
        assertEquals(messageCount, messageTypesAtAgent1.size());
    }

    @Test
    void testResponsePattern() throws InterruptedException {
        // Create an agent that responds to messages
        ResponseAgent responseAgent = new ResponseAgent(AGENT_3, registry);
        responseAgent.registerPeer(AGENT_1, new InMemoryTestPeerConnection(AGENT_1, agent1.getGossipProtocol()));
        responseAgent.registerPeer(AGENT_2, new InMemoryTestPeerConnection(AGENT_2, agent2.getGossipProtocol()));
        responseAgent.start();

        try {
            // Wait for response agent to start
            Thread.sleep(50);

            // Agent 1 sends a request message
            GossipMessage<String> request = GossipMessage.ofType(
                "request",
                "Can you help me?",
                AGENT_1,
                5000
            );
            agent1.send(request);

            // Wait for propagation and response
            Thread.sleep(200);

            // Response agent should have received the request
            assertEquals(1, responseAgent.getReceivedMessages().size());

            // Original agents should have received the response
            assertTrue(agent1.getReceivedMessages().stream()
                .anyMatch(m -> m.messageType().equals("response")));
            assertTrue(agent2.getReceivedMessages().stream()
                .anyMatch(m -> m.messageType().equals("response")));

        } finally {
            responseAgent.stop();
        }
    }

    @Test
    void testTTLExpiration() throws InterruptedException {
        // Agent 1 sends a message with very short TTL
        GossipMessage<String> shortLivedMessage = GossipMessage.ofType(
            "short-lived",
            TEST_PAYLOAD,
            AGENT_1,
            50 // 50ms TTL
        );
        agent1.send(shortLivedMessage);

        // Wait for message to expire
        Thread.sleep(100);

        // Check that expired messages are not propagated
        GossipProtocol.GossipMetrics metrics1 = agent1.getGossipProtocol().getMetrics();
        GossipProtocol.GossipMetrics metrics2 = agent2.getGossipProtocol().getMetrics();
        GossipProtocol.GossipMetrics metrics3 = agent3.getGossipProtocol().getMetrics();

        assertTrue(metrics1.messagesExpired() > 0 || metrics2.messagesExpired() > 0 ||
                   metrics3.messagesExpired() > 0);

        // Some agents might still have received it before expiration
        int totalReceived = agent1.getReceivedMessages().size() +
                           agent2.getReceivedMessages().size() +
                           agent3.getReceivedMessages().size();
        assertTrue(totalReceived <= 3); // At most one per agent
    }

    /**
     * Test agent implementation for integration tests.
     */
    private static class TestAgent extends GossipAwareAgent<String> {
        private final List<GossipMessage<String>> receivedMessages = Collections.synchronizedList(new ArrayList<>());

        public TestAgent(String agentId, PeerRegistry peerRegistry) {
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
     * Test agent that responds to incoming messages.
     */
    private static class ResponseAgent extends GossipAwareAgent<String> {
        private final List<GossipMessage<String>> receivedMessages = Collections.synchronizedList(new ArrayList<>());

        public ResponseAgent(String agentId, PeerRegistry peerRegistry) {
            super(agentId, peerRegistry);
        }

        @Override
        public void receive(GossipMessage<String> message) {
            receivedMessages.add(message);

            // Send a response if it's a request
            if ("request".equals(message.messageType())) {
                GossipMessage<String> response = GossipMessage.ofType(
                    "response",
                    "Yes, I can help!",
                    getAgentId(),
                    3000
                );
                send(response);
            }
        }

        public List<GossipMessage<String>> getReceivedMessages() {
            return receivedMessages;
        }
    }

    /**
     * In-memory peer connection for testing.
     */
    private static class InMemoryTestPeerConnection implements PeerConnection {
        private final String targetAgentId;
        private final GossipProtocol<?> targetProtocol;
        private volatile boolean available = true;
        private long lastRtt = -1;
        private long successCount = 0;
        private long failureCount = 0;

        public InMemoryTestPeerConnection(String targetAgentId, GossipProtocol<?> targetProtocol) {
            this.targetAgentId = targetAgentId;
            this.targetProtocol = targetProtocol;
        }

        @Override
        public <T> boolean deliver(GossipMessage<T> message) {
            if (!available) {
                failureCount++;
                return false;
            }

            try {
                long start = System.currentTimeMillis();
                targetProtocol.send(message);
                lastRtt = System.currentTimeMillis() - start;
                successCount++;
                return true;
            } catch (Exception e) {
                failureCount++;
                return false;
            }
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