package org.yawlfoundation.yawl.integration.mcp.sdk;

import java.util.List;
import java.util.Map;

/**
 * MCP Schema definitions implementing the Model Context Protocol specification.
 *
 * <p>Provides all core MCP type definitions used by the YAWL MCP integration.
 * This class mirrors the {@code io.modelcontextprotocol.sdk.McpSchema} API surface
 * as defined in the MCP specification (https://modelcontextprotocol.io/specification).</p>
 *
 * <p>All types in this class correspond directly to JSON schema objects in the MCP
 * protocol specification. See the specification for field semantics and validation rules.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class McpSchema {

    private McpSchema() {
        throw new UnsupportedOperationException("McpSchema is a utility class");
    }

    /**
     * JSON Schema definition for MCP tool input parameter validation.
     * Corresponds to the MCP protocol {@code JsonSchema} object.
     */
    public static final class JsonSchema {
        private final String type;
        private final Map<String, Object> properties;
        private final List<String> required;
        private final boolean additionalProperties;
        private final String description;
        private final Object defaultValue;

        /**
         * Constructs a JSON schema definition for tool input validation.
         *
         * @param type the JSON schema type (e.g. "object", "string", "number")
         * @param properties map of property name to property schema definition
         * @param required list of required property names
         * @param additionalProperties whether additional properties are allowed
         * @param description optional human-readable description of the schema
         * @param defaultValue optional default value for the schema root
         */
        public JsonSchema(String type, Map<String, Object> properties, List<String> required,
                          boolean additionalProperties, String description, Object defaultValue) {
            this.type = type;
            this.properties = properties;
            this.required = required;
            this.additionalProperties = additionalProperties;
            this.description = description;
            this.defaultValue = defaultValue;
        }

        public String getType() { return type; }
        public Map<String, Object> getProperties() { return properties; }
        public List<String> getRequired() { return required; }
        public boolean isAdditionalProperties() { return additionalProperties; }
        public String getDescription() { return description; }
        public Object getDefaultValue() { return defaultValue; }
    }

    /**
     * MCP Tool definition.
     * Corresponds to the MCP protocol {@code Tool} object.
     */
    public static final class Tool {
        private final String name;
        private final String description;
        private final JsonSchema inputSchema;

        private Tool(Builder builder) {
            this.name = builder.name;
            this.description = builder.description;
            this.inputSchema = builder.inputSchema;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public JsonSchema getInputSchema() { return inputSchema; }

        /** Returns a new builder for constructing a Tool definition. */
        public static Builder builder() { return new Builder(); }

        /** Builder for MCP Tool definitions. */
        public static final class Builder {
            private String name;
            private String description;
            private JsonSchema inputSchema;

            public Builder name(String name) { this.name = name; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder inputSchema(JsonSchema inputSchema) { this.inputSchema = inputSchema; return this; }

            public Tool build() {
                if (name == null || name.isEmpty()) {
                    throw new IllegalArgumentException("Tool name is required");
                }
                return new Tool(this);
            }
        }
    }

    /**
     * Result from a tool invocation.
     * Corresponds to the MCP protocol {@code CallToolResult} object.
     */
    public static final class CallToolResult {
        private final String content;
        private final boolean isError;

        /**
         * Constructs a tool call result.
         *
         * @param content the result content (text output or error message)
         * @param isError true if this result represents a tool execution error
         */
        public CallToolResult(String content, boolean isError) {
            this.content = content;
            this.isError = isError;
        }

        public String getContent() { return content; }
        public boolean isError() { return isError; }
    }

    /**
     * MCP Resource definition for static resources.
     * Corresponds to the MCP protocol {@code Resource} object.
     */
    public static final class Resource {
        private final String uri;
        private final String name;
        private final String description;
        private final String mimeType;
        private final String annotations;

        /**
         * Constructs an MCP resource definition.
         *
         * @param uri unique resource URI (e.g. "yawl://specifications")
         * @param name human-readable resource name
         * @param description resource description shown to AI clients
         * @param mimeType MIME type of the resource content
         * @param annotations optional annotations JSON string
         */
        public Resource(String uri, String name, String description,
                        String mimeType, String annotations) {
            this.uri = uri;
            this.name = name;
            this.description = description;
            this.mimeType = mimeType;
            this.annotations = annotations;
        }

        public String getUri() { return uri; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getMimeType() { return mimeType; }
        public String getAnnotations() { return annotations; }
    }

    /**
     * MCP Resource Template definition for parameterized resources.
     * Corresponds to the MCP protocol {@code ResourceTemplate} object.
     */
    public static final class ResourceTemplate {
        private final String uriTemplate;
        private final String name;
        private final String title;
        private final String description;
        private final String mimeType;
        private final String annotations;

        /**
         * Constructs a parameterized resource template definition.
         *
         * @param uriTemplate URI template with RFC 6570 parameters (e.g. "yawl://cases/{caseId}")
         * @param name human-readable resource name
         * @param title optional display title
         * @param description resource description shown to AI clients
         * @param mimeType MIME type of the resource content
         * @param annotations optional annotations JSON string
         */
        public ResourceTemplate(String uriTemplate, String name, String title,
                                String description, String mimeType, String annotations) {
            this.uriTemplate = uriTemplate;
            this.name = name;
            this.title = title;
            this.description = description;
            this.mimeType = mimeType;
            this.annotations = annotations;
        }

        public String getUriTemplate() { return uriTemplate; }
        public String getName() { return name; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getMimeType() { return mimeType; }
        public String getAnnotations() { return annotations; }
    }

    /**
     * Result from reading an MCP resource.
     * Corresponds to the MCP protocol {@code ReadResourceResult} object.
     */
    public static final class ReadResourceResult {
        private final List<ResourceContents> contents;

        /**
         * Constructs a resource read result.
         *
         * @param contents list of resource content objects (text or binary)
         */
        public ReadResourceResult(List<ResourceContents> contents) {
            this.contents = contents;
        }

        public List<ResourceContents> getContents() { return contents; }
    }

    /**
     * Base interface for resource content objects.
     * Implemented by {@link TextResourceContents}.
     */
    public interface ResourceContents {
        String uri();
        String mimeType();
    }

    /**
     * Text content returned from an MCP resource read operation.
     * Corresponds to the MCP protocol {@code TextResourceContents} object.
     */
    public static final class TextResourceContents implements ResourceContents {
        private final String uri;
        private final String mimeType;
        private final String text;

        /**
         * Constructs text resource contents.
         *
         * @param uri the URI of the resource
         * @param mimeType MIME type of the text content
         * @param text the text content
         */
        public TextResourceContents(String uri, String mimeType, String text) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.text = text;
        }

        @Override public String uri() { return uri; }
        @Override public String mimeType() { return mimeType; }
        public String getText() { return text; }
    }

    /**
     * MCP Prompt definition.
     * Corresponds to the MCP protocol {@code Prompt} object.
     */
    public static final class Prompt {
        private final String name;
        private final String description;
        private final List<PromptArgument> arguments;

        /**
         * Constructs a prompt definition.
         *
         * @param name unique prompt name
         * @param description human-readable description
         * @param arguments list of prompt argument definitions
         */
        public Prompt(String name, String description, List<PromptArgument> arguments) {
            this.name = name;
            this.description = description;
            this.arguments = arguments;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<PromptArgument> getArguments() { return arguments; }
    }

    /**
     * MCP Prompt argument definition.
     * Corresponds to the MCP protocol {@code PromptArgument} object.
     */
    public static final class PromptArgument {
        private final String name;
        private final String description;
        private final boolean required;

        /**
         * Constructs a prompt argument definition.
         *
         * @param name argument name
         * @param description human-readable description
         * @param required whether this argument must be provided
         */
        public PromptArgument(String name, String description, boolean required) {
            this.name = name;
            this.description = description;
            this.required = required;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isRequired() { return required; }
    }

    /**
     * Result from a prompt retrieval operation.
     * Corresponds to the MCP protocol {@code GetPromptResult} object.
     */
    public static final class GetPromptResult {
        private final String description;
        private final List<PromptMessage> messages;

        /**
         * Constructs a prompt result.
         *
         * @param description description of the generated prompt
         * @param messages list of prompt messages to send to the AI model
         */
        public GetPromptResult(String description, List<PromptMessage> messages) {
            this.description = description;
            this.messages = messages;
        }

        public String getDescription() { return description; }
        public List<PromptMessage> getMessages() { return messages; }
    }

    /**
     * A single message in an MCP prompt.
     * Corresponds to the MCP protocol {@code PromptMessage} object.
     */
    public static final class PromptMessage {
        private final String role;
        private final Content content;

        /**
         * Constructs a prompt message with an enum role.
         *
         * @param role the message role (USER or ASSISTANT)
         * @param content the message content
         */
        public PromptMessage(Role role, Content content) {
            this.role = role.name();
            this.content = content;
        }

        /**
         * Constructs a prompt message with a string role.
         *
         * @param role the message role string
         * @param content the message content
         */
        public PromptMessage(String role, Content content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public Content getContent() { return content; }
    }

    /**
     * Base interface for MCP message content.
     * Implemented by {@link TextContent}.
     */
    public interface Content {
        String type();
    }

    /**
     * Text content in an MCP message.
     * Corresponds to the MCP protocol {@code TextContent} object.
     */
    public static final class TextContent implements Content {
        private final String text;

        /**
         * Constructs text content.
         *
         * @param text the text content
         */
        public TextContent(String text) { this.text = text; }

        @Override public String type() { return "text"; }
        public String getText() { return text; }
    }

    /**
     * MCP Server Capabilities declaration.
     * Corresponds to the MCP protocol {@code ServerCapabilities} object.
     * Use {@link ServerCapabilitiesBuilder} to construct instances.
     */
    public static final class ServerCapabilities {
        private final boolean tools;
        private final boolean resources;
        private final boolean prompts;
        private final boolean logging;
        private final boolean completions;

        ServerCapabilities(boolean tools, boolean resources, boolean prompts,
                           boolean logging, boolean completions) {
            this.tools = tools;
            this.resources = resources;
            this.prompts = prompts;
            this.logging = logging;
            this.completions = completions;
        }

        public boolean hasTools() { return tools; }
        public boolean hasResources() { return resources; }
        public boolean hasPrompts() { return prompts; }
        public boolean hasLogging() { return logging; }
        public boolean hasCompletions() { return completions; }

        /** Returns a new builder for constructing server capabilities. */
        public static ServerCapabilitiesBuilder builder() {
            return new ServerCapabilitiesBuilder();
        }
    }

    /**
     * Base interface for completion references.
     * Implemented by {@link PromptReference} and {@link ResourceReference}.
     */
    public interface Reference {
        String getType();
        String getUri();
    }

    /**
     * Autocomplete result for MCP completions.
     * Corresponds to the MCP protocol {@code CompleteResult} object.
     */
    public static final class CompleteResult {
        private final CompleteCompletion completion;

        /**
         * Constructs a completion result.
         *
         * @param completion the completion data
         */
        public CompleteResult(CompleteCompletion completion) {
            this.completion = completion;
        }

        public CompleteCompletion getCompletion() { return completion; }

        /**
         * Completion data container holding matched values and pagination state.
         */
        public static final class CompleteCompletion {
            private final List<String> values;
            private final int total;
            private final boolean hasMore;

            /**
             * Constructs completion data.
             *
             * @param values list of completion value strings
             * @param total total number of matching completions (may exceed values.size())
             * @param hasMore true if there are more completions beyond those returned
             */
            public CompleteCompletion(List<String> values, int total, boolean hasMore) {
                this.values = values;
                this.total = total;
                this.hasMore = hasMore;
            }

            public List<String> getValues() { return values; }
            public int getTotal() { return total; }
            public boolean hasMore() { return hasMore; }
        }
    }

    /**
     * Reference to a named prompt for completion purposes.
     * Corresponds to the MCP protocol {@code PromptReference} object.
     */
    public static final class PromptReference implements Reference {
        private final String name;

        /**
         * Constructs a prompt reference by name.
         *
         * @param name the prompt name
         */
        public PromptReference(String name) { this.name = name; }

        @Override public String getType() { return "ref/prompt"; }
        @Override public String getUri() { return name; }
        public String getName() { return name; }
    }

    /**
     * Reference to a resource URI for completion purposes.
     * Corresponds to the MCP protocol {@code ResourceReference} object.
     */
    public static final class ResourceReference implements Reference {
        private final String uri;

        /**
         * Constructs a resource reference by URI.
         *
         * @param uri the resource URI (may include template parameters)
         */
        public ResourceReference(String uri) { this.uri = uri; }

        @Override public String getType() { return "ref/resource"; }
        @Override public String getUri() { return uri; }
    }

    /**
     * MCP logging severity levels.
     * Corresponds to the MCP protocol {@code LoggingLevel} enumeration.
     * Levels follow RFC 5424 syslog severity ordering.
     */
    public enum LoggingLevel {
        DEBUG(0),
        INFO(1),
        NOTICE(2),
        WARNING(3),
        ERROR(4),
        CRITICAL(5),
        ALERT(6),
        EMERGENCY(7);

        private final int level;

        LoggingLevel(int level) { this.level = level; }

        /** Returns the numeric severity level (higher = more severe). */
        public int level() { return level; }
    }

    /**
     * MCP logging notification sent from server to client.
     * Corresponds to the MCP protocol {@code LoggingMessageNotification} object.
     * Use {@link #builder()} to construct instances.
     */
    public static final class LoggingMessageNotification {
        private final LoggingLevel level;
        private final String logger;
        private final String data;

        private LoggingMessageNotification(Builder builder) {
            this.level = builder.level;
            this.logger = builder.logger;
            this.data = builder.data;
        }

        public LoggingLevel getLevel() { return level; }
        public String getLogger() { return logger; }
        public String getData() { return data; }

        /** Returns a new builder for constructing log notifications. */
        public static Builder builder() { return new Builder(); }

        /** Builder for logging notification objects. */
        public static final class Builder {
            private LoggingLevel level;
            private String logger;
            private String data;

            public Builder level(LoggingLevel level) { this.level = level; return this; }
            public Builder logger(String logger) { this.logger = logger; return this; }
            public Builder data(String data) { this.data = data; return this; }
            public LoggingMessageNotification build() { return new LoggingMessageNotification(this); }
        }
    }

    /**
     * Message role in the MCP prompt conversation model.
     */
    public enum Role {
        USER, ASSISTANT
    }

    /**
     * Builder for MCP server capabilities declarations.
     * Construct via {@link ServerCapabilities#builder()}.
     */
    public static final class ServerCapabilitiesBuilder {
        private boolean tools = false;
        private boolean resources = false;
        private boolean prompts = false;
        private boolean logging = false;
        private boolean completions = false;

        /**
         * Enables the resources capability.
         *
         * @param subscribe whether clients can subscribe to resource change notifications
         * @param listChanged whether the server sends list-changed notifications
         * @return this builder
         */
        public ServerCapabilitiesBuilder resources(boolean subscribe, boolean listChanged) {
            this.resources = true;
            return this;
        }

        /**
         * Enables the tools capability.
         *
         * @param listChanged whether the server sends list-changed notifications
         * @return this builder
         */
        public ServerCapabilitiesBuilder tools(boolean listChanged) {
            this.tools = true;
            return this;
        }

        /**
         * Enables the prompts capability.
         *
         * @param listChanged whether the server sends list-changed notifications
         * @return this builder
         */
        public ServerCapabilitiesBuilder prompts(boolean listChanged) {
            this.prompts = true;
            return this;
        }

        /**
         * Enables the logging capability.
         *
         * @return this builder
         */
        public ServerCapabilitiesBuilder logging() {
            this.logging = true;
            return this;
        }

        /**
         * Enables the completions capability.
         *
         * @return this builder
         */
        public ServerCapabilitiesBuilder completions() {
            this.completions = true;
            return this;
        }

        /**
         * Builds the server capabilities declaration.
         *
         * @return immutable ServerCapabilities instance
         */
        public ServerCapabilities build() {
            return new ServerCapabilities(tools, resources, prompts, logging, completions);
        }
    }
}
