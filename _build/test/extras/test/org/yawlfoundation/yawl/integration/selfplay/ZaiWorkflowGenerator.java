/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.selfplay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Z.AI Integration for Workflow Generation.
 *
 * Provides integration with Z.AI API for automated workflow XML generation.
 * Includes support for:
 * - REST API calls to Z.AI
 * - Prompt engineering for YAWL workflows
 * - Response parsing and validation
 * - Fallback mechanisms
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiWorkflowGenerator {

    private static final Logger logger = Logger.getLogger(ZaiWorkflowGenerator.class.getName());

    // Z.AI API configuration
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final HttpClient httpClient;
    private final SelfPlayConfig config;

    // XML template for fallback workflow generation
    private static final String FALLBACK_TEMPLATE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">
          <specification uri="%s">
            <metaData>
              <title>%s</title>
              <creator>YAWL Self-Play Orchestrator</creator>
              <description>%s</description>
              <version>1.0</version>
            </metaData>
            <decomposition id="mainFlow" xsi:type="NetFactsType" isRootNet="true">
              <name>Main Flow</name>
              <processControlElements>
                <inputCondition id="start">
                  <name>Start</name>
                  <flowsInto>
                    <nextElementRef id="task1"/>
                  </flowsInto>
                </inputCondition>
                <task id="task1">
                  <name>Task 1</name>
                  <documentation>First task in workflow</documentation>
                  <flowsInto>
                    <nextElementRef id="task2"/>
                  </flowsInto>
                  <join code="xor"/>
                  <split code="and"/>
                </task>
                <task id="task2">
                  <name>Task 2</name>
                  <documentation>Second task in workflow</documentation>
                  <flowsInto>
                    <nextElementRef id="end"/>
                  </flowsInto>
                  <join code="xor"/>
                  <split code="and"/>
                </task>
                <outputCondition id="end">
                  <name>End</name>
                </outputCondition>
              </processControlElements>
            </decomposition>
          </specification>
        </specificationSet>
        """;

    /**
     * Create a new ZAI workflow generator.
     */
    public ZaiWorkflowGenerator(SelfPlayConfig config) {
        this.config = config;
        this.apiKey = System.getenv("ZAI_API_KEY");
        this.apiUrl = System.getenv("ZAI_API_URL");
        this.model = System.getenv("ZAI_MODEL");

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(HttpClient.Version.HTTP_2)
            .build();

        if (apiKey == null || apiKey.isEmpty()) {
            logger.info("ZAI_API_KEY not set - ZAI integration will use fallback generation");
        }
    }

    /**
     * Generate workflow XML via Z.AI.
     */
    public CompletableFuture<String> generateWorkflowAsync(String promptType, Map<String, String> params) {
        if (apiKey == null || apiKey.isEmpty()) {
            return CompletableFuture.completedFuture(generateFallbackWorkflow(params));
        }

        try {
            String prompt = buildPrompt(promptType, params);
            return callZaiApi(prompt);
        } catch (Exception e) {
            logger.warning("ZAI generation failed, using fallback: " + e.getMessage());
            return CompletableFuture.completedFuture(generateFallbackWorkflow(params));
        }
    }

    /**
     * Generate workflow XML synchronously.
     */
    public String generateWorkflow(String promptType, Map<String, String> params) {
        try {
            String prompt = buildPrompt(promptType, params);
            return callZaiApiSync(prompt);
        } catch (Exception e) {
            logger.warning("ZAI generation failed, using fallback: " + e.getMessage());
            return generateFallbackWorkflow(params);
        }
    }

    /**
     * Build prompt for Z.AI based on type and parameters.
     */
    private String buildPrompt(String promptType, Map<String, String> params) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a YAWL workflow XML specification. ");

        switch (promptType) {
            case "sequence":
                prompt.append("Create a simple sequential workflow with 2-3 tasks. ");
                break;
            case "parallel":
                prompt.append("Create a workflow with parallel split and synchronization patterns. ");
                break;
            case "choice":
                prompt.append("Create a workflow with exclusive choice pattern. ");
                break;
            case "loop":
                prompt.append("Create a workflow with a loop pattern. ");
                break;
            default:
                prompt.append("Create a simple YAWL workflow. ");
        }

        // Add specific parameters
        if (params != null) {
            if (params.containsKey("task_count")) {
                prompt.append("Include exactly ").append(params.get("task_count")).append(" tasks. ");
            }
            if (params.containsKey("description")) {
                prompt.append("Workflow description: ").append(params.get("description")).append(". ");
            }
            if (params.containsKey("complexity")) {
                prompt.append("Complexity level: ").append(params.get("complexity")).append(". ");
            }
        }

        prompt.append("\n\nRequirements:\n");
        prompt.append("1. Must be valid YAWL XML 4.0\n");
        prompt.append("2. Include proper namespace declarations\n");
        prompt.append("3. Follow YAWL schema patterns\n");
        prompt.append("4. Return only the XML specification\n");

        return prompt.toString();
    }

    /**
     * Call Z.AI API asynchronously.
     */
    private CompletableFuture<String> callZaiApi(String prompt) {
        try {
            String requestBody = String.format("""
                {
                    "model": "%s",
                    "messages": [
                        {
                            "role": "system",
                            "content": "You are a YAWL workflow expert. Generate only valid YAWL XML specifications."
                        },
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ],
                    "temperature": 0.3,
                    "max_tokens": 2000
                }
                """, model != null ? model : "gpt-3.5-turbo", prompt.replace("\"", "\\\""));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl != null ? apiUrl : "https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseApiResponse(response.body());
                    } else {
                        throw new RuntimeException("ZAI API error: " + response.statusCode());
                    }
                })
                .exceptionally(e -> {
                    logger.warning("ZAI API call failed: " + e.getMessage());
                    return generateFallbackWorkflow(null);
                });

        } catch (Exception e) {
            return CompletableFuture.completedFuture(generateFallbackWorkflow(null));
        }
    }

    /**
     * Call Z.AI API synchronously.
     */
    private String callZaiApiSync(String prompt) throws IOException, InterruptedException {
        try {
            String requestBody = String.format("""
                {
                    "model": "%s",
                    "messages": [
                        {
                            "role": "system",
                            "content": "You are a YAWL workflow expert. Generate only valid YAWL XML specifications."
                        },
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ],
                    "temperature": 0.3,
                    "max_tokens": 2000
                }
                """, model != null ? model : "gpt-3.5-turbo", prompt.replace("\"", "\\\""));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl != null ? apiUrl : "https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseApiResponse(response.body());
            } else {
                throw new RuntimeException("ZAI API error: " + response.statusCode());
            }

        } catch (Exception e) {
            throw new RuntimeException("ZAI API call failed", e);
        }
    }

    /**
     * Parse Z.AI API response.
     */
    private String parseApiResponse(String response) {
        try {
            // Simple JSON parsing - in production, use proper JSON library
            int contentStart = response.indexOf("\"content\": \"");
            if (contentStart != -1) {
                int contentEnd = response.indexOf("\"", contentStart + 11);
                if (contentEnd != -1) {
                    String content = response.substring(contentStart + 11, contentEnd);
                    // Clean up the response
                    content = content.replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
                    return extractXmlFromResponse(content);
                }
            }
            throw new RuntimeException("Failed to parse ZAI response");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse ZAI response", e);
        }
    }

    /**
     * Extract XML from the AI response.
     */
    private String extractXmlFromResponse(String response) {
        try {
            // Look for XML content
            int xmlStart = response.indexOf("<?xml");
            if (xmlStart == -1) {
                xmlStart = response.indexOf("<specificationSet");
            }

            if (xmlStart != -1) {
                int xmlEnd = response.lastIndexOf("</specificationSet>");
                if (xmlEnd != -1) {
                    return response.substring(xmlStart, xmlEnd + "</specificationSet>".length());
                } else {
                    // Extract to end of string
                    return response.substring(xmlStart);
                }
            }

            throw new RuntimeException("No XML found in response");
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract XML from response", e);
        }
    }

    /**
     * Generate fallback workflow when ZAI is not available.
     */
    private String generateFallbackWorkflow(Map<String, String> params) {
        logger.info("Using fallback workflow generation");

        String uri = "fallbackWorkflow_" + UUID.randomUUID().toString().substring(0, 8);
        String title = "Fallback Workflow";
        String description = "Generated workflow when ZAI is unavailable";

        if (params != null) {
            if (params.containsKey("description")) {
                description = params.get("description");
            }
        }

        return String.format(FALLBACK_TEMPLATE, uri, title, description);
    }

    /**
     * Check if ZAI integration is available.
     */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Get ZAI configuration status.
     */
    public String getStatus() {
        if (apiKey == null || apiKey.isEmpty()) {
            return "disabled - ZAI_API_KEY not set";
        }
        if (apiUrl == null || apiUrl.isEmpty()) {
            return "configured - using default endpoint";
        }
        return "configured - " + apiUrl;
    }
}
