/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.powl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validates the structural soundness of POWL models.
 * Detects cycles in non-LOOP control structures using depth-first search.
 */
public class PowlValidator {

    /**
     * Validates a POWL model for structural correctness.
     *
     * <p>Checks:
     * <ul>
     *   <li>No cycles in control flow (except LOOP operators which are allowed to cycle)</li>
     *   <li>LOOP operators have exactly 2 children (already checked by constructor)</li>
     * </ul>
     *
     * @param model the POWL model to validate (must not be null)
     * @return a ValidationReport indicating validity and listing any violations
     * @throws IllegalArgumentException if model is null
     */
    public ValidationReport validate(PowlModel model) {
        Objects.requireNonNull(model, "model must not be null");

        List<String> violations = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();

        detectCycles(model.root(), visited, recStack, violations);

        if (violations.isEmpty()) {
            return ValidationReport.clean();
        } else {
            return new ValidationReport(false, violations);
        }
    }

    /**
     * Detects cycles in the POWL tree using depth-first search with a recursion stack.
     * Cycles are not allowed in non-LOOP subtrees. LOOP nodes themselves may cycle
     * (they are designed to do so), but we do not traverse into their children
     * for cycle detection since LOOP is an intentional cycle construct.
     *
     * @param node        the current node being visited
     * @param visited     set of all visited node IDs
     * @param recStack    set of node IDs in the current DFS path (recursion stack)
     * @param violations  list to accumulate violation messages
     */
    private void detectCycles(PowlNode node, Set<String> visited, Set<String> recStack,
                              List<String> violations) {
        String nodeId = getNodeId(node);

        // If this node is already in our recursion stack, we have a cycle
        if (recStack.contains(nodeId)) {
            violations.add("Cycle detected: node '" + nodeId + "' revisited in control flow");
            return;
        }

        // If already fully visited, skip
        if (visited.contains(nodeId)) {
            return;
        }

        // Mark as in-progress (in recursion stack)
        recStack.add(nodeId);

        // Recursively check children (but not for LOOP operators, which are cycle-safe)
        if (node instanceof PowlOperatorNode opNode) {
            // Skip cycle checking inside LOOP bodies; LOOP is a safe cycle construct
            if (opNode.type() != PowlOperatorType.LOOP) {
                for (PowlNode child : opNode.children()) {
                    detectCycles(child, visited, recStack, violations);
                }
            }
        }
        // PowlActivity has no children, so nothing to recurse on

        // Mark as complete
        recStack.remove(nodeId);
        visited.add(nodeId);
    }

    /**
     * Gets the unique identifier from a PowlNode.
     * Uses pattern matching to extract the id field from either PowlActivity or PowlOperatorNode.
     *
     * @param node the node whose id is needed
     * @return the node's id string
     */
    private String getNodeId(PowlNode node) {
        return switch (node) {
            case PowlActivity activity -> activity.id();
            case PowlOperatorNode operator -> operator.id();
        };
    }
}
