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

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verifies structural soundness of YAWL workflow specifications expressed in compact YAML.
 *
 * <p>Implements a static analysis of van der Aalst's workflow net soundness criteria:
 * <ol>
 *   <li><strong>Reachability</strong>: Every task is reachable from the input condition
 *       (i-top) via forward BFS/DFS traversal.</li>
 *   <li><strong>Option to complete</strong>: Every task can reach the output condition
 *       (o-top) via reverse BFS/DFS traversal (back-edges).</li>
 *   <li><strong>Valid flow targets</strong>: All IDs referenced in {@code flows} fields
 *       either name another declared task or the sentinel value {@code "end"}.</li>
 *   <li><strong>Non-empty task list</strong>: The specification must contain at least
 *       one task.</li>
 *   <li><strong>Path existence</strong>: At least one complete path from i-top to
 *       o-top must exist.</li>
 * </ol>
 *
 * <p>The YAML format is the same compact format accepted by {@link YawlYamlConverter}:
 * <pre>{@code
 * name: OrderFulfillment
 * first: VerifyPayment
 * tasks:
 *   - id: VerifyPayment
 *     flows: [CheckInventory, CancelOrder]
 *   - id: CheckInventory
 *     flows: [end]
 *   - id: CancelOrder
 *     flows: [end]
 * }</pre>
 *
 * <p>The string {@code "end"} in a task's {@code flows} list is the conventional sentinel
 * that maps to the output condition (o-top).  Any task that lists {@code "end"} is
 * therefore a direct predecessor of o-top.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see YawlYamlConverter
 */
public class WorkflowSoundnessVerifier {

    /** Sentinel value used in YAML flows to indicate the output condition. */
    private static final String FLOW_END_SENTINEL = "end";

    /** Logical node ID for the input condition (i-top). */
    private static final String I_TOP = "i-top";

    /** Logical node ID for the output condition (o-top). */
    private static final String O_TOP = "o-top";

    private final YAMLMapper yamlMapper;

    /**
     * Immutable result of a soundness verification run.
     *
     * @param sound      {@code true} iff no violations were detected
     * @param violations ordered list of human-readable violation descriptions;
     *                   empty when {@code sound} is {@code true}
     */
    public record SoundnessResult(boolean sound, List<String> violations) {

        /** Canonical constructor — defensive copy of the violations list. */
        public SoundnessResult(boolean sound, List<String> violations) {
            this.sound = sound;
            this.violations = List.copyOf(violations);
        }

        /** Convenience factory for a sound (no-violation) result. */
        static SoundnessResult sound() {
            return new SoundnessResult(true, List.of());
        }

        /** Convenience factory for a violated result. */
        static SoundnessResult violated(List<String> violations) {
            return new SoundnessResult(false, violations);
        }
    }

    /** Creates a new verifier backed by a default Jackson {@link YAMLMapper}. */
    public WorkflowSoundnessVerifier() {
        this.yamlMapper = new YAMLMapper();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parses the given YAML string and verifies structural soundness.
     *
     * <p>Markdown code-fence wrappers ({@code ```yaml ... ```}) are stripped
     * automatically before parsing, matching the behaviour of {@link YawlYamlConverter}.
     *
     * @param yaml the compact YAML workflow specification (not null, not blank)
     * @return a {@link SoundnessResult} describing whether the spec is sound
     * @throws IllegalArgumentException if {@code yaml} is null, blank, or unparseable
     */
    @SuppressWarnings("unchecked")
    public SoundnessResult verifyYaml(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            throw new IllegalArgumentException("YAML input cannot be null or blank");
        }
        String cleaned = stripMarkdownCodeBlock(yaml);
        Map<String, Object> spec;
        try {
            spec = yamlMapper.readValue(cleaned, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse YAML: " + e.getMessage(), e);
        }
        return verify(spec);
    }

    /**
     * Verifies structural soundness of an already-parsed specification map.
     *
     * <p>The map must follow the same schema as the input to
     * {@link YawlYamlConverter#convertToXml}: keys {@code name}, {@code first}
     * (optional), and {@code tasks} (a list of task maps each with {@code id}
     * and {@code flows}).
     *
     * @param spec parsed specification map (not null)
     * @return a {@link SoundnessResult} describing whether the spec is sound
     * @throws IllegalArgumentException if {@code spec} is null
     */
    public SoundnessResult verify(Map<String, Object> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Specification map cannot be null");
        }

        List<String> violations = new ArrayList<>();

        List<Map<String, Object>> tasks = getTasks(spec);
        if (tasks.isEmpty()) {
            violations.add("Workflow contains no tasks; at least one task is required");
            return SoundnessResult.violated(violations);
        }

        // Collect all declared task IDs
        Set<String> declaredIds = collectDeclaredIds(tasks, violations);
        if (declaredIds.isEmpty()) {
            // Every task entry lacked an 'id' field — already recorded violations
            return SoundnessResult.violated(violations);
        }

        // Determine the entry task (first task reachable from i-top)
        String firstTaskId = resolveFirstTask(spec, tasks);
        if (firstTaskId == null || !declaredIds.contains(firstTaskId)) {
            String specified = getString(spec, "first", null);
            if (specified != null) {
                violations.add("The 'first' task '" + specified
                        + "' is not declared in the tasks list");
            } else {
                violations.add("Cannot determine the first task: "
                        + "no 'first' field and no tasks with an 'id' field");
            }
            return SoundnessResult.violated(violations);
        }

        // Build forward adjacency list: taskId -> set of successor IDs
        // O_TOP is used as the synthetic node for "end"
        Map<String, Set<String>> forward = buildForwardAdjacency(tasks, declaredIds, violations);

        // Build reverse adjacency list: taskId -> set of predecessor IDs
        Map<String, Set<String>> reverse = buildReverseAdjacency(forward, declaredIds);

        // Forward BFS from i-top (entry is firstTaskId)
        Set<String> forwardReachable = bfs(firstTaskId, forward, declaredIds);

        // Reverse BFS from o-top
        Set<String> backwardReachable = bfsReverse(O_TOP, reverse);

        // Check 1: every declared task must be forward-reachable from the first task
        for (String taskId : declaredIds) {
            if (!forwardReachable.contains(taskId)) {
                violations.add("Task '" + taskId
                        + "' is unreachable from the input condition (i-top)");
            }
        }

        // Check 2: every declared task must have a path to o-top
        for (String taskId : declaredIds) {
            if (!backwardReachable.contains(taskId)) {
                violations.add("Task '" + taskId
                        + "' cannot reach the output condition (o-top); it is a dead-end");
            }
        }

        // Check 3: at least one path from i-top to o-top must exist
        // This is implied when firstTaskId is both forward-reachable and backward-reachable,
        // but we check it explicitly as a top-level invariant.
        boolean pathExists = backwardReachable.contains(firstTaskId)
                && forwardReachable.contains(firstTaskId);
        if (!pathExists && violations.isEmpty()) {
            violations.add("No complete path exists from input condition (i-top) "
                    + "to output condition (o-top)");
        }

        return violations.isEmpty() ? SoundnessResult.sound() : SoundnessResult.violated(violations);
    }

    // -------------------------------------------------------------------------
    // Graph construction
    // -------------------------------------------------------------------------

    /**
     * Collects all declared task IDs.  Duplicate IDs and missing IDs are
     * recorded as violations.
     */
    private Set<String> collectDeclaredIds(
            List<Map<String, Object>> tasks,
            List<String> violations) {

        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> task : tasks) {
            String id = getString(task, "id", null);
            if (id == null || id.isBlank()) {
                violations.add("A task entry is missing its 'id' field");
                continue;
            }
            if (!ids.add(id)) {
                violations.add("Duplicate task id '" + id + "'");
            }
        }
        return ids;
    }

    /**
     * Builds the forward adjacency map from the tasks' {@code flows} lists.
     *
     * <p>The sentinel {@code "end"} is translated to {@link #O_TOP}.  Any flow
     * target that is neither a declared task ID nor the sentinel is recorded as
     * a violation (invalid reference) but is still added to the graph so that
     * subsequent BFS checks can run completely.
     */
    private Map<String, Set<String>> buildForwardAdjacency(
            List<Map<String, Object>> tasks,
            Set<String> declaredIds,
            List<String> violations) {

        Map<String, Set<String>> adjacency = new HashMap<>();
        // Pre-populate every declared node (including O_TOP) to ensure BFS works
        // even for tasks with no outgoing edges declared.
        for (String id : declaredIds) {
            adjacency.put(id, new LinkedHashSet<>());
        }
        adjacency.put(O_TOP, new LinkedHashSet<>());

        for (Map<String, Object> task : tasks) {
            String taskId = getString(task, "id", null);
            if (taskId == null || taskId.isBlank()) {
                continue; // already recorded above
            }

            List<String> flows = getStringList(task, "flows");
            for (String flow : flows) {
                String target = FLOW_END_SENTINEL.equals(flow) ? O_TOP : flow;

                if (!FLOW_END_SENTINEL.equals(flow) && !declaredIds.contains(flow)) {
                    violations.add("Task '" + taskId + "' references unknown flow target '"
                            + flow + "'");
                    // Still add to adjacency so BFS is not blocked
                    adjacency.computeIfAbsent(target, k -> new LinkedHashSet<>());
                }

                adjacency.get(taskId).add(target);
            }
        }
        return adjacency;
    }

    /**
     * Inverts the forward adjacency map to produce a reverse adjacency map used
     * for backward BFS from o-top.
     */
    private Map<String, Set<String>> buildReverseAdjacency(
            Map<String, Set<String>> forward,
            Set<String> declaredIds) {

        Map<String, Set<String>> reverse = new HashMap<>();
        for (String id : declaredIds) {
            reverse.put(id, new LinkedHashSet<>());
        }
        reverse.put(O_TOP, new LinkedHashSet<>());

        for (Map.Entry<String, Set<String>> entry : forward.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                reverse.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
            }
        }
        return reverse;
    }

    // -------------------------------------------------------------------------
    // BFS traversal
    // -------------------------------------------------------------------------

    /**
     * Forward BFS from {@code start}, only traversing into nodes that are in
     * {@code declaredIds} or are {@link #O_TOP}.
     *
     * @return the set of all nodes (task IDs) reachable from {@code start}
     */
    private Set<String> bfs(
            String start,
            Map<String, Set<String>> adjacency,
            Set<String> declaredIds) {

        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> neighbours = adjacency.getOrDefault(current, Set.of());
            for (String neighbour : neighbours) {
                if (!visited.contains(neighbour)) {
                    visited.add(neighbour);
                    queue.add(neighbour);
                }
            }
        }
        return visited;
    }

    /**
     * Reverse BFS from {@code start} (o-top), following back-edges.
     *
     * @return the set of all task IDs that have a path leading to {@code start}
     */
    private Set<String> bfsReverse(
            String start,
            Map<String, Set<String>> reverse) {

        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> predecessors = reverse.getOrDefault(current, Set.of());
            for (String predecessor : predecessors) {
                if (!visited.contains(predecessor)) {
                    visited.add(predecessor);
                    queue.add(predecessor);
                }
            }
        }
        // Remove o-top itself — callers check for task IDs, not the synthetic node
        visited.remove(start);
        return visited;
    }

    // -------------------------------------------------------------------------
    // Helpers (same conventions as YawlYamlConverter)
    // -------------------------------------------------------------------------

    /**
     * Resolves the first task ID: uses the {@code first} field if present;
     * otherwise falls back to the {@code id} of the first task in the list.
     */
    private String resolveFirstTask(
            Map<String, Object> spec,
            List<Map<String, Object>> tasks) {

        String first = getString(spec, "first", null);
        if (first != null) {
            return first;
        }
        if (!tasks.isEmpty()) {
            return getString(tasks.get(0), "id", null);
        }
        return null;
    }

    /** Reads a string field from a map, returning {@code defaultValue} when absent. */
    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    /** Reads the {@code tasks} list from the specification map. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getTasks(Map<String, Object> spec) {
        Object tasksObj = spec.get("tasks");
        if (tasksObj instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    result.add((Map<String, Object>) m);
                }
            }
            return result;
        }
        return List.of();
    }

    /** Reads a list-of-strings field from a task map. */
    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return List.of();
    }

    /**
     * Strips markdown code-fence wrappers from the input string.
     * Handles both {@code ```yaml} and plain {@code ```} fences.
     */
    private String stripMarkdownCodeBlock(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int newlineIndex = trimmed.indexOf('\n');
            if (newlineIndex > 0) {
                trimmed = trimmed.substring(newlineIndex + 1);
            }
            int closingFence = trimmed.lastIndexOf("```");
            if (closingFence > 0) {
                trimmed = trimmed.substring(0, closingFence);
            }
        }
        return trimmed.trim();
    }
}
