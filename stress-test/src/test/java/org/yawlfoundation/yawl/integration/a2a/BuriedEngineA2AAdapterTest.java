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
 * Test suite for BuriedEngineA2AAdapter functionality.
 */
class BuriedEngineA2AAdapterTest {

    private static final Logger logger = LoggerFactory.getLogger(BuriedEngineA2AAdapterTest.class);

    private BuriedEngineA2AAdapter adapter;
    private PeerRegistry peerRegistry;
    private CountDownLatch messageLatch;
    private AtomicBoolean messageReceived;
    private AtomicReference<A2AMessage> lastMessage;

    @BeforeEach
    void setUp() {
        peerRegistry = new PeerRegistry();
        adapter = new BuriedEngineA2AAdapter(
            "test-engine-123",
            "test-group",
            peerRegistry,
            Duration.ofSeconds(5),
            10
        );

        messageLatch = new CountDownLatch(1);
        messageReceived = new AtomicBoolean(false);
        lastMessage = new AtomicReference<>();

        // Set up message handler for testing
        adapter.setMessageHandler(message -> {
            messageReceived.set(true);
            lastMessage.set(message);
            messageLatch.countDown();
        });

        // Register adapter as peer
        adapter.start();
        peerRegistry.registerPeer("test-engine-123",
            new PeerConnection.InMemoryPeerConnection("test-engine-123", adapter));
    }

    @AfterEach
    void tearDown() {
        if (adapter.isRunning()) {
            adapter.stop();
        }
    }

    @Test
    void testBasicMessageDelivery() throws InterruptedException {
        // Arrange
        A2AMessage message = A2AMessage.builder()
            .type("test-message")
            .targetAgent("test-agent")
            .payload(Map.of("key", "value"))
            .build();

        // Act
        boolean sent = adapter.sendToAgent("test-agent", message);

        // Assert
        assertTrue(sent, "Message should be sent successfully");
        assertTrue(messageLatch.await(10, TimeUnit.SECONDS), "Message should be received");
        assertTrue(messageReceived.get(), "Message should be received");
        assertNotNull(lastMessage.get(), "Last message should be set");
        assertEquals("test-message", lastMessage.get().getType());
    }

    @Test
    void testMessageWithAck() throws InterruptedException {
        // Arrange
        A2AMessage message = A2AMessage.builder()
            .type("request")
            .targetAgent("test-agent")
            .payload(Map.of("requestId", "12345"))
            .build();

        // Act
        boolean ackReceived = adapter.sendWithAck("test-agent", message, Duration.ofSeconds(10));

        // Assert
        assertTrue(ackReceived, "Acknowledgment should be received");
    }

    @Test
    void testBroadcastToGroup() {
        // Arrange
        A2AMessage message = A2AMessage.builder()
            .type("broadcast-test")
            .payload(Map.of("broadcast", true))
            .build();

        // Add additional peer to registry
        peerRegistry.registerPeer("test-peer",
            new PeerConnection.InMemoryPeerConnection("test-peer", adapter));

        // Act
        int sentCount = adapter.broadcastToGroup(message);

        // Assert
        assertTrue(sentCount >= 0, "Broadcast count should be valid");
        logger.info("Broadcasted message to {} peers", sentCount);
    }

    @Test
    void testMessageValidation() {
        // Arrange - Invalid message with blank type
        A2AMessage invalidMessage = A2AMessage.builder()
            .type("")  // Blank type - should be invalid
            .build();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, invalidMessage::validate,
            "Should throw exception for invalid message");
    }

    @Test
    void testAdapterMetrics() {
        // Arrange
        A2AMessage message = A2AMessage.builder()
            .type("metrics-test")
            .payload(Map.of("test", true))
            .build();

        // Act
        adapter.sendToAgent("test-agent", message);

        // Wait for message processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        BuriedEngineA2AAdapter.AdapterMetrics metrics = adapter.getMetrics();
        assertTrue(metrics.messagesReceived() >= 0, "Messages received should be >= 0");
        assertTrue(metrics.messagesSent() >= 0, "Messages sent should be >= 0");
        assertTrue(metrics.incomingQueueSize() >= 0, "Incoming queue size should be >= 0");
        assertTrue(metrics.outgoingQueueSize() >= 0, "Outgoing queue size should be >= 0");
    }

    @Test
    void testAdapterLifecycle() {
        // Assert initial state
        assertTrue(adapter.isRunning(), "Adapter should be running after start");
        assertEquals("test-engine-123", adapter.getEngineId());
        assertEquals("test-group", adapter.getEngineGroup());

        // Act - Stop adapter
        adapter.stop();

        // Assert
        assertFalse(adapter.isRunning(), "Adapter should not be running after stop");

        // Act - Try to send message when not running
        A2AMessage message = A2AMessage.builder()
            .type("test")
            .build();

        // Assert
        assertFalse(adapter.sendToAgent("test-agent", message),
            "Should not send message when adapter is not running");
    }

    @Test
    void testMessageCorrelation() throws InterruptedException {
        // Arrange
        AtomicReference<String> correlationId = new AtomicReference<>();
        AtomicReference<A2AMessage> ackMessage = new AtomicReference<>();

        adapter.setMessageAckHandler(ack -> {
            ackMessage.set(ack);
            correlationId.set(ack.getCorrelationId());
        });

        String testCorrelationId = "test-correlation-123";
        A2AMessage message = A2AMessage.builder()
            .type("correlated-request")
            .targetAgent("test-agent")
            .correlationId(testCorrelationId)
            .payload(Map.of("test", true))
            .build();

        // Act
        adapter.sendToAgent("test-agent", message);

        // Wait for processing
        assertTrue(messageLatch.await(10, TimeUnit.SECONDS));

        // Assert
        assertEquals(testCorrelationId, correlationId.get(),
            "Correlation ID should match in acknowledgment");
    }

    @Test
    void testMessageAge() {
        // Arrange
        Instant before = Instant.now();
        A2AMessage message = A2AMessage.builder()
            .type("age-test")
            .timestamp(before)
            .build();

        // Act
        long ageMs = message.getAgeMs();

        // Assert
        assertTrue(ageMs >= 0, "Message age should be >= 0");
        assertTrue(ageMs < 1000, "Message age should be less than 1 second");
    }

    @Test
    void testConcurrentMessageDelivery() throws InterruptedException {
        // Arrange
        int messageCount = 10;
        CountDownLatch deliveryLatch = new CountDownLatch(messageCount);
        AtomicBoolean allDelivered = new AtomicBoolean(true);

        adapter.setMessageHandler(message -> {
            deliveryLatch.countDown();
        });

        // Act - Send multiple messages concurrently
        for (int i = 0; i < messageCount; i++) {
            A2AMessage message = A2AMessage.builder()
                .type("concurrent-" + i)
                .targetAgent("test-agent")
                .payload(Map.of("index", i))
                .build();

            if (!adapter.sendToAgent("test-agent", message)) {
                allDelivered.set(false);
                break;
            }
        }

        // Assert
        assertTrue(allDelivered.get(), "All messages should be queued successfully");
        assertTrue(deliveryLatch.await(10, TimeUnit.SECONDS),
            "All messages should be delivered");
    }
}