package org.yawlfoundation.yawl.integration.mcp.sdk;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP Server Feature Specifications.
 *
 * <p>Provides container types for binding MCP server capability handlers. Each specification
 * type pairs an MCP schema object (Tool, Resource, Prompt, etc.) with a handler function that
 * the MCP server invokes when a client requests that capability.</p>
 *
 * <p>This class mirrors the {@code io.modelcontextprotocol.sdk.McpServerFeatures} API as
 * defined in the MCP specification (https://modelcontextprotocol.io/specification).</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class McpServerFeatures {

    private McpServerFeatures() {
        throw new UnsupportedOperationException("McpServerFeatures is a utility class");
    }

    /**
     * Pairs a Tool definition with its synchronous invocation handler.
     * Register via {@link McpServer.SyncServerBuilder#tools(java.util.List)}.
     */
    public static final class SyncToolSpecification {
        private final McpSchema.Tool tool;
        private final BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> handler;

        /**
         * Constructs a tool specification.
         *
         * @param tool the MCP tool definition
         * @param handler function invoked when the client calls this tool;
         *        receives the exchange context and tool arguments, returns the call result
         */
        public SyncToolSpecification(
                McpSchema.Tool tool,
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
     * Pairs a static Resource definition with its synchronous read handler.
     * Register via {@link McpServer.SyncServerBuilder#resources(java.util.List)}.
     */
    public static final class SyncResourceSpecification {
        private final McpSchema.Resource resource;
        private final BiFunction<McpSyncServerExchange, ReadResourceRequest, McpSchema.ReadResourceResult> handler;

        /**
         * Constructs a resource specification.
         *
         * @param resource the MCP resource definition
         * @param handler function invoked when the client reads this resource;
         *        receives the exchange context and read request, returns the resource contents
         */
        public SyncResourceSpecification(
                McpSchema.Resource resource,
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
     * Pairs a ResourceTemplate definition with its synchronous read handler.
     * Register via {@link McpServer.SyncServerBuilder#resourceTemplates(java.util.List)}.
     */
    public static final class SyncResourceTemplateSpecification {
        private final McpSchema.ResourceTemplate resourceTemplate;
        private final BiFunction<McpSyncServerExchange, ReadResourceRequest, McpSchema.ReadResourceResult> handler;

        /**
         * Constructs a resource template specification.
         *
         * @param resourceTemplate the MCP resource template definition
         * @param handler function invoked when the client reads a URI matching this template;
         *        receives the exchange context and read request (with resolved URI), returns the resource contents
         */
        public SyncResourceTemplateSpecification(
                McpSchema.ResourceTemplate resourceTemplate,
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
     * Pairs a Prompt definition with its synchronous generation handler.
     * Register via {@link McpServer.SyncServerBuilder#prompts(java.util.List)}.
     */
    public static final class SyncPromptSpecification {
        private final McpSchema.Prompt prompt;
        private final BiFunction<McpSyncServerExchange, GetPromptRequest, McpSchema.GetPromptResult> handler;

        /**
         * Constructs a prompt specification.
         *
         * @param prompt the MCP prompt definition
         * @param handler function invoked when the client requests this prompt;
         *        receives the exchange context and prompt request, returns the generated prompt messages
         */
        public SyncPromptSpecification(
                McpSchema.Prompt prompt,
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
     * Pairs a Reference with its synchronous completion handler.
     * Register via {@link McpServer.SyncServerBuilder#completions(java.util.List)}.
     */
    public static final class SyncCompletionSpecification {
        private final McpSchema.CompleteReference reference;
        private final BiFunction<McpSyncServerExchange, CompleteRequest, McpSchema.CompleteResult> handler;

        /**
         * Constructs a completion specification.
         *
         * @param reference the MCP reference (PromptReference or ResourceReference)
         * @param handler function invoked when the client requests completions for this reference;
         *        receives the exchange context and complete request, returns completion values
         */
        public SyncCompletionSpecification(
                McpSchema.CompleteReference reference,
                BiFunction<McpSyncServerExchange, CompleteRequest, McpSchema.CompleteResult> handler) {
            this.reference = reference;
            this.handler = handler;
        }

        public McpSchema.CompleteReference getReference() { return reference; }
        public BiFunction<McpSyncServerExchange, CompleteRequest, McpSchema.CompleteResult> getHandler() {
            return handler;
        }
    }

    /**
     * Request to read a specific resource by URI.
     * Received by resource and resource template handlers.
     */
    public static final class ReadResourceRequest {
        private final String uri;

        /**
         * Constructs a resource read request.
         *
         * @param uri the resolved resource URI to read
         */
        public ReadResourceRequest(String uri) { this.uri = uri; }

        /** Returns the URI of the resource to read. */
        public String uri() { return uri; }
    }

    /**
     * Request to get a specific prompt by name with argument values.
     * Received by prompt handlers.
     */
    public static final class GetPromptRequest {
        private final String name;
        private final Map<String, Object> arguments;

        /**
         * Constructs a prompt get request.
         *
         * @param name the prompt name
         * @param arguments map of argument name to value
         */
        public GetPromptRequest(String name, Map<String, Object> arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String getName() { return name; }
        public Map<String, Object> arguments() { return arguments; }
        public Map<String, Object> getArguments() { return arguments; }
    }

    /**
     * Request for autocomplete values for a prompt argument or resource URI parameter.
     * Received by completion handlers.
     */
    public static final class CompleteRequest {
        private final McpSchema.CompleteReference reference;
        private final CompleteArgument argument;

        /**
         * Constructs a complete request.
         *
         * @param reference the reference (prompt or resource) the completion is for
         * @param argument the argument being completed
         */
        public CompleteRequest(McpSchema.CompleteReference reference, CompleteArgument argument) {
            this.reference = reference;
            this.argument = argument;
        }

        public McpSchema.CompleteReference getReference() { return reference; }
        public CompleteArgument argument() { return argument; }
        public CompleteArgument getArgument() { return argument; }
    }

    /**
     * The argument being completed in a completion request.
     */
    public static final class CompleteArgument {
        private final String name;
        private final String value;

        /**
         * Constructs a complete argument.
         *
         * @param name the argument name (identifies which field is being completed)
         * @param value the partial value entered so far (prefix for filtering)
         */
        public CompleteArgument(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public String value() { return value; }
        public String getValue() { return value; }
    }
}
