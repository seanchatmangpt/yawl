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

package org.yawlfoundation.yawl.integration.autonomous;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.autonomous.generators.XmlOutputGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for XmlOutputGenerator.
 * Chicago TDD style - test real XML generation and validation.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class XmlOutputGeneratorTest extends TestCase {

    private XmlOutputGenerator generator;

    public XmlOutputGeneratorTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        generator = new XmlOutputGenerator();
    }

    public void testConstructorWithDefaultFormatting() {
        XmlOutputGenerator gen = new XmlOutputGenerator();
        assertNotNull(gen);
    }

    public void testConstructorWithPrettyPrint() {
        XmlOutputGenerator gen = new XmlOutputGenerator(true);
        assertNotNull(gen);
    }

    public void testGenerateOutputWithSingleElement() {
        Map<String, String> elements = new HashMap<>();
        elements.put("result", "true");

        String xml = generator.generateOutput("TestTask", elements);

        assertNotNull(xml);
        assertTrue(xml.contains("<TestTask"));
        assertTrue(xml.contains("<result>true</result>"));
        assertTrue(xml.contains("</TestTask>"));
    }

    public void testGenerateOutputWithMultipleElements() {
        Map<String, String> elements = new HashMap<>();
        elements.put("orderId", "12345");
        elements.put("status", "approved");
        elements.put("amount", "1000.00");

        String xml = generator.generateOutput("ApproveOrder", elements);

        assertNotNull(xml);
        assertTrue(xml.contains("<ApproveOrder"));
        assertTrue(xml.contains("<orderId>12345</orderId>"));
        assertTrue(xml.contains("<status>approved</status>"));
        assertTrue(xml.contains("<amount>1000.00</amount>"));
    }

    public void testGenerateOutputWithEmptyValue() {
        Map<String, String> elements = new HashMap<>();
        elements.put("field1", "");
        elements.put("field2", "value");

        String xml = generator.generateOutput("Task", elements);

        assertNotNull(xml);
        assertTrue(xml.contains("<field1"));
        assertTrue(xml.contains("<field2>value</field2>"));
    }

    public void testGenerateOutputWithNullValue() {
        Map<String, String> elements = new HashMap<>();
        elements.put("field1", null);
        elements.put("field2", "value");

        String xml = generator.generateOutput("Task", elements);

        assertNotNull(xml);
        assertTrue(xml.contains("<field1"));
        assertTrue(xml.contains("<field2>value</field2>"));
    }

    public void testGenerateOutputRejectsNullRootName() {
        Map<String, String> elements = new HashMap<>();
        elements.put("field", "value");

        try {
            generator.generateOutput(null, elements);
            fail("Should reject null rootName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("rootName is required"));
        }
    }

    public void testGenerateOutputRejectsEmptyRootName() {
        Map<String, String> elements = new HashMap<>();

        try {
            generator.generateOutput("", elements);
            fail("Should reject empty rootName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("rootName is required"));
        }
    }

    public void testGenerateOutputRejectsNullElements() {
        try {
            generator.generateOutput("Task", null);
            fail("Should reject null elements");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("elements cannot be null"));
        }
    }

    public void testGenerateApprovalOutputTrue() {
        String xml = generator.generateApprovalOutput("ApproveOrder", true);

        assertNotNull(xml);
        assertTrue(xml.contains("<ApproveOrder"));
        assertTrue(xml.contains("<Approved>true</Approved>"));
        assertTrue(xml.contains("</ApproveOrder>"));
    }

    public void testGenerateApprovalOutputFalse() {
        String xml = generator.generateApprovalOutput("RejectOrder", false);

        assertNotNull(xml);
        assertTrue(xml.contains("<RejectOrder"));
        assertTrue(xml.contains("<Approved>false</Approved>"));
        assertTrue(xml.contains("</RejectOrder>"));
    }

    public void testGenerateApprovalOutputRejectsNullRoot() {
        try {
            generator.generateApprovalOutput(null, true);
            fail("Should reject null rootName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("rootName is required"));
        }
    }

    public void testGenerateNestedOutput() {
        Map<String, String> childElements = new HashMap<>();
        childElements.put("street", "123 Main St");
        childElements.put("city", "Springfield");
        childElements.put("zip", "12345");

        String xml = generator.generateNestedOutput("Order", "address", childElements);

        assertNotNull(xml);
        assertTrue(xml.contains("<Order"));
        assertTrue(xml.contains("<address"));
        assertTrue(xml.contains("<street>123 Main St</street>"));
        assertTrue(xml.contains("<city>Springfield</city>"));
        assertTrue(xml.contains("<zip>12345</zip>"));
        assertTrue(xml.contains("</address>"));
        assertTrue(xml.contains("</Order>"));
    }

    public void testGenerateNestedOutputRejectsNullParent() {
        Map<String, String> elements = new HashMap<>();
        elements.put("field", "value");

        try {
            generator.generateNestedOutput("Root", null, elements);
            fail("Should reject null parentName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("parentName is required"));
        }
    }

    public void testExtractXmlFromPlainXml() {
        String input = "<root><field>value</field></root>";

        String extracted = generator.extractXml(input);

        assertEquals(input, extracted);
    }

    public void testExtractXmlFromTextWithXml() {
        String input = "Here is some XML: <root><field>value</field></root> and more text";

        String extracted = generator.extractXml(input);

        assertEquals("<root><field>value</field></root>", extracted);
    }

    public void testExtractXmlFromMarkdownCodeBlock() {
        String input = "```xml\n<root><field>value</field></root>\n```";

        String extracted = generator.extractXml(input);

        assertEquals("<root><field>value</field></root>", extracted);
    }

    public void testExtractXmlRejectsNullText() {
        try {
            generator.extractXml(null);
            fail("Should reject null text");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("text cannot be empty"));
        }
    }

    public void testExtractXmlRejectsEmptyText() {
        try {
            generator.extractXml("");
            fail("Should reject empty text");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("text cannot be empty"));
        }
    }

    public void testExtractXmlRejectsTextWithoutXml() {
        try {
            generator.extractXml("This is just plain text without any XML");
            fail("Should reject text without XML");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No valid XML found"));
        }
    }

    public void testValidateXmlWithCorrectRoot() {
        String xml = "<TestTask><result>true</result></TestTask>";

        generator.validateXml(xml, "TestTask");
    }

    public void testValidateXmlRejectsWrongRoot() {
        String xml = "<WrongTask><result>true</result></WrongTask>";

        try {
            generator.validateXml(xml, "TestTask");
            fail("Should reject wrong root element");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Root element mismatch"));
            assertTrue(e.getMessage().contains("expected 'TestTask'"));
            assertTrue(e.getMessage().contains("found 'WrongTask'"));
        }
    }

    public void testValidateXmlRejectsMalformedXml() {
        String xml = "<root><unclosed>";

        try {
            generator.validateXml(xml, "root");
            fail("Should reject malformed XML");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid XML"));
        }
    }

    public void testValidateXmlWithNullExpectedRoot() {
        String xml = "<AnyRoot><field>value</field></AnyRoot>";

        generator.validateXml(xml, null);
    }

    public void testExtractAndValidateXml() {
        String input = "Here is the output: <TestTask><result>true</result></TestTask>";

        String xml = generator.extractAndValidateXml(input, "TestTask");

        assertNotNull(xml);
        assertTrue(xml.contains("<TestTask"));
        assertTrue(xml.contains("<result>true</result>"));
    }

    public void testExtractAndValidateXmlRejectsWrongRoot() {
        String input = "<WrongTask><result>true</result></WrongTask>";

        try {
            generator.extractAndValidateXml(input, "TestTask");
            fail("Should reject wrong root");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Root element mismatch"));
        }
    }

    public void testFormatXml() {
        String messyXml = "  <root>  <field>value</field>  </root>  ";

        String formatted = generator.formatXml(messyXml);

        assertNotNull(formatted);
        assertTrue(formatted.contains("<root"));
        assertTrue(formatted.contains("<field>value</field>"));
    }

    public void testFormatXmlRejectsMalformed() {
        String badXml = "<root><unclosed>";

        try {
            generator.formatXml(badXml);
            fail("Should reject malformed XML");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid XML"));
        }
    }

    public void testPrettyPrintFormatting() {
        XmlOutputGenerator prettyGen = new XmlOutputGenerator(true);
        Map<String, String> elements = new HashMap<>();
        elements.put("field1", "value1");
        elements.put("field2", "value2");

        String xml = prettyGen.generateOutput("Root", elements);

        assertNotNull(xml);
        assertTrue(xml.contains("Root"));
    }

    public void testGenerateOutputWithSpecialCharacters() {
        Map<String, String> elements = new HashMap<>();
        elements.put("field", "value<>&\"'");

        String xml = generator.generateOutput("Task", elements);

        assertNotNull(xml);
        assertFalse("Should escape special characters", xml.contains("value<>&\"'"));
    }
}
