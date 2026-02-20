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

package org.yawlfoundation.yawl.mcp.a2a.a2a;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentProvider;
import io.a2a.spec.AgentSkill;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * YAWL Agent Card implementation for A2A protocol.
 *
 * <p>This component defines the YAWL agent's capabilities and metadata
 * for the A2A protocol. It adapts the pattern from CloudAgentCardProducer
 * for Spring Boot using {@code @Component} and {@code @Bean}.</p>
 *
 * <h2>Agent Capabilities</h2>
 * <ul>
 *   <li><strong>Workflow Management</strong>: Launch new workflow cases,
 *       cancel existing cases, monitor case status</li>
 *   <li><strong>Task Handling</strong>: Check out work items for processing,
 *       check in completed work items with results</li>
 *   <li><strong>Process Introspection</strong>: Query available workflow specifications,
 *       analyze process instances and their states</li>
 *   <li><strong>Data Transformation</strong>: Convert between XML and JSON formats,
 *       transform workflow data between different representations</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Agent properties are configured via {@code application.yml}:</p>
 * <pre>{@code
 * yawl:
 *   a2a:
 *     agent-name: "yawl-workflow-agent"
 *     agent-version: "6.0.0-Alpha"
 *     agent-description: "YAWL workflow management agent"
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see AgentCard
 */
@Component
@ConditionalOnProperty(prefix = "yawl.a2a", name = "enabled", havingValue = "true", matchIfMissing = true)
public class YawlA2AAgentCard {

    private static final Logger LOGGER = LoggerFactory.getLogger(YawlA2AAgentCard.class);

    @Value("${yawl.a2a.agent-name:yawl-workflow-agent}")
    private String agentName;

    @Value("${yawl.a2a.agent-version:6.0.0-Alpha}")
    private String agentVersion;

    @Value("${yawl.a2a.agent-description:YAWL workflow management agent with MCP tool support}")
    private String agentDescription;

    @Value("${yawl.a2a.transport.rest.port:8082}")
    private int restPort;

    @Value("${yawl.a2a.transport.rest.path:/a2a}")
    private String restPath;

    private AgentCard agentCard;

    /**
     * Initializes the agent card after bean creation.
     *
     * <p>This method builds the complete agent card with all YAWL-specific
     * capabilities defined as skills.</p>
     */
    @PostConstruct
    public void initialize() {
        LOGGER.info("Initializing YAWL A2A Agent Card: {}", agentName);

        this.agentCard = AgentCard.builder()
            .name(agentName)
            .description(agentDescription)
            .version(agentVersion)
            .provider(new AgentProvider("YAWL Foundation", "https://yawlfoundation.github.io"))
            .capabilities(AgentCapabilities.builder()
                .streaming(false)
                .pushNotifications(false)
                .build())
            .defaultInputModes(List.of("text"))
            .defaultOutputModes(List.of("text"))
            .skills(List.of(
                // Workflow Management Skills
                createSkill("launch-case", "Launch a new YAWL workflow case",
                    List.of("workflow", "launch", "case")),
                createSkill("cancel-case", "Cancel an existing YAWL workflow case",
                    List.of("workflow", "cancel", "case")),
                createSkill("monitor-case", "Monitor the status of a YAWL workflow case",
                    List.of("workflow", "monitor", "status")),

                // Task Handling Skills
                createSkill("checkout-workitem", "Check out a work item for processing",
                    List.of("workitem", "checkout", "task")),
                createSkill("checkin-workitem", "Check in a completed work item with results",
                    List.of("workitem", "checkin", "complete")),
                createSkill("delegate-workitem", "Delegate a work item to another agent",
                    List.of("workitem", "delegate", "transfer")),

                // Process Introspection Skills
                createSkill("list-specifications", "List available YAWL workflow specifications",
                    List.of("specification", "list", "query")),
                createSkill("get-specification", "Retrieve details of a specific workflow specification",
                    List.of("specification", "get", "details")),
                createSkill("list-cases", "List running workflow cases",
                    List.of("case", "list", "running")),
                createSkill("get-case-status", "Get the current status of a workflow case",
                    List.of("case", "status", "query")),

                // Data Transformation Skills
                createSkill("xml-to-json", "Convert XML workflow data to JSON format",
                    List.of("transform", "xml", "json")),
                createSkill("json-to-xml", "Convert JSON workflow data to XML format",
                    List.of("transform", "json", "xml")),
                createSkill("transform-data", "Transform workflow data using specified rules",
                    List.of("transform", "data", "convert"))
            ))
            .supportedInterfaces(List.of(
                new AgentInterface(
                    "a2a-rest",
                    "http://localhost:" + restPort + restPath
                )
            ))
            .build();

        LOGGER.info("YAWL A2A Agent Card initialized with {} skills",
                   this.agentCard.skills().size());
    }

    /**
     * Gets the configured agent card.
     *
     * @return the agent card instance
     */
    public AgentCard getAgentCard() {
        return agentCard;
    }

    /**
     * Creates an agent skill with the given name, description, and tags.
     *
     * @param name the skill name
     * @param description the skill description
     * @param tags the skill tags
     * @return a new AgentSkill instance
     */
    private AgentSkill createSkill(String name, String description, List<String> tags) {
        return AgentSkill.builder()
            .id(name)
            .name(name)
            .description(description)
            .tags(tags)
            .inputModes(List.of("text"))
            .outputModes(List.of("text"))
            .build();
    }

    /**
     * Gets the agent name.
     *
     * @return the agent name
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * Gets the agent version.
     *
     * @return the agent version
     */
    public String getAgentVersion() {
        return agentVersion;
    }

    /**
     * Gets the agent description.
     *
     * @return the agent description
     */
    public String getAgentDescription() {
        return agentDescription;
    }
}
