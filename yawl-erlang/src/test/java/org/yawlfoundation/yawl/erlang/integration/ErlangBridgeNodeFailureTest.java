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
package org.yawlfoundation.yawl.erlang.integration;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.erlang.bridge.ErlangNode;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.error.OtpNodeUnavailableException;
import org.yawlfoundation.yawl.erlang.lifecycle.OtpInstallationVerifier;
import org.yawlfoundation.yawl.erlang.lifecycle.OtpNodeLifecycleManager;
import org.yawlfoundation.yawl.erlang.term.*;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests verifying graceful handling of OTP node failures.
 * Tests that Java throws ErlangConnectionException (not hang) when the node dies,
 * and that the node can be restarted.
 */
@Tag("integration")
class ErlangBridgeNodeFailureTest {

    private static final String NODE_NAME = "yawl_failure@127.0.0.1";
    private static final String COOKIE = "test_cookie";

    private OtpNodeLifecycleManager lifecycle;
    private ErlangNode node;

    @BeforeEach
    void checkOtp() {
        assumeTrue(OtpInstallationVerifier.isOtp28Available(), "OTP 28 not installed — skipping integration tests");
    }

    @AfterEach
    void cleanup() {
        if (node != null) node.close();
        if (lifecycle != null) lifecycle.close();
    }

    /**
     * Kills the OTP node mid-session and verifies Java throws ErlangConnectionException.
     * Then restarts the node and verifies 10 more successful calls.
     */
    @Test
    @Timeout(60)
    void nodeKill_throwsConnectionException_restartRecovers()
            throws OtpNodeUnavailableException, ErlangConnectionException, InterruptedException, ErlangRpcException {
        lifecycle = OtpNodeLifecycleManager.forNode(NODE_NAME, COOKIE);
        lifecycle.start();

        node = new ErlangNode("yawl_java_fail@127.0.0.1");
        node.connect(NODE_NAME, COOKIE);

        // Send 10 messages successfully before kill
        for (int i = 0; i < 10; i++) {
            ErlTerm result = node.rpc("yawl_echo", "echo", List.of(new ErlInteger(i)));
            assertInstanceOf(ErlInteger.class, result);
        }

        // Kill the OTP node forcibly
        Process p = lifecycle.getProcess();
        assertNotNull(p, "Lifecycle process must be non-null");
        p.destroyForcibly();
        p.waitFor(5, TimeUnit.SECONDS);

        // Close the existing node connection (it's dead)
        node.close();

        // Next connect or RPC should throw ErlangConnectionException
        ErlangNode deadNode = new ErlangNode("yawl_java_dead@127.0.0.1");
        assertThrows(ErlangConnectionException.class,
            () -> deadNode.connect(NODE_NAME, COOKIE),
            "Connecting to dead node must throw ErlangConnectionException");
        deadNode.close();

        // Restart the node
        lifecycle.restart();
        assertTrue(lifecycle.isAlive(), "Node must be alive after restart");

        // Create a new connection and verify 10 more successful calls
        node = new ErlangNode("yawl_java_recovered@127.0.0.1");
        node.connect(NODE_NAME, COOKIE);

        for (int i = 0; i < 10; i++) {
            ErlTerm result = node.rpc("yawl_echo", "echo", List.of(new ErlInteger(i)));
            assertInstanceOf(ErlInteger.class, result);
            assertEquals(BigInteger.valueOf(i), ((ErlInteger) result).value());
        }
    }
}
