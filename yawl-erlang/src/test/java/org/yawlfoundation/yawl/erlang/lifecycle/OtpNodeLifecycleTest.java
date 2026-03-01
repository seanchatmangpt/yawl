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
package org.yawlfoundation.yawl.erlang.lifecycle;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.erlang.error.OtpNodeUnavailableException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for OtpNodeLifecycleManager.
 * All tests skip gracefully when OTP 28 is not installed.
 */
@Tag("integration")
class OtpNodeLifecycleTest {

    private static final String NODE_NAME = "yawl_lifecycle_test@127.0.0.1";
    private static final String COOKIE = "test_cookie";

    private OtpNodeLifecycleManager lifecycle;

    @BeforeEach
    void checkOtp() {
        assumeTrue(OtpInstallationVerifier.isOtp28Available(),
            "OTP 28 not installed — skipping integration tests");
    }

    @AfterEach
    void cleanup() {
        if (lifecycle != null) {
            lifecycle.close();
        }
    }

    /**
     * Starts a node and verifies it appears in EPMD.
     */
    @Test
    @Timeout(30)
    void start_validOtp_nodeRegistersInEpmd() throws OtpNodeUnavailableException {
        lifecycle = OtpNodeLifecycleManager.forNode(NODE_NAME, COOKIE);
        lifecycle.start();

        assertTrue(lifecycle.isAlive(), "Node should be alive after start");
    }

    /**
     * Verifies isAlive() returns true immediately after start.
     */
    @Test
    @Timeout(30)
    void isAlive_afterStart_true() throws OtpNodeUnavailableException {
        lifecycle = OtpNodeLifecycleManager.forNode(NODE_NAME, COOKIE);
        lifecycle.start();

        assertTrue(lifecycle.isAlive());
    }

    /**
     * Verifies isAlive() returns false after shutdown.
     */
    @Test
    @Timeout(30)
    void isAlive_afterShutdown_false() throws OtpNodeUnavailableException, InterruptedException {
        lifecycle = OtpNodeLifecycleManager.forNode(NODE_NAME, COOKIE);
        lifecycle.start();
        assertTrue(lifecycle.isAlive());

        lifecycle.shutdown();

        assertFalse(lifecycle.isAlive(), "Node should not be alive after shutdown");
    }

    /**
     * Kills the node process mid-flight and verifies restart() recovers.
     */
    @Test
    @Timeout(60)
    void restart_nodeRecoversAfterKill() throws OtpNodeUnavailableException, InterruptedException {
        lifecycle = OtpNodeLifecycleManager.forNode(NODE_NAME, COOKIE);
        lifecycle.start();
        assertTrue(lifecycle.isAlive());

        // Kill the process forcibly
        Process p = lifecycle.getProcess();
        assertNotNull(p);
        p.destroyForcibly();
        p.waitFor(5, TimeUnit.SECONDS);

        assertFalse(lifecycle.isAlive(), "Node should be dead after forcible kill");

        // Restart should recover
        lifecycle.restart();
        assertTrue(lifecycle.isAlive(), "Node should be alive after restart");
    }

    /**
     * Verifies the watchdog detects node death and triggers a restart.
     */
    @Test
    @Timeout(60)
    void watchdog_triggersRestartOnNodeDeath() throws OtpNodeUnavailableException, InterruptedException {
        lifecycle = OtpNodeLifecycleManager.forNode(NODE_NAME, COOKIE);
        lifecycle.start();

        CountDownLatch restartLatch = new CountDownLatch(1);
        AtomicReference<NodeRestartEvent> capturedEvent = new AtomicReference<>();

        lifecycle.setRestartListener(event -> {
            capturedEvent.set(event);
            restartLatch.countDown();
        });

        lifecycle.startWatchdog();

        // Kill the process forcibly to trigger watchdog
        Process p = lifecycle.getProcess();
        assertNotNull(p);
        p.destroyForcibly();
        p.waitFor(5, TimeUnit.SECONDS);

        // Wait for watchdog to detect and restart (watchdog polls every 5s)
        boolean restarted = restartLatch.await(30, TimeUnit.SECONDS);
        assertTrue(restarted, "Watchdog should have triggered restart within 30s");

        NodeRestartEvent event = capturedEvent.get();
        assertNotNull(event);
        assertEquals(NODE_NAME, event.nodeName());
        assertEquals(1, event.restartCount());
        assertTrue(lifecycle.isAlive(), "Node should be alive after watchdog restart");
    }
}
