/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.a2a;

import junit.framework.TestCase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Virtual thread concurrency tests for A2A operations.
 *
 * Chicago TDD: Tests the virtual thread implementation of A2A operations
 * to ensure high concurrency with low resource usage.
 *
 * Coverage targets:
 * - Virtual thread creation for each agent
 * - Virtual thread lifecycle management
 * - Concurrency under high load
 * - Memory usage patterns
 * - Thread safety
 * - Performance comparison
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-22
 */
public class VirtualThreadConcurrencyTest extends TestCase {

    private static final int TEST_PORT = 19886;
    private YawlA2AServer server;
    private VirtualThreadAgentManager agentManager;

    public VirtualThreadConcurrencyTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        // Start A2A server
        server = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", TEST_PORT,
            createTestAuthenticationProvider());
        server.start();
        Thread.sleep(200);

        // Initialize virtual thread agent manager
        agentManager = new VirtualThreadAgentManager("http://localhost:" + TEST_PORT);
    }

    @Override
    protected void tearDown() throws Exception {
        if (agentManager != null) {
            agentManager.shutdown();
        }
        if (server != null && server.isRunning()) {
            server.stop();
        }
        server = null;
        agentManager = null;
        super.tearDown();
    }

    // =========================================================================
    // Virtual Thread Creation Tests
    // =========================================================================

    public void testVirtualThreadCreationForAgents() throws Exception {
        // Create multiple virtual threads for agents
        int agentCount = 10;
        for (int i = 0; i < agentCount; i++) {
            VirtualThreadAgent agent = agentManager.createVirtualThreadAgent(
                "agent-" + i, "user-" + i, "key-" + i);
            assertNotNull("Agent should be created", agent);

            // Verify virtual thread is running
            boolean isRunning = agent.isVirtualThreadRunning();
            assertTrue("Virtual thread should be running", isRunning);
        }
    }

    public void testVirtualThreadLifecycle() throws Exception {
        VirtualThreadAgent agent = agentManager.createVirtualThreadAgent(
            "test-agent", "test-user", "test-key");

        // Verify thread is alive
        boolean isAlive = agent.isVirtualThreadAlive();
        assertTrue("Virtual thread should be alive", isAlive);

        // Simulate some work
        String result = agent.executeVirtualThreadWork("simple-workflow", "{}");
        assertNotNull("Virtual thread should complete work", result);

        // Stop the virtual thread
        agent.stopVirtualThread();
        boolean isStopped = agent.isVirtualThreadAlive();
        assertFalse("Virtual thread should be stopped", isStopped);
    }

    // =========================================================================
    // Concurrency Tests
    // =========================================================================

    public void testHighConcurrencyVirtualThreads() throws Exception {
        int agentCount = 100;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        AtomicInteger completedCount = new AtomicInteger(0);

        // Launch 100 virtual threads concurrently
        for (int i = 0; i < agentCount; i++) {
            final int agentId = i;
            executor.submit(() -> {
                try {
                    VirtualThreadAgent agent = agentManager.createVirtualThreadAgent(
                        "agent-" + agentId, "user-" + agentId, "key-" + agentId);

                    // Execute workflow
                    String result = agent.executeVirtualThreadWork(
                        "simple-workflow", "{\"id\": " + agentId + "}");

                    if (result != null) {
                        completedCount.incrementAndGet();
                    }

                    agent.stopVirtualThread();
                } catch (Exception e) {
                    // Log error but continue
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
        assertTrue("All threads should complete", terminated);
        assertEquals("All agents should complete", agentCount, completedCount.get());
    }

    public void testVirtualThreadPerformance() throws Exception {
        int iterations = 1000;
        long startTime = System.currentTimeMillis();

        // Use virtual threads for high throughput
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < iterations; i++) {
            final int iteration = i;
            executor.submit(() -> {
                try {
                    VirtualThreadAgent agent = agentManager.createVirtualThreadAgent(
                        "perf-agent-" + iteration, "user-" + iteration, "key-" + iteration);

                    long nanoStart = System.nanoTime();
                    String result = agent.executeVirtualThreadWork(
                        "quick-workflow", "{\"iteration\": " + iteration + "}");
                    long duration = System.nanoTime() - nanoStart;

                    if (result != null) {
                        successCount.incrementAndGet();
                    }

                    agent.stopVirtualThread();
                } catch (Exception e) {
                    // Ignore for performance test
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Performance metrics
        double throughput = iterations / (duration / 1000.0);
        System.out.println("Virtual thread throughput: " + throughput + " ops/sec");

        assertTrue("Should handle 1000 operations in 60 seconds", successCount.get() > 900);
        assertTrue("Throughput should be reasonable", throughput > 10); // 10+ ops/sec
    }

    // =========================================================================
    // Memory Usage Tests
    // =========================================================================

    public void testVirtualThreadMemoryUsage() throws Exception {
        Runtime runtime = Runtime.getRuntime();

        // Measure memory before
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // Create many virtual threads
        int threadCount = 1000;
        VirtualThreadAgent[] agents = new VirtualThreadAgent[threadCount];

        for (int i = 0; i < threadCount; i++) {
            agents[i] = agentManager.createVirtualThreadAgent(
                "mem-agent-" + i, "user-" + i, "key-" + i);

            // Execute simple work
            agents[i].executeVirtualThreadWork("minimal-workflow", "{}");
        }

        // Measure memory after
        runtime.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        // Calculate memory per thread
        long memoryPerThread = memoryUsed / threadCount;

        System.out.println("Memory per virtual thread: " + memoryPerThread + " bytes");

        // Virtual threads should use minimal memory (a few KB each)
        assertTrue("Virtual threads should use minimal memory", memoryPerThread < 10000); // < 10KB per thread

        // Cleanup
        for (VirtualThreadAgent agent : agents) {
            agent.stopVirtualThread();
        }
    }

    // =========================================================================
    // Thread Safety Tests
    // =========================================================================

    public void testVirtualThreadSafety() throws Exception {
        // Test shared resource access with virtual threads
        AtomicInteger sharedCounter = new AtomicInteger(0);
        ReentrantLock lock = new ReentrantLock();

        int threadCount = 100;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    VirtualThreadAgent agent = agentManager.createVirtualThreadAgent(
                        "safe-agent-" + Thread.currentThread().threadId(),
                        "user", "key");

                    // Use lock for thread-safe access
                    lock.lock();
                    try {
                        int currentValue = sharedCounter.incrementAndGet();
                        // Verify counter is increasing correctly
                        assertTrue("Counter should be positive", currentValue > 0);
                    } finally {
                        lock.unlock();
                    }

                    agent.stopVirtualThread();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        assertEquals("All increments should be counted", threadCount, sharedCounter.get());
    }

    // =========================================================================
    // Error Handling in Virtual Threads
    // =========================================================================

    public void testVirtualThreadErrorHandling() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // Submit tasks that will fail
        for (int i = 0; i < 50; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    VirtualThreadAgent agent = agentManager.createVirtualThreadAgent(
                        "error-agent-" + taskId, "user", "key");

                    // Execute operation that might fail
                    if (taskId % 5 == 0) {
                        // Simulate failure
                        throw new RuntimeException("Simulated error for task " + taskId);
                    }

                    String result = agent.executeVirtualThreadWork("simple-workflow", "{}");
                    if (result != null) {
                        successCount.incrementAndGet();
                    }

                    agent.stopVirtualThread();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    // Virtual thread should handle error gracefully
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Some tasks should fail, some should succeed
        assertTrue("Some tasks should succeed", successCount.get() > 0);
        assertTrue("Some tasks should fail", errorCount.get() > 0);
        assertEquals("Total tasks accounted for", 50, successCount.get() + errorCount.get());
    }

    // =========================================================================
    // Virtual Thread Resource Management
    // =========================================================================

    public void testVirtualThreadResourceLimit() throws Exception {
        // Test behavior when hitting virtual thread limits
        int maxThreads = 10000; // Large number to test limits
        int successfulCreates = 0;

        try {
            for (int i = 0; i < maxThreads; i++) {
                VirtualThreadAgent agent = agentManager.createVirtualThreadAgent(
                    "resource-agent-" + i, "user", "key");

                // Simple operation
                String result = agent.executeVirtualThreadWork("minimal-workflow", "{}");

                if (result != null) {
                    successfulCreates++;
                }

                agent.stopVirtualThread();

                // Check if we hit any resource limits
                if (i % 1000 == 0) {
                    System.gc(); // Clean up periodically
                    Thread.sleep(10); // Small pause to avoid overwhelming
                }
            }
        } catch (OutOfMemoryError e) {
            // Expected at some point, but should handle gracefully
            System.out.println("Caught OOM at thread " + successfulCreates);
        }

        // Should create many threads before hitting limits
        assertTrue("Should create many virtual threads", successfulCreates > 100);
    }

    // =========================================================================
    // Virtual Thread vs Platform Thread Comparison
    // =========================================================================

    public void testVirtualThreadVsPlatformThreadPerformance() throws Exception {
        int iterations = 500;

        // Test with virtual threads
        long virtualThreadTime = testPerformanceWithThreads(
            Executors.newVirtualThreadPerTaskExecutor(), iterations);

        // Test with platform threads
        long platformThreadTime = testPerformanceWithThreads(
            Executors.newFixedThreadPool(100), iterations);

        System.out.println("Virtual thread time: " + virtualThreadTime + "ms");
        System.out.println("Platform thread time: " + platformThreadTime + "ms");

        // Virtual threads should be competitive or better
        double ratio = (double) platformThreadTime / virtualThreadTime;
        System.out.println("Platform/Virtual ratio: " + ratio);

        // Virtual threads should handle the load effectively
        assertTrue("Virtual threads should handle the load", virtualThreadTime < platformThreadTime * 2);
    }

    private long testPerformanceWithThreads(ExecutorService executor, int iterations) throws Exception {
        AtomicInteger completed = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            final int iteration = i;
            executor.submit(() -> {
                try {
                    VirtualThreadAgent agent = agentManager.createVirtualThreadAgent(
                        "perf-agent-" + iteration, "user", "key");

                    agent.executeVirtualThreadWork("quick-workflow", "{}");
                    completed.incrementAndGet();

                    agent.stopVirtualThread();
                } catch (Exception e) {
                    // Ignore for performance test
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        return System.currentTimeMillis() - startTime;
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    private YawlAuthenticationProvider createTestAuthenticationProvider() {
        return new YawlAuthenticationProvider() {
            @Override
            public boolean authenticate(String username, String password, String apiKey) {
                return username != null && password != null;
            }

            @Override
            public String generateToken(String username) {
                return "virtual-thread-token-" + username;
            }
        };
    }
}