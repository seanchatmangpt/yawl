package org.yawlfoundation.yawl.integration.mcp.stub;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP Server Features for synchronous operations.
 *
 * <p>This is a minimal stub interface for the MCP SDK's McpServerFeatures class.
 * Provides just enough API surface for YAWL MCP integration to compile.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 * @deprecated Replace with official MCP SDK (io.modelcontextprotocol.sdk:mcp-core) when available.
 *             See https://github.com/modelcontextprotocol/java-sdk
 */
@Deprecated
public final class McpServerFeatures {

    private McpServerFeatures() {
        throw new UnsupportedOperationException("McpServerFeatures is a utility class");
    }

    /**
     * Synchronous tool specification.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class SyncToolSpecification {
        private final McpSchema.Tool tool;
        private final BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> handler;

        public SyncToolSpecification(McpSchema.Tool tool,
                                     BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> handler) {
            this.tool = tool;
            this.handler = handler;
        }

        public McpSchema.Tool getTool() { return tool; }
        public BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> getHandler() {
            return handler;
        }
    }

    /**
     * Synchronous resource specification.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class SyncResourceSpecification {
        private final McpSchema.Resource resource;
        private final BiFunction<McpSyncServerExchange, ReadResourceRequest, McpSchema.ReadResourceResult> handler;

        public SyncResourceSpecification(McpSchema.Resource resource,
                                         BiFunction<McpSyncServerExchange, ReadResourceRequest, McpSchema.ReadResourceResult> handler) {
            this.resource = resource;
            this.handler = handler;
        }

        public McpSchema.Resource getResource() { return resource; }
        public BiFunction<McpSyncServerExchange, ReadResourceRequest, McpSchema.ReadResourceResult> getHandler() {
            return handler;
        }
    }

    /**
     * Synchronous resource template specification.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class SyncResourceTemplateSpecification {
        private final McpSchema.ResourceTemplate resourceTemplate;
        private final BiFunction<McpSyncServerExchange, ReadResourceRequest, McpSchema.ReadResourceResult> handler;

        public SyncResourceTemplateSpecification(McpSchema.ResourceTemplate resourceTemplate,
                                                  BiFunction<McpSyncServerExchange, ReadResourceRequest, McpSchema.ReadResourceResult> handler) {
            this.resourceTemplate = resourceTemplate;
            this.handler = handler;
        }

        public McpSchema.ResourceTemplate getResourceTemplate() { return resourceTemplate; }
        public BiFunction<McpSyncServerExchange, ReadResourceRequest, McpSchema.ReadResourceResult> getHandler() {
            return handler;
        }
    }

    /**
     * Synchronous prompt specification.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class SyncPromptSpecification {
        private final McpSchema.Prompt prompt;
        private final BiFunction<McpSyncServerExchange, GetPromptRequest, McpSchema.GetPromptResult> handler;

        public SyncPromptSpecification(McpSchema.Prompt prompt,
                                       BiFunction<McpSyncServerExchange, GetPromptRequest, McpSchema.GetPromptResult> handler) {
            this.prompt = prompt;
            this.handler = handler;
        }

        public McpSchema.Prompt getPrompt() { return prompt; }
        public BiFunction<McpSyncServerExchange, GetPromptRequest, McpSchema.GetPromptResult> getHandler() {
            return handler;
        }
    }

    /**
     * Synchronous completion specification.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class SyncCompletionSpecification {
        private final McpSchema.Reference reference;
        private final BiFunction<McpSyncServerExchange, CompleteRequest, McpSchema.CompleteResult> handler;

        public SyncCompletionSpecification(McpSchema.Reference reference,
                                           BiFunction<McpSyncServerExchange, CompleteRequest, McpSchema.CompleteResult> handler) {
            this.reference = reference;
            this.handler = handler;
        }

        public McpSchema.Reference getReference() { return reference; }
        public BiFunction<McpSyncServerExchange, CompleteRequest, McpSchema.CompleteResult> getHandler() {
            return handler;
        }
    }

    /**
     * Read resource request.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class ReadResourceRequest {
        private final String uri;

        public ReadResourceRequest(String uri) { this.uri = uri; }

        public String uri() { return uri; }
    }

    /**
     * Get prompt request.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class GetPromptRequest {
        private final String name;
        private final Map<String, Object> arguments;

        public GetPromptRequest(String name, Map<String, Object> arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String getName() { return name; }
        public Map<String, Object> arguments() { return arguments; }
        public Map<String, Object> getArguments() { return arguments; }
    }

    /**
     * Complete request.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class CompleteRequest {
        private final McpSchema.Reference reference;
        private final CompleteArgument argument;

        public CompleteRequest(McpSchema.Reference reference, CompleteArgument argument) {
            this.reference = reference;
            this.argument = argument;
        }

        public McpSchema.Reference getReference() { return reference; }
        public CompleteArgument argument() { return argument; }
        public CompleteArgument getArgument() { return argument; }
    }

    /**
     * Complete argument.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class CompleteArgument {
        private final String name;
        private final String value;

        public CompleteArgument(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public String value() { return value; }
        public String getValue() { return value; }
    }
}
