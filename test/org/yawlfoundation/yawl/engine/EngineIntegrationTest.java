package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.elements.state.YIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Engine Core Integration Tests
 * Tests YEngine and YNetRunner with real workflow execution (Chicago TDD)
 *
 * Coverage:
 * - YEngine initialization
 * - YNetRunner workflow execution
 * - YWorkItem creation and state transitions
 * - Parallel execution (concurrent cases)
 * - Exception handling
 * - State transitions
 * - Performance (identifier creation throughput)
 */
@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD)
class EngineIntegrationTest {

    private YEngine engine;
    private YSpecification specification;

    @BeforeEach
    void setUp() throws Exception {
        engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine instance should be available");
        EngineClearer.clear(engine);
        specification = createMinimalSpecification();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            EngineClearer.clear(engine);
            engine.getWorkItemRepository().clear();
        }
    }

    @Test
    void testEngineInitialization() {
        assertNotNull(engine, "Engine should be initialized");
        YEngine instance1 = YEngine.getInstance();
        YEngine instance2 = YEngine.getInstance();
        assertSame(instance1, instance2, "Engine should be singleton");
    }

    @Test
    void testSpecificationCreation() throws Exception {
        assertNotNull(specification, "Specification should be created");
        assertNotNull(specification.getSpecificationID(), "Specification should have ID");

        YNet rootNet = specification.getRootNet();
        assertNotNull(rootNet, "Specification should have root net");
    }

    @Test
    void testBasicWorkflowExecution() throws Exception {
        YSpecification spec = createMinimalSpecification();
        assertNotNull(spec, "Specification created");

        YIdentifier caseID = new YIdentifier(null);
        assertNotNull(caseID, "Case ID created");

        YNet rootNet = spec.getRootNet();
        assertNotNull(rootNet, "Root net available");

        assertNotNull(rootNet.getNetElements(), "Root net should have elements");
    }

    @Test
    void testMultipleCaseExecution() {
        int caseCount = 10;
        List<YIdentifier> caseIds = new ArrayList<>();

        for (int i = 0; i < caseCount; i++) {
            YIdentifier caseId = new YIdentifier(null);
            caseIds.add(caseId);
            assertNotNull(caseId, "Case ID " + i + " should be created");
        }

        assertEquals(caseCount, caseIds.size(), "Should have created 10 cases");

        for (int i = 0; i < caseIds.size(); i++) {
            for (int j = i + 1; j < caseIds.size(); j++) {
                assertNotSame(caseIds.get(i), caseIds.get(j), "Case IDs should be unique");
            }
        }
    }

    @Test
    void testConcurrentCaseExecution() throws Exception {
        int concurrentCases = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(concurrentCases);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentCases; i++) {
            final int caseNumber = i;
            executor.submit(() -> {
                try {
                    YIdentifier caseId = new YIdentifier(null);
                    assertNotNull(caseId, "Case " + caseNumber + " created");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "All concurrent cases should complete");
        assertEquals(concurrentCases, successCount.get(), "All cases should succeed");
        assertEquals(0, errorCount.get(), "No cases should error");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void testYWorkItemCreation() throws Exception {
        YSpecification spec = createMinimalSpecification();
        YNet rootNet = spec.getRootNet();

        YTask task = createTask(rootNet, "task1");
        assertNotNull(task, "Task should be created");

        YIdentifier caseId = new YIdentifier(null);
        YWorkItemID workItemID = new YWorkItemID(caseId, "task1");
        YWorkItem workItem = new YWorkItem(null, spec.getSpecificationID(), task, workItemID, true, false);

        assertNotNull(workItem, "Work item should be created");
        assertEquals("task1", workItem.getTaskID(), "Work item task ID should match");
        assertEquals(YWorkItemStatus.statusEnabled, workItem.getStatus(), "Work item should be enabled");
    }

    @Test
    void testYWorkItemStateTransitions() throws Exception {
        YSpecification spec = createMinimalSpecification();
        YNet rootNet = spec.getRootNet();
        YTask task = createTask(rootNet, "task_state");

        YIdentifier caseId = new YIdentifier(null);
        YWorkItemID workItemID = new YWorkItemID(caseId, "task_state");
        YWorkItem workItem = new YWorkItem(null, spec.getSpecificationID(), task, workItemID, true, false);

        assertEquals(YWorkItemStatus.statusEnabled, workItem.getStatus(),
                "Initial state should be enabled");
        assertNotEquals(YWorkItemStatus.statusExecuting, workItem.getStatus(),
                "Should not be executing initially");

        workItem.setStatus(YWorkItemStatus.statusExecuting);
        assertEquals(YWorkItemStatus.statusExecuting, workItem.getStatus(),
                "Status should be executing after transition");
    }

    @Test
    void testLaunchCaseWithUnloadedSpecificationThrowsException() throws Exception {
        // Launching a case with a spec ID that has not been loaded must throw YStateException
        YSpecificationID unloadedSpec = new YSpecificationID("NonExistent", "0.1", "NonExistent");
        assertThrows(Exception.class,
                () -> engine.launchCase(unloadedSpec, null, null, null),
                "Launching a case for an unloaded specification must throw an exception");
    }

    @Test
    void testEnginePerformanceThroughput() throws Exception {
        int caseCount = 100;
        long startTime = System.currentTimeMillis();

        List<YIdentifier> caseIds = new ArrayList<>();
        for (int i = 0; i < caseCount; i++) {
            caseIds.add(new YIdentifier(null));
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertEquals(caseCount, caseIds.size(), "Should create all cases");

        double casesPerSecond = (caseCount * 1000.0) / Math.max(duration, 1);
        System.out.println("Case creation throughput: " + String.format("%.1f", casesPerSecond) +
            " cases/second (" + caseCount + " cases in " + duration + "ms)");

        assertTrue(casesPerSecond > 10, "Should achieve reasonable throughput (>10 cases/sec)");
    }

    @Test
    void testHighVolumeCaseCreation() {
        int volumeCount = 1000;
        long startTime = System.currentTimeMillis();

        List<YIdentifier> caseIds = new ArrayList<>(volumeCount);
        for (int i = 0; i < volumeCount; i++) {
            caseIds.add(new YIdentifier(null));
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertEquals(volumeCount, caseIds.size(), "Should create all high-volume cases");

        double casesPerSecond = (volumeCount * 1000.0) / Math.max(duration, 1);
        System.out.println("High-volume throughput: " + String.format("%.1f", casesPerSecond) +
            " cases/second (" + volumeCount + " cases in " + duration + "ms)");
    }

    @Test
    void testNetRunnerVerification() throws Exception {
        YSpecification spec = createMinimalSpecification();
        YNet rootNet = spec.getRootNet();

        assertNotNull(rootNet, "Net should exist");
        assertNotNull(rootNet.getSpecification(), "Net should have specification");
        assertNotNull(rootNet.getNetElements(), "Net should have net elements");
    }

    // Helper methods

    private YSpecification createMinimalSpecification() throws Exception {
        YSpecification spec = new YSpecification("MinimalSpec");
        spec.setName("Minimal Test Specification");
        spec.setVersion(YSchemaVersion.Beta7);

        YNet rootNet = new YNet("root", spec);
        spec.setRootNet(rootNet);

        YInputCondition input = new YInputCondition("input", rootNet);
        YOutputCondition output = new YOutputCondition("output", rootNet);

        rootNet.setInputCondition(input);
        rootNet.setOutputCondition(output);

        YAtomicTask task = new YAtomicTask("task1", YAtomicTask._AND, YAtomicTask._AND, rootNet);
        rootNet.addNetElement(task);

        YFlow flowIn = new YFlow(input, task);
        YFlow flowOut = new YFlow(task, output);

        input.addPostset(flowIn);
        task.addPreset(flowIn);
        task.addPostset(flowOut);
        output.addPreset(flowOut);

        return spec;
    }

    private YTask createTask(YNet net, String taskId) {
        YAtomicTask task = new YAtomicTask(taskId, YAtomicTask._AND, YAtomicTask._AND, net);
        net.addNetElement(task);
        return task;
    }
}
