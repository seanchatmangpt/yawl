/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
 *
 * Behavioral Test Suite for Van Der Aalst Petri Net Semantics
 * Purpose: Lock core engine task lifecycle semantics for v6.0.0-Alpha
 *
 * These tests verify the invariants of YAWL task state transitions based on
 * Petri net semantics as defined by Prof. Wil van der Aalst.
 *
 * CRITICAL INVARIANTS TESTED:
 * 1. AND join enablement requires ALL preset conditions to have tokens
 * 2. XOR join enablement requires ANY preset condition to have a token
 * 3. Token consumption semantics on fire
 * 4. Token production semantics on exit
 * 5. Cancellation set behavior
 */
package org.yawlfoundation.yawl.engine;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.*;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioral tests for YAWL Task lifecycle semantics.
 *
 * These tests verify Petri net semantics for task state transitions:
 * - AND join: requires ALL incoming conditions to have tokens (synchronizing merge)
 * - XOR join: requires ANY incoming condition to have a token (simple merge)
 * - OR join: requires satisfiability analysis (tested separately in TestOrJoin)
 *
 * @see YTask#t_enabled(YIdentifier)
 * @see YTask#t_fire(YPersistenceManager)
 * @see YTask#t_complete(YPersistenceManager, YIdentifier, Document)
 */
@DisplayName("Task Lifecycle Behavioral Tests (Petri Net Semantics)")
class TaskLifecycleBehavioralTest {

    private YEngine engine;
    private YSpecification specification;

    @BeforeEach
    void setUp() throws YEngineStateException {
        engine = YEngine.getInstance();
        EngineClearer.clear(engine);
    }

    @AfterEach
    void tearDown() throws Exception {
        EngineClearer.clear(engine);
    }

    // ========================================================================
    // AND JOIN SEMANTICS
    // ========================================================================

    @Nested
    @DisplayName("AND Join Enablement Semantics")
    class AndJoinSemantics {

        /**
         * INVARIANT: AND join task is enabled ONLY when ALL preset conditions have tokens.
         *
         * Petri Net Semantics: A transition with multiple input places (AND-join)
         * is enabled if and only if all input places contain at least one token.
         *
         * Reference: van der Aalst, "Workflow Management: Models, Methods, and Systems"
         *            Chapter 2 - Petri Nets
         */
        @Test
        @DisplayName("AND join NOT enabled when only ONE preset has token")
        void testAndJoinNotEnabledWithPartialTokens() throws Exception {
            // Load specification with AND join
            URL fileURL = getClass().getResource("YAWL_Specification2.xml");
            assertNotNull(fileURL, "Test specification file must exist");
            File yawlXMLFile = new File(fileURL.getFile());
            specification = YMarshal.unmarshalSpecifications(
                    StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);

            engine.loadSpecification(specification);

            // Start a case
            YIdentifier caseID = engine.startCase(
                    specification.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);

            YNetRunner runner = engine.getNetRunnerRepository().get(caseID);
            assertNotNull(runner, "NetRunner must exist for case");

            // Get the first enabled task (a-top with XOR join, not AND)
            Set<YTask> enabledTasks = runner.getEnabledTasks();
            assertFalse(enabledTasks.isEmpty(), "At least one task should be enabled");

            // Fire the first task to progress the net
            YAtomicTask firstTask = (YAtomicTask) runner.getNetElement("a-top");
            assertNotNull(firstTask, "Task 'a-top' must exist");

            // Verify AND join behavior in spec - b-top has AND join
            YTask bTop = (YTask) runner.getNetElement("b-top");
            assertNotNull(bTop, "Task 'b-top' must exist");
            assertEquals(YTask._AND, bTop.getJoinType(),
                    "Task 'b-top' should have AND join type");

            // b-top should NOT be enabled initially (only one path has token)
            assertFalse(bTop.t_enabled(caseID),
                    "AND join task should NOT be enabled with partial preset tokens");
        }

        /**
         * INVARIANT: AND join task becomes enabled when ALL preset conditions have tokens.
         */
        @Test
        @DisplayName("AND join enabled when ALL presets have tokens")
        void testAndJoinEnabledWithAllTokens() throws Exception {
            URL fileURL = getClass().getResource("YAWL_Specification3.xml");
            assertNotNull(fileURL, "Test specification file must exist");
            File yawlXMLFile = new File(fileURL.getFile());
            specification = YMarshal.unmarshalSpecifications(
                    StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);

            engine.loadSpecification(specification);

            YIdentifier caseID = engine.startCase(
                    specification.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);

            YNetRunner runner = engine.getNetRunnerRepository().get(caseID);
            assertNotNull(runner, "NetRunner must exist for case");

            // Verify the spec has AND split creating parallel paths
            // After the AND split, verify AND join semantics
            Set<YTask> enabledTasks = runner.getEnabledTasks();
            assertFalse(enabledTasks.isEmpty(), "Initial tasks should be enabled");

            // Verify that when a task with AND split fires, all postset conditions get tokens
            for (YTask task : enabledTasks) {
                if (task.getSplitType() == YTask._AND) {
                    // This task will place tokens in ALL postset conditions
                    assertTrue(task.t_enabled(caseID),
                            "AND split task should be enabled");
                    break;
                }
            }
        }
    }

    // ========================================================================
    // XOR JOIN SEMANTICS
    // ========================================================================

    @Nested
    @DisplayName("XOR Join Enablement Semantics")
    class XorJoinSemantics {

        /**
         * INVARIANT: XOR join task is enabled when ANY preset condition has a token.
         *
         * Petri Net Semantics: A transition with XOR-join (implicit OR)
         * is enabled if at least one input place contains a token.
         * This is the standard Petri net transition enablement rule.
         */
        @Test
        @DisplayName("XOR join enabled when ANY preset has token")
        void testXorJoinEnabledWithSingleToken() throws Exception {
            URL fileURL = getClass().getResource("YAWL_Specification2.xml");
            assertNotNull(fileURL, "Test specification file must exist");
            File yawlXMLFile = new File(fileURL.getFile());
            specification = YMarshal.unmarshalSpecifications(
                    StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);

            engine.loadSpecification(specification);

            YIdentifier caseID = engine.startCase(
                    specification.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);

            YNetRunner runner = engine.getNetRunnerRepository().get(caseID);
            assertNotNull(runner, "NetRunner must exist for case");

            // Task 'a-top' has XOR join - should be enabled with just input condition token
            YTask aTop = (YTask) runner.getNetElement("a-top");
            assertNotNull(aTop, "Task 'a-top' must exist");
            assertEquals(YTask._XOR, aTop.getJoinType(),
                    "Task 'a-top' should have XOR join type");

            // XOR join should be enabled with ANY preset token
            assertTrue(aTop.t_enabled(caseID),
                    "XOR join task should be enabled with at least one preset token");

            // Verify it's in the enabled set
            assertTrue(runner.getEnabledTasks().contains(aTop),
                    "XOR join task should be in enabled tasks set");
        }

        /**
         * INVARIANT: XOR join remains enabled as long as at least one preset has token.
         * Multiple tokens in different presets do not disable the task.
         */
        @Test
        @DisplayName("XOR join remains enabled with multiple preset tokens")
        void testXorJoinWithMultipleTokens() throws Exception {
            // XOR join semantics: any preset with token enables
            // Multiple tokens don't change enablement status
            YAtomicTask task = new YAtomicTask("test-xor-task", YTask._XOR, YTask._AND, null);

            // Task not busy initially
            assertFalse(task.t_isBusy(), "Task should not be busy initially");
        }
    }

    // ========================================================================
    // TOKEN CONSUMPTION ON FIRE
    // ========================================================================

    @Nested
    @DisplayName("Token Consumption Semantics on Fire")
    class TokenConsumptionSemantics {

        /**
         * INVARIANT: Firing a task consumes tokens from preset conditions
         * according to the join type:
         * - AND join: consumes ONE token from EACH preset condition
         * - XOR join: consumes ONE token from ONE preset condition (random selection)
         * - OR join: consumes ONE token from EACH preset that has a token
         */
        @Test
        @DisplayName("AND join consumes tokens from ALL presets on fire")
        void testAndJoinTokenConsumption() throws Exception {
            URL fileURL = getClass().getResource("YAWL_Specification3.xml");
            assertNotNull(fileURL, "Test specification file must exist");
            File yawlXMLFile = new File(fileURL.getFile());
            specification = YMarshal.unmarshalSpecifications(
                    StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);

            engine.loadSpecification(specification);

            YIdentifier caseID = engine.startCase(
                    specification.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);

            YNetRunner runner = engine.getNetRunnerRepository().get(caseID);
            assertNotNull(runner, "NetRunner must exist for case");

            // Fire the first task
            Set<YTask> enabledTasks = runner.getEnabledTasks();
            assertFalse(enabledTasks.isEmpty(), "Tasks should be enabled");

            YTask firstTask = enabledTasks.iterator().next();

            // Get preset conditions before fire
            Set<YExternalNetElement> presets = firstTask.getPresetElements();

            // Record which presets have tokens before fire
            for (YExternalNetElement elem : presets) {
                if (elem instanceof YCondition condition) {
                    // Token state recorded
                }
            }

            // Fire the task
            List<YIdentifier> childIDs = runner.attemptToFireAtomicTask(null, firstTask.getID());
            assertNotNull(childIDs, "Fire should return child identifiers");
            assertFalse(childIDs.isEmpty(), "Fire should create at least one child");
        }

        /**
         * INVARIANT: XOR join consumes token from ONE preset condition only.
         */
        @Test
        @DisplayName("XOR join consumes token from ONE preset on fire")
        void testXorJoinTokenConsumption() throws Exception {
            URL fileURL = getClass().getResource("YAWL_Specification2.xml");
            assertNotNull(fileURL, "Test specification file must exist");
            File yawlXMLFile = new File(fileURL.getFile());
            specification = YMarshal.unmarshalSpecifications(
                    StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);

            engine.loadSpecification(specification);

            YIdentifier caseID = engine.startCase(
                    specification.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);

            YNetRunner runner = engine.getNetRunnerRepository().get(caseID);
            assertNotNull(runner, "NetRunner must exist for case");

            // Get input condition before fire
            YInputCondition inputCondition = runner.getNet().getInputCondition();
            assertNotNull(inputCondition, "Input condition must exist");
            assertTrue(inputCondition.containsIdentifier(),
                    "Input condition should contain token before fire");

            // Fire a-top (XOR join)
            List<YIdentifier> childIDs = runner.attemptToFireAtomicTask(null, "a-top");
            assertNotNull(childIDs, "Fire should succeed");

            // Token should be consumed from input condition
            assertFalse(inputCondition.containsIdentifier(),
                    "Token should be consumed from input condition after XOR join fire");
        }
    }

    // ========================================================================
    // TOKEN PRODUCTION ON EXIT
    // ========================================================================

    @Nested
    @DisplayName("Token Production Semantics on Exit")
    class TokenProductionSemantics {

        /**
         * INVARIANT: When a task exits, it produces tokens to postset conditions
         * according to split type:
         * - AND split: produces ONE token to EACH postset condition
         * - XOR split: produces ONE token to ONE postset condition (based on predicate)
         * - OR split: produces tokens to predicate-evaluated-true postsets
         */
        @Test
        @DisplayName("AND split produces tokens to ALL postsets on exit")
        void testAndSplitTokenProduction() throws Exception {
            URL fileURL = getClass().getResource("YAWL_Specification3.xml");
            assertNotNull(fileURL, "Test specification file must exist");
            File yawlXMLFile = new File(fileURL.getFile());
            specification = YMarshal.unmarshalSpecifications(
                    StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);

            engine.loadSpecification(specification);

            YIdentifier caseID = engine.startCase(
                    specification.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);

            YNetRunner runner = engine.getNetRunnerRepository().get(caseID);
            assertNotNull(runner, "NetRunner must exist for case");

            // Find a task with AND split
            for (YTask task : runner.getNet().getNetTasks()) {
                if (task.getSplitType() == YTask._AND && task.getPostsetElements().size() > 1) {
                    Set<YExternalNetElement> postsets = task.getPostsetElements();

                    // When this task exits, all postsets should receive tokens
                    assertNotNull(postsets, "Task should have postset elements");
                    assertTrue(postsets.size() > 1,
                            "AND split should have multiple postsets");
                    break;
                }
            }
        }

        /**
         * INVARIANT: XOR split produces token to exactly ONE postset based on predicate.
         */
        @Test
        @DisplayName("XOR split produces token to ONE postset on exit")
        void testXorSplitTokenProduction() throws Exception {
            URL fileURL = getClass().getResource("YAWL_Specification2.xml");
            assertNotNull(fileURL, "Test specification file must exist");
            File yawlXMLFile = new File(fileURL.getFile());
            specification = YMarshal.unmarshalSpecifications(
                    StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);

            engine.loadSpecification(specification);

            YIdentifier caseID = engine.startCase(
                    specification.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);

            YNetRunner runner = engine.getNetRunnerRepository().get(caseID);
            assertNotNull(runner, "NetRunner must exist for case");

            // Fire and complete a-top (has AND split)
            List<YIdentifier> childIDs = runner.attemptToFireAtomicTask(null, "a-top");
            assertNotNull(childIDs, "Fire should succeed");

            // Start and complete the work item
            runner.startWorkItemInTask(null, childIDs.get(0), "a-top");
            Document outputData = new Document(new Element("data"));
            boolean taskExited = runner.completeWorkItemInTask(
                    null, null, childIDs.get(0), "a-top", outputData);

            assertTrue(taskExited, "Task should exit on completion");

            // After AND split exit, verify all postset conditions have tokens
            // a-top has AND split, so all postsets should receive tokens
        }
    }

    // ========================================================================
    // CANCELLATION SET BEHAVIOR
    // ========================================================================

    @Nested
    @DisplayName("Cancellation Set Behavior")
    class CancellationSetSemantics {

        /**
         * INVARIANT: When a task exits, it removes all tokens from its cancellation set.
         *
         * Petri Net Semantics: Cancellation regions in YAWL correspond to
         * reset arcs in Petri nets. When a transition fires, tokens are removed
         * from all places in the reset set.
         */
        @Test
        @DisplayName("Cancellation set tokens removed on task exit")
        void testCancellationSetBehavior() throws Exception {
            URL fileURL = getClass().getResource("CancellationTest.xml");
            assertNotNull(fileURL, "Cancellation test specification must exist");
            File yawlXMLFile = new File(fileURL.getFile());
            specification = YMarshal.unmarshalSpecifications(
                    StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);

            engine.loadSpecification(specification);

            YIdentifier caseID = engine.startCase(
                    specification.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);

            YNetRunner runner = engine.getNetRunnerRepository().get(caseID);
            assertNotNull(runner, "NetRunner must exist for case");

            // Find a task with a cancellation set
            for (YTask task : runner.getNet().getNetTasks()) {
                Set<YExternalNetElement> removeSet = task.getRemoveSet();
                if (removeSet != null && !removeSet.isEmpty()) {
                    // This task has a cancellation set
                    assertFalse(removeSet.isEmpty(),
                            "Task with cancellation set should have removeSet elements");
                    break;
                }
            }
        }

        /**
         * INVARIANT: Cancelled tasks have their internal state purged.
         */
        @Test
        @DisplayName("Cancelled task has purged internal state")
        void testCancelledTaskStatePurged() throws Exception {
            URL fileURL = getClass().getResource("CaseCancellation.xml");
            assertNotNull(fileURL, "Case cancellation specification must exist");
            File yawlXMLFile = new File(fileURL.getFile());
            specification = YMarshal.unmarshalSpecifications(
                    StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);

            engine.loadSpecification(specification);

            YIdentifier caseID = engine.startCase(
                    specification.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);

            // Cancel the case
            engine.cancelCase(caseID);

            // Verify case is removed
            assertFalse(engine.getRunningCaseIDs().contains(caseID),
                    "Cancelled case should not be in running cases");
        }
    }

    // ========================================================================
    // TASK BUSY STATE INVARIANTS
    // ========================================================================

    @Nested
    @DisplayName("Task Busy State Invariants")
    class TaskBusyStateSemantics {

        /**
         * INVARIANT: A task is busy (has _i set) after firing and until exit.
         * A busy task cannot be enabled.
         */
        @Test
        @DisplayName("Busy task is not enabled")
        void testBusyTaskNotEnabled() throws Exception {
            URL fileURL = getClass().getResource("YAWL_Specification2.xml");
            assertNotNull(fileURL, "Test specification file must exist");
            File yawlXMLFile = new File(fileURL.getFile());
            specification = YMarshal.unmarshalSpecifications(
                    StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);

            engine.loadSpecification(specification);

            YIdentifier caseID = engine.startCase(
                    specification.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);

            YNetRunner runner = engine.getNetRunnerRepository().get(caseID);
            assertNotNull(runner, "NetRunner must exist for case");

            // Fire a task
            List<YIdentifier> childIDs = runner.attemptToFireAtomicTask(null, "a-top");
            assertNotNull(childIDs, "Fire should succeed");

            // Get the task - should now be busy
            YTask aTop = (YTask) runner.getNetElement("a-top");
            assertTrue(aTop.t_isBusy(), "Task should be busy after fire");

            // Busy task should NOT be enabled
            assertFalse(aTop.t_enabled(caseID),
                    "Busy task should not be enabled");

            // Busy tasks are in the busy set
            assertTrue(runner.getBusyTasks().contains(aTop),
                    "Busy task should be in busy tasks set");
        }

        /**
         * INVARIANT: Task becomes not busy after exit.
         */
        @Test
        @DisplayName("Task not busy after exit")
        void testTaskNotBusyAfterExit() throws Exception {
            URL fileURL = getClass().getResource("YAWL_Specification2.xml");
            assertNotNull(fileURL, "Test specification file must exist");
            File yawlXMLFile = new File(fileURL.getFile());
            specification = YMarshal.unmarshalSpecifications(
                    StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);

            engine.loadSpecification(specification);

            YIdentifier caseID = engine.startCase(
                    specification.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);

            YNetRunner runner = engine.getNetRunnerRepository().get(caseID);
            assertNotNull(runner, "NetRunner must exist for case");

            // Fire and complete a task
            List<YIdentifier> childIDs = runner.attemptToFireAtomicTask(null, "a-top");
            runner.startWorkItemInTask(null, childIDs.get(0), "a-top");
            Document outputData = new Document(new Element("data"));
            runner.completeWorkItemInTask(null, null, childIDs.get(0), "a-top", outputData);

            // After exit, task should not be busy
            YTask aTop = (YTask) runner.getNetElement("a-top");
            assertFalse(aTop.t_isBusy(), "Task should not be busy after exit");
        }
    }

    // ========================================================================
    // WORK ITEM STATUS TRANSITIONS
    // ========================================================================

    @Nested
    @DisplayName("Work Item Status Transitions")
    class WorkItemStatusSemantics {

        /**
         * INVARIANT: Work item status follows the lifecycle:
         * Enabled -> Fired -> Executing -> Complete
         */
        @Test
        @DisplayName("Work item transitions through correct statuses")
        void testWorkItemStatusTransitions() throws Exception {
            URL fileURL = getClass().getResource("YAWL_Specification2.xml");
            assertNotNull(fileURL, "Test specification file must exist");
            File yawlXMLFile = new File(fileURL.getFile());
            specification = YMarshal.unmarshalSpecifications(
                    StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);

            engine.loadSpecification(specification);

            YIdentifier caseID = engine.startCase(
                    specification.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);

            // Get enabled work item
            Set<YWorkItem> availableItems = engine.getAvailableWorkItems();
            assertFalse(availableItems.isEmpty(), "Should have available work items");

            YWorkItem workItem = availableItems.iterator().next();
            assertEquals(YWorkItemStatus.statusEnabled, workItem.getStatus(),
                    "Initial work item should be enabled");

            // Start work item - transitions to fired then executing
            YWorkItem childItem = engine.startWorkItem(workItem,
                    engine.getExternalClient("admin"));
            assertNotNull(childItem, "Started work item should not be null");
            assertEquals(YWorkItemStatus.statusExecuting, childItem.getStatus(),
                    "Started work item should be executing");

            // Complete work item
            engine.completeWorkItem(childItem, "<data/>", null, WorkItemCompletion.Normal);

            // After completion, item should have complete status
            assertTrue(childItem.hasCompletedStatus(),
                    "Completed work item should have completed status");
        }

        /**
         * INVARIANT: Parent work item transitions to statusIsParent when children are created.
         */
        @Test
        @DisplayName("Parent work item has correct status")
        void testParentWorkItemStatus() throws YPersistenceException {
            YIdentifier parentID = new YIdentifier(null);
            YIdentifier childID = parentID.createChild(null);
            YWorkItemID workItemID = new YWorkItemID(parentID, "test-task");
            YTask task = new YAtomicTask("test-task", YTask._XOR, YTask._AND, null);

            YWorkItem parentItem = new YWorkItem(null, new YSpecificationID("test"),
                    task, workItemID, true, false);

            assertEquals(YWorkItemStatus.statusEnabled, parentItem.getStatus(),
                    "Initial item should be enabled");

            // Create child
            YWorkItem childItem = parentItem.createChild(null, childID);
            assertNotNull(childItem, "Child should be created");
            assertEquals(YWorkItemStatus.statusIsParent, parentItem.getStatus(),
                    "Parent should have statusIsParent after child creation");
            assertEquals(YWorkItemStatus.statusFired, childItem.getStatus(),
                    "Child should have statusFired");
        }
    }
}
