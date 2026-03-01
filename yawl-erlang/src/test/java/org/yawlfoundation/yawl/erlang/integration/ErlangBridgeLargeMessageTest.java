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
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.error.OtpNodeUnavailableException;
import org.yawlfoundation.yawl.erlang.lifecycle.OtpInstallationVerifier;
import org.yawlfoundation.yawl.erlang.lifecycle.OtpNodeLifecycleManager;
import org.yawlfoundation.yawl.erlang.term.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for large binary message transfer via OTP bridge.
 * Verifies 1 MB and 10 MB roundtrip correctness and basic throughput.
 */
@Tag("integration")
class ErlangBridgeLargeMessageTest {

    private static final String NODE_NAME = "yawl_largemsg@127.0.0.1";
    private static final String COOKIE = "test_cookie";

    private static OtpNodeLifecycleManager lifecycle;
    private static ErlangNode node;

    @BeforeAll
    static void startOtp() throws OtpNodeUnavailableException, ErlangConnectionException {
        assumeTrue(OtpInstallationVerifier.isOtp28Available(), "OTP 28 not installed — skipping integration tests");
        lifecycle = OtpNodeLifecycleManager.forNode(NODE_NAME, COOKIE);
        lifecycle.start();
        node = new ErlangNode("yawl_java_lm@127.0.0.1");
        node.connect(NODE_NAME, COOKIE);
    }

    @AfterAll
    static void stopOtp() {
        if (node != null) node.close();
        if (lifecycle != null) lifecycle.close();
    }

    /**
     * Sends a 1 MB binary via yawl_echo:echo/1 and verifies roundtrip correctness.
     */
    @Test
    @Timeout(30)
    void largeMessage_1mb_roundtrip_correct() throws ErlangRpcException {
        int size = 1_048_576; // 1 MB
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 256);
        }

        ErlTerm result = node.rpc("yawl_echo", "echo", List.of(new ErlBinary(data)));

        assertInstanceOf(ErlBinary.class, result);
        byte[] returned = ((ErlBinary) result).data();
        assertEquals(size, returned.length);
        // Spot-check data integrity at 100 points
        for (int i = 0; i < 100; i++) {
            int idx = (i * size) / 100;
            assertEquals(data[idx], returned[idx], "Data mismatch at index " + idx);
        }
    }

    /**
     * Sends a 10 MB binary via yawl_echo:echo/1 and verifies roundtrip correctness.
     */
    @Test
    @Timeout(30)
    void largeMessage_10mb_roundtrip_correct() throws ErlangRpcException {
        int size = 10_485_760; // 10 MB
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ((i * 7) % 256);
        }

        ErlTerm result = node.rpc("yawl_echo", "echo", List.of(new ErlBinary(data)));

        assertInstanceOf(ErlBinary.class, result);
        byte[] returned = ((ErlBinary) result).data();
        assertEquals(size, returned.length);
        // Spot-check at 100 points
        for (int i = 0; i < 100; i++) {
            int idx = (i * size) / 100;
            assertEquals(data[idx], returned[idx], "Data mismatch at index " + idx);
        }
    }

    /**
     * Measures throughput for a 1 MB roundtrip. Asserts > 10 MB/s (conservative for CI).
     * If this test is flaky in CI, it should be disabled with @Disabled("throughput-sensitive").
     */
    @Test
    @Timeout(30)
    void largeMessage_1mb_throughput_above10mbps() throws ErlangRpcException {
        int size = 1_048_576; // 1 MB
        byte[] data = new byte[size];

        long startMs = System.currentTimeMillis();
        ErlTerm result = node.rpc("yawl_echo", "echo", List.of(new ErlBinary(data)));
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertInstanceOf(ErlBinary.class, result);

        // Total data transferred: send 1MB + receive 1MB = 2MB
        double throughputMbps = (2.0 * size / (1024.0 * 1024.0)) / (elapsedMs / 1000.0);
        assertTrue(throughputMbps > 10.0,
            "Throughput should exceed 10 MB/s, got " + String.format("%.1f", throughputMbps) + " MB/s");
    }
}
