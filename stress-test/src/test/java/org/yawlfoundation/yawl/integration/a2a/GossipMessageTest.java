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
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

/**
 * Unit tests for GossipMessage class.
 *
 * <p>These tests verify the functionality of the gossip message including
 * creation, validation, expiration checking, and propagation tracking.</p>
 */
class GossipMessageTest {

    private static final String TEST_AGENT_ID = "test-agent";
    private static final String TEST_MESSAGE_ID = "msg-123";
    private static final String TEST_MESSAGE_TYPE = "test-message";
    private static final String TEST_PAYLOAD = "Hello, World!";
    private static final long TEST_TTL = 5000; // 5 seconds

    @Test
    void testCreateGossipMessageWithOf() {
        // Test creating a message with explicit parameters
        GossipMessage<String> message = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );

        assertEquals(TEST_MESSAGE_ID, message.messageId());
        assertEquals(TEST_MESSAGE_TYPE, message.messageType());
        assertEquals(TEST_PAYLOAD, message.payload());
        assertEquals(TEST_AGENT_ID, message.originator());
        assertTrue(message.propagatedTo().isEmpty());
        assertEquals(TEST_TTL, message.ttl());
        assertNotNull(message.timestamp());
        assertFalse(message.isExpired());
    }

    @Test
    void testCreateGossipMessageWithType() {
        // Test creating a message with auto-generated ID
        GossipMessage<String> message = GossipMessage.ofType(
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );

        assertNotNull(message.messageId());
        assertFalse(message.messageId().isBlank());
        assertTrue(message.messageId().startsWith("msg-"));
        assertEquals(TEST_MESSAGE_TYPE, message.messageType());
        assertEquals(TEST_PAYLOAD, message.payload());
        assertEquals(TEST_AGENT_ID, message.originator());
        assertTrue(message.propagatedTo().isEmpty());
        assertEquals(TEST_TTL, message.ttl());
        assertNotNull(message.timestamp());
        assertFalse(message.isExpired());
    }

    @Test
    void testMessageValidation() {
        // Test valid message
        GossipMessage<String> validMessage = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );
        assertDoesNotThrow(validMessage::validate);

        // Test null message ID
        GossipMessage<String> invalidMessage1 = GossipMessage.of(
            null,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );
        assertThrows(IllegalArgumentException.class, invalidMessage1::validate);

        // Test blank message ID
        GossipMessage<String> invalidMessage2 = GossipMessage.of(
            "",
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );
        assertThrows(IllegalArgumentException.class, invalidMessage2::validate);

        // Test null message type
        GossipMessage<String> invalidMessage3 = GossipMessage.of(
            TEST_MESSAGE_ID,
            null,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );
        assertThrows(IllegalArgumentException.class, invalidMessage3::validate);

        // Test blank message type
        GossipMessage<String> invalidMessage4 = GossipMessage.of(
            TEST_MESSAGE_ID,
            "",
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );
        assertThrows(IllegalArgumentException.class, invalidMessage4::validate);

        // Test null payload
        GossipMessage<String> invalidMessage5 = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            null,
            TEST_AGENT_ID,
            TEST_TTL
        );
        assertThrows(IllegalArgumentException.class, invalidMessage5::validate);

        // Test null originator
        GossipMessage<String> invalidMessage6 = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            null,
            TEST_TTL
        );
        assertThrows(IllegalArgumentException.class, invalidMessage6::validate);

        // Test blank originator
        GossipMessage<String> invalidMessage7 = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            "",
            TEST_TTL
        );
        assertThrows(IllegalArgumentException.class, invalidMessage7::validate);

        // Test non-positive TTL
        GossipMessage<String> invalidMessage8 = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            0
        );
        assertThrows(IllegalArgumentException.class, invalidMessage8::validate);

        GossipMessage<String> invalidMessage9 = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            -1
        );
        assertThrows(IllegalArgumentException.class, invalidMessage9::validate);
    }

    @Test
    void testPropagationTracking() {
        GossipMessage<String> message = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );

        // Initially not propagated to any peer
        assertFalse(message.hasBeenPropagatedTo("peer1"));
        assertFalse(message.hasBeenPropagatedTo("peer2"));

        // Add propagation to peer1
        GossipMessage<String> messageWithPeer1 = message.withPropagationTo("peer1");
        assertFalse(message.hasBeenPropagatedTo("peer1")); // Original unchanged
        assertTrue(messageWithPeer1.hasBeenPropagatedTo("peer1"));
        assertFalse(messageWithPeer1.hasBeenPropagatedTo("peer2"));

        // Add propagation to peer2
        GossipMessage<String> messageWithPeer2 = messageWithPeer1.withPropagationTo("peer2");
        assertTrue(messageWithPeer2.hasBeenPropagatedTo("peer1"));
        assertTrue(messageWithPeer2.hasBeenPropagatedTo("peer2"));
        assertEquals(2, messageWithPeer2.propagatedTo().size());
    }

    @Test
    void testMessageExpiration() {
        // Create message with very short TTL
        long shortTtl = 100; // 100ms
        GossipMessage<String> message = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            shortTtl
        );

        // Initially not expired
        assertFalse(message.isExpired());

        // Wait for TTL to expire
        try {
            Thread.sleep(shortTtl + 50); // Wait a bit longer than TTL
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Now should be expired
        assertTrue(message.isExpired());
    }

    @Test
    void testGetAgeMs() {
        GossipMessage<String> message = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );

        long age1 = message.getAgeMs();
        assertTrue(age1 >= 0);

        // Wait a bit
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long age2 = message.getAgeMs();
        assertTrue(age2 > age1);
    }

    @Test
    void testImmutability() {
        GossipMessage<String> original = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );

        // Create modified version
        GossipMessage<String> modified = original.withPropagationTo("peer1");

        // Original should remain unchanged
        assertTrue(original.propagatedTo().isEmpty());
        assertEquals(TEST_MESSAGE_ID, original.messageId());

        // Modified should have new propagation set
        assertTrue(modified.propagatedTo().contains("peer1"));
        assertEquals(TEST_MESSAGE_ID, modified.messageId());
        assertEquals(TEST_PAYLOAD, modified.payload());
    }

    @Test
    void testWithEmptyPropagationSet() {
        GossipMessage<String> original = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );

        GossipMessage<String> modified = original.withPropagationTo("");

        // Even empty string should be in propagatedTo set
        assertTrue(modified.propagatedTo().contains(""));
        assertEquals(1, modified.propagatedTo().size());
    }

    @Test
    void testLargePropagationSet() {
        GossipMessage<String> original = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );

        // Add many peers to propagation set
        Set<String> manyPeers = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            manyPeers.add("peer-" + i);
            GossipMessage<String> current = original.withPropagationTo("peer-" + i);
            assertEquals(i + 1, current.propagatedTo().size());
        }

        // Verify the final state
        GossipMessage<String> finalMessage = original.withPropagationTo("peer-999");
        assertEquals(1000, finalMessage.propagatedTo().size());
    }

    @Test
    void testEqualityAndHashCode() {
        GossipMessage<String> message1 = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );

        GossipMessage<String> message2 = GossipMessage.of(
            TEST_MESSAGE_ID,
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );

        // Same messageId should make them equal
        assertEquals(message1, message2);
        assertEquals(message1.hashCode(), message2.hashCode());

        // Different messageId should make them different
        GossipMessage<String> message3 = GossipMessage.of(
            "different-id",
            TEST_MESSAGE_TYPE,
            TEST_PAYLOAD,
            TEST_AGENT_ID,
            TEST_TTL
        );
       .assertNotEquals(message1, message3);
    }
}