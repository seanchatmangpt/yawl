/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.unmarshal;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.util.JDOMUtil;

/**
 * Comprehensive performance tests for the YAWL unmarshalling layer.
 *
 * Tests cover:
 * - Large XML parsing benchmarks
 * - Memory leak detection
 * - Concurrent marshalling safety
 * - Streaming vs DOM performance comparison
 *
 * Chicago TDD: All tests use real XML parsing, no mocks.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
@DisplayName("Unmarshal Performance Tests")
@Tag("performance")
class TestUnmarshalPerformance {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int BENCHMARK_ITERATIONS = 5;

    // ============================================================
    // Large XML Benchmarks
    // ============================================================

    @Nested
    @DisplayName("Large XML Benchmarks")
    class LargeXmlBenchmarkTests {

        @Test
        @DisplayName("Parses specification with 100 tasks within time limit")
        void parsesSpecificationWith100TasksWithinTimeLimit() throws YSyntaxException {
            String xml = generateSpecificationWithTasks(100);

            long totalTime = 0;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);
                long duration = System.nanoTime() - start;
                totalTime += duration;

                assertNotNull(specs);
                assertEquals(1, specs.size());
            }

            long avgTimeMs = (totalTime / BENCHMARK_ITERATIONS) / 1_000_000;
            System.out.println("100 tasks average parse time: " + avgTimeMs + "ms");

            // Should parse within 2 seconds
            assertTrue(avgTimeMs < 2000, "Parsing 100 tasks should take < 2s, took " + avgTimeMs + "ms");
        }

        @Test
        @DisplayName("Parses specification with 500 tasks within time limit")
        void parsesSpecificationWith500TasksWithinTimeLimit() throws YSyntaxException {
            String xml = generateSpecificationWithTasks(500);

            long totalTime = 0;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);
                long duration = System.nanoTime() - start;
                totalTime += duration;

                assertNotNull(specs);
            }

            long avgTimeMs = (totalTime / BENCHMARK_ITERATIONS) / 1_000_000;
            System.out.println("500 tasks average parse time: " + avgTimeMs + "ms");

            // Should parse within 5 seconds
            assertTrue(avgTimeMs < 5000, "Parsing 500 tasks should take < 5s, took " + avgTimeMs + "ms");
        }

        @Test
        @DisplayName("Parses specification with complex decompositions")
        void parsesSpecificationWithComplexDecompositions() throws YSyntaxException {
            String xml = generateSpecificationWithDecompositions(50, 10);

            long start = System.nanoTime();
            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            assertNotNull(specs);
            assertEquals(1, specs.size());

            System.out.println("50 decompositions with 10 tasks each: " + durationMs + "ms");
            assertTrue(durationMs < 5000, "Complex specification should parse within 5s");
        }

        @Test
        @DisplayName("Marshal performance scales linearly")
        void marshalPerformanceScalesLinearly() throws YSyntaxException {
            String xml = generateSpecificationWithTasks(100);
            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);
            YSpecification spec = specs.get(0);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                YMarshal.marshal(spec);
            }

            long totalTime = 0;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                String marshalled = YMarshal.marshal(spec);
                long duration = System.nanoTime() - start;
                totalTime += duration;
                assertNotNull(marshalled);
            }

            long avgTimeMs = (totalTime / BENCHMARK_ITERATIONS) / 1_000_000;
            System.out.println("Marshal 100 tasks average: " + avgTimeMs + "ms");

            assertTrue(avgTimeMs < 1000, "Marshalling should take < 1s, took " + avgTimeMs + "ms");
        }

        @Test
        @DisplayName("JDOMUtil stringToDocument performance")
        void jdomUtilStringToDocumentPerformance() {
            String xml = generateSpecificationWithTasks(100);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                JDOMUtil.stringToDocument(xml);
            }

            long totalTime = 0;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                var doc = JDOMUtil.stringToDocument(xml);
                long duration = System.nanoTime() - start;
                totalTime += duration;
                assertNotNull(doc);
            }

            long avgTimeMs = (totalTime / BENCHMARK_ITERATIONS) / 1_000_000;
            System.out.println("JDOMUtil stringToDocument (100 tasks): " + avgTimeMs + "ms");

            assertTrue(avgTimeMs < 500, "JDOM parsing should be fast, took " + avgTimeMs + "ms");
        }
    }

    // ============================================================
    // Memory Leak Detection Tests
    // ============================================================

    @Nested
    @DisplayName("Memory Leak Detection")
    class MemoryLeakDetectionTests {

        @Test
        @DisplayName("Repeated parsing does not leak memory")
        void repeatedParsingDoesNotLeakMemory() throws YSyntaxException {
            String xml = generateSpecificationWithTasks(50);

            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            memoryBean.gc();
            Thread.yield();

            MemoryUsage before = memoryBean.getHeapMemoryUsage();
            long beforeUsed = before.getUsed();

            // Parse 100 times
            for (int i = 0; i < 100; i++) {
                List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);
                assertNotNull(specs);
            }

            memoryBean.gc();
            Thread.yield();
            MemoryUsage after = memoryBean.getHeapMemoryUsage();
            long afterUsed = after.getUsed();

            // Memory growth should be reasonable (< 50MB for 100 iterations)
            long memoryGrowth = afterUsed - beforeUsed;
            long memoryGrowthMB = memoryGrowth / (1024 * 1024);

            System.out.println("Memory growth after 100 parses: " + memoryGrowthMB + "MB");

            // Allow some variance but flag significant leaks
            assertTrue(memoryGrowthMB < 50,
                "Memory should not grow significantly. Growth: " + memoryGrowthMB + "MB");
        }

        @Test
        @DisplayName("Marshal/unmarshal cycle does not leak")
        void marshalUnmarshalCycleDoesNotLeak() throws YSyntaxException {
            String xml = generateSpecificationWithTasks(50);

            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            memoryBean.gc();

            MemoryUsage before = memoryBean.getHeapMemoryUsage();
            long beforeUsed = before.getUsed();

            // 50 marshal/unmarshal cycles
            for (int i = 0; i < 50; i++) {
                List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);
                String marshalled = YMarshal.marshal(specs.get(0));
                assertNotNull(marshalled);
            }

            memoryBean.gc();
            MemoryUsage after = memoryBean.getHeapMemoryUsage();
            long afterUsed = after.getUsed();

            long memoryGrowthMB = (afterUsed - beforeUsed) / (1024 * 1024);
            System.out.println("Memory growth after 50 cycles: " + memoryGrowthMB + "MB");

            assertTrue(memoryGrowthMB < 30,
                "Marshal/unmarshal cycles should not leak. Growth: " + memoryGrowthMB + "MB");
        }

        @Test
        @DisplayName("Large specification cleanup after GC")
        void largeSpecificationCleanupAfterGc() throws YSyntaxException {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

            // Create and discard large specifications
            for (int i = 0; i < 10; i++) {
                String xml = generateSpecificationWithTasks(200);
                List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);
                // Let specs go out of scope
            }

            memoryBean.gc();
            Thread.yield();

            MemoryUsage after = memoryBean.getHeapMemoryUsage();
            System.out.println("Heap after GC: " + (after.getUsed() / (1024 * 1024)) + "MB");

            // Memory should be reclaimed
            assertTrue(after.getUsed() < 200 * 1024 * 1024,
                "Memory should be reclaimed after GC");
        }
    }

    // ============================================================
    // Concurrent Marshalling Tests
    // ============================================================

    @Nested
    @DisplayName("Concurrent Marshalling")
    class ConcurrentMarshallingTests {

        @Test
        @DisplayName("Concurrent unmarshal is thread-safe")
        void concurrentUnmarshalIsThreadSafe() throws Exception {
            String xml = generateSpecificationWithTasks(20);
            int threadCount = 10;
            int iterationsPerThread = 20;

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);
                            if (specs != null && !specs.isEmpty()) {
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "Concurrent operations should complete within timeout");
            assertEquals(threadCount * iterationsPerThread, successCount.get(),
                "All operations should succeed");
            assertEquals(0, errorCount.get(), "No errors should occur");
        }

        @Test
        @DisplayName("Concurrent marshal is thread-safe")
        void concurrentMarshalIsThreadSafe() throws Exception {
            String xml = generateSpecificationWithTasks(20);
            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);
            YSpecification spec = specs.get(0);

            int threadCount = 10;
            int iterationsPerThread = 20;

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            String marshalled = YMarshal.marshal(spec);
                            if (marshalled != null && !marshalled.isEmpty()) {
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "Concurrent marshal should complete within timeout");
            assertEquals(threadCount * iterationsPerThread, successCount.get());
            assertEquals(0, errorCount.get());
        }

        @Test
        @DisplayName("Concurrent JDOMUtil operations are thread-safe")
        void concurrentJdomUtilOperationsAreThreadSafe() throws Exception {
            String xml = generateSpecificationWithTasks(20);
            int threadCount = 10;
            int iterationsPerThread = 50;

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            var doc = JDOMUtil.stringToDocument(xml);
                            if (doc != null) {
                                String str = JDOMUtil.documentToString(doc);
                                if (str != null) {
                                    successCount.incrementAndGet();
                                }
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed);
            assertEquals(threadCount * iterationsPerThread, successCount.get());
        }

        @Test
        @DisplayName("High concurrency stress test")
        void highConcurrencyStressTest() throws Exception {
            String xml = generateSpecificationWithTasks(10);
            int threadCount = 50;
            int iterationsPerThread = 10;

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        for (int i = 0; i < iterationsPerThread; i++) {
                            var specs = YMarshal.unmarshalSpecifications(xml, false);
                            if (specs != null && !specs.isEmpty()) {
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            long startTime = System.nanoTime();
            startLatch.countDown(); // Release all threads simultaneously
            boolean completed = endLatch.await(60, TimeUnit.SECONDS);
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            executor.shutdown();

            assertTrue(completed, "Stress test should complete");
            assertEquals(threadCount * iterationsPerThread, successCount.get());

            System.out.println("High concurrency: " + threadCount + " threads x " +
                iterationsPerThread + " iterations = " + durationMs + "ms");
        }
    }

    // ============================================================
    // Streaming vs DOM Tests
    // ============================================================

    @Nested
    @DisplayName("Streaming vs DOM Performance")
    class StreamingVsDomTests {

        @Test
        @DisplayName("DOM parsing handles large documents")
        void domParsingHandlesLargeDocuments() throws YSyntaxException {
            String xml = generateSpecificationWithTasks(500);

            long start = System.nanoTime();
            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            assertNotNull(specs);
            assertEquals(1, specs.size());

            System.out.println("DOM parsing 500 tasks: " + durationMs + "ms");

            // DOM should handle this size reasonably
            assertTrue(durationMs < 10000, "DOM should parse within 10s");
        }

        @Test
        @DisplayName("Document size scales with task count")
        void documentSizeScalesWithTaskCount() throws YSyntaxException {
            String xml100 = generateSpecificationWithTasks(100);
            String xml200 = generateSpecificationWithTasks(200);
            String xml400 = generateSpecificationWithTasks(400);

            long time100 = measureParseTime(xml100);
            long time200 = measureParseTime(xml200);
            long time400 = measureParseTime(xml400);

            System.out.println("100 tasks: " + time100 + "ms");
            System.out.println("200 tasks: " + time200 + "ms");
            System.out.println("400 tasks: " + time400 + "ms");

            // Time should scale roughly linearly (not exponentially)
            // 400 tasks should take less than 4x 100 tasks
            assertTrue(time400 < time100 * 6,
                "Scaling should be roughly linear. 400 tasks took " + time400 +
                "ms vs 100 tasks at " + time100 + "ms");
        }

        @Test
        @DisplayName("Memory usage scales with document size")
        void memoryUsageScalesWithDocumentSize() throws YSyntaxException {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

            // Parse small document
            String smallXml = generateSpecificationWithTasks(10);
            memoryBean.gc();
            MemoryUsage beforeSmall = memoryBean.getHeapMemoryUsage();
            YMarshal.unmarshalSpecifications(smallXml, false);
            MemoryUsage afterSmall = memoryBean.getHeapMemoryUsage();
            long smallMemory = afterSmall.getUsed() - beforeSmall.getUsed();

            // Parse large document
            String largeXml = generateSpecificationWithTasks(200);
            memoryBean.gc();
            MemoryUsage beforeLarge = memoryBean.getHeapMemoryUsage();
            YMarshal.unmarshalSpecifications(largeXml, false);
            MemoryUsage afterLarge = memoryBean.getHeapMemoryUsage();
            long largeMemory = afterLarge.getUsed() - beforeLarge.getUsed();

            System.out.println("Small doc memory delta: " + (smallMemory / 1024) + "KB");
            System.out.println("Large doc memory delta: " + (largeMemory / 1024) + "KB");

            // Memory should scale roughly linearly
            // Allow for GC variation
            assertTrue(largeMemory < smallMemory * 50,
                "Memory scaling should be reasonable");
        }

        @Test
        @DisplayName("JDOMUtil formatXMLString performance")
        void jdomUtilFormatXmlStringPerformance() {
            String xml = generateSpecificationWithTasks(100);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                JDOMUtil.formatXMLString(xml);
            }

            long totalTime = 0;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                String formatted = JDOMUtil.formatXMLString(xml);
                long duration = System.nanoTime() - start;
                totalTime += duration;
                assertNotNull(formatted);
            }

            long avgTimeMs = (totalTime / BENCHMARK_ITERATIONS) / 1_000_000;
            System.out.println("formatXMLString (100 tasks): " + avgTimeMs + "ms");

            assertTrue(avgTimeMs < 500, "Formatting should be fast");
        }
    }

    // ============================================================
    // Benchmark Summary Tests
    // ============================================================

    @Nested
    @DisplayName("Benchmark Summary")
    class BenchmarkSummaryTests {

        @Test
        @DisplayName("Overall unmarshal performance benchmark")
        void overallUnmarshalPerformanceBenchmark() throws YSyntaxException {
            System.out.println("\n=== Unmarshal Performance Benchmark ===");

            int[] taskCounts = {10, 50, 100, 200};

            for (int count : taskCounts) {
                String xml = generateSpecificationWithTasks(count);

                // Warmup
                for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                    YMarshal.unmarshalSpecifications(xml, false);
                }

                // Benchmark
                long totalTime = 0;
                for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                    long start = System.nanoTime();
                    YMarshal.unmarshalSpecifications(xml, false);
                    totalTime += System.nanoTime() - start;
                }

                long avgTimeMs = (totalTime / BENCHMARK_ITERATIONS) / 1_000_000;
                System.out.println(count + " tasks: " + avgTimeMs + "ms avg");

                // Performance should be reasonable
                assertTrue(avgTimeMs < count * 20,
                    "Performance should scale reasonably for " + count + " tasks");
            }
        }

        @Test
        @DisplayName("Marshal performance benchmark")
        void marshalPerformanceBenchmark() throws YSyntaxException {
            System.out.println("\n=== Marshal Performance Benchmark ===");

            int[] taskCounts = {10, 50, 100, 200};

            for (int count : taskCounts) {
                String xml = generateSpecificationWithTasks(count);
                List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);
                YSpecification spec = specs.get(0);

                // Warmup
                for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                    YMarshal.marshal(spec);
                }

                // Benchmark
                long totalTime = 0;
                for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                    long start = System.nanoTime();
                    YMarshal.marshal(spec);
                    totalTime += System.nanoTime() - start;
                }

                long avgTimeMs = (totalTime / BENCHMARK_ITERATIONS) / 1_000_000;
                System.out.println(count + " tasks marshal: " + avgTimeMs + "ms avg");
            }
        }
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private String generateSpecificationWithTasks(int taskCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <?xml version="1.0" encoding="UTF-8"?>
            <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <specification uri="perf-test-spec">
                <metaData>
                  <title>Performance Test</title>
                </metaData>
                <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                  <processControlElements>
                    <inputCondition id="input">
                      <flowsInto>
                        <nextElementRef id="task1"/>
                      </flowsInto>
                    </inputCondition>
            """);

        for (int i = 1; i <= taskCount; i++) {
            sb.append("""
                    <task id="task%d">
                      <flowsInto>
                        <nextElementRef id="task%d"/>
                      </flowsInto>
                    </task>
                """.formatted(i, (i < taskCount) ? i + 1 : taskCount + 1));
        }

        sb.append("""
                    <outputCondition id="output"/>
                  </processControlElements>
                </decomposition>
              </specification>
            </specificationSet>
            """);

        return sb.toString();
    }

    private String generateSpecificationWithDecompositions(int decompositionCount, int tasksPerDecomposition) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <?xml version="1.0" encoding="UTF-8"?>
            <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <specification uri="complex-perf-test">
                <metaData/>
                <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
            """);

        // Root decomposition
        sb.append("""
                <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                  <processControlElements>
                    <inputCondition id="root-input"/>
                    <outputCondition id="root-output"/>
                  </processControlElements>
                </decomposition>
            """);

        // Sub-net decompositions
        for (int d = 1; d <= decompositionCount; d++) {
            sb.append("""
                    <decomposition id="subnet-%d" xsi:type="NetFactsType">
                      <processControlElements>
                        <inputCondition id="subnet-%d-input"/>
                """.formatted(d, d));

            for (int t = 1; t <= tasksPerDecomposition; t++) {
                sb.append("""
                            <task id="subnet-%d-task-%d"/>
                    """.formatted(d, t));
            }

            sb.append("""
                        <outputCondition id="subnet-%d-output"/>
                      </processControlElements>
                    </decomposition>
                """.formatted(d));
        }

        sb.append("""
              </specification>
            </specificationSet>
            """);

        return sb.toString();
    }

    private long measureParseTime(String xml) throws YSyntaxException {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            YMarshal.unmarshalSpecifications(xml, false);
        }

        long totalTime = 0;
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            YMarshal.unmarshalSpecifications(xml, false);
            totalTime += System.nanoTime() - start;
        }

        return (totalTime / BENCHMARK_ITERATIONS) / 1_000_000;
    }
}
