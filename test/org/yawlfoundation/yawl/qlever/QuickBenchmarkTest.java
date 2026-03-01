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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Quick benchmark tests to verify basic functionality.
 * Can be run as a quick smoke test before running full benchmarks.
 */
@DisplayName("Quick QLever Benchmarks")
@Tag("benchmark")
class QuickBenchmarkTest {

    private static final int MEASUREMENT_ITERATIONS = 10; // Reduced for quick test

    private QLeverEmbeddedSparqlEngine engine;

    @BeforeAll
    static void ensureAvailable() {
        assumeTrue(QLeverTestNode.isAvailable(), "QLever native lib not available");
    }

    @BeforeEach
    void setup() throws Exception {
        engine = QLeverTestNode.engine();

        // Quick warmup
        for (int i = 0; i < 3; i++) {
            try (QLeverResult result = engine.executeSelect("SELECT ?s WHERE { ?s ?p ?o } LIMIT 1", QLeverMediaType.JSON)) {
                String data = result.data();
                if (data == null || data.isEmpty()) {
                    throw new RuntimeException("Engine warmup failed");
                }
            }
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        // Engine is managed by QLeverTestNode, no manual cleanup needed
    }

    @Test
    @EnabledIf("isEngineAvailable")
    void testBasicQueryPerformance() throws Exception {
        List<Long> durations = new ArrayList<>();

        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long start = System.nanoTime();
            try (QLeverResult result = engine.executeSelect("SELECT ?s WHERE { ?s ?p ?o } LIMIT 10", QLeverMediaType.JSON)) {
                String data = result.data();

                long end = System.nanoTime();

                assertNotNull(data, "Query should return a result");
                assertFalse(data.isEmpty(), "Result should not be empty");

                durations.add((end - start) / 1_000); // Convert to microseconds
            }
        }

        // Calculate statistics
        double mean = durations.stream().mapToLong(Long::longValue).average().orElse(0);
        double median = calculateMedian(durations);
        double stdDev = calculateStandardDeviation(durations, mean);

        System.out.println("Basic Query Performance:");
        System.out.println("  Mean: " + mean + " μs");
        System.out.println("  Median: " + median + " μs");
        System.out.println("  Std Dev: " + stdDev + " μs");
        System.out.println("  Throughput: " + (1_000_000.0 / mean) + " ops/sec");

        // Basic validation
        assertTrue(mean < 100_000, "Mean query time should be < 100ms");
        assertTrue(durations.size() == MEASUREMENT_ITERATIONS, "Should have " + MEASUREMENT_ITERATIONS + " measurements");
    }

    @Test
    @EnabledIf("isEngineAvailable")
    void testDifferentQueryComplexities() throws Exception {
        String[] queries = {
            "SELECT ?s WHERE { ?s ?p ?o } LIMIT 10",                                    // Simple
            "SELECT ?s ?p ?o WHERE { ?s ?p ?o }",                                       // Medium
            "PREFIX yawl: <http://yawl.io/> SELECT ?case ?task WHERE { ?case yawl:hasTask ?task }" // Complex
        };

        for (String query : queries) {
            List<Long> durations = new ArrayList<>();

            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                long start = System.nanoTime();
                try (QLeverResult result = engine.executeSelect(query, QLeverMediaType.JSON)) {
                    String data = result.data();

                    long end = System.nanoTime();

                    assertNotNull(data, "Query should return a result");
                    assertFalse(data.isEmpty(), "Result should not be empty");

                    durations.add((end - start) / 1_000); // Convert to microseconds
                }
            }

            double mean = durations.stream().mapToLong(Long::longValue).average().orElse(0);
            System.out.printf("Query '%s': %.2f μs mean%n",
                query.length() > 50 ? query.substring(0, 50) + "..." : query, mean);

            // Complex queries should not take orders of magnitude longer
            assertTrue(mean < 1_000_000, "Query should complete within 1 second");
        }
    }

    @Test
    @EnabledIf("isEngineAvailable")
    void testMemoryUsage() throws Exception {
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Execute multiple queries
        for (int i = 0; i < 50; i++) {
            String query = BenchmarkDataGenerator.generateRandomQuery();
            try (QLeverResult result = engine.executeSelect(query, QLeverMediaType.JSON)) {
                String data = result.data();

                // Process results to encourage memory allocation
                if (data != null) {
                    data.length();
                    data.intern();
                }
            }
        }

        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memoryUsed = endMemory - startMemory;

        System.out.println("Memory Usage:");
        System.out.println("  Memory allocated: " + (memoryUsed / 1024.0 / 1024.0) + " MB");

        // Reasonable memory growth check
        assertTrue(memoryUsed < 100 * 1024 * 1024, "Should use less than 100MB");
    }

    @Test
    @EnabledIf("isEngineAvailable")
    void testConcurrentQueries() throws Exception {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<String>> futures = new ArrayList<>();
        List<Long> durations = new ArrayList<>();

        // Submit concurrent queries
        for (int i = 0; i < threadCount * 5; i++) {
            final int threadId = i;
            Future<String> future = executor.submit(() -> {
                long start = System.nanoTime();
                try (QLeverResult result = engine.executeSelect(
                    "SELECT ?s WHERE { ?s ?p ?o } LIMIT " + (10 + threadId % 10), QLeverMediaType.JSON)) {
                    String data = result.data();
                    long end = System.nanoTime();

                    durations.add((end - start) / 1_000); // Convert to microseconds
                    return data;
                }
            });
            futures.add(future);
        }

        // Wait for completion
        for (Future<String> future : futures) {
            try {
                String result = future.get(10, TimeUnit.SECONDS);
                assertNotNull(result, "Concurrent query should return result");
            } catch (Exception e) {
                fail("Concurrent query failed: " + e.getMessage());
            }
        }

        executor.shutdown();

        System.out.println("Concurrent Query Performance:");
        System.out.println("  Total queries: " + futures.size());
        System.out.println("  Mean: " +
            durations.stream().mapToLong(Long::longValue).average().orElse(0) + " μs");
        System.out.println("  Max: " + durations.stream().mapToLong(Long::longValue).max().orElse(0) + " μs");

        assertTrue(durations.size() == futures.size(), "Should have results for all queries");
    }

    @Test
    @EnabledIf("isEngineAvailable")
    void testFormatSerialization() {
        String query = "SELECT ?case ?status WHERE { ?case workflow:status ?status } LIMIT 50";

        // Test different formats
        QLeverMediaType[] formats = {
            QLeverMediaType.JSON,
            QLeverMediaType.TSV,
            QLeverMediaType.CSV
        };

        for (QLeverMediaType format : formats) {
            long start = System.nanoTime();

            try {
                try (QLeverResult result = engine.executeSelect(query, format)) {
                    String data = result.data();

                    long end = System.nanoTime();
                    long duration = (end - start) / 1_000; // Convert to microseconds

                    System.out.printf("%s format: %.2f μs, %d bytes%n",
                        format.name(), duration, data.getBytes().length);

                    assertNotNull(data, format.name() + " serialization should return result");
                    assertFalse(data.isEmpty(), format.name() + " result should not be empty");
                    assertTrue(duration < 500_000, format.name() + " serialization should be < 500ms");
                }

            } catch (Exception e) {
                fail(format.name() + " serialization failed: " + e.getMessage());
            }
        }
    }

    // Helper methods

    private static boolean isEngineAvailable() {
        return QLeverTestNode.isAvailable();
    }

    private static double calculateMedian(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        } else {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        }
    }

    private static double calculateStandardDeviation(List<Long> values, double mean) {
        double variance = values.stream()
            .mapToDouble(value -> Math.pow(value - mean, 2))
            .average()
            .orElse(0);
        return Math.sqrt(variance);
    }
}
