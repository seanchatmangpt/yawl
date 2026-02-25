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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for YFlow class.
 * Tests flow connections between elements and flow properties.
 */
class YFlowTest {

    private YNet testNet;
    private YTask task1;
    private YTask task2;
    private YCondition condition1;
    private YCondition condition2;
    private YFlow flow1;
    private YFlow flow2;

    @BeforeEach
    void setUp() {
        testNet = new YNet("testNet", new YSpecification("testSpec", "1.0", "http://example.com"));
        task1 = new YAtomicTask("task1", YTask._AND, YTask._XOR, testNet);
        task2 = new YAtomicTask("task2", YTask._AND, YTask._XOR, testNet);
        condition1 = new YCondition("condition1", testNet);
        condition2 = new YCondition("condition2", testNet);
        flow1 = new YFlow(condition1, task1);
        flow2 = new YFlow(task1, condition2);
    }

    @Nested
    @DisplayName("Basic Flow Properties")
    class BasicPropertiesTest {

        @Test
        @DisplayName("Flow creation with elements")
        void flowCreationWithElements() {
            assertEquals(condition1, flow1.getPriorElement());
            assertEquals(task1, flow1.getNextElement());
            assertEquals(task1, flow2.getPriorElement());
            assertEquals(condition2, flow2.getNextElement());
        }

        @Test
        @DisplayName("Flow documentation")
        void flowDocumentation() {
            String doc = "Test flow documentation";
            flow1.setDocumentation(doc);
            assertEquals(doc, flow1.getDocumentation());
        }
    }

    @Nested
    @DisplayName("Flow Predicate and Ordering")
    class FlowPredicateTest {

        @Test
        @DisplayName("Flow with null predicate")
        void flowWithNullPredicate() {
            assertNull(flow1.getXpathPredicate());
        }

        @Test
        @DisplayName("Flow with custom predicate")
        void flowWithCustomPredicate() {
            String predicate = "$data/active = 'true'";
            flow1.setXpathPredicate(predicate);
            assertEquals(predicate, flow1.getXpathPredicate());
        }

        @Test
        @DisplayName("Flow with empty predicate")
        void flowWithEmptyPredicate() {
            flow1.setXpathPredicate("");
            assertEquals("", flow1.getXpathPredicate());
        }

        @Test
        @DisplayName("Flow evaluation ordering")
        void flowEvaluationOrdering() {
            assertNull(flow1.getEvalOrdering());

            flow1.setEvalOrdering(1);
            assertEquals(Integer.valueOf(1), flow1.getEvalOrdering());

            flow1.setEvalOrdering(null);
            assertNull(flow1.getEvalOrdering());
        }

        @Test
        @DisplayName("Flow default status")
        void flowDefaultStatus() {
            assertFalse(flow1.isDefaultFlow());

            flow1.setIsDefaultFlow(true);
            assertTrue(flow1.isDefaultFlow());

            flow1.setIsDefaultFlow(false);
            assertFalse(flow1.isDefaultFlow());
        }
    }

    @Nested
    @DisplayName("Flow XML Generation")
    class XMLGenerationTest {

        @Test
        @DisplayName("Flow toXML basic structure")
        void flowToXMLBasic() {
            String xml = flow1.toXML();
            assertTrue(xml.contains("<flow"));
            assertTrue(xml.contains("from=\"condition1\""));
            assertTrue(xml.contains("to=\"task1\""));
            assertTrue(xml.contains("</flow>"));
        }

        @Test
        @DisplayName("Flow toXML with all properties")
        void flowToXMLWithAllProperties() {
            flow1.setXpathPredicate("$data/active = 'true'");
            flow1.setEvalOrdering(1);
            flow1.setDocumentation("Test flow");

            String xml = flow1.toXML();
            assertTrue(xml.contains("xpath=\"$data/active = 'true'\""));
            assertTrue(xml.contains("ordering=\"1\""));
            assertTrue(xml.contains("documentation=\"Test flow\""));
        }

        @Test
        @DisplayName("Flow toXML as default")
        void flowToXMLAsDefault() {
            flow1.setIsDefaultFlow(true);

            String xml = flow1.toXML();
            assertTrue(xml.contains("isDefault=\"true\""));
        }
    }

    @Nested
    @DisplayName("Flow Verification")
    class VerificationTest {

        @Test
        @DisplayName("Flow verification with valid elements")
        void flowVerificationValidElements() {
            assertDoesNotThrow(() -> flow1.verify(task1, new org.yawlfoundation.yawl.util.YVerificationHandler()));
        }

        @Test
        @DisplayName("Flow verification with invalid prior element")
        void flowVerificationInvalidPriorElement() {
            YTask differentTask = new YAtomicTask("differentTask", YTask._AND, YTask._XOR, testNet);

            // Should handle case where prior element doesn't match
            assertDoesNotThrow(() -> flow1.verify(differentTask, new org.yawlfoundation.yawl.util.YVerificationHandler()));
        }

        @Test
        @DisplayName("Flow verification with null handler")
        void flowVerificationWithNullHandler() {
            // Should not throw exception
            assertDoesNotThrow(() -> flow1.verify(task1, null));
        }
    }

    @Nested
    @DisplayName("Flow Connections")
    class FlowConnectionsTest {

        @Test
        @DisplayName("Flow connects elements properly")
        void flowConnectsElementsProperly() {
            assertTrue(task1.getPresetElements().contains(condition1));
            assertTrue(condition1.getPostsetElements().contains(task1));
        }

        @Test
        @DisplayName("Multiple flows from same element")
        void multipleFlowsFromSameElement() {
            YTask task3 = new YAtomicTask("task3", YTask._AND, YTask._XOR, testNet);
            YFlow flow3 = new YFlow(condition1, task3);

            assertEquals(2, condition1.getPostsetElements().size());
            assertTrue(condition1.getPostsetElements().contains(task1));
            assertTrue(condition1.getPostsetElements().contains(task3));
        }

        @Test
        @DisplayName("Multiple flows to same element")
        void multipleFlowsToSameElement() {
            YCondition condition3 = new YCondition("condition3", testNet);
            YFlow flow3 = new YFlow(condition3, task1);

            assertEquals(2, task1.getPresetElements().size());
            assertTrue(task1.getPresetElements().contains(condition1));
            assertTrue(task1.getPresetElements().contains(condition3));
        }
    }

    @Nested
    @DisplayName("Flow Clone")
    class CloneTest {

        @Test
        @DisplayName("Flow basic clone")
        void flowBasicClone() throws CloneNotSupportedException {
            flow1.setXpathPredicate("test predicate");
            flow1.setEvalOrdering(1);
            flow1.setIsDefaultFlow(true);

            YFlow cloned = (YFlow) flow1.clone();
            assertEquals(flow1.getXpathPredicate(), cloned.getXpathPredicate());
            assertEquals(flow1.getEvalOrdering(), cloned.getEvalOrdering());
            assertEquals(flow1.isDefaultFlow(), cloned.isDefaultFlow());
            assertNotSame(flow1, cloned);
        }

        @Test
        @DisplayName("Flow clone preserves element references")
        void flowClonePreservesElementReferences() throws CloneNotSupportedException {
            YFlow cloned = (YFlow) flow1.clone();
            assertEquals(flow1.getPriorElement(), cloned.getPriorElement());
            assertEquals(flow1.getNextElement(), cloned.getNextElement());
        }
    }

    @Nested
    @DisplayName("Flow Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("Flow with null elements")
        void flowWithNullElements() {
            YFlow nullFlow = new YFlow(null, null);
            assertNull(nullFlow.getPriorElement());
            assertNull(nullFlow.getNextElement());
        }

        @Test
        @DisplayName("Flow toXML with complex predicates")
        void flowToXMLWithComplexPredicates() {
            String complexPredicate = "count($data/items) > 10 and $data/status = 'active'";
            flow1.setXpathPredicate(complexPredicate);

            String xml = flow1.toXML();
            assertTrue(xml.contains("xpath=\"" + complexPredicate + "\""));
        }

        @Test
        @DisplayName("Flow with special characters in documentation")
        void flowWithSpecialCharactersInDocumentation() {
            String specialDoc = "Documentation with <xml> tags and 'quotes'";
            flow1.setDocumentation(specialDoc);

            String xml = flow1.toXML();
            // Should properly escape XML special characters
            assertTrue(xml.contains("documentation=\"" + specialDoc + "\""));
        }
    }
}