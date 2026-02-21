package org.yawlfoundation.yawl.integration.wizard.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStep;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStepResult;

/**
 * Configures MCP tool bindings based on selected workflow pattern and requirements.
 *
 * <p>This step executes during the MCP_CONFIG phase and performs the following:
 * <ul>
 *   <li>Read available tools from session context (set by McpDiscoveryStep)</li>
 *   <li>Read selected workflow pattern from session context</li>
 *   <li>Use McpCapabilityMatcher to match tools to task slots</li>
 *   <li>Build McpWizardConfiguration with selected tools and bindings</li>
 *   <li>Store configuration in session context</li>
 *   <li>Return configuration as step result</li>
 * </ul>
 *
 * <p>Session context keys consumed:
 * <ul>
 *   <li>"mcp.tools.all" (List&lt;McpToolDescriptor&gt;) - from McpDiscoveryStep</li>
 *   <li>"workflow.pattern" (String) - e.g., "WP-1", "WP-2" from pattern selection</li>
 *   <li>"workflow.requirements" (List&lt;String&gt;) - optional user requirements</li>
 *   <li>"mcp.transport" (String) - optional transport choice ("STDIO" or "HTTP")</li>
 *   <li>"engine.url" (String) - optional engine URL</li>
 * </ul>
 *
 * <p>Session context keys produced:
 * <ul>
 *   <li>"mcp.configuration" (McpWizardConfiguration) - final MCP configuration</li>
 *   <li>"mcp.task.bindings" (Map&lt;String, McpToolDescriptor&gt;) - tool bindings</li>
 *   <li>"mcp.configuration.timestamp" (String) - ISO-8601 timestamp</li>
 * </ul>
 *
 * <p>This step is skippable if MCP configuration has already been completed.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class McpConfigurationStep implements WizardStep<McpWizardConfiguration> {

    private final McpCapabilityMatcher matcher = new McpCapabilityMatcher();

    @Override
    public String stepId() {
        return "mcp-configuration";
    }

    @Override
    public String title() {
        return "Configure MCP Tool Bindings";
    }

    @Override
    public String description() {
        return "Match available MCP tools to workflow task slots based on the selected " +
               "workflow pattern and capability requirements.";
    }

    @Override
    public WizardPhase requiredPhase() {
        return WizardPhase.MCP_CONFIG;
    }

    @Override
    public boolean isSkippable() {
        return true;
    }

    @Override
    public List<String> validatePrerequisites(WizardSession session) {
        var errors = new java.util.ArrayList<String>();

        if (!session.has("mcp.tools.all")) {
            errors.add("MCP tools must be discovered first (McpDiscoveryStep required)");
        }

        if (!session.has("workflow.pattern")) {
            errors.add("Workflow pattern must be selected before MCP configuration");
        }

        return errors;
    }

    @Override
    public WizardStepResult<McpWizardConfiguration> execute(WizardSession session) {
        try {
            // Validate prerequisites
            var prereqErrors = validatePrerequisites(session);
            if (!prereqErrors.isEmpty()) {
                return WizardStepResult.failure(stepId(), prereqErrors);
            }

            // Load available tools from session context
            @SuppressWarnings("unchecked")
            Optional<List<McpToolDescriptor>> toolsOpt = (Optional<List<McpToolDescriptor>>) (Optional<?>) session.get(
                "mcp.tools.all", List.class);
            if (toolsOpt.isEmpty()) {
                return WizardStepResult.failure(stepId(),
                    "No MCP tools found in session context");
            }
            List<McpToolDescriptor> availableTools = toolsOpt.get();

            // Load selected pattern
            Optional<String> patternOpt = session.get("workflow.pattern", String.class);
            if (patternOpt.isEmpty()) {
                return WizardStepResult.failure(stepId(),
                    "No workflow pattern selected in session context");
            }
            String patternCode = patternOpt.get();

            // Load optional requirements
            List<String> requirements = session.get("workflow.requirements", List.class)
                .orElse(List.of());

            // Get recommended tools for the pattern
            List<McpToolDescriptor> recommendedTools =
                McpToolRegistry.recommendedForPattern(patternCode);

            // Filter available tools to recommended set
            List<McpToolDescriptor> toolsForPattern = availableTools.stream()
                .filter(tool -> recommendedTools.stream()
                    .anyMatch(rec -> rec.toolId().equals(tool.toolId())))
                .toList();

            // Match tools to task slots
            Map<String, McpToolDescriptor> taskSlotBindings =
                matcher.match(patternCode, toolsForPattern, requirements);

            // Load configuration parameters
            String mcpTransport = session.get("mcp.transport", String.class)
                .orElse("STDIO");
            String engineUrl = session.get("engine.url", String.class)
                .orElse("http://localhost:8080/yawl");

            // Build configuration
            McpWizardConfiguration config = McpWizardConfiguration.of(
                toolsForPattern,
                taskSlotBindings,
                mcpTransport,
                engineUrl
            );

            return WizardStepResult.success(stepId(), config);

        } catch (Exception e) {
            return WizardStepResult.failure(stepId(),
                "Failed to configure MCP tools: " + e.getMessage());
        }
    }
}
