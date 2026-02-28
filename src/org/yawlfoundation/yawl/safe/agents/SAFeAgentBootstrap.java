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

package org.yawlfoundation.yawl.safe.agents;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Bootstrap utility for starting SAFe agents in production.
 *
 * <p>Usage:
 * <pre>
 * export YAWL_ENGINE_URL=http://localhost:8080/yawl
 * export YAWL_USERNAME=admin
 * export YAWL_PASSWORD=YAWL
 * export SAFE_BASE_PORT=8090
 *
 * java org.yawlfoundation.yawl.safe.agents.SAFeAgentBootstrap
 * </pre>
 *
 * <p>Starts all five SAFe agents and registers graceful shutdown hook.
 *
 * @since YAWL 6.0
 */
public final class SAFeAgentBootstrap {

    private static final Logger logger = LogManager.getLogger(SAFeAgentBootstrap.class);

    private SAFeAgentBootstrap() {
        // Static utility class
    }

    /**
     * Main entry point for SAFe agent startup.
     *
     * @param args command-line arguments (not used; uses environment variables)
     */
    public static void main(String[] args) {
        try {
            // Read environment configuration
            String engineUrl = getEnv("YAWL_ENGINE_URL", "http://localhost:8080/yawl");
            String username = getEnv("YAWL_USERNAME", "admin");
            String password = getEnv("YAWL_PASSWORD", "YAWL");
            int basePort = getIntEnv("SAFE_BASE_PORT", 8090);

            logger.info("Initializing SAFe agent team");
            logger.info("  Engine URL: {}", engineUrl);
            logger.info("  Base port: {}", basePort);

            // Create registry and agents
            SAFeAgentRegistry registry = new SAFeAgentRegistry(
                engineUrl, username, password, basePort);

            // Start all agents
            registry.createCompleteTeam();
            registry.startAll();

            logger.info("SAFe agent team started successfully");
            logger.info("  ProductOwner agent on port {}", basePort);
            logger.info("  ScrumMaster agent on port {}", basePort + 1);
            logger.info("  Developer agent on port {}", basePort + 2);
            logger.info("  SystemArchitect agent on port {}", basePort + 3);
            logger.info("  ReleaseTrainEngineer agent on port {}", basePort + 4);

            // Register graceful shutdown
            registerShutdownHook(registry);

            // Keep the application running
            logger.info("SAFe agent team is running. Press Ctrl+C to stop.");
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.fatal("Failed to start SAFe agents: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * Register JVM shutdown hook for graceful agent shutdown.
     */
    private static void registerShutdownHook(SAFeAgentRegistry registry) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, stopping SAFe agents...");
            registry.stopAll();
            logger.info("SAFe agents stopped gracefully");
        }));
    }

    /**
     * Get environment variable with default fallback.
     */
    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            logger.warn("Environment variable {} not set, using default: {}", key, defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * Get integer environment variable with default fallback.
     */
    private static int getIntEnv(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            logger.warn("Environment variable {} not set, using default: {}", key, defaultValue);
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }
}
