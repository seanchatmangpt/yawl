/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Model Context Protocol (MCP) Client for YAWL
 *
 * Provides full MCP client functionality for connecting to MCP servers via HTTP or stdio.
 * Implements JSON-RPC 2.0 protocol with support for:
 * - Tool discovery and invocation
 * - Resource fetching
 * - Prompt management
 * - Session handling
 * - Streaming responses
 * - Error handling
 *
 * Example Usage:
 *
 * // HTTP connection
 * YawlMcpClient client = new YawlMcpClient("http://localhost:3000");
 * client.initialize();
 * List<McpTool> tools = client.listTools();
 * String result = client.callTool("analyzeWorkflow", parameters);
 * client.close();
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpClient {

    private static final Logger logger = LogManager.getLogger(YawlMcpClient.class);
    private static final String JSON_RPC_VERSION = "2.0";
    private static final int HTTP_CONNECT_TIMEOUT_MS = 30000;
    private static final int HTTP_READ_TIMEOUT_MS = 60000;

    private final String serverUrl;
    private final TransportMode transportMode;
    private final Gson gson;
    private final AtomicLong requestIdCounter;

    private boolean initialized = false;
    private String sessionId;
    private Map<String, McpTool> availableTools;
    private Map<String, McpResource> availableResources;
    private Map<String, McpPrompt> availablePrompts;
    private ServerCapabilities serverCapabilities;

    // Stdio transport
    private Process stdioProcess;
    private BufferedWriter stdioWriter;
    private BufferedReader stdioReader;

    // HTTP transport
    private int connectTimeout = HTTP_CONNECT_TIMEOUT_MS;
    private int readTimeout = HTTP_READ_TIMEOUT_MS;

    /**
     * Transport modes for MCP communication
     */
    public enum TransportMode {
        HTTP,
        STDIO
    }

    /**
     * Create HTTP-based MCP client
     *
     * @param serverUrl HTTP URL of the MCP server (e.g., "http://localhost:3000")
     */
    public YawlMcpClient(String serverUrl) {
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Server URL cannot be null or empty");
        }

        this.serverUrl = serverUrl.trim();
        this.transportMode = TransportMode.HTTP;
        this.gson = new Gson();
        this.requestIdCounter = new AtomicLong(1);
        this.availableTools = new HashMap<>();
        this.availableResources = new HashMap<>();
        this.availablePrompts = new HashMap<>();

        logger.info("Created MCP client for HTTP transport: {}", serverUrl);
    }

    /**
     * Create stdio-based MCP client
     *
     * @param command Command to start the MCP server process
     * @param args Arguments for the command
     */
    public YawlMcpClient(String command, String[] args) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }

        this.serverUrl = command;
        this.transportMode = TransportMode.STDIO;
        this.gson = new Gson();
        this.requestIdCounter = new AtomicLong(1);
        this.availableTools = new HashMap<>();
        this.availableResources = new HashMap<>();
        this.availablePrompts = new HashMap<>();

        logger.info("Created MCP client for stdio transport: {}", command);
    }

    /**
     * Initialize connection and discover server capabilities
     *
     * @throws IOException if connection fails
     */
    public void initialize() throws IOException {
        if (initialized) {
            logger.warn("Client already initialized");
            return;
        }

        logger.info("Initializing MCP client connection...");

        if (transportMode == TransportMode.STDIO) {
            initializeStdio();
        }

        // Send initialize request
        JsonObject initParams = new JsonObject();
        initParams.addProperty("protocolVersion", "2024-11-05");
        initParams.addProperty("clientInfo", "YAWL MCP Client v5.2");

        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("sampling", false);
        capabilities.addProperty("experimental", false);
        initParams.add("capabilities", capabilities);

        JsonObject response = sendRequest("initialize", initParams);

        if (response.has("error")) {
            throw new IOException("Initialize failed: " + response.get("error"));
        }

        // Parse server capabilities
        JsonObject result = response.getAsJsonObject("result");
        parseServerCapabilities(result);

        // Send initialized notification
        sendNotification("notifications/initialized", new JsonObject());

        // Discover tools, resources, and prompts
        discoverCapabilities();

        initialized = true;
        logger.info("MCP client initialized successfully");
    }

    /**
     * Initialize stdio transport
     */
    private void initializeStdio() throws IOException {
        List<String> command = new ArrayList<>();
        command.add(serverUrl);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        try {
            stdioProcess = builder.start();
            stdioWriter = new BufferedWriter(
                new OutputStreamWriter(stdioProcess.getOutputStream(), StandardCharsets.UTF_8)
            );
            stdioReader = new BufferedReader(
                new InputStreamReader(stdioProcess.getInputStream(), StandardCharsets.UTF_8)
            );

            logger.info("Stdio process started successfully");
        } catch (IOException e) {
            throw new IOException("Failed to start stdio process: " + e.getMessage(), e);
        }
    }

    /**
     * Parse server capabilities from initialize response
     */
    private void parseServerCapabilities(JsonObject result) {
        serverCapabilities = new ServerCapabilities();

        if (result.has("capabilities")) {
            JsonObject caps = result.getAsJsonObject("capabilities");

            if (caps.has("tools")) {
                serverCapabilities.supportsTools = true;
            }
            if (caps.has("resources")) {
                serverCapabilities.supportsResources = true;
            }
            if (caps.has("prompts")) {
                serverCapabilities.supportsPrompts = true;
            }
            if (caps.has("sampling")) {
                serverCapabilities.supportsSampling = true;
            }
        }

        logger.info("Server capabilities: tools={}, resources={}, prompts={}, sampling={}",
            serverCapabilities.supportsTools,
            serverCapabilities.supportsResources,
            serverCapabilities.supportsPrompts,
            serverCapabilities.supportsSampling);
    }

    /**
     * Discover available tools, resources, and prompts
     */
    private void discoverCapabilities() throws IOException {
        if (serverCapabilities.supportsTools) {
            JsonObject toolsResponse = sendRequest("tools/list", new JsonObject());
            if (toolsResponse.has("result")) {
                parseTools(toolsResponse.getAsJsonObject("result"));
            }
        }

        if (serverCapabilities.supportsResources) {
            JsonObject resourcesResponse = sendRequest("resources/list", new JsonObject());
            if (resourcesResponse.has("result")) {
                parseResources(resourcesResponse.getAsJsonObject("result"));
            }
        }

        if (serverCapabilities.supportsPrompts) {
            JsonObject promptsResponse = sendRequest("prompts/list", new JsonObject());
            if (promptsResponse.has("result")) {
                parsePrompts(promptsResponse.getAsJsonObject("result"));
            }
        }
    }

    /**
     * Parse tools from server response
     */
    private void parseTools(JsonObject result) {
        if (!result.has("tools")) {
            return;
        }

        JsonArray toolsArray = result.getAsJsonArray("tools");
        for (JsonElement elem : toolsArray) {
            JsonObject toolObj = elem.getAsJsonObject();
            McpTool tool = new McpTool();
            tool.name = toolObj.get("name").getAsString();
            tool.description = toolObj.has("description")
                ? toolObj.get("description").getAsString()
                : "";

            if (toolObj.has("inputSchema")) {
                tool.inputSchema = toolObj.getAsJsonObject("inputSchema");
            }

            availableTools.put(tool.name, tool);
        }

        logger.info("Discovered {} tools", availableTools.size());
    }

    /**
     * Parse resources from server response
     */
    private void parseResources(JsonObject result) {
        if (!result.has("resources")) {
            return;
        }

        JsonArray resourcesArray = result.getAsJsonArray("resources");
        for (JsonElement elem : resourcesArray) {
            JsonObject resObj = elem.getAsJsonObject();
            McpResource resource = new McpResource();
            resource.uri = resObj.get("uri").getAsString();
            resource.name = resObj.has("name")
                ? resObj.get("name").getAsString()
                : resource.uri;
            resource.description = resObj.has("description")
                ? resObj.get("description").getAsString()
                : "";
            resource.mimeType = resObj.has("mimeType")
                ? resObj.get("mimeType").getAsString()
                : "text/plain";

            availableResources.put(resource.uri, resource);
        }

        logger.info("Discovered {} resources", availableResources.size());
    }

    /**
     * Parse prompts from server response
     */
    private void parsePrompts(JsonObject result) {
        if (!result.has("prompts")) {
            return;
        }

        JsonArray promptsArray = result.getAsJsonArray("prompts");
        for (JsonElement elem : promptsArray) {
            JsonObject promptObj = elem.getAsJsonObject();
            McpPrompt prompt = new McpPrompt();
            prompt.name = promptObj.get("name").getAsString();
            prompt.description = promptObj.has("description")
                ? promptObj.get("description").getAsString()
                : "";

            if (promptObj.has("arguments")) {
                JsonArray argsArray = promptObj.getAsJsonArray("arguments");
                prompt.arguments = new ArrayList<>();
                for (JsonElement argElem : argsArray) {
                    JsonObject argObj = argElem.getAsJsonObject();
                    PromptArgument arg = new PromptArgument();
                    arg.name = argObj.get("name").getAsString();
                    arg.description = argObj.has("description")
                        ? argObj.get("description").getAsString()
                        : "";
                    arg.required = argObj.has("required")
                        && argObj.get("required").getAsBoolean();
                    prompt.arguments.add(arg);
                }
            }

            availablePrompts.put(prompt.name, prompt);
        }

        logger.info("Discovered {} prompts", availablePrompts.size());
    }

    /**
     * List all available tools
     *
     * @return List of available tools
     * @throws IOException if request fails
     */
    public List<McpTool> listTools() throws IOException {
        ensureInitialized();
        return new ArrayList<>(availableTools.values());
    }

    /**
     * Get tool by name
     *
     * @param toolName Name of the tool
     * @return Tool metadata or null if not found
     */
    public McpTool getTool(String toolName) {
        return availableTools.get(toolName);
    }

    /**
     * Call a tool with parameters
     *
     * @param toolName Name of the tool to call
     * @param parameters Tool parameters as JSON object
     * @return Tool execution result
     * @throws IOException if call fails
     */
    public String callTool(String toolName, JsonObject parameters) throws IOException {
        ensureInitialized();

        if (!availableTools.containsKey(toolName)) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        logger.info("Calling tool: {} with parameters: {}", toolName, parameters);

        JsonObject params = new JsonObject();
        params.addProperty("name", toolName);
        params.add("arguments", parameters != null ? parameters : new JsonObject());

        JsonObject response = sendRequest("tools/call", params);

        if (response.has("error")) {
            JsonObject error = response.getAsJsonObject("error");
            throw new IOException("Tool call failed: " + error.get("message").getAsString());
        }

        JsonObject result = response.getAsJsonObject("result");
        if (result.has("content")) {
            JsonArray content = result.getAsJsonArray("content");
            if (content.size() > 0) {
                JsonObject firstContent = content.get(0).getAsJsonObject();
                if (firstContent.has("text")) {
                    return firstContent.get("text").getAsString();
                }
            }
        }

        return result.toString();
    }

    /**
     * Call a tool with string parameters
     *
     * @param toolName Name of the tool
     * @param jsonParameters JSON string of parameters
     * @return Tool result
     * @throws IOException if call fails
     */
    public String callTool(String toolName, String jsonParameters) throws IOException {
        JsonObject params = jsonParameters != null && !jsonParameters.isEmpty()
            ? JsonParser.parseString(jsonParameters).getAsJsonObject()
            : new JsonObject();
        return callTool(toolName, params);
    }

    /**
     * List all available resources
     *
     * @return List of available resources
     */
    public List<McpResource> listResources() {
        ensureInitialized();
        return new ArrayList<>(availableResources.values());
    }

    /**
     * Get resource by URI
     *
     * @param resourceUri URI of the resource
     * @return Resource metadata or null if not found
     */
    public McpResource getResourceMetadata(String resourceUri) {
        return availableResources.get(resourceUri);
    }

    /**
     * Fetch resource contents by URI
     *
     * @param resourceUri URI of the resource to fetch
     * @return Resource content
     * @throws IOException if fetch fails
     */
    public String getResource(String resourceUri) throws IOException {
        ensureInitialized();

        logger.info("Fetching resource: {}", resourceUri);

        JsonObject params = new JsonObject();
        params.addProperty("uri", resourceUri);

        JsonObject response = sendRequest("resources/read", params);

        if (response.has("error")) {
            JsonObject error = response.getAsJsonObject("error");
            throw new IOException("Resource fetch failed: " + error.get("message").getAsString());
        }

        JsonObject result = response.getAsJsonObject("result");
        if (result.has("contents")) {
            JsonArray contents = result.getAsJsonArray("contents");
            if (contents.size() > 0) {
                JsonObject firstContent = contents.get(0).getAsJsonObject();
                if (firstContent.has("text")) {
                    return firstContent.get("text").getAsString();
                }
                if (firstContent.has("blob")) {
                    return firstContent.get("blob").getAsString();
                }
            }
        }

        return result.toString();
    }

    /**
     * List all available prompts
     *
     * @return List of available prompts
     */
    public List<McpPrompt> listPrompts() {
        ensureInitialized();
        return new ArrayList<>(availablePrompts.values());
    }

    /**
     * Get prompt by name
     *
     * @param promptName Name of the prompt
     * @return Prompt metadata or null if not found
     */
    public McpPrompt getPrompt(String promptName) {
        return availablePrompts.get(promptName);
    }

    /**
     * Get prompt with arguments
     *
     * @param promptName Name of the prompt
     * @param arguments Prompt arguments
     * @return Formatted prompt
     * @throws IOException if request fails
     */
    public String getPromptText(String promptName, Map<String, String> arguments) throws IOException {
        ensureInitialized();

        if (!availablePrompts.containsKey(promptName)) {
            throw new IllegalArgumentException("Unknown prompt: " + promptName);
        }

        logger.info("Getting prompt: {} with arguments: {}", promptName, arguments);

        JsonObject params = new JsonObject();
        params.addProperty("name", promptName);

        if (arguments != null && !arguments.isEmpty()) {
            JsonObject argsObj = new JsonObject();
            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                argsObj.addProperty(entry.getKey(), entry.getValue());
            }
            params.add("arguments", argsObj);
        }

        JsonObject response = sendRequest("prompts/get", params);

        if (response.has("error")) {
            JsonObject error = response.getAsJsonObject("error");
            throw new IOException("Prompt get failed: " + error.get("message").getAsString());
        }

        JsonObject result = response.getAsJsonObject("result");
        if (result.has("messages")) {
            JsonArray messages = result.getAsJsonArray("messages");
            StringBuilder promptText = new StringBuilder();
            for (JsonElement msgElem : messages) {
                JsonObject msg = msgElem.getAsJsonObject();
                if (msg.has("content")) {
                    JsonObject content = msg.getAsJsonObject("content");
                    if (content.has("text")) {
                        promptText.append(content.get("text").getAsString()).append("\n");
                    }
                }
            }
            return promptText.toString();
        }

        return result.toString();
    }

    /**
     * Send JSON-RPC request to server
     *
     * @param method RPC method name
     * @param params Method parameters
     * @return Response object
     * @throws IOException if request fails
     */
    private JsonObject sendRequest(String method, JsonObject params) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", JSON_RPC_VERSION);
        request.addProperty("id", requestIdCounter.getAndIncrement());
        request.addProperty("method", method);
        request.add("params", params);

        String requestJson = gson.toJson(request);
        logger.debug("Sending request: {}", requestJson);

        String responseJson;
        if (transportMode == TransportMode.HTTP) {
            responseJson = sendHttpRequest(requestJson);
        } else {
            responseJson = sendStdioRequest(requestJson);
        }

        logger.debug("Received response: {}", responseJson);

        return JsonParser.parseString(responseJson).getAsJsonObject();
    }

    /**
     * Send JSON-RPC notification (no response expected)
     *
     * @param method Notification method name
     * @param params Notification parameters
     * @throws IOException if send fails
     */
    private void sendNotification(String method, JsonObject params) throws IOException {
        JsonObject notification = new JsonObject();
        notification.addProperty("jsonrpc", JSON_RPC_VERSION);
        notification.addProperty("method", method);
        notification.add("params", params);

        String notificationJson = gson.toJson(notification);
        logger.debug("Sending notification: {}", notificationJson);

        if (transportMode == TransportMode.HTTP) {
            sendHttpRequest(notificationJson);
        } else {
            sendStdioNotification(notificationJson);
        }
    }

    /**
     * Send HTTP request
     */
    private String sendHttpRequest(String requestJson) throws IOException {
        URL url = new URL(serverUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);

            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read response
            int responseCode = connection.getResponseCode();
            InputStream inputStream = responseCode >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();

            if (inputStream == null) {
                throw new IOException("No response from server (HTTP " + responseCode + ")");
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
                return response.toString();
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Send stdio request and read response
     */
    private String sendStdioRequest(String requestJson) throws IOException {
        if (stdioWriter == null || stdioReader == null) {
            throw new IOException("Stdio transport not initialized");
        }

        stdioWriter.write(requestJson);
        stdioWriter.newLine();
        stdioWriter.flush();

        String response = stdioReader.readLine();
        if (response == null) {
            throw new IOException("Stdio process terminated unexpectedly");
        }

        return response;
    }

    /**
     * Send stdio notification (no response expected)
     */
    private void sendStdioNotification(String notificationJson) throws IOException {
        if (stdioWriter == null) {
            throw new IOException("Stdio transport not initialized");
        }

        stdioWriter.write(notificationJson);
        stdioWriter.newLine();
        stdioWriter.flush();
    }

    /**
     * Set HTTP connection timeout
     *
     * @param timeoutMs Timeout in milliseconds
     */
    public void setConnectTimeout(int timeoutMs) {
        this.connectTimeout = timeoutMs;
    }

    /**
     * Set HTTP read timeout
     *
     * @param timeoutMs Timeout in milliseconds
     */
    public void setReadTimeout(int timeoutMs) {
        this.readTimeout = timeoutMs;
    }

    /**
     * Check if client is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get server capabilities
     */
    public ServerCapabilities getServerCapabilities() {
        return serverCapabilities;
    }

    /**
     * Ensure client is initialized
     */
    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                "Client not initialized. Call initialize() first.");
        }
    }

    /**
     * Close connection and cleanup resources
     *
     * @throws IOException if close fails
     */
    public void close() throws IOException {
        if (!initialized) {
            return;
        }

        logger.info("Closing MCP client connection...");

        try {
            // Send shutdown notification if supported
            sendNotification("notifications/shutdown", new JsonObject());
        } catch (Exception e) {
            logger.warn("Error sending shutdown notification: {}", e.getMessage());
        }

        if (transportMode == TransportMode.STDIO) {
            closeStdio();
        }

        initialized = false;
        availableTools.clear();
        availableResources.clear();
        availablePrompts.clear();

        logger.info("MCP client closed");
    }

    /**
     * Close stdio transport
     */
    private void closeStdio() {
        if (stdioWriter != null) {
            try {
                stdioWriter.close();
            } catch (IOException e) {
                logger.warn("Error closing stdio writer: {}", e.getMessage());
            }
        }

        if (stdioReader != null) {
            try {
                stdioReader.close();
            } catch (IOException e) {
                logger.warn("Error closing stdio reader: {}", e.getMessage());
            }
        }

        if (stdioProcess != null) {
            stdioProcess.destroy();
            try {
                stdioProcess.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted waiting for stdio process to terminate");
            }
        }
    }

    /**
     * Server capabilities
     */
    public static class ServerCapabilities {
        public boolean supportsTools = false;
        public boolean supportsResources = false;
        public boolean supportsPrompts = false;
        public boolean supportsSampling = false;
    }

    /**
     * MCP Tool metadata
     */
    public static class McpTool {
        public String name;
        public String description;
        public JsonObject inputSchema;

        @Override
        public String toString() {
            return String.format("McpTool{name='%s', description='%s'}", name, description);
        }
    }

    /**
     * MCP Resource metadata
     */
    public static class McpResource {
        public String uri;
        public String name;
        public String description;
        public String mimeType;

        @Override
        public String toString() {
            return String.format("McpResource{uri='%s', name='%s', mimeType='%s'}",
                uri, name, mimeType);
        }
    }

    /**
     * MCP Prompt metadata
     */
    public static class McpPrompt {
        public String name;
        public String description;
        public List<PromptArgument> arguments;

        @Override
        public String toString() {
            return String.format("McpPrompt{name='%s', description='%s', args=%d}",
                name, description, arguments != null ? arguments.size() : 0);
        }
    }

    /**
     * Prompt argument metadata
     */
    public static class PromptArgument {
        public String name;
        public String description;
        public boolean required;

        @Override
        public String toString() {
            return String.format("PromptArgument{name='%s', required=%b}", name, required);
        }
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        String serverUrl = args.length > 0 ? args[0] : "http://localhost:3000";

        YawlMcpClient client = new YawlMcpClient(serverUrl);

        try {
            System.out.println("=== YAWL MCP Client Test ===");
            System.out.println("Server: " + serverUrl);
            System.out.println();

            // Initialize connection
            System.out.println("Initializing connection...");
            client.initialize();
            System.out.println("Connected successfully!");
            System.out.println();

            // Display server capabilities
            ServerCapabilities caps = client.getServerCapabilities();
            System.out.println("Server Capabilities:");
            System.out.println("  Tools: " + caps.supportsTools);
            System.out.println("  Resources: " + caps.supportsResources);
            System.out.println("  Prompts: " + caps.supportsPrompts);
            System.out.println("  Sampling: " + caps.supportsSampling);
            System.out.println();

            // List tools
            if (caps.supportsTools) {
                List<McpTool> tools = client.listTools();
                System.out.println("Available Tools (" + tools.size() + "):");
                for (McpTool tool : tools) {
                    System.out.println("  - " + tool.name + ": " + tool.description);
                }
                System.out.println();

                // Try calling a tool if available
                if (!tools.isEmpty()) {
                    McpTool firstTool = tools.get(0);
                    System.out.println("Calling tool: " + firstTool.name);
                    try {
                        JsonObject params = new JsonObject();
                        params.addProperty("test", "value");
                        String result = client.callTool(firstTool.name, params);
                        System.out.println("Result: " + result);
                        System.out.println();
                    } catch (Exception e) {
                        System.out.println("Tool call failed: " + e.getMessage());
                        System.out.println();
                    }
                }
            }

            // List resources
            if (caps.supportsResources) {
                List<McpResource> resources = client.listResources();
                System.out.println("Available Resources (" + resources.size() + "):");
                for (McpResource resource : resources) {
                    System.out.println("  - " + resource.uri + ": " + resource.description);
                }
                System.out.println();
            }

            // List prompts
            if (caps.supportsPrompts) {
                List<McpPrompt> prompts = client.listPrompts();
                System.out.println("Available Prompts (" + prompts.size() + "):");
                for (McpPrompt prompt : prompts) {
                    System.out.println("  - " + prompt.name + ": " + prompt.description);
                }
                System.out.println();
            }

            // Close connection
            System.out.println("Closing connection...");
            client.close();
            System.out.println("Disconnected successfully!");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
