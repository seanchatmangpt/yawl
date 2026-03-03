/*
 * YAWL Mailbox Stress Test Demo
 *
 * Standalone demonstration of mailbox overflow stress testing capabilities.
 * This class can be run directly to showcase the stress testing patterns.
 */

package org.yawlfoundation.yawl.stress;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo class to demonstrate mailbox overflow stress testing patterns.
 * Can be run directly with: java StressTestDemo
 */
public class StressTestDemo {

    public static void main(String[] args) {
        System.out.println("YAWL Mailbox Overflow Stress Test Implementation");
        System.out.println("============================================");

        try {
            executeBoundedQueueOverflowTest();
            executeUnboundedQueueGrowthTest();
            executeBackpressureComparisonTest();
            executeMemoryUsagePatternsTest();

        } catch (Exception e) {
            System.err.println("Error during test execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Executes bounded queue overflow behavior with blocking.
     */
    private static void executeBoundedQueueOverflowTest() throws InterruptedException {
        System.out.println("\n1. Bounded Queue Overflow Demo");
        System.out.println("===============================");

        final int CAPACITY = 20;
        final int PRODUCERS = 3;
        final int CONSUMERS = 1;

        BlockingQueue<String> queue = new ArrayBlockingQueue<>(CAPACITY);
        AtomicInteger sent = new AtomicInteger(0);
        AtomicInteger blocked = new AtomicInteger(0);
        AtomicInteger consumed = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(PRODUCERS + CONSUMERS);

        // Start producers that send faster than consumers can process
        for (int i = 0; i < PRODUCERS; i++) {
            executor.submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        String message = "PRODUCER_MSG_" + sent.incrementAndGet();
                        long startTime = System.currentTimeMillis();

                        // Send with potential blocking
                        if (queue.offer(message, 50, TimeUnit.MILLISECONDS)) {
                            long sendTime = System.currentTimeMillis() - startTime;
                            if (sendTime > 10) {
                                blocked.incrementAndGet();
                            }
                        } else {
                            System.out.println("Message dropped (queue full)");
                        }

                        // Small delay between sends
                        Thread.sleep(5);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Start consumer that processes slowly
        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String message = queue.take(); // Blocking receive
                    consumed.incrementAndGet();

                    // Simulate slow processing
                    Thread.sleep(20);

                    if (consumed.get() % 10 == 0) {
                        System.out.printf("Queue status: %d/%d consumed%n",
                                consumed.get(), sent.get());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        // Run for 5 seconds
        Thread.sleep(5000);

        // Shutdown
        executor.shutdownNow();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        System.out.printf("Results:%n");
        System.out.printf("  Sent: %d, Consumed: %d, Blocked: %d%n",
                sent.get(), consumed.get(), blocked.get());
        System.out.printf("  Queue size: %d (capacity: %d)%n",
                queue.size(), CAPACITY);
    }

    /**
     * Executes unbounded queue memory growth.
     */
    private static void executeUnboundedQueueGrowthTest() throws InterruptedException {
        System.out.println("\n2. Unbounded Queue Growth Demo");
        System.out.println("==============================");

        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        AtomicInteger sent = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        // Single producer that floods the queue
        Thread producer = new Thread(() -> {
            try {
                while (System.currentTimeMillis() - startTime < 3000) {
                    String largeMessage = createLargeMessage(sent.incrementAndGet());
                    queue.put(largeMessage);

                    if (sent.get() % 1000 == 0) {
                        System.out.println("Sent " + sent.get() + " messages");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();

        // Monitor queue size over time
        while (producer.isAlive()) {
            Thread.sleep(100);
            System.out.printf("Queue size: %,d (elapsed: %.1fs)%n",
                    queue.size(), (System.currentTimeMillis() - startTime) / 1000.0);
        }

        // Final statistics
        long endTime = System.currentTimeMillis();
        System.out.printf("\nFinal results:%n");
        System.out.printf("  Messages sent: %,d%n", sent.get());
        System.out.printf("  Queue size: %,d%n", queue.size());
        System.out.printf("  Duration: %.1f seconds%n", (endTime - startTime) / 1000.0);
        System.out.printf("  Avg send rate: %.1f msg/sec%n",
                sent.get() / ((endTime - startTime) / 1000.0));
    }

    /**
     * Compares different queue types under load.
     */
    private static void executeBackpressureComparisonTest() {
        System.out.println("\n3. Backpressure Comparison");
        System.out.println("===========================");

        int[] capacities = {10, 50, 100};
        int testDuration = 2000; // 2 seconds

        for (int capacity : capacities) {
            System.out.printf("\nTesting capacity: %d%n", capacity);

            // Test bounded queue
            BlockingQueue<String> bounded = new ArrayBlockingQueue<>(capacity);
            StressTestMetrics boundedMetrics = new StressTestMetrics("Bounded-" + capacity);

            simulateLoad(bounded, boundedMetrics, testDuration);
            StressTestMetrics.StressReport boundedReport = boundedMetrics.generateReport();

            System.out.printf("  Bounded - Dropped: %.1f%%, Blocked: %.1f%%%n",
                    boundedReport.droppedPercentage, boundedReport.blockedPercentage);

            // Clean up for next test
            bounded.clear();
        }
    }

    /**
     * Executes memory usage patterns with large messages.
     */
    private static void executeMemoryUsagePatternsTest() throws InterruptedException {
        System.out.println("\n4. Memory Usage Patterns");
        System.out.println("========================");

        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(100);
        AtomicInteger sent = new AtomicInteger(0);

        // Producer sending large messages
        Thread producer = new Thread(() -> {
            try {
                while (true) {
                    byte[] message = new byte[1024]; // 1KB messages
                    queue.put(message);
                    sent.incrementAndGet();

                    if (sent.get() % 500 == 0) {
                        long currentMemory = runtime.totalMemory() - runtime.freeMemory();
                        long used = currentMemory - initialMemory;
                        System.out.printf("Sent %,d messages, Memory used: %,d KB%n",
                                sent.get(), used / 1024);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();

        // Monitor memory for 10 seconds
        for (int i = 0; i < 10; i++) {
            Thread.sleep(1000);
            long currentMemory = runtime.totalMemory() - runtime.freeMemory();
            long used = currentMemory - initialMemory;
            System.out.printf("Time %ds: Memory %,d KB, Queue size: %d%n",
                    i + 1, used / 1024, queue.size());
        }

        // Shutdown
        producer.interrupt();

        // Final memory check
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long totalUsed = finalMemory - initialMemory;

        System.out.printf("\nMemory summary:%n");
        System.out.printf("  Initial: %,d KB%n", initialMemory / 1024);
        System.out.printf("  Final:   %,d KB%n", finalMemory / 1024);
        System.out.printf("  Used:    %,d KB%n", totalUsed / 1024);
        System.out.printf("  Messages: %,d (%.1f KB each)%n",
                sent.get(), (double) totalUsed / sent.get());
    }

    /**
     * Simulates load on a queue for testing.
     */
    private static void simulateLoad(BlockingQueue<String> queue,
                                   StressTestMetrics metrics,
                                   int durationMs) {
        Thread producer = new Thread(() -> {
            long endTime = System.currentTimeMillis() + durationMs;
            while (System.currentTimeMillis() < endTime) {
                try {
                    String message = "TEST_" + System.currentTimeMillis();
                    queue.put(message);
                    metrics.recordMessageSent();
                    metrics.recordQueueSize(queue.size());
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        Thread consumer = new Thread(() -> {
            long endTime = System.currentTimeMillis() + durationMs + 1000;
            while (System.currentTimeMillis() < endTime) {
                try {
                    String message = queue.poll(10, TimeUnit.MILLISECONDS);
                    if (message != null) {
                        Thread.sleep(5); // Simulate processing
                        metrics.recordMessageDelivered(5);
                    }
                    metrics.recordQueueSize(queue.size());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        metrics.stop();
    }

    /**
     * Creates a large message for testing.
     */
    private static String createLargeMessage(int number) {
        StringBuilder sb = new StringBuilder();
        sb.append("Message_").append(number).append("_");
        for (int i = 0; i < 100; i++) {
            sb.append("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
        }
        return sb.toString();
    }
}