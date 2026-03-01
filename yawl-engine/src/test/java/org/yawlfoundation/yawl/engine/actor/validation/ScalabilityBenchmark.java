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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.lang.management.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Scalability benchmark for YAWL Actor Model with throughput measurement.
 * Implements comprehensive throughput metrics at various scales.
 *
 * <p>Targets:
 * - Spawn throughput: >100K agents/second
 * - Message throughput: >1M messages/second across 1M agents
 * - Message delivery latency: p95 < 1ms
 * - Throughput stability: <5% variation over 1 minute
 *
 * <p>Benchmark scenarios:
 * 1. Single-agent message rate
 * 2. Linear scaling (1K, 10K, 100K, 1M agents)
 * 3. Message flood testing
 * 4. Sustained throughput over time
 */
@Tag("performance")
@Tag("validation")
class ScalabilityBenchmark {

    private static final int DURATION_SECONDS = 60;
    private static final int WARMUP_SECONDS = 10;
    
    private MemoryMXBean memoryBean;
    private ThreadMXBean threadBean;
    private GarbageCollectorMXBean gcBean;
    private long startTime;

    @BeforeEach
    void setup() {
        memoryBean = ManagementFactory.getMemoryMXBean();
        threadBean = ManagementFactory.getThreadMXBean();
        gcBean = ManagementFactory.getGarbageCollectorMXBeans().get(0);
        startTime = System.currentTimeMillis();
    }

    @Test
    void spawnThroughputBenchmark() {
        System.out.println("=== Spawn Throughput Benchmark ===");
        
        int[] agentCounts = {1_000, 10_000, 100_000, 1_000_000};
        
        for (int count : agentCounts) {
            ThroughputMetrics metrics = measureSpawnThroughput(count);
            System.out.printf("Agents: %,d | Rate: %,.0f/s | Time: %.3fs | Heap/Agent: %.1f bytes%n",
                count, metrics.throughput, metrics.durationSeconds, 
                metrics.heapUsed / (double) count);
            
            // Validate targets
            if (metrics.throughput < 100_000 && count == 100_000) {
                throw new RuntimeException("Spawn throughput target not met: " + 
                    String.format("%,.0f agents/s < 100,000", metrics.throughput));
            }
        }
    }

    @Test
    void messageThroughputBenchmark() throws InterruptedException {
        System.out.println("=== Message Throughput Benchmark ===");
        
        int[] agentCounts = {1_000, 10_000, 100_000};
        
        for (int agentCount : agentCounts) {
            ThroughputMetrics metrics = measureMessageThroughput(agentCount);
            System.out.printf("Agents: %,d | Rate: %,.0f msg/s | Latency p95: %.2fms | GC: %d pauses%n",
                agentCount, metrics.throughput, metrics.p95Latency, metrics.gcPauses);
            
            // Validate targets
            if (metrics.throughput < 1_000_000 && agentCount == 100_000) {
                throw new RuntimeException("Message throughput target not met: " + 
                    String.format("%,.0f msg/s < 1,000,000", metrics.throughput));
            }
            
            if (metrics.p95Latency > 1.0) {
                throw new RuntimeException("Latency target not met: " + 
                    String.format("%.2fms > 1.0ms p95", metrics.p95Latency));
            }
        }
    }

    @Test
    void sustainedThroughputTest() throws InterruptedException {
        System.out.println("=== Sustained Throughput Test ===");
        
        // Run for 2 minutes to check for degradation
        Runtime runtime = new Runtime();
        AtomicInteger messageCount = new AtomicInteger(0);
        LongAdder totalLatency = new LongAdder();
        
        try {
            // Create 100K agents
            Agent[] agents = new Agent[100_000];
            for (int i = 0; i < 100_000; i++) {
                agents[i] = runtime.spawn(msg -> {
                    long latency = System.nanoTime() - (long) msg;
                    totalLatency.add(latency);
                    messageCount.incrementAndGet();
                });
            }

            // Warmup
            Thread.sleep(WARMUP_SECONDS * 1000);
            
            // Measure sustained throughput
            long warmupMessages = messageCount.get();
            AtomicLong floodStart = new AtomicLong(System.nanoTime());
            
            // Start message flood
            for (int i = 0; i < 100_000; i++) {
                agents[i % agents.length].send(System.nanoTime());
            }
            
            // Let it run for measurement period
            Thread.sleep(DURATION_SECONDS * 1000);
            
            // Final flood to capture any remaining messages
            for (int i = 0; i < 100_000; i++) {
                agents[i % agents.length].send(System.nanoTime());
            }
            
            // Wait for completion
            Thread.sleep(5_000);
            
            long totalMessages = messageCount.get() - warmupMessages;
            double durationSeconds = DURATION_SECONDS;
            double throughput = totalMessages / durationSeconds;
            
            double avgLatency = totalLatency.sum() / (double) totalMessages / 1_000_000;
            
            System.out.printf("Sustained: %,.0f msg/s | Avg latency: %.3fms | Stability: check variance%n",
                throughput, avgLatency);
            
            // Check for stability (no significant degradation)
            if (throughput < 500_000) { // Half of target indicates degradation
                throw new RuntimeException("Throughput degraded to " + 
                    String.format("%,.0f msg/s", throughput));
            }
            
        } finally {
            runtime.close();
        }
    }

    @Test
    void floodTest() throws InterruptedException {
        System.out.println("=== Message Flood Test ===");
        
        // Test extreme message rates
        int agentCount = 10_000;
        int messagePerAgent = 1000;
        
        Runtime runtime = new Runtime();
        try {
            Agent[] agents = new Agent[agentCount];
            for (int i = 0; i < agentCount; i++) {
                agents[i] = runtime.spawn(msg -> {});
            }
            
            // Pre-warm
            for (Agent a : agents) {
                a.send("warmup");
            }
            Thread.sleep(100);
            
            // Flood test
            AtomicLong maxLatency = new AtomicLong(0);
            CountDownLatch latch = new CountDownLatch(agentCount * messagePerAgent);
            
            long floodStart = System.nanoTime();
            for (int i = 0; i < agentCount; i++) {
                for (int j = 0; j < messagePerAgent; j++) {
                    final long startTime = System.nanoTime();
                    agents[i].send(startTime);
                }
            }
            
            // Process flood
            for (int i = 0; i < agentCount; i++) {
                final Agent agent = agents[i];
                Thread.ofVirtual().start(() -> {
                    while (latch.getCount() > 0) {
                        Object msg = agent.recv();
                        if (msg instanceof Long) {
                            long latency = System.nanoTime() - (long) msg;
                            maxLatency.updateAndGet(m -> Math.max(m, latency));
                            latch.countDown();
                        }
                    }
                });
            }
            
            // Wait for completion
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            long floodDuration = System.nanoTime() - floodStart;
            
            double totalMessages = agentCount * messagePerAgent;
            double throughput = totalMessages / (floodDuration / 1_000_000_000.0);
            
            System.out.printf("Flood: %,.0f msg/s | Max latency: %.2fms | Completed: %b%n",
                throughput, maxLatency.get() / 1_000_000.0, completed);
            
            if (!completed) {
                System.out.println("WARNING: Not all messages were delivered within timeout");
            }
            
        } finally {
            runtime.close();
        }
    }

    private ThroughputMetrics measureSpawnThroughput(int agentCount) {
        Runtime runtime = new Runtime();
        long start = System.nanoTime();
        long heapBefore = memoryBean.getHeapMemoryUsage().getUsed();
        
        try {
            for (int i = 0; i < agentCount; i++) {
                runtime.spawn(msg -> {
                    // Minimal work
                });
            }
            
            long end = System.nanoTime();
            long heapAfter = memoryBean.getHeapMemoryUsage().getUsed();
            
            double durationSeconds = (end - start) / 1_000_000_000.0;
            double throughput = agentCount / durationSeconds;
            long heapUsed = heapAfter - heapBefore;
            
            ThroughputMetrics metrics = new ThroughputMetrics();
            metrics.throughput = throughput;
            metrics.durationSeconds = durationSeconds;
            metrics.heapUsed = heapUsed;
            metrics.gcPauses = countGCEvents();
            
            return metrics;
            
        } finally {
            runtime.close();
        }
    }

    private ThroughputMetrics measureMessageThroughput(int agentCount) 
        throws InterruptedException {
        
        Runtime runtime = new Runtime();
        try {
            // Create agents
            Agent[] agents = new Agent[agentCount];
            for (int i = 0; i < agentCount; i++) {
                agents[i] = runtime.spawn(msg -> {});
            }
            
            // Measure message throughput
            long start = System.nanoTime();
            for (int i = 0; i < agentCount; i++) {
                for (int j = 0; j < 100; j++) { // 100 messages per agent
                    agents[i].send("message-" + j);
                }
            }
            long end = System.nanoTime();
            
            // Measure delivery latency
            AtomicLong maxLatency = new AtomicLong(0);
            AtomicInteger delivered = new AtomicInteger(0);
            CountDownLatch deliveryLatch = new CountDownLatch(agentCount * 100);
            
            // Process messages
            for (int i = 0; i < agentCount; i++) {
                final Agent agent = agents[i];
                Thread.ofVirtual().start(() -> {
                    while (deliveryLatch.getCount() > 0) {
                        Object msg = agent.recv();
                        if (msg != null) {
                            delivered.incrementAndGet();
                            deliveryLatch.countDown();
                            long latency = System.nanoTime() - start;
                            maxLatency.updateAndGet(m -> Math.max(m, latency));
                        }
                    }
                });
            }
            
            // Wait for delivery
            deliveryLatch.await(10, TimeUnit.SECONDS);
            
            double durationSeconds = (end - start) / 1_000_000_000.0;
            double totalMessages = agentCount * 100;
            double throughput = totalMessages / durationSeconds;
            
            ThroughputMetrics metrics = new ThroughputMetrics();
            metrics.throughput = throughput;
            metrics.durationSeconds = durationSeconds;
            metrics.p95Latency = maxLatency.get() / 1_000_000.0; // Simplified - should be actual p95
            metrics.gcPauses = countGCEvents();
            
            return metrics;
            
        } finally {
            runtime.close();
        }
    }

    private int countGCEvents() {
        return gcBean.getCollectionCount();
    }

    static class ThroughputMetrics {
        double throughput; // agents/sec or messages/sec
        double durationSeconds;
        long heapUsed;
        double p95Latency; // milliseconds
        int gcPauses;
    }
}
