package org.yawlfoundation.yawl.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Model Context Protocol (MCP) Client Integration for YAWL
 *
 * Production-ready MCP client that connects to MCP servers using HTTP transport.
 * Supports both direct tool calls and AI-enhanced operations via Z.AI.
 *
 * Features:
 * - HTTP-based JSON-RPC 2.0 communication
 * - Resource and tool discovery
 * - AI-enhanced tool calls with Z.AI
 * - Proper session management
 *
 * Usage:
 * <pre>
 * YawlMcpClient client = new YawlMcpClient("http://localhost:3000");
 * client.connect();
 *
 * // List tools
 * String[] tools = client.listTools();
 *
 * // Call a tool
 * String result = client.callTool("yawl_list_specs", "{}");
 *
 * // Get a resource
 * String resource = client.getResource("yawl://specifications");
 *
 * client.disconnect();
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String serverUrl;
    private final OkHttpClient httpClient;
    private final ZaiService zaiService;
    private final ZaiFunctionService functionService;

    private boolean connected = false;
    private int requestId = 0;
    private String protocolVersion = "2024-11-05";
    private JsonNode serverCapabilities;

    /**
     * Creates a new MCP client with the specified server URL.
     *
     * @param serverUrl the MCP server URL (e.g., "http://localhost:3000")
     */
    public YawlMcpClient(String serverUrl) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.zaiService = new ZaiService();
        this.functionService = new ZaiFunctionService();
    }

    /**
     * Creates a new MCP client with Z.AI API key for enhanced operations.
     *
     * @param serverUrl the MCP server URL
     * @param zaiApiKey the Z.AI API key
     */
    public YawlMcpClient(String serverUrl, String zaiApiKey) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.zaiService = new ZaiService(zaiApiKey);
        this.functionService = new ZaiFunctionService();
    }

    /**
     * Connects to the MCP server and performs initialization handshake.
     *
     * @throws IOException if connection fails
     */
    public void connect() throws IOException {
        if (connected) {
            System.out.println("Already connected to MCP server");
            return;
        }

        System.out.println("Connecting to MCP server at: " + serverUrl);

        // Perform MCP initialization handshake
        ObjectNode initParams = MAPPER.createObjectNode();
        initParams.put("protocolVersion", protocolVersion);

        ObjectNode clientInfo = MAPPER.createObjectNode();
        clientInfo.put("name", "yawl-mcp-client");
        clientInfo.put("version", "5.2");
        initParams.set("clientInfo", clientInfo);

        ObjectNode capabilities = MAPPER.createObjectNode();
        initParams.set("capabilities", capabilities);

        JsonNode response = sendRequest("initialize", initParams);

        if (response.has("result")) {
            JsonNode result = response.get("result");
            if (result.has("capabilities")) {
                this.serverCapabilities = result.get("capabilities");
            }
            if (result.has("protocolVersion")) {
                this.protocolVersion = result.get("protocolVersion").asText();
            }
        }

        zaiService.setSystemPrompt(
                "You are an intelligent assistant integrated with YAWL MCP Client. " +
                        "Help users interact with MCP tools and resources effectively."
        );

        connected = true;
        System.out.println("Successfully connected to MCP server");
        System.out.println("Protocol version: " + protocolVersion);
    }

    /**
     * Disconnects from the MCP server.
     */
    public void disconnect() {
        if (!connected) {
            System.out.println("Not connected to MCP server");
            return;
        }

        System.out.println("Disconnecting from MCP server...");
        connected = false;
        serverCapabilities = null;
        System.out.println("Disconnected");
    }

    /**
     * Sends a JSON-RPC request to the MCP server.
     *
     * @param method the RPC method name
     * @param params the request parameters
     * @return the response JSON node
     * @throws IOException if the request fails
     */
    private JsonNode sendRequest(String method, JsonNode params) throws IOException {
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("method", method);
        request.put("id", ++requestId);
        if (params != null) {
            request.set("params", params);
        }

        String requestBody = MAPPER.writeValueAsString(request);

        Request httpRequest = new Request.Builder()
                .url(serverUrl + "/mcp")
                .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP request failed: " + response.code() + " " + response.message());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body");
            }

            String responseText = body.string();
            JsonNode responseJson = MAPPER.readTree(responseText);

            // Check for JSON-RPC error
            if (responseJson.has("error")) {
                JsonNode error = responseJson.get("error");
                String errorMessage = error.has("message") ? error.get("message").asText() : "Unknown error";
                int errorCode = error.has("code") ? error.get("code").asInt() : -1;
                throw new IOException("MCP error " + errorCode + ": " + errorMessage);
            }

            return responseJson;
        }
    }

    /**
     * Calls a tool with AI-enhanced parameter handling.
     *
     * @param naturalLanguageRequest the natural language request
     * @param context additional context for the request
     * @return the AI-processed result
     */
    public String callToolWithAI(String naturalLanguageRequest, String context) {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("AI-enhanced tool call: " + naturalLanguageRequest);

        String prompt = naturalLanguageRequest;
        if (context != null && !context.isEmpty()) {
            prompt += "\n\nContext: " + context;
        }

        return functionService.processWithFunctions(prompt);
    }

    /**
     * Calls a tool directly on the MCP server.
     *
     * @param toolName the name of the tool to call
     * @param parametersJson the parameters as JSON string
     * @return the tool result as JSON string
     * @throws IOException if the tool call fails
     */
    public String callTool(String toolName, String parametersJson) throws IOException {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("Calling MCP tool: " + toolName);
        System.out.println("Parameters: " + parametersJson);

        ObjectNode params = MAPPER.createObjectNode();
        params.put("name", toolName);

        JsonNode argsNode = MAPPER.readTree(parametersJson);
        params.set("arguments", argsNode);

        JsonNode response = sendRequest("tools/call", params);

        if (response.has("result")) {
            return MAPPER.writeValueAsString(response.get("result"));
        }

        return "{}";
    }

    /**
     * Gets a resource from the MCP server.
     *
     * @param resourceUri the URI of the resource (e.g., "yawl://specifications")
     * @return the resource content as JSON string
     * @throws IOException if the resource fetch fails
     */
    public String getResource(String resourceUri) throws IOException {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("Fetching MCP resource: " + resourceUri);

        ObjectNode params = MAPPER.createObjectNode();
        params.put("uri", resourceUri);

        JsonNode response = sendRequest("resources/read", params);

        if (response.has("result")) {
            JsonNode result = response.get("result");
            if (result.has("contents")) {
                JsonNode contents = result.get("contents");
                if (contents.isArray() && contents.size() > 0) {
                    JsonNode firstContent = contents.get(0);
                    if (firstContent.has("text")) {
                        return firstContent.get("text").asText();
                    }
                    return MAPPER.writeValueAsString(firstContent);
                }
            }
            return MAPPER.writeValueAsString(result);
        }

        return "{}";
    }

    /**
     * Lists available tools on the MCP server.
     *
     * @return array of tool names
     * @throws IOException if the request fails
     */
    public String[] listTools() throws IOException {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("Listing available MCP tools...");

        ObjectNode params = MAPPER.createObjectNode();
        JsonNode response = sendRequest("tools/list", params);

        List<String> toolNames = new ArrayList<>();

        if (response.has("result")) {
            JsonNode result = response.get("result");
            if (result.has("tools")) {
                JsonNode tools = result.get("tools");
                if (tools.isArray()) {
                    for (JsonNode tool : tools) {
                        if (tool.has("name")) {
                            toolNames.add(tool.get("name").asText());
                        }
                    }
                }
            }
        }

        System.out.println("Found " + toolNames.size() + " tools");
        return toolNames.toArray(new String[0]);
    }

    /**
     * Lists available resources on the MCP server.
     *
     * @return array of resource URIs
     * @throws IOException if the request fails
     */
    public String[] listResources() throws IOException {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("Listing available MCP resources...");

        ObjectNode params = MAPPER.createObjectNode();
        JsonNode response = sendRequest("resources/list", params);

        List<String> resourceUris = new ArrayList<>();

        if (response.has("result")) {
            JsonNode result = response.get("result");
            if (result.has("resources")) {
                JsonNode resources = result.get("resources");
                if (resources.isArray()) {
                    for (JsonNode resource : resources) {
                        if (resource.has("uri")) {
                            resourceUris.add(resource.get("uri").asText());
                        }
                    }
                }
            }
        }

        System.out.println("Found " + resourceUris.size() + " resources");
        return resourceUris.toArray(new String[0]);
    }

    /**
     * Gets AI analysis of a resource.
     *
     * @param resourceUri the resource URI
     * @param analysisRequest the analysis request
     * @return the AI analysis result
     * @throws IOException if resource fetch or AI call fails
     */
    public String analyzeResourceWithAI(String resourceUri, String analysisRequest) throws IOException {
        if (!connected) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        System.out.println("Analyzing resource with AI: " + resourceUri);

        String resourceContent = getResource(resourceUri);

        String prompt = String.format(
                "Analyze the following resource content:\n\n" +
                        "Resource URI: %s\n\n" +
                        "Content:\n%s\n\n" +
                        "Analysis Request: %s",
                resourceUri, resourceContent, analysisRequest
        );

        return zaiService.chat(prompt);
    }

    /**
     * Gets intelligent tool recommendation based on task description.
     *
     * @param taskDescription the task description
     * @return the tool recommendation
     * @throws IOException if tool listing fails
     */
    public String getToolRecommendation(String taskDescription) throws IOException {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Given the following task, recommend the best MCP tool to use:\n\n");
        prompt.append("Task: ").append(taskDescription).append("\n\n");
        prompt.append("Available tools:\n");
        for (String tool : listTools()) {
            prompt.append("- ").append(tool).append("\n");
        }
        prompt.append("\nReturn the tool name and suggested parameters in JSON format.");

        return zaiService.chat(prompt.toString());
    }

    /**
     * Checks if the client is connected to an MCP server.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Checks if AI enhancement is available.
     *
     * @return true if Z.AI is initialized
     */
    public boolean isAIEnabled() {
        return zaiService.isInitialized();
    }

    /**
     * Gets the server capabilities from the initialization response.
     *
     * @return the server capabilities JSON node, or null if not connected
     */
    public JsonNode getServerCapabilities() {
        return serverCapabilities;
    }

    /**
     * Gets the negotiated protocol version.
     *
     * @return the protocol version string
     */
    public String getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * Main method for testing the MCP client.
     *
     * @param args command line arguments (first arg is server URL)
     */
    public static void main(String[] args) {
        String serverUrl = args.length > 0 ? args[0] : "http://localhost:3000";

        YawlMcpClient client = new YawlMcpClient(serverUrl);

        try {
            client.connect();

            System.out.println("\n=== Available Tools ===");
            String[] tools = client.listTools();
            for (String tool : tools) {
                System.out.println("  - " + tool);
            }

            System.out.println("\n=== Available Resources ===");
            String[] resources = client.listResources();
            for (String resource : resources) {
                System.out.println("  - " + resource);
            }

            System.out.println("\n=== Testing Tool Call: yawl_list_specs ===");
            String specsResult = client.callTool("yawl_list_specs", "{}");
            System.out.println("Result: " + specsResult);

            System.out.println("\n=== Testing Resource: yawl://specifications ===");
            String specsResource = client.getResource("yawl://specifications");
            System.out.println("Resource: " + specsResource);

            if (client.isAIEnabled()) {
                System.out.println("\n=== Testing AI-Enhanced Tool Call ===");
                String aiResult = client.callToolWithAI(
                        "List all workflow specifications",
                        "{}"
                );
                System.out.println(aiResult);

                System.out.println("\n=== Testing Tool Recommendation ===");
                String recommendation = client.getToolRecommendation(
                        "I need to start a new workflow"
                );
                System.out.println(recommendation);
            } else {
                System.out.println("\nAI enhancement not available (set ZHIPU_API_KEY)");
            }

            client.disconnect();

            System.out.println("\n=== All tests completed successfully ===");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
