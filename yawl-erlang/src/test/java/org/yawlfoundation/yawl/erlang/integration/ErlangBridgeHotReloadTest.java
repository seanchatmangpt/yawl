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
import org.yawlfoundation.yawl.erlang.processmining.ErlangBridge;
import org.yawlfoundation.yawl.erlang.term.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for hot-reloading Erlang modules via the bridge.
 * Loads v1 beam, verifies behavior, swaps to v2 beam, reloads, verifies new behavior.
 * All tests skip gracefully when OTP 28 is not installed.
 */
@Tag("integration")
class ErlangBridgeHotReloadTest {

    private static final String NODE_NAME = "yawl_hotreload@127.0.0.1";
    private static final String COOKIE = "test_cookie";

    private static OtpNodeLifecycleManager lifecycle;
    private static ErlangBridge bridge;

    @BeforeAll
    static void startOtp() throws OtpNodeUnavailableException, ErlangConnectionException {
        assumeTrue(OtpInstallationVerifier.isOtp28Available(), "OTP 28 not installed — skipping integration tests");
        lifecycle = OtpNodeLifecycleManager.forNode(NODE_NAME, COOKIE);
        lifecycle.start();
        bridge = ErlangBridge.connect(NODE_NAME, COOKIE);
    }

    @AfterAll
    static void stopOtp() {
        if (bridge != null) bridge.close();
        if (lifecycle != null) lifecycle.close();
    }

    /**
     * Load v1.beam as yawl_echo, verify {ok, Term}, then swap to v2.beam
     * and verify {v2, Term} after hot reload.
     */
    @Test
    @Timeout(30)
    void hotReload_v1_to_v2_changesEchoBehavior()
            throws ErlangRpcException, IOException, OtpNodeUnavailableException, ErlangConnectionException {
        Path ebinDir = lifecycle.getEbinPath();

        // Compile v1 and v2 beams (they must have been compiled and placed in resources)
        // For testing, we load the pre-compiled beams from test resources or check if erl is available
        // If beams are not pre-compiled, skip this test
        Path v1Beam = findBeam("yawl_echo_v1");
        Path v2Beam = findBeam("yawl_echo_v2");

        assumeTrue(v1Beam != null, "yawl_echo_v1.beam not found — skipping hot reload test");
        assumeTrue(v2Beam != null, "yawl_echo_v2.beam not found — skipping hot reload test");

        // Copy v1 beam as yawl_echo.beam to ebin path
        Path echoBeam = ebinDir.resolve("yawl_echo.beam");
        Files.copy(v1Beam, echoBeam, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Add ebin to code path and load v1
        ErlangNode node = new ErlangNode("yawl_java_hr@127.0.0.1");
        node.connect(NODE_NAME, COOKIE);
        try {
            node.rpc("code", "add_path", List.of(new ErlBinary(ebinDir.toString().getBytes())));
            node.rpc("code", "load_file", List.of(new ErlAtom("yawl_echo")));

            // Call echo — v1 returns {ok, Term}
            ErlTerm result1 = node.rpc("yawl_echo", "echo", List.of(ErlNil.INSTANCE));
            assertInstanceOf(ErlTuple.class, result1);
            ErlTuple tuple1 = (ErlTuple) result1;
            assertEquals(2, tuple1.elements().size());
            assertEquals("ok", ((ErlAtom) tuple1.elements().get(0)).value());

            // Swap to v2 beam
            Files.copy(v2Beam, echoBeam, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Hot reload
            bridge.reloadModule("yawl_echo");

            // Call echo — v2 returns {v2, Term}
            ErlTerm result2 = node.rpc("yawl_echo", "echo", List.of(ErlNil.INSTANCE));
            assertInstanceOf(ErlTuple.class, result2);
            ErlTuple tuple2 = (ErlTuple) result2;
            assertEquals(2, tuple2.elements().size());
            assertEquals("v2", ((ErlAtom) tuple2.elements().get(0)).value());
        } finally {
            node.close();
        }
    }

    private Path findBeam(String moduleName) {
        // Search in test resources and target directories
        for (String candidate : new String[]{
            "yawl-erlang/target/test-classes/" + moduleName + ".beam",
            "yawl-erlang/target/classes/" + moduleName + ".beam",
            "yawl-erlang/src/test/resources/" + moduleName + ".beam",
        }) {
            Path p = Path.of(candidate);
            if (Files.exists(p)) {
                return p;
            }
        }
        return null;
    }
}
