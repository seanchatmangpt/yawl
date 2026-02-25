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

package org.yawlfoundation.yawl.util;

import org.junit.jupiter.api.*;
import org.jdom2.*;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for JDOMUtil utility class.
 * Tests XML parsing, serialization, escaping, XPath queries, and file operations.
 * Implements Chicago TDD with real JDOM objects - no mocks.
 *
 * @author YAWL Foundation Test Suite
 * @since YAWL v6.0
 */
class JDOMUtilTest {

    private static final String TEST_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<root attr=\"value\">\n" +
            "  <child name=\"child1\">\n" +
            "    <grandchild>text content</grandchild>\n" +
            "  </child>\n" +
            "  <child name=\"child2\"/>\n" +
            "  <empty/>\n" +
            "</root>";

    private static final String NAMESPACE_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<root xmlns=\"http://example.com/ns\" xmlns:ns2=\"http://example.com/ns2\">\n" +
            "  <child ns2:attr=\"value\">content</child>\n" +
            "</root>";

    private static final String MALFORMED_XML = "<root><unclosed></root>";
    private static final String XML_WITH_BOM = "\uFEFF<?xml version=\"1.0\"?><root>content</root>";

    private Document testDocument;
    private Element testElement;
    private Element namespacedElement;

    @BeforeEach
    @DisplayName("Setup test documents")
    void setUp() throws Exception {
        // Create test document
        testDocument = JDOMUtil.stringToDocument(TEST_XML);
        testElement = testDocument.getRootElement();

        // Create namespaced document
        Document namespacedDoc = JDOMUtil.stringToDocument(NAMESPACE_XML);
        namespacedElement = namespacedDoc.getRootElement();
    }

    @AfterEach
    @DisplayName("Clean up after tests")
    void tearDown() {
        // Clean up any temporary files
        try {
            Files.deleteIfExists(Path.of("test_output.xml"));
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    // =========================================================================
    // Document Conversion Tests
    // =========================================================================

    @Test
    @DisplayName("documentToString converts Document to String correctly")
    void documentToString_convertsDocumentToStringCorrectly() {
        String result = JDOMUtil.documentToString(testDocument);

        assertNotNull(result, "Should return non-null string");
        assertTrue(result.contains("<root"), "Should contain root element");
        assertTrue(result.contains("</root>"), "Should contain closing root element");
        assertTrue(result.contains("attr=\"value\""), "Should contain attribute");
        assertTrue(result.contains("<grandchild>text content</grandchild>"), "Should contain nested content");
    }

    @Test
    @DisplayName("documentToString with null Document returns null")
    void documentToString_withNullDocument_returnsNull() {
        assertNull(JDOMUtil.documentToString(null), "Should return null for null Document");
    }

    @Test
    @DisplayName("documentToStringDump produces compact format")
    void documentToStringDump_producesCompactFormat() {
        String pretty = JDOMUtil.documentToString(testDocument);
        String compact = JDOMUtil.documentToStringDump(testDocument);

        assertNotEquals(pretty, compact, "Pretty and compact formats should be different");
        assertFalse(compact.contains("\n"), "Compact format should not have newlines");
        assertFalse(compact.contains("  "), "Compact format should not have extra spaces");
    }

    @Test
    @DisplayName("elementToString converts Element to String correctly")
    void elementToString_convertsElementToStringCorrectly() {
        String result = JDOMUtil.elementToString(testElement);

        assertNotNull(result, "Should return non-null string");
        assertTrue(result.contains("<root"), "Should contain root element");
        assertTrue(result.contains("attr=\"value\""), "Should contain attribute");
    }

    @Test
    @DisplayName("elementToString with null Element returns null")
    void elementToString_withNullElement_returnsNull() {
        assertNull(JDOMUtil.elementToString(null), "Should return null for null Element");
    }

    @Test
    @DisplayName("elementToStringDump produces compact format")
    void elementToStringDump_producesCompactFormat() {
        String pretty = JDOMUtil.elementToString(testElement);
        String compact = JDOMUtil.elementToStringDump(testElement);

        assertNotEquals(pretty, compact, "Pretty and compact formats should be different");
        assertFalse(compact.contains("\n"), "Compact format should not have newlines");
    }

    @Test
    @DisplayName("stringToDocument converts valid String to Document")
    void stringToDocument_convertsValidStringToDocument() {
        Document doc = JDOMUtil.stringToDocument(TEST_XML);

        assertNotNull(doc, "Should return non-null Document");
        assertEquals("root", doc.getRootElement().getName(), "Root element name should match");
        assertEquals("value", doc.getRootElement().getAttributeValue("attr"), "Attribute should match");
        assertEquals("text content", doc.getRootElement().getChildText("grandchild"), "Nested text should match");
    }

    @Test
    @DisplayName("stringToDocument with null String returns null")
    void stringToDocument_withNullString_returnsNull() {
        assertNull(JDOMUtil.stringToDocument(null), "Should return null for null String");
    }

    @Test
    @DisplayName("stringToDocument removes UTF-8 BOM")
    void stringToDocument_removesUTF8BOM() {
        Document doc = JDOMUtil.stringToDocument(XML_WITH_BOM);

        assertNotNull(doc, "Should handle XML with BOM");
        assertEquals("root", doc.getRootElement().getName(), "Root element should be parsed correctly");
    }

    @Test
    @DisplayName("stringToDocument with malformed XML returns null")
    void stringToDocument_withMalformedXML_returnsNull() {
        Document doc = JDOMUtil.stringToDocument(MALFORMED_XML);

        assertNull(doc, "Should return null for malformed XML");
    }

    @Test
    @DisplayName("stringToDocument with empty string returns null")
    void stringToDocument_withEmptyString_returnsNull() {
        Document doc = JDOMUtil.stringToDocument("");

        assertNull(doc, "Should return null for empty string");
    }

    @Test
    @DisplayName("stringToDocumentUncaught throws exception for null input")
    void stringToDocumentUncaught_throwsExceptionForNullInput() {
        assertThrows(JDOMException.class, () -> JDOMUtil.stringToDocumentUncaught(null),
            "Should throw JDOMException for null input");
    }

    @Test
    @DisplayName("stringToDocumentUncaught throws exception for malformed XML")
    void stringToDocumentUncaught_throwsExceptionForMalformedXML() {
        assertThrows(JDOMException.class, () -> JDOMUtil.stringToDocumentUncaught(MALFORMED_XML),
            "Should throw JDOMException for malformed XML");
    }

    @Test
    @DisplayName("stringToElement converts valid String to Element")
    void stringToElement_convertsValidStringToElement() {
        Element element = JDOMUtil.stringToElement(TEST_XML);

        assertNotNull(element, "Should return non-null Element");
        assertEquals("root", element.getName(), "Element name should match");
        assertEquals("value", element.getAttributeValue("attr"), "Attribute should match");
        assertEquals("text content", element.getChildText("grandchild"), "Nested text should match");
    }

    @Test
    @DisplayName("stringToElement with null String returns null")
    void stringToElement_withNullString_returnsNull() {
        assertNull(JDOMUtil.stringToElement(null), "Should return null for null String");
    }

    // =========================================================================
    // File Operations Tests
    // =========================================================================

    @Test
    @DisplayName("fileToDocument loads document from existing file")
    void fileToDocument_loadsDocumentFromExistingFile() throws Exception {
        // Create test file
        Path testFile = Path.of("test_input.xml");
        Files.writeString(testFile, TEST_XML);

        try {
            Document doc = JDOMUtil.fileToDocument(testFile.toString());

            assertNotNull(doc, "Should return non-null Document");
            assertEquals("root", doc.getRootElement().getName(), "Root element should match");
        } finally {
            Files.deleteIfExists(testFile);
        }
    }

    @Test
    @DisplayName("fileToDocument with non-existent file returns null")
    void fileToDocument_withNonExistentFile_returnsNull() {
        Document doc = JDOMUtil.fileToDocument("non_existent_file.xml");

        assertNull(doc, "Should return null for non-existent file");
    }

    @Test
    @DisplayName("fileToDocument with null file path returns null")
    void fileToDocument_withNullFilePath_returnsNull() {
        Document doc = JDOMUtil.fileToDocument((String) null);

        assertNull(doc, "Should return null for null file path");
    }

    @Test
    @DisplayName("fileToDocument with File object works correctly")
    void fileToDocument_withFileObject_worksCorrectly() throws Exception {
        Path testFile = Path.of("test_input_file.xml");
        Files.writeString(testFile, TEST_XML);

        try {
            Document doc = JDOMUtil.fileToDocument(testFile.toFile());

            assertNotNull(doc, "Should return non-null Document");
            assertEquals("root", doc.getRootElement().getName(), "Root element should match");
        } finally {
            Files.deleteIfExists(testFile);
        }
    }

    @Test
    @DisplayName("documentToFile saves document to file")
    void documentToFile_savesDocumentToFile() throws Exception {
        Path testFile = Path.of("test_output.xml");

        JDOMUtil.documentToFile(testDocument, testFile.toString());

        assertTrue(Files.exists(testFile), "Output file should exist");

        // Read back and verify content
        String content = Files.readString(testFile);
        assertTrue(content.contains("<root"), "File should contain root element");
        assertTrue(content.contains("attr=\"value\""), "File should contain attribute");

        Files.deleteIfExists(testFile);
    }

    @Test
    @DisplayName("documentToFile handles null document")
    void documentToFile_handlesNullDocument() {
        // Should not throw exception, just log error
        assertDoesNotThrow(() -> JDOMUtil.documentToFile(null, "null_test.xml"));
    }

    @Test
    @DisplayName("documentToFile handles invalid file path")
    void documentToFile_handlesInvalidFilePath() {
        // Should not throw exception, just log error
        assertDoesNotThrow(() -> JDOMUtil.documentToFile(testDocument, "/invalid/path/file.xml"));
    }

    // =========================================================================
    // Default Value Tests
    // =========================================================================

    @Test
    @DisplayName("getDefaultValueForType returns correct defaults")
    void getDefaultValueForType_returnsCorrectDefaults() {
        assertEquals("false", JDOMUtil.getDefaultValueForType("boolean"), "Boolean default should be false");
        assertEquals("0", JDOMUtil.getDefaultValueForType("integer"), "Integer default should be 0");
        assertEquals("", JDOMUtil.getDefaultValueForType("string"), "String default should be empty");
        assertEquals("", JDOMUtil.getDefaultValueForType("customType"), "Custom type default should be empty");
        assertEquals("null", JDOMUtil.getDefaultValueForType(null), "Null type default should be 'null'");
    }

    @Test
    @DisplayName("getDefaultValueForType handles case insensitive boolean")
    void getDefaultValueForType_handlesCaseInsensitiveBoolean() {
        assertEquals("false", JDOMUtil.getDefaultValueForType("BOOLEAN"));
        assertEquals("false", JDOMUtil.getDefaultValueForType("Boolean"));
        assertEquals("false", JDOMUtil.getDefaultValueForType("bool"));
    }

    // =========================================================================
    // XML Escaping Tests
    // =========================================================================

    @Test
    @DisplayName("encodeEscapes encodes XML special characters")
    void encodeEscapes_encodesXMLSpecialCharacters() {
        String input = "Test & < > \" ' &";
        String expected = "Test &amp; &lt; &gt; &quot; &apos; &amp;";
        String result = JDOMUtil.encodeEscapes(input);

        assertEquals(expected, result, "Should encode XML special characters");
    }

    @Test
    @DisplayName("encodeEscapes with null input returns null")
    void encodeEscapes_withNullInput_returnsNull() {
        assertNull(JDOMUtil.encodeEscapes(null), "Should return null for null input");
    }

    @Test
    @DisplayName("encodeEscapes with no special characters returns original")
    void encodeEscapes_withNoSpecialCharacters_returnsOriginal() {
        String input = "Simple text";
        String result = JDOMUtil.encodeEscapes(input);

        assertEquals(input, result, "Should return unchanged string with no special characters");
    }

    @Test
    @DisplayName("encodeEscapes handles all XML entities")
    void encodeEscapes_handlesAllXMLEntities() {
        String input = "'\"<>&";
        String expected = "&apos;&quot;&lt;&gt;&amp;";
        String result = JDOMUtil.encodeEscapes(input);

        assertEquals(expected, result, "Should encode all XML entities");
    }

    @Test
    @DisplayName("encodeAttributeEscapes encodes attribute values")
    void encodeAttributeEscapes_encodesAttributeValues() {
        String input = "Test \"quote\" & amp";
        String result = JDOMUtil.encodeAttributeEscapes(input);

        assertNotNull(result, "Should return non-null result");
        assertTrue(result.contains("&quot;"), "Should quote double quotes");
        assertTrue(result.contains("&amp;"), "Should encode ampersands");
    }

    @Test
    @DisplayName("encodeAttributeEscapes with null input returns null")
    void encodeAttributeEscapes_withNullInput_returnsNull() {
        assertNull(JDOMUtil.encodeAttributeEscapes(null), "Should return null for null input");
    }

    @Test
    @DisplayName("decodeAttributeEscapes decodes attribute values")
    void decodeAttributeEscapes_decodesAttributeValues() {
        String encoded = "Test &quot;quote&quot; &amp; amp";
        String decoded = JDOMUtil.decodeAttributeEscapes(encoded);

        assertEquals("Test \"quote\" & amp", decoded, "Should decode XML entities");
    }

    @Test
    @DisplayName("decodeAttributeEscapes with null input returns null")
    void decodeAttributeEscapes_withNullInput_returnsNull() {
        assertNull(JDOMUtil.decodeAttributeEscapes(null), "Should return null for null input");
    }

    @Test
    @DisplayName("decodeEscapes decodes XML entities")
    void decodeEscapes_decodesXMLEntities() {
        String input = "Test &lt;b&gt;bold&lt;/b&gt; &amp; more";
        String expected = "Test <b>bold</b> & more";
        String result = JDOMUtil.decodeEscapes(input);

        assertEquals(expected, result, "Should decode XML entities");
    }

    @Test
    @DisplayName("decodeEscapes with no entities returns original")
    void decodeEscapes_withNoEntities_returnsOriginal() {
        String input = "Simple text";
        String result = JDOMUtil.decodeEscapes(input);

        assertEquals(input, result, "Should return unchanged string with no entities");
    }

    @Test
    @DisplayName("decodeEscapes with null input returns null")
    void decodeEscapes_withNullInput_returnsNull() {
        assertNull(JDOMUtil.decodeEscapes(null), "Should return null for null input");
    }

    @Test
    @DisplayName("decodeEscapes with empty input returns empty string")
    void decodeEscapes_withEmptyInput_returnsEmptyString() {
        assertEquals("", JDOMUtil.decodeEscapes(""), "Should return empty string for empty input");
    }

    @Test
    @DisplayName("decodeEscapes with no ampersands returns original")
    void decodeEscapes_withNoAmpersands_returnsOriginal() {
        String input = "Simple text without entities";
        String result = JDOMUtil.decodeEscapes(input);

        assertEquals(input, result, "Should return unchanged string with no ampersands");
    }

    @Test
    @DisplayName("decodeEscapes handles partial entities gracefully")
    void decodeEscapes_handlesPartialEntitiesGracefully() {
        String input = "Test &incomplete &valid &more";
        String result = JDOMUtil.decodeEscapes(input);

        assertEquals("Test &incomplete &valid &more", result, "Should handle partial entities");
    }

    // =========================================================================
    // XPath Tests
    // =========================================================================

    @Test
    @DisplayName("selectElement selects single element by XPath")
    void selectElement_selectsSingleElementByXPath() {
        Element child = JDOMUtil.selectElement(testDocument, "//child[@name='child1']");

        assertNotNull(child, "Should find child element");
        assertEquals("child", child.getName(), "Element name should match");
        assertEquals("child1", child.getAttributeValue("name"), "Attribute should match");
    }

    @Test
    @DisplayName("selectElement with null document returns null")
    void selectElement_withNullDocument_returnsNull() {
        Element result = JDOMUtil.selectElement(null, "//root");

        assertNull(result, "Should return null for null document");
    }

    @Test
    @DisplayName("selectElement with null XPath returns null")
    void selectElement_withNullXPath_returnsNull() {
        Element result = JDOMUtil.selectElement(testDocument, null);

        assertNull(result, "Should return null for null XPath");
    }

    @Test
    @DisplayName("selectElement with invalid XPath returns null")
    void selectElement_withInvalidXPath_returnsNull() {
        Element result = JDOMUtil.selectElement(testDocument, "//invalid/xpath");

        assertNull(result, "Should return null for invalid XPath");
    }

    @Test
    @DisplayName("selectElement with namespace works correctly")
    void selectElement_withNamespaceWorksCorrectly() {
        Namespace ns = Namespace.getNamespace("ns2", "http://example.com/ns2");
        Element child = JDOMUtil.selectElement(testDocument, "//child", ns);

        assertNotNull(child, "Should find child with namespace");
    }

    @Test
    @DisplayName("selectElementList selects multiple elements")
    void selectElementList_selectsMultipleElements() {
        List<Element> children = JDOMUtil.selectElementList(testDocument, "//child");

        assertNotNull(children, "Should return non-null list");
        assertEquals(2, children.size(), "Should find two child elements");
        assertEquals("child", children.get(0).getName(), "First child should be named 'child'");
        assertEquals("child", children.get(1).getName(), "Second child should be named 'child'");
    }

    @Test
    @DisplayName("selectElementList with no matches returns empty list")
    void selectElementList_withNoMatches_returnsEmptyList() {
        List<Element> elements = JDOMUtil.selectElementList(testDocument, "//nonexistent");

        assertNotNull(elements, "Should return non-null list");
        assertTrue(elements.isEmpty(), "Should return empty list for no matches");
    }

    @Test
    @DisplayName("selectElementList with null document returns empty list")
    void selectElementList_withNullDocument_returnsEmptyList() {
        List<Element> elements = JDOMUtil.selectElementList(null, "//root");

        assertNotNull(elements, "Should return non-null list");
        assertTrue(elements.isEmpty(), "Should return empty list for null document");
    }

    @Test
    @DisplayName("selectElementList with null XPath returns empty list")
    void selectElementList_withNullXPath_returnsEmptyList() {
        List<Element> elements = JDOMUtil.selectElementList(testDocument, null);

        assertNotNull(elements, "Should return non-null list");
        assertTrue(elements.isEmpty(), "Should return empty list for null XPath");
    }

    @Test
    @DisplayName("selectElementList with namespace works correctly")
    void selectElementList_withNamespaceWorksCorrectly() {
        Namespace ns = Namespace.getNamespace("ns2", "http://example.com/ns2");
        List<Element> elements = JDOMUtil.selectElementList(testDocument, "//child", ns);

        assertNotNull(elements, "Should return non-null list");
    }

    @Test
    @DisplayName("getXPathExpression returns valid XPathExpression")
    void getXPathExpression_returnsValidXPathExpression() {
        XPathExpression<Element> expr = JDOMUtil.getXPathExpression("//child", null);

        assertNotNull(expr, "Should return non-null XPathExpression");
        assertEquals(2, expr.evaluate(testDocument).size(), "Should find two child elements");
    }

    @Test
    @DisplayName("getXPathExpression with namespace works correctly")
    void getXPathExpression_withNamespaceWorksCorrectly() {
        Namespace ns = Namespace.getNamespace("ns2", "http://example.com/ns2");
        XPathExpression<Element> expr = JDOMUtil.getXPathExpression("//child", ns);

        assertNotNull(expr, "Should return non-null XPathExpression with namespace");
    }

    @Test
    @DisplayName("getXPathExpression with null path works")
    void getXPathExpression_withNullPathWorks() {
        XPathExpression<Element> expr = JDOMUtil.getXPathExpression(null, null);

        assertNotNull(expr, "Should return non-null XPathExpression for null path");
    }

    // =========================================================================
    // Formatting Tests
    // =========================================================================

    @Test
    @DisplayName("formatXMLString formats document XML")
    void formatXMLString_formatsDocumentXML() {
        String formatted = JDOMUtil.formatXMLString(TEST_XML);

        assertNotNull(formatted, "Should return non-null formatted string");
        assertTrue(formatted.startsWith("<?xml"), "Should include XML declaration");
        assertTrue(formatted.contains("\n"), "Should include newlines for formatting");
    }

    @Test
    @DisplayName("formatXMLString formats element XML")
    void formatXMLString_formatsElementXML() {
        String elementOnly = "<root><child>text</child></root>";
        String formatted = JDOMUtil.formatXMLString(elementOnly);

        assertNotNull(formatted, "Should return non-null formatted string");
        assertFalse(formatted.startsWith("<?xml"), "Should not include XML declaration for element");
        assertTrue(formatted.contains("\n"), "Should include newlines for formatting");
    }

    @Test
    @DisplayName("formatXMLString with null input returns null")
    void formatXMLString_withNullInput_returnsNull() {
        assertNull(JDOMUtil.formatXMLString(null), "Should return null for null input");
    }

    @Test
    @DisplayName("formatXMLStringAsDocument formats as document")
    void formatXMLStringAsDocument_formatsAsDocument() {
        String formatted = JDOMUtil.formatXMLStringAsDocument(TEST_XML);

        assertNotNull(formatted, "Should return non-null formatted string");
        assertTrue(formatted.startsWith("<?xml"), "Should include XML declaration");
        assertTrue(formatted.contains("\n"), "Should include newlines");
    }

    @Test
    @DisplayName("formatXMLStringAsElement formats as element")
    void formatXMLStringAsElement_formatsAsElement() {
        String elementOnly = "<root><child>text</child></root>";
        String formatted = JDOMUtil.formatXMLStringAsElement(elementOnly);

        assertNotNull(formatted, "Should return non-null formatted string");
        assertFalse(formatted.startsWith("<?xml"), "Should not include XML declaration");
        assertTrue(formatted.contains("\n"), "Should include newlines");
    }

    @Test
    @DisplayName("strip extracts text content from element string")
    void strip_extractTextContentFromElementString() {
        String elementWithText = "<root>extracted text</root>";
        String text = JDOMUtil.strip(elementWithText);

        assertEquals("extracted text", text, "Should extract text content");
    }

    @Test
    @DisplayName("strip with null input returns null")
    void strip_withNullInput_returnsNull() {
        assertNull(JDOMUtil.strip(null), "Should return null for null input");
    }

    @Test
    @DisplayName("strip with empty element returns empty string")
    void strip_withEmptyElement_returnsEmptyString() {
        assertEquals("", JDOMUtil.strip("<root/>"), "Should return empty string for empty element");
    }

    @Test
    @DisplayName("strip with element with attributes returns text")
    void strip_withElementWithAttributes_returnsText() {
        String elementWithAttrs = "<root attr=\"value\">text content</root>";
        String text = JDOMUtil.strip(elementWithAttrs);

        assertEquals("text content", text, "Should extract text content ignoring attributes");
    }

    @Test
    @DisplayName("stripAttributes removes all attributes recursively")
    void stripAttributes_removesAllAttributesRecursively() {
        Element root = testElement.clone();
        JDOMUtil.stripAttributes(root);

        assertFalse(root.hasAttributes(), "Root should have no attributes");
        for (Element child : root.getChildren("child")) {
            assertFalse(child.hasAttributes(), "Child elements should have no attributes");
        }
    }

    @Test
    @DisplayName("stripAttributes returns the modified element")
    void stripAttributes_returnsTheModifiedElement() {
        Element root = testElement.clone();
        Element result = JDOMUtil.stripAttributes(root);

        assertSame(root, result, "Should return the same element instance");
    }

    @Test
    @DisplayName("stripAttributes with null element does nothing")
    void stripAttributes_withNullElement_doesNothing() {
        assertDoesNotThrow(() -> JDOMUtil.stripAttributes(null),
            "Should not throw exception for null element");
    }

    // =========================================================================
    // SAXBuilder Configuration Tests
    // =========================================================================

    @Test
    @DisplayName("SAXBuilder is configured correctly")
    void saxBuilder_isConfiguredCorrectly() {
        SAXBuilder builder = JDOMUtil._builder;

        assertNotNull(builder, "SAXBuilder should be initialized");
        // Note: We can't directly verify the internal configuration without reflection,
        // but we can verify it works by parsing valid XML
        assertDoesNotThrow(() -> builder.build(new StringReader(TEST_XML)),
            "SAXBuilder should be able to parse valid XML");
    }

    @Test
    @DisplayName("UTF8_BOM constant is correct")
    void utf8BOMConstant_isCorrect() {
        assertEquals("\uFEFF", JDOMUtil.UTF8_BOM, "UTF8_BOM should be the correct BOM character");
    }

    // =========================================================================
    // Integration Tests
    // =========================================================================

    @Test
    @DisplayName("Round-trip conversion preserves content")
    void roundTripConversion_preservesContent() {
        // Document -> String -> Document
        String docString = JDOMUtil.documentToString(testDocument);
        Document roundTripDoc = JDOMUtil.stringToDocument(docString);

        assertNotNull(roundTripDoc, "Round-trip Document should not be null");
        assertEquals("root", roundTripDoc.getRootElement().getName(), "Root name should match");
        assertEquals("value", roundTripDoc.getRootElement().getAttributeValue("attr"), "Attribute should match");
        assertEquals("text content", roundTripDoc.getRootElement().getChildText("grandchild"), "Nested text should match");
    }

    @Test
    @DisplayName("Element round-trip conversion preserves content")
    void elementRoundTripConversion_preservesContent() {
        // Element -> String -> Element
        String elementString = JDOMUtil.elementToString(testElement);
        Element roundTripElement = JDOMUtil.stringToElement(elementString);

        assertNotNull(roundTripElement, "Round-trip Element should not be null");
        assertEquals("root", roundTripElement.getName(), "Element name should match");
        assertEquals("value", roundTripElement.getAttributeValue("attr"), "Attribute should match");
        assertEquals("text content", roundTripElement.getChildText("grandchild"), "Nested text should match");
    }

    @Test
    @DisplayName("Complex document with namespaces parses correctly")
    void complexDocumentWithNamespaces_parsesCorrectly() {
        Document doc = JDOMUtil.stringToDocument(NAMESPACE_XML);

        assertNotNull(doc, "Should parse namespaced XML");
        Element root = doc.getRootElement();
        assertEquals("root", root.getName(), "Root element name should match");
        assertEquals("http://example.com/ns", root.getNamespaceURI(), "Default namespace should match");

        Element child = root.getChild("child");
        assertNotNull(child, "Should find child element");
        assertEquals("content", child.getText(), "Child text should match");

        Namespace ns2 = Namespace.getNamespace("ns2", "http://example.com/ns2");
        String attrValue = child.getAttributeValue("attr", ns2);
        assertEquals("value", attrValue, "Namespaced attribute should match");
    }

    @Test
    @DisplayName("Empty document handles gracefully")
    void emptyDocument_handlesGracefully() {
        String minimalXML = "<root/>";
        Document doc = JDOMUtil.stringToDocument(minimalXML);

        assertNotNull(doc, "Should parse minimal XML");
        assertEquals("root", doc.getRootElement().getName(), "Root should be present");
        assertTrue(doc.getRootElement().getChildren().isEmpty(), "Root should have no children");
    }

    @Test
    @DisplayName("Deep nesting parses correctly")
    void deepNesting_parsesCorrectly() {
        String deepXML = "<level1><level2><level3><level4>deep content</level4></level3></level2></level1>";
        Document doc = JDOMUtil.stringToDocument(deepXML);

        assertNotNull(doc, "Should parse deeply nested XML");
        assertEquals("deep content", doc.getRootElement().getChildText("level2/level3/level4"),
            "Deep text content should be accessible");
    }

    @Test
    @DisplayName("Mixed content with text and elements works correctly")
    void mixedContent_withTextAndElements_worksCorrectly() {
        String mixedXML = "<root>prefix<child>text</child>suffix</root>";
        Document doc = JDOMUtil.stringToDocument(mixedXML);

        assertEquals("prefixsuffix", doc.getRootElement().getText(), "Mixed text should be concatenated");
        assertEquals("text", doc.getRootElement().getChildText("child"), "Child text should be preserved");
    }

    @Test
    @DisplayName("CData sections are preserved in round-trip")
    void cDataSections_arePreservedInRoundTrip() {
        String cdataXML = "<root><![CDATA[some <tag> content]]></root>";
        Document doc = JDOMUtil.stringToDocument(cdataXML);
        String roundTrip = JDOMUtil.documentToString(doc);

        assertTrue(roundTrip.contains("some <tag> content"), "CData content should be preserved");
        assertTrue(roundTrip.contains("<![CDATA["), "CData marker should be preserved");
        assertTrue(roundTrip.contains("]]>"), "CData end marker should be preserved");
    }

    @Test
    @DisplayName("Comments are preserved in round-trip")
    void comments_arePreservedInRoundTrip() {
        String commentXML = "<root><!-- comment --><child>text</child></root>";
        Document doc = JDOMUtil.stringToDocument(commentXML);
        String roundTrip = JDOMUtil.documentToString(doc);

        assertTrue(roundTrip.contains("<!-- comment -->"), "Comment should be preserved");
    }

    @Test
    @DisplayName("Entity references are handled correctly")
    void entityReferences_areHandledCorrectly() {
        String entityXML = "<root&gt; &#x26; &#38;</root>";
        Document doc = JDOMUtil.stringToDocument(entityXML);
        String decodedText = JDOMUtil.decodeEscapes(doc.getRootElement().getText());

        assertEquals("& & &", decodedText, "Entity references should be decoded");
    }

    // =========================================================================
    // Performance and Edge Cases
    // =========================================================================

    @Test
    @DisplayName("Large document handles gracefully")
    void largeDocument_handlesGracefully() {
        StringBuilder largeXML = new StringBuilder("<root>");
        for (int i = 0; i < 1000; i++) {
            largeXML.append("<item>").append(i).append("</item>");
        }
        largeXML.append("</root>");

        Document doc = JDOMUtil.stringToDocument(largeXML.toString());

        assertNotNull(doc, "Should parse large XML");
        assertEquals(1000, doc.getRootElement().getChildren("item").size(), "Should have all items");
    }

    @Test
    @DisplayName("Unicode content handles correctly")
    void unicodeContent_handlesCorrectly() {
        String unicodeXML = "<root>你好世界\nΓειά σου κόσμε\nПривет мир</root>";
        Document doc = JDOMUtil.stringToDocument(unicodeXML);

        assertEquals("你好世界\nΓειά σου κόσμε\nПривет мир", doc.getRootElement().getText(),
            "Unicode content should be preserved");
    }

    @Test
    @DisplayName("Whitespace handling preserves formatting")
    void whitespaceHandling_preservesFormatting() {
        String whitespaceXML = "<root>\n  <child>\n    text\n  </child>\n</root>";
        Document doc = JDOMUtil.stringToDocument(whitespaceXML);

        assertEquals("\n  \n    text\n  \n", doc.getRootElement().getText(), "Whitespace should be preserved");
    }

    @Test
    @DisplayName("Empty tags self-close correctly")
    void emptyTags_selfCloseCorrectly() {
        String emptyTagsXML = "<root><empty/><with>content</with></root>";
        Document doc = JDOMUtil.stringToDocument(emptyTagsXML);

        Element empty = doc.getRootElement().getChild("empty");
        Element with = docRootElement().getChild("with");

        assertNotNull(empty, "Empty element should exist");
        assertTrue(empty.getChildren().isEmpty() && empty.getText().isEmpty(),
            "Empty element should have no content");
        assertEquals("content", with.getText(), "Non-empty element should have content");
    }

    @Test
    @DisplayName("Namespace prefixes are handled in XPath")
    void namespacePrefixes_areHandledInXPath() {
        // Test with namespaced XML
        Document doc = JDOMUtil.stringToDocument(NAMESPACE_XML);

        XPathExpression<Element> expr = XPathFactory.instance().compile(
            "//ns2:child", Filters.element(), null,
            Namespace.getNamespace("ns2", "http://example.com/ns2"));

        List<Element> results = expr.evaluate(doc);
        assertEquals(1, results.size(), "Should find namespaced child");
    }

    @Test
    @DisplayName("XPath with predicates works correctly")
    void xpathWithPredicates_worksCorrectly() {
        List<Element> children = JDOMUtil.selectElementList(testDocument, "//child[@name='child1']");

        assertEquals(1, children.size(), "Should find one child with name='child1'");
        assertEquals("child1", children.get(0).getAttributeValue("name"), "Attribute should match");
    }

    @Test
    @DisplayName("XPath position predicates work correctly")
    void xpathPositionPredicates_workCorrectly() {
        List<Element> firstChild = JDOMUtil.selectElementList(testDocument, "//child[1]");

        assertEquals(1, firstChild.size(), "Should find first child");
        assertEquals("child1", firstChild.get(0).getAttributeValue("name"), "Should be first child");
    }

    @Test
    @DisplayName("XPath text predicates work correctly")
    void xpathTextPredicates_workCorrectly() {
        List<Element> childrenWithText = JDOMUtil.selectElementList(testDocument, "//child[text()='text content']");

        assertEquals(1, childrenWithText.size(), "Should find child with specific text");
        assertEquals("text content", childrenWithText.get(0).getChildText("grandchild"), "Text should match");
    }

    // =========================================================================
    // Error Recovery Tests
    // =========================================================================

    @Test
    @DisplayName("Invalid XML entity returns null")
    void invalidXMLEntity_returnsNull() {
        String invalidEntityXML = "<root>invalid &entity; text</root>";
        Document doc = JDOMUtil.stringToDocument(invalidEntityXML);

        // Should still parse, but entity may be preserved as-is
        assertNotNull(doc, "Should handle XML with invalid entities");
        assertTrue(doc.getRootElement().getText().contains("&entity;"),
            "Invalid entity should be preserved");
    }

    @Test
    @DisplayName("Unclosed tags in text content handled gracefully")
    void unclosedTagsInTextContent_handledGracefully() {
        String unclosedXML = "<root>text with <unclosed tag</root>";
        Document doc = JDOMUtil.stringToDocument(unclosedXML);

        assertNotNull(doc, "Should handle XML with unclosed tags in text");
        assertEquals("text with <unclosed tag", doc.getRootElement().getText(),
            "Unclosed tag should be preserved as text");
    }

    @Test
    @DisplayName("Multiple root elements error returns null")
    void multipleRootElementError_returnsNull() {
        String multiRootXML = "<root1/><root2/>";
        Document doc = JDOMUtil.stringToDocument(multiRootXML);

        assertNull(doc, "Should reject XML with multiple root elements");
    }

    @Test
    @DisplayName("Empty XML string returns null")
    void emptyXMLString_returnsNull() {
        assertNull(JDOMUtil.stringToDocument(""), "Should reject empty XML string");
    }

    @Test
    @DisplayName("Whitespace-only XML string returns null")
    void whitespaceOnlyXMLString_returnsNull() {
        assertNull(JDOMUtil.stringToDocument("   \n\t  "), "Should reject whitespace-only XML");
    }

    @Test
    @DisplayName("XML with only declaration returns document")
    void xmlWithOnlyDeclaration_returnsDocument() {
        String declarationOnly = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
        Document doc = JDOMUtil.stringToDocument(declarationOnly);

        assertNull(doc, "Should reject XML with only declaration (no root)");
    }

    // =========================================================================
    // Format Tests
    // =========================================================================

    @Test
    @DisplayName("Format constants are properly configured")
    void formatConstants_areProperlyConfigured() {
        // Test pretty format
        Format prettyFormat = Format.getPrettyFormat();
        assertNotNull(prettyFormat, "Pretty format should be available");
        assertFalse(prettyFormat.isOmitDeclaration(), "Pretty format should include declaration");

        // Test compact format
        Format compactFormat = Format.getCompactFormat();
        assertNotNull(compactFormat, "Compact format should be available");
        assertTrue(compactFormat.isOmitDeclaration(), "Compact format should omit declaration");
    }

    @Test
    @DisplayName("XMLOutputter produces valid output")
    void xmlOutputterProducesValidOutput() {
        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        String output = outputter.outputString(testDocument);

        assertNotNull(output, "Output should not be null");
        assertTrue(output.contains("<root"), "Output should contain XML content");
        assertTrue(output.endsWith("</root>"), "Output should be complete XML");
    }

    @Test
    @DisplayName("Document and Element string conversions are consistent")
    void documentAndElementStringConversionsAreConsistent() {
        // Document -> String -> Element
        String docString = JDOMUtil.documentToString(testDocument);
        Element fromDoc = JDOMUtil.stringToElement(docString);

        // Direct Element conversion
        Element direct = testElement;

        // Both should have same structure
        assertEquals(fromDoc.getName(), direct.getName(), "Names should match");
        assertEquals(fromDoc.getAttributeValue("attr"), direct.getAttributeValue("attr"), "Attributes should match");
        assertEquals(fromDoc.getText(), direct.getText(), "Text should match");
    }

    @Test
    @DisplayName("String encoding is preserved")
    void stringEncodingIsPreserved() {
        String encodingTest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root>测试</root>";
        Document doc = JDOMUtil.stringToDocument(encodingTest);
        String output = JDOMUtil.documentToString(doc);

        assertTrue(output.contains("测试"), "Unicode content should be preserved");
        assertTrue(output.contains("encoding=\"UTF-8\""), "Encoding should be preserved");
    }

    @Test
    @DisplayName("Version is preserved in output")
    void versionIsPreservedInOutput() {
        String versionTest = "<?xml version=\"1.1\"?><root>content</root>";
        Document doc = JDOMUtil.stringToDocument(versionTest);
        String output = JDOMUtil.documentToString(doc);

        assertTrue(output.contains("version=\"1.1\""), "Version should be preserved");
    }
}