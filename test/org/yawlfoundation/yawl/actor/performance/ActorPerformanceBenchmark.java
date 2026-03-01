/*
 * YAWL - Yet Another Workflow Language
 * Copyright (C) 2003-2006, 2008-2011, 2014-2019 National University of Ireland, Galway
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.yawlfoundation.yawl.actor.performance;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.engine.*;
import org.yawlfoundation.yawl.engine.YWorkItem.StatusStatus;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance benchmarks for actor system throughput and latency
 *
 * Tests message throughput, latency, scalability, and resource utilization
 * under various load scenarios.
 */
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("Performance: Actor Throughput Benchmark")
class ActorPerformanceBenchmark {

    private static YEngine engine;
    private YSpecificationID specID;
    private String caseID;
    private List<String> actorIDs;

    @BeforeAll
    static void setupEngine() throws Exception {
        engine = YEngine.getInstance();
        if (engine != null) {
            engine.initialise();
        }
    }

    @AfterAll
    static void cleanupEngine() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @BeforeEach
    void setupSpecification() throws Exception {
        // Create test specification
        specID = engine.importSpecification(createPerformanceSpecXML());
        assertNotNull(specID, "Specification must be imported");

        // Create case
        caseID = engine.createCase(specID);
        assertNotNull(caseID, "Case must be created");

        // Setup actors
        List<YWorkItem> workItems = engine.getWorkItems(caseID, StatusStatus.ALL);
        assertFalse(workItems.isEmpty(), "Must have work items");

        for (YWorkItem workItem : workItems) {
            engine.startWorkItem(caseID, workItem.getID(), "");
        }

        actorIDs = engine.getActiveParticipants();
        assertFalse(actorIDs.isEmpty(), "Must have active actors");
    }

    @AfterEach
    void cleanup() throws Exception {
        if (caseID != null) {
            engine.completeCase(caseID);
        }
        if (specID != null) {
            engine.removeSpecification(specID);
        }
    }

    @Test
    @DisplayName("Single actor throughput benchmark")
    void testSingleActorThroughput() throws Exception {
        int messageCount = 1000;
        double targetThroughput = 500; // messages per second

        ThroughputResult result = benchmarkThroughput(messageCount, 1);
        double throughput = result.messagesProcessed / result.durationSeconds;

        // Verify throughput
        assertTrue(throughput >= targetThroughput,
                   String.format("Throughput %.2f msg/s must meet target %.2f msg/s",
                                throughput, targetThroughput));

        // Verify latency
        assertTrue(result.avgLatencyMs < 100,
                   String.format("Average latency %.2f ms must be < 100ms",
                                result.avgLatencyMs));

        // Verify resource utilization
        assertTrue(result.maxMemoryUsed < 100 * 1024 * 1024, // 100MB
                   String.format("Memory usage %d bytes must be < 100MB",
                                result.maxMemoryUsed));

        // Report results
        System.out.printf("Single Actor: %.2f msg/s, avg latency: %.2f ms%n",
                         throughput, result.avgLatencyMs);
    }

    @Test
    @DisplayName("Multiple actor throughput benchmark")
    void testMultipleActorThroughput() throws Exception {
        int messageCount = 5000;
        double targetThroughput = 2000; // messages per second across all actors

        ThroughputResult result = benchmarkThroughput(messageCount, actorIDs.size());
        double throughput = result.messagesProcessed / result.durationSeconds;

        // Verify throughput scales with actor count
        assertTrue(throughput >= targetThroughput,
                   String.format("Throughput %.2f msg/s must meet target %.2f msg/s",
                                throughput, targetThroughput));

        // Verify latency doesn't increase significantly
        assertTrue(result.avgLatencyMs < 150,
                   String.format("Average latency %.2f ms must be < 150ms",
                                result.avgLatencyMs));

        // Report results
        System.out.printf("Multiple Actors (%d): %.2f msg/s, avg latency: %.2f ms%n",
                         actorIDs.size(), throughput, result.avgLatencyMs);
    }

    @Test
    @DisplayName("Latency benchmark with varying message sizes")
    void testLatencyBenchmark() throws Exception {
        int messageCount = 500;
        Map<Integer, Double> sizeLatencies = new HashMap<>();

        // Test different message sizes
        for (int size : new int[]{100, 1024, 10240}) { // 100B, 1KB, 10KB
            double avgLatency = benchmarkLatency(messageCount, size);
            sizeLatencies.put(size, avgLatency);

            // Latency should not increase disproportionately with size
            double sizeMultiplier = (double) size / 100;
            assertTrue(avgLatency < 100 * sizeMultiplier,
                      String.format("Latency %.2f ms for %d bytes should scale reasonably",
                                   avgLatency, size));
        }

        // Report results
        sizeLatencies.forEach((size, latency) -> {
            System.out.printf("Message size %d bytes: avg latency %.2f ms%n",
                             size, latency);
        });
    }

    @Test
    @DisplayName("Stress test: High load endurance")
    void testHighLoadEndurance() throws Exception {
        int messagesPerSecond = 1000;
        int durationMinutes = 5;
        int totalMessages = messagesPerSecond * 60 * durationMinutes;

        StressResult result = benchmarkStressLoad(messagesPerSecond, durationMinutes);

        // Verify system stability under stress
        assertTrue(result.successRate >= 0.99,
                   String.format("Success rate %.2f%% must be >= 99%%",
                                result.successRate * 100));

        // Verify resource limits
        assertTrue(result.maxMemoryUsage < 500 * 1024 * 1024, // 500MB
                   String.format("Max memory %d bytes must be < 500MB",
                                result.maxMemoryUsage));

        // Verify GC pressure is acceptable
        assertTrue(result.gcPauses < totalMessages / 1000,
                   String.format("GC pauses %d must be < totalMessages/1000",
                                result.gcPauses));

        // Report results
        System.out.printf("Stress Test: %.2f%% success, %d GC pauses, max mem: %d MB%n",
                         result.successRate * 100, result.gcPauses,
                         result.maxMemoryUsage / (1024 * 1024));
    }

    @Test
    @DisplayName("Burst handling benchmark")
    void testBurstHandling() throws Exception {
        int normalRate = 100; // messages per second
        int burstMultiplier = 10;
        int burstDuration = 1; // second
        int normalDuration = 5; // seconds

        BurstResult result = benchmarkBurst(normalRate, burstMultiplier, burstDuration, normalDuration);

        // Verify burst is handled without excessive latency
        assertTrue(result.burstAvgLatency < 500,
                   String.format("Burst latency %.2f ms must be < 500ms",
                                result.burstAvgLatency));

        // Verify recovery time
        assertTrue(result.recoveryTimeSeconds < 2,
                   String.format("Recovery time %.2f s must be < 2s",
                                result.recoveryTimeSeconds));

        // Verify message loss is minimal
        assertTrue(result.messageLossRate < 0.01,
                   String.format("Message loss rate %.4f must be < 1%%",
                                result.messageLossRate));

        // Report results
        System.out.printf("Burst Test: %.2f%% loss, burst latency: %.2f ms, recovery: %.2f s%n",
                         result.messageLossRate * 100, result.burstAvgLatency,
                         result.recoveryTimeSeconds);
    }

    @Test
    @DisplayName("Scaling benchmark")
    void testScalingBenchmark() throws Exception {
        int baseActorCount = actorIDs.size();
        int[] scalingFactors = new int[]{2, 4, 8};
        int baseMessageCount = 1000;
        double targetThroughputPerActor = 300;

        ScalingResult scalingResults = new ScalingResult();

        for (int factor : scalingFactors) {
            int targetActorCount = baseActorCount * factor;
            int totalMessages = baseMessageCount * factor;

            // Scale up actors
            scaleActors(targetActorCount);

            // Run throughput benchmark
            ThroughputResult result = benchmarkThroughput(totalMessages, targetActorCount);
            double throughputPerActor = result.messagesProcessed / result.durationSeconds / targetActorCount;

            scalingResults.addResult(factor, result);

            // Verify scaling efficiency
            assertTrue(throughputPerActor >= targetThroughputPerActor,
                       String.format("Actor factor %d: throughput %.2f/msg/s must meet target %.2f/msg/s",
                                    factor, throughputPerActor, targetThroughputPerActor));

            // Cleanup scaled actors
            cleanupScaledActors(targetActorCount);
        }

        // Verify scaling efficiency
        verifyScalingEfficiency(scalingResults);

        // Report results
        scalingResults.printResults();
    }

    @Test
    @DisplayName("Mixed workload benchmark")
    void testMixedWorkload() throws Exception {
        // Test mixed workload with different message types
        int messageCount = 2000;
        MixedWorkloadResult result = benchmarkMixedWorkload(messageCount);

        // Verify each workload type meets requirements
        assertTrue(result.normalThroughput >= 300,
                   String.format("Normal throughput %.2f msg/s must be >= 300",
                                result.normalThroughput));
        assertTrue(result.priorityThroughput >= 200,
                   String.format("Priority throughput %.2f msg/s must be >= 200",
                                result.priorityThroughput));
        assertTrue(result.largeMessageThroughput >= 100,
                   String.format("Large message throughput %.2f msg/s must be >= 100",
                                result.largeMessageThroughput));

        // Verify overall system stability
        assertTrue(result.totalSuccessRate >= 0.98,
                   String.format("Total success rate %.2f%% must be >= 98%%",
                                result.totalSuccessRate * 100));

        // Report results
        System.out.printf("Mixed Workload: Normal %.2f, Priority %.2f, Large %.2f msg/s, Success: %.2f%%%n",
                         result.normalThroughput, result.priorityThroughput,
                         result.largeMessageThroughput, result.totalSuccessRate * 100);
    }

    // Benchmark methods

    private ThroughputResult benchmarkThroughput(int messageCount, int actorCount) throws Exception {
        AtomicLong messagesProcessed = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicBoolean stopFlag = new AtomicBoolean(false);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        long startTime = System.currentTimeMillis();

        // Start message producers
        for (int i = 0; i < messageCount; i++) {
            final int messageId = i;
            executor.submit(() -> {
                long messageStart = System.currentTimeMillis();

                try {
                    String eventName = "throughput_test_" + messageId;
                    boolean success = engine.fireExternalEvent(
                        caseID,
                        eventName,
                        Collections.emptyMap(),
                        actorIDs.get(messageId % actorCount)
                    );

                    if (success) {
                        messagesProcessed.incrementAndGet();
                        totalLatency.addAndGet(System.currentTimeMillis() - messageStart);
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }

                if (messagesProcessed.get() >= messageCount) {
                    stopFlag.set(true);
                }
            });

            if (stopFlag.get()) break;
        }

        // Wait for completion
        while (!stopFlag.get() && messagesProcessed.get() < messageCount) {
            Thread.sleep(100);
        }

        long endTime = System.currentTimeMillis();
        double durationSeconds = (endTime - startTime) / 1000.0;
        double avgLatencyMs = totalLatency.get() / (double) messagesProcessed.get();

        executor.shutdown();

        return new ThroughputResult(messageCount, durationSeconds, avgLatencyMs);
    }

    private double benchmarkLatency(int messageCount, int messageSizeBytes) throws Exception {
        List<Long> latencies = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < messageCount; i++) {
            long messageStart = System.nanoTime();

            try {
                // Create message of specified size
                Map<String, Object> data = new HashMap<>();
                if (messageSizeBytes > 0) {
                    String padding = "x".repeat(messageSizeBytes);
                    data.put("data", padding);
                }

                String eventName = "latency_test_" + i;
                boolean success = engine.fireExternalEvent(
                    caseID,
                    eventName,
                    data,
                    actorIDs.get(0)
                );

                if (success) {
                    long latencyNanos = System.nanoTime() - messageStart;
                    latencies.add(latencyNanos / 1_000_000.0); // Convert to ms
                }
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        }

        double avgLatencyMs = latencies.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0);

        return avgLatencyMs;
    }

    private StressResult benchmarkStressLoad(int messagesPerSecond, int durationMinutes) throws Exception {
        int totalMessages = messagesPerSecond * 60 * durationMinutes;
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong gcPauses = new AtomicLong(0);
        long maxMemoryUsed = 0;
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < totalMessages; i++) {
            final int messageId = i;
            executor.submit(() -> {
                try {
                    long messageStart = System.currentTimeMillis();

                    String eventName = "stress_test_" + messageId;
                    boolean success = engine.fireExternalEvent(
                        caseID,
                        eventName,
                        Collections.emptyMap(),
                        actorIDs.get(messageId % actorIDs.size())
                    );

                    if (success) {
                        successCount.incrementAndGet();
                        totalLatency.addAndGet(System.currentTimeMillis() - messageStart);
                    }

                    // Monitor memory
                    long currentMemory = Runtime.getRuntime().totalMemory();
                    maxMemoryUsed = Math.max(maxMemoryUsed, currentMemory);

                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });

            // Control message rate
            Thread.sleep(1000 / messagesPerSecond);
        }

        // Wait for all messages to complete
        executor.shutdown();
        executor.awaitTermination(durationMinutes + 1, TimeUnit.MINUTES);

        long endTime = System.currentTimeMillis();
        double durationSeconds = (endTime - startTime) / 1000.0;
        double successRate = successCount.get() / (double) totalMessages;
        double avgLatencyMs = totalLatency.get() / (double) successCount.get();

        return new StressResult(successRate, avgLatencyMs, maxMemoryUsed, gcPauses.get());
    }

    private BurstResult benchmarkBurst(int normalRate, int burstMultiplier, int burstDuration, int normalDuration) throws Exception {
        List<Long> normalLatencies = new ArrayList<>();
        List<Long> burstLatencies = new ArrayList<>();
        AtomicLong messageLossCount = new AtomicLong(0);
        long recoveryStartTime = 0;

        // Normal load phase
        runLoadPhase(normalRate, normalDuration, normalLatencies);

        // Burst phase
        runBurstPhase(normalRate * burstMultiplier, burstDuration, burstLatencies);

        // Recovery phase
        recoveryStartTime = System.currentTimeMillis();
        runLoadPhase(normalRate, normalDuration, normalLatencies);
        double recoveryTimeSeconds = (System.currentTimeMillis() - recoveryStartTime) / 1000.0;

        double totalMessagesSent = normalLatencies.size() + burstLatencies.size();
        double messageLossRate = messageLossCount.get() / totalMessagesSent;

        return new BurstResult(
            burstLatencies.stream().mapToLong(Long::longValue).average().orElse(0),
            recoveryTimeSeconds,
            messageLossRate
        );
    }

    private void runLoadPhase(int rate, int durationSeconds, List<Long> latencies) throws Exception {
        int messages = rate * durationSeconds;
        long phaseStart = System.currentTimeMillis();

        for (int i = 0; i < messages; i++) {
            long messageStart = System.currentTimeMillis();

            try {
                String eventName = "load_test_" + i;
                boolean success = engine.fireExternalEvent(
                    caseID,
                    eventName,
                    Collections.emptyMap(),
                    actorIDs.get(i % actorIDs.size())
                );

                if (success) {
                    latencies.add(System.currentTimeMillis() - messageStart);
                }

            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }

            // Control rate
            long elapsed = System.currentTimeMillis() - phaseStart;
            long expectedTime = (i + 1) * 1000 / rate;
            long sleepTime = Math.max(0, expectedTime - elapsed);
            Thread.sleep(sleepTime);
        }
    }

    private void runBurstPhase(int burstRate, int burstDuration, List<Long> burstLatencies) throws Exception {
        runLoadPhase(burstRate, burstDuration, burstLatencies);
    }

    // Scaling helper methods

    private void scaleActors(int targetActorCount) throws Exception {
        int currentCount = actorIDs.size();
        int actorsToAdd = targetActorCount - currentCount;

        for (int i = 0; i < actorsToAdd; i++) {
            String newActorID = "scaled_actor_" + i;
            boolean created = engine.createParticipant(
                specID,
                newActorID,
                "ScaledActor",
                true
            );

            if (created) {
                engine.enableParticipant(specID, newActorID);
                actorIDs.add(newActorID);
            }
        }
    }

    private void cleanupScaledActors(int targetActorCount) throws Exception {
        int actorsToRemove = actorIDs.size() - targetActorCount;

        for (int i = 0; i < actorsToRemove; i++) {
            String actorToRemove = actorIDs.get(actorIDs.size() - 1);
            engine.disableParticipant(specID, actorToRemove);
            actorIDs.remove(actorID);
        }
    }

    private void verifyScalingEfficiency(ScalingResult results) {
        double efficiencyThreshold = 0.8; // 80% scaling efficiency

        for (int i = 0; i < results.factors.length - 1; i++) {
            int factor1 = results.factors[i];
            int factor2 = results.factors[i + 1];
            ThroughputResult result1 = results.results.get(factor1);
            ThroughputResult result2 = results.results.get(factor2);

            double throughputRatio = (result2.messagesProcessed / result2.durationSeconds) /
                                   (result1.messagesProcessed / result1.durationSeconds);
            double scalingFactorRatio = (double) factor2 / factor1;

            double efficiency = throughputRatio / scalingFactorRatio;
            assertTrue(efficiency >= efficiencyThreshold,
                       String.format("Scaling efficiency %.2f must be >= %.2f",
                                    efficiency, efficiencyThreshold));
        }
    }

    private MixedWorkloadResult benchmarkMixedWorkload(int messageCount) throws Exception {
        AtomicLong normalMessages = new AtomicLong(0);
        AtomicLong priorityMessages = new AtomicLong(0);
        AtomicLong largeMessages = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong totalProcessed = new AtomicLong(0);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        long startTime = System.currentTimeMillis();

        // Submit mixed workload
        for (int i = 0; i < messageCount; i++) {
            final int messageId = i;
            String messageType = getMessageType(i, messageCount);

            executor.submit(() -> {
                long messageStart = System.currentTimeMillis();

                try {
                    Map<String, Object> data = new HashMap<>();
                    switch (messageType) {
                        case "normal":
                            data.put("priority", "normal");
                            normalMessages.incrementAndGet();
                            break;
                        case "priority":
                            data.put("priority", "high");
                            priorityMessages.incrementAndGet();
                            break;
                        case "large":
                            data.put("data", "x".repeat(10240)); // 10KB
                            data.put("priority", "normal");
                            largeMessages.incrementAndGet();
                            break;
                    }

                    String eventName = "mixed_test_" + messageId;
                    boolean success = engine.fireExternalEvent(
                        caseID,
                        eventName,
                        data,
                        actorIDs.get(messageId % actorIDs.size())
                    );

                    if (success) {
                        totalProcessed.incrementAndGet();
                        totalLatency.addAndGet(System.currentTimeMillis() - messageStart);
                    }

                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Wait for completion
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        double durationSeconds = (endTime - startTime) / 1000.0;

        return new MixedWorkloadResult(
            normalMessages.get() / durationSeconds,
            priorityMessages.get() / durationSeconds,
            largeMessages.get() / durationSeconds,
            totalLatency.get() / (double) totalProcessed.get(),
            totalProcessed.get() / (double) messageCount
        );
    }

    private String getMessageType(int index, int totalCount) {
        // 60% normal, 30% priority, 10% large
        int segmentSize = totalCount / 10;
        int segment = index / segmentSize;

        if (segment < 6) return "normal";     // 0-5
        if (segment < 9) return "priority";   // 6-8
        return "large";                        // 9
    }

    // Helper classes for benchmark results

    private static class ThroughputResult {
        final int messagesProcessed;
        final double durationSeconds;
        final double avgLatencyMs;

        ThroughputResult(int messages, double duration, double latency) {
            this.messagesProcessed = messages;
            this.durationSeconds = duration;
            this.avgLatencyMs = latency;
        }
    }

    private static class StressResult {
        final double successRate;
        final double avgLatencyMs;
        final long maxMemoryUsage;
        final long gcPauses;

        StressResult(double successRate, double avgLatencyMs, long maxMemoryUsage, long gcPauses) {
            this.successRate = successRate;
            this.avgLatencyMs = avgLatencyMs;
            this.maxMemoryUsage = maxMemoryUsage;
            this.gcPauses = gcPauses;
        }
    }

    private static class BurstResult {
        final double burstAvgLatency;
        final double recoveryTimeSeconds;
        final double messageLossRate;

        BurstResult(double burstAvgLatency, double recoveryTimeSeconds, double messageLossRate) {
            this.burstAvgLatency = burstAvgLatency;
            this.recoveryTimeSeconds = recoveryTimeSeconds;
            this.messageLossRate = messageLossRate;
        }
    }

    private static class ScalingResult {
        final int[] factors = {1, 2, 4, 8};
        final Map<Integer, ThroughputResult> results = new HashMap<>();

        void addResult(int factor, ThroughputResult result) {
            results.put(factor, result);
        }

        void printResults() {
            System.out.println("Scaling Results:");
            for (int factor : factors) {
                ThroughputResult result = results.get(factor);
                if (result != null) {
                    double throughput = result.messagesProcessed / result.durationSeconds;
                    System.out.printf("Factor %d: %.2f msg/s, avg latency: %.2f ms%n",
                                     factor, throughput, result.avgLatencyMs);
                }
            }
        }
    }

    private static class MixedWorkloadResult {
        final double normalThroughput;
        final double priorityThroughput;
        final double largeMessageThroughput;
        final double avgLatencyMs;
        final double totalSuccessRate;

        MixedWorkloadResult(double normal, double priority, double large, double latency, double successRate) {
            this.normalThroughput = normal;
            this.priorityThroughput = priority;
            this.largeMessageThroughput = large;
            this.avgLatencyMs = latency;
            this.totalSuccessRate = successRate;
        }
    }

    // Helper method to create XML specification

    private String createPerformanceSpecXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <specification xmlns="http://www.yawlfoundation.org/yawlschema">
              <header>
                <name>PerformanceSpec</name>
                <version>1.0</version>
                <description>Specification for performance benchmarks</description>
              </header>
              <nets>
                <net id="Net">
                  <inputCondition id="i"/>
                  <tasks>
                    <task id="Task1">
                      <flowsInto id="i"/>
                      <flowsInto id="Task2"/>
                    </task>
                    <task id="Task2">
                      <externalEventHandler>message_handler</externalEventHandler>
                      <flowsInto id="o"/>
                    </task>
                    <task id="Task3">
                      <flowsInto id="i"/>
                      <flowsInto id="o"/>
                    </task>
                  </tasks>
                  <outputCondition id="o">
                    <flowsInto id="Task1"/>
                    <flowsInto id="Task2"/>
                    <flowsInto id="Task3"/>
                  </outputCondition>
                </net>
              </nets>
            </specification>
            """;
    }
}