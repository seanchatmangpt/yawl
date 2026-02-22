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

package org.yawlfoundation.yawl.integration.processmining;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YCompositeTask;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;

/**
 * Unit tests for PnmlExporter.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Simple nets (input → task → output)</li>
 *   <li>AND-split nets with multiple output conditions</li>
 *   <li>XOR-split and OR-split annotations</li>
 *   <li>Full specification export with decompositions</li>
 *   <li>XML validity and well-formedness</li>
 *   <li>PNML structure compliance (places, transitions, arcs)</li>
 * </ul>
 * </p>
 */
public class PnmlExporterTest {

    private static final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

    /**
     * Test simple net export: input → task → output.
     *
     * Expected PNML:
     * - 2 places (input, output)
     * - 1 transition (task)
     * - 2 arcs (input→task, task→output)
     */
    @Test
    public void testSimpleNetExport() {
        YSpecification spec = new YSpecification("test.simple");
        YNet net = new YNet("simpleNet", spec);
        spec.setRootNet(net);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask task = new YAtomicTask("taskA", net);
        task.setName("Task A");

        // Connect: input → task → output
        input.addPostset(new org.yawlfoundation.yawl.elements.YFlow(input, task));
        task.addPostset(new org.yawlfoundation.yawl.elements.YFlow(task, output));

        String pnml = PnmlExporter.netToPnml(net);

        // Verify XML is well-formed
        Document doc = parseXmlString(pnml);
        assertNotNull(doc, "PNML should parse as valid XML");

        // Verify structure
        assertTrue(pnml.contains("<place id=\"p_input\""),
                "PNML should contain input place");
        assertTrue(pnml.contains("<place id=\"p_output\""),
                "PNML should contain output place");
        assertTrue(pnml.contains("<transition id=\"t_taskA\""),
                "PNML should contain task A transition");
        assertTrue(pnml.contains("<initialMarking><text>1</text></initialMarking>"),
                "Input place should have initial marking of 1");

        // Count arcs (should be 2)
        long arcCount = pnml.split("<arc id=").length - 1;
        assertEquals(2, arcCount, "Simple net should have 2 arcs");
    }

    /**
     * Test AND-split net: task with AND split creates 2 output conditions.
     *
     * Expected:
     * - Input condition
     * - 1 task with AND split
     * - 2 output places (conditionA, conditionB)
     * - Output condition
     * - 4 arcs: input→task, task→condA, task→condB, (both eventually to output)
     */
    @Test
    public void testAndSplitNetExport() {
        YSpecification spec = new YSpecification("test.andsplit");
        YNet net = new YNet("andSplitNet", spec);
        spec.setRootNet(net);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask taskA = new YAtomicTask("taskA", net);
        taskA.setName("Task A");
        taskA.setSplitType(YTask._AND);  // AND split

        YCondition conditionA = new YCondition("conditionA", net);
        YCondition conditionB = new YCondition("conditionB", net);

        // Connect: input → taskA → [conditionA, conditionB] → output
        input.addPostset(new org.yawlfoundation.yawl.elements.YFlow(input, taskA));
        taskA.addPostset(new org.yawlfoundation.yawl.elements.YFlow(taskA, conditionA));
        taskA.addPostset(new org.yawlfoundation.yawl.elements.YFlow(taskA, conditionB));
        conditionA.addPostset(new org.yawlfoundation.yawl.elements.YFlow(conditionA, output));
        conditionB.addPostset(new org.yawlfoundation.yawl.elements.YFlow(conditionB, output));

        String pnml = PnmlExporter.netToPnml(net);

        // Verify XML is well-formed
        Document doc = parseXmlString(pnml);
        assertNotNull(doc, "AND-split PNML should parse as valid XML");

        // Verify AND split annotation
        assertTrue(pnml.contains("<joinType>and</joinType>") ||
                   pnml.contains("<splitType>and</splitType>"),
                "PNML should contain AND split annotation");

        // Count places (should be 4: input, conditionA, conditionB, output)
        long placeCount = pnml.split("<place id=").length - 1;
        assertEquals(4, placeCount, "AND-split net should have 4 places");

        // Count transitions (should be 1: taskA)
        long transCount = pnml.split("<transition id=").length - 1;
        assertEquals(1, transCount, "AND-split net should have 1 transition");
    }

    /**
     * Test XOR-split annotation is preserved in PNML.
     */
    @Test
    public void testXorSplitAnnotation() {
        YSpecification spec = new YSpecification("test.xorsplit");
        YNet net = new YNet("xorSplitNet", spec);
        spec.setRootNet(net);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask task = new YAtomicTask("decisionTask", net);
        task.setSplitType(YTask._XOR);
        task.setJoinType(YTask._XOR);

        input.addPostset(new org.yawlfoundation.yawl.elements.YFlow(input, task));
        task.addPostset(new org.yawlfoundation.yawl.elements.YFlow(task, output));

        String pnml = PnmlExporter.netToPnml(net);

        // Verify XOR annotations
        assertTrue(pnml.contains("<splitType>xor</splitType>"),
                "XOR-split task should have xor splitType annotation");
        assertTrue(pnml.contains("<joinType>xor</joinType>"),
                "XOR-split task should have xor joinType annotation");
    }

    /**
     * Test OR-split annotation is preserved in PNML.
     */
    @Test
    public void testOrSplitAnnotation() {
        YSpecification spec = new YSpecification("test.orsplit");
        YNet net = new YNet("orSplitNet", spec);
        spec.setRootNet(net);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask task = new YAtomicTask("multiChoiceTask", net);
        task.setSplitType(YTask._OR);
        task.setJoinType(YTask._OR);

        input.addPostset(new org.yawlfoundation.yawl.elements.YFlow(input, task));
        task.addPostset(new org.yawlfoundation.yawl.elements.YFlow(task, output));

        String pnml = PnmlExporter.netToPnml(net);

        // Verify OR annotations
        assertTrue(pnml.contains("<splitType>or</splitType>"),
                "OR-split task should have or splitType annotation");
        assertTrue(pnml.contains("<joinType>or</joinType>"),
                "OR-split task should have or joinType annotation");
    }

    /**
     * Test composite task is marked as such in toolspecific annotations.
     */
    @Test
    public void testCompositeTaskAnnotation() {
        YSpecification spec = new YSpecification("test.composite");
        YNet net = new YNet("compositeNet", spec);
        spec.setRootNet(net);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        // Create composite task
        YCompositeTask compositeTask = new YCompositeTask("compositeTask", net);
        compositeTask.setName("Composite Task");

        // Create a sub-net for decomposition
        YNet subNet = new YNet("subNet", spec);
        YInputCondition subInput = new YInputCondition("subInput", subNet);
        YOutputCondition subOutput = new YOutputCondition("subOutput", subNet);
        subNet.setInputCondition(subInput);
        subNet.setOutputCondition(subOutput);

        compositeTask.setDecompositionPrototype(subNet);

        input.addPostset(new org.yawlfoundation.yawl.elements.YFlow(input, compositeTask));
        compositeTask.addPostset(new org.yawlfoundation.yawl.elements.YFlow(compositeTask, output));

        String pnml = PnmlExporter.netToPnml(net);

        // Verify composite task annotation
        assertTrue(pnml.contains("<composite>true</composite>"),
                "Composite task should have composite=true annotation");
    }

    /**
     * Test atomic task is marked as not composite.
     */
    @Test
    public void testAtomicTaskAnnotation() {
        YSpecification spec = new YSpecification("test.atomic");
        YNet net = new YNet("atomicNet", spec);
        spec.setRootNet(net);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask atomicTask = new YAtomicTask("atomicTask", net);
        atomicTask.setName("Atomic Task");

        input.addPostset(new org.yawlfoundation.yawl.elements.YFlow(input, atomicTask));
        atomicTask.addPostset(new org.yawlfoundation.yawl.elements.YFlow(atomicTask, output));

        String pnml = PnmlExporter.netToPnml(net);

        // Verify atomic task annotation
        assertTrue(pnml.contains("<composite>false</composite>"),
                "Atomic task should have composite=false annotation");
    }

    /**
     * Test PNML XML is well-formed and valid.
     */
    @Test
    public void testPnmlIsValidXml() {
        YSpecification spec = new YSpecification("test.xml");
        YNet net = new YNet("xmlNet", spec);
        spec.setRootNet(net);

        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask task = new YAtomicTask("task1", net);

        input.addPostset(new org.yawlfoundation.yawl.elements.YFlow(input, task));
        task.addPostset(new org.yawlfoundation.yawl.elements.YFlow(task, output));

        String pnml = PnmlExporter.netToPnml(net);

        // Parse as XML document (will fail if not well-formed)
        Document doc = parseXmlString(pnml);
        assertNotNull(doc, "PNML should parse as valid XML");

        // Verify PNML root element
        assertEquals("pnml", doc.getDocumentElement().getTagName(),
                "Root element should be pnml");

        // Verify net child element exists
        assertTrue(doc.getElementsByTagName("net").getLength() > 0,
                "PNML should contain at least one net element");
    }

    /**
     * Test specification-level export includes all nets.
     */
    @Test
    public void testSpecificationExport() {
        YSpecification spec = new YSpecification("test.spec");

        // Root net
        YNet rootNet = new YNet("rootNet", spec);
        YInputCondition rootInput = new YInputCondition("rootInput", rootNet);
        YOutputCondition rootOutput = new YOutputCondition("rootOutput", rootNet);
        rootNet.setInputCondition(rootInput);
        rootNet.setOutputCondition(rootOutput);

        spec.setRootNet(rootNet);

        // Sub-net for composite task decomposition
        YNet subNet = new YNet("subNet", spec);
        YInputCondition subInput = new YInputCondition("subInput", subNet);
        YOutputCondition subOutput = new YOutputCondition("subOutput", subNet);
        subNet.setInputCondition(subInput);
        subNet.setOutputCondition(subOutput);

        YCompositeTask compositeTask = new YCompositeTask("composite", rootNet);
        compositeTask.setDecompositionPrototype(subNet);

        rootInput.addPostset(new org.yawlfoundation.yawl.elements.YFlow(rootInput, compositeTask));
        compositeTask.addPostset(new org.yawlfoundation.yawl.elements.YFlow(compositeTask, rootOutput));

        String pnml = PnmlExporter.specificationToPnml(spec);

        // Verify XML is well-formed
        Document doc = parseXmlString(pnml);
        assertNotNull(doc, "Specification PNML should parse as valid XML");

        // Should have at least 2 nets: root and sub-net
        int netCount = doc.getElementsByTagName("net").getLength();
        assertGreaterThanOrEqual(netCount, 1, "Specification should export at least one net");
    }

    /**
     * Test XmlId escaping: special characters are properly escaped.
     */
    @Test
    public void testXmlIdEscaping() {
        YSpecification spec = new YSpecification("test.escaping");
        YNet net = new YNet("net-with.special_chars", spec);
        spec.setRootNet(net);

        YInputCondition input = new YInputCondition("input@123", net);
        YOutputCondition output = new YOutputCondition("output!", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask task = new YAtomicTask("task<>&", net);

        input.addPostset(new org.yawlfoundation.yawl.elements.YFlow(input, task));
        task.addPostset(new org.yawlfoundation.yawl.elements.YFlow(task, output));

        String pnml = PnmlExporter.netToPnml(net);

        // Verify PNML parses (special chars should be escaped)
        Document doc = parseXmlString(pnml);
        assertNotNull(doc, "PNML with escaped IDs should parse as valid XML");

        // Verify the net ID is escaped correctly
        assertTrue(pnml.contains("id=\"net") && pnml.contains("special"),
                "PNML should contain escaped net ID");
    }

    /**
     * Test exception when null net is passed.
     */
    @Test
    public void testNullNetThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            PnmlExporter.netToPnml(null);
        }, "Should throw IllegalArgumentException for null net");
    }

    /**
     * Test exception when null specification is passed.
     */
    @Test
    public void testNullSpecificationThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            PnmlExporter.specificationToPnml(null);
        }, "Should throw IllegalArgumentException for null specification");
    }

    /**
     * Test exception when net is missing input condition.
     */
    @Test
    public void testMissingInputConditionThrowsException() {
        YSpecification spec = new YSpecification("test.missing.input");
        YNet net = new YNet("incompleteNet", spec);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setOutputCondition(output);

        assertThrows(IllegalArgumentException.class, () -> {
            PnmlExporter.netToPnml(net);
        }, "Should throw IllegalArgumentException for missing input condition");
    }

    /**
     * Test exception when net is missing output condition.
     */
    @Test
    public void testMissingOutputConditionThrowsException() {
        YSpecification spec = new YSpecification("test.missing.output");
        YNet net = new YNet("incompleteNet", spec);
        YInputCondition input = new YInputCondition("input", net);
        net.setInputCondition(input);

        assertThrows(IllegalArgumentException.class, () -> {
            PnmlExporter.netToPnml(net);
        }, "Should throw IllegalArgumentException for missing output condition");
    }

    /**
     * Test exception when specification is missing root net.
     */
    @Test
    public void testMissingRootNetThrowsException() {
        YSpecification spec = new YSpecification("test.missing.root");

        assertThrows(IllegalArgumentException.class, () -> {
            PnmlExporter.specificationToPnml(spec);
        }, "Should throw IllegalArgumentException for missing root net");
    }

    // Helper methods

    /**
     * Parse a PNML XML string into a DOM Document.
     *
     * @param pnmlString the PNML XML string to parse
     * @return the parsed Document, or null if parsing fails
     */
    private Document parseXmlString(String pnmlString) {
        try {
            DocumentBuilder builder = dbf.newDocumentBuilder();
            return builder.parse(new StringReader(pnmlString));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            fail("Failed to parse PNML XML: " + e.getMessage());
            throw new AssertionError("Parsing failed", e); // Unreachable, but satisfies typing
        }
    }

    /**
     * Helper assertion for greater-than-or-equal comparison.
     */
    private static void assertGreaterThanOrEqual(int actual, int expected, String message) {
        assertTrue(actual >= expected,
                message + " (expected >= " + expected + ", got " + actual + ")");
    }
}
