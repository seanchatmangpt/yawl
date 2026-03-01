/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.erlang.bridge;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.term.ErlAtom;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for ErlangNode state machine.
 *
 * Tests verify lifecycle correctness without any OTP connection:
 * - Constructor validation
 * - State transitions (unconnected -> connected -> closed)
 * - Pre-condition checks on RPC/send/receive
 * - Idempotency of close()
 * - No network or native library required
 *
 * All tests run in @Tag("unit") environment (no FFI, no network).
 */
@Tag("unit")
class ErlangNodeStateTest {

    // ===== Test 1: RPC before connect throws IllegalStateException =====

    @Test
    void testRpc_BeforeConnect_ThrowsIllegalStateException() {
        ErlangNode node = new ErlangNode("test@localhost");
        assertFalse(node.isConnected(), "New node should not be connected");

        assertThrows(IllegalStateException.class,
                () -> node.rpc("mod", "fun", List.of()),
                "rpc should throw IllegalStateException when not connected");
    }

    // ===== Test 2: send before connect throws IllegalStateException =====

    @Test
    void testSend_BeforeConnect_ThrowsIllegalStateException() {
        ErlangNode node = new ErlangNode("test@localhost");

        assertThrows(IllegalStateException.class,
                () -> node.send("some_process", new ErlAtom("message")),
                "send should throw IllegalStateException when not connected");
    }

    // ===== Test 3: receive before connect throws IllegalStateException =====

    @Test
    void testReceive_BeforeConnect_ThrowsIllegalStateException() {
        ErlangNode node = new ErlangNode("test@localhost");

        assertThrows(IllegalStateException.class,
                () -> node.receive(),
                "receive should throw IllegalStateException when not connected");
    }

    // ===== Test 4: close without connect doesn't throw =====

    @Test
    void testClose_WithoutConnect_NoException() {
        ErlangNode node = new ErlangNode("unconnected@localhost");
        assertDoesNotThrow(node::close,
                "close should not throw when node was never connected");
    }

    // ===== Test 5: isConnected initially returns false =====

    @Test
    void testIsConnected_Initial_False() {
        ErlangNode node = new ErlangNode("fresh@localhost");
        assertFalse(node.isConnected(),
                "Newly created node should report isConnected() = false");
    }

    // ===== Test 6: getLocalName preserves constructor argument =====

    @Test
    void testGetLocalName_Preserved() {
        String localName = "mynode@127.0.0.1";
        ErlangNode node = new ErlangNode(localName);
        assertEquals(localName, node.getLocalName(),
                "getLocalName should return the name passed to constructor");
    }

    // ===== Test 7: connect with null targetNode throws IllegalArgumentException =====

    @Test
    void testConnect_NullTargetNode_ThrowsIllegalArgumentException() {
        ErlangNode node = new ErlangNode("client@localhost");
        assertThrows(IllegalArgumentException.class,
                () -> node.connect(null, "cookie"),
                "connect should throw IllegalArgumentException for null targetNode");
    }

    // ===== Test 8: connect with blank cookie throws IllegalArgumentException =====

    @Test
    void testConnect_BlankCookie_ThrowsIllegalArgumentException() {
        ErlangNode node = new ErlangNode("client@localhost");
        assertThrows(IllegalArgumentException.class,
                () -> node.connect("erl@localhost", "   "),
                "connect should throw IllegalArgumentException for blank cookie");
    }

    // ===== Test 9: connect(host, port, cookie) with null host throws IllegalArgumentException =====

    @Test
    void testConnectHostPort_NullHost_ThrowsIllegalArgumentException() {
        ErlangNode node = new ErlangNode("client@localhost");
        assertThrows(IllegalArgumentException.class,
                () -> node.connect(null, 9000, "cookie"),
                "connect(host, port, cookie) should throw for null host");
    }

    // ===== Test 10: connect(host, port, cookie) with invalid port throws IllegalArgumentException =====

    @Test
    void testConnectHostPort_InvalidPort_0_ThrowsIllegalArgumentException() {
        ErlangNode node = new ErlangNode("client@localhost");
        assertThrows(IllegalArgumentException.class,
                () -> node.connect("localhost", 0, "cookie"),
                "connect(host, port, cookie) should throw for port 0");
    }

    @Test
    void testConnectHostPort_InvalidPort_65536_ThrowsIllegalArgumentException() {
        ErlangNode node = new ErlangNode("client@localhost");
        assertThrows(IllegalArgumentException.class,
                () -> node.connect("localhost", 65536, "cookie"),
                "connect(host, port, cookie) should throw for port > 65535");
    }

    // ===== Test 11: constructor with null name throws IllegalArgumentException =====

    @Test
    void testConstructor_NullName_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ErlangNode(null),
                "ErlangNode constructor should throw for null name");
    }

    // ===== Test 12: constructor with blank name throws IllegalArgumentException =====

    @Test
    void testConstructor_BlankName_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ErlangNode(""),
                "ErlangNode constructor should throw for empty name");

        assertThrows(IllegalArgumentException.class,
                () -> new ErlangNode("   "),
                "ErlangNode constructor should throw for whitespace-only name");
    }

    // ===== Test 13: close is idempotent (call twice) =====

    @Test
    void testClose_Idempotent_CallTwice() {
        ErlangNode node = new ErlangNode("temp@localhost");
        assertDoesNotThrow(node::close, "First close should not throw");
        assertDoesNotThrow(node::close, "Second close should not throw (idempotent)");
    }

    // ===== Test 14: rpc with null module throws IllegalArgumentException =====

    @Test
    void testRpc_NullModule_ThrowsIllegalArgumentException() {
        ErlangNode node = new ErlangNode("test@localhost");
        assertThrows(IllegalArgumentException.class,
                () -> node.rpc(null, "fn", List.of()),
                "rpc should throw IllegalArgumentException for null module");
    }

    // ===== Test 15: rpc with blank module throws IllegalArgumentException =====

    @Test
    void testRpc_BlankModule_ThrowsIllegalArgumentException() {
        ErlangNode node = new ErlangNode("test@localhost");
        assertThrows(IllegalArgumentException.class,
                () -> node.rpc("   ", "fn", List.of()),
                "rpc should throw IllegalArgumentException for blank module");
    }

    // ===== Test 16: rpc with null function throws IllegalArgumentException =====

    @Test
    void testRpc_NullFunction_ThrowsIllegalArgumentException() {
        ErlangNode node = new ErlangNode("test@localhost");
        assertThrows(IllegalArgumentException.class,
                () -> node.rpc("mod", null, List.of()),
                "rpc should throw IllegalArgumentException for null function");
    }

    // ===== Test 17: rpc with null args is converted to empty list =====

    @Test
    void testRpc_NullArgs_ConvertedToEmptyList() {
        ErlangNode node = new ErlangNode("test@localhost");
        // This should pass args validation, but fail on not-connected check
        assertThrows(IllegalStateException.class,
                () -> node.rpc("mod", "fun", null),
                "rpc with null args should check not-connected before args");
    }

    // ===== Test 18: send with null registeredName throws IllegalArgumentException =====

    @Test
    void testSend_NullRegisteredName_ThrowsIllegalArgumentException() {
        ErlangNode node = new ErlangNode("test@localhost");
        assertThrows(IllegalArgumentException.class,
                () -> node.send(null, new ErlAtom("msg")),
                "send should throw IllegalArgumentException for null registeredName");
    }

    // ===== Test 19: send with null message throws IllegalArgumentException =====

    @Test
    void testSend_NullMessage_ThrowsIllegalArgumentException() {
        ErlangNode node = new ErlangNode("test@localhost");
        assertThrows(IllegalArgumentException.class,
                () -> node.send("some_process", null),
                "send should throw IllegalArgumentException for null message");
    }

    // ===== Test 20: connect to libei-unavailable node throws ErlangConnectionException =====

    @Test
    void testConnect_LibeiNotAvailable_ThrowsErlangConnectionException() {
        ErlangNode node = new ErlangNode("client@localhost");
        // In test environment, libei.so is not loaded, so connect should fail
        assertThrows(ErlangConnectionException.class,
                () -> node.connect("erl@localhost", "cookie"),
                "connect should throw ErlangConnectionException when libei library is unavailable");
    }

    // ===== Test 21: connect(host, port, cookie) to libei-unavailable throws ErlangConnectionException =====

    @Test
    void testConnectHostPort_LibeiNotAvailable_ThrowsErlangConnectionException() {
        ErlangNode node = new ErlangNode("client@localhost");
        // In test environment, libei.so is not loaded
        assertThrows(ErlangConnectionException.class,
                () -> node.connect("localhost", 9000, "cookie"),
                "connect(host, port, cookie) should throw when libei library is unavailable");
    }

    // ===== Test 22: Multiple nodes with different names can coexist =====

    @Test
    void testMultipleNodes_DifferentNames_Coexist() {
        ErlangNode node1 = new ErlangNode("node1@localhost");
        ErlangNode node2 = new ErlangNode("node2@localhost");

        assertEquals("node1@localhost", node1.getLocalName());
        assertEquals("node2@localhost", node2.getLocalName());
        assertFalse(node1.isConnected());
        assertFalse(node2.isConnected());

        assertDoesNotThrow(() -> {
            node1.close();
            node2.close();
        });
    }

    // ===== Test 23: send with blank registeredName throws IllegalArgumentException =====

    @Test
    void testSend_BlankRegisteredName_ThrowsIllegalArgumentException() {
        ErlangNode node = new ErlangNode("test@localhost");
        assertThrows(IllegalArgumentException.class,
                () -> node.send("   ", new ErlAtom("msg")),
                "send should throw IllegalArgumentException for blank registeredName");
    }
}
