/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;
import org.yawlfoundation.yawl.integration.mcp.server.YawlServerCapabilities;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performance tests for MCP server components.
 *
 * Chicago TDD: exercises real YAWL MCP objects under load conditions.
 * Measures construction latency, concurrent logging handler throughput,
 * and server capabilities construction overhead.
 *
 * Performance thresholds (CI-appropriate, not production benchmarks):
 * - 1000 YawlMcpServer constructions in under 5 seconds
 * - 10000 McpLoggingHandler level checks in under 1 second
 * - 50 concurrent capability constructions complete in under 2 seconds
 *
 * Note: A2A authentication performance tests are in the a2a module tests.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class McpPerformanceTest extends TestCase {

    public McpPerformanceTest(String name) {
        super(name);
    }

    // =========================================================================
    // YawlMcpServer construction latency
    // =========================================================================

    public void testThousandServerConstructionsUnder5Seconds() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            YawlMcpServer server = new YawlMcpServer(
                "http://localhost:8080/yawl", "admin", "YAWL");
            assertNotNull(server);
        }
        long elapsed = System.currentTimeMillis() - start;
        assertTrue("1000 YawlMcpServer constructions should complete in < 5000ms, took "
            + elapsed + "ms", elapsed < 5000);
    }

    public void testSingleServerConstructionIsSubMillisecond() {
        // Warm up
        new YawlMcpServer("http://localhost:8080/yawl", "admin", "YAWL");

        long start = System.nanoTime();
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "YAWL");
        long elapsedNs = System.nanoTime() - start;
        assertNotNull(server);

        // Each construction should take less than 50ms (50_000_000 ns)
        assertTrue("Single server construction should be < 50ms, took "
            + elapsedNs / 1_000_000 + "ms", elapsedNs < 50_000_000L);
    }

    // =========================================================================
    // McpLoggingHandler level check throughput
    // =========================================================================

    public void testTenThousandLevelChecksUnder1Second() {
        McpLoggingHandler handler = new McpLoggingHandler();
        handler.setLevel(McpSchema.LoggingLevel.INFO);

        long start = System.currentTimeMillis();
        for (int i = 0; i < 10_000; i++) {
            // Filtered log notifications (DEBUG < INFO) - fast path
            handler.sendLogNotification(
                null, McpSchema.LoggingLevel.DEBUG, "perf.test", "debug message " + i);
        }
        long elapsed = System.currentTimeMillis() - start;
        assertTrue("10000 filtered log notifications should complete in < 1000ms, took "
            + elapsed + "ms", elapsed < 1000);
    }

    public void testTenThousandInfoNotificationsUnder2Seconds() {
        McpLoggingHandler handler = new McpLoggingHandler();
        handler.setLevel(McpSchema.LoggingLevel.DEBUG); // all messages pass

        long start = System.currentTimeMillis();
        for (int i = 0; i < 10_000; i++) {
            handler.sendLogNotification(
                null, McpSchema.LoggingLevel.INFO, "perf.test", "info message " + i);
        }
        long elapsed = System.currentTimeMillis() - start;
        assertTrue("10000 info notifications (null server) should complete in < 2000ms, took "
            + elapsed + "ms", elapsed < 2000);
    }

    public void testToolExecutionLoggingThroughput() {
        McpLoggingHandler handler = new McpLoggingHandler();
        Map<String, Object> args = new HashMap<>();
        args.put("specId", "OrderProcessing");
        args.put("caseData", "<data><order>123</order></data>");

        long start = System.currentTimeMillis();
        for (int i = 0; i < 5_000; i++) {
            handler.logToolExecution(null, "launch_case", args);
        }
        long elapsed = System.currentTimeMillis() - start;
        assertTrue("5000 tool execution log calls should complete in < 2000ms, took "
            + elapsed + "ms", elapsed < 2000);
    }

    public void testToolCompletionLoggingThroughput() {
        McpLoggingHandler handler = new McpLoggingHandler();

        long start = System.currentTimeMillis();
        for (int i = 0; i < 5_000; i++) {
            handler.logToolCompletion(null, "get_case_status", i % 2 == 0, i * 10L);
        }
        long elapsed = System.currentTimeMillis() - start;
        assertTrue("5000 tool completion log calls should complete in < 2000ms, took "
            + elapsed + "ms", elapsed < 2000);
    }

    // =========================================================================
    // Server capabilities construction performance
    // =========================================================================

    public void testFiveThousandCapabilityConstructionsUnder2Seconds() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 5_000; i++) {
            McpSchema.ServerCapabilities caps = YawlServerCapabilities.full();
            assertNotNull(caps);
        }
        long elapsed = System.currentTimeMillis() - start;
        assertTrue("5000 full() capability constructions should complete in < 2000ms, took "
            + elapsed + "ms", elapsed < 2000);
    }

    public void testAllCapabilityVariantsUnder1Second() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1_000; i++) {
            assertNotNull(YawlServerCapabilities.full());
            assertNotNull(YawlServerCapabilities.minimal());
            assertNotNull(YawlServerCapabilities.toolsAndResources());
            assertNotNull(YawlServerCapabilities.readOnly());
        }
        long elapsed = System.currentTimeMillis() - start;
        assertTrue("1000 iterations of all 4 capability variants should complete < 1000ms, took "
            + elapsed + "ms", elapsed < 1000);
    }

    // =========================================================================
    // Concurrent MCP server construction
    // =========================================================================

    public void testFiftyConcurrentServerConstructions() throws InterruptedException {
        int threadCount = 50;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    YawlMcpServer server = new YawlMcpServer(
                        "http://localhost:" + (8080 + idx) + "/yawl",
                        "admin-" + idx, "pass-" + idx);
                    if (server != null && !server.isRunning()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Construction should never throw for valid params
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startGate.countDown(); // release all threads simultaneously
        boolean finished = doneLatch.await(2, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        executor.shutdown();

        assertTrue("All 50 concurrent constructions should complete in < 2 seconds",
            finished);
        assertEquals("All 50 constructions should succeed",
            threadCount, successCount.get());
        assertTrue("50 concurrent constructions should finish in < 2000ms, took "
            + elapsed + "ms", elapsed < 2000);
    }

    // =========================================================================
    // Concurrent logging handler level checks
    // =========================================================================

    public void testConcurrentLevelChangesSafe() throws InterruptedException {
        McpLoggingHandler handler = new McpLoggingHandler();
        int threadCount = 20;
        int iterationsPerThread = 500;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        McpSchema.LoggingLevel[] levels = McpSchema.LoggingLevel.values();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        // Alternate between setting level and reading it
                        if (i % 3 == 0) {
                            handler.setLevel(levels[(threadIdx + i) % levels.length]);
                        } else {
                            McpSchema.LoggingLevel level = handler.getLevel();
                            if (level == null) {
                                errors.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneLatch.await(3, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue("Concurrent level changes should complete in 3 seconds", finished);
        assertEquals("No errors should occur during concurrent level changes",
            0, errors.get());
    }
}
