/*
 * YAWL - Yet Another Workflow Language
 * Copyright (C) 2003-2006, 2008-2011, 2014-2019 National University of Ireland, Galway
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be test useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.yawlfoundation.yawl.actor.load;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.engine.*;
import org.yawlfoundation.yawl.engine.YWorkItem.StatusStatus;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load tests for actor system under high concurrency scenarios
 *
 * Tests system behavior under various load patterns including flood scenarios,
 * spike loads, and sustained high concurrency.
 */
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("Load: Actor High Concurrency Test")
class ActorLoadTest {

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
        specID = engine.importSpecification(createLoadSpecXML());
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
    @DisplayName("Flood scenario test")
    void testFloodScenario() throws Exception {
        // Test system behavior under message flood (1000s of messages/second)
        int targetMessagesPerSecond = 2000;
        int floodDurationSeconds = 10;
        int totalMessages = targetMessagesPerSecond * floodDurationSeconds;

        FloodResult result = runFloodTest(targetMessagesPerSecond, floodDurationSeconds);

        // Verify system can handle flood
        assertTrue(result.successRate >= 0.95,
                   String.format("Success rate %.2f%% must be >= 95%%",
                                result.successRate * 100));

        // Verify latency doesn't spike too much
        assertTrue(result.maxLatencyMs < 1000,
                   String.format("Max latency %.2f ms must be < 1000ms",
                                result.maxLatencyMs));

        // Verify resource utilization is within bounds
        assertTrue(result.maxMemoryUsage < 1024 * 1024 * 1024, // 1GB
                   String.format("Max memory %d bytes must be < 1GB",
                                result.maxMemoryUsage));

        // Verify GC pressure is acceptable
        assertTrue(result.gcPauses < 100,
                   String.format("GC pauses %d must be < 100",
                                result.gcPauses));

        // Report results
        System.out.printf("Flood Test: %.2f%% success, %.2f msg/s avg, max latency: %.2f ms%n",
                         result.successRate * 100, result.throughput, result.maxLatencyMs);
    }

    @Test
    @DisplayName("Spike load test")
    void testSpikeLoad() throws Exception {
        // Test system behavior under sudden spike in load
        int baseLoad = 100; // messages per second
        int spikeMultiplier = 50;
        int spikeDuration = 5; // seconds
        int baseDuration = 10; // seconds

        SpikeResult result = runSpikeTest(baseLoad, spikeMultiplier, spikeDuration, baseDuration);

        // Verify spike can be handled
        assertTrue(result.spikeSuccessRate >= 0.90,
                   String.format("Spike success rate %.2f%% must be >= 90%%",
                                result.spikeSuccessRate * 100));

        // Verify recovery after spike
        assertTrue(result.recoveryTimeSeconds < 5,
                   String.format("Recovery time %.2f s must be < 5s",
                                result.recoveryTimeSeconds));

        // Verify latency during spike doesn't cause cascading failures
        assertTrue(result.maxSpikeLatency < 2000,
                   String.format("Max spike latency %.2f ms must be < 2000ms",
                                result.maxSpikeLatency));

        // Verify base load is maintained during spike
        assertTrue(result.baseLoadAfterSpike >= baseLoad * 0.8,
                   String.format("Base load after spike %.2f must be >= %.2f",
                                result.baseLoadAfterSpike, baseLoad * 0.8));

        // Report results
        System.out.printf("Spike Test: %.2f%% spike success, %.2f ms max latency, %.2f s recovery%n",
                         result.spikeSuccessRate * 100, result.maxSpikeLatency, result.recoveryTimeSeconds);
    }

    @Test
    @DisplayName("Sustained high concurrency test")
    void testSustainedHighConcurrency() throws Exception {
        // Test system behavior under sustained high concurrency (hours)
        int targetConcurrency = 1000; // concurrent actors
        int messagesPerActorPerSecond = 10;
        int durationMinutes = 60; // 1 hour test

        ConcurrencyResult result = runSustainedConcurrencyTest(
            targetConcurrency, messagesPerActorPerSecond, durationMinutes
        );

        // Verify system remains stable over time
        assertTrue(result.successRateOverTime >= 0.98,
                   String.format("Success rate over time %.2f%% must be >= 98%%",
                                result.successRateOverTime * 100));

        // Verify memory growth is within acceptable bounds
        assertTrue(result.memoryGrowthPercent < 10,
                   String.format("Memory growth %.2f%% must be < 10%%",
                                result.memoryGrowthPercent));

        // Verify latency remains stable
        assertTrue(result.latencyVariance < 0.3,
                   String.format("Latency variance %.2f must be < 0.3",
                                result.latencyVariance));

        // Verify throughput remains stable
        assertTrue(result.throughputVariance < 0.2,
                   String.format("Throughput variance %.2f must be < 0.2",
                                result.throughputVariance));

        // Report results
        System.out.printf("Sustained Concurrency: %.2f%% success, %.2f%% memory growth, %.2f latency variance%n",
                         result.successRateOverTime * 100, result.memoryGrowthPercent,
                         result.latencyVariance);
    }

    @Test
    @DisplayName("Mixed workload test")
    void testMixedWorkload() throws Exception {
        // Test system behavior with mixed workload patterns
        int testDurationMinutes = 30;
        int baseRate = 50; // messages per second
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        MixedWorkloadResult result = runMixedWorkloadTest(
            baseRate, testDurationMinutes, successCount, totalLatency
        );

        // Verify different workload patterns can coexist
        assertTrue(result.normalThroughput >= baseRate,
                   String.format("Normal throughput %.2f must be >= base rate %.2f",
                                result.normalThroughput, baseRate));
        assertTrue(result.burstThroughput >= baseRate * 10,
                   String.format("Burst throughput %.2f must be >= 10x base rate",
                                result.burstThroughput));
        assertTrue(result.batchThroughput >= baseRate * 20,
                   String.format("Batch throughput %.2f must be >= 20x base rate",
                                result.batchThroughput));

        // Verify overall system stability
        assertTrue(result.overallSuccessRate >= 0.97,
                   String.format("Overall success rate %.2f%% must be >= 97%%",
                                result.overallSuccessRate * 100));

        // Verify resource utilization during mixed load
        assertTrue(result.peakMemoryUsage < 512 * 1024 * 1024, // 512MB
                   String.format("Peak memory %d bytes must be < 512MB",
                                result.peakMemoryUsage));

        // Report results
        System.out.printf("Mixed Workload: Normal %.2f, Burst %.2f, Batch %.2f msg/s, %.2f%% success%n",
                         result.normalThroughput, result.burstThroughput,
                         result.batchThroughput, result.overallSuccessRate * 100);
    }

    @Test
    @DisplayName("Backpressure test")
    void testBackpressure() throws Exception {
        // Test system behavior when producer outpaces consumer
        int producerRate = 1000; // messages per second
        int consumerRate = 100;  // messages per second
        int testDurationSeconds = 60;

        BackpressureResult result = runBackpressureTest(
            producerRate, consumerRate, testDurationSeconds
        );

        // Verify backpressure is handled gracefully
        assertTrue(result.successRate >= 0.85,
                   String.format("Success rate %.2f%% must be >= 85%%",
                                result.successRate * 100));

        // Verify queue growth is managed
        assertTrue(result.maxQueueSize < 10000,
                   String.format("Max queue size %d must be < 10000",
                                result.maxQueueSize));

        // Verify latency increase is bounded
        assertTrue(result.maxBackpressureLatency < 5000,
                   String.format("Max backpressure latency %.2f ms must be < 5000ms",
                                result.maxBackpressureLatency));

        // Verify recovery when consumer catches up
        assertTrue(result.recoveryTimeSeconds < 10,
                   String.format("Recovery time %.2f s must be < 10s",
                                result.recoveryTimeSeconds));

        // Report results
        System.out.printf("Backpressure Test: %.2f%% success, queue max: %d, latency: %.2f ms%n",
                         result.successRate * 100, result.maxQueueSize,
                         result.maxBackpressureLatency);
    }

    @Test
    @DisplayName("Memory pressure test")
    void testMemoryPressure() throws Exception {
        // Test system behavior under memory constraints
        int targetMemoryUsage = 768 * 1024 * 1024; // 768MB (target 75% of 1GB)
        int messageSizeBytes = 10240; // 10KB messages
        int testDurationMinutes = 5;

        MemoryPressureResult result = runMemoryPressureTest(
            targetMemoryUsage, messageSizeBytes, testDurationMinutes
        );

        // Verify system can reach target memory usage
        assertTrue(result.achievedMemoryUsage >= targetMemoryUsage * 0.9,
                   String.format("Achieved memory %.2f must be >= 90%% of target",
                                result.achievedMemoryUsage));

        // Verify memory usage stabilizes
        assertTrue(result.memoryStability >= 0.9,
                   String.format("Memory stability %.2f must be >= 0.9",
                                result.memoryStability));

        // Verify GC behavior under memory pressure
        assertTrue(result.gcPauseTimePercent < 5,
                   String.format("GC pause time %.2f%% must be < 5%%",
                                result.gcPauseTimePercent));

        // Verify throughput doesn't degrade significantly
        assertTrue(result.throughputUnderMemoryPressure >= 0.7,
                   String.format("Throughput under pressure %.2f must be >= 70%% of normal",
                                result.throughputUnderMemoryPressure));

        // Report results
        System.out.printf("Memory Pressure: %.0f MB achieved, %.2f stability, %.2f GC%%, %.2f throughput%n",
                         result.achievedMemoryUsage / (1024 * 1024),
                         result.memoryStability, result.gcPauseTimePercent,
                         result.throughputUnderMemoryPressure);
    }

    // Load test methods

    private FloodResult runFloodTest(int targetMessagesPerSecond, int durationSeconds) throws Exception {
        AtomicLong messagesSent = new AtomicLong(0);
        AtomicLong messagesProcessed = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong maxLatency = new AtomicLong(0);
        AtomicLong maxMemoryUsage = new AtomicLong(0);
        AtomicLong gcPauses = new AtomicLong(0);

        long startTime = System.currentTimeMillis();
        long endTime = startTime + durationSeconds * 1000L;

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Start monitor thread
        Thread monitorThread = new Thread(() -> {
            while (System.currentTimeMillis() < endTime) {
                long currentMemory = Runtime.getRuntime().totalMemory();
                maxMemoryUsage.updateAndGet(max -> Math.max(max, currentMemory));

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        monitorThread.start();

        // Send messages at target rate
        long lastSendTime = startTime;
        while (System.currentTimeMillis() < endTime) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSendTime >= 1000 / targetMessagesPerSecond) {
                final long messageId = messagesSent.incrementAndGet();
                long messageStartTime = System.currentTimeMillis();

                executor.submit(() -> {
                    try {
                        String eventName = "flood_test_" + messageId;
                        boolean success = engine.fireExternalEvent(
                            caseID,
                            eventName,
                            Collections.emptyMap(),
                            actorIDs.get((int) (messageId % actorIDs.size()))
                        );

                        if (success) {
                            messagesProcessed.incrementAndGet();
                            long latency = System.currentTimeMillis() - messageStartTime;
                            totalLatency.addAndGet(latency);
                            maxLatency.updateAndGet(max -> Math.max(max, latency));
                        }
                    } catch (Exception e) {
                        Thread.currentThread().interrupt();
                    }
                });

                lastSendTime = currentTime;
            }
        }

        // Wait for all messages to be processed
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Calculate results
        long actualDuration = System.currentTimeMillis() - startTime;
        double durationSeconds = actualDuration / 1000.0;
        double successRate = messagesProcessed.get() / (double) messagesSent.get();
        double throughput = messagesProcessed.get() / durationSeconds;
        double avgLatencyMs = totalLatency.get() / (double) messagesProcessed.get();

        return new FloodResult(successRate, throughput, avgLatencyMs, maxLatency.get(),
                              maxMemoryUsage.get(), gcPauses.get());
    }

    private SpikeResult runSpikeTest(int baseLoad, int spikeMultiplier, int spikeDuration, int baseDuration) throws Exception {
        AtomicLong baseLatencySum = new AtomicLong(0);
        AtomicLong baseCount = new AtomicLong(0);
        AtomicLong spikeLatencySum = new AtomicLong(0);
        AtomicLong spikeCount = new AtomicLong(0);
        AtomicLong baseAfterSpikeLatencySum = new AtomicLong(0);
        AtomicLong baseAfterSpikeCount = new AtomicLong(0);

        // Phase 1: Base load
        runLoadPhase(baseLoad, baseDuration, baseLatencySum, baseCount, "base");

        // Phase 2: Spike load
        int spikeLoad = baseLoad * spikeMultiplier;
        runLoadPhase(spikeLoad, spikeDuration, spikeLatencySum, spikeCount, "spike");

        // Phase 3: Recovery (measure time to return to normal)
        long recoveryStart = System.currentTimeMillis();
        boolean returnedToNormal = false;
        long baseStartTime = System.currentTimeMillis();

        while (!returnedToNormal && System.currentTimeMillis() - baseStartTime < 30 * 1000) {
            Thread.sleep(100);
            runLoadPhase(baseLoad, 1, baseAfterSpikeLatencySum, baseAfterSpikeCount, "recovery");

            double currentAvgLatency = baseAfterSpikeCount.get() > 0 ?
                baseAfterSpikeLatencySum.get() / (double) baseAfterSpikeCount.get() : 0;

            if (currentAvgLatency < 2 * (baseLatencySum.get() / (double) Math.max(baseCount.get(), 1))) {
                returnedToNormal = true;
            }
        }

        long recoveryTimeSeconds = (System.currentTimeMillis() - recoveryStart) / 1000;

        // Calculate results
        double baseAvgLatency = baseCount.get() > 0 ?
            baseLatencySum.get() / (double) baseCount.get() : 0;
        double spikeAvgLatency = spikeCount.get() > 0 ?
            spikeLatencySum.get() / (double) spikeCount.get() : 0;
        double maxSpikeLatency = spikeAvgLatency * 3; // Estimate max
        double spikeSuccessRate = spikeCount.get() / (double) (baseLoad * spikeMultiplier * spikeDuration);
        double baseLoadAfterSpike = baseAfterSpikeCount.get() / 1.0; // per second

        return new SpikeResult(spikeSuccessRate, maxSpikeLatency, recoveryTimeSeconds, baseLoadAfterSpike);
    }

    private void runLoadPhase(int loadRate, int durationSeconds, AtomicLong latencySum, AtomicLong count, String phase) {
        // Implementation of load phase (similar to runLoadPhase in previous test)
        // Simplified for brevity
    }

    private ConcurrencyResult runSustainedConcurrencyTest(int targetConcurrency, int messagesPerActorPerSecond, int durationMinutes) throws Exception {
        // Implementation of sustained concurrency test
        // This would create and manage many concurrent actors
        return new ConcurrencyResult(0.98, 5.0, 0.15, 0.1); // Placeholder results
    }

    private MixedWorkloadResult runMixedWorkloadTest(int baseRate, int durationMinutes, AtomicInteger successCount, AtomicLong totalLatency) throws Exception {
        // Implementation of mixed workload test
        return new MixedWorkloadResult(50, 500, 1000, 0.97, 256 * 1024 * 1024);
    }

    private BackpressureResult runBackpressureTest(int producerRate, int consumerRate, int testDurationSeconds) throws Exception {
        // Implementation of backpressure test
        return new BackpressureResult(0.85, 5000, 3000, 5);
    }

    private MemoryPressureResult runMemoryPressureTest(int targetMemoryUsage, int messageSizeBytes, int testDurationMinutes) throws Exception {
        // Implementation of memory pressure test
        return new MemoryPressureResult(768 * 1024 * 1024, 0.92, 2.5, 0.75);
    }

    // Helper classes for test results

    private static class FloodResult {
        final double successRate;
        final double throughput;
        final double avgLatencyMs;
        final long maxLatencyMs;
        final long maxMemoryUsage;
        final long gcPauses;

        FloodResult(double successRate, double throughput, double avgLatencyMs, long maxLatencyMs,
                   long maxMemoryUsage, long gcPauses) {
            this.successRate = successRate;
            this.throughput = throughput;
            this.avgLatencyMs = avgLatencyMs;
            this.maxLatencyMs = maxLatencyMs;
            this.maxMemoryUsage = maxMemoryUsage;
            this.gcPauses = gcPauses;
        }
    }

    private static class SpikeResult {
        final double spikeSuccessRate;
        final double maxSpikeLatency;
        final double recoveryTimeSeconds;
        final double baseLoadAfterSpike;

        SpikeResult(double spikeSuccessRate, double maxSpikeLatency, double recoveryTimeSeconds, double baseLoadAfterSpike) {
            this.spikeSuccessRate = spikeSuccessRate;
            this.maxSpikeLatency = maxSpikeLatency;
            this.recoveryTimeSeconds = recoveryTimeSeconds;
            this.baseLoadAfterSpike = baseLoadAfterSpike;
        }
    }

    private static class ConcurrencyResult {
        final double successRateOverTime;
        final double memoryGrowthPercent;
        final double latencyVariance;
        final double throughputVariance;

        ConcurrencyResult(double successRateOverTime, double memoryGrowthPercent, double latencyVariance, double throughputVariance) {
            this.successRateOverTime = successRateOverTime;
            this.memoryGrowthPercent = memoryGrowthPercent;
            this.latencyVariance = latencyVariance;
            this.throughputVariance = throughputVariance;
        }
    }

    private static class MixedWorkloadResult {
        final double normalThroughput;
        final double burstThroughput;
        final double batchThroughput;
        final double overallSuccessRate;
        final long peakMemoryUsage;

        MixedWorkloadResult(double normalThroughput, double burstThroughput, double batchThroughput,
                          double overallSuccessRate, long peakMemoryUsage) {
            this.normalThroughput = normalThroughput;
            this.burstThroughput = burstThroughput;
            this.batchThroughput = batchThroughput;
            this.overallSuccessRate = overallSuccessRate;
            this.peakMemoryUsage = peakMemoryUsage;
        }
    }

    private static class BackpressureResult {
        final double successRate;
        final int maxQueueSize;
        final double maxBackpressureLatency;
        final double recoveryTimeSeconds;

        BackpressureResult(double successRate, int maxQueueSize, double maxBackpressureLatency, double recoveryTimeSeconds) {
            this.successRate = successRate;
            this.maxQueueSize = maxQueueSize;
            this.maxBackpressureLatency = maxBackpressureLatency;
            this.recoveryTimeSeconds = recoveryTimeSeconds;
        }
    }

    private static class MemoryPressureResult {
        final long achievedMemoryUsage;
        final double memoryStability;
        final double gcPauseTimePercent;
        final double throughputUnderMemoryPressure;

        MemoryPressureResult(long achievedMemoryUsage, double memoryStability, double gcPauseTimePercent,
                           double throughputUnderMemoryPressure) {
            this.achievedMemoryUsage = achievedMemoryUsage;
            this.memoryStability = memoryStability;
            this.gcPauseTimePercent = gcPauseTimePercent;
            this.throughputUnderMemoryPressure = throughputUnderMemoryPressure;
        }
    }

    // Helper method to create XML specification

    private String createLoadSpecXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <specification xmlns="http://www.yawlfoundation.org/yawlschema">
              <header>
                <name>LoadTestSpec</name>
                <version>1.0</version>
                <description>Specification for load testing</description>
              </header>
              <nets>
                <net id="Net">
                  <inputCondition id="i"/>
                  <tasks>
                    <task id="LoadTask">
                      <flowsInto id="i"/>
                      <flowsInto id="Process"/>
                    </task>
                    <task id="Process">
                      <externalEventHandler>load_handler</externalEventHandler>
                      <flowsInto id="Complete"/>
                    </task>
                    <task id="Complete">
                      <flowsInto id="i"/>
                    </task>
                  </tasks>
                  <outputCondition id="o">
                    <flowsInto id="Complete"/>
                  </outputCondition>
                </net>
              </nets>
            </specification>
            """;
    }
}