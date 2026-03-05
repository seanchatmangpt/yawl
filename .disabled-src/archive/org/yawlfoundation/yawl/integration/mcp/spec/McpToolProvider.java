package org.yawlfoundation.yawl.integration.mcp.spec;

import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures;

/**
 * Extension point for contributing MCP tool specifications to YAWL's MCP server.
 *
 * <p>Implement this interface to add new tool sets without modifying
 * {@code YawlMcpServer}. Register implementations via
 * {@link McpToolRegistry#register(McpToolProvider)}.
 *
 * <p>Example — add a custom analytics tool set:
 * <pre>{@code
 * public class AnalyticsToolProvider implements McpToolProvider {
 *     @Override
 *     public List<McpServerFeatures.SyncToolSpecification> createTools(YawlMcpContext ctx) {
 *         return List.of(createDashboardTool(ctx), createExportTool(ctx));
 *     }
 * }
 *
 * // Register once at startup (before YawlMcpServer.start()):
 * McpToolRegistry.register(new AnalyticsToolProvider());
 * }</pre>
 *
 * <p>Providers receive a {@link YawlMcpContext} containing the live YAWL engine
 * clients and session handle. Providers that require Z.AI should guard with
 * {@code ctx.isZaiAvailable()} before calling Z.AI APIs.
 *
 * <p>Providers must not return {@code null} — return an empty list if no tools
 * are available in the current environment.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see McpToolRegistry
 * @see YawlMcpContext
 */
@FunctionalInterface
public interface McpToolProvider {

    /**
     * Create the MCP tool specifications contributed by this provider.
     *
     * @param context live YAWL engine context with clients and session handle
     * @return non-null list of tool specifications (empty if none available)
     */
    List<McpServerFeatures.SyncToolSpecification> createTools(YawlMcpContext context);
}
