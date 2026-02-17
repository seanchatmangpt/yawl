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
 * Stateless eligibility workflow: "Is this work item for me?"
 *
 * Uses ZAI to reason over agent capability and work item context.
 * No hardcoded task mappings.
 *
 * @author YAWL Foundation
 * @version 5.2
 * @deprecated Use {@link org.yawlfoundation.yawl.integration.autonomous.reasoners.ZaiEligibilityReasoner} instead.
 *             This class is specific to orderfulfillment and will be removed in a future version.
 */
@Deprecated
public final class EligibilityWorkflow {

    private final AgentCapability capability;
    private final ZaiService zaiService;

    public EligibilityWorkflow(AgentCapability capability, ZaiService zaiService) {
        if (capability == null) {
            throw new IllegalArgumentException("capability is required");
        }
        if (zaiService == null) {
            throw new IllegalArgumentException("zaiService is required");
        }
        this.capability = capability;
        this.zaiService = zaiService;
    }

    /**
     * Determine if this agent should handle the work item.
     *
     * @param workItem the work item to evaluate
     * @return true if the agent should handle it
     */
    public boolean isEligible(WorkItemRecord workItem) {
        String taskName = workItem.getTaskName();
        if (taskName == null || taskName.isEmpty()) {
            taskName = workItem.getTaskID();
        }
        String inputSummary = summarizeInput(workItem);

        String prompt = String.format(
            "You are an autonomous agent with this domain capability: %s\n\n" +
            "Work item to evaluate:\n" +
            "- Task: %s\n" +
            "- Case: %s\n" +
            "- Input data summary: %s\n\n" +
            "Should this agent handle this work item? Answer with exactly YES or NO. " +
            "If YES, add a brief reason in one sentence. If NO, add a brief reason.",
            capability.getDescription(),
            taskName,
            workItem.getCaseID(),
            inputSummary);

        zaiService.setSystemPrompt(
            "You are a workflow routing assistant. You decide if an agent should " +
            "handle a work item based on the agent's domain capability and the " +
            "work item's task. Be concise. Answer YES or NO first.");

        try {
            String response = zaiService.chat(prompt);
            return response != null && response.strip().toUpperCase().startsWith("YES");
        } catch (Exception e) {
            return false;
        }
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
}
