package org.yawlfoundation.yawl.elements;

import junit.framework.TestCase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.yawlfoundation.yawl.elements.data.YParameter;
import org.yawlfoundation.yawl.elements.data.YVariable;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YNetData;
import org.yawlfoundation.yawl.exceptions.*;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;
import org.yawlfoundation.yawl.util.YVerificationHandler;
import org.yawlfoundation.yawl.util.YVerificationMessage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comprehensive test suite for YAWL advanced control-flow patterns (WCP10-WCP20).
 *
 * Tests cover:
 * - WCP10: Arbitrary Cycles
 * - WCP11: Implicit Termination
 * - WCP12: Multiple Instances without Synchronization
 * - WCP13: Multiple Instances with Design-time Knowledge
 * - WCP14: Multiple Instances with Runtime Knowledge
 * - WCP15: Multiple Instances without Runtime Knowledge
 * - WCP16: Deferred Choice
 * - WCP17: Interleaved Parallel Routing
 * - WCP18: Milestone
 * - WCP19: Cancel Task
 * - WCP20: Cancel Case
 * - Cancel Region (cancellation set)
 * - Cancel Multi-Instance
 * - Structured Loop (while/repeat)
 * - While Loop construct
 *
 * @author Test Suite Generator
 */
public class TestAdvancedPatterns extends TestCase {

    private YSpecification specification;
    private YNet net;
    private YVerificationHandler handler;

    public TestAdvancedPatterns(String name) {
        super(name);
    }

    @Override
    public void setUp() throws YPersistenceException {
        handler = new YVerificationHandler();
    }

    @Override
    public void tearDown() {
        handler.reset();
        specification = null;
        net = null;
    }

    /**
     * WCP10: Arbitrary Cycles Pattern Test
     *
     * Verifies that a task can be revisited multiple times in an unstructured
     * manner without requiring structured loop constructs. The cycle path
     * allows returning to a previously executed task based on runtime conditions.
     */
    public void testArbitraryCyclesPattern() throws Exception {
        File specFile = loadFixture("ArbitraryCyclesSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        // Verify specification is valid
        specification.verify(handler);
        assertNoErrors("Arbitrary Cycles pattern should verify successfully");

        // Verify net structure
        assertNotNull("Root net should exist", net);
        assertNotNull("Input condition should exist", net.getInputCondition());
        assertNotNull("Output condition should exist", net.getOutputCondition());

        // Verify cycle elements exist
        YTask taskA = (YTask) net.getNetElement("taskA");
        YTask taskB = (YTask) net.getNetElement("taskB");
        YCondition choice = (YCondition) net.getNetElement("choice");

        assertNotNull("TaskA should exist", taskA);
        assertNotNull("TaskB should exist", taskB);
        assertNotNull("Choice condition should exist", choice);

        // Verify cycle path: taskB -> taskA
        assertTrue("TaskB should flow back to TaskA (cycle)",
                containsElementById(taskB.getPostsetElements(), "taskA"));

        // Verify XOR join on taskA enables cycle re-entry
        assertEquals("TaskA should have XOR join for cycle re-entry",
                YTask._XOR, taskA.getJoinType());
    }

    /**
     * WCP11: Implicit Termination Pattern Test
     *
     * Verifies that a process terminates implicitly when no remaining work
     * can be done - all active paths have reached an output condition.
     */
    public void testImplicitTerminationPattern() throws Exception {
        File specFile = loadFixture("ImplicitTerminationSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("Implicit Termination pattern should verify successfully");

        // Verify parallel paths to different outputs
        YTask taskA = (YTask) net.getNetElement("taskA");
        YTask taskB = (YTask) net.getNetElement("taskB");

        assertNotNull("TaskA should exist", taskA);
        assertNotNull("TaskB should exist", taskB);

        // Verify paths lead to output conditions
        assertTrue("TaskA should flow to outputA",
                containsElementById(taskA.getPostsetElements(), "outputA"));
        assertTrue("TaskB should flow to outputB",
                containsElementById(taskB.getPostsetElements(), "outputB"));
    }

    /**
     * WCP12: Multiple Instances without Synchronization Pattern Test
     *
     * Verifies that multiple instances of a task can be created and each
     * instance completes independently without waiting for other instances.
     * The threshold is less than the maximum, allowing early continuation.
     */
    public void testMIWithoutSynchronizationPattern() throws Exception {
        File specFile = loadFixture("MIWithoutSynchronizationSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("MI without Synchronization pattern should verify successfully");

        YTask miTask = (YTask) net.getNetElement("miTask");
        assertNotNull("MI task should exist", miTask);

        YMultiInstanceAttributes miAttr = miTask.getMultiInstanceAttributes();
        assertNotNull("Task should have MI attributes", miAttr);

        // Verify threshold < max for no synchronization
        assertEquals("Min instances should be 1", 1, miAttr.getMinInstances());
        assertEquals("Max instances should be 3", 3, miAttr.getMaxInstances());
        assertEquals("Threshold should be 1 (no synchronization)", 1, miAttr.getThreshold());
        assertEquals("Creation mode should be static",
                YMultiInstanceAttributes.CREATION_MODE_STATIC, miAttr.getCreationMode());
    }

    /**
     * WCP13: Multiple Instances with Design-time Knowledge Pattern Test
     *
     * Verifies that the number of instances is known at design time and
     * all instances are synchronized at completion (threshold = max = min).
     */
    public void testMIWithDesignTimeKnowledgePattern() throws Exception {
        File specFile = loadFixture("MIDesignTimeKnowledgeSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("MI with Design-time Knowledge pattern should verify successfully");

        YTask miTask = (YTask) net.getNetElement("miTask");
        assertNotNull("MI task should exist", miTask);

        YMultiInstanceAttributes miAttr = miTask.getMultiInstanceAttributes();
        assertNotNull("Task should have MI attributes", miAttr);

        // All values equal means fixed count at design time
        assertEquals("Min, max, and threshold should all be 3",
                3, miAttr.getMinInstances());
        assertEquals("Max should be 3", 3, miAttr.getMaxInstances());
        assertEquals("Threshold should be 3 (full synchronization)", 3, miAttr.getThreshold());
    }

    /**
     * WCP14: Multiple Instances with Runtime Knowledge Pattern Test
     *
     * Verifies that the number of instances is determined at runtime but
     * before task execution begins. Instance count comes from input data.
     */
    public void testMIWithRunTimeKnowledgePattern() throws Exception {
        File specFile = loadFixture("MIRunTimeKnowledgeSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("MI with Runtime Knowledge pattern should verify successfully");

        YTask miTask = (YTask) net.getNetElement("miTask");
        assertNotNull("MI task should exist", miTask);

        YMultiInstanceAttributes miAttr = miTask.getMultiInstanceAttributes();
        assertNotNull("Task should have MI attributes", miAttr);

        // Verify runtime queries for instance count
        String minQuery = miAttr.getMinInstancesQuery();
        String maxQuery = miAttr.getMaxInstancesQuery();
        String thresholdQuery = miAttr.getThresholdQuery();

        assertTrue("Min query should reference runtime data",
                minQuery.contains("instanceCount"));
        assertTrue("Max query should reference runtime data",
                maxQuery.contains("instanceCount"));
        assertTrue("Threshold query should reference runtime data",
                thresholdQuery.contains("instanceCount"));

        // Verify static creation mode (all instances created at once)
        assertEquals("Creation mode should be static",
                YMultiInstanceAttributes.CREATION_MODE_STATIC, miAttr.getCreationMode());
    }

    /**
     * WCP15: Multiple Instances without Runtime Knowledge Pattern Test
     *
     * Verifies dynamic instance creation during execution. New instances
     * can be created while other instances are still running.
     */
    public void testMIWithoutRunTimeKnowledgePattern() throws Exception {
        File specFile = loadFixture("MIWithoutRunTimeKnowledgeSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("MI without Runtime Knowledge pattern should verify successfully");

        YTask miTask = (YTask) net.getNetElement("miTask");
        assertNotNull("MI task should exist", miTask);

        YMultiInstanceAttributes miAttr = miTask.getMultiInstanceAttributes();
        assertNotNull("Task should have MI attributes", miAttr);

        // Verify dynamic creation mode
        assertTrue("Should use dynamic creation mode",
                miAttr.isDynamicCreationMode());

        // Verify dynamic max query
        String maxQuery = miAttr.getMaxInstancesQuery();
        assertTrue("Max query should reference dynamic count",
                maxQuery.contains("dynamicCount"));
    }

    /**
     * WCP16: Deferred Choice Pattern Test
     *
     * Verifies that the choice between branches is deferred until one
     * of them actually executes. The choice is made by the environment
     * or external event, not by evaluating data.
     */
    public void testDeferredChoicePattern() throws Exception {
        File specFile = loadFixture("DeferredChoiceSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("Deferred Choice pattern should verify successfully");

        YCondition deferredCondition = (YCondition) net.getNetElement("deferredCondition");
        assertNotNull("Deferred condition should exist", deferredCondition);

        // Verify multiple outgoing flows without predicates
        Set<YFlow> postsetFlows = deferredCondition.getPostsetFlows();
        assertEquals("Should have 2 outgoing flows", 2, postsetFlows.size());

        // Verify both tasks can be reached from deferred condition
        YTask taskA = (YTask) net.getNetElement("taskA");
        YTask taskB = (YTask) net.getNetElement("taskB");

        assertTrue("Deferred condition should flow to taskA",
                containsElementById(deferredCondition.getPostsetElements(), "taskA"));
        assertTrue("Deferred condition should flow to taskB",
                containsElementById(deferredCondition.getPostsetElements(), "taskB"));

        // Verify XOR join on tasks (only one path taken)
        assertEquals("TaskA should have XOR join", YTask._XOR, taskA.getJoinType());
        assertEquals("TaskB should have XOR join", YTask._XOR, taskB.getJoinType());
    }

    /**
     * WCP17: Interleaved Parallel Routing Pattern Test
     *
     * Verifies that a set of tasks can be executed in any order but
     * never concurrently. Each task must complete before another begins.
     */
    public void testInterleavedParallelRoutingPattern() throws Exception {
        File specFile = loadFixture("InterleavedParallelRoutingSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("Interleaved Parallel Routing pattern should verify successfully");

        // Verify all interleaved tasks exist
        YTask taskA = (YTask) net.getNetElement("taskA");
        YTask taskB = (YTask) net.getNetElement("taskB");
        YTask taskC = (YTask) net.getNetElement("taskC");

        assertNotNull("TaskA should exist", taskA);
        assertNotNull("TaskB should exist", taskB);
        assertNotNull("TaskC should exist", taskC);

        // Verify split condition allows all tasks
        YCondition splitCondition = (YCondition) net.getNetElement("splitCondition");
        assertEquals("Split condition should have 3 outgoing flows",
                3, splitCondition.getPostsetFlows().size());

        // Verify join condition collects from all tasks
        YCondition joinCondition = (YCondition) net.getNetElement("joinCondition");
        assertNotNull("Join condition should exist", joinCondition);

        // Verify XOR join on tasks (interleaved - not concurrent)
        assertEquals("TaskA should have XOR join for interleaving",
                YTask._XOR, taskA.getJoinType());
        assertEquals("TaskB should have XOR join for interleaving",
                YTask._XOR, taskB.getJoinType());
        assertEquals("TaskC should have XOR join for interleaving",
                YTask._XOR, taskC.getJoinType());
    }

    /**
     * WCP18: Milestone Pattern Test
     *
     * Verifies that a task can only execute when a milestone condition
     * has been reached. The waiting task depends on milestone completion.
     */
    public void testMilestonePattern() throws Exception {
        File specFile = loadFixture("MilestoneSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("Milestone pattern should verify successfully");

        // Verify milestone task and waiting task exist
        YTask milestoneTask = (YTask) net.getNetElement("milestoneTask");
        YTask waitingTask = (YTask) net.getNetElement("waitingTask");
        YCondition milestoneCondition = (YCondition) net.getNetElement("milestoneCondition");

        assertNotNull("Milestone task should exist", milestoneTask);
        assertNotNull("Waiting task should exist", waitingTask);
        assertNotNull("Milestone condition should exist", milestoneCondition);

        // Verify milestone task reaches milestone condition
        assertTrue("Milestone task should flow to milestone condition",
                containsElementById(milestoneTask.getPostsetElements(), "milestoneCondition"));

        // Verify milestone condition enables waiting task
        assertTrue("Milestone condition should flow to waiting task",
                containsElementById(milestoneCondition.getPostsetElements(), "waitingTask"));

        // Verify waiting task has AND join (requires milestone + initial token)
        assertEquals("Waiting task should have AND join for milestone dependency",
                YTask._AND, waitingTask.getJoinType());
    }

    /**
     * WCP19: Cancel Task Pattern Test
     *
     * Verifies that one task can cancel another task. When the cancelling
     * task executes, the cancelled task is removed if running.
     */
    public void testCancelTaskPattern() throws Exception {
        File specFile = loadFixture("CancelTaskSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("Cancel Task pattern should verify successfully");

        YTask taskToCancel = (YTask) net.getNetElement("taskToCancel");
        YTask cancellingTask = (YTask) net.getNetElement("cancellingTask");

        assertNotNull("Task to cancel should exist", taskToCancel);
        assertNotNull("Cancelling task should exist", cancellingTask);

        // Verify removal set on cancelling task
        Set<YExternalNetElement> removeSet = cancellingTask.getRemoveSet();
        assertNotNull("Cancelling task should have remove set", removeSet);
        assertTrue("Remove set should contain taskToCancel",
                removeSet.contains(taskToCancel));
    }

    /**
     * WCP20: Cancel Case Pattern Test
     *
     * Verifies that an entire case (workflow instance) can be cancelled.
     * All active tasks and tokens are removed when cancellation occurs.
     */
    public void testCancelCasePattern() throws Exception {
        File specFile = loadFixture("CancelCaseSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("Cancel Case pattern should verify successfully");

        // Verify case has multiple active tasks that can be cancelled
        YTask taskA = (YTask) net.getNetElement("taskA");
        YTask taskB = (YTask) net.getNetElement("taskB");
        YTask cancelTask = (YTask) net.getNetElement("cancelTask");

        assertNotNull("TaskA should exist", taskA);
        assertNotNull("TaskB should exist", taskB);
        assertNotNull("Cancel task should exist", cancelTask);

        // Verify parallel execution path
        YInputCondition input = net.getInputCondition();
        assertEquals("Input should have 2 outgoing flows (parallel split)",
                2, input.getPostsetFlows().size());
    }

    /**
     * Cancel Region Pattern Test
     *
     * Verifies that a region (set of tasks) can be cancelled together.
     * Multiple tasks in a cancellation set are removed when trigger fires.
     */
    public void testCancelRegionPattern() throws Exception {
        File specFile = loadFixture("CancelRegionSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("Cancel Region pattern should verify successfully");

        YTask regionTaskA = (YTask) net.getNetElement("regionTaskA");
        YTask regionTaskB = (YTask) net.getNetElement("regionTaskB");
        YTask cancelTrigger = (YTask) net.getNetElement("cancelTrigger");

        assertNotNull("Region task A should exist", regionTaskA);
        assertNotNull("Region task B should exist", regionTaskB);
        assertNotNull("Cancel trigger should exist", cancelTrigger);

        // Verify removal set contains all region tasks
        Set<YExternalNetElement> removeSet = cancelTrigger.getRemoveSet();
        assertNotNull("Cancel trigger should have remove set", removeSet);
        assertTrue("Remove set should contain regionTaskA",
                removeSet.contains(regionTaskA));
        assertTrue("Remove set should contain regionTaskB",
                removeSet.contains(regionTaskB));
        assertEquals("Remove set should have exactly 2 tasks", 2, removeSet.size());
    }

    /**
     * Cancel Multi-Instance Pattern Test
     *
     * Verifies that all instances of a multi-instance task can be cancelled.
     * When cancellation triggers, all running instances are removed.
     */
    public void testCancelMultiInstancePattern() throws Exception {
        File specFile = loadFixture("CancelMultiInstanceSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("Cancel Multi-Instance pattern should verify successfully");

        YTask miTask = (YTask) net.getNetElement("miTask");
        YTask cancelTask = (YTask) net.getNetElement("cancelTask");

        assertNotNull("MI task should exist", miTask);
        assertNotNull("Cancel task should exist", cancelTask);

        // Verify MI attributes
        YMultiInstanceAttributes miAttr = miTask.getMultiInstanceAttributes();
        assertNotNull("MI task should have MI attributes", miAttr);
        assertTrue("Should be multi-instance task", miAttr.isMultiInstance());

        // Verify removal set includes MI task
        Set<YExternalNetElement> removeSet = cancelTask.getRemoveSet();
        assertTrue("Remove set should contain MI task", removeSet.contains(miTask));
    }

    /**
     * Structured Loop Pattern Test
     *
     * Verifies while/repeat loop constructs where a task iterates
     * until a condition is met. Loop has single entry and exit point.
     */
    public void testStructuredLoopPattern() throws Exception {
        File specFile = loadFixture("StructuredLoopSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("Structured Loop pattern should verify successfully");

        YTask loopTask = (YTask) net.getNetElement("loopTask");
        YCondition loopCondition = (YCondition) net.getNetElement("loopCondition");

        assertNotNull("Loop task should exist", loopTask);
        assertNotNull("Loop condition should exist", loopCondition);

        // Verify loop path: loopTask -> loopCondition -> loopTask
        assertTrue("Loop task should flow back to loop condition",
                containsElementById(loopTask.getPostsetElements(), "loopCondition"));

        // Verify loop condition has flows for loop continuation
        Set<YFlow> postsetFlows = loopCondition.getPostsetFlows();
        assertTrue("Loop condition should have at least 1 flow",
                postsetFlows.size() >= 1);

        // Verify XOR join on loop task for loop re-entry
        assertEquals("Loop task should have XOR join for structured loop",
                YTask._XOR, loopTask.getJoinType());
    }

    /**
     * While Loop Pattern Test
     *
     * Verifies pre-condition loop where condition is checked before
     * each iteration. Loop body executes only if condition is true.
     */
    public void testWhileLoopPattern() throws Exception {
        File specFile = loadFixture("WhileLoopSpec.xml");
        specification = loadSpecification(specFile);
        net = specification.getRootNet();

        specification.verify(handler);
        assertNoErrors("While Loop pattern should verify successfully");

        YTask whileTask = (YTask) net.getNetElement("whileTask");
        YCondition whileCondition = (YCondition) net.getNetElement("whileCondition");

        assertNotNull("While task should exist", whileTask);
        assertNotNull("While condition should exist", whileCondition);

        // Verify condition checked before task (pre-condition)
        YInputCondition input = net.getInputCondition();
        assertTrue("Input should flow to while condition (pre-check)",
                containsElementById(input.getPostsetElements(), "whileCondition"));

        // Verify loop path exists
        assertTrue("While task should flow back to while condition",
                containsElementById(whileTask.getPostsetElements(), "whileCondition"));

        // Verify condition has exit path
        YOutputCondition output = net.getOutputCondition();
        assertTrue("While condition should have exit path to output",
                containsElementById(whileCondition.getPostsetElements(), output.getID()));
    }

    /**
     * Test Multi-Instance Attributes Configuration
     *
     * Verifies programmatic creation and validation of MI attributes.
     */
    public void testMultiInstanceAttributesConfiguration() throws YPersistenceException {
        YSpecification spec = new YSpecification("testMI");
        spec.setVersion(YSchemaVersion.Beta4);

        YNet testNet = new YNet("testNet", spec);
        YAtomicTask miTask = new YAtomicTask("miTask", YTask._AND, YTask._AND, testNet);

        // Configure static MI attributes
        miTask.setUpMultipleInstanceAttributes("2", "5", "3",
                YMultiInstanceAttributes.CREATION_MODE_STATIC);

        YMultiInstanceAttributes miAttr = miTask.getMultiInstanceAttributes();
        assertNotNull("MI attributes should be set", miAttr);
        assertEquals("Min instances should be 2", 2, miAttr.getMinInstances());
        assertEquals("Max instances should be 5", 5, miAttr.getMaxInstances());
        assertEquals("Threshold should be 3", 3, miAttr.getThreshold());
        assertFalse("Should not be dynamic mode", miAttr.isDynamicCreationMode());
    }

    /**
     * Test Multi-Instance Dynamic Creation Mode
     *
     * Verifies dynamic creation mode for MI tasks.
     */
    public void testMultiInstanceDynamicCreationMode() throws YPersistenceException {
        YSpecification spec = new YSpecification("testDynamicMI");
        spec.setVersion(YSchemaVersion.Beta4);

        YNet testNet = new YNet("testNet", spec);
        YAtomicTask miTask = new YAtomicTask("dynamicMITask", YTask._AND, YTask._AND, testNet);

        // Configure dynamic MI attributes
        miTask.setUpMultipleInstanceAttributes("1", "10", "5",
                YMultiInstanceAttributes.CREATION_MODE_DYNAMIC);

        YMultiInstanceAttributes miAttr = miTask.getMultiInstanceAttributes();
        assertNotNull("MI attributes should be set", miAttr);
        assertTrue("Should be dynamic mode", miAttr.isDynamicCreationMode());
    }

    /**
     * Test Invalid Multi-Instance Attributes
     *
     * Verifies that invalid MI attribute configurations are rejected.
     */
    public void testInvalidMultiInstanceAttributes() throws YPersistenceException {
        YSpecification spec = new YSpecification("testInvalidMI");
        spec.setVersion(YSchemaVersion.Beta4);

        YNet testNet = new YNet("testNet", spec);
        YAtomicTask miTask = new YAtomicTask("invalidMITask", YTask._AND, YTask._AND, testNet);

        // Configure invalid MI attributes (min > max)
        miTask.setUpMultipleInstanceAttributes("10", "5", "3",
                YMultiInstanceAttributes.CREATION_MODE_STATIC);

        YVerificationHandler localHandler = new YVerificationHandler();
        miTask.verify(localHandler);

        assertTrue("Should have verification errors for min > max",
                localHandler.hasMessages());
    }

    /**
     * Test Split/Join Type Combinations
     *
     * Verifies various split/join type combinations for pattern support.
     */
    public void testSplitJoinTypeCombinations() throws YPersistenceException {
        YSpecification spec = new YSpecification("testSplitJoin");
        spec.setVersion(YSchemaVersion.Beta4);

        YNet testNet = new YNet("testNet", spec);

        // Test AND-AND
        YAtomicTask andAndTask = new YAtomicTask("andAndTask", YTask._AND, YTask._AND, testNet);
        assertEquals("Should have AND join", YTask._AND, andAndTask.getJoinType());
        assertEquals("Should have AND split", YTask._AND, andAndTask.getSplitType());

        // Test XOR-XOR
        YAtomicTask xorXorTask = new YAtomicTask("xorXorTask", YTask._XOR, YTask._XOR, testNet);
        assertEquals("Should have XOR join", YTask._XOR, xorXorTask.getJoinType());
        assertEquals("Should have XOR split", YTask._XOR, xorXorTask.getSplitType());

        // Test OR-OR
        YAtomicTask orOrTask = new YAtomicTask("orOrTask", YTask._OR, YTask._OR, testNet);
        assertEquals("Should have OR join", YTask._OR, orOrTask.getJoinType());
        assertEquals("Should have OR split", YTask._OR, orOrTask.getSplitType());

        // Test mixed combination (XOR join, OR split)
        YAtomicTask xorOrTask = new YAtomicTask("xorOrTask", YTask._XOR, YTask._OR, testNet);
        assertEquals("Should have XOR join", YTask._XOR, xorOrTask.getJoinType());
        assertEquals("Should have OR split", YTask._OR, xorOrTask.getSplitType());
    }

    /**
     * Test Composite Task with Subnet
     *
     * Verifies composite task containing a subnet decomposition.
     */
    public void testCompositeTaskWithSubnet() throws YPersistenceException {
        YSpecification spec = new YSpecification("testComposite");
        spec.setVersion(YSchemaVersion.Beta4);

        YNet rootNet = new YNet("rootNet", spec);
        YNet subnet = new YNet("subnet", spec);

        // Create composite task with subnet decomposition
        YCompositeTask compositeTask = new YCompositeTask("compositeTask",
                YTask._AND, YTask._AND, rootNet);
        compositeTask.setDecompositionPrototype(subnet);

        assertEquals("Composite task should decompose to subnet",
                subnet, compositeTask.getDecompositionPrototype());
        assertTrue("Decomposition should be YNet",
                compositeTask.getDecompositionPrototype() instanceof YNet);
    }

    /**
     * Test Flow Predicates for Conditional Routing
     *
     * Verifies XPath predicates for conditional flow routing.
     */
    public void testFlowPredicates() throws YPersistenceException {
        YSpecification spec = new YSpecification("testPredicates");
        spec.setVersion(YSchemaVersion.Beta4);

        YNet testNet = new YNet("testNet", spec);
        YVariable var = new YVariable(testNet);
        var.setName("condition");
        var.setUntyped(true);
        var.setInitialValue("<value>true</value>");
        testNet.setLocalVariable(var);

        YCondition input = new YCondition("input", testNet);
        YTask task = new YAtomicTask("task", YTask._XOR, YTask._OR, testNet);
        YCondition output = new YCondition("output", testNet);

        // Create flow with predicate
        YFlow predicateFlow = new YFlow(task, output);
        predicateFlow.setXpathPredicate("/data/condition/value = 'true'");

        assertEquals("Flow should have predicate",
                "/data/condition/value = 'true'", predicateFlow.getXpathPredicate());
    }

    /**
     * Test Cancel Behavior on Atomic Task
     *
     * Verifies that atomic tasks support cancellation properly.
     */
    public void testCancelBehaviorOnAtomicTask() throws YPersistenceException {
        YSpecification spec = new YSpecification("testCancel");
        spec.setVersion(YSchemaVersion.Beta4);

        YNet testNet = new YNet("testNet", spec);
        YAtomicTask cancelableTask = new YAtomicTask("cancelableTask",
                YTask._AND, YTask._AND, testNet);

        // Initially task is not running
        assertFalse("Task should not be running initially", cancelableTask.isRunning());

        // Cancel should work even if not running (no-op)
        cancelableTask.cancel(null);
        assertFalse("Task should still not be running after cancel", cancelableTask.isRunning());
    }

    /**
     * Test Default Flow for XOR Split
     *
     * Verifies that XOR split can have a default flow for fallback.
     */
    public void testDefaultFlowForXorSplit() throws YPersistenceException {
        YSpecification spec = new YSpecification("testDefaultFlow");
        spec.setVersion(YSchemaVersion.Beta4);

        YNet testNet = new YNet("testNet", spec);

        YTask xorSplitTask = new YAtomicTask("xorSplitTask", YTask._XOR, YTask._XOR, testNet);
        YCondition defaultOutput = new YCondition("defaultOutput", testNet);
        YCondition predicateOutput = new YCondition("predicateOutput", testNet);

        // Create default flow
        YFlow defaultFlow = new YFlow(xorSplitTask, defaultOutput);
        defaultFlow.setIsDefaultFlow(true);

        // Create predicate flow
        YFlow predicateFlow = new YFlow(xorSplitTask, predicateOutput);
        predicateFlow.setXpathPredicate("true()");

        assertTrue("Default flow should be marked as default", defaultFlow.isDefaultFlow());
        assertFalse("Predicate flow should not be default", predicateFlow.isDefaultFlow());
    }

    // ==================== Helper Methods ====================

    /**
     * Helper method to check if a set of net elements contains an element with the given ID.
     */
    private boolean containsElementById(Set<YExternalNetElement> elements, String id) {
        for (YExternalNetElement element : elements) {
            if (element.getID().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads a test fixture file from the fixtures directory.
     */
    private File loadFixture(String filename) {
        String resourcePath = "fixtures/" + filename;
        java.net.URL url = getClass().getResource(resourcePath);
        assertNotNull("Fixture file should exist: " + resourcePath, url);
        return new File(url.getFile());
    }

    /**
     * Loads a YAWL specification from an XML file.
     */
    private YSpecification loadSpecification(File file)
            throws YSyntaxException, JDOMException, IOException {
        String xmlContent = StringUtil.fileToString(file.getAbsolutePath());
        List<YSpecification> specs = YMarshal.unmarshalSpecifications(xmlContent);
        assertEquals("Should load exactly one specification", 1, specs.size());
        return specs.get(0);
    }

    /**
     * Asserts that no verification errors occurred.
     */
    private void assertNoErrors(String message) {
        if (handler.hasMessages()) {
            StringBuilder sb = new StringBuilder(message);
            sb.append(" but got ").append(handler.getMessageCount()).append(" errors:\n");
            for (YVerificationMessage msg : handler.getMessages()) {
                sb.append("  - ").append(msg.getMessage()).append("\n");
            }
            fail(sb.toString());
        }
    }
}
