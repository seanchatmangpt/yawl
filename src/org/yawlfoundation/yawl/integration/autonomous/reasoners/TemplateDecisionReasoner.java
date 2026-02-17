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

package org.yawlfoundation.yawl.integration.autonomous.reasoners;

import java.util.HashMap;
import java.util.Map;

import org.jdom2.Element;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;

/**
 * Template-based decision reasoner for autonomous agents.
 *
 * Uses string templates (simple variable substitution) to generate
 * work item output. Lightweight alternative to AI-based reasoning
 * for deterministic, rule-based output generation.
 *
 * Template syntax: ${variableName}
 * Built-in variables:
 * - ${taskName} - task name
 * - ${taskId} - task ID
 * - ${caseId} - case ID
 * - ${workItemId} - work item ID (caseId:taskId)
 * - ${decompositionRoot} - task name with spaces replaced by underscores
 *
 * Additional variables can be extracted from input data using XPath-like notation.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class TemplateDecisionReasoner implements DecisionReasoner {

    private final Map<String, String> taskTemplates;
    private String defaultTemplate;

    /**
     * Create reasoner with default template.
     * Default: &lt;${decompositionRoot}&gt;&lt;result&gt;true&lt;/result&gt;&lt;/${decompositionRoot}&gt;
     */
    public TemplateDecisionReasoner() {
        this.taskTemplates = new HashMap<>();
        this.defaultTemplate = DEFAULT_TEMPLATE;
    }

    /**
     * Create reasoner with custom default template.
     * @param defaultTemplate template to use when no task-specific template exists
     */
    public TemplateDecisionReasoner(String defaultTemplate) {
        if (defaultTemplate == null || defaultTemplate.trim().isEmpty()) {
            throw new IllegalArgumentException("defaultTemplate is required");
        }
        this.taskTemplates = new HashMap<>();
        this.defaultTemplate = defaultTemplate;
    }

    /**
     * Add a task-specific template.
     * @param taskName task name to match (exact match)
     * @param template XML template with ${variable} placeholders
     */
    public void addTaskTemplate(String taskName, String template) {
        if (taskName == null || taskName.trim().isEmpty()) {
            throw new IllegalArgumentException("taskName is required");
        }
        if (template == null || template.trim().isEmpty()) {
            throw new IllegalArgumentException("template is required");
        }
        taskTemplates.put(taskName.trim(), template);
    }

    /**
     * Set default template for tasks without specific templates.
     */
    public void setDefaultTemplate(String template) {
        if (template == null || template.trim().isEmpty()) {
            throw new IllegalArgumentException("template is required");
        }
        this.defaultTemplate = template;
    }

    @Override
    public String produceOutput(WorkItemRecord workItem) {
        if (workItem == null) {
            throw new IllegalArgumentException("workItem is required");
        }

        String taskName = extractTaskName(workItem);
        String template = taskTemplates.getOrDefault(taskName, defaultTemplate);

        Map<String, String> variables = buildVariableMap(workItem);
        return replaceVariables(template, variables);
    }

    /**
     * Get all configured task names.
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
        String taskName = workItem.getTaskName();
        if (taskName == null || taskName.isEmpty()) {
            taskName = workItem.getTaskID();
        }
        return taskName;
    }

    private static Map<String, String> buildVariableMap(WorkItemRecord workItem) {
        Map<String, String> variables = new HashMap<>();

        String taskName = extractTaskName(workItem);
        String taskId = workItem.getTaskID();
        String caseId = workItem.getCaseID();
        String decompositionRoot = taskName.replace(' ', '_');

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
            Element dataList = workItem.getDataList();
            if (dataList == null) {
                return;
            }

            for (Element child : dataList.getChildren()) {
                String name = child.getName();
                String value = child.getTextTrim();
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
        for (Element child : parent.getChildren()) {
            String name = child.getName();
            String fullPath = prefix + "." + name;
            String value = child.getTextTrim();

            if (!value.isEmpty()) {
                variables.put(fullPath, value);
            }

            if (!child.getChildren().isEmpty()) {
                extractNestedElements(child, fullPath, variables);
            }
        }
    }

    private static String replaceVariables(String template,
                                          Map<String, String> variables) {
        String result = template;

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue();
            result = result.replace(placeholder, escapeXml(value));
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

    private static final String DEFAULT_TEMPLATE =
        "<${decompositionRoot}><result>true</result></${decompositionRoot}>";
}
