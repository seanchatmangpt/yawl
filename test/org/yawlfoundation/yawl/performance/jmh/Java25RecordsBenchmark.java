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

import java.time.Instant;
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
 * JMH benchmark: Records vs mutable classes for YAWL event and work item data.
 *
 * <p>Measures instantiation speed, memory allocation rate, equals/hashCode overhead,
 * and GC impact for Java record types compared to equivalent mutable POJOs.</p>
 *
 * <h2>YAWL context</h2>
 * <p>YAWL generates millions of event objects per hour (YEngineEvent, YAnnouncement,
 * WorkItemRecord). Replacing mutable event classes with records reduces:
 * <ul>
 *   <li>Allocation cost: no setter calls after construction</li>
 *   <li>Memory per object: no field padding from mutation tracking</li>
 *   <li>GC pressure: short-lived records are promoted less frequently</li>
 *   <li>Lock-free safety: immutable objects need no synchronization</li>
 * </ul>
 * </p>
 *
 * <h2>Compact Object Headers (Java 25 JEP 519)</h2>
 * <p>With {@code -XX:+UseCompactObjectHeaders}, each record (and all objects) gains
 * 4-8 bytes back. For 10K work items active simultaneously:
 * {@code 10,000 * 8 bytes = 80 KB} saved per record field reduction.
 * The JVM flag is a product flag in Java 25 (not experimental).</p>
 *
 * <h2>Expected results</h2>
 * <pre>
 *   Benchmark                    Score    Units
 *   recordInstantiation           ~2ns    ns/op
 *   mutableClassInstantiation     ~8ns    ns/op  (setter overhead)
 *   recordEquals                  ~1ns    ns/op
 *   mutableClassEquals            ~3ns    ns/op
 *   recordHashCode                ~1ns    ns/op  (auto-generated, inlined)
 *   mutableClassHashCode          ~2ns    ns/op
 *   bulkRecordAllocation         fast     ops/s  (lower GC pressure)
 *   bulkMutableAllocation        slower   ops/s  (more GC pressure)
 * </pre>
 *
 * @see org.yawlfoundation.yawl.engine.announcement.YEngineEvent
 * @see org.yawlfoundation.yawl.engine.YWorkItem
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {
    "-Xms2g", "-Xmx4g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Djmh.executor=VIRTUAL_TPE"
})
public class Java25RecordsBenchmark {

    /** How many objects to allocate per benchmark iteration. */
    @Param({"1", "100", "10000"})
    private int objectCount;

    // Pre-built instances used for equality/hashCode benchmarks
    private WorkItemEventRecord recordInstance;
    private MutableWorkItemEvent mutableInstance;
    private WorkItemEventRecord recordInstance2;
    private MutableWorkItemEvent mutableInstance2;

    @Setup(Level.Trial)
    public void setup() {
        Instant now = Instant.now();
        recordInstance = new WorkItemEventRecord(
            "case-001", "task-A", "statusExecuting", now, "service-handler", 42
        );
        recordInstance2 = new WorkItemEventRecord(
            "case-001", "task-A", "statusExecuting", now, "service-handler", 42
        );
        mutableInstance = MutableWorkItemEvent.of(
            "case-001", "task-A", "statusExecuting", now, "service-handler", 42
        );
        mutableInstance2 = MutableWorkItemEvent.of(
            "case-001", "task-A", "statusExecuting", now, "service-handler", 42
        );
    }

    // -----------------------------------------------------------------------
    // Benchmark 1: Instantiation cost
    // -----------------------------------------------------------------------

    /**
     * Record construction: single canonical constructor call.
     * No setter overhead, fields set atomically at construction.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public WorkItemEventRecord recordInstantiation(Blackhole bh) {
        WorkItemEventRecord event = new WorkItemEventRecord(
            "case-" + objectCount,
            "task-checkout",
            "statusExecuting",
            Instant.now(),
            "yawl-engine",
            objectCount
        );
        bh.consume(event);
        return event;
    }

    /**
     * Mutable class: constructor + 4 setter calls (pattern from YEvent).
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public MutableWorkItemEvent mutableClassInstantiation(Blackhole bh) {
        MutableWorkItemEvent event = new MutableWorkItemEvent();
        event.setCaseId("case-" + objectCount);
        event.setTaskId("task-checkout");
        event.setStatus("statusExecuting");
        event.setTimestamp(Instant.now());
        event.setServiceName("yawl-engine");
        event.setEngineNbr(objectCount);
        bh.consume(event);
        return event;
    }

    // -----------------------------------------------------------------------
    // Benchmark 2: equals() — critical for Set<YWorkItem> lookups
    // -----------------------------------------------------------------------

    /** Record equals: auto-generated, compares all fields. JIT-friendly. */
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public boolean recordEquals(Blackhole bh) {
        boolean eq = recordInstance.equals(recordInstance2);
        bh.consume(eq);
        return eq;
    }

    /** Mutable class equals: manually implemented (field-by-field). */
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public boolean mutableClassEquals(Blackhole bh) {
        boolean eq = mutableInstance.equals(mutableInstance2);
        bh.consume(eq);
        return eq;
    }

    // -----------------------------------------------------------------------
    // Benchmark 3: hashCode() — critical for HashMap<YWorkItemID, YWorkItem>
    // -----------------------------------------------------------------------

    /** Record hashCode: auto-generated from all fields, typically inlined. */
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int recordHashCode(Blackhole bh) {
        int h = recordInstance.hashCode();
        bh.consume(h);
        return h;
    }

    /** Mutable class hashCode: manually implemented. */
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int mutableClassHashCode(Blackhole bh) {
        int h = mutableInstance.hashCode();
        bh.consume(h);
        return h;
    }

    // -----------------------------------------------------------------------
    // Benchmark 4: Bulk allocation — GC pressure comparison
    // -----------------------------------------------------------------------

    /**
     * Allocate objectCount records and put them in a list.
     * Records: no mutable state so JIT can scalar-replace many short-lived ones.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public List<WorkItemEventRecord> bulkRecordAllocation(Blackhole bh) {
        List<WorkItemEventRecord> list = new ArrayList<>(objectCount);
        Instant ts = Instant.now();
        for (int i = 0; i < objectCount; i++) {
            WorkItemEventRecord r = new WorkItemEventRecord(
                "case-" + i, "task-" + (i % 10), "statusEnabled", ts, "engine", i
            );
            list.add(r);
            bh.consume(r);
        }
        return list;
    }

    /**
     * Allocate objectCount mutable objects + setters.
     * Each object undergoes write barriers during construction (JVM tracking).
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public List<MutableWorkItemEvent> bulkMutableAllocation(Blackhole bh) {
        List<MutableWorkItemEvent> list = new ArrayList<>(objectCount);
        Instant ts = Instant.now();
        for (int i = 0; i < objectCount; i++) {
            MutableWorkItemEvent e = new MutableWorkItemEvent();
            e.setCaseId("case-" + i);
            e.setTaskId("task-" + (i % 10));
            e.setStatus("statusEnabled");
            e.setTimestamp(ts);
            e.setServiceName("engine");
            e.setEngineNbr(i);
            list.add(e);
            bh.consume(e);
        }
        return list;
    }

    // -----------------------------------------------------------------------
    // Benchmark 5: HashMap lookup (YWorkItemRepository pattern)
    // -----------------------------------------------------------------------

    /**
     * HashMap put+get for record keys.
     * Records used as map keys benefit from immutable, JIT-stable hashCode.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public int recordMapLookup(Blackhole bh) {
        Map<WorkItemEventRecord, Integer> map = new HashMap<>(objectCount * 2);
        Instant ts = Instant.now();
        for (int i = 0; i < objectCount; i++) {
            WorkItemEventRecord key = new WorkItemEventRecord(
                "case-" + i, "task-" + i, "statusEnabled", ts, "engine", i
            );
            map.put(key, i);
        }
        int sum = 0;
        for (int i = 0; i < objectCount; i++) {
            WorkItemEventRecord key = new WorkItemEventRecord(
                "case-" + i, "task-" + i, "statusEnabled", ts, "engine", i
            );
            Integer val = map.get(key);
            if (val != null) sum += val;
        }
        bh.consume(map);
        return sum;
    }

    /**
     * HashMap put+get for mutable object keys.
     * Mutable keys risk hashCode instability if fields change after insertion.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public int mutableMapLookup(Blackhole bh) {
        Map<MutableWorkItemEvent, Integer> map = new HashMap<>(objectCount * 2);
        Instant ts = Instant.now();
        for (int i = 0; i < objectCount; i++) {
            MutableWorkItemEvent key = new MutableWorkItemEvent();
            key.setCaseId("case-" + i);
            key.setTaskId("task-" + i);
            key.setStatus("statusEnabled");
            key.setTimestamp(ts);
            key.setServiceName("engine");
            key.setEngineNbr(i);
            map.put(key, i);
        }
        // Must re-create matching objects for lookup (different instances, same data)
        int sum = 0;
        for (int i = 0; i < objectCount; i++) {
            MutableWorkItemEvent key = new MutableWorkItemEvent();
            key.setCaseId("case-" + i);
            key.setTaskId("task-" + i);
            key.setStatus("statusEnabled");
            key.setTimestamp(ts);
            key.setServiceName("engine");
            key.setEngineNbr(i);
            Integer val = map.get(key);
            if (val != null) sum += val;
        }
        bh.consume(map);
        return sum;
    }

    // -----------------------------------------------------------------------
    // Type definitions — record vs mutable equivalent
    // -----------------------------------------------------------------------

    /**
     * Record-style work item event.
     * Mirrors the target design from JAVA-25-FEATURES.md: YCaseLifecycleEvent.
     * Auto-generated: equals, hashCode, toString, accessor methods.
     */
    private record WorkItemEventRecord(
        String caseId,
        String taskId,
        String status,
        Instant timestamp,
        String serviceName,
        int engineNbr
    ) {}

    /**
     * Mutable POJO — current pattern in YEvent (abstract class with post-construction setters).
     * Manually implements equals/hashCode to provide a fair comparison.
     */
    private static final class MutableWorkItemEvent {
        private String caseId;
        private String taskId;
        private String status;
        private Instant timestamp;
        private String serviceName;
        private int engineNbr;

        MutableWorkItemEvent() {}

        static MutableWorkItemEvent of(String caseId, String taskId, String status,
                                       Instant timestamp, String serviceName, int engineNbr) {
            MutableWorkItemEvent e = new MutableWorkItemEvent();
            e.caseId      = caseId;
            e.taskId      = taskId;
            e.status      = status;
            e.timestamp   = timestamp;
            e.serviceName = serviceName;
            e.engineNbr   = engineNbr;
            return e;
        }

        void setCaseId(String v)      { caseId      = v; }
        void setTaskId(String v)      { taskId      = v; }
        void setStatus(String v)      { status      = v; }
        void setTimestamp(Instant v)  { timestamp   = v; }
        void setServiceName(String v) { serviceName = v; }
        void setEngineNbr(int v)      { engineNbr   = v; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MutableWorkItemEvent other)) return false;
            return engineNbr == other.engineNbr
                && java.util.Objects.equals(caseId, other.caseId)
                && java.util.Objects.equals(taskId, other.taskId)
                && java.util.Objects.equals(status, other.status)
                && java.util.Objects.equals(timestamp, other.timestamp)
                && java.util.Objects.equals(serviceName, other.serviceName);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(caseId, taskId, status, timestamp, serviceName, engineNbr);
        }
    }

    // -----------------------------------------------------------------------
    // Standalone runner
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(Java25RecordsBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(3)
            .measurementIterations(5)
            .param("objectCount", "1", "100", "10000")
            .build();

        new Runner(opt).run();
    }
}
