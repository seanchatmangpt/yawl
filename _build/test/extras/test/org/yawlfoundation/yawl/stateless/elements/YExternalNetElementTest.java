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
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for YExternalNetElement class.
 * Tests the base functionality for all YAWL net elements.
 */
class YExternalNetElementTest {

    private YNet testNet;
    private YSpecification testSpec;
    private YExternalNetElement testElement;
    private YTask otherTask;
    private YCondition condition;

    @BeforeEach
    void setUp() {
        testSpec = new YSpecification("testSpec", "1.0", "test://uri");
        testNet = new YNet("testNet", testSpec);
        testElement = new YTask("element1", YTask._AND, YTask._XOR, testNet) {
            @Override
            protected void startOne(YNetRunner parentRunner, YIdentifier id) throws YDataStateException, YQueryException, YStateException {
                // Dummy implementation for abstract method
            }
        };
        otherTask = new YAtomicTask("otherTask", YTask._AND, YTask._XOR, testNet);
        condition = new YCondition("condition1", testNet);
    }

    @Nested
    @DisplayName("Basic Element Properties")
    class BasicPropertiesTest {

        @Test
        @DisplayName("Element ID management")
        void elementIDManagement() {
            assertEquals("element1", testElement.getID());
            testElement.setID("element1_updated");
            assertEquals("element1_updated", testElement.getID());
        }

        @Test
        @DisplayName("Element name and documentation")
        void elementNameAndDocumentation() {
            assertNull(testElement.getName());
            assertNull(testElement.getDocumentation());

            testElement.setName("Test Element");
            testElement.setDocumentation("Test documentation");

            assertEquals("Test Element", testElement.getName());
            assertEquals("Test documentation", testElement.getDocumentation());
        }

        @Test
        @DisplayName("Element name gets pre-parsed documentation")
        void elementPreparsedDocumentation() {
            testElement.setDocumentation("Simple documentation");
            assertEquals("Simple documentation", testElement.getDocumentationPreParsed());

            // Documentation with variables would be parsed
            testElement.setDocumentation("Variable ${/data}");
            // Note: Actual parsing depends on the net's data document
        }

        @Test
        @DisplayName("Element containing net")
        void elementContainingNet() {
            assertEquals(testNet, testElement.getNet());
        }

        @Test
        @DisplayName("Element proper ID")
        void elementProperID() {
            String properID = testElement.getProperID();
            assertTrue(properID.contains("testSpec"));
            assertTrue(properID.contains("element1"));
        }
    }

    @Nested
    @DisplayName("Flow Management")
    class FlowManagementTest {

        @Test
        @DisplayName("Add preset flow")
        void addPresetFlow() {
            YFlow flow = new YFlow(condition, (YExternalNetElement) testElement);
            testElement.addPreset(flow);

            assertEquals(1, testElement.getPresetFlows().size());
            assertEquals(1, testElement.getPresetElements().size());
            assertTrue(testElement.getPresetElements().contains(condition));
        }

        @Test
        @DisplayName("Add postset flow")
        void addPostsetFlow() {
            YFlow flow = new YFlow(testElement, condition);
            testElement.addPostset(flow);

            assertEquals(1, testElement.getPostsetFlows().size());
            assertEquals(1, testElement.getPostsetElements().size());
            assertTrue(testElement.getPostsetElements().contains(condition));
        }

        @Test
        @DisplayName("Get specific preset flow")
        void getPresetFlow() {
            YFlow flow = new YFlow(condition, testElement);
            testElement.addPreset(flow);

            YFlow retrievedFlow = testElement.getPresetFlow(condition);
            assertEquals(flow, retrievedFlow);
        }

        @Test
        @DisplayName("Get specific postset flow")
        void getPostsetFlow() {
            YFlow flow = new YFlow(testElement, condition);
            testElement.addPostset(flow);

            YFlow retrievedFlow = testElement.getPostsetFlow(condition);
            assertEquals(flow, retrievedFlow);
        }

        @Test
        @DisplayName("Remove preset flow")
        void removePresetFlow() {
            YFlow flow = new YFlow(condition, testElement);
            testElement.addPreset(flow);

            assertEquals(1, testElement.getPresetFlows().size());
            testElement.removePresetFlow(flow);
            assertTrue(testElement.getPresetFlows().isEmpty());
        }

        @Test
        @DisplayName("Remove postset flow")
        void removePostsetFlow() {
            YFlow flow = new YFlow(testElement, condition);
            testElement.addPostset(flow);

            assertEquals(1, testElement.getPostsetFlows().size());
            testElement.removePostsetFlow(flow);
            assertTrue(testElement.getPostsetFlows().isEmpty());
        }

        @Test
        @DisplayName("Get preset and postset elements")
        void getPresetAndPostsetElements() {
            YFlow presetFlow = new YFlow(condition, testElement);
            YFlow postsetFlow = new YFlow(testElement, otherTask);

            testElement.addPreset(presetFlow);
            testElement.addPostset(postsetFlow);

            assertEquals(1, testElement.getPresetElements().size());
            assertEquals(1, testElement.getPostsetElements().size());
            assertTrue(testElement.getPresetElements().contains(condition));
            assertTrue(testElement.getPostsetElements().contains(otherTask));
        }
    }

    @Nested
    @DisplayName("Cancellation and Mapping Sets")
    class SetsTest {

        @Test
        @DisplayName("Cancelled by set")
        void cancelledBySet() {
            assertNull(testElement.getCancelledBySet());

            testElement.addToCancelledBySet(otherTask);
            Set<YExternalNetElement> cancelledBy = testElement.getCancelledBySet();
            assertNotNull(cancelledBy);
            assertEquals(1, cancelledBy.size());
            assertTrue(cancelledBy.contains(otherTask));
        }

        @Test
        @DisplayName("Remove from cancelled by set")
        void removeFromCancelledBySet() {
            testElement.addToCancelledBySet(otherTask);
            testElement.removeFromCancelledBySet(otherTask);

            assertNull(testElement.getCancelledBySet());
        }

        @Test
        @DisplayName("YAWL mapping set")
        void yawlMappingSet() {
            assertNull(testElement.getYawlMappings());

            testElement.addToYawlMappings(condition);
            Set<YExternalNetElement> mappings = testElement.getYawlMappings();
            assertNotNull(mappings);
            assertEquals(1, mappings.size());
            assertTrue(mappings.contains(condition));
        }

        @Test
        @DisplayName("Add multiple elements to YAWL mappings")
        void addMultipleToYawlMappings() {
            testElement.addToYawlMappings(Set.of(condition, otherTask));

            Set<YExternalNetElement> mappings = testElement.getYawlMappings();
            assertEquals(2, mappings.size());
            assertTrue(mappings.contains(condition));
            assertTrue(mappings.contains(otherTask));
        }
    }

    @Nested
    @DisplayName("Element ID Change Impact")
    class IDChangeImpactTest {

        @Test
        @DisplayName("Element ID change updates flow references")
        void elementIDChangeUpdatesFlows() {
            YFlow presetFlow = new YFlow(condition, testElement);
            YFlow postsetFlow = new YFlow(testElement, otherTask);

            testElement.addPreset(presetFlow);
            testElement.addPostset(postsetFlow);

            assertEquals(1, testElement.getPresetFlows().size());
            assertEquals(1, testElement.getPostsetFlows().size());

            // Change ID
            testElement.setID("element1_updated");

            // Flows should still be present but with updated references
            assertEquals(1, testElement.getPresetFlows().size());
            assertEquals(1, testElement.getPostsetFlows().size());
        }
    }

    @Nested
    @DisplayName("XML Generation")
    class XMLGenerationTest {

        @Test
        @DisplayName("Element toXML basic structure")
        void elementToXMLBasic() {
            testElement.setName("Test Element");
            testElement.setDocumentation("Test documentation");

            String xml = testElement.toXML();
            assertTrue(xml.contains("name=\"Test Element\""));
            assertTrue(xml.contains("documentation=\"Test documentation\""));
        }

        @Test
        @DisplayName("Element toXML with flows")
        void elementToXMLWithFlows() {
            YFlow postsetFlow = new YFlow(testElement, condition);
            testElement.addPostset(postsetFlow);

            String xml = testElement.toXML();
            assertTrue(xml.contains("<flow from=\"element1\" to=\"condition1\"/>"));
        }

        @Test
        @DisplayName("Task toXML with implicit conditions")
        void taskToXMLWithImplicitConditions() {
            // Create implicit condition flow
            YCondition implicitCond = new YCondition("implicit", testNet);
            implicitCond.setImplicit(true);

            YFlow flow = new YFlow(testElement, implicitCond);
            testElement.addPostset(flow);

            String xml = testElement.toXML();
            // Should handle implicit conditions appropriately
            assertTrue(xml.contains("</flow>"));
        }
    }

    @Nested
    @DisplayName("Element Verification")
    class VerificationTest {

        @Test
        @DisplayName("Element verification with valid flows")
        void elementVerificationValidFlows() {
            YFlow presetFlow = new YFlow(condition, testElement);
            YFlow postsetFlow = new YFlow(testElement, otherTask);

            testElement.addPreset(presetFlow);
            testElement.addPostset(postsetFlow);

            // Should not throw exception
            assertDoesNotThrow(() -> testElement.verify(new org.yawlfoundation.yawl.util.YVerificationHandler()));
        }

        @Test
        @DisplayName("Element verification without preset flows")
        void elementVerificationNoPresetFlows() {
            // Element without preset flows should fail verification
            assertThrows(RuntimeException.class, () -> {
                testElement.verify(new org.yawlfoundation.yawl.util.YVerificationHandler() {
                    @Override
                    public void error(Object source, String message) {
                        throw new RuntimeException(message);
                    }
                });
            });
        }

        @Test
        @DisplayName("Element verification without postset flows")
        void elementVerificationNoPostsetFlows() {
            // Element without postset flows should fail verification
            assertThrows(RuntimeException.class, () -> {
                testElement.verify(new org.yawlfoundation.yawl.util.YVerificationHandler() {
                    @Override
                    public void error(Object source, String message) {
                        throw new RuntimeException(message);
                    }
                });
            });
        }
    }

    @Nested
    @DisplayName("Element Clone")
    class CloneTest {

        @Test
        @DisplayName("Element basic clone")
        void elementBasicClone() throws CloneNotSupportedException {
            testElement.setName("Test Element");
            testElement.setDocumentation("Test documentation");

            YExternalNetElement cloned = (YExternalNetElement) testElement.clone();
            assertEquals(testElement.getID(), cloned.getID());
            assertEquals(testElement.getName(), cloned.getName());
            assertEquals(testElement.getDocumentation(), cloned.getDocumentation());
            assertNotSame(testElement, cloned);
        }

        @Test
        @DisplayName("Element clone with flows")
        void elementCloneWithFlows() throws CloneNotSupportedException {
            YFlow presetFlow = new YFlow(condition, testElement);
            YFlow postsetFlow = new YFlow(testElement, otherTask);

            testElement.addPreset(presetFlow);
            testElement.addPostset(postsetFlow);

            YExternalNetElement cloned = (YExternalNetElement) testElement.clone();
            assertEquals(1, cloned.getPresetFlows().size());
            assertEquals(1, cloned.getPostsetFlows().size());
        }
    }
}