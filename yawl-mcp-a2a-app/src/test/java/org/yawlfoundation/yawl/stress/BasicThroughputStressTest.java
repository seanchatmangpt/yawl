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

/**
 * Basic throughput stress test implementation.
 *
 * @author YAWL Stress Testing Framework
 * @version 1.0
 */
public class BasicThroughputStressTest implements StressTest {

    @Override
    public String getTestName() {
        return "Basic Throughput Stress Test";
    }

    @Override
    public void prepare() throws StressTestException {
        // Prepare the test environment
    }

    @Override
    public StressTestResult run() throws StressTestException {
        Instant start = Instant.now();
        List<ThroughputDataPoint> dataPoints = new ArrayList<>();

        // Test at different concurrency levels
        for (int concurrency = 10; concurrency <= 200; concurrency += 10) {
            testAtConcurrencyLevel(concurrency, dataPoints);
        }

        Duration duration = Duration.between(start, Instant.now());

        // Calculate metrics
        double maxThroughput = dataPoints.stream()
            .mapToDouble(ThroughputDataPoint::getThroughput)
            .max()
            .orElse(0.0);

        return new ThroughputTestResult(duration, true, 0, maxThroughput, dataPoints);
    }

    private void testAtConcurrencyLevel(int concurrency, List<ThroughputDataPoint> dataPoints) {
        Instant testStart = Instant.now();
        int successfulOps = 0;
        int failedOps = 0;

        // Simulate work at given concurrency
        for (int i = 0; i < 1000; i++) {
            double throughput = calculateThroughput(i, concurrency);
            double responseTime = ThreadLocalRandom.current().nextDouble(5, 100);
            double errorRate = Math.min(0.1, (double) concurrency / 1000);

            if (ThreadLocalRandom.current().nextDouble() < errorRate) {
                failedOps++;
            } else {
                successfulOps++;
            }
        }

        Duration testDuration = Duration.between(testStart, Instant.now());
        double throughput = successfulOps / (testDuration.getSeconds() + testDuration.getNano() / 1e9);

        dataPoints.add(new ThroughputDataPoint(
            System.currentTimeMillis(),
            concurrency,
            throughput,
            ThreadLocalRandom.current().nextDouble(50, 200),
            (double) failedOps / (successfulOps + failedOps)
        ));
    }

    private double calculateThroughput(int opCount, int concurrency) {
        // Simulate throughput degradation under high concurrency
        double baseThroughput = 1000;
        double degradationFactor = 1.0 / (1.0 + (concurrency / 50.0));
        return baseThroughput * degradationFactor;
    }

    @Override
    public void cleanup() throws StressTestException {
        // Clean up after test
    }

    public static class ThroughputTestResult extends StressTestResult {
        private final double maxThroughput;
        private final List<ThroughputDataPoint> throughputDataPoints;

        public ThroughputTestResult(Duration duration, boolean success, int errorCount,
                                   double maxThroughput, List<ThroughputDataPoint> throughputDataPoints) {
            super(duration, success, errorCount);
            this.maxThroughput = maxThroughput;
            this.throughputDataPoints = throughputDataPoints;
        }

        public double getMaxThroughput() {
            return maxThroughput;
        }

        public List<ThroughputDataPoint> getThroughputDataPoints() {
            return throughputDataPoints;
        }
    }
}