/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous.strategies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Eligibility reasoner using Z.AI for intelligent work item evaluation.
 *
 * <p>Submits a work item to Z.AI with a configurable prompt template and
 * expects a JSON response containing an {@code "eligible"} field to determine
 * whether the work item should be processed.
 *
 * <p>Prompt substitutions:
 * <ul>
 *   <li>{@code {taskName}} → work item's task name</li>
 *   <li>{@code {taskDescription}} → work item's task name (no separate description field)</li>
 *   <li>{@code {inputData}} → work item's input data as string</li>
 * </ul>
 *
 * <p>Expected JSON response format:
 * <pre>
 * {
 *   "eligible": true/false,
 *   "confidence": 0.0-1.0,
 *   "reason": "explanation"
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since YAWL 6.0
 */
public final class ZaiEligibilityReasonerStrategy implements EligibilityReasoner {

    private static final Logger logger = LogManager.getLogger(ZaiEligibilityReasonerStrategy.class);

    private final ZaiService zaiService;
    private final String promptTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Create a Z.AI-based eligibility reasoner.
     *
     * @param zaiService the Z.AI service for making API calls
     * @param promptTemplate the prompt template with {taskName}, {taskDescription}, {inputData} placeholders
     */
    public ZaiEligibilityReasonerStrategy(ZaiService zaiService, String promptTemplate) {
        if (zaiService == null) {
            throw new IllegalArgumentException("ZaiService is required");
        }
        if (promptTemplate == null || promptTemplate.isBlank()) {
            throw new IllegalArgumentException("promptTemplate is required");
        }
        this.zaiService = zaiService;
        this.promptTemplate = promptTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Determine if a work item is eligible using Z.AI reasoning.
     *
     * @param workItem the work item to evaluate
     * @return true if Z.AI response contains "eligible": true; false otherwise
     */
    @Override
    public boolean isEligible(WorkItemRecord workItem) {
        if (workItem == null) {
            logger.warn("Work item is null, marking as not eligible");
            return false;
        }

        try {
            // Substitute placeholders in prompt
            String taskName = workItem.getTaskName();
            String inputData = workItem.getDataListString();

            String prompt = promptTemplate
                .replace("{taskName}", taskName != null ? taskName : "")
                .replace("{taskDescription}", taskName != null ? taskName : "")
                .replace("{inputData}", inputData != null ? inputData : "");

            // Call Z.AI with the prompt
            String response = zaiService.chat(prompt, "GLM-4.7-Flash");

            // Parse response JSON and check "eligible" field
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode eligibleNode = jsonNode.get("eligible");

            if (eligibleNode != null) {
                boolean eligible = eligibleNode.asBoolean();
                JsonNode confidenceNode = jsonNode.get("confidence");
                String reason = jsonNode.get("reason") != null
                    ? jsonNode.get("reason").asText()
                    : "";

                logger.debug("Z.AI eligibility for work item {}: eligible={}, reason={}",
                    workItem.getID(), eligible, reason);

                return eligible;
            }

            logger.warn("Z.AI response missing 'eligible' field for work item {}: {}",
                workItem.getID(), response);
            return false;

        } catch (Exception e) {
            logger.error("Z.AI eligibility check failed for work item {}: {}",
                workItem.getID(), e.getMessage(), e);
            return false;
        }
    }
}
