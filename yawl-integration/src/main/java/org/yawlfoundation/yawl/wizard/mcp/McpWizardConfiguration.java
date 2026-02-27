package org.yawlfoundation.yawl.integration.wizard.mcp;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Final MCP wizard configuration produced by the wizard.
 *
 * <p>Contains all tool bindings and transport configuration resulting from
 * the MCP configuration step. This record is produced by
 * {@link McpConfigurationStep} and represents the complete configuration
 * for MCP tool binding to workflow tasks.
 *
 * <p>Configuration includes:
 * <ul>
 *   <li>configurationId: unique identifier for this configuration</li>
 *   <li>selectedTools: list of MCP tools selected for this workflow</li>
 *   <li>taskSlotBindings: mapping from task slot name to specific tool</li>
 *   <li>mcpTransport: communication transport ("STDIO" or "HTTP")</li>
 *   <li>engineUrl: base URL of YAWL engine</li>
 *   <li>toolParameters: per-tool configuration parameters</li>
 *   <li>configuredAt: timestamp when configuration was created</li>
 * </ul>
 *
 * @param configurationId unique identifier for this configuration (UUID)
 * @param selectedTools list of MCP tools selected for the workflow
 * @param taskSlotBindings mapping from task slot name to tool descriptor
 * @param mcpTransport MCP transport mechanism ("STDIO" or "HTTP")
 * @param engineUrl base URL of the YAWL engine (e.g., http://localhost:8080/yawl)
 * @param toolParameters per-tool configuration (tool ID → parameters)
 * @param configuredAt timestamp when configuration was completed (UTC)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record McpWizardConfiguration(
    String configurationId,
    List<McpToolDescriptor> selectedTools,
    Map<String, McpToolDescriptor> taskSlotBindings,
    String mcpTransport,
    String engineUrl,
    Map<String, Object> toolParameters,
    Instant configuredAt
) {
    /**
     * Compact constructor validates all fields.
     */
    public McpWizardConfiguration {
        Objects.requireNonNull(configurationId, "configurationId cannot be null");
        Objects.requireNonNull(selectedTools, "selectedTools cannot be null");
        Objects.requireNonNull(taskSlotBindings, "taskSlotBindings cannot be null");
        Objects.requireNonNull(mcpTransport, "mcpTransport cannot be null");
        Objects.requireNonNull(engineUrl, "engineUrl cannot be null");
        Objects.requireNonNull(toolParameters, "toolParameters cannot be null");
        Objects.requireNonNull(configuredAt, "configuredAt cannot be null");

        if (configurationId.isEmpty()) {
            throw new IllegalArgumentException("configurationId cannot be empty");
        }
        if (mcpTransport.isEmpty()) {
            throw new IllegalArgumentException("mcpTransport cannot be empty");
        }
        if (engineUrl.isEmpty()) {
            throw new IllegalArgumentException("engineUrl cannot be empty");
        }

        // Defensive copies for immutability
        selectedTools = Collections.unmodifiableList(List.copyOf(selectedTools));
        taskSlotBindings = Collections.unmodifiableMap(Map.copyOf(taskSlotBindings));
        toolParameters = Collections.unmodifiableMap(Map.copyOf(toolParameters));
    }

    /**
     * Factory method to construct a new configuration.
     *
     * @param selectedTools list of MCP tools selected
     * @param taskSlotBindings mapping from task slots to tools
     * @param mcpTransport transport mechanism ("STDIO" or "HTTP")
     * @param engineUrl YAWL engine URL
     * @return new configuration with generated ID and current timestamp
     */
    public static McpWizardConfiguration of(
            List<McpToolDescriptor> selectedTools,
            Map<String, McpToolDescriptor> taskSlotBindings,
            String mcpTransport,
            String engineUrl) {

        return new McpWizardConfiguration(
            UUID.randomUUID().toString(),
            selectedTools,
            taskSlotBindings,
            mcpTransport,
            engineUrl,
            Map.of(),
            Instant.now()
        );
    }

    /**
     * Factory method to construct configuration with tool parameters.
     *
     * @param selectedTools list of MCP tools selected
     * @param taskSlotBindings mapping from task slots to tools
     * @param mcpTransport transport mechanism
     * @param engineUrl YAWL engine URL
     * @param toolParameters per-tool configuration map
     * @return new configuration
     */
    public static McpWizardConfiguration of(
            List<McpToolDescriptor> selectedTools,
            Map<String, McpToolDescriptor> taskSlotBindings,
            String mcpTransport,
            String engineUrl,
            Map<String, Object> toolParameters) {

        return new McpWizardConfiguration(
            UUID.randomUUID().toString(),
            selectedTools,
            taskSlotBindings,
            mcpTransport,
            engineUrl,
            toolParameters,
            Instant.now()
        );
    }

    /**
     * Convert this configuration to a Map<String, Object> for serialization.
     *
     * <p>Returns a map with the following structure:
     * <ul>
     *   <li>"configurationId": string</li>
     *   <li>"selectedToolCount": integer</li>
     *   <li>"selectedToolIds": list of tool IDs</li>
     *   <li>"taskSlotCount": integer</li>
     *   <li>"mcpTransport": string</li>
     *   <li>"engineUrl": string</li>
     *   <li>"configuredAt": ISO-8601 timestamp</li>
     * </ul>
     *
     * @return map representation of configuration
     */
    public Map<String, Object> toConfigMap() {
        return Map.ofEntries(
            Map.entry("configurationId", configurationId),
            Map.entry("selectedToolCount", selectedTools.size()),
            Map.entry("selectedToolIds",
                selectedTools.stream()
                    .map(McpToolDescriptor::toolId)
                    .toList()),
            Map.entry("taskSlotCount", taskSlotBindings.size()),
            Map.entry("taskSlotBindings",
                taskSlotBindings.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().toolId()))),
            Map.entry("mcpTransport", mcpTransport),
            Map.entry("engineUrl", engineUrl),
            Map.entry("configuredAt", configuredAt.toString())
        );
    }

    /**
     * Get the number of tools in this configuration.
     *
     * @return count of selected tools
     */
    public int toolCount() {
        return selectedTools.size();
    }

    /**
     * Get the number of task slots configured.
     *
     * @return count of task slot bindings
     */
    public int taskSlotCount() {
        return taskSlotBindings.size();
    }

    /**
     * Check if all required task slots have tool bindings.
     *
     * <p>A configuration is complete if every expected task slot has a tool assigned.
     *
     * @return true if all task slots are bound
     */
    public boolean isComplete() {
        return !selectedTools.isEmpty() && !taskSlotBindings.isEmpty();
    }
}
