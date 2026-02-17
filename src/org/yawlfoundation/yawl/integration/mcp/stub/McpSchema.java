package org.yawlfoundation.yawl.integration.mcp.stub;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP Schema definitions.
 *
 * <p>This is a minimal stub interface for the MCP SDK's McpSchema class.
 * Provides just enough API surface for YAWL MCP integration to compile.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 * @deprecated Replace with official MCP SDK (io.modelcontextprotocol.sdk:mcp-core) when available.
 *             See https://github.com/modelcontextprotocol/java-sdk
 */
@Deprecated
public final class McpSchema {

    private McpSchema() {
        throw new UnsupportedOperationException("McpSchema is a utility class and cannot be instantiated");
    }

    /**
     * JSON Schema definition for tool input validation.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class JsonSchema {
        private final String type;
        private final Map<String, Object> properties;
        private final List<String> required;
        private final boolean additionalProperties;
        private final String description;
        private final Object defaultValue;

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
     * Tool definition for MCP.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
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

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String name;
            private String description;
            private JsonSchema inputSchema;

            public Builder name(String name) { this.name = name; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder inputSchema(JsonSchema inputSchema) { this.inputSchema = inputSchema; return this; }
            public Tool build() { return new Tool(this); }
        }
    }

    /**
     * Result from calling a tool.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class CallToolResult {
        private final String content;
        private final boolean isError;

        public CallToolResult(String content, boolean isError) {
            this.content = content;
            this.isError = isError;
        }

        public String getContent() { return content; }
        public boolean isError() { return isError; }
    }

    /**
     * Resource definition for MCP.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class Resource {
        private final String uri;
        private final String name;
        private final String description;
        private final String mimeType;
        private final String annotations;

        public Resource(String uri, String name, String description, String mimeType, String annotations) {
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
     * Resource template for parameterized resources.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class ResourceTemplate {
        private final String uriTemplate;
        private final String name;
        private final String title;
        private final String description;
        private final String mimeType;
        private final String annotations;

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
     * Result from reading a resource.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class ReadResourceResult {
        private final List<ResourceContents> contents;

        public ReadResourceResult(List<ResourceContents> contents) {
            this.contents = contents;
        }

        public List<ResourceContents> getContents() { return contents; }
    }

    /**
     * Base interface for resource contents.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public interface ResourceContents {
        String uri();
        String mimeType();
    }

    /**
     * Text resource contents.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class TextResourceContents implements ResourceContents {
        private final String uri;
        private final String mimeType;
        private final String text;

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
     * Prompt definition for MCP.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class Prompt {
        private final String name;
        private final String description;
        private final List<PromptArgument> arguments;

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
     * Prompt argument definition.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class PromptArgument {
        private final String name;
        private final String description;
        private final boolean required;

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
     * Result from getting a prompt.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class GetPromptResult {
        private final String description;
        private final List<PromptMessage> messages;

        public GetPromptResult(String description, List<PromptMessage> messages) {
            this.description = description;
            this.messages = messages;
        }

        public String getDescription() { return description; }
        public List<PromptMessage> getMessages() { return messages; }
    }

    /**
     * Prompt message.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class PromptMessage {
        private final String role;
        private final Content content;

        public PromptMessage(Role role, Content content) {
            this.role = role.name();
            this.content = content;
        }

        public PromptMessage(String role, Content content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public Content getContent() { return content; }
    }

    /**
     * Content interface for messages.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public interface Content {
        String type();
    }

    /**
     * Text content.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class TextContent implements Content {
        private final String text;

        public TextContent(String text) { this.text = text; }

        @Override public String type() { return "text"; }
        public String getText() { return text; }
    }

    /**
     * Server capabilities definition.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class ServerCapabilities {
        private final boolean tools;
        private final boolean resources;
        private final boolean prompts;
        private final boolean logging;
        private final boolean completions;

        public ServerCapabilities(boolean tools, boolean resources, boolean prompts,
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

        public static ServerCapabilitiesBuilder builder() {
            return new ServerCapabilitiesBuilder();
        }
    }

    /**
     * Reference interface for completion.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public interface Reference {
        String getType();
        String getUri();
    }

    /**
     * Completion result.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class CompleteResult {
        private final CompleteCompletion completion;

        public CompleteResult(CompleteCompletion completion) {
            this.completion = completion;
        }

        public CompleteCompletion getCompletion() { return completion; }

        /**
         * Completion data.
         *
         * @deprecated Replace with official MCP SDK
         */
        @Deprecated
        public static final class CompleteCompletion {
            private final List<String> values;
            private final int total;
            private final boolean hasMore;

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
     * Prompt reference for completions.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class PromptReference implements Reference {
        private final String name;

        public PromptReference(String name) { this.name = name; }

        @Override public String getType() { return "prompt"; }
        @Override public String getUri() { return name; }
        public String getName() { return name; }
    }

    /**
     * Resource reference for completions.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class ResourceReference implements Reference {
        private final String uri;

        public ResourceReference(String uri) { this.uri = uri; }

        @Override public String getType() { return "resource"; }
        @Override public String getUri() { return uri; }
    }

    /**
     * Logging level enumeration.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
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

        public int level() { return level; }
    }

    /**
     * Logging message notification.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
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

        public static Builder builder() { return new Builder(); }

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
     * Role enumeration for messages.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public enum Role {
        USER, ASSISTANT
    }

    /**
     * Server capabilities builder.
     *
     * @deprecated Replace with official MCP SDK
     */
    @Deprecated
    public static final class ServerCapabilitiesBuilder {
        private boolean tools = false;
        private boolean toolsListChanged = false;
        private boolean resources = false;
        private boolean resourcesSubscribe = false;
        private boolean resourcesListChanged = false;
        private boolean prompts = false;
        private boolean promptsListChanged = false;
        private boolean logging = false;
        private boolean completions = false;

        public ServerCapabilitiesBuilder resources(boolean subscribe, boolean listChanged) {
            this.resources = true;
            this.resourcesSubscribe = subscribe;
            this.resourcesListChanged = listChanged;
            return this;
        }

        public ServerCapabilitiesBuilder tools(boolean listChanged) {
            this.tools = true;
            this.toolsListChanged = listChanged;
            return this;
        }

        public ServerCapabilitiesBuilder prompts(boolean listChanged) {
            this.prompts = true;
            this.promptsListChanged = listChanged;
            return this;
        }

        public ServerCapabilitiesBuilder logging() {
            this.logging = true;
            return this;
        }

        public ServerCapabilitiesBuilder completions() {
            this.completions = true;
            return this;
        }

        public ServerCapabilities build() {
            return new ServerCapabilities(tools, resources, prompts, logging, completions);
        }
    }
}
