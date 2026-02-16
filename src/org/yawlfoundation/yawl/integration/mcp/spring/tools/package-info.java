/**
 * Spring-managed MCP tool implementations for YAWL.
 *
 * <p>This package contains example implementations of {@link YawlMcpTool}
 * that demonstrate Spring dependency injection and lifecycle management
 * for MCP tools.</p>
 *
 * <h2>Example Tools</h2>
 * <ul>
 *   <li>{@link LaunchCaseTool} - Launch workflow cases with Spring DI</li>
 * </ul>
 *
 * <h2>Creating Custom Tools</h2>
 * <p>Implement {@link YawlMcpTool} and register as a Spring bean:</p>
 * <pre>{@code
 * @Component
 * public class MyCustomTool implements YawlMcpTool {
 *     @Autowired
 *     private InterfaceB_EnvironmentBasedClient interfaceBClient;
 *
 *     @Autowired
 *     private YawlMcpSessionManager sessionManager;
 *
 *     // Implement interface methods...
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
package org.yawlfoundation.yawl.integration.mcp.spring.tools;

import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpTool;
