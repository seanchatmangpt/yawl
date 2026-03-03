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
 * Basic latency stress test implementation.
 *
 * @author YAWL Stress Testing Framework
 * @version 1.0
 */
public class BasicLatencyStressTest implements StressTest {

    @Override
    public String getTestName() {
        return "Basic Latency Stress Test";
    }

    @Override
    public void prepare() throws StressTestException {
        // Prepare the test environment
    }

    @Override
    public StressTestResult run() throws StressTestException {
        Instant start = Instant.now();

        // Simulate test execution with various latency measurements
        List<Double> responseTimes = new ArrayList<>();
        int operations = 1000;

        for (int i = 0; i < operations; i++) {
            // Simulate work with variable latency
            double baseLatency = 10 + ThreadLocalRandom.current().nextDouble(90); // 10-100ms
            double actualLatency = baseLatency + ThreadLocalRandom.current().nextDouble(20); // Add noise

            try {
                Thread.sleep((long) actualLatency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new StressTestException("Test interrupted", e);
            }

            responseTimes.add(actualLatency);

            // Simulate occasional errors
            if (i % 100 == 0 && ThreadLocalRandom.current().nextDouble() < 0.1) {
                throw new StressTestException("Simulated error during test execution");
            }
        }

        Duration duration = Duration.between(start, Instant.now());

        // Calculate metrics
        double minResponseTime = responseTimes.stream().min(Double::compare).orElse(0.0);
        double maxResponseTime = responseTimes.stream().max(Double::compare).orElse(0.0);
        double avgResponseTime = responseTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        // Calculate P95
        List<Double> sorted = new ArrayList<>(responseTimes);
        sorted.sort(Double::compare);
        int p95Index = (int) (sorted.size() * 0.95);
        double p95ResponseTime = sorted.get(p95Index);

        return new LatencyTestResult(duration, true, 0, minResponseTime, maxResponseTime, avgResponseTime, p95ResponseTime);
    }

    @Override
    public void cleanup() throws StressTestException {
        // Clean up after test
    }

    public static class LatencyTestResult extends StressTestResult {
        private final double minResponseTime;
        private final double maxResponseTime;
        private final double avgResponseTime;
        private final double p95ResponseTime;

        public LatencyTestResult(Duration duration, boolean success, int errorCount,
                                 double minResponseTime, double maxResponseTime,
                                 double avgResponseTime, double p95ResponseTime) {
            super(duration, success, errorCount);
            this.minResponseTime = minResponseTime;
            this.maxResponseTime = maxResponseTime;
            this.avgResponseTime = avgResponseTime;
            this.p95ResponseTime = p95ResponseTime;
        }

        public double getMinResponseTime() {
            return minResponseTime;
        }

        public double getMaxResponseTime() {
            return maxResponseTime;
        }

        public double getAvgResponseTime() {
            return avgResponseTime;
        }

        public double getP95ResponseTime() {
            return p95ResponseTime;
        }
    }
}