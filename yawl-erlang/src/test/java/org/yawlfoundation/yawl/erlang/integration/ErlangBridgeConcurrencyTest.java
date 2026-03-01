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
import org.yawlfoundation.yawl.erlang.bridge.ErlangNodePool;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.error.OtpNodeUnavailableException;
import org.yawlfoundation.yawl.erlang.lifecycle.OtpInstallationVerifier;
import org.yawlfoundation.yawl.erlang.lifecycle.OtpNodeLifecycleManager;
import org.yawlfoundation.yawl.erlang.term.*;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Concurrency integration test: 1,000 virtual threads each send a unique integer
 * via yawl_echo:echo/1 and verify their own integer is returned.
 * Verifies no data corruption under concurrent OTP node access.
 */
@Tag("integration")
class ErlangBridgeConcurrencyTest {

    private static final String NODE_NAME = "yawl_concurrency@127.0.0.1";
    private static final String COOKIE = "test_cookie";
    private static final int THREAD_COUNT = 1_000;

    private static final int POOL_SIZE = 20;

    private static OtpNodeLifecycleManager lifecycle;
    private static ErlangNodePool pool;

    @BeforeAll
    static void startOtp() throws OtpNodeUnavailableException, ErlangConnectionException {
        assumeTrue(OtpInstallationVerifier.isOtp28Available(), "OTP 28 not installed — skipping integration tests");
        lifecycle = OtpNodeLifecycleManager.forNode(NODE_NAME, COOKIE);
        lifecycle.start();
        pool = new ErlangNodePool("yawl_java_cc@127.0.0.1", NODE_NAME, COOKIE, POOL_SIZE, POOL_SIZE);
        pool.initialise();
    }

    @AfterAll
    static void stopOtp() {
        if (pool != null) pool.close();
        if (lifecycle != null) lifecycle.close();
    }

    /**
     * 1,000 virtual threads each call yawl_echo:echo(unique_integer) concurrently.
     * Verifies each thread receives its own integer back without corruption.
     */
    @Test
    @Timeout(60)
    void concurrent_1000threads_each_echoes_unique_integer() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            Thread.ofVirtual().start(() -> {
                try {
                    ErlangNode node = pool.acquire();
                    try {
                        ErlTerm result = node.rpc("yawl_echo", "echo",
                            List.of(new ErlInteger(threadId)));
                        if (!(result instanceof ErlInteger ei)
                                || !ei.value().equals(BigInteger.valueOf(threadId))) {
                            failureCount.incrementAndGet();
                        }
                    } finally {
                        pool.release(node);
                    }
                } catch (ErlangRpcException | ErlangConnectionException | InterruptedException e) {
                    failureCount.incrementAndGet();
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        assertEquals(0, failureCount.get(),
            "All " + THREAD_COUNT + " concurrent echo calls must succeed");
    }
}
