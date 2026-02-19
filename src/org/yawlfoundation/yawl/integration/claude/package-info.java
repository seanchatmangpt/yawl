/**
 * Claude Code CLI integration for YAWL self-upgrading capabilities.
 *
 * <p>This package provides MCP tool integration for executing Claude Code CLI
 * commands from YAWL workflows, enabling autonomous code generation, testing,
 * and deployment.</p>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.claude.ClaudeExecutionResult} -
 *       Immutable result record for CLI execution outcomes</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.claude.ClaudeCodeExecutor} -
 *       Process executor with pipe-based communication and timeout handling</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.claude.ClaudePromptSanitizer} -
 *       Security layer for prompt validation and sanitization</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.claude.ClaudeSessionManager} -
 *       Multi-turn conversation state management</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.claude.ExecuteClaudeTool} -
 *       MCP tool implementation for yawl_execute_claude</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create executor
 * ClaudeCodeExecutor executor = new ClaudeCodeExecutor();
 *
 * // Execute a simple prompt
 * ClaudeExecutionResult result = executor.execute("Generate a unit test for Calculator.java");
 *
 * if (result.success()) {
 *     System.out.println("Output: " + result.output());
 *     if (result.hasSession()) {
 *         // Continue conversation
 *         ClaudeExecutionResult followUp = executor.execute(
 *             "Now add edge case tests",
 *             result.sessionId(),
 *             null,
 *             Duration.ofMinutes(5),
 *             null
 *         );
 *     }
 * }
 * }</pre>
 *
 * <h2>MCP Tool Registration</h2>
 * <p>The {@code yawl_execute_claude} tool is registered with the MCP server
 * to enable YAWL workflows to control Claude Code CLI:</p>
 * <pre>{@code
 * ExecuteClaudeTool tool = new ExecuteClaudeTool(executor);
 * McpServerFeatures.SyncToolSpecification spec = tool.createSpecification();
 * // Register with MCP server
 * }</pre>
 *
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li>Shell metacharacters are escaped by default</li>
 *   <li>Dangerous commands (rm -rf, format, etc.) are blocked</li>
 *   <li>Credential patterns in prompts are rejected</li>
 *   <li>Allowed operations can be restricted per-request</li>
 *   <li>Maximum prompt length is enforced</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.claude;
