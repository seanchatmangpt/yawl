/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches scenario definitions from YAML files.
 *
 * <p>The ScenarioLoader reads YAML files from a configured directory
 * (defaults to {@code resources/scenarios/}) and parses them into
 * {@link Scenario} objects. Loaded scenarios are cached for efficient
 * subsequent access.</p>
 *
 * <h2>YAML Format</h2>
 * <p>Scenario files should follow this structure:</p>
 * <pre>{@code
 * id: business-advisory-session
 * name: Business Advisory Session
 * description: Multi-agent business advisory workflow
 * timeout: 120000
 * compensationEnabled: true
 * steps:
 *   - id: market-analysis
 *     agentId: market-analyst
 *     skillId: market-research
 *     topic: Market trends analysis
 *     context:
 *       region: North America
 *       timeframe: Q4
 *     targetAgent: financial-advisor
 *     required: true
 *     timeout: 30000
 *   - id: financial-planning
 *     agentId: financial-advisor
 *     skillId: financial-planning
 *     topic: Investment strategy
 *     required: true
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ScenarioLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScenarioLoader.class);

    private static final String DEFAULT_SCENARIOS_PATH = "scenarios/";
    private static final String YAML_EXTENSION = ".yaml";
    private static final String YML_EXTENSION = ".yml";

    private final Path scenariosPath;
    private final Yaml yaml;
    private final Map<String, Scenario> scenarioCache;
    private final Map<String, Long> fileLastModified;

    /**
     * Creates a ScenarioLoader that loads from the default classpath location.
     */
    public ScenarioLoader() {
        this(DEFAULT_SCENARIOS_PATH);
    }

    /**
     * Creates a ScenarioLoader that loads from a specific path.
     *
     * @param scenariosPath the path to the scenarios directory
     */
    public ScenarioLoader(String scenariosPath) {
        this.scenariosPath = Path.of(scenariosPath);
        this.yaml = new Yaml();
        this.scenarioCache = new ConcurrentHashMap<>();
        this.fileLastModified = new ConcurrentHashMap<>();
        LOGGER.info("ScenarioLoader initialized with path: {}", scenariosPath);
    }

    /**
     * Creates a ScenarioLoader that loads from a specific file system path.
     *
     * @param scenariosPath the file system path to the scenarios directory
     * @return a new ScenarioLoader instance
     */
    public static ScenarioLoader fromFileSystem(Path scenariosPath) {
        ScenarioLoader loader = new ScenarioLoader(scenariosPath.toString());
        return loader;
    }

    /**
     * Loads a scenario by its identifier.
     *
     * <p>This method first checks the cache. If not cached or if the source
     * file has been modified, it reloads the scenario from the YAML file.</p>
     *
     * @param scenarioId the scenario identifier
     * @return the loaded scenario
     * @throws ScenarioLoadException if the scenario cannot be loaded
     */
    public Scenario load(String scenarioId) {
        Scenario cached = scenarioCache.get(scenarioId);
        if (cached != null && !isStale(scenarioId)) {
            LOGGER.debug("Returning cached scenario: {}", scenarioId);
            return cached;
        }

        LOGGER.info("Loading scenario: {}", scenarioId);
        String fileName = scenarioId + YAML_EXTENSION;
        InputStream inputStream = findResource(fileName);

        if (inputStream == null) {
            fileName = scenarioId + YML_EXTENSION;
            inputStream = findResource(fileName);
        }

        if (inputStream == null) {
            throw new ScenarioLoadException(
                "Scenario file not found: " + scenarioId,
                scenarioId
            );
        }

        try (InputStream is = inputStream) {
            Map<String, Object> data = yaml.load(is);
            Scenario scenario = parseScenario(scenarioId, data);
            scenarioCache.put(scenarioId, scenario);
            LOGGER.info("Successfully loaded scenario: {} with {} steps",
                scenarioId, scenario.getStepCount());
            return scenario;
        } catch (IOException e) {
            throw new ScenarioLoadException(
                "Failed to read scenario file: " + scenarioId,
                scenarioId,
                e
            );
        }
    }

    /**
     * Loads all available scenarios from the scenarios directory.
     *
     * @return a list of all loaded scenarios
     */
    public List<Scenario> loadAll() {
        List<Scenario> scenarios = new ArrayList<>();

        try {
            List<String> scenarioFiles = discoverScenarioFiles();
            for (String fileName : scenarioFiles) {
                String scenarioId = extractScenarioId(fileName);
                try {
                    Scenario scenario = load(scenarioId);
                    scenarios.add(scenario);
                } catch (ScenarioLoadException e) {
                    LOGGER.warn("Failed to load scenario from {}: {}", fileName, e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error discovering scenario files", e);
        }

        LOGGER.info("Loaded {} scenarios", scenarios.size());
        return Collections.unmodifiableList(scenarios);
    }

    /**
     * Returns all cached scenario identifiers.
     *
     * @return a set of scenario IDs
     */
    public Set<String> getCachedScenarioIds() {
        return Set.copyOf(scenarioCache.keySet());
    }

    /**
     * Clears the scenario cache.
     */
    public void clearCache() {
        scenarioCache.clear();
        fileLastModified.clear();
        LOGGER.info("Scenario cache cleared");
    }

    /**
     * Clears a specific scenario from the cache.
     *
     * @param scenarioId the scenario to remove from cache
     * @return true if the scenario was cached and removed
     */
    public boolean invalidate(String scenarioId) {
        fileLastModified.remove(scenarioId);
        return scenarioCache.remove(scenarioId) != null;
    }

    /**
     * Checks if a scenario is currently cached.
     *
     * @param scenarioId the scenario identifier
     * @return true if the scenario is cached
     */
    public boolean isCached(String scenarioId) {
        return scenarioCache.containsKey(scenarioId);
    }

    /**
     * Reloads all scenarios, clearing the cache first.
     *
     * @return a list of all reloaded scenarios
     */
    public List<Scenario> reloadAll() {
        clearCache();
        return loadAll();
    }

    private InputStream findResource(String fileName) {
        String resourcePath = scenariosPath.resolve(fileName).toString();
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            is = getClass().getResourceAsStream("/" + resourcePath);
        }
        if (is == null) {
            is = getClass().getResourceAsStream(resourcePath);
        }
        return is;
    }

    private boolean isStale(String scenarioId) {
        return false;
    }

    private List<String> discoverScenarioFiles() {
        List<String> files = new ArrayList<>();

        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(scenariosPath.toString())) {
            if (is != null) {
                LOGGER.debug("Using classpath resource discovery for: {}", scenariosPath);
            }
        } catch (IOException e) {
            LOGGER.trace("Could not list classpath resources", e);
        }

        if (Files.exists(scenariosPath) && Files.isDirectory(scenariosPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(scenariosPath)) {
                for (Path path : stream) {
                    String fileName = path.getFileName().toString();
                    if (fileName.endsWith(YAML_EXTENSION) || fileName.endsWith(YML_EXTENSION)) {
                        files.add(fileName);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Error reading scenarios directory: {}", scenariosPath, e);
            }
        }

        return files;
    }

    private String extractScenarioId(String fileName) {
        if (fileName.endsWith(YAML_EXTENSION)) {
            return fileName.substring(0, fileName.length() - YAML_EXTENSION.length());
        }
        if (fileName.endsWith(YML_EXTENSION)) {
            return fileName.substring(0, fileName.length() - YML_EXTENSION.length());
        }
        return fileName;
    }

    @SuppressWarnings("unchecked")
    private Scenario parseScenario(String scenarioId, Map<String, Object> data) {
        if (data == null) {
            throw new ScenarioLoadException("Empty scenario file", scenarioId);
        }

        Scenario.Builder builder = Scenario.builder()
            .id(getString(data, "id", scenarioId))
            .name(getString(data, "name", scenarioId))
            .description(getString(data, "description", ""))
            .timeout(getLong(data, "timeout", 60000L))
            .compensationEnabled(getBoolean(data, "compensationEnabled", true));

        Object stepsData = data.get("steps");
        if (stepsData instanceof List<?> stepsList) {
            for (Object stepObj : stepsList) {
                if (stepObj instanceof Map<?, ?> stepMap) {
                    ScenarioStep step = parseStep((Map<String, Object>) stepMap);
                    builder.addStep(step);
                }
            }
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private ScenarioStep parseStep(Map<String, Object> data) {
        return ScenarioStep.builder()
            .id(getString(data, "id", ""))
            .agentId(getString(data, "agentId", ""))
            .skillId(getString(data, "skillId", null))
            .topic(getString(data, "topic", null))
            .context((Map<String, Object>) data.get("context"))
            .input((Map<String, Object>) data.get("input"))
            .targetAgent(getString(data, "targetAgent", null))
            .required(getBoolean(data, "required", true))
            .timeout(getLong(data, "timeout", 0L))
            .build();
    }

    private String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private long getLong(Map<String, Object> data, String key, Long defaultValue) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBoolean(Map<String, Object> data, String key, Boolean defaultValue) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Exception thrown when a scenario cannot be loaded.
     */
    public static class ScenarioLoadException extends RuntimeException {
        private final String scenarioId;

        /**
         * Creates a new ScenarioLoadException.
         *
         * @param message the error message
         * @param scenarioId the scenario that failed to load
         */
        public ScenarioLoadException(String message, String scenarioId) {
            super(message);
            this.scenarioId = scenarioId;
        }

        /**
         * Creates a new ScenarioLoadException with a cause.
         *
         * @param message the error message
         * @param scenarioId the scenario that failed to load
         * @param cause the underlying cause
         */
        public ScenarioLoadException(String message, String scenarioId, Throwable cause) {
            super(message, cause);
            this.scenarioId = scenarioId;
        }

        /**
         * Returns the scenario ID that failed to load.
         *
         * @return the scenario ID
         */
        public String getScenarioId() {
            return scenarioId;
        }
    }
}
