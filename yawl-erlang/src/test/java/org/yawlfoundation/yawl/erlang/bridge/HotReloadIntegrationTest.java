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
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.processmining.ErlangBridge;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for hot-reloading Erlang modules via the bridge.
 * Tests module purge and reload without stopping the OTP node.
 */
@Tag("integration")
class HotReloadIntegrationTest {

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
     * Connects to the bridge and reloads a standard library module.
     * Verifies no exception is thrown.
     */
    @Test
    @Timeout(10)
    void hotReload_yawlWorkflow_noException() throws ErlangConnectionException, ErlangRpcException {
        ErlangBridge bridge = ErlangBridge.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);
        try {
            assertTrue(bridge.isConnected());

            bridge.reloadModule("lists");

            assertTrue(bridge.isConnected(),
                "Bridge should still be connected after reload");
        } finally {
            bridge.close();
        }
    }

    /**
     * Tries to reload a non-existent module.
     * Verifies ErlangRpcException is thrown.
     */
    @Test
    @Timeout(10)
    void hotReload_nonExistentModule_throwsRpcException() throws ErlangConnectionException {
        ErlangBridge bridge = ErlangBridge.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);
        try {
            assertTrue(bridge.isConnected());

            assertThrows(ErlangRpcException.class, () ->
                bridge.reloadModule("nonexistent_module_xyz_12345")
            );
        } finally {
            bridge.close();
        }
    }

    /**
     * Reloads the same module twice in succession.
     * Verifies idempotent reload with no exception on second call.
     */
    @Test
    @Timeout(10)
    void hotReload_twice_sameModule_idempotent() throws ErlangConnectionException, ErlangRpcException {
        ErlangBridge bridge = ErlangBridge.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);
        try {
            assertTrue(bridge.isConnected());

            bridge.reloadModule("lists");

            bridge.reloadModule("lists");

            assertTrue(bridge.isConnected(),
                "Bridge should still be connected after two reloads");
        } finally {
            bridge.close();
        }
    }
}
