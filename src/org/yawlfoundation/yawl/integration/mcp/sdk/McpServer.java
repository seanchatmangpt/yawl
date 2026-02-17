package org.yawlfoundation.yawl.integration.mcp.sdk;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;

/**
 * MCP Server builder factory.
 *
 * <p>Entry point for constructing an MCP server. Use {@link #sync(Object)} to obtain a
 * {@link SyncServerBuilder} and chain configuration calls before calling {@link SyncServerBuilder#build()}
 * to start the server.</p>
 *
 * <p>This class mirrors the {@code io.modelcontextprotocol.sdk.McpServer} API as defined in the
 * MCP specification (https://modelcontextprotocol.io/specification/2024-11-05/basic/transports).</p>
 *
 * <p><b>Implementation Note:</b> This class is the bridge between YAWL's MCP integration layer
 * and the underlying MCP transport. The {@link SyncServerBuilder#build()} method throws
 * {@link UnsupportedOperationException} because the official MCP Java SDK transport implementation
 * ({@code io.modelcontextprotocol:mcp}) is not yet distributed via Maven Central. The YAWL MCP
 * integration compiles and all type-checked logic is correct; however, a running server requires
 * the real SDK jar at runtime. Migration path: add the {@code io.modelcontextprotocol:mcp}
 * dependency and replace this class with the official {@code McpServer} from that artifact.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class McpServer {

    private McpServer() {
        throw new UnsupportedOperationException("Use McpServer.sync(transportProvider) to create a server builder");
    }

    /**
     * Creates a synchronous MCP server builder bound to the given transport provider.
     *
     * @param transportProvider the transport provider (e.g. {@link StdioServerTransportProvider})
     * @return a new {@link SyncServerBuilder} configured for synchronous request handling
     * @throws UnsupportedOperationException if the transport provider is a build-time bridge instance
     *         that cannot create a real server connection (requires the official MCP SDK at runtime)
     */
    public static SyncServerBuilder sync(Object transportProvider) {
        if (transportProvider instanceof StdioServerTransportProvider) {
            throw new UnsupportedOperationException(
                "MCP server runtime requires the official MCP Java SDK at runtime. " +
                "Add dependency: io.modelcontextprotocol:mcp (see https://github.com/modelcontextprotocol/java-sdk). " +
                "The YAWL MCP integration compiles correctly and all types are valid; " +
                "a live server connection requires the SDK transport implementation.");
        }
        throw new UnsupportedOperationException(
            "Unknown transport provider type: " + transportProvider.getClass().getName() + ". " +
            "Use StdioServerTransportProvider or an official MCP SDK transport.");
    }

    /**
     * Fluent builder for configuring and starting a synchronous MCP server.
     *
     * <p>All configuration methods return {@code this} for chaining. Call {@link #build()}
     * after all capabilities are registered to start the server.</p>
     */
    public static final class SyncServerBuilder {
        private String serverName;
        private String serverVersion;
        private McpSchema.ServerCapabilities capabilities;
        private String instructions;
        private List<McpServerFeatures.SyncToolSpecification> tools;
        private List<McpServerFeatures.SyncResourceSpecification> resources;
        private List<McpServerFeatures.SyncResourceTemplateSpecification> resourceTemplates;
        private List<McpServerFeatures.SyncPromptSpecification> prompts;
        private List<McpServerFeatures.SyncCompletionSpecification> completions;

        /**
         * Sets the server name and version reported to clients during handshake.
         *
         * @param name server name (e.g. "yawl-mcp-server")
         * @param version server version (e.g. "6.0.0")
         * @return this builder
         */
        public SyncServerBuilder serverInfo(String name, String version) {
            this.serverName = name;
            this.serverVersion = version;
            return this;
        }

        /**
         * Sets the server capabilities declaration.
         *
         * @param capabilities capabilities to advertise to clients
         * @return this builder
         */
        public SyncServerBuilder capabilities(McpSchema.ServerCapabilities capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        /**
         * Sets the server instructions shown to AI models using this server.
         *
         * @param instructions instructions text
         * @return this builder
         */
        public SyncServerBuilder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        /**
         * Registers tool specifications for this server.
         *
         * @param tools list of tool specifications to register
         * @return this builder
         */
        public SyncServerBuilder tools(List<McpServerFeatures.SyncToolSpecification> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * Registers static resource specifications for this server.
         *
         * @param resources list of static resource specifications to register
         * @return this builder
         */
        public SyncServerBuilder resources(List<McpServerFeatures.SyncResourceSpecification> resources) {
            this.resources = resources;
            return this;
        }

        /**
         * Registers resource template specifications for this server.
         *
         * @param resourceTemplates list of resource template specifications to register
         * @return this builder
         */
        public SyncServerBuilder resourceTemplates(List<McpServerFeatures.SyncResourceTemplateSpecification> resourceTemplates) {
            this.resourceTemplates = resourceTemplates;
            return this;
        }

        /**
         * Registers prompt specifications for this server.
         *
         * @param prompts list of prompt specifications to register
         * @return this builder
         */
        public SyncServerBuilder prompts(List<McpServerFeatures.SyncPromptSpecification> prompts) {
            this.prompts = prompts;
            return this;
        }

        /**
         * Registers completion specifications for this server.
         *
         * @param completions list of completion specifications to register
         * @return this builder
         */
        public SyncServerBuilder completions(List<McpServerFeatures.SyncCompletionSpecification> completions) {
            this.completions = completions;
            return this;
        }

        /**
         * Validates all configuration and starts the MCP server.
         *
         * @return a running {@link McpSyncServer} instance
         * @throws UnsupportedOperationException because the official MCP Java SDK transport
         *         implementation is required at runtime and is not available as a Maven Central
         *         artifact. Add {@code io.modelcontextprotocol:mcp} to yawl-integration/pom.xml
         *         and replace this bridge package with the official SDK classes to enable
         *         live server operation.
         */
        public McpSyncServer build() {
            throw new UnsupportedOperationException(
                "MCP server runtime requires the official MCP Java SDK. " +
                "Add dependency: io.modelcontextprotocol:mcp " +
                "(see https://github.com/modelcontextprotocol/java-sdk). " +
                "Server configuration: name=" + serverName +
                ", tools=" + (tools != null ? tools.size() : 0) +
                ", resources=" + (resources != null ? resources.size() : 0) +
                ", prompts=" + (prompts != null ? prompts.size() : 0));
        }
    }
}
