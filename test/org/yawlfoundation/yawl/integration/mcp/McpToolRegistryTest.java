package org.yawlfoundation.yawl.integration.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.spec.McpToolProvider;
import org.yawlfoundation.yawl.integration.mcp.spec.McpToolRegistry;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlMcpContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for McpToolRegistry.
 *
 * <p>Uses real InterfaceB/InterfaceA clients (constructed, not connected — no engine needed
 * to verify tool specification assembly). All tool provider interactions are real;
 * no mocks used.
 *
 * <p>Each test restores registry state via {@link McpToolRegistry#reset()} in @AfterEach
 * to prevent cross-test contamination from the static provider list.
 *
 * <p>SAME_THREAD execution is required because the registry uses shared static state:
 * concurrent JUnit 5 execution would interleave test body and @AfterEach reset(),
 * producing non-deterministic provider counts.
 */
@Execution(ExecutionMode.SAME_THREAD)
class McpToolRegistryTest {

    private static final String TEST_ENGINE_URL = "http://localhost:8080/yawl";
    private static final String TEST_SESSION = "test-session-handle";

    // Real clients — constructor-only, no network calls made
    private final InterfaceB_EnvironmentBasedClient interfaceB =
            new InterfaceB_EnvironmentBasedClient(TEST_ENGINE_URL + "/ib");
    private final InterfaceA_EnvironmentBasedClient interfaceA =
            new InterfaceA_EnvironmentBasedClient(TEST_ENGINE_URL + "/ia");

    @AfterEach
    void restoreRegistry() {
        McpToolRegistry.reset();
    }

    // =========================================================================
    // Default provider
    // =========================================================================

    @Test
    void defaultProviderIsRegisteredAtStartup() {
        assertEquals(1, McpToolRegistry.providerCount(),
            "Registry must ship with exactly 1 default provider (core tools)");
    }

    @Test
    void createAllReturns16CoreToolsByDefault() {
        YawlMcpContext ctx = new YawlMcpContext(interfaceB, interfaceA, TEST_SESSION, null);
        List<McpServerFeatures.SyncToolSpecification> tools = McpToolRegistry.createAll(ctx);

        assertEquals(16, tools.size(),
            "Default provider must contribute exactly 16 core YAWL tools");
    }

    @Test
    void coreToolNamesAreDistinct() {
        YawlMcpContext ctx = new YawlMcpContext(interfaceB, interfaceA, TEST_SESSION, null);
        List<McpServerFeatures.SyncToolSpecification> tools = McpToolRegistry.createAll(ctx);

        long distinctNames = tools.stream()
            .map(t -> t.tool().name())
            .distinct()
            .count();
        assertEquals(tools.size(), distinctNames, "All tool names must be unique");
    }

    @Test
    void coreToolNamesAllStartWithYawlPrefix() {
        YawlMcpContext ctx = new YawlMcpContext(interfaceB, interfaceA, TEST_SESSION, null);
        List<McpServerFeatures.SyncToolSpecification> tools = McpToolRegistry.createAll(ctx);

        tools.forEach(spec ->
            assertTrue(spec.tool().name().startsWith("yawl_"),
                "Tool name must have 'yawl_' prefix: " + spec.tool().name()));
    }

    // =========================================================================
    // Provider registration
    // =========================================================================

    @Test
    void registerAddsProviderToCount() {
        McpToolProvider extra = ctx -> List.of();
        McpToolRegistry.register(extra);
        assertEquals(2, McpToolRegistry.providerCount());
    }

    @Test
    void registerThrowsOnNullProvider() {
        assertThrows(IllegalArgumentException.class,
            () -> McpToolRegistry.register(null));
    }

    @Test
    void registeredProviderToolsAppearInCreateAll() {
        McpSchema.JsonSchema emptySchema = new McpSchema.JsonSchema(
            "object", null, List.of(), false, null, null);
        McpSchema.Tool customTool = McpSchema.Tool.builder()
            .name("yawl_custom_analytics")
            .description("Custom analytics tool for testing")
            .inputSchema(emptySchema)
            .build();
        McpServerFeatures.SyncToolSpecification customSpec =
            new McpServerFeatures.SyncToolSpecification(customTool, (ex, req) -> null);

        McpToolRegistry.register(ctx -> List.of(customSpec));

        YawlMcpContext context = new YawlMcpContext(interfaceB, interfaceA, TEST_SESSION, null);
        List<McpServerFeatures.SyncToolSpecification> tools = McpToolRegistry.createAll(context);

        assertEquals(17, tools.size(), "16 core + 1 custom = 17 total tools");
        assertTrue(tools.contains(customSpec), "Custom tool must appear in assembled list");
    }

    @Test
    void multipleProvidersAccumulate() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", null, List.of(), false, null, null);

        McpToolProvider provider1 = ctx -> List.of(
            new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder().name("yawl_tool_a").description("A")
                    .inputSchema(schema).build(),
                (ex, req) -> null));
        McpToolProvider provider2 = ctx -> List.of(
            new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder().name("yawl_tool_b").description("B")
                    .inputSchema(schema).build(),
                (ex, req) -> null));

        McpToolRegistry.register(provider1);
        McpToolRegistry.register(provider2);

        YawlMcpContext ctx = new YawlMcpContext(interfaceB, interfaceA, TEST_SESSION, null);
        assertEquals(18, McpToolRegistry.createAll(ctx).size(),
            "16 core + 1 from provider1 + 1 from provider2 = 18");
    }

    @Test
    void providerReturningNullListIsHandledGracefully() {
        McpToolRegistry.register(ctx -> null);  // provider returns null

        YawlMcpContext context = new YawlMcpContext(interfaceB, interfaceA, TEST_SESSION, null);
        // Must not throw NullPointerException
        List<McpServerFeatures.SyncToolSpecification> tools =
            assertDoesNotThrow(() -> McpToolRegistry.createAll(context));
        assertEquals(16, tools.size(), "Null-returning provider skipped; core tools still present");
    }

    // =========================================================================
    // Unregister
    // =========================================================================

    @Test
    void unregisterRemovesProvider() {
        McpToolProvider provider = ctx -> List.of();
        McpToolRegistry.register(provider);
        assertEquals(2, McpToolRegistry.providerCount());

        boolean removed = McpToolRegistry.unregister(provider);

        assertTrue(removed);
        assertEquals(1, McpToolRegistry.providerCount());
    }

    @Test
    void unregisterReturnsFalseForUnknownProvider() {
        McpToolProvider notRegistered = ctx -> List.of();
        assertFalse(McpToolRegistry.unregister(notRegistered));
    }

    // =========================================================================
    // Context validation
    // =========================================================================

    @Test
    void createAllThrowsOnNullContext() {
        assertThrows(IllegalArgumentException.class,
            () -> McpToolRegistry.createAll(null));
    }

    @Test
    void contextThrowsOnNullInterfaceB() {
        assertThrows(IllegalArgumentException.class,
            () -> new YawlMcpContext(null, interfaceA, TEST_SESSION, null));
    }

    @Test
    void contextThrowsOnNullInterfaceA() {
        assertThrows(IllegalArgumentException.class,
            () -> new YawlMcpContext(interfaceB, null, TEST_SESSION, null));
    }

    @Test
    void contextThrowsOnBlankSessionHandle() {
        assertThrows(IllegalArgumentException.class,
            () -> new YawlMcpContext(interfaceB, interfaceA, "  ", null));
    }

    @Test
    void contextAllowsNullZaiService() {
        // zaiFunctionService is optional — must not throw
        assertDoesNotThrow(
            () -> new YawlMcpContext(interfaceB, interfaceA, TEST_SESSION, null));
    }

    @Test
    void contextIsZaiAvailableReturnsFalseWhenNull() {
        YawlMcpContext ctx = new YawlMcpContext(interfaceB, interfaceA, TEST_SESSION, null);
        assertFalse(ctx.isZaiAvailable());
    }

    // =========================================================================
    // Reset
    // =========================================================================

    @Test
    void resetRestoresOneDefaultProvider() {
        McpToolRegistry.register(ctx -> List.of());
        McpToolRegistry.register(ctx -> List.of());
        assertEquals(3, McpToolRegistry.providerCount());

        McpToolRegistry.reset();

        assertEquals(1, McpToolRegistry.providerCount(),
            "reset() must restore exactly the default core-tools provider");
    }

    @Test
    void afterResetCreateAllStillReturns16CoreTools() {
        McpToolRegistry.register(ctx -> List.of());
        McpToolRegistry.reset();

        YawlMcpContext ctx = new YawlMcpContext(interfaceB, interfaceA, TEST_SESSION, null);
        assertEquals(16, McpToolRegistry.createAll(ctx).size());
    }
}
