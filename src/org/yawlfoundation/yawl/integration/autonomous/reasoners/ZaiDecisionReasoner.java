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

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.util.JDOMUtil;

/**
 * ZAI-powered decision reasoner for autonomous agents.
 *
 * Uses AI reasoning to generate work item output based on task context
 * and input data. Extracted from DecisionWorkflow but made generic and
 * configurable with custom prompt templates.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class ZaiDecisionReasoner implements DecisionReasoner {

    private final ZaiService zaiService;
    private String systemPromptTemplate;
    private String userPromptTemplate;
    private String mcpGuide;

    /**
     * Create reasoner with ZAI service.
     * Uses default prompt templates.
     */
    public ZaiDecisionReasoner(ZaiService zaiService) {
        if (zaiService == null) {
            throw new IllegalArgumentException("zaiService is required");
        }
        this.zaiService = zaiService;
        this.systemPromptTemplate = DEFAULT_SYSTEM_PROMPT;
        this.userPromptTemplate = DEFAULT_USER_PROMPT;
    }

    /**
     * Create reasoner with custom prompt templates.
     * Templates support variables: {workItemId}, {taskName}, {decompositionRoot}, {inputXml}, {mcpGuide}
     */
    public ZaiDecisionReasoner(ZaiService zaiService,
                               String systemPromptTemplate,
                               String userPromptTemplate) {
        if (zaiService == null) {
            throw new IllegalArgumentException("zaiService is required");
        }
        if (systemPromptTemplate == null || systemPromptTemplate.trim().isEmpty()) {
            throw new IllegalArgumentException("systemPromptTemplate is required");
        }
        if (userPromptTemplate == null || userPromptTemplate.trim().isEmpty()) {
            throw new IllegalArgumentException("userPromptTemplate is required");
        }
        this.zaiService = zaiService;
        this.systemPromptTemplate = systemPromptTemplate;
        this.userPromptTemplate = userPromptTemplate;
    }

    @Override
    public String produceOutput(WorkItemRecord workItem) {
        if (workItem == null) {
            throw new IllegalArgumentException("workItem is required");
        }

        String taskName = extractTaskName(workItem);
        String decompositionRoot = taskName.replace(' ', '_');
        String inputXml = getInputXml(workItem);
        String workItemId = workItem.getID();

        String mcpGuideText = (mcpGuide != null && !mcpGuide.isEmpty())
            ? "=== MCP Task Completion Guide ===\n" + mcpGuide + "\n\n"
            : "";

        String systemPrompt = replaceVariables(systemPromptTemplate,
            workItemId, taskName, decompositionRoot, inputXml, mcpGuideText);
        String userPrompt = replaceVariables(userPromptTemplate,
            workItemId, taskName, decompositionRoot, inputXml, mcpGuideText);

        zaiService.setSystemPrompt(systemPrompt);

        try {
            String response = zaiService.chat(userPrompt);
            return extractXml(response, decompositionRoot);
        } catch (Exception e) {
            throw new RuntimeException(
                "Decision reasoning failed for work item " + workItem.getID(), e);
        }
    }

    /**
     * Set MCP task completion guide for enhanced prompts.
     * This provides spec-aware guidance from an MCP server.
     */
    public void setMcpGuide(String guide) {
        this.mcpGuide = guide;
    }

    /**
     * Set custom system prompt template.
     * Variables: {workItemId}, {taskName}, {decompositionRoot}, {inputXml}, {mcpGuide}
     */
    public void setSystemPromptTemplate(String template) {
        if (template == null || template.trim().isEmpty()) {
            throw new IllegalArgumentException("systemPromptTemplate cannot be empty");
        }
        this.systemPromptTemplate = template;
    }

    /**
     * Set custom user prompt template.
     * Variables: {workItemId}, {taskName}, {decompositionRoot}, {inputXml}, {mcpGuide}
     */
    public void setUserPromptTemplate(String template) {
        if (template == null || template.trim().isEmpty()) {
            throw new IllegalArgumentException("userPromptTemplate cannot be empty");
        }
        this.userPromptTemplate = template;
    }

    private static String extractTaskName(WorkItemRecord workItem) {
        String taskName = workItem.getTaskName();
        if (taskName == null || taskName.isEmpty()) {
            taskName = workItem.getTaskID();
        }
        return taskName;
    }

    private static String getInputXml(WorkItemRecord workItem) {
        try {
            var dataList = workItem.getDataList();
            if (dataList == null) {
                return "<data/>";
            }
            String xml = JDOMUtil.elementToStringDump(dataList);
            return xml != null ? xml : "<data/>";
        } catch (Exception e) {
            return "<data/>";
        }
    }

    private static String extractXml(String response, String expectedRoot) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty response from ZAI");
        }
        String s = response.trim();
        int start = s.indexOf("<");
        int end = s.lastIndexOf(">");
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return "<" + expectedRoot + "><result>true</result></" + expectedRoot + ">";
    }

    private static String replaceVariables(String template,
                                          String workItemId,
                                          String taskName,
                                          String decompositionRoot,
                                          String inputXml,
                                          String mcpGuide) {
        return template
            .replace("{workItemId}", workItemId)
            .replace("{taskName}", taskName)
            .replace("{decompositionRoot}", decompositionRoot)
            .replace("{inputXml}", inputXml)
            .replace("{mcpGuide}", mcpGuide);
    }

    private static final String DEFAULT_SYSTEM_PROMPT =
        "You are a YAWL workflow output generator. Produce valid XML for workflow tasks. " +
        "Return only the XML, no other text. Align with task_completion_guide: expected " +
        "input/output format, data validation, and YAWL engine requirements.";

    private static final String DEFAULT_USER_PROMPT =
        "{mcpGuide}Produce valid XML output for this YAWL workflow task.\n\n" +
        "Work Item ID: {workItemId}\n" +
        "Task name: {taskName}\n" +
        "Decomposition root element (use this as the XML root): {decompositionRoot}\n\n" +
        "Input data:\n{inputXml}\n\n" +
        "Expected output format:\n" +
        "1. Root element MUST be <{decompositionRoot}>\n" +
        "2. Include required output parameters by task type:\n" +
        "   - Approval tasks: Approved/Approval/POApproval etc. as boolean true\n" +
        "   - Document tasks: document structure matching input schema\n" +
        "   - Create tasks: created entity with valid structure\n" +
        "3. Data validation: XML must be well-formed; element names match YAWL spec\n" +
        "4. Return ONLY the XML output, no explanation, no markdown, no code block.\n" +
        "5. Common issues: avoid extra whitespace; use correct namespaces if specified.";
}
