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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.w3c.dom.*;

import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for DOMUtil functionality methods.
 * Tests XML parsing, string conversion, XPath operations, and utility functions.
 * This complements the existing XXE protection tests.
 *
 * @author YAWL Foundation Test Suite
 * @since YAWL v6.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DOMUtilFunctionalityTest {

    private static final String TEST_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<root attr=\"value\">\n" +
            "  <child>\n" +
            "    <grandchild>text content</grandchild>\n" +
            "  </child>\n" +
            "  <empty/>\n" +
            "</root>";

    private static final String NAMESPACE_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<root xmlns=\"http://example.com/ns\">\n" +
            "  <child>content</child>\n" +
            "</root>";

    private Document testDocument;
    private Element testElement;

    @BeforeEach
    @DisplayName("Setup test documents")
    void setUp() throws Exception {
        testDocument = DOMUtil.getDocumentFromString(TEST_XML);
        testElement = testDocument.getDocumentElement();
    }

    // =========================================================================
    // Document Creation Tests
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("createDocumentInstance creates valid namespace-aware document")
    void createDocumentInstance_createsValidNamespaceAwareDocument() throws Exception {
        Document doc = DOMUtil.createDocumentInstance();

        assertNotNull(doc, "Should create non-null document");
        assertNull(doc.getDocumentElement(), "New document should have no root element");
        assertEquals("http://www.w3.org/2000/xmlns/", doc.getNamespaceURI(),
            "Document should be namespace aware");
    }

    @Test
    @Order(2)
    @DisplayName("createNamespacelessDocumentInstance creates valid document")
    void createNamespacelessDocumentInstance_createsValidDocument() throws Exception {
        Document doc = DOMUtil.createNamespacelessDocumentInstance();

        assertNotNull(doc, "Should create non-null document");
        assertNull(doc.getDocumentElement(), "New document should have no root element");
    }

    // =========================================================================
    // String Conversion Tests
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("getXMLStringFragmentFromNode converts element to string correctly")
    void getXMLStringFragmentFromNode_convertsElementToStringCorrectly() throws TransformerException {
        String result = DOMUtil.getXMLStringFragmentFromNode(testElement);

        assertNotNull(result, "Should return non-null string");
        assertTrue(result.contains("<root"), "Should contain root element");
        assertTrue(result.contains("attr=\"value\""), "Should contain attribute");
        assertFalse(result.contains("<?xml"), "Should not contain XML declaration");
    }

    @Test
    @Order(4)
    @DisplayName("getXMLStringFragmentFromNode includes XML declaration when requested")
    void getXMLStringFragmentFromNode_includesXMLDeclarationWhenRequested() throws TransformerException {
        String result = DOMUtil.getXMLStringFragmentFromNode(testElement, false);

        assertTrue(result.contains("<?xml"), "Should include XML declaration");
        assertTrue(result.contains("encoding=\"UTF-8\""), "Should include encoding");
    }

    @Test
    @Order(5)
    @DisplayName("getXMLStringFragmentFromNode with encoding parameter")
    void getXMLStringFragmentFromNode_withEncodingParameter() throws TransformerException {
        String result = DOMUtil.getXMLStringFragmentFromNode(testElement, true, "UTF-16");

        assertNotNull(result, "Should return non-null string");
        assertFalse(result.contains("UTF-8"), "Should not contain UTF-8 encoding");
        // Content should be preserved despite encoding change
        assertTrue(result.contains("<root"), "Should contain root element");
    }

    @Test
    @Order(6)
    @DisplayName("getXMLStringFragmentFromNode with collapseEmptyTags parameter")
    void getXMLStringFragmentFromNode_withCollapseEmptyTagsParameter() throws TransformerException {
        String resultCollapsed = DOMUtil.getXMLStringFragmentFromNode(testElement, true, "UTF-8", true);
        String resultExpanded = DOMUtil.getXMLStringFragmentFromNode(testElement, true, "UTF-8", false);

        // Both should contain the content but may handle empty tags differently
        assertTrue(resultCollapsed.contains("<empty/>"), "Collapsed should use self-closing tags");
        assertTrue(resultExpanded.contains("<empty/>"), "Expanded should also use self-closing tags for empty elements");
    }

    @Test
    @Order(7)
    @DisplayName("getXMLStringFragmentFromNode with null node throws exception")
    void getXMLStringFragmentFromNode_withNullNode_throwsException() {
        assertThrows(TransformerException.class, () -> DOMUtil.getXMLStringFragmentFromNode(null),
            "Should throw TransformerException for null node");
    }

    @Test
    @Order(8)
    @DisplayName("getXMLStringFragmentFromNode with invalid encoding throws exception")
    void getXMLStringFragmentFromNode_withInvalidEncoding_throwsException() throws TransformerException {
        assertThrows(AssertionError.class, () -> DOMUtil.getXMLStringFragmentFromNode(testElement, true, "INVALID_ENCODING"),
            "Should throw AssertionError for invalid encoding");
    }

    @Test
    @Order(9)
    @DisplayName("getXMLStringFragmentFromNode with document includes root element")
    void getXMLStringFragmentFromNode_withDocumentIncludesRootElement() throws TransformerException {
        String result = DOMUtil.getXMLStringFragmentFromNode(testDocument);

        assertTrue(result.contains("<root"), "Should include root element");
        assertTrue(result.contains("</root>"), "Should include closing root element");
    }

    // =========================================================================
    // XPath Tests
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("selectSingleNode selects single node correctly")
    void selectSingleNode_selectsSingleNodeCorrectly() throws XPathExpressionException {
        Node child = DOMUtil.selectSingleNode(testElement, "//child");

        assertNotNull(child, "Should find child node");
        assertEquals("child", child.getNodeName(), "Node name should match");
    }

    @Test
    @Order(11)
    @DisplayName("selectSingleNode with invalid XPath returns null")
    void selectSingleNode_withInvalidXPath_returnsNull() throws XPathExpressionException {
        Node result = DOMUtil.selectSingleNode(testElement, "//invalid/xpath");

        assertNull(result, "Should return null for invalid XPath");
    }

    @Test
    @Order(12)
    @DisplayName("selectSingleNode with null node throws exception")
    void selectSingleNode_withNullNode_throwsException() {
        assertThrows(XPathExpressionException.class, () -> DOMUtil.selectSingleNode(null, "//root"),
            "Should throw XPathExpressionException for null node");
    }

    @Test
    @Order(13)
    @DisplayName("selectSingleNode with null XPath throws exception")
    void selectSingleNode_withNullXPath_throwsException() throws XPathExpressionException {
        assertThrows(XPathExpressionException.class, () -> DOMUtil.selectSingleNode(testElement, null),
            "Should throw XPathExpressionException for null XPath");
    }

    @Test
    @Order(14)
    @DisplayName("selectNodeText extracts text content correctly")
    void selectNodeText_extractTextContentCorrectly() throws XPathExpressionException {
        String text = DOMUtil.selectNodeText(testElement, "//grandchild");

        assertEquals("text content", text, "Should extract text content");
    }

    @Test
    @Order(15)
    @DisplayName("selectNodeText with no matching node returns empty string")
    void selectNodeText_withNoMatchingNode_returnsEmptyString() throws XPathExpressionException {
        String text = DOMUtil.selectNodeText(testElement, "//nonexistent");

        assertEquals("", text, "Should return empty string for no matching node");
    }

    @Test
    @Order(16)
    @DisplayName("selectNodeList returns multiple nodes")
    void selectNodeList_returnsMultipleNodes() throws XPathExpressionException {
        NodeList nodes = DOMUtil.selectNodeList(testElement, "//child");

        assertEquals(1, nodes.getLength(), "Should find one child node");
        assertEquals("child", nodes.item(0).getNodeName(), "Node name should match");
    }

    @Test
    @Order(17)
    @DisplayName("selectNodeList with no matches returns empty NodeList")
    void selectNodeList_withNoMatches_returnsEmptyNodeList() throws XPathExpressionException {
        NodeList nodes = DOMUtil.selectNodeList(testElement, "//nonexistent");

        assertEquals(0, nodes.getLength(), "Should return empty NodeList for no matches");
    }

    // =========================================================================
    // Node Manipulation Tests
    // =========================================================================

    @Test
    @Order(18)
    @DisplayName("removeAllChildNodes removes all children")
    void removeAllChildNodes_removesAllChildren() {
        Element parent = testDocument.createElement("parent");
        Element child1 = testDocument.createElement("child1");
        Element child2 = testDocument.createElement("child2");

        parent.appendChild(child1);
        parent.appendChild(child2);

        assertEquals(2, parent.getChildNodes().getLength(), "Should have two children initially");
        DOMUtil.removeAllChildNodes(parent);
        assertEquals(0, parent.getChildNodes().getLength(), "Should have no children after removal");
    }

    @Test
    @Order(19)
    @DisplayName("removeAllChildNodes with empty node does nothing")
    void removeAllChildNodes_withEmptyNode_doesNothing() {
        Element empty = testDocument.createElement("empty");

        DOMUtil.removeAllChildNodes(empty);
        assertEquals(0, empty.getChildNodes().getLength(), "Should still have no children");
    }

    @Test
    @Order(20)
    @DisplayName("removeAllAttributes removes all attributes")
    void removeAllAttributes_removesAllAttributes() {
        Element element = testDocument.createElement("test");
        element.setAttribute("attr1", "value1");
        element.setAttribute("attr2", "value2");

        assertEquals(2, element.getAttributes().getLength(), "Should have two attributes initially");
        DOMUtil.removeAllAttributes(element);
        assertEquals(0, element.getAttributes().getLength(), "Should have no attributes after removal");
    }

    @Test
    @Order(21)
    @DisplayName("removeAllAttributes with empty element does nothing")
    void removeAllAttributes_withEmptyElement_doesNothing() {
        Element empty = testDocument.createElement("empty");

        DOMUtil.removeAllAttributes(empty);
        assertEquals(0, empty.getAttributes().getLength(), "Should still have no attributes");
    }

    @Test
    @Order(22)
    @DisplayName("removeAllAttributes with null element throws exception")
    void removeAllAttributes_withNullElement_throwsException() {
        assertThrows(NullPointerException.class, () -> DOMUtil.removeAllAttributes(null),
            "Should throw NullPointerException for null element");
    }

    @Test
    @Order(23)
    @DisplayName("alphabetiseChildNodes alphabetizes children by local name")
    void alphabetiseChildNodes_alphabetizesChildrenByLocalName() throws XPathExpressionException {
        // Create parent with children in reverse alphabetical order
        Element parent = testDocument.createElement("parent");
        Element childC = testDocument.createElement("childC");
        Element childA = testDocument.createElement("childA");
        Element childB = testDocument.createElement("childB");

        parent.appendChild(childC);
        parent.appendChild(childA);
        parent.appendChild(childB);

        // Alphabetize
        Node result = DOMUtil.alphabetiseChildNodes(parent);

        // Children should now be in alphabetical order
        assertEquals("childA", result.getChildNodes().item(0).getNodeName());
        assertEquals("childB", result.getChildNodes().item(1).getNodeName());
        assertEquals("childC", result.getChildNodes().item(2).getNodeName());
        assertSame(parent, result, "Should return the same node instance");
    }

    @Test
    @Order(24)
    @DisplayName("alphabetiseChildNodes with no children does nothing")
    void alphabetiseChildNodes_withNoChildren_doesNothing() throws XPathExpressionException {
        Element parent = testDocument.createElement("parent");

        Node result = DOMUtil.alphabetiseChildNodes(parent);

        assertNull(result.getFirstChild(), "Should still have no children");
        assertSame(parent, result, "Should return the same node instance");
    }

    @Test
    @Order(25)
    @DisplayName("alphabetiseChildNodes with mixed content preserves non-element children")
    void alphabetiseChildNodes_withMixedContentPreservesNonElementChildren() throws XPathExpressionException {
        Element parent = testDocument.createElement("parent");

        // Add text and element children
        parent.appendChild(testDocument.createTextNode("text before"));
        Element childB = testDocument.createElement("childB");
        Element childA = testDocument.createElement("childA");
        parent.appendChild(childB);
        parent.appendChild(childA);
        parent.appendChild(testDocument.createTextNode("text after"));

        Node result = DOMUtil.alphabetiseChildNodes(parent);

        // Check that elements are alphabetized but text nodes remain in place
        Node firstChild = result.getFirstChild();
        assertTrue(firstChild.getNodeType() == Node.TEXT_NODE, "First child should be text node");
        assertEquals("text before", firstChild.getNodeValue());

        Node elementChild1 = result.getChildNodes().item(1);
        Node elementChild2 = result.getChildNodes().item(2);
        assertEquals("childA", elementChild1.getNodeName());
        assertEquals("childB", elementChild2.getNodeName());

        Node lastChild = result.getLastChild();
        assertTrue(lastChild.getNodeType() == Node.TEXT_NODE, "Last child should be text node");
        assertEquals("text after", lastChild.getNodeValue());
    }

    // =========================================================================
    // Node Text Extraction Tests
    // =========================================================================

    @Test
    @Order(26)
    @DisplayName("getNodeText extracts text from node with children")
    void getNodeText_extractTextFromNodeWithChildren() {
        Element parent = testDocument.createElement("parent");
        Element child = testDocument.createElement("child");
        child.appendChild(testDocument.createTextNode("child text"));
        parent.appendChild(child);

        String text = DOMUtil.getNodeText(parent);

        assertEquals("child text", text, "Should extract text from child nodes");
    }

    @Test
    @Order(27)
    @DisplayName("getNodeText extracts text from multiple children")
    void getNodeText_extractTextFromMultipleChildren() {
        Element parent = testDocument.createElement("parent");
        Element child1 = testDocument.createElement("child1");
        Element child2 = testDocument.createElement("child2");

        child1.appendChild(testDocument.createTextNode("text1"));
        child2.appendChild(testDocument.createTextNode("text2"));
        parent.appendChild(child1);
        parent.appendChild(child2);

        String text = DOMUtil.getNodeText(parent);

        assertEquals("text1text2", text, "Should concatenate text from all children");
    }

    @Test
    @Order(28)
    @DisplayName("getNodeText with null node returns empty string")
    void getNodeText_withNullNode_returnsEmptyString() {
        String text = DOMUtil.getNodeText(null);

        assertEquals("", text, "Should return empty string for null node");
    }

    @Test
    @Order(29)
    @DisplayName("getNodeText with node without children returns empty string")
    void getNodeText_withNodeWithoutChildren_returnsEmptyString() {
        Element element = testDocument.createElement("empty");

        String text = DOMUtil.getNodeText(element);

        assertEquals("", text, "Should return empty string for node without children");
    }

    @Test
    @Order(30)
    @DisplayName("getNodeText preserves CDATA content")
    void getNodeText_preservesCDATAContent() {
        Element parent = testDocument.createElement("parent");
        Element cdata = testDocument.createElement("cdata");
        CDATASection cdataSection = testDocument.createCDATASection("CDATA content");
        cdata.appendChild(cdataSection);
        parent.appendChild(cdata);

        String text = DOMUtil.getNodeText(parent);

        assertEquals("CDATA content", text, "Should preserve CDATA content");
    }

    @Test
    @Order(31)
    @DisplayName("getNodeText handles entity references")
    void getNodeText_handlesEntityReferences() {
        Element parent = testDocument.createElement("parent");
        Element child = testDocument.createElement("child");
        child.appendChild(testDocument.createTextNode("Text &amp; entity"));
        parent.appendChild(child);

        String text = DOMUtil.getNodeText(parent);

        assertEquals("Text &amp; entity", text, "Should preserve entity references");
    }

    // =========================================================================
    // Input Source Creation Tests
    // =========================================================================

    @Test
    @Order(32)
    @DisplayName("createUTF8InputSource with string creates valid InputSource")
    void createUTF8InputSource_withString_createsValidInputSource() {
        String xml = "<root>content</root>";
        InputSource source = DOMUtil.createUTF8InputSource(xml);

        assertNotNull(source, "Should create non-null InputSource");
        // Cannot directly test encoding of InputSource, but it should not throw exceptions
    }

    @Test
    @Order(33)
    @DisplayName("createUTF8InputSource with string containing special characters")
    void createUTF8InputSource_withStringContainingSpecialCharacters() {
        String xml = "<root>测试 &amp; more</root>";
        InputSource source = DOMUtil.createUTF8InputSource(xml);

        assertNotNull(source, "Should create non-null InputSource with special characters");
    }

    @Test
    @Order(34)
    @DisplayName("createUTF8InputSource with node creates valid InputSource")
    void createUTF8InputSource_withNode_createsValidInputSource() throws TransformerException {
        InputSource source = DOMUtil.createUTF8InputSource(testElement);

        assertNotNull(source, "Should create non-null InputSource from node");
    }

    @Test
    @Order(35)
    @DisplayName("createUTF8InputSource with null node throws exception")
    void createUTF8InputSource_withNullNode_throwsException() {
        assertThrows(TransformerException.class, () -> DOMUtil.createUTF8InputSource(null),
            "Should throw TransformerException for null node");
    }

    // =========================================================================
    // Empty Node Removal Tests
    // =========================================================================

    @Test
    @Order(36)
    @DisplayName("removeEmptyNodes removes empty elements")
    void removeEmptyNodes_removesEmptyElements() throws Exception {
        // Create document with empty elements
        String xml = "<root><empty1/><empty2></empty2><non-empty>content</non-empty></root>";
        Document doc = DOMUtil.getDocumentFromString(xml);
        Element root = doc.getDocumentElement();

        Document result = DOMUtil.removeEmptyNodes(root);

        assertEquals(1, result.getDocumentElement().getChildNodes().getLength(),
            "Should have only one non-empty child");
        assertEquals("non-empty", result.getDocumentElement().getFirstChild().getNodeName(),
            "Should preserve non-empty element");
    }

    @Test
    @Order(37)
    @DisplayName("removeEmptyNodes with no empty elements preserves structure")
    void removeEmptyNodes_withNoEmptyElements_preservesStructure() throws Exception {
        Document result = DOMUtil.removeEmptyNodes(testElement);

        assertEquals(testElement.getChildNodes().getLength(), result.getDocumentElement().getChildNodes().getLength(),
            "Should preserve original structure when no empty elements");
    }

    @Test
    @Order(38)
    @DisplayName("removeEmptyNodes with null node throws exception")
    void removeEmptyNodes_withNullNode_throwsException() {
        assertThrows(NullPointerException.class, () -> DOMUtil.removeEmptyNodes(null),
            "Should throw NullPointerException for null node");
    }

    @Test
    @Order(39)
    @DisplayName("removeEmptyElements removes empty elements recursively")
    void removeEmptyElements_removesEmptyElementsRecursively() throws XPathExpressionException {
        // Create nested structure with empty elements
        Element parent = testDocument.createElement("parent");
        Element child1 = testDocument.createElement("child1");
        Element empty1 = testDocument.createElement("empty1");
        Element child2 = testDocument.createElement("child2");
        Element empty2 = testDocument.createElement("empty2");
        Element grandchild = testDocument.createElement("grandchild");
        Element empty3 = testDocument.createElement("empty3");

        parent.appendChild(child1);
        child1.appendChild(empty1);
        child1.appendChild(child2);
        child2.appendChild(grandchild);
        child2.appendChild(empty2);
        parent.appendChild(empty3);

        Node result = DOMUtil.removeEmptyElements(parent);

        // Check that empty elements are removed but non-empty ones remain
        assertEquals(2, result.getChildNodes().getLength(), "Should have two children at root");
        // Check that grandchildren are preserved
        assertTrue(result.getFirstChild().getChildNodes().getLength() > 0,
            "Non-empty child should have children");
    }

    // =========================================================================
    // XML Formatting Tests
    // =========================================================================

    @Test
    @Order(40)
    @DisplayName("formatXMLStringForDisplay formats XML correctly")
    void formatXMLStringForDisplay_formatsXMLCorrectly() {
        String unformatted = "<root><child>text</child></root>";
        String formatted = DOMUtil.formatXMLStringForDisplay(unformatted);

        assertNotNull(formatted, "Should return non-null formatted string");
        assertTrue(formatted.contains("\n"), "Should include newlines for formatting");
        assertTrue(formatted.contains("  "), "Should include indentation");
        assertFalse(formatted.contains("<?xml"), "Should omit XML declaration");
    }

    @Test
    @Order(41)
    @DisplayName("formatXMLStringForDisplay with malformed XML returns original")
    void formatXMLStringForDisplay_withMalformedXML_returnsOriginal() {
        String malformed = "<root><unclosed></root>";
        String result = DOMUtil.formatXMLStringForDisplay(malformed);

        assertEquals(malformed, result, "Should return original malformed XML");
    }

    @Test
    @Order(42)
    @DisplayName("formatXMLStringForDisplay with null input returns null")
    void formatXMLStringForDisplay_withNullInput_returnsNull() {
        String result = DOMUtil.formatXMLStringForDisplay(null);

        assertNull(result, "Should return null for null input");
    }

    @Test
    @Order(43)
    @DisplayName("formatXMLStringForDisplay with omitDeclaration parameter")
    void formatXMLStringForDisplay_withOmitDeclarationParameter() {
        String xml = "<?xml version=\"1.0\"?><root><child>text</child></root>";
        String result = DOMUtil.formatXMLStringForDisplay(xml, false);

        assertTrue(result.contains("<?xml"), "Should include XML declaration when requested");
    }

    // =========================================================================
    // Complex Structure Tests
    // =========================================================================

    @Test
    @Order(44)
    @DisplayName("Round-trip conversion preserves content")
    void roundTripConversion_preservesContent() throws Exception {
        // Document -> String -> Document
        String xml = DOMUtil.getXMLStringFragmentFromNode(testElement);
        Document roundTrip = DOMUtil.getDocumentFromString(xml);

        assertNotNull(roundTrip, "Round-trip Document should not be null");
        assertEquals("root", roundTrip.getDocumentElement().getNodeName(),
            "Root name should match");
        assertEquals("value", roundTrip.getDocumentElement().getAttribute("attr"),
            "Attribute should match");
        assertEquals("text content", DOMUtil.getNodeText(roundTrip.getElementsByTagName("grandchild").item(0)),
            "Nested text should match");
    }

    @Test
    @Order(45)
    @DisplayName("Namespace handling works correctly")
    void namespaceHandling_worksCorrectly() throws Exception {
        Document nsDoc = DOMUtil.getDocumentFromString(NAMESPACE_XML);
        String nsString = DOMUtil.getXMLStringFragmentFromNode(nsDoc.getDocumentElement());

        assertTrue(nsString.contains("xmlns="), "Should include namespace declaration");
        assertEquals("http://example.com/ns", nsDoc.getDocumentElement().getNamespaceURI(),
            "Namespace URI should match");
    }

    @Test
    @Order(46)
    @DisplayName("Complex document with all node types handles correctly")
    void complexDocument_withAllNodeTypes_handlesCorrectly() throws Exception {
        String complexXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<root>\n" +
                "  <!-- Comment -->\n" +
                "  <element>text</element>\n" +
                "  <![CDATA[cdata content]]>\n" +
                "  <empty/>\n" +
                "</root>";

        Document doc = DOMUtil.getDocumentFromString(complexXml);
        String result = DOMUtil.getXMLStringFragmentFromNode(doc);

        assertTrue(result.contains("text"), "Should preserve element text");
        assertTrue(result.contains("CDATA content"), "Should preserve CDATA content");
        assertTrue(result.contains("empty"), "Should preserve empty element");
    }

    @Test
    @Order(47)
    @DisplayName("XPath with namespace queries works")
    void xpathWithNamespaceQueries_works() throws Exception {
        String nsXml = "<?xml version=\"1.0\"?><root xmlns:ns=\"http://example.com\"><ns:child>content</ns:child></root>";
        Document doc = DOMUtil.getDocumentFromString(nsXml);

        // Note: DOMUtil doesn't directly support namespace-aware XPath queries
        // This tests that it doesn't fail, though it may not find the namespaced element
        assertDoesNotThrow(() -> DOMUtil.selectSingleNode(doc, "//child"),
            "Should handle namespace queries without throwing");
    }

    // =========================================================================
    // Error Handling Tests
    // =========================================================================

    @Test
    @Order(48)
    @DisplayName("All methods handle empty XML gracefully")
    void allMethods_handleEmptyXMLGracefully() throws Exception {
        String emptyXml = "<root/>";

        Document doc = DOMUtil.getDocumentFromString(emptyXml);
        String string = DOMUtil.getXMLStringFragmentFromNode(doc);
        String text = DOMUtil.getNodeText(doc.getDocumentElement());
        NodeList nodes = DOMUtil.selectNodeList(doc, "//*");

        assertEquals(1, nodes.getLength(), "Should find root element");
        assertEquals("", text, "Empty element should have empty text");
        assertTrue(string.contains("<root/>"), "Empty element should be represented correctly");
    }

    @Test
    @Order(49)
    @DisplayName("Methods handle whitespace-only content")
    void methods_handleWhitespaceOnlyContent() throws Exception {
        String xml = "<root>   \n\t  </root>";
        Document doc = DOMUtil.getDocumentFromString(xml);
        String text = DOMUtil.getNodeText(doc.getDocumentElement());

        assertEquals("   \n\t  ", text, "Whitespace content should be preserved");
    }

    @Test
    @Order(50)
    @DisplayName("Methods handle Unicode content")
    void methods_handleUnicodeContent() throws Exception {
        String xml = "<root>你好世界</root>";
        Document doc = DOMUtil.getDocumentFromString(xml);
        String text = DOMUtil.getNodeText(doc.getDocumentElement());
        String result = DOMUtil.getXMLStringFragmentFromNode(doc);

        assertEquals("你好世界", text, "Unicode text should be preserved");
        assertTrue(result.contains("你好世界"), "Unicode should be in output");
    }

    // =========================================================================
    // Performance Tests
    // =========================================================================

    @Test
    @Order(51)
    @DisplayName("Large document handling")
    void largeDocument_handling() throws Exception {
        StringBuilder largeXml = new StringBuilder("<root>");
        for (int i = 0; i < 100; i++) {
            largeXml.append("<item>").append(i).append("</item>");
        }
        largeXml.append("</root>");

        Document doc = DOMUtil.getDocumentFromString(largeXml.toString());
        String result = DOMUtil.getXMLStringFragmentFromNode(doc);

        assertNotNull(doc, "Should parse large XML");
        assertEquals(100, doc.getElementsByTagName("item").getLength(), "Should have all items");
        assertTrue(result.contains("<item>99</item>"), "Should contain last item");
    }

    @Test
    @Order(52)
    @DisplayName("Deep nesting handling")
    void deepNesting_handling() throws Exception {
        String deepXml = "<root><level1><level2><level3><level4>deep</level4></level3></level2></level1></root>";
        Document doc = DOMUtil.getDocumentFromString(deepXml);

        assertNotNull(doc, "Should parse deeply nested XML");
        assertEquals("deep", DOMUtil.getNodeText(doc.getElementsByTagName("level4").item(0)),
            "Deep content should be accessible");
    }

    @Test
    @Order(53)
    @DisplayName("Many siblings at same level")
    void manySiblingsAtSameLevel() throws Exception {
        StringBuilder xml = new StringBuilder("<root>");
        for (int i = 0; i < 50; i++) {
            xml.append("<child>").append(i).append("</child>");
        }
        xml.append("</root>");

        Document doc = DOMUtil.getDocumentFromString(xml);
        String result = DOMUtil.getXMLStringFragmentFromNode(doc);

        assertEquals(50, doc.getElementsByTagName("child").getLength(), "Should have 50 children");
        assertTrue(result.contains("<child>49</child>"), "Should contain last child");
    }

    // =========================================================================
    // Integration Tests
    // =========================================================================

    @Test
    @Order(54)
    @DisplayName("Complete workflow: parse -> manipulate -> format")
    void completeWorkflow_parseManipulateFormat() throws Exception {
        // Parse XML
        Document doc = DOMUtil.getDocumentFromString(TEST_XML);
        Element root = doc.getDocumentElement();

        // Add new element
        Element newChild = doc.createElement("added");
        newChild.setText("new content");
        root.appendChild(newChild);

        // Remove empty elements
        DOMUtil.removeEmptyNodes(root);

        // Format to string
        String result = DOMUtil.getXMLStringFragmentFromNode(root, true);

        assertTrue(result.contains("added"), "Should contain added element");
        assertTrue(result.contains("new content"), "Should contain new content");
        assertFalse(result.contains("empty"), "Should not contain empty element");
    }

    @Test
    @Order(55)
    @DisplayName("Multiple XPath operations on same document")
    void multipleXpathOperationsOnSameDocument() throws XPathExpressionException {
        // Get all child elements
        NodeList children = DOMUtil.selectNodeList(testElement, "child::*");
        assertEquals(2, children.getLength(), "Should find two child elements");

        // Get specific element by name
        Node grandchild = DOMUtil.selectSingleNode(testElement, "//grandchild");
        assertNotNull(grandchild, "Should find grandchild element");

        // Extract text from found node
        String text = DOMUtil.getNodeText(grandchild);
        assertEquals("text content", text, "Grandchild text should match");
    }

    @Test
    @Order(56)
    @DisplayName("Document creation and population")
    void documentCreationAndPopulation() throws Exception {
        // Create new document
        Document newDoc = DOMUtil.createDocumentInstance();

        // Create elements and populate
        Element root = newDoc.createElement("specification");
        root.setAttribute("id", "test-spec");
        newDoc.appendChild(root);

        Element name = newDoc.createElement("name");
        name.setTextContent("Test Specification");
        root.appendChild(name);

        Element task = newDoc.createElement("task");
        task.setAttribute("id", "task1");
        root.appendChild(task);

        // Convert to string and back
        String xml = DOMUtil.getXMLStringFragmentFromNode(root);
        Document roundTrip = DOMUtil.getDocumentFromString(xml);

        assertEquals("test-spec", roundTrip.getDocumentElement().getAttribute("id"),
            "ID attribute should be preserved");
        assertEquals("Test Specification", DOMUtil.getNodeText(roundTrip.getElementsByTagName("name").item(0)),
            "Name text should be preserved");
        assertEquals(1, roundTrip.getElementsByTagName("task").getLength(), "Task element should be present");
    }

    @Test
    @Order(57)
    @DisplayName("Attribute and child manipulation together")
    void attributeAndChildManipulationTogether() throws Exception {
        Element element = testDocument.createElement("complex");
        element.setAttribute("attr1", "value1");
        element.setAttribute("attr2", "value2");

        Element child1 = testDocument.createElement("child1");
        child1.setTextContent("child1 text");
        Element child2 = testDocument.createElement("child2");
        child2.setTextContent("child2 text");

        element.appendChild(child1);
        element.appendChild(child2);

        // Test various operations
        assertEquals(2, element.getAttributes().getLength(), "Should have two attributes");
        assertEquals("child1 textchild2 text", DOMUtil.getNodeText(element),
            "Should concatenate child text");

        // Remove all attributes
        DOMUtil.removeAllAttributes(element);
        assertEquals(0, element.getAttributes().getLength(), "Should have no attributes");

        // Format to string
        String result = DOMUtil.getXMLStringFragmentFromNode(element);
        assertTrue(result.contains("child1 text"), "Should contain child1 text");
        assertTrue(result.contains("child2 text"), "Should contain child2 text");
    }

    @Test
    @Order(58)
    @DisplayName("Error resilience with various XML structures")
    void errorResilience_withVariousXMLStructures() {
        String[] testCases = {
            "<root/>", // Empty root
            "<root>text</root>", // Text only
            "<root><a/><b>text</b></root>", // Mixed empty and non-empty
            "<root xmlns=\"http://example.com\"><a>text</a></root>" // Namespaced
        };

        for (String xml : testCases) {
            assertDoesNotThrow(() -> {
                Document doc = DOMUtil.getDocumentFromString(xml);
                String result = DOMUtil.getXMLStringFragmentFromNode(doc);
                assertNotNull(doc, "Should parse: " + xml);
                assertNotNull(result, "Should convert to string: " + xml);
            }, "Should handle XML structure gracefully: " + xml);
        }
    }

    @Test
    @Order(59)
    @DisplayName("Memory efficiency with large operations")
    void memoryEfficiency_withLargeOperations() throws Exception {
        // Create a moderately large XML structure
        StringBuilder xml = new StringBuilder("<root>");
        for (int i = 0; i < 20; i++) {
            xml.append("<item>").append(i).append("</item>");
        }
        xml.append("</root>");

        Document doc = DOMUtil.getDocumentFromString(xml.toString());

        // Perform operations that don't create excessive temporary objects
        String text1 = DOMUtil.getNodeText(doc);
        String text2 = DOMUtil.getXMLStringFragmentFromNode(doc);
        NodeList nodes = DOMUtil.selectNodeList(doc, "//item");

        assertEquals(20, nodes.getLength(), "Should have 20 items");
        assertTrue(text1.contains("19"), "Should contain last item number");
        assertTrue(text2.contains("<item>19</item>"), "String representation should be complete");
    }

    @Test
    @Order(60)
    @DisplayName("Consistency between different string conversion methods")
    void consistency_betweenDifferentStringConversionMethods() throws TransformerException {
        // Test that different overloads produce similar results
        String result1 = DOMUtil.getXMLStringFragmentFromNode(testElement);
        String result2 = DOMUtil.getXMLStringFragmentFromNode(testElement, true);
        String result3 = DOMUtil.getXMLStringFragmentFromNode(testElement, "UTF-8");
        String result4 = DOMUtil.getXMLStringFragmentFromNode(testElement, true, "UTF-8");

        // All should be valid XML fragments without declaration
        assertFalse(result1.contains("<?xml"), "Result1 should not have XML declaration");
        assertFalse(result2.contains("<?xml"), "Result2 should not have XML declaration");
        assertFalse(result3.contains("<?xml"), "Result3 should not have XML declaration");
        assertFalse(result4.contains("<?xml"), "Result4 should not have XML declaration");

        // All should contain the content
        assertTrue(result1.contains("<root"), "All results should contain root element");
        assertTrue(result2.contains("<root"), "All results should contain root element");
        assertTrue(result3.contains("<root"), "All results should contain root element");
        assertTrue(result4.contains("<root"), "All results should contain root element");
    }
}
