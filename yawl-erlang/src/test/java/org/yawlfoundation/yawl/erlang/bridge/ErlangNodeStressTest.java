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
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress tests for ErlangNode state machine concurrency.
 * Tests without connecting to OTP, focusing on thread safety of the node state machine.
 */
@Tag("stress")
class ErlangNodeStressTest {

    /**
     * Creates one node and calls close() from 10 concurrent virtual threads.
     * Verifies idempotent close behavior with no exceptions.
     */
    @Test
    @Timeout(30)
    void concurrent_close_10threads_no_exception() throws InterruptedException {
        ErlangNode node = new ErlangNode("stress@localhost");
        final int threadCount = 10;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    node.close();
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        assertEquals(0, exceptionCount.get(), "Concurrent close should not throw exceptions");
    }

    /**
     * Creates 10 separate nodes and closes all of them concurrently.
     * Verifies no exceptions during concurrent close operations.
     */
    @Test
    @Timeout(30)
    void connect_disconnect_10nodes_no_exception() throws InterruptedException {
        final int nodeCount = 10;
        final List<ErlangNode> nodes = new ArrayList<>(nodeCount);

        for (int i = 0; i < nodeCount; i++) {
            nodes.add(new ErlangNode("stress_" + i + "@localhost"));
        }

        final CountDownLatch latch = new CountDownLatch(nodeCount);
        final AtomicInteger exceptionCount = new AtomicInteger(0);

        for (ErlangNode node : nodes) {
            Thread.ofVirtual().start(() -> {
                try {
                    node.close();
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        assertEquals(0, exceptionCount.get());
    }

    /**
     * Creates one unconnected node and tries to RPC from 50 concurrent threads.
     * All should throw IllegalStateException (not connected).
     */
    @Test
    @Timeout(30)
    void rpc_before_connect_50threads_all_throw_state_exception() throws InterruptedException {
        ErlangNode node = new ErlangNode("stress@localhost");
        final int threadCount = 50;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final AtomicInteger stateExceptionCount = new AtomicInteger(0);
        final AtomicInteger otherExceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                try {
                    node.rpc("erlang", "node", List.of());
                    otherExceptionCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    stateExceptionCount.incrementAndGet();
                } catch (Exception e) {
                    otherExceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        startLatch.countDown();
        latch.await();

        assertEquals(threadCount, stateExceptionCount.get(),
            "All threads should throw IllegalStateException when not connected");
        assertEquals(0, otherExceptionCount.get());
    }

    /**
     * Creates one node and reads isConnected() from 100 concurrent threads.
     * All should return false (initial state).
     */
    @Test
    @Timeout(30)
    void isConnected_concurrent_100reads_consistent() throws InterruptedException {
        ErlangNode node = new ErlangNode("stress@localhost");
        final int threadCount = 100;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger falseCount = new AtomicInteger(0);
        final AtomicInteger trueCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    if (node.isConnected()) {
                        trueCount.incrementAndGet();
                    } else {
                        falseCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        assertEquals(threadCount, falseCount.get(),
            "All reads should return false for unconnected node");
        assertEquals(0, trueCount.get());
    }

    /**
     * Creates one node and calls close() from 20 concurrent virtual threads.
     * Verifies idempotent behavior under high concurrency.
     */
    @Test
    @Timeout(30)
    void close_idempotent_under_20concurrent_threads() throws InterruptedException {
        ErlangNode node = new ErlangNode("stress@localhost");
        final int threadCount = 20;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    node.close();
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        assertEquals(threadCount, successCount.get(),
            "All close calls should complete without exception");
    }
}
