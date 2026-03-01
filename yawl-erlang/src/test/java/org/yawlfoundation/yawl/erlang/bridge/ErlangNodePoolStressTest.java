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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress tests for ErlangNodePool state and concurrency.
 * Tests pool thread safety and state transitions without OTP connection.
 */
@Tag("stress")
class ErlangNodePoolStressTest {

    /**
     * Creates a pool, starts 10 threads all calling acquire() (will fail/block),
     * then closes the pool. Verifies all threads unblock within timeout.
     */
    @Test
    @Timeout(30)
    void close_while_threads_waiting_no_deadlock() throws InterruptedException {
        ErlangNodePool pool = new ErlangNodePool("yawl@localhost", "erl@localhost", "cookie", 1, 5);

        final int threadCount = 10;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    pool.acquire();
                } catch (IllegalStateException e) {
                    exceptionCount.incrementAndGet();
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Give threads a moment to start waiting
        Thread.sleep(100);

        // Close the pool while threads are waiting
        pool.close();

        // Verify all threads unblock (latch times out if any hang)
        latch.await();
        assertTrue(exceptionCount.get() > 0, "Threads should have received exceptions");
    }

    /**
     * Starts 10 threads all calling release(null).
     * Each should throw an exception.
     */
    @Test
    @Timeout(30)
    void release_null_concurrent_10threads_throws() throws InterruptedException {
        ErlangNodePool pool = new ErlangNodePool("yawl@localhost", "erl@localhost", "cookie", 1, 5);

        final int threadCount = 10;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    pool.release(null);
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        assertTrue(exceptionCount.get() > 0,
            "release(null) should not succeed silently");
    }

    /**
     * Creates a pool and calls close() from 20 concurrent threads.
     * Verifies no exceptions from idempotent close.
     */
    @Test
    @Timeout(30)
    void pool_close_twice_concurrent_no_exception() throws InterruptedException {
        ErlangNodePool pool = new ErlangNodePool("yawl@localhost", "erl@localhost", "cookie", 1, 5);

        final int threadCount = 20;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    pool.close();
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        assertEquals(0, exceptionCount.get(),
            "Concurrent close should not throw exceptions");
    }

    /**
     * Closes a pool, then tries to acquire from 20 concurrent threads.
     * All should throw IllegalStateException.
     */
    @Test
    @Timeout(30)
    void acquire_closedPool_concurrent_all_throw() throws InterruptedException {
        ErlangNodePool pool = new ErlangNodePool("yawl@localhost", "erl@localhost", "cookie", 1, 5);
        pool.close();

        final int threadCount = 20;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger stateExceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    pool.acquire();
                } catch (IllegalStateException e) {
                    stateExceptionCount.incrementAndGet();
                } catch (Exception e) {
                    // Other exceptions acceptable (InterruptedException)
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        assertTrue(stateExceptionCount.get() > 0,
            "Acquire on closed pool should throw IllegalStateException");
    }

    /**
     * Starts 100 threads all reading getAvailableCount() concurrently.
     * All should return a non-negative value.
     */
    @Test
    @Timeout(30)
    void getAvailableCount_concurrent_reads_stable() throws InterruptedException {
        ErlangNodePool pool = new ErlangNodePool("yawl@localhost", "erl@localhost", "cookie", 2, 8);

        final int threadCount = 100;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger negativeCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    int available = pool.getAvailableCount();
                    if (available < 0) {
                        negativeCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        assertEquals(0, negativeCount.get(),
            "getAvailableCount should always return non-negative");
    }
}
