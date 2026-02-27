package org.yawlfoundation.yawl.integration.mcp.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import io.modelcontextprotocol.server.McpServerFeatures;

/**
 * Extensible registry for YAWL MCP tool providers.
 *
 * <p>Decouples tool creation from server configuration: {@code YawlMcpServer}
 * calls {@link #createAll(YawlMcpContext)} once during startup; new tool sets
 * are contributed by registering {@link McpToolProvider} instances without
 * modifying the server.
 *
 * <p>The default provider (registered at class load) wraps the 16 core
 * {@link YawlToolSpecifications} tools. Third-party providers register via
 * {@link #register(McpToolProvider)} before {@code YawlMcpServer.start()}.
 *
 * <p>Example — marketplace agent adds tools at startup:
 * <pre>{@code
 * // In your autonomous agent or service initializer:
 * McpToolRegistry.register(ctx -> buildMarketplaceTools(ctx));
 *
 * // YawlMcpServer.start() picks up all registered providers:
 * YawlMcpServer server = new YawlMcpServer(engineUrl, username, password);
 * server.start();  // includes marketplace tools automatically
 * }</pre>
 *
 * <p>Thread safety: the provider list uses {@link CopyOnWriteArrayList}, so
 * {@link #register} and {@link #createAll} are safe to call from multiple threads.
 * Providers registered after {@code createAll()} returns will not appear in that
 * server's tool list — register before {@code YawlMcpServer.start()}.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see McpToolProvider
 * @see YawlMcpContext
 */
public final class McpToolRegistry {

    private static final Logger LOGGER = Logger.getLogger(McpToolRegistry.class.getName());

    /**
     * Ordered list of registered providers. CopyOnWriteArrayList allows safe
     * concurrent reads during createAll() while registrations proceed.
     */
    private static final CopyOnWriteArrayList<McpToolProvider> PROVIDERS =
            new CopyOnWriteArrayList<>();

    static {
        // Default provider: 16 core YAWL workflow tools
        PROVIDERS.add(ctx -> YawlToolSpecifications.createAll(
                ctx.interfaceBClient(),
                ctx.interfaceAClient(),
                ctx.sessionHandle(),
                ctx.zaiFunctionService()));
    }

    private McpToolRegistry() {
        throw new UnsupportedOperationException(
            "McpToolRegistry is a static utility class and cannot be instantiated.");
    }

    /**
     * Register an additional tool provider.
     *
     * <p>Providers are called in registration order during {@link #createAll}.
     * Register before {@code YawlMcpServer.start()} to include tools in the
     * server's initial capability set.
     *
     * @param provider non-null tool provider to add
     * @throws IllegalArgumentException if {@code provider} is null
     */
    public static void register(McpToolProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        PROVIDERS.add(provider);
        LOGGER.fine("Registered MCP tool provider: " + provider.getClass().getSimpleName());
    }

    /**
     * Remove a previously registered provider.
     *
     * <p>Primarily useful in tests to restore the registry to a known state
     * via {@link #reset()}.
     *
     * @param provider provider to remove
     * @return true if the provider was registered and has been removed
     */
    public static boolean unregister(McpToolProvider provider) {
        return PROVIDERS.remove(provider);
    }

    /**
     * Create all tool specifications from every registered provider.
     *
     * <p>Called once by {@code YawlMcpServer.start()} with the live engine
     * context. Each provider's {@link McpToolProvider#createTools(YawlMcpContext)}
     * is invoked in registration order; all results are concatenated.
     *
     * @param context live YAWL engine context (non-null)
     * @return combined list of all tool specifications from all providers
     * @throws IllegalArgumentException if {@code context} is null
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(YawlMcpContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        List<McpServerFeatures.SyncToolSpecification> all = new ArrayList<>();

        for (McpToolProvider provider : PROVIDERS) {
            List<McpServerFeatures.SyncToolSpecification> tools = provider.createTools(context);
            if (tools != null) {
                all.addAll(tools);
            }
        }

        LOGGER.info("McpToolRegistry assembled " + all.size() + " tools from " +
                    PROVIDERS.size() + " provider(s)");
        return all;
    }

    /**
     * Return the number of currently registered providers.
     *
     * @return provider count (always ≥ 1 due to the default core tools provider)
     */
    public static int providerCount() {
        return PROVIDERS.size();
    }

    /**
     * Reset the registry to its initial state (default provider only).
     *
     * <p><b>Test use only.</b> Removes all third-party providers, restoring
     * the default core-tools provider. Never call in production code.
     */
    public static void reset() {
        PROVIDERS.clear();
        PROVIDERS.add(ctx -> YawlToolSpecifications.createAll(
                ctx.interfaceBClient(),
                ctx.interfaceAClient(),
                ctx.sessionHandle(),
                ctx.zaiFunctionService()));
    }
}
