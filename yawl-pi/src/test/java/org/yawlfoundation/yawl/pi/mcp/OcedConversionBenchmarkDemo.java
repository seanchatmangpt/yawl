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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.pi.mcp;

import java.util.Random;

/**
 * Demo version of OCEL conversion benchmark showing the structure.
 *
 * This version simulates the benchmark behavior without requiring the
 * actual YAWL dependencies, making it easy to demonstrate the benchmark
 * framework and measurement approach.
 *
 * @since YAWL 6.0
 */
public class OcedConversionBenchmarkDemo {

    // Test data generators (same as real version)
    private String smallCsvData;
    private String mediumCsvData;
    private String largeCsvData;

    @SuppressWarnings("unused")
    private void setup() {
        // Initialize test data
        smallCsvData = generateCsvData(100);
        mediumCsvData = generateCsvData(1000);
        largeCsvData = generateCsvData(10000);
    }

    // ============================================================================
    // Benchmark Methods (Simulated)
    // ============================================================================

    /**
     * Simulated OCEL conversion benchmark for small CSV dataset
     */
    public void benchmarkOcelConversion_Small_Csv() {
        try {
            // Simulate processing delay based on data size
            int processingTime = simulateConversion(100);
            Thread.sleep(processingTime);

            // Validate result
            validateConversionResult(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simulated OCEL conversion benchmark for medium CSV dataset
     */
    public void benchmarkOcelConversion_Medium_Csv() {
        try {
            int processingTime = simulateConversion(1000);
            Thread.sleep(processingTime);
            validateConversionResult(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simulated OCEL conversion benchmark for large CSV dataset
     */
    public void benchmarkOcelConversion_Large_Csv() {
        try {
            int processingTime = simulateConversion(10000);
            Thread.sleep(processingTime);
            validateConversionResult(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simulated schema inference benchmark
     */
    public void benchmarkSchemaInference() {
        try {
            // Simulate schema inference time
            Thread.sleep(15); // 15ms for small dataset
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simulated format detection benchmark
     */
    public void benchmarkFormatDetection() {
        try {
            // Simulate format detection
            Thread.sleep(5); // 5ms for detection
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simulated memory usage benchmark
     */
    public void benchmarkMemoryUsage() {
        try {
            // Simulate memory allocation
            String testData = generateCsvData(1000);
            processTestData(testData);
            System.gc();
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private int simulateConversion(int eventCount) {
        // Simulate processing time scaling with data size
        // Base: 10ms for small data, plus 0.1ms per event
        return 10 + (int) (eventCount * 0.1);
    }

    private void validateConversionResult(int eventCount) {
        // Simulate validation work
        Random random = new Random();
        for (int i = 0; i < eventCount / 100; i++) {
            if (random.nextDouble() < 0.001) { // 0.1% chance of "validation"
                throw new RuntimeException("Validation error on event " + i);
            }
        }
    }

    private void processTestData(String data) {
        // Simulate data processing
        byte[] bytes = data.getBytes();
        for (int i = 0; i < bytes.length; i += 1024) {
            int chunk = Math.min(1024, bytes.length - i);
            byte[] buffer = new byte[chunk];
            System.arraycopy(bytes, i, buffer, 0, chunk);
        }
    }

    // ============================================================================
    // Data Generation Methods (Same as Real Version)
    // ============================================================================

    private String generateCsvData(int eventCount) {
        StringBuilder csv = new StringBuilder();
        csv.append("case_id,activity,timestamp,resource,amount,status,customer_id\n");

        Random random = new Random(42);

        for (int i = 1; i <= eventCount; i++) {
            String caseId = "case-" + (i % 50 == 0 ? i/50 : i/50 + 1);
            String activity = getRandomActivity(random);
            String timestamp = generateTimestamp(random, i);
            String resource = getRandomResource(random);
            double amount = random.nextDouble() * 10000;
            String status = getRandomStatus(random);
            String customerId = "cust-" + (random.nextInt(1000) + 1);

            csv.append(String.format("%s,%s,%s,%s,%.2f,%s,%s\n",
                caseId, activity, timestamp, resource, amount, status, customerId));
        }

        return csv.toString();
    }

    private String getRandomActivity(Random random) {
        String[] activities = {
            "Login", "Approve", "Reject", "Review", "Submit",
            "Verify", "Process", "Complete", "Update", "Cancel"
        };
        return activities[random.nextInt(activities.length)];
    }

    private String generateTimestamp(Random random, int eventNumber) {
        long baseTime = 1704067200000L; // 2024-01-01T00:00:00Z
        long timestamp = baseTime + (eventNumber * 60000L) + (random.nextInt(30000) - 15000);
        return java.time.Instant.ofEpochMilli(timestamp).toString();
    }

    private String getRandomResource(Random random) {
        String[] resources = {
            "alice", "bob", "charlie", "diana", "eve", "frank", "grace", "henry"
        };
        return resources[random.nextInt(resources.length)];
    }

    private String getRandomStatus(Random random) {
        String[] statuses = {"pending", "completed", "failed", "approved", "rejected"};
        return statuses[random.nextInt(statuses.length)];
    }

    // ============================================================================
    // Main Method for Demo
    // ============================================================================

    public static void main(String[] args) {
        System.out.println("OCEL Conversion Benchmark Demo");
        System.out.println("=============================");
        System.out.println("This demo shows the benchmark structure without dependencies.");
        System.out.println();

        OcedConversionBenchmarkDemo benchmark = new OcedConversionBenchmarkDemo();
        benchmark.setup();

        System.out.println("Running demo benchmarks...\n");

        // Time the benchmarks
        long startTime = System.currentTimeMillis();

        benchmark.benchmarkOcelConversion_Small_Csv();
        long smallTime = System.currentTimeMillis() - startTime;
        System.out.printf("Small CSV conversion: %d ms\n", smallTime);

        benchmark.benchmarkOcelConversion_Medium_Csv();
        long mediumTime = System.currentTimeMillis() - startTime - smallTime;
        System.out.printf("Medium CSV conversion: %d ms\n", mediumTime);

        benchmark.benchmarkOcelConversion_Large_Csv();
        long largeTime = System.currentTimeMillis() - startTime - smallTime - mediumTime;
        System.out.printf("Large CSV conversion: %d ms\n", largeTime);

        benchmark.benchmarkSchemaInference();
        System.out.println("Schema inference: 15 ms (simulated)");

        benchmark.benchmarkFormatDetection();
        System.out.println("Format detection: 5 ms (simulated)");

        benchmark.benchmarkMemoryUsage();
        System.out.println("Memory usage: 10 ms (simulated)");

        System.out.println("\nDemo completed successfully!");
        System.out.println("\nThe real JMH benchmark (OcedConversionBenchmark.java) will provide:");
        System.out.println("- Statistical analysis with JMH");
        System.out.println("- Microbenchmark accuracy");
        System.out.println("- Memory profiling");
        System.out.println("- Throughput calculations");
    }
}