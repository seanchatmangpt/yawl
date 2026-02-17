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

import org.yawlfoundation.yawl.integration.CredentialManager;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.io.IOException;

/**
 * Factory for creating autonomous agents with various configurations.
 *
 * <p>The factory pattern provides:
 * <ul>
 *   <li>Centralized agent creation logic</li>
 *   <li>Environment-based configuration (12-factor app pattern)</li>
 *   <li>Consistent initialization and validation</li>
 *   <li>Support for different agent types via strategy injection</li>
 * </ul>
 * </p>
 *
 * <p>Usage:
 * <pre>
 * // From environment variables
 * AutonomousAgent agent = AgentFactory.fromEnvironment();
 * agent.start();
 *
 * // With custom configuration
 * AgentConfiguration config = AgentConfiguration.builder()
 *     .capability(new AgentCapability("Ordering", "procurement, purchase orders"))
 *     .engineUrl("http://localhost:8080/yawl")
 *     .username("admin")
 *     .password(CredentialManager.getYawlPassword())
 *     .discoveryStrategy(new PollingDiscoveryStrategy())
 *     .eligibilityReasoner(new ZaiEligibilityReasoner(capability, zaiService))
 *     .decisionReasoner(new ZaiDecisionReasoner(zaiService))
 *     .build();
 * AutonomousAgent agent = AgentFactory.create(config);
 * </pre>
 * </p>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see AutonomousAgent
 * @see AgentConfiguration
 */
public final class AgentFactory {

    /**
     * Private constructor prevents instantiation of utility class.
     */
    private AgentFactory() {
        throw new UnsupportedOperationException("AgentFactory is a utility class and cannot be instantiated");
    }

    /**
     * Create an autonomous agent from configuration.
     *
     * <p>This is the primary factory method. It validates the configuration,
     * connects to the YAWL engine, and returns a fully initialized agent
     * ready to start.</p>
     *
     * @param configuration the agent configuration with all dependencies
     * @return initialized autonomous agent (not started)
     * @throws IOException if connection to YAWL engine fails
     * @throws IllegalArgumentException if configuration is invalid
     */
    public static AutonomousAgent create(AgentConfiguration configuration) throws IOException {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration is required");
        }
        return new GenericPartyAgent(configuration);
    }

    /**
     * Create an autonomous agent from environment variables.
     *
     * <p>Required environment variables:
     * <ul>
     *   <li><code>AGENT_CAPABILITY</code> - Domain capability (format: "DomainName: description")</li>
     *   <li><code>YAWL_PASSWORD</code> - YAWL engine admin password (required; no default)</li>
     *   <li><code>ZAI_API_KEY</code> - Z.AI API key for AI reasoning (required)</li>
     * </ul>
     * </p>
     *
     * <p>Optional environment variables (with defaults):
     * <ul>
     *   <li><code>YAWL_ENGINE_URL</code> - YAWL engine URL (default: http://localhost:8080/yawl)</li>
     *   <li><code>YAWL_USERNAME</code> - YAWL username (default: admin)</li>
     * </ul>
     * </p>
     *
     * <p>Optional environment variables:
     * <ul>
     *   <li><code>AGENT_PORT</code> - HTTP server port (default: 8091)</li>
     *   <li><code>POLL_INTERVAL_MS</code> - Discovery poll interval in milliseconds (default: 3000)</li>
     *   <li><code>ZAI_API_KEY</code> - Z.AI API key for AI-based reasoning (required if using ZAI strategies)</li>
     * </ul>
     * </p>
     *
     * <p>This method creates default strategies based on available services:
     * <ul>
     *   <li>Discovery: Polling all live work items</li>
     *   <li>Eligibility: ZAI-based reasoning (requires ZAI_API_KEY)</li>
     *   <li>Decision: ZAI-based output generation (requires ZAI_API_KEY)</li>
     * </ul>
     * </p>
     *
     * @return initialized autonomous agent (not started)
     * @throws IOException if connection to YAWL engine fails
     * @throws IllegalStateException if required environment variables are missing
     */
    public static AutonomousAgent fromEnvironment() throws IOException {
        AgentCapability capability = AgentCapability.fromEnvironment();

        String engineUrl = CredentialManager.getYawlEngineUrl();
        String username = CredentialManager.getYawlUsername();
        String password = CredentialManager.getYawlPassword();

        int port = parsePort(getEnv("AGENT_PORT", "8091"));
        long pollIntervalMs = parseLong(getEnv("POLL_INTERVAL_MS", "3000"));

        var zaiKey = System.getenv("ZAI_API_KEY");
        if (zaiKey == null || zaiKey.isBlank()) {
            throw new IllegalStateException(
                "ZAI_API_KEY environment variable is required for autonomous agent reasoning.\n" +
                "Set ZAI_API_KEY to your Z.AI API key to enable AI-based eligibility and decision reasoning.");
        }

        var zaiService = new ZaiService(zaiKey);

        var discoveryStrategy = createDefaultDiscoveryStrategy();
        var eligibilityReasoner = createDefaultEligibilityReasoner(capability, zaiService);
        var decisionReasoner = createDefaultDecisionReasoner(zaiService);

        var config = AgentConfiguration.builder()
            .capability(capability)
            .engineUrl(engineUrl)
            .username(username)
            .password(password)
            .port(port)
            .pollIntervalMs(pollIntervalMs)
            .discoveryStrategy(discoveryStrategy)
            .eligibilityReasoner(eligibilityReasoner)
            .decisionReasoner(decisionReasoner)
            .build();

        return create(config);
    }

    /**
     * Create default discovery strategy (polling all live work items).
     *
     * @return polling discovery strategy
     */
    private static DiscoveryStrategy createDefaultDiscoveryStrategy() {
        return (interfaceBClient, sessionHandle) ->
            interfaceBClient.getCompleteListOfLiveWorkItems(sessionHandle);
    }

    /**
     * Create default eligibility reasoner using ZAI.
     *
     * <p>The reasoner uses ZAI to evaluate work items against the agent's
     * domain capability. It sends a prompt with the capability and work item
     * context, and determines eligibility based on the AI response.</p>
     *
     * @param capability the agent's capability
     * @param zaiService the ZAI service for AI reasoning
     * @return ZAI-based eligibility reasoner
     */
    private static EligibilityReasoner createDefaultEligibilityReasoner(
            AgentCapability capability,
            ZaiService zaiService) {

        return workItem -> {
            var taskName = workItem.getTaskName();
            if (taskName == null || taskName.isBlank()) {
                taskName = workItem.getTaskID();
            }

            var inputSummary = summarizeWorkItemInput(workItem);

            var prompt = """
                You are an autonomous agent with this domain capability: %s

                Work item to evaluate:
                - Task: %s
                - Case: %s
                - Input data summary: %s

                Should this agent handle this work item? Answer with exactly YES or NO. \
                If YES, add a brief reason in one sentence. If NO, add a brief reason."""
                .formatted(capability.description(), taskName,
                           workItem.getCaseID(), inputSummary);

            zaiService.setSystemPrompt(
                "You are a workflow routing assistant. You decide if an agent should " +
                "handle a work item based on the agent's domain capability and the " +
                "work item's task. Be concise. Answer YES or NO first.");

            try {
                var response = zaiService.chat(prompt);
                return response != null && response.strip().toUpperCase().startsWith("YES");
            } catch (Exception e) {
                return false;
            }
        };
    }

    /**
     * Create default decision reasoner using ZAI.
     *
     * <p>The reasoner uses ZAI to generate valid XML output for work items.
     * It analyzes the work item's input data and task requirements to produce
     * appropriate output XML conforming to the YAWL specification.</p>
     *
     * @param zaiService the ZAI service for AI reasoning
     * @return ZAI-based decision reasoner
     */
    private static DecisionReasoner createDefaultDecisionReasoner(ZaiService zaiService) {
        return workItem -> {
            var taskName = workItem.getTaskName();
            if (taskName == null || taskName.isBlank()) {
                taskName = workItem.getTaskID();
            }
            var decompositionRoot = taskName.replace(' ', '_');
            var inputXml = getWorkItemInputXml(workItem);

            var prompt = buildDecisionPrompt(workItem, taskName, decompositionRoot, inputXml);

            zaiService.setSystemPrompt(
                "You are a YAWL workflow output generator. Produce valid XML for workflow tasks. " +
                "Return only the XML, no other text. Align with task requirements: expected " +
                "input/output format, data validation, and YAWL engine requirements.");

            try {
                String response = zaiService.chat(prompt);
                return extractXml(response, decompositionRoot);
            } catch (Exception e) {
                throw new RuntimeException("Failed to produce output for " + workItem.getID(), e);
            }
        };
    }

    /**
     * Build decision prompt for ZAI output generation.
     */
    private static String buildDecisionPrompt(
            org.yawlfoundation.yawl.engine.interfce.WorkItemRecord workItem,
            String taskName,
            String decompositionRoot,
            String inputXml) {

        return """
            Produce valid XML output for this YAWL workflow task.

            Work Item ID: %s
            Task name: %s
            Decomposition root element (use this as the XML root): %s

            Input data:
            %s

            Expected output format:
            1. Root element MUST be <%s>
            2. Include required output parameters by task type:
               - Approval tasks: Approved/Approval/POApproval etc. as boolean true
               - Document tasks: document structure matching input schema
               - Create tasks: created entity with valid structure
            3. Data validation: XML must be well-formed; element names match YAWL spec
            4. Return ONLY the XML output, no explanation, no markdown, no code block.
            5. Common issues: avoid extra whitespace; use correct namespaces if specified."""
            .formatted(workItem.getID(), taskName, decompositionRoot, inputXml, decompositionRoot);
    }

    /**
     * Summarize work item input for eligibility prompts.
     */
    private static String summarizeWorkItemInput(org.yawlfoundation.yawl.engine.interfce.WorkItemRecord workItem) {
        try {
            var dataList = workItem.getDataList();
            if (dataList == null) {
                return "(no input data)";
            }
            var xml = org.yawlfoundation.yawl.util.JDOMUtil.elementToStringDump(dataList);
            if (xml == null || xml.isBlank()) {
                return "(empty input)";
            }
            return xml.length() > 500 ? xml.substring(0, 500) + "..." : xml;
        } catch (Exception e) {
            return "(could not read input)";
        }
    }

    /**
     * Get work item input as XML string.
     */
    private static String getWorkItemInputXml(org.yawlfoundation.yawl.engine.interfce.WorkItemRecord workItem) {
        try {
            var dataList = workItem.getDataList();
            if (dataList == null) {
                return "<data/>";
            }
            var xml = org.yawlfoundation.yawl.util.JDOMUtil.elementToStringDump(dataList);
            return xml != null ? xml : "<data/>";
        } catch (Exception e) {
            return "<data/>";
        }
    }

    /**
     * Extract XML from ZAI response.
     */
    private static String extractXml(String response, String expectedRoot) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Empty response from ZAI");
        }
        var s = response.strip();
        int start = s.indexOf("<");
        int end = s.lastIndexOf(">");
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return "<" + expectedRoot + "><result>true</result></" + expectedRoot + ">";
    }

    /**
     * Get environment variable with default fallback.
     */
    private static String getEnv(String name, String defaultValue) {
        var value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value.strip() : defaultValue;
    }

    /**
     * Parse port from environment variable.
     */
    private static int parsePort(String portStr) {
        try {
            return Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid AGENT_PORT: " + portStr);
        }
    }

    /**
     * Parse long value from environment variable.
     */
    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid numeric value: " + value);
        }
    }
}
