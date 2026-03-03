package org.yawlfoundation.yawl.benchmark.patterns;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.Msg;
import org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime;
import org.yawlfoundation.yawl.engine.agent.patterns.ScatterGather;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH Benchmark suite for Scatter-Gather pattern.
 * Tests scatter throughput, gather latency, and registry bloat from incomplete gathers.
 *
 * Scenario: Coordinator scatters work to N workers, waits for all replies within timeout.
 *
 * Measurements:
 * - Test 2a: Scatter rate at various worker counts (1K-10K scatter ops/sec)
 * - Test 2b: Gather latency under different concurrency (p50, p99 latencies)
 * - Test 2c: Registry bloat detection for incomplete gathers
 *
 * Run with:
 * java -jar benchmarks.jar ScatterGatherBenchmark -f 1 -i 10 -wi 3 -prof gc
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
public class ScatterGatherBenchmark extends BenchmarkBase {
    private VirtualThreadRuntime runtime;
    private List<ActorRef> workers;
    private AtomicInteger scatterId;
    private final MetricsCollector metrics = new MetricsCollector("ScatterGather");

    @Param({"10", "50", "100"})
    public int workerCount;

    @Param({"10", "100", "1000"})
    public int concurrencyLevel;

    @Setup(Level.Trial)
    public void setup() {
        recordMemory("setup_start");

        runtime = new VirtualThreadRuntime();
        workers = new ArrayList<>();
        scatterId = new AtomicInteger(0);

        // Spawn 100 worker actors that echo back results
        for (int i = 0; i < 100; i++) {
            final int workerId = i;
            ActorRef worker = runtime.spawn(mailbox -> {
                Object msg = mailbox.recv();
                if (msg instanceof Msg.Query query) {
                    // Simulate work with random delay
                    try {
                        Thread.sleep((long) (Math.random() * 10));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // Send result back with correlation ID
                    String result = "worker-" + workerId + "-processed";
                    query.sender().tell(new Msg.Reply(query.correlationId(), result, null));
                }
            });
            workers.add(worker);
        }

        recordMemory("setup_end");
        fullGC();
        recordMemory("setup_after_gc");
    }

    /**
     * Test 2a: Scatter throughput at various worker counts
     * Measures: Scatter operations completed per second
     * Expected: 1K-10K scatter ops/sec depending on worker count
     */
    @Benchmark
    public void scatterThroughput(Blackhole bh) throws Exception {
        long startTime = System.nanoTime();
        int scatterCount = 0;

        // Use only specified number of workers
        List<ActorRef> activeWorkers = workers.subList(0, Math.min(workerCount, workers.size()));

        // Fire scatter operations for 1 second
        long endTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);

        while (System.nanoTime() < endTime) {
            int id = scatterId.getAndIncrement();

            // Create ScatterGather coordinator for this scatter operation
            ScatterGather coordinator = new ScatterGather(activeWorkers.size());

            // Initiate scatter (fire and forget for throughput test)
            int splitId = coordinator.scatter(
                activeWorkers.toArray(new ActorRef[0]),
                "payload-" + id
            );

            scatterCount++;
        }

        long actualTime = System.nanoTime() - startTime;
        double throughput = scatterCount / (actualTime / 1_000_000_000.0);

        metrics.recordThroughput((long) throughput);
        bh.consume(scatterCount);
    }

    /**
     * Test 2b: Gather latency under various concurrency levels
     * Measures: Time from scatter initiation to all replies collected
     * Expected: p50=10-50ms, p99=100-500ms depending on worker count
     */
    @Benchmark
    public void gatherLatency(Blackhole bh) throws Exception {
        List<Long> latencies = new ArrayList<>(concurrencyLevel);

        // Fire concurrent scatter-gather operations
        for (int i = 0; i < concurrencyLevel; i++) {
            int id = scatterId.getAndIncrement();
            long startTime = System.nanoTime();

            int workerSubsetSize = Math.min(50, workers.size());
            ScatterGather coordinator = new ScatterGather(workerSubsetSize);
            ActorRef[] targets = workers.subList(0, workerSubsetSize)
                .toArray(new ActorRef[0]);

            try {
                // Scatter work to targets
                int splitId = coordinator.scatter(targets, "payload-" + id);

                // Gather results (blocking)
                List<Msg> results = coordinator.gather(splitId, Duration.ofSeconds(5));

                long endTime = System.nanoTime();
                double latencyMs = (endTime - startTime) / 1_000_000.0;
                metrics.recordLatency(latencyMs);
                latencies.add((endTime - startTime) / 1_000_000);
            } catch (TimeoutException e) {
                // Timeout recorded as latency
                long endTime = System.nanoTime();
                double latencyMs = (endTime - startTime) / 1_000_000.0;
                metrics.recordLatency(latencyMs);
            }
        }

        bh.consume(latencies.size());
    }

    /**
     * Test 2c: Registry bloat detection for incomplete gathers
     * Measures: Memory growth from pending scatter operations
     * Expected: Each incomplete gather holds registry entry (~1KB per entry)
     *
     * Failure threshold: If registry grows unbounded, indicates leak in correlation map
     */
    @Benchmark
    @Timeout(time = 180, timeUnit = TimeUnit.SECONDS)
    public void registryBloatDetection(Blackhole bh) throws Exception {
        recordMemory("bloat_start");

        // Fire 100K scatter operations with intentionally short timeouts
        // to force registry cleanup without waiting for all replies
        for (int batch = 0; batch < 10; batch++) {
            for (int i = 0; i < 10_000; i++) {
                int id = scatterId.getAndIncrement();

                int workerSubsetSize = 10;
                ScatterGather coordinator = new ScatterGather(workerSubsetSize);
                ActorRef[] targets = workers.subList(0, workerSubsetSize)
                    .toArray(new ActorRef[0]);

                try {
                    // Fire scatter with short timeout
                    int splitId = coordinator.scatter(targets, "payload-" + id);
                    // Try to gather with very short timeout (will timeout)
                    coordinator.gather(splitId, Duration.ofMillis(50));
                } catch (TimeoutException e) {
                    // Expected - registry should clean up
                }
            }

            // Let timeouts fire
            Thread.sleep(200);
            recordMemory("bloat_batch_" + batch);

            // Check if registry is growing unbounded
            if (batch > 0 && isHeapGrowingUnbounded(1000)) {
                throw new AssertionError("Registry bloat detected at batch " + batch +
                    " - scatter correlation map not cleaned up");
            }
        }

        recordMemory("bloat_end");
        fullGC();
        recordMemory("bloat_after_gc");

        bh.consume(scatterId.get());
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        recordMemory("teardown_start");

        if (runtime != null) {
            runtime.close();
        }

        fullGC();
        recordMemory("teardown_end");

        System.out.println("\n=== ScatterGather Benchmark Results ===");
        System.out.println(metrics.generateReport());

        System.out.println("\nMemory Snapshots:");
        for (MemorySnapshot snap : snapshots) {
            System.out.println("  " + snap);
        }

        long growthMB = getHeapGrowth("setup_after_gc", "teardown_end") / (1024 * 1024);
        System.out.println("\nTotal heap growth: " + growthMB + " MB");

        if (growthMB > 500) {
            System.err.println("WARNING: Significant memory growth detected in scatter-gather!");
        }

        System.out.println("\n=== Key Measurements ===");
        System.out.println("Scatter Throughput: " + metrics.getAvgThroughput() + " ops/sec");
        System.out.println("Gather Latency (p50): " + String.format("%.2f ms", metrics.getP50Latency()));
        System.out.println("Gather Latency (p99): " + String.format("%.2f ms", metrics.getP99Latency()));
        System.out.println("Max Latency: " + String.format("%.2f ms", metrics.getMaxLatency()));
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