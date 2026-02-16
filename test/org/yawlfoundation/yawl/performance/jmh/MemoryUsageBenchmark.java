/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.jmh;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.*;

/**
 * JMH benchmark for memory usage comparing platform threads vs virtual threads.
 *
 * Measures:
 * - Heap usage for thread creation
 * - Memory per thread (platform: ~1MB, virtual: ~1KB)
 * - GC pressure with many threads
 *
 * Expected: Virtual threads should use 100-1000x less memory than platform threads.
 *
 * @author YAWL Performance Team
 * @date 2026-02-16
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g", "-XX:+UseG1GC", "-verbose:gc"})
public class MemoryUsageBenchmark {

    @Param({"100", "1000", "5000", "10000"})
    private int threadCount;

    private MemoryMXBean memoryBean;

    @Setup(Level.Trial)
    public void setup() {
        memoryBean = ManagementFactory.getMemoryMXBean();
    }

    /**
     * Measure memory usage with platform threads.
     * Platform threads use ~1MB per thread (stack + metadata).
     */
    @Benchmark
    public void platformThreadMemory(Blackhole bh) throws Exception {
        System.gc();
        Thread.sleep(100);
        
        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long usedBefore = beforeHeap.getUsed();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    startLatch.await();
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        Thread.sleep(50);
        MemoryUsage duringHeap = memoryBean.getHeapMemoryUsage();
        long usedDuring = duringHeap.getUsed();
        long memoryUsed = usedDuring - usedBefore;

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long memoryPerThread = memoryUsed / threadCount;
        bh.consume(memoryPerThread);

        System.out.println("Platform threads - Total memory: " + (memoryUsed / 1024 / 1024) + "MB, " +
                          "Per thread: " + (memoryPerThread / 1024) + "KB");
    }

    /**
     * Measure memory usage with virtual threads.
     * Virtual threads use ~1KB per thread (heap only, stack is on demand).
     */
    @Benchmark
    public void virtualThreadMemory(Blackhole bh) throws Exception {
        System.gc();
        Thread.sleep(100);
        
        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long usedBefore = beforeHeap.getUsed();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    startLatch.await();
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        Thread.sleep(50);
        MemoryUsage duringHeap = memoryBean.getHeapMemoryUsage();
        long usedDuring = duringHeap.getUsed();
        long memoryUsed = usedDuring - usedBefore;

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long memoryPerThread = memoryUsed / threadCount;
        bh.consume(memoryPerThread);

        System.out.println("Virtual threads - Total memory: " + (memoryUsed / 1024 / 1024) + "MB, " +
                          "Per thread: " + (memoryPerThread / 1024) + "KB");
    }

    /**
     * Measure GC activity with platform threads.
     */
    @Benchmark
    public void platformThreadGC(Blackhole bh) throws Exception {
        long gcCountBefore = getTotalGCCount();
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        long gcCountAfter = getTotalGCCount();
        long gcOccurred = gcCountAfter - gcCountBefore;
        
        bh.consume(gcOccurred);
        System.out.println("Platform threads - GC collections: " + gcOccurred);
    }

    /**
     * Measure GC activity with virtual threads.
     */
    @Benchmark
    public void virtualThreadGC(Blackhole bh) throws Exception {
        long gcCountBefore = getTotalGCCount();
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        long gcCountAfter = getTotalGCCount();
        long gcOccurred = gcCountAfter - gcCountBefore;
        
        bh.consume(gcOccurred);
        System.out.println("Virtual threads - GC collections: " + gcOccurred);
    }

    private long getTotalGCCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
            .mapToLong(gc -> gc.getCollectionCount())
            .sum();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(MemoryUsageBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
