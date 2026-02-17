/*
 * Copyright (c) 2004-2024 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.stateless;

import org.jdom2.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.monitor.YCaseExporter;
import org.yawlfoundation.yawl.stateless.monitor.YCaseImporter;
import org.yawlfoundation.yawl.stateless.unmarshal.YMarshal;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.yawlfoundation.yawl.exceptions.YQueryException;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for stateless marshalling (case import/export).
 * Tests serialization round-trips and XML generation.
 *
 * @author YAWL Test Suite
 */
class TestStatelessMarshalling {

    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";

    private YSpecification specification;
    private YStatelessEngine engine;


    @BeforeEach
    void setUp() throws YSyntaxException {
        InputStream is = getClass().getResourceAsStream(MINIMAL_SPEC_RESOURCE);
        assertNotNull(is, "Test specification file must exist: " + MINIMAL_SPEC_RESOURCE);
        String specXML = StringUtil.streamToString(is);
        assertNotNull(specXML, "Specification XML must not be null");

        List<YSpecification> specs = YMarshal.unmarshalSpecifications(specXML);
        assertFalse(specs.isEmpty(), "Should parse at least one specification");
        specification = specs.get(0);

        engine = new YStatelessEngine();
    }


    @Test
    @DisplayName("Test specification marshal to XML")
    void testSpecificationMarshalToXML() {
        String xml = YMarshal.marshal(specification);
        assertNotNull(xml, "Marshalled XML should not be null");
        assertFalse(xml.isEmpty(), "Marshalled XML should not be empty");
        assertTrue(xml.contains("<specification"), "XML should contain specification element");
        assertTrue(xml.contains("uri="), "XML should contain URI attribute");
    }


    @Test
    @DisplayName("Test specification unmarshal round trip")
    void testSpecificationUnmarshalRoundTrip() throws YSyntaxException {
        String xml = YMarshal.marshal(specification);
        List<YSpecification> reParsed = YMarshal.unmarshalSpecifications(xml);

        assertEquals(1, reParsed.size(), "Should parse exactly one specification");
        YSpecification reSpec = reParsed.get(0);

        assertEquals(specification.getURI(), reSpec.getURI(), "URI should match after round trip");
        assertNotNull(reSpec.getRootNet(), "Root net should not be null after round trip");
    }


    @Test
    @DisplayName("Test case exporter instantiation")
    void testCaseExporterInstantiation() {
        YCaseExporter exporter = new YCaseExporter();
        assertNotNull(exporter, "Exporter should be instantiable");
    }


    @Test
    @DisplayName("Test case importer instantiation")
    void testCaseImporterInstantiation() {
        YCaseImporter importer = new YCaseImporter();
        assertNotNull(importer, "Importer should be instantiable");
    }


    @Test
    @DisplayName("Test case marshal from runner")
    void testCaseMarshalFromRunner() throws YStateException, YDataStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification, "test-case-marshal-001");
        assertNotNull(runner, "Runner should be created");

        String caseXML = engine.marshalCase(runner);
        assertNotNull(caseXML, "Case XML should not be null");
        assertFalse(caseXML.isEmpty(), "Case XML should not be empty");
        assertTrue(caseXML.contains("<case"), "Case XML should contain case element");
        assertTrue(caseXML.contains("test-case-marshal-001"), "Case XML should contain case ID");
    }


    @Test
    @DisplayName("Test case export contains specification")
    void testCaseExportContainsSpecification() throws YStateException, YDataStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification, "test-case-spec-001");
        String caseXML = engine.marshalCase(runner);

        assertTrue(caseXML.contains("<specificationSet"), "Case XML should contain specification set");
    }


    @Test
    @DisplayName("Test case export contains runner data")
    void testCaseExportContainsRunnerData() throws YStateException, YDataStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification, "test-case-runner-001");
        String caseXML = engine.marshalCase(runner);

        assertTrue(caseXML.contains("<runners"), "Case XML should contain runners element");
        assertTrue(caseXML.contains("<runner"), "Case XML should contain runner element");
    }


    @Test
    @DisplayName("Test marshal with null runner throws exception")
    void testMarshalWithNullRunnerThrowsException() {
        YStateException exception = assertThrows(YStateException.class, () -> {
            engine.marshalCase(null);
        });
        assertTrue(exception.getMessage().contains("null"), "Exception should mention null");
    }


    @Test
    @DisplayName("Test schema version in marshal")
    void testSchemaVersionInMarshal() {
        String xml = YMarshal.marshal(specification);
        assertNotNull(xml, "XML should not be null");

        YSchemaVersion version = specification.getSchemaVersion();
        assertNotNull(version, "Schema version should not be null");
    }


    @Test
    @DisplayName("Test YMarshal unmarshal with invalid XML throws exception")
    void testUnmarshalWithInvalidXMLThrowsException() {
        assertThrows(YSyntaxException.class, () -> {
            YMarshal.unmarshalSpecifications("not valid xml");
        });
    }


    @Test
    @DisplayName("Test YMarshal unmarshal with null throws exception")
    void testUnmarshalWithNullThrowsException() {
        assertThrows(YSyntaxException.class, () -> {
            YMarshal.unmarshalSpecifications((String) null);
        });
    }


    @Test
    @DisplayName("Test identifier creation for case")
    void testIdentifierCreationForCase() {
        YIdentifier id = new YIdentifier("case-id-123");
        assertNotNull(id, "Identifier should be created");
        assertEquals("case-id-123", id.toString(), "ID string should match");
    }


    @Test
    @DisplayName("Test identifier with null creates UUID")
    void testIdentifierWithNullCreatesUuid() {
        YIdentifier id = new YIdentifier((String) null);
        assertNotNull(id, "Identifier should be created");
        assertNotNull(id.toString(), "ID string should not be null");
        assertFalse(id.toString().isEmpty(), "ID string should not be empty");
    }


    @Test
    @DisplayName("Test identifier parent child relationship")
    void testIdentifierParentChildRelationship() {
        YIdentifier parent = new YIdentifier("parent-id");
        YIdentifier child = parent.createChild();

        assertNotNull(child, "Child should be created");
        assertEquals(parent, child.getParent(), "Child's parent should match");
        assertTrue(parent.getChildren().contains(child), "Parent should contain child");
    }


    @Test
    @DisplayName("Test identifier child numbering")
    void testIdentifierChildNumbering() {
        YIdentifier parent = new YIdentifier("parent");
        YIdentifier child1 = parent.createChild(1);
        YIdentifier child2 = parent.createChild(2);

        assertTrue(child1.toString().endsWith(".1"), "First child should end with .1");
        assertTrue(child2.toString().endsWith(".2"), "Second child should end with .2");
    }


    @Test
    @DisplayName("Test identifier create child with invalid number throws exception")
    void testIdentifierCreateChildInvalidNumber() {
        YIdentifier parent = new YIdentifier("parent");

        assertThrows(IllegalArgumentException.class, () -> {
            parent.createChild(0);
        });
    }


    @Test
    @DisplayName("Test JDOMUtil string to document")
    void testJDOMUtilStringToDocument() {
        String xml = "<root><child>value</child></root>";
        Document doc = JDOMUtil.stringToDocument(xml);

        assertNotNull(doc, "Document should not be null");
        assertEquals("root", doc.getRootElement().getName(), "Root element should be 'root'");
    }


    @Test
    @DisplayName("Test JDOMUtil document to string")
    void testJDOMUtilDocumentToString() {
        String xml = "<root><child>value</child></root>";
        Document doc = JDOMUtil.stringToDocument(xml);
        String result = JDOMUtil.documentToString(doc);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("<root>"), "Result should contain root element");
    }


    @Test
    @DisplayName("Test case export with enabled tasks")
    void testCaseExportWithEnabledTasks() throws YStateException, YDataStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification, "test-enabled-001");
        String caseXML = engine.marshalCase(runner);

        assertTrue(caseXML.contains("<enabledtasks>") || caseXML.contains("enabled"),
                "Case XML should contain enabled tasks information");
    }


    @Test
    @DisplayName("Test specification to XML contains root net")
    void testSpecificationToXMLContainsRootNet() {
        String xml = specification.toXML();
        assertNotNull(xml, "XML should not be null");
        assertTrue(xml.contains("isRootNet=\"true\""), "XML should mark root net");
    }
}
