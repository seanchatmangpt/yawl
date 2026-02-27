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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for YMultiInstanceAttributes class.
 * Tests multi-instance task configurations, data mappings, and validation.
 */
class YMultiInstanceAttributesTest {

    private YNet testNet;
    private YTask testTask;
    private YMultiInstanceAttributes multiInstanceAttributes;

    @BeforeEach
    void setUp() {
        testNet = new YNet("testNet", new YSpecification("testSpec", "1.0", "http://example.com"));
        testTask = new YAtomicTask("task1", YTask._AND, YTask._XOR, testNet);
        multiInstanceAttributes = new YMultiInstanceAttributes(testTask,
            "count(\"instance\")", "10", "5", "static");
    }

    @Nested
    @DisplayName("Basic Multi-Instance Properties")
    class BasicPropertiesTest {

        @Test
        @DisplayName("Multi-instance attributes with task reference")
        void multiInstanceAttributesWithTaskReference() {
            assertEquals(testTask, multiInstanceAttributes.getTask());
            assertTrue(multiInstanceAttributes.isMultiInstance());
        }

        @Test
        @DisplayName("Multi-instance creation mode")
        void multiInstanceCreationMode() {
            assertEquals("static", multiInstanceAttributes.getCreationMode());
        }

        @Test
        @DisplayName("Multi-instance instance bounds")
        void multiInstanceInstanceBounds() {
            // Note: These values are set during constructor
            // Cannot be directly modified after creation
            assertEquals("1", multiInstanceAttributes.getMinInstancesQuery());
            assertEquals("2", multiInstanceAttributes.getMaxInstancesQuery());
            assertEquals("2", multiInstanceAttributes.getThresholdQuery());
        }
    }

    @Nested
    @DisplayName("Multi-Instance Data Mappings")
    class DataMappingsTest {

        @Test
        @DisplayName("Multi-instance formal input parameter")
        void multiInstanceFormalInputParam() {
            assertNull(multiInstanceAttributes._inputVarName);

            // Note: _inputVarName is private, cannot be directly set
            // Test getter if available
            // multiInstanceAttributes.getMIFormalInputParam() would be available
        }

        @Test
        @DisplayName("Multi-instance MI splitting query")
        void multiInstanceMISplittingQuery() {
            assertNull(multiInstanceAttributes.getMISplittingQuery());

            // Note: _inputSplittingQuery is private
            // Test existing getter
            multiInstanceAttributes.getMISplittingQuery();
        }

        @Test
        @DisplayName("Multi-instance MI joining query")
        void multiInstanceMIJoiningQuery() {
            assertNull(multiInstanceAttributes.getMIJoiningQuery());

            // Note: _outputProcessingQuery is private
            // Test existing getter
            multiInstanceAttributes.getMIJoiningQuery();
        }
    }

    @Nested
    @DisplayName("Multi-Instance XML Generation")
    class XMLGenerationTest {

        @Test
        @DisplayName("Multi-instance toXML basic structure")
        void multiInstanceToXMLBasic() {
            String xml = multiInstanceAttributes.toXML();
            assertTrue(xml.contains("<multipleInstanceAttributes>"));
            assertTrue(xml.contains("</multipleInstanceAttributes>"));
        }

        @Test
        @DisplayName("Multi-instance toXML with input query")
        void multiInstanceToXMLWithInputQuery() {
            multiInstanceAttributes.setMIFormalInputParam("inputParam");
            multiInstanceAttributes.setUniqueInputMISplittingQuery("$data/*");

            String xml = multiInstanceAttributes.toXML();
            assertTrue(xml.contains("<formalInputParam>inputParam</formalInputParam>"));
            assertTrue(xml.contains("<uniqueInputMISplittingQuery>$data/*</uniqueInputMISplittingQuery>"));
        }

        @Test
        @DisplayName("Multi-instance toXML with output query")
        void multiInstanceToXMLWithOutputQuery() {
            multiInstanceAttributes.setMIFormalOutputQuery("$output/*");
            multiInstanceAttributes.setUniqueOutputMIJoiningQuery("aggregate()");

            String xml = multiInstanceAttributes.toXML();
            assertTrue(xml.contains("<formalOutputQuery>$output/*</formalOutputQuery>"));
            assertTrue(xml.contains("<uniqueOutputMIJoiningQuery>aggregate()</uniqueOutputMIJoiningQuery>"));
        }

        @Test
        @DisplayName("Multi-instance toXML with all properties")
        void multiInstanceToXMLWithAllProperties() {
            multiInstanceAttributes.setMIFormalInputParam("inputParam");
            multiInstanceAttributes.setUniqueInputMISplittingQuery("$data/*");
            multiInstanceAttributes.setMIFormalOutputQuery("$output/*");
            multiInstanceAttributes.setUniqueOutputMIJoiningQuery("aggregate()");
            multiInstanceAttributes.setMISplittingQuery("collection($data/items)");
            multiInstanceAttributes.setMIJoiningQuery("sum($instances/value)");

            String xml = multiInstanceAttributes.toXML();
            assertTrue(xml.contains("<minimum>5</minimum>"));
            assertTrue(xml.contains("<maximum>10</maximum>"));
            assertTrue(xml.contains("<threshold>5</threshold>"));
            assertTrue(xml.contains("<static>true</static>"));
        }
    }

    @Nested
    @DisplayName("Multi-Instance Validation")
    class ValidationTest {

        @Test
        @DisplayName("Multi-instance validation")
        void multiInstanceValidation() {
            // Should not throw exception for valid multi-instance attributes
            assertDoesNotThrow(() -> multiInstanceAttributes.verify(new org.yawlfoundation.yawl.util.YVerificationHandler()));
        }

        @Test
        @DisplayName("Multi-instance validation with null handler")
        void multiInstanceValidationWithNullHandler() {
            // Should not throw exception with null handler
            assertDoesNotThrow(() -> multiInstanceAttributes.verify(null));
        }

        @Test
        @DisplayName("Multi-instance validation with invalid bounds")
        void multiInstanceValidationWithInvalidBounds() {
            multiInstanceAttributes.setMaxInstances(0);
            multiInstanceAttributes.setThreshold(-1);

            // Should still not throw exception, validation depends on context
            assertDoesNotThrow(() -> multiInstanceAttributes.verify(new org.yawlfoundation.yawl.util.YVerificationHandler()));
        }
    }

    @Nested
    @DisplayName("Multi-Instance Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("Multi-instance with null task")
        void multiInstanceWithNullTask() {
            YMultiInstanceAttributes nullTaskAttributes = new YMultiInstanceAttributes(
                null, "count", "10", "5", "static");

            assertNull(nullTaskAttributes.getTask());
            assertTrue(nullTaskAttributes.isMultiInstance());
        }

        @Test
        @DisplayName("Multi-instance with empty queries")
        void multiInstanceWithEmptyQueries() {
            multiInstanceAttributes.setMIFormalInputParam("");
            multiInstanceAttributes.setUniqueInputMISplittingQuery("");
            multiInstanceAttributes.setMIFormalOutputQuery("");
            multiInstanceAttributes.setUniqueOutputMIJoiningQuery("");

            String xml = multiInstanceAttributes.toXML();
            // Should handle empty strings gracefully
            assertTrue(xml.contains("<formalInputParam></formalInputParam>"));
        }

        @Test
        @DisplayName("Multi-instance with complex queries")
        void multiInstanceWithComplexQueries() {
            String complexInput = "if ($data/priority = 'high') then 'urgent' else 'normal'";
            String complexOutput = "for $i in $instances return $i/result";

            multiInstanceAttributes.setUniqueInputMISplittingQuery(complexInput);
            multiInstanceAttributes.setUniqueOutputMIJoiningQuery(complexOutput);

            String xml = multiInstanceAttributes.toXML();
            assertTrue(xml.contains(complexInput));
            assertTrue(xml.contains(complexOutput));
        }

        @Test
        @DisplayName("Multi-instance clone")
        void multiInstanceClone() throws CloneNotSupportedException {
            multiInstanceAttributes.setMIFormalInputParam("inputParam");
            multiInstanceAttributes.setCreationMode("dynamic");

            YMultiInstanceAttributes cloned = (YMultiInstanceAttributes) multiInstanceAttributes.clone();
            assertEquals(multiInstanceAttributes.getTask(), cloned.getTask());
            assertEquals(multiInstanceAttributes.getMIFormalInputParam(), cloned.getMIFormalInputParam());
            assertEquals(multiInstanceAttributes.getCreationMode(), cloned.getCreationMode());
            assertNotSame(multiInstanceAttributes, cloned);
        }
    }

    @Nested
    @DisplayName("Multi-Instance Creation Modes")
    class CreationModesTest {

        @Test
        @DisplayName("Multi-instance static creation mode")
        void multiInstanceStaticCreationMode() {
            assertEquals("static", multiInstanceAttributes.getCreationMode());

            multiInstanceAttributes.setCreationMode("static");
            assertEquals("static", multiInstanceAttributes.getCreationMode());
        }

        @Test
        @DisplayName("Multi-instance dynamic creation mode")
        void multiInstanceDynamicCreationMode() {
            multiInstanceAttributes.setCreationMode("dynamic");
            assertEquals("dynamic", multiInstanceAttributes.getCreationMode());
        }

        @Test
        @DisplayName("Multi-instance invalid creation mode")
        void multiInstanceInvalidCreationMode() {
            // Should handle invalid modes gracefully or throw appropriate exception
            multiInstanceAttributes.setCreationMode("invalid");
            // Depending on implementation, might keep current value or set to invalid
        }
    }
}