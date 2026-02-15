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

package org.yawlfoundation.yawl.integration.orderfulfillment;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.util.JDOMUtil;

/**
 * Stateless decision workflow: "What output should I produce?"
 *
 * Uses ZAI to reason over task context and input data to produce
 * valid output XML. No hardcoded task-to-output mappings.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class DecisionWorkflow {

    private final ZaiService zaiService;
    private final McpTaskContextSupplier mcpContextSupplier;

    public DecisionWorkflow(ZaiService zaiService) {
        this(zaiService, null);
    }

    public DecisionWorkflow(ZaiService zaiService, McpTaskContextSupplier mcpContextSupplier) {
        if (zaiService == null) {
            throw new IllegalArgumentException("zaiService is required");
        }
        this.zaiService = zaiService;
        this.mcpContextSupplier = mcpContextSupplier;
    }

    /**
     * Produce output XML for the work item.
     *
     * @param workItem the work item to complete
     * @return valid XML output (root = decomposition id, e.g. Approve_Purchase_Order)
     */
    public String produceOutput(WorkItemRecord workItem) {
        String taskName = workItem.getTaskName();
        if (taskName == null || taskName.isEmpty()) {
            taskName = workItem.getTaskID();
        }
        String decompositionRoot = taskName.replace(' ', '_');
        String inputXml = getInputXml(workItem);

        String mcpGuide = null;
        if (mcpContextSupplier != null) {
            mcpGuide = mcpContextSupplier.getTaskCompletionGuide(workItem);
        }

        String prompt = buildPrompt(workItem, taskName, decompositionRoot, inputXml, mcpGuide);
        zaiService.setSystemPrompt(
            "You are a YAWL workflow output generator. Produce valid XML for workflow tasks. " +
            "Return only the XML, no other text. Align with task_completion_guide: expected " +
            "input/output format, data validation, and YAWL engine requirements.");

        try {
            String response = zaiService.chat(prompt);
            return extractXml(response, decompositionRoot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to produce output for " + workItem.getID(), e);
        }
    }

    private static String buildPrompt(WorkItemRecord workItem, String taskName,
            String decompositionRoot, String inputXml, String mcpGuide) {
        StringBuilder sb = new StringBuilder();
        if (mcpGuide != null && !mcpGuide.isEmpty()) {
            sb.append("=== MCP Task Completion Guide ===\n").append(mcpGuide).append("\n\n");
        }
        sb.append("Produce valid XML output for this YAWL workflow task.\n\n");
        sb.append("Work Item ID: ").append(workItem.getID()).append("\n");
        sb.append("Task name: ").append(taskName).append("\n");
        sb.append("Decomposition root element (use this as the XML root): ").append(decompositionRoot).append("\n\n");
        sb.append("Input data:\n").append(inputXml).append("\n\n");
        sb.append("Expected output format:\n");
        sb.append("1. Root element MUST be <").append(decompositionRoot).append(">\n");
        sb.append("2. Include required output parameters by task type:\n");
        sb.append("   - Approval tasks: Approved/Approval/POApproval etc. as boolean true\n");
        sb.append("   - Document tasks: document structure matching input schema\n");
        sb.append("   - Create tasks: created entity with valid structure\n");
        sb.append("3. Data validation: XML must be well-formed; element names match YAWL spec\n");
        sb.append("4. Return ONLY the XML output, no explanation, no markdown, no code block.\n");
        sb.append("5. Common issues: avoid extra whitespace; use correct namespaces if specified.");
        return sb.toString();
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
}
