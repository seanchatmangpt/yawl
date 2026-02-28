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

package org.yawlfoundation.yawl.integration.autonomous;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.PollingDiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.StaticMappingEligibilityReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.TemplateDecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.ZaiEligibilityReasonerStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.ZaiDecisionReasonerStrategy;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yaml.snakeyaml.Yaml;

/**
 * Loader for agent configurations from YAML files.
 *
 * <p>Scans a configuration directory for {@code *.yaml} files and constructs
 * {@link AgentConfiguration} instances from YAML content. Supports:
 * <ul>
 *   <li>Environment variable substitution: {@code ${VAR:-default}}</li>
 *   <li>Static and ZAI-based reasoning strategies</li>
 *   <li>Pluggable discovery, eligibility, and decision strategies</li>
 * </ul>
 *
 * <p>YAML structure:
 * <pre>
 * agent:
 *   name: "Agent Name"
 *   capability:
 *     domain: "Domain"
 *     description: "Description"
 *   discovery:
 *     strategy: "polling"
 *     interval_ms: 3000
 *   reasoning:
 *     eligibility_engine: "static" | "zai"
 *     decision_engine: "template" | "zai"
 *     mapping_file: "path/to/mapping.json"
 *     template_file: "path/to/template.xml"
 *     eligibility_prompt: "prompt template"
 *     decision_prompt: "prompt template"
 *   output:
 *     format: "xml"
 *   server:
 *     port: 8091
 * yawl:
 *   engine_url: "${YAWL_ENGINE_URL:-http://localhost:8080/yawl}"
 *   username: "${YAWL_USERNAME:-admin}"
 *   password: "${YAWL_PASSWORD}"
 * zai:
 *   api_key: "${ZAI_API_KEY}"
 *   model: "${ZAI_MODEL:-GLM-4.7-Flash}"
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since YAWL 6.0
 */
public final class YamlAgentConfigLoader {

    private static final Logger logger = LogManager.getLogger(YamlAgentConfigLoader.class);

    private YamlAgentConfigLoader() {
        throw new UnsupportedOperationException("YamlAgentConfigLoader is a utility class and cannot be instantiated");
    }

    /**
     * Load all agent configurations from YAML files in the given directory.
     *
     * <p>Scans the directory (non-recursively) for {@code *.yaml} files,
     * parses each, and constructs {@link AgentConfiguration} instances.
     * Environment variables are resolved in the form {@code ${VAR:-default}}.
     *
     * @param configDir the directory containing YAML configuration files
     * @param zaiService the ZaiService for ZAI-based reasoning (may be null if using static reasoning)
     * @return list of loaded agent configurations (empty list if no files found)
     * @throws IOException if file I/O fails
     */
    public static List<AgentConfiguration> load(Path configDir, ZaiService zaiService) throws IOException {
        List<AgentConfiguration> configs = new ArrayList<>();

        if (!Files.isDirectory(configDir)) {
            logger.warn("Config directory does not exist: {}", configDir);
            return configs;
        }

        // Scan for *.yaml files (non-recursive)
        try (var stream = Files.list(configDir)) {
            stream.filter(p -> p.toString().endsWith(".yaml"))
                .forEach(configFile -> {
                    try {
                        AgentConfiguration config = loadFromFile(configFile, zaiService);
                        if (config != null) {
                            configs.add(config);
                            logger.info("Loaded agent configuration: {}", config.getAgentName());
                        }
                    } catch (Exception e) {
                        logger.error("Failed to load agent config from {}: {}",
                            configFile, e.getMessage(), e);
                    }
                });
        }

        return configs;
    }

    /**
     * Load a single agent configuration from a YAML file.
     *
     * @param configFile path to the YAML configuration file
     * @param zaiService the ZaiService for ZAI-based reasoning (may be null)
     * @return the agent configuration, or null if parsing fails
     * @throws IOException if file I/O fails
     */
    private static AgentConfiguration loadFromFile(Path configFile, ZaiService zaiService) throws IOException {
        String yamlContent = Files.readString(configFile);
        Yaml yaml = new Yaml();
        Map<String, Object> rawConfig = yaml.load(yamlContent);

        if (rawConfig == null) {
            logger.warn("Empty or invalid YAML in {}", configFile);
            return null;
        }

        // Extract top-level sections
        @SuppressWarnings("unchecked")
        Map<String, Object> agentMap = (Map<String, Object>) rawConfig.get("agent");
        @SuppressWarnings("unchecked")
        Map<String, Object> yawlMap = (Map<String, Object>) rawConfig.get("yawl");

        if (agentMap == null || yawlMap == null) {
            logger.warn("Missing 'agent' or 'yawl' section in {}", configFile);
            return null;
        }

        // Parse agent capability
        @SuppressWarnings("unchecked")
        Map<String, Object> capabilityMap = (Map<String, Object>) agentMap.get("capability");
        if (capabilityMap == null) {
            logger.warn("Missing 'capability' section in {}", configFile);
            return null;
        }

        String domain = (String) capabilityMap.get("domain");
        String description = (String) capabilityMap.get("description");
        AgentCapability capability = new AgentCapability(domain, description);

        // Parse YAWL connection details
        String engineUrl = resolveEnv((String) yawlMap.get("engine_url"));
        String username = resolveEnv((String) yawlMap.get("username"));
        String password = resolveEnv((String) yawlMap.get("password"));

        if (engineUrl == null || username == null || password == null) {
            logger.warn("Missing YAWL connection details in {}", configFile);
            return null;
        }

        // Parse discovery config
        @SuppressWarnings("unchecked")
        Map<String, Object> discoveryMap = (Map<String, Object>) agentMap.get("discovery");
        long pollIntervalMs = 5000;
        if (discoveryMap != null) {
            Object intervalObj = discoveryMap.get("interval_ms");
            if (intervalObj instanceof Number) {
                pollIntervalMs = ((Number) intervalObj).longValue();
            }
        }

        // Parse reasoning config and create strategies
        @SuppressWarnings("unchecked")
        Map<String, Object> reasoningMap = (Map<String, Object>) agentMap.get("reasoning");

        EligibilityReasoner eligibilityReasoner = createEligibilityReasoner(
            reasoningMap, configFile.getParent(), domain, zaiService);
        DecisionReasoner decisionReasoner = createDecisionReasoner(
            reasoningMap, configFile.getParent(), zaiService);

        // Parse server port
        @SuppressWarnings("unchecked")
        Map<String, Object> serverMap = (Map<String, Object>) agentMap.get("server");
        int port = 8091;
        if (serverMap != null) {
            Object portObj = serverMap.get("port");
            if (portObj instanceof Number) {
                port = ((Number) portObj).intValue();
            }
        }

        // Build configuration
        String agentId = domain.toLowerCase().replace(" ", "-");
        String version = (String) agentMap.getOrDefault("version", "6.0.0");

        return AgentConfiguration.builder(agentId, engineUrl, username, password)
            .capability(capability)
            .discoveryStrategy(new PollingDiscoveryStrategy())
            .eligibilityReasoner(eligibilityReasoner)
            .decisionReasoner(decisionReasoner)
            .port(port)
            .version(version)
            .pollIntervalMs(pollIntervalMs)
            .build();
    }

    /**
     * Create an EligibilityReasoner based on YAML configuration.
     *
     * @param reasoningMap the reasoning section from YAML
     * @param configDir the configuration directory for resolving relative paths
     * @param agentDomain the agent's domain name
     * @param zaiService the ZaiService instance (may be null)
     * @return the constructed EligibilityReasoner
     */
    private static EligibilityReasoner createEligibilityReasoner(
            Map<String, Object> reasoningMap,
            Path configDir,
            String agentDomain,
            ZaiService zaiService) throws java.io.IOException {

        if (reasoningMap == null) {
            throw new IllegalArgumentException("reasoning section is required in YAML");
        }

        String engine = (String) reasoningMap.get("eligibility_engine");
        if (engine == null) {
            engine = "static";
        }

        return switch (engine) {
            case "static" -> {
                String mappingFile = (String) reasoningMap.get("mapping_file");
                if (mappingFile == null) {
                    throw new IllegalArgumentException("mapping_file required for static eligibility_engine");
                }
                Path mappingPath = resolvePath(mappingFile, configDir);
                yield new StaticMappingEligibilityReasoner(mappingPath.toString(), agentDomain);
            }
            case "zai" -> {
                if (zaiService == null) {
                    throw new IllegalStateException("ZaiService is required for zai eligibility_engine");
                }
                String prompt = (String) reasoningMap.get("eligibility_prompt");
                if (prompt == null) {
                    throw new IllegalArgumentException("eligibility_prompt required for zai eligibility_engine");
                }
                yield new ZaiEligibilityReasonerStrategy(zaiService, prompt);
            }
            default -> throw new IllegalArgumentException("Unknown eligibility_engine: " + engine);
        };
    }

    /**
     * Create a DecisionReasoner based on YAML configuration.
     *
     * @param reasoningMap the reasoning section from YAML
     * @param configDir the configuration directory for resolving relative paths
     * @param zaiService the ZaiService instance (may be null)
     * @return the constructed DecisionReasoner
     */
    private static DecisionReasoner createDecisionReasoner(
            Map<String, Object> reasoningMap,
            Path configDir,
            ZaiService zaiService) throws java.io.IOException {

        if (reasoningMap == null) {
            throw new IllegalArgumentException("reasoning section is required in YAML");
        }

        String engine = (String) reasoningMap.get("decision_engine");
        if (engine == null) {
            engine = "template";
        }

        return switch (engine) {
            case "template" -> {
                String templateFile = (String) reasoningMap.get("template_file");
                if (templateFile == null) {
                    throw new IllegalArgumentException("template_file required for template decision_engine");
                }
                Path templatePath = resolvePath(templateFile, configDir);
                yield new TemplateDecisionReasoner(templatePath.toString());
            }
            case "zai" -> {
                if (zaiService == null) {
                    throw new IllegalStateException("ZaiService is required for zai decision_engine");
                }
                String prompt = (String) reasoningMap.get("decision_prompt");
                if (prompt == null) {
                    throw new IllegalArgumentException("decision_prompt required for zai decision_engine");
                }
                yield new ZaiDecisionReasonerStrategy(zaiService, prompt);
            }
            default -> throw new IllegalArgumentException("Unknown decision_engine: " + engine);
        };
    }

    /**
     * Resolve environment variables in a string of the form {@code ${VAR:-default}}.
     *
     * @param value the string to resolve
     * @return the resolved string, with env vars substituted
     */
    private static String resolveEnv(String value) {
        if (value == null) {
            return null;
        }

        String result = value;
        // Pattern: ${VAR:-default} or ${VAR}
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{([^}:]+)(?::-([^}]*))?\\}");
        java.util.regex.Matcher matcher = pattern.matcher(result);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String defaultValue = matcher.group(2);

            String envValue = System.getenv(varName);
            String replacement = (envValue != null && !envValue.isEmpty())
                ? envValue
                : (defaultValue != null ? defaultValue : matcher.group(0));

            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolve a file path, either absolute or relative to config directory.
     *
     * @param filePath the file path from YAML
     * @param configDir the configuration directory
     * @return the resolved Path
     */
    private static Path resolvePath(String filePath, Path configDir) {
        Path path = Path.of(filePath);
        if (path.isAbsolute()) {
            return path;
        }
        return configDir.resolve(path);
    }
}
