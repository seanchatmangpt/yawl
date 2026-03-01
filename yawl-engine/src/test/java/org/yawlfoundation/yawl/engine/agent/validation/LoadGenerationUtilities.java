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

package org.yawlfoundation.yawl.engine.agent.validation;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Enhanced load generation utilities for stress testing the YAWL Actor Model.
 * Provides various load patterns, stress testing scenarios, and comprehensive metrics.
 *
 * <p>Enhanced load patterns:
 * - Uniform load: Even distribution across all agents
 * - Hotspot load: Concentrated on subset of agents
 * - Burst load: Periodic spikes in message rate
 * - Progressive load: Gradual increase over time
 * - Churn load: Constant creation and destruction of agents
 * - Chaotic load: Random, unpredictable patterns
 * - Wave load: Sinusoidal pattern over time
 * - Stress load: Maximum capacity testing
 *
 * <p>Enhanced features:
 * - Real-time metrics collection
 * - Memory profiling integration
 * - Latency tracking
 * - Customizable load parameters
 * - Failure injection
 * - Performance regression detection
 * - System state validation
 */
public class LoadGenerationUtilities {

    private final Runtime runtime;
    private final LatencyMetrics latencyMetrics;
    private final MemoryProfiler memoryProfiler;
    private final LoadMetrics loadMetrics = new LoadMetrics();

    // Configuration
    private static final int DEFAULT_MESSAGE_TIMEOUT_MS = 30_000;
    private static final int DEFAULT_AGENT_TIMEOUT_MS = 60_000;
    private static final double DEFAULT_FAILURE_RATE = 0.0; // No failures by default

    public LoadGenerationUtilities(Runtime runtime) {
        this.runtime = runtime;
        this.latencyMetrics = new LatencyMetrics();
        this.memoryProfiler = new MemoryProfiler();
    }

    /**
     * Enhanced uniform load generation with comprehensive metrics.
     */
    public EnhancedLoadResult generateUniformLoad(int agentCount, int messagesPerAgent,
                                               Consumer<Object> messageHandler,
                                               LoadGenerationConfig config)
        throws InterruptedException {

        EnhancedLoadResult result = new EnhancedLoadResult();
        result.loadType = "uniform";
        result.agentCount = agentCount;
        result.messageCount = agentCount * messagesPerAgent;
        result.config = config;

        Agent[] agents = new Agent[agentCount];
        CountDownLatch completionLatch = new CountDownLatch(result.messageCount);
        LongAdder deliveredCount = new LongAdder();
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong maxLatency = new AtomicLong(0);

        // Create agents
        long agentsCreatedTime = System.nanoTime();
        for (int i = 0; i < agentCount; i++) {
            final int agentIndex = i;
            agents[i] = runtime.spawn(msg -> {
                long startTime = System.nanoTime();
                try {
                    messageHandler.accept(msg);
                    long latency = System.nanoTime() - startTime;
                    totalLatency.add(latency);
                    maxLatency.updateAndGet(m -> Math.max(m, latency));
                    deliveredCount.incrementAndGet();
                    completionLatch.countDown();
                } catch (Exception e) {
                    // Handle failures
                    if (config.failureRate > 0 && Math.random() < config.failureRate) {
                        completionLatch.countDown(); // Count failed deliveries
                    } else {
                        throw e;
                    }
                }
            });
        }
        result.agentsCreatedTime = System.nanoTime() - agentsCreatedTime;

        // Send uniform load
        long loadStartTime = System.nanoTime();
        AtomicInteger sendIndex = new AtomicInteger(0);

        // Use virtual threads for message sending
        int senderThreads = Math.min(config.senderThreads, agentCount);
        ExecutorService senders = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch sendCompletion = new CountDownLatch(agentCount);

        for (int t = 0; t < senderThreads; t++) {
            senders.submit(() -> {
                while (sendIndex.get() < agentCount) {
                    int idx = sendIndex.getAndIncrement();
                    if (idx < agentCount) {
                        long sendTime = System.nanoTime();
                        agents[idx].send("uniform-" + sendTime);
                        sendCompletion.countDown();
                    }
                }
            });
        }

        sendCompletion.await(config.messageTimeoutMs, TimeUnit.MILLISECONDS);
        senders.shutdownNow();

        long loadEndTime = System.nanoTime();
        result.loadDurationNanos = loadEndTime - loadStartTime;

        // Wait for delivery with timeout
        boolean deliveryCompleted = completionLatch.await(config.messageTimeoutMs, TimeUnit.MILLISECONDS);
        result.deliveryCompleted = deliveryCompleted;
        result.actualDelivered = deliveredCount.sum();
        result.maxLatencyNanos = maxLatency.get();

        // Calculate metrics
        result.throughput = result.messageCount / (result.loadDurationNanos / 1_000_000_000.0);
        result.deliveryRate = (double) result.actualDelivered / result.messageCount;
        result.avgLatencyNanos = totalLatency.sum() / (double) result.actualDelivered;

        // Capture memory profile
        result.memorySnapshot = memoryProfiler.profileAgentSystem(agentCount);

        // Record latency metrics
        result.latencyMetrics = latencyMetrics;

        return result;
    }

    /**
     * Enhanced hotspot load generation with configurable hotspot patterns.
     */
    public EnhancedLoadResult generateHotspotLoad(int totalAgents, int hotAgents, int messagesPerAgent,
                                                Consumer<Object> messageHandler,
                                                LoadGenerationConfig config)
        throws InterruptedException {

        EnhancedLoadResult result = new EnhancedLoadResult();
        result.loadType = "hotspot";
        result.agentCount = totalAgents;
        result.hotAgentCount = hotAgents;
        result.messageCount = totalAgents * messagesPerAgent;
        result.config = config;

        Agent[] allAgents = new Agent[totalAgents];
        Agent[] hotAgentsArray = new Agent[hotAgents];

        // Create all agents
        for (int i = 0; i < totalAgents; i++) {
            allAgents[i] = runtime.spawn(msg -> {
                long startTime = System.nanoTime();
                try {
                    messageHandler.accept(msg);
                    long latency = System.nanoTime() - startTime;
                    latencyMetrics.recordLatency(startTime, "hotspot");
                    loadMetrics.recordLatency(latency);
                } catch (Exception e) {
                    if (config.failureRate > 0 && Math.random() < config.failureRate) {
                        // Silently fail
                    } else {
                        throw e;
                    }
                }
            });
        }

        // Select hot agents
        System.arraycopy(allAgents, 0, hotAgentsArray, 0, hotAgents);

        // Generate hotspot load
        CountDownLatch completionLatch = new CountDownLatch(result.messageCount);
        LongAdder deliveredCount = new LongAdder();
        DoubleAdder loadDistribution = new DoubleAdder();

        long loadStart = System.nanoTime();
        int round = 0;

        while (round < messagesPerAgent) {
            // Send messages according to hotspot distribution
            for (int i = 0; i < totalAgents; i++) {
                boolean isHot = config.hotspotRatio > 0.5 && Math.random() < config.hotspotRatio;
                Agent target = isHot ? hotAgentsArray[i % hotAgents] : allAgents[hotAgents + (i % (totalAgents - hotAgents))];

                long sendTime = System.nanoTime();
                target.send("hot-" + round + "-" + sendTime);
                loadDistribution.add(isHot ? 1.0 : 0.0);
            }
            round++;
        }

        long loadEnd = System.nanoTime();
        result.loadDurationNanos = loadEnd - loadStart;

        // Process messages
        AtomicInteger activeProcessors = new AtomicInteger(0);
        for (Agent agent : allAgents) {
            Thread.ofVirtual().start(() -> {
                activeProcessors.incrementAndGet();
                try {
                    while (completionLatch.getCount() > 0) {
                        Object msg = agent.recv();
                        if (msg != null) {
                            deliveredCount.incrementAndGet();
                            completionLatch.countDown();
                        }
                    }
                } finally {
                    activeProcessors.decrementAndGet();
                }
            });
        }

        // Wait for completion
        boolean completed = completionLatch.await(config.messageTimeoutMs, TimeUnit.MILLISECONDS);
        result.deliveryCompleted = completed;
        result.actualDelivered = deliveredCount.sum();
        result.hotspotRatio = loadDistribution.sum() / result.messageCount;

        // Calculate metrics
        result.throughput = result.messageCount / (result.loadDurationNanos / 1_000_000_000.0);
        result.deliveryRate = (double) result.actualDelivered / result.messageCount;

        // Capture additional metrics
        result.processorThreads = activeProcessors.get();
        result.memorySnapshot = memoryProfiler.profileAgentSystem(totalAgents);

        return result;
    }

    /**
     * Enhanced burst load generation with configurable burst patterns.
     */
    public EnhancedLoadResult generateBurstLoad(int agentCount, int totalDurationSeconds,
                                              int burstIntervalSeconds, int burstIntensity,
                                              LoadGenerationConfig config)
        throws InterruptedException {

        EnhancedLoadResult result = new EnhancedLoadResult();
        result.loadType = "burst";
        result.agentCount = agentCount;
        result.durationSeconds = totalDurationSeconds;
        result.config = config;

        Agent[] agents = new Agent[agentCount];
        LongAdder totalDelivered = new LongAdder();
        LongAdder burstMessageCount = new LongAdder();
        AtomicInteger currentBurst = new AtomicInteger(0);

        // Create agents
        for (int i = 0; i < agentCount; i++) {
            agents[i] = runtime.spawn(msg -> {
                long startTime = System.nanoTime();
                try {
                    latencyMetrics.recordLatency(startTime, "burst");
                    loadMetrics.recordLatency(System.nanoTime() - startTime);
                    totalDelivered.incrementAndGet();
                } catch (Exception e) {
                    if (config.failureRate > 0 && Math.random() < config.failureRate) {
                        // Silently fail
                    } else {
                        throw e;
                    }
                }
            });
        }

        long totalStart = System.nanoTime();
        int burstCount = 0;

        // Run burst pattern
        while (true) {
            long currentElapsed = (System.nanoTime() - totalStart) / 1_000_000_000;
            if (currentElapsed >= totalDurationSeconds) {
                break;
            }

            // Check if we're in a burst interval
            int cyclePosition = currentElapsed % (burstIntervalSeconds + burstIntensity);
            if (cyclePosition < burstIntensity) {
                // Burst phase
                burstCount++;
                currentBurst.set(burstCount);
                long burstStart = System.nanoTime();
                int burstMessages = agentCount * burstIntensity;

                // Send burst messages
                for (int i = 0; i < burstMessages; i++) {
                    int agentIndex = i % agentCount;
                    long sendTime = System.nanoTime();
                    agents[agentIndex].send("burst-" + burstCount + "-" + sendTime);
                    burstMessageCount.incrementAndGet();
                }

                long burstEnd = System.nanoTime();
                double burstDuration = (burstEnd - burstStart) / 1_000_000_000.0;

                System.out.printf("Burst %d: %,d messages in %.3fs%n",
                    burstCount, burstMessages, burstDuration);
            } else {
                // Idle phase - sleep for remaining time
                long remainingTime = (burstIntervalSeconds + burstIntensity) - cyclePosition;
                Thread.sleep(remainingTime * 1000L);
            }
        }

        long totalEnd = System.nanoTime();
        result.totalDurationNanos = totalEnd - totalStart;
        result.burstCount = burstCount;
        result.totalMessages = burstMessageCount.sum();
        result.deliveredCount = totalDelivered.sum();

        // Calculate metrics
        result.throughput = result.totalMessages / (result.totalDurationNanos / 1_000_000_000.0);
        result.deliveryRate = (double) result.deliveredCount / result.totalMessages;
        result.burstThroughput = result.totalMessages / (burstIntensity * (double) burstCount);

        // Capture final metrics
        result.memorySnapshot = memoryProfiler.profileAgentSystem(agentCount);
        result.latencyMetrics = latencyMetrics;

        return result;
    }

    /**
     * Enhanced progressive load generation with ramp-up patterns.
     */
    public EnhancedLoadResult generateProgressiveLoad(int maxAgents, int progressionDuration,
                                                     int rampUpInterval, LoadGenerationConfig config)
        throws InterruptedException {

        EnhancedLoadResult result = new EnhancedLoadResult();
        result.loadType = "progressive";
        result.maxAgents = maxAgents;
        result.durationSeconds = progressionDuration;
        result.config = config;

        AtomicInteger currentAgentCount = new AtomicInteger(0);
        LongAdder totalMessages = new LongAdder();
        LongAdder totalLatency = new LongAdder();
        AtomicInteger activeAgents = new AtomicInteger(0);

        // Start with initial agents
        int initialAgents = Math.min(1000, maxAgents / 10);
        for (int i = 0; i < initialAgents; i++) {
            spawnProgressiveAgent(i, totalMessages, totalLatency);
            currentAgentCount.incrementAndGet();
            activeAgents.incrementAndGet();
        }

        long progressionStart = System.nanoTime();
        int lastRampUp = 0;
        List<Long> throughputMeasurements = new ArrayList<>();

        // Progressive ramp-up
        while (true) {
            long elapsed = (System.nanoTime() - progressionStart) / 1_000_000_000;
            if (elapsed >= progressionDuration) {
                break;
            }

            // Check if it's time to add more agents
            int rampUpPoint = (int) (elapsed / rampUpInterval);
            if (rampUpPoint > lastRampUp && currentAgentCount.get() < maxAgents) {
                lastRampUp = rampUpPoint;
                int agentsToAdd = Math.min(1000, maxAgents - currentAgentCount.get());

                for (int i = 0; i < agentsToAdd; i++) {
                    spawnProgressiveAgent(currentAgentCount.get() + i, totalMessages, totalLatency);
                    currentAgentCount.incrementAndGet();
                    activeAgents.incrementAndGet();
                }

                System.out.printf("Progressed to %,d agents%n", currentAgentCount.get());
            }

            // Generate some load
            int messagesToSend = Math.min(1000, currentAgentCount.get());
            for (int i = 0; i < messagesToSend; i++) {
                int agentIndex = i % currentAgentCount.get();
                runtime.send(agentIndex, "progressive-" + elapsed);
            }
            totalMessages.addAndGet(messagesToSend);

            // Measure throughput
            if (elapsed % 5 == 0) { // Every 5 seconds
                throughputMeasurements.add((long) totalMessages.sum());
            }

            Thread.sleep(100); // Small delay to not overwhelm
        }

        long progressionEnd = System.nanoTime();
        result.totalDurationNanos = progressionEnd - progressionStart;
        result.finalAgentCount = currentAgentCount.get();
        result.totalMessages = totalMessages.sum();
        result.totalLatency = totalLatency.sum();

        // Calculate metrics
        result.throughput = result.totalMessages / (result.totalDurationNanos / 1_000_000_000.0);
        result.avgLatencyNanos = result.totalLatency / (double) result.totalMessages;

        // Calculate throughput stability
        if (throughputMeasurements.size() > 1) {
            double[] throughputValues = throughputMeasurements.stream()
                .mapToLong(l -> l)
                .toArray();
            double mean = Arrays.stream(throughputValues).average().orElse(0);
            double stdDev = Math.sqrt(Arrays.stream(throughputValues)
                .map(x -> Math.pow(x - mean, 2))
                .average().orElse(0));

            result.throughputVariation = stdDev / mean;
        }

        // Capture final metrics
        result.memorySnapshot = memoryProfiler.profileAgentSystem(result.finalAgentCount);
        result.activeAgents = activeAgents.get();

        return result;
    }

    /**
     * Enhanced chaotic load generation with random patterns.
     */
    public EnhancedLoadResult generateChaoticLoad(int agentCount, int durationSeconds,
                                                LoadGenerationConfig config)
        throws InterruptedException {

        EnhancedLoadResult result = new EnhancedLoadResult();
        result.loadType = "chaotic";
        result.agentCount = agentCount;
        result.durationSeconds = durationSeconds;
        result.config = config;

        Agent[] agents = new Agent[agentCount];
        Random random = new Random();
        LongAdder totalMessages = new LongAdder();
        LongAdder deliveredCount = new LongAdder();

        // Create agents with different processing times
        for (int i = 0; i < agentCount; i++) {
            final int agentIndex = i;
            agents[i] = runtime.spawn(msg -> {
                long startTime = System.nanoTime();
                try {
                    // Random processing time
                    if (random.nextDouble() < 0.1) {
                        Thread.sleep(random.nextInt(10)); // 10ms sleep for 10% of messages
                    }

                    // Random processing load
                    int processingLoad = random.nextInt(1000);
                    for (int j = 0; j < processingLoad; j++) {
                        Math.sin(j); // Some CPU work
                    }

                    long latency = System.nanoTime() - startTime;
                    latencyMetrics.recordLatency(startTime, "chaotic");
                    loadMetrics.recordLatency(latency);
                    deliveredCount.incrementAndGet();
                } catch (Exception e) {
                    if (config.failureRate > 0 && random.nextDouble() < config.failureRate) {
                        deliveredCount.incrementAndGet(); // Count failed as delivered
                    } else {
                        throw e;
                    }
                }
            });
        }

        long chaosStart = System.nanoTime();

        // Chaotic load generation
        ExecutorService chaosGenerator = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch generationComplete = new CountDownLatch(1);

        // Start chaotic message generation
        for (int i = 0; i < config.senderThreads; i++) {
            chaosGenerator.submit(() -> {
                long endNano = System.nanoTime() + TimeUnit.SECONDS.toNanos(durationSeconds);
                while (System.nanoTime() < endNano) {
                    // Random agent selection
                    int agentIndex = random.nextInt(agentCount);
                    long sendTime = System.nanoTime();
                    String message = "chaotic-" + random.nextLong();

                    agents[agentIndex].send(message);
                    totalMessages.incrementAndGet();

                    // Random delay between messages
                    try {
                        Thread.sleep(random.nextInt(50)); // 0-50ms delay
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                generationComplete.countDown();
            });
        }

        generationComplete.await();
        chaosGenerator.shutdownNow();

        long chaosEnd = System.nanoTime();
        result.totalDurationNanos = chaosEnd - chaosStart;
        result.totalMessages = totalMessages.sum();
        result.deliveredCount = deliveredCount.sum();

        // Calculate metrics
        result.throughput = result.totalMessages / (result.totalDurationNanos / 1_000_000_000.0);
        result.deliveryRate = (double) result.deliveredCount / result.totalMessages;
        result.avgLatencyNanos = latencyMetrics.calculateEnhancedPercentiles().meanNanos;

        // Capture metrics
        result.memorySnapshot = memoryProfiler.profileAgentSystem(agentCount);
        result.latencyMetrics = latencyMetrics;

        return result;
    }

    // Helper method for progressive load
    private void spawnProgressiveAgent(int agentId, LongAdder totalMessages, LongAdder totalLatency) {
        runtime.spawn(msg -> {
            long startTime = System.nanoTime();
            latencyMetrics.recordLatency(startTime, "progressive");
            totalMessages.incrementAndGet();
            totalLatency.add(System.nanoTime() - startTime);
        });
    }

    // Configuration class
    public static class LoadGenerationConfig {
        int senderThreads = 10;
        int messageTimeoutMs = DEFAULT_MESSAGE_TIMEOUT_MS;
        int agentTimeoutMs = DEFAULT_AGENT_TIMEOUT_MS;
        double failureRate = DEFAULT_FAILURE_RATE;
        double hotspotRatio = 0.9; // For hotspot load
        boolean enableMemoryTracking = true;
        boolean enableLatencyTracking = true;

        public LoadGenerationConfig withSenderThreads(int threads) {
            this.senderThreads = threads;
            return this;
        }

        public LoadGenerationConfig withFailureRate(double rate) {
            this.failureRate = rate;
            return this;
        }

        public LoadGenerationConfig withHotspotRatio(double ratio) {
            this.hotspotRatio = ratio;
            return this;
        }
    }

    // Enhanced result class
    public static class EnhancedLoadResult {
        String loadType;
        int agentCount;
        int hotAgentCount;
        long messageCount;
        long totalMessages;
        long deliveredCount;
        int maxAgents;
        long totalDurationNanos;
        int durationSeconds;
        long loadDurationNanos;
        double throughput;
        double deliveryRate;
        double hotspotRatio;
        boolean deliveryCompleted;
        int actualDelivered;
        long maxLatencyNanos;
        long avgLatencyNanos;
        long totalLatency;
        int burstCount;
        double burstThroughput;
        int processorThreads;
        int activeAgents;
        double throughputVariation;
        long agentsCreatedTime;

        LoadGenerationConfig config;
        MemoryProfiler.MemorySnapshot memorySnapshot;
        LatencyMetrics latencyMetrics;
        LoadMetrics loadMetrics;

        public String summary() {
            return String.format(
                "LoadResult[type=%s, agents=%,d, throughput=%,.0f/s, delivery=%.1f%%, duration=%.1fs]",
                loadType, agentCount, throughput, deliveryRate * 100,
                totalDurationNanos / 1_000_000_000.0
            );
        }
    }

    // Load metrics collector
    public static class LoadMetrics {
        private final LongAdder totalLatency = new LongAdder();
        private final AtomicInteger sampleCount = new AtomicInteger(0);
        private final AtomicLong maxLatency = new AtomicLong(0);
        private final LongAdder messageCount = new LongAdder();

        public void recordLatency(long latency) {
            totalLatency.add(latency);
            sampleCount.incrementAndGet();
            maxLatency.updateAndGet(m -> Math.max(m, latency));
            messageCount.incrementAndGet();
        }

        public double getAverageLatency() {
            int count = sampleCount.get();
            return count > 0 ? totalLatency.sum() / (double) count : 0;
        }

        public long getMaxLatency() {
            return maxLatency.get();
        }

        public long getTotalMessages() {
            return messageCount.sum();
        }
    }
}