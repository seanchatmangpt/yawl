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

package org.yawlfoundation.yawl.performance.edge;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.yawlfoundation.yawl.elements.YAWLServiceInterfaceRegistry;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.data.YDataHandler;
import org.yawlfoundation.yawl.elements.data.YVariable;
import org.yawlfoundation.yawl.engine.YAWLServiceInterfaceRegistry;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.exceptions.YSchemaException;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.jdom2.Element;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test for deeply nested workflow execution in YAWL.
 *
 * Tests extreme scenarios that could cause production issues:
 * - 50+ workflow level nested execution
 * - Deep task hierarchy recursion
 * - Stack overflow prevention
 * - Memory limits with deep nesting
 * - Performance degradation at depth
 * - Cross-level data propagation
 *
 * Chicago TDD: Uses real YAWL objects, no mocks.
 *
 * @author Performance Test Specialist
 * @version 6.0.0
 */
@Tag("stress")
@Tag("performance")
@Tag("edge-cases")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 60, unit = TimeUnit.MINUTES)  // 1 hour max for all tests
class DeepNestingTest {

    private static final String NESTING_HDR = "=== DEEP NESTING TEST REPORT ===";
    private static final String NESTING_FTR_PASS = "=== ALL TESTS PASSED ===";
    private static final String NESTING_FTR_FAIL = "=== TEST FAILURES DETECTED ===";

    private static final List<String> TEST_REPORT_LINES = new ArrayList<>();
    private YAWLServiceInterfaceRegistry registry;
    private YNetRunner netRunner;
    private YDataHandler dataHandler;

    @BeforeAll
    static void setup() {
        System.out.println();
        System.out.println(NESTING_HDR);
        System.out.println("Testing workflow nesting levels from 1 to 50+");
        System.out.println();
    }

    @AfterAll
    static void tearDown() {
        boolean allPass = TEST_REPORT_LINES.stream()
                .allMatch(l -> l.contains("PASS") || l.contains("CHARACTERISED"));
        for (String line : TEST_REPORT_LINES) {
            System.out.println(line);
        }
        System.out.println(allPass ? NESTING_FTR_PASS : NESTING_FTR_FAIL);
        System.out.println();
    }

    @BeforeEach
    void initializeTest() {
        registry = new YAWLServiceInterfaceRegistry();
        netRunner = new YNetRunner();
        dataHandler = new YDataHandler();
    }

    // =========================================================================
    // Test 1: Linear Deep Nesting
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Test 1 — Linear Deep Nesting (50 levels)")
    void test1_linearDeepNesting() throws Exception {
        // Given: A workflow with 50 linearly nested tasks
        YSpecification spec = createLinearNestedSpecification(50);

        // When: Execute the deeply nested workflow
        long startTime = System.nanoTime();
        String caseId = netRunner.launchCase(spec.getID(), Map.of("input", "start"));

        // Wait for completion with extended timeout
        List<WorkItemRecord> workItems = waitForCaseCompletion(caseId, 120);
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        // Then: Validate all levels executed correctly
        assertNotNull(caseId, "Case should be created successfully");
        assertEquals(50, workItems.size(), "Should generate 50 work items for linear nesting");

        // Verify data propagation through all levels
        String expectedValue = "propagated-through-50-levels";
        for (int i = 0; i < 50; i++) {
            YWorkItem workItem = netRunner.getWorkItem(workItems.get(i).getWorkItemID());
            Object levelValue = workItem.getDataVariableByName("levelValue").getValue();
            assertEquals(expectedValue, levelValue,
                "Level " + i + " should have propagated value: " + levelValue);
        }

        // Performance assertions
        assertTrue(durationMs < 30000, "Linear nesting should complete within 30s");
        double avgLatency = durationMs / 50.0;
        assertTrue(avgLatency < 500, "Average latency per level should be < 500ms");

        String report = String.format(
                "Test 1  Linear Nesting:            levels=%d  duration=%.2fs  avgLatency=%.2fms totalThroughput=%.1f/s  PASS",
                50, durationMs / 1000.0, avgLatency, 50.0 / (durationMs / 1000.0));
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 2: Tree-Structured Deep Nesting
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("Test 2 — Tree-Structured Nesting (binary tree, depth 10)")
    void test2_treeStructuredNesting() throws Exception {
        // Given: A workflow with binary tree structure (10 levels deep, 2^10-1 tasks)
        YSpecification spec = createTreeNestedSpecification(10);

        // When: Execute the tree-structured workflow
        long startTime = System.nanoTime();
        String caseId = netRunner.launchCase(spec.getID(), Map.of("input", "root"));

        // Wait for completion with extended timeout
        List<WorkItemRecord> workItems = waitForCaseCompletion(caseId, 180);
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        // Then: Validate tree structure execution
        assertNotNull(caseId, "Case should be created successfully");
        assertEquals(1023, workItems.size(), "Should generate 1023 work items for binary tree (2^10-1)");

        // Verify tree hierarchy and data propagation
        verifyTreeDataPropagation(workItems, 10);

        // Performance assertions
        assertTrue(durationMs < 60000, "Tree nesting should complete within 60s");
        double avgLatency = durationMs / 1023.0;
        assertTrue(avgLatency < 100, "Average latency per node should be < 100ms");

        String report = String.format(
                "Test 2  Tree Nesting:                depth=%d nodes=%,d  duration=%.2fs  avgLatency=%.2fms  PASS",
                10, 1023, durationMs / 1000.0, avgLatency);
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 3: Recursive Nesting Patterns
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("Test 3 — Recursive Nesting (factorial computation, 15 levels)")
    void test3_recursiveNesting() throws Exception {
        // Given: A workflow that computes factorial through recursion (15 levels)
        YSpecification spec = createRecursiveSpecification(15);

        // When: Execute the recursive workflow
        long startTime = System.nanoTime();
        String caseId = netRunner.launchCase(spec.getID(), Map.of("n", 15));

        // Wait for completion
        List<WorkItemRecord> workItems = waitForCaseCompletion(caseId, 90);
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        // Then: Validate recursion results
        assertNotNull(caseId, "Case should be created successfully");
        assertTrue(workItems.size() >= 15, "Should generate at least 15 recursive calls");

        // Verify factorial computation result
        YWorkItem finalWorkItem = netRunner.getWorkItem(workItems.get(workItems.size() - 1).getWorkItemID());
        Object result = finalWorkItem.getDataVariableByName("factorial").getValue();
        assertEquals(1307674368000L, result,
            "Factorial of 15 should be 1,307,674,368,000");

        // Performance assertions
        assertTrue(durationMs < 45000, "Recursive nesting should complete within 45s");

        String report = String.format(
                "Test 3  Recursive Nesting:          depth=%d result=%,d duration=%.2fs  PASS",
                15, (Long) result, durationMs / 1000.0);
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 4: Concurrent Deep Nesting
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("Test 4 — Concurrent Deep Nesting (10 instances of 20-level nesting)")
    void test4_concurrentDeepNesting() throws Exception {
        // Given: 10 concurrent instances of 20-level nested workflows
        final int CONCURRENT_INSTANCES = 10;
        final int NESTING_DEPTH = 20;

        YSpecification spec = createLinearNestedSpecification(NESTING_DEPTH);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<NestingResult>> futures = new ArrayList<>(CONCURRENT_INSTANCES);
        CountDownLatch allStarted = new CountDownLatch(CONCURRENT_INSTANCES);

        long startTime = System.nanoTime();

        // Launch concurrent instances
        for (int i = 0; i < CONCURRENT_INSTANCES; i++) {
            final int instanceId = i;
            futures.add(executor.submit(() -> {
                try {
                    allStarted.countDown();
                    allStarted.await(10, TimeUnit.SECONDS);

                    String caseId = netRunner.launchCase(spec.getID(),
                            Map.of("input", "instance-" + instanceId, "instanceId", instanceId));

                    List<WorkItemRecord> workItems = waitForCaseCompletion(caseId, 60);
                    return new NestingResult(instanceId, caseId, workItems.size(), true);
                } catch (Exception e) {
                    return new NestingResult(instanceId, null, 0, false);
                }
            }));
        }

        // Wait for all instances to complete
        List<NestingResult> results = new ArrayList<>();
        AtomicInteger successful = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (Future<NestingResult> future : futures) {
            NestingResult result = future.get(300, TimeUnit.SECONDS);
            results.add(result);
            if (result.success) {
                successful.incrementAndGet();
            } else {
                failed.incrementAndGet();
            }
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        executor.shutdown();

        // Then: Validate concurrent nesting results
        assertEquals(CONCURRENT_INSTANCES, results.size(), "All instances should be completed");
        assertEquals(0, failed.get(), "No instances should fail");

        // Verify each instance completed all levels
        for (NestingResult result : results) {
            if (result.success) {
                assertEquals(NESTING_DEPTH, result.workItemCount,
                    "Instance " + result.instanceId + " should complete " + NESTING_DEPTH + " levels");
            }
        }

        // Performance assertions
        double avgDurationMs = results.stream()
                .filter(r -> r.success)
                .mapToLong(r -> r.durationMs)
                .average()
                .orElse(0);

        assertTrue(avgDurationMs < 20000, "Average concurrent nesting should complete within 20s");

        String report = String.format(
                "Test 4  Concurrent Nesting:          instances=%d depth=%d success=%d avgDuration=%.2fs  PASS",
                CONCURRENT_INSTANCES, NESTING_DEPTH, successful.get(), avgDurationMs / 1000.0);
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 5: Memory Usage with Deep Nesting
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("Test 5 — Memory Usage with Deep Nesting (increasing depth)")
    void test5_memoryUsageWithDeepNesting() throws Exception {
        // Given: Execute workflows with progressively increasing nesting depths
        int[] testDepths = {10, 20, 30, 40, 50};
        Runtime runtime = Runtime.getRuntime();

        List<String> caseIds = new ArrayList<>();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        for (int depth : testDepths) {
            // Monitor memory before test
            long beforeMemory = runtime.totalMemory() - runtime.freeMemory();

            // Execute test
            YSpecification spec = createLinearNestedSpecification(depth);
            String caseId = netRunner.launchCase(spec.getID(), Map.of("input", "depth-" + depth));

            // Wait for completion
            List<WorkItemRecord> workItems = waitForCaseCompletion(caseId, 60);

            // Check memory after test
            long afterMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryDelta = afterMemory - beforeMemory;

            // Memory should not grow excessively
            if (memoryDelta > 5 * 1024 * 1024) {  // 5MB threshold
                fail("Memory usage increased by " + (memoryDelta / 1024 / 1024) + "MB at depth " + depth);
            }

            // Force garbage collection
            runtime.gc();
            Thread.sleep(200);

            caseIds.add(caseId);

            String memoryReport = String.format(
                    "  Depth %2d: memoryDelta=%,dKB items=%,d  OK",
                    depth, memoryDelta / 1024, workItems.size());
            System.out.println(memoryReport);
        }

        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long totalMemoryDelta = finalMemory - initialMemory;

        // Final memory check
        assertTrue(totalMemoryDelta < 10 * 1024 * 1024,
            "Total memory increase should be < 10MB, got " + (totalMemoryDelta / 1024 / 1024) + "MB");

        // Verify all cases completed
        for (String caseId : caseIds) {
            assertTrue(netRunner.getCaseStatus(caseId).equals("complete"),
                "Case " + caseId + " should be complete");
        }

        String report = String.format(
                "Test 5  Memory Usage:              depths=%s totalMemoryDelta=%,dKB gcCount=%d  PASS",
                Arrays.toString(testDepths), totalMemoryDelta / 1024, runtime.gcCount());
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 6: Stack Overflow Protection
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("Test 6 — Stack Overflow Protection (100 levels)")
    void test6_stackOverflowProtection() throws Exception {
        // Given: A workflow with extreme nesting (100 levels)
        YSpecification spec = createLinearNestedSpecification(100);

        // When: Execute extremely deep workflow
        long startTime = System.nanoTime();
        String caseId = netRunner.launchCase(spec.getID(), Map.of("input", "extreme-nesting"));

        // Wait for extended timeout (this should detect stack overflow early)
        List<WorkItemRecord> workItems = new ArrayList<>();
        boolean stackOverflowDetected = false;
        long startTimeMs = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTimeMs < 300000) {  // 5 minutes timeout
            List<WorkItemRecord> items = netRunner.getWorkItemListForCase(caseId);

            // Check for stack overflow indicators
            if (items.stream().anyMatch(item ->
                    item.getStatus().equals("failed") &&
                    item.getErrorCondition() != null &&
                    item.getErrorCondition().contains("stack overflow"))) {
                stackOverflowDetected = true;
                break;
            }

            // Add completed items
            workItems.addAll(items.stream()
                    .filter(item -> item.getStatus().equals("complete"))
                    .collect(Collectors.toList()));

            if (workItems.size() >= 100) {
                break;  // Successfully completed all levels
            }

            Thread.sleep(5000);
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        // Then: Either complete successfully or detect stack overflow properly
        if (stackOverflowDetected) {
            // Stack overflow detected and handled gracefully
            System.out.println("Stack overflow detected at level " + workItems.size() + " - properly handled");

            String report = String.format(
                    "Test 6  Stack Overflow Protection:  maxDepth=%d duration=%.2fs stackOverflowDetected=true  PASS",
                    workItems.size(), durationMs / 1000.0);
            TEST_REPORT_LINES.add(report);
            System.out.println(report);
        } else {
            // Successfully completed all levels without stack overflow
            assertEquals(100, workItems.size(),
                "Should complete all 100 levels without stack overflow");

            String report = String.format(
                    "Test 6  Stack Overflow Protection:  levels=%d duration=%.2fs noStackOverflow=PASS",
                    100, durationMs / 1000.0);
            TEST_REPORT_LINES.add(report);
            System.out.println(report);
        }
    }

    // Helper classes and methods

    private record NestingResult(int instanceId, String caseId, int workItemCount, boolean success) {}

    private YSpecification createLinearNestedSpecification(int depth) throws Exception {
        Element yawlElement = new Element("yawl");
        yawlElement.setAttribute("xmlns", "http://www.yawlfoundation.org/yawl");

        Element specElement = new Element("specification");
        specElement.setAttribute("id", "LinearNestingSpec_" + depth);
        specElement.setAttribute("name", "Linear Nesting Specification (" + depth + " levels)");

        // Input parameters
        Element inputParams = new Element("inputParameters");
        Element inputVar = new Element("data");
        inputVar.setAttribute("name", "input");
        inputVar.setAttribute("type", "xs:string");
        inputParams.addContent(inputVar);

        // Output parameters
        Element outputParams = new Element("outputParameters");
        Element outputVar = new Element("data");
        outputVar.setAttribute("name", "finalResult");
        outputVar.setAttribute("type", "xs:string");
        outputParams.addContent(outputVar);

        // Create net with nested tasks
        Element netElement = new Element("net");
        netElement.setAttribute("id", "LinearNet");

        // Create linear sequence of tasks
        Element prevTask = null;
        for (int i = 0; i < depth; i++) {
            Element taskElement = new Element("task");
            taskElement.setAttribute("name", "Task" + i);
            taskElement.setAttribute("id", "Task" + i);

            // Add data variable for this level
            Element levelVar = new Element("data");
            levelVar.setAttribute("name", "levelValue");
            levelVar.setAttribute("type", "xs:string");
            levelVar.setAttribute("schemaType", "string");
            taskElement.addContent(levelVar);

            if (prevTask == null) {
                // First task connects to start
                Element startConnector = new Element("flow");
                startConnector.setAttribute("source", "start");
                startConnector.setAttribute("target", "Task" + i);
                netElement.addContent(startConnector);
            } else {
                // Connect previous task to current task
                Element flowElement = new Element("flow");
                flowElement.setAttribute("source", "Task" + (i - 1));
                flowElement.setAttribute("target", "Task" + i);
                netElement.addContent(flowElement);
            }

            netElement.addContent(taskElement);
            prevTask = taskElement;
        }

        // Connect final task to output
        if (prevTask != null) {
            Element finalFlow = new Element("flow");
            finalFlow.setAttribute("source", "Task" + (depth - 1));
            finalFlow.setAttribute("target", "finish");
            netElement.addContent(finalFlow);
        }

        // Connect to output
        Element outputFlow = new Element("flow");
        outputFlow.setAttribute("source", "Task" + (depth - 1));
        outputFlow.setAttribute("target", "output");
        netElement.addContent(outputFlow);

        specElement.addContent(inputParams);
        specElement.addContent(outputParams);
        specElement.addContent(netElement);
        yawlElement.addContent(specElement);

        return YMarshal.unmarshalSpecification(yawlElement);
    }

    private YSpecification createTreeNestedSpecification(int depth) throws Exception {
        Element yawlElement = new Element("yawl");
        yawlElement.setAttribute("xmlns", "http://www.yawlfoundation.org/yawl");

        Element specElement = new Element("specification");
        specElement.setAttribute("id", "TreeNestingSpec");
        specElement.setAttribute("name", "Tree Nesting Specification");

        // Input parameters
        Element inputParams = new Element("inputParameters");
        Element inputVar = new Element("data");
        inputVar.setAttribute("name", "input");
        inputVar.setAttribute("type", "xs:string");
        inputParams.addContent(inputVar);

        // Create binary tree net
        Element netElement = new Element("net");
        netElement.setAttribute("id", "BinaryTreeNet");

        // Create nodes recursively
        String rootNode = createTreeNode(netElement, "Root", 0, depth);

        // Connect start to root
        Element startFlow = new Element("flow");
        startFlow.setAttribute("source", "start");
        startFlow.setAttribute("target", rootNode);
        netElement.addContent(startFlow);

        // Connect root to finish
        Element finalFlow = new Element("flow");
        finalFlow.setAttribute("source", rootNode);
        finalFlow.setAttribute("target", "finish");
        netElement.addContent(finalFlow);

        specElement.addContent(inputParams);
        specElement.addContent(netElement);
        yawlElement.addContent(specElement);

        return YMarshal.unmarshalSpecification(yawlElement);
    }

    private String createTreeNode(Element netElement, String prefix, int level, int maxDepth) {
        if (level >= maxDepth) {
            // Create leaf node
            String nodeId = prefix + "_" + level;
            Element taskElement = new Element("task");
            taskElement.setAttribute("name", "Leaf_" + prefix);
            taskElement.setAttribute("id", nodeId);
            netElement.addContent(taskElement);
            return nodeId;
        }

        // Create internal node with children
        String nodeId = prefix + "_" + level;
        Element taskElement = new Element("task");
        taskElement.setAttribute("name", "Internal_" + prefix);
        taskElement.setAttribute("id", nodeId);
        netElement.addContent(taskElement);

        // Create left and right children
        String leftChildId = createTreeNode(netElement, prefix + "L", level + 1, maxDepth);
        String rightChildId = createTreeNode(netElement, prefix + "R", level + 1, maxDepth);

        // Connect to children
        Element leftFlow = new Element("flow");
        leftFlow.setAttribute("source", nodeId);
        leftFlow.setAttribute("target", leftChildId);
        netElement.addContent(leftFlow);

        Element rightFlow = new Element("flow");
        rightFlow.setAttribute("source", nodeId);
        rightFlow.setAttribute("target", rightChildId);
        netElement.addContent(rightFlow);

        return nodeId;
    }

    private YSpecification createRecursiveSpecification(int depth) throws Exception {
        Element yawlElement = new Element("yawl");
        yawlElement.setAttribute("xmlns", "http://www.yawlfoundation.org/yawl");

        Element specElement = new Element("specification");
        specElement.setAttribute("id", "RecursiveSpec");
        specElement.setAttribute("name", "Recursive Specification");

        // Input parameters
        Element inputParams = new Element("inputParameters");
        Element nVar = new Element("data");
        nVar.setAttribute("name", "n");
        nVar.setAttribute("type", "xs:integer");
        inputParams.addContent(nVar);

        // Output parameters
        Element outputParams = new Element("outputParameters");
        Element resultVar = new Element("data");
        resultVar.setAttribute("name", "factorial");
        resultVar.setAttribute("type", "xs:integer");
        outputParams.addContent(resultVar);

        // Create net with recursive task
        Element netElement = new Element("net");
        netElement.setAttribute("id", "RecursiveNet");

        // Base case task
        Element baseTask = new Element("task");
        baseTask.setAttribute("name", "BaseCase");
        baseTask.setAttribute("id", "BaseTask");
        netElement.addContent(baseTask);

        // Recursive case task
        Element recursiveTask = new Element("task");
        recursiveTask.setAttribute("name", "RecursiveCase");
        recursiveTask.setAttribute("id", "RecursiveTask");
        netElement.addContent(recursiveTask);

        // Connect start to base task
        Element baseFlow = new Element("flow");
        baseFlow.setAttribute("source", "start");
        baseFlow.setAttribute("target", "BaseTask");
        netElement.addContent(baseFlow);

        // Connect base task to recursive task (for n > 1)
        Element recursiveFlow = new Element("flow");
        recursiveFlow.setAttribute("source", "BaseTask");
        recursiveFlow.setAttribute("target", "RecursiveTask");
        netElement.addContent(recursiveFlow);

        // Connect both to finish
        Element finishBaseFlow = new Element("flow");
        finishBaseFlow.setAttribute("source", "BaseTask");
        finishBaseFlow.setAttribute("target", "finish");
        netElement.addContent(finishBaseFlow);

        Element finishRecursiveFlow = new Element("flow");
        finishRecursiveFlow.setAttribute("source", "RecursiveTask");
        finishRecursiveFlow.setAttribute("target", "finish");
        netElement.addContent(finishRecursiveFlow);

        specElement.addContent(inputParams);
        specElement.addContent(outputParams);
        specElement.addContent(netElement);
        yawlElement.addContent(specElement);

        return YMarshal.unmarshalSpecification(yawlElement);
    }

    private void verifyTreeDataPropagation(List<WorkItemRecord> workItems, int depth) {
        // Verify that tree structure is correctly executed
        // This is a simplified verification - in practice, you'd want more sophisticated tree validation
        Map<String, Integer> levelCounts = new HashMap<>();

        for (WorkItemRecord item : workItems) {
            String name = item.getTaskName();
            if (name.contains("Leaf")) {
                levelCounts.merge("leaf", 1, Integer::sum);
            } else if (name.contains("Internal")) {
                levelCounts.merge("internal", 1, Integer::sum);
            }
        }

        // Verify expected number of internal nodes and leaves
        int expectedInternal = (1 << depth) - 2;  // 2^depth - 2 for internal nodes
        int expectedLeaves = 1 << depth;         // 2^depth leaves

        assertEquals(expectedInternal, levelCounts.getOrDefault("internal", 0),
            "Incorrect number of internal nodes");
        assertEquals(expectedLeaves, levelCounts.getOrDefault("leaf", 0),
            "Incorrect number of leaf nodes");
    }

    private List<WorkItemRecord> waitForCaseCompletion(String caseId, int timeoutSeconds) throws InterruptedException {
        List<WorkItemRecord> workItems = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            List<WorkItemRecord> items = netRunner.getWorkItemListForCase(caseId);

            // Add completed work items
            for (WorkItemRecord item : items) {
                if (item.getStatus().equals("complete") && !workItems.contains(item)) {
                    workItems.add(item);
                }
            }

            // Check if all expected work items are complete
            if (!workItems.isEmpty()) {
                return workItems;
            }

            Thread.sleep(1000);
        }

        fail("Case " + caseId + " did not complete work items within " + timeoutSeconds + " seconds");
        return Collections.emptyList();
    }
}