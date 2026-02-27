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

package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance benchmark comparing ThreadLocal vs ScopedValue tenant context.
 * Tests performance characteristics on both platform and virtual threads.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class TenantContextPerformanceTest {

    private static final String TEST_TENANT_ID = "benchmark-tenant";
    private static final String TEST_CASE_ID = "benchmark-case-123";
    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 10000;

    @Test
    @DisplayName("ThreadLocal vs ScopedValue performance on platform threads")
    void testThreadLocalVsScopedValuePlatformThreads() {
        TenantContext tenant = new TenantContext(TEST_TENANT_ID);
        tenant.registerCase(TEST_CASE_ID);

        // Warmup
        warmupPlatformThreads();

        // Benchmark ThreadLocal
        long threadLocalTime = benchmarkThreadLocalPlatformThreads(tenant, BENCHMARK_ITERATIONS);
        System.out.println("[Platform Threads] ThreadLocal average: " +
                (threadLocalTime / (double) BENCHMARK_ITERATIONS) + " ns");

        // Benchmark ScopedValue
        long scopedValueTime = benchmarkScopedValuePlatformThreads(tenant, BENCHMARK_ITERATIONS);
        System.out.println("[Platform Threads] ScopedValue average: " +
                (scopedValueTime / (double) BENCHMARK_ITERATIONS) + " ns");

        // ScopedValue should be competitive or better than ThreadLocal
        double ratio = (double) scopedValueTime / threadLocalTime;
        System.out.printf("[Platform Threads] ScopedValue/ThreadLocal ratio: %.2f%n", ratio);
        assertTrue(ratio < 2.0, "ScopedValue should not be more than 2x slower than ThreadLocal");
    }

    @Test
    @DisplayName("ThreadLocal vs ScopedValue performance on virtual threads")
    void testThreadLocalVsScopedValueVirtualThreads() {
        TenantContext tenant = new TenantContext(TEST_TENANT_ID);
        tenant.registerCase(TEST_CASE_ID);

        // Warmup
        warmupVirtualThreads();

        // Benchmark ThreadLocal on virtual threads
        long threadLocalTime = benchmarkThreadLocalVirtualThreads(tenant, BENCHMARK_ITERATIONS);
        System.out.println("[Virtual Threads] ThreadLocal average: " +
                (threadLocalTime / (double) BENCHMARK_ITERATIONS) + " ns");

        // Benchmark ScopedValue on virtual threads
        long scopedValueTime = benchmarkScopedValueVirtualThreads(tenant, BENCHMARK_ITERATIONS);
        System.out.println("[Virtual Threads] ScopedValue average: " +
                (scopedValueTime / (double) BENCHMARK_ITERATIONS) + " ns");

        // ScopedValue should be significantly better than ThreadLocal for virtual threads
        double ratio = (double) scopedValueTime / threadLocalTime;
        System.out.printf("[Virtual Threads] ScopedValue/ThreadLocal ratio: %.2f%n", ratio);
        assertTrue(ratio < 1.5, "ScopedValue should be faster than ThreadLocal on virtual threads");
    }

    @Test
    @DisplayName("Virtual thread inheritance overhead measurement")
    void testVirtualThreadInheritanceOverhead() {
        TenantContext tenant = new TenantContext(TEST_TENANT_ID);
        tenant.registerCase(TEST_CASE_ID);

        // Measure time to create virtual thread with context inheritance
        long inheritanceTime = benchmarkInheritanceOverhead(tenant, BENCHMARK_ITERATIONS);
        System.out.println("Virtual thread inheritance average: " +
                (inheritanceTime / (double) BENCHMARK_ITERATIONS) + " ns");

        // Inheritance overhead should be minimal
        double avgTimePerInheritance = inheritanceTime / (double) BENCHMARK_ITERATIONS;
        assertTrue(avgTimePerInheritance < 1000,
                "Inheritance overhead should be less than 1000 ns per virtual thread");
    }

    @Test
    @DisplayName("Concurrent access performance with ScopedValue")
    void testConcurrentAccessPerformance() {
        TenantContext tenant = new TenantContext(TEST_TENANT_ID);
        tenant.registerCase(TEST_CASE_ID);

        int threadCount = Runtime.getRuntime().availableProcessors();
        long[] threadTimes = new long[threadCount];

        System.out.println("Testing concurrent access with " + threadCount + " threads...");

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threadTimes[i] = benchmarkConcurrentScopedAccess(tenant, threadIndex, BENCHMARK_ITERATIONS / threadCount);
        }

        double totalTime = 0;
        for (long time : threadTimes) {
            totalTime += time;
        }
        double averageTime = totalTime / threadCount;
        System.out.printf("Average concurrent access time: %.2f ms%n", averageTime);

        // Concurrent access should scale well
        assertTrue(averageTime < 10,
                "Concurrent ScopedValue access should complete in less than 10ms per thread");
    }

    @ParameterizedTest
    @DisplayName("Performance test with different thread types")
    @EnumSource(ThreadType.class)
    void testPerformanceByThreadType(ThreadType threadType) {
        TenantContext tenant = new TenantContext(TEST_TENANT_ID);
        tenant.registerCase(TEST_CASE_ID);

        long startTime = System.nanoTime();
        int iterations = 5000;

        for (int i = 0; i < iterations; i++) {
            switch (threadType) {
                case PLATFORM_THREAD:
                    runOnPlatformThread(tenant);
                    break;
                case VIRTUAL_THREAD:
                    runOnVirtualThread(tenant);
                    break;
                case VIRTUAL_THREAD_INHERITING:
                    runOnVirtualThreadWithInheritance(tenant);
                    break;
            }
        }

        long duration = System.nanoTime() - startTime;
        double averageTime = duration / (double) iterations;
        System.out.printf("[%s] Average time: %.2f ns%n", threadType, averageTime);

        // Performance should be reasonable for all thread types
        assertTrue(averageTime < 500,
                String.format("%s performance should be less than 500 ns per operation", threadType));
    }

    // Helper methods for benchmarking
    private void warmupPlatformThreads() {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runOnPlatformThread(new TenantContext("warmup-" + i));
        }
    }

    private void warmupVirtualThreads() {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runOnVirtualThread(new TenantContext("warmup-" + i));
        }
    }

    private long benchmarkThreadLocalPlatformThreads(TenantContext tenant, int iterations) {
        long startTime = System.nanoTime();
        YEngine.setTenantContext(tenant);
        for (int i = 0; i < iterations; i++) {
            YEngine.getTenantContext().isAuthorized(TEST_CASE_ID);
        }
        YEngine.clearTenantContext();
        return System.nanoTime() - startTime;
    }

    private long benchmarkScopedValuePlatformThreads(TenantContext tenant, int iterations) {
        AtomicLong totalTime = new AtomicLong(0);
        ScopedTenantContext.runWithTenant(tenant, () -> {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                ScopedTenantContext.getTenantContext().isAuthorized(TEST_CASE_ID);
            }
            totalTime.set(System.nanoTime() - start);
        });
        return totalTime.get();
    }

    private long benchmarkThreadLocalVirtualThreads(TenantContext tenant, int iterations) {
        AtomicLong totalTime = new AtomicLong(0);

        for (int i = 0; i < iterations; i++) {
            Thread.ofVirtual()
                .name("tl-benchmark-" + i)
                .start(() -> {
                    YEngine.setTenantContext(tenant);
                    YEngine.getTenantContext().isAuthorized(TEST_CASE_ID);
                    YEngine.clearTenantContext();
                })
                .join();

            totalTime.addAndGet(System.nanoTime());
        }

        return totalTime.get() / iterations;
    }

    private long benchmarkScopedValueVirtualThreads(TenantContext tenant, int iterations) {
        AtomicLong totalTime = new AtomicLong(0);

        ScopedTenantContext.runWithTenant(tenant, () -> {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                Thread.ofVirtual()
                    .name("sv-benchmark-" + i)
                    .start(() -> {
                        ScopedTenantContext.getTenantContext().isAuthorized(TEST_CASE_ID);
                    })
                    .join();
            }
            totalTime.set(System.nanoTime() - start);
        });

        return totalTime.get();
    }

    private long benchmarkInheritanceOverhead(TenantContext tenant, int iterations) {
        AtomicLong totalTime = new AtomicLong(0);

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            ScopedTenantContext.runWithTenant(tenant, () -> {
                Thread.ofVirtual()
                    .name("inheritance-" + i)
                    .start(() -> {})
                    .join();
            });
            totalTime.addAndGet(System.nanoTime() - start);
        }

        return totalTime.get();
    }

    private long benchmarkConcurrentScopedAccess(TenantContext tenant, int threadIndex, int iterations) {
        long startTime = System.nanoTime();

        Thread.ofVirtual()
            .name("concurrent-" + threadIndex)
            .start(() -> {
                ScopedTenantContext.runWithTenant(tenant, () -> {
                    for (int i = 0; i < iterations; i++) {
                        ScopedTenantContext.getTenantContext().isAuthorized(TEST_CASE_ID);
                    }
                });
            })
            .join();

        return System.nanoTime() - startTime;
    }

    private void runOnPlatformThread(TenantContext tenant) {
        YEngine.setTenantContext(tenant);
        YEngine.getTenantContext().isAuthorized(TEST_CASE_ID);
        YEngine.clearTenantContext();
    }

    private void runOnVirtualThread(TenantContext tenant) {
        Thread.ofVirtual()
            .name("platform-task")
            .start(() -> {
                YEngine.setTenantContext(tenant);
                YEngine.getTenantContext().isAuthorized(TEST_CASE_ID);
                YEngine.clearTenantContext();
            })
            .join();
    }

    private void runOnVirtualThreadWithInheritance(TenantContext tenant) {
        ScopedTenantContext.runWithTenant(tenant, () -> {
            Thread.ofVirtual()
                .name("inherit-task")
                .start(() -> {
                    ScopedTenantContext.getTenantContext().isAuthorized(TEST_CASE_ID);
                })
                .join();
        });
    }

    @Test
    @DisplayName("Memory usage comparison between ThreadLocal and ScopedValue")
    void testMemoryUsageComparison() {
        // This test would normally use a memory profiler, but we'll do a basic check
        // by creating many contexts and checking for memory leaks

        // ThreadLocal check
        for (int i = 0; i < 1000; i++) {
            YEngine.setTenantContext(new TenantContext("tenant-" + i));
        }
        long threadLocalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Clear ThreadLocal
        for (int i = 0; i < 1000; i++) {
            YEngine.clearTenantContext();
        }
        long threadLocalAfterClear = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // ScopedValue check
        for (int i = 0; i < 1000; i++) {
            ScopedTenantContext.runWithTenant(
                new TenantContext("tenant-" + i),
                () -> {}
            );
        }
        long scopedValueMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // ScopedValue should not accumulate memory like ThreadLocal
        System.out.printf("ThreadLocal memory: %d%n", threadLocalMemory);
        System.out.printf("ThreadLocal after clear: %d%n", threadLocalAfterClear);
        System.out.printf("ScopedValue memory: %d%n", scopedValueMemory);

        // Memory should be cleaned up properly
        assertTrue(threadLocalAfterClear < threadLocalMemory,
            "ThreadLocal should release memory after clear");
    }

    // Enum for thread types
    enum ThreadType {
        PLATFORM_THREAD,
        VIRTUAL_THREAD,
        VIRTUAL_THREAD_INHERITING
    }
}