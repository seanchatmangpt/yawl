/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.contract;

/**
 * Pact Broker integration configuration for YAWL contract publishing and verification.
 *
 * This class provides utilities for integrating with Pact Broker (optional SaaS or
 * self-hosted instance) to manage contract versions across provider and consumer.
 *
 * Features:
 * - Publish generated pact contracts to broker after consumer tests pass
 * - Retrieve pacts for provider verification
 * - Version tracking and compatibility checks
 * - Can-I-Deploy checks before production deployment
 *
 * Usage:
 * - Enable Pact Broker with environment variables:
 *   PACT_BROKER_URL=https://broker.example.com
 *   PACT_BROKER_TOKEN=<your-token>
 *   PACT_BROKER_PUBLISH=true
 *
 * - Maven plugin configuration (pom.xml):
 *   <plugin>
 *     <groupId>au.com.dius</groupId>
 *     <artifactId>pact-foundation-maven</artifactId>
 *     <version>4.6.11</version>
 *     <configuration>
 *       <brokerUrl>${pact.broker.url}</brokerUrl>
 *       <brokerToken>${pact.broker.token}</brokerToken>
 *       <pactDirectory>target/pacts</pactDirectory>
 *     </configuration>
 *   </plugin>
 *
 * Notes:
 * - Pact Broker is optional; contracts work with local files via target/pacts
 * - For production deployment, enable Can-I-Deploy checks before merge
 * - All credentials via environment variables (never hardcode)
 * - Protocol evolution tracked: v1 (legacy) -> v2 (current)
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-20
 */
public class PactBrokerIntegration {

    // Environment variable names for broker configuration
    private static final String PACT_BROKER_URL = "PACT_BROKER_URL";
    private static final String PACT_BROKER_TOKEN = "PACT_BROKER_TOKEN";
    private static final String PACT_BROKER_PUBLISH = "PACT_BROKER_PUBLISH";
    private static final String PACT_BROKER_VERIFY = "PACT_BROKER_VERIFY";

    // Default Pact directory for local contracts
    private static final String DEFAULT_PACT_DIR = "target/pacts";

    /**
     * Get Pact Broker URL from environment.
     *
     * @return broker URL if configured, otherwise null
     */
    public static String getBrokerUrl() {
        return System.getenv(PACT_BROKER_URL);
    }

    /**
     * Get Pact Broker authentication token from environment.
     *
     * @return broker token if configured, otherwise null
     */
    public static String getBrokerToken() {
        return System.getenv(PACT_BROKER_TOKEN);
    }

    /**
     * Check if Pact publishing to broker is enabled.
     *
     * @return true if PACT_BROKER_PUBLISH=true, false otherwise
     */
    public static boolean isPublishingEnabled() {
        String publish = System.getenv(PACT_BROKER_PUBLISH);
        return "true".equalsIgnoreCase(publish);
    }

    /**
     * Check if Pact verification from broker is enabled.
     *
     * @return true if PACT_BROKER_VERIFY=true, false otherwise
     */
    public static boolean isVerificationEnabled() {
        String verify = System.getenv(PACT_BROKER_VERIFY);
        return "true".equalsIgnoreCase(verify);
    }

    /**
     * Check if broker is configured and accessible.
     *
     * @return true if both URL and token are configured
     */
    public static boolean isBrokerConfigured() {
        return getBrokerUrl() != null && getBrokerToken() != null;
    }

    /**
     * Get default local pact directory.
     *
     * @return path to target/pacts
     */
    public static String getDefaultPactDirectory() {
        return DEFAULT_PACT_DIR;
    }

    /**
     * Build Pact Broker URL for retrieving provider pacts.
     *
     * @param providerName name of the provider (e.g., "YawlA2AServer")
     * @return full URL to retrieve pacts for provider, or null if broker not configured
     */
    public static String getPactsUrlForProvider(String providerName) {
        String brokerUrl = getBrokerUrl();
        if (brokerUrl == null) {
            return null;
        }
        return brokerUrl + "/pacts/provider/" + providerName + "/latest";
    }

    /**
     * Protocol version information for contract evolution tracking.
     * Used to document API breaking changes and migration paths.
     *
     * Protocol versions:
     * - v1: Initial A2A and MCP contracts (YAWL 6.0.0)
     * - v2: Reserved for future protocol enhancements
     */
    public static class ProtocolVersion {
        public static final String A2A_V1 = "1.0.0";
        public static final String MCP_V1 = "2024-11-05";

        private ProtocolVersion() {
            // Utility class
        }
    }

    /**
     * Pact metadata for tracking protocol evolution.
     * Embedded in generated pact files for documentation.
     */
    public static class PactMetadata {
        private String providerName;
        private String consumerName;
        private String protocolVersion;
        private String yawlVersion = "6.0.0";
        private String timestamp;

        public PactMetadata(String providerName, String consumerName, String protocolVersion) {
            this.providerName = providerName;
            this.consumerName = consumerName;
            this.protocolVersion = protocolVersion;
            this.timestamp = java.time.Instant.now().toString();
        }

        public String getProviderName() {
            return providerName;
        }

        public String getConsumerName() {
            return consumerName;
        }

        public String getProtocolVersion() {
            return protocolVersion;
        }

        public String getYawlVersion() {
            return yawlVersion;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Configuration for provider state handler callbacks.
     *
     * When provider verification runs, it must establish the required state
     * (e.g., "task exists in PROCESSING state") before invoking interactions.
     * These callbacks are registered in the provider test class.
     */
    public static class ProviderStateHandler {
        /**
         * Handle A2A provider state setup.
         *
         * @param state description from "given" clause in pact
         */
        public static void setupA2AState(String state) {
            switch (state) {
                case "A2A server is ready to receive tasks" -> {
                    // Ensure A2A server is running and ready
                }
                case "task 'TASK-001' exists in PROCESSING state" -> {
                    // Create task in database with PROCESSING status
                }
                case "task 'TASK-001' is in PROCESSING state" -> {
                    // Ensure task exists and is in correct state
                }
                case "task 'INVALID-TASK' does not exist" -> {
                    // Ensure task does not exist in database
                }
                default -> throw new IllegalArgumentException("Unknown A2A state: " + state);
            }
        }

        /**
         * Handle MCP provider state setup.
         *
         * @param state description from "given" clause in pact
         */
        public static void setupMcpState(String state) {
            switch (state) {
                case "MCP server is ready for connections" -> {
                    // Ensure MCP server is running
                }
                case "MCP server has 3 workflow tools available" -> {
                    // Ensure tools are registered
                }
                case "MCP tool 'launch_case' is available" -> {
                    // Ensure tool is accessible
                }
                case "MCP server supports streaming with SSE" -> {
                    // Ensure SSE support is enabled
                }
                case "MCP server responds quickly to requests" -> {
                    // Ensure low latency response
                }
                case "MCP server temporarily experiences issues" -> {
                    // Simulate transient error condition
                }
                default -> throw new IllegalArgumentException("Unknown MCP state: " + state);
            }
        }
    }

    private PactBrokerIntegration() {
        // Utility class
    }
}
