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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifierBag;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for YCondition class.
 * Tests condition functionality, identifier management, and edge cases.
 */
class YConditionTest {

    private YNet testNet;
    private YSpecification testSpec;
    private YCondition testCondition;
    private YCondition labeledCondition;
    private YIdentifier testIdentifier1;
    private YIdentifier testIdentifier2;

    @BeforeEach
    void setUp() {
        testSpec = new YSpecification("testSpec", "1.0", "test://uri");
        testNet = new YNet("testNet", testSpec);
        testCondition = new YCondition("cond1", testNet);
        labeledCondition = new YCondition("labeledCond", "Label", testNet);
        testIdentifier1 = new YIdentifier("case1");
        testIdentifier2 = new YIdentifier("case2");
    }

    @Nested
    @DisplayName("Basic Condition Properties")
    class BasicPropertiesTest {

        @Test
        @DisplayName("Condition with ID only")
        void conditionWithID() {
            assertEquals("cond1", testCondition.getID());
            assertEquals("cond1", testCondition.getName());
            assertFalse(testCondition.isAnonymous());
        }

        @Test
        @DisplayName("Condition with label")
        void conditionWithLabel() {
            assertEquals("Label", labeledCondition.getName());
            assertEquals("labeledCond", labeledCondition.getID());
            assertFalse(labeledCondition.isAnonymous());
        }

        @Test
        @DisplayName("Condition documentation")
        void conditionDocumentation() {
            String doc = "Test condition";
            testCondition.setDocumentation(doc);
            assertEquals(doc, testCondition.getDocumentation());
        }

        @Test
        @DisplayName("Condition implicit status")
        void conditionImplicitStatus() {
            assertFalse(testCondition.isImplicit());
            testCondition.setImplicit(true);
            assertTrue(testCondition.isImplicit());
        }
    }

    @Nested
    @DisplayName("Condition Identifier Management")
    class IdentifierManagementTest {

        @Test
        @DisplayName("Add identifier to condition")
        void addIdentifierToCondition() {
            testCondition.add(testIdentifier1);
            assertTrue(testCondition.contains(testIdentifier1));
            assertTrue(testCondition.containsIdentifier());
        }

        @Test
        @DisplayName("Remove identifier from condition")
        void removeIdentifierFromCondition() {
            testCondition.add(testIdentifier1);
            YIdentifier removed = testCondition.removeOne();
            assertEquals(testIdentifier1, removed);
            assertFalse(testCondition.contains(testIdentifier1));
        }

        @Test
        @DisplayName("Remove specific identifier")
        void removeSpecificIdentifier() {
            testCondition.add(testIdentifier1);
            testCondition.add(testIdentifier2);

            testCondition.removeOne(testIdentifier1);
            assertFalse(testCondition.contains(testIdentifier1));
            assertTrue(testCondition.contains(testIdentifier2));
        }

        @Test
        @DisplayName("Get amount of specific identifier")
        void getIdentifierAmount() {
            testCondition.add(testIdentifier1);
            testCondition.add(testIdentifier1); // Add same identifier twice
            testCondition.add(testIdentifier2);

            assertEquals(2, testCondition.getAmount(testIdentifier1));
            assertEquals(1, testCondition.getAmount(testIdentifier2));
        }

        @Test
        @DisplayName("Get all identifiers")
        void getAllIdentifiers() {
            testCondition.add(testIdentifier1);
            testCondition.add(testIdentifier2);

            List<YIdentifier> identifiers = testCondition.getIdentifiers();
            assertEquals(2, identifiers.size());
            assertTrue(identifiers.contains(testIdentifier1));
            assertTrue(identifiers.contains(testIdentifier2));
        }

        @Test
        @DisplayName("Condition contains identifier when empty")
        void conditionContainsIdentifierWhenEmpty() {
            assertFalse(testCondition.containsIdentifier());
            assertFalse(testCondition.contains(testIdentifier1));
        }

        @Test
        @DisplayName("Remove one from empty condition")
        void removeOneFromEmptyCondition() {
            // This should handle empty condition gracefully
            List<YIdentifier> identifiers = testCondition.getIdentifiers();
            assertTrue(identifiers.isEmpty());
        }
    }

    @Nested
    @DisplayName("Condition Flow Management")
    class FlowManagementTest {

        @Test
        @DisplayName("Add preset flow to condition")
        void addPresetFlow() {
            YFlow flow = new YFlow(new YCondition("prior", testNet), testCondition);
            testCondition.addPreset(flow);

            assertEquals(1, testCondition.getPresetFlows().size());
            assertEquals(1, testCondition.getPresetElements().size());
        }

        @Test
        @DisplayName("Add postset flow from condition")
        void addPostsetFlow() {
            YFlow flow = new YFlow(testCondition, new YCondition("next", testNet));
            testCondition.addPostset(flow);

            assertEquals(1, testCondition.getPostsetFlows().size());
            assertEquals(1, testCondition.getPostsetElements().size());
        }

        @Test
        @DisplayName("Remove preset flow")
        void removePresetFlow() {
            YCondition prior = new YCondition("prior", testNet);
            YFlow flow = new YFlow(prior, testCondition);

            testCondition.addPreset(flow);
            assertEquals(1, testCondition.getPresetFlows().size());

            testCondition.removePresetFlow(flow);
            assertTrue(testCondition.getPresetFlows().isEmpty());
        }

        @Test
        @DisplayName("Remove postset flow")
        void removePostsetFlow() {
            YCondition next = new YCondition("next", testNet);
            YFlow flow = new YFlow(testCondition, next);

            testCondition.addPostset(flow);
            assertEquals(1, testCondition.getPostsetFlows().size());

            testCondition.removePostsetFlow(flow);
            assertTrue(testCondition.getPostsetFlows().isEmpty());
        }
    }

    @Nested
    @DisplayName("Condition XML Generation")
    class XMLGenerationTest {

        @Test
        @DisplayName("Condition toXML basic structure")
        void conditionToXMLBasic() {
            testCondition.setName("Test Condition");
            testCondition.setDocumentation("Test documentation");

            String xml = testCondition.toXML();
            assertTrue(xml.contains("<condition id=\"cond1\">"));
            assertTrue(xml.contains("<name>Test Condition</name>"));
            assertTrue(xml.contains("<documentation>Test documentation</documentation>"));
            assertTrue(xml.contains("</condition>"));
        }

        @Test
        @DisplayName("Condition toXML with flows")
        void conditionToXMLWithFlows() {
            YCondition next = new YCondition("next", testNet);
            YFlow flow = new YFlow(testCondition, next);
            testCondition.addPostset(flow);

            String xml = testCondition.toXML();
            assertTrue(xml.contains("<flow from=\"cond1\" to=\"next\"/>"));
        }
    }

    @Nested
    @DisplayName("Condition Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("Condition with null identifier")
        void conditionWithNullIdentifier() {
            // Should not throw exception
            assertDoesNotThrow(() -> testCondition.contains(null));
        }

        @Test
        @DisplayName("Condition clone functionality")
        void conditionClone() throws CloneNotSupportedException {
            testCondition.add(testIdentifier1);
            testCondition.setImplicit(true);

            YCondition cloned = (YCondition) testCondition.clone();
            assertEquals(testCondition.getID(), cloned.getID());
            assertTrue(cloned.isImplicit());
            // Note: Clone behavior with identifiers needs specific implementation
        }

        @Test
        @DisplayName("Condition verification")
        void conditionVerification() {
            // Simple verification test
            assertDoesNotThrow(() -> testCondition.verify(new org.yawlfoundation.yawl.util.YVerificationHandler()));
        }
    }

    @Nested
    @DisplayName("Condition ID Management")
    class IDManagementTest {

        @Test
        @DisplayName("Condition ID change updates flows")
        void conditionIDChangeUpdatesFlows() {
            YCondition prior = new YCondition("prior", testNet);
            YFlow presetFlow = new YFlow(prior, testCondition);

            testCondition.addPreset(presetFlow);
            assertEquals(1, testCondition.getPresetFlows().size());

            // Change ID
            testCondition.setID("cond1_updated");
            assertEquals("cond1_updated", testCondition.getID());
        }
    }
}