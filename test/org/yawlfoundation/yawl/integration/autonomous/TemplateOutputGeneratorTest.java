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
import org.jdom2.Element;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.generators.TemplateOutputGenerator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Tests for TemplateOutputGenerator.
 * Chicago TDD style - test real template-based output generation.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class TemplateOutputGeneratorTest extends TestCase {

    private TemplateOutputGenerator generator;

    public TemplateOutputGeneratorTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        generator = new TemplateOutputGenerator();
    }

    public void testConstructorWithDefaults() {
        TemplateOutputGenerator gen = new TemplateOutputGenerator();
        assertNotNull(gen);
    }

    public void testConstructorWithCustomDefault() {
        String customTemplate = "<custom>${taskName}</custom>";
        TemplateOutputGenerator gen = new TemplateOutputGenerator(customTemplate);
        assertNotNull(gen);
    }

    public void testConstructorRejectsNullTemplate() {
        try {
            new TemplateOutputGenerator(null);
            fail("Should reject null template");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("defaultTemplate is required"));
        }
    }

    public void testConstructorRejectsEmptyTemplate() {
        try {
            new TemplateOutputGenerator("");
            fail("Should reject empty template");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("defaultTemplate is required"));
        }
    }

    public void testGenerateOutputWithDefaultTemplate() {
        WorkItemRecord wir = createWorkItem("wi-1", "ApproveOrder", "case-1");

        String output = generator.generateOutput(wir);

        assertNotNull(output);
        assertTrue(output.contains("<ApproveOrder>"));
        assertTrue(output.contains("<result>true</result>"));
        assertTrue(output.contains("</ApproveOrder>"));
    }

    public void testGenerateOutputSubstitutesTaskName() {
        generator.setDefaultTemplate("<${taskName}><status>complete</status></${taskName}>");

        WorkItemRecord wir = createWorkItem("wi-1", "ProcessOrder", "case-1");

        String output = generator.generateOutput(wir);

        assertTrue(output.contains("<ProcessOrder>"));
        assertTrue(output.contains("</ProcessOrder>"));
        assertTrue(output.contains("<status>complete</status>"));
    }

    public void testGenerateOutputSubstitutesTaskId() {
        generator.setDefaultTemplate("<root><taskId>${taskId}</taskId></root>");

        WorkItemRecord wir = createWorkItem("wi-1", "Task", "case-1");
        wir.setTaskID("task-123");

        String output = generator.generateOutput(wir);

        assertTrue(output.contains("<taskId>task-123</taskId>"));
    }

    public void testGenerateOutputSubstitutesCaseId() {
        generator.setDefaultTemplate("<root><caseId>${caseId}</caseId></root>");

        WorkItemRecord wir = createWorkItem("wi-1", "Task", "case-456");

        String output = generator.generateOutput(wir);

        assertTrue(output.contains("<caseId>case-456</caseId>"));
    }

    public void testGenerateOutputSubstitutesWorkItemId() {
        generator.setDefaultTemplate("<root><wiId>${workItemId}</wiId></root>");

        WorkItemRecord wir = createWorkItem("wi-789", "Task", "case-1");

        String output = generator.generateOutput(wir);

        assertTrue(output.contains("<wiId>wi-789</wiId>"));
    }

    public void testGenerateOutputSubstitutesDecompositionRoot() {
        generator.setDefaultTemplate("<${decompositionRoot}><done>true</done></${decompositionRoot}>");

        WorkItemRecord wir = createWorkItem("wi-1", "Approve Purchase Order", "case-1");

        String output = generator.generateOutput(wir);

        assertTrue(output.contains("<Approve_Purchase_Order>"));
        assertTrue(output.contains("</Approve_Purchase_Order>"));
    }

    public void testGenerateOutputSubstitutesInputVariables() {
        generator.setDefaultTemplate("<root><orderId>${input.orderId}</orderId></root>");

        WorkItemRecord wir = createWorkItemWithData("wi-1", "Task", "case-1");
        Element dataList = new Element("data");
        Element orderIdElement = new Element("orderId");
        orderIdElement.setText("12345");
        dataList.addContent(orderIdElement);
        wir.setDataList(dataList);

        String output = generator.generateOutput(wir);

        assertTrue(output.contains("<orderId>12345</orderId>"));
    }

    public void testGenerateOutputRemovesUnresolvedVariables() {
        generator.setDefaultTemplate("<root><field1>${existing}</field1><field2>${missing}</field2></root>");

        WorkItemRecord wir = createWorkItemWithData("wi-1", "Task", "case-1");
        Element dataList = new Element("data");
        Element existingElement = new Element("existing");
        existingElement.setText("value");
        dataList.addContent(existingElement);
        wir.setDataList(dataList);

        String output = generator.generateOutput(wir);

        assertTrue(output.contains("<field1>value</field1>"));
        assertTrue(output.contains("<field2></field2>"));
    }

    public void testGenerateOutputEscapesXmlSpecialCharacters() {
        generator.setDefaultTemplate("<root><text>${input.text}</text></root>");

        WorkItemRecord wir = createWorkItemWithData("wi-1", "Task", "case-1");
        Element dataList = new Element("data");
        Element textElement = new Element("text");
        textElement.setText("<>&\"'");
        dataList.addContent(textElement);
        wir.setDataList(dataList);

        String output = generator.generateOutput(wir);

        assertFalse("Should escape special chars", output.contains("<>&\"'"));
        assertTrue("Should contain escaped ampersand", output.contains("&amp;"));
        assertTrue("Should contain escaped less-than", output.contains("&lt;"));
    }

    public void testGenerateOutputWithExplicitTemplate() {
        String template = "<CustomRoot><value>constant</value></CustomRoot>";

        WorkItemRecord wir = createWorkItem("wi-1", "Task", "case-1");

        String output = generator.generateOutput(wir, template);

        assertTrue(output.contains("<CustomRoot>"));
        assertTrue(output.contains("<value>constant</value>"));
    }

    public void testGenerateOutputRejectsNullWorkItem() {
        try {
            generator.generateOutput(null);
            fail("Should reject null workItem");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("workItem is required"));
        }
    }

    public void testGenerateOutputWithTemplateRejectsNullTemplate() {
        WorkItemRecord wir = createWorkItem("wi-1", "Task", "case-1");

        try {
            generator.generateOutput(wir, null);
            fail("Should reject null template");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("template is required"));
        }
    }

    public void testAddTaskTemplate() {
        String template = "<ApproveOrder><approved>true</approved></ApproveOrder>";
        generator.addTaskTemplate("ApproveOrder", template);

        WorkItemRecord wir = createWorkItem("wi-1", "ApproveOrder", "case-1");

        String output = generator.generateOutput(wir);

        assertTrue(output.contains("<approved>true</approved>"));
    }

    public void testAddTaskTemplateRejectsNullName() {
        try {
            generator.addTaskTemplate(null, "<template/>");
            fail("Should reject null taskName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("taskName is required"));
        }
    }

    public void testSetDefaultTemplate() {
        String newDefault = "<NewDefault><field>value</field></NewDefault>";
        generator.setDefaultTemplate(newDefault);

        WorkItemRecord wir = createWorkItem("wi-1", "UnknownTask", "case-1");

        String output = generator.generateOutput(wir);

        assertTrue(output.contains("<NewDefault>"));
        assertTrue(output.contains("<field>value</field>"));
    }

    public void testGetTaskTemplates() {
        generator.addTaskTemplate("Task1", "<t1/>");
        generator.addTaskTemplate("Task2", "<t2/>");

        Map<String, String> templates = generator.getTaskTemplates();

        assertEquals(2, templates.size());
        assertTrue(templates.containsKey("Task1"));
        assertTrue(templates.containsKey("Task2"));
    }

    public void testClearTaskTemplates() {
        generator.addTaskTemplate("Task1", "<t1/>");
        generator.addTaskTemplate("Task2", "<t2/>");
        assertEquals(2, generator.getTaskTemplates().size());

        generator.clearTaskTemplates();

        assertEquals(0, generator.getTaskTemplates().size());
    }

    public void testLoadTemplateFromFile() throws Exception {
        File tempFile = File.createTempFile("template", ".xml");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("<ApproveOrder><approved>${input.approved}</approved></ApproveOrder>");
        }

        generator.loadTemplateFromFile("ApproveOrder", tempFile.getAbsolutePath());

        assertTrue(generator.getTaskTemplates().containsKey("ApproveOrder"));
    }

    public void testLoadTemplateFromFileRejectsNullTaskName() {
        try {
            generator.loadTemplateFromFile(null, "/path/to/file");
            fail("Should reject null taskName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("taskName is required"));
        } catch (IOException e) {
            fail("Should throw IllegalArgumentException, not IOException");
        }
    }

    public void testLoadTemplateFromFileRejectsNullPath() {
        try {
            generator.loadTemplateFromFile("Task", null);
            fail("Should reject null filePath");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("filePath is required"));
        } catch (IOException e) {
            fail("Should throw IllegalArgumentException, not IOException");
        }
    }

    public void testLoadTemplatesFromDirectory() throws Exception {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "templates-" + System.currentTimeMillis());
        tempDir.mkdir();
        tempDir.deleteOnExit();

        File template1 = new File(tempDir, "Task1.xml");
        File template2 = new File(tempDir, "Task2.xml");
        template1.deleteOnExit();
        template2.deleteOnExit();

        try (FileWriter writer = new FileWriter(template1)) {
            writer.write("<Task1><result>true</result></Task1>");
        }
        try (FileWriter writer = new FileWriter(template2)) {
            writer.write("<Task2><result>false</result></Task2>");
        }

        generator.loadTemplatesFromDirectory(tempDir.getAbsolutePath());

        Map<String, String> templates = generator.getTaskTemplates();
        assertTrue(templates.containsKey("Task1"));
        assertTrue(templates.containsKey("Task2"));
    }

    public void testLoadTemplatesFromDirectoryRejectsNullPath() {
        try {
            generator.loadTemplatesFromDirectory(null);
            fail("Should reject null templateDir");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("templateDir is required"));
        } catch (IOException e) {
            fail("Should throw IllegalArgumentException, not IOException");
        }
    }

    public void testNestedInputVariables() {
        generator.setDefaultTemplate("<root><street>${input.address.street}</street></root>");

        WorkItemRecord wir = createWorkItemWithData("wi-1", "Task", "case-1");
        Element dataList = new Element("data");
        Element address = new Element("address");
        Element street = new Element("street");
        street.setText("123 Main St");
        address.addContent(street);
        dataList.addContent(address);
        wir.setDataList(dataList);

        String output = generator.generateOutput(wir);

        assertTrue(output.contains("<street>123 Main St</street>"));
    }

    public void testTaskSpecificTemplateOverridesDefault() {
        generator.setDefaultTemplate("<default/>");
        generator.addTaskTemplate("SpecificTask", "<specific/>");

        WorkItemRecord defaultTask = createWorkItem("wi-1", "DefaultTask", "case-1");
        WorkItemRecord specificTask = createWorkItem("wi-2", "SpecificTask", "case-1");

        String defaultOutput = generator.generateOutput(defaultTask);
        String specificOutput = generator.generateOutput(specificTask);

        assertTrue(defaultOutput.contains("<default/>"));
        assertTrue(specificOutput.contains("<specific/>"));
    }

    public void testWhitespaceInTaskNamesForTemplates() {
        generator.addTaskTemplate("  Task1  ", "<t1/>");

        assertTrue(generator.getTaskTemplates().containsKey("Task1"));
    }

    private WorkItemRecord createWorkItem(String id, String taskName, String caseId) {
        WorkItemRecord wir = new WorkItemRecord();
        wir.setUniqueID(id);
        wir.setTaskName(taskName);
        wir.setCaseID(caseId);
        wir.setTaskID("task-" + id);
        return wir;
    }

    private WorkItemRecord createWorkItemWithData(String id, String taskName, String caseId) {
        WorkItemRecord wir = createWorkItem(id, taskName, caseId);
        wir.setDataList(new Element("data"));
        return wir;
    }
}
