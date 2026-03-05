/**
 * Spring AI Model Context Protocol (MCP) integration for YAWL.
 *
 * <p>This package provides Spring-managed MCP tools and resources that expose
 * YAWL workflow engine capabilities through the Model Context Protocol. It bridges
 * YAWL's existing MCP implementation with Spring's dependency injection and
 * lifecycle management.</p>
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li><b>Configuration:</b> {@link YawlMcpConfiguration} - Spring configuration
 *       for MCP server and YAWL clients</li>
 *   <li><b>Tools:</b> {@link YawlMcpToolRegistry} - Spring-managed MCP tool
 *       registration and lifecycle</li>
 *   <li><b>Resources:</b> {@link YawlMcpResourceRegistry} - Spring-managed MCP
 *       resource providers</li>
 *   <li><b>Properties:</b> {@link YawlMcpProperties} - Spring Boot configuration
 *       properties for YAWL MCP integration</li>
 *   <li><b>Health:</b> {@link YawlMcpHealthIndicator} - Spring Actuator health
 *       indicator for MCP server status</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Dependency injection for all YAWL clients (InterfaceB, InterfaceA)</li>
 *   <li>Spring-managed lifecycle (startup, shutdown, reconnection)</li>
 *   <li>Support for both STDIO and HTTP transports</li>
 *   <li>Configuration via application.yml or application.properties</li>
 *   <li>Integration with Spring Actuator for health monitoring</li>
 *   <li>Automatic tool and resource registration from existing implementations</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // application.yml
 * yawl:
 *   mcp:
 *     enabled: true
 *     engine-url: http://localhost:8080/yawl
 *     username: admin
 *     password: YAWL
 *     transport: stdio  # or http
 *     http:
 *       port: 8081
 *       path: /mcp
 *
 * // Spring Boot Application
 * @SpringBootApplication
 * @EnableYawlMcp
 * public class YawlMcpApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(YawlMcpApplication.class, args);
 *     }
 * }
 *
 * // Custom Tool
 * @Component
 * public class CustomYawlTool implements YawlMcpTool {
 *     @Autowired
 *     private InterfaceB_EnvironmentBasedClient interfaceBClient;
 *
 *     @Override
 *     public String getName() { return "custom_workflow_operation"; }
 *
 *     @Override
 *     public McpSchema.CallToolResult execute(Map<String, Object> params) {
 *         // Use injected interfaceBClient for YAWL operations
 *     }
 * }
 * }</pre>
 *
 * <h2>Integration with Existing MCP Implementation</h2>
 * <p>This Spring integration wraps YAWL's existing MCP tools and resources
 * ({@link org.yawlfoundation.yawl.integration.mcp.spec.YawlToolSpecifications},
 * {@link org.yawlfoundation.yawl.integration.mcp.resource.YawlResourceProvider})
 * and makes them available as Spring beans with full dependency injection support.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @since 5.2
 */
package org.yawlfoundation.yawl.integration.mcp.spring;
