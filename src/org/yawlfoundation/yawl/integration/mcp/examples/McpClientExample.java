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

package org.yawlfoundation.yawl.integration.mcp.examples;

import com.google.gson.JsonObject;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpClient;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpClient.McpTool;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpClient.McpResource;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpClient.McpPrompt;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpClient.ServerCapabilities;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example demonstrating YawlMcpClient usage
 *
 * This example shows how to:
 * - Connect to an MCP server
 * - Discover and call tools
 * - Fetch resources
 * - Use prompts
 * - Handle errors gracefully
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class McpClientExample {

    /**
     * Example 1: Basic connection and capability discovery
     */
    public static void basicConnectionExample(String serverUrl) {
        System.out.println("\n=== Example 1: Basic Connection ===");

        YawlMcpClient client = new YawlMcpClient(serverUrl);

        try {
            // Initialize connection
            System.out.println("Connecting to " + serverUrl + "...");
            client.initialize();
            System.out.println("Connected successfully!");

            // Check server capabilities
            ServerCapabilities caps = client.getServerCapabilities();
            System.out.println("\nServer Capabilities:");
            System.out.println("  - Tools: " + caps.supportsTools);
            System.out.println("  - Resources: " + caps.supportsResources);
            System.out.println("  - Prompts: " + caps.supportsPrompts);
            System.out.println("  - Sampling: " + caps.supportsSampling);

            // Close connection
            client.close();
            System.out.println("\nConnection closed.");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Example 2: Tool discovery and invocation
     */
    public static void toolInvocationExample(String serverUrl) {
        System.out.println("\n=== Example 2: Tool Invocation ===");

        YawlMcpClient client = new YawlMcpClient(serverUrl);

        try {
            client.initialize();

            // List all available tools
            List<McpTool> tools = client.listTools();
            System.out.println("Found " + tools.size() + " tools:");
            for (McpTool tool : tools) {
                System.out.println("  - " + tool.name);
                System.out.println("    Description: " + tool.description);
                if (tool.inputSchema != null) {
                    System.out.println("    Schema: " + tool.inputSchema);
                }
            }

            // Call a specific tool
            if (!tools.isEmpty()) {
                McpTool firstTool = tools.get(0);
                System.out.println("\nCalling tool: " + firstTool.name);

                JsonObject params = new JsonObject();
                params.addProperty("input", "test data");
                params.addProperty("mode", "analysis");

                try {
                    String result = client.callTool(firstTool.name, params);
                    System.out.println("Result: " + result);
                } catch (Exception e) {
                    System.err.println("Tool call failed: " + e.getMessage());
                }
            }

            client.close();

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * Example 3: Resource fetching
     */
    public static void resourceFetchingExample(String serverUrl) {
        System.out.println("\n=== Example 3: Resource Fetching ===");

        YawlMcpClient client = new YawlMcpClient(serverUrl);

        try {
            client.initialize();

            // List available resources
            List<McpResource> resources = client.listResources();
            System.out.println("Found " + resources.size() + " resources:");
            for (McpResource resource : resources) {
                System.out.println("  - " + resource.uri);
                System.out.println("    Name: " + resource.name);
                System.out.println("    Type: " + resource.mimeType);
                System.out.println("    Description: " + resource.description);
            }

            // Fetch a specific resource
            if (!resources.isEmpty()) {
                McpResource firstResource = resources.get(0);
                System.out.println("\nFetching resource: " + firstResource.uri);

                try {
                    String content = client.getResource(firstResource.uri);
                    System.out.println("Content length: " + content.length() + " characters");
                    System.out.println("First 200 chars: " +
                        content.substring(0, Math.min(200, content.length())));
                } catch (Exception e) {
                    System.err.println("Resource fetch failed: " + e.getMessage());
                }
            }

            client.close();

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * Example 4: Prompt usage
     */
    public static void promptUsageExample(String serverUrl) {
        System.out.println("\n=== Example 4: Prompt Usage ===");

        YawlMcpClient client = new YawlMcpClient(serverUrl);

        try {
            client.initialize();

            // List available prompts
            List<McpPrompt> prompts = client.listPrompts();
            System.out.println("Found " + prompts.size() + " prompts:");
            for (McpPrompt prompt : prompts) {
                System.out.println("  - " + prompt.name);
                System.out.println("    Description: " + prompt.description);
                if (prompt.arguments != null) {
                    System.out.println("    Arguments: " + prompt.arguments.size());
                    for (var arg : prompt.arguments) {
                        System.out.println("      - " + arg.name +
                            (arg.required ? " (required)" : " (optional)"));
                    }
                }
            }

            // Get a prompt with arguments
            if (!prompts.isEmpty()) {
                McpPrompt firstPrompt = prompts.get(0);
                System.out.println("\nGetting prompt: " + firstPrompt.name);

                Map<String, String> args = new HashMap<>();
                args.put("taskType", "workflow_analysis");
                args.put("context", "production");

                try {
                    String promptText = client.getPromptText(firstPrompt.name, args);
                    System.out.println("Prompt text:");
                    System.out.println(promptText);
                } catch (Exception e) {
                    System.err.println("Prompt retrieval failed: " + e.getMessage());
                }
            }

            client.close();

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * Example 5: Error handling and retry logic
     */
    public static void errorHandlingExample(String serverUrl) {
        System.out.println("\n=== Example 5: Error Handling ===");

        YawlMcpClient client = new YawlMcpClient(serverUrl);
        int maxRetries = 3;
        int retryDelay = 1000; // ms

        // Try to connect with retries
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                System.out.println("Connection attempt " + attempt + "...");
                client.initialize();
                System.out.println("Connected successfully!");
                break;
            } catch (IOException e) {
                System.err.println("Attempt " + attempt + " failed: " + e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    System.err.println("All connection attempts failed.");
                    return;
                }
            }
        }

        try {
            // Try calling unknown tool - should fail gracefully
            System.out.println("\nTrying to call unknown tool...");
            try {
                JsonObject params = new JsonObject();
                client.callTool("unknownTool", params);
            } catch (IllegalArgumentException e) {
                System.out.println("Correctly caught: " + e.getMessage());
            }

            // Try fetching unknown resource
            System.out.println("\nTrying to fetch unknown resource...");
            try {
                client.getResource("unknown://resource");
            } catch (IOException e) {
                System.out.println("Correctly caught: " + e.getMessage());
            }

            client.close();
            System.out.println("\nError handling completed successfully.");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * Example 6: Custom timeout configuration
     */
    public static void timeoutConfigurationExample(String serverUrl) {
        System.out.println("\n=== Example 6: Timeout Configuration ===");

        YawlMcpClient client = new YawlMcpClient(serverUrl);

        // Configure timeouts for slow servers
        client.setConnectTimeout(5000);   // 5 seconds to connect
        client.setReadTimeout(30000);     // 30 seconds to read response

        try {
            System.out.println("Connecting with custom timeouts...");
            client.initialize();
            System.out.println("Connected!");

            // Perform operations...

            client.close();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * Main method - runs all examples
     */
    public static void main(String[] args) {
        String serverUrl = args.length > 0 ? args[0] : "http://localhost:3000";

        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  YAWL MCP Client Examples                                 ║");
        System.out.println("║  Testing against server: " + serverUrl);
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // Run examples
        basicConnectionExample(serverUrl);
        toolInvocationExample(serverUrl);
        resourceFetchingExample(serverUrl);
        promptUsageExample(serverUrl);
        errorHandlingExample(serverUrl);
        timeoutConfigurationExample(serverUrl);

        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  All Examples Completed                                   ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }
}
