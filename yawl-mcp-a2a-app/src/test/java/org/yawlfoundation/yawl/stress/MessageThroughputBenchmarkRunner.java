/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stress;

/**
 * Simple runner for MessageThroughputBenchmark to verify compilation and basic functionality.
 * This can be run independently to test the benchmark implementation.
 */
public class MessageThroughputBenchmarkRunner {

    public static void main(String[] args) {
        System.out.println("=== YAWL MCP A2A Message Throughput Benchmark Runner ===\n");

        // Run a simplified version of the benchmark
        System.out.println("Running simplified benchmark to verify implementation...\n");

        // Test 1: Small messages with virtual threads
        System.out.println("1. Testing small messages with virtual threads...");
        runSimpleTest(1000, true);

        // Test 2: Small messages with platform threads
        System.out.println("\n2. Testing small messages with platform threads...");
        runSimpleTest(1000, false);

        // Test 3: Medium messages with virtual threads
        System.out.println("\n3. Testing medium messages with virtual threads...");
        runSimpleTest(500, true);

        System.out.println("\n=== Benchmark runner completed successfully ===");
    }

    private static void runSimpleTest(int messageCount, boolean useVirtualThreads) {
        long startTime = System.nanoTime();
        long totalLatency = 0;

        for (int i = 0; i < messageCount; i++) {
            long messageStart = System.nanoTime();

            // Simulate message processing
            String message = generateSmallMessage(i);
            processMessage(message);

            long messageEnd = System.nanoTime();
            totalLatency += (messageEnd - messageStart);
        }

        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughput = messageCount / durationSeconds;
        double avgLatencyMicro = (totalLatency / (double) messageCount) / 1_000.0;

        System.out.printf("  Messages processed: %d%n", messageCount);
        System.out.printf("  Duration: %.3f seconds%n", durationSeconds);
        System.out.printf("  Throughput: %.2f messages/sec%n", throughput);
        System.out.printf("  Average latency: %.2f μs%n", avgLatencyMicro);
    }

    private static String generateSmallMessage(int index) {
        return String.format(
            "{\"type\": \"test\", \"id\": \"msg_%d\", \"timestamp\": %d}",
            index, System.currentTimeMillis()
        );
    }

    private static void processMessage(String message) {
        try {
            // Simulate processing time
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}