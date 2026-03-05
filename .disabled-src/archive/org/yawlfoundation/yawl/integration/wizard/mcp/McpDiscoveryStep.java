package org.yawlfoundation.yawl.integration.wizard.mcp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStep;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStepResult;

/**
 * Autonomic discovery step that inventories available MCP tools.
 *
 * <p>This step executes during the DISCOVERY phase and performs the following:
 * <ul>
 *   <li>Load all 15 MCP tools from {@link McpToolRegistry}</li>
 *   <li>Group tools by category (CASE_MANAGEMENT, SPECIFICATION, WORKITEM, LIFECYCLE)</li>
 *   <li>Store tool list and category grouping in session context</li>
 *   <li>Return the complete tool list as step result</li>
 * </ul>
 *
 * <p>Session context keys produced:
 * <ul>
 *   <li>"mcp.tools.all" (List&lt;McpToolDescriptor&gt;): all 15 available tools</li>
 *   <li>"mcp.tool.count" (Integer): total count of tools (15)</li>
 *   <li>"mcp.tools.by.category" (Map&lt;McpToolCategory, List&lt;McpToolDescriptor&gt;&gt;): tools grouped by category</li>
 *   <li>"mcp.discovery.timestamp" (String): ISO-8601 timestamp when discovery completed</li>
 * </ul>
 *
 * <p>This step is always skippable if tools have already been discovered in the current session.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class McpDiscoveryStep implements WizardStep<List<McpToolDescriptor>> {

    @Override
    public String stepId() {
        return "mcp-discovery";
    }

    @Override
    public String title() {
        return "Discover MCP Tools";
    }

    @Override
    public String description() {
        return "Scan and inventory all available MCP (Model Context Protocol) tools " +
               "exposed by the YAWL engine.";
    }

    @Override
    public WizardPhase requiredPhase() {
        return WizardPhase.DISCOVERY;
    }

    @Override
    public boolean isSkippable() {
        return true;
    }

    @Override
    public WizardStepResult<List<McpToolDescriptor>> execute(WizardSession session) {
        try {
            // Load all tools from registry
            List<McpToolDescriptor> allTools = McpToolRegistry.allTools();

            // Group tools by category
            Map<McpToolCategory, List<McpToolDescriptor>> toolsByCategory =
                allTools.stream()
                    .collect(Collectors.groupingByConcurrent(
                        McpToolDescriptor::category,
                        Collectors.toUnmodifiableList()
                    ));

            // Record context for downstream steps
            WizardSession updatedSession = session
                .withContext("mcp.tools.all", allTools)
                .withContext("mcp.tool.count", allTools.size())
                .withContext("mcp.tools.by.category", toolsByCategory)
                .withContext("mcp.discovery.timestamp", java.time.Instant.now().toString());

            return WizardStepResult.success(stepId(), allTools);

        } catch (Exception e) {
            return WizardStepResult.failure(stepId(),
                "Failed to discover MCP tools: " + e.getMessage());
        }
    }
}
