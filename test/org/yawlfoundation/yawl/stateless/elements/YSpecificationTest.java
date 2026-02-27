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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for YSpecification class.
 * Tests workflow specification functionality, schema management, and validation.
 */
class YSpecificationTest {

    private YSpecification testSpec;

    @BeforeEach
    void setUp() {
        testSpec = new YSpecification("testSpec", "1.0", "http://example.com/spec");
    }

    @Nested
    @DisplayName("Basic Specification Properties")
    class BasicPropertiesTest {

        @Test
        @DisplayName("Specification with constructor parameters")
        void specificationWithConstructorParams() {
            assertEquals("testSpec", testSpec.getID());
            assertEquals("1.0", testSpec.getSpecVersion());
            assertEquals("http://example.com/spec", testSpec.getURI());
        }

        @Test
        @DisplayName("Specification version management")
        void specificationVersionManagement() {
            assertEquals("1.0", testSpec.getSpecVersion());

            // Version typically managed through parsing/creation
            // Cannot be directly changed with a setter in this implementation
        }

        @Test
        @DisplayName("Specification URI")
        void specificationURI() {
            assertEquals("http://example.com/spec", testSpec.getURI());
        }

        @Test
        @DisplayName("Specification XML namespace")
        void specificationXMLNamespace() {
            String namespace = testSpec.getXMLNamespace();
            assertNotNull(namespace);
            assertTrue(namespace.startsWith("http"));
        }
    }

    @Nested
    @DisplayName("Specification Schema Management")
    class SchemaManagementTest {

        @Test
        @DisplayName("Specification schema version")
        void specificationSchemaVersion() {
            assertNotNull(testSpec.getSchemaVersion());
            // Schema version controls validation behavior
            assertTrue(testSpec.getSchemaVersion().isSchemaValidating());
        }

        @Test
        @DisplayName("Specification data validator")
        void specificationDataValidator() {
            YDataValidator validator = testSpec.getDataValidator();
            assertNotNull(validator);
            validator.setSchema("<test/>"); // Would normally be set with actual schema
        }

        @Test
        @DisplayName("Specification schema validation")
        void specificationSchemaValidation() {
            YDataValidator validator = testSpec.getDataValidator();
            assertNotNull(validator.getSchema());
        }
    }

    @Nested
    @DisplayName("Specification Net Management")
    class NetManagementTest {

        @Test
        @DisplayName("Specification with no nets initially")
        void specificationWithNoNets() {
            assertTrue(testSpec.getNets().isEmpty());
        }

        @Test
        @DisplayName("Add net to specification")
        void addNetToSpecification() {
            YNet net1 = new YNet("net1", testSpec);
            testSpec.addNet(net1);

            assertEquals(1, testSpec.getNets().size());
            assertEquals(net1, testSpec.getNet("net1"));
        }

        @Test
        @DisplayName("Get all nets from specification")
        void getAllNetsFromSpecification() {
            YNet net1 = new YNet("net1", testSpec);
            YNet net2 = new YNet("net2", testSpec);

            testSpec.addNet(net1);
            testSpec.addNet(net2);

            assertEquals(2, testSpec.getNets().size());
        }

        @Test
        @DisplayName("Get net by ID")
        void getNetByID() {
            YNet net = new YNet("testNet", testSpec);
            testSpec.addNet(net);

            YNet retrievedNet = testSpec.getNet("testNet");
            assertEquals(net, retrievedNet);

            assertNull(testSpec.getNet("nonexistent"));
        }

        @Test
        @DisplayName("Remove net from specification")
        void removeNetFromSpecification() {
            YNet net = new YNet("testNet", testSpec);
            testSpec.addNet(net);

            testSpec.removeNet("testNet");
            assertNull(testSpec.getNet("testNet"));
            assertTrue(testSpec.getNets().isEmpty());
        }
    }

    @Nested
    @DisplayName("Specification XML Generation")
    class XMLGenerationTest {

        @Test
        @DisplayName("Specification toXML basic structure")
        void specificationToXMLBasic() {
            String xml = testSpec.toXML();
            assertTrue(xml.contains("<specification id=\"testSpec\""));
            assertTrue(xml.contains("version=\"1.0\""));
            assertTrue(xml.contains("uri=\"http://example.com/spec\""));
            assertTrue(xml.contains("</specification>"));
        }

        @Test
        @DisplayName("Specification toXML with nets")
        void specificationToXMLWithNets() {
            YNet net = new YNet("testNet", testSpec);
            testSpec.addNet(net);

            String xml = testSpec.toXML();
            assertTrue(xml.contains("<net id=\"testNet\">"));
            assertTrue(xml.contains("</net>"));
        }

        @Test
        @DisplayName("Specification toXML without nets")
        void specificationToXMLWithoutNets() {
            String xml = testSpec.toXML();
            assertTrue(xml.contains("<specification"));
            assertTrue(xml.contains("</specification>"));
        }
    }

    @Nested
    @DisplayName("Specification Verification")
    class VerificationTest {

        @Test
        @DisplayName("Specification verification")
        void specificationVerification() {
            // Should not throw exception for empty specification
            assertDoesNotThrow(() -> testSpec.verify(new org.yawlfoundation.yawl.util.YVerificationHandler()));
        }

        @Test
        @DisplayName("Specification verification with nets")
        void specificationVerificationWithNets() {
            YNet net = new YNet("testNet", testSpec);
            YTask task = new YAtomicTask("task1", YTask._AND, YTask._XOR, net);
            YCondition condition = new YCondition("condition1", net);

            net.addNetElement(task);
            net.addNetElement(condition);

            testSpec.addNet(net);

            // Should not throw exception for valid specification
            assertDoesNotThrow(() -> testSpec.verify(new org.yawlfoundation.yawl.util.YVerificationHandler()));
        }
    }

    @Nested
    @DisplayName("Specification Decomposition Management")
    class DecompositionManagementTest {

        @Test
        @DisplayName("Specification decompositions")
        void specificationDecompositions() {
            assertTrue(testSpec.getDecompositions().isEmpty());
        }

        @Test
        @DisplayName("Add decomposition to specification")
        void addDecompositionToSpecification() {
            YDecomposition decomp = new YDecomposition("decomp1", testSpec);
            testSpec.addDecomposition(decomp);

            assertEquals(1, testSpec.getDecompositions().size());
            assertEquals(decomp, testSpec.getDecomposition("decomp1"));
        }

        @Test
        @DisplayName("Get decomposition by ID")
        void getDecompositionByID() {
            YDecomposition decomp = new YDecomposition("decomp1", testSpec);
            testSpec.addDecomposition(decomp);

            YDecomposition retrieved = testSpec.getDecomposition("decomp1");
            assertEquals(decomp, retrieved);

            assertNull(testSpec.getDecomposition("nonexistent"));
        }

        @Test
        @DisplayName("Remove decomposition from specification")
        void removeDecompositionFromSpecification() {
            YDecomposition decomp = new YDecomposition("decomp1", testSpec);
            testSpec.addDecomposition(decomp);

            testSpec.removeDecomposition("decomp1");
            assertNull(testSpec.getDecomposition("decomp1"));
            assertTrue(testSpec.getDecompositions().isEmpty());
        }
    }

    @Nested
    @DisplayName("Specification Parameter Management")
    class ParameterManagementTest {

        @Test
        @DisplayName("Specification input parameters")
        void specificationInputParameters() {
            assertTrue(testSpec.getInputParameters().isEmpty());
        }

        @Test
        @DisplayName("Specification output parameters")
        void specificationOutputParameters() {
            assertTrue(testSpec.getOutputParameters().isEmpty());
        }

        @Test
        @DisplayName("Add input parameter to specification")
        void addInputParameterToSpecification() {
            YParameter param = new YParameter("input1", "string", "Input parameter");
            testSpec.addInputParameter("input1", param);

            assertEquals(1, testSpec.getInputParameters().size());
            assertEquals(param, testSpec.getInputParameters().get("input1"));
        }

        @Test
        @DisplayName("Add output parameter to specification")
        void addOutputParameterToSpecification() {
            YParameter param = new YParameter("output1", "string", "Output parameter");
            testSpec.addOutputParameter("output1", param);

            assertEquals(1, testSpec.getOutputParameters().size());
            assertEquals(param, testSpec.getOutputParameters().get("output1"));
        }
    }

    @Nested
    @DisplayName("Specification Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("Specification with null URI")
        void specificationWithNullURI() {
            YSpec spec = new YSpec("testSpec", "1.0", null);
            assertNull(spec.getURI());
        }

        @Test
        @DisplayName("Specification with empty ID")
        void specificationWithEmptyID() {
            YSpec spec = new YSpec("", "1.0", "http://example.com");
            assertEquals("", spec.getID());
        }

        @Test
        @DisplayName("Specification duplicate net IDs")
        void specificationDuplicateNetIDs() {
            YNet net1 = new YNet("duplicateNet", testSpec);
            YNet net2 = new YNet("duplicateNet", testSpec);

            testSpec.addNet(net1);
            testSpec.addNet(net2); // Should handle duplicate appropriately

            // Depending on implementation, might keep first, last, or throw exception
            assertEquals(1, testSpec.getNets().size());
        }
    }

    @Nested
    @DisplayName("Specification Clone")
    class CloneTest {

        @Test
        @DisplayName("Specification basic clone")
        void specificationBasicClone() throws CloneNotSupportedException {
            YNet net = new YNet("testNet", testSpec);
            testSpec.addNet(net);

            YSpec cloned = (YSpec) testSpec.clone();
            assertEquals(testSpec.getID(), cloned.getID());
            assertNotSame(testSpec, cloned);
            assertEquals(testSpec.getNets().size(), cloned.getNets().size());
        }

        @Test
        @DisplayName("Specification clone preserves nets")
        void specificationClonePreservesNets() throws CloneNotSupportedException {
            YNet net = new YNet("testNet", testSpec);
            testSpec.addNet(net);

            YSpec cloned = (YSpec) testSpec.clone();
            assertTrue(cloned.getNets().containsKey("testNet"));
        }
    }
}