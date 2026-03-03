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
import org.yawlfoundation.yawl.engine.agent.patterns.RequestReply;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMH Benchmark suite for RequestReply pattern.
 * Tests ask/reply throughput, timeout cleanup rate, and memory leak detection.
 *
 * Run with:
 * java -jar benchmarks.jar RequestReplyBenchmark -f 1 -i 10 -wi 3 -prof gc
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
public class RequestReplyBenchmark extends BenchmarkBase {
    @Param({"10", "100", "1000", "10000"})
    public int concurrency = 10;

    private VirtualThreadRuntime runtime;
    private List<ActorRef> responders;
    private AtomicLong correlationIdCounter;
    private final MetricsCollector metrics = new MetricsCollector("RequestReply");

    @Setup(Level.Trial)
    public void setup() {
        recordMemory("setup_start");

        runtime = new VirtualThreadRuntime();
        responders = new ArrayList<>();
        correlationIdCounter = new AtomicLong(0);

        // Spawn 1000 responder actors
        for (int i = 0; i < 1000; i++) {
            final int responderIdx = i;
            ActorRef responder = runtime.spawn(mailbox -> {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = mailbox.recv();
                    if (msg instanceof Msg.Query query) {
                        // Send reply back via RequestReply dispatcher
                        String result = "responder_" + responderIdx + "_result";
                        RequestReply.dispatch(new Msg.Reply(query.correlationId(), result, null));
                    }
                }
            });
            responders.add(responder);
        }

        recordMemory("setup_end");
        fullGC();
        recordMemory("setup_after_gc");
    }

    /**
     * Benchmark 1a: Ask throughput at various concurrency levels
     * Measure: Async asks completed per second
     */
    @Benchmark
    public void askThroughput(Blackhole bh) throws Exception {
        long startTime = System.nanoTime();

        // Fire off N concurrent asks
        List<CompletableFuture<Object>> futures = new ArrayList<>(concurrency);
        for (int i = 0; i < concurrency; i++) {
            ActorRef target = responders.get(i % responders.size());
            long cid = correlationIdCounter.incrementAndGet();
            Msg.Query query = new Msg.Query(cid, null, "GET_STATUS", null);
            CompletableFuture<Object> reply = RequestReply.ask(target, query, Duration.ofSeconds(5));
            futures.add(reply);
        }

        // Wait for all replies
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        long endTime = System.nanoTime();
        long latencyMs = (endTime - startTime) / 1_000_000;

        metrics.recordLatency(latencyMs);
        bh.consume(futures);
    }

    /**
     * Benchmark 1b: Timeout cleanup rate
     * Measure: How fast can we clean up pending requests after timeout?
     */
    @Benchmark
    @Timeout(time = 120, timeUnit = TimeUnit.SECONDS)
    public void timeoutCleanupRate(Blackhole bh) throws Exception {
        recordMemory("cleanup_start");

        // Fire 100K asks with short timeout
        for (int i = 0; i < 100_000; i++) {
            long cid = correlationIdCounter.incrementAndGet();
            ActorRef target = responders.get(i % responders.size());
            Msg.Query query = new Msg.Query(cid, null, "TIMEOUT_TEST", null);
            RequestReply.ask(target, query, Duration.ofMillis(50));  // Short timeout
        }

        // Wait for timeouts to fire
        Thread.sleep(1000);

        recordMemory("cleanup_after_timeout");
        fullGC();
        recordMemory("cleanup_after_gc");

        long growth = getHeapGrowth("cleanup_start", "cleanup_after_gc");
        bh.consume(growth);
    }

    /**
     * Benchmark 1c: Correlation ID leak detection
     * Measure: Registry size after 1M ask/reply cycles
     * Expected: Registry should be cleaned up, size ≤ 1K
     */
    @Benchmark
    @Timeout(time = 120, timeUnit = TimeUnit.SECONDS)
    public void correlationIdLeakDetection(Blackhole bh) throws Exception {
        recordMemory("leak_detection_start");

        List<CompletableFuture<Object>> activeFutures = new ArrayList<>();

        // 1M asks over time
        for (int cycle = 0; cycle < 100; cycle++) {
            // Clear old futures that should have completed
            activeFutures.removeIf(f -> f.isDone());

            // Add new asks
            for (int i = 0; i < 10_000; i++) {
                long cid = correlationIdCounter.incrementAndGet();
                ActorRef target = responders.get((cycle * 10_000 + i) % responders.size());
                Msg.Query query = new Msg.Query(cid, null, "LEAK_TEST", null);
                CompletableFuture<Object> reply = RequestReply.ask(target, query, Duration.ofSeconds(10));
                activeFutures.add(reply);
            }

            // Every 100K, record metrics
            if (cycle % 10 == 0) {
                recordMemory("leak_detection_cycle_" + cycle);
                if (cycle > 0 && isHeapGrowingUnbounded(500)) {
                    throw new AssertionError("Unbounded heap growth detected at cycle " + cycle);
                }
            }
        }

        recordMemory("leak_detection_end");
        bh.consume(activeFutures.size());
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        recordMemory("teardown_start");

        if (runtime != null) {
            runtime.close();
        }

        fullGC();
        recordMemory("teardown_end");

        System.out.println("\n=== RequestReply Benchmark Results ===");
        System.out.println(metrics.generateReport());

        System.out.println("\nMemory Snapshots:");
        for (MemorySnapshot snap : snapshots) {
            System.out.println("  " + snap);
        }

        long growthMB = getHeapGrowth("setup_after_gc", "teardown_end") / (1024 * 1024);
        System.out.println("\nTotal heap growth: " + growthMB + " MB");

        if (growthMB > 500) {
            System.err.println("WARNING: Significant memory growth detected!");
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