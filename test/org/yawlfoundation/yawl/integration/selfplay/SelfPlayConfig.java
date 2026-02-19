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

import java.util.Properties;

/**
 * Configuration class for Self-Play Test Orchestrator.
 *
 * Provides centralized configuration management with support for:
 * - Default values
 * - Environment variables
 * - Property files
 * - Command-line overrides
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SelfPlayConfig {

    private Properties properties;
    private boolean loaded;

    // Default values
    private static final String DEFAULT_ENGINE_URL = "http://localhost:8080/yawl";
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin";
    private static final int DEFAULT_ITERATIONS = 3;
    private static final long DEFAULT_TIMEOUT_MS = 30_000;
    private static final boolean DEFAULT_USE_ZAI = false;
    private static final String DEFAULT_OUTPUT_DIR = "./self-play-results";

    /**
     * Create a new configuration instance.
     */
    public SelfPlayConfig() {
        this.properties = new Properties();
        this.loaded = false;
        loadDefaults();
    }

    /**
     * Load default values.
     */
    private void loadDefaults() {
        properties.setProperty("engine.url", DEFAULT_ENGINE_URL);
        properties.setProperty("username", DEFAULT_USERNAME);
        properties.setProperty("password", DEFAULT_PASSWORD);
        properties.setProperty("iterations", String.valueOf(DEFAULT_ITERATIONS));
        properties.setProperty("timeout.ms", String.valueOf(DEFAULT_TIMEOUT_MS));
        properties.setProperty("use.zai", String.valueOf(DEFAULT_USE_ZAI));
        properties.setProperty("output.dir", DEFAULT_OUTPUT_DIR);
    }

    /**
     * Load configuration from environment variables.
     */
    public void loadFromEnvironment() {
        String envEngineUrl = System.getenv("YAWL_ENGINE_URL");
        if (envEngineUrl != null && !envEngineUrl.isEmpty()) {
            properties.setProperty("engine.url", envEngineUrl);
        }

        String envUsername = System.getenv("YAWL_USERNAME");
        if (envUsername != null && !envUsername.isEmpty()) {
            properties.setProperty("username", envUsername);
        }

        String envPassword = System.getenv("YAWL_PASSWORD");
        if (envPassword != null && !envPassword.isEmpty()) {
            properties.setProperty("password", envPassword);
        }

        String envIterations = System.getenv("YAWL_ITERATIONS");
        if (envIterations != null && !envIterations.isEmpty()) {
            properties.setProperty("iterations", envIterations);
        }

        String envTimeout = System.getenv("YAWL_TIMEOUT_MS");
        if (envTimeout != null && !envTimeout.isEmpty()) {
            properties.setProperty("timeout.ms", envTimeout);
        }

        String envUseZai = System.getenv("YAWL_USE_ZAI");
        if (envUseZai != null) {
            properties.setProperty("use.zai", envUseZai);
        }

        String envOutputDir = System.getenv("YAWL_OUTPUT_DIR");
        if (envOutputDir != null && !envOutputDir.isEmpty()) {
            properties.setProperty("output.dir", envOutputDir);
        }

        loaded = true;
    }

    /**
     * Load configuration from properties file.
     */
    public void loadFromFile(String filePath) throws Exception {
        try (var input = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(filePath))) {
            properties.load(input);
            loaded = true;
        }
    }

    /**
     * Get engine URL.
     */
    public String getEngineUrl() {
        return properties.getProperty("engine.url", DEFAULT_ENGINE_URL);
    }

    /**
     * Get username.
     */
    public String getUsername() {
        return properties.getProperty("username", DEFAULT_USERNAME);
    }

    /**
     * Get password.
     */
    public String getPassword() {
        return properties.getProperty("password", DEFAULT_PASSWORD);
    }

    /**
     * Get number of iterations.
     */
    public int getIterations() {
        try {
            return Integer.parseInt(properties.getProperty("iterations", String.valueOf(DEFAULT_ITERATIONS)));
        } catch (NumberFormatException e) {
            return DEFAULT_ITERATIONS;
        }
    }

    /**
     * Get timeout in milliseconds.
     */
    public long getTimeoutMs() {
        try {
            return Long.parseLong(properties.getProperty("timeout.ms", String.valueOf(DEFAULT_TIMEOUT_MS)));
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    /**
     * Check if Z.ai integration is enabled.
     */
    public boolean isUseZai() {
        return Boolean.parseBoolean(properties.getProperty("use.zai", String.valueOf(DEFAULT_USE_ZAI)));
    }

    /**
     * Get output directory path.
     */
    public String getOutputDir() {
        return properties.getProperty("output.dir", DEFAULT_OUTPUT_DIR);
    }

    /**
     * Update configuration from command line arguments.
     */
    public void updateFromArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--engine-url":
                    if (i + 1 < args.length) {
                        properties.setProperty("engine.url", args[++i]);
                    }
                    break;
                case "--username":
                    if (i + 1 < args.length) {
                        properties.setProperty("username", args[++i]);
                    }
                    break;
                case "--password":
                    if (i + 1 < args.length) {
                        properties.setProperty("password", args[++i]);
                    }
                    break;
                case "--iterations":
                    if (i + 1 < args.length) {
                        properties.setProperty("iterations", args[++i]);
                    }
                    break;
                case "--timeout":
                    if (i + 1 < args.length) {
                        properties.setProperty("timeout.ms", args[++i]);
                    }
                    break;
                case "--use-zai":
                    properties.setProperty("use.zai", "true");
                    break;
                case "--output-dir":
                    if (i + 1 < args.length) {
                        properties.setProperty("output.dir", args[++i]);
                    }
                    break;
            }
        }
        loaded = true;
    }

    /**
     * Check if configuration is loaded.
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Get all properties as a map.
     */
    public java.util.Map<String, String> toMap() {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        properties.forEach((k, v) -> map.put((String) k, (String) v));
        return map;
    }
}
