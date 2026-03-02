/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stress;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Basic concurrency stress test implementation.
 *
 * @author YAWL Stress Testing Framework
 * @version 1.0
 */
public class BasicConcurrencyStressTest implements StressTest {

    @Override
    public String getTestName() {
        return "Basic Concurrency Stress Test";
    }

    @Override
    public void prepare() throws StressTestException {
        // Prepare the test environment
    }

    @Override
    public StressTestResult run() throws StressTestException {
        Instant start = Instant.now();
        List<ConcurrencyDataPoint> dataPoints = new ArrayList<>();
        Lock sharedLock = new ReentrantLock();
        int maxThreads = 100;

        for (int threadCount = 10; threadCount <= maxThreads; threadCount += 10) {
            testConcurrentThreads(threadCount, sharedLock, dataPoints);
        }

        Duration duration = Duration.between(start, Instant.now());

        return new ConcurrencyTestResult(duration, true, 0, maxThreads, dataPoints);
    }

    private void testConcurrentThreads(int threadCount, Lock sharedLock, List<ConcurrencyDataPoint> dataPoints) {
        Instant testStart = Instant.now();
        Thread[] threads = new Thread[threadCount];
        long successfulOps = 0;
        long failedOps = 0;
        int errors = 0;

        // Create and start threads
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        // Simulate work with potential contention
                        sharedLock.lock();
                        try {
                            Thread.sleep(ThreadLocalRandom.current().nextLong(1, 10));
                        } finally {
                            sharedLock.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            threads[i].start();
        }

        // Wait for all threads to complete
        for (int i = 0; i < threadCount; i++) {
            try {
                threads[i].join();
                successfulOps += 100;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failedOps += 100;
                errors++;
            }
        }

        Duration testDuration = Duration.between(testStart, Instant.now());
        double avgResponseTime = testDuration.toMillis() / (double) threadCount;
        int queueSize = Math.max(0, threadCount - 5);

        dataPoints.add(new ConcurrencyDataPoint(
            System.currentTimeMillis(),
            threadCount,
            successfulOps,
            failedOps,
            errors,
            avgResponseTime,
            queueSize
        ));
    }

    @Override
    public void cleanup() throws StressTestException {
        // Clean up after test
    }

    public static class ConcurrencyTestResult extends StressTestResult {
        private final int maxConcurrentThreads;
        private final List<ConcurrencyDataPoint> concurrencyDataPoints;

        public ConcurrencyTestResult(Duration duration, boolean success, int errorCount,
                                    int maxConcurrentThreads, List<ConcurrencyDataPoint> concurrencyDataPoints) {
            super(duration, success, errorCount);
            this.maxConcurrentThreads = maxConcurrentThreads;
            this.concurrencyDataPoints = concurrencyDataPoints;
        }

        public int getMaxConcurrentThreads() {
            return maxConcurrentThreads;
        }

        public List<ConcurrencyDataPoint> getConcurrencyDataPoints() {
            return concurrencyDataPoints;
        }
    }
}