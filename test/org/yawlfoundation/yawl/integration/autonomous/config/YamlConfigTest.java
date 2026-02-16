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
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous.config;

import org.yawlfoundation.yawl.integration.autonomous.AgentConfiguration;

import java.io.File;

/**
 * Simple test program to verify YAML configuration loading.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YamlConfigTest {

    public static void main(String[] args) {
        try {
            String yawlRoot = System.getProperty("user.dir", "/home/user/yawl");
            String configPath = yawlRoot + "/config/agents/orderfulfillment/ordering-agent.yaml";

            File configFile = new File(configPath);
            if (!configFile.exists()) {
                System.err.println("Config file not found: " + configPath);
                System.exit(1);
            }

            System.out.println("Loading configuration from: " + configPath);
            System.out.println("Set ZAI_API_KEY environment variable for full testing\n");

            AgentConfigLoader loader = AgentConfigLoader.fromFile(configPath);

            System.out.println("Configuration loaded successfully!");
            System.out.println("Attempting to build AgentConfiguration...\n");

            try {
                AgentConfiguration config = loader.build();

                System.out.println("AgentConfiguration built successfully!");
                System.out.println("Agent Details:");
                System.out.println("  Name: " + config.getAgentName());
                System.out.println("  Capability: " + config.getCapability());
                System.out.println("  Engine URL: " + config.getEngineUrl());
                System.out.println("  Username: " + config.getUsername());
                System.out.println("  Port: " + config.getPort());
                System.out.println("  Poll Interval: " + config.getPollIntervalMs() + "ms");
                System.out.println("\nAll validations passed!");

            } catch (Exception e) {
                if (e.getMessage().contains("ZAI API key")) {
                    System.out.println("Expected error (no ZAI API key set): " + e.getMessage());
                    System.out.println("\nPartial test PASSED - config loading works!");
                    System.out.println("Set ZAI_API_KEY environment variable for full test.");
                } else {
                    throw e;
                }
            }

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
