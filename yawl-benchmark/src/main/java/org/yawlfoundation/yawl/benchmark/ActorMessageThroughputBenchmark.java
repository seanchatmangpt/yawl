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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Actor Message Throughput Benchmarks.
 *
 * <p>Measures message throughput under various loads and patterns. Tests include:
 * - Single-threaded message rate
 * - Multi-threaded message distribution
 * - Message batching vs individual messages
 * - Heavy and light message patterns
 * - Memory pressure under high throughput</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 5)
@Fork(value = 3, jvmArgs = {
    "-Xms4g", "-Xmx8g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Djmh.executor=VIRTUAL_TPE"
})
public class ActorMessageThroughputBenchmark {

    // Configuration
    @Param({"100", "1000", "10000"})
    private int messageBatchSize;

    @Param({"single", "roundrobin", "broadcast", "random"})
    private String messagePattern;

    @Param({"light", "heavy"})
    private String messageType;

    @Param({"1", "2", "4", "8", "16"})
    private int senderThreads;

    // Test data
    private YStatelessEngine engine;
    private YSpecification spec;
    
    // Metrics
    private final AtomicInteger activeMessages = new AtomicInteger(0);
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final AtomicLong droppedMessages = new AtomicLong(0);
    private final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
    
    // Threading
    private ExecutorService senderPool;
    private final BlockingQueue<Runnable> messageQueue = new LinkedBlockingQueue<>();
    private final CountDownLatch completionLatch;
    
    public ActorMessageThroughputBenchmark() {
        this.completionLatch = new CountDownLatch(1);
    }

    @Setup(Level.Trial)
    public void setup() throws YSyntaxException {
        // Initialize engine
        engine = new YStatelessEngine();
        spec = engine.unmarshalSpecification(BenchmarkSpecFactory.SEQUENTIAL_2_TASK);

        // Setup thread pools
        senderPool = Executors.newFixedThreadPool(senderThreads);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        senderPool.shutdownNow();
        try {
            if (!senderPool.awaitTermination(5, TimeUnit.SECONDS)) {
                senderPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Throughput Tests ─────────────────────────────────────────────────────

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void singleThreadedThroughput() throws InterruptedException {
        long testDuration = 10000; // 10 seconds
        Instant start = Instant.now();
        AtomicLong messageCount = new AtomicLong(0);

        // Start message sender
        Thread sender = new Thread(() -> {
            try {
                while (Duration.between(start, Instant.now()).toMillis() < testDuration) {
                    sendSingleMessage(messageCount);
                }
            } catch (Exception e) {
                // Handle exception
            }
        });
        sender.start();

        // Collect metrics periodically
        ScheduledExecutorService metricCollector = Executors.newSingleThreadScheduledExecutor();
        metricCollector.scheduleAtFixedRate(() -> {
            long currentCount = messageCount.get();
            long elapsed = Duration.between(start, Instant.now()).toSeconds();
            System.out.printf("Throughput: %d msg/s%n", currentCount / elapsed);
        }, 1, 1, TimeUnit.SECONDS);

        sender.join();
        metricCollector.shutdown();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void multiThreadedThroughput() throws InterruptedException {
        long testDuration = 10000; // 10 seconds
        Instant start = Instant.now();
        final AtomicInteger messageCount = new AtomicInteger(0);

        // Start multiple senders
        List<Thread> senders = IntStream.range(0, senderThreads)
            .mapToObj(i -> new Thread(() -> {
                try {
                    while (Duration.between(start, Instant.now()).toMillis() < testDuration) {
                        if (messagePattern.equals("single")) {
                            sendSingleMessage(messageCount);
                        } else if (messagePattern.equals("roundrobin")) {
                            sendRoundRobinMessage(i, messageCount);
                        } else if (messagePattern.equals("broadcast")) {
                            sendBroadcastMessage(messageCount);
                        } else if (messagePattern.equals("random")) {
                            sendRandomMessage(messageCount);
                        }
                    }
                } catch (Exception e) {
                    // Handle exception
                }
            }))
            .collect(Collectors.toList());

        senders.forEach(Thread::start);

        // Collect metrics
        ScheduledExecutorService metricCollector = Executors.newSingleThreadScheduledExecutor();
        metricCollector.scheduleAtFixedRate(() -> {
            int currentCount = messageCount.get();
            long elapsed = Duration.between(start, Instant.now()).toSeconds();
            System.out.printf("Multi-threaded throughput: %d msg/s (%d threads)%n", 
                currentCount / elapsed, senderThreads);
        }, 1, 1, TimeUnit.SECONDS);

        // Wait for completion
        for (Thread sender : senders) {
            sender.join();
        }
        metricCollector.shutdown();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void batchThroughput() throws InterruptedException {
        long testDuration = 10000; // 10 seconds
        Instant start = Instant.now();
        final AtomicInteger messageCount = new AtomicInteger(0);

        // Batch message sender
        Thread batchSender = new Thread(() -> {
            try {
                while (Duration.between(start, Instant.now()).toMillis() < testDuration) {
                    sendBatch(messageCount);
                    // Batch processing time
                    LockSupport.parkNanos(1000_000); // 1ms
                }
            } catch (Exception e) {
                // Handle exception
            }
        });
        batchSender.start();

        // Collect metrics
        ScheduledExecutorService metricCollector = Executors.newSingleThreadScheduledExecutor();
        metricCollector.scheduleAtFixedRate(() -> {
            int currentCount = messageCount.get();
            long elapsed = Duration.between(start, Instant.now()).toSeconds();
            System.out.printf("Batch throughput: %d msg/s (batch size: %d)%n", 
                currentCount / elapsed, messageBatchSize);
        }, 1, 1, TimeUnit.SECONDS);

        batchSender.join();
        metricCollector.shutdown();
    }

    // ── Message Pattern Implementations ─────────────────────────────────────

    private void sendSingleMessage(AtomicInteger counter) {
        try {
            long startTime = System.nanoTime();
            String caseId = "single-case-" + counter.incrementAndGet();
            engine.createCase(spec.getID(), caseId);
            
            // Record latency
            long latency = System.nanoTime() - startTime;
            latencies.add(latency);
            
            // Update metrics
            totalMessages.incrementAndGet();
            totalBytes.addAndGet(calculateMessageSize(caseId));
        } catch (Exception e) {
            droppedMessages.incrementAndGet();
        }
    }

    private void sendRoundRobinMessage(int senderId, AtomicInteger counter) {
        try {
            long startTime = System.nanoTime();
            int actorId = counter.get() % 100; // Round-robin across 100 actors
            String caseId = "roundrobin-case-" + senderId + "-" + actorId + "-" + counter.incrementAndGet();
            engine.createCase(spec.getID(), caseId);
            
            long latency = System.nanoTime() - startTime;
            latencies.add(latency);
            
            totalMessages.incrementAndGet();
            totalBytes.addAndGet(calculateMessageSize(caseId));
        } catch (Exception e) {
            droppedMessages.incrementAndGet();
        }
    }

    private void sendBroadcastMessage(AtomicInteger counter) {
        try {
            long startTime = System.nanoTime();
            String caseId = "broadcast-case-" + counter.incrementAndGet();
            engine.createCase(spec.getID(), caseId);
            
            long latency = System.nanoTime() - startTime;
            latencies.add(latency);
            
            totalMessages.incrementAndGet();
            totalBytes.addAndGet(calculateMessageSize(caseId) * 10); // Broadcast factor
        } catch (Exception e) {
            droppedMessages.incrementAndGet();
        }
    }

    private void sendRandomMessage(AtomicInteger counter) {
        try {
            long startTime = System.nanoTime();
            int actorId = (int)(Math.random() * 1000); // Random actor
            String caseId = "random-case-" + actorId + "-" + counter.incrementAndGet();
            engine.createCase(spec.getID(), caseId);
            
            long latency = System.nanoTime() - startTime;
            latencies.add(latency);
            
            totalMessages.incrementAndGet();
            totalBytes.addAndGet(calculateMessageSize(caseId));
        } catch (Exception e) {
            droppedMessages.incrementAndGet();
        }
    }

    private void sendBatch(AtomicInteger counter) throws InterruptedException {
        List<String> batchMessages = new ArrayList<>(messageBatchSize);
        for (int i = 0; i < messageBatchSize; i++) {
            batchMessages.add("batch-case-" + counter.incrementAndGet());
        }
        
        long startTime = System.nanoTime();
        
        // Send batch
        for (String caseId : batchMessages) {
            engine.createCase(spec.getID(), caseId);
        }
        
        // Record batch processing time
        long batchTime = System.nanoTime() - startTime;
        latencies.add(batchTime / messageBatchSize); // Average per message
        
        totalMessages.addAndGet(messageBatchSize);
        totalBytes.addAndGet(batchMessages.stream().mapToLong(this::calculateMessageSize).sum());
    }

    // ── Helper Methods ──────────────────────────────────────────────────────

    private long calculateMessageSize(String caseId) {
        // Simulate message size calculation
        if (messageType.equals("light")) {
            return caseId.length() * 2; // Light message
        } else {
            return caseId.length() * 10 + 1000; // Heavy message with payload
        }
    }

    // ── Metrics Accessors ───────────────────────────────────────────────────

    public long getTotalMessages() {
        return totalMessages.get();
    }

    public long getTotalBytes() {
        return totalBytes.get();
    }

    public long getDroppedMessages() {
        return droppedMessages.get();
    }

    public double getAverageLatency() {
        return latencies.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
    }

    public double getMessageSize() {
        return totalBytes.get() / (double)totalMessages.get();
    }
}
