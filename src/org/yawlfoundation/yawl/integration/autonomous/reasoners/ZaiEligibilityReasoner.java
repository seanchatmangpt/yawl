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
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;
import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.util.JDOMUtil;

/**
 * ZAI-powered eligibility reasoner for autonomous agents.
 *
 * Uses AI reasoning to determine if an agent should handle a work item
 * based on capability description and work item context. Extracted from
 * EligibilityWorkflow but made generic and configurable.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class ZaiEligibilityReasoner implements EligibilityReasoner {

    private final AgentCapability capability;
    private final ZaiService zaiService;
    private String systemPromptTemplate;
    private String userPromptTemplate;

    /**
     * Create reasoner with agent capability and ZAI service.
     * Uses default prompt templates.
     */
    public ZaiEligibilityReasoner(AgentCapability capability, ZaiService zaiService) {
        if (capability == null) {
            throw new IllegalArgumentException("capability is required");
        }
        if (zaiService == null) {
            throw new IllegalArgumentException("zaiService is required");
        }
        this.capability = capability;
        this.zaiService = zaiService;
        this.systemPromptTemplate = DEFAULT_SYSTEM_PROMPT;
        this.userPromptTemplate = DEFAULT_USER_PROMPT;
    }

    /**
     * Create reasoner with custom prompt templates.
     * Templates support variables: {capability}, {taskName}, {caseId}, {inputSummary}
     */
    public ZaiEligibilityReasoner(AgentCapability capability,
                                   ZaiService zaiService,
                                   String systemPromptTemplate,
                                   String userPromptTemplate) {
        if (capability == null) {
            throw new IllegalArgumentException("capability is required");
        }
        if (zaiService == null) {
            throw new IllegalArgumentException("zaiService is required");
        }
        if (systemPromptTemplate == null || systemPromptTemplate.trim().isEmpty()) {
            throw new IllegalArgumentException("systemPromptTemplate is required");
        }
        if (userPromptTemplate == null || userPromptTemplate.trim().isEmpty()) {
            throw new IllegalArgumentException("userPromptTemplate is required");
        }
        this.capability = capability;
        this.zaiService = zaiService;
        this.systemPromptTemplate = systemPromptTemplate;
        this.userPromptTemplate = userPromptTemplate;
    }

    @Override
    public boolean isEligible(WorkItemRecord workItem) {
        if (workItem == null) {
            throw new IllegalArgumentException("workItem is required");
        }

        String taskName = extractTaskName(workItem);
        String caseId = workItem.getCaseID();
        String inputSummary = summarizeInput(workItem);

        String systemPrompt = replaceVariables(systemPromptTemplate,
            capability.description(), taskName, caseId, inputSummary);
        String userPrompt = replaceVariables(userPromptTemplate,
            capability.description(), taskName, caseId, inputSummary);

        zaiService.setSystemPrompt(systemPrompt);

        try {
            String response = zaiService.chat(userPrompt);
            return response != null && response.trim().toUpperCase().startsWith("YES");
        } catch (Exception e) {
            throw new RuntimeException(
                "Eligibility reasoning failed for work item " + workItem.getID(), e);
        }
    }

    /**
     * Set custom system prompt template.
     * Variables: {capability}, {taskName}, {caseId}, {inputSummary}
     */
    public void setSystemPromptTemplate(String template) {
        if (template == null || template.trim().isEmpty()) {
            throw new IllegalArgumentException("systemPromptTemplate cannot be empty");
        }
        this.systemPromptTemplate = template;
    }

    /**
     * Set custom user prompt template.
     * Variables: {capability}, {taskName}, {caseId}, {inputSummary}
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

    private static String summarizeInput(WorkItemRecord workItem) {
        try {
            var dataList = workItem.getDataList();
            if (dataList == null) {
                return "(no input data)";
            }
            String xml = JDOMUtil.elementToStringDump(dataList);
            if (xml == null || xml.isEmpty()) {
                return "(empty input)";
            }
            return xml.length() > 500 ? xml.substring(0, 500) + "..." : xml;
        } catch (Exception e) {
            return "(could not read input)";
        }
    }

    private static String replaceVariables(String template,
                                          String capability,
                                          String taskName,
                                          String caseId,
                                          String inputSummary) {
        return template
            .replace("{capability}", capability)
            .replace("{taskName}", taskName)
            .replace("{caseId}", caseId)
            .replace("{inputSummary}", inputSummary);
    }

    private static final String DEFAULT_SYSTEM_PROMPT =
        "You are a workflow routing assistant. You decide if an agent should " +
        "handle a work item based on the agent's domain capability and the " +
        "work item's task. Be concise. Answer YES or NO first.";

    private static final String DEFAULT_USER_PROMPT =
        "You are an autonomous agent with this domain capability: {capability}\n\n" +
        "Work item to evaluate:\n" +
        "- Task: {taskName}\n" +
        "- Case: {caseId}\n" +
        "- Input data summary: {inputSummary}\n\n" +
        "Should this agent handle this work item? Answer with exactly YES or NO. " +
        "If YES, add a brief reason in one sentence. If NO, add a brief reason.";
}
