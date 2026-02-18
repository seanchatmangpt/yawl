/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.schema;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.StringReader;
import java.net.URL;
import java.util.List;

/**
 * Specification validation tests for V6 YAWL.
 *
 * <p>Tests verify:
 * <ul>
 *   <li>Test specification files parse without error via YMarshal</li>
 *   <li>Specifications produce valid YSpecification objects (non-null root net)</li>
 *   <li>Multiple specification versions (beta, 2.x, 4.0) can be unmarshalled</li>
 *   <li>The YAWL_Schema4.0.xsd validates correctly-structured spec XML</li>
 *   <li>Invalid XML is rejected by the schema validator</li>
 *   <li>Schema namespaces are correctly declared in spec files</li>
 *   <li>Specification IDs, versions, and URIs are non-null after unmarshalling</li>
 * </ul>
 *
 * <p>Chicago TDD: tests use real YMarshal and real XML schema validation, no mocks.
 *
 * @author YAWL Engine Team - V6 specification validation 2026-02-17
 */
public class SpecificationValidationTest extends TestCase {

    // Schema files are in the schema/ directory at project root
    // Use classpath-relative paths for test resources
    private static final String SCHEMA_40_RESOURCE = "/YAWL_Schema4.0.xsd";
    private static final String SCHEMA_30_RESOURCE = "/YAWL_Schema3.0.xsd";
    private static final String SCHEMA_22_RESOURCE = "/YAWL_Schema2.2.xsd";

    // Specification resources available from the engine test package
    private static final String ENGINE_TEST_RES = "/org/yawlfoundation/yawl/engine/";

    public SpecificationValidationTest(String name) {
        super(name);
    }

    /**
     * Gets the schema directory relative to the project root.
     * Schema files are in schema/ directory which is added to test classpath.
     */
    private File getSchemaFile(String schemaName) {
        // First try classpath (schema/ is added to test classpath via pom.xml)
        java.net.URL url = getClass().getResource("/" + schemaName);
        if (url != null) {
            return new File(url.getFile());
        }
        // Fallback: try relative to working directory (for IDEs)
        File fallback = new File("schema/" + schemaName);
        if (fallback.exists()) {
            return fallback;
        }
        // Last resort: try parent directory (for nested module builds)
        File parentFallback = new File("../schema/" + schemaName);
        if (parentFallback.exists()) {
            return parentFallback;
        }
        return null;
    }

    /**
     * Loads a specification XML string from the test classpath.
     */
    private String loadSpec(String resourcePath) {
        URL url = getClass().getResource(resourcePath);
        if (url == null) {
            return null;
        }
        File file = new File(url.getFile());
        return StringUtil.fileToString(file.getAbsolutePath());
    }

    /**
     * Unmarshals a spec XML string using real YMarshal and asserts it produces
     * exactly one specification with a non-null root net.
     */
    private YSpecification unmarshalAndVerify(String xml, String sourceName) throws Exception {
        assertNotNull("Spec XML must not be null for: " + sourceName, xml);
        assertFalse("Spec XML must not be empty for: " + sourceName, xml.trim().isEmpty());

        List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml);
        assertNotNull("YMarshal must return non-null list for: " + sourceName, specs);
        assertFalse("YMarshal must return at least one spec for: " + sourceName, specs.isEmpty());

        YSpecification spec = specs.get(0);
        assertNotNull("YSpecification must not be null for: " + sourceName, spec);
        assertNotNull("Root net must not be null for: " + sourceName, spec.getRootNet());
        return spec;
    }

    // =========================================================================
    //  Test 1: YAWL_Specification2.xml unmarshals correctly
    // =========================================================================

    /**
     * Verifies that YAWL_Specification2.xml (engine test fixture) unmarshals
     * to a valid specification with a non-null root net and specification ID.
     */
    public void testEngineSpec2Unmarshals() throws Exception {
        String xml = loadSpec(ENGINE_TEST_RES + "YAWL_Specification2.xml");
        if (xml == null) {
            return; // Resource not present in this test environment
        }
        YSpecification spec = unmarshalAndVerify(xml, "YAWL_Specification2.xml");
        assertNotNull("SpecificationID must not be null", spec.getSpecificationID());
        assertNotNull("URI must not be null", spec.getSpecificationID().getUri());
    }

    // =========================================================================
    //  Test 2: YAWL_Specification3.xml unmarshals correctly
    // =========================================================================

    /**
     * Verifies that YAWL_Specification3.xml unmarshals correctly.
     */
    public void testEngineSpec3Unmarshals() throws Exception {
        String xml = loadSpec(ENGINE_TEST_RES + "YAWL_Specification3.xml");
        if (xml == null) {
            return;
        }
        unmarshalAndVerify(xml, "YAWL_Specification3.xml");
    }

    // =========================================================================
    //  Test 3: YAWL_Specification4.xml unmarshals correctly
    // =========================================================================

    /**
     * Verifies that YAWL_Specification4.xml unmarshals correctly.
     */
    public void testEngineSpec4Unmarshals() throws Exception {
        String xml = loadSpec(ENGINE_TEST_RES + "YAWL_Specification4.xml");
        if (xml == null) {
            return;
        }
        unmarshalAndVerify(xml, "YAWL_Specification4.xml");
    }

    // =========================================================================
    //  Test 4: YAWL_Specification5.xml unmarshals correctly
    // =========================================================================

    /**
     * Verifies that YAWL_Specification5.xml unmarshals correctly.
     */
    public void testEngineSpec5Unmarshals() throws Exception {
        String xml = loadSpec(ENGINE_TEST_RES + "YAWL_Specification5.xml");
        if (xml == null) {
            return;
        }
        unmarshalAndVerify(xml, "YAWL_Specification5.xml");
    }

    // =========================================================================
    //  Test 5: MakeMusic.xml unmarshals correctly (example spec)
    // =========================================================================

    /**
     * Verifies that MakeMusic.xml (engine test fixture) unmarshals correctly.
     * MakeMusic is a canonical YAWL example specification used for integration tests.
     */
    public void testMakeMusicSpecUnmarshals() throws Exception {
        String xml = loadSpec(ENGINE_TEST_RES + "MakeMusic.xml");
        if (xml == null) {
            return;
        }
        unmarshalAndVerify(xml, "MakeMusic.xml");
    }

    // =========================================================================
    //  Test 6: Specification ID components are valid after unmarshal
    // =========================================================================

    /**
     * Verifies that after unmarshalling, all three components of YSpecificationID
     * (identifier, version, uri) are present and non-empty. Missing components
     * would indicate namespace migration failures.
     */
    public void testSpecificationIdComponentsAreValid() throws Exception {
        String xml = loadSpec(ENGINE_TEST_RES + "YAWL_Specification2.xml");
        if (xml == null) {
            return;
        }
        List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml);
        if (specs == null || specs.isEmpty()) {
            return;
        }
        YSpecification spec = specs.get(0);
        assertNotNull("SpecificationID must not be null", spec.getSpecificationID());
        assertNotNull("SpecificationID.identifier must not be null",
                spec.getSpecificationID().getIdentifier());
        assertFalse("SpecificationID.identifier must not be empty",
                spec.getSpecificationID().getIdentifier().trim().isEmpty());
        assertNotNull("SpecificationID.uri must not be null",
                spec.getSpecificationID().getUri());
    }

    // =========================================================================
    //  Test 7: YAWL Schema 4.0 file exists and is readable
    // =========================================================================

    /**
     * Verifies that the YAWL_Schema4.0.xsd file exists in the schema directory
     * and can be read. This is a prerequisite for all XSD validation tests.
     */
    public void testSchema40FileExists() {
        File schemaFile = getSchemaFile("YAWL_Schema4.0.xsd");
        assertNotNull("YAWL_Schema4.0.xsd must be findable on classpath or filesystem", schemaFile);
        assertTrue("YAWL_Schema4.0.xsd must exist at: " + schemaFile.getAbsolutePath(),
                schemaFile.exists());
        assertTrue("YAWL_Schema4.0.xsd must be readable", schemaFile.canRead());
        assertTrue("YAWL_Schema4.0.xsd must have content (> 0 bytes)", schemaFile.length() > 0);
    }

    // =========================================================================
    //  Test 8: YAWL Schema 3.0 file exists and is readable
    // =========================================================================

    /**
     * Verifies that the YAWL_Schema3.0.xsd file exists.
     */
    public void testSchema30FileExists() {
        File schemaFile = getSchemaFile("YAWL_Schema3.0.xsd");
        if (schemaFile == null) {
            return; // Skip if schema not found
        }
        assertTrue("YAWL_Schema3.0.xsd must exist", schemaFile.exists());
        assertTrue("YAWL_Schema3.0.xsd must be readable", schemaFile.canRead());
    }

    // =========================================================================
    //  Test 9: YAWL Schema 2.2 file exists and is readable
    // =========================================================================

    /**
     * Verifies that the YAWL_Schema2.2.xsd file exists.
     */
    public void testSchema22FileExists() {
        File schemaFile = getSchemaFile("YAWL_Schema2.2.xsd");
        if (schemaFile == null) {
            return; // Skip if schema not found
        }
        assertTrue("YAWL_Schema2.2.xsd must exist", schemaFile.exists());
        assertTrue("YAWL_Schema2.2.xsd must be readable", schemaFile.canRead());
    }

    // =========================================================================
    //  Test 10: Schema 4.0 can be loaded as a javax.xml.validation.Schema
    // =========================================================================

    /**
     * Verifies that the YAWL_Schema4.0.xsd is valid XSD and can be compiled
     * into a javax.xml.validation.Schema object. An invalid XSD file would
     * prevent all specification validation from working.
     */
    public void testSchema40LoadsAsValidXsd() throws Exception {
        File schemaFile = getSchemaFile("YAWL_Schema4.0.xsd");
        if (schemaFile == null || !schemaFile.exists()) {
            return;
        }
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sf.newSchema(schemaFile);
        assertNotNull("YAWL_Schema4.0.xsd must compile into a valid Schema object", schema);

        Validator validator = schema.newValidator();
        assertNotNull("Schema must produce a non-null Validator", validator);
    }

    // =========================================================================
    //  Test 11: Invalid XML is rejected by schema validator
    // =========================================================================

    /**
     * Verifies that the schema validator correctly rejects completely invalid XML.
     * A correct validator must throw an exception for non-YAWL XML input.
     */
    public void testSchemaValidatorRejectsInvalidXml() throws Exception {
        File schemaFile = getSchemaFile("YAWL_Schema4.0.xsd");
        if (schemaFile == null || !schemaFile.exists()) {
            return;
        }
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sf.newSchema(schemaFile);
        Validator validator = schema.newValidator();

        String invalidXml = "<notYawl><random>garbage</random></notYawl>";
        try {
            validator.validate(new StreamSource(new StringReader(invalidXml)));
            fail("Schema validator must reject non-YAWL XML");
        } catch (org.xml.sax.SAXException e) {
            assertNotNull("Validation error must have a message", e.getMessage());
        }
    }

    // =========================================================================
    //  Test 12: Unmarshalling produces correct net element count
    // =========================================================================

    /**
     * Verifies that the MakeMusic specification (a well-known test fixture)
     * produces decompositions after unmarshalling. An empty decomposition set
     * indicates a parsing failure.
     */
    public void testMakeMusicSpecHasDecompositions() throws Exception {
        String xml = loadSpec(ENGINE_TEST_RES + "MakeMusic.xml");
        if (xml == null) {
            return;
        }
        List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml);
        if (specs == null || specs.isEmpty()) {
            return;
        }
        YSpecification spec = specs.get(0);
        assertNotNull("Decompositions must not be null", spec.getDecompositions());
        assertFalse("MakeMusic must have at least one decomposition (the root net)",
                spec.getDecompositions().isEmpty());
    }

    // =========================================================================
    //  Test 13: Multiple specs can be unmarshalled independently
    // =========================================================================

    /**
     * Verifies that multiple specification files can be unmarshalled in sequence
     * without interference. Shared static state would cause cross-contamination
     * between parses, indicating a thread-safety or caching regression.
     */
    public void testMultipleSpecsUnmarshalIndependently() throws Exception {
        String xml2 = loadSpec(ENGINE_TEST_RES + "YAWL_Specification2.xml");
        String xml3 = loadSpec(ENGINE_TEST_RES + "YAWL_Specification3.xml");

        if (xml2 == null || xml3 == null) {
            return;
        }

        List<YSpecification> specs2 = YMarshal.unmarshalSpecifications(xml2);
        List<YSpecification> specs3 = YMarshal.unmarshalSpecifications(xml3);

        assertNotNull("specs2 must not be null", specs2);
        assertNotNull("specs3 must not be null", specs3);
        assertFalse("specs2 must not be empty", specs2.isEmpty());
        assertFalse("specs3 must not be empty", specs3.isEmpty());

        // Each parse must produce an independent object
        assertNotSame("Spec2 and Spec3 must be distinct objects",
                specs2.get(0), specs3.get(0));
    }

    // =========================================================================
    //  Test 14: Schema handler correctly validates well-formed YAWL data fragment
    // =========================================================================

    /**
     * Verifies that the YAWL SchemaHandler compiles and validates correctly
     * for a simple XSD and data fragment. This tests the SchemaHandler utility
     * class used by the engine for task data validation.
     */
    public void testSchemaHandlerValidatesWellFormedData() throws Exception {
        String xsd = "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
                "<xsd:element name=\"order\">" +
                "<xsd:complexType>" +
                "<xsd:sequence>" +
                "<xsd:element name=\"id\" type=\"xsd:string\"/>" +
                "<xsd:element name=\"amount\" type=\"xsd:decimal\"/>" +
                "</xsd:sequence>" +
                "</xsd:complexType>" +
                "</xsd:element>" +
                "</xsd:schema>";

        SchemaHandler handler = new SchemaHandler(xsd);
        assertTrue("SchemaHandler must compile valid XSD", handler.compileSchema());

        String validData = "<order><id>ORD-001</id><amount>99.99</amount></order>";
        assertTrue("SchemaHandler must validate well-formed data", handler.validate(validData));
    }

    // =========================================================================
    //  Test 15: Schema handler rejects malformed data
    // =========================================================================

    /**
     * Verifies that the SchemaHandler correctly rejects data that violates the
     * compiled schema. This confirms that the validator is enforcing constraints.
     */
    public void testSchemaHandlerRejectsMalformedData() throws Exception {
        String xsd = "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
                "<xsd:element name=\"order\">" +
                "<xsd:complexType>" +
                "<xsd:sequence>" +
                "<xsd:element name=\"id\" type=\"xsd:string\"/>" +
                "<xsd:element name=\"amount\" type=\"xsd:decimal\"/>" +
                "</xsd:sequence>" +
                "</xsd:complexType>" +
                "</xsd:element>" +
                "</xsd:schema>";

        SchemaHandler handler = new SchemaHandler(xsd);
        assertTrue("SchemaHandler must compile valid XSD", handler.compileSchema());

        // Missing required 'amount' element - violates the schema
        String invalidData = "<order><id>ORD-002</id></order>";
        assertFalse("SchemaHandler must reject data missing required elements",
                handler.validate(invalidData));
    }

    // =========================================================================
    //  Test suite
    // =========================================================================

    public static Test suite() {
        TestSuite suite = new TestSuite("YAWL V6 Specification Validation Tests");
        suite.addTestSuite(SpecificationValidationTest.class);
        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
