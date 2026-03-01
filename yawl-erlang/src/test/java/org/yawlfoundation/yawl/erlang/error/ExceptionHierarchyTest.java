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
package org.yawlfoundation.yawl.erlang.error;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Erlang bridge exception hierarchy.
 * Chicago TDD: pure unit tests, no mocks, no I/O.
 */
@Tag("unit")
class ExceptionHierarchyTest {

    // -----------------------------------------------------------------------
    // Inheritance verification
    // -----------------------------------------------------------------------

    @Test
    void erlangConnectionException_extendsErlangException() {
        ErlangConnectionException ex = new ErlangConnectionException("foo@host", "refused");
        assertInstanceOf(ErlangException.class, ex);
    }

    @Test
    void erlangRpcException_extendsErlangException() {
        ErlangRpcException ex = new ErlangRpcException("erlang", "node", "noconnection");
        assertInstanceOf(ErlangException.class, ex);
    }

    @Test
    void erlangSendException_extendsErlangException() {
        ErlangSendException ex = new ErlangSendException("yawl_workflow", "not registered");
        assertInstanceOf(ErlangException.class, ex);
    }

    @Test
    void erlangReceiveException_extendsErlangException() {
        ErlangReceiveException ex = new ErlangReceiveException("malformed ETF at byte 42");
        assertInstanceOf(ErlangException.class, ex);
    }

    @Test
    void erlangException_extendsCheckedException() {
        ErlangException ex = new ErlangException("test");
        assertInstanceOf(Exception.class, ex);
        assertFalse(RuntimeException.class.isAssignableFrom(ErlangException.class),
            "ErlangException must be checked (not RuntimeException)");
    }

    // -----------------------------------------------------------------------
    // Message content — actionable context
    // -----------------------------------------------------------------------

    @Test
    void erlangConnectionException_messageContainsTargetNode() {
        String node = "foo@localhost";
        ErlangConnectionException ex = new ErlangConnectionException(node, "cookie mismatch");
        assertTrue(ex.getMessage().contains(node),
            "Message should contain target node: " + ex.getMessage());
    }

    @Test
    void erlangConnectionException_messageContainsReason() {
        ErlangConnectionException ex = new ErlangConnectionException("bar@host", "EPMD not running");
        assertTrue(ex.getMessage().contains("EPMD not running"),
            "Message should contain reason: " + ex.getMessage());
    }

    @Test
    void erlangRpcException_messageContainsModuleAndFunction() {
        ErlangRpcException ex = new ErlangRpcException("lists", "sort", "{badrpc,timeout}");
        String msg = ex.getMessage();
        assertTrue(msg.contains("lists"), "Message should contain module: " + msg);
        assertTrue(msg.contains("sort"), "Message should contain function: " + msg);
    }

    @Test
    void erlangRpcException_messageContainsReason() {
        ErlangRpcException ex = new ErlangRpcException("m", "f", "{badrpc,noconnection}");
        assertTrue(ex.getMessage().contains("noconnection"),
            "Message should contain badrpc reason: " + ex.getMessage());
    }

    @Test
    void erlangSendException_messageContainsTargetName() {
        ErlangSendException ex = new ErlangSendException("yawl_workflow", "process not registered");
        assertTrue(ex.getMessage().contains("yawl_workflow"),
            "Message should contain target name: " + ex.getMessage());
    }

    @Test
    void erlangReceiveException_messageContainsContext() {
        ErlangReceiveException ex = new ErlangReceiveException("ETF decode failed: invalid tag 0x00");
        assertTrue(ex.getMessage().contains("ETF"),
            "Message should contain diagnostic context: " + ex.getMessage());
    }

    // -----------------------------------------------------------------------
    // Cause chaining
    // -----------------------------------------------------------------------

    @Test
    void erlangConnectionException_causeChaining() {
        RuntimeException cause = new RuntimeException("ECONNREFUSED");
        ErlangConnectionException ex = new ErlangConnectionException("n@h", "refused", cause);
        assertSame(cause, ex.getCause(), "getCause() should return the wrapped exception");
    }

    @Test
    void erlangRpcException_causeChaining() {
        IllegalStateException cause = new IllegalStateException("decode failure");
        ErlangRpcException ex = new ErlangRpcException("m", "f", "bad", cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void erlangSendException_causeChaining() {
        RuntimeException cause = new RuntimeException("connection closed");
        ErlangSendException ex = new ErlangSendException("proc", "send failed", cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void erlangReceiveException_causeChaining() {
        IllegalArgumentException cause = new IllegalArgumentException("binary too large");
        ErlangReceiveException ex = new ErlangReceiveException("oversized binary", cause);
        assertSame(cause, ex.getCause());
    }

    // -----------------------------------------------------------------------
    // Diagnostic fields
    // -----------------------------------------------------------------------

    @Test
    void erlangConnectionException_getTargetNode() {
        ErlangConnectionException ex = new ErlangConnectionException("yawl@prod", "refused");
        assertEquals("yawl@prod", ex.getTargetNode());
    }

    @Test
    void erlangRpcException_getModule() {
        ErlangRpcException ex = new ErlangRpcException("yawl_workflow", "launch_case", "err");
        assertEquals("yawl_workflow", ex.getModule());
    }

    @Test
    void erlangRpcException_getFunction() {
        ErlangRpcException ex = new ErlangRpcException("yawl_workflow", "launch_case", "err");
        assertEquals("launch_case", ex.getFunction());
    }

    @Test
    void erlangSendException_getTargetName() {
        ErlangSendException ex = new ErlangSendException("yawl_event_relay", "not registered");
        assertEquals("yawl_event_relay", ex.getTargetName());
    }
}
