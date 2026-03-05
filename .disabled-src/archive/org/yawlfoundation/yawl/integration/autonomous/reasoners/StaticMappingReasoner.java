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

package org.yawlfoundation.yawl.integration.autonomous.reasoners;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Static task-to-capability mapping eligibility reasoner for autonomous agents.
 *
 * <p>This reasoner implements {@link EligibilityReasoner} to determine if an agent
 * with a specific capability domain is eligible to handle a work item based on
 * pre-configured task-to-capability mappings. It supports wildcard patterns for
 * flexible task matching.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * AgentCapability financing = new AgentCapability("Finance", "invoicing, payments");
 * StaticMappingReasoner reasoner = new StaticMappingReasoner(financing);
 * reasoner.addMapping("Create_Invoice", "Finance, Accounting");
 * reasoner.addMapping("Approve_*", "Finance");
 *
 * boolean eligible = reasoner.isEligible(workItem);
 * }</pre>
 *
 * <p>Wildcard patterns:</p>
 * <ul>
 *   <li>{@code *} matches any sequence of characters</li>
 *   <li>{@code ?} matches exactly one character</li>
 *   <li>Exact task names are matched first for performance</li>
 * </ul>
 *
 * <p>Eligibility check performs:</p>
 * <ol>
 *   <li>Retrieves task name from work item (falls back to task ID if null)</li>
 *   <li>Iterates through configured mappings</li>
 *   <li>For each mapping: tests if task pattern matches and agent capability is in set</li>
 *   <li>Returns true on first match; false if no mapping matches</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since YAWL 6.0
 */
public class StaticMappingReasoner implements EligibilityReasoner {

    private final AgentCapability capability;
    private final Map<String, Set<String>> mappings;
    private final Map<String, Pattern> patternCache;

    /**
     * Constructs a reasoner with the given agent capability and empty mappings.
     *
     * @param capability the agent's capability domain
     * @throws IllegalArgumentException if capability is null
     */
    public StaticMappingReasoner(AgentCapability capability) {
        if (capability == null) {
            throw new IllegalArgumentException("capability is required");
        }
        this.capability = capability;
        this.mappings = new HashMap<>();
        this.patternCache = new HashMap<>();
    }

    /**
     * Constructs a reasoner with the given agent capability and pre-configured mappings.
     *
     * @param capability the agent's capability domain
     * @param mappings a map of task name patterns to sets of capability domain names
     * @throws IllegalArgumentException if capability or mappings is null
     */
    public StaticMappingReasoner(AgentCapability capability, Map<String, Set<String>> mappings) {
        if (capability == null) {
            throw new IllegalArgumentException("capability is required");
        }
        if (mappings == null) {
            throw new IllegalArgumentException("mappings is required");
        }
        this.capability = capability;
        this.mappings = new HashMap<>(mappings);
        this.patternCache = new HashMap<>();
    }

    /**
     * Determines if the agent is eligible to handle the given work item.
     *
     * <p>The eligibility check:
     * <ol>
     *   <li>Gets task name from work item, falling back to task ID if task name is null</li>
     *   <li>Iterates through all configured mappings</li>
     *   <li>For each mapping: checks if task pattern matches work item's task identifier
     *       AND the agent's capability domain name is in the set of mapped capabilities</li>
     *   <li>Returns true on first match; false if no mapping matches</li>
     * </ol></p>
     *
     * <p>Pattern matching supports wildcards:
     * <ul>
     *   <li>{@code *} in pattern matches any sequence</li>
     *   <li>{@code ?} in pattern matches any single character</li>
     *   <li>Exact matches are tested first (no wildcard conversion)</li>
     * </ul></p>
     *
     * @param workItem the work item to evaluate for eligibility
     * @return true if agent's capability is mapped to a pattern matching the task;
     *         false otherwise
     * @throws IllegalArgumentException if workItem is null
     */
    @Override
    public boolean isEligible(WorkItemRecord workItem) {
        if (workItem == null) {
            throw new IllegalArgumentException("workItem is required");
        }

        String taskIdentifier = workItem.getTaskName();
        if (taskIdentifier == null || taskIdentifier.trim().isEmpty()) {
            taskIdentifier = workItem.getTaskID();
        }

        if (taskIdentifier == null || taskIdentifier.trim().isEmpty()) {
            return false;
        }

        for (Map.Entry<String, Set<String>> entry : mappings.entrySet()) {
            String pattern = entry.getKey();
            Set<String> capabilities = entry.getValue();

            if (capabilities.contains(capability.domainName())) {
                if (matchesPattern(taskIdentifier, pattern)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Adds a mapping from a task name pattern to a set of capability domain names.
     *
     * <p>The capabilities string is parsed as a comma-separated list. Each capability
     * is trimmed of whitespace.</p>
     *
     * @param taskName the task name pattern (may contain wildcards: * for any sequence, ? for single char)
     * @param capabilities comma-separated list of capability domain names
     * @throws IllegalArgumentException if taskName is null, empty, or capabilities is null
     */
    public void addMapping(String taskName, String capabilities) {
        if (taskName == null || taskName.trim().isEmpty()) {
            throw new IllegalArgumentException("taskName is required");
        }
        if (capabilities == null) {
            throw new IllegalArgumentException("capabilities is required");
        }

        String trimmedTaskName = taskName.trim();
        Set<String> capabilitySet = new HashSet<>();

        String[] capArray = capabilities.split(",");
        for (String cap : capArray) {
            String trimmed = cap.trim();
            if (!trimmed.isEmpty()) {
                capabilitySet.add(trimmed);
            }
        }

        mappings.put(trimmedTaskName, capabilitySet);
        patternCache.remove(trimmedTaskName);
    }

    /**
     * Returns an unmodifiable view of all configured task name patterns.
     *
     * @return unmodifiable set of task name patterns
     */
    public Set<String> getConfiguredTasks() {
        return Collections.unmodifiableSet(mappings.keySet());
    }

    /**
     * Returns the set of capability domain names configured for a specific task.
     *
     * <p>If the task name is null or not found in the mappings, returns an empty set.</p>
     *
     * @param taskName the task name to look up
     * @return unmodifiable set of capability domain names, or empty set if not configured
     */
    public Set<String> getCapabilitiesForTask(String taskName) {
        if (taskName == null) {
            return Collections.emptySet();
        }
        Set<String> capabilities = mappings.get(taskName);
        if (capabilities == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(capabilities);
    }

    /**
     * Clears all configured task-to-capability mappings.
     */
    public void clearMappings() {
        mappings.clear();
        patternCache.clear();
    }

    /**
     * Determines if a task identifier matches a pattern string.
     *
     * <p>Pattern matching supports wildcards:
     * <ul>
     *   <li>If pattern contains no wildcards (no * or ?), performs exact string comparison</li>
     *   <li>Converts wildcards to regex: {@code *} -> {@code .*}, {@code ?} -> {@code .}</li>
     *   <li>Uses cached compiled Pattern objects for performance</li>
     * </ul></p>
     *
     * @param taskIdentifier the task identifier to match
     * @param pattern the pattern string (may contain wildcards)
     * @return true if taskIdentifier matches the pattern; false otherwise
     */
    private boolean matchesPattern(String taskIdentifier, String pattern) {
        if (pattern.indexOf('*') == -1 && pattern.indexOf('?') == -1) {
            return pattern.equals(taskIdentifier);
        }

        Pattern compiled = patternCache.get(pattern);
        if (compiled == null) {
            String regex = convertWildcardToRegex(pattern);
            compiled = Pattern.compile(regex);
            patternCache.put(pattern, compiled);
        }

        return compiled.matcher(taskIdentifier).matches();
    }

    /**
     * Converts a wildcard pattern string to a regex pattern.
     *
     * <p>Conversion rules:
     * <ul>
     *   <li>{@code *} becomes {@code .*} (any sequence)</li>
     *   <li>{@code ?} becomes {@code .} (any single character)</li>
     *   <li>Other characters are escaped for regex</li>
     * </ul></p>
     *
     * @param wildcard the wildcard pattern string
     * @return regex pattern string
     */
    private String convertWildcardToRegex(String wildcard) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < wildcard.length(); i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '.' -> regex.append("\\.");
                case '^' -> regex.append("\\^");
                case '$' -> regex.append("\\$");
                case '(' -> regex.append("\\(");
                case ')' -> regex.append("\\)");
                case '[' -> regex.append("\\[");
                case ']' -> regex.append("\\]");
                case '{' -> regex.append("\\{");
                case '}' -> regex.append("\\}");
                case '|' -> regex.append("\\|");
                case '\\' -> regex.append("\\\\");
                case '+' -> regex.append("\\+");
                default -> regex.append(c);
            }
        }
        return "^" + regex + "$";
    }
}
