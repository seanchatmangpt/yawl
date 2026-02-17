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

package org.yawlfoundation.yawl.tooling.intellij.highlighting;

import java.util.Set;

/**
 * Syntax highlighting token classifier for YAWL specification XML files.
 *
 * Categorises tokens extracted from a YAWL XML document into semantic highlight
 * groups that an IntelliJ IDEA plugin's lexer-based highlighter maps to editor
 * colour schemes.
 *
 * This class is intentionally decoupled from the IntelliJ Platform SDK so that
 * it can be tested without an IDE process. The IntelliJ plugin's
 * {@code YawlXmlSyntaxHighlighter} adapter calls {@link #classify(String, TokenContext)}
 * to get the {@link TokenType} and then maps it to an
 * {@code com.intellij.openapi.editor.colors.TextAttributesKey}.
 *
 * Token classification rules:
 * <ul>
 *   <li>YAWL_STRUCTURAL_ELEMENT — root/container elements:
 *       specificationSet, specification, decomposition, processControlElements</li>
 *   <li>YAWL_TASK — task-defining elements: task</li>
 *   <li>YAWL_CONDITION — condition elements: inputCondition, outputCondition, condition</li>
 *   <li>YAWL_FLOW — flow elements: flowsInto, nextElementRef, predicate</li>
 *   <li>YAWL_JOIN_SPLIT_KEYWORD — join/split code attribute values: and, xor, or</li>
 *   <li>YAWL_ATTRIBUTE_KEY — important YAWL attribute names: id, uri, isRootNet, code, ordering</li>
 *   <li>XML_TAG — all other XML element names</li>
 *   <li>XML_ATTRIBUTE — all other XML attribute names</li>
 *   <li>XML_ATTRIBUTE_VALUE — all attribute values not otherwise classified</li>
 *   <li>XML_COMMENT — XML comment content</li>
 *   <li>XML_CDATA — CDATA sections</li>
 * </ul>
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public final class YawlSyntaxHighlighter {

    /**
     * Semantic token types used for IntelliJ text attribute key mapping.
     */
    public enum TokenType {
        /** Root and container structural elements */
        YAWL_STRUCTURAL_ELEMENT,
        /** Workflow task element */
        YAWL_TASK,
        /** Input/output/intermediate condition elements */
        YAWL_CONDITION,
        /** Flow arc elements */
        YAWL_FLOW,
        /** Join/split semantic keyword values (and, xor, or) */
        YAWL_JOIN_SPLIT_KEYWORD,
        /** Important YAWL attribute names */
        YAWL_ATTRIBUTE_KEY,
        /** Generic XML element name */
        XML_TAG,
        /** Generic XML attribute name */
        XML_ATTRIBUTE,
        /** Generic XML attribute value */
        XML_ATTRIBUTE_VALUE,
        /** XML comment */
        XML_COMMENT,
        /** XML CDATA section */
        XML_CDATA,
        /** Whitespace */
        WHITESPACE
    }

    /** Context in which a token appears */
    public enum TokenContext {
        /** Inside an opening element tag: {@code <tag ...>} */
        ELEMENT_NAME,
        /** As an attribute name */
        ATTRIBUTE_NAME,
        /** As an attribute value (the text between quotes) */
        ATTRIBUTE_VALUE,
        /** Text content of an element */
        ELEMENT_CONTENT
    }

    // YAWL-specific element name sets
    private static final Set<String> STRUCTURAL_ELEMENTS = Set.of(
            "specificationSet", "specification", "decomposition",
            "processControlElements", "metaData");

    private static final Set<String> TASK_ELEMENTS = Set.of("task");

    private static final Set<String> CONDITION_ELEMENTS = Set.of(
            "inputCondition", "outputCondition", "condition");

    private static final Set<String> FLOW_ELEMENTS = Set.of(
            "flowsInto", "nextElementRef", "predicate", "isDefaultFlow");

    // YAWL-specific attribute name set
    private static final Set<String> YAWL_ATTRIBUTE_KEYS = Set.of(
            "id", "uri", "isRootNet", "code", "ordering", "xsi:type");

    // Join/split keyword values
    private static final Set<String> JOIN_SPLIT_KEYWORDS = Set.of("and", "xor", "or");

    private YawlSyntaxHighlighter() {
        throw new UnsupportedOperationException("YawlSyntaxHighlighter is a utility class");
    }

    /**
     * Classify a single token into a {@link TokenType}.
     *
     * @param token   the raw token text (trimmed, not null)
     * @param context the syntactic context in which the token appears
     * @return the semantic {@link TokenType} for this token
     */
    public static TokenType classify(String token, TokenContext context) {
        if (token == null || token.isBlank()) {
            return TokenType.WHITESPACE;
        }

        return switch (context) {
            case ELEMENT_NAME -> classifyElementName(token);
            case ATTRIBUTE_NAME -> classifyAttributeName(token);
            case ATTRIBUTE_VALUE -> classifyAttributeValue(token);
            case ELEMENT_CONTENT -> TokenType.XML_ATTRIBUTE_VALUE;
        };
    }

    private static TokenType classifyElementName(String name) {
        if (STRUCTURAL_ELEMENTS.contains(name)) return TokenType.YAWL_STRUCTURAL_ELEMENT;
        if (TASK_ELEMENTS.contains(name))       return TokenType.YAWL_TASK;
        if (CONDITION_ELEMENTS.contains(name))  return TokenType.YAWL_CONDITION;
        if (FLOW_ELEMENTS.contains(name))       return TokenType.YAWL_FLOW;
        return TokenType.XML_TAG;
    }

    private static TokenType classifyAttributeName(String name) {
        if (YAWL_ATTRIBUTE_KEYS.contains(name)) return TokenType.YAWL_ATTRIBUTE_KEY;
        return TokenType.XML_ATTRIBUTE;
    }

    private static TokenType classifyAttributeValue(String value) {
        if (JOIN_SPLIT_KEYWORDS.contains(value)) return TokenType.YAWL_JOIN_SPLIT_KEYWORD;
        return TokenType.XML_ATTRIBUTE_VALUE;
    }

    /**
     * Return a short description of the token type for use in the
     * IntelliJ Color Scheme settings panel label.
     *
     * @param type the token type
     * @return human-readable description
     */
    public static String displayName(TokenType type) {
        return switch (type) {
            case YAWL_STRUCTURAL_ELEMENT -> "YAWL Structural Elements";
            case YAWL_TASK               -> "YAWL Task Elements";
            case YAWL_CONDITION          -> "YAWL Condition Elements";
            case YAWL_FLOW               -> "YAWL Flow Elements";
            case YAWL_JOIN_SPLIT_KEYWORD -> "YAWL Join/Split Keywords";
            case YAWL_ATTRIBUTE_KEY      -> "YAWL Attribute Names";
            case XML_TAG                 -> "XML Element Name";
            case XML_ATTRIBUTE           -> "XML Attribute Name";
            case XML_ATTRIBUTE_VALUE     -> "XML Attribute Value";
            case XML_COMMENT             -> "XML Comment";
            case XML_CDATA               -> "XML CDATA";
            case WHITESPACE              -> "Whitespace";
        };
    }
}
