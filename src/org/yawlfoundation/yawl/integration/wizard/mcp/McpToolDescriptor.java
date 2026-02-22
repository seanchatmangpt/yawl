package org.yawlfoundation.yawl.integration.wizard.mcp;

import java.util.List;
import java.util.Objects;

/**
 * Descriptor for an MCP (Model Context Protocol) tool available in YAWL.
 *
 * <p>Captures tool metadata for capability matching in the wizard. Each tool has:
 * <ul>
 *   <li>toolId: unique identifier (e.g., "launch_case")</li>
 *   <li>displayName: human-readable name for UI</li>
 *   <li>description: detailed description of what the tool does</li>
 *   <li>parameterNames: required input parameters</li>
 *   <li>outputFields: fields/values returned on successful execution</li>
 *   <li>category: functional category (CASE_MANAGEMENT, SPECIFICATION, WORKITEM, LIFECYCLE)</li>
 *   <li>complexityScore: 1-10, how complex to configure/use</li>
 *   <li>requiresEngineSession: whether tool needs active YAWL engine session</li>
 * </ul>
 *
 * <p>All tools require an active engine session to execute.
 *
 * @param toolId unique identifier for the tool (e.g., "launch_case")
 * @param displayName human-readable display name for UI
 * @param description detailed description of tool functionality
 * @param parameterNames list of required input parameter names
 * @param outputFields list of output field names returned by the tool
 * @param category functional category of this tool
 * @param complexityScore complexity score (1-10, higher = more complex)
 * @param requiresEngineSession whether tool requires active YAWL engine session
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record McpToolDescriptor(
    String toolId,
    String displayName,
    String description,
    List<String> parameterNames,
    List<String> outputFields,
    McpToolCategory category,
    int complexityScore,
    boolean requiresEngineSession
) {
    /**
     * Compact constructor validates all fields.
     */
    public McpToolDescriptor {
        Objects.requireNonNull(toolId, "toolId cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(parameterNames, "parameterNames cannot be null");
        Objects.requireNonNull(outputFields, "outputFields cannot be null");
        Objects.requireNonNull(category, "category cannot be null");

        if (toolId.isEmpty()) {
            throw new IllegalArgumentException("toolId cannot be empty");
        }
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName cannot be empty");
        }
        if (description.isEmpty()) {
            throw new IllegalArgumentException("description cannot be empty");
        }
        if (complexityScore < 1 || complexityScore > 10) {
            throw new IllegalArgumentException("complexityScore must be between 1 and 10");
        }

        // Defensive copies for immutability
        parameterNames = List.copyOf(parameterNames);
        outputFields = List.copyOf(outputFields);
    }

    /**
     * Factory method to construct a new descriptor.
     *
     * @param toolId unique identifier
     * @param displayName human-readable name
     * @param description detailed description
     * @param parameterNames list of required parameters
     * @param outputFields list of output fields
     * @param category functional category
     * @param complexityScore 1-10 complexity score
     * @param requiresEngineSession whether engine session needed
     * @return new descriptor
     */
    public static McpToolDescriptor of(
            String toolId,
            String displayName,
            String description,
            List<String> parameterNames,
            List<String> outputFields,
            McpToolCategory category,
            int complexityScore,
            boolean requiresEngineSession) {
        return new McpToolDescriptor(
            toolId,
            displayName,
            description,
            parameterNames,
            outputFields,
            category,
            complexityScore,
            requiresEngineSession
        );
    }
}
