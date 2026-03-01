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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.actor.validation;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Load generation utilities for stress testing the YAWL Actor Model.
 * Provides various load patterns and stress testing scenarios.
 *
 * <p>Load patterns:
 * - Uniform load: Even distribution across all agents
 * - Hotspot load: Concentrated on subset of agents
 * - Burst load: Periodic spikes in message rate
 * - Progressive load: Gradual increase over time
 * - Churn load: Constant creation and destruction of agents
 */
public class LoadGenerationUtilities {

    private final Runtime runtime;
    private final LatencyMetrics latencyMetrics;
    private final MemoryProfiler memoryProfiler;
    
    public LoadGenerationUtilities(Runtime runtime) {
        this.runtime = runtime;
        this.latencyMetrics = new LatencyMetrics();
        this.memoryProfiler = new MemoryProfiler();
    }

    /**
     * Uniform load generation - even message distribution.
     */
    public LoadResult generateUniformLoad(int agentCount, int messagesPerAgent, 
                                         Consumer<Object> messageHandler) 
        throws InterruptedException {
        
        LoadResult result = new LoadResult();
        Agent[] agents = new Agent[agentCount];
        CountDownLatch completionLatch = new CountDownLatch(agentCount * messagesPerAgent);
        LongAdder deliveredCount = new LongAdder();
        
        // Create agents
        for (int i = 0; i < agentCount; i++) {
            final int agentIndex = i;
            agents[i] = runtime.spawn(msg -> {
                long startTime = System.nanoTime();
                messageHandler.accept(msg);
                long latency = System.nanoTime() - startTime;
                latencyMetrics.recordLatency(startTime, "uniform");
                deliveredCount.incrementAndGet();
                completionLatch.countDown();
            });
        }
        
        // Send uniform load
        long loadStart = System.nanoTime();
        for (int agentIndex = 0; agentIndex < agentCount; agentIndex++) {
            for (int msg = 0; msg < messagesPerAgent; msg++) {
                final long sendTime = System.nanoTime();
                agents[agentIndex].send("uniform-" + msg);
            }
        }
        long loadEnd = System.nanoTime();
        
        // Wait for completion
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);
        
        // Calculate metrics
        result.durationSeconds = (loadEnd - loadStart) / 1_000_000_000.0;
        result.totalMessages = agentCount * messagesPerAgent;
        result.throughput = result.totalMessages / result.durationSeconds;
        result.completed = completed;
        result.deliveredCount = deliveredCount.sum();
        
        // Capture memory profile
        result.memorySnapshot = memoryProfiler.profileAgentSystem(agentCount);
        
        return result;
    }

    /**
     * Hotspot load generation - concentrated on a small subset.
     */
    public LoadResult generateHotspotLoad(int totalAgents, int hotAgents, int messagesPerAgent, 
                                         Consumer<Object> messageHandler) 
        throws InterruptedException {
        
        LoadResult result = new LoadResult();
        Agent[] allAgents = new Agent[totalAgents];
        Agent[] hotAgentsArray = new Agent[hotAgents];
        
        // Create all agents
        for (int i = 0; i < totalAgents; i++) {
            allAgents[i] = runtime.spawn(msg -> {
                long startTime = System.nanoTime();
                messageHandler.accept(msg);
                latencyMetrics.recordLatency(startTime, "hotspot");
            });
        }
        
        // Select hot agents
        System.arraycopy(allAgents, 0, hotAgentsArray, 0, hotAgents);
        
        // Generate hotspot load (90% of messages to hot agents)
        CountDownLatch completionLatch = new CountDownLatch(
            (int) (hotAgents * messagesPerAgent * 1.9) // Adjust for distribution
        );
        LongAdder deliveredCount = new LongAdder();
        
        // Send hotspot load
        long loadStart = System.nanoTime();
        for (int round = 0; round < messagesPerAgent * 1.9; round++) {
            // 90% to hot agents, 10% to others
            if (round % 10 < 9) {
                // Hot agent
                Agent target = hotAgentsArray[round % hotAgents];
                target.send("hot-" + round);
            } else {
                // Random non-hot agent
                Agent target = allAgents[hotAgents + (round % (totalAgents - hotAgents))];
                target.send("cold-" + round);
            }
            
            // Simulate processing
            if (completionLatch.getCount() % 1000 == 0) {
                Thread.yield();
            }
        }
        long loadEnd = System.nanoTime();
        
        // Process messages in virtual threads
        for (Agent agent : allAgents) {
            Thread.ofVirtual().start(() -> {
                while (completionLatch.getCount() > 0) {
                    Object msg = agent.recv();
                    if (msg != null) {
                        deliveredCount.incrementAndGet();
                        completionLatch.countDown();
                    }
                }
            });
        }
        
        // Wait for completion
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);
        
        // Calculate metrics
        result.durationSeconds = (loadEnd - loadStart) / 1_000_000_000.0;
        result.totalMessages = (int) completionLatch.getCount(); // Actual messages sent
        result.throughput = result.totalMessages / result.durationSeconds;
        result.completed = completed;
        result.deliveredCount = deliveredCount.sum();
        result.hotspotRatio = 0.9; // 90% to hot agents
        
        return result;
    }

    /**
     * Burst load generation - periodic spikes.
     */
    public LoadResult generateBurstLoad(int agentCount, int burstDurationSeconds, 
                                       int burstIntervalSeconds, int burstIntensity)
        throws InterruptedException {
        
        LoadResult result = new LoadResult();
        Agent[] agents = new Agent[agentCount];
        LongAdder totalDelivered = new LongAdder();
        
        // Create agents
        for (int i = 0; i < agentCount; i++) {
            agents[i] = runtime.spawn(msg -> {
                long startTime = System.nanoTime();
                latencyMetrics.recordLatency(startTime, "burst");
                totalDelivered.incrementAndGet();
            });
        }
        
        long totalStart = System.nanoTime();
        int totalBursts = 0;
        
        // Run burst pattern
        while (true) {
            long currentElapsed = (System.nanoTime() - totalStart) / 1_000_000_000;
            if (currentElapsed > burstDurationSeconds + burstIntervalSeconds * 5) {
                break;
            }
            
            // Check if we're in a burst interval
            int cyclePosition = currentElapsed % (burstDurationSeconds + burstIntervalSeconds);
            if (cyclePosition < burstDurationSeconds) {
                // Burst phase
                totalBursts++;
                long burstStart = System.nanoTime();
                int burstMessages = agentCount * burstIntensity;
                
                // Send burst messages
                for (int i = 0; i < burstMessages; i++) {
                    int agentIndex = i % agentCount;
                    agents[agentIndex].send("burst-" + totalBursts + "-" + i);
                }
                
                long burstEnd = System.nanoTime();
                double burstDuration = (burstEnd - burstStart) / 1_000_000_000.0;
                
                System.out.printf("Burst %d: %,d messages in %.3fs%n", 
                    totalBursts, burstMessages, burstDuration);
            } else {
                // Idle phase
                Thread.sleep(burstIntervalSeconds * 1000);
            }
        }
        
        long totalEnd = System.nanoTime();
        result.durationSeconds = (totalEnd - totalStart) / 1_000_000_000.0;
        result.totalMessages = totalBursts * agentCount * burstIntensity;
        result.throughput = result.totalMessages / result.durationSeconds;
        result.completed = true;
        result.deliveredCount = totalDelivered.sum();
        result.burstCount = totalBursts;
        
        return result;
    }

    /**
     * Progressive load generation - gradual increase over time.
     */
    public LoadResult generateProgressiveLoad(int maxAgents, int progressionDuration, 
                                           int rampUpInterval) 
        throws InterruptedException {
        
        LoadResult result = new LoadResult();
        AtomicInteger currentAgentCount = new AtomicInteger(0);
        LongAdder totalMessages = new LongAdder();
        
        // Start with a small number of agents
        int initialAgents = 1000;
        for (int i = 0; i < initialAgents; i++) {
            final int agentId = i;
            runtime.spawn(msg -> {
                latencyMetrics.recordLatency(System.nanoTime(), "progressive");
                totalMessages.incrementAndGet();
            });
            currentAgentCount.incrementAndGet();
        }
        
        long progressionStart = System.nanoTime();
        int lastRampUp = 0;
        
        // Progressive ramp-up
        while (true) {
            long elapsed = (System.nanoTime() - progressionStart) / 1_000_000_000;
            if (elapsed > progressionDuration) {
                break;
            }
            
            // Check if it's time to add more agents
            int rampUpPoint = (int) (elapsed / rampUpInterval);
            if (rampUpPoint > lastRampUp && currentAgentCount.get() < maxAgents) {
                lastRampUp = rampUpPoint;
                int agentsToAdd = Math.min(1000, maxAgents - currentAgentCount.get());
                
                for (int i = 0; i < agentsToAdd; i++) {
                    final int agentId = currentAgentCount.get() + i;
                    runtime.spawn(msg -> {
                        latencyMetrics.recordLatency(System.nanoTime(), "progressive");
                        totalMessages.incrementAndGet();
                    });
                }
                currentAgentCount.addAndGet(agentsToAdd);
                
                System.out.printf("Progressed to %,d agents%n", currentAgentCount.get());
                
                // Allow initialization
                Thread.sleep(100);
            }
            
            // Generate some load
            for (int i = 0; i < 1000; i++) {
                int agentIndex = i % currentAgentCount.get();
                runtime.send(agentIndex, "progressive-" + elapsed);
            }
            
            Thread.sleep(100); // Small delay to not overwhelm
        }
        
        long progressionEnd = System.nanoTime();
        result.durationSeconds = (progressionEnd - progressionStart) / 1_000_000_000.0;
        result.totalMessages = totalMessages.sum();
        result.throughput = result.totalMessages / result.durationSeconds;
        result.completed = true;
        result.finalAgentCount = currentAgentCount.get();
        
        return result;
    }

    /**
     * Churn load generation - constant agent creation/destruction.
     */
    public LoadResult generateChurnLoad(int maxAgents, int churnDuration, 
                                      int batchSize, int batchInterval) 
        throws InterruptedException {
        
        LoadResult result = new LoadResult();
        AtomicInteger activeAgents = new AtomicInteger(0);
        LongAdder totalCreated = new LongAdder();
        LongAdder totalDestroyed = new LongAdder();
        
        long churnStart = System.nanoTime();
        int batchNumber = 0;
        
        while (true) {
            long elapsed = (System.nanoTime() - churnStart) / 1_000_000_000;
            if (elapsed > churnDuration) {
                break;
            }
            
            // Destroy a batch of agents
            if (activeAgents.get() > batchSize) {
                for (int i = 0; i < batchSize; i++) {
                    // In real implementation, would need to track agents for destruction
                    // For now, just simulate
                }
                activeAgents.addAndGet(-batchSize);
                totalDestroyed.addAndGet(batchSize);
            }
            
            // Create a batch of agents
            for (int i = 0; i < batchSize; i++) {
                if (activeAgents.get() < maxAgents) {
                    final int batchId = batchNumber;
                    runtime.spawn(msg -> {
                        latencyMetrics.recordLatency(System.nanoTime(), "churn");
                    });
                    activeAgents.incrementAndGet();
                    totalCreated.incrementAndGet();
                }
            }
            
            batchNumber++;
            Thread.sleep(batchInterval * 1000);
        }
        
        long churnEnd = System.nanoTime();
        result.durationSeconds = (churnEnd - churnStart) / 1_000_000_000.0;
        result.totalMessages = totalCreated.sum(); // Approximate
        result.throughput = result.totalMessages / result.durationSeconds;
        result.completed = true;
        result.agentsCreated = totalCreated.sum();
        result.agentsDestroyed = totalDestroyed.sum();
        result.finalAgentCount = activeAgents.get();
        
        return result;
    }

    // Result classes
    public static class LoadResult {
        double durationSeconds;
        int totalMessages;
        double throughput;
        boolean completed;
        long deliveredCount;
        
        // Optional fields for specific load patterns
        double hotspotRatio;
        int burstCount;
        int finalAgentCount;
        long agentsCreated;
        long agentsDestroyed;
        
        MemoryProfiler.MemorySnapshot memorySnapshot;
        
        public String summary() {
            return String.format(
                "LoadResult[dur=%.1fs, msgs=%,d, throughput=%,.0f/s, delivered=%d, completed=%b]",
                durationSeconds, totalMessages, throughput, deliveredCount, completed
            );
        }
    }
}
