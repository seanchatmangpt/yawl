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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

/**
 * Decision reasoner using static templates with work item substitution.
 *
 * <p>Loads a template file and substitutes placeholders with work item values.
 * Supports the following placeholders:
 * <ul>
 *   <li>{@code {taskName}} → work item's task name</li>
 *   <li>{@code {workItemId}} → work item's unique ID</li>
 *   <li>{@code {caseId}} → work item's case ID</li>
 *   <li>{@code {inputData}} → work item's input data as string</li>
 * </ul>
 *
 * <p>The template file can be in any text format (typically XML or JSON).
 * After substitution, the template content is returned as-is as the output data.
 *
 * <p>Example template file (notification-output.xml):
 * <pre>
 * {@code <outputData>
 *   <status>Success</status>
 *   <timestamp>{timestamp}</timestamp>
 *   <caseid>{caseId}</caseid>
 *   <workItemId>{workItemId}</workItemId>
 *   <taskName>{taskName}</taskName>
 *   <inputSummary>{inputData}</inputSummary>
 * </outputData>}
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since YAWL 6.0
 */
public final class TemplateDecisionReasoner implements DecisionReasoner {

    private static final Logger logger = LogManager.getLogger(TemplateDecisionReasoner.class);

    private final String templateContent;

    /**
     * Create a template-based decision reasoner.
     *
     * @param templateFilePath the path to the template file
     * @throws IOException if the template file cannot be read
     */
    public TemplateDecisionReasoner(String templateFilePath) throws IOException {
        if (templateFilePath == null || templateFilePath.isBlank()) {
            throw new IllegalArgumentException("templateFilePath is required");
        }

        Path filePath = Path.of(templateFilePath);
        if (!Files.exists(filePath)) {
            throw new IOException("Template file not found: " + templateFilePath);
        }

        this.templateContent = Files.readString(filePath);
        logger.info("Loaded template from {}, {} bytes", templateFilePath, templateContent.length());
    }

    /**
     * Produce output by substituting template placeholders with work item values.
     *
     * @param workItem the work item to process
     * @return the template content with all placeholders substituted
     */
    @Override
    public String produceOutput(WorkItemRecord workItem) {
        if (workItem == null) {
            logger.error("Work item is null");
            throw new IllegalArgumentException("Work item cannot be null");
        }

        String result = templateContent;

        // Extract values from work item
        String taskName = workItem.getTaskName();
        String workItemId = workItem.getID();
        String caseId = workItem.getCaseID();
        String inputData = workItem.getDataListString();

        // Perform substitutions
        result = result.replace("{taskName}", taskName != null ? taskName : "");
        result = result.replace("{workItemId}", workItemId != null ? workItemId : "");
        result = result.replace("{caseId}", caseId != null ? caseId : "");
        result = result.replace("{inputData}", inputData != null ? inputData : "");

        logger.debug("Generated template output for work item {} ({}), {} bytes",
            workItemId, taskName, result.length());

        return result;
    }
}
