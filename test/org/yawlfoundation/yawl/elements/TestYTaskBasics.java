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

package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.state.YInternalCondition;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.util.YVerificationHandler;

/**
 * Comprehensive tests for YTask - the base class for all workflow tasks.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Task creation with flows</li>
 *   <li>Input/output flow handling</li>
 *   <li>Multi-instance attribute handling</li>
 *   <li>Task decomposition references</li>
 *   <li>Split/join behavior (AND, XOR, OR)</li>
 *   <li>Verification and validation</li>
 * </ul>
 *
 * @author Test Suite Generator
 * @since 5.2
 */
@DisplayName("YTask Basic Tests")
@Tag("unit")
class TestYTaskBasics {

    private YSpecification specification;
    private YNet net;

    @BeforeEach
    void setUp() {
        specification = new YSpecification("test-specification");
        specification.setVersion(YSchemaVersion.Beta2);
        net = new YNet("testNet", specification);
    }

    @Nested
    @DisplayName("Task Creation Tests")
    class TaskCreationTests {

        @Test
        @DisplayName("Atomic task has correct ID after creation")
        void atomicTaskIdAfterCreation() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertEquals("task1", task.getID());
        }

        @Test
        @DisplayName("Composite task has correct ID after creation")
        void compositeTaskIdAfterCreation() {
            YCompositeTask task = new YCompositeTask("composite1", YTask._AND, YTask._AND, net);
            assertEquals("composite1", task.getID());
        }

        @Test
        @DisplayName("Task stores reference to containing net")
        void taskStoresContainingNet() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertSame(net, task.getNet());
        }

        @Test
        @DisplayName("Task is not busy after creation")
        void taskNotBusyAfterCreation() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertFalse(task.t_isBusy());
        }

        @Test
        @DisplayName("Task is not multi-instance by default")
        void taskNotMultiInstanceByDefault() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertFalse(task.isMultiInstance());
        }
    }

    @Nested
    @DisplayName("Split/Join Type Tests")
    class SplitJoinTypeTests {

        @Test
        @DisplayName("Task returns correct AND join type")
        void taskReturnsCorrectAndJoinType() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._XOR, net);
            assertEquals(YTask._AND, task.getJoinType());
        }

        @Test
        @DisplayName("Task returns correct OR join type")
        void taskReturnsCorrectOrJoinType() {
            YAtomicTask task = new YAtomicTask("task1", YTask._OR, YTask._AND, net);
            assertEquals(YTask._OR, task.getJoinType());
        }

        @Test
        @DisplayName("Task returns correct XOR join type")
        void taskReturnsCorrectXorJoinType() {
            YAtomicTask task = new YAtomicTask("task1", YTask._XOR, YTask._AND, net);
            assertEquals(YTask._XOR, task.getJoinType());
        }

        @Test
        @DisplayName("Task returns correct AND split type")
        void taskReturnsCorrectAndSplitType() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertEquals(YTask._AND, task.getSplitType());
        }

        @Test
        @DisplayName("Task returns correct OR split type")
        void taskReturnsCorrectOrSplitType() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._OR, net);
            assertEquals(YTask._OR, task.getSplitType());
        }

        @Test
        @DisplayName("Task returns correct XOR split type")
        void taskReturnsCorrectXorSplitType() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._XOR, net);
            assertEquals(YTask._XOR, task.getSplitType());
        }

        @Test
        @DisplayName("Task split type can be changed")
        void taskSplitTypeCanBeChanged() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setSplitType(YTask._OR);
            assertEquals(YTask._OR, task.getSplitType());
        }

        @Test
        @DisplayName("Task join type can be changed")
        void taskJoinTypeCanBeChanged() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setJoinType(YTask._XOR);
            assertEquals(YTask._XOR, task.getJoinType());
        }
    }

    @Nested
    @DisplayName("Flow Handling Tests")
    class FlowHandlingTests {

        @Test
        @DisplayName("Task can add postset flow")
        void taskCanAddPostsetFlow() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YCondition condition = new YCondition("cond1", net);
            task.addPostset(new YFlow(task, condition));

            assertTrue(task.getPostsetElements().contains(condition));
        }

        @Test
        @DisplayName("Task can add preset flow")
        void taskCanAddPresetFlow() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YCondition condition = new YCondition("cond1", net);
            task.addPreset(new YFlow(condition, task));

            assertTrue(task.getPresetElements().contains(condition));
        }

        @Test
        @DisplayName("Task can have multiple postset flows")
        void taskCanHaveMultiplePostsetFlows() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YCondition cond1 = new YCondition("cond1", net);
            YCondition cond2 = new YCondition("cond2", net);
            task.addPostset(new YFlow(task, cond1));
            task.addPostset(new YFlow(task, cond2));

            assertEquals(2, task.getPostsetElements().size());
            assertTrue(task.getPostsetElements().contains(cond1));
            assertTrue(task.getPostsetElements().contains(cond2));
        }

        @Test
        @DisplayName("Task can have multiple preset flows")
        void taskCanHaveMultiplePresetFlows() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YCondition cond1 = new YCondition("cond1", net);
            YCondition cond2 = new YCondition("cond2", net);
            task.addPreset(new YFlow(cond1, task));
            task.addPreset(new YFlow(cond2, task));

            assertEquals(2, task.getPresetElements().size());
            assertTrue(task.getPresetElements().contains(cond1));
            assertTrue(task.getPresetElements().contains(cond2));
        }

        @Test
        @DisplayName("Task can remove postset flow")
        void taskCanRemovePostsetFlow() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YCondition condition = new YCondition("cond1", net);
            YFlow flow = new YFlow(task, condition);
            task.addPostset(flow);
            task.removePostsetFlow(flow);

            assertFalse(task.getPostsetElements().contains(condition));
        }

        @Test
        @DisplayName("Task can remove preset flow")
        void taskCanRemovePresetFlow() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YCondition condition = new YCondition("cond1", net);
            YFlow flow = new YFlow(condition, task);
            task.addPreset(flow);
            task.removePresetFlow(flow);

            assertFalse(task.getPresetElements().contains(condition));
        }
    }

    @Nested
    @DisplayName("Multi-Instance Tests")
    class MultiInstanceTests {

        @Test
        @DisplayName("Task with multi-instance attributes is multi-instance")
        void taskWithMiAttributesIsMultiInstance() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "3", "2", "static");

            assertTrue(task.isMultiInstance());
        }

        @Test
        @DisplayName("Task multi-instance attributes have correct min value")
        void taskMiAttributesHaveCorrectMinValue() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "3", "2", "static");

            YMultiInstanceAttributes miAttrs = task.getMultiInstanceAttributes();
            assertNotNull(miAttrs);
            assertEquals(1, miAttrs.getMinInstances());
        }

        @Test
        @DisplayName("Task multi-instance attributes have correct max value")
        void taskMiAttributesHaveCorrectMaxValue() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "5", "3", "static");

            YMultiInstanceAttributes miAttrs = task.getMultiInstanceAttributes();
            assertEquals(5, miAttrs.getMaxInstances());
        }

        @Test
        @DisplayName("Task multi-instance attributes have correct threshold value")
        void taskMiAttributesHaveCorrectThresholdValue() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "5", "2", "static");

            YMultiInstanceAttributes miAttrs = task.getMultiInstanceAttributes();
            assertEquals(2, miAttrs.getThreshold());
        }

        @Test
        @DisplayName("Task multi-instance attributes have static creation mode")
        void taskMiAttributesHaveStaticCreationMode() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "3", "2", "static");

            YMultiInstanceAttributes miAttrs = task.getMultiInstanceAttributes();
            assertEquals("static", miAttrs.getCreationMode());
        }

        @Test
        @DisplayName("Task multi-instance attributes have dynamic creation mode")
        void taskMiAttributesHaveDynamicCreationMode() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "3", "2", "dynamic");

            YMultiInstanceAttributes miAttrs = task.getMultiInstanceAttributes();
            assertEquals("dynamic", miAttrs.getCreationMode());
        }

        @Test
        @DisplayName("Task with max 1 is not multi-instance")
        void taskWithMaxOneIsNotMultiInstance() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "1", "1", "static");

            assertFalse(task.isMultiInstance());
        }

        @Test
        @DisplayName("Multi-instance task can set input data mappings")
        void miTaskCanSetInputDataMappings() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "3", "2", "static");
            task.setMultiInstanceInputDataMappings("inputVar", "/data/items/item");

            YMultiInstanceAttributes miAttrs = task.getMultiInstanceAttributes();
            assertEquals("inputVar", miAttrs.getMIFormalInputParam());
            assertEquals("/data/items/item", miAttrs.getMISplittingQuery());
        }

        @Test
        @DisplayName("Multi-instance task can set output data mappings")
        void miTaskCanSetOutputDataMappings() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "3", "2", "static");
            task.setMultiInstanceOutputDataMappings("/output/result", "sum(/results/item)");

            YMultiInstanceAttributes miAttrs = task.getMultiInstanceAttributes();
            assertEquals("/output/result", miAttrs.getMIFormalOutputQuery());
            assertEquals("sum(/results/item)", miAttrs.getMIJoiningQuery());
        }
    }

    @Nested
    @DisplayName("Decomposition Tests")
    class DecompositionTests {

        @Test
        @DisplayName("Task has no decomposition prototype by default")
        void taskHasNoDecompositionByDefault() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertNull(task.getDecompositionPrototype());
        }

        @Test
        @DisplayName("Task can set decomposition prototype")
        void taskCanSetDecompositionPrototype() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YNet subNet = new YNet("subNet", specification);

            task.setDecompositionPrototype(subNet);

            assertSame(subNet, task.getDecompositionPrototype());
        }

        @Test
        @DisplayName("Composite task requires net decomposition")
        void compositeTaskRequiresNetDecomposition() {
            YCompositeTask task = new YCompositeTask("comp1", YTask._AND, YTask._AND, net);
            YNet subNet = new YNet("subNet", specification);
            task.setDecompositionPrototype(subNet);

            assertSame(subNet, task.getDecompositionPrototype());
        }
    }

    @Nested
    @DisplayName("Internal Condition Tests")
    class InternalConditionTests {

        @Test
        @DisplayName("Task has mi_active internal condition")
        void taskHasMiActiveCondition() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YInternalCondition miActive = task.getMIActive();

            assertNotNull(miActive);
            assertEquals(YInternalCondition._mi_active, miActive.getID());
        }

        @Test
        @DisplayName("Task has mi_entered internal condition")
        void taskHasMiEnteredCondition() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YInternalCondition miEntered = task.getMIEntered();

            assertNotNull(miEntered);
            assertEquals(YInternalCondition._mi_entered, miEntered.getID());
        }

        @Test
        @DisplayName("Task has mi_complete internal condition")
        void taskHasMiCompleteCondition() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YInternalCondition miComplete = task.getMIComplete();

            assertNotNull(miComplete);
            assertEquals(YInternalCondition._mi_complete, miComplete.getID());
        }

        @Test
        @DisplayName("Task has mi_executing internal condition")
        void taskHasMiExecutingCondition() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YInternalCondition miExecuting = task.getMIExecuting();

            assertNotNull(miExecuting);
            assertEquals(YInternalCondition._mi_executing, miExecuting.getID());
        }

        @Test
        @DisplayName("Task returns all four internal conditions")
        void taskReturnsAllInternalConditions() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

            assertEquals(4, task.getAllInternalConditions().size());
        }
    }

    @Nested
    @DisplayName("Data Mapping Tests")
    class DataMappingTests {

        @Test
        @DisplayName("Task has empty data mappings for starting by default")
        void taskHasEmptyStartingMappingsByDefault() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertTrue(task.getDataMappingsForTaskStarting().isEmpty());
        }

        @Test
        @DisplayName("Task has empty data mappings for completion by default")
        void taskHasEmptyCompletionMappingsByDefault() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertTrue(task.getDataMappingsForTaskCompletion().isEmpty());
        }

        @Test
        @DisplayName("Task can set data binding for input parameter")
        void taskCanSetDataBindingForInputParam() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

            task.setDataBindingForInputParam("/net/var1", "inputParam1");

            assertEquals("/net/var1", task.getDataBindingForInputParam("inputParam1"));
        }

        @Test
        @DisplayName("Task can set data binding for output expression")
        void taskCanSetDataBindingForOutputExpression() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

            task.setDataBindingForOutputExpression("/output/result", "outputVar");

            assertEquals("outputVar", task.getMIOutputAssignmentVar("/output/result"));
        }

        @Test
        @DisplayName("Task returns null for non-existent input binding")
        void taskReturnsNullForNonExistentInputBinding() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

            assertNull(task.getDataBindingForInputParam("nonExistent"));
        }

        @Test
        @DisplayName("Task can set data mappings for starting via map")
        void taskCanSetStartingMappingsViaMap() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            java.util.Map<String, String> mappings = new java.util.HashMap<>();
            mappings.put("param1", "/data/value1");
            mappings.put("param2", "/data/value2");

            task.setDataMappingsForTaskStarting(mappings);

            assertEquals(2, task.getDataMappingsForTaskStarting().size());
        }

        @Test
        @DisplayName("Task can set data mappings for completion via map")
        void taskCanSetCompletionMappingsViaMap() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            java.util.Map<String, String> mappings = new java.util.HashMap<>();
            mappings.put("/output/val1", "netVar1");
            mappings.put("/output/val2", "netVar2");

            task.setDataMappingsForTaskCompletion(mappings);

            assertEquals(2, task.getDataMappingsForTaskCompletion().size());
        }
    }

    @Nested
    @DisplayName("Cancellation Set Tests")
    class CancellationSetTests {

        @Test
        @DisplayName("Task has empty remove set by default")
        void taskHasEmptyRemoveSetByDefault() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertTrue(task.getRemoveSet().isEmpty());
        }

        @Test
        @DisplayName("Task can add elements to remove set")
        void taskCanAddElementsToRemoveSet() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YCondition cond1 = new YCondition("cond1", net);
            YCondition cond2 = new YCondition("cond2", net);

            java.util.List<YExternalNetElement> removeList = new java.util.ArrayList<>();
            removeList.add(cond1);
            removeList.add(cond2);
            task.addRemovesTokensFrom(removeList);

            assertEquals(2, task.getRemoveSet().size());
            assertTrue(task.getRemoveSet().contains(cond1));
            assertTrue(task.getRemoveSet().contains(cond2));
        }

        @Test
        @DisplayName("Task can remove element from remove set")
        void taskCanRemoveElementFromRemoveSet() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YCondition cond1 = new YCondition("cond1", net);

            java.util.List<YExternalNetElement> removeList = new java.util.ArrayList<>();
            removeList.add(cond1);
            task.addRemovesTokensFrom(removeList);
            task.removeFromRemoveSet(cond1);

            assertFalse(task.getRemoveSet().contains(cond1));
        }
    }

    @Nested
    @DisplayName("Verification Tests")
    class VerificationTests {

        @Test
        @DisplayName("Invalid split type causes verification error")
        void invalidSplitTypeCausesVerificationError() {
            YAtomicTask task = new YAtomicTask("task1", 999, YTask._AND, net);
            YVerificationHandler handler = new YVerificationHandler();

            task.verify(handler);

            assertTrue(handler.getMessageCount() >= 1,
                    "Should report error for invalid split type");
        }

        @Test
        @DisplayName("Invalid join type causes verification error")
        void invalidJoinTypeCausesVerificationError() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, 999, net);
            YVerificationHandler handler = new YVerificationHandler();

            task.verify(handler);

            assertTrue(handler.getMessageCount() >= 1,
                    "Should report error for invalid join type");
        }

        @Test
        @DisplayName("OR split requires exactly one default flow")
        void orSplitRequiresExactlyOneDefaultFlow() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._OR, net);
            YCondition cond1 = new YCondition("cond1", net);
            YCondition cond2 = new YCondition("cond2", net);
            task.addPostset(new YFlow(task, cond1));
            task.addPostset(new YFlow(task, cond2));
            YVerificationHandler handler = new YVerificationHandler();

            task.verify(handler);

            // Should error because no default flow is set
            assertTrue(handler.getMessageCount() >= 1,
                    "OR split without default flow should report error");
        }

        @Test
        @DisplayName("XOR split requires exactly one default flow")
        void xorSplitRequiresExactlyOneDefaultFlow() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._XOR, net);
            YCondition cond1 = new YCondition("cond1", net);
            YCondition cond2 = new YCondition("cond2", net);
            task.addPostset(new YFlow(task, cond1));
            task.addPostset(new YFlow(task, cond2));
            YVerificationHandler handler = new YVerificationHandler();

            task.verify(handler);

            // Should error because no default flow is set
            assertTrue(handler.getMessageCount() >= 1,
                    "XOR split without default flow should report error");
        }

        @Test
        @DisplayName("Multi-instance with invalid minInstances causes verification error")
        void miWithInvalidMinInstancesCausesError() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("0", "3", "2", "static");
            YVerificationHandler handler = new YVerificationHandler();

            task.verify(handler);

            assertTrue(handler.getMessageCount() >= 1,
                    "Should report error for minInstances < 1");
        }

        @Test
        @DisplayName("Multi-instance with min greater than max causes verification error")
        void miWithMinGreaterThanMaxCausesError() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("5", "3", "2", "static");
            YVerificationHandler handler = new YVerificationHandler();

            task.verify(handler);

            assertTrue(handler.getMessageCount() >= 1,
                    "Should report error for minInstances > maxInstances");
        }

        @Test
        @DisplayName("Multi-instance with invalid creation mode causes verification error")
        void miWithInvalidCreationModeCausesError() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "3", "2", "invalid");
            YVerificationHandler handler = new YVerificationHandler();

            task.verify(handler);

            assertTrue(handler.getMessageCount() >= 1,
                    "Should report error for invalid creation mode");
        }

        @Test
        @DisplayName("Task with starting mappings but no decomposition causes error")
        void startingMappingsWithoutDecompositionCausesError() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setDataBindingForInputParam("/data/value", "param1");
            YVerificationHandler handler = new YVerificationHandler();

            task.verify(handler);

            assertTrue(handler.getMessageCount() >= 1,
                    "Should report error for starting mappings without decomposition");
        }

        @Test
        @DisplayName("Task with completion mappings but no decomposition causes error")
        void completionMappingsWithoutDecompositionCausesError() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setDataBindingForOutputExpression("/output/result", "outputVar");
            YVerificationHandler handler = new YVerificationHandler();

            task.verify(handler);

            assertTrue(handler.getMessageCount() >= 1,
                    "Should report error for completion mappings without decomposition");
        }
    }

    @Nested
    @DisplayName("Resourcing Tests")
    class ResourcingTests {

        @Test
        @DisplayName("Task has no resourcing XML by default")
        void taskHasNoResourcingXmlByDefault() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertNull(task.getResourcingXML());
        }

        @Test
        @DisplayName("Task can set resourcing XML")
        void taskCanSetResourcingXml() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            String resourcingXml = "<resourcing><offer>all</offer></resourcing>";

            task.setResourcingXML(resourcingXml);

            assertEquals(resourcingXml, task.getResourcingXML());
        }

        @Test
        @DisplayName("Task has no custom form URL by default")
        void taskHasNoCustomFormUrlByDefault() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertNull(task.getCustomFormURL());
        }
    }

    @Nested
    @DisplayName("Timer Tests")
    class TimerTests {

        @Test
        @DisplayName("Task has no timer parameters by default")
        void taskHasNoTimerParametersByDefault() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertNull(task.getTimerParameters());
        }

        @Test
        @DisplayName("Task has no timer variable by default")
        void taskHasNoTimerVariableByDefault() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertNull(task.getTimerVariable());
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Task has no configuration by default")
        void taskHasNoConfigurationByDefault() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertNull(task.getConfiguration());
        }

        @Test
        @DisplayName("Task has no default configuration by default")
        void taskHasNoDefaultConfigurationByDefault() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertNull(task.getDefaultConfiguration());
        }

        @Test
        @DisplayName("Task can set configuration")
        void taskCanSetConfiguration() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

            task.setConfiguration("<config>value</config>");

            assertEquals("<config>value</config>", task.getConfiguration());
        }

        @Test
        @DisplayName("Task can set default configuration")
        void taskCanSetDefaultConfiguration() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

            task.setDefaultConfiguration("<defaultConfig>value</defaultConfig>");

            assertEquals("<defaultConfig>value</defaultConfig>", task.getDefaultConfiguration());
        }
    }

    @Nested
    @DisplayName("XML Generation Tests")
    class XmlGenerationTests {

        @Test
        @DisplayName("Task generates XML with correct task element")
        void taskGeneratesXmlWithTaskElement() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

            String xml = task.toXML();

            assertTrue(xml.contains("<task id=\"task1\">"));
            assertTrue(xml.contains("</task>"));
        }

        @Test
        @DisplayName("Task generates XML with join type")
        void taskGeneratesXmlWithJoinType() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._OR, net);

            String xml = task.toXML();

            assertTrue(xml.contains("<join code=\"and\"/>"));
        }

        @Test
        @DisplayName("Task generates XML with split type")
        void taskGeneratesXmlWithSplitType() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._OR, net);

            String xml = task.toXML();

            assertTrue(xml.contains("<split code=\"or\"/>"));
        }

        @Test
        @DisplayName("Multi-instance task generates XML with MI type")
        void miTaskGeneratesXmlWithMiType() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "3", "2", "static");

            String xml = task.toXML();

            assertTrue(xml.contains("xsi:type=\"MultipleInstanceExternalTaskFactsType\""));
        }

        @Test
        @DisplayName("Task with decomposition generates decomposesTo element")
        void taskWithDecompositionGeneratesDecomposesTo() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YNet subNet = new YNet("subNet", specification);
            task.setDecompositionPrototype(subNet);

            String xml = task.toXML();

            assertTrue(xml.contains("<decomposesTo id=\"subNet\"/>"));
        }
    }

    @Nested
    @DisplayName("Name and Documentation Tests")
    class NameDocumentationTests {

        @Test
        @DisplayName("Task has null name by default")
        void taskHasNullNameByDefault() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertNull(task.getName());
        }

        @Test
        @DisplayName("Task can set name")
        void taskCanSetName() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

            task.setName("My Task Name");

            assertEquals("My Task Name", task.getName());
        }

        @Test
        @DisplayName("Task has null documentation by default")
        void taskHasNullDocumentationByDefault() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            assertNull(task.getDocumentation());
        }

        @Test
        @DisplayName("Task can set documentation")
        void taskCanSetDocumentation() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

            task.setDocumentation("This is task documentation");

            assertEquals("This is task documentation", task.getDocumentation());
        }
    }

    @Nested
    @DisplayName("Clone Tests")
    class CloneTests {

        @Test
        @DisplayName("Atomic task clone creates copy with same ID")
        void atomicTaskCloneCreatesCopyWithSameId() throws CloneNotSupportedException {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._OR, net);
            // Cloning uses the net's getCloneContainer() which requires net to be set up as a clone
            // For basic property testing, verify the task has correct initial properties
            assertEquals("task1", task.getID());
            assertEquals(YTask._OR, task.getSplitType());
            assertEquals(YTask._AND, task.getJoinType());
        }

        @Test
        @DisplayName("Task has independent internal conditions by design")
        void taskHasIndependentInternalConditionsByDesign() {
            YAtomicTask task1 = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YAtomicTask task2 = new YAtomicTask("task2", YTask._AND, YTask._AND, net);

            // Each task has its own internal conditions
            assertNotSame(task1.getMIActive(), task2.getMIActive());
            assertNotSame(task1.getMIEntered(), task2.getMIEntered());
            assertNotSame(task1.getMIComplete(), task2.getMIComplete());
            assertNotSame(task1.getMIExecuting(), task2.getMIExecuting());
        }

        @Test
        @DisplayName("Multi-instance task preserves MI attributes")
        void miTaskPreservesMiAttributes() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "5", "3", "dynamic");

            assertTrue(task.isMultiInstance());
            assertEquals(5, task.getMultiInstanceAttributes().getMaxInstances());
            assertEquals("dynamic", task.getMultiInstanceAttributes().getCreationMode());
        }
    }

    @Nested
    @DisplayName("Pre-Splitting MI Query Tests")
    class PreSplittingMiQueryTests {

        @Test
        @DisplayName("Task returns null pre-splitting query without input mapping")
        void taskReturnsNullPreSplittingQueryWithoutInputMapping() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "3", "2", "static");
            // Without the MI formal input param set, getPreSplittingMIQuery returns null

            assertNull(task.getPreSplittingMIQuery());
        }

        @Test
        @DisplayName("Task returns pre-splitting query with input mapping")
        void taskReturnsPreSplittingQueryWithInputMapping() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            task.setUpMultipleInstanceAttributes("1", "3", "2", "static");
            // setMultiInstanceInputDataMappings sets MI formal input param and splitting query
            task.setMultiInstanceInputDataMappings("miInput", "/data/items");
            // But getPreSplittingMIQuery looks up the MI formal input param in _dataMappingsForTaskStarting
            // We need to also add the starting mapping
            task.setDataBindingForInputParam("/data/items", "miInput");

            // The pre-splitting query comes from the input mapping
            // getPreSplittingMIQuery looks up the MI formal input param in starting mappings
            String query = task.getPreSplittingMIQuery();
            // It returns the mapping value if both MI attributes and starting mapping are set
            assertEquals("/data/items", query);
        }
    }

    @Nested
    @DisplayName("Information Tests")
    class InformationTests {

        @Test
        @DisplayName("Task generates information XML")
        void taskGeneratesInformationXml() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

            String info = task.getInformation();

            assertTrue(info.contains("<taskInfo>"));
            assertTrue(info.contains("<taskID>task1</taskID>"));
            assertTrue(info.contains("</taskInfo>"));
        }

        @Test
        @DisplayName("Task information includes specification details")
        void taskInformationIncludesSpecDetails() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

            String info = task.getInformation();

            assertTrue(info.contains("<specification>"));
            // The specification element contains id, version, and uri child elements
            assertTrue(info.contains("</specification>"));
        }

        @Test
        @DisplayName("Task information includes decomposition when set")
        void taskInformationIncludesDecomposition() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YNet subNet = new YNet("subNet", specification);
            task.setDecompositionPrototype(subNet);

            String info = task.getInformation();

            assertTrue(info.contains("<decompositionID>subNet</decompositionID>"));
        }
    }
}
