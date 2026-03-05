/**
 * MCP (Model Context Protocol) wizard steps for the Autonomic A2A/MCP Wizard.
 *
 * <p>This package provides the discovery, configuration, and capability matching
 * steps for configuring MCP tool bindings in YAWL workflow patterns. It discovers
 * the 15 MCP tools exposed by {@link org.yawlfoundation.yawl.integration.mcp.YawlMcpServer}
 * and matches them to workflow task slots based on pattern requirements.
 *
 * <p><strong>Key Classes:</strong>
 *
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.mcp.McpToolDescriptor}
 *       — Immutable descriptor for an MCP tool with metadata (parameters, outputs, category, complexity)</li>
 *
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.mcp.McpToolCategory}
 *       — Enum for tool categories (CASE_MANAGEMENT, SPECIFICATION, WORKITEM, LIFECYCLE)</li>
 *
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.mcp.McpToolRegistry}
 *       — Static registry of all 15 MCP tools with lookup and filtering</li>
 *
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.mcp.McpDiscoveryStep}
 *       — Wizard step that discovers all available MCP tools (DISCOVERY phase)</li>
 *
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.mcp.McpCapabilityMatcher}
 *       — Autonomic capability matching engine (van der Aalst RP-1)</li>
 *
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.mcp.McpWizardConfiguration}
 *       — Final MCP configuration produced by the wizard</li>
 *
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.mcp.McpConfigurationStep}
 *       — Wizard step that configures tool bindings (MCP_CONFIG phase)</li>
 * </ul>
 *
 * <p><strong>15 MCP Tools Included:</strong>
 *
 * <p>CASE_MANAGEMENT (4 tools):
 * <ul>
 *   <li>launch_case — Launch new workflow case</li>
 *   <li>cancel_case — Cancel running case</li>
 *   <li>get_case_state — Get current case status</li>
 *   <li>get_running_cases — List all running cases</li>
 * </ul>
 *
 * <p>SPECIFICATION (4 tools):
 * <ul>
 *   <li>list_specifications — List loaded specifications</li>
 *   <li>get_specification — Get specification XML</li>
 *   <li>upload_specification — Upload new specification</li>
 *   <li>unload_specification — Unload specification</li>
 * </ul>
 *
 * <p>WORKITEM (5 tools):
 * <ul>
 *   <li>get_workitems — List all work items</li>
 *   <li>get_workitems_for_case — Get work items for specific case</li>
 *   <li>checkout_workitem — Claim work item for execution</li>
 *   <li>checkin_workitem — Complete work item with output data</li>
 *   <li>skip_workitem — Skip work item (if allowed)</li>
 * </ul>
 *
 * <p>LIFECYCLE (2 tools):
 * <ul>
 *   <li>suspend_case — Suspend case execution</li>
 *   <li>resume_case — Resume suspended case</li>
 * </ul>
 *
 * <p><strong>Wizard Phases:</strong>
 *
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.mcp.McpDiscoveryStep}
 *       operates in DISCOVERY phase</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.mcp.McpConfigurationStep}
 *       operates in MCP_CONFIG phase</li>
 * </ul>
 *
 * <p><strong>Matching Strategy (van der Aalst RP-1):</strong>
 *
 * <p>The {@link org.yawlfoundation.yawl.integration.wizard.mcp.McpCapabilityMatcher}
 * implements Direct Allocation resource pattern (RP-1) from van der Aalst's resource
 * patterns. Tools are allocated directly to workflow task slots based on:
 *
 * <ul>
 *   <li>Category fit (40 points): tool category matches task slot function</li>
 *   <li>Keyword matching (30 points): task slot keywords appear in tool description</li>
 *   <li>Complexity (20 points): prefer tools with lower configuration complexity</li>
 *   <li>Requirements (10 points): tool meets user-specified requirements</li>
 * </ul>
 *
 * <p>Scoring produces 0-100 rating; highest-scoring tool is selected for each task slot.
 *
 * <p><strong>Example Usage:</strong>
 *
 * <pre>{@code
 * // 1. Discover tools
 * WizardSession session = WizardSession.newSession();
 * var discoveryStep = new McpDiscoveryStep();
 * var discoveryResult = discoveryStep.execute(
 *     session.withPhase(WizardPhase.DISCOVERY, "init", "Starting discovery"));
 *
 * // 2. Configure based on pattern
 * WizardSession withTools = session
 *     .withContext("mcp.tools.all", discoveryResult.value())
 *     .withContext("workflow.pattern", "WP-1");
 *
 * var configStep = new McpConfigurationStep();
 * var configResult = configStep.execute(
 *     withTools.withPhase(WizardPhase.MCP_CONFIG, "config", "Configuring tools"));
 *
 * McpWizardConfiguration config = configResult.value();
 * System.out.println("Selected " + config.toolCount() + " tools");
 * System.out.println("Bindings: " + config.taskSlotBindings());
 * }</pre>
 *
 * @see org.yawlfoundation.yawl.integration.wizard.core
 * @see org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.integration.wizard.mcp;
