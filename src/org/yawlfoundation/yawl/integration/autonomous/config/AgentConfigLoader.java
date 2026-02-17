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

import java.io.*;
import java.util.*;

import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.AgentConfiguration;
import org.yawlfoundation.yawl.integration.autonomous.reasoners.*;
import org.yawlfoundation.yawl.integration.autonomous.strategies.*;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

/**
 * Configuration loader for autonomous agents.
 *
 * Loads agent configuration from properties files with environment variable expansion.
 * Supports multiple reasoner types and flexible configuration.
 *
 * Configuration file format (Java Properties):
 * <pre>
 * # Agent identity
 * agent.capability.domain=Ordering
 * agent.capability.description=procurement, purchase orders, approvals
 *
 * # Engine connection
 * engine.url=http://localhost:8080/yawl
 * engine.username=admin
 * engine.password=${YAWL_PASSWORD}
 * engine.port=8091
 * engine.poll.interval.ms=3000
 *
 * # Reasoner configuration
 * eligibility.reasoner=zai
 * # Options: zai, static, custom
 *
 * decision.reasoner=zai
 * # Options: zai, template, custom
 *
 * # ZAI configuration (if using zai reasoners)
 * zai.api.key=${ZAI_API_KEY}
 * zai.system.prompt.eligibility=You are a workflow routing assistant...
 * zai.user.prompt.eligibility=You are an agent with capability...
 * zai.system.prompt.decision=You are a YAWL output generator...
 * zai.user.prompt.decision=Produce XML output for...
 *
 * # Static mapping configuration (if using static reasoner)
 * static.mappings.file=/path/to/mappings.properties
 *
 * # Template configuration (if using template reasoner)
 * template.directory=/path/to/templates
 * template.default=<${decompositionRoot}><result>true</result></${decompositionRoot}>
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class AgentConfigLoader {

    private final Properties properties;
    private final Map<String, String> envOverrides;

    /**
     * Create loader with properties.
     */
    public AgentConfigLoader(Properties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties cannot be null");
        }
        this.properties = properties;
        this.envOverrides = new HashMap<>();
    }

    /**
     * Load configuration from properties file.
     * @param filePath absolute path to properties file
     * @return configured loader
     * @throws IOException if file cannot be read
     */
    public static AgentConfigLoader fromFile(String filePath) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("filePath is required");
        }

        Properties props = new Properties();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            props.load(reader);
        }

        return new AgentConfigLoader(props);
    }

    /**
     * Load configuration from properties file with environment variable overrides.
     * @param filePath absolute path to properties file
     * @param envOverrides map of property keys to environment variable names
     * @return configured loader
     * @throws IOException if file cannot be read
     */
    public static AgentConfigLoader fromFile(String filePath,
                                             Map<String, String> envOverrides) throws IOException {
        AgentConfigLoader loader = fromFile(filePath);
        if (envOverrides != null) {
            loader.envOverrides.putAll(envOverrides);
        }
        return loader;
    }

    /**
     * Build AgentConfiguration from loaded properties.
     * @return fully configured AgentConfiguration
     * @throws IllegalStateException if required properties are missing
     * @throws IOException if referenced configuration files cannot be read
     */
    public AgentConfiguration build() throws IOException {
        AgentConfiguration.Builder builder = AgentConfiguration.builder();

        builder.capability(loadCapability());
        builder.engineUrl(getRequired("engine.url"));
        builder.username(getRequired("engine.username"));
        builder.password(getRequired("engine.password"));
        builder.port(getInt("engine.port", 8091));
        builder.pollIntervalMs(getLong("engine.poll.interval.ms", 3000L));
        builder.version(get("engine.version", "5.2.0"));

        builder.discoveryStrategy(loadDiscoveryStrategy());
        builder.eligibilityReasoner(loadEligibilityReasoner());
        builder.decisionReasoner(loadDecisionReasoner());

        return builder.build();
    }

    private AgentCapability loadCapability() {
        String domain = get("agent.capability.domain");
        String description = get("agent.capability.description");

        if (domain != null && description != null) {
            return new AgentCapability(domain, description);
        }

        return AgentCapability.fromEnvironment();
    }

    private DiscoveryStrategy loadDiscoveryStrategy() {
        String strategy = get("discovery.strategy", "polling");

        if ("polling".equals(strategy)) {
            return new PollingDiscoveryStrategy();
        }

        throw new UnsupportedOperationException(
            "Discovery strategy not supported: " + strategy +
            ". Only 'polling' is currently implemented.");
    }

    private EligibilityReasoner loadEligibilityReasoner() throws IOException {
        String reasonerType = getRequired("eligibility.reasoner");
        AgentCapability capability = loadCapability();

        switch (reasonerType.toLowerCase()) {
            case "zai":
                return loadZaiEligibilityReasoner(capability);

            case "static":
                return loadStaticMappingReasoner(capability);

            default:
                throw new UnsupportedOperationException(
                    "Eligibility reasoner not supported: " + reasonerType +
                    ". Supported types: zai, static");
        }
    }

    private EligibilityReasoner loadZaiEligibilityReasoner(AgentCapability capability) {
        String apiKey = getRequired("zai.api.key");
        ZaiService zaiService = new ZaiService(apiKey);

        String systemPrompt = get("zai.system.prompt.eligibility");
        String userPrompt = get("zai.user.prompt.eligibility");

        if (systemPrompt != null && userPrompt != null) {
            return new ZaiEligibilityReasoner(capability, zaiService, systemPrompt, userPrompt);
        }

        return new ZaiEligibilityReasoner(capability, zaiService);
    }

    private EligibilityReasoner loadStaticMappingReasoner(AgentCapability capability) throws IOException {
        StaticMappingReasoner reasoner = new StaticMappingReasoner(capability);

        String mappingsFile = get("static.mappings.file");
        if (mappingsFile != null) {
            reasoner.loadFromFile(expandEnvVars(mappingsFile));
        }

        return reasoner;
    }

    private DecisionReasoner loadDecisionReasoner() throws IOException {
        String reasonerType = getRequired("decision.reasoner");

        switch (reasonerType.toLowerCase()) {
            case "zai":
                return loadZaiDecisionReasoner();

            case "template":
                return loadTemplateDecisionReasoner();

            default:
                throw new UnsupportedOperationException(
                    "Decision reasoner not supported: " + reasonerType +
                    ". Supported types: zai, template");
        }
    }

    private DecisionReasoner loadZaiDecisionReasoner() {
        String apiKey = getRequired("zai.api.key");
        ZaiService zaiService = new ZaiService(apiKey);

        String systemPrompt = get("zai.system.prompt.decision");
        String userPrompt = get("zai.user.prompt.decision");

        if (systemPrompt != null && userPrompt != null) {
            return new ZaiDecisionReasoner(zaiService, systemPrompt, userPrompt);
        }

        return new ZaiDecisionReasoner(zaiService);
    }

    private DecisionReasoner loadTemplateDecisionReasoner() throws IOException {
        String defaultTemplate = get("template.default");
        TemplateDecisionReasoner reasoner = defaultTemplate != null
            ? new TemplateDecisionReasoner(defaultTemplate)
            : new TemplateDecisionReasoner();

        String templateDir = get("template.directory");
        if (templateDir != null) {
            loadTemplatesFromDirectory(reasoner, expandEnvVars(templateDir));
        }

        return reasoner;
    }

    private void loadTemplatesFromDirectory(TemplateDecisionReasoner reasoner,
                                           String templateDir) throws IOException {
        File dir = new File(templateDir);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("Template directory does not exist: " + templateDir);
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".xml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String taskName = file.getName().replace(".xml", "");
            String template = readFile(file.getAbsolutePath());
            reasoner.addTaskTemplate(taskName, template);
        }
    }

    private String get(String key) {
        return get(key, null);
    }

    private String get(String key, String defaultValue) {
        if (envOverrides.containsKey(key)) {
            String envVar = envOverrides.get(key);
            String envValue = System.getenv(envVar);
            if (envValue != null) {
                return envValue;
            }
        }

        String value = properties.getProperty(key, defaultValue);
        return value != null ? expandEnvVars(value) : null;
    }

    private String getRequired(String key) {
        String value = get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Required property missing: " + key);
        }
        return value.trim();
    }

    private int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "Invalid integer value for " + key + ": " + value, e);
        }
    }

    private long getLong(String key, long defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "Invalid long value for " + key + ": " + value, e);
        }
    }

    private String expandEnvVars(String value) {
        if (value == null) {
            return null;
        }

        String result = value;
        int start = 0;

        while ((start = result.indexOf("${", start)) != -1) {
            int end = result.indexOf("}", start);
            if (end == -1) {
                break;
            }

            String varName = result.substring(start + 2, end);
            String varValue = System.getenv(varName);

            if (varValue != null) {
                result = result.substring(0, start) + varValue + result.substring(end + 1);
                start += varValue.length();
            } else {
                start = end + 1;
            }
        }

        return result;
    }

    private static String readFile(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
}
