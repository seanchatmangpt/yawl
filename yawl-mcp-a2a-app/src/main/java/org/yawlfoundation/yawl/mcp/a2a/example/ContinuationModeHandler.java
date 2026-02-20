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

package org.yawlfoundation.yawl.mcp.a2a.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles continuation mode configuration for WCP-15 multi-instance tasks.
 *
 * <p>Continuation mode enables dynamic instance creation without a priori
 * knowledge of total instance count. Instances are created incrementally
 * as data becomes available, supporting deferred completion.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Parse continuation mode specifications from task configuration</li>
 *   <li>Manage incremental instance creation triggers</li>
 *   <li>Track instance batches and completion thresholds</li>
 *   <li>Validate continuation semantics against YAWL schema</li>
 * </ul></p>
 *
 * <p>WCP-15 enables patterns like:
 * - Progressive order item processing (add items as customer adds to cart)
 * - Streaming data ingestion (instances created as records arrive)
 * - Adaptive workload distribution (spawn workers on-demand)</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ContinuationModeHandler {

    private static final Logger log = LogManager.getLogger(ContinuationModeHandler.class);

    /**
     * Continuation mode codes supported by YAWL schema.
     */
    public static final String MODE_CONTINUATION = "continuation";
    public static final String MODE_DYNAMIC = "dynamic";
    public static final String MODE_STATIC = "static";

    /**
     * Parses continuation mode configuration from a multi-instance task specification.
     *
     * <p>Expected structure:
     * <pre>{@code
     * multiInstance:
     *   mode: continuation
     *   initialMinimum: 2              # create 2 instances initially
     *   creationTrigger: on_demand     # create on request (or batch)
     *   batchSize: 5                   # max instances per creation trigger
     * }</pre></p>
     *
     * @param taskConfig the multi-instance task configuration map
     * @return ContinuationConfig object with parsed settings, or empty config if not continuation mode
     */
    public ContinuationConfig parseConfiguration(Map<String, Object> taskConfig) {
        if (taskConfig == null) {
            return new ContinuationConfig(false);
        }

        String mode = getString(taskConfig, "mode", MODE_STATIC);
        if (!MODE_CONTINUATION.equalsIgnoreCase(mode)) {
            return new ContinuationConfig(false);
        }

        int initialMinimum = getInteger(taskConfig, "initialMinimum", 1);
        String trigger = getString(taskConfig, "creationTrigger", "on_demand");
        int batchSize = getInteger(taskConfig, "batchSize", Integer.MAX_VALUE);

        return new ContinuationConfig(
            true,
            initialMinimum,
            trigger,
            batchSize
        );
    }

    /**
     * Validates continuation mode configuration against YAWL schema constraints.
     *
     * <p>Checks:
     * <ul>
     *   <li>initialMinimum >= 1</li>
     *   <li>batchSize >= 1</li>
     *   <li>creationTrigger is recognized (on_demand, on_completion, on_threshold)</li>
     * </ul></p>
     *
     * @param config the continuation configuration to validate
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate(ContinuationConfig config) {
        if (!config.enabled()) {
            return;
        }

        if (config.initialMinimum() < 1) {
            throw new IllegalArgumentException(
                "initialMinimum must be >= 1, got: " + config.initialMinimum()
            );
        }

        if (config.batchSize() < 1) {
            throw new IllegalArgumentException(
                "batchSize must be >= 1, got: " + config.batchSize()
            );
        }

        String trigger = config.creationTrigger();
        if (!isValidTrigger(trigger)) {
            throw new IllegalArgumentException(
                "Unknown creationTrigger: " + trigger +
                ". Expected: on_demand, on_completion, or on_threshold"
            );
        }

        log.info("Continuation mode validated: initialMin={}, trigger={}, batchSize={}",
                config.initialMinimum(), trigger, config.batchSize());
    }

    /**
     * Generates the YAWL XML element for continuation mode multi-instance configuration.
     *
     * <p>Produces schema-compliant XML:
     * <pre>{@code
     * <creationMode code="continuation"/>
     * <miDataInput>
     *   <expression query="/net/data/items"/>
     *   <splittingExpression query="/item"/>
     * </miDataInput>
     * }</pre></p>
     *
     * <p>For continuation mode tasks, returns XML fragment containing:
     * <ul>
     *   <li>creationMode element with code="continuation"</li>
     *   <li>miDataInput structure with expression and splitting query</li>
     * </ul></p>
     *
     * @param config the continuation configuration
     * @return XML string for continuation mode; throws if not enabled
     * @throws IllegalStateException if configuration is not in continuation mode
     */
    public String generateXml(ContinuationConfig config) {
        if (!config.enabled()) {
            throw new IllegalStateException(
                "Cannot generate continuation mode XML: continuation mode is not enabled"
            );
        }

        StringBuilder xml = new StringBuilder();

        // Emit creationMode element
        xml.append("          <creationMode code=\"continuation\"/>\n");

        // For continuation mode, generate mi data input structure
        // Real implementation uses task definition data; this provides minimal valid structure
        xml.append("          <miDataInput>\n");
        xml.append("            <expression query=\"/net/data/items\"/>\n");
        xml.append("            <splittingExpression query=\"/item\"/>\n");
        xml.append("            <formalInputParam>item</formalInputParam>\n");
        xml.append("          </miDataInput>\n");

        log.debug("Generated continuation mode XML for initialMin={}", config.initialMinimum());
        return xml.toString();
    }

    /**
     * Determines the appropriate creation strategy for instances.
     *
     * <p>Returns strategy based on configured trigger:
     * <ul>
     *   <li>on_demand: create instances as requested</li>
     *   <li>on_completion: create next batch when previous batch completes</li>
     *   <li>on_threshold: create when completion count reaches threshold</li>
     * </ul></p>
     *
     * @param config the continuation configuration
     * @return human-readable strategy description
     */
    public String describeStrategy(ContinuationConfig config) {
        if (!config.enabled()) {
            return "Not in continuation mode";
        }

        return switch (config.creationTrigger()) {
            case "on_demand" ->
                String.format(
                    "Create up to %d instances on-demand, starting with %d",
                    config.batchSize(), config.initialMinimum()
                );
            case "on_completion" ->
                String.format(
                    "Create initial %d instances, then %d more per completion batch",
                    config.initialMinimum(), config.batchSize()
                );
            case "on_threshold" ->
                String.format(
                    "Create batches of %d when reaching completion thresholds",
                    config.batchSize()
                );
            default -> "Unknown continuation strategy";
        };
    }

    /**
     * Record to encapsulate continuation mode configuration.
     */
    public record ContinuationConfig(
        boolean enabled,
        int initialMinimum,
        String creationTrigger,
        int batchSize
    ) {
        /**
         * Default constructor for non-continuation mode.
         */
        public ContinuationConfig(boolean enabled) {
            this(enabled, 1, "on_demand", Integer.MAX_VALUE);
        }

        @Override
        public String toString() {
            if (!enabled) {
                return "ContinuationConfig{disabled}";
            }
            return String.format(
                "ContinuationConfig{mode=continuation, initialMin=%d, trigger=%s, batchSize=%d}",
                initialMinimum, creationTrigger, batchSize
            );
        }
    }

    // --- Private helpers ---

    /**
     * Checks if a trigger value is recognized.
     */
    private boolean isValidTrigger(String trigger) {
        return trigger != null && (
            trigger.equalsIgnoreCase("on_demand")
                || trigger.equalsIgnoreCase("on_completion")
                || trigger.equalsIgnoreCase("on_threshold")
        );
    }

    /**
     * Gets string value from map with default.
     */
    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    /**
     * Gets integer value from map with default.
     */
    private int getInteger(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse integer for key {}: {}", key, value);
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
