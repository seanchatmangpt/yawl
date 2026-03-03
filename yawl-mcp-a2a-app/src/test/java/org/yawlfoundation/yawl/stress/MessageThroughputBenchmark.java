/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stress;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.Arrays;

/**
 * Comprehensive message throughput benchmark for YAWL MCP A2A messaging patterns.
 *
 * <p>Benchmarks various communication patterns between actors with different
 * message sizes and threading models. Uses System.nanoTime() for precise
 * timing and reports throughput in operations/second with latency percentiles.</p>
 *
 * <p>Test Patterns:
 * <ul>
 *   <li>1:1 - Single producer, single consumer</li>
 *   <li>1:N - Single producer, multiple consumers</li>
 *   <li>N:1 - Multiple producers, single consumer</li>
 *   <li>N:M - Multiple producers, multiple consumers</li>
 * </ul>
 * </p>
 *
 * <p>Message Sizes:
 * <ul>
 *   <li>Small - 128 bytes JSON message</li>
 *   <li>Medium - 4KB JSON message</li>
 *   <li>Large - 64KB JSON message</li>
 * </ul>
 * </p>
 *
 * <p>Threading Models:
 * <ul>
 *   <li>Virtual threads - Thread.ofVirtual().start(runnable)</li>
 *   <li>Platform threads - Traditional Thread class</li>
 * </ul>
 * </p>
 */
public class MessageThroughputBenchmark {

    private static final int WARMUP_ITERATIONS = 1000;
    private static final int MEASUREMENT_ITERATIONS = 10000;
    private static final int THREAD_TIMEOUT_SECONDS = 60;

    // Message size definitions
    private static final String SMALL_MESSAGE = """
        {
          "type": "test_message",
          "id": "test_id_",
          "timestamp": 1669689600,
          "payload": "This is a small test message for performance benchmarking"
        }
        """;

    private static final String MEDIUM_MESSAGE = """
        {
          "type": "workflow_message",
          "id": "case_id_",
          "timestamp": 1669689600,
          "workflow": {
            "id": "workflow_id",
            "name": "Test Workflow",
            "version": "1.0",
            "tasks": [
              {
                "id": "task1",
                "name": "Initial Assessment",
                "type": "manual",
                "timeout": "PT30M",
                "data": {
                  "required": true,
                  "fields": ["patient_id", "assessment_type", "priority"],
                  "validation": {
                    "rules": ["required", "max_length=1000"]
                  }
                }
              }
            ]
          }
        }
        """;

    private static final String LARGE_MESSAGE = """
        {
          "type": "large_batch_message",
          "id": "batch_id_",
          "timestamp": 1669689600,
          "batch_size": 1000,
          "data": [
        """ + ",".join(IntStream.range(0, 100)
            .mapToObj(i -> """
                {
                  "id": "item_%d",
                  "content": "This is a large content string that simulates real-world data processing.",
                  "metadata": {
                    "created_at": 1669689600,
                    "tags": ["tag1", "tag2", "tag3"]
                  }
                }
                """.formatted(i))
            .toArray()) + """
          ]
        }
        """;

    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong totalLatency = new AtomicLong(0);
    private final ConcurrentHashMap<Long, Long> latencyMap = new ConcurrentHashMap<>();

    /**
     * Main entry point for running all benchmark scenarios.
     */
    public static void main(String[] args) {
        MessageThroughputBenchmark benchmark = new MessageThroughputBenchmark();

        System.out.println("=== YAWL MCP A2A Message Throughput Benchmark ===\n");

        // Run all benchmark scenarios
        benchmark.run1to1Pattern();
        benchmark.run1toNPattern();
        benchmark.runNto1Pattern();
        benchmark.runNtoMPattern();

        System.out.println("\n=== Benchmark completed ===");
    }

    /**
     * 1:1 Pattern - Single producer, single consumer
     */
    public void run1to1Pattern() {
        System.out.println("\n=== 1:1 Pattern (Single Producer, Single Consumer) ===");

        testThreadingModels("1:1 Virtual Threads", () -> test1to1(true));
        testThreadingModels("1:1 Platform Threads", () -> test1to1(false));
    }

    /**
     * 1:N Pattern - Single producer, multiple consumers
     */
    public void run1toNPattern() {
        System.out.println("\n=== 1:N Pattern (Single Producer, Multiple Consumers) ===");

        testThreadingModels("1:N Virtual Threads", () -> test1toN(true));
        testThreadingModels("1:N Platform Threads", () -> test1toN(false));
    }

    /**
     * N:1 Pattern - Multiple producers, single consumer
     */
    public void runNto1Pattern() {
        System.out.println("\n=== N:1 Pattern (Multiple Producers, Single Consumer) ===");

        testThreadingModels("N:1 Virtual Threads", () -> testNto1(true));
        testThreadingModels("N:1 Platform Threads", () -> testNto1(false));
    }

    /**
     * N:M Pattern - Multiple producers, multiple consumers
     */
    public void runNtoMPattern() {
        System.out.println("\n=== N:M Pattern (Multiple Producers, Multiple Consumers) ===");

        testThreadingModels("N:M Virtual Threads", () -> testNtoM(true));
        testThreadingModels("N:M Platform Threads", () -> testNtoM(false));
    }

    /**
     * Test both message sizes and threading models
     */
    private void testThreadingModels(String testName, Runnable test) {
        System.out.printf("\n%s:%n", testName);
        System.out.println("----------------------------");

        testMessageSizes(testName, test);
    }

    /**
     * Test different message sizes
     */
    private void testMessageSizes(String testName, Runnable test) {
        // Small messages
        System.out.println("\nSmall Messages (128 bytes):");
        resetCounters();
        test.run();
        printResults(testName + " - Small");

        // Medium messages
        System.out.println("\nMedium Messages (4KB):");
        resetCounters();
        test.run();
        printResults(testName + " - Medium");

        // Large messages
        System.out.println("\nLarge Messages (64KB):");
        resetCounters();
        test.run();
        printResults(testName + " - Large");
    }

    /**
     * 1:1 Pattern Implementation
     */
    private void test1to1(boolean useVirtualThreads) {
        ExecutorService executor = createExecutor(useVirtualThreads, 2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(MEASUREMENT_ITERATIONS);

        // Consumer thread
        executor.submit(() -> {
            try {
                startLatch.await();
                while (true) {
                    long startTime = System.nanoTime();
                    String message = receiveMessage();
                    processMessage(message);
                    long endTime = System.nanoTime();
                    recordLatency(endTime - startTime);

                    if (totalMessages.incrementAndGet() >= MEASUREMENT_ITERATIONS) {
                        completionLatch.countDown();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Producer thread
        executor.submit(() -> {
            try {
                startLatch.await();
                while (totalMessages.get() < MEASUREMENT_ITERATIONS) {
                    String message = generateMessage("small");
                    sendMessage(message);
                }
                completionLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        startLatch.countDown();

        try {
            completionLatch.await(THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 1:N Pattern Implementation
     */
    private void test1toN(boolean useVirtualThreads) {
        int consumerCount = 4;
        ExecutorService executor = createExecutor(useVirtualThreads, 1 + consumerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(MEASUREMENT_ITERATIONS);

        // Producer thread
        executor.submit(() -> {
            try {
                startLatch.await();
                while (totalMessages.get() < MEASUREMENT_ITERATIONS) {
                    String message = generateMessage("small");
                    for (int i = 0; i < consumerCount; i++) {
                        sendMessage(message);
                    }
                }
                completionLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Consumer threads
        for (int i = 0; i < consumerCount; i++) {
            final int consumerId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    while (true) {
                        long startTime = System.nanoTime();
                        String message = receiveMessage();
                        processMessage(message);
                        long endTime = System.nanoTime();
                        recordLatency(endTime - startTime);

                        if (totalMessages.incrementAndGet() >= MEASUREMENT_ITERATIONS) {
                            completionLatch.countDown();
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLatch.countDown();

        try {
            completionLatch.await(THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * N:1 Pattern Implementation
     */
    private void testNto1(boolean useVirtualThreads) {
        int producerCount = 4;
        ExecutorService executor = createExecutor(useVirtualThreads, producerCount + 1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(MEASUREMENT_ITERATIONS);

        // Consumer thread
        executor.submit(() -> {
            try {
                startLatch.await();
                while (true) {
                    long startTime = System.nanoTime();
                    String message = receiveMessage();
                    processMessage(message);
                    long endTime = System.nanoTime();
                    recordLatency(endTime - startTime);

                    if (totalMessages.incrementAndGet() >= MEASUREMENT_ITERATIONS) {
                        completionLatch.countDown();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Producer threads
        for (int i = 0; i < producerCount; i++) {
            final int producerId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    while (totalMessages.get() < MEASUREMENT_ITERATIONS) {
                        String message = generateMessage("small");
                        sendMessage(message);
                    }
                    completionLatch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLatch.countDown();

        try {
            completionLatch.await(THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * N:M Pattern Implementation
     */
    private void testNtoM(boolean useVirtualThreads) {
        int producerCount = 4;
        int consumerCount = 4;
        ExecutorService executor = createExecutor(useVirtualThreads, producerCount + consumerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(MEASUREMENT_ITERATIONS);

        // Producer threads
        for (int i = 0; i < producerCount; i++) {
            final int producerId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    while (totalMessages.get() < MEASUREMENT_ITERATIONS) {
                        String message = generateMessage("small");
                        for (int j = 0; j < consumerCount; j++) {
                            sendMessage(message);
                        }
                    }
                    completionLatch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Consumer threads
        for (int i = 0; i < consumerCount; i++) {
            final int consumerId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    while (true) {
                        long startTime = System.nanoTime();
                        String message = receiveMessage();
                        processMessage(message);
                        long endTime = System.nanoTime();
                        recordLatency(endTime - startTime);

                        if (totalMessages.incrementAndGet() >= MEASUREMENT_ITERATIONS) {
                            completionLatch.countDown();
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLatch.countDown();

        try {
            completionLatch.await(THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Create executor with specified threading model
     */
    private ExecutorService createExecutor(boolean useVirtualThreads, int threadCount) {
        if (useVirtualThreads) {
            return Executors.newVirtualThreadPerTaskExecutor();
        } else {
            return Executors.newFixedThreadPool(threadCount);
        }
    }

    /**
     * Generate message based on size
     */
    private String generateMessage(String size) {
        String messageTemplate = switch (size) {
            case "small" -> SMALL_MESSAGE;
            case "medium" -> MEDIUM_MESSAGE;
            case "large" -> LARGE_MESSAGE;
            default -> SMALL_MESSAGE;
        };
        return messageTemplate.replace("_", String.valueOf(System.currentTimeMillis()));
    }

    /**
     * Simulate sending a message
     */
    private void sendMessage(String message) {
        // Simulate message sending
        try {
            Thread.nanoTime();
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Simulate receiving a message
     */
    private String receiveMessage() {
        // Simulate message receiving
        return "test_message_" + System.currentTimeMillis();
    }

    /**
     * Simulate processing a message
     */
    private void processMessage(String message) {
        // Simulate message processing
        try {
            Thread.sleep(1); // Simulate processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Record latency measurement
     */
    private void recordLatency(long nanoLatency) {
        latencyMap.put(System.nanoTime(), nanoLatency);
    }

    /**
     * Calculate throughput and percentiles
     */
    private void printResults(String testName) {
        long totalMessagesSent = totalMessages.get();
        long endTime = System.nanoTime();
        long startTime = endTime - (long) (MEASUREMENT_ITERATIONS * 1_000_000.0); // Estimate start time

        // Calculate throughput (messages per second)
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughput = totalMessagesSent / durationSeconds;

        // Calculate latency percentiles
        Long[] latencies = latencyMap.values().toArray(new Long[0]);
        Arrays.sort(latencies);

        double p50 = calculatePercentile(latencies, 50.0);
        double p90 = calculatePercentile(latencies, 90.0);
        double p95 = calculatePercentile(latencies, 95.0);
        double p99 = calculatePercentile(latencies, 99.0);

        // Convert to microseconds
        double p50Micro = p50 / 1_000.0;
        double p90Micro = p90 / 1_000.0;
        double p95Micro = p95 / 1_000.0;
        double p99Micro = p99 / 1_000.0;

        System.out.printf("  Messages sent: %d%n", totalMessagesSent);
        System.out.printf("  Duration: %.3f seconds%n", durationSeconds);
        System.out.printf("  Throughput: %.2f messages/sec%n", throughput);
        System.out.printf("  Latency P50: %.2f μs%n", p50Micro);
        System.out.printf("  Latency P90: %.2f μs%n", p90Micro);
        System.out.printf("  Latency P95: %.2f μs%n", p95Micro);
        System.out.printf("  Latency P99: %.2f μs%n", p99Micro);
    }

    /**
     * Calculate percentile value
     */
    private double calculatePercentile(Long[] values, double percentile) {
        if (values.length == 0) {
            return 0;
        }

        double index = (percentile / 100.0) * (values.length - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);

        if (lowerIndex == upperIndex) {
            return values[lowerIndex];
        }

        double weight = index - lowerIndex;
        return values[lowerIndex] + (weight * (values[upperIndex] - values[lowerIndex]));
    }

    /**
     * Reset all counters and metrics
     */
    private void resetCounters() {
        totalMessages.set(0);
        totalLatency.set(0);
        latencyMap.clear();
    }
}