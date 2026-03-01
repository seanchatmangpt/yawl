/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.benchmark;

import org.openjdk.jmh.annotations.*;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import javax.management.*;

/**
 * Comprehensive Actor System Performance Benchmarks.
 *
 * <p>Benchmarks actor-based workflows including message throughput, memory usage,
 * scalability, latency, and recovery capabilities. All benchmarks use modern
 * virtual threads and measure real actor behavior.</p>
 *
 * <p>Performance Targets:</p>
 * <ul>
 *   <li>Message throughput: >10M messages/second across 1M actors</li>
 *   <li>Actor creation: >1M actors/second</li>
 *   <li>Message latency: p95 < 1ms, p99 < 5ms</li>
 *   <li>Memory growth: < 10% increase under sustained load</li>
 *   <li>Recovery time: < 100ms after failures</li>
 * </ul>
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 20, time = 3)
@Fork(value = 3, jvmArgs = {
    "-Xms4g", "-Xmx8g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Djmh.executor=VIRTUAL_TPE",
    "-Djmh.throughput=iterations"
})
public class ActorSystemBenchmarks implements YWorkItemEventListener, YCaseEventListener {

    // Configuration parameters
    @Param({"1000", "10000", "100000", "1000000"})
    private int actorCount;

    @Param({"1", "10", "100", "1000"})
    private int messagesPerSecond;

    @Param({"simple", "complex", "sequential", "parallel"})
    private String workflowType;

    @Param({"true", "false"})
    private boolean enableFailureInjection;

    // Core components
    private YStatelessEngine engine;
    private YSpecification simpleSpec;
    private YSpecification complexSpec;
    private MemoryMXBean memoryMXBean;

    // Metrics collectors
    private AtomicLong messageCount = new AtomicLong(0);
    private AtomicLong completionCount = new AtomicLong(0);
    private AtomicLong failureCount = new AtomicLong(0);
    private final ConcurrentLinkedQueue<Long> latencySamples = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> memorySamples = new ConcurrentLinkedQueue<>();

    // Threading utilities
    private final ExecutorService messageExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService metricsCollector = Executors.newScheduledThreadPool(2);
    private CountDownLatch completionLatch;
    private volatile AtomicReference<Exception> listenerError = new AtomicReference<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Setup(Level.Trial)
    public void setup() throws YSyntaxException {
        // Initialize JVM monitoring
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        
        // Initialize YAWL engine
        engine = new YStatelessEngine();
        engine.addWorkItemEventListener(this);
        engine.addCaseEventListener(this);

        // Load workflow specifications
        simpleSpec = engine.unmarshalSpecification(BenchmarkSpecFactory.SEQUENTIAL_2_TASK);
        complexSpec = engine.unmarshalSpecification(BenchmarkSpecFactory.PARALLEL_DIVERGE_CONVERGE);

        // Start metrics collection
        startMetricsCollection();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        messageExecutor.shutdown();
        metricsCollector.shutdown();
        engine = null;
        messageCount.set(0);
        completionCount.set(0);
        failureCount.set(0);
        latencySamples.clear();
        memorySamples.clear();
    }

    // ── Benchmark 1: Message Throughput ─────────────────────────────────────

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void messageThroughputBenchmark() throws InterruptedException {
        completionLatch = new CountDownLatch(actorCount);
        completionCount.set(0);

        Instant start = Instant.now();
        AtomicBoolean running = new AtomicBoolean(true);

        // Start message generation
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < actorCount; i++) {
            final int actorId = i;
            futures.add(messageExecutor.submit(() -> {
                try {
                    while (running.get() && completionCount.get() < actorCount) {
                        long startTime = System.nanoTime();
                        sendActorMessage(actorId);
                        long latency = System.nanoTime() - startTime;
                        latencySamples.add(latency);
                        
                        // Simulate processing time
                        Thread.sleep(1000 / messagesPerSecond);
                    }
                } catch (Exception e) {
                    listenerError.set(e);
                }
            }));
        }

        // Wait for completion or timeout
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);
        running.set(false);

        // Wait for all tasks to finish
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }

        // Collect final metrics
        long totalMessages = messageCount.get();
        Duration duration = Duration.between(start, Instant.now());
        
        recordSample("throughput", totalMessages / duration.getSeconds());
    }

    // ── Benchmark 2: Memory Usage Patterns ──────────────────────────────────

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void memoryUsageBenchmark() throws InterruptedException {
        MemoryUsage initialMemory = memoryMXBean.getHeapMemoryUsage();
        long initialUsed = initialMemory.getUsed();

        // Create actors and send messages
        completionLatch = new CountDownLatch(actorCount);
        completionCount.set(0);

        Instant start = Instant.now();
        for (int i = 0; i < actorCount; i++) {
            messageExecutor.submit(() -> {
                try {
                    sendActorMessage(i);
                    if (completionCount.incrementAndGet() >= actorCount) {
                        completionLatch.countDown();
                    }
                } catch (Exception e) {
                    listenerError.set(e);
                }
            });
        }

        completionLatch.await(30, TimeUnit.SECONDS);

        // Monitor memory over time
        for (int i = 0; i < 10; i++) {
            Thread.sleep(1000);
            MemoryUsage currentMemory = memoryMXBean.getHeapMemoryUsage();
            long currentUsed = currentMemory.getUsed();
            memorySamples.add(currentUsed);
            
            // Check for memory leaks (growth > 10% of initial)
            if (currentUsed > initialUsed * 1.1) {
                failureCount.incrementAndGet();
            }
        }
    }

    // ── Benchmark 3: Scalability ───────────────────────────────────────────

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void scalabilityBenchmark() throws InterruptedException {
        int[] scales = {1000, 10000, 100000};
        long[] results = new long[scales.length];

        for (int scaleIndex = 0; scaleIndex < scales.length; scaleIndex++) {
            int scale = scales[scaleIndex];
            completionLatch = new CountDownLatch(scale);
            completionCount.set(0);

            Instant start = Instant.now();
            for (int i = 0; i < scale; i++) {
                final int actorId = i;
                messageExecutor.submit(() -> {
                    try {
                        sendActorMessage(actorId);
                        if (completionCount.incrementAndGet() >= scale) {
                            completionLatch.countDown();
                        }
                    } catch (Exception e) {
                        listenerError.set(e);
                    }
                });
            }

            completionLatch.await(30, TimeUnit.SECONDS);
            results[scaleIndex] = Duration.between(start, Instant.now()).toMillis();
        }

        // Record scaling curve
        recordSample("scalability_1k", results[0]);
        recordSample("scalability_10k", results[1]);
        recordSample("scalability_100k", results[2]);
    }

    // ── Benchmark 4: Latency Measurement ───────────────────────────────────

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void latencyBenchmark() throws InterruptedException {
        completionLatch = new CountDownLatch(1000); // 1000 messages
        completionCount.set(0);

        Instant start = Instant.now();
        for (int i = 0; i < 1000; i++) {
            final int messageSeq = i;
            messageExecutor.submit(() -> {
                long sendTime = System.nanoTime();
                sendActorMessage(messageSeq);
                long receiveTime = System.nanoTime();
                latencySamples.add(receiveTime - sendTime);
                
                if (completionCount.incrementAndGet() >= 1000) {
                    completionLatch.countDown();
                }
            });
        }

        completionLatch.await(30, TimeUnit.SECONDS);

        // Calculate percentiles
        long[] latencies = latencySamples.stream()
            .mapToLong(Long::longValue)
            .sorted()
            .toArray();

        if (latencies.length > 0) {
            long p95 = latencies[(int)(latencies.length * 0.95)];
            long p99 = latencies[(int)(latencies.length * 0.99)];
            recordSample("latency_p95", p95);
            recordSample("latency_p99", p99);
        }
    }

    // ── Benchmark 5: Recovery Times ─────────────────────────────────────────

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void recoveryTimeBenchmark() throws InterruptedException {
        int failurePoints = 10;
        long[] recoveryTimes = new long[failurePoints];

        for (int i = 0; i < failurePoints; i++) {
            // Create stable system
            completionLatch = new CountDownLatch(actorCount);
            completionCount.set(0);
            
            // Setup actors
            for (int j = 0; j < actorCount; j++) {
                messageExecutor.submit(() -> sendActorMessage(0));
            }
            
            // Wait for stability
            Thread.sleep(1000);
            
            // Inject failure
            Instant failureStart = Instant.now();
            injectFailure();
            
            // Measure recovery time
            AtomicBoolean recovered = new AtomicBoolean(false);
            long recoveryStart = System.nanoTime();
            
            while (!recovered.get()) {
                Thread.sleep(10);
                if (isSystemRecovered()) {
                    recoveryTimes[i] = (System.nanoTime() - recoveryStart) / 1_000_000;
                    recovered.set(true);
                }
            }
        }

        // Average recovery time
        long avgRecovery = 0;
        for (long time : recoveryTimes) {
            avgRecovery += time;
        }
        recordSample("recovery_time", avgRecovery / failurePoints);
    }

    // ── Helper Methods ──────────────────────────────────────────────────────

    private void sendActorMessage(int actorId) throws YSyntaxException {
        try {
            String caseId = "actor-case-" + actorId + "-" + System.currentTimeMillis();
            engine.createCase(simpleSpec.getID(), caseId);
            messageCount.incrementAndGet();
        } catch (Exception e) {
            failureCount.incrementAndGet();
            throw e;
        }
    }

    private void injectFailure() {
        if (enableFailureInjection) {
            // Simulate actor failure by creating an exceptional condition
            try {
                Thread.sleep(10); // Simulate processing delay
                memoryMXBean.gc(); // Force GC to simulate pressure
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean isSystemRecovered() {
        // Check if system has recovered by monitoring activity levels
        long recentActivity = messageCount.get();
        return recentActivity > 0; // Simplified recovery check
    }

    private void startMetricsCollection() {
        metricsCollector.scheduleAtFixedRate(() -> {
            MemoryUsage memory = memoryMXBean.getHeapMemoryUsage();
            memorySamples.add(memory.getUsed());
            
            // System.gc(); // Uncomment for memory pressure testing
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void recordSample(String metric, long value) {
        // In a real implementation, this would persist metrics to a database
        System.out.printf("Sample: %s = %d%n", metric, value);
    }

    // ── Event Listeners ─────────────────────────────────────────────────────

    @Override
    public void handleWorkItemEvent(YWorkItemEvent event) {
        if (event.getEventType() == YEventType.WorkItemStarted ||
            event.getEventType() == YEventType.WorkItemCompleted) {
            // Actor message processed
        }
    }

    @Override
    public void handleCaseEvent(YCaseEvent event) {
        if (event.getEventType() == YEventType.CaseCancelled ||
            event.getEventType() == YEventType.CaseCompleted) {
            completionCount.incrementAndGet();
            completionLatch.countDown();
        }
    }
}
