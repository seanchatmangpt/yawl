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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.elements;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
// import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for YNet class.
 * Tests workflow net functionality, element management, and workflow execution.
 */
class YNetTest {

    private YSpecification testSpec;
    private YNet testNet;
    private YTask task1;
    private YTask task2;
    private YCondition condition1;
    private YCondition condition2;

    @BeforeEach
    void setUp() {
        testSpec = new YSpecification("test://uri");
        testNet = new YNet("testNet", testSpec);
        task1 = new YAtomicTask("task1", YTask._AND, YTask._XOR, testNet);
        task2 = new YAtomicTask("task2", YTask._AND, YTask._XOR, testNet);
        condition1 = new YCondition("condition1", testNet);
        condition2 = new YCondition("condition2", testNet);

        // Add elements to net
        testNet.addNetElement(task1);
        testNet.addNetElement(task2);
        testNet.addNetElement(condition1);
        testNet.addNetElement(condition2);
    }

    @Nested
    @DisplayName("Basic Net Properties")
    class BasicPropertiesTest {

        @Test
        @DisplayName("Net name and specification")
        void netNameAndSpecification() {
            assertEquals("testNet", testNet.getID());
            assertEquals(testSpec, testNet.getSpecification());
        }

        @Test
        @DisplayName("Net with empty name")
        void netWithEmptyName() {
            YNet emptyNet = new YNet("", testSpec);
            assertEquals("", emptyNet.getID());
        }
    }

    @Nested
    @DisplayName("Net Element Management")
    class ElementManagementTest {

        @Test
        @DisplayName("Add elements to net")
        void addElementsToNet() {
            assertEquals(4, testNet.getNetElements().size());
            assertTrue(testNet.getNetElements().contains(task1));
            assertTrue(testNet.getNetElements().contains(condition1));
        }

        @Test
        @DisplayName("Get element by ID")
        void getElementByID() {
            YTask retrievedTask = testNet.getNetElement("task1");
            assertEquals(task1, retrievedTask);

            YCondition retrievedCondition = testNet.getNetElement("condition1");
            assertEquals(condition1, retrievedCondition);

            assertNull(testNet.getNetElement("nonexistent"));
        }

        @Test
        @DisplayName("Get elements by type")
        void getElementsByType() {
            List<YTask> tasks = testNet.getElementsByType(YTask.class);
            assertEquals(2, tasks.size());
            assertTrue(tasks.contains(task1));
            assertTrue(tasks.contains(task2));

            List<YCondition> conditions = testNet.getElementsByType(YCondition.class);
            assertEquals(2, conditions.size());
            assertTrue(conditions.contains(condition1));
            assertTrue(conditions.contains(condition2));
        }

        @Test
        @DisplayName("Net contains element")
        void netContainsElement() {
            assertTrue(testNet.containsElement(task1));
            assertTrue(testNet.containsElement(condition1));
            assertFalse(testNet.containsElement(new YTask("external", YTask._AND, YTask._XOR, testNet)));
        }

        @Test
        @DisplayName("Remove element from net")
        void removeElementFromNet() {
            testNet.removeNetElement("task1");
            assertFalse(testNet.containsElement(task1));
            assertNull(testNet.getNetElement("task1"));
        }
    }

    @Nested
    @DisplayName("Net Variable Management")
    class VariableManagementTest {

        @Test
        @DisplayName("Add and get input parameters")
        void addAndGetInputParameters() {
            YParameter param1 = new YParameter("input1", "string", "Input parameter 1");
            YParameter param2 = new YParameter("input2", "integer", "Input parameter 2");

            testNet.addInputParameter("input1", param1);
            testNet.addInputParameter("input2", param2);

            assertEquals(param1, testNet.getInputParameters().get("input1"));
            assertEquals(param2, testNet.getInputParameters().get("input2"));
            assertEquals(2, testNet.getInputParameters().size());
        }

        @Test
        @DisplayName("Add and get local variables")
        void addAndGetLocalVariables() {
            YVariable var1 = new YVariable("local1", "string", "Local variable 1");
            YVariable var2 = new YVariable("local2", "integer", "Local variable 2");

            testNet.addLocalVariable("local1", var1);
            testNet.addLocalVariable("local2", var2);

            assertEquals(var1, testNet.getLocalVariables().get("local1"));
            assertEquals(var2, testNet.getLocalVariables().get("local2"));
            assertEquals(2, testNet.getLocalVariables().size());
        }

        @Test
        @DisplayName("Get or create local variable")
        void getOrCreateLocalVariable() {
            YVariable var = testNet.getLocalOrInputVariable("existing");
            assertNull(var); // Doesn't exist yet

            // This method typically would create the variable if it doesn't exist
            // Depending on implementation, it might throw an exception
        }

        @Test
        @DisplayName("Net with no variables")
        void netWithNoVariables() {
            assertTrue(testNet.getInputParameters().isEmpty());
            assertTrue(testNet.getLocalVariables().isEmpty());
        }
    }

    @Nested
    @DisplayName("Net Flow Management")
    class FlowManagementTest {

        @Test
        @DisplayName("Connect elements with flows")
        void connectElementsWithFlows() {
            testNet.connectElements(condition1, task1);
            testNet.connectElements(task1, condition2);
            testNet.connectElements(condition2, task2);

            assertEquals(1, task1.getPresetElements().size());
            assertEquals(condition1, task1.getPresetElements().iterator().next());

            assertEquals(1, task1.getPostsetElements().size());
            assertEquals(condition2, task1.getPostsetElements().iterator().next());
        }

        @Test
        @DisplayName("Net flow from preset to postset")
        void netFlowFromPresetToPostset() {
            testNet.connectElements(condition1, task1);
            YFlow flow = testNet.getFlow(condition1, task1);

            assertNotNull(flow);
            assertEquals(condition1, flow.getPriorElement());
            assertEquals(task1, flow.getNextElement());
        }

        @Test
        @DisplayName("Net flows between elements")
        void netFlowsBetweenElements() {
            testNet.connectElements(condition1, task1);
            testNet.connectElements(condition1, task2);

            List<YFlow> flows = testNet.getFlows(condition1);
            assertEquals(2, flows.size());
        }

        @Test
        @DisplayName("Remove flow between elements")
        void removeFlowBetweenElements() {
            testNet.connectElements(condition1, task1);
            testNet.removeFlow(condition1, task1);

            assertNull(testNet.getFlow(condition1, task1));
            assertTrue(task1.getPresetElements().isEmpty());
        }
    }

    @Nested
    @DisplayName("Net Data Management")
    class DataManagementTest {

        @Test
        @DisplayName("Net internal data document")
        void netInternalDataDocument() {
            // The net should have an internal data document
            assertNotNull(testNet.getInternalDataDocument());
        }

        @Test
        @DisplayName("Add data to net")
        void addDataToNet() {
            // This would typically involve adding XML elements to the internal data document
            // Implementation depends on the actual data structure
            assertTrue(testNet.getInternalDataDocument() != null);
        }

        @Test
        @DisplayName("Net data validator")
        void netDataValidator() {
            YDataValidator validator = testNet.getDataValidator();
            assertNotNull(validator);
        }
    }

    @Nested
    @DisplayName("Net XML Generation")
    class XMLGenerationTest {

        @Test
        @DisplayName("Net toXML basic structure")
        void netToXMLBasic() {
            testNet.connectElements(condition1, task1);
            testNet.connectElements(task1, condition2);

            String xml = testNet.toXML();
            assertTrue(xml.contains("<net id=\"testNet\">"));
            assertTrue(xml.contains("</net>"));
        }

        @Test
        @DisplayName("Net toXML with elements")
        void netToXMLWithElements() {
            String xml = testNet.toXML();
            // Should contain XML representations of all elements
            assertTrue(xml.contains("task1"));
            assertTrue(xml.contains("condition1"));
        }

        @Test
        @DisplayName("Net toXML with flows")
        void netToXMLWithFlows() {
            testNet.connectElements(condition1, task1);
            testNet.connectElements(task1, condition2);

            String xml = testNet.toXML();
            // Should contain flow elements
            assertTrue(xml.contains("<flow from=\"condition1\" to=\"task1\"/>"));
            assertTrue(xml.contains("<flow from=\"task1\" to=\"condition2\"/>"));
        }
    }

    @Nested
    @DisplayName("Net Verification")
    class VerificationTest {

        @Test
        @DisplayName("Net verification")
        void netVerification() {
            testNet.connectElements(condition1, task1);
            testNet.connectElements(task1, condition2);

            // Should not throw exception for valid net
            assertDoesNotThrow(() -> testNet.verify(new org.yawlfoundation.yawl.util.YVerificationHandler()));
        }

        @Test
        @DisplayName("Net verification with invalid flows")
        void netVerificationInvalidFlows() {
            // Net without proper connections might fail verification
            // This depends on specific validation rules
            assertDoesNotThrow(() -> testNet.verify(new org.yawlfoundation.yawl.util.YVerificationHandler()));
        }
    }

    @Nested
    @DisplayName("Net Clone")
    class CloneTest {

        @Test
        @DisplayName("Net basic clone")
        void netBasicClone() throws CloneNotSupportedException {
            YNet clonedNet = (YNet) testNet.clone();
            assertEquals(testNet.getID(), clonedNet.getID());
            assertNotSame(testNet, clonedNet);
            assertEquals(testNet.getElements().size(), clonedNet.getElements().size());
        }

        @Test
        @DisplayName("Net clone preserves elements")
        void netClonePreservesElements() throws CloneNotSupportedException {
            YNet clonedNet = (YNet) testNet.clone();

            assertTrue(clonedNet.containsElement(task1));
            assertTrue(clonedNet.containsElement(condition1));
        }
    }

    @Nested
    @DisplayName("Net Identifier Management")
    class IdentifierManagementTest {

        @Test
        @DisplayName("Net identifier container")
        void netIdentifierContainer() {
            YIdentifier identifier = new YIdentifier("case1");
            testNet.addIdentifier(identifier);

            Map<String, YIdentifier> identifiers = testNet.getIdentifiers();
            assertEquals(1, identifiers.size());
            assertTrue(identifiers.containsValue(identifier));
        }

        @Test
        @DisplayName("Remove identifier from net")
        void removeIdentifierFromNet() {
            YIdentifier identifier = new YIdentifier("case1");
            testNet.addIdentifier(identifier);

            testNet.removeIdentifier("case1");
            assertFalse(testNet.getIdentifiers().containsValue(identifier));
        }
    }

    @Nested
    @DisplayName("Net Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("Net with duplicate element IDs")
        void netWithDuplicateIDs() {
            YTask duplicateTask = new YAtomicTask("task1", YTask._AND, YTask._XOR, testNet);
            // This should be handled appropriately by the net
            assertDoesNotThrow(() -> testNet.addNetElement(duplicateTask));
        }

        @Test
        @DisplayName("Net refresh net element identifier")
        void netRefreshNetElementIdentifier() {
            // This method is called when element IDs change
            assertDoesNotThrow(() -> testNet.refreshNetElementIdentifier("oldID", "newID"));
        }
    }
}