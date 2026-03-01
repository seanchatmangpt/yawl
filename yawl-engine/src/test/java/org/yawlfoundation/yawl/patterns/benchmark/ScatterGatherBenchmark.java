package org.yawlfoundation.yawl.patterns.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.YawlActorRuntime;
import org.yawlfoundation.yawl.engine.agent.patterns.ScatterGather;
import org.yawlfoundation.yawl.engine.agent.patterns.ScatterGather.ScatterRequest;
import org.yawlfoundation.yawl.engine.agent.patterns.ScatterGather.ScatterResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    private ScatterGather scatterGather;
    private YawlActorRuntime runtime;
    private List<ActorRef> workers;
    private AtomicInteger scatterId;
    private final MetricsCollector metrics = new MetricsCollector("ScatterGather");

    @Setup(Level.Trial)
    public void setup() {
        recordMemory("setup_start");

        runtime = new YawlActorRuntime();
        scatterGather = new ScatterGather(runtime);
        workers = new ArrayList<>();
        scatterId = new AtomicInteger(0);

        // Spawn 100 worker actors that echo back results
        for (int i = 0; i < 100; i++) {
            final int workerId = i;
            ActorRef worker = runtime.spawn(mailbox -> {
                Object msg = mailbox.recv();
                if (msg instanceof ScatterRequest request) {
                    // Simulate work with random delay
                    try {
                        Thread.sleep((long) (Math.random() * 10));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // Send result back to coordinator
                    String result = "worker-" + workerId + "-processed-" + request.payload();
                    scatterGather.sendResult(request.correlationId(), result);
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
    @Param({"10", "50", "100"})
    public void scatterThroughput(int workerCount, Blackhole bh) throws Exception {
        long startTime = System.nanoTime();
        int scatterCount = 0;

        // Use only specified number of workers
        List<ActorRef> activeWorkers = workers.subList(0, Math.min(workerCount, workers.size()));

        // Fire scatter operations for 1 second
        long endTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);

        while (System.nanoTime() < endTime) {
            int id = scatterId.getAndIncrement();
            ScatterRequest request = new ScatterRequest(
                "scatter-" + id,
                activeWorkers,
                "payload-" + id,
                Duration.ofSeconds(5)
            );

            // Initiate scatter (fire and forget for throughput test)
            CompletableFuture<List<ScatterResult>> results =
                scatterGather.scatter(request);

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
    @Param({"10", "100", "1000"})
    public void gatherLatency(int concurrencyLevel, Blackhole bh) throws Exception {
        List<CompletableFuture<List<ScatterResult>>> futures = new ArrayList<>(concurrencyLevel);

        // Fire concurrent scatter-gather operations
        for (int i = 0; i < concurrencyLevel; i++) {
            int id = scatterId.getAndIncrement();
            long startTime = System.nanoTime();

            ScatterRequest request = new ScatterRequest(
                "scatter-" + id,
                workers.subList(0, 50),  // Use 50 workers
                "payload-" + id,
                Duration.ofSeconds(5)
            );

            CompletableFuture<List<ScatterResult>> future =
                scatterGather.scatter(request)
                    .whenComplete((results, ex) -> {
                        long endTime = System.nanoTime();
                        double latencyMs = (endTime - startTime) / 1_000_000.0;
                        metrics.recordLatency(latencyMs);
                    });

            futures.add(future);
        }

        // Wait for all gathers to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        bh.consume(futures.size());
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

                ScatterRequest request = new ScatterRequest(
                    "scatter-timeout-" + id,
                    workers.subList(0, 10),  // Use 10 workers
                    "payload-" + id,
                    Duration.ofMillis(50)  // Short timeout to trigger cleanup
                );

                // Fire without waiting for result
                scatterGather.scatter(request);
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
            runtime.shutdown();
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
}
