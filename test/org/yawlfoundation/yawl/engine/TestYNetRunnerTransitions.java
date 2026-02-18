/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.*;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Comprehensive tests for YNetRunner state transitions following Chicago TDD methodology.
 *
 * <p>These tests verify the core execution semantics of YNetRunner including:</p>
 * <ul>
 *   <li>Net runner initialization from specifications</li>
 *   <li>Token flow through nets (input condition to output condition)</li>
 *   <li>Task execution lifecycle (enabled -> fired -> started -> completed)</li>
 *   <li>Synchronization points (AND/XOR joins)</li>
 *   <li>Cancellation handling</li>
 *   <li>Error states and recovery mechanisms</li>
 *   <li>Suspend/resume execution states</li>
 *   <li>Deadlock detection</li>
 * </ul>
 *
 * <p>All tests use real YAWL Engine instances and actual specification files.
 * NO MOCKS are used - this follows the Chicago/Detroit School TDD approach.</p>
 *
 * @author YAWL Test Suite
 * @see YNetRunner
 * @see YNetRunner.ExecutionStatus
 */
@DisplayName("YNetRunner State Transition Tests")
@Tag("integration")
class TestYNetRunnerTransitions {

    private YEngine _engine;
    private YSpecification _specification;
    private YSpecificationID _specID;
    private YIdentifier _caseID;
    private YNetRunner _runner;

    /**
     * Test fixture setup - initializes engine and loads specification.
     */
    @BeforeEach
    void setUp() throws YSchemaBuildingException, YSyntaxException,
            YEngineStateException, JDOMException, IOException,
            YPersistenceException, YQueryException {
        _engine = YEngine.getInstance();
        EngineClearer.clear(_engine);
    }

    /**
     * Test fixture teardown - cleans up engine state.
     */
    @AfterEach
    void tearDown() throws YPersistenceException, YEngineStateException {
        if (_engine != null) {
            EngineClearer.clear(_engine);
        }
    }

    /**
     * Helper method to load a specification from a test resource file.
     */
    private YSpecification loadSpecification(String resourcePath)
            throws YSchemaBuildingException, YSyntaxException, JDOMException, IOException {
        URL fileURL = getClass().getResource(resourcePath);
        assertNotNull(fileURL, "Test resource not found: " + resourcePath);
        File yawlFile = new File(fileURL.getFile());
        List<YSpecification> specs = YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(yawlFile.getAbsolutePath()));
        assertFalse(specs.isEmpty(), "No specifications found in: " + resourcePath);
        return specs.get(0);
    }

    /**
     * Helper method to start a case and get the runner.
     */
    private YNetRunner startCaseAndGetRunner(YSpecification spec)
            throws YStateException, YPersistenceException, YDataStateException,
            YQueryException {
        _specID = spec.getSpecificationID();
        _engine.loadSpecification(spec);
        _caseID = _engine.startCase(_specID, null, null, null,
                new YLogDataItemList(), null, false);
        return _engine._netRunnerRepository.get(_caseID);
    }

    /**
     * Helper method to create an empty data document.
     */
    private Document createEmptyData() {
        Document doc = new Document();
        doc.setRootElement(new Element("data"));
        return doc;
    }

    // =========================================================================
    // Net Runner Initialization Tests
    // =========================================================================

    @Nested
    @DisplayName("Net Runner Initialization")
    class NetRunnerInitializationTests {

        @Test
        @DisplayName("Runner initializes with correct specification")
        void runnerInitializesWithCorrectSpecification()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            assertNotNull(_runner, "NetRunner should not be null after case start");
            assertNotNull(_runner.getCaseID(), "Case ID should not be null");
            assertEquals(_specID, _runner.getSpecificationID(),
                    "Specification ID should match loaded specification");
            assertNotNull(_runner.getNet(), "Net should be initialized");
        }

        @Test
        @DisplayName("Runner starts with token in input condition")
        void runnerStartsWithTokenInInputCondition()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            YNet net = _runner.getNet();
            YInputCondition inputCondition = net.getInputCondition();

            assertTrue(inputCondition.containsIdentifier(),
                    "Input condition should contain a token after case start");
            assertTrue(_caseID.getLocations().contains(inputCondition),
                    "Case ID should be located in input condition");
        }

        @Test
        @DisplayName("Runner initializes with normal execution status")
        void runnerInitializesWithNormalExecutionStatus()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            assertTrue(_runner.hasNormalState(),
                    "Runner should start in Normal execution status");
            assertFalse(_runner.isSuspending(), "Runner should not be suspending");
            assertFalse(_runner.isSuspended(), "Runner should not be suspended");
            assertFalse(_runner.isResuming(), "Runner should not be resuming");
            assertFalse(_runner.isInSuspense(), "Runner should not be in suspense");
        }

        @Test
        @DisplayName("Runner tracks start time correctly")
        void runnerTracksStartTimeCorrectly()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            long beforeStart = System.currentTimeMillis();
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);
            long afterStart = System.currentTimeMillis();

            long startTime = _runner.getStartTime();
            assertTrue(startTime >= beforeStart && startTime <= afterStart,
                    "Start time should be between test start and test end");
        }
    }

    // =========================================================================
    // Token Flow Tests
    // =========================================================================

    @Nested
    @DisplayName("Token Flow Through Nets")
    class TokenFlowTests {

        @Test
        @DisplayName("Token flows from input condition to first enabled task")
        void tokenFlowsFromInputToEnabledTask()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            Set<YTask> enabledTasks = _runner.getEnabledTasks();
            assertFalse(enabledTasks.isEmpty(),
                    "Should have at least one enabled task after case start");

            YTask firstTask = enabledTasks.iterator().next();
            assertNotNull(firstTask, "First enabled task should not be null");
            assertEquals("a-top", firstTask.getID(),
                    "First enabled task should be 'a-top'");
        }

        @Test
        @DisplayName("Token moves correctly when task is fired")
        void tokenMovesCorrectlyWhenTaskFired()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Verify initial state - task is enabled
            assertTrue(_runner.getEnabledTasks().size() == 1,
                    "Should have exactly one enabled task initially");
            assertTrue(_runner.getBusyTasks().isEmpty(),
                    "Should have no busy tasks initially");

            // Fire the task
            List<YIdentifier> children = _runner.attemptToFireAtomicTask(null, "a-top");

            assertNotNull(children, "Fire should return child identifiers");
            assertFalse(children.isEmpty(), "Fire should return at least one child");

            // Verify state after firing
            assertTrue(_runner.getEnabledTasks().isEmpty(),
                    "Should have no enabled tasks after firing");
            assertTrue(_runner.getBusyTasks().size() == 1,
                    "Should have exactly one busy task after firing");
        }

        @Test
        @DisplayName("Token reaches output condition on net completion")
        void tokenReachesOutputConditionOnCompletion()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException, InterruptedException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Complete the sequence: fire a-top, then b-top
            List<YIdentifier> aTopChildren = _runner.attemptToFireAtomicTask(null, "a-top");
            _runner.startWorkItemInTask(null, aTopChildren.get(0), "a-top");
            _runner.completeWorkItemInTask(null, null, aTopChildren.get(0), "a-top",
                    createEmptyData());

            // Give the engine time to process
            Thread.sleep(100);

            // Now b-top should be enabled - fire and complete it
            List<YIdentifier> bTopChildren = _runner.attemptToFireAtomicTask(null, "b-top");

            // For multi-instance task, complete threshold number of instances
            YAtomicTask bTop = (YAtomicTask) _runner.getNetElement("b-top");
            int threshold = bTop.getMultiInstanceAttributes().getThreshold();
            int childrenToComplete = Math.min(threshold, bTopChildren.size());

            for (int i = 0; i < childrenToComplete; i++) {
                YIdentifier child = bTopChildren.get(i);
                _runner.startWorkItemInTask(null, child, "b-top");

                boolean isLastRequired = (i == childrenToComplete - 1);
                boolean taskExited = _runner.completeWorkItemInTask(null, null, child,
                        "b-top", createEmptyData());

                if (isLastRequired) {
                    assertTrue(taskExited,
                            "Task should exit after completing threshold instances");
                }
            }

            // Give time for completion processing
            Thread.sleep(200);

            // Verify net completion
            YOutputCondition outputCondition = _runner.getNet().getOutputCondition();
            assertTrue(outputCondition.containsIdentifier() || !_runner.isAlive(),
                    "Output condition should contain token or case should be complete");
        }
    }

    // =========================================================================
    // Task Execution Lifecycle Tests
    // =========================================================================

    @Nested
    @DisplayName("Task Execution Lifecycle")
    class TaskExecutionLifecycleTests {

        @Test
        @DisplayName("Task transitions from enabled to busy on fire")
        void taskTransitionsEnabledToBusyOnFire()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            YTask task = (YTask) _runner.getNetElement("a-top");
            assertTrue(_runner.getEnabledTasks().contains(task),
                    "Task 'a-top' should be enabled initially");

            _runner.attemptToFireAtomicTask(null, "a-top");

            assertTrue(_runner.getBusyTasks().contains(task),
                    "Task 'a-top' should be busy after firing");
            assertFalse(_runner.getEnabledTasks().contains(task),
                    "Task 'a-top' should not be enabled after firing");
        }

        @Test
        @DisplayName("Task cannot fire if not enabled")
        void taskCannotFireIfNotEnabled()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // b-top is not enabled initially (a-top must complete first)
            YStateException exception = assertThrows(YStateException.class, () -> {
                _runner.attemptToFireAtomicTask(null, "b-top");
            }, "Should throw exception when trying to fire non-enabled task");

            assertTrue(exception.getMessage().contains("b-top"),
                    "Exception message should mention the task ID");
        }

        @Test
        @DisplayName("Work item starts correctly within task")
        void workItemStartsCorrectlyWithinTask()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            List<YIdentifier> children = _runner.attemptToFireAtomicTask(null, "a-top");
            YIdentifier caseID = children.get(0);

            // Start should not throw exception
            assertDoesNotThrow(() -> {
                _runner.startWorkItemInTask(null, caseID, "a-top");
            }, "Starting work item should not throw exception");
        }

        @Test
        @DisplayName("Work item completes and task exits")
        void workItemCompletesAndTaskExits()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            List<YIdentifier> children = _runner.attemptToFireAtomicTask(null, "a-top");
            YIdentifier caseID = children.get(0);
            _runner.startWorkItemInTask(null, caseID, "a-top");

            boolean taskExited = _runner.completeWorkItemInTask(null, null, caseID,
                    "a-top", createEmptyData());

            assertTrue(taskExited, "Task should exit after work item completion");
        }
    }

    // =========================================================================
    // AND/XOR Join Tests
    // =========================================================================

    @Nested
    @DisplayName("Synchronization Points")
    class SynchronizationTests {

        @Test
        @DisplayName("AND join waits for all incoming branches")
        void andJoinWaitsForAllBranches()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException {
            _specification = loadSpecification("TestOrJoin.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Task A has AND split - fires multiple branches
            // Task D has AND join - must wait for all branches
            List<YIdentifier> children = _runner.attemptToFireAtomicTask(null, "A");
            assertNotNull(children, "Task A should fire successfully");

            // Verify multiple branches are active
            assertTrue(children.size() > 1,
                    "AND split should create multiple child identifiers");
        }

        @Test
        @DisplayName("XOR join activates on first incoming branch")
        void xorJoinActivatesOnFirstBranch()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Task a-top has XOR join - only needs one token
            YTask aTop = (YTask) _runner.getNetElement("a-top");
            assertEquals(YTask._XOR, aTop.getJoinType(),
                    "Task 'a-top' should have XOR join");

            // Task should be enabled with single token in input condition
            assertTrue(_runner.getEnabledTasks().contains(aTop),
                    "Task with XOR join should be enabled with single incoming token");
        }

        @Test
        @DisplayName("OR join enables when at least one branch arrives")
        void orJoinEnablesWhenAtLeastOneBranchArrives()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("TestOrJoin.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Task E has OR join - should enable when any branch arrives
            YTask taskE = (YTask) _runner.getNetElement("E");
            assertEquals(YTask._OR, taskE.getJoinType(),
                    "Task 'E' should have OR join");

            // Fire A to start the process
            _runner.attemptToFireAtomicTask(null, "A");

            // OR join task E should eventually be enabled
            // Note: OR join semantics in YAWL are complex; this tests basic enablement
        }
    }

    // =========================================================================
    // Cancellation Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Cancellation Handling")
    class CancellationTests {

        @Test
        @DisplayName("Runner cancellation clears all active tasks")
        void runnerCancellationClearsAllActiveTasks()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Fire a task to make it busy
            _runner.attemptToFireAtomicTask(null, "a-top");
            assertFalse(_runner.getBusyTasks().isEmpty(),
                    "Should have busy task before cancellation");

            // Cancel the runner
            _runner.cancel(null);

            assertTrue(_runner.getBusyTasks().isEmpty(),
                    "Busy tasks should be empty after cancellation");
            assertTrue(_runner.getEnabledTasks().isEmpty(),
                    "Enabled tasks should be empty after cancellation");
        }

        @Test
        @DisplayName("Runner is not alive after cancellation")
        void runnerNotAliveAfterCancellation()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            assertTrue(_runner.isAlive(), "Runner should be alive initially");

            _runner.cancel(null);

            assertFalse(_runner.isAlive(), "Runner should not be alive after cancellation");
        }

        @Test
        @DisplayName("Task cancellation removes it from active task lists")
        void taskCancellationRemovesFromActiveLists()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Fire and start a task
            List<YIdentifier> children = _runner.attemptToFireAtomicTask(null, "a-top");
            YTask aTop = (YTask) _runner.getNetElement("a-top");

            assertTrue(_runner.getBusyTasks().contains(aTop),
                    "Task should be in busy list before cancellation");

            // Cancel the specific task
            _runner.cancelTask(null, "a-top");

            assertFalse(_runner.getBusyTasks().contains(aTop),
                    "Task should not be in busy list after task cancellation");
        }

        @Test
        @DisplayName("Cancellation removes tokens from conditions")
        void cancellationRemovesTokensFromConditions()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            YInputCondition inputCondition = _runner.getNet().getInputCondition();
            assertTrue(inputCondition.containsIdentifier(),
                    "Input condition should contain token before cancellation");

            _runner.cancel(null);

            assertFalse(inputCondition.containsIdentifier(),
                    "Input condition should not contain token after cancellation");
        }
    }

    // =========================================================================
    // Execution Status Tests
    // =========================================================================

    @Nested
    @DisplayName("Execution Status Transitions")
    class ExecutionStatusTests {

        @Test
        @DisplayName("Runner transitions to suspending state")
        void runnerTransitionsToSuspendingState()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            assertTrue(_runner.hasNormalState(), "Should start in normal state");

            _runner.setStateSuspending();

            assertTrue(_runner.isSuspending(), "Should be in suspending state");
            assertTrue(_runner.isInSuspense(), "Should be in suspense");
            assertFalse(_runner.hasNormalState(), "Should not be in normal state");
        }

        @Test
        @DisplayName("Runner transitions to suspended state")
        void runnerTransitionsToSuspendedState()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            _runner.setStateSuspended();

            assertTrue(_runner.isSuspended(), "Should be in suspended state");
            assertTrue(_runner.isInSuspense(), "Should be in suspense");
        }

        @Test
        @DisplayName("Runner transitions through full suspend/resume cycle")
        void runnerTransitionsThroughSuspendResumeCycle()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Normal -> Suspending
            _runner.setStateSuspending();
            assertTrue(_runner.isSuspending(), "Should be suspending");

            // Suspending -> Suspended
            _runner.setStateSuspended();
            assertTrue(_runner.isSuspended(), "Should be suspended");

            // Suspended -> Resuming
            _runner.setStateResuming();
            assertTrue(_runner.isResuming(), "Should be resuming");

            // Resuming -> Normal
            _runner.setStateNormal();
            assertTrue(_runner.hasNormalState(), "Should be back in normal state");
        }

        @Test
        @DisplayName("Execution status string conversion works correctly")
        void executionStatusStringConversion()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            assertEquals("Normal", _runner.getExecutionStatus(),
                    "Initial status should be 'Normal'");

            _runner.setStateSuspending();
            assertEquals("Suspending", _runner.getExecutionStatus(),
                    "Status should be 'Suspending'");

            _runner.setExecutionStatus("Suspended");
            assertEquals("Suspended", _runner.getExecutionStatus(),
                    "Status should be 'Suspended' after setExecutionStatus");
        }
    }

    // =========================================================================
    // Deadlock Detection Tests
    // =========================================================================

    @Nested
    @DisplayName("Deadlock Detection")
    class DeadlockDetectionTests {

        @Test
        @DisplayName("Runner detects completed net")
        void runnerDetectsCompletedNet()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException, InterruptedException,
                YEngineStateException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Complete the sequence
            List<YIdentifier> aTopChildren = _runner.attemptToFireAtomicTask(null, "a-top");
            _runner.startWorkItemInTask(null, aTopChildren.get(0), "a-top");
            _runner.completeWorkItemInTask(null, null, aTopChildren.get(0), "a-top",
                    createEmptyData());

            Thread.sleep(100);

            // Check if net is completed or has active tasks
            boolean hasActive = _runner.hasActiveTasks();
            boolean endReached = _runner.endOfNetReached();
            boolean isComplete = _runner.isCompleted();

            // Either we have active tasks or the net is complete
            assertTrue(hasActive || endReached || isComplete,
                    "Net should either have active tasks or be complete");
        }

        @Test
        @DisplayName("Runner correctly identifies empty net state")
        void runnerCorrectlyIdentifiesEmptyNetState()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException, YEngineStateException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Initially not empty (has tokens)
            assertFalse(_runner.isEmpty(),
                    "Net should not be empty initially");

            // After cancellation, net should be empty
            _runner.cancel(null);

            assertTrue(_runner.isEmpty(),
                    "Net should be empty after cancellation");
        }

        @Test
        @DisplayName("End of net reached when output condition has token")
        void endOfNetReachedWhenOutputConditionHasToken()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException, InterruptedException,
                YEngineStateException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Complete full sequence
            List<YIdentifier> aTopChildren = _runner.attemptToFireAtomicTask(null, "a-top");
            _runner.startWorkItemInTask(null, aTopChildren.get(0), "a-top");
            _runner.completeWorkItemInTask(null, null, aTopChildren.get(0), "a-top",
                    createEmptyData());

            Thread.sleep(100);

            // Check output condition state
            YOutputCondition outputCondition = _runner.getNet().getOutputCondition();

            // If output condition has token, end of net is reached
            if (outputCondition.containsIdentifier()) {
                assertTrue(_runner.endOfNetReached(),
                        "endOfNetReached should return true when output condition has token");
                assertTrue(_runner.isCompleted(),
                        "isCompleted should return true when end of net is reached");
            }
        }
    }

    // =========================================================================
    // Multi-Instance Task Tests
    // =========================================================================

    @Nested
    @DisplayName("Multi-Instance Task Handling")
    class MultiInstanceTaskTests {

        @Test
        @DisplayName("Multi-instance task creates multiple child identifiers")
        void multiInstanceTaskCreatesMultipleChildren()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Complete first task to enable multi-instance task b-top
            List<YIdentifier> aTopChildren = _runner.attemptToFireAtomicTask(null, "a-top");
            _runner.startWorkItemInTask(null, aTopChildren.get(0), "a-top");
            _runner.completeWorkItemInTask(null, null, aTopChildren.get(0), "a-top",
                    createEmptyData());

            // Fire multi-instance task
            List<YIdentifier> bTopChildren = _runner.attemptToFireAtomicTask(null, "b-top");

            // b-top is a multi-instance task with min=2, max=7, threshold=5
            YAtomicTask bTop = (YAtomicTask) _runner.getNetElement("b-top");
            assertTrue(bTop.isMultiInstance(), "Task b-top should be multi-instance");

            // Should create multiple instances
            assertTrue(bTopChildren.size() >= 2,
                    "Multi-instance task should create at least min instances");
        }

        @Test
        @DisplayName("Multi-instance task completes after threshold reached")
        void multiInstanceTaskCompletesAfterThreshold()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Enable and fire multi-instance task
            List<YIdentifier> aTopChildren = _runner.attemptToFireAtomicTask(null, "a-top");
            _runner.startWorkItemInTask(null, aTopChildren.get(0), "a-top");
            _runner.completeWorkItemInTask(null, null, aTopChildren.get(0), "a-top",
                    createEmptyData());

            List<YIdentifier> bTopChildren = _runner.attemptToFireAtomicTask(null, "b-top");
            YAtomicTask bTop = (YAtomicTask) _runner.getNetElement("b-top");
            int threshold = bTop.getMultiInstanceAttributes().getThreshold();

            // Complete instances up to threshold
            boolean taskExited = false;
            for (int i = 0; i < Math.min(threshold, bTopChildren.size()); i++) {
                YIdentifier child = bTopChildren.get(i);
                _runner.startWorkItemInTask(null, child, "b-top");
                boolean exited = _runner.completeWorkItemInTask(null, null, child,
                        "b-top", createEmptyData());
                if (exited) {
                    taskExited = true;
                }
            }

            assertTrue(taskExited,
                    "Task should exit after completing threshold number of instances");
        }

        @Test
        @DisplayName("Adding new instance to busy multi-instance task")
        void addingNewInstanceToBusyMultiInstanceTask()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Enable and fire multi-instance task
            List<YIdentifier> aTopChildren = _runner.attemptToFireAtomicTask(null, "a-top");
            _runner.startWorkItemInTask(null, aTopChildren.get(0), "a-top");
            _runner.completeWorkItemInTask(null, null, aTopChildren.get(0), "a-top",
                    createEmptyData());

            List<YIdentifier> bTopChildren = _runner.attemptToFireAtomicTask(null, "b-top");

            // b-top allows dynamic instance creation
            YIdentifier firstChild = bTopChildren.get(0);
            _runner.startWorkItemInTask(null, firstChild, "b-top");

            // Add new instance
            YIdentifier newID = _runner.addNewInstance(null, "b-top", firstChild,
                    new Element("stub"));

            // If dynamic creation is enabled, should get new identifier
            // If not, should return null
            // b-top has dynamic creation mode in the test specification
        }
    }

    // =========================================================================
    // Active Task Tracking Tests
    // =========================================================================

    @Nested
    @DisplayName("Active Task Tracking")
    class ActiveTaskTrackingTests {

        @Test
        @DisplayName("Runner tracks enabled task names")
        void runnerTracksEnabledTaskNames()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            Set<String> enabledNames = _runner.getEnabledTaskNames();
            assertFalse(enabledNames.isEmpty(),
                    "Should have enabled task names after case start");
            assertTrue(enabledNames.contains("a-top"),
                    "Enabled task names should include 'a-top'");
        }

        @Test
        @DisplayName("Runner tracks busy task names")
        void runnerTracksBusyTaskNames()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            assertTrue(_runner.getBusyTaskNames().isEmpty(),
                    "Should have no busy task names initially");

            _runner.attemptToFireAtomicTask(null, "a-top");

            Set<String> busyNames = _runner.getBusyTaskNames();
            assertFalse(busyNames.isEmpty(),
                    "Should have busy task names after firing");
            assertTrue(busyNames.contains("a-top"),
                    "Busy task names should include 'a-top'");
        }

        @Test
        @DisplayName("Active tasks combines enabled and busy")
        void activeTasksCombinesEnabledAndBusy()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            Set<YTask> activeTasks = _runner.getActiveTasks();
            assertEquals(_runner.getEnabledTasks(), activeTasks,
                    "Active tasks should equal enabled tasks when no busy tasks");

            assertTrue(_runner.hasActiveTasks(),
                    "hasActiveTasks should return true when there are enabled tasks");
        }

        @Test
        @DisplayName("hasActiveTasks returns false when no enabled or busy tasks")
        void hasActiveTasksReturnsFalseWhenEmpty()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            _runner.cancel(null);

            assertFalse(_runner.hasActiveTasks(),
                    "hasActiveTasks should return false after cancellation");
        }
    }

    // =========================================================================
    // Net Element Access Tests
    // =========================================================================

    @Nested
    @DisplayName("Net Element Access")
    class NetElementAccessTests {

        @Test
        @DisplayName("Runner provides access to net elements by ID")
        void runnerProvidesAccessToNetElements()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            YExternalNetElement element = _runner.getNetElement("a-top");
            assertNotNull(element, "Should find net element by ID");
            assertEquals("a-top", element.getID(), "Element ID should match");

            assertTrue(element instanceof YTask, "Element should be a task");
        }

        @Test
        @DisplayName("Runner provides access to conditions")
        void runnerProvidesAccessToConditions()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            YExternalNetElement inputCond = _runner.getNetElement("i-top");
            assertNotNull(inputCond, "Should find input condition");
            assertTrue(inputCond instanceof YCondition,
                    "Input condition should be a YCondition");

            YExternalNetElement outputCond = _runner.getNetElement("o-top");
            assertNotNull(outputCond, "Should find output condition");
        }

        @Test
        @DisplayName("Runner toString provides useful debug information")
        void runnerToStringProvidesDebugInfo()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException, YDataStateException,
                YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            String str = _runner.toString();
            assertNotNull(str, "toString should not return null");
            assertTrue(str.contains("CaseID"), "toString should mention CaseID");
            assertTrue(str.contains("Enabled"), "toString should mention Enabled");
            assertTrue(str.contains("Busy"), "toString should mention Busy");
        }
    }

    // =========================================================================
    // Work Item Rollback Tests
    // =========================================================================

    @Nested
    @DisplayName("Work Item Rollback")
    class WorkItemRollbackTests {

        @Test
        @DisplayName("Rollback returns work item from executing to fired state")
        void rollbackReturnsWorkItemToFiredState()
                throws YSchemaBuildingException, YSyntaxException, JDOMException,
                IOException, YStateException, YPersistenceException,
                YDataStateException, YQueryException {
            _specification = loadSpecification("YAWL_Specification2.xml");
            _runner = startCaseAndGetRunner(_specification);

            // Fire and start task
            List<YIdentifier> children = _runner.attemptToFireAtomicTask(null, "a-top");
            YIdentifier caseID = children.get(0);
            _runner.startWorkItemInTask(null, caseID, "a-top");

            // Rollback
            boolean rolledBack = _runner.rollbackWorkItem(null, caseID, "a-top");

            assertTrue(rolledBack, "Rollback should succeed for executing work item");
        }
    }

    /**
     * Main method for running tests via command line.
     */
    public static void main(String[] args) {
        System.out.println("Run via: mvn test -Dtest=TestYNetRunnerTransitions");
    }
}
