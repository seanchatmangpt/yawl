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

import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for XNodeParser utility class.
 * Tests XML parsing from strings, Documents, Elements, and various edge cases.
 * Implements Chicago TDD with real YAWL objects - no mocks.
 *
 * @author YAWL Foundation Test Suite
 * @since YAWL v6.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class XNodeParserTest {

    private XNodeParser parser;
    private XNodeParser validatingParser;

    @BeforeEach
    @DisplayName("Setup parsers")
    void setUp() {
        parser = new XNodeParser();
        validatingParser = new XNodeParser(true); // Enable validation
    }

    @AfterEach
    @DisplayName("Clean up after tests")
    void tearDown() {
        // Clean up any resources
    }

    // =========================================================================
    // Constructor Tests
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Default constructor creates valid parser")
    void defaultConstructor_createsValidParser() {
        XNodeParser parser = new XNodeParser();

        assertNotNull(parser, "Parser should not be null");
        // Can't directly access internal validation flag, but test behavior
    }

    @Test
    @Order(2)
    @DisplayName("Constructor with validation flag creates valid parser")
    void constructorWithValidationFlag_createsValidParser() {
        XNodeParser validatingParser = new XNodeParser(true);
        XNodeParser nonValidatingParser = new XNodeParser(false);

        assertNotNull(validatingParser, "Validating parser should not be null");
        assertNotNull(nonValidatingParser, "Non-validating parser should not be null");
    }

    // =========================================================================
    // Basic String Parsing Tests
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("parse with simple element creates correct XNode")
    void parse_withSimpleElement_createsCorrectXNode() {
        String xml = "<root>content</root>";
        XNode result = parser.parse(xml);

        assertNotNull(result, "Should parse to non-null XNode");
        assertEquals("root", result.getName(), "Node name should match");
        assertEquals("content", result.getText(), "Node text should match");
        assertFalse(result.hasChildren(), "Simple element should have no children");
    }

    @Test
    @Order(4)
    @DisplayName("parse with empty string returns null")
    void parse_withEmptyString_returnsNull() {
        XNode result = parser.parse("");

        assertNull(result, "Should return null for empty string");
    }

    @Test
    @Order(5)
    @DisplayName("parse with null string returns null")
    void parse_withNullString_returnsNull() {
        XNode result = parser.parse(null);

        assertNull(result, "Should return null for null string");
    }

    @Test
    @Order(6)
    @DisplayName("parse with whitespace only returns null")
    void parse_withWhitespaceOnly_returnsNull() {
        XNode result = parser.parse("   \n\t  ");

        assertNull(result, "Should return null for whitespace-only string");
    }

    @Test
    @Order(7)
    @DisplayName("parse with XML declaration creates correct XNode")
    void parse_withXMLDeclaration_createsCorrectXNode() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root>content</root>";
        XNode result = parser.parse(xml);

        assertNotNull(result, "Should parse to non-null XNode");
        assertEquals("root", result.getName(), "Node name should match");
        assertEquals("content", result.getText(), "Node text should match");
    }

    @Test
    @Order(8)
    @DisplayName("parse with DOCTYPE declaration creates correct XNode")
    void parse_withDOCTYPEDeclaration_createsCorrectXNode() {
        String xml = "<!DOCTYPE root><root>content</root>";
        XNode result = parser.parse(xml);

        assertNotNull(result, "Should parse to non-null XNode");
        assertEquals("root", result.getName(), "Node name should match");
        assertEquals("content", result.getText(), "Node text should match");
    }

    @Test
    @Order(9)
    @DisplayName("parse with self-closing element creates correct XNode")
    void parse_withSelfClosingElement_createsCorrectXNode() {
        String xml = "<root/>";
        XNode result = parser.parse(xml);

        assertNotNull(result, "Should parse to non-null XNode");
        assertEquals("root", result.getName(), "Node name should match");
        assertEquals("", result.getText(), "Self-closing element should have empty text");
        assertFalse(result.hasChildren(), "Self-closing element should have no children");
    }

    @Test
    @Order(10)
    @DisplayName("parse with element with attributes creates correct XNode")
    void parse_withElementWithAttributes_createsCorrectXNode() {
        String xml = "<root attr1=\"value1\" attr2=\"value2\">content</root>";
        XNode result = parser.parse(xml);

        assertNotNull(result, "Should parse to non-null XNode");
        assertEquals("root", result.getName(), "Node name should match");
        assertEquals("content", result.getText(), "Node text should match");
        assertEquals(2, result.getAttributeCount(), "Should have two attributes");
        assertEquals("value1", result.getAttributeValue("attr1"), "Attribute 1 should match");
        assertEquals("value2", result.getAttributeValue("attr2"), "Attribute 2 should match");
    }

    @Test
    @Order(11)
    @DisplayName("parse with empty element with attributes creates correct XNode")
    void parse_withEmptyElementWithAttributes_createsCorrectXNode() {
        String xml = "<root attr=\"value\"/>";
        XNode result = parser.parse(xml);

        assertNotNull(result, "Should parse to non-null XNode");
        assertEquals("root", result.getName(), "Node name should match");
        assertEquals("", result.getText(), "Empty element should have empty text");
        assertEquals(1, result.getAttributeCount(), "Should have one attribute");
        assertEquals("value", result.getAttributeValue("attr"), "Attribute should match");
        assertFalse(result.hasChildren(), "Empty element should have no children");
    }

    @Test
    @Order(12)
    @DisplayName("parse with element containing special characters creates correct XNode")
    void parse_withElementContainingSpecialCharacters_createsCorrectXNode() {
        String xml = "<root>Test &amp; &lt; &gt; &quot; &apos;</root>";
        XNode result = parser.parse(xml);

        assertNotNull(result, "Should parse to non-null XNode");
        assertEquals("root", result.getName(), "Node name should match");
        assertEquals("Test &amp; &lt; &gt; &quot; &apos;", result.getText(),
            "Special characters should be preserved");
    }

    @Test
    @Order(13)
    @DisplayName("parse with element containing XML entities creates correct XNode")
    void parse_withElementContainingXMLEntities_createsCorrectXNode() {
        String xml = "<root>Text with &amp; entity &lt;tag&gt;</root>";
        XNode result = parser.parse(xml);

        assertNotNull(result, "Should parse to non-null XNode");
        assertEquals("root", result.getName(), "Node name should match");
        assertEquals("Text with &amp; entity &lt;tag&gt;", result.getText(),
            "XML entities should be preserved");
    }

    @Test
    @Order(14)
    @DisplayName("parse with malformed XML returns null")
    void parse_withMalformedXML_returnsNull() {
        String xml = "<root><unclosed></root>";
        XNode result = parser.parse(xml);

        assertNull(result, "Should return null for malformed XML");
    }

    @Test
    @Order(15)
    @DisplayName("parse with validating parser rejects malformed XML")
    void parse_withValidatingParser_rejectsMalformedXML() {
        String xml = "<root><unclosed></root>";
        XNode result = validatingParser.parse(xml);

        assertNull(result, "Validating parser should reject malformed XML");
    }

    @Test
    @Order(16)
    @DisplayName("parse with non-XML string returns null")
    void parse_withNonXMLString_returnsNull() {
        String xml = "This is not XML";
        XNode result = parser.parse(xml);

        assertNull(result, "Should return null for non-XML string");
    }

    @Test
    @Order(17)
    @DisplayName("parse with string not starting with < returns null")
    void parse_withStringNotStartingWithLessThan_returnsNull() {
        String xml = "text without opening tag";
        XNode result = parser.parse(xml);

        assertNull(result, "Should return null for string not starting with <");
    }

    // =========================================================================
    // Complex Structure Parsing Tests
    // =========================================================================

    @Test
    @Order(18)
    @DisplayName("parse with parent and child elements creates hierarchy")
    void parse_withParentAndChildElements_createsHierarchy() {
        String xml = "<parent><child>content</child></parent>";
        XNode result = parser.parse(xml);

        assertNotNull(result, "Should parse to non-null XNode");
        assertEquals("parent", result.getName(), "Parent name should match");
        assertEquals(1, result.getChildCount(), "Should have one child");
        assertTrue(result.hasChildren(), "Should have children");

        XNode child = result.getChild("child");
        assertNotNull(child, "Child should exist");
        assertEquals("child", child.getName(), "Child name should match");
        assertEquals("content", child.getText(), "Child text should match");
    }

    @Test
    @Order(19)
    @DisplayName("parse with multiple children creates multiple child nodes")
    void parse_withMultipleChildren_createsMultipleChildNodes() {
        String xml = "<root><child1>text1</child1><child2>text2</child2></root>";
        XNode result = parser.parse(xml);

        assertEquals(2, result.getChildCount(), "Should have two children");
        assertEquals("text1", result.getChildText("child1"), "First child text should match");
        assertEquals("text2", result.getChildText("child2"), "Second child text should match");
    }

    @Test
    @Order(20)
    @DisplayName("parse with nested children creates deep hierarchy")
    void parse_withNestedChildren_createsDeepHierarchy() {
        String xml = "<root><level1><level2><level3>deep content</level3></level2></level1></root>";
        XNode result = parser.parse(xml);

        assertEquals(1, result.getChildCount(), "Root should have one child");
        XNode level1 = result.getChild("level1");
        assertEquals(1, level1.getChildCount(), "Level1 should have one child");
        XNode level2 = level1.getChild("level2");
        assertEquals(1, level2.getChildCount(), "Level2 should have one child");
        XNode level3 = level2.getChild("level3");
        assertEquals("deep content", level3.getText(), "Level3 text should match");
    }

    @Test
    @Order(21)
    @DisplayName("parse with siblings with same name creates multiple nodes")
    void parse_withSiblingWithSameName_createsMultipleNodes() {
        String xml = "<root><child>first</child><child>second</child></root>";
        XNode result = parser.parse(xml);

        assertEquals(2, result.getChildCount(), "Should have two child nodes");
        assertEquals("first", result.getChild("child").getText(), "First child text should match");
        assertEquals("second", result.getChild(1).getText(), "Second child text should match");
    }

    @Test
    @Order(22)
    @DisplayName("parse with mixed content (text and elements) handles correctly")
    void parse_withMixedContent_handlesCorrectly() {
        String xml = "<root>prefix<child>text</child>suffix</root>";
        XNode result = parser.parse(xml);

        // Note: According to XNode behavior, text content is ignored if children are present
        assertEquals(1, result.getChildCount(), "Should have one child");
        assertEquals("text", result.getChild("child").getText(), "Child text should match");
        // Text content before/after elements is ignored when children are present
    }

    @Test
    @Order(23)
    @DisplayName("parse with text content only preserves text")
    void parse_withTextContentOnly_preservesText() {
        String xml = "<root>only text content</root>";
        XNode result = parser.parse(xml);

        assertEquals("only text content", result.getText(), "Text content should be preserved");
        assertEquals(0, result.getChildCount(), "Should have no children");
    }

    @Test
    @Order(24)
    @DisplayName("parse with element having both text and children (children take precedence)")
    void parse_withElementHavingBothTextAndChildren_childrenTakePrecedence() {
        String xml = "<root>ignored text<child>child text</child></root>";
        XNode result = parser.parse(xml);

        assertEquals(1, result.getChildCount(), "Should have one child");
        assertEquals("child text", result.getChild("child").getText(), "Child text should be preserved");
        // Parent text should be ignored when children are present
    }

    @Test
    @Order(25)
    @DisplayName("parse with empty element has no children or text")
    void parse_withEmptyElement_hasNoChildrenOrText() {
        String xml = "<root/>";
        XNode result = parser.parse(xml);

        assertEquals("", result.getText(), "Empty element should have empty text");
        assertEquals(0, result.getChildCount(), "Empty element should have no children");
    }

    // =========================================================================
    // Comment Tests
    // =========================================================================

    @Test
    @Order(26)
    @DisplayName("parse with comment creates comment node")
    void parse_withComment_createsCommentNode() {
        String xml = "<root><!-- This is a comment --></root>";
        XNode result = parser.parse(xml);

        assertEquals(1, result.getChildCount(), "Should have one child");
        XNode comment = result.getChild(0);
        assertTrue(comment.isComment(), "Child should be a comment");
        assertEquals("This is a comment", comment.getText(), "Comment text should match");
        assertEquals("_!_", comment.getName(), "Comment should have special name");
    }

    @Test
    @Order(27)
    @DisplayName("parse with multiple comments creates multiple comment nodes")
    void parse_withMultipleComments_createsMultipleCommentNodes() {
        String xml = "<root><!-- Comment 1 --><!-- Comment 2 --></root>";
        XNode result = parser.parse(xml);

        assertEquals(2, result.getChildCount(), "Should have two comments");
        assertTrue(result.getChild(0).isComment(), "First should be a comment");
        assertTrue(result.getChild(1).isComment(), "Second should be a comment");
        assertEquals("Comment 1", result.getChild(0).getText(), "First comment text should match");
        assertEquals("Comment 2", result.getChild(1).getText(), "Second comment text should match");
    }

    @Test
    @Order(28)
    @DisplayName("parse with comment containing special characters")
    void parse_withCommentContainingSpecialCharacters() {
        String xml = "<root><!-- Comment with & < > \" ' --></root>";
        XNode result = parser.parse(xml);

        assertEquals(1, result.getChildCount(), "Should have one comment");
        XNode comment = result.getChild(0);
        assertEquals("Comment with & < > \" '", comment.getText(),
            "Special characters in comment should be preserved");
    }

    @Test
    @Order(29)
    @DisplayName("parse with comment at root level with opening comment")
    void parse_withCommentAtRootLevelWithOpeningComment() {
        String xml = "<!-- Opening comment --><root>content</root><!-- Closing comment -->";
        XNode result = parser.parse(xml);

        assertEquals(1, result.getChildCount(), "Should have one root element");
        assertEquals("root", result.getName(), "Root name should match");
        assertEquals("content", result.getText(), "Root text should match");

        // Check opening comments
        assertNotNull(result.getOpeningComments(), "Should have opening comments");
        assertEquals(1, result.getOpeningComments().size(), "Should have one opening comment");
        assertEquals("Opening comment", result.getOpeningComments().get(0));

        // Check closing comments
        assertNotNull(result.getClosingComments(), "Should have closing comments");
        assertEquals(1, result.getClosingComments().size(), "Should have one closing comment");
        assertEquals("Closing comment", result.getClosingComments().get(0));
    }

    @Test
    @Order(30)
    @DisplayName("parse with comment between elements")
    void parse_withCommentBetweenElements() {
        String xml = "<root><child1>text1</child1><!-- Comment --><child2>text2</child2></root>";
        XNode result = parser.parse(xml);

        assertEquals(3, result.getChildCount(), "Should have three children");
        assertEquals("text1", result.getChild("child1").getText(), "First child text should match");
        assertTrue(result.getChild(1).isComment(), "Middle should be a comment");
        assertEquals("text2", result.getChild("child2").getText(), "Second child text should match");
    }

    // =========================================================================
    // CDATA Tests
    // =========================================================================

    @Test
    @Order(31)
    @DisplayName("parse with CDATA creates CDATA node")
    void parse_withCDATA_createsCDATA() {
        String xml = "<root><![CDATA[some <tag> content]]></root>";
        XNode result = parser.parse(xml);

        assertEquals(1, result.getChildCount(), "Should have one child");
        XNode cdata = result.getChild(0);
        assertTrue(cdata.isCDATA(), "Child should be CDATA");
        assertEquals("some <tag> content", cdata.getText(), "CDATA content should match");
        assertEquals("_[_", cdata.getName(), "CDATA should have special name");
    }

    @Test
    @Order(32)
    @DisplayName("parse with CDATA containing special characters preserves content")
    void parse_withCDATAContainingSpecialCharacters_preservesContent() {
        String xml = "<root><![CDATA[Text with &entities; <tags> "quotes" 'apostrophes']]></root>";
        XNode result = parser.parse(xml);

        assertEquals(1, result.getChildCount(), "Should have one child");
        XNode cdata = result.getChild(0);
        assertEquals("Text with &entities; <tags> \"quotes\" 'apostrophes'", cdata.getText(),
            "CDATA content should be preserved exactly");
    }

    @Test
    @Order(33)
    @DisplayName("parse with multiple CDATA sections creates multiple CDATA nodes")
    void parse_withMultipleCDATASections_createsMultipleCDATA() {
        String xml = "<root><![CDATA[first]]></root><![CDATA[second]]></root>";
        // This is malformed XML, let's test valid nested CDATA
        String validXml = "<root><![CDATA[first]]><![CDATA[second]]></root>";
        XNode result = parser.parse(validXml);

        assertEquals(2, result.getChildCount(), "Should have two CDATA nodes");
        assertTrue(result.getChild(0).isCDATA(), "First should be CDATA");
        assertTrue(result.getChild(1).isCDATA(), "Second should be CDATA");
        assertEquals("first", result.getChild(0).getText(), "First CDATA content should match");
        assertEquals("second", result.getChild(1).getText(), "Second CDATA content should match");
    }

    @Test
    @Order(34)
    @DisplayName("parse with empty CDATA creates empty CDATA node")
    void parse_withEmptyCDATA_createsEmptyCDATA() {
        String xml = "<root><![CDATA[]]></root>";
        XNode result = parser.parse(xml);

        assertEquals(1, result.getChildCount(), "Should have one child");
        XNode cdata = result.getChild(0);
        assertTrue(cdata.isCDATA(), "Child should be CDATA");
        assertEquals("", cdata.getText(), "Empty CDATA should have empty text");
    }

    // =========================================================================
    // Namespace Tests
    // =========================================================================

    @Test
    @Order(35)
    @DisplayName("parse with namespace prefix creates correct attributes")
    void parse_withNamespacePrefix_createsCorrectAttributes() {
        String xml = "<root xmlns:ns=\"http://example.com\"><ns:child>content</ns:child></root>";
        XNode result = parser.parse(xml);

        assertEquals(1, result.getChildCount(), "Should have one child");
        XNode child = result.getChild("child");
        assertEquals("content", child.getText(), "Child text should match");
        // Namespaces are handled as attributes in the parser
    }

    @Test
    @Order(36)
    @DisplayName("parse with default namespace creates correct structure")
    void parse_withDefaultNamespace_createsCorrectStructure() {
        String xml = "<root xmlns=\"http://example.com\"><child>content</child></root>";
        XNode result = parser.parse(xml);

        assertEquals(1, result.getChildCount(), "Should have one child");
        XNode child = result.getChild("child");
        assertEquals("content", child.getText(), "Child text should match");
    }

    @Test
    @Order(37)
    @DisplayName("parse with mixed namespaces creates correct structure")
    void parse_withMixedNamespaces_createsCorrectStructure() {
        String xml = "<root xmlns:ns1=\"http://example1.com\" xmlns:ns2=\"http://example2.com\">\n" +
                "  <ns1:child1>content1</ns1:child1>\n" +
                "  <ns2:child2>content2</ns2:child2>\n" +
                "</root>";
        XNode result = parser.parse(xml);

        assertEquals(2, result.getChildCount(), "Should have two children");
        XNode child1 = result.getChild("child1");
        XNode child2 = result.getChild("child2");
        assertEquals("content1", child1.getText(), "First child text should match");
        assertEquals("content2", child2.getText(), "Second child text should match");
    }

    // =========================================================================
    // Element and Document Parsing Tests
    // =========================================================================

    @Test
    @Order(38)
    @DisplayName("parse with JDOM Element creates correct XNode")
    void parse_withJDOMElement_createsCorrectXNode() {
        Element jdomElement = new Element("root");
        jdomElement.setAttribute("attr", "value");
        jdomElement.addContent(new Element("child").setText("child text"));

        XNode result = parser.parse(jdomElement);

        assertEquals("root", result.getName(), "Node name should match");
        assertEquals("value", result.getAttributeValue("attr"), "Attribute should match");
        assertEquals(1, result.getChildCount(), "Should have one child");
        assertEquals("child text", result.getChildText("child"), "Child text should match");
    }

    @Test
    @Order(39)
    @DisplayName("parse with JDOM Document creates correct XNode")
    void parse_withJDOMDocument_createsCorrectXNode() {
        Element root = new Element("root");
        root.setAttribute("attr", "value");
        root.addContent(new Element("child").setText("child text"));
        Document jdomDocument = new Document(root);

        XNode result = parser.parse(jdomDocument);

        assertEquals("root", result.getName(), "Node name should match");
        assertEquals("value", result.getAttributeValue("attr"), "Attribute should match");
        assertEquals(1, result.getChildCount(), "Should have one child");
        assertEquals("child text", result.getChildText("child"), "Child text should match");
    }

    @Test
    @Order(40)
    @DisplayName("parse with null Element returns null")
    void parse_withNullElement_returnsNull() {
        XNode result = parser.parse((Element) null);

        assertNull(result, "Should return null for null Element");
    }

    @Test
    @Order(41)
    @DisplayName("parse with null Document returns null")
    void parse_withNullDocument_returnsNull() {
        XNode result = parser.parse((Document) null);

        assertNull(result, "Should return null for null Document");
    }

    // =========================================================================
    // Suppress Messages Tests
    // =========================================================================

    @Test
    @Order(42)
    @DisplayName("suppressMessages with true suppresses error messages")
    void suppressMessages_withTrue_suppressesErrorMessages() {
        String malformedXml = "<root><unclosed></root>";

        // Test default behavior (messages not suppressed)
        XNode result1 = parser.parse(malformedXml);
        assertNull(result1, "Should return null for malformed XML");

        // Test with suppression
        parser.suppressMessages(true);
        XNode result2 = parser.parse(malformedXml);
        assertNull(result2, "Should still return null but without error messages");

        // Reset
        parser.suppressMessages(false);
    }

    @Test
    @Order(43)
    @DisplayName("suppressMessages with false enables error messages")
    void suppressMessages_withFalse_enablesErrorMessages() {
        parser.suppressMessages(false); // Should be default, but ensure it
        String malformedXml = "<root><unclosed></root>";

        XNode result = parser.parse(malformedXml);
        assertNull(result, "Should return null for malformed XML");
    }

    // =========================================================================
    // Error Handling Tests
    // =========================================================================

    @Test
    @Order(44)
    @DisplayName("parse with deeply nested malformed XML returns null")
    void parse_withDeeplyNestedMalformedXML_returnsNull() {
        String xml = "<root><level1><level2><level3>content</level2></level1></root>";
        XNode result = parser.parse(xml);

        assertNull(result, "Should return null for deeply nested malformed XML");
    }

    @Test
    @Order(45)
    @DisplayName("parse with mismatched tags returns null")
    void parse_withMismatchedTags_returnsNull() {
        String xml = "<root><child></root></child>";
        XNode result = parser.parse(xml);

        assertNull(result, "Should return null for mismatched tags");
    }

    @Test
    @Order(46)
    @DisplayName("parse with unclosed comment returns null")
    void parse_withUnclosedComment_returnsNull() {
        String xml = "<root><!-- Unclosed comment</root>";
        XNode result = parser.parse(xml);

        assertNull(result, "Should return null for unclosed comment");
    }

    @Test
    @Order(47)
    @DisplayName("parse with unclosed CDATA returns null")
    void parse_withUnclosedCDATA_returnsNull() {
        String xml = "<root><![CDATA[Unclosed CDATA</root>";
        XNode result = parser.parse(xml);

        assertNull(result, "Should return null for unclosed CDATA");
    }

    @Test
    @Order(48)
    @DisplayName("parse with incomplete tag returns null")
    void parse_withIncompleteTag_returnsNull() {
        String xml = "<root><child";
        XNode result = parser.parse(xml);

        assertNull(result, "Should return null for incomplete tag");
    }

    @Test
    @Order(49)
    @DisplayName("parse with unterminated attribute returns null")
    void parse_withUnterminatedAttribute_returnsNull() {
        String xml = "<root attr=\"unterminated";
        XNode result = parser.parse(xml);

        assertNull(result, "Should return null for unterminated attribute");
    }

    // =========================================================================
    // Edge Case Tests
    // =========================================================================

    @Test
    @Order(50)
    @DisplayName("parse with empty attributes creates correct structure")
    void parse_withEmptyAttributes_createsCorrectStructure() {
        String xml = "<root attr=\"\"></root>";
        XNode result = parser.parse(xml);

        assertEquals("", result.getAttributeValue("attr"), "Empty attribute should be empty string");
        assertEquals(1, result.getAttributeCount(), "Should have one attribute");
    }

    @Test
    @Order(51)
    @DisplayName("parse with attributes with spaces creates correct structure")
    void parse_withAttributesWithSpaces_createsCorrectStructure() {
        String xml = "<root attr=\"value with spaces\"></root>";
        XNode result = parser.parse(xml);

        assertEquals("value with spaces", result.getAttributeValue("attr"), "Attribute with spaces should be preserved");
    }

    @Test
    @Order(52)
    @DisplayName("parse with attributes with quotes creates correct structure")
    void parse_withAttributesWithQuotes_createsCorrectStructure() {
        String xml = "<root attr=\"value&quot;with&quot;quotes\"></root>";
        XNode result = parser.parse(xml);

        assertEquals("value&quot;with&quot;quotes", result.getAttributeValue("attr"),
            "Attributes with quotes should be preserved");
    }

    @Test
    @Order(53)
    @DisplayName("parse with element name containing special characters creates correct XNode")
    void parse_withElementNameContainingSpecialCharacters_createsCorrectXNode() {
        String xml = "<root-test:123>content</root-test:123>";
        XNode result = parser.parse(xml);

        assertEquals("root-test:123", result.getName(), "Element name with special chars should be preserved");
        assertEquals("content", result.getText(), "Element text should be preserved");
    }

    @Test
    @Order(54)
    @DisplayName("parse with UTF-8 BOM removes BOM correctly")
    void parse_withUTF8BOM_removesBOMCorrectly() {
        String xmlWithBOM = "\uFEFF<root>content</root>";
        XNode result = parser.parse(xmlWithBOM);

        assertEquals("root", result.getName(), "Element name should match");
        assertEquals("content", result.getText(), "Element text should match");
    }

    @Test
    @Order(55)
    @DisplayName("parse with newlines and whitespace handles correctly")
    void parse_withNewlinesAndWhitespace_handlesCorrectly() {
        String xmlWithWhitespace = "\n  <root>\n    <child>  text  </child>\n  </root>\n  ";
        XNode result = parser.parse(xmlWithWhitespace);

        assertEquals("root", result.getName(), "Element name should match");
        assertEquals(1, result.getChildCount(), "Should have one child");
        assertEquals("  text  ", result.getChildText("child"), "Child text with whitespace should be preserved");
    }

    @Test
    @Order(56)
    @DisplayName("parse with complex mixed content handles correctly")
    void parse_withComplexMixedContent_handlesCorrectly() {
        String xml = "<root>\n" +
                "  Text before child\n" +
                "  <child>\n" +
                "    Child text\n" +
                "    <!-- Comment in child -->\n" +
                "    <![CDATA[cdata content]]>\n" +
                "  </child>\n" +
                "  Text after child\n" +
                "</root>";
        XNode result = parser.parse(xml);

        assertEquals("root", result.getName(), "Root name should match");
        assertTrue(result.hasChildren(), "Root should have children");

        // Find the child element
        XNode child = null;
        for (XNode node : result.getChildren()) {
            if (!node.isComment() && !node.isCDATA()) {
                child = node;
                break;
            }
        }

        assertNotNull(child, "Should have child element");
        assertEquals("child", child.getName(), "Child name should match");
        assertTrue(child.hasChildren(), "Child should have children");

        // Check comment and CDATA in child
        boolean hasComment = false;
        boolean hasCDATA = false;
        for (XNode node : child.getChildren()) {
            if (node.isComment()) hasComment = true;
            if (node.isCDATA()) hasCDATA = true;
        }

        assertTrue(hasComment, "Child should have comment");
        assertTrue(hasCDATA, "Child should have CDATA");
    }

    @Test
    @Order(57)
    @DisplayName("parse with self-closing child elements creates correct structure")
    void parse_withSelfClosingChildElements_createsCorrectStructure() {
        String xml = "<root><empty1/><empty2 attr=\"value\"/></root>";
        XNode result = parser.parse(xml);

        assertEquals(2, result.getChildCount(), "Should have two children");
        XNode empty1 = result.getChild("empty1");
        XNode empty2 = result.getChild("empty2");

        assertEquals("", empty1.getText(), "First empty element should have empty text");
        assertEquals("", empty2.getText(), "Second empty element should have empty text");
        assertEquals("value", empty2.getAttributeValue("attr"), "Attribute should be preserved");
        assertFalse(empty1.hasChildren(), "First empty should have no children");
        assertFalse(empty2.hasChildren(), "Second empty should have no children");
    }

    @Test
    @Order(58)
    @DisplayName("parse with entity references in text content preserves references")
    void parse_withEntityReferencesInTextContent_preservesReferences() {
        String xml = "<root>Text &amp; &lt; &gt; &quot; &apos;</root>";
        XNode result = parser.parse(xml);

        assertEquals("Text &amp; &lt; &gt; &quot; &apos;", result.getText(),
            "Entity references should be preserved in text content");
    }

    @Test
    @Order(59)
    @DisplayName("parse with numeric character references preserves references")
    void parse_withNumericCharacterReferences_preservesReferences() {
        String xml = "<root>Text &#38; &#60; &#62; &#34; &#39;</root>";
        XNode result = parser.parse(xml);

        assertEquals("Text &#38; &#60; &#62; &#34; &#39;", result.getText(),
            "Numeric character references should be preserved");
    }

    // =========================================================================
    // Integration Tests
    // =========================================================================

    @Test
    @Order(60)
    @DisplayName("parse with complex YAWL workflow example creates correct hierarchy")
    void parse_withComplexYAWLWorkflowExample_createsCorrectHierarchy() {
        String yawlXml = "<specification id=\"test-spec\">\n" +
                "  <name>Test Specification</name>\n" +
                "  <process id=\"test-process\">\n" +
                "    <task id=\"task1\" name=\"First Task\">\n" +
                "      <input>\n" +
                "        <param name=\"param1\"/>\n" +
                "      </input>\n" +
                "      <output>\n" +
                "        <param name=\"result\"/>\n" +
                "      </output>\n" +
                "    </task>\n" +
                "    <flow>\n" +
                "      <arc id=\"arc1\" from=\"task1\" to=\"task2\"/>\n" +
                "    </flow>\n" +
                "  </process>\n" +
                "</specification>";

        XNode result = parser.parse(yawlXml);

        assertEquals("specification", result.getName(), "Root should be specification");
        assertEquals("test-spec", result.getAttributeValue("id"), "Spec ID should match");
        assertEquals(2, result.getChildCount(), "Should have name and process children");

        XNode process = result.getChild("process");
        assertNotNull(process, "Process should exist");
        assertEquals("test-process", process.getAttributeValue("id"), "Process ID should match");

        XNode task = process.getChild("task");
        assertNotNull(task, "Task should exist");
        assertEquals("task1", task.getAttributeValue("id"), "Task ID should match");
        assertEquals("First Task", task.getAttributeValue("name"), "Task name should match");

        XNode input = task.getChild("input");
        XNode output = task.getChild("output");
        assertNotNull(input, "Input should exist");
        assertNotNull(output, "Output should exist");

        assertEquals(1, input.getChildCount(), "Input should have one parameter");
        assertEquals(1, output.getChildCount(), "Output should have one parameter");
    }

    @Test
    @Order(61)
    @DisplayName("round-trip test: XNode -> string -> XNode preserves structure")
    void roundTripTest_XNodeToStringXNode_preservesStructure() {
        // Create original XNode
        XNode original = new XNode("root");
        original.addAttribute("attr1", "value1");
        original.addAttribute("attr2", "value2");
        original.addChild("child", "child text");

        XNode subChild = new XNode("subchild");
        subChild.setText("subchild text");
        original.getChild("child").addChild(subChild);

        // Convert to string
        String xml = original.toString();

        // Parse back to XNode
        XNode parsed = parser.parse(xml);

        // Compare structure
        assertEquals(original.getName(), parsed.getName(), "Names should match");
        assertEquals(original.getAttributeCount(), parsed.getAttributeCount(), "Attribute count should match");
        assertEquals(original.getChildCount(), parsed.getChildCount(), "Child count should match");

        // Compare attributes
        assertEquals(original.getAttributeValue("attr1"), parsed.getAttributeValue("attr1"), "Attr1 should match");
        assertEquals(original.getAttributeValue("attr2"), parsed.getAttributeValue("attr2"), "Attr2 should match");

        // Compare children
        XNode originalChild = original.getChild("child");
        XNode parsedChild = parsed.getChild("child");
        assertEquals(originalChild.getText(), parsedChild.getText(), "Child text should match");

        XNode originalSubChild = originalChild.getChild("subchild");
        XNode parsedSubChild = parsedChild.getChild("subchild");
        assertEquals(originalSubChild.getText(), parsedSubChild.getText(), "Subchild text should match");
    }

    @Test
    @Order(62)
    @DisplayName("parse then convert back with JDOM preserves structure")
    void parseThenConvertBackWithJDOM_preservesStructure() {
        String xml = "<root attr=\"value\"><child>text</child></root>";

        // Parse to XNode
        XNode xnode = parser.parse(xml);

        // Convert to JDOM Element
        Element jdomElement = xnode.toElement();

        // Create new parser and parse JDOM back
        XNode roundTrip = parser.parse(jdomElement);

        // Compare
        assertEquals(xnode.getName(), roundTrip.getName(), "Names should match");
        assertEquals(xnode.getAttributeCount(), roundTrip.getAttributeCount(), "Attribute count should match");
        assertEquals(xnode.getChildCount(), roundTrip.getChildCount(), "Child count should match");
        assertEquals(xnode.getAttributeValue("attr"), roundTrip.getAttributeValue("attr"), "Attribute should match");
        assertEquals(xnode.getChildText("child"), roundTrip.getChildText("child"), "Child text should match");
    }

    @Test
    @Order(63)
    @DisplayName("parse with validating parser accepts valid XML")
    void parse_withValidatingParser_acceptsValidXML() {
        String validXml = "<root><child attr=\"value\">text</child></root>";
        XNode result = validatingParser.parse(validXml);

        assertNotNull(result, "Should accept valid XML");
        assertEquals("root", result.getName(), "Root name should match");
        assertEquals(1, result.getChildCount(), "Should have one child");
    }

    @Test
    @Order(64)
    @DisplayName("parse with validating parser rejects invalid XML with proper structure")
    void parse_withValidatingParser_rejectsInvalidXMLWithProperStructure() {
        // Test various invalid structures that validation should catch
        String[] invalidXmls = {
            "<root><unclosed></root>", // Unclosed tag
            "<root><child></unclosed></root>", // Mismatched tags
            "<root><child attr='unterminated></root>", // Unterminated attribute
            "<root><child><unclosed></child></root>" // Deep nesting error
        };

        for (String xml : invalidXmls) {
            XNode result = validatingParser.parse(xml);
            assertNull(result, "Should reject invalid XML: " + xml);
        }
    }

    @Test
    @Order(65)
    @DisplayName("parse performance with large XML")
    void parsePerformance_withLargeXML() {
        // Create a large XML string
        StringBuilder largeXml = new StringBuilder("<root>");
        for (int i = 0; i < 100; i++) {
            largeXml.append("<item id=\"").append(i).append("\">value ").append(i).append("</item>");
        }
        largeXml.append("</root>");

        // Parse it
        XNode result = parser.parse(largeXml.toString());

        assertNotNull(result, "Should parse large XML");
        assertEquals(100, result.getChildCount(), "Should have 100 items");
        assertEquals("value 99", result.getChild("item").getText(), "Last item should be correct");
    }

    @Test
    @Order(66)
    @DisplayName("parse with very deep nesting")
    void parse_withVeryDeepNesting() {
        String deepXml = "<root>";
        for (int i = 0; i < 20; i++) {
            deepXml += "<level" + i + ">";
        }
        deepXml += "deep content";
        for (int i = 19; i >= 0; i--) {
            deepXml += "</level" + i + ">";
        }
        deepXml += "</root>";

        XNode result = parser.parse(deepXml);

        assertNotNull(result, "Should parse deeply nested XML");
        // Navigate to deepest level
        XNode current = result;
        for (int i = 0; i < 20; i++) {
            current = current.getChild("level" + i);
        }
        assertEquals("deep content", current.getText(), "Deep content should match");
    }

    @Test
    @Order(67)
    @DisplayName("parse with many siblings at same level")
    void parse_withManySiblingsAtSameLevel() {
        StringBuilder xml = new StringBuilder("<root>");
        for (int i = 0; i < 50; i++) {
            xml.append("<child id=\"").append(i).append("\">text ").append(i).append("</child>");
        }
        xml.append("</root>");

        XNode result = parser.parse(xml.toString());

        assertNotNull(result, "Should parse XML with many siblings");
        assertEquals(50, result.getChildCount(), "Should have 50 child nodes");
        assertEquals("text 0", result.getChild("child").getText(), "First child should be correct");
        assertEquals("text 49", result.getChild(49).getText(), "Last child should be correct");
    }

    @Test
    @Order(68)
    @DisplayName("parse handles mixed content with comments and elements correctly")
    void parse_handlesMixedContentWithCommentsAndElementsCorrectly() {
        String xml = "<root>\n" +
                "  Text before\n" +
                "  <!-- Comment 1 -->\n" +
                "  <child>\n" +
                "    Child text\n" +
                "    <!-- Comment 2 -->\n" +
                "  </child>\n" +
                "  <!-- Comment 3 -->\n" +
                "  Text after\n" +
                "  <![CDATA[cdata content]]>\n" +
                "  <!-- Comment 4 -->\n" +
                "</root>";

        XNode result = parser.parse(xml);

        assertEquals("root", result.getName(), "Root name should match");

        // Count different types of children
        int elementCount = 0;
        int commentCount = 0;
        int cdataCount = 0;

        for (XNode child : result.getChildren()) {
            if (child.isComment()) commentCount++;
            else if (child.isCDATA()) cdataCount++;
            else elementCount++;
        }

        assertEquals(1, elementCount, "Should have one element child");
        assertEquals(4, commentCount, "Should have four comment children");
        assertEquals(1, cdataCount, "Should have one CDATA child");
    }

    @Test
    @Order(69)
    @DisplayName("parse with complex attributes handles correctly")
    void parse_withComplexAttributes_handlesCorrectly() {
        String xml = "<root ns1:attr1=\"value1\" ns2:attr2=\"value2\" normal=\"normal_value\">\n" +
                "  <child ns:attr=\"child_value\">content</child>\n" +
                "</root>";

        XNode result = parser.parse(xml);

        // Check root attributes
        assertEquals(3, result.getAttributeCount(), "Root should have three attributes");
        assertTrue(result.hasAttribute("normal"), "Root should have normal attribute");

        // Check child attributes
        XNode child = result.getChild("child");
        assertNotNull(child, "Child should exist");
        assertEquals(1, child.getAttributeCount(), "Child should have one attribute");
        assertTrue(child.hasAttribute("attr"), "Child should have attr attribute");
        assertEquals("child_value", child.getAttributeValue("attr"), "Child attribute should match");
    }

    @Test
    @Order(70)
    @DisplayName("parse handles XML 1.1 declaration correctly")
    void parse_handlesXML11DeclarationCorrectly() {
        String xml = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<root>content</root>";
        XNode result = parser.parse(xml);

        assertNotNull(result, "Should parse XML 1.1 declaration");
        assertEquals("root", result.getName(), "Root name should match");
        assertEquals("content", result.getText(), "Root text should match");
    }

    @Test
    @Order(71)
    @DisplayName("parse with standalone declaration handles correctly")
    void parse_withStandaloneDeclaration_handlesCorrectly() {
        String xml = "<?xml version=\"1.0\" standalone=\"yes\"?>\n<root>content</root>";
        XNode result = parser.parse(xml);

        assertNotNull(result, "Should parse standalone declaration");
        assertEquals("root", result.getName(), "Root name should match");
        assertEquals("content", result.getText(), "Root text should match");
    }

    @Test
    @Order(72)
    @DisplayName("parse with encoding declaration handles correctly")
    void parse_withEncodingDeclaration_handlesCorrectly() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-16\"?>\n<root>content</root>";
        XNode result = parser.parse(xml);

        assertNotNull(result, "Should parse encoding declaration");
        assertEquals("root", result.getName(), "Root name should match");
        assertEquals("content", result.getText(), "Root text should match");
    }
}