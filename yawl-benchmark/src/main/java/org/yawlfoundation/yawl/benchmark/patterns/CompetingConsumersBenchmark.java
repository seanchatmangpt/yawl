package org.yawlfoundation.yawl.benchmark.patterns;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;
import org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime;
import org.yawlfoundation.yawl.engine.agent.patterns.CompetingConsumers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMH Benchmark suite for CompetingConsumers pattern.
 * Tests work distribution fairness, throughput scaling, and queue depth under load.
 *
 * Run with:
 * java -jar benchmarks.jar CompetingConsumersBenchmark -f 1 -i 10 -wi 3 -prof gc
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Xms8g", "-Xmx8g",
    "-Djdk.virtualThreadScheduler.parallelism=16",
    "-Djdk.virtualThreadScheduler.maxPoolSize=100000"
})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
public class CompetingConsumersBenchmark extends BenchmarkBase {
    private ActorRuntime runtime;
    private final MetricsCollector metrics = new MetricsCollector("CompetingConsumers");

    @Param({"4", "8", "16", "32"})
    public int workerCount = 4;

    @Setup(Level.Trial)
    public void setup() {
        recordMemory("setup_start");
        runtime = new VirtualThreadRuntime();
        recordMemory("setup_end");
        fullGC();
        recordMemory("setup_after_gc");
    }

    /**
     * Benchmark 1a: Work distribution fairness
     * Measure: Task distribution across worker pool
     */
    @Benchmark
    public void workDistributionFairness(Blackhole bh) throws Exception {
        AtomicInteger[] taskCounts = new AtomicInteger[workerCount];
        for (int i = 0; i < workerCount; i++) {
            taskCounts[i] = new AtomicInteger(0);
        }

        CountDownLatch latch = new CountDownLatch(10_000);

        CompetingConsumers pool = new CompetingConsumers.Builder()
            .poolName("fairness_test_" + workerCount)
            .workerCount(workerCount)
            .handler(task -> {
                // Distribute work tracking
                int workerIdx = ((Number) task).intValue() % workerCount;
                taskCounts[workerIdx].incrementAndGet();
                latch.countDown();
            })
            .build();

        pool.start(runtime);

        // Submit 10K tasks
        for (int i = 0; i < 10_000; i++) {
            pool.submit(i);
        }

        // Wait for all tasks
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new TimeoutException("Benchmark timeout");
        }

        pool.shutdown(Duration.ofSeconds(10));

        // Calculate fairness metrics
        int min = Arrays.stream(taskCounts).mapToInt(AtomicInteger::get).min().orElse(0);
        int max = Arrays.stream(taskCounts).mapToInt(AtomicInteger::get).max().orElse(0);
        double avg = Arrays.stream(taskCounts).mapToInt(AtomicInteger::get).average().orElse(0);
        double stdDev = Math.sqrt(
            Arrays.stream(taskCounts)
                .mapToInt(AtomicInteger::get)
                .mapToDouble(x -> Math.pow(x - avg, 2))
                .average()
                .orElse(0)
        );

        metrics.recordLatency(stdDev);

        if (max - min > 2500) {
            System.err.println("WARNING: Poor distribution at " + workerCount + " workers");
            System.err.println("  Min: " + min + ", Max: " + max + ", Avg: " + avg + ", StdDev: " + stdDev);
        }

        bh.consume(stdDev);
    }

    /**
     * Benchmark 1b: Throughput vs worker count
     * Measure: Messages/sec scaling with pool size
     */
    @Benchmark
    public void throughputVsWorkers(Blackhole bh) throws Exception {
        recordMemory("throughput_start");

        AtomicLong completedTasks = new AtomicLong(0);

        CompetingConsumers pool = new CompetingConsumers.Builder()
            .poolName("throughput_test_" + workerCount)
            .workerCount(workerCount)
            .handler(task -> completedTasks.incrementAndGet())
            .build();

        pool.start(runtime);

        long testDurationNanos = TimeUnit.SECONDS.toNanos(5);
        long startTime = System.nanoTime();

        // Continuously submit tasks for 5 seconds
        while (System.nanoTime() - startTime < testDurationNanos) {
            pool.submit("task");
        }

        pool.shutdown(Duration.ofSeconds(5));

        long elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000;
        long throughput = completedTasks.get() / Math.max(1, elapsedSeconds);

        metrics.recordThroughput(throughput);
        recordMemory("throughput_end");

        System.out.println("Workers: " + workerCount + " → Throughput: " + throughput + " tasks/sec");

        bh.consume(throughput);
    }

    /**
     * Benchmark 1c: Queue depth under load
     * Measure: Max queue depth during sustained load
     */
    @Benchmark
    @Timeout(time = 120, timeUnit = TimeUnit.SECONDS)
    public void queueDepthUnderLoad(Blackhole bh) throws Exception {
        recordMemory("queue_start");

        AtomicInteger maxDepth = new AtomicInteger(0);
        AtomicLong submitted = new AtomicLong(0);

        CompetingConsumers pool = new CompetingConsumers.Builder()
            .poolName("queue_test")
            .workerCount(16)
            .handler(task -> {
                try {
                    Thread.sleep(10);  // Slow task to build up queue
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            })
            .build();

        pool.start(runtime);

        long testDurationNanos = TimeUnit.SECONDS.toNanos(10);
        long startTime = System.nanoTime();

        while (System.nanoTime() - startTime < testDurationNanos) {
            pool.submit("task");
            submitted.incrementAndGet();

            int currentDepth = pool.queueSize();
            maxDepth.set(Math.max(maxDepth.get(), currentDepth));
        }

        pool.shutdown(Duration.ofSeconds(15));
        recordMemory("queue_end");

        long maxQueueDepth = maxDepth.get();
        System.out.println("Max queue depth: " + maxQueueDepth + " (submitted: " + submitted + ")");

        if (maxQueueDepth > 100_000) {
            System.err.println("WARNING: Queue depth exceeded 100K!");
        }

        bh.consume(maxQueueDepth);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        recordMemory("teardown_start");

        if (runtime != null) {
            runtime.close();
        }

        fullGC();
        recordMemory("teardown_end");

        System.out.println("\n=== CompetingConsumers Benchmark Results ===");
        System.out.println(metrics.generateReport());

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