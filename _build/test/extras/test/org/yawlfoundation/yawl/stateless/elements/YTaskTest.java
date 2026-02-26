/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.stateless.elements;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.yawlfoundation.yawl.exceptions.YQueryException;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.jdom2.Document;
import org.jdom2.Element;

import java.net.URL;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for YTask class.
 * Tests all public methods, edge cases, and exception conditions.
 */
class YTaskTest {

    private YNet testNet;
    private YSpecification testSpec;
    private YTask testTask;
    private YIdentifier testIdentifier;

    @BeforeEach
    void setUp() throws Exception {
        testSpec = new YSpecification("testSpec", "1.0", "test://uri");
        testNet = new YNet("testNet", testSpec);
        testTask = new YAtomicTask("task1", YTask._AND, YTask._XOR, testNet);
        testIdentifier = new YIdentifier("case1");
    }

    @Nested
    @DisplayName("Basic Task Properties")
    class BasicPropertiesTest {

        @Test
        @DisplayName("Task with valid join and split types")
        void taskWithValidJoinSplitTypes() {
            YTask task = new YAtomicTask("testTask", YTask._AND, YTask._OR, testNet);
            assertEquals(YTask._AND, task.getJoinType());
            assertEquals(YTask._OR, task.getSplitType());
        }

        @Test
        @DisplayName("Task ID and name management")
        void taskIdentityManagement() {
            assertEquals("task1", testTask.getID());
            testTask.setName("Test Task");
            assertEquals("Test Task", testTask.getName());
            assertNull(testTask.getDocumentation());
        }

        @Test
        @DisplayName("Task documentation handling")
        void taskDocumentationHandling() {
            String doc = "This is a test task";
            testTask.setDocumentation(doc);
            assertEquals(doc, testTask.getDocumentation());
            assertEquals(doc, testTask.getDocumentationPreParsed());
        }
    }

    @Nested
    @DisplayName("Task State Management")
    class TaskStateManagementTest {

        @Test
        @DisplayName("Task busy state when not executing")
        void taskNotBusyInitially() {
            assertFalse(testTask.t_isBusy());
            assertNull(testTask.getI());
        }

        @Test
        @DisplayName("Task becomes busy when identifier is set")
        void taskBecomesBusyWithIdentifier() {
            testTask.setI(testIdentifier);
            assertTrue(testTask.t_isBusy());
        }

        @Test
        @DisplayName("Task enabled state with XOR join")
        void taskEnabledStateXORJoin() {
            // XOR join should be enabled when exactly one preset has identifier
            YCondition preset1 = new YCondition("cond1", testNet);
            YCondition preset2 = new YCondition("cond2", testNet);

            testTask.addPreset(new YFlow(preset1, testTask));
            testTask.addPreset(new YFlow(preset2, testTask));

            preset1.add(testIdentifier);
            assertTrue(testTask.t_enabled(testIdentifier));

            preset2.add(testIdentifier);
            assertFalse(testTask.t_enabled(testIdentifier)); // XOR should not be enabled with two
        }

        @Test
        @DisplayName("Task enabled state with AND join")
        void taskEnabledStateANDJoin() {
            // Change task to AND join
            testTask.setJoinType(YTask._AND);
            YCondition preset1 = new YCondition("cond1", testNet);
            YCondition preset2 = new YCondition("cond2", testNet);

            testTask.addPreset(new YFlow(preset1, testTask));
            testTask.addPreset(new YFlow(preset2, testTask));

            // AND join should only be enabled when all presets have identifiers
            assertFalse(testTask.t_enabled(testIdentifier));

            preset1.add(testIdentifier);
            assertFalse(testTask.t_enabled(testIdentifier));

            preset2.add(testIdentifier);
            assertTrue(testTask.t_enabled(testIdentifier));
        }

        @Test
        @DisplayName("Task cannot fire when not enabled")
        void taskCannotFireWhenNotEnabled() {
            YIdentifier result = testTask.t_fire();
            assertNull(result);
        }

        @Test
        @DisplayName("Task cannot fire when already busy")
        void taskCannotFireWhenBusy() {
            testTask.setI(testIdentifier);
            assertThrows(YStateException.class, () -> testTask.t_fire());
        }
    }

    @Nested
    @DisplayName("Multi-Instance Task Support")
    class MultiInstanceTaskTest {

        @Test
        @DisplayName("Task is not multi-instance by default")
        void taskNotMultiInstanceByDefault() {
            assertFalse(testTask.isMultiInstance());
            assertNull(testTask.getMultiInstanceAttributes());
        }

        @Test
        @DisplayName("Setting up multi-instance attributes")
        void setUpMultiInstanceAttributes() {
            testTask.setUpMultipleInstanceAttributes(
                "count(\"instance\")",
                "10",
                "5",
                "static"
            );

            assertTrue(testTask.isMultiInstance());
            assertNotNull(testTask.getMultiInstanceAttributes());
            assertEquals("static", testTask.getMultiInstanceAttributes().getCreationMode());
        }

        @Test
        @DisplayName("Multi-instance data mappings")
        void multiInstanceDataMappings() {
            testTask.setUpMultipleInstanceAttributes(
                "count(\"instance\")",
                "10",
                "5",
                "dynamic"
            );

            testTask.setMultiInstanceInputDataMappings("inputParam", "$data/*");
            testTask.setMultiInstanceOutputDataMappings("$output/*", "aggregate()");

            assertEquals("inputParam", testTask.getMultiInstanceAttributes().getMIFormalInputParam());
            assertEquals("$data/*", testTask.getMultiInstanceAttributes().getUniqueInputMISplittingQuery());
        }
    }

    @Nested
    @DisplayName("Task Data Management")
    class TaskDataManagementTest {

        @Test
        @DisplayName("Task data mappings for starting")
        void taskDataMappingsForStarting() {
            Map<String, String> mappings = new HashMap<>();
            mappings.put("param1", "$input/data1");
            mappings.put("param2", "$input/data2");

            testTask.setDataMappingsForTaskStarting(mappings);

            assertEquals(2, testTask.getDataMappingsForTaskStarting().size());
            assertEquals("$input/data1", testTask.getDataMappingsForTaskStarting().get("param1"));
        }

        @Test
        @DisplayName("Task data mappings for completion")
        void taskDataMappingsForCompletion() {
            Map<String, String> mappings = new HashMap<>();
            mappings.put("$output/data1", "result1");
            mappings.put("$output/data2", "result2");

            testTask.setDataMappingsForTaskCompletion(mappings);

            assertEquals(2, testTask.getDataMappingsForTaskCompletion().size());
            assertEquals("result1", testTask.getDataMappingsForTaskCompletion().get("$output/data1"));
        }

        @Test
        @DisplayName("Task binding for input parameters")
        void taskInputParameterBinding() {
            testTask.setDataBindingForInputParam("$input/data", "inputParam");
            assertEquals("$input/data", testTask.getDataBindingForInputParam("inputParam"));
        }

        @Test
        @DisplayName("Task binding for output parameters")
        void taskOutputParameterBinding() {
            testTask.setDataBindingForOutputExpression("$output/result", "outputVar");
            assertEquals("$output/result", testTask.getDataBindingForOutputParam("outputVar"));
        }
    }

    @Nested
    @DisplayName("Task Configuration and Settings")
    class TaskConfigurationTest {

        @Test
        @DisplayName("Task resourcing XML configuration")
        void taskResourcingConfiguration() {
            String resourcingXML = "<resourcing><human/><role/></resourcing>";
            testTask.setResourcingXML(resourcingXML);
            assertEquals(resourcingXML, testTask.getResourcingXML());
        }

        @Test
        @DisplayName("Task custom form URL")
        void taskCustomFormURL() throws Exception {
            URL formURL = new URL("http://example.com/custom-form.html");
            testTask.setCustomFormURI(formURL);
            assertEquals(formURL, testTask.getCustomFormURL());
        }

        @Test
        @DisplayName("Task timer parameters")
        void taskTimerParameters() {
            YTimerParameters timerParams = new YTimerParameters();
            timerParams.setTimerDuration("PT30S");

            testTask.setTimerParameters(timerParams);
            assertEquals(timerParams, testTask.getTimerParameters());
            assertNotNull(testTask.getTimerVariable());
        }

        @Test
        @DisplayName("Task configuration settings")
        void taskConfigurationSettings() {
            String config = "<configuration>test</configuration>";
            String defaultConfig = "<default>default</default>";

            testTask.setConfiguration(config);
            testTask.setDefaultConfiguration(defaultConfig);

            assertEquals(config, testTask.getConfiguration());
            assertEquals(defaultConfig, testTask.getDefaultConfiguration());
        }
    }

    @Nested
    @DisplayName("Task Cancellation and Removal")
    class TaskCancellationTest {

        @Test
        @DisplayName("Task cancellation")
        void taskCancellation() {
            testTask.setI(testIdentifier);
            testTask.cancel();
            assertFalse(testTask.t_isBusy());
            assertNull(testTask.getI());
        }

        @Test
        @DisplayName("Task remove set management")
        void taskRemoveSetManagement() {
            YCondition condition = new YCondition("cond1", testNet);
            YTask otherTask = new YAtomicTask("otherTask", YTask._AND, YTask._XOR, testNet);

            testTask.addRemovesTokensFrom(List.of(condition, otherTask));

            assertNotNull(testTask.getRemoveSet());
            assertEquals(2, testTask.getRemoveSet().size());
        }

        @Test
        @DisplayName("Removing from remove set")
        void removeFromRemoveSet() {
            YCondition condition = new YCondition("cond1", testNet);
            testTask.addRemovesTokensFrom(List.of(condition));

            testTask.removeFromRemoveSet(condition);
            assertTrue(testTask.getRemoveSet().isEmpty());
        }
    }

    @Nested
    @DisplayName("Task XML Generation")
    class TaskXMLGenerationTest {

        @Test
        @DisplayName("Task toXML basic structure")
        void taskToXMLBasicStructure() {
            String xml = testTask.toXML();
            assertTrue(xml.contains("<task id=\"task1\">"));
            assertTrue(xml.contains("<join code=\"and\"/>"));
            assertTrue(xml.contains("<split code=\"xor\"/>"));
            assertTrue(xml.contains("</task>"));
        }

        @Test
        @DisplayName("Task toXML with multi-instance")
        void taskToXMLWithMultiInstance() {
            testTask.setUpMultipleInstanceAttributes("count", "10", "5", "static");
            String xml = testTask.toXML();
            assertTrue(xml.contains("xsi:type=\"MultipleInstanceExternalTaskFactsType\""));
        }

        @Test
        @DisplayName("Task toXML with data mappings")
        void taskToXMLWithDataMappings() {
            Map<String, String> mappings = new HashMap<>();
            mappings.put("param1", "$input/data");
            testTask.setDataMappingsForTaskStarting(mappings);

            String xml = testTask.toXML();
            assertTrue(xml.contains("<startingMappings>"));
            assertTrue(xml.contains("query=\"$input/data\""));
        }
    }

    @Nested
    @DisplayName("Task Information Generation")
    class TaskInformationTest {

        @Test
        @DisplayName("Task information generation")
        void taskInformationGeneration() {
            testTask.setName("Test Task");
            testTask.setDocumentation("Test documentation");

            String info = testTask.getInformation();
            assertTrue(info.contains("<taskInfo>"));
            assertTrue(info.contains("<taskID>task1</taskID>"));
            assertTrue(info.contains("<taskName>Test Task</taskName>"));
        }
    }

    @Nested
    @DisplayName("Task Verification")
    class TaskVerificationTest {

        @Test
        @DisplayName("Task with invalid join type")
        void taskWithInvalidJoinType() {
            YTask invalidTask = new YAtomicTask("invalid", 999, YTask._XOR, testNet);
            assertThrows(RuntimeException.class, () -> invalidTask.getJoinType());
        }

        @Test
        @DisplayName("Task with invalid split type")
        void taskWithInvalidSplitType() {
            YTask invalidTask = new YAtomicTask("invalid", YTask._AND, 999, testNet);
            assertThrows(RuntimeException.class, () -> invalidTask.getSplitType());
        }

        @Test
        @DisplayName("Task verification with flows")
        void taskVerificationWithFlows() {
            YCondition condition = new YCondition("cond1", testNet);
            testTask.addPreset(new YFlow(condition, testTask));

            // Should not throw exception
            assertDoesNotThrow(() -> testTask.verify(new org.yawlfoundation.yawl.util.YVerificationHandler()));
        }
    }

    @Nested
    @DisplayName("Task Edge Cases and Error Handling")
    class TaskEdgeCasesTest {

        @Test
        @DisplayName("Task with null identifier")
        void taskWithNullIdentifier() {
            testTask.setI(null);
            assertFalse(testTask.t_isBusy());
        }

        @Test
        @DisplayName("Task rollback functionality")
        void taskRollback() {
            testTask.setI(testIdentifier);
            testTask.rollbackFired(testIdentifier);
            assertFalse(testTask.t_isBusy());
        }

        @Test
        @DisplayName("Task clone functionality")
        void taskClone() throws CloneNotSupportedException {
            YTask clonedTask = (YTask) testTask.clone();
            assertEquals(testTask.getID(), clonedTask.getID());
            assertNotSame(testTask, clonedTask);
        }
    }
}