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

import org.jdom2.Element;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.util.JDOMUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Template-based output generator for YAWL work items.
 *
 * Loads XML templates from files or configuration, substitutes variables
 * from work item data, and produces well-formed output. Provides a
 * lightweight alternative to AI-based output generation.
 *
 * Template syntax: ${variableName}
 * Built-in variables:
 * - ${taskName} - task name
 * - ${taskId} - task ID
 * - ${caseId} - case ID
 * - ${workItemId} - work item ID
 * - ${decompositionRoot} - task name with spaces replaced by underscores
 * - ${input.elementName} - value from input data element
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class TemplateOutputGenerator {

    private final Map<String, String> taskTemplates;
    private String defaultTemplate;

    /**
     * Create generator with default template.
     */
    public TemplateOutputGenerator() {
        this.taskTemplates = new HashMap<>();
        this.defaultTemplate = DEFAULT_TEMPLATE;
    }

    /**
     * Create generator with custom default template.
     * @param defaultTemplate template to use when no task-specific template exists
     */
    public TemplateOutputGenerator(String defaultTemplate) {
        if (defaultTemplate == null || defaultTemplate.isBlank()) {
            throw new IllegalArgumentException("defaultTemplate is required");
        }
        this.taskTemplates = new HashMap<>();
        this.defaultTemplate = defaultTemplate;
    }

    /**
     * Generate output for a work item using configured templates.
     *
     * @param workItem work item to generate output for
     * @return XML output string with variables substituted
     */
    public String generateOutput(WorkItemRecord workItem) {
        if (workItem == null) {
            throw new IllegalArgumentException("workItem is required");
        }

        var taskName = extractTaskName(workItem);
        var template = taskTemplates.getOrDefault(taskName, defaultTemplate);

        var variables = buildVariableMap(workItem);
        return substituteVariables(template, variables);
    }

    /**
     * Generate output using a specific template (bypasses task-specific templates).
     *
     * @param workItem work item providing data
     * @param template XML template with ${variable} placeholders
     * @return XML output string
     */
    public String generateOutput(WorkItemRecord workItem, String template) {
        if (workItem == null) {
            throw new IllegalArgumentException("workItem is required");
        }
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("template is required");
        }

        var variables = buildVariableMap(workItem);
        return substituteVariables(template, variables);
    }

    /**
     * Add a task-specific template.
     * @param taskName task name to match (exact match)
     * @param template XML template with ${variable} placeholders
     */
    public void addTaskTemplate(String taskName, String template) {
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("taskName is required");
        }
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("template is required");
        }
        taskTemplates.put(taskName.strip(), template);
    }

    /**
     * Load templates from a directory.
     * File naming: taskName.xml (e.g., Approve_Purchase_Order.xml)
     *
     * @param templateDir directory containing template files
     * @throws IOException if templates cannot be read
     */
    public void loadTemplatesFromDirectory(String templateDir) throws IOException {
        if (templateDir == null || templateDir.isBlank()) {
            throw new IllegalArgumentException("templateDir is required");
        }

        var dir = new java.io.File(templateDir);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("Template directory does not exist: " + templateDir);
        }

        var files = dir.listFiles((d, name) -> name.endsWith(".xml"));
        if (files == null) {
            throw new IOException("Cannot read template directory: " + templateDir);
        }

        for (var file : files) {
            var taskName = file.getName().replace(".xml", "");
            var template = readFile(file.getAbsolutePath());
            addTaskTemplate(taskName, template);
        }
    }

    /**
     * Load a single template from file.
     *
     * @param taskName task name for this template
     * @param filePath absolute path to template file
     * @throws IOException if file cannot be read
     */
    public void loadTemplateFromFile(String taskName, String filePath) throws IOException {
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("taskName is required");
        }
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }

        var template = readFile(filePath);
        addTaskTemplate(taskName, template);
    }

    /**
     * Set default template for tasks without specific templates.
     */
    public void setDefaultTemplate(String template) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("template is required");
        }
        this.defaultTemplate = template;
    }

    /**
     * Get all configured task templates.
     */
    public Map<String, String> getTaskTemplates() {
        return new HashMap<>(taskTemplates);
    }

    /**
     * Clear all task-specific templates.
     */
    public void clearTaskTemplates() {
        taskTemplates.clear();
    }

    private static String extractTaskName(WorkItemRecord workItem) {
        var taskName = workItem.getTaskName();
        if (taskName == null || taskName.isBlank()) {
            taskName = workItem.getTaskID();
        }
        return taskName;
    }

    private static Map<String, String> buildVariableMap(WorkItemRecord workItem) {
        var variables = new HashMap<String, String>();

        var taskName = extractTaskName(workItem);
        var taskId = workItem.getTaskID();
        var caseId = workItem.getCaseID();
        var decompositionRoot = taskName.replace(' ', '_');

        variables.put("taskName", taskName);
        variables.put("taskId", taskId);
        variables.put("caseId", caseId);
        variables.put("workItemId", workItem.getID());
        variables.put("decompositionRoot", decompositionRoot);

        extractInputVariables(workItem, variables);

        return variables;
    }

    private static void extractInputVariables(WorkItemRecord workItem,
                                             Map<String, String> variables) {
        try {
            var dataList = workItem.getDataList();
            if (dataList == null) {
                return;
            }

            for (var child : dataList.getChildren()) {
                var name = child.getName();
                var value = child.getTextTrim();
                if (!value.isEmpty()) {
                    variables.put("input." + name, value);
                    variables.put(name, value);
                }

                extractNestedElements(child, "input." + name, variables);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to extract input variables from work item " + workItem.getID(), e);
        }
    }

    private static void extractNestedElements(Element parent,
                                             String prefix,
                                             Map<String, String> variables) {
        for (var child : parent.getChildren()) {
            var name = child.getName();
            var fullPath = prefix + "." + name;
            var value = child.getTextTrim();

            if (!value.isEmpty()) {
                variables.put(fullPath, value);
            }

            if (!child.getChildren().isEmpty()) {
                extractNestedElements(child, fullPath, variables);
            }
        }
    }

    private static String substituteVariables(String template,
                                             Map<String, String> variables) {
        var result = template;

        for (var entry : variables.entrySet()) {
            var placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, escapeXml(entry.getValue()));
        }

        result = removeUnresolvedVariables(result);

        return result;
    }

    private static String removeUnresolvedVariables(String text) {
        return text.replaceAll("\\$\\{[^}]+\\}", "");
    }

    private static String escapeXml(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private static String readFile(String filePath) throws IOException {
        var content = new StringBuilder();
        try (var reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private static final String DEFAULT_TEMPLATE =
        "<${decompositionRoot}><result>true</result></${decompositionRoot}>";
}
