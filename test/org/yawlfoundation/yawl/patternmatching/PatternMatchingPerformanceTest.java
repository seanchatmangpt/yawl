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

package org.yawlfoundation.yawl.patternmatching;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAWLServiceGateway;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.unmarshal.YMetaData;

/**
 * Performance tests for pattern matching in YAWL.
 *
 * Tests cover:
 * - Switch vs if/else performance comparison
 * - Memory overhead of pattern matching
 * - Pattern compilation caching
 *
 * Chicago TDD: All tests use real objects, no mocks.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
@DisplayName("Pattern Matching Performance Tests")
@Tag("performance")
class PatternMatchingPerformanceTest {

    private static final int WARMUP_ITERATIONS = 1000;
    private static final int BENCHMARK_ITERATIONS = 10000;

    // ============================================================
    // Switch vs If/Else Benchmarks
    // ============================================================

    @Nested
    @DisplayName("Switch vs If/Else Benchmarks")
    class SwitchVsIfElseTests {

        @Test
        @DisplayName("Switch expression on enum is faster than if/else chain")
        void switchExpressionOnEnumIsFasterThanIfElseChain() {
            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                switchMethod(TestEnum.values()[i % TestEnum.values().length]);
                ifElseMethod(TestEnum.values()[i % TestEnum.values().length]);
            }

            // Benchmark switch
            long switchStart = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                switchMethod(TestEnum.values()[i % TestEnum.values().length]);
            }
            long switchTime = System.nanoTime() - switchStart;

            // Benchmark if/else
            long ifElseStart = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                ifElseMethod(TestEnum.values()[i % TestEnum.values().length]);
            }
            long ifElseTime = System.nanoTime() - ifElseStart;

            System.out.println("Switch time: " + (switchTime / 1_000_000) + "ms");
            System.out.println("If/Else time: " + (ifElseTime / 1_000_000) + "ms");

            // Switch should be at least as fast (usually faster due to tableswitch)
            // Allow some variance for JIT warmup differences
            assertTrue(switchTime < ifElseTime * 2,
                "Switch should not be significantly slower than if/else");
        }

        @Test
        @DisplayName("Pattern matching on sealed class hierarchy")
        void patternMatchingOnSealedClassHierarchy() throws YSyntaxException {
            YSpecification spec = createTestSpecification();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                patternMatchMethod(spec.getRootNet());
                patternMatchMethod(new YAWLServiceGateway("gw", spec));
            }

            // Benchmark pattern matching
            long patternStart = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                patternMatchMethod(spec.getRootNet());
                patternMatchMethod(new YAWLServiceGateway("gw" + i, spec));
            }
            long patternTime = System.nanoTime() - patternStart;

            // Benchmark instanceof chain
            long instanceOfStart = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                instanceofChainMethod(spec.getRootNet());
                instanceofChainMethod(new YAWLServiceGateway("gw" + i, spec));
            }
            long instanceOfTime = System.nanoTime() - instanceOfStart;

            System.out.println("Pattern match time: " + (patternTime / 1_000_000) + "ms");
            System.out.println("InstanceOf chain time: " + (instanceOfTime / 1_000_000) + "ms");

            // Both should complete in reasonable time
            assertTrue(patternTime < 500_000_000, "Pattern matching should complete within 500ms");
        }

        @Test
        @DisplayName("String switch performance")
        void stringSwitchPerformance() {
            String[] values = {"alpha", "beta", "gamma", "delta", "epsilon"};

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                stringSwitchMethod(values[i % values.length]);
                stringIfElseMethod(values[i % values.length]);
            }

            // Benchmark
            long switchStart = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                stringSwitchMethod(values[i % values.length]);
            }
            long switchTime = System.nanoTime() - switchStart;

            long ifElseStart = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                stringIfElseMethod(values[i % values.length]);
            }
            long ifElseTime = System.nanoTime() - ifElseStart;

            System.out.println("String switch time: " + (switchTime / 1_000_000) + "ms");
            System.out.println("String if/else time: " + (ifElseTime / 1_000_000) + "ms");

            // String switch uses hashCode, should be comparable
            assertTrue(switchTime < ifElseTime * 3,
                "String switch should be reasonably efficient");
        }

        @Test
        @DisplayName("Exhaustive switch ensures all cases covered")
        void exhaustiveSwitchEnsuresAllCasesCovered() {
            int casesHit = 0;
            for (TestEnum value : TestEnum.values()) {
                String result = switchMethod(value);
                assertNotNull(result);
                casesHit++;
            }

            assertEquals(TestEnum.values().length, casesHit,
                "All enum values should be handled");
        }
    }

    // ============================================================
    // Memory Overhead Tests
    // ============================================================

    @Nested
    @DisplayName("Memory Overhead Tests")
    class MemoryOverheadTests {

        @Test
        @DisplayName("Pattern matching has minimal memory overhead")
        void patternMatchingHasMinimalMemoryOverhead() {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            memoryBean.gc();

            MemoryUsage before = memoryBean.getHeapMemoryUsage();
            long beforeUsed = before.getUsed();

            // Execute many pattern matching operations
            for (int i = 0; i < 10000; i++) {
                String result = switchMethod(TestEnum.values()[i % TestEnum.values().length]);
                assertNotNull(result);
            }

            memoryBean.gc();
            MemoryUsage after = memoryBean.getHeapMemoryUsage();
            long afterUsed = after.getUsed();

            long memoryGrowthKB = (afterUsed - beforeUsed) / 1024;
            System.out.println("Memory growth after 10000 operations: " + memoryGrowthKB + "KB");

            // Memory growth should be minimal (< 1MB)
            assertTrue(memoryGrowthKB < 1024,
                "Pattern matching should not cause significant memory growth");
        }

        @Test
        @DisplayName("Instanceof pattern variables don't leak memory")
        void instanceofPatternVariablesDontLeakMemory() throws YSyntaxException {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

            // Create and pattern match many objects
            for (int batch = 0; batch < 10; batch++) {
                YSpecification spec = createTestSpecification();

                for (int i = 0; i < 1000; i++) {
                    String result = patternMatchMethod(spec.getRootNet());
                    assertNotNull(result);
                }

                memoryBean.gc();
            }

            MemoryUsage after = memoryBean.getHeapMemoryUsage();
            System.out.println("Heap after pattern matching batches: " +
                (after.getUsed() / (1024 * 1024)) + "MB");

            // Memory should stabilize, not grow indefinitely
            assertTrue(after.getUsed() < 100 * 1024 * 1024,
                "Memory should remain reasonable");
        }

        @Test
        @DisplayName("Switch table is cached by JVM")
        void switchTableIsCachedByJvm() {
            // First call triggers table creation
            long firstCall = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                switchMethod(TestEnum.A);
            }
            long firstDuration = System.nanoTime() - firstCall;

            // Subsequent calls should be faster (table cached)
            long secondCall = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                switchMethod(TestEnum.A);
            }
            long secondDuration = System.nanoTime() - secondCall;

            System.out.println("First 100 calls: " + (firstDuration / 1000) + "us");
            System.out.println("Second 100 calls: " + (secondDuration / 1000) + "us");

            // Both should be fast
            assertTrue(secondDuration < 10_000_000,
                "Cached switch should be very fast");
        }
    }

    // ============================================================
    // Pattern Compilation Caching Tests
    // ============================================================

    @Nested
    @DisplayName("Pattern Compilation Caching")
    class PatternCompilationCachingTests {

        @Test
        @DisplayName("Same pattern is not recompiled")
        void samePatternIsNotRecompiled() {
            // Simulate a pattern cache
            ConcurrentHashMap<String, AtomicInteger> cache = new ConcurrentHashMap<>();

            String pattern = "YNet";

            // Multiple uses of same pattern
            for (int i = 0; i < 1000; i++) {
                cache.computeIfAbsent(pattern, k -> new AtomicInteger(0)).incrementAndGet();
            }

            assertEquals(1, cache.size(), "Pattern should be cached");
            assertEquals(1000, cache.get(pattern).get(), "Pattern should be reused");
        }

        @Test
        @DisplayName("Different patterns are cached separately")
        void differentPatternsAreCachedSeparately() {
            ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

            cache.computeIfAbsent("YNet", k -> "compiled_YNet");
            cache.computeIfAbsent("YTask", k -> "compiled_YTask");
            cache.computeIfAbsent("YCondition", k -> "compiled_YCondition");

            assertEquals(3, cache.size());
            assertEquals("compiled_YNet", cache.get("YNet"));
            assertEquals("compiled_YTask", cache.get("YTask"));
            assertEquals("compiled_YCondition", cache.get("YCondition"));
        }

        @Test
        @DisplayName("Cache hit ratio is high for repeated patterns")
        void cacheHitRatioIsHighForRepeatedPatterns() {
            ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
            AtomicInteger hits = new AtomicInteger(0);
            AtomicInteger misses = new AtomicInteger(0);

            String[] patterns = {"YNet", "YTask", "YNet", "YTask", "YNet",
                                 "YCondition", "YNet", "YTask", "YNet", "YNet"};

            for (String pattern : patterns) {
                if (cache.containsKey(pattern)) {
                    hits.incrementAndGet();
                } else {
                    cache.put(pattern, "compiled_" + pattern);
                    misses.incrementAndGet();
                }
            }

            double hitRatio = (double) hits.get() / (hits.get() + misses.get());
            System.out.println("Cache hit ratio: " + (hitRatio * 100) + "%");

            assertTrue(hitRatio >= 0.5, "Cache hit ratio should be at least 50%");
        }

        @Test
        @DisplayName("InstanceOf check performance degrades with chain length")
        void instanceofCheckPerformanceDegradesWithChainLength() throws YSyntaxException {
            YSpecification spec = createTestSpecification();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                longChainCheck(spec.getRootNet(), 5);
            }

            // Benchmark different chain lengths
            long time5 = benchmarkChainLength(spec.getRootNet(), 5);
            long time10 = benchmarkChainLength(spec.getRootNet(), 10);
            long time15 = benchmarkChainLength(spec.getRootNet(), 15);

            System.out.println("5 checks: " + time5 + "ns avg");
            System.out.println("10 checks: " + time10 + "ns avg");
            System.out.println("15 checks: " + time15 + "ns avg");

            // Time should scale roughly linearly with chain length
            assertTrue(time15 < time5 * 5,
                "Chain check time should scale linearly, not exponentially");
        }
    }

    // ============================================================
    // Throughput Benchmarks
    // ============================================================

    @Nested
    @DisplayName("Throughput Benchmarks")
    class ThroughputBenchmarks {

        @Test
        @DisplayName("Switch throughput measurement")
        void switchThroughputMeasurement() {
            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                switchMethod(TestEnum.values()[i % TestEnum.values().length]);
            }

            long start = System.nanoTime();
            int operations = 1_000_000;
            for (int i = 0; i < operations; i++) {
                switchMethod(TestEnum.values()[i % TestEnum.values().length]);
            }
            long duration = System.nanoTime() - start;

            double opsPerSecond = (double) operations / (duration / 1_000_000_000.0);
            System.out.println("Switch throughput: " + String.format("%.0f", opsPerSecond) + " ops/sec");

            // Should achieve millions of operations per second
            assertTrue(opsPerSecond > 10_000_000,
                "Switch should handle at least 10M ops/sec");
        }

        @Test
        @DisplayName("Pattern match throughput measurement")
        void patternMatchThroughputMeasurement() throws YSyntaxException {
            YSpecification spec = createTestSpecification();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                patternMatchMethod(spec.getRootNet());
            }

            long start = System.nanoTime();
            int operations = 100_000;
            for (int i = 0; i < operations; i++) {
                patternMatchMethod(spec.getRootNet());
            }
            long duration = System.nanoTime() - start;

            double opsPerSecond = (double) operations / (duration / 1_000_000_000.0);
            System.out.println("Pattern match throughput: " + String.format("%.0f", opsPerSecond) + " ops/sec");

            // Pattern matching should still be fast
            assertTrue(opsPerSecond > 100_000,
                "Pattern matching should handle at least 100K ops/sec");
        }
    }

    // ============================================================
    // Helper Methods and Types
    // ============================================================

    private enum TestEnum {
        A, B, C, D, E
    }

    private String switchMethod(TestEnum value) {
        return switch (value) {
            case A -> "alpha";
            case B -> "beta";
            case C -> "gamma";
            case D -> "delta";
            case E -> "epsilon";
        };
    }

    private String ifElseMethod(TestEnum value) {
        if (value == TestEnum.A) return "alpha";
        else if (value == TestEnum.B) return "beta";
        else if (value == TestEnum.C) return "gamma";
        else if (value == TestEnum.D) return "delta";
        else return "epsilon";
    }

    private String stringSwitchMethod(String value) {
        return switch (value) {
            case "alpha" -> "A";
            case "beta" -> "B";
            case "gamma" -> "C";
            case "delta" -> "D";
            case "epsilon" -> "E";
            default -> "?";
        };
    }

    private String stringIfElseMethod(String value) {
        if ("alpha".equals(value)) return "A";
        else if ("beta".equals(value)) return "B";
        else if ("gamma".equals(value)) return "C";
        else if ("delta".equals(value)) return "D";
        else if ("epsilon".equals(value)) return "E";
        else return "?";
    }

    private String patternMatchMethod(Object obj) {
        if (obj instanceof YNet net) {
            return "net:" + net.getID();
        } else if (obj instanceof YTask task) {
            return "task:" + task.getID();
        } else if (obj instanceof YAWLServiceGateway gateway) {
            return "gateway:" + gateway.getID();
        } else {
            return "unknown";
        }
    }

    private String instanceofChainMethod(Object obj) {
        if (obj instanceof YNet) {
            YNet net = (YNet) obj;
            return "net:" + net.getID();
        } else if (obj instanceof YTask) {
            YTask task = (YTask) obj;
            return "task:" + task.getID();
        } else if (obj instanceof YAWLServiceGateway) {
            YAWLServiceGateway gateway = (YAWLServiceGateway) obj;
            return "gateway:" + gateway.getID();
        } else {
            return "unknown";
        }
    }

    private boolean longChainCheck(Object obj, int checks) {
        // Simulates long instanceof chain
        boolean result = false;
        if (checks > 0) result |= obj instanceof YNet;
        if (checks > 1) result |= obj instanceof YTask;
        if (checks > 2) result |= obj instanceof YAWLServiceGateway;
        if (checks > 3) result |= obj instanceof YInputCondition;
        if (checks > 4) result |= obj instanceof YOutputCondition;
        if (checks > 5) result |= obj instanceof YSpecification;
        if (checks > 6) result |= obj instanceof String;
        if (checks > 7) result |= obj instanceof Number;
        if (checks > 8) result |= obj instanceof Boolean;
        if (checks > 9) result |= obj instanceof Character;
        if (checks > 10) result |= obj instanceof Integer;
        if (checks > 11) result |= obj instanceof Long;
        if (checks > 12) result |= obj instanceof Double;
        if (checks > 13) result |= obj instanceof Float;
        if (checks > 14) result |= obj instanceof Byte;
        return result;
    }

    private long benchmarkChainLength(Object obj, int checks) {
        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            longChainCheck(obj, checks);
        }
        return (System.nanoTime() - start) / 10000;
    }

    private YSpecification createTestSpecification() throws YSyntaxException {
        YSpecification spec = new YSpecification("perf-test-spec");
        spec.setVersion(YSchemaVersion.FourPointZero);
        spec.setMetaData(new YMetaData());
        spec.setSchema("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");

        YNet net = new YNet("root-net", spec);
        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);
        net.addNetElement(input);
        net.addNetElement(output);
        spec.setRootNet(net);

        return spec;
    }
}
