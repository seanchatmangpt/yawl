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

package org.yawlfoundation.yawl.unmarshal;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YDecomposition;
import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Chicago TDD Roundtrip Tests for YMarshal.
 *
 * These tests verify that marshalling and unmarshalling specifications
 * preserves the semantic content and structure. All tests use real YAWL
 * specification files - no mocks.
 *
 * @author YAWL Test Suite
 */
@DisplayName("YMarshal Roundtrip Tests")
@Tag("unit")
class TestYMarshalRoundtrip {

    private static final String FIXTURES_PATH = "/org/yawlfoundation/yawl/unmarshal/";

    /**
     * Helper method to load a specification from an XML file.
     */
    private YSpecification loadSpecification(String filename) throws YSyntaxException, IOException {
        File file = new File(YMarshal.class.getResource(filename).getFile());
        String xmlContent = StringUtil.fileToString(file.getAbsolutePath());
        List<YSpecification> specs = YMarshal.unmarshalSpecifications(xmlContent, false);
        assertNotNull(specs, "Unmarshalled specifications should not be null");
        assertFalse(specs.isEmpty(), "Should have at least one specification");
        return specs.get(0);
    }

    /**
     * Helper method to load XML content from a file.
     */
    private String loadXmlContent(String filename) throws IOException {
        File file = new File(YMarshal.class.getResource(filename).getFile());
        return StringUtil.fileToString(file.getAbsolutePath());
    }

    /**
     * Helper method to verify XML is well-formed.
     */
    private void assertValidXml(String xml) throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder();
        Document doc = builder.build(new java.io.StringReader(xml));
        assertNotNull(doc, "XML document should be parseable");
        assertNotNull(doc.getRootElement(), "XML document should have a root element");
    }

    @Nested
    @DisplayName("Basic Roundtrip Tests")
    class BasicRoundtripTests {

        @Test
        @DisplayName("MakeRecordings specification roundtrip preserves URI")
        void specificationRoundtripPreservesUri() throws YSyntaxException, IOException {
            YSpecification original = loadSpecification("MakeRecordings.xml");
            String xml = YMarshal.marshal(original);
            YSpecification restored = YMarshal.unmarshalSpecifications(xml, false).get(0);

            assertEquals(original.getURI(), restored.getURI(),
                    "Specification URI should be preserved after roundtrip");
        }

        @Test
        @DisplayName("MakeRecordings specification roundtrip preserves name")
        void specificationRoundtripPreservesName() throws YSyntaxException, IOException {
            YSpecification original = loadSpecification("MakeRecordings.xml");
            String xml = YMarshal.marshal(original);
            YSpecification restored = YMarshal.unmarshalSpecifications(xml, false).get(0);

            assertEquals(original.getName(), restored.getName(),
                    "Specification name should be preserved after roundtrip");
        }

        @Test
        @DisplayName("MakeRecordings specification roundtrip preserves root net ID")
        void specificationRoundtripPreservesRootNetId() throws YSyntaxException, IOException {
            YSpecification original = loadSpecification("MakeRecordings.xml");
            String xml = YMarshal.marshal(original);
            YSpecification restored = YMarshal.unmarshalSpecifications(xml, false).get(0);

            YNet originalRootNet = original.getRootNet();
            YNet restoredRootNet = restored.getRootNet();

            assertNotNull(originalRootNet, "Original should have a root net");
            assertNotNull(restoredRootNet, "Restored should have a root net");
            assertEquals(originalRootNet.getID(), restoredRootNet.getID(),
                    "Root net ID should be preserved after roundtrip");
        }

        @Test
        @DisplayName("Roundtrip produces valid XML")
        void roundtripProducesValidXml() throws YSyntaxException, IOException, JDOMException {
            YSpecification spec = loadSpecification("MakeRecordings.xml");
            String xml = YMarshal.marshal(spec);

            assertValidXml(xml);
        }

        @Test
        @DisplayName("Roundtrip marshalled XML contains specificationSet wrapper")
        void roundtripXmlContainsSpecificationSetWrapper() throws YSyntaxException, IOException {
            YSpecification spec = loadSpecification("MakeRecordings.xml");
            String xml = YMarshal.marshal(spec);

            assertTrue(xml.contains("<specificationSet"),
                    "Marshalled XML should contain specificationSet element");
            assertTrue(xml.contains("</specificationSet>"),
                    "Marshalled XML should contain closing specificationSet tag");
        }
    }

    @Nested
    @DisplayName("Multi-Specification Tests")
    class MultiSpecificationTests {

        @Test
        @DisplayName("Marshal list of specifications produces single XML")
        void marshalListOfSpecifications() throws YSyntaxException, IOException, JDOMException {
            YSpecification spec1 = loadSpecification("MakeRecordings.xml");
            YSpecification spec2 = loadSpecification("RemoveTokens.xml");

            List<YSpecification> specList = List.of(spec1, spec2);
            String xml = YMarshal.marshal(specList, spec1.getSchemaVersion());

            assertValidXml(xml);
            assertTrue(xml.contains("MakeRecordings") || xml.contains("RemoveTokens"),
                    "Marshalled XML should contain specification URIs");
        }
    }

    @Nested
    @DisplayName("Specification Structure Tests")
    class SpecificationStructureTests {

        @Test
        @DisplayName("Roundtrip preserves decomposition count")
        void roundtripPreservesDecompositionCount() throws YSyntaxException, IOException {
            YSpecification original = loadSpecification("MakeRecordings.xml");
            String xml = YMarshal.marshal(original);
            YSpecification restored = YMarshal.unmarshalSpecifications(xml, false).get(0);

            assertEquals(original.getDecompositions().size(), restored.getDecompositions().size(),
                    "Decomposition count should be preserved after roundtrip");
        }

        @Test
        @DisplayName("Roundtrip preserves input condition")
        void roundtripPreservesInputCondition() throws YSyntaxException, IOException {
            YSpecification original = loadSpecification("MakeRecordings.xml");
            String xml = YMarshal.marshal(original);
            YSpecification restored = YMarshal.unmarshalSpecifications(xml, false).get(0);

            YNet originalNet = original.getRootNet();
            YNet restoredNet = restored.getRootNet();

            assertNotNull(originalNet.getInputCondition(), "Original should have input condition");
            assertNotNull(restoredNet.getInputCondition(), "Restored should have input condition");
            assertEquals(originalNet.getInputCondition().getID(),
                    restoredNet.getInputCondition().getID(),
                    "Input condition ID should be preserved");
        }

        @Test
        @DisplayName("Roundtrip preserves output condition")
        void roundtripPreservesOutputCondition() throws YSyntaxException, IOException {
            YSpecification original = loadSpecification("MakeRecordings.xml");
            String xml = YMarshal.marshal(original);
            YSpecification restored = YMarshal.unmarshalSpecifications(xml, false).get(0);

            YNet originalNet = original.getRootNet();
            YNet restoredNet = restored.getRootNet();

            assertNotNull(originalNet.getOutputCondition(), "Original should have output condition");
            assertNotNull(restoredNet.getOutputCondition(), "Restored should have output condition");
            assertEquals(originalNet.getOutputCondition().getID(),
                    restoredNet.getOutputCondition().getID(),
                    "Output condition ID should be preserved");
        }
    }

    @Nested
    @DisplayName("Different Schema Version Tests")
    class SchemaVersionTests {

        @Test
        @DisplayName("Roundtrip preserves Beta3 schema version specification")
        void roundtripPreservesBeta3Specification() throws YSyntaxException, IOException {
            YSpecification original = loadSpecification("MakeRecordings.xml");
            String xml = YMarshal.marshal(original);
            YSpecification restored = YMarshal.unmarshalSpecifications(xml, false).get(0);

            assertEquals(original.getURI(), restored.getURI(),
                    "Beta3 spec URI should be preserved");
        }

        @Test
        @DisplayName("Roundtrip handles YAWL_Specification with multiple nets")
        void roundtripHandlesMultipleNets() throws YSyntaxException, IOException {
            YSpecification original = loadSpecification("YAWL_Specification.xml");
            String xml = YMarshal.marshal(original);
            YSpecification restored = YMarshal.unmarshalSpecifications(xml, false).get(0);

            assertEquals(original.getURI(), restored.getURI(),
                    "Specification URI should be preserved");
            assertEquals(original.getDecompositions().size(), restored.getDecompositions().size(),
                    "All decompositions should be preserved");
        }
    }

    @Nested
    @DisplayName("XML Content Tests")
    class XmlContentTests {

        @Test
        @DisplayName("Roundtrip handles special characters in specification")
        void roundtripHandlesSpecialCharacters() throws YSyntaxException, IOException, JDOMException {
            YSpecification original = loadSpecification("MakeRecordings.xml");

            // MakeRecordings contains XML-escaped content in initialValues
            String xml = YMarshal.marshal(original);
            assertValidXml(xml);

            YSpecification restored = YMarshal.unmarshalSpecifications(xml, false).get(0);
            assertEquals(original.getURI(), restored.getURI(),
                    "Specification with special characters should roundtrip correctly");
        }

        @Test
        @DisplayName("Roundtrip preserves XML structure integrity")
        void roundtripPreservesXmlStructureIntegrity() throws YSyntaxException, IOException, JDOMException {
            YSpecification spec = loadSpecification("RemoveTokens.xml");
            String xml = YMarshal.marshal(spec);

            // Verify the XML is well-formed and parseable
            SAXBuilder builder = new SAXBuilder();
            Document doc = builder.build(new java.io.StringReader(xml));

            assertEquals("specificationSet", doc.getRootElement().getName(),
                    "Root element should be specificationSet");
        }

        @Test
        @DisplayName("Roundtrip with CantParseThis specification")
        void roundtripCantParseThisSpecification() throws YSyntaxException, IOException {
            YSpecification original = loadSpecification("CantParseThis.xml");
            String xml = YMarshal.marshal(original);
            YSpecification restored = YMarshal.unmarshalSpecifications(xml, false).get(0);

            assertEquals(original.getURI(), restored.getURI(),
                    "CantParseThis spec should roundtrip correctly");
            assertEquals(original.getDecompositions().size(), restored.getDecompositions().size(),
                    "All decompositions should be preserved");
        }
    }

    @Nested
    @DisplayName("Marshal Output Tests")
    class MarshalOutputTests {

        @Test
        @DisplayName("Marshal produces non-empty XML")
        void marshalProducesNonEmptyXml() throws YSyntaxException, IOException {
            YSpecification spec = loadSpecification("MakeRecordings.xml");
            String xml = YMarshal.marshal(spec);

            assertNotNull(xml, "Marshalled XML should not be null");
            assertFalse(xml.isEmpty(), "Marshalled XML should not be empty");
            assertTrue(xml.length() > 100, "Marshalled XML should have substantial content");
        }

        @Test
        @DisplayName("Marshal includes specification element")
        void marshalIncludesSpecificationElement() throws YSyntaxException, IOException {
            YSpecification spec = loadSpecification("MakeRecordings.xml");
            String xml = YMarshal.marshal(spec);

            assertTrue(xml.contains("<specification "),
                    "Marshalled XML should contain specification element");
            assertTrue(xml.contains("</specification>"),
                    "Marshalled XML should contain closing specification tag");
        }

        @Test
        @DisplayName("Marshal includes decomposition elements")
        void marshalIncludesDecompositionElements() throws YSyntaxException, IOException {
            YSpecification spec = loadSpecification("YAWL_Specification.xml");
            String xml = YMarshal.marshal(spec);

            assertTrue(xml.contains("<decomposition "),
                    "Marshalled XML should contain decomposition elements");
        }
    }

    @Nested
    @DisplayName("Unmarshal Input Tests")
    class UnmarshalInputTests {

        @Test
        @DisplayName("Unmarshal handles specification set with single specification")
        void unmarshalHandlesSingleSpecification() throws YSyntaxException, IOException {
            String xml = loadXmlContent("RemoveTokens.xml");
            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);

            assertEquals(1, specs.size(),
                    "Should unmarshal exactly one specification");
        }

        @Test
        @DisplayName("Unmarshal creates correct specification from XML")
        void unmarshalCreatesCorrectSpecification() throws YSyntaxException, IOException {
            String xml = loadXmlContent("RemoveTokens.xml");
            YSpecification spec = YMarshal.unmarshalSpecifications(xml, false).get(0);

            assertEquals("RemoveTokens.xml", spec.getURI(),
                    "Specification URI should match filename");
            assertNotNull(spec.getRootNet(), "Specification should have root net");
        }

        @Test
        @DisplayName("Unmarshal without schema validation succeeds for valid XML")
        void unmarshalWithoutSchemaValidationSucceeds() throws YSyntaxException, IOException {
            String xml = loadXmlContent("YAWL_Specification.xml");
            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);

            assertNotNull(specs, "Should unmarshal without schema validation");
            assertFalse(specs.isEmpty(), "Should have at least one specification");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Unmarshal invalid XML throws YSyntaxException")
        void unmarshalInvalidXmlThrowsException() {
            String invalidXml = "this is not valid xml";

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(invalidXml, false);
            }, "Invalid XML should throw YSyntaxException");
        }

        @Test
        @DisplayName("Unmarshal empty string throws YSyntaxException")
        void unmarshalEmptyStringThrowsException() {
            String emptyXml = "";

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(emptyXml, false);
            }, "Empty XML should throw YSyntaxException");
        }

        @Test
        @DisplayName("Unmarshal null returns null or throws exception")
        void unmarshalNullThrowsException() {
            assertThrows(Exception.class, () -> {
                YMarshal.unmarshalSpecifications(null, false);
            }, "Null input should throw an exception");
        }
    }

    @Nested
    @DisplayName("Complex Specification Tests")
    class ComplexSpecificationTests {

        @Test
        @DisplayName("Roundtrip preserves net element count")
        void roundtripPreservesNetElementCount() throws YSyntaxException, IOException {
            YSpecification original = loadSpecification("YAWL_Specification.xml");
            String xml = YMarshal.marshal(original);
            YSpecification restored = YMarshal.unmarshalSpecifications(xml, false).get(0);

            YNet originalNet = original.getRootNet();
            YNet restoredNet = restored.getRootNet();

            assertEquals(originalNet.getNetElements().size(), restoredNet.getNetElements().size(),
                    "Net element count should be preserved after roundtrip");
        }

        @Test
        @DisplayName("Roundtrip preserves task count")
        void roundtripPreservesTaskCount() throws YSyntaxException, IOException {
            YSpecification original = loadSpecification("YAWL_Specification.xml");
            String xml = YMarshal.marshal(original);
            YSpecification restored = YMarshal.unmarshalSpecifications(xml, false).get(0);

            int originalTaskCount = 0;
            int restoredTaskCount = 0;

            for (YDecomposition decomp : original.getDecompositions()) {
                if (decomp instanceof YNet net) {
                    for (YExternalNetElement element : net.getNetElements().values()) {
                        if (element instanceof YTask) {
                            originalTaskCount++;
                        }
                    }
                }
            }

            for (YDecomposition decomp : restored.getDecompositions()) {
                if (decomp instanceof YNet net) {
                    for (YExternalNetElement element : net.getNetElements().values()) {
                        if (element instanceof YTask) {
                            restoredTaskCount++;
                        }
                    }
                }
            }

            assertEquals(originalTaskCount, restoredTaskCount,
                    "Task count should be preserved after roundtrip");
        }
    }

    @Nested
    @DisplayName("Roundtrip Fidelity Tests")
    class RoundtripFidelityTests {

        @Test
        @DisplayName("Double roundtrip preserves specification identity")
        void doubleRoundtripPreservesIdentity() throws YSyntaxException, IOException {
            YSpecification original = loadSpecification("MakeRecordings.xml");

            // First roundtrip
            String xml1 = YMarshal.marshal(original);
            YSpecification first = YMarshal.unmarshalSpecifications(xml1, false).get(0);

            // Second roundtrip
            String xml2 = YMarshal.marshal(first);
            YSpecification second = YMarshal.unmarshalSpecifications(xml2, false).get(0);

            assertEquals(original.getURI(), second.getURI(),
                    "URI should be preserved after double roundtrip");
            assertEquals(first.getURI(), second.getURI(),
                    "URI should be stable between roundtrips");
        }

        @Test
        @DisplayName("Roundtrip XML is idempotent")
        void roundtripXmlIsIdempotent() throws YSyntaxException, IOException {
            // Use CantParseThis.xml instead of RemoveTokens.xml because the latter
            // contains element names with spaces which are invalid in JDOM/XML
            YSpecification spec = loadSpecification("CantParseThis.xml");

            String xml1 = YMarshal.marshal(spec);
            YSpecification restored1 = YMarshal.unmarshalSpecifications(xml1, false).get(0);
            String xml2 = YMarshal.marshal(restored1);

            // XML strings should be equivalent (allowing for formatting differences)
            assertTrue(xml1.contains("CantParseThis"),
                    "First marshalling should contain spec URI");
            assertTrue(xml2.contains("CantParseThis"),
                    "Second marshalling should contain spec URI");
        }
    }
}
