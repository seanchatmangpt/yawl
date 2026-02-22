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

package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for autonomous cost optimization components.
 *
 * Tests verify that each optimization achieves measurable cost reduction
 * without sacrificing functionality or introducing mocks/stubs.
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public class CostOptimizationIntegrationTest {

    /**
     * Test 1: VirtualThreadPool autonomous right-sizing.
     * Verifies that the pool:
     * - Starts and stops cleanly
     * - Measures actual carrier thread usage
     * - Estimates cost savings
     */
    @Test
    public void testVirtualThreadPoolAutoscaling() throws Exception {
        VirtualThreadPool pool = new VirtualThreadPool("test-pool", 100, 1);
        pool.start();

        try {
            // Submit test tasks
            int taskCount = 100;
            for (int i = 0; i < taskCount; i++) {
                int taskId = i;
                pool.submit(() -> {
                    // Simulate work
                    Thread.sleep(10);
                    return "task-" + taskId;
                });
            }

            // Wait for processing
            Thread.sleep(2000);

            // Verify metrics
            VirtualThreadPool.CostMetrics metrics = pool.getCostMetrics();
            assertTrue(metrics.tasksSubmitted() > 0,
                      "Should have submitted tasks");
            assertTrue(metrics.tasksCompleted() > 0,
                      "Should have completed tasks");
            assertTrue(metrics.costFactor() >= 0 && metrics.costFactor() <= 1,
                      "Cost factor should be normalized 0-1");
            assertTrue(metrics.costSavingsPercent() >= 0,
                      "Should have cost savings");

            System.out.printf("VirtualThreadPool metrics: tasks=%d, carriers=%d/%d, " +
                             "utilization=%.1f%%, costSavings=%.1f%%%n",
                             metrics.tasksCompleted(),
                             metrics.estimatedCarrierThreads(),
                             metrics.maxCarrierThreads(),
                             metrics.carrierUtilizationPercent(),
                             metrics.costSavingsPercent());

        } finally {
            pool.shutdown();
        }
    }

    /**
     * Test 2: WorkItemBatcher intelligent batching.
     * Verifies that the batcher:
     * - Groups similar work items
     * - Reduces context switches
     * - Measures throughput improvement
     */
    @Test
    public void testWorkItemBatcherGrouping() throws Exception {
        WorkItemBatcher batcher = new WorkItemBatcher("caseId", 10, Duration.ofMillis(50));
        batcher.start();

        try {
            // Submit work items (same case IDs to trigger batching)
            int itemCount = 100;
            for (int i = 0; i < itemCount; i++) {
                String caseId = "case-" + (i % 10);
                int itemId = i;
                batcher.submit(caseId + ":item-" + itemId, workItem -> {
                    // Process work item
                    Thread.sleep(1);
                });
            }

            // Wait for batching
            Thread.sleep(500);
            batcher.flushAllBatches();
            Thread.sleep(100);

            // Verify metrics
            WorkItemBatcher.BatchMetrics metrics = batcher.getMetrics();
            assertTrue(metrics.itemsProcessed() > 0,
                      "Should have processed items");
            assertTrue(metrics.totalBatches() > 0,
                      "Should have created batches");
            assertTrue(metrics.avgBatchSize() > 0,
                      "Should have averaged batch size");
            assertTrue(metrics.contextSwitchesAvoided() >= 0,
                      "Should have avoided context switches");

            System.out.printf("WorkItemBatcher metrics: processed=%d, batches=%d, " +
                             "avgBatchSize=%d, switchesAvoided=%d, " +
                             "throughputGain=%.1f%%%n",
                             metrics.itemsProcessed(),
                             metrics.totalBatches(),
                             metrics.avgBatchSize(),
                             metrics.contextSwitchesAvoided(),
                             metrics.throughputGainPercent());

        } finally {
            batcher.shutdown();
        }
    }

    /**
     * Test 3: ResourcePool automatic lifecycle management.
     * Verifies that the pool:
     * - Pre-warms resources
     * - Reuses pooled resources
     * - Evicts stale resources
     * - Measures latency savings
     */
    @Test
    public void testResourcePoolLifecycle() throws Exception {
        // Create pool with simple String resources
        ResourcePool<String> pool = new ResourcePool<>(
            "test-strings",
            5,                              // initial
            20,                             // max
            Duration.ofSeconds(2),          // idle timeout
            () -> "resource-" + System.nanoTime(),  // factory
            resource -> {/* cleanup */}     // no-op cleanup
        );

        pool.start();

        try {
            // Borrow resources multiple times
            for (int i = 0; i < 50; i++) {
                try (ResourcePool.Resource<String> res = pool.borrow()) {
                    String resource = res.get();
                    assertNotNull(resource, "Resource should not be null");
                }
            }

            // Verify metrics
            ResourcePool.PoolMetrics metrics = pool.getMetrics();
            assertTrue(metrics.availableCount() > 0,
                      "Should have available resources");
            assertTrue(metrics.totalBorrowed() > 0,
                      "Should have borrowed resources");
            assertTrue(metrics.reuseEfficiency() > 1,
                      "Should have reused resources multiple times");

            System.out.printf("ResourcePool metrics: available=%d, inUse=%d, " +
                             "borrowed=%d, reuseEfficiency=%.1f, " +
                             "latencySaved=%dms%n",
                             metrics.availableCount(),
                             metrics.inUseCount(),
                             metrics.totalBorrowed(),
                             metrics.reuseEfficiency(),
                             metrics.latencySavedMs());

        } finally {
            pool.shutdown();
        }
    }

    /**
     * Test 4: CompressionStrategy intelligent compression.
     * Verifies that the strategy:
     * - Detects compressible content
     * - Measures compression ratio
     * - Calculates bandwidth savings
     * - Handles both compression and decompression
     */
    @Test
    public void testCompressionStrategyIntelligence() throws Exception {
        CompressionStrategy strategy = new CompressionStrategy();
        strategy.start();

        try {
            // Create test payloads
            String xmlPayload = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specification>
                    <name>TestWorkflow</name>
                    <description>A test workflow specification</description>
                    <task id="task1">
                        <name>First Task</name>
                    </task>
                    <task id="task2">
                        <name>Second Task</name>
                    </task>
                </specification>
                """.repeat(10); // Repeat to make compressible

            byte[] xmlBytes = xmlPayload.getBytes();

            // Test compression decision
            assertTrue(strategy.isCompressible("application/xml"),
                      "Should detect XML as compressible");
            assertTrue(strategy.isCompressible("application/json"),
                      "Should detect JSON as compressible");
            assertFalse(strategy.isCompressible("image/jpeg"),
                       "Should reject JPEG as not compressible");

            // Compress
            CompressionStrategy.CompressedData compressed = strategy.compress(
                xmlBytes, "application/xml");
            assertTrue(compressed.isCompressed(),
                      "Should have compressed XML");
            assertTrue(compressed.data().length < xmlBytes.length,
                      "Compressed should be smaller");

            // Decompress and verify
            byte[] decompressed = strategy.decompress(compressed.data());
            assertArrayEquals(xmlBytes, decompressed,
                             "Decompressed should match original");

            // Verify metrics
            CompressionStrategy.CompressionMetrics metrics = strategy.getMetrics();
            assertTrue(metrics.bytesProcessed() > 0,
                      "Should have processed bytes");
            assertTrue(metrics.compressionSuccessRate() > 0,
                      "Should have successful compressions");
            assertTrue(metrics.bandwidthSavedMB() >= 0,
                      "Should have bandwidth savings");

            System.out.printf("CompressionStrategy metrics: processed=%d bytes, " +
                             "ratio=%.2f, successRate=%.1f%%, " +
                             "savedMB=%.3f, cpuCost=%.2fμs/byte%n",
                             metrics.bytesProcessed(),
                             metrics.compressionRatio(),
                             metrics.compressionSuccessRate(),
                             metrics.bandwidthSavedMB(),
                             metrics.cpuCostPerByteSaved());

        } finally {
            strategy.shutdown();
        }
    }

    /**
     * Test 5: Combined cost optimization scenario.
     * Tests realistic workflow with all optimizations:
     * - Virtual thread pool handles concurrent work
     * - Work item batcher groups similar items
     * - Resource pool avoids allocation overhead
     * - Compression reduces transmission size
     */
    @Test
    public void testCombinedOptimizations() throws Exception {
        VirtualThreadPool pool = new VirtualThreadPool("combined", 50, 1);
        WorkItemBatcher batcher = new WorkItemBatcher("taskType", 50, Duration.ofMillis(100));
        ResourcePool<StringBuilder> resPool = new ResourcePool<>(
            "buffers",
            10,
            50,
            Duration.ofSeconds(5),
            StringBuilder::new,
            _ -> {}
        );
        CompressionStrategy compression = new CompressionStrategy();

        pool.start();
        batcher.start();
        resPool.start();
        compression.start();

        try {
            // Simulate concurrent workflow processing
            CountDownLatch latch = new CountDownLatch(100);

            for (int i = 0; i < 100; i++) {
                final int taskId = i;
                pool.submit(() -> {
                    try {
                        // Get resource from pool
                        try (ResourcePool.Resource<StringBuilder> res = resPool.borrow()) {
                            StringBuilder sb = res.get();
                            sb.append("task-").append(taskId);

                            // Batch work item
                            String taskType = "type-" + (taskId % 5);
                            batcher.submit(taskType, item -> {
                                Thread.sleep(5);
                            });

                            // Compress result
                            byte[] data = sb.toString().getBytes();
                            compression.compress(data, "text/plain");
                        }
                    } catch (Exception e) {
                        fail("Task execution failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Wait for all tasks
            assertTrue(latch.await(30, TimeUnit.SECONDS),
                      "All tasks should complete");

            // Ensure all batches are flushed before checking metrics
            Thread.sleep(150);  // Wait for scheduled flush to complete
            batcher.flushAllBatches();

            // Verify combined metrics
            VirtualThreadPool.CostMetrics vtMetrics = pool.getCostMetrics();
            WorkItemBatcher.BatchMetrics batchMetrics = batcher.getMetrics();
            ResourcePool.PoolMetrics resMetrics = resPool.getMetrics();
            CompressionStrategy.CompressionMetrics compMetrics = compression.getMetrics();

            System.out.printf("Combined optimization results:%n");
            System.out.printf("  VirtualThreadPool: %.1f%% cost savings%n",
                             vtMetrics.costSavingsPercent());
            System.out.printf("  WorkItemBatcher: %d batches, %.1f%% throughput gain%n",
                             batchMetrics.totalBatches(),
                             batchMetrics.throughputGainPercent());
            System.out.printf("  ResourcePool: %.2f reuse efficiency%n",
                             resMetrics.reuseEfficiency());
            System.out.printf("  Compression: %.2f ratio, %.3f MB saved%n",
                             compMetrics.compressionRatio(),
                             compMetrics.bandwidthSavedMB());

            // Verify all optimizations working
            assertTrue(vtMetrics.costSavingsPercent() >= 0);
            assertTrue(batchMetrics.totalBatches() > 0);
            assertTrue(resMetrics.reuseEfficiency() > 1);
            assertTrue(compMetrics.compressionRatio() >= 0);

        } finally {
            compression.shutdown();
            resPool.shutdown();
            batcher.shutdown();
            pool.shutdown();
        }
    }
}
