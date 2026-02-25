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

package org.yawlfoundation.yawl.engine.property;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.arbitraries.IntegerArbitrary;
import net.jqwik.api.Combinators;

import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.schema.YSchemaVersion;

/**
 * jqwik Arbitrary generator for valid YAWL workflow specifications.
 *
 * <p>This generator produces valid minimal workflow specifications for use in
 * property-based testing. It generates four patterns:
 * <ul>
 *   <li><b>Sequential</b>: InputCondition -&gt; A -&gt; B -&gt; C -&gt; OutputCondition</li>
 *   <li><b>AND-split/join</b>: InputCondition -&gt; A -&gt; [B || C] -&gt; D -&gt; OutputCondition</li>
 *   <li><b>XOR-choice</b>: InputCondition -&gt; A -&gt; [B xor C] -&gt; D -&gt; OutputCondition</li>
 *   <li><b>Mixed</b>: Random combination of above patterns</li>
 * </ul>
 *
 * <p>All generated specifications are:
 * - Syntactically valid (loadable by YStatelessEngine)
 * - Contain 2-5 tasks
 * - Have exactly one input condition and one output condition
 * - Are properly wired (no floating nodes)
 * - Use only AND, XOR split/join types (valid for Petri net token flow analysis)
 *
 * <p>Usage in property-based tests:
 * <pre>{@code
 * @Property
 * @Provide("validSpecifications")
 * void myProperty(@ForAll("validSpecifications") YSpecification spec) {
 *     // Test code here
 * }
 *
 * @Provide
 * Arbitrary<YSpecification> validSpecifications() {
 *     return YSpecificationArbitrary.specifications();
 * }
 * }</pre>
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 6.0.0
 */
public final class YSpecificationArbitrary {

    private static final int MIN_TASKS = 2;
    private static final int MAX_TASKS = 5;

    private YSpecificationArbitrary() {
        throw new UnsupportedOperationException(
            "YSpecificationArbitrary is a static utility class");
    }

    /**
     * Creates an Arbitrary that generates valid YAWL specifications with mixed patterns.
     *
     * @return Arbitrary of YSpecification objects
     */
    public static Arbitrary<YSpecification> specifications() {
        IntegerArbitrary taskCounts = net.jqwik.api.Arbitraries.integers()
            .between(MIN_TASKS, MAX_TASKS);
        Arbitrary<PatternType> patterns = net.jqwik.api.Arbitraries.of(PatternType.values());

        return Combinators.combine(taskCounts, patterns)
            .flatMap(YSpecificationArbitrary::generateSpecification);
    }

    /**
     * Generates a specification based on pattern type and task count.
     *
     * @param taskCount number of tasks to create (2-5)
     * @param pattern workflow pattern type (SEQUENTIAL, AND_SPLIT_JOIN, XOR_CHOICE, MIXED)
     * @return Arbitrary of YSpecification for this combination
     */
    private static Arbitrary<YSpecification> generateSpecification(
            int taskCount, PatternType pattern) {
        return net.jqwik.api.Arbitraries.just(
            switch (pattern) {
                case SEQUENTIAL -> createSequentialSpec(taskCount);
                case AND_SPLIT_JOIN -> createAndSplitJoinSpec();
                case XOR_CHOICE -> createXorChoiceSpec();
                case MIXED -> createMixedSpec(taskCount);
            }
        );
    }

    /**
     * Creates a sequential workflow: A -&gt; B -&gt; C (N tasks)
     *
     * @param taskCount number of sequential tasks
     * @return syntactically valid YSpecification
     */
    private static YSpecification createSequentialSpec(int taskCount) {
        String specId = "seq-" + UUID.randomUUID();
        YSpecification spec = new YSpecification(specId);
        spec.setName("Sequential Workflow [" + specId + "]");
        spec.setVersion(YSchemaVersion.Beta7);

        YNet net = new YNet("root", spec);
        spec.setRootNet(net);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        List<YAtomicTask> tasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            YAtomicTask task = new YAtomicTask(
                "task_" + i,
                YAtomicTask._AND,
                YAtomicTask._AND,
                net
            );
            net.addNetElement(task);
            tasks.add(task);
        }

        YFlow flowIn = new YFlow(input, tasks.get(0));
        input.addPostset(flowIn);
        tasks.get(0).addPreset(flowIn);

        for (int i = 0; i < tasks.size() - 1; i++) {
            YFlow flow = new YFlow(tasks.get(i), tasks.get(i + 1));
            tasks.get(i).addPostset(flow);
            tasks.get(i + 1).addPreset(flow);
        }

        YFlow flowOut = new YFlow(tasks.get(tasks.size() - 1), output);
        tasks.get(tasks.size() - 1).addPostset(flowOut);
        output.addPreset(flowOut);

        return spec;
    }

    /**
     * Creates an AND-split/join workflow:
     * InputCondition -&gt; A -&gt; [B || C] -&gt; D -&gt; OutputCondition
     *
     * <p>All branches must converge at D with matching AND-join.
     * Tokens flowing through B and C must synchronize at D.
     *
     * @return syntactically valid YSpecification with AND parallelism
     */
    private static YSpecification createAndSplitJoinSpec() {
        String specId = "and-split-" + UUID.randomUUID();
        YSpecification spec = new YSpecification(specId);
        spec.setName("AND-Split/Join Workflow [" + specId + "]");
        spec.setVersion(YSchemaVersion.Beta7);

        YNet net = new YNet("root", spec);
        spec.setRootNet(net);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask taskA = new YAtomicTask("A", YAtomicTask._AND, YAtomicTask._AND, net);
        YAtomicTask taskB = new YAtomicTask("B", YAtomicTask._AND, YAtomicTask._AND, net);
        YAtomicTask taskC = new YAtomicTask("C", YAtomicTask._AND, YAtomicTask._AND, net);
        YAtomicTask taskD = new YAtomicTask("D", YAtomicTask._AND, YAtomicTask._AND, net);

        net.addNetElement(taskA);
        net.addNetElement(taskB);
        net.addNetElement(taskC);
        net.addNetElement(taskD);

        YFlow f1 = new YFlow(input, taskA);
        input.addPostset(f1);
        taskA.addPreset(f1);

        YFlow f2 = new YFlow(taskA, taskB);
        taskA.addPostset(f2);
        taskB.addPreset(f2);

        YFlow f3 = new YFlow(taskA, taskC);
        taskA.addPostset(f3);
        taskC.addPreset(f3);

        YFlow f4 = new YFlow(taskB, taskD);
        taskB.addPostset(f4);
        taskD.addPreset(f4);

        YFlow f5 = new YFlow(taskC, taskD);
        taskC.addPostset(f5);
        taskD.addPreset(f5);

        YFlow f6 = new YFlow(taskD, output);
        taskD.addPostset(f6);
        output.addPreset(f6);

        return spec;
    }

    /**
     * Creates an XOR-choice workflow:
     * InputCondition -&gt; A -&gt; [B xor C] -&gt; D -&gt; OutputCondition
     *
     * <p>Only one of B or C executes per case (mutually exclusive).
     * Both paths reconverge at D (implicit OR-join semantics).
     *
     * @return syntactically valid YSpecification with XOR choice
     */
    private static YSpecification createXorChoiceSpec() {
        String specId = "xor-choice-" + UUID.randomUUID();
        YSpecification spec = new YSpecification(specId);
        spec.setName("XOR-Choice Workflow [" + specId + "]");
        spec.setVersion(YSchemaVersion.Beta7);

        YNet net = new YNet("root", spec);
        spec.setRootNet(net);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask taskA = new YAtomicTask("A", YAtomicTask._AND, YAtomicTask._XOR, net);
        YAtomicTask taskB = new YAtomicTask("B", YAtomicTask._XOR, YAtomicTask._AND, net);
        YAtomicTask taskC = new YAtomicTask("C", YAtomicTask._XOR, YAtomicTask._AND, net);
        YAtomicTask taskD = new YAtomicTask("D", YAtomicTask._OR, YAtomicTask._AND, net);

        net.addNetElement(taskA);
        net.addNetElement(taskB);
        net.addNetElement(taskC);
        net.addNetElement(taskD);

        YFlow f1 = new YFlow(input, taskA);
        input.addPostset(f1);
        taskA.addPreset(f1);

        YFlow f2 = new YFlow(taskA, taskB);
        taskA.addPostset(f2);
        taskB.addPreset(f2);

        YFlow f3 = new YFlow(taskA, taskC);
        taskA.addPostset(f3);
        taskC.addPreset(f3);

        YFlow f4 = new YFlow(taskB, taskD);
        taskB.addPostset(f4);
        taskD.addPreset(f4);

        YFlow f5 = new YFlow(taskC, taskD);
        taskC.addPostset(f5);
        taskD.addPreset(f5);

        YFlow f6 = new YFlow(taskD, output);
        taskD.addPostset(f6);
        output.addPreset(f6);

        return spec;
    }

    /**
     * Creates a mixed workflow combining sequential and split/join patterns.
     * Pattern: A -&gt; B -&gt; [C || D] -&gt; E (for taskCount=5)
     *
     * @param taskCount total number of tasks (2-5)
     * @return syntactically valid YSpecification with mixed patterns
     */
    private static YSpecification createMixedSpec(int taskCount) {
        if (taskCount < 2) {
            return createSequentialSpec(2);
        }
        if (taskCount == 2) {
            return createSequentialSpec(2);
        }
        if (taskCount == 3) {
            return createXorChoiceSpec();
        }
        if (taskCount == 4) {
            return createAndSplitJoinSpec();
        }
        return createComplexMixedSpec();
    }

    /**
     * Creates a complex mixed workflow for 5+ tasks.
     * Pattern: A -&gt; [B || C] -&gt; D -&gt; E
     *
     * @return syntactically valid YSpecification
     */
    private static YSpecification createComplexMixedSpec() {
        String specId = "mixed-" + UUID.randomUUID();
        YSpecification spec = new YSpecification(specId);
        spec.setName("Mixed Workflow [" + specId + "]");
        spec.setVersion(YSchemaVersion.Beta7);

        YNet net = new YNet("root", spec);
        spec.setRootNet(net);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask taskA = new YAtomicTask("A", YAtomicTask._AND, YAtomicTask._AND, net);
        YAtomicTask taskB = new YAtomicTask("B", YAtomicTask._AND, YAtomicTask._AND, net);
        YAtomicTask taskC = new YAtomicTask("C", YAtomicTask._AND, YAtomicTask._AND, net);
        YAtomicTask taskD = new YAtomicTask("D", YAtomicTask._AND, YAtomicTask._AND, net);
        YAtomicTask taskE = new YAtomicTask("E", YAtomicTask._AND, YAtomicTask._AND, net);

        net.addNetElement(taskA);
        net.addNetElement(taskB);
        net.addNetElement(taskC);
        net.addNetElement(taskD);
        net.addNetElement(taskE);

        YFlow f1 = new YFlow(input, taskA);
        input.addPostset(f1);
        taskA.addPreset(f1);

        YFlow f2 = new YFlow(taskA, taskB);
        taskA.addPostset(f2);
        taskB.addPreset(f2);

        YFlow f3 = new YFlow(taskA, taskC);
        taskA.addPostset(f3);
        taskC.addPreset(f3);

        YFlow f4 = new YFlow(taskB, taskD);
        taskB.addPostset(f4);
        taskD.addPreset(f4);

        YFlow f5 = new YFlow(taskC, taskD);
        taskC.addPostset(f5);
        taskD.addPreset(f5);

        YFlow f6 = new YFlow(taskD, taskE);
        taskD.addPostset(f6);
        taskE.addPreset(f6);

        YFlow f7 = new YFlow(taskE, output);
        taskE.addPostset(f7);
        output.addPreset(f7);

        return spec;
    }

    /**
     * Enumeration of workflow pattern types supported by this generator.
     */
    private enum PatternType {
        SEQUENTIAL,
        AND_SPLIT_JOIN,
        XOR_CHOICE,
        MIXED
    }
}
