package org.yawlfoundation.yawl.integration.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Model Context Protocol (MCP) Client for YAWL using the official MCP Java SDK.
 *
 * Connects to MCP servers via STDIO or SSE transport to discover and invoke
 * tools, read resources, and use prompts provided by external MCP servers.
 *
 * Supports two transport modes:
 *   - STDIO: Launches an MCP server subprocess and communicates via stdin/stdout
 *   - SSE: Connects to a remote MCP server via HTTP Server-Sent Events
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpClient implements AutoCloseable {

    private final JacksonMcpJsonMapper jsonMapper;
    private final ObjectMapper objectMapper;
    private McpSyncClient mcpClient;
    private boolean connected;

    /**
     * Construct a YAWL MCP Client.
     */
    public YawlMcpClient() {
        this.objectMapper = new ObjectMapper();
        this.jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        this.connected = false;
    }

    /**
     * Connect to an MCP server via STDIO transport by launching a subprocess.
     *
     * @param command the command to launch the MCP server process (e.g. "java")
     * @param args arguments to pass to the command
     */
    public void connectStdio(String command, String... args) {
        if (connected) {
            throw new IllegalStateException("Already connected to an MCP server");
        }

        ServerParameters serverParams = ServerParameters.builder(command)
            .args(args)
            .build();

        StdioClientTransport transport = new StdioClientTransport(serverParams, jsonMapper);

        mcpClient = McpClient.sync(transport)
            .clientInfo(new McpSchema.Implementation("yawl-mcp-client", "5.2.0"))
            .requestTimeout(Duration.ofSeconds(30))
            .build();

        mcpClient.initialize();
        connected = true;
        System.err.println("Connected to MCP server via STDIO transport");
    }

    /**
     * Connect to an MCP server via SSE (Server-Sent Events) HTTP transport.
     *
     * @param serverUrl the URL of the MCP server SSE endpoint
     */
    public void connectSse(String serverUrl) {
        if (connected) {
            throw new IllegalStateException("Already connected to an MCP server");
        }

        HttpClientSseClientTransport transport =
            HttpClientSseClientTransport.builder(serverUrl)
                .jsonMapper(jsonMapper)
                .build();

        mcpClient = McpClient.sync(transport)
            .clientInfo(new McpSchema.Implementation("yawl-mcp-client", "5.2.0"))
            .requestTimeout(Duration.ofSeconds(30))
            .build();

        mcpClient.initialize();
        connected = true;
        System.err.println("Connected to MCP server via SSE at " + serverUrl);
    }

    /**
     * List all tools available on the connected MCP server.
     *
     * @return list of tool definitions
     */
    public List<McpSchema.Tool> listTools() {
        ensureConnected();
        McpSchema.ListToolsResult result = mcpClient.listTools();
        return result.tools();
    }

    /**
     * Call a tool on the connected MCP server.
     *
     * @param toolName name of the tool to invoke
     * @param arguments arguments to pass to the tool as key-value pairs
     * @return the tool call result
     */
    public McpSchema.CallToolResult callTool(String toolName,
            Map<String, Object> arguments) {
        ensureConnected();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
            toolName, arguments);
        return mcpClient.callTool(request);
    }

    /**
     * Call a tool using a JSON string of arguments.
     *
     * @param toolName name of the tool
     * @param argumentsJson JSON string of arguments
     * @return tool result text content
     */
    @SuppressWarnings("unchecked")
    public String callToolJson(String toolName, String argumentsJson) {
        try {
            Map<String, Object> args = objectMapper.readValue(argumentsJson, Map.class);
            McpSchema.CallToolResult result = callTool(toolName, args);
            return extractTextContent(result);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to call tool '" + toolName + "': " + e.getMessage(), e);
        }
    }

    /**
     * List all resources available on the connected MCP server.
     *
     * @return list of resource definitions
     */
    public List<McpSchema.Resource> listResources() {
        ensureConnected();
        McpSchema.ListResourcesResult result = mcpClient.listResources();
        return result.resources();
    }

    /**
     * Read a resource from the connected MCP server.
     *
     * @param resource the resource to read
     * @return the resource contents
     */
    public McpSchema.ReadResourceResult readResource(McpSchema.Resource resource) {
        ensureConnected();
        return mcpClient.readResource(resource);
    }

    /**
     * Read a resource by URI.
     *
     * @param uri the resource URI to read
     * @return the text content of the resource
     */
    public String readResourceByUri(String uri) {
        ensureConnected();
        McpSchema.ReadResourceRequest request = new McpSchema.ReadResourceRequest(uri);
        McpSchema.ReadResourceResult result = mcpClient.readResource(request);
        List<McpSchema.ResourceContents> contents = result.contents();
        if (contents != null && !contents.isEmpty()) {
            McpSchema.ResourceContents first = contents.get(0);
            if (first instanceof McpSchema.TextResourceContents textContents) {
                return textContents.text();
            }
        }
        throw new RuntimeException("Resource at " + uri + " did not return text content");
    }

    /**
     * List all prompts available on the connected MCP server.
     *
     * @return list of prompt definitions
     */
    public List<McpSchema.Prompt> listPrompts() {
        ensureConnected();
        McpSchema.ListPromptsResult result = mcpClient.listPrompts();
        return result.prompts();
    }

    /**
     * Get a prompt from the connected MCP server.
     *
     * @param promptName name of the prompt
     * @param arguments arguments to pass to the prompt
     * @return the prompt result with generated messages
     */
    public McpSchema.GetPromptResult getPrompt(String promptName,
            Map<String, Object> arguments) {
        ensureConnected();
        McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
            promptName, arguments);
        return mcpClient.getPrompt(request);
    }

    /**
     * Get server capabilities after initialization.
     *
     * @return the server's capabilities
     */
    public McpSchema.ServerCapabilities getServerCapabilities() {
        ensureConnected();
        return mcpClient.getServerCapabilities();
    }

    /**
     * Get server info after initialization.
     *
     * @return the server's implementation info
     */
    public McpSchema.Implementation getServerInfo() {
        ensureConnected();
        return mcpClient.getServerInfo();
    }

    /**
     * Ping the server.
     */
    public void ping() {
        ensureConnected();
        mcpClient.ping();
    }

    /**
     * Check if connected to an MCP server.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Disconnect from the MCP server and release resources.
     */
    @Override
    public void close() {
        if (mcpClient != null) {
            mcpClient.closeGracefully();
            mcpClient = null;
        }
        connected = false;
    }

    private void ensureConnected() {
        if (!connected || mcpClient == null) {
            throw new IllegalStateException(
                "Not connected to an MCP server. Call connectStdio() or connectSse() first.");
        }
    }

    private String extractTextContent(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            throw new RuntimeException(
                "Tool call returned no content. The server response was empty.");
        }
        String text = result.content().stream()
            .filter(c -> c instanceof McpSchema.TextContent)
            .map(c -> ((McpSchema.TextContent) c).text())
            .collect(Collectors.joining("\n"));
        if (text.isEmpty()) {
            throw new RuntimeException(
                "Tool call returned no text content. Content types: "
                + result.content().stream()
                    .map(c -> c.getClass().getSimpleName())
                    .collect(Collectors.joining(", ")));
        }
        return text;
    }

    /**
     * Entry point for testing the MCP client.
     *
     * Usage:
     *   java YawlMcpClient stdio &lt;command&gt; [args...]
     *   java YawlMcpClient sse &lt;server-url&gt;
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage:");
            System.err.println("  java YawlMcpClient stdio <command> [args...]");
            System.err.println("  java YawlMcpClient sse <server-url>");
            System.exit(1);
        }

        String mode = args[0];
        YawlMcpClient client = new YawlMcpClient();

        try {
            if ("stdio".equals(mode)) {
                String command = args[1];
                String[] cmdArgs = Arrays.copyOfRange(args, 2, args.length);
                client.connectStdio(command, cmdArgs);
            } else if ("sse".equals(mode)) {
                client.connectSse(args[1]);
            } else {
                System.err.println("Unknown mode: " + mode + ". Use 'stdio' or 'sse'.");
                System.exit(1);
            }

            McpSchema.Implementation serverInfo = client.getServerInfo();
            System.out.println("Connected to: " + serverInfo.name()
                + " v" + serverInfo.version());

            System.out.println("\nAvailable tools:");
            for (McpSchema.Tool tool : client.listTools()) {
                System.out.println("  - " + tool.name() + ": " + tool.description());
            }

            System.out.println("\nAvailable resources:");
            for (McpSchema.Resource resource : client.listResources()) {
                System.out.println("  - " + resource.uri() + ": " + resource.description());
            }

            System.out.println("\nAvailable prompts:");
            for (McpSchema.Prompt prompt : client.listPrompts()) {
                System.out.println("  - " + prompt.name() + ": " + prompt.description());
            }
        } finally {
            client.close();
        }
    }
}
