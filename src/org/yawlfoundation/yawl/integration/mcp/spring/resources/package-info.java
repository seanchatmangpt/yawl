/**
 * Spring-managed MCP resource implementations for YAWL.
 *
 * <p>This package contains example implementations of {@link YawlMcpResource}
 * that demonstrate Spring dependency injection and lifecycle management
 * for MCP resources.</p>
 *
 * <h2>Example Resources</h2>
 * <ul>
 *   <li>{@link SpecificationsResource} - Static resource for loaded specifications</li>
 * </ul>
 *
 * <h2>Creating Custom Resources</h2>
 * <p>Implement {@link YawlMcpResource} and register as a Spring bean:</p>
 * <pre>{@code
 * @Component
 * public class MyCustomResource implements YawlMcpResource {
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
 * <h2>Resource Templates</h2>
 * <p>For parameterized URIs, set {@code isTemplate()} to true:</p>
 * <pre>{@code
 * @Override
 * public String getUri() {
 *     return "yawl://cases/{caseId}";
 * }
 *
 * @Override
 * public boolean isTemplate() {
 *     return true;
 * }
 *
 * @Override
 * public McpSchema.ReadResourceResult read(String uri) {
 *     // Extract parameter: yawl://cases/42 -> "42"
 *     String caseId = uri.substring("yawl://cases/".length());
 *     // Use caseId to fetch real data from YAWL engine...
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
package org.yawlfoundation.yawl.integration.mcp.spring.resources;

import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpResource;
