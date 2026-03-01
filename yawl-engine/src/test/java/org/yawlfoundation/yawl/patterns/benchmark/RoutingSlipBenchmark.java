package org.yawlfoundation.yawl.patterns.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.YawlActorRuntime;
import org.yawlfoundation.yawl.engine.agent.patterns.RoutingSlip;
import org.yawlfoundation.yawl.engine.agent.patterns.RoutingSlip.WorkWithSlip;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH Benchmark suite for Routing Slip pattern.
 * Tests routing throughput, multi-hop latency, and registry growth.
 *
 * Scenario: Work items flow through a routing slip [A → B → C → ...].
 * Each actor processes work and forwards to next actor on slip.
 *
 * Measurements:
 * - Test 3a: Routing speed with various slip lengths (1-10 hops)
 * - Test 3b: Multi-hop latency under different throughput levels
 * - Test 3c: Registry growth detection for routing slip correlation tracking
 *
 * Run with:
 * java -jar benchmarks.jar RoutingSlipBenchmark -f 1 -i 10 -wi 3 -prof gc
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
public class RoutingSlipBenchmark extends BenchmarkBase {
    private RoutingSlip routingSlip;
    private YawlActorRuntime runtime;
    private List<ActorRef> routingActors;
    private AtomicInteger workId;
    private final MetricsCollector metrics = new MetricsCollector("RoutingSlip");

    @Setup(Level.Trial)
    public void setup() {
        recordMemory("setup_start");

        runtime = new YawlActorRuntime();
        routingSlip = new RoutingSlip(runtime);
        routingActors = new ArrayList<>();
        workId = new AtomicInteger(0);

        // Spawn 50 routing actors for slip routes
        for (int i = 0; i < 50; i++) {
            final int actorId = i;
            ActorRef actor = runtime.spawn(mailbox -> {
                Object msg = mailbox.recv();
                if (msg instanceof WorkWithSlip work) {
                    // Process work (simulate processing time)
                    try {
                        Thread.sleep((long) (Math.random() * 5));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // Forward to next on slip if more steps
                    if (work.hasMoreStops()) {
                        ActorRef nextActor = work.nextRecipient();
                        nextActor.tell(work.withSlipAdvanced());
                    } else {
                        // End of slip - notify completion
                        routingSlip.notifyCompletion(work.correlationId());
                    }
                }
            });
            routingActors.add(actor);
        }

        recordMemory("setup_end");
        fullGC();
        recordMemory("setup_after_gc");
    }

    /**
     * Test 3a: Routing speed with various slip lengths
     * Measures: Work items routed per second at different hop counts
     * Expected: Throughput decreases with longer slips (more actor hops)
     * Range: 1K-10K items/sec for 1-hop, 100-1K for 10-hop
     */
    @Benchmark
    @Param({"1", "3", "10"})
    public void routingSpeed(int slipLength, Blackhole bh) throws Exception {
        long startTime = System.nanoTime();
        int routedCount = 0;

        // Fire routing operations for 1 second
        long endTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);

        while (System.nanoTime() < endTime) {
            int id = workId.getAndIncrement();

            // Build routing slip with specified length
            Deque<ActorRef> slip = new ArrayDeque<>();
            for (int i = 0; i < slipLength; i++) {
                slip.add(routingActors.get((id * slipLength + i) % routingActors.size()));
            }

            WorkWithSlip work = routingSlip.createWork(
                "work-" + id,
                "payload-" + id,
                slip
            );

            // Initiate routing (fire and forget for throughput)
            routingSlip.initiate(work);
            routedCount++;
        }

        long actualTime = System.nanoTime() - startTime;
        double throughput = routedCount / (actualTime / 1_000_000_000.0);

        metrics.recordThroughput((long) throughput);
        bh.consume(routedCount);
    }

    /**
     * Test 3b: Multi-hop latency under different concurrency
     * Measures: Time from work initiation to completion across all hops
     * Expected: p50=5-20ms per hop, p99=50-200ms depending on actor count
     */
    @Benchmark
    @Param({"10", "100", "1000"})
    public void multiHopLatency(int concurrency, Blackhole bh) throws Exception {
        List<CompletableFuture<Void>> futures = new ArrayList<>(concurrency);

        // Fire concurrent routing operations with 5-hop slip
        for (int i = 0; i < concurrency; i++) {
            int id = workId.getAndIncrement();
            long startTime = System.nanoTime();

            // Build 5-hop routing slip
            Deque<ActorRef> slip = new ArrayDeque<>();
            for (int hop = 0; hop < 5; hop++) {
                slip.add(routingActors.get((id * 5 + hop) % routingActors.size()));
            }

            WorkWithSlip work = routingSlip.createWork(
                "work-" + id,
                "payload-" + id,
                slip
            );

            // Initiate routing and track completion
            CompletableFuture<Void> future = routingSlip.initiateWithCompletion(work)
                .whenComplete((v, ex) -> {
                    long endTime = System.nanoTime();
                    double latencyMs = (endTime - startTime) / 1_000_000.0;
                    metrics.recordLatency(latencyMs);
                });

            futures.add(future);
        }

        // Wait for all routing to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        bh.consume(futures.size());
    }

    /**
     * Test 3c: Registry growth detection for routing correlations
     * Measures: Memory growth from pending routing slip operations
     * Expected: Registry cleaned up after completion, size should stabilize
     *
     * Failure threshold: Unbounded growth indicates leak in correlation ID mapping
     */
    @Benchmark
    @Timeout(time = 180, timeUnit = TimeUnit.SECONDS)
    public void registryGrowthDetection(Blackhole bh) throws Exception {
        recordMemory("registry_start");

        // Fire 100K routing operations with various slip lengths
        for (int batch = 0; batch < 10; batch++) {
            for (int i = 0; i < 10_000; i++) {
                int id = workId.getAndIncrement();

                // Vary slip length: 1-10 hops
                int slipLength = 1 + (id % 10);

                // Build routing slip
                Deque<ActorRef> slip = new ArrayDeque<>();
                for (int hop = 0; hop < slipLength; hop++) {
                    slip.add(routingActors.get((id * slipLength + hop) % routingActors.size()));
                }

                WorkWithSlip work = routingSlip.createWork(
                    "work-" + id,
                    "payload-" + id,
                    slip
                );

                // Fire routing operation
                routingSlip.initiate(work);
            }

            // Let routing operations flow through system
            Thread.sleep(100);
            recordMemory("registry_batch_" + batch);

            // Check for unbounded growth
            if (batch > 0 && isHeapGrowingUnbounded(800)) {
                throw new AssertionError("Registry growth detected at batch " + batch +
                    " - routing correlation map not cleaned up");
            }
        }

        recordMemory("registry_end");
        fullGC();
        recordMemory("registry_after_gc");

        bh.consume(workId.get());
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        recordMemory("teardown_start");

        if (runtime != null) {
            runtime.shutdown();
        }

        fullGC();
        recordMemory("teardown_end");

        System.out.println("\n=== RoutingSlip Benchmark Results ===");
        System.out.println(metrics.generateReport());

        System.out.println("\nMemory Snapshots:");
        for (MemorySnapshot snap : snapshots) {
            System.out.println("  " + snap);
        }

        long growthMB = getHeapGrowth("setup_after_gc", "teardown_end") / (1024 * 1024);
        System.out.println("\nTotal heap growth: " + growthMB + " MB");

        if (growthMB > 500) {
            System.err.println("WARNING: Significant memory growth detected in routing slip!");
        }

        System.out.println("\n=== Key Measurements ===");
        System.out.println("Routing Throughput: " + metrics.getAvgThroughput() + " items/sec");
        System.out.println("Multi-hop Latency (p50): " + String.format("%.2f ms", metrics.getP50Latency()));
        System.out.println("Multi-hop Latency (p99): " + String.format("%.2f ms", metrics.getP99Latency()));
        System.out.println("Max Latency: " + String.format("%.2f ms", metrics.getMaxLatency()));
    }
}
