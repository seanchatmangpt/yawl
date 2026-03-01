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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.erlang.ErlangTestNode;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.term.ErlAtom;
import org.yawlfoundation.yawl.erlang.term.ErlTerm;
import org.yawlfoundation.yawl.erlang.term.ErlTuple;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for ErlangNode connecting to a real OTP node.
 * Requires OTP 28 to be installed; tests are skipped gracefully if absent.
 */
@Tag("integration")
class ErlangNodeIntegrationTest {

    private static ErlangTestNode testNode;

    @BeforeAll
    static void startOtp() throws IOException, InterruptedException {
        assumeTrue(ErlangTestNode.isOtpAvailable(), "OTP 28 not installed — skipping integration tests");
        testNode = ErlangTestNode.start();
        testNode.awaitReady();
    }

    @AfterAll
    static void stopOtp() {
        if (testNode != null) {
            testNode.close();
        }
    }

    /**
     * Connects to the test Erlang node with the correct cookie.
     * Verifies the connection is established.
     */
    @Test
    @Timeout(10)
    void connect_validCookie_succeeds() throws ErlangConnectionException {
        ErlangNode node = new ErlangNode("yawl_java@localhost");
        try {
            node.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);
            assertTrue(node.isConnected());
        } finally {
            node.close();
        }
    }

    /**
     * Tries to connect with the wrong cookie.
     * Verifies connection fails with ErlangConnectionException.
     */
    @Test
    @Timeout(10)
    void connect_wrongCookie_throwsConnectionException() {
        ErlangNode node = new ErlangNode("yawl_java@localhost");
        try {
            assertThrows(ErlangConnectionException.class, () ->
                node.connect(ErlangTestNode.NODE_NAME, "wrong_cookie")
            );
            assertFalse(node.isConnected());
        } finally {
            node.close();
        }
    }

    /**
     * Connects and calls erlang:node/0, verifying the result is an atom
     * matching the test node name prefix.
     */
    @Test
    @Timeout(10)
    void rpc_erlang_node_returns_atom() throws ErlangConnectionException, Exception {
        ErlangNode node = new ErlangNode("yawl_java@localhost");
        try {
            node.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);

            ErlTerm result = node.rpc("erlang", "node", List.of());

            assertInstanceOf(ErlAtom.class, result);
            ErlAtom nodeAtom = (ErlAtom) result;
            assertTrue(nodeAtom.value().startsWith("yawl_test"),
                "Node name should start with 'yawl_test', got: " + nodeAtom.value());
        } finally {
            node.close();
        }
    }

    /**
     * Connects and calls erlang:date/0, verifying the result is a 3-tuple.
     */
    @Test
    @Timeout(10)
    void rpc_erlang_date_returns_3tuple() throws ErlangConnectionException, Exception {
        ErlangNode node = new ErlangNode("yawl_java@localhost");
        try {
            node.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);

            ErlTerm result = node.rpc("erlang", "date", List.of());

            assertInstanceOf(ErlTuple.class, result);
            ErlTuple dateTuple = (ErlTuple) result;
            assertEquals(3, dateTuple.elements().size(),
                "Date tuple should have 3 elements (year, month, day)");
        } finally {
            node.close();
        }
    }

    /**
     * Connects and waits 5 seconds, then verifies the connection is still alive.
     * Tests the keepalive tick mechanism.
     */
    @Test
    @Timeout(15)
    void send_and_receive_tick_keepalive() throws ErlangConnectionException, InterruptedException {
        ErlangNode node = new ErlangNode("yawl_java@localhost");
        try {
            node.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);
            assertTrue(node.isConnected());

            Thread.sleep(5000);

            assertTrue(node.isConnected(),
                "Node should remain connected after 5 seconds (keepalive tick)");
        } finally {
            node.close();
        }
    }
}
