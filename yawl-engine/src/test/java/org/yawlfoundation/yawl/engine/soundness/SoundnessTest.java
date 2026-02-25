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

package org.yawlfoundation.yawl.engine.soundness;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Petri Net Soundness Verifier using REAL YAWL objects.
 *
 * These tests construct actual YNet instances with YTask and YCondition elements,
 * then verify soundness properties using formal reachability analysis.
 *
 * @author YAWL Team
 * @since 6.0.0
 */
public class SoundnessTest {

    private PetriNetSoundnessVerifier verifier;
    private YSpecification specification;

    @BeforeEach
    void setUp() {
        verifier = new PetriNetSoundnessVerifier();
        specification = new YSpecification("TestSpec");
    }

    /**
     * Test case: Sequential workflow A→B→C is sound.
     *
     * <p>Verification checks:
     * <ul>
     *   <li>Initial marking has 1 token on input condition</li>
     *   <li>Token flows through A, then B, then C to output condition</li>
     *   <li>Terminal marking: 1 token on output condition only</li>
     *   <li>All tasks fire exactly once</li>
     * </ul>
     */
    @Test
    void testSequentialWorkflowIsSound() {
        YNet net = new YNet("SequentialNet", specification);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask taskA = new YAtomicTask("A", YTask._XOR, YTask._XOR, net);
        YAtomicTask taskB = new YAtomicTask("B", YTask._XOR, YTask._XOR, net);
        YAtomicTask taskC = new YAtomicTask("C", YTask._XOR, YTask._XOR, net);

        net.addNetElement(taskA);
        net.addNetElement(taskB);
        net.addNetElement(taskC);

        YCondition c1 = new YCondition("c1", net);
        YCondition c2 = new YCondition("c2", net);
        net.addNetElement(c1);
        net.addNetElement(c2);

        YFlow flowInputA = new YFlow(input, taskA);
        input.addPostset(flowInputA);
        taskA.addPreset(flowInputA);

        YFlow flowAC1 = new YFlow(taskA, c1);
        taskA.addPostset(flowAC1);
        c1.addPreset(flowAC1);

        YFlow flowC1B = new YFlow(c1, taskB);
        c1.addPostset(flowC1B);
        taskB.addPreset(flowC1B);

        YFlow flowBC2 = new YFlow(taskB, c2);
        taskB.addPostset(flowBC2);
        c2.addPreset(flowBC2);

        YFlow flowC2C = new YFlow(c2, taskC);
        c2.addPostset(flowC2C);
        taskC.addPreset(flowC2C);

        YFlow flowCOutput = new YFlow(taskC, output);
        taskC.addPostset(flowCOutput);
        output.addPreset(flowCOutput);

        PetriNetSoundnessVerifier.SoundnessResult result = verifier.verify(net);

        assertTrue(result.isSound(), "Sequential workflow should be sound. Violations: " + result.violations());
        assertTrue(result.violations().isEmpty(), "Expected no violations");
    }

    /**
     * Test case: AND-split with synchronization A→[B||C]→D is sound.
     *
     * <p>Verification checks:
     * <ul>
     *   <li>A fires, enabling B and C in parallel via AND-split</li>
     *   <li>D requires both B and C to complete (AND-join)</li>
     *   <li>Terminal marking: 1 token on output condition only</li>
     *   <li>All tasks fire: A, B, C, D</li>
     * </ul>
     */
    @Test
    void testAndSplitSyncWorkflowIsSound() {
        YNet net = new YNet("AndSplitNet", specification);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask taskA = new YAtomicTask("A", YTask._XOR, YTask._AND, net);
        YAtomicTask taskB = new YAtomicTask("B", YTask._XOR, YTask._XOR, net);
        YAtomicTask taskC = new YAtomicTask("C", YTask._XOR, YTask._XOR, net);
        YAtomicTask taskD = new YAtomicTask("D", YTask._AND, YTask._XOR, net);

        net.addNetElement(taskA);
        net.addNetElement(taskB);
        net.addNetElement(taskC);
        net.addNetElement(taskD);

        YCondition c1 = new YCondition("c1", net);
        YCondition c2 = new YCondition("c2", net);
        YCondition c3 = new YCondition("c3", net);
        net.addNetElement(c1);
        net.addNetElement(c2);
        net.addNetElement(c3);

        YFlow flowInputA = new YFlow(input, taskA);
        input.addPostset(flowInputA);
        taskA.addPreset(flowInputA);

        YFlow flowAC1 = new YFlow(taskA, c1);
        taskA.addPostset(flowAC1);
        c1.addPreset(flowAC1);

        YFlow flowC1B = new YFlow(c1, taskB);
        c1.addPostset(flowC1B);
        taskB.addPreset(flowC1B);

        YFlow flowC1C = new YFlow(c1, taskC);
        c1.addPostset(flowC1C);
        taskC.addPreset(flowC1C);

        YFlow flowBC2 = new YFlow(taskB, c2);
        taskB.addPostset(flowBC2);
        c2.addPreset(flowBC2);

        YFlow flowCC3 = new YFlow(taskC, c3);
        taskC.addPostset(flowCC3);
        c3.addPreset(flowCC3);

        YFlow flowC2D = new YFlow(c2, taskD);
        c2.addPostset(flowC2D);
        taskD.addPreset(flowC2D);

        YFlow flowC3D = new YFlow(c3, taskD);
        c3.addPostset(flowC3D);
        taskD.addPreset(flowC3D);

        YFlow flowDOutput = new YFlow(taskD, output);
        taskD.addPostset(flowDOutput);
        output.addPreset(flowDOutput);

        PetriNetSoundnessVerifier.SoundnessResult result = verifier.verify(net);

        assertTrue(result.isSound(), "AND-split/sync workflow should be sound. Violations: " + result.violations());
        assertTrue(result.violations().isEmpty(), "Expected no violations");
    }

    /**
     * Test case: Dead-end net (task with no path to output) is NOT sound.
     *
     * <p>Violation: After task A fires, token reaches condition c1 which has no
     * outgoing flow to any task. This creates a dead-end marking with no enabled
     * transitions and no path to output condition.</p>
     */
    @Test
    void testDeadEndNetIsNotSound() {
        YNet net = new YNet("DeadEndNet", specification);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask taskA = new YAtomicTask("A", YTask._XOR, YTask._XOR, net);
        YAtomicTask taskB = new YAtomicTask("B", YTask._XOR, YTask._XOR, net);

        net.addNetElement(taskA);
        net.addNetElement(taskB);

        YCondition c1 = new YCondition("c1", net);
        YCondition c2 = new YCondition("c2", net);
        net.addNetElement(c1);
        net.addNetElement(c2);

        YFlow flowInputA = new YFlow(input, taskA);
        input.addPostset(flowInputA);
        taskA.addPreset(flowInputA);

        YFlow flowAC1 = new YFlow(taskA, c1);
        taskA.addPostset(flowAC1);
        c1.addPreset(flowAC1);

        YFlow flowInputB = new YFlow(input, taskB);
        input.addPostset(flowInputB);
        taskB.addPreset(flowInputB);

        YFlow flowBC2 = new YFlow(taskB, c2);
        taskB.addPostset(flowBC2);
        c2.addPreset(flowBC2);

        YFlow flowC2Output = new YFlow(c2, output);
        c2.addPostset(flowC2Output);
        output.addPreset(flowC2Output);

        PetriNetSoundnessVerifier.SoundnessResult result = verifier.verify(net);

        assertFalse(result.isSound(), "Dead-end net should NOT be sound");
        assertFalse(result.violations().isEmpty(), "Expected violations for dead-end marking");
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("Dead-end") || v.contains("unreachable")),
            "Expected dead-end or unreachability violation. Got: " + result.violations());
    }

    /**
     * Test case: Orphan task (unreachable from input) is NOT sound.
     *
     * <p>Violation: Task C is never reachable from the input condition path,
     * so it never fires. This violates the "no dead transitions" property.</p>
     */
    @Test
    void testOrphanTaskIsNotSound() {
        YNet net = new YNet("OrphanTaskNet", specification);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask taskA = new YAtomicTask("A", YTask._XOR, YTask._XOR, net);
        YAtomicTask taskB = new YAtomicTask("B", YTask._XOR, YTask._XOR, net);
        YAtomicTask taskC = new YAtomicTask("C", YTask._XOR, YTask._XOR, net);

        net.addNetElement(taskA);
        net.addNetElement(taskB);
        net.addNetElement(taskC);

        YCondition c1 = new YCondition("c1", net);
        YCondition c2 = new YCondition("c2", net);
        YCondition orphan = new YCondition("orphan", net);
        net.addNetElement(c1);
        net.addNetElement(c2);
        net.addNetElement(orphan);

        YFlow flowInputA = new YFlow(input, taskA);
        input.addPostset(flowInputA);
        taskA.addPreset(flowInputA);

        YFlow flowAC1 = new YFlow(taskA, c1);
        taskA.addPostset(flowAC1);
        c1.addPreset(flowAC1);

        YFlow flowC1B = new YFlow(c1, taskB);
        c1.addPostset(flowC1B);
        taskB.addPreset(flowC1B);

        YFlow flowBC2 = new YFlow(taskB, c2);
        taskB.addPostset(flowBC2);
        c2.addPreset(flowBC2);

        YFlow flowC2Output = new YFlow(c2, output);
        c2.addPostset(flowC2Output);
        output.addPreset(flowC2Output);

        YFlow flowOrphanC = new YFlow(orphan, taskC);
        orphan.addPostset(flowOrphanC);
        taskC.addPreset(flowOrphanC);

        YFlow flowCOutput = new YFlow(taskC, output);
        taskC.addPostset(flowCOutput);
        output.addPreset(flowCOutput);

        PetriNetSoundnessVerifier.SoundnessResult result = verifier.verify(net);

        assertFalse(result.isSound(), "Net with orphan task should NOT be sound");
        assertFalse(result.violations().isEmpty(), "Expected violations for dead transitions");
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("Dead transition")),
            "Expected dead transition violation. Got: " + result.violations());
    }

    /**
     * Test case: Circular flow without termination is NOT sound.
     *
     * <p>Violation: Task A→B→A creates a cycle with no path to output condition.
     * The workflow loops infinitely and never reaches the output.</p>
     */
    @Test
    void testCircularFlowWithoutTerminationIsNotSound() {
        YNet net = new YNet("CircularNet", specification);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask taskA = new YAtomicTask("A", YTask._XOR, YTask._XOR, net);
        YAtomicTask taskB = new YAtomicTask("B", YTask._XOR, YTask._XOR, net);

        net.addNetElement(taskA);
        net.addNetElement(taskB);

        YCondition c1 = new YCondition("c1", net);
        YCondition c2 = new YCondition("c2", net);
        net.addNetElement(c1);
        net.addNetElement(c2);

        YFlow flowInputA = new YFlow(input, taskA);
        input.addPostset(flowInputA);
        taskA.addPreset(flowInputA);

        YFlow flowAC1 = new YFlow(taskA, c1);
        taskA.addPostset(flowAC1);
        c1.addPreset(flowAC1);

        YFlow flowC1B = new YFlow(c1, taskB);
        c1.addPostset(flowC1B);
        taskB.addPreset(flowC1B);

        YFlow flowBC2 = new YFlow(taskB, c2);
        taskB.addPostset(flowBC2);
        c2.addPreset(flowBC2);

        YFlow flowC2A = new YFlow(c2, taskA);
        c2.addPostset(flowC2A);
        taskA.addPreset(flowC2A);

        PetriNetSoundnessVerifier.SoundnessResult result = verifier.verify(net);

        assertFalse(result.isSound(), "Net with circular flow should NOT be sound");
        assertFalse(result.violations().isEmpty(), "Expected violations for circular flow");
    }
}
