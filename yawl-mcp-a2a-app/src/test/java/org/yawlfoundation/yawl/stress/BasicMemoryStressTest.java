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
 * Basic memory stress test implementation.
 *
 * @author YAWL Stress Testing Framework
 * @version 1.0
 */
public class BasicMemoryStressTest implements StressTest {

    @Override
    public String getTestName() {
        return "Basic Memory Stress Test";
    }

    @Override
    public void prepare() throws StressTestException {
        // Prepare the test environment
    }

    @Override
    public StressTestResult run() throws StressTestException {
        Instant start = Instant.now();
        List<MemoryDataPoint> dataPoints = new ArrayList<>();
        double peakMemoryUsage = 0;

        // Simulate memory usage patterns
        for (int i = 0; i < 100; i++) {
            // Allocate memory objects
            List<Object> memoryObjects = new ArrayList<>();
            for (int j = 0; j < 1000; j++) {
                byte[] data = new byte[ThreadLocalRandom.current().nextInt(1024, 10240)];
                memoryObjects.add(data);
            }

            // Calculate memory usage (in MB)
            double memoryUsage = calculateMemoryUsage() / (1024.0 * 1024.0);
            peakMemoryUsage = Math.max(peakMemoryUsage, memoryUsage);

            // Calculate leak rate (simulated)
            double leakRate = Math.random() * 0.5; // 0-0.5 MB/min

            // GC count (simulated)
            int gcCount = ThreadLocalRandom.current().nextInt(0, 10);

            dataPoints.add(new MemoryDataPoint(
                System.currentTimeMillis(),
                memoryUsage,
                leakRate,
                gcCount,
                gcCount * 10, // GC time
                i * 1000 // Operation count
            ));

            // Simulate some cleanup
            if (i % 10 == 0) {
                memoryObjects.clear();
                System.gc();
            }

            // Simulate occasional errors
            if (i % 25 == 0 && ThreadLocalRandom.current().nextDouble() < 0.2) {
                throw new StressTestException("Simulated out of memory error");
            }

            try {
                Thread.sleep(100); // 100ms between measurements
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new StressTestException("Test interrupted", e);
            }
        }

        Duration duration = Duration.between(start, Instant.now());

        return new MemoryTestResult(duration, true, 0, peakMemoryUsage, dataPoints);
    }

    private double calculateMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return usedMemory;
    }

    @Override
    public void cleanup() throws StressTestException {
        // Clean up after test
    }

    public static class MemoryTestResult extends StressTestResult {
        private final double peakMemoryUsage;
        private final List<MemoryDataPoint> memoryDataPoints;

        public MemoryTestResult(Duration duration, boolean success, int errorCount,
                              double peakMemoryUsage, List<MemoryDataPoint> memoryDataPoints) {
            super(duration, success, errorCount);
            this.peakMemoryUsage = peakMemoryUsage;
            this.memoryDataPoints = memoryDataPoints;
        }

        public double getPeakMemoryUsage() {
            return peakMemoryUsage;
        }

        public List<MemoryDataPoint> getMemoryDataPoints() {
            return memoryDataPoints;
        }
    }
}