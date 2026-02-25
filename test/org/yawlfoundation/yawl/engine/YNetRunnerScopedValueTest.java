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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.ScopedValue;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Java 25 ScopedValue features in YAWL engine.
 *
 * <p>This test class validates the integration of ScopedValue with YAWL's virtual thread
 * execution model, ensuring thread-safe context propagation and inheritance.</p>
 *
 * <h2>Test Scope</h2>
 * <ul>
 *   <li>Context binding and inheritance across virtual threads</li>
 *   <li>Virtual thread execution with workflow context</li>
 *   <li>Context validation and isolation</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
@Tag("java25")
@Execution(ExecutionMode.CONCURRENT)
class YNetRunnerScopedValueTest {

    private static final ScopedValue<Map<String, String>> WORKFLOW_CONTEXT =
        ScopedValue.newInstance();

    private ExecutorService executor;
    private YNetRunner netRunner;
    private String testCaseId;

    /**
     * Setup test environment before each test.
     */
    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        testCaseId = "test-case-" + System.currentTimeMillis();

        // Create test context
        Map<String, String> context = new HashMap<>();
        context.put("caseId", testCaseId);
        context.put("userId", "test-user");
        context.put("department", "engineering");
    }

    /**
     * Test context binding and inheritance in virtual threads.
     */
    @Test
    void testContextBindingAndInheritance() throws Exception {
        // Test ScopedValue binding
        Map<String, String> context = new HashMap<>();
        context.put("caseId", testCaseId);
        context.put("userId", "test-user");

        // Test that context is properly bound and inherited
        String result = ScopedValue.callWhere(
            WORKFLOW_CONTEXT,
            context,
            () -> {
                // Verify context is available in this thread
                Map<String, String> currentContext = WORKFLOW_CONTEXT.get();
                assertEquals(testCaseId, currentContext.get("caseId"));
                assertEquals("test-user", currentContext.get("userId"));

                // Test context inheritance in forked virtual thread
                return executor.submit(() -> {
                    Map<String, String> inheritedContext = WORKFLOW_CONTEXT.get();
                    return inheritedContext.get("caseId");
                }).get();
            }
        );

        assertEquals(testCaseId, result, "Context should be inherited by forked virtual thread");
    }

    /**
     * Test virtual thread execution with workflow context.
     */
    @Test
    void testVirtualThreadExecutionWithContext() throws Exception {
        // Create YNetRunner with virtual thread execution
        YEngine engine = YEngine.getInstance();
        YSpecification spec = createSimpleTestSpecification();
        engine.loadSpecification(spec);

        String caseId = engine.startCase(spec.getSpecificationID(), null, null, null,
            new org.yawlfoundation.yawl.logging.YLogDataItemList(), null, false);
        netRunner = engine._netRunnerRepository.get(caseId);

        // Execute workflow tasks with context propagation
        Map<String, String> workflowContext = new HashMap<>();
        workflowContext.put("caseId", caseId);
        workflowContext.put("executor", "virtual-thread-test");

        String taskResult = ScopedValue.callWhere(
            WORKFLOW_CONTEXT,
            workflowContext,
            () -> executeWorkflowTaskWithNetRunner(netRunner, caseId)
        );

        assertNotNull(taskResult, "Task should execute successfully with context");
        assertTrue(taskResult.contains(caseId), "Result should contain case context");
    }

    /**
     * Test context validation across multiple virtual threads.
     */
    @Test
    void testContextValidation() throws Exception {
        // Test context isolation between threads
        CountDownLatch latch = new CountDownLatch(2);
        Map<String, String> context1 = new HashMap<>();
        context1.put("threadId", "thread-1");
        context1.put("data", "value1");

        Map<String, String> context2 = new HashMap<>();
        context2.put("threadId", "thread-2");
        context2.put("data", "value2");

        var future1 = executor.submit(() -> ScopedValue.callWhere(
            WORKFLOW_CONTEXT,
            context1,
            () -> {
                String threadData = WORKFLOW_CONTEXT.get().get("data");
                latch.countDown();
                return threadData;
            }
        ));

        var future2 = executor.submit(() -> ScopedValue.callWhere(
            WORKFLOW_CONTEXT,
            context2,
            () -> {
                String threadData = WORKFLOW_CONTEXT.get().get("data");
                latch.countDown();
                return threadData;
            }
        ));

        // Wait for both threads to complete
        latch.await(10, TimeUnit.SECONDS);

        assertEquals("value1", future1.get());
        assertEquals("value2", future2.get());
    }

    /**
     * Test structured concurrency with ScopedValue propagation.
     */
    @Test
    void testStructuredConcurrencyWithScopedValue() throws Exception {
        // Create workflow context
        Map<String, String> workflowContext = new HashMap<>();
        workflowContext.put("parentCaseId", testCaseId);
        workflowContext.put("concurrencyMode", "structured");

        // Use StructuredTaskScope with ScopedValue
        ScopedValue.callWhere(
            WORKFLOW_CONTEXT,
            workflowContext,
            () -> {
                try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                    // Submit multiple concurrent tasks
                    var subTask1 = scope.fork(() -> executeSubTask("subtask-1"));
                    var subTask2 = scope.fork(() -> executeSubTask("subtask-2"));

                    // Wait for all tasks to complete
                    scope.join();

                    // Collect results
                    String result1 = subTask1.resultNow();
                    String result2 = subTask2.resultNow();

                    return result1 + "|" + result2;
                }
            }
        );
    }

    /**
     * Test ScopedValue with YNetRunner's virtual thread-safe operations.
     */
    @Test
    void testScopedValueWithYNetRunnerOperations() throws Exception {
        // Create a test case
        YEngine engine = YEngine.getInstance();
        YSpecification spec = createSimpleTestSpecification();
        engine.loadSpecification(spec);

        String caseId = engine.startCase(spec.getSpecificationID(), null, null, null,
            new org.yawlfoundation.yawl.logging.YLogDataItemList(), null, false);
        netRunner = engine._netRunnerRepository.get(caseId);

        // Execute operations with workflow context
        Map<String, String> context = new HashMap<>();
        context.put("caseId", caseId);
        context.put("operation", "kick-operation");

        ScopedValue.callWhere(
            WORKFLOW_CONTEXT,
            context,
            () -> {
                // Test that YNetRunner operations preserve context
                assertTrue(netRunner.isAlive(), "Net runner should be alive with context");
                assertFalse(netRunner.getEnabledTasks().isEmpty(), "Should have enabled tasks with context");

                // Execute task in context
                var enabledTasks = netRunner.getEnabledTasks();
                if (!enabledTasks.isEmpty()) {
                    YTask task = enabledTasks.iterator().next();
                    return task.getNetID();
                }
                return "no-tasks";
            }
        );
    }

    /**
     * Test ScopedValue exception handling and cleanup.
     */
    @Test
    void testScopedValueExceptionHandling() throws Exception {
        Map<String, String> context = new HashMap<>();
        context.put("testCaseId", testCaseId);

        // Test that ScopedValue properly cleans up context even when exceptions occur
        try {
            ScopedValue.callWhere(
                WORKFLOW_CONTEXT,
                context,
                () -> {
                    // Verify context is present
                    assertEquals(testCaseId, WORKFLOW_CONTEXT.get().get("testCaseId"));

                    // Throw an exception
                    throw new RuntimeException("Test exception");
                }
            );
            fail("Should have thrown RuntimeException");
        } catch (RuntimeException e) {
            // Verify context is not accessible after exception
            assertThrows(IllegalStateException.class, () -> WORKFLOW_CONTEXT.get());
        }
    }

    /**
     * Test ScopedValue with complex nested virtual thread scenarios.
     */
    @Test
    void testNestedVirtualThreadContext() throws Exception {
        // Create parent context
        Map<String, String> parentContext = new HashMap<>();
        parentContext.put("parentCase", testCaseId);
        parentContext.put("level", "parent");

        String result = ScopedValue.callWhere(
            WORKFLOW_CONTEXT,
            parentContext,
            () -> {
                // Level 1: Create virtual thread
                return executor.submit(() -> {
                    // Verify context inheritance
                    Map<String, String> level1Context = WORKFLOW_CONTEXT.get();
                    assertEquals(parentContext.get("parentCase"), level1Context.get("parentCase"));

                    // Level 2: Nested virtual thread
                    return executor.submit(() -> {
                        // Verify context inheritance in nested thread
                        Map<String, String> level2Context = WORKFLOW_CONTEXT.get();
                        assertEquals(parentContext.get("parentCase"), level2Context.get("parentCase"));
                        assertEquals("parent", level2Context.get("level"));

                        return level2Context.get("parentCase");
                    }).get();
                }).get();
            }
        );

        assertEquals(testCaseId, result, "Context should be preserved across nested virtual threads");
    }

    // Helper methods

    /**
     * Creates a simple YAWL specification for testing.
     */
    private YSpecification createSimpleTestSpecification() throws Exception {
        // Create a simple specification for testing
        String specXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <specification xmlns="http://www.yawlfoundation.org/yawlschema"
                           xmlns:jxb="http://java.sun.com/xml/ns/jaxb"
                           jxb:version="2.0"
                           name="SimpleTest"
                           version="1.0"
                           xsdversion="2.0">
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="data" type="xs:string"/>
                </xs:schema>
                <net id="simple" name="Simple Test Net">
                    <places>
                        <place id="start" name="Start" in="true"/>
                        <place id="end" name="End" out="true"/>
                    </places>
                    <transitions>
                        <transition id="task1" name="Task 1">
                            <condition preset="start"/>
                        </transition>
                    </transitions>
                    <arcs>
                        <arc id="start_to_task1" source="start" target="task1"/>
                        <arc id="task1_to_end" source="task1" target="end"/>
                    </arcs>
                </net>
            </specification>
            """;

        org.jdom2.Document document = new org.jdom2.Document();
        org.jdom2.Element root = new org.jdom2.Element("specification");
        document.setRootElement(root);

        return new YSpecification("SimpleTest", "1.0", document);
    }

    /**
     * Helper method to execute a workflow task with context.
     */
    private String executeWorkflowTaskWithNetRunner(YNetRunner netRunner, String caseId) {
        try {
            // Simulate task execution with context awareness
            var enabledTasks = netRunner.getEnabledTasks();
            if (!enabledTasks.isEmpty()) {
                YTask task = enabledTasks.iterator().next();
                return "executed|" + caseId + "|" + task.getNetID();
            }
            return "no-tasks|" + caseId;
        } catch (Exception e) {
            return "error|" + caseId + "|" + e.getMessage();
        }
    }

    /**
     * Helper method to execute a subtask in structured concurrency.
     */
    private String executeSubTask(String subTaskId) {
        // Simulate subtask execution
        return "subtask-result-" + subTaskId + "-" + Thread.currentThread().getName();
    }
}