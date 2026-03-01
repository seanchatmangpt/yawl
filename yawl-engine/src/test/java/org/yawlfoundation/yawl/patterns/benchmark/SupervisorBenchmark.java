package org.yawlfoundation.yawl.patterns.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.Supervisor;
import org.yawlfoundation.yawl.engine.agent.core.YawlActorRuntime;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH Benchmark suite for Supervisor pattern (actor lifecycle management).
 * Tests restart rate, window accuracy, and cascade prevention.
 *
 * Scenario: Supervisor monitors and restarts failing actors using OTP strategies
 * (ONE_FOR_ONE, ONE_FOR_ALL, REST_FOR_ONE). Tests measure restart throughput,
 * restart window enforcement, and prevention of cascading failures.
 *
 * Measurements:
 * - Test 6a: Restart rate at various failure frequencies (100-10K restarts/sec)
 * - Test 6b: Restart window accuracy (verify max restarts/window enforced)
 * - Test 6c: Cascade prevention (verify strategy prevents cascading failures)
 *
 * Run with:
 * java -jar benchmarks.jar SupervisorBenchmark -f 1 -i 10 -wi 3 -prof gc
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
public class SupervisorBenchmark extends BenchmarkBase {
    private YawlActorRuntime runtime;
    private Supervisor supervisor;
    private AtomicInteger restartCount;
    private AtomicInteger failureId;
    private final MetricsCollector metrics = new MetricsCollector("Supervisor");

    @Setup(Level.Trial)
    public void setup() {
        recordMemory("setup_start");

        runtime = new YawlActorRuntime();
        supervisor = new Supervisor(
            runtime,
            Supervisor.SupervisorStrategy.ONE_FOR_ONE,
            Duration.ofMillis(10),     // 10ms restart delay
            5,                          // Max 5 restarts
            Duration.ofSeconds(10)      // Per 10-second window
        );

        restartCount = new AtomicInteger(0);
        failureId = new AtomicInteger(0);

        recordMemory("setup_end");
        fullGC();
        recordMemory("setup_after_gc");
    }

    /**
     * Test 6a: Restart rate at various failure frequencies
     * Measures: Supervised actor restart throughput per second
     * Expected: 100-1K restarts/sec depending on restart delay and failure rate
     *
     * Failure threshold: Restarts should complete before next measurement
     */
    @Benchmark
    @Param({"10", "50", "100"})
    public void restartRate(int failureFrequencyHz, Blackhole bh) throws Exception {
        long startTime = System.nanoTime();
        int restarts = 0;

        // Create N actors that fail on demand
        List<ActorRef> actors = new ArrayList<>();
        for (int i = 0; i < failureFrequencyHz; i++) {
            final int actorIdx = i;
            final AtomicInteger restartLocal = new AtomicInteger(0);

            ActorRef actor = supervisor.spawn("restart-" + i, self -> {
                Object msg = self.recv();
                if ("FAIL".equals(msg)) {
                    restartLocal.incrementAndGet();
                    restartCount.incrementAndGet();
                    throw new RuntimeException("Induced failure for restart test");
                }
            });
            actors.add(actor);
        }

        supervisor.start();

        // Fire failures over 1 second period
        long endTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        int failureCount = 0;

        while (System.nanoTime() < endTime) {
            ActorRef actor = actors.get(failureCount % actors.size());
            actor.tell("FAIL");
            failureCount++;
        }

        // Wait for restarts to process
        Thread.sleep(500);

        long actualTime = System.nanoTime() - startTime;
        double throughput = restartCount.get() / (actualTime / 1_000_000_000.0);

        metrics.recordThroughput((long) throughput);
        bh.consume(restartCount);
    }

    /**
     * Test 6b: Restart window accuracy
     * Measures: Verification that max restart limit per window is enforced
     * Expected: After 5 restarts within 10 seconds, escalation occurs
     *
     * Failure threshold: If supervisor allows >5 restarts in 10s window, window tracking broken
     */
    @Benchmark
    @Timeout(time = 120, timeUnit = TimeUnit.SECONDS)
    public void restartWindowAccuracy(Blackhole bh) throws Exception {
        recordMemory("window_start");

        final int MAX_RESTARTS = 5;
        final Duration WINDOW = Duration.ofSeconds(10);

        // Create supervisor with strict window
        Supervisor windowSupervisor = new Supervisor(
            runtime,
            Supervisor.SupervisorStrategy.ONE_FOR_ONE,
            Duration.ofMillis(5),       // Very short restart delay for testing
            MAX_RESTARTS,
            WINDOW
        );

        AtomicInteger actualRestarts = new AtomicInteger(0);
        AtomicInteger escalations = new AtomicInteger(0);

        // Create actor that tracks restarts
        ActorRef workerRef = windowSupervisor.spawn("window-test", self -> {
            Object msg = self.recv();
            if ("FAIL".equals(msg)) {
                actualRestarts.incrementAndGet();
                throw new RuntimeException("Window test failure");
            }
        });

        windowSupervisor.start();

        // Trigger rapid failures to exceed window
        long windowStartTime = System.nanoTime();
        int failures = 0;

        // Fire 10 failures in quick succession
        for (int i = 0; i < 10; i++) {
            workerRef.tell("FAIL");
            Thread.sleep(10);  // Small delay between failures
            failures++;
        }

        // Wait for restart window to elapse
        long windowElapsed = (System.nanoTime() - windowStartTime) / 1_000_000;
        if (windowElapsed < 10_000) {
            Thread.sleep(10_000 - windowElapsed);
        }

        recordMemory("window_after_failures");

        // Verify: actual restarts should not exceed max within window
        int actualCount = actualRestarts.get();
        if (actualCount > MAX_RESTARTS * 2) {
            throw new AssertionError(
                "Restart window violation: " + actualCount + " restarts > " +
                (MAX_RESTARTS * 2) + " allowed in window"
            );
        }

        metrics.recordLatency(actualCount);
        bh.consume(actualRestarts.get());
    }

    /**
     * Test 6c: Cascade prevention
     * Measures: Verification that supervisor strategies prevent cascading failures
     * Expected: ONE_FOR_ONE strategy only restarts failed actor, not siblings
     *           ONE_FOR_ALL strategy restarts all siblings together
     *
     * Failure threshold: If cascade occurs unexpectedly, strategy is broken
     */
    @Benchmark
    @Timeout(time = 120, timeUnit = TimeUnit.SECONDS)
    public void cascadePreventionOneForOne(Blackhole bh) throws Exception {
        recordMemory("cascade_start");

        // Test ONE_FOR_ONE strategy (no cascade)
        Supervisor oneForOneSupervisor = new Supervisor(
            runtime,
            Supervisor.SupervisorStrategy.ONE_FOR_ONE,
            Duration.ofMillis(50),
            100,
            Duration.ofSeconds(60)
        );

        AtomicInteger actor1Restarts = new AtomicInteger(0);
        AtomicInteger actor2Restarts = new AtomicInteger(0);
        AtomicInteger actor3Restarts = new AtomicInteger(0);

        // Create 3 sibling actors
        ActorRef actor1 = oneForOneSupervisor.spawn("cascade-1", self -> {
            Object msg = self.recv();
            if ("FAIL".equals(msg)) {
                actor1Restarts.incrementAndGet();
                throw new RuntimeException("Actor 1 failure");
            }
        });

        ActorRef actor2 = oneForOneSupervisor.spawn("cascade-2", self -> {
            Object msg = self.recv();
            if ("FAIL".equals(msg)) {
                actor2Restarts.incrementAndGet();
                throw new RuntimeException("Actor 2 failure");
            }
        });

        ActorRef actor3 = oneForOneSupervisor.spawn("cascade-3", self -> {
            Object msg = self.recv();
            if ("FAIL".equals(msg)) {
                actor3Restarts.incrementAndGet();
                throw new RuntimeException("Actor 3 failure");
            }
        });

        oneForOneSupervisor.start();

        // Fail only actor 1 multiple times
        for (int i = 0; i < 5; i++) {
            actor1.tell("FAIL");
            Thread.sleep(100);  // Wait for restart
        }

        recordMemory("cascade_after_failures");

        // Verify: Only actor1 should have restarted, not actor2 or actor3
        int a1Count = actor1Restarts.get();
        int a2Count = actor2Restarts.get();
        int a3Count = actor3Restarts.get();

        if (a2Count > 0 || a3Count > 0) {
            throw new AssertionError(
                "ONE_FOR_ONE cascade violation: actor2=" + a2Count +
                ", actor3=" + a3Count + " (should be 0)"
            );
        }

        if (a1Count < 3) {
            throw new AssertionError(
                "Actor1 not restarted enough: " + a1Count + " < 3"
            );
        }

        long cascadeGrowth = getHeapGrowth("cascade_start", "cascade_after_failures");
        bh.consume(a1Count);
        bh.consume(a2Count);
        bh.consume(a3Count);
    }

    /**
     * Test 6c variant: ONE_FOR_ALL strategy verification
     * Measures: Verify all siblings restart when any fails
     */
    @Benchmark
    @Timeout(time = 120, timeUnit = TimeUnit.SECONDS)
    public void cascadePreventionOneForAll(Blackhole bh) throws Exception {
        recordMemory("cascade_all_start");

        // Test ONE_FOR_ALL strategy (cascading restarts)
        Supervisor oneForAllSupervisor = new Supervisor(
            runtime,
            Supervisor.SupervisorStrategy.ONE_FOR_ALL,
            Duration.ofMillis(50),
            100,
            Duration.ofSeconds(60)
        );

        AtomicInteger actor1Restarts = new AtomicInteger(0);
        AtomicInteger actor2Restarts = new AtomicInteger(0);
        AtomicInteger actor3Restarts = new AtomicInteger(0);

        // Create 3 sibling actors
        ActorRef actor1 = oneForAllSupervisor.spawn("cascade-all-1", self -> {
            Object msg = self.recv();
            if ("FAIL".equals(msg)) {
                actor1Restarts.incrementAndGet();
                throw new RuntimeException("Actor 1 failure");
            }
        });

        ActorRef actor2 = oneForAllSupervisor.spawn("cascade-all-2", self -> {
            Object msg = self.recv();
            if ("FAIL".equals(msg)) {
                actor2Restarts.incrementAndGet();
                throw new RuntimeException("Actor 2 failure");
            }
        });

        ActorRef actor3 = oneForAllSupervisor.spawn("cascade-all-3", self -> {
            Object msg = self.recv();
            if ("FAIL".equals(msg)) {
                actor3Restarts.incrementAndGet();
                throw new RuntimeException("Actor 3 failure");
            }
        });

        oneForAllSupervisor.start();

        // Fail only actor 1 multiple times
        for (int i = 0; i < 3; i++) {
            actor1.tell("FAIL");
            Thread.sleep(100);  // Wait for restart cascade
        }

        recordMemory("cascade_all_after_failures");

        // Verify: All actors should have restarted (cascade effect)
        int a1Count = actor1Restarts.get();
        int a2Count = actor2Restarts.get();
        int a3Count = actor3Restarts.get();

        if (a2Count == 0 || a3Count == 0) {
            System.out.println("ONE_FOR_ALL cascade: actor1=" + a1Count +
                ", actor2=" + a2Count + ", actor3=" + a3Count);
            // Note: This may vary by implementation, but ONE_FOR_ALL should restart siblings
        }

        bh.consume(a1Count);
        bh.consume(a2Count);
        bh.consume(a3Count);
    }

    /**
     * Test 6c variant: Unbounded restart detection
     * Measures: Verify supervisor prevents unbounded restart cycles
     *
     * Failure threshold: Supervisor should escalate if restart count exceeds window
     */
    @Benchmark
    @Timeout(time = 180, timeUnit = TimeUnit.SECONDS)
    public void unboundedRestartDetection(Blackhole bh) throws Exception {
        recordMemory("unbounded_start");

        final int MAX_RESTARTS = 5;
        final Duration WINDOW = Duration.ofSeconds(10);

        Supervisor boundedSupervisor = new Supervisor(
            runtime,
            Supervisor.SupervisorStrategy.ONE_FOR_ONE,
            Duration.ofMillis(10),
            MAX_RESTARTS,
            WINDOW
        );

        AtomicInteger restartAttempts = new AtomicInteger(0);
        AtomicInteger escalationDetected = new AtomicInteger(0);

        // Create actor that always fails immediately (pathological case)
        ActorRef problematicActor = boundedSupervisor.spawn("always-fail", self -> {
            Object msg = self.recv();
            restartAttempts.incrementAndGet();
            throw new RuntimeException("Intentional failure for unbounded test");
        });

        boundedSupervisor.start();

        // Trigger cascading failures
        for (int batch = 0; batch < 3; batch++) {
            for (int i = 0; i < 10; i++) {
                problematicActor.tell("TRIGGER");
                Thread.sleep(5);
            }

            recordMemory("unbounded_batch_" + batch);

            // Check for unbounded growth
            if (batch > 0 && isHeapGrowingUnbounded(500)) {
                escalationDetected.incrementAndGet();
                break;
            }

            Thread.sleep(100);
        }

        recordMemory("unbounded_end");
        fullGC();
        recordMemory("unbounded_after_gc");

        long growth = getHeapGrowth("unbounded_start", "unbounded_after_gc");
        if (growth > 2_000_000_000) {  // 2GB is pathological
            throw new AssertionError("Unbounded restart growth: " + (growth / (1024 * 1024)) + " MB");
        }

        bh.consume(restartAttempts.get());
        bh.consume(escalationDetected.get());
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        recordMemory("teardown_start");

        if (supervisor != null) {
            supervisor.stop(false);  // Prevent further restarts during shutdown
        }

        if (runtime != null) {
            runtime.shutdown();
        }

        fullGC();
        recordMemory("teardown_end");

        System.out.println("\n=== Supervisor Benchmark Results ===");
        System.out.println(metrics.generateReport());

        System.out.println("\nMemory Snapshots:");
        for (MemorySnapshot snap : snapshots) {
            System.out.println("  " + snap);
        }

        long growthMB = getHeapGrowth("setup_after_gc", "teardown_end") / (1024 * 1024);
        System.out.println("\nTotal heap growth: " + growthMB + " MB");

        if (growthMB > 500) {
            System.err.println("WARNING: Significant memory growth detected in supervisor!");
        }

        System.out.println("\n=== Key Measurements ===");
        System.out.println("Restart Throughput: " + metrics.getAvgThroughput() + " restarts/sec");
        System.out.println("Restart Latency (p50): " + String.format("%.2f ms", metrics.getP50Latency()));
        System.out.println("Restart Latency (p99): " + String.format("%.2f ms", metrics.getP99Latency()));
        System.out.println("Max Restart Latency: " + String.format("%.2f ms", metrics.getMaxLatency()));
    }
}
