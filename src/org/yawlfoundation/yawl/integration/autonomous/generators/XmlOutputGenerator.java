/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous.generators;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import java.io.StringReader;
import java.util.Map;

/**
 * XML output generator for YAWL work items.
 *
 * Provides utilities to construct well-formed XML output from structured
 * data, validate XML, and format output for YAWL engine consumption.
 * Extracted from DecisionWorkflow logic.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class XmlOutputGenerator {

    private final XMLOutputter outputter;
    private final SAXBuilder saxBuilder;

    /**
     * Create generator with default formatting (compact).
     */
    public XmlOutputGenerator() {
        this(false);
    }

    /**
     * Create generator with optional pretty printing.
     * @param prettyPrint if true, format output with indentation
     */
    public XmlOutputGenerator(boolean prettyPrint) {
        Format format = prettyPrint ? Format.getPrettyFormat() : Format.getCompactFormat();
        this.outputter = new XMLOutputter(format);
        this.saxBuilder = new SAXBuilder();
    }

    /**
     * Generate XML output with root element and child elements.
     *
     * @param rootName root element name (typically decomposition name)
     * @param elements map of element name to value
     * @return well-formed XML string
     */
    public String generateOutput(String rootName, Map<String, String> elements) {
        if (rootName == null || rootName.isBlank()) {
            throw new IllegalArgumentException("rootName is required");
        }
        if (elements == null) {
            throw new IllegalArgumentException("elements cannot be null");
        }

        Element root = new Element(rootName);
        for (Map.Entry<String, String> entry : elements.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name != null && !name.isBlank()) {
                Element child = new Element(name);
                if (value != null) {
                    child.setText(value);
                }
                root.addContent(child);
            }
        }

        return outputter.outputString(root);
    }

    /**
     * Generate simple approval output.
     * Creates XML: &lt;rootName&gt;&lt;Approved&gt;true&lt;/Approved&gt;&lt;/rootName&gt;
     *
     * @param rootName root element name
     * @param approved approval status
     * @return XML string
     */
    public String generateApprovalOutput(String rootName, boolean approved) {
        if (rootName == null || rootName.isBlank()) {
            throw new IllegalArgumentException("rootName is required");
        }

        Element root = new Element(rootName);
        Element approvedElement = new Element("Approved");
        approvedElement.setText(String.valueOf(approved));
        root.addContent(approvedElement);

        return outputter.outputString(root);
    }

    /**
     * Generate output with nested structure.
     *
     * @param rootName root element name
     * @param parentName parent element name (child of root)
     * @param childElements map of child element names to values
     * @return XML string with nested structure
     */
    public String generateNestedOutput(String rootName,
                                       String parentName,
                                       Map<String, String> childElements) {
        if (rootName == null || rootName.isBlank()) {
            throw new IllegalArgumentException("rootName is required");
        }
        if (parentName == null || parentName.isBlank()) {
            throw new IllegalArgumentException("parentName is required");
        }
        if (childElements == null) {
            throw new IllegalArgumentException("childElements cannot be null");
        }

        Element root = new Element(rootName);
        Element parent = new Element(parentName);

        for (Map.Entry<String, String> entry : childElements.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name != null && !name.isBlank()) {
                Element child = new Element(name);
                if (value != null) {
                    child.setText(value);
                }
                parent.addContent(child);
            }
        }

        root.addContent(parent);
        return outputter.outputString(root);
    }

    /**
     * Validate and extract XML from potentially noisy text.
     * Handles AI responses that may include explanatory text around XML.
     *
     * @param text text containing XML
     * @param expectedRoot expected root element name (for validation)
     * @return extracted and validated XML
     * @throws IllegalArgumentException if no valid XML found
     */
    public String extractAndValidateXml(String text, String expectedRoot) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        if (expectedRoot == null || expectedRoot.isBlank()) {
            throw new IllegalArgumentException("expectedRoot is required");
        }

        String xml = extractXml(text);
        validateXml(xml, expectedRoot);
        return xml;
    }

    /**
     * Extract XML from text (finds first &lt; to last &gt;).
     *
     * @param text text containing XML
     * @return extracted XML string
     * @throws IllegalArgumentException if no XML found
     */
    public String extractXml(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text cannot be empty");
        }

        String trimmed = text.strip();
        int start = trimmed.indexOf("<");
        int end = trimmed.lastIndexOf(">");

        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("No valid XML found in text");
        }

        return trimmed.substring(start, end + 1);
    }

    /**
     * Validate XML is well-formed and has expected root element.
     *
     * @param xml XML string to validate
     * @param expectedRoot expected root element name (null to skip check)
     * @throws IllegalArgumentException if XML is invalid
     */
    public void validateXml(String xml, String expectedRoot) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("xml cannot be empty");
        }

        try {
            Document doc = saxBuilder.build(new StringReader(xml));
            Element root = doc.getRootElement();

            if (expectedRoot != null && !expectedRoot.isBlank()) {
                String actualRoot = root.getName();
                if (!actualRoot.equals(expectedRoot)) {
                    throw new IllegalArgumentException(
                        "Root element mismatch: expected '" + expectedRoot +
                        "' but found '" + actualRoot + "'");
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid XML: " + e.getMessage(), e);
        }
    }

    /**
     * Format XML string (normalize whitespace and optionally pretty print).
     *
     * @param xml XML string to format
     * @return formatted XML
     * @throws IllegalArgumentException if XML is invalid
     */
    public String formatXml(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("xml cannot be empty");
        }

        try {
            Document doc = saxBuilder.build(new StringReader(xml));
            return outputter.outputString(doc.getRootElement());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid XML: " + e.getMessage(), e);
        }
    }
}
