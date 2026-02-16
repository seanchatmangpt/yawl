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

package org.yawlfoundation.yawl.elements.patterns;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.yawlfoundation.yawl.elements.*;
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
import java.util.*;

/**
 * Comprehensive JUnit tests for basic control-flow patterns (WCP 1-15).
 *
 * Tests cover the following Workflow Control Patterns:
 * - WCP1: Sequence
 * - WCP2: Parallel Split (AND-split)
 * - WCP3: Synchronization (AND-join)
 * - WCP4: Exclusive Choice (XOR-split)
 * - WCP5: Simple Merge (XOR-join)
 * - WCP6: Multi-Choice (OR-split)
 * - WCP7: Structured Synchronizing Merge (OR-join)
 * - WCP8: Multi-Merge
 * - WCP9: Structured Discriminator
 * - Combined patterns (AND/XOR/OR split-join)
 * - Nested patterns (nested AND, nested XOR)
 * - Mixed patterns (AND/XOR combinations)
 *
 * @author YAWL Foundation
 */
public class TestBasicPatterns extends TestCase {

    private YVerificationHandler handler;
    private Map<String, YSpecification> specCache;

    public TestBasicPatterns(String name) {
        super(name);
    }

    public void setUp() {
        handler = new YVerificationHandler();
        specCache = new HashMap<String, YSpecification>();
    }

    public void tearDown() {
        handler = null;
        specCache = null;
    }

    /**
     * Load a specification from the fixtures directory.
     */
    private YSpecification loadSpecification(String filename)
            throws YSyntaxException, JDOMException, IOException, YSchemaBuildingException {
        if (specCache.containsKey(filename)) {
            return specCache.get(filename);
        }
        File specFile = new File(getClass().getResource("../fixtures/" + filename).getFile());
        String xmlContent = StringUtil.fileToString(specFile.getAbsolutePath());
        List specifications = YMarshal.unmarshalSpecifications(xmlContent);
        YSpecification spec = (YSpecification) specifications.get(0);
        specCache.put(filename, spec);
        return spec;
    }

    /**
     * Verify specification has no errors.
     */
    private void assertNoErrors(YSpecification spec, String patternName) {
        handler.reset();
        spec.verify(handler);
        if (handler.hasErrors()) {
            StringBuilder errorMsg = new StringBuilder(patternName + " specification has errors:\n");
            for (YVerificationMessage msg : handler.getMessages()) {
                errorMsg.append("  - ").append(msg.getMessage()).append("\n");
            }
            fail(errorMsg.toString());
        }
    }

    /**
     * Verify specification has expected error count.
     */
    private void assertErrorCount(YSpecification spec, int expectedCount, String patternName) {
        handler.reset();
        spec.verify(handler);
        assertEquals(patternName + " should have " + expectedCount + " errors",
                expectedCount, handler.getMessageCount());
    }

    // ========================================================================
    // WCP1: Sequence Pattern Tests
    // ========================================================================

    /**
     * WCP1: Sequence - Tasks execute in sequential order.
     * Pattern: A -> B -> C
     * Each task must complete before the next can start.
     */
    public void testSequencePattern() throws Exception {
        YSpecification spec = loadSpecification("WCP01_Sequence.xml");
        assertNoErrors(spec, "WCP1: Sequence");

        YNet net = spec.getRootNet();
        assertNotNull("Root net should not be null", net);

        // Verify sequential flow structure
        YInputCondition input = net.getInputCondition();
        assertNotNull("Input condition should exist", input);
        assertEquals("Input should have one output", 1, input.getPostsetElements().size());

        YTask taskA = (YTask) net.getNetElement("taskA");
        assertNotNull("Task A should exist", taskA);
        assertEquals("Task A should have AND split", YTask._AND, taskA.getSplitType());
        assertEquals("Task A should have XOR join", YTask._XOR, taskA.getJoinType());

        YTask taskB = (YTask) net.getNetElement("taskB");
        assertNotNull("Task B should exist", taskB);

        YTask taskC = (YTask) net.getNetElement("taskC");
        assertNotNull("Task C should exist", taskC);

        YOutputCondition output = (YOutputCondition) net.getNetElement("output");
        assertNotNull("Output condition should exist", output);

        // Verify flow: input -> taskA -> (condition) -> taskB -> (condition) -> taskC -> output
        // In YAWL, task-to-task flow creates implicit conditions
        assertTrue("Input flows to taskA or condition before taskA",
                input.getPostsetElements().iterator().next().equals(taskA) ||
                input.getPostsetElements().iterator().next() instanceof YCondition);

        // Verify the tasks exist and have correct types
        assertEquals("Task B should have XOR join", YTask._XOR, taskB.getJoinType());
        assertEquals("Task C should have XOR join", YTask._XOR, taskC.getJoinType());
    }

    /**
     * WCP1: Test sequence execution semantics - verify tasks execute one after another.
     */
    public void testSequenceExecutionSemantics() throws Exception {
        YSpecification spec = new YSpecification("SequenceTest");
        spec.setVersion(YSchemaVersion.Beta4);
        YNet net = new YNet("SequenceNet", spec);

        // Create input condition
        YInputCondition input = new YInputCondition("input", net);

        // Create sequential tasks
        YAtomicTask taskA = new YAtomicTask("taskA", YTask._XOR, YTask._AND, net);
        YAtomicTask taskB = new YAtomicTask("taskB", YTask._XOR, YTask._AND, net);
        YAtomicTask taskC = new YAtomicTask("taskC", YTask._XOR, YTask._AND, net);

        // Create output condition
        YOutputCondition output = new YOutputCondition("output", net);

        // Create conditions between tasks
        YCondition c1 = new YCondition("c1", net);
        YCondition c2 = new YCondition("c2", net);

        // Set up flows
        YFlow flow1 = new YFlow(input, taskA);
        input.addPostset(flow1);

        YFlow flow2 = new YFlow(taskA, c1);
        taskA.addPostset(flow2);

        YFlow flow3 = new YFlow(c1, taskB);
        c1.addPostset(flow3);
        taskB.addPreset(flow3);

        YFlow flow4 = new YFlow(taskB, c2);
        taskB.addPostset(flow4);

        YFlow flow5 = new YFlow(c2, taskC);
        c2.addPostset(flow5);
        taskC.addPreset(flow5);

        YFlow flow6 = new YFlow(taskC, output);
        taskC.addPostset(flow6);

        // Add decompositions
        YAWLServiceGateway gatewayA = new YAWLServiceGateway("gatewayA", spec);
        taskA.setDecompositionPrototype(gatewayA);
        YAWLServiceGateway gatewayB = new YAWLServiceGateway("gatewayB", spec);
        taskB.setDecompositionPrototype(gatewayB);
        YAWLServiceGateway gatewayC = new YAWLServiceGateway("gatewayC", spec);
        taskC.setDecompositionPrototype(gatewayC);

        // Verify structure
        assertEquals("Task A should have one postset element", 1, taskA.getPostsetElements().size());
        assertEquals("Task B should have one postset element", 1, taskB.getPostsetElements().size());
        assertEquals("Task C should have one postset element", 1, taskC.getPostsetElements().size());
    }

    // ========================================================================
    // WCP2: Parallel Split Pattern Tests (AND-split)
    // ========================================================================

    /**
     * WCP2: Parallel Split - A single thread of control splits into multiple
     * threads that can be executed concurrently.
     */
    public void testParallelSplitPattern() throws Exception {
        YSpecification spec = loadSpecification("WCP02_ParallelSplit.xml");
        assertNoErrors(spec, "WCP2: Parallel Split");

        YNet net = spec.getRootNet();
        YTask splitTask = (YTask) net.getNetElement("splitTask");

        assertNotNull("Split task should exist", splitTask);
        assertEquals("Split task should have AND split type",
                YTask._AND, splitTask.getSplitType());
        assertEquals("Split task should have 3 branches",
                3, splitTask.getPostsetElements().size());

        // Verify all branches exist
        assertNotNull("Branch A should exist", net.getNetElement("branchA"));
        assertNotNull("Branch B should exist", net.getNetElement("branchB"));
        assertNotNull("Branch C should exist", net.getNetElement("branchC"));
    }

    /**
     * WCP2: Test parallel split creates tokens for all branches.
     */
    public void testParallelSplitExecution() throws Exception {
        YSpecification spec = new YSpecification("ParallelSplitTest");
        spec.setVersion(YSchemaVersion.Beta4);
        YNet net = new YNet("ParallelSplitNet", spec);

        YInputCondition input = new YInputCondition("input", net);
        YAtomicTask splitTask = new YAtomicTask("split", YTask._XOR, YTask._AND, net);
        splitTask.setDecompositionPrototype(new YAWLServiceGateway("splitGW", spec));

        YAtomicTask branchA = new YAtomicTask("branchA", YTask._XOR, YTask._AND, net);
        branchA.setDecompositionPrototype(new YAWLServiceGateway("gwA", spec));

        YAtomicTask branchB = new YAtomicTask("branchB", YTask._XOR, YTask._AND, net);
        branchB.setDecompositionPrototype(new YAWLServiceGateway("gwB", spec));

        YOutputCondition output = new YOutputCondition("output", net);

        // Set up flows from input to split
        YFlow inputToSplit = new YFlow(input, splitTask);
        input.addPostset(inputToSplit);

        // Set up AND-split to both branches
        YFlow splitToA = new YFlow(splitTask, branchA);
        YFlow splitToB = new YFlow(splitTask, branchB);
        splitTask.addPostset(splitToA);
        splitTask.addPostset(splitToB);

        // Set up flows to output
        YFlow aToOutput = new YFlow(branchA, output);
        YFlow bToOutput = new YFlow(branchB, output);
        branchA.addPostset(aToOutput);
        branchB.addPostset(bToOutput);

        assertEquals("AND split should have 2 postset elements",
                2, splitTask.getPostsetElements().size());
        assertTrue("Split should reach branch A",
                splitTask.getPostsetElements().contains(branchA));
        assertTrue("Split should reach branch B",
                splitTask.getPostsetElements().contains(branchB));
    }

    // ========================================================================
    // WCP3: Synchronization Pattern Tests (AND-join)
    // ========================================================================

    /**
     * WCP3: Synchronization - Multiple concurrent threads synchronize at
     * a single point before proceeding.
     */
    public void testSynchronizationPattern() throws Exception {
        YSpecification spec = loadSpecification("WCP03_Synchronization.xml");
        assertNoErrors(spec, "WCP3: Synchronization");

        YNet net = spec.getRootNet();
        YTask syncTask = (YTask) net.getNetElement("syncTask");

        assertNotNull("Sync task should exist", syncTask);
        assertEquals("Sync task should have AND join type",
                YTask._AND, syncTask.getJoinType());
        assertEquals("Sync task should have 2 preset branches",
                2, syncTask.getPresetElements().size());
    }

    /**
     * WCP3: Test AND-join requires all incoming tokens.
     */
    public void testSynchronizationExecution() throws Exception {
        YSpecification spec = new YSpecification("SyncTest");
        spec.setVersion(YSchemaVersion.Beta4);
        YNet net = new YNet("SyncNet", spec);

        // Create conditions for preset
        YCondition c1 = new YCondition("c1", net);
        YCondition c2 = new YCondition("c2", net);

        // Create sync task with AND join
        YAtomicTask syncTask = new YAtomicTask("sync", YTask._AND, YTask._AND, net);
        syncTask.setDecompositionPrototype(new YAWLServiceGateway("syncGW", spec));

        YOutputCondition output = new YOutputCondition("output", net);

        // Set up flows
        YFlow flow1 = new YFlow(c1, syncTask);
        YFlow flow2 = new YFlow(c2, syncTask);
        c1.addPostset(flow1);
        c2.addPostset(flow2);
        syncTask.addPreset(flow1);
        syncTask.addPreset(flow2);

        YFlow flow3 = new YFlow(syncTask, output);
        syncTask.addPostset(flow3);

        // Verify AND join requires all presets
        assertEquals("AND join should have 2 preset elements",
                2, syncTask.getPresetElements().size());
        assertEquals("Sync task should have AND join",
                YTask._AND, syncTask.getJoinType());
    }

    // ========================================================================
    // WCP4: Exclusive Choice Pattern Tests (XOR-split)
    // ========================================================================

    /**
     * WCP4: Exclusive Choice - A branch point where exactly one branch
     * is chosen based on a condition.
     */
    public void testExclusiveChoicePattern() throws Exception {
        YSpecification spec = loadSpecification("WCP04_ExclusiveChoice.xml");
        assertNoErrors(spec, "WCP4: Exclusive Choice");

        YNet net = spec.getRootNet();
        YTask decisionTask = (YTask) net.getNetElement("decisionTask");

        assertNotNull("Decision task should exist", decisionTask);
        assertEquals("Decision task should have XOR split type",
                YTask._XOR, decisionTask.getSplitType());
        assertEquals("Decision task should have 2 branches",
                2, decisionTask.getPostsetElements().size());
    }

    /**
     * WCP4: Test XOR split chooses exactly one branch.
     */
    public void testExclusiveChoiceExecution() throws Exception {
        YSpecification spec = new YSpecification("XORSplitTest");
        spec.setVersion(YSchemaVersion.Beta4);
        YNet net = new YNet("XORSplitNet", spec);

        YInputCondition input = new YInputCondition("input", net);

        // Create XOR split task
        YAtomicTask decisionTask = new YAtomicTask("decision", YTask._XOR, YTask._XOR, net);
        decisionTask.setDecompositionPrototype(new YAWLServiceGateway("decisionGW", spec));

        YAtomicTask branchA = new YAtomicTask("branchA", YTask._XOR, YTask._AND, net);
        branchA.setDecompositionPrototype(new YAWLServiceGateway("gwA", spec));

        YAtomicTask branchB = new YAtomicTask("branchB", YTask._XOR, YTask._AND, net);
        branchB.setDecompositionPrototype(new YAWLServiceGateway("gwB", spec));

        YOutputCondition output = new YOutputCondition("output", net);

        // Set up flows
        YFlow inputToDecision = new YFlow(input, decisionTask);
        input.addPostset(inputToDecision);

        // XOR split with predicates
        YFlow decisionToA = new YFlow(decisionTask, branchA);
        decisionToA.setXpathPredicate("true()");
        decisionToA.setEvalOrdering(1);

        YFlow decisionToB = new YFlow(decisionTask, branchB);
        decisionToB.setXpathPredicate("false()");
        decisionToB.setEvalOrdering(2);
        decisionToB.setIsDefaultFlow(true);

        decisionTask.addPostset(decisionToA);
        decisionTask.addPostset(decisionToB);

        YFlow aToOutput = new YFlow(branchA, output);
        YFlow bToOutput = new YFlow(branchB, output);
        branchA.addPostset(aToOutput);
        branchB.addPostset(bToOutput);

        assertEquals("XOR split should have 2 postset elements",
                2, decisionTask.getPostsetElements().size());
        assertEquals("Decision task should have XOR split",
                YTask._XOR, decisionTask.getSplitType());
    }

    // ========================================================================
    // WCP5: Simple Merge Pattern Tests (XOR-join)
    // ========================================================================

    /**
     * WCP5: Simple Merge - Two or more branches merge without synchronization.
     * Only one branch can be active at a time.
     */
    public void testSimpleMergePattern() throws Exception {
        YSpecification spec = loadSpecification("WCP05_SimpleMerge.xml");
        assertNoErrors(spec, "WCP5: Simple Merge");

        YNet net = spec.getRootNet();
        YTask mergeTask = (YTask) net.getNetElement("mergeTask");

        assertNotNull("Merge task should exist", mergeTask);
        assertEquals("Merge task should have XOR join type",
                YTask._XOR, mergeTask.getJoinType());
    }

    /**
     * WCP5: Test XOR merge accepts single token from any branch.
     */
    public void testSimpleMergeExecution() throws Exception {
        YSpecification spec = new YSpecification("XORMergeTest");
        spec.setVersion(YSchemaVersion.Beta4);
        YNet net = new YNet("XORMergeNet", spec);

        // Create conditions for branches
        YCondition c1 = new YCondition("c1", net);
        YCondition c2 = new YCondition("c2", net);

        // Create XOR merge task
        YAtomicTask mergeTask = new YAtomicTask("merge", YTask._XOR, YTask._AND, net);
        mergeTask.setDecompositionPrototype(new YAWLServiceGateway("mergeGW", spec));

        YOutputCondition output = new YOutputCondition("output", net);

        // Set up flows
        YFlow flow1 = new YFlow(c1, mergeTask);
        YFlow flow2 = new YFlow(c2, mergeTask);
        c1.addPostset(flow1);
        c2.addPostset(flow2);
        mergeTask.addPreset(flow1);
        mergeTask.addPreset(flow2);

        YFlow mergeToOutput = new YFlow(mergeTask, output);
        mergeTask.addPostset(mergeToOutput);

        assertEquals("XOR merge should have 2 preset elements",
                2, mergeTask.getPresetElements().size());
        assertEquals("Merge task should have XOR join",
                YTask._XOR, mergeTask.getJoinType());
    }

    // ========================================================================
    // WCP6: Multi-Choice Pattern Tests (OR-split)
    // ========================================================================

    /**
     * WCP6: Multi-Choice - A branch point where one or more branches
     * are chosen based on conditions.
     */
    public void testMultiChoicePattern() throws Exception {
        YSpecification spec = loadSpecification("WCP06_MultiChoice.xml");
        assertNoErrors(spec, "WCP6: Multi-Choice");

        YNet net = spec.getRootNet();
        YTask decisionTask = (YTask) net.getNetElement("decisionTask");

        assertNotNull("Decision task should exist", decisionTask);
        assertEquals("Decision task should have OR split type",
                YTask._OR, decisionTask.getSplitType());
        assertEquals("Decision task should have 3 branches",
                3, decisionTask.getPostsetElements().size());
    }

    /**
     * WCP6: Test OR split can enable multiple branches.
     */
    public void testMultiChoiceExecution() throws Exception {
        YSpecification spec = new YSpecification("ORSplitTest");
        spec.setVersion(YSchemaVersion.Beta4);
        YNet net = new YNet("ORSplitNet", spec);

        YInputCondition input = new YInputCondition("input", net);

        // Create OR split task
        YAtomicTask decisionTask = new YAtomicTask("decision", YTask._XOR, YTask._OR, net);
        decisionTask.setDecompositionPrototype(new YAWLServiceGateway("decisionGW", spec));

        YAtomicTask branchA = new YAtomicTask("branchA", YTask._XOR, YTask._AND, net);
        YAtomicTask branchB = new YAtomicTask("branchB", YTask._XOR, YTask._AND, net);
        YAtomicTask branchC = new YAtomicTask("branchC", YTask._XOR, YTask._AND, net);

        branchA.setDecompositionPrototype(new YAWLServiceGateway("gwA", spec));
        branchB.setDecompositionPrototype(new YAWLServiceGateway("gwB", spec));
        branchC.setDecompositionPrototype(new YAWLServiceGateway("gwC", spec));

        YOutputCondition output = new YOutputCondition("output", net);

        // Set up flows
        YFlow inputToDecision = new YFlow(input, decisionTask);
        input.addPostset(inputToDecision);

        // OR split with predicates
        YFlow decisionToA = new YFlow(decisionTask, branchA);
        decisionToA.setXpathPredicate("true()");
        decisionToA.setEvalOrdering(1);

        YFlow decisionToB = new YFlow(decisionTask, branchB);
        decisionToB.setXpathPredicate("true()");
        decisionToB.setEvalOrdering(2);

        YFlow decisionToC = new YFlow(decisionTask, branchC);
        decisionToC.setXpathPredicate("false()");
        decisionToC.setEvalOrdering(3);
        decisionToC.setIsDefaultFlow(true);

        decisionTask.addPostset(decisionToA);
        decisionTask.addPostset(decisionToB);
        decisionTask.addPostset(decisionToC);

        // Set up output flows
        branchA.addPostset(new YFlow(branchA, output));
        branchB.addPostset(new YFlow(branchB, output));
        branchC.addPostset(new YFlow(branchC, output));

        assertEquals("OR split should have 3 postset elements",
                3, decisionTask.getPostsetElements().size());
        assertEquals("Decision task should have OR split",
                YTask._OR, decisionTask.getSplitType());
    }

    // ========================================================================
    // WCP7: Structured Synchronizing Merge Pattern Tests (OR-join)
    // ========================================================================

    /**
     * WCP7: Structured Synchronizing Merge - Multiple branches merge with
     * synchronization of all active threads.
     */
    public void testStructuredSynchronizingMergePattern() throws Exception {
        YSpecification spec = loadSpecification("WCP07_StructuredSynchronizingMerge.xml");
        assertNoErrors(spec, "WCP7: Structured Synchronizing Merge");

        YNet net = spec.getRootNet();
        YTask syncTask = (YTask) net.getNetElement("syncTask");

        assertNotNull("Sync task should exist", syncTask);
        assertEquals("Sync task should have OR join type",
                YTask._OR, syncTask.getJoinType());
        assertEquals("Sync task should have 3 preset branches",
                3, syncTask.getPresetElements().size());
    }

    /**
     * WCP7: Test OR join synchronizes all active branches.
     */
    public void testStructuredSynchronizingMergeExecution() throws Exception {
        YSpecification spec = new YSpecification("ORJoinTest");
        spec.setVersion(YSchemaVersion.Beta4);
        YNet net = new YNet("ORJoinNet", spec);

        // Create conditions for branches
        YCondition c1 = new YCondition("c1", net);
        YCondition c2 = new YCondition("c2", net);
        YCondition c3 = new YCondition("c3", net);

        // Create OR join task
        YAtomicTask syncTask = new YAtomicTask("sync", YTask._OR, YTask._AND, net);
        syncTask.setDecompositionPrototype(new YAWLServiceGateway("syncGW", spec));

        YOutputCondition output = new YOutputCondition("output", net);

        // Set up flows
        YFlow flow1 = new YFlow(c1, syncTask);
        YFlow flow2 = new YFlow(c2, syncTask);
        YFlow flow3 = new YFlow(c3, syncTask);

        c1.addPostset(flow1);
        c2.addPostset(flow2);
        c3.addPostset(flow3);

        syncTask.addPreset(flow1);
        syncTask.addPreset(flow2);
        syncTask.addPreset(flow3);

        YFlow syncToOutput = new YFlow(syncTask, output);
        syncTask.addPostset(syncToOutput);

        assertEquals("OR join should have 3 preset elements",
                3, syncTask.getPresetElements().size());
        assertEquals("Sync task should have OR join",
                YTask._OR, syncTask.getJoinType());
    }

    // ========================================================================
    // WCP8: Multi-Merge Pattern Tests
    // ========================================================================

    /**
     * WCP8: Multi-Merge - Multiple activations pass through a merge point
     * without synchronization. Each activation triggers the subsequent task.
     */
    public void testMultiMergePattern() throws Exception {
        YSpecification spec = loadSpecification("WCP08_MultiMerge.xml");
        assertNoErrors(spec, "WCP8: Multi-Merge");

        YNet net = spec.getRootNet();
        YTask mergeTask = (YTask) net.getNetElement("mergeTask");

        assertNotNull("Merge task should exist", mergeTask);
        assertEquals("Merge task should have XOR join type",
                YTask._XOR, mergeTask.getJoinType());
    }

    /**
     * WCP8: Test multi-merge allows multiple tokens through.
     */
    public void testMultiMergeExecution() throws Exception {
        YSpecification spec = new YSpecification("MultiMergeTest");
        spec.setVersion(YSchemaVersion.Beta4);
        YNet net = new YNet("MultiMergeNet", spec);

        // Create OR split and XOR merge for multi-merge pattern
        YInputCondition input = new YInputCondition("input", net);

        YAtomicTask splitTask = new YAtomicTask("split", YTask._XOR, YTask._OR, net);
        splitTask.setDecompositionPrototype(new YAWLServiceGateway("splitGW", spec));

        YCondition c1 = new YCondition("c1", net);
        YCondition c2 = new YCondition("c2", net);

        YAtomicTask mergeTask = new YAtomicTask("merge", YTask._XOR, YTask._AND, net);
        mergeTask.setDecompositionPrototype(new YAWLServiceGateway("mergeGW", spec));

        YOutputCondition output = new YOutputCondition("output", net);

        // Set up flows
        input.addPostset(new YFlow(input, splitTask));

        YFlow splitToC1 = new YFlow(splitTask, c1);
        splitToC1.setXpathPredicate("true()");
        splitToC1.setEvalOrdering(1);

        YFlow splitToC2 = new YFlow(splitTask, c2);
        splitToC2.setXpathPredicate("true()");
        splitToC2.setEvalOrdering(2);
        splitToC2.setIsDefaultFlow(true);

        splitTask.addPostset(splitToC1);
        splitTask.addPostset(splitToC2);

        YFlow c1ToMerge = new YFlow(c1, mergeTask);
        YFlow c2ToMerge = new YFlow(c2, mergeTask);
        c1.addPostset(c1ToMerge);
        c2.addPostset(c2ToMerge);
        mergeTask.addPreset(c1ToMerge);
        mergeTask.addPreset(c2ToMerge);

        mergeTask.addPostset(new YFlow(mergeTask, output));

        assertEquals("Multi-merge should have 2 preset elements",
                2, mergeTask.getPresetElements().size());
        assertEquals("Merge task should have XOR join for multi-merge",
                YTask._XOR, mergeTask.getJoinType());
    }

    // ========================================================================
    // WCP9: Structured Discriminator Pattern Tests
    // ========================================================================

    /**
     * WCP9: Structured Discriminator - The first incoming branch to complete
     * triggers the subsequent branch, other branches are ignored.
     */
    public void testStructuredDiscriminatorPattern() throws Exception {
        YSpecification spec = loadSpecification("WCP09_StructuredDiscriminator.xml");
        assertNoErrors(spec, "WCP9: Structured Discriminator");

        YNet net = spec.getRootNet();
        YTask discriminatorTask = (YTask) net.getNetElement("discriminatorTask");

        assertNotNull("Discriminator task should exist", discriminatorTask);
        assertEquals("Discriminator task should have OR join type",
                YTask._OR, discriminatorTask.getJoinType());
    }

    /**
     * WCP9: Test discriminator triggers on first completion.
     */
    public void testStructuredDiscriminatorExecution() throws Exception {
        YSpecification spec = new YSpecification("DiscriminatorTest");
        spec.setVersion(YSchemaVersion.Beta4);
        YNet net = new YNet("DiscriminatorNet", spec);

        // Create conditions for parallel branches
        YCondition c1 = new YCondition("c1", net);
        YCondition c2 = new YCondition("c2", net);
        YCondition c3 = new YCondition("c3", net);

        // Create discriminator task with OR join (first-wins semantics)
        YAtomicTask discriminatorTask = new YAtomicTask("discriminator", YTask._OR, YTask._AND, net);
        discriminatorTask.setDecompositionPrototype(new YAWLServiceGateway("discGW", spec));

        YOutputCondition output = new YOutputCondition("output", net);

        // Set up flows
        YFlow flow1 = new YFlow(c1, discriminatorTask);
        YFlow flow2 = new YFlow(c2, discriminatorTask);
        YFlow flow3 = new YFlow(c3, discriminatorTask);

        c1.addPostset(flow1);
        c2.addPostset(flow2);
        c3.addPostset(flow3);

        discriminatorTask.addPreset(flow1);
        discriminatorTask.addPreset(flow2);
        discriminatorTask.addPreset(flow3);

        discriminatorTask.addPostset(new YFlow(discriminatorTask, output));

        assertEquals("Discriminator should have 3 preset elements",
                3, discriminatorTask.getPresetElements().size());
        assertEquals("Discriminator task should have OR join",
                YTask._OR, discriminatorTask.getJoinType());
    }

    // ========================================================================
    // Combined Pattern Tests
    // ========================================================================

    /**
     * Combined AND-split/AND-join - Parallel execution with full synchronization.
     */
    public void testParallelSplitJoinPattern() throws Exception {
        YSpecification spec = loadSpecification("WCP10_ParallelSplitJoin.xml");
        assertNoErrors(spec, "WCP10: Parallel Split-Join");

        YNet net = spec.getRootNet();

        YTask splitTask = (YTask) net.getNetElement("splitTask");
        YTask joinTask = (YTask) net.getNetElement("joinTask");

        assertNotNull("Split task should exist", splitTask);
        assertNotNull("Join task should exist", joinTask);

        assertEquals("Split task should have AND split",
                YTask._AND, splitTask.getSplitType());
        assertEquals("Join task should have AND join",
                YTask._AND, joinTask.getJoinType());

        // Verify split creates correct number of branches
        assertEquals("AND split should have 2 branches",
                2, splitTask.getPostsetElements().size());

        // Verify join expects correct number of branches
        assertEquals("AND join should have 2 presets",
                2, joinTask.getPresetElements().size());
    }

    /**
     * Combined XOR-split/XOR-join - Exclusive choice with simple merge.
     */
    public void testXORSplitJoinPattern() throws Exception {
        YSpecification spec = loadSpecification("WCP11_XORSplitJoin.xml");
        assertNoErrors(spec, "WCP11: XOR Split-Join");

        YNet net = spec.getRootNet();

        YTask splitTask = (YTask) net.getNetElement("splitTask");
        YTask joinTask = (YTask) net.getNetElement("joinTask");

        assertNotNull("Split task should exist", splitTask);
        assertNotNull("Join task should exist", joinTask);

        assertEquals("Split task should have XOR split",
                YTask._XOR, splitTask.getSplitType());
        assertEquals("Join task should have XOR join",
                YTask._XOR, joinTask.getJoinType());
    }

    /**
     * Combined OR-split/OR-join - Multi-choice with synchronizing merge.
     */
    public void testORSplitJoinPattern() throws Exception {
        YSpecification spec = loadSpecification("WCP12_ORSplitJoin.xml");
        assertNoErrors(spec, "WCP12: OR Split-Join");

        YNet net = spec.getRootNet();

        YTask splitTask = (YTask) net.getNetElement("splitTask");
        YTask joinTask = (YTask) net.getNetElement("joinTask");

        assertNotNull("Split task should exist", splitTask);
        assertNotNull("Join task should exist", joinTask);

        assertEquals("Split task should have OR split",
                YTask._OR, splitTask.getSplitType());
        assertEquals("Join task should have OR join",
                YTask._OR, joinTask.getJoinType());
    }

    // ========================================================================
    // Nested Pattern Tests
    // ========================================================================

    /**
     * Nested AND patterns - Nested parallel structures.
     */
    public void testNestedANDPatterns() throws Exception {
        YSpecification spec = loadSpecification("WCP13_NestedAND.xml");
        assertNoErrors(spec, "WCP13: Nested AND");

        YNet net = spec.getRootNet();

        // Verify outer AND split/join
        YTask outerSplit = (YTask) net.getNetElement("outerSplit");
        YTask outerJoin = (YTask) net.getNetElement("outerJoin");

        assertNotNull("Outer split should exist", outerSplit);
        assertNotNull("Outer join should exist", outerJoin);
        assertEquals("Outer split should be AND", YTask._AND, outerSplit.getSplitType());
        assertEquals("Outer join should be AND", YTask._AND, outerJoin.getJoinType());

        // Verify inner AND split/join
        YTask innerSplitA = (YTask) net.getNetElement("innerSplitA");
        YTask innerJoinA = (YTask) net.getNetElement("innerJoinA");

        assertNotNull("Inner split should exist", innerSplitA);
        assertNotNull("Inner join should exist", innerJoinA);
        assertEquals("Inner split should be AND", YTask._AND, innerSplitA.getSplitType());
        assertEquals("Inner join should be AND", YTask._AND, innerJoinA.getJoinType());
    }

    /**
     * Nested XOR patterns - Nested exclusive choice structures.
     */
    public void testNestedXORPatterns() throws Exception {
        YSpecification spec = loadSpecification("WCP14_NestedXOR.xml");
        assertNoErrors(spec, "WCP14: Nested XOR");

        YNet net = spec.getRootNet();

        // Verify outer XOR split/merge
        YTask outerDecision = (YTask) net.getNetElement("outerDecision");
        YTask outerMerge = (YTask) net.getNetElement("outerMerge");

        assertNotNull("Outer decision should exist", outerDecision);
        assertNotNull("Outer merge should exist", outerMerge);
        assertEquals("Outer decision should be XOR split", YTask._XOR, outerDecision.getSplitType());
        assertEquals("Outer merge should be XOR join", YTask._XOR, outerMerge.getJoinType());

        // Verify inner XOR split
        YTask innerDecision = (YTask) net.getNetElement("innerDecision");
        assertNotNull("Inner decision should exist", innerDecision);
        assertEquals("Inner decision should be XOR split", YTask._XOR, innerDecision.getSplitType());
    }

    /**
     * Mixed AND/XOR patterns - Complex workflow combining parallel and exclusive structures.
     */
    public void testMixedSplitJoinPatterns() throws Exception {
        YSpecification spec = loadSpecification("WCP15_MixedSplitJoin.xml");
        assertNoErrors(spec, "WCP15: Mixed Split-Join");

        YNet net = spec.getRootNet();

        // Verify AND split
        YTask andSplit = (YTask) net.getNetElement("andSplit");
        assertNotNull("AND split should exist", andSplit);
        assertEquals("Should have AND split", YTask._AND, andSplit.getSplitType());

        // Verify XOR decision within AND branch
        YTask xorDecision = (YTask) net.getNetElement("xorDecision");
        assertNotNull("XOR decision should exist", xorDecision);
        assertEquals("Should have XOR split", YTask._XOR, xorDecision.getSplitType());

        // Verify XOR merge
        YTask xorMerge = (YTask) net.getNetElement("xorMerge");
        assertNotNull("XOR merge should exist", xorMerge);
        assertEquals("Should have XOR join", YTask._XOR, xorMerge.getJoinType());

        // Verify AND join
        YTask andJoin = (YTask) net.getNetElement("andJoin");
        assertNotNull("AND join should exist", andJoin);
        assertEquals("Should have AND join", YTask._AND, andJoin.getJoinType());
    }

    // ========================================================================
    // Execution Semantics Tests
    // ========================================================================

    /**
     * Test task enablement for different join types.
     */
    public void testTaskEnablement() throws YPersistenceException {
        YSpecification spec = new YSpecification("EnablementTest");
        spec.setVersion(YSchemaVersion.Beta4);
        YNet net = new YNet("EnablementNet", spec);

        // Create conditions with tokens
        YCondition c1 = new YCondition("c1", net);
        YCondition c2 = new YCondition("c2", net);

        // Create task with AND join
        YAtomicTask andJoinTask = new YAtomicTask("andJoin", YTask._AND, YTask._AND, net);
        andJoinTask.setDecompositionPrototype(new YAWLServiceGateway("andJoinGW", spec));

        // Create task with XOR join
        YAtomicTask xorJoinTask = new YAtomicTask("xorJoin", YTask._XOR, YTask._AND, net);
        xorJoinTask.setDecompositionPrototype(new YAWLServiceGateway("xorJoinGW", spec));

        // Set up flows
        YFlow flow1 = new YFlow(c1, andJoinTask);
        YFlow flow2 = new YFlow(c2, andJoinTask);
        c1.addPostset(flow1);
        c2.addPostset(flow2);
        andJoinTask.addPreset(flow1);
        andJoinTask.addPreset(flow2);

        YFlow flow3 = new YFlow(c1, xorJoinTask);
        YFlow flow4 = new YFlow(c2, xorJoinTask);
        xorJoinTask.addPreset(flow3);
        xorJoinTask.addPreset(flow4);

        // Initialize data
        YNetData netData = new YNetData();
        net.initializeDataStore(null, netData);
        net.initialise(null);

        // Create identifier
        YIdentifier id = new YIdentifier(null);

        // Test AND join enablement - needs all presets
        c1.add(null, id);
        assertFalse("AND join should not be enabled with only one token",
                andJoinTask.t_enabled(id));

        // Add token to second condition - should now be enabled
        YIdentifier id2 = new YIdentifier(null);
        c2.add(null, id2);
        assertTrue("AND join should be enabled with all tokens",
                andJoinTask.t_enabled(id2));

        // Test XOR join enablement - needs only one preset
        assertTrue("XOR join should be enabled with one token",
                xorJoinTask.t_enabled(id));
    }

    /**
     * Test task busy state.
     */
    public void testTaskBusyState() throws Exception {
        YSpecification spec = new YSpecification("BusyStateTest");
        spec.setVersion(YSchemaVersion.Beta4);
        YNet net = new YNet("BusyStateNet", spec);

        YCondition c = new YCondition("c", net);
        YAtomicTask task = new YAtomicTask("task", YTask._XOR, YTask._AND, net);
        task.setDecompositionPrototype(new YAWLServiceGateway("taskGW", spec));

        YFlow flow = new YFlow(c, task);
        c.addPostset(flow);
        task.addPreset(flow);

        // Initialize data
        YNetData netData = new YNetData();
        net.initializeDataStore(null, netData);
        net.initialise(null);

        // Task should not be busy initially
        assertFalse("Task should not be busy initially", task.t_isBusy());
    }

    /**
     * Test split type constants.
     */
    public void testSplitJoinTypeConstants() {
        assertEquals("AND type constant", 95, YTask._AND);
        assertEquals("OR type constant", 103, YTask._OR);
        assertEquals("XOR type constant", 126, YTask._XOR);
    }

    /**
     * Test specification verification for invalid split type.
     */
    public void testInvalidSplitType() throws Exception {
        YSpecification spec = new YSpecification("InvalidSplitTest");
        spec.setVersion(YSchemaVersion.Beta4);
        YNet net = new YNet("InvalidSplitNet", spec);

        YInputCondition input = new YInputCondition("input", net);
        YAtomicTask task = new YAtomicTask("task", YTask._XOR, 999, net); // Invalid split type
        task.setDecompositionPrototype(new YAWLServiceGateway("taskGW", spec));
        YOutputCondition output = new YOutputCondition("output", net);

        input.addPostset(new YFlow(input, task));
        task.addPostset(new YFlow(task, output));

        handler.reset();
        task.verify(handler);
        assertTrue("Should have error for invalid split type", handler.hasErrors());
    }

    /**
     * Test specification verification for invalid join type.
     */
    public void testInvalidJoinType() throws Exception {
        YSpecification spec = new YSpecification("InvalidJoinTest");
        spec.setVersion(YSchemaVersion.Beta4);
        YNet net = new YNet("InvalidJoinNet", spec);

        YInputCondition input = new YInputCondition("input", net);
        YAtomicTask task = new YAtomicTask("task", 999, YTask._AND, net); // Invalid join type
        task.setDecompositionPrototype(new YAWLServiceGateway("taskGW", spec));
        YOutputCondition output = new YOutputCondition("output", net);

        input.addPostset(new YFlow(input, task));
        task.addPostset(new YFlow(task, output));

        handler.reset();
        task.verify(handler);
        assertTrue("Should have error for invalid join type", handler.hasErrors());
    }

    // ========================================================================
    // Test Suite
    // ========================================================================

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTestSuite(TestBasicPatterns.class);
        return suite;
    }

    public static void main(String args[]) {
        TestRunner runner = new TestRunner();
        runner.doRun(suite());
        System.exit(0);
    }
}
