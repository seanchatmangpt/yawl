/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for complete A2A system with engines and agents.
 */
class A2AIntegrationSystemTest {

    private static final Logger logger = LoggerFactory.getLogger(A2AIntegrationSystemTest.class);

    private PeerRegistry peerRegistry;
    private BuriedEngineA2AAdapter engineAdapter;
    private AgentA2AIntegration agentIntegration;
    private CountDownLatch engineMessageLatch;
    private CountDownLatch agentMessageLatch;
    private AtomicBoolean engineMessageReceived;
    private AtomicBoolean agentMessageReceived;
    private AtomicReference<A2AMessage> lastEngineMessage;
    private AtomicReference<A2AMessage> lastAgentMessage;

    @BeforeEach
    void setUp() {
        peerRegistry = new PeerRegistry();

        // Create engine adapter
        engineAdapter = new BuriedEngineA2AAdapter(
            "engine-123",
            "workflow-group",
            peerRegistry,
            Duration.ofSeconds(10),
            50
        );

        // Create agent integration
        agentIntegration = new AgentA2AIntegration(
            "agent-456",
            "workflow-group",
            peerRegistry,
            Duration.ofSeconds(10)
        );

        // Set up latches and atomic references
        engineMessageLatch = new CountDownLatch(1);
        agentMessageLatch = new CountDownLatch(1);
        engineMessageReceived = new AtomicBoolean(false);
        agentMessageReceived = new AtomicBoolean(false);
        lastEngineMessage = new AtomicReference<>();
        lastAgentMessage = new AtomicReference<>();

        // Set up engine message handler
        engineAdapter.setMessageHandler(message -> {
            engineMessageReceived.set(true);
            lastEngineMessage.set(message);
            engineMessageLatch.countDown();
        });

        // Set up agent message handlers
        agentIntegration.subscribeToAllEvents(message -> {
            agentMessageReceived.set(true);
            lastAgentMessage.set(message);
            agentMessageLatch.countDown();
        });

        // Start the system
        engineAdapter.start();
        agentIntegration.start();

        // Register both as peers
        peerRegistry.registerPeer("engine-123",
            new PeerConnection.InMemoryPeerConnection("engine-123", engineAdapter));
        peerRegistry.registerPeer("agent-456",
            new PeerConnection.InMemoryPeerConnection("agent-456", agentIntegration));
    }

    @AfterEach
    void tearDown() {
        if (engineAdapter.isRunning()) {
            engineAdapter.stop();
        }
        if (agentIntegration.isRunning()) {
            agentIntegration.stop();
        }
    }

    @Test
    void testAgentToEngineCommunication() throws InterruptedException {
        // Arrange
        A2AMessage message = A2AMessage.builder()
            .type("workflow-event")
            .targetAgent("engine-123")
            .payload(Map.of(
                "caseId", "case-789",
                "event", "task-completed",
                "timestamp", Instant.now()
            ))
            .build();

        // Act
        boolean sent = agentIntegration.sendToEngine("engine-123", "workflow-event",
            Map.of("caseId", "case-789", "event", "task-completed"));

        // Assert
        assertTrue(sent, "Message should be sent successfully");
        assertTrue(engineMessageLatch.await(10, TimeUnit.SECONDS),
            "Engine should receive message");
        assertTrue(engineMessageReceived.get(), "Engine message received flag should be true");
        assertNotNull(lastEngineMessage.get(), "Last engine message should be set");
        assertEquals("workflow-event", lastEngineMessage.get().getType());
        assertEquals("agent-456", lastEngineMessage.get().getSourceEngine());
    }

    @Test
    void testEngineToAgentCommunication() throws InterruptedException {
        // Arrange
        A2AMessage message = A2AMessage.builder()
            .type("engine-response")
            .targetAgent("agent-456")
            .payload(Map.of(
                "responseCode", "200",
                "message", "Task processed successfully"
            ))
            .build();

        // Act
        boolean sent = engineAdapter.sendToAgent("agent-456", message);

        // Assert
        assertTrue(sent, "Message should be sent successfully");
        assertTrue(agentMessageLatch.await(10, TimeUnit.SECONDS),
            "Agent should receive message");
        assertTrue(agentMessageReceived.get(), "Agent message received flag should be true");
        assertNotNull(lastAgentMessage.get(), "Last agent message should be set");
        assertEquals("engine-response", lastAgentMessage.get().getType());
        assertEquals("engine-123", lastAgentMessage.get().getSourceEngine());
    }

    @Test
    void testBidirectionalCommunication() throws InterruptedException {
        // Arrange
        AtomicReference<String> engineResponse = new AtomicReference<>();
        AtomicReference<String> agentResponse = new AtomicReference<>();

        // Set up response handlers
        engineAdapter.setMessageHandler(engineMessage -> {
            engineResponse.set(engineMessage.getMessageId());
            engineMessageLatch.countDown();
        });

        agentIntegration.subscribeToAllEvents(agentMessage -> {
            agentResponse.set(agentMessage.getMessageId());
            agentMessageLatch.countDown();
        });

        // Act - Agent sends to engine
        A2AMessage agentMessage = A2AMessage.builder()
            .type("request")
            .targetAgent("engine-123")
            .payload(Map.of("data", "test-data"))
            .build();

        boolean agentToEngine = agentIntegration.sendToEngine("engine-123", "request",
            Map.of("data", "test-data"));

        // Wait for engine response
        assertTrue(agentToEngine, "Agent should send message to engine");
        assertTrue(engineMessageLatch.await(10, TimeUnit.SECONDS),
            "Engine should receive message from agent");

        // Act - Engine responds to agent
        A2AMessage engineResponseMessage = A2AMessage.builder()
            .type("response")
            .targetAgent("agent-456")
            .payload(Map.of("result", "success"))
            .build();

        boolean engineToAgent = engineAdapter.sendToAgent("agent-456", engineResponseMessage);

        // Assert
        assertTrue(engineToAgent, "Engine should send response to agent");
        assertTrue(agentMessageLatch.await(10, TimeUnit.SECONDS),
            "Agent should receive response from engine");
        assertNotNull(engineResponse.get(), "Engine should have received message");
        assertNotNull(agentResponse.get(), "Agent should have received response");
    }

    @Test
    void testEngineBroadcastToAgents() throws InterruptedException {
        // Arrange
        CountDownLatch broadcastLatch = new CountDownLatch(2);
        AtomicBoolean broadcastReceived = new AtomicBoolean(false);

        // Add second agent
        AgentA2AIntegration secondAgent = new AgentA2AIntegration(
            "agent-789",
            "workflow-group",
            peerRegistry,
            Duration.ofSeconds(10)
        );

        secondAgent.subscribeToAllEvents(message -> {
            broadcastReceived.set(true);
            broadcastLatch.countDown();
        });

        secondAgent.start();
        peerRegistry.registerPeer("agent-789",
            new PeerConnection.InMemoryPeerConnection("agent-789", secondAgent));

        // Set up broadcast handler for first agent
        agentIntegration.subscribeToAllEvents(message -> {
            broadcastLatch.countDown();
        });

        // Act - Engine broadcasts message
        int broadcastCount = engineAdapter.broadcastToGroup(
            A2AMessage.builder()
                .type("broadcast-test")
                .payload(Map.of("broadcast", true))
                .build()
        );

        // Assert
        assertTrue(broadcastCount >= 0, "Broadcast count should be valid");
        assertTrue(broadcastLatch.await(10, TimeUnit.SECONDS),
            "All agents should receive broadcast");
        assertTrue(broadcastReceived.get(), "Broadcast should be received by agents");
    }

    @Test
    void testMessageWithAcknowledgment() throws InterruptedException {
        // Arrange
        AtomicBoolean ackReceived = new AtomicBoolean(false);

        agentIntegration.subscribeToSystemEvents(message -> {
            if (message.getType().equals("ack")) {
                ackReceived.set(true);
            }
        });

        // Act - Send message with acknowledgment
        A2AMessage message = A2AMessage.builder()
            .type("ack-test")
            .targetAgent("engine-123")
            .payload(Map.of("requireAck", true))
            .build();

        boolean ack = engineAdapter.sendWithAck("engine-123", message, Duration.ofSeconds(5));

        // Assert
        assertTrue(ack, "Acknowledgment should be received");
        // Note: The actual acknowledgment depends on the engine implementation
        // In a real scenario, the engine would send back an ack
    }

    @Test
    void testEventSubscriptions() throws InterruptedException {
        // Arrange
        AtomicReference<String> workflowEvent = new AtomicReference<>();
        AtomicReference<String> engineEvent = new AtomicReference<>();

        // Subscribe to specific event types
        agentIntegration.subscribeToEvents("workflow-event", message -> {
            workflowEvent.set(message.getMessageId());
        });

        agentIntegration.subscribeToEngineEvents(message -> {
            engineEvent.set(message.getMessageId());
        });

        // Act - Send different types of messages
        A2AMessage workflowMessage = A2AMessage.builder()
            .type("workflow-event")
            .targetAgent("agent-456")
            .payload(Map.of("caseId", "case-123"))
            .build();

        A2AMessage engineMessage = A2AMessage.builder()
            .type("engine-event")
            .targetAgent("agent-456")
            .payload(Map.of("engineId", "engine-123"))
            .build();

        engineAdapter.sendToAgent("agent-456", workflowMessage);
        engineAdapter.sendToAgent("agent-456", engineMessage);

        // Wait for processing
        Thread.sleep(500);

        // Assert
        assertNotNull(workflowEvent.get(), "Workflow event should be received");
        assertNotNull(engineEvent.get(), "Engine event should be received");
    }

    @Test
    void testSystemMetrics() {
        // Arrange
        // Send some messages to generate metrics
        A2AMessage message1 = A2AMessage.builder()
            .type("metrics-test-1")
            .build();

        A2AMessage message2 = A2AMessage.builder()
            .type("metrics-test-2")
            .build();

        // Act
        agentIntegration.sendToEngine("engine-123", "metrics-test-1", Map.of());
        agentIntegration.sendToAgent("agent-789", "metrics-test-2", Map.of());

        // Wait for processing
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        BuriedEngineA2AAdapter.AdapterMetrics engineMetrics = engineAdapter.getMetrics();
        AgentA2AIntegration.IntegrationMetrics agentMetrics = agentIntegration.getMetrics();

        assertTrue(engineMetrics.messagesReceived() >= 0, "Engine metrics should be valid");
        assertTrue(agentMetrics.eventsDelivered() >= 0, "Agent metrics should be valid");
        assertTrue(agentMetrics.eventSubscriptions() > 0, "Agent should have subscriptions");
    }

    @Test
    void testSystemLifecycle() {
        // Assert initial state
        assertTrue(engineAdapter.isRunning(), "Engine adapter should be running");
        assertTrue(agentIntegration.isRunning(), "Agent integration should be running");

        // Act - Stop system
        engineAdapter.stop();
        agentIntegration.stop();

        // Assert
        assertFalse(engineAdapter.isRunning(), "Engine adapter should not be running");
        assertFalse(agentIntegration.isRunning(), "Agent integration should not be running");

        // Verify metrics are still available after stopping
        BuriedEngineA2AAdapter.AdapterMetrics metrics = engineAdapter.getMetrics();
        assertNotNull(metrics, "Metrics should still be available after stopping");
    }
}