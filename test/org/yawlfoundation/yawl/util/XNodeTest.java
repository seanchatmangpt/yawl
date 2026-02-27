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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.jdom2.Document;
import org.jdom2.Element;
import org.yawlfoundation.yawl.util.XNodeIO;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for XNode utility class.
 * Tests XML node creation, manipulation, serialization, and edge cases.
 * Implements Chicago TDD with real YAWL objects - no mocks.
 *
 * @author YAWL Foundation Test Suite
 * @since YAWL v6.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class XNodeTest {

    private XNode rootNode;
    private XNode childNode;
    private XNode grandChildNode;

    @BeforeEach
    @DisplayName("Setup test nodes")
    void setUp() {
        // Create a hierarchical structure for testing
        rootNode = new XNode("root");
        childNode = new XNode("child");
        grandChildNode = new XNode("grandchild", "value");
    }

    // =========================================================================
    // Constructor Tests
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Constructor with name only creates valid node")
    void constructor_withNameOnly_createsValidNode() {
        XNode node = new XNode("test");

        assertEquals("test", node.getName(), "Node name should match constructor parameter");
        assertNull(node.getText(), "New node should have null text content");
        assertEquals(0, node.getAttributeCount(), "New node should have no attributes");
        assertEquals(0, node.getChildCount(), "New node should have no children");
        assertTrue(node.getChildren().isEmpty(), "Children list should be empty");
        assertFalse(node.hasChildren(), "hasChildren() should return false");
        assertFalse(node.isComment(), "New node should not be a comment");
        assertFalse(node.isCDATA(), "New node should not be CDATA");
    }

    @Test
    @Order(2)
    @DisplayName("Constructor with name and text creates valid node")
    void constructor_withNameAndText_createsValidNode() {
        String testText = "Hello, World!";
        XNode node = new XNode("test", testText);

        assertEquals("test", node.getName(), "Node name should match constructor parameter");
        assertEquals(testText, node.getText(), "Node text should match constructor parameter");
        assertEquals(0, node.getAttributeCount(), "New node should have no attributes");
    }

    @Test
    @Order(3)
    @DisplayName("Constructor with null name throws exception")
    void constructor_withNullName_throwsException() {
        assertThrows(NullPointerException.class, () -> new XNode(null),
            "Constructor should throw NullPointerException for null name");
    }

    @Test
    @Order(4)
    @DisplayName("Constructor with null text creates valid node")
    void constructor_withNullText_createsValidNode() {
        XNode node = new XNode("test", null);

        assertEquals("test", node.getName(), "Node name should match constructor parameter");
        assertNull(node.getText(), "Node text should be null");
    }

    // =========================================================================
    // Attribute Tests
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("addAttribute with string value adds attribute correctly")
    void addAttribute_withStringValue_addsAttribute() {
        rootNode.addAttribute("key", "value");

        assertEquals(1, rootNode.getAttributeCount(), "Should have one attribute");
        assertTrue(rootNode.hasAttribute("key"), "hasAttribute() should return true");
        assertEquals("value", rootNode.getAttributeValue("key"), "Attribute value should match");
    }

    @Test
    @Order(6)
    @DisplayName("addAttribute with numeric values converts to string")
    void addAttribute_withNumericValues_convertsToString() {
        rootNode.addAttribute("int", 42);
        rootNode.addAttribute("long", 123L);
        rootNode.addAttribute("double", 3.14);
        rootNode.addAttribute("float", 2.5f);
        rootNode.addAttribute("boolean", true);
        rootNode.addAttribute("byte", (byte) 100);
        rootNode.addAttribute("short", (short) 500);

        assertEquals("42", rootNode.getAttributeValue("int"));
        assertEquals("123", rootNode.getAttributeValue("long"));
        assertEquals("3.14", rootNode.getAttributeValue("double"));
        assertEquals("2.5", rootNode.getAttributeValue("float"));
        assertEquals("true", rootNode.getAttributeValue("boolean"));
        assertEquals("100", rootNode.getAttributeValue("byte"));
        assertEquals("500", rootNode.getAttributeValue("short"));
    }

    @Test
    @Order(7)
    @DisplayName("addAttribute with null value adds null string")
    void addAttribute_withNullValue_addsNullString() {
        rootNode.addAttribute("key", null);

        assertEquals(1, rootNode.getAttributeCount(), "Should have one attribute");
        assertEquals("null", rootNode.getAttributeValue("key"), "Null value should become 'null' string");
    }

    @Test
    @Order(8)
    @DisplayName("addAttribute with escape flag escapes XML special characters")
    void addAttribute_withEscape_escapesSpecialChars() {
        String testValue = "Test & 'quoted' <value>";
        rootNode.addAttribute("key", testValue, true);

        assertEquals("Test &amp; &apos;quoted&apos; &lt;value&gt;",
            rootNode.getAttributeValue("key"), "Special characters should be escaped");
    }

    @Test
    @Order(9)
    @DisplayName("addAttribute with null key throws exception")
    void addAttribute_withNullKey_throwsException() {
        assertThrows(NullPointerException.class, () -> rootNode.addAttribute(null, "value"),
            "addAttribute should throw NullPointerException for null key");
    }

    @Test
    @Order(10)
    @DisplayName("addAttributes with map adds all attributes")
    void addAttributes_withMap_addsAllAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("key1", "value1");
        attributes.put("key2", "value2");
        attributes.put("key3", "value3");

        rootNode.addAttributes(attributes);

        assertEquals(3, rootNode.getAttributeCount(), "Should have three attributes");
        assertEquals("value1", rootNode.getAttributeValue("key1"));
        assertEquals("value2", rootNode.getAttributeValue("key2"));
        assertEquals("value3", rootNode.getAttributeValue("key3"));
    }

    @Test
    @Order(11)
    @DisplayName("addAttributes with null map does nothing")
    void addAttributes_withNullMap_doesNothing() {
        rootNode.addAttributes(null);

        assertEquals(0, rootNode.getAttributeCount(), "Should have no attributes");
    }

    @Test
    @Order(12)
    @DisplayName("addAttributes with empty map does nothing")
    void addAttributes_withEmptyMap_doesNothing() {
        rootNode.addAttributes(Collections.emptyMap());

        assertEquals(0, rootNode.getAttributeCount(), "Should have no attributes");
    }

    @Test
    @Order(13)
    @DisplayName("setAttributes replaces all attributes")
    void setAttributes_replacesAllAttributes() {
        // Add initial attributes
        rootNode.addAttribute("old1", "oldValue1");
        rootNode.addAttribute("old2", "oldValue2");

        // Set new attributes
        Map<String, String> newAttributes = new HashMap<>();
        newAttributes.put("new1", "newValue1");
        newAttributes.put("new2", "newValue2");
        rootNode.setAttributes(newAttributes);

        assertEquals(2, rootNode.getAttributeCount(), "Should have two attributes");
        assertFalse(rootNode.hasAttribute("old1"), "Old attribute 1 should be gone");
        assertFalse(rootNode.hasAttribute("old2"), "Old attribute 2 should be gone");
        assertTrue(rootNode.hasAttribute("new1"), "New attribute 1 should be present");
        assertTrue(rootNode.hasAttribute("new2"), "New attribute 2 should be present");
    }

    @Test
    @Order(14)
    @DisplayName("setAttributes with null map clears all attributes")
    void setAttributes_withNullMap_clearsAllAttributes() {
        // Add some attributes first
        rootNode.addAttribute("key", "value");

        // Set null attributes
        rootNode.setAttributes(null);

        assertEquals(0, rootNode.getAttributeCount(), "Should have no attributes");
        assertFalse(rootNode.hasAttribute("key"), "Old attribute should be cleared");
    }

    // =========================================================================
    // Child Node Tests
    // =========================================================================

    @Test
    @Order(15)
    @DisplayName("addChild with XNode adds child correctly")
    void addChild_withXNode_addsChild() {
        XNode child = new XNode("testChild");
        XNode result = rootNode.addChild(child);

        assertSame(child, result, "Should return the same child node");
        assertEquals(1, rootNode.getChildCount(), "Should have one child");
        assertEquals(1, rootNode.getChildren().size(), "Children list should have one element");
        assertTrue(rootNode.hasChildren(), "hasChildren() should return true");
        assertTrue(rootNode.hasChild("testChild"), "hasChild() should return true");
        assertEquals(1, rootNode.getChildren("testChild").size(), "getChildren() should return one child");
    }

    @Test
    @Order(16)
    @DisplayName("addChild with name creates new child")
    void addChild_withName_createsNewChild() {
        XNode result = rootNode.addChild("testChild");

        assertNotNull(result, "Should return a new child node");
        assertEquals("testChild", result.getName(), "Child name should match");
        assertEquals(1, rootNode.getChildCount(), "Parent should have one child");
        assertSame(result, rootNode.getChild("testChild"), "Should be the same instance");
    }

    @Test
    @Order(17)
    @DisplayName("addChild with name and text creates new child with text")
    void addChild_withNameAndText_createsNewChildWithText() {
        String text = "child text";
        XNode result = rootNode.addChild("testChild", text);

        assertEquals("testChild", result.getName(), "Child name should match");
        assertEquals(text, result.getText(), "Child text should match");
        assertEquals(1, rootNode.getChildCount(), "Parent should have one child");
    }

    @Test
    @Order(18)
    @DisplayName("addChild with name and numeric value creates child with string value")
    void addChild_withNameAndNumericValue_createsChildWithStringValue() {
        XNode result = rootNode.addChild("number", 42);

        assertEquals("42", result.getText(), "Numeric value should be converted to string");
    }

    @Test
    @Order(19)
    @DisplayName("addChild with null child does nothing")
    void addChild_withNullChild_doesNothing() {
        rootNode.addChild(null);

        assertEquals(0, rootNode.getChildCount(), "Should have no children");
    }

    @Test
    @Order(20)
    @DisplayName("addChild with null name creates child with null name")
    void addChild_withNullName_createsChildWithNullName() {
        XNode child = new XNode(null, "text");
        rootNode.addChild(child);

        assertNull(child.getName(), "Child name should be null");
        assertEquals(1, rootNode.getChildCount(), "Should have one child");
    }

    @Test
    @Order(21)
    @DisplayName("insertChild with index inserts at correct position")
    void insertChild_withIndex_insertsAtCorrectPosition() {
        XNode firstChild = rootNode.addChild("first");
        XNode secondChild = new XNode("second");
        XNode thirdChild = rootNode.addChild("third");

        // Insert second child at position 1
        rootNode.insertChild(1, secondChild);

        assertEquals(3, rootNode.getChildCount(), "Should have three children");
        assertSame(firstChild, rootNode.getChild(0), "First child should be at position 0");
        assertSame(secondChild, rootNode.getChild(1), "Second child should be at position 1");
        assertSame(thirdChild, rootNode.getChild(2), "Third child should be at position 2");
        assertEquals("second", rootNode.getChild(1).getName(), "Inserted child should have correct name");
    }

    @Test
    @Order(22)
    @DisplayName("insertChild with negative index appends to end")
    void insertChild_withNegativeIndex_appendsToEnd() {
        rootNode.addChild("first");

        XNode second = new XNode("second");
        rootNode.insertChild(-1, second);

        assertEquals(2, rootNode.getChildCount(), "Should have two children");
        assertSame(second, rootNode.getChild(1), "Child should be at the end");
    }

    @Test
    @Order(23)
    @DisplayName("insertChild with index beyond end appends to end")
    void insertChild_withIndexBeyondEnd_appendsToEnd() {
        rootNode.addChild("first");

        XNode second = new XNode("second");
        rootNode.insertChild(10, second);

        assertEquals(2, rootNode.getChildCount(), "Should have two children");
        assertSame(second, rootNode.getChild(1), "Child should be at the end");
    }

    @Test
    @Order(24)
    @DisplayName("insertChild with null child does nothing")
    void insertChild_withNullChild_doesNothing() {
        rootNode.addChild("first");
        rootNode.insertChild(0, null);

        assertEquals(1, rootNode.getChildCount(), "Should still have one child");
    }

    @Test
    @Order(25)
    @DisplayName("addChildren with collection adds all children")
    void addChildren_withCollection_addsAllChildren() {
        List<XNode> children = Arrays.asList(
            new XNode("child1", "text1"),
            new XNode("child2", "text2"),
            new XNode("child3", "text3")
        );

        rootNode.addChildren(children);

        assertEquals(3, rootNode.getChildCount(), "Should have three children");
        assertEquals("text1", rootNode.getChild("child1").getText());
        assertEquals("text2", rootNode.getChild("child2").getText());
        assertEquals("text3", rootNode.getChild("child3").getText());
    }

    @Test
    @Order(26)
    @DisplayName("addChildren with null collection does nothing")
    void addChildren_withNullCollection_doesNothing() {
        rootNode.addChildren(null);

        assertEquals(0, rootNode.getChildCount(), "Should have no children");
    }

    @Test
    @Order(27)
    @DisplayName("addChildren with map creates children with keys as names")
    void addChildren_withMap_createsChildrenWithKeysAsNames() {
        Map<String, String> childrenData = new HashMap<>();
        childrenData.put("child1", "text1");
        childrenData.put("child2", "text2");

        rootNode.addChildren(childrenData);

        assertEquals(2, rootNode.getChildCount(), "Should have two children");
        assertEquals("text1", rootNode.getChild("child1").getText());
        assertEquals("text2", rootNode.getChild("child2").getText());
    }

    @Test
    @Order(28)
    @DisplayName("removeChild removes specific child")
    void removeChild_removesSpecificChild() {
        XNode child1 = rootNode.addChild("child1");
        XNode child2 = rootNode.addChild("child2");
        XNode child3 = rootNode.addChild("child3");

        assertTrue(rootNode.removeChild(child2), "removeChild should return true for existing child");
        assertEquals(2, rootNode.getChildCount(), "Should have two children remaining");
        assertTrue(rootNode.hasChild("child1"), "child1 should still exist");
        assertFalse(rootNode.hasChild("child2"), "child2 should be removed");
        assertTrue(rootNode.hasChild("child3"), "child3 should still exist");
    }

    @Test
    @Order(29)
    @DisplayName("removeChild with null returns false")
    void removeChild_withNull_returnsFalse() {
        assertFalse(rootNode.removeChild(null), "removeChild should return false for null child");
    }

    @Test
    @Order(30)
    @DisplayName("removeChild with non-child returns false")
    void removeChild_withNonChild_returnsFalse() {
        XNode otherNode = new XNode("other");
        assertFalse(rootNode.removeChild(otherNode), "removeChild should return false for non-child");
    }

    @Test
    @Order(31)
    @DisplayName("removeChildren removes all children")
    void removeChildren_removesAllChildren() {
        rootNode.addChild("child1");
        rootNode.addChild("child2");
        rootNode.addChild("child3");

        assertTrue(rootNode.hasChildren(), "Should have children before removal");
        rootNode.removeChildren();
        assertEquals(0, rootNode.getChildCount(), "Should have no children after removal");
        assertFalse(rootNode.hasChildren(), "hasChildren() should return false after removal");
    }

    @Test
    @Order(32)
    @DisplayName("removeChildren with no children does nothing")
    void removeChildren_withNoChildren_doesNothing() {
        rootNode.removeChildren();
        assertEquals(0, rootNode.getChildCount(), "Should still have no children");
    }

    @Test
    @Order(33)
    @DisplayName("getChild with name returns correct child")
    void getChild_withName_returnsCorrectChild() {
        XNode child = rootNode.addChild("testChild", "text");

        assertSame(child, rootNode.getChild("testChild"), "Should return the correct child");
        assertNull(rootNode.getChild("nonexistent"), "Should return null for nonexistent child");
    }

    @Test
    @Order(34)
    @DisplayName("getChild with index returns correct child")
    void getChild_withIndex_returnsCorrectChild() {
        XNode child1 = rootNode.addChild("first");
        XNode child2 = rootNode.addChild("second");

        assertSame(child1, rootNode.getChild(0), "Should return first child at index 0");
        assertSame(child2, rootNode.getChild(1), "Should return second child at index 1");
        assertNull(rootNode.getChild(-1), "Should return null for negative index");
        assertNull(rootNode.getChild(10), "Should return null for out-of-bounds index");
    }

    @Test
    @Order(35)
    @DisplayName("getChild returns first text-type child")
    void getChild_returnsFirstTextTypeChild() {
        XNode commentChild = new XNode("comment");
        commentChild.setContentType(XNode.ContentType.comment);
        rootNode.addChild(commentChild);
        rootNode.addChild("textChild", "some text");
        rootNode.addChild("anotherTextChild", "more text");

        XNode textChild = rootNode.getChild();

        assertNotNull(textChild, "Should return a text-type child");
        assertEquals("textChild", textChild.getName(), "Should return the first text child");
        assertFalse(textChild.isComment(), "Returned child should not be a comment");
    }

    @Test
    @Order(36)
    @DisplayName("getChild returns null when no text children")
    void getChild_returnsNullWhenNoTextChildren() {
        rootNode.addChild("comment", "not a real comment");
        rootNode.addChildCDATA("CDATA content");
        rootNode.addComment("real comment");

        assertNull(rootNode.getChild(), "Should return null when no text children");
    }

    @Test
    @Order(37)
    @DisplayName("getOrAddChild returns existing child")
    void getOrAddChild_returnsExistingChild() {
        XNode existingChild = rootNode.addChild("existing", "old text");
        XNode result = rootNode.getOrAddChild("existing");

        assertSame(existingChild, result, "Should return existing child");
        assertEquals("old text", result.getText(), "Existing child text should be preserved");
        assertEquals(1, rootNode.getChildCount(), "Should still have only one child");
    }

    @Test
    @Order(38)
    @DisplayName("getOrAddChild creates new child if doesn't exist")
    void getOrAddChild_createsNewChildIfDoesntExist() {
        XNode result = rootNode.getOrAddChild("new");

        assertEquals("new", result.getName(), "New child should have correct name");
        assertEquals(1, rootNode.getChildCount(), "Should have one child");
        assertSame(result, rootNode.getChild("new"), "Should be the same instance");
    }

    @Test
    @Order(39)
    @DisplayName("getChildren with name returns correct children")
    void getChildren_withName_returnsCorrectChildren() {
        rootNode.addChild("common", "first");
        rootNode.addChild("different", "second");
        rootNode.addChild("common", "third");
        rootNode.addChild("different", "fourth");

        List<XNode> commonChildren = rootNode.getChildren("common");
        List<XNode> differentChildren = rootNode.getChildren("different");

        assertEquals(2, commonChildren.size(), "Should have two common children");
        assertEquals(2, differentChildren.size(), "Should have two different children");
        assertEquals("first", commonChildren.get(0).getText());
        assertEquals("third", commonChildren.get(1).getText());
    }

    @Test
    @Order(40)
    @DisplayName("getChildren with name returns empty list for nonexistent name")
    void getChildren_withName_returnsEmptyListForNonexistentName() {
        List<XNode> children = rootNode.getChildren("nonexistent");

        assertTrue(children.isEmpty(), "Should return empty list for nonexistent name");
    }

    @Test
    @Order(41)
    @DisplayName("getChildren returns all children")
    void getChildren_returnsAllChildren() {
        XNode child1 = rootNode.addChild("child1");
        XNode child2 = rootNode.addChild("child2");
        XNode child3 = rootNode.addChild("child3");

        List<XNode> children = rootNode.getChildren();

        assertEquals(3, children.size(), "Should have three children");
        assertTrue(children.contains(child1), "Should contain child1");
        assertTrue(children.contains(child2), "Should contain child2");
        assertTrue(children.contains(child3), "Should contain child3");
    }

    @Test
    @Order(42)
    @DisplayName("getChildren returns unmodifiable list")
    void getChildren_returnsUnmodifiableList() {
        rootNode.addChild("child1");
        List<XNode> children = rootNode.getChildren();

        assertThrows(UnsupportedOperationException.class, () -> children.add(new XNode("illegal")),
            "Children list should be unmodifiable");
    }

    @Test
    @Order(43)
    @DisplayName("getChildren with type filters by content type")
    void getChildren_withType_filtersByContentType() {
        XNode comment = new XNode("comment");
        comment.setContentType(XNode.ContentType.comment);
        XNode text = new XNode("text");
        XNode cdata = new XNode("cdata");
        cdata.setContentType(XNode.ContentType.cdata);

        rootNode.addChild(comment);
        rootNode.addChild(text);
        rootNode.addChild(cdata);

        List<XNode> comments = rootNode.getChildren(XNode.ContentType.comment);
        List<XNode> texts = rootNode.getChildren(XNode.ContentType.text);
        List<XNode> cdatas = rootNode.getChildren(XNode.ContentType.cdata);

        assertEquals(1, comments.size(), "Should have one comment");
        assertEquals(1, texts.size(), "Should have one text child");
        assertEquals(1, cdatas.size(), "Should have one CDATA child");
        assertTrue(comments.get(0).isComment(), "Filtered child should be a comment");
        assertTrue(cdatas.get(0).isCDATA(), "Filtered child should be CDATA");
    }

    @Test
    @Order(44)
    @DisplayName("getChildText returns text of named child")
    void getChildText_returnsTextOfNamedChild() {
        rootNode.addChild("name", "John Doe");
        rootNode.addChild("age", "30");

        assertEquals("John Doe", rootNode.getChildText("name"), "Should return child text");
        assertEquals("30", rootNode.getChildText("age"), "Should return another child text");
        assertNull(rootNode.getChildText("nonexistent"), "Should return null for nonexistent child");
    }

    @Test
    @Order(45)
    @DisplayName("getChildText with escape flag escapes text")
    void getChildText_withEscape_escapesText() {
        String originalText = "Test & 'quotes' <tag>";
        rootNode.addChild("content", originalText);

        String escapedText = rootNode.getChildText("content", true);
        assertEquals("Test &amp; &apos;quotes&apos; &lt;tag&gt;", escapedText, "Text should be escaped");

        String unescapedText = rootNode.getChildText("content", false);
        assertEquals(originalText, unescapedText, "Text should be unescaped");
    }

    @Test
    @Order(46)
    @DisplayName("posChildWithName returns correct position")
    void posChildWithName_returnsCorrectPosition() {
        rootNode.addChild("first");
        rootNode.addChild("second");
        rootNode.addChild("third");
        rootNode.addChild("second"); // duplicate

        assertEquals(1, rootNode.posChildWithName("second"), "Should return first occurrence");
        assertEquals(0, rootNode.posChildWithName("first"), "Should return position of first");
        assertEquals(-1, rootNode.posChildWithName("nonexistent"), "Should return -1 for nonexistent");
    }

    @Test
    @Order(47)
    @DisplayName("posChildWithAttribute returns correct position")
    void posChildWithAttribute_returnsCorrectPosition() {
        XNode child1 = rootNode.addChild("child");
        child1.addAttribute("id", "1");

        XNode child2 = rootNode.addChild("child");
        child2.addAttribute("id", "2");

        XNode child3 = rootNode.addChild("child");
        child3.addAttribute("id", "3");

        assertEquals(1, rootNode.posChildWithAttribute("id", "2"), "Should return position of child with id=2");
        assertEquals(-1, rootNode.posChildWithAttribute("id", "nonexistent"), "Should return -1 for nonexistent");
    }

    // =========================================================================
    // Comment and CDATA Tests
    // =========================================================================

    @Test
    @Order(48)
    @DisplayName("addComment adds comment child correctly")
    void addComment_addsCommentChild() {
        XNode comment = rootNode.addComment("This is a comment");

        assertNotNull(comment, "Should return the comment node");
        assertEquals("_!_", comment.getName(), "Comment node should have name '_!_'");
        assertTrue(comment.isComment(), "Node should be marked as comment");
        assertEquals(1, rootNode.getChildCount(), "Parent should have one child");
        assertEquals("This is a comment", comment.getText(), "Comment text should match");
    }

    @Test
    @Order(49)
    @DisplayName("insertComment inserts comment at correct position")
    void insertComment_insertsCommentAtCorrectPosition() {
        rootNode.addChild("before");
        XNode comment = rootNode.insertComment(1, "Inserted comment");
        rootNode.addChild("after");

        assertEquals(3, rootNode.getChildCount(), "Should have three children");
        assertEquals("before", rootNode.getChild(0).getName());
        assertTrue(rootNode.getChild(1).isComment(), "Middle child should be comment");
        assertEquals("after", rootNode.getChild(2).getName());
        assertSame(comment, rootNode.getChild(1), "Should return inserted comment");
    }

    @Test
    @Order(50)
    @DisplayName("addCDATA adds CDATA child correctly")
    void addCDATA_addsCDATAChild() {
        String cdataContent = "<xml>content & with special chars</xml>";
        XNode cdata = rootNode.addCDATA(cdataContent);

        assertNotNull(cdata, "Should return the CDATA node");
        assertEquals("_[_", cdata.getName(), "CDATA node should have name '_[_'");
        assertTrue(cdata.isCDATA(), "Node should be marked as CDATA");
        assertEquals(1, rootNode.getChildCount(), "Parent should have one child");
        assertEquals(cdataContent, cdata.getText(), "CDATA content should match");
    }

    @Test
    @Order(51)
    @DisplayName("addOpeningComment adds comment to opening comments list")
    void addOpeningComment_addsToOpeningComments() {
        rootNode.addOpeningComment("First opening comment");
        rootNode.addOpeningComment("Second opening comment");

        assertEquals(2, rootNode.getOpeningComments().size(), "Should have two opening comments");
        assertTrue(rootNode.hasOpeningComments(), "hasOpeningComments() should return true");
    }

    @Test
    @Order(52)
    @DisplayName("addClosingComment adds comment to closing comments list")
    void addClosingComment_addsToClosingComments() {
        rootNode.addClosingComment("First closing comment");
        rootNode.addClosingComment("Second closing comment");

        assertEquals(2, rootNode.getClosingComments().size(), "Should have two closing comments");
        assertTrue(rootNode.hasClosingComments(), "hasClosingComments() should return true");
    }

    @Test
    @Order(53)
    @DisplayName("getOpeningComments returns unmodifiable list")
    void getOpeningComments_returnsUnmodifiableList() {
        rootNode.addOpeningComment("comment");
        List<String> comments = rootNode.getOpeningComments();

        assertThrows(UnsupportedOperationException.class, () -> comments.add("illegal"),
            "Opening comments list should be unmodifiable");
    }

    @Test
    @Order(54)
    @DisplayName("getClosingComments returns unmodifiable list")
    void getClosingComments_returnsUnmodifiableList() {
        rootNode.addClosingComment("comment");
        List<String> comments = rootNode.getClosingComments();

        assertThrows(UnsupportedOperationException.class, () -> comments.add("illegal"),
            "Closing comments list should be unmodifiable");
    }

    // =========================================================================
    // Text Content Tests
    // =========================================================================

    @Test
    @Order(55)
    @DisplayName("setText with string value sets text correctly")
    void setText_withStringValue_setsText() {
        rootNode.setText("new text");

        assertEquals("new text", rootNode.getText(), "Text should be set correctly");
        assertEquals("new text", rootNode.getText(false), "getText(false) should match");
    }

    @Test
    @Order(56)
    @DisplayName("setText with numeric value converts to string")
    void setText_withNumericValue_convertsToString() {
        rootNode.setText(42);
        assertEquals("42", rootNode.getText(), "Numeric value should be converted to string");

        rootNode.setText(3.14);
        assertEquals("3.14", rootNode.getText(), "Double value should be converted to string");

        rootNode.setText(true);
        assertEquals("true", rootNode.getText(), "Boolean value should be converted to string");
    }

    @Test
    @Order(57)
    @DisplayName("setText with null value sets text to null")
    void setText_withNullValue_setsTextToNull() {
        rootNode.setText("original");
        rootNode.setText(null);

        assertNull(rootNode.getText(), "Text should be null after setting null");
    }

    @Test
    @Order(58)
    @DisplayName("setText with escape flag escapes XML special characters")
    void setText_withEscape_escapesSpecialChars() {
        String originalText = "Test & 'quoted' <value>";
        rootNode.setText(originalText, true);

        assertEquals("Test &amp; &apos;quoted&apos; &lt;value&gt;", rootNode.getText(),
            "Special characters should be escaped");
    }

    @Test
    @Order(59)
    @DisplayName("getText with escape flag decodes XML entities")
    void getText_withEscape_decodesEntities() {
        rootNode.setText("Test &amp; &apos;quoted&apos; &lt;value&gt;");
        String decoded = rootNode.getText(true);

        assertEquals("Test & 'quoted' <value>", decoded, "Entities should be decoded");
    }

    @Test
    @Order(60)
    @DisplayName("getText with escape false returns original text")
    void getText_withEscapeFalse_returnsOriginalText() {
        rootNode.setText("Test &amp; &apos;quotedapos; &lt;value&gt;");
        String original = rootNode.getText(false);

        assertEquals("Test &amp; &apos;quotedapos; &lt;value&gt;", original, "Should return original text");
    }

    @Test
    @Order(61)
    @DisplayName("getText returns null for null text")
    void getText_returnsNullForNullText() {
        assertNull(rootNode.getText(), "Should return null for null text");
    }

    @Test
    @Order(62)
    @DisplayName("getTextLength returns correct length")
    void getTextLength_returnsCorrectLength() {
        assertEquals(0, rootNode.getTextLength(), "Should be 0 for null text");

        rootNode.setText("Hello");
        assertEquals(5, rootNode.getTextLength(), "Should return text length");

        rootNode.setText("你好"); // Unicode characters
        assertEquals(2, rootNode.getTextLength(), "Should handle Unicode correctly");
    }

    // =========================================================================
    // Hierarchy and Depth Tests
    // =========================================================================

    @Test
    @Order(63)
    @DisplayName("setParent and getParent work correctly")
    void setParent_andGetParent_workCorrectly() {
        XNode parent = new XNode("parent");
        XNode child = new XNode("child");

        child.setParent(parent);

        assertSame(parent, child.getParent(), "Parent should be set correctly");
        assertNull(parent.getParent(), "Root parent should be null");
    }

    @Test
    @Order(64)
    @DisplayName("setDepth with depth updates node and children")
    void setDepth_withDepth_updatesNodeAndChildren() {
        // Create hierarchy: root -> child -> grandchild
        rootNode.addChild(childNode);
        childNode.addChild(grandChildNode);

        // Set depth of root to 1
        rootNode.setDepth(1);

        assertEquals(1, rootNode.getDepth(), "Root depth should be 1");
        assertEquals(2, childNode.getDepth(), "Child depth should be 2");
        assertEquals(3, grandChildNode.getDepth(), "Grandchild depth should be 3");
    }

    @Test
    @Order(65)
    @DisplayName("setDepth with negative depth updates correctly")
    void setDepth_withNegativeDepth_updatesCorrectly() {
        rootNode.addChild(childNode);
        childNode.setDepth(-5);

        assertEquals(-5, childNode.getDepth(), "Child depth should be -5");
        assertEquals(-4, rootNode.getDepth(), "Parent depth should be -4");
    }

    @Test
    @Order(66)
    @DisplayName("hasChildren returns correct status")
    void hasChildren_returnsCorrectStatus() {
        assertFalse(rootNode.hasChildren(), "Should be false with no children");

        rootNode.addChild("child");
        assertTrue(rootNode.hasChildren(), "Should be true with children");
    }

    @Test
    @Order(67)
    @DisplayName("hasChildren with name checks for named children")
    void hasChildren_withName_checksForNamedChildren() {
        rootNode.addChild("child1");
        rootNode.addChild("child2");
        rootNode.addChild("child1"); // duplicate

        assertTrue(rootNode.hasChildren("child1"), "Should return true for existing named child");
        assertTrue(rootNode.hasChildren("child2"), "Should return true for existing named child");
        assertFalse(rootNode.hasChildren("child3"), "Should return false for nonexistent named child");
    }

    @Test
    @Order(68)
    @DisplayName("hasChild returns correct status")
    void hasChild_returnsCorrectStatus() {
        assertFalse(rootNode.hasChild("nonexistent"), "Should return false for nonexistent child");

        rootNode.addChild("existing");
        assertTrue(rootNode.hasChild("existing"), "Should return true for existing child");
    }

    @Test
    @Order(69)
    @DisplayName("addChild sets parent and depth automatically")
    void addChild_setsParentAndDepthAutomatically() {
        XNode newParent = new XNode("newParent");
        XNode orphan = new XNode("orphan");

        // Before adding
        assertNull(orphan.getParent(), "Orphan should have no parent");
        assertEquals(0, orphan.getDepth(), "Orphan should have depth 0");

        // Add to parent
        newParent.addChild(orphan);

        assertSame(newParent, orphan.getParent(), "Child should have correct parent");
        assertEquals(1, orphan.getDepth(), "Child should have correct depth");
    }

    // =========================================================================
    // Content Type Tests
    // =========================================================================

    @Test
    @Order(70)
    @DisplayName("ContentType enum has correct values")
    void contentTypeEnum_hasCorrectValues() {
        assertEquals(3, XNode.ContentType.values().length, "Should have three content types");
        assertEquals(XNode.ContentType.text, XNode.ContentType.valueOf("text"));
        assertEquals(XNode.ContentType.comment, XNode.ContentType.valueOf("comment"));
        assertEquals(XNode.ContentType.cdata, XNode.ContentType.valueOf("cdata"));
    }

    @Test
    @Order(71)
    @DisplayName("ContentType of new node is text by default")
    void contentType_ofNewNodeIsTextByDefault() {
        XNode node = new XNode("test");
        assertEquals(XNode.ContentType.text, node.getContentType(), "New node should be text type");
    }

    @Test
    @Order(72)
    @DisplayName("Setting content type affects comment and CDATA flags")
    void setContentType_affectsCommentAndCDATAFlags() {
        XNode node = new XNode("test");

        node.setContentType(XNode.ContentType.comment);
        assertTrue(node.isComment(), "isComment() should return true");
        assertFalse(node.isCDATA(), "isCDATA() should return false");

        node.setContentType(XNode.ContentType.cdata);
        assertFalse(node.isComment(), "isComment() should return false");
        assertTrue(node.isCDATA(), "isCDATA() should return true");

        node.setContentType(XNode.ContentType.text);
        assertFalse(node.isComment(), "isComment() should return false");
        assertFalse(node.isCDATA(), "isCDATA() should return false");
    }

    // =========================================================================
    // Serialization Tests
    // =========================================================================

    @Test
    @Order(73)
    @DisplayName("toString generates valid XML")
    void toString_generatesValidXML() {
        rootNode.addAttribute("attr1", "value1");
        rootNode.addChild("child", "child text");
        rootNode.addCDATA("<cdata>content</cdata>");

        String xml = rootNode.toString();

        assertTrue(xml.startsWith("<root"), "XML should start with opening root tag");
        assertTrue(xml.contains("</root>"), "XML should contain closing root tag");
        assertTrue(xml.contains("attr1=\"value1\""), "XML should contain attribute");
        assertTrue(xml.contains("<child>child text</child>"), "XML should contain child element");
        assertTrue(xml.contains("<![CDATA[<cdata>content</cdata>]]>"), "XML should contain CDATA");
    }

    @Test
    @Order(74)
    @DisplayName("toString with header includes XML declaration")
    void toString_withHeaderIncludesXMLDeclaration() {
        String xml = rootNode.toString(true);

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
            "Should include XML declaration");
        assertTrue(xml.contains("<root>"), "Should contain root element");
    }

    @Test
    @Order(75)
    @DisplayName("toPrettyString generates formatted XML")
    void toPrettyString_generatesFormattedXML() {
        rootNode.addChild("child1", "text1");
        rootNode.addChild("child2", "text2");

        String pretty = rootNode.toPrettyString();

        assertTrue(pretty.contains("\n"), "Pretty string should include newlines");
        assertTrue(pretty.contains("  "), "Pretty string should include indentation");
    }

    @Test
    @Order(76)
    @DisplayName("toPrettyString with custom tab size")
    void toPrettyString_withCustomTabSize() {
        rootNode.addChild("child");
        rootNode.addChild("grandchild");

        String pretty = rootNode.toPrettyString(4); // 4 spaces per indent

        assertTrue(pretty.contains("    "), "Should use custom tab size");
    }

    @Test
    @Order(77)
    @DisplayName("toPrettyString with offset and tab size")
    void toPrettyString_withOffsetAndTabSize() {
        rootNode.addChild("child");

        String pretty = rootNode.toPrettyString(2, 4); // offset=2, tabSize=4

        // Should start with 8 spaces (2 * 4)
        assertTrue(pretty.startsWith("        "), "Should respect offset and tab size");
    }

    @Test
    @Order(78)
    @DisplayName("toString includes comments correctly")
    void toString_includesCommentsCorrectly() {
        rootNode.addOpeningComment("Opening comment");
        rootNode.addClosingComment("Closing comment");
        rootNode.addChild("content", "text");

        String xml = rootNode.toString();

        assertTrue(xml.contains("<!-- Opening comment -->"), "Should include opening comment");
        assertTrue(xml.contains("<!-- Closing comment -->"), "Should include closing comment");
    }

    @Test
    @Order(79)
    @DisplayName("toString generates empty element for no content")
    void toString_generatesEmptyElementForNoContent() {
        XNode emptyNode = new XNode("empty");
        String xml = emptyNode.toString();

        assertEquals("<empty/>", xml, "Empty element should be self-closing");
    }

    @Test
    @Order(80)
    @DisplayName("toString handles text-only elements")
    void toString_handlesTextOnlyElements() {
        XNode textOnly = new XNode("text", "content");
        String xml = textOnly.toString();

        assertEquals("<text>content</text>", xml, "Text-only element should not be self-closing");
    }

    @Test
    @Order(81)
    @DisplayName("toString handles child-only elements")
    void toString_handlesChildOnlyElements() {
        XNode parent = new XNode("parent");
        parent.addChild("child", "text");

        String xml = parent.toString();

        assertTrue(xml.contains("<parent>"), "Should have opening tag");
        assertTrue(xml.contains("<child>text</child>"), "Should have child");
        assertTrue(xml.contains("</parent>"), "Should have closing tag");
    }

    @Test
    @Order(82)
    @DisplayName("toString handles both text and children (children take precedence)")
    void toString_handlesBothTextAndChildren() {
        XNode node = new XNode("parent", "ignored text");
        node.addChild("child", "child text");

        String xml = node.toString();

        assertTrue(xml.contains("<parent>"), "Should have opening tag");
        assertTrue(xml.contains("<child>child text</child>"), "Should have child");
        assertTrue(xml.contains("</parent>"), "Should have closing tag");
        assertFalse(xml.contains("ignored text"), "Ignored text should not appear");
    }

    @Test
    @Order(83)
    @DisplayName("length returns string length")
    void length_returnsStringLength() {
        rootNode.setText("Hello");
        assertEquals(rootNode.toString().length(), rootNode.length(), "Length should match string length");

        rootNode.addChild("child");
        assertEquals(rootNode.toString().length(), rootNode.length(), "Length should update with children");
    }

    @Test
    @Order(84)
    @DisplayName("toElement converts to JDOM Element")
    void toElement_convertsToJDOMElement() {
        rootNode.addAttribute("attr", "value");
        rootNode.addChild("child", "text");

        Element element = rootNode.toElement();

        assertNotNull(element, "Should return non-null Element");
        assertEquals("root", element.getName(), "Element name should match");
        assertEquals("value", element.getAttributeValue("attr"), "Attribute should be preserved");
        assertEquals("text", element.getChildText("child"), "Child text should be preserved");
    }

    @Test
    @Order(85)
    @DisplayName("toDocument converts to JDOM Document")
    void toDocument_convertsToJDOMDocument() {
        Document doc = rootNode.toDocument();

        assertNotNull(doc, "Should return non-null Document");
        assertEquals("root", doc.getRootElement().getName(), "Root element name should match");
    }

    // =========================================================================
    // Utility Method Tests
    // =========================================================================

    @Test
    @Order(86)
    @DisplayName("sort sorts children alphabetically")
    void sort_sortsChildrenAlphabetically() {
        rootNode.addChild("zebra");
        rootNode.addChild("apple");
        rootNode.addChild("banana");

        rootNode.sort();

        assertEquals("apple", rootNode.getChild(0).getName());
        assertEquals("banana", rootNode.getChild(1).getName());
        assertEquals("zebra", rootNode.getChild(2).getName());
    }

    @Test
    @Order(87)
    @DisplayName("sort with comparator sorts according to comparator")
    void sort_withComparator_sortsAccordingToComparator() {
        XNode child1 = rootNode.addChild("child1", "value1");
        XNode child2 = rootNode.addChild("child2", "value2");

        // Sort by text content in reverse order
        rootNode.sort((a, b) -> b.getText().compareTo(a.getText()));

        assertSame(child2, rootNode.getChild(0), "Child with 'value2' should be first");
        assertSame(child1, rootNode.getChild(1), "Child with 'value1' should be second");
    }

    @Test
    @Order(88)
    @DisplayName("sort does nothing with no children")
    void sort_doesNothingWithNoChildren() {
        rootNode.sort();
        assertEquals(0, rootNode.getChildCount(), "Should still have no children");
    }

    @Test
    @Order(89)
    @DisplayName("removeDuplicateChildren removes duplicate children")
    void removeDuplicateChildren_removesDuplicateChildren() {
        // Add children with same text content
        rootNode.addChild("duplicate", "same");
        rootNode.addChild("unique", "different");
        rootNode.addChild("duplicate", "same"); // duplicate
        rootNode.addChild("another", "another");

        int countBefore = rootNode.getChildCount();
        rootNode.removeDuplicateChildren();
        int countAfter = rootNode.getChildCount();

        assertTrue(countAfter < countBefore, "Should have removed duplicates");
        assertEquals(3, rootNode.getChildCount(), "Should have three unique children");

        // Check order is maintained
        assertEquals("duplicate", rootNode.getChild(0).getName());
        assertEquals("unique", rootNode.getChild(1).getName());
        assertEquals("another", rootNode.getChild(2).getName());
    }

    @Test
    @Order(90)
    @DisplayName("removeDuplicateChildren preserves order of first occurrence")
    void removeDuplicateChildren_preservesOrderOfFirstOccurrence() {
        rootNode.addChild("first", "text");
        rootNode.addChild("second", "text");
        rootNode.addChild("first", "text"); // duplicate
        rootNode.addChild("third", "text");

        rootNode.removeDuplicateChildren();

        assertEquals(3, rootNode.getChildCount(), "Should have three children");
        assertEquals("first", rootNode.getChild(0).getName(), "First occurrence should be preserved");
        assertEquals("second", rootNode.getChild(1).getName(), "Second should remain");
        assertEquals("third", rootNode.getChild(2).getName(), "Third should remain");
    }

    @Test
    @Order(91)
    @DisplayName("removeDuplicateChildren does nothing with unique children")
    void removeDuplicateChildren_doesNothingWithUniqueChildren() {
        rootNode.addChild("unique1");
        rootNode.addChild("unique2");

        int countBefore = rootNode.getChildCount();
        rootNode.removeDuplicateChildren();
        int countAfter = rootNode.getChildCount();

        assertEquals(countBefore, countAfter, "Should have same number of children");
    }

    @Test
    @Order(92)
    @DisplayName("addContent with string adds content correctly")
    void addContent_withString_addsContentCorrectly() {
        String xmlContent = "<child><subchild>value</subchild></child>";
        rootNode.addContent(xmlContent);

        assertEquals(1, rootNode.getChildCount(), "Should have added one child");
        XNode child = rootNode.getChild("child");
        assertNotNull(child, "Should have added child node");
        assertEquals("value", rootNode.getChildText("subchild"), "Should have added nested content");
    }

    @Test
    @Order(93)
    @DisplayName("addContent with null does nothing")
    void addContent_withNull_doesNothing() {
        rootNode.addContent(null);
        assertEquals(0, rootNode.getChildCount(), "Should have no children");
    }

    @Test
    @Order(94)
    @DisplayName("addContent removes XML declaration if present")
    void addContent_removesXMLDeclarationIfPresent() {
        String xmlWithHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><child>text</child>";
        rootNode.addContent(xmlWithHeader);

        assertEquals(1, rootNode.getChildCount(), "Should have added child");
        assertEquals("text", rootNode.getChildText("child"), "Child text should be preserved");
    }

    @Test
    @Order(95)
    @DisplayName("addContent with namespace prefix and URI")
    void addContent_withNamespacePrefixAndURI() {
        String content = "<child>text</child>";
        rootNode.addContent(content, "ns", "http://example.com/ns");

        XNode child = rootNode.getChild("child");
        assertNotNull(child, "Should have added child with namespace");
    }

    @Test
    @Order(96)
    @DisplayName("addCollection with XNodeIO interface adds children")
    void addCollection_withXNodeIOInterface_addsChildren() {
        // Mock XNodeIO interface for testing
        TestXNodeIO item1 = new TestXNodeIO() {
            @Override
            public void fromXNode(XNode node) { /* not used in this test */ }

            @Override
            public XNode toXNode() {
                return new XNode("item1", "value1");
            }

            @Override
            public XNodeIO newInstance(XNode node) {
                throw new UnsupportedOperationException("newInstance not needed in this test");
            }
        };

        TestXNodeIO item2 = new TestXNodeIO() {
            @Override
            public void fromXNode(XNode node) { /* not used in this test */ }

            @Override
            public XNode toXNode() {
                return new XNode("item2", "value2");
            }

            @Override
            public XNodeIO newInstance(XNode node) {
                throw new UnsupportedOperationException("newInstance not needed in this test");
            }
        };

        List<TestXNodeIO> collection = Arrays.asList(item1, item2);
        rootNode.addCollection(collection);

        assertEquals(2, rootNode.getChildCount(), "Should have added two children");
        assertEquals("value1", rootNode.getChildText("item1"));
        assertEquals("value2", rootNode.getChildText("item2"));
    }

    @Test
    @Order(97)
    @DisplayName("addCollection with null or empty collection does nothing")
    void addCollection_withNullOrEmptyCollection_doesNothing() {
        rootNode.addCollection(null);
        assertEquals(0, rootNode.getChildCount(), "Should have no children");

        rootNode.addCollection(Collections.emptyList());
        assertEquals(0, rootNode.getChildCount(), "Should still have no children");
    }

    // =========================================================================
    // Edge Cases and Exception Tests
    // =========================================================================

    @Test
    @Order(98)
    @DisplayName("Constructor with empty string name is valid")
    void constructor_withEmptyStringName_isValid() {
        XNode node = new XNode("");
        assertEquals("", node.getName(), "Empty name should be allowed");
    }

    @Test
    @Order(99)
    @DisplayName("Constructor with empty string text is valid")
    void constructor_withEmptyStringText_isValid() {
        XNode node = new XNode("test", "");
        assertEquals("", node.getText(), "Empty text should be allowed");
    }

    @Test
    @Order(100)
    @DisplayName("Add attributes with empty values")
    void addAttributes_withEmptyValues() {
        rootNode.addAttribute("empty", "");
        rootNode.addAttribute("space", " ");

        assertEquals("", rootNode.getAttributeValue("empty"));
        assertEquals(" ", rootNode.getAttributeValue("space"));
    }

    @Test
    @Order(101)
    @DisplayName("Add children with special characters in names")
    void addChildren_withSpecialCharactersInNames() {
        XNode node = new XNode("test&<>");
        rootNode.addChild(node);

        assertEquals(1, rootNode.getChildCount(), "Should have special character in name");
    }

    @Test
    @Order(102)
    @DisplayName("Text with only whitespace")
    void test_withOnlyWhitespace() {
        rootNode.setText("   \n\t  ");
        assertEquals("   \n\t  ", rootNode.getText(), "Whitespace should be preserved");
    }

    @Test
    @Order(103)
    @DisplayName("CompareTo comparison works correctly")
    void compareTo_comparisonWorksCorrectly() {
        XNode node1 = new XNode("a", "text1");
        XNode node2 = new XNode("b", "text2");
        XNode node3 = new XNode("a", "text3");
        XNode node4 = new XNode("a", "text1"); // identical

        assertTrue(node1.compareTo(node2) < 0, "a should come before b");
        assertTrue(node2.compareTo(node1) > 0, "b should come after a");
        assertTrue(node1.compareTo(node3) < 0, "text1 should come before text3 for same name");
        assertEquals(0, node1.compareTo(node4), "Identical nodes should be equal");
    }

    @Test
    @Order(104)
    @DisplayName("Equals and hashCode work correctly")
    void equalsAndHashCode_workCorrectly() {
        XNode node1 = new XNode("test", "text");
        XNode node2 = new XNode("test", "text");
        XNode node3 = new XNode("test", "different");
        XNode node4 = new XNode("different", "text");

        assertEquals(node1, node2, "Nodes with same name and text should be equal");
        assertNotEquals(node1, node3, "Nodes with different text should not be equal");
        assertNotEquals(node1, node4, "Nodes with different name should not be equal");
        assertNotEquals(node1, null, "Node should not be equal to null");
        assertNotEquals(node1, "string", "Node should not be equal to string");

        assertEquals(node1.hashCode(), node2.hashCode(), "Equal nodes should have same hash code");
    }

    // =========================================================================
    // Helper Classes
    // =========================================================================

    /**
     * Helper interface for testing XNodeIO functionality
     */
    private interface TestXNodeIO extends XNodeIO {
        @Override
        XNode toXNode();
        @Override
        XNodeIO newInstance(XNode node);
    }

    /**
     * Helper class for testing
     */
    private static class TestObject {
        private final XNode node;

        public TestObject(XNode node) {
            this.node = node;
        }

        public XNode getNode() {
            return node;
        }
    }
}