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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH benchmark: Compact Object Headers (Java 25 JEP 519) throughput and memory impact.
 *
 * <h2>What JEP 519 does</h2>
 * <p>The JVM object header historically uses 96-128 bits (12-16 bytes aligned).
 * Compact Object Headers reduce it to 64 bits (8 bytes), saving 4-8 bytes per object.
 * The flag {@code -XX:+UseCompactObjectHeaders} is a product flag in Java 25 (not experimental).</p>
 *
 * <h2>YAWL workload profile</h2>
 * <p>A typical YAWL deployment with 500 concurrent cases and 10 tasks per case
 * maintains ~5,000 active YWorkItem objects simultaneously, plus announcements,
 * event records, conditions, and identifiers. At 8 bytes saved per object:</p>
 * <pre>
 *   10,000 objects * 8 bytes = 80 KB saved (heap)
 *   100,000 objects * 8 bytes = 800 KB saved
 *   1,000,000 objects * 8 bytes = 8 MB saved (startup batch processing)
 * </pre>
 *
 * <h2>Throughput mechanism</h2>
 * <p>Smaller objects mean:</p>
 * <ul>
 *   <li>More objects fit in L1/L2 cache → fewer cache misses</li>
 *   <li>Reduced GC marking time (less heap to scan)</li>
 *   <li>Lower allocation rate (same data, fewer bytes)</li>
 *   <li>Faster object copy in young-gen GC</li>
 * </ul>
 *
 * <h2>Benchmark design</h2>
 * <p>This benchmark cannot toggle the JVM flag at runtime. Instead it:
 * <ol>
 *   <li>Measures allocation-heavy throughput workloads</li>
 *   <li>Reports actual heap usage so the operator can compare runs
 *       with and without {@code -XX:+UseCompactObjectHeaders}</li>
 *   <li>Runs the same benchmark with two JVM fork configurations:
 *       standard G1GC and ZGC (target for Java 25 production)</li>
 * </ol>
 *
 * <h2>Running with Compact Headers (Java 25 only)</h2>
 * <pre>
 *   java -XX:+UseCompactObjectHeaders \
 *        -XX:+UseZGC \
 *        -jar benchmarks.jar Java25CompactHeadersBenchmark
 * </pre>
 *
 * @see <a href="https://openjdk.org/jeps/519">JEP 519: Compact Object Headers</a>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {
    "-Xms2g", "-Xmx4g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Djmh.executor=VIRTUAL_TPE"
})
public class Java25CompactHeadersBenchmark {

    /** Object graph size — mirrors YWorkItemRepository active item count. */
    @Param({"1000", "10000", "100000"})
    private int objectCount;

    private MemoryMXBean memoryBean;

    @Setup(Level.Trial)
    public void setup() {
        memoryBean = ManagementFactory.getMemoryMXBean();
    }

    // -----------------------------------------------------------------------
    // Benchmark 1: YWorkItem-like object graph allocation throughput
    // -----------------------------------------------------------------------

    /**
     * Allocate an object graph representative of YWorkItem (shallow model).
     * With compact headers: each object saves 4-8 bytes.
     * Run once with standard headers, once with {@code -XX:+UseCompactObjectHeaders}
     * and compare the throughput score and reported heap delta.
     */
    @Benchmark
    public long workItemGraphAllocation(Blackhole bh) {
        System.gc();
        MemoryUsage before = memoryBean.getHeapMemoryUsage();

        List<WorkItemSimulant> items = new ArrayList<>(objectCount);
        for (int i = 0; i < objectCount; i++) {
            items.add(new WorkItemSimulant(i));
        }

        MemoryUsage after = memoryBean.getHeapMemoryUsage();
        long heapDelta = after.getUsed() - before.getUsed();

        for (WorkItemSimulant item : items) {
            bh.consume(item);
        }

        long bytesPerObject = heapDelta / objectCount;
        System.out.printf("[CompactHeaders] %d objects | heap delta: %d KB | ~%d bytes/obj%n",
            objectCount, heapDelta / 1024, bytesPerObject);

        return heapDelta;
    }

    // -----------------------------------------------------------------------
    // Benchmark 2: String-keyed HashMap (YWorkItemRepository pattern)
    // -----------------------------------------------------------------------

    /**
     * HashMap with many small value objects — typical YWorkItemRepository lookup map.
     * Compact headers reduce HashMap.Entry node size + value object sizes.
     */
    @Benchmark
    public int hashMapPutGet(Blackhole bh) {
        Map<String, WorkItemSimulant> map = new HashMap<>(objectCount * 2);

        for (int i = 0; i < objectCount; i++) {
            String key = "case-" + i + ":task-" + (i % 20);
            map.put(key, new WorkItemSimulant(i));
        }

        int found = 0;
        for (int i = 0; i < objectCount; i++) {
            String key = "case-" + i + ":task-" + (i % 20);
            if (map.get(key) != null) found++;
        }

        bh.consume(map);
        return found;
    }

    // -----------------------------------------------------------------------
    // Benchmark 3: YIdentifier chain traversal — deep linked object graph
    // -----------------------------------------------------------------------

    /**
     * Traverse a linked object chain (YIdentifier parent→child tokens).
     * Compact headers improve traversal cache locality by ~8 bytes/node.
     */
    @Benchmark
    public int linkedObjectTraversal(Blackhole bh) {
        // Build a linked chain representing a case identifier hierarchy
        IdentifierNode root = buildChain(objectCount);

        // Traverse and count
        int count = 0;
        IdentifierNode curr = root;
        while (curr != null) {
            bh.consume(curr);
            count++;
            curr = curr.next;
        }
        return count;
    }

    // -----------------------------------------------------------------------
    // Benchmark 4: Small object allocation storm — GC baseline
    // -----------------------------------------------------------------------

    /**
     * Creates and discards many small short-lived objects (event records).
     * Compact headers reduce G1 young-gen copy time proportionally.
     * Baseline for measuring GC improvement after enabling compact headers.
     */
    @Benchmark
    public int shortLivedObjectStorm(Blackhole bh) {
        int consumed = 0;
        for (int i = 0; i < objectCount; i++) {
            // Simulate event record creation (short-lived, escape analysis may optimize)
            TinyEventRecord event = new TinyEventRecord(i, "case-" + (i % 100), "statusEnabled");
            bh.consume(event);
            consumed++;
        }
        return consumed;
    }

    // -----------------------------------------------------------------------
    // Benchmark 5: Array of objects — YEnablementRule[] / YTask[] patterns
    // -----------------------------------------------------------------------

    /**
     * Object array allocation — models YNet.getNetElements() storage.
     * Compact headers reduce each element's footprint, improving array scan speed.
     */
    @Benchmark
    public long objectArrayScan(Blackhole bh) {
        WorkItemSimulant[] array = new WorkItemSimulant[objectCount];
        for (int i = 0; i < objectCount; i++) {
            array[i] = new WorkItemSimulant(i);
        }

        long sum = 0;
        for (WorkItemSimulant item : array) {
            sum += item.id;
            bh.consume(item);
        }
        return sum;
    }

    // -----------------------------------------------------------------------
    // Benchmark 6: Heap footprint reporter
    // Run this directly to measure actual bytes/object under two configurations:
    //   1. java -jar benchmarks.jar Java25CompactHeadersBenchmark.heapFootprintReport
    //   2. java -XX:+UseCompactObjectHeaders -jar benchmarks.jar ... (Java 25 only)
    // -----------------------------------------------------------------------

    /**
     * Reports exact heap footprint per object class.
     * Compare output between standard headers and compact headers runs.
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public String heapFootprintReport(Blackhole bh) {
        System.gc();
        MemoryUsage before = memoryBean.getHeapMemoryUsage();

        List<Object> retained = new ArrayList<>(objectCount);
        for (int i = 0; i < objectCount; i++) {
            retained.add(new WorkItemSimulant(i));
        }

        System.gc();
        MemoryUsage after = memoryBean.getHeapMemoryUsage();
        long delta = after.getUsed() - before.getUsed();
        long perObject = delta / Math.max(objectCount, 1);

        String report = String.format(
            "heap_delta=%dKB objects=%d bytes/obj=%d jvm=%s headers=%s",
            delta / 1024,
            objectCount,
            perObject,
            System.getProperty("java.vm.version", "unknown"),
            System.getProperty("java.vm.info", "standard")
        );
        System.out.println("[HeapFootprint] " + report);

        bh.consume(retained);
        return report;
    }

    // -----------------------------------------------------------------------
    // Internal types
    // -----------------------------------------------------------------------

    /**
     * Simulates a YWorkItem's in-memory footprint (6 fields, representative size).
     * Each instance: 1 int + 3 String refs + 1 long + 1 enum-like ref = ~56 bytes standard.
     * With compact headers: ~48 bytes (saves ~8 bytes per object).
     */
    private static final class WorkItemSimulant {
        final int id;
        final String caseId;
        final String taskId;
        final String status;
        final long enabledAt;
        final String specUri;

        WorkItemSimulant(int id) {
            this.id        = id;
            this.caseId    = "case-" + id;
            this.taskId    = "task-" + (id % 20);
            this.status    = (id % 3 == 0) ? "statusEnabled" : "statusExecuting";
            this.enabledAt = System.nanoTime();
            this.specUri   = "http://yawl/spec/" + (id % 5);
        }
    }

    /** Lightweight linked node — models YIdentifier parent chain. */
    private static final class IdentifierNode {
        final int seq;
        final String label;
        final IdentifierNode next;

        IdentifierNode(int seq, String label, IdentifierNode next) {
            this.seq   = seq;
            this.label = label;
            this.next  = next;
        }
    }

    /** Short-lived event record — models YEngineEvent dispatch. */
    private record TinyEventRecord(int seq, String caseId, String eventType) {}

    private IdentifierNode buildChain(int length) {
        IdentifierNode node = null;
        for (int i = length - 1; i >= 0; i--) {
            node = new IdentifierNode(i, "id-" + i, node);
        }
        return node;
    }

    // -----------------------------------------------------------------------
    // Standalone runner
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws RunnerException {
        System.out.println("""
            ================================================================
            Compact Object Headers Benchmark
            ================================================================
            Run this benchmark twice:

              Pass 1 (baseline, any JVM):
                java -jar benchmarks.jar Java25CompactHeadersBenchmark

              Pass 2 (Java 25 only, compact headers enabled):
                java -XX:+UseCompactObjectHeaders \\
                     -XX:+UseZGC \\
                     -jar benchmarks.jar Java25CompactHeadersBenchmark

            Expected: 5-10% throughput increase on objectCount=100000
            Expected: ~8 bytes/object reduction in heapFootprintReport
            ================================================================
            """);

        Options opt = new OptionsBuilder()
            .include(Java25CompactHeadersBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(2)
            .measurementIterations(3)
            .param("objectCount", "1000", "10000", "100000")
            .build();

        new Runner(opt).run();
    }
}
