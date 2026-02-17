package org.yawlfoundation.yawl.integration.mcp.stub;

import java.util.List;

/**
 * MCP Server builder factory.
 *
 * <p>This is a minimal stub interface for the MCP SDK's server builder.
 * Provides just enough API surface for YAWL MCP integration to compile.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 * @deprecated Replace with official MCP SDK (io.modelcontextprotocol.sdk:mcp-core) when available.
 *             See https://github.com/modelcontextprotocol/java-sdk
 */
@Deprecated
public final class McpServer {

    private McpServer() {
        throw new UnsupportedOperationException("Use McpServer.sync() to create a server builder");
    }

    /**
     * Create a synchronous server builder.
     *
     * @param transportProvider the transport provider
     * @return server builder
     * @throws UnsupportedOperationException always - this is a stub
     */
    public static SyncServerBuilder sync(Object transportProvider) {
        throw new UnsupportedOperationException(
            "MCP SDK stub - cannot create real server. " +
            "Replace with official MCP SDK from https://github.com/modelcontextprotocol/java-sdk");
    }

    /**
     * Synchronous server builder.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
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

        public SyncServerBuilder serverInfo(String name, String version) {
            this.serverName = name;
            this.serverVersion = version;
            return this;
        }

        public SyncServerBuilder capabilities(McpSchema.ServerCapabilities capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public SyncServerBuilder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public SyncServerBuilder tools(List<McpServerFeatures.SyncToolSpecification> tools) {
            this.tools = tools;
            return this;
        }

        public SyncServerBuilder resources(List<McpServerFeatures.SyncResourceSpecification> resources) {
            this.resources = resources;
            return this;
        }

        public SyncServerBuilder resourceTemplates(List<McpServerFeatures.SyncResourceTemplateSpecification> resourceTemplates) {
            this.resourceTemplates = resourceTemplates;
            return this;
        }

        public SyncServerBuilder prompts(List<McpServerFeatures.SyncPromptSpecification> prompts) {
            this.prompts = prompts;
            return this;
        }

        public SyncServerBuilder completions(List<McpServerFeatures.SyncCompletionSpecification> completions) {
            this.completions = completions;
            return this;
        }

        public McpSyncServer build() {
            throw new UnsupportedOperationException(
                "MCP SDK stub - cannot build real server. " +
                "Replace with official MCP SDK from https://github.com/modelcontextprotocol/java-sdk");
        }
    }
}
