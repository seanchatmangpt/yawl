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
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Eligibility reasoner using static task-to-agent mappings from a JSON file.
 *
 * <p>Loads a JSON file containing explicit mappings of task names to agent domains.
 * A work item is eligible if its task name is mapped to the agent's domain, or if
 * no mapping exists and the default agent matches.
 *
 * <p>JSON file format:
 * <pre>
 * {
 *   "taskMappings": {
 *     "Task_Name_1": "Domain_1",
 *     "Task_Name_2": "Domain_1",
 *     "Task_Name_3": "Domain_2",
 *     ...
 *   },
 *   "defaultAgent": "Domain_1"
 * }
 * </pre>
 *
 * <p>Example:
 * <pre>
 * {
 *   "taskMappings": {
 *     "Send_Email": "Email",
 *     "Send_SMS": "SMS",
 *     "Send_Alert": "Alert"
 *   },
 *   "defaultAgent": "Email"
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since YAWL 6.0
 */
public final class StaticMappingEligibilityReasoner implements EligibilityReasoner {

    private static final Logger logger = LogManager.getLogger(StaticMappingEligibilityReasoner.class);

    private final Map<String, String> taskToAgentDomain;
    private final String agentDomain;
    private final String defaultDomain;
    private final ObjectMapper objectMapper;

    /**
     * Create a static mapping-based eligibility reasoner.
     *
     * @param mappingFilePath the path to the JSON mapping file
     * @param agentDomain the domain name of this agent
     * @throws IOException if the mapping file cannot be read or parsed
     */
    public StaticMappingEligibilityReasoner(String mappingFilePath, String agentDomain) throws IOException {
        if (mappingFilePath == null || mappingFilePath.isBlank()) {
            throw new IllegalArgumentException("mappingFilePath is required");
        }
        if (agentDomain == null || agentDomain.isBlank()) {
            throw new IllegalArgumentException("agentDomain is required");
        }

        this.agentDomain = agentDomain;
        this.objectMapper = new ObjectMapper();

        // Load and parse the mapping file
        Path filePath = Path.of(mappingFilePath);
        if (!Files.exists(filePath)) {
            throw new IOException("Mapping file not found: " + mappingFilePath);
        }

        String jsonContent = Files.readString(filePath);
        JsonNode rootNode = objectMapper.readTree(jsonContent);

        // Extract taskMappings
        JsonNode mappingsNode = rootNode.get("taskMappings");
        if (mappingsNode == null || !mappingsNode.isObject()) {
            throw new IOException("Missing or invalid 'taskMappings' section in mapping file");
        }

        // Build the task-to-domain map
        java.util.Map<String, String> mappings = new java.util.HashMap<>();
        mappingsNode.fields().forEachRemaining(entry -> {
            mappings.put(entry.getKey(), entry.getValue().asText());
        });
        this.taskToAgentDomain = java.util.Collections.unmodifiableMap(mappings);

        // Extract defaultAgent
        JsonNode defaultNode = rootNode.get("defaultAgent");
        this.defaultDomain = defaultNode != null ? defaultNode.asText() : agentDomain;

        logger.info("Loaded static task mapping with {} entries. Agent domain: {}, Default: {}",
            taskToAgentDomain.size(), agentDomain, defaultDomain);
    }

    /**
     * Determine if a work item is eligible based on static task mappings.
     *
     * @param workItem the work item to evaluate
     * @return true if the task is mapped to this agent's domain or if default matches
     */
    @Override
    public boolean isEligible(WorkItemRecord workItem) {
        if (workItem == null) {
            logger.warn("Work item is null, marking as not eligible");
            return false;
        }

        String taskName = workItem.getTaskName();
        if (taskName == null || taskName.isBlank()) {
            logger.warn("Work item has no task name, marking as not eligible");
            return false;
        }

        // Check explicit mapping
        String mappedDomain = taskToAgentDomain.get(taskName);
        if (mappedDomain != null) {
            boolean eligible = mappedDomain.equals(agentDomain);
            logger.debug("Task {} explicitly mapped to {}, eligible for {}: {}",
                taskName, mappedDomain, agentDomain, eligible);
            return eligible;
        }

        // Use default domain
        boolean eligible = defaultDomain.equals(agentDomain);
        logger.debug("Task {} not explicitly mapped, using default domain {}, eligible for {}: {}",
            taskName, defaultDomain, agentDomain, eligible);
        return eligible;
    }
}
