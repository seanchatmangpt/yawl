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
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

/**
 * Actor System Scalability Benchmark.
 *
 * <p>Measures scalability characteristics including:
 * - Linear scaling curve (100 to 1M actors)
 * - Thread scalability (virtual vs platform threads)
 * - Resource utilization under load
 * - Bottleneck identification
 * - Throughput degradation analysis</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 3)
@Fork(value = 3, jvmArgs = {
    "-Xms4g", "-Xmx8g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Djmh.executor=VIRTUAL_TPE"
})
public class ActorScalabilityBenchmark implements YWorkItemEventListener, YCaseEventListener {

    // Scaling parameters
    @Param({"100", "1000", "10000", "100000"})
    private int actorCount;

    @Param({"linear", "exponential", "wave"})
    private String scalingPattern;

    @Param({"virtual", "platform", "mixed"})
    private String threadType;

    @Param({"low", "medium", "high"})
    private String loadLevel;

    // Engine and spec
    private YStatelessEngine engine;
    private YSpecification spec;
    
    // Performance metrics
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong completedMessages = new AtomicLong(0);
    private final AtomicLong failedMessages = new AtomicLong(0);
    private final AtomicLong totalTime = new AtomicLong(0);
    
    // Threading
    private ExecutorService executor;
    private final List<Future<?>> actorFutures = new ArrayList<>();
    private final AtomicInteger activeActors = new AtomicInteger(0);
    
    // Synchronization
    private CountDownLatch completionLatch;
    private final AtomicReference<Exception> errorRef = new AtomicReference<>();
    
    // Load configuration
    private int messagesPerActor;
    private int actorThinkTime;

    @Setup(Level.Trial)
    public void setup() throws YSyntaxException {
        // Initialize engine
        engine = new YStatelessEngine();
        engine.addWorkItemEventListener(this);
        engine.addCaseEventListener(this);
        spec = engine.unmarshalSpecification(BenchmarkSpecFactory.SEQUENTIAL_2_TASK);

        // Configure load
        switch (loadLevel) {
            case "low":
                messagesPerActor = 10;
                actorThinkTime = 10;
                break;
            case "medium":
                messagesPerActor = 50;
                actorThinkTime = 5;
                break;
            case "high":
                messagesPerActor = 100;
                actorThinkTime = 1;
                break;
        }

        // Setup executor
        switch (threadType) {
            case "virtual":
                executor = Executors.newVirtualThreadPerTaskExecutor();
                break;
            case "platform":
                executor = Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors() * 2
                );
                break;
            case "mixed":
                executor = Executors.newWorkStealingPool(
                    Runtime.getRuntime().availableProcessors() * 4
                );
                break;
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // Cancel all running tasks
        for (Future<?> future : actorFutures) {
            future.cancel(true);
        }
        
        // Shutdown executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Main Scalability Tests ─────────────────────────────────────────────

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void linearScalingBenchmark() throws InterruptedException {
        // Test scaling from small to large
        int[] scales = {100, 1000, 10000, 100000};
        double[] throughputResults = new double[scales.length];
        
        for (int i = 0; i < scales.length; i++) {
            int scale = scales[i];
            throughputResults[i] = testScale(scale);
            
            System.out.printf("Scale %d actors: %.2f msg/s%n", scale, throughputResults[i]);
        }
        
        // Calculate scalability efficiency
        double efficiency = calculateScalabilityEfficiency(throughputResults);
        System.out.printf("Scalability efficiency: %.2f%%%n", efficiency * 100);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void threadScalingBenchmark() throws InterruptedException {
        // Test thread count scaling
        int[] threadCounts = {1, 2, 4, 8, 16, 32};
        double[] throughputResults = new double[threadCounts.length];
        
        for (int i = 0; i < threadCounts.length; i++) {
            int threads = threadCounts[i];
            
            // Adjust executor for this test
            ExecutorService testExecutor = createExecutorForThreads(threads);
            
            long startTime = System.currentTimeMillis();
            testScaleWithExecutor(testExecutor, actorCount / 10); // Reduced scale for testing
            long duration = System.currentTimeMillis() - startTime;
            
            throughputResults[i] = (actorCount / 10) / (duration / 1000.0);
            testExecutor.shutdown();
            
            System.out.printf("Threads %d: %.2f msg/s%n", threads, throughputResults[i]);
        }
        
        // Identify scaling inflection point
        int inflectionPoint = findScalingInflectionPoint(throughputResults);
        System.out.printf("Scaling inflection point at %d threads%n", 
            threadCounts[inflectionPoint]);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void loadBalancingBenchmark() throws InterruptedException {
        // Test load distribution across actors
        int partitionSize = actorCount / 10;
        
        // Create actor partitions
        List<ActorPartition> partitions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            partitions.add(new ActorPartition("partition-" + i, partitionSize));
        }
        
        // Distribute load
        Instant start = Instant.now();
        for (int i = 0; i < actorCount; i++) {
            final int actorId = i;
            executor.submit(() -> {
                int partition = actorId % 10;
                partitions.get(partition).processActor(actorId);
            });
        }
        
        // Wait for completion
        for (ActorPartition partition : partitions) {
            partition.waitForCompletion();
        }
        
        Duration duration = Duration.between(start, Instant.now());
        double throughput = totalMessages.get() / duration.getSeconds();
        
        // Analyze load balance
        double imbalance = calculateLoadImbalance(partitions);
        System.out.printf("Throughput: %.2f msg/s, Load imbalance: %.2f%%%n", 
            throughput, imbalance * 100);
    }

    // ── Helper Methods ─────────────────────────────────────────────────────

    private double testScale(int scale) throws InterruptedException {
        // Reset metrics
        totalMessages.set(0);
        completedMessages.set(0);
        failedMessages.set(0);
        
        // Setup completion tracking
        completionLatch = new CountDownLatch(scale);
        activeActors.set(0);
        
        Instant start = Instant.now();
        
        // Create actors with scaling pattern
        for (int i = 0; i < scale; i++) {
            final int actorId = i;
            Future<?> future = executor.submit(() -> {
                try {
                    createAndProcessActor(actorId);
                } catch (Exception e) {
                    errorRef.set(e);
                    failedMessages.incrementAndGet();
                } finally {
                    completionLatch.countDown();
                }
            });
            actorFutures.add(future);
        }
        
        // Wait for completion
        completionLatch.await(60, TimeUnit.SECONDS);
        
        Duration duration = Duration.between(start, Instant.now());
        return scale / duration.getSeconds();
    }

    private void createAndProcessActor(int actorId) throws YSyntaxException {
        activeActors.incrementAndGet();
        
        // Create actor-specific workload
        for (int msg = 0; msg < messagesPerActor; msg++) {
            try {
                // Actor processing
                String caseId = "actor-" + actorId + "-msg-" + msg;
                engine.createCase(spec.getID(), caseId);
                totalMessages.incrementAndGet();
                
                // Simulate processing time
                if (scalingPattern.equals("linear")) {
                    Thread.sleep(actorThinkTime);
                } else if (scalingPattern.equals("exponential")) {
                    Thread.sleep(actorThinkTime * (msg + 1));
                } else if (scalingPattern.equals("wave")) {
                    Thread.sleep((long)(actorThinkTime * (1 + 0.5 * Math.sin(msg))));
                }
                
                completedMessages.incrementAndGet();
            } catch (Exception e) {
                failedMessages.incrementAndGet();
                throw e;
            }
        }
        
        activeActors.decrementAndGet();
    }

    private double calculateScalabilityEfficiency(double[] throughputResults) {
        // Perfect scaling: each 10x increase gives 10x throughput
        double perfectScaling = throughputResults[0];
        for (int i = 1; i < throughputResults.length; i++) {
            double expected = perfectScaling * Math.pow(10, i);
            double actual = throughputResults[i];
            perfectScaling = Math.min(perfectScaling, actual / expected);
        }
        return perfectScaling;
    }

    private int findScalingInflectionPoint(double[] throughputResults) {
        // Find where throughput stops scaling linearly
        for (int i = 1; i < throughputResults.length - 1; i++) {
            double improvement = throughputResults[i] / throughputResults[i - 1];
            if (improvement < 1.5) { // Less than 50% improvement
                return i;
            }
        }
        return throughputResults.length - 1;
    }

    private double calculateLoadImbalance(List<ActorPartition> partitions) {
        // Calculate standard deviation of message counts
        double[] messageCounts = partitions.stream()
            .mapToDouble(ActorPartition::getMessageCount)
            .toArray();
        
        double mean = Arrays.stream(messageCounts).average().orElse(0);
        double variance = Arrays.stream(messageCounts)
            .map(x -> Math.pow(x - mean, 2))
            .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        
        return stdDev / mean;
    }

    private ExecutorService createExecutorForThreads(int threads) {
        switch (threadType) {
            case "virtual":
                return Executors.newVirtualThreadPerTaskExecutor();
            case "platform":
                return Executors.newFixedThreadPool(threads);
            case "mixed":
                return Executors.newWorkStealingPool(threads);
            default:
                return Executors.newFixedThreadPool(threads);
        }
    }

    private void testScaleWithExecutor(ExecutorService testExecutor, int scale) 
        throws InterruptedException {
        
        for (int i = 0; i < scale; i++) {
            final int actorId = i;
            testExecutor.submit(() -> {
                try {
                    createAndProcessActor(actorId);
                } catch (Exception e) {
                    errorRef.set(e);
                }
            });
        }
    }

    // ── Event Listeners ─────────────────────────────────────────────────────

    @Override
    public void handleWorkItemEvent(YWorkItemEvent event) {
        // Track work item events for processing
    }

    @Override
    public void handleCaseEvent(YCaseEvent event) {
        if (event.getEventType() == YEventType.CaseCompleted) {
            completedMessages.incrementAndGet();
        }
    }

    // ── Inner Classes ───────────────────────────────────────────────────────

    private static class ActorPartition {
        private final String name;
        private final int expectedActors;
        private final AtomicInteger processedActors = new AtomicInteger(0);
        private final AtomicInteger messageCount = new AtomicInteger(0);
        private final CountDownLatch partitionLatch;
        
        public ActorPartition(String name, int expectedActors) {
            this.name = name;
            this.expectedActors = expectedActors;
            this.partitionLatch = new CountDownLatch(expectedActors);
        }
        
        public void processActor(int actorId) {
            try {
                // Simulate actor processing
                for (int i = 0; i < 10; i++) {
                    messageCount.incrementAndGet();
                    Thread.sleep((long)(Math.random() * 10));
                }
            } finally {
                processedActors.incrementAndGet();
                partitionLatch.countDown();
            }
        }
        
        public void waitForCompletion() {
            try {
                partitionLatch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        public int getMessageCount() {
            return messageCount.get();
        }
    }
}
