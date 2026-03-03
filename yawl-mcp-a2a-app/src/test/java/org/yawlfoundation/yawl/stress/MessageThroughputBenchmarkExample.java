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

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Example usage of MessageThroughputBenchmark with custom configuration.
 *
 * <p>This example demonstrates how to:
 * 1. Configure custom benchmark parameters
 * 2. Extend the benchmark with custom message patterns
 * 3. Integrate with existing YAWL components
 * 4. Collect and analyze custom metrics</p>
 */
public class MessageThroughputBenchmarkExample {

    /**
     * Example of extending the benchmark with custom metrics
     */
    public static class CustomThroughputBenchmark extends MessageThroughputBenchmark {

        private final AtomicLong totalBytesProcessed = new AtomicLong(0);
        private final AtomicInteger peakConcurrentMessages = new AtomicInteger(0);
        private final LongAdder gcCount = new LongAdder();
        private final LongAdder gcTime = new LongAdder();

        @Override
        protected void recordLatency(long nanoLatency) {
            super.recordLatency(nanoLatency);

            // Track GC events
            long lastGcCount = gcCount.sum();
            System.gc();
            if (gcCount.sum() > lastGcCount) {
                gcCount.increment();
            }
        }

        public void withCustomMetrics(Runnable benchmark) {
            // Track concurrent message count
            trackConcurrentMessages(benchmark);
        }

        private void trackConcurrentMessages(Runnable benchmark) {
            // Implement concurrent message tracking
            benchmark.run();
        }

        public long getTotalBytesProcessed() {
            return totalBytesProcessed.get();
        }

        public int getPeakConcurrentMessages() {
            return peakConcurrentMessages.get();
        }

        public long getGcCount() {
            return gcCount.sum();
        }
    }

    /**
     * Example of benchmarking with YAWL work items
     */
    public static class YAWLWorkItemBenchmark {

        private final YAWLWorkItemGenerator generator;
        private final YAWLWorkItemProcessor processor;
        private final ExecutorService executor;

        public YAWLWorkItemBenchmark(int threadCount) {
            this.generator = new YAWLWorkItemGenerator();
            this.processor = new YAWLWorkItemProcessor();
            this.executor = Executors.newFixedThreadPool(threadCount);
        }

        /**
         * Benchmark YAWL work item processing with different patterns
         */
        public void runWorkItemBenchmark() throws InterruptedException {
            System.out.println("=== YAWL Work Item Throughput Benchmark ===\n");

            // Test different workload patterns
            testSingleThreadWorkload();
            testMultiThreadWorkload();
            testBatchWorkload();
        }

        private void testSingleThreadWorkload() throws InterruptedException {
            System.out.println("Single-threaded workload:");

            int workItemCount = 1000;
            CountDownLatch latch = new CountDownLatch(workItemCount);
            long startTime = System.nanoTime();

            for (int i = 0; i < workItemCount; i++) {
                final int workItemId = i;
                executor.submit(() -> {
                    try {
                        // Generate work item
                        YAWLWorkItem item = generator.generateWorkItem(workItemId);

                        // Process work item
                        processor.processWorkItem(item);

                        // Complete work item
                        generator.completeWorkItem(item);

                    } catch (Exception e) {
                        System.err.println("Error processing work item " + workItemId + ": " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            long endTime = System.nanoTime();

            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughput = workItemCount / durationSeconds;

            System.out.printf("  Work items processed: %d%n", workItemCount);
            System.out.printf("  Duration: %.3f seconds%n", durationSeconds);
            System.out.printf("  Throughput: %.2f items/sec%n", throughput);
        }

        private void testMultiThreadWorkload() throws InterruptedException {
            System.out.println("\nMulti-threaded workload:");

            int threadCount = 4;
            int workItemCountPerThread = 250;
            int totalWorkItems = threadCount * workItemCountPerThread;
            CountDownLatch latch = new CountDownLatch(totalWorkItems);
            long startTime = System.nanoTime();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    for (int i = 0; i < workItemCountPerThread; i++) {
                        try {
                            int workItemId = threadId * workItemCountPerThread + i;
                            YAWLWorkItem item = generator.generateWorkItem(workItemId);
                            processor.processWorkItem(item);
                            generator.completeWorkItem(item);
                        } catch (Exception e) {
                            System.err.println("Error in thread " + threadId + ": " + e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            long endTime = System.nanoTime();

            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughput = totalWorkItems / durationSeconds;

            System.out.printf("  Threads: %d%n", threadCount);
            System.out.printf("  Work items processed: %d%n", totalWorkItems);
            System.out.printf("  Duration: %.3f seconds%n", durationSeconds);
            System.out.printf("  Throughput: %.2f items/sec%n", throughput);
        }

        private void testBatchWorkload() throws InterruptedException {
            System.out.println("\nBatch workload:");

            int batchSize = 100;
            int batchCount = 10;
            int totalWorkItems = batchSize * batchCount;
            CountDownLatch latch = new CountDownLatch(totalWorkItems);
            long startTime = System.nanoTime();

            for (int b = 0; b < batchCount; b++) {
                final int batchId = b;

                // Process batch in parallel
                for (int i = 0; i < batchSize; i++) {
                    final int workItemId = b * batchSize + i;
                    executor.submit(() -> {
                        try {
                            YAWLWorkItem item = generator.generateWorkItem(workItemId);
                            processor.processWorkItem(item);
                            generator.completeWorkItem(item);
                        } catch (Exception e) {
                            System.err.println("Error in batch " + batchId + ": " + e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }

            latch.await(30, TimeUnit.SECONDS);
            long endTime = System.nanoTime();

            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughput = totalWorkItems / durationSeconds;

            System.out.printf("  Batch size: %d%n", batchSize);
            System.out.printf("  Batches: %d%n", batchCount);
            System.out.printf("  Work items processed: %d%n", totalWorkItems);
            System.out.printf("  Duration: %.3f seconds%n", durationSeconds);
            System.out.printf("  Throughput: %.2f items/sec%n", throughput);
        }

        public void shutdown() {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Simulated YAWL Work Item
     */
    public static class YAWLWorkItem {
        private final String caseId;
        private final String taskId;
        private final String data;
        private final long timestamp;

        public YAWLWorkItem(String caseId, String taskId, String data) {
            this.caseId = caseId;
            this.taskId = taskId;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public String getCaseId() { return caseId; }
        public String getTaskId() { return taskId; }
        public String getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * Generator for YAWL work items
     */
    public static class YAWLWorkItemGenerator {
        public YAWLWorkItem generateWorkItem(int id) {
            String caseId = "case-" + (id / 10);
            String taskId = "task-" + (id % 10);
            String data = String.format("{\"id\": %d, \"payload\": \"Work item data %d\"}", id, id);

            try {
                // Simulate work item generation time
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return new YAWLWorkItem(caseId, taskId, data);
        }

        public void completeWorkItem(YAWLWorkItem item) {
            // Simulate work item completion
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Processor for YAWL work items
     */
    public static class YAWLWorkItemProcessor {
        public void processWorkItem(YAWLWorkItem item) {
            // Simulate work item processing
            try {
                Thread.sleep(5); // Longer processing time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Main method to run the examples
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== YAWL MCP A2A Benchmark Examples ===\n");

        // Example 1: Custom benchmark with extended metrics
        CustomThroughputBenchmark customBenchmark = new CustomThroughputBenchmark();
        System.out.println("Running custom benchmark...");
        customBenchmark.withCustomMetrics(() -> {
            try {
                customBenchmark.run1to1Pattern();
            } catch (Exception e) {
                System.err.println("Error running custom benchmark: " + e.getMessage());
            }
        });

        // Example 2: YAWL Work Item benchmark
        YAWLWorkItemBenchmark workItemBenchmark = new YAWLWorkItemBenchmark(4);
        workItemBenchmark.runWorkItemBenchmark();
        workItemBenchmark.shutdown();

        System.out.println("\n=== Examples completed ===");
    }
}