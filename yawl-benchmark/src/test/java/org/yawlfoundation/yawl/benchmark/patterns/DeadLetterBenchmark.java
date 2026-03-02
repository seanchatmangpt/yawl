package org.yawlfoundation.yawl.benchmark.patterns;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.engine.agent.patterns.DeadLetter;
import org.yawlfoundation.yawl.engine.agent.patterns.DeadLetter.DeadLetterEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH Benchmark suite for DeadLetter pattern.
 * Tests append throughput, query performance, and unbounded growth detection.
 *
 * Run with:
 * java -jar benchmarks.jar DeadLetterBenchmark -f 1 -i 10 -wi 3 -prof gc
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Xms8g", "-Xmx16g",  // Larger heap for big logs
    "-Djdk.virtualThreadScheduler.parallelism=16"
})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
public class DeadLetterBenchmark extends BenchmarkBase {
    @Param({"1000", "10000", "100000"})
    public int failureRate = 1000;

    private ExecutorService executor;
    private AtomicInteger failureId;
    private final MetricsCollector metrics = new MetricsCollector("DeadLetter");

    @Setup(Level.Trial)
    public void setup() {
        recordMemory("setup_start");

        DeadLetter.clear();  // Start fresh
        executor = Executors.newFixedThreadPool(100);
        failureId = new AtomicInteger(0);

        recordMemory("setup_end");
        fullGC();
        recordMemory("setup_after_gc");
    }

    /**
     * Benchmark 1a: Append throughput at various failure rates
     * Measure: Messages logged to dead letter per second
     */
    @Benchmark
    public void appendThroughput(Blackhole bh) throws Exception {
        long startTime = System.nanoTime();
        AtomicInteger logged = new AtomicInteger(0);

        // Log at specified rate for 1 second
        long endTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);

        while (System.nanoTime() < endTime) {
            int fid = failureId.getAndIncrement();
            DeadLetter.log(
                "message_" + fid,
                DeadLetter.ACTOR_NOT_FOUND,
                new RuntimeException("Test failure")
            );
            logged.incrementAndGet();
        }

        long actualTime = System.nanoTime() - startTime;
        double throughput = logged.get() / (actualTime / 1_000_000_000.0);

        metrics.recordThroughput((long) throughput);
        bh.consume(logged);
    }

    /**
     * Benchmark 1b: Query performance with large log
     * Measure: Time to query and filter dead letter log
     */
    @Benchmark
    @Timeout(time = 120, timeUnit = TimeUnit.SECONDS)
    public void queryPerformance(Blackhole bh) throws Exception {
        recordMemory("query_start");

        // First, populate log with 1M entries
        String[] reasons = {
            DeadLetter.ACTOR_NOT_FOUND,
            DeadLetter.TIMEOUT,
            DeadLetter.ACTOR_CRASHED,
            DeadLetter.INVALID_MESSAGE
        };

        for (int i = 0; i < 1_000_000; i++) {
            DeadLetter.log(
                "message_" + i,
                reasons[i % reasons.length],
                new RuntimeException("Test")
            );
        }

        recordMemory("query_after_populate");

        // Now measure query performance
        long queryStart = System.nanoTime();

        // Query by reason
        long count = DeadLetter.getByReason(DeadLetter.ACTOR_NOT_FOUND).size();

        long queryTime = System.nanoTime() - queryStart;
        double queryMs = queryTime / 1_000_000.0;

        metrics.recordLatency(queryMs);

        recordMemory("query_after_query");

        if (queryMs > 1000) {
            System.err.println("WARNING: Query took " + queryMs + "ms for 1M entry log");
        }

        bh.consume(count);
    }

    /**
     * Benchmark 1c: Unbounded log growth detection
     * Measure: Heap pressure with continuous failure logging
     */
    @Benchmark
    @Timeout(time = 180, timeUnit = TimeUnit.SECONDS)
    public void unboundedGrowthDetection(Blackhole bh) throws Exception {
        recordMemory("growth_start");

        // Simulate continuous failures over extended period
        ExecutorService loggers = Executors.newFixedThreadPool(50);
        String[] reasons = {
            DeadLetter.ACTOR_NOT_FOUND,
            DeadLetter.TIMEOUT,
            DeadLetter.ACTOR_CRASHED,
            DeadLetter.QUEUE_FULL
        };

        for (int batch = 0; batch < 10; batch++) {
            for (int i = 0; i < 50; i++) {
                final int batchNum = batch;
                final int threadNum = i;
                loggers.submit(() -> {
                    for (int j = 0; j < 100_000; j++) {
                        int fid = failureId.getAndIncrement();
                        try {
                            DeadLetter.log(
                                "msg_" + fid,
                                reasons[fid % reasons.length],
                                new RuntimeException("Failure")
                            );
                        } catch (Exception e) {
                            // Log full - stop
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }

            // Every batch, check heap
            Thread.sleep(100);
            recordMemory("growth_batch_" + batch);

            if (batch > 0 && isHeapGrowingUnbounded(2000)) {
                throw new AssertionError("Unbounded log growth at batch " + batch);
            }
        }

        loggers.shutdown();
        loggers.awaitTermination(30, TimeUnit.SECONDS);

        recordMemory("growth_end");
        bh.consume(DeadLetter.count());
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        recordMemory("teardown_start");

        if (executor != null) {
            executor.shutdownNow();
        }

        long finalCount = DeadLetter.count();
        long finalHeap = getHeapUsedMB();

        fullGC();
        recordMemory("teardown_end");

        System.out.println("\n=== DeadLetter Benchmark Results ===");
        System.out.println(metrics.generateReport());

        System.out.println("\nDeadLetter Statistics:");
        System.out.println("  Final log size: " + finalCount + " entries");
        System.out.println("  Heap at end: " + finalHeap + " MB");
        if (finalCount > 0) {
            System.out.println("  Bytes per entry: " + (finalHeap * 1024 * 1024 / finalCount) + " bytes");
        }

        System.out.println("\nMemory Snapshots:");
        for (MemorySnapshot snap : snapshots) {
            System.out.println("  " + snap);
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 300, unit = java.util.concurrent.TimeUnit.SECONDS)
    void runBenchmarks() throws Exception {
        Options opt = new OptionsBuilder()
            .include(getClass().getSimpleName())
            .forks(0)
            .warmupIterations(1)
            .warmupTime(TimeValue.seconds(1))
            .measurementIterations(3)
            .measurementTime(TimeValue.seconds(3))
            .build();
        new Runner(opt).run();
    }
}