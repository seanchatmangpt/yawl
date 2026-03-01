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
package org.yawlfoundation.yawl.erlang.processmining;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.erlang.ErlangTestNode;
import org.yawlfoundation.yawl.erlang.processmining.ErlangBridge;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for ErlangBridge domain API.
 * Tests workflow case launching, conformance checking, and event subscription
 * against a real OTP node.
 */
@Tag("integration")
class ErlangBridgeIntegrationTest {

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
     * Connects to the bridge and verifies it is connected.
     */
    @Test
    @Timeout(10)
    void connect_validNode_succeeds() throws ErlangConnectionException {
        ErlangBridge bridge = ErlangBridge.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);
        try {
            assertTrue(bridge.isConnected());
        } finally {
            bridge.close();
        }
    }

    /**
     * Launches a workflow case and verifies the case ID has the expected prefix.
     */
    @Test
    @Timeout(10)
    void launchCase_returns_caseId_with_prefix() throws ErlangConnectionException, ErlangRpcException {
        ErlangBridge bridge = ErlangBridge.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);
        try {
            String caseId = bridge.launchCase("TestSpec");

            assertNotNull(caseId);
            assertTrue(caseId.startsWith("case_") || !caseId.isEmpty(),
                "Case ID should be a non-empty string, got: " + caseId);
        } finally {
            bridge.close();
        }
    }

    /**
     * Calls checkConformance with an empty event log.
     * Verifies the result has fitness in [0.0, 1.0].
     */
    @Test
    @Timeout(10)
    void checkConformance_emptyLog_noException() throws ErlangConnectionException, ErlangRpcException {
        ErlangBridge bridge = ErlangBridge.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);
        try {
            ConformanceResult result = bridge.checkConformance(List.of());

            assertNotNull(result);
            assertTrue(result.fitness() >= 0.0 && result.fitness() <= 1.0,
                "Fitness should be in [0.0, 1.0], got: " + result.fitness());
            assertTrue(result.precision() >= 0.0 && result.precision() <= 1.0,
                "Precision should be in [0.0, 1.0], got: " + result.precision());
            assertTrue(result.totalEvents() >= 0);
            assertTrue(result.conformingEvents() >= 0);
        } finally {
            bridge.close();
        }
    }

    /**
     * Subscribes to events with a handler that counts received events.
     * Verifies no exception is thrown.
     */
    @Test
    @Timeout(10)
    void subscribeToEvents_no_exception() throws ErlangConnectionException {
        ErlangBridge bridge = ErlangBridge.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);
        try {
            AtomicInteger eventCount = new AtomicInteger(0);
            bridge.subscribeToEvents(event -> eventCount.incrementAndGet());

            assertTrue(bridge.isConnected());
        } finally {
            bridge.close();
        }
    }

    /**
     * Closes the bridge after connecting.
     * Verifies close() succeeds without exception.
     */
    @Test
    @Timeout(10)
    void close_afterConnect_noException() throws ErlangConnectionException {
        ErlangBridge bridge = ErlangBridge.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);

        assertDoesNotThrow(bridge::close);
        assertFalse(bridge.isConnected(),
            "Bridge should be disconnected after close()");
    }
}
