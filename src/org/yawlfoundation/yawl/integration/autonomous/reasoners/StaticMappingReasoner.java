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

package org.yawlfoundation.yawl.integration.autonomous.reasoners;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;

/**
 * Static rule-based eligibility reasoner.
 *
 * Reads task name to agent capability mappings from a properties file
 * or programmatic configuration. Provides deterministic, fast eligibility
 * checking without AI inference costs.
 *
 * Configuration format (properties file):
 * <pre>
 * # Task mappings (task name = capability1, capability2, ...)
 * Approve_Purchase_Order=Ordering,Finance
 * Create_Invoice=Finance,Accounting
 * Ship_Order=Logistics,Carrier
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class StaticMappingReasoner implements EligibilityReasoner {

    private final Map<String, Set<String>> taskToCapabilities;
    private final AgentCapability capability;

    /**
     * Create reasoner with agent capability and empty mappings.
     * Use addMapping() or loadFromFile() to configure.
     */
    public StaticMappingReasoner(AgentCapability capability) {
        if (capability == null) {
            throw new IllegalArgumentException("capability is required");
        }
        this.capability = capability;
        this.taskToCapabilities = new HashMap<>();
    }

    /**
     * Create reasoner with agent capability and initial mappings.
     * @param capability the agent's capability descriptor
     * @param mappings map of task name to set of capabilities
     */
    public StaticMappingReasoner(AgentCapability capability, Map<String, Set<String>> mappings) {
        if (capability == null) {
            throw new IllegalArgumentException("capability is required");
        }
        if (mappings == null) {
            throw new IllegalArgumentException("mappings cannot be null");
        }
        this.capability = capability;
        this.taskToCapabilities = new HashMap<>(mappings);
    }

    /**
     * Load mappings from properties file.
     * Format: taskName=capability1,capability2,...
     *
     * @param filePath absolute path to properties file
     * @throws IOException if file cannot be read
     */
    public void loadFromFile(String filePath) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("filePath is required");
        }

        Properties props = new Properties();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            props.load(reader);
        }

        for (String taskName : props.stringPropertyNames()) {
            String capabilitiesStr = props.getProperty(taskName);
            if (capabilitiesStr != null && !capabilitiesStr.trim().isEmpty()) {
                Set<String> capabilities = parseCapabilities(capabilitiesStr);
                taskToCapabilities.put(taskName.trim(), capabilities);
            }
        }
    }

    /**
     * Add a mapping programmatically.
     * @param taskName task name (can include wildcards: * for any, ? for single char)
     * @param capabilities set of capability names that can handle this task
     */
    public void addMapping(String taskName, Set<String> capabilities) {
        if (taskName == null || taskName.trim().isEmpty()) {
            throw new IllegalArgumentException("taskName is required");
        }
        if (capabilities == null || capabilities.isEmpty()) {
            throw new IllegalArgumentException("capabilities cannot be empty");
        }
        taskToCapabilities.put(taskName.trim(), new HashSet<>(capabilities));
    }

    /**
     * Add a mapping with comma-separated capabilities.
     * @param taskName task name
     * @param capabilitiesStr comma-separated capability names
     */
    public void addMapping(String taskName, String capabilitiesStr) {
        if (taskName == null || taskName.trim().isEmpty()) {
            throw new IllegalArgumentException("taskName is required");
        }
        if (capabilitiesStr == null || capabilitiesStr.trim().isEmpty()) {
            throw new IllegalArgumentException("capabilitiesStr is required");
        }
        Set<String> capabilities = parseCapabilities(capabilitiesStr);
        taskToCapabilities.put(taskName.trim(), capabilities);
    }

    @Override
    public boolean isEligible(WorkItemRecord workItem) {
        if (workItem == null) {
            throw new IllegalArgumentException("workItem is required");
        }

        String taskName = extractTaskName(workItem);
        String normalizedCapability = capability.domainName().trim();

        Set<String> mappedCapabilities = taskToCapabilities.get(taskName);
        if (mappedCapabilities != null) {
            return mappedCapabilities.contains(normalizedCapability);
        }

        for (Map.Entry<String, Set<String>> entry : taskToCapabilities.entrySet()) {
            String pattern = entry.getKey();
            if (matchesPattern(taskName, pattern)) {
                return entry.getValue().contains(normalizedCapability);
            }
        }

        return false;
    }

    /**
     * Get all configured task names.
     */
    public Set<String> getConfiguredTasks() {
        return new HashSet<>(taskToCapabilities.keySet());
    }

    /**
     * Get capabilities mapped to a task.
     * @return set of capabilities, or empty set if task not configured
     */
    public Set<String> getCapabilitiesForTask(String taskName) {
        if (taskName == null) {
            return Collections.emptySet();
        }
        Set<String> capabilities = taskToCapabilities.get(taskName.trim());
        return capabilities != null ? new HashSet<>(capabilities) : Collections.emptySet();
    }

    /**
     * Clear all mappings.
     */
    public void clearMappings() {
        taskToCapabilities.clear();
    }

    private static String extractTaskName(WorkItemRecord workItem) {
        String taskName = workItem.getTaskName();
        if (taskName == null || taskName.isEmpty()) {
            taskName = workItem.getTaskID();
        }
        return taskName;
    }

    private static Set<String> parseCapabilities(String capabilitiesStr) {
        Set<String> capabilities = new HashSet<>();
        String[] parts = capabilitiesStr.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                capabilities.add(trimmed);
            }
        }
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException(
                "No valid capabilities found in: " + capabilitiesStr);
        }
        return capabilities;
    }

    private static boolean matchesPattern(String taskName, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }
        if (!pattern.contains("*") && !pattern.contains("?")) {
            return taskName.equals(pattern);
        }

        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
        return taskName.matches(regex);
    }
}
