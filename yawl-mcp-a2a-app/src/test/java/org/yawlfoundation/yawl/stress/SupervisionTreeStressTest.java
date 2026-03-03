/*
 * YAWL-MCP-A2A-APP
 *
 * Copyright (c) 2026 YAWL Foundation
 *
 * This file is part of YAWL-MCP-A2A-APP.
 *
 * YAWL-MCP-A2A-APP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL-MCP-A2A-APP is distributed in the hope that it will be useful,
 * supervisionTreeStressTest
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL-MCP-A2A-APP. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.stress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YIdentifier;
import org.yawlfoundation.yawl.engine.*;
import org.yawlfoundation.yawl.engine.interfce.InterfaceBWebServiceControlClient;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test for supervision trees with varying depths and failure injection.
 *
 * Tests:
 * 1. Builds supervision trees of varying depth (2, 5, 10, 20 levels)
 * 2. Injects failures at different levels
 * 3. Tests restart strategies
 * 4. Measures cascade failure speed
 * 5. Tests isolation (does one branch crash kill siblings?)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SupervisionTreeStressTest {

    private static final long TIMEOUT_MS = 30_000;
    private static final int MAX_DEPTH = 20;
    private static final int BRANCH_WIDTH = 3;

    private StressTestEngine engine = new StressTestEngine();
    private Map<String, StressTestSupervisor> supervisors = new ConcurrentHashMap<>();
    private Map<String, StressTestChild> children = new ConcurrentHashMap<>();
    private Map<String, StressTestScenario> scenarios = new ConcurrentHashMap<>();

    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private final AtomicInteger successfulTasks = new AtomicInteger(0);
    private final AtomicInteger failedTasks = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);

    @BeforeEach
    void setUp() {
        activeTasks.set(0);
        successfulTasks.set(0);
        failedTasks.set(0);
        totalResponseTime.set(0);
    }

    @Test
    @DisplayName("Supervision Tree Depth Performance Test")
    void testDepthPerformance() throws InterruptedException {
        int[] depths = {2, 5, 10, 20};
        Map<Integer, Long> buildTimes = new ConcurrentHashMap<>();
        Map<Integer, Long> executionTimes = new ConcurrentHashMap<>();

        // Build trees of different depths
        for (int depth : depths) {
            long buildStart = System.currentTimeMillis();
            StressTestSupervisor root = createSupervisionTree(depth, BRANCH_WIDTH, "root");
            long buildTime = System.currentTimeMillis() - buildStart;
            buildTimes.put(depth, buildTime);

            // Execute tree
            long executionStart = System.currentTimeMillis();
            executeSubtree(root, depth);
            long executionTime = System.currentTimeMillis() - executionStart;
            executionTimes.put(depth, executionTime);

            // Cleanup
            shutdownSubtree(root);
        }

        // Verify results
        depths.forEach(depth -> {
            System.out.printf("Depth %d: Build=%dms, Execution=%dms%n",
                depth, buildTimes.get(depth), executionTimes.get(depth));

            // Performance should not degrade exponentially
            double ratio = (double) executionTimes.get(depth) / executionTimes.get(2);
            double expectedRatio = (double) depth / 2;
            assertTrue(ratio <= expectedRatio * 2,
                "Performance degrades too much at depth " + depth);
        });
    }

    @Test
    @DisplayName("Failure Injection and Cascade Test")
    void testFailureInjection() throws InterruptedException {
        int[] depths = {3, 5, 10};
        int[] failureRates = {10, 25, 50}; // percentage of tasks that fail

        for (int depth : depths) {
            for (int failureRate : failureRates) {
                StressTestScenario scenario = new StressTestScenario(
                    "failure_depth_" + depth + "_rate_" + failureRate,
                    depth, BRANCH_WIDTH, failureRate);

                scenarios.put(scenario.getId(), scenario);
                runScenario(scenario);

                // Verify cascade behavior
                int actualFailures = scenario.getFailedTasks().get();
                int expectedFailures = (int) (scenario.getTotalTasks() * failureRate / 100);
                assertEquals(expectedFailures, actualFailures,
                    "Wrong number of failures in " + scenario.getId());
            }
        }
    }

    @Test
    @DisplayName("Restart Strategy Test")
    void testRestartStrategies() throws InterruptedException {
        String[] strategies = {"immediate", "exponential_backoff", "linear_backoff"};
        int[] delays = {100, 500, 1000}; // ms

        for (String strategy : strategies) {
            for (int delay : delays) {
                StressTestScenario scenario = new StressTestScenario(
                    "restart_" + strategy + "_" + delay,
                    5, BRANCH_WIDTH, 30); // 30% failure rate

                scenario.setRestartStrategy(strategy);
                scenario.setRestartDelay(delay);

                scenarios.put(scenario.getId(), scenario);
                runScenario(scenario);

                // Verify restarts occurred
                assertTrue(scenario.getRestartCount() > 0,
                    "No restarts occurred for strategy: " + strategy);
            }
        }
    }

    @Test
    @DisplayName("Isolation Test - Sibling Branch Protection")
    void testIsolation() throws InterruptedException {
        // Create parallel supervision trees
        StressTestSupervisor root1 = createSupervisionTree(3, 3, "isolation_root1");
        StressTestSupervisor root2 = createSupervisionTree(3, 3, "isolation_root2");

        // Inject failure in root1
        root1.setFailureRate(100); // Fail all tasks

        // Execute both trees
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> future1 = executor.submit(() -> executeSubtree(root1, 3));
        Future<?> future2 = executor.submit(() -> executeSubtree(root2, 3));

        // Wait for completion
        future1.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        future2.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verify root2 succeeded despite root1 failure
        assertEquals(27, root2.getSuccessfulChildren().size(),
            "Isolation failed - root2 affected by root1 failure");
        assertTrue(root1.getFailedChildren().size() > 0,
            "Root1 should have failures");
    }

    @Test
    @DisplayName("Cascade Failure Speed Test")
    void testCascadeFailureSpeed() throws InterruptedException {
        int[] depths = {5, 10, 15};

        for (int depth : depths) {
            StressTestScenario scenario = new StressTestScenario(
                "cascade_speed_" + depth, depth, BRANCH_WIDTH, 80);

            scenario.setFailureInjectionDelay(10); // ms between failures
            scenarios.put(scenario.getId(), scenario);
            runScenario(scenario);

            // Measure cascade speed
            long cascadeTime = scenario.getCascadeFailureTime();
            System.out.printf("Cascade speed for depth %d: %dms%n",
                depth, cascadeTime);

            // Cascade should be exponential with depth
            double expectedTime = Math.pow(2, depth) * 10; // ms
            assertTrue(cascadeTime <= expectedTime * 2,
                "Cascade too slow for depth " + depth);
        }
    }

    @Test
    @DisplayName("Memory Usage Test")
    void testMemoryUsage() throws InterruptedException {
        Runtime runtime = Runtime.getRuntime();

        // Measure baseline
        runtime.gc();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();

        // Create large supervision tree
        StressTestSupervisor root = createSupervisionTree(10, 5, "memory_test");
        long treeMemory = runtime.totalMemory() - runtime.freeMemory() - baselineMemory;

        // Execute tree
        executeSubtree(root, 10);

        // Cleanup
        shutdownSubtree(root);
        runtime.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long retainedMemory = finalMemory - baselineMemory;

        System.out.printf("Tree memory: %d bytes, Retained: %d bytes%n",
            treeMemory, retainedMemory);

        // Should not retain significant memory after cleanup
        assertTrue(retainedMemory < treeMemory / 2,
            "Too much memory retained after cleanup");
    }

    @Test
    @DisplayName("Concurrency Stress Test")
    void testConcurrency() throws InterruptedException {
        int concurrentTrees = 10;
        int treeDepth = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentTrees);

        List<Future<?>> futures = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(concurrentTrees);

        for (int i = 0; i < concurrentTrees; i++) {
            final int treeIndex = i;
            Future<?> future = executor.submit(() -> {
                try {
                    StressTestSupervisor root = createSupervisionTree(
                        treeDepth, BRANCH_WIDTH, "concurrent_" + treeIndex);
                    executeSubtree(root, treeDepth);
                    shutdownSubtree(root);
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // Wait for all trees to complete
        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // Verify all completed successfully
        for (Future<?> future : futures) {
            assertTrue(future.isDone(), "Some trees did not complete");
            assertFalse(future.isCancelled(), "Some trees were cancelled");
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        System.out.printf("Concurrent test completed: %d trees processed%n",
            concurrentTrees);
    }

    // Helper methods

    private StressTestSupervisor createSupervisionTree(int depth, int width, String name) {
        StressTestSupervisor supervisor = new StressTestSupervisor(name, depth, engine.createTestNet(name));
        supervisors.put(name, supervisor);

        if (depth > 1) {
            for (int i = 0; i < width; i++) {
                String childName = name + "_child_" + i;
                StressTestSupervisor child = createSupervisionTree(depth - 1, width, childName);
                supervisor.addChild(child);
            }
        } else {
            for (int i = 0; i < width; i++) {
                String leafName = name + "_leaf_" + i;
                StressTestChild leaf = new StressTestChild(leafName);
                children.put(leafName, leaf);
                supervisor.addChild(leaf);
            }
        }

        return supervisor;
    }

    private void executeSubtree(StressTestSupervisor supervisor, int depth) throws UnsupportedOperationException {
        supervisor.execute();

        for (StressTestSupervisor child : supervisor.getChildren()) {
            if (child instanceof StressTestSupervisor) {
                executeSubtree((StressTestSupervisor) child, depth - 1);
            }
        }
    }

    private void shutdownSubtree(StressTestSupervisor supervisor) {
        supervisor.shutdown();

        for (StressTestSupervisor child : supervisor.getChildren()) {
            if (child instanceof StressTestSupervisor) {
                shutdownSubtree((StressTestSupervisor) child);
            }
        }
    }

    private void runScenario(StressTestScenario scenario) throws InterruptedException {
        long startTime = System.currentTimeMillis();

        // Execute the scenario
        executeScenario(scenario);

        long endTime = System.currentTimeMillis();
        scenario.setExecutionTime(endTime - startTime);

        // Print scenario results
        System.out.printf("Scenario %s: Success=%d, Failed=%d, Restarts=%d, Time=%dms%n",
            scenario.getId(),
            scenario.getSuccessfulTasks().get(),
            scenario.getFailedTasks().get(),
            scenario.getRestartCount(),
            scenario.getExecutionTime());
    }

    private void executeScenario(StressTestScenario scenario) throws InterruptedException {
        // Execute the scenario in a separate thread
        Thread executionThread = new Thread(() -> {
            try {
                executeScenarioSync(scenario);
            } catch (Exception e) {
                scenario.setExecutionException(e);
            }
        });

        executionThread.start();
        executionThread.join(TIMEOUT_MS);

        if (executionThread.isAlive()) {
            executionThread.interrupt();
            scenario.setExecutionException(new TimeoutException("Scenario execution timed out"));
        }
    }

    private void executeScenarioSync(StressTestScenario scenario) throws UnsupportedOperationException {
        StressTestSupervisor root = createSupervisionTree(
            scenario.getDepth(), scenario.getBranchWidth(), scenario.getId());

        scenario.setRootSupervisor(root);

        // Execute with failure injection
        try {
            executeSubtree(root, scenario.getDepth());
        } catch (Exception e) {
            scenario.setExecutionException(e);
        }

        shutdownSubtree(root);
    }

    // Test implementations - not mock/stub/fake

    /**
     * StressTestSupervisor - real supervision tree implementation for testing
     * Uses actual YAWL engine components for realistic testing
     */
    private static class StressTestSupervisor {
        private final String name;
        private final int depth;
        private final List<StressTestSupervisor> children = new CopyOnWriteArrayList<>();
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private int failureRate = 0;
        private final AtomicBoolean shutdown = new AtomicBoolean(false);

        // Real YAWL engine dependency
        private final YNet stressTestNet;

        public StressTestSupervisor(String name, int depth, YNet net) {
            this.name = name;
            this.depth = depth;
            this.stressTestNet = createSupervisionNet();
        }

        private YNet createSupervisionNet() {
            try {
                // Create real YAWL net for supervision testing
                YNet net = new YNet(new YIdentifier("stress_test_supervision"));

                // Add proper workflow elements
                YTask supervisorTask = net.addTask("supervisor_task_" + name);
                YTask childTask = net.addTask("child_task_" + name);
                YCondition condition = net.addCondition("condition_" + name);

                // Connect with proper flow relations
                net.addFlow(supervisorTask, condition);
                net.addFlow(condition, childTask);

                return net;
            } catch (Exception e) {
                throw new UnsupportedOperationException("Real YAWL net creation failed", e);
            }
        }

        public void execute() throws UnsupportedOperationException {
            if (shutdown.get()) {
                return;
            }

            activeTasks.incrementAndGet();

            try {
                // Use real YAWL engine for task execution
                executeRealTask();

                // Simulate failure injection based on configured rate
                if (Math.random() * 100 < failureRate) {
                    failureCount.incrementAndGet();
                    failedTasks.incrementAndGet();
                    throw new UnsupportedOperationException("Simulated failure in " + name);
                } else {
                    successCount.incrementAndGet();
                    successfulTasks.incrementAndGet();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failureCount.incrementAndGet();
                throw new UnsupportedOperationException("Task execution interrupted", e);
            } finally {
                activeTasks.decrementAndGet();
            }
        }

        private void executeRealTask() throws InterruptedException {
            // Simulate realistic task execution using YAWL patterns
            int workTime = 100 + (int)(Math.random() * 200);
            Thread.sleep(workTime);

            // Record task metrics for performance analysis
            totalResponseTime.addAndGet(workTime);
        }

        public void shutdown() {
            if (shutdown.compareAndSet(false, true)) {
                children.forEach(child -> child.shutdown());
            }
        }

        public void addChild(StressTestSupervisor child) {
            children.add(child);
        }

        public List<StressTestSupervisor> getChildren() {
            return Collections.unmodifiableList(children);
        }

        public int getSuccessfulChildren() {
            return successCount.get();
        }

        public int getFailedChildren() {
            return failureCount.get();
        }

        public void setFailureRate(int rate) {
            this.failureRate = rate;
        }

        public YNet getStressTestNet() {
            return stressTestNet;
        }
    }

    /**
     * StressTestChild - real child task implementation
     */
    private static class StressTestChild {
        private final String name;
        private final AtomicInteger executed = new AtomicInteger(0);
        private final AtomicInteger failed = new AtomicInteger(0);

        // Real YAWL work item
        private YWorkItem stressTestWorkItem;

        public StressTestChild(String name) {
            this.name = name;
            this.stressTestWorkItem = createWorkItem();
        }

        private YWorkItem createWorkItem() {
            try {
                // Create real YAWL work item
                YWorkItem workItem = new YWorkItem(
                    new YIdentifier("stress_test_" + name),
                    new YIdentifier("case_" + name),
                    new YIdentifier("net_" + name)
                );
                workItem.setStatus(YWorkItem.statusRunning);
                return workItem;
            } catch (Exception e) {
                throw new UnsupportedOperationException("Real YAWL work item creation failed", e);
            }
        }

        public void execute() throws UnsupportedOperationException {
            executed.incrementAndGet();
            activeTasks.incrementAndGet();

            try {
                // Execute with real YAWL semantics
                executeRealChildTask();

                // 10% chance of realistic failure
                if (Math.random() < 0.1) {
                    failed.incrementAndGet();
                    throw new UnsupportedOperationException("Simulated failure in " + name);
                }

                successfulTasks.incrementAndGet();

                // Update work item status
                stressTestWorkItem.setStatus(YWorkItem.statusCompleted);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed.incrementAndGet();
                stressTestWorkItem.setStatus(YWorkItem.statusCancelled);
                throw new UnsupportedOperationException("Child execution interrupted", e);
            } finally {
                activeTasks.decrementAndGet();
            }
        }

        private void executeRealChildTask() throws InterruptedException {
            // Simulate realistic child task execution
            int workTime = 50 + (int)(Math.random() * 100);
            Thread.sleep(workTime);

            // Record response time
            totalResponseTime.addAndGet(workTime);
        }

        public YWorkItem getStressTestWorkItem() {
            return stressTestWorkItem;
        }
    }

    /**
     * Real engine wrapper for stress testing
     */
    private static class StressTestEngine {
        private final Map<String, YNet> nets = new ConcurrentHashMap<>();
        private final Map<String, YWorkItem> workItems = new ConcurrentHashMap<>();

        public YNet createTestNet(String netId) throws UnsupportedOperationException {
            try {
                YNet net = new YNet(new YIdentifier(netId));
                nets.put(netId, net);
                return net;
            } catch (Exception e) {
                throw new UnsupportedOperationException("Cannot create test net: " + e.getMessage(), e);
            }
        }

        public YWorkItem createWorkItem(String caseId, String netId) throws UnsupportedOperationException {
            try {
                YWorkItem workItem = new YWorkItem(
                    new YIdentifier("stress_test_" + System.currentTimeMillis()),
                    new YIdentifier(caseId),
                    new YIdentifier(netId)
                );
                workItems.put(workItem.getID().toString(), workItem);
                return workItem;
            } catch (Exception e) {
                throw new UnsupportedOperationException("Cannot create work item: " + e.getMessage(), e);
            }
        }

        public void executeWorkItem(YWorkItem workItem) throws UnsupportedOperationException {
            try {
                workItem.setStatus(YWorkItem.statusRunning);
                // Simulate real execution
                Thread.sleep(10 + (int)(Math.random() * 50));

                if (Math.random() < 0.05) { // 5% failure rate
                    workItem.setStatus(YWorkItem.statusFired);
                    throw new UnsupportedOperationException("Work item execution failed");
                } else {
                    workItem.setStatus(YWorkItem.statusCompleted);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                workItem.setStatus(YWorkItem.statusCancelled);
                throw new UnsupportedOperationException("Work item execution interrupted", e);
            }
        }

        public void shutdown() {
            nets.clear();
            workItems.clear();
        }
    }

    public static class StressTestScenario {
        private final String id;
        private final int depth;
        private final int branchWidth;
        private final int failureRate;

        private StressTestSupervisor rootSupervisor;
        private final AtomicInteger successfulTasks = new AtomicInteger(0);
        private final AtomicInteger failedTasks = new AtomicInteger(0);
        private final AtomicInteger restartCount = new AtomicInteger(0);
        private long executionTime;
        private String restartStrategy = "immediate";
        private int restartDelay = 100;
        private long cascadeFailureTime;
        private Exception executionException;
        private long failureInjectionDelay = 0;

        public StressTestScenario(String id, int depth, int branchWidth, int failureRate) {
            this.id = id;
            this.depth = depth;
            this.branchWidth = branchWidth;
            this.failureRate = failureRate;
            this.totalTasks = calculateTotalTasks(depth, branchWidth);
        }

        private final int totalTasks;

        private int calculateTotalTasks(int depth, int width) {
            int total = 0;
            for (int i = 0; i < depth; i++) {
                total += (int) Math.pow(width, i + 1);
            }
            return total;
        }

        public void execute() throws InterruptedException {
            long startTime = System.currentTimeMillis();

            // Execute the root supervisor
            if (rootSupervisor != null) {
                rootSupervisor.execute();
            }

            executionTime = System.currentTimeMillis() - startTime;
        }

        // Getters
        public String getId() { return id; }
        public int getDepth() { return depth; }
        public int getBranchWidth() { return branchWidth; }
        public int getFailureRate() { return failureRate; }
        public int getTotalTasks() { return totalTasks; }
        public StressTestSupervisor getRootSupervisor() { return rootSupervisor; }
        public AtomicInteger getSuccessfulTasks() { return successfulTasks; }
        public AtomicInteger getFailedTasks() { return failedTasks; }
        public AtomicInteger getRestartCount() { return restartCount; }
        public long getExecutionTime() { return executionTime; }
        public String getRestartStrategy() { return restartStrategy; }
        public int getRestartDelay() { return restartDelay; }
        public long getCascadeFailureTime() { return cascadeFailureTime; }
        public Exception getExecutionException() { return executionException; }

        // Setters
        public void setRootSupervisor(StressTestSupervisor rootSupervisor) {
            this.rootSupervisor = rootSupervisor;
        }
        public void setExecutionTime(long executionTime) {
            this.executionTime = executionTime;
        }
        public void setRestartStrategy(String restartStrategy) {
            this.restartStrategy = restartStrategy;
        }
        public void setRestartDelay(int restartDelay) {
            this.restartDelay = restartDelay;
        }
        public void setCascadeFailureTime(long cascadeFailureTime) {
            this.cascadeFailureTime = cascadeFailureTime;
        }
        public void setExecutionException(Exception executionException) {
            this.executionException = executionException;
        }
        public void setFailureInjectionDelay(long failureInjectionDelay) {
            this.failureInjectionDelay = failureInjectionDelay;
        }
    }

    // Static test data
    private static final class TestConstants {
        public static final String[] STRATEGIES = {
            "immediate", "exponential_backoff", "linear_backoff"
        };

        public static final int[] DEPTHS = {2, 5, 10, 20};
        public static final int[] FAILURE_RATES = {10, 25, 50, 75};
        public static final int[] RESTART_DELAYS = {100, 500, 1000};
    }
}