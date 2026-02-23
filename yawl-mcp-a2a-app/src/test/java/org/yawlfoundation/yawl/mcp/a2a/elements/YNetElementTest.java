/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.elements;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for YAWL element domain model classes.
 *
 * <p>Tests verify the construction, properties, and relationships of the core YAWL
 * Petri net elements including nets, tasks, conditions, and flow arcs. These tests
 * operate on plain Java objects with no database or Spring context requirements.</p>
 *
 * <p>Chicago/Detroit TDD — all tests operate on real YAWL element instances
 * instantiated directly. No mocks, stubs, or fakes are used.</p>
 *
 * <h2>Test Coverage</h2>
 * <ul>
 *   <li>YSpecification: creation, URI, net management</li>
 *   <li>YNet: construction, element addition/retrieval, input/output conditions</li>
 *   <li>YAtomicTask: creation, split/join types, decomposition</li>
 *   <li>YCompositeTask: creation, sub-net handling</li>
 *   <li>YInputCondition/YOutputCondition: type verification, containment</li>
 *   <li>YFlow/Flows: arc creation, source/target tracking</li>
 *   <li>YSpecVersion: version comparison, increment/rollback</li>
 *   <li>Element identity: same ID in same net references same element</li>
 *   <li>Net reachability: input→task→output chain verification</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("YAWL Element Domain Model — Petri Net Structure")
class YNetElementTest {

    private YSpecification specification;
    private YNet net;

    @BeforeEach
    void setUp() {
        specification = new YSpecification("http://example.com/workflow");
        net = new YNet("main_net", specification);
        specification.setRootNet(net);
    }

    @Nested
    @DisplayName("YSpecification Tests")
    class YSpecificationTests {

        @Test
        @DisplayName("Create specification with URI")
        void testSpecificationConstruction() {
            YSpecification spec = new YSpecification("http://example.com/test");
            assertNotNull(spec);
            assertEquals("http://example.com/test", spec.getURI());
        }

        @Test
        @DisplayName("Create specification with no-arg constructor")
        void testSpecificationNoArgConstruction() {
            YSpecification spec = new YSpecification();
            assertNotNull(spec);
        }

        @Test
        @DisplayName("Set and retrieve root net")
        void testSetRootNet() {
            YSpecification spec = new YSpecification("http://example.com/proc");
            YNet rootNet = new YNet("root", spec);
            spec.setRootNet(rootNet);

            YNet retrieved = spec.getRootNet();
            assertNotNull(retrieved);
            assertEquals(rootNet, retrieved);
            assertEquals("root", retrieved.getID());
        }

        @Test
        @DisplayName("Get specification URI")
        void testGetSpecificationURI() {
            String uri = "http://acme.com/order_process";
            YSpecification spec = new YSpecification(uri);
            assertEquals(uri, spec.getURI());
        }

        @Test
        @DisplayName("Get default specification version")
        void testDefaultSpecificationVersion() {
            YSpecification spec = new YSpecification();
            assertNotNull(spec.getSpecVersion());
            assertEquals("0.1", spec.getSpecVersion());
        }
    }

    @Nested
    @DisplayName("YNet Construction and Element Management")
    class YNetConstructionTests {

        @Test
        @DisplayName("Construct YNet with id and specification")
        void testYNetConstruction() {
            YNet testNet = new YNet("workflow", specification);
            assertNotNull(testNet);
            assertEquals("workflow", testNet.getID());
        }

        @Test
        @DisplayName("Add input condition to net")
        void testSetInputCondition() {
            YInputCondition input = new YInputCondition("input_cond", net);
            net.setInputCondition(input);

            assertEquals(input, net.getInputCondition());
            assertNotNull(net.getNetElement("input_cond"));
        }

        @Test
        @DisplayName("Add output condition to net")
        void testSetOutputCondition() {
            YOutputCondition output = new YOutputCondition("output_cond", net);
            net.setOutputCondition(output);

            assertEquals(output, net.getOutputCondition());
            assertNotNull(net.getNetElement("output_cond"));
        }

        @Test
        @DisplayName("Retrieve net element by ID")
        void testGetNetElement() {
            YInputCondition input = new YInputCondition("i", net);
            net.setInputCondition(input);

            YExternalNetElement retrieved = net.getNetElement("i");
            assertNotNull(retrieved);
            assertEquals(input, retrieved);
        }

        @Test
        @DisplayName("Get non-existent net element returns null")
        void testGetNonExistentElement() {
            YExternalNetElement retrieved = net.getNetElement("does_not_exist");
            assertNull(retrieved);
        }

        @Test
        @DisplayName("Add atomic task to net")
        void testAddAtomicTaskToNet() {
            YAtomicTask task = new YAtomicTask("task1", YTask._XOR, YTask._AND, net);
            net.addNetElement(task);

            YExternalNetElement retrieved = net.getNetElement("task1");
            assertNotNull(retrieved);
            assertEquals(task, retrieved);
        }

        @Test
        @DisplayName("Get all net elements as map")
        void testGetNetElements() {
            YInputCondition input = new YInputCondition("i", net);
            YOutputCondition output = new YOutputCondition("o", net);
            YAtomicTask task = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);

            net.setInputCondition(input);
            net.setOutputCondition(output);
            net.addNetElement(task);

            var elements = net.getNetElements();
            assertEquals(3, elements.size());
            assertTrue(elements.containsKey("i"));
            assertTrue(elements.containsKey("o"));
            assertTrue(elements.containsKey("t1"));
        }

        @Test
        @DisplayName("Get all net tasks")
        void testGetNetTasks() {
            YAtomicTask task1 = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);
            YCompositeTask task2 = new YCompositeTask("t2", YTask._AND, YTask._OR, net);
            net.addNetElement(task1);
            net.addNetElement(task2);

            var tasks = net.getNetTasks();
            assertEquals(2, tasks.size());
            assertTrue(tasks.contains(task1));
            assertTrue(tasks.contains(task2));
        }
    }

    @Nested
    @DisplayName("YAtomicTask Tests")
    class YAtomicTaskTests {

        @Test
        @DisplayName("Construct atomic task with all parameters")
        void testAtomicTaskConstruction() {
            YAtomicTask task = new YAtomicTask("approve", YTask._AND, YTask._XOR, net);

            assertNotNull(task);
            assertEquals("approve", task.getID());
            assertEquals(YTask._AND, task.getJoinType());
            assertEquals(YTask._XOR, task.getSplitType());
        }

        @Test
        @DisplayName("Get join type of atomic task")
        void testGetJoinType() {
            YAtomicTask task = new YAtomicTask("t1", YTask._OR, YTask._AND, net);
            assertEquals(YTask._OR, task.getJoinType());
        }

        @Test
        @DisplayName("Get split type of atomic task")
        void testGetSplitType() {
            YAtomicTask task = new YAtomicTask("t1", YTask._XOR, YTask._OR, net);
            assertEquals(YTask._OR, task.getSplitType());
        }

        @Test
        @DisplayName("Set join type on atomic task")
        void testSetJoinType() {
            YAtomicTask task = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);
            task.setJoinType(YTask._OR);
            assertEquals(YTask._OR, task.getJoinType());
        }

        @Test
        @DisplayName("Set split type on atomic task")
        void testSetSplitType() {
            YAtomicTask task = new YAtomicTask("t1", YTask._AND, YTask._XOR, net);
            task.setSplitType(YTask._OR);
            assertEquals(YTask._OR, task.getSplitType());
        }

        @Test
        @DisplayName("Set name on atomic task")
        void testSetTaskName() {
            YAtomicTask task = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);
            task.setName("Review Document");
            assertEquals("Review Document", task.getName());
        }

        @Test
        @DisplayName("Atomic task initially not running")
        void testAtomicTaskNotRunningInitially() {
            YAtomicTask task = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);
            assertFalse(task.isRunning());
        }
    }

    @Nested
    @DisplayName("YCompositeTask Tests")
    class YCompositeTaskTests {

        @Test
        @DisplayName("Construct composite task with all parameters")
        void testCompositeTaskConstruction() {
            YCompositeTask task = new YCompositeTask("process_order", YTask._AND, YTask._XOR, net);

            assertNotNull(task);
            assertEquals("process_order", task.getID());
            assertEquals(YTask._AND, task.getJoinType());
            assertEquals(YTask._XOR, task.getSplitType());
        }

        @Test
        @DisplayName("Composite task is a YTask")
        void testCompositeTaskIsYTask() {
            YCompositeTask task = new YCompositeTask("ct1", YTask._XOR, YTask._AND, net);
            assertTrue(task instanceof YTask);
        }

        @Test
        @DisplayName("Get join and split types for composite task")
        void testCompositeTaskTypes() {
            YCompositeTask task = new YCompositeTask("ct1", YTask._OR, YTask._XOR, net);
            assertEquals(YTask._OR, task.getJoinType());
            assertEquals(YTask._XOR, task.getSplitType());
        }

        @Test
        @DisplayName("Modify composite task types")
        void testModifyCompositeTaskTypes() {
            YCompositeTask task = new YCompositeTask("ct1", YTask._XOR, YTask._AND, net);

            task.setJoinType(YTask._AND);
            task.setSplitType(YTask._OR);

            assertEquals(YTask._AND, task.getJoinType());
            assertEquals(YTask._OR, task.getSplitType());
        }
    }

    @Nested
    @DisplayName("YInputCondition and YOutputCondition Tests")
    class ConditionTests {

        @Test
        @DisplayName("Construct input condition with id and net")
        void testInputConditionConstruction() {
            YInputCondition input = new YInputCondition("input", net);
            assertNotNull(input);
            assertEquals("input", input.getID());
        }

        @Test
        @DisplayName("Construct output condition with id and net")
        void testOutputConditionConstruction() {
            YOutputCondition output = new YOutputCondition("output", net);
            assertNotNull(output);
            assertEquals("output", output.getID());
        }

        @Test
        @DisplayName("Construct input condition with label")
        void testInputConditionWithLabel() {
            YInputCondition input = new YInputCondition("i_cond", "Start Process", net);
            assertNotNull(input);
            assertEquals("i_cond", input.getID());
            assertEquals("Start Process", input.getName());
        }

        @Test
        @DisplayName("Construct output condition with label")
        void testOutputConditionWithLabel() {
            YOutputCondition output = new YOutputCondition("o_cond", "End Process", net);
            assertNotNull(output);
            assertEquals("o_cond", output.getID());
            assertEquals("End Process", output.getName());
        }

        @Test
        @DisplayName("Input condition is a YCondition")
        void testInputConditionIsYCondition() {
            YInputCondition input = new YInputCondition("i", net);
            assertTrue(input instanceof YCondition);
        }

        @Test
        @DisplayName("Output condition is a YCondition")
        void testOutputConditionIsYCondition() {
            YOutputCondition output = new YOutputCondition("o", net);
            assertTrue(output instanceof YCondition);
        }

        @Test
        @DisplayName("Input condition is YExternalNetElement")
        void testInputConditionIsExternalNetElement() {
            YInputCondition input = new YInputCondition("i", net);
            assertTrue(input instanceof YExternalNetElement);
        }

        @Test
        @DisplayName("Output condition is YExternalNetElement")
        void testOutputConditionIsExternalNetElement() {
            YOutputCondition output = new YOutputCondition("o", net);
            assertTrue(output instanceof YExternalNetElement);
        }
    }

    @Nested
    @DisplayName("YFlow (Flows Into) Arc Tests")
    class YFlowTests {

        @Test
        @DisplayName("Create flow from input to task")
        void testCreateFlowInputToTask() {
            YInputCondition input = new YInputCondition("i", net);
            YAtomicTask task = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);

            YFlow flow = new YFlow(input, task);

            assertNotNull(flow);
            assertEquals(input, flow.getPriorElement());
            assertEquals(task, flow.getNextElement());
        }

        @Test
        @DisplayName("Create flow from task to output")
        void testCreateFlowTaskToOutput() {
            YAtomicTask task = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);
            YOutputCondition output = new YOutputCondition("o", net);

            YFlow flow = new YFlow(task, output);

            assertNotNull(flow);
            assertEquals(task, flow.getPriorElement());
            assertEquals(output, flow.getNextElement());
        }

        @Test
        @DisplayName("Create flow between two tasks")
        void testCreateFlowBetweenTasks() {
            YAtomicTask task1 = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);
            YAtomicTask task2 = new YAtomicTask("t2", YTask._AND, YTask._XOR, net);

            YFlow flow = new YFlow(task1, task2);

            assertEquals(task1, flow.getPriorElement());
            assertEquals(task2, flow.getNextElement());
        }

        @Test
        @DisplayName("Set XPath predicate on flow")
        void testSetXPathPredicate() {
            YAtomicTask task1 = new YAtomicTask("t1", YTask._OR, YTask._AND, net);
            YAtomicTask task2 = new YAtomicTask("t2", YTask._XOR, YTask._OR, net);

            YFlow flow = new YFlow(task1, task2);
            String predicate = "/root/status = 'approved'";
            flow.setXpathPredicate(predicate);

            assertEquals(predicate, flow.getXpathPredicate());
        }

        @Test
        @DisplayName("Set default flow flag")
        void testSetDefaultFlow() {
            YAtomicTask task1 = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);
            YAtomicTask task2 = new YAtomicTask("t2", YTask._AND, YTask._XOR, net);

            YFlow flow = new YFlow(task1, task2);
            flow.setIsDefaultFlow(true);

            assertTrue(flow.isDefaultFlow());
        }

        @Test
        @DisplayName("Set evaluation ordering on flow")
        void testSetEvaluationOrdering() {
            YAtomicTask task1 = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);
            YAtomicTask task2 = new YAtomicTask("t2", YTask._AND, YTask._XOR, net);

            YFlow flow = new YFlow(task1, task2);
            flow.setEvalOrdering(1);

            assertEquals(1, flow.getEvalOrdering());
        }

        @Test
        @DisplayName("Set documentation on flow")
        void testSetFlowDocumentation() {
            YInputCondition input = new YInputCondition("i", net);
            YAtomicTask task = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);

            YFlow flow = new YFlow(input, task);
            String docs = "Approval path when request is valid";
            flow.setDocumentation(docs);

            assertEquals(docs, flow.getDocumentation());
        }

        @Test
        @DisplayName("Add flow to elements' preset and postset")
        void testFlowPresetPostset() {
            YInputCondition input = new YInputCondition("i", net);
            YAtomicTask task = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);

            YFlow flow = new YFlow(input, task);
            input.addPostset(flow);

            assertTrue(input.getPostsetElements().contains(task));
            assertTrue(task.getPresetElements().contains(input));
        }
    }

    @Nested
    @DisplayName("Net Reachability and Element Connectivity")
    class NetReachabilityTests {

        @Test
        @DisplayName("Simple linear path: input → task → output")
        void testLinearPath() {
            YInputCondition input = new YInputCondition("i", net);
            YAtomicTask task = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);
            YOutputCondition output = new YOutputCondition("o", net);

            net.setInputCondition(input);
            net.setOutputCondition(output);
            net.addNetElement(task);

            YFlow flow1 = new YFlow(input, task);
            YFlow flow2 = new YFlow(task, output);

            input.addPostset(flow1);
            task.addPostset(flow2);

            assertEquals(input, net.getInputCondition());
            assertEquals(output, net.getOutputCondition());
            assertTrue(input.getPostsetElements().contains(task));
            assertTrue(task.getPostsetElements().contains(output));
        }

        @Test
        @DisplayName("Multi-task workflow with sequential tasks")
        void testMultiTaskSequence() {
            YInputCondition input = new YInputCondition("i", net);
            YAtomicTask task1 = new YAtomicTask("t1", YTask._AND, YTask._XOR, net);
            YAtomicTask task2 = new YAtomicTask("t2", YTask._XOR, YTask._AND, net);
            YOutputCondition output = new YOutputCondition("o", net);

            net.setInputCondition(input);
            net.setOutputCondition(output);
            net.addNetElement(task1);
            net.addNetElement(task2);

            input.addPostset(new YFlow(input, task1));
            task1.addPostset(new YFlow(task1, task2));
            task2.addPostset(new YFlow(task2, output));

            assertEquals(1, input.getPostsetElements().size());
            assertEquals(1, task1.getPostsetElements().size());
            assertEquals(1, task2.getPostsetElements().size());
        }

        @Test
        @DisplayName("Branching workflow with AND split")
        void testANDSplitBranch() {
            YInputCondition input = new YInputCondition("i", net);
            YAtomicTask splitTask = new YAtomicTask("split", YTask._XOR, YTask._AND, net);
            YAtomicTask task1 = new YAtomicTask("t1", YTask._AND, YTask._XOR, net);
            YAtomicTask task2 = new YAtomicTask("t2", YTask._AND, YTask._XOR, net);
            YOutputCondition output = new YOutputCondition("o", net);

            net.setInputCondition(input);
            net.setOutputCondition(output);
            net.addNetElement(splitTask);
            net.addNetElement(task1);
            net.addNetElement(task2);

            input.addPostset(new YFlow(input, splitTask));
            splitTask.addPostset(new YFlow(splitTask, task1));
            splitTask.addPostset(new YFlow(splitTask, task2));
            task1.addPostset(new YFlow(task1, output));
            task2.addPostset(new YFlow(task2, output));

            assertEquals(2, splitTask.getPostsetElements().size());
            assertTrue(splitTask.getPostsetElements().contains(task1));
            assertTrue(splitTask.getPostsetElements().contains(task2));
        }

        @Test
        @DisplayName("Verify task is reachable from input condition")
        void testTaskReachabilityFromInput() {
            YInputCondition input = new YInputCondition("i", net);
            YAtomicTask task = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);

            net.setInputCondition(input);
            net.addNetElement(task);

            input.addPostset(new YFlow(input, task));

            assertTrue(input.getPostsetElements().contains(task));
        }
    }

    @Nested
    @DisplayName("Element Identity and Same-Net References")
    class ElementIdentityTests {

        @Test
        @DisplayName("Same element ID in same net references same object")
        void testSameIDReferences() {
            YAtomicTask task = new YAtomicTask("approve", YTask._XOR, YTask._AND, net);
            net.addNetElement(task);

            YExternalNetElement retrieved = net.getNetElement("approve");
            assertSame(task, retrieved);
        }

        @Test
        @DisplayName("Different task IDs reference different objects")
        void testDifferentIDsReferenceDifferent() {
            YAtomicTask task1 = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);
            YAtomicTask task2 = new YAtomicTask("t2", YTask._AND, YTask._XOR, net);

            net.addNetElement(task1);
            net.addNetElement(task2);

            assertNotSame(task1, task2);
            assertNotSame(net.getNetElement("t1"), net.getNetElement("t2"));
        }

        @Test
        @DisplayName("Input and output conditions are distinct in same net")
        void testDistinctConditions() {
            YInputCondition input = new YInputCondition("i", net);
            YOutputCondition output = new YOutputCondition("o", net);

            net.setInputCondition(input);
            net.setOutputCondition(output);

            assertNotSame(input, output);
            assertNotEquals(input, output);
        }

        @Test
        @DisplayName("Element contains reference to its containing net")
        void testElementContainsNetReference() {
            YAtomicTask task = new YAtomicTask("t1", YTask._XOR, YTask._AND, net);

            assertEquals(net, task.getNet());
        }
    }

    @Nested
    @DisplayName("Split/Join Type Constants")
    class SplitJoinTypeTests {

        @Test
        @DisplayName("AND split type constant is 95")
        void testANDConstant() {
            assertEquals(95, YTask._AND);
        }

        @Test
        @DisplayName("OR split type constant is 103")
        void testORConstant() {
            assertEquals(103, YTask._OR);
        }

        @Test
        @DisplayName("XOR split type constant is 126")
        void testXORConstant() {
            assertEquals(126, YTask._XOR);
        }

        @Test
        @DisplayName("Create task with AND/OR split type combinations")
        void testAllSplitJoinCombinations() {
            YAtomicTask t1 = new YAtomicTask("t1", YTask._AND, YTask._OR, net);
            YAtomicTask t2 = new YAtomicTask("t2", YTask._OR, YTask._XOR, net);
            YAtomicTask t3 = new YAtomicTask("t3", YTask._XOR, YTask._AND, net);

            assertEquals(YTask._AND, t1.getJoinType());
            assertEquals(YTask._OR, t1.getSplitType());

            assertEquals(YTask._OR, t2.getJoinType());
            assertEquals(YTask._XOR, t2.getSplitType());

            assertEquals(YTask._XOR, t3.getJoinType());
            assertEquals(YTask._AND, t3.getSplitType());
        }
    }

    @Nested
    @DisplayName("YSpecVersion Tests")
    class YSpecVersionTests {

        @Test
        @DisplayName("Create YSpecVersion with default constructor")
        void testDefaultSpecVersion() {
            YSpecVersion version = new YSpecVersion();
            assertEquals("0.1", version.toString());
            assertEquals(0, version.getMajorVersion());
            assertEquals(1, version.getMinorVersion());
        }

        @Test
        @DisplayName("Create YSpecVersion with major and minor")
        void testSpecVersionWithInts() {
            YSpecVersion version = new YSpecVersion(2, 5);
            assertEquals("2.5", version.toString());
            assertEquals(2, version.getMajorVersion());
            assertEquals(5, version.getMinorVersion());
        }

        @Test
        @DisplayName("Create YSpecVersion from string")
        void testSpecVersionFromString() {
            YSpecVersion version = new YSpecVersion("3.8");
            assertEquals("3.8", version.toString());
            assertEquals(3, version.getMajorVersion());
            assertEquals(8, version.getMinorVersion());
        }

        @Test
        @DisplayName("Create YSpecVersion from null defaults to 0.1")
        void testSpecVersionNullDefaults() {
            YSpecVersion version = new YSpecVersion(null);
            assertEquals("0.1", version.toString());
        }

        @Test
        @DisplayName("Increment minor version")
        void testMinorIncrement() {
            YSpecVersion version = new YSpecVersion(1, 5);
            version.minorIncrement();
            assertEquals("1.6", version.toString());
        }

        @Test
        @DisplayName("Increment major version")
        void testMajorIncrement() {
            YSpecVersion version = new YSpecVersion(2, 3);
            version.majorIncrement();
            assertEquals("3.3", version.toString());
        }

        @Test
        @DisplayName("Rollback minor version")
        void testMinorRollback() {
            YSpecVersion version = new YSpecVersion(1, 5);
            version.minorRollback();
            assertEquals("1.4", version.toString());
        }

        @Test
        @DisplayName("Rollback major version")
        void testMajorRollback() {
            YSpecVersion version = new YSpecVersion(2, 3);
            version.majorRollback();
            assertEquals("1.3", version.toString());
        }

        @Test
        @DisplayName("Compare equal versions")
        void testCompareEqual() {
            YSpecVersion v1 = new YSpecVersion(2, 5);
            YSpecVersion v2 = new YSpecVersion(2, 5);
            assertEquals(0, v1.compareTo(v2));
            assertTrue(v1.equals(v2));
        }

        @Test
        @DisplayName("Compare versions with different major")
        void testCompareDifferentMajor() {
            YSpecVersion v1 = new YSpecVersion(2, 5);
            YSpecVersion v2 = new YSpecVersion(3, 2);
            assertTrue(v1.compareTo(v2) < 0);
            assertTrue(v2.compareTo(v1) > 0);
        }

        @Test
        @DisplayName("Compare versions with different minor")
        void testCompareDifferentMinor() {
            YSpecVersion v1 = new YSpecVersion(2, 3);
            YSpecVersion v2 = new YSpecVersion(2, 7);
            assertTrue(v1.compareTo(v2) < 0);
            assertTrue(v2.compareTo(v1) > 0);
        }

        @Test
        @DisplayName("Version converts to double")
        void testVersionToDouble() {
            YSpecVersion version = new YSpecVersion(2, 5);
            double value = version.toDouble();
            assertEquals(2.5, value);
        }

        @Test
        @DisplayName("Version with string format 'X' parses correctly")
        void testVersionStringWithoutDecimal() {
            YSpecVersion version = new YSpecVersion("5");
            assertEquals("5.0", version.toString());
            assertEquals(5, version.getMajorVersion());
            assertEquals(0, version.getMinorVersion());
        }

        @Test
        @DisplayName("Invalid version string defaults to 0.1")
        void testInvalidVersionDefaults() {
            YSpecVersion version = new YSpecVersion("not.a.version");
            assertEquals("0.1", version.toString());
        }

        @Test
        @DisplayName("Two equal YSpecVersion have same hash code")
        void testEqualVersionsSameHashCode() {
            YSpecVersion v1 = new YSpecVersion(3, 4);
            YSpecVersion v2 = new YSpecVersion(3, 4);
            assertEquals(v1.hashCode(), v2.hashCode());
        }
    }

    @Nested
    @DisplayName("External Data Gateway Tests")
    class ExternalDataGatewayTests {

        @Test
        @DisplayName("Set external data gateway on net")
        void testSetExternalDataGateway() {
            String gatewayURI = "http://external.example.com/gateway";
            net.setExternalDataGateway(gatewayURI);
            assertEquals(gatewayURI, net.getExternalDataGateway());
        }

        @Test
        @DisplayName("Get external data gateway from net")
        void testGetExternalDataGateway() {
            String gatewayURI = "http://data.provider.com/api";
            net.setExternalDataGateway(gatewayURI);

            String retrieved = net.getExternalDataGateway();
            assertNotNull(retrieved);
            assertEquals(gatewayURI, retrieved);
        }

        @Test
        @DisplayName("External data gateway initially null")
        void testGatewayInitiallyNull() {
            YNet testNet = new YNet("test", specification);
            assertNull(testNet.getExternalDataGateway());
        }
    }

    @Nested
    @DisplayName("Complex Workflow Construction")
    class ComplexWorkflowTests {

        @Test
        @DisplayName("Construct complete workflow with multiple tasks and flows")
        void testCompleteWorkflow() {
            YInputCondition input = new YInputCondition("i", net);
            YAtomicTask request = new YAtomicTask("submit_request", YTask._XOR, YTask._XOR, net);
            YAtomicTask approve = new YAtomicTask("approve", YTask._XOR, YTask._XOR, net);
            YAtomicTask reject = new YAtomicTask("reject", YTask._XOR, YTask._XOR, net);
            YAtomicTask notify = new YAtomicTask("notify", YTask._AND, YTask._AND, net);
            YOutputCondition output = new YOutputCondition("o", net);

            net.setInputCondition(input);
            net.setOutputCondition(output);
            net.addNetElement(request);
            net.addNetElement(approve);
            net.addNetElement(reject);
            net.addNetElement(notify);

            YFlow flow1 = new YFlow(input, request);
            YFlow flow2 = new YFlow(request, approve);
            flow2.setXpathPredicate("/root/approved = true");
            YFlow flow3 = new YFlow(request, reject);
            flow3.setXpathPredicate("/root/approved = false");
            YFlow flow4 = new YFlow(approve, notify);
            YFlow flow5 = new YFlow(reject, notify);
            YFlow flow6 = new YFlow(notify, output);

            input.addPostset(flow1);
            request.addPostset(flow2);
            request.addPostset(flow3);
            approve.addPostset(flow4);
            reject.addPostset(flow5);
            notify.addPostset(flow6);

            assertEquals(4, net.getNetTasks().size());
            assertEquals(2, request.getPostsetElements().size());
        }

        @Test
        @DisplayName("Workflow with composite task")
        void testWorkflowWithCompositeTask() {
            YInputCondition input = new YInputCondition("i", net);
            YCompositeTask subprocess = new YCompositeTask("subprocess", YTask._XOR, YTask._XOR, net);
            YOutputCondition output = new YOutputCondition("o", net);

            net.setInputCondition(input);
            net.setOutputCondition(output);
            net.addNetElement(subprocess);

            input.addPostset(new YFlow(input, subprocess));
            subprocess.addPostset(new YFlow(subprocess, output));

            assertTrue(net.getNetTasks().stream()
                    .anyMatch(t -> t instanceof YCompositeTask));
        }

        @Test
        @DisplayName("Verify net contains input and output conditions")
        void testNetHasConditions() {
            YInputCondition input = new YInputCondition("i", net);
            YAtomicTask task = new YAtomicTask("t", YTask._XOR, YTask._AND, net);
            YOutputCondition output = new YOutputCondition("o", net);

            net.setInputCondition(input);
            net.setOutputCondition(output);
            net.addNetElement(task);

            assertNotNull(net.getInputCondition());
            assertNotNull(net.getOutputCondition());
            assertSame(input, net.getInputCondition());
            assertSame(output, net.getOutputCondition());
        }
    }
}
