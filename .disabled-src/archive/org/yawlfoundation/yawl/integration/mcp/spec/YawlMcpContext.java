package org.yawlfoundation.yawl.integration.mcp.spec;

import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;

/**
 * Dependency context passed to all {@link McpToolProvider} implementations.
 *
 * <p>Encapsulates the four runtime dependencies needed to create YAWL MCP tool
 * specifications: the InterfaceB client for runtime workflow operations, the
 * InterfaceA client for design-time operations, the active session handle, and
 * the optional Z.AI function service for specification synthesis.
 *
 * <p>Usage — build once in {@code YawlMcpServer.start()} and pass to all providers:
 * <pre>{@code
 * YawlMcpContext ctx = new YawlMcpContext(
 *     interfaceBClient, interfaceAClient, sessionHandle, zaiService);
 * List<SyncToolSpecification> tools = McpToolRegistry.createAll(ctx);
 * }</pre>
 *
 * <p>{@code zaiFunctionService} is nullable — providers that require Z.AI must
 * check {@code ctx.zaiFunctionService() != null && ctx.zaiFunctionService().isInitialized()}
 * before invoking AI-powered operations.
 *
 * @param interfaceBClient  YAWL InterfaceB HTTP client for runtime operations (required)
 * @param interfaceAClient  YAWL InterfaceA HTTP client for design-time operations (required)
 * @param sessionHandle     active YAWL engine session handle (required, non-blank)
 * @param zaiFunctionService Z.AI function service for spec synthesis (optional, may be null)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record YawlMcpContext(
        InterfaceB_EnvironmentBasedClient interfaceBClient,
        InterfaceA_EnvironmentBasedClient interfaceAClient,
        String sessionHandle,
        ZaiFunctionService zaiFunctionService) {

    /**
     * Compact constructor — validates required fields.
     *
     * @throws IllegalArgumentException if any required field is null or blank
     */
    public YawlMcpContext {
        if (interfaceBClient == null) {
            throw new IllegalArgumentException(
                "interfaceBClient is required — provide a connected InterfaceB_EnvironmentBasedClient");
        }
        if (interfaceAClient == null) {
            throw new IllegalArgumentException(
                "interfaceAClient is required — provide a connected InterfaceA_EnvironmentBasedClient");
        }
        if (sessionHandle == null || sessionHandle.isBlank()) {
            throw new IllegalArgumentException(
                "sessionHandle is required — connect to the YAWL engine first");
        }
        // zaiFunctionService is intentionally nullable (Z.AI is optional)
    }

    /**
     * Returns true if Z.AI is available and initialized in this context.
     *
     * @return true if {@code zaiFunctionService} is non-null and initialized
     */
    public boolean isZaiAvailable() {
        return zaiFunctionService != null && zaiFunctionService.isInitialized();
    }
}
