/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An RDR (Ripple Down Rules) decision tree for worklet selection.
 *
 * <p>The tree is a binary tree of {@link RdrNode} instances. The traversal algorithm
 * starts at the root and follows true-child/false-child branches based on condition
 * evaluation. The last satisfied node's conclusion is returned as the selected worklet.
 *
 * <p>A tree must have a root node before it can be used for selection.
 * Nodes are added via the {@link #addNode(RdrNode, int, boolean)} method, which
 * attaches a new node as a child of an existing parent.
 *
 * <p>The tree's task name identifies which workflow task this rule set applies to.
 *
 * @author YAWL Foundation
 * @version 6.0.0-Beta
 */
public class RdrTree {

    private final String taskName;
    private RdrNode root;
    private int nodeCount;

    /**
     * Constructs an empty RDR tree for the specified task.
     *
     * @param taskName the workflow task name this rule tree covers (must not be null or blank)
     * @throws IllegalArgumentException if taskName is null or blank
     */
    public RdrTree(String taskName) {
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("Task name must not be null or blank");
        }
        this.taskName = taskName.trim();
        this.root = null;
        this.nodeCount = 0;
    }

    /**
     * Returns the task name this tree applies to.
     */
    public String getTaskName() {
        return taskName;
    }

    /**
     * Returns the root node of this tree, or null if the tree is empty.
     */
    public RdrNode getRoot() {
        return root;
    }

    /**
     * Returns the total number of nodes in this tree.
     */
    public int getNodeCount() {
        return nodeCount;
    }

    /**
     * Returns true if this tree has no root node (is empty).
     */
    public boolean isEmpty() {
        return root == null;
    }

    /**
     * Sets the root node of this tree.
     *
     * @param rootNode the root node (must not be null)
     * @throws IllegalArgumentException if rootNode is null
     * @throws IllegalStateException if the tree already has a root node
     */
    public void setRoot(RdrNode rootNode) {
        if (rootNode == null) {
            throw new IllegalArgumentException("Root node must not be null");
        }
        if (root != null) {
            throw new IllegalStateException("Tree already has a root node; use addNode() to extend");
        }
        this.root = rootNode;
        this.nodeCount = 1;
    }

    /**
     * Adds a new node as a child of the specified parent node.
     *
     * @param newNode      the node to add (must not be null)
     * @param parentId     the ID of the parent node to attach the new node to
     * @param asTrueChild  true to add as the parent's true-child; false for false-child
     * @throws IllegalArgumentException if newNode is null
     * @throws IllegalStateException    if no parent node with the given ID exists,
     *                                  or if the parent already has a child in the specified position
     */
    public void addNode(RdrNode newNode, int parentId, boolean asTrueChild) {
        if (newNode == null) {
            throw new IllegalArgumentException("New node must not be null");
        }
        if (root == null) {
            throw new IllegalStateException("Tree has no root; call setRoot() first");
        }
        RdrNode parent = findNode(root, parentId);
        if (parent == null) {
            throw new IllegalStateException("No node found with ID: " + parentId);
        }
        if (asTrueChild) {
            if (parent.hasTrueChild()) {
                throw new IllegalStateException(
                        "Parent node " + parentId + " already has a true-child");
            }
            parent.setTrueChild(newNode);
        } else {
            if (parent.hasFalseChild()) {
                throw new IllegalStateException(
                        "Parent node " + parentId + " already has a false-child");
            }
            parent.setFalseChild(newNode);
        }
        nodeCount++;
    }

    /**
     * Selects a worklet by traversing this tree against the provided data context.
     *
     * <p>The RDR algorithm:
     * <ol>
     *   <li>Start at root.</li>
     *   <li>Evaluate the current node's condition.</li>
     *   <li>If true and a true-child exists, recurse into true-child (refinement case).</li>
     *   <li>If false and a false-child exists, recurse into false-child (alternative case).</li>
     *   <li>Track the last node whose condition was true.</li>
     *   <li>Return the last-satisfied node's conclusion, or null if no rule was satisfied.</li>
     * </ol>
     *
     * @param context the data context to evaluate against (must not be null)
     * @return the selected worklet name (conclusion of the last satisfied node),
     *         or null if no rule applies
     * @throws IllegalArgumentException if context is null
     * @throws IllegalStateException    if the tree is empty
     */
    public String select(Map<String, String> context) {
        if (context == null) {
            throw new IllegalArgumentException("Selection context must not be null");
        }
        if (root == null) {
            throw new IllegalStateException("Cannot select from an empty tree");
        }
        RdrNode selected = traverse(root, context, null);
        return selected != null ? selected.getConclusion() : null;
    }

    /**
     * Finds the last satisfied node in the tree for the given context.
     * Returns null if no node condition is satisfied.
     *
     * @param context the data context to evaluate against
     * @return the last satisfied {@link RdrNode}, or null if none satisfied
     */
    public RdrNode selectNode(Map<String, String> context) {
        if (context == null) {
            throw new IllegalArgumentException("Selection context must not be null");
        }
        if (root == null) {
            throw new IllegalStateException("Cannot select from an empty tree");
        }
        return traverse(root, context, null);
    }

    /**
     * Returns all nodes in this tree via depth-first pre-order traversal.
     *
     * @return a list of all nodes; empty list if the tree is empty
     */
    public List<RdrNode> getAllNodes() {
        List<RdrNode> nodes = new ArrayList<>();
        collectNodes(root, nodes);
        return nodes;
    }

    /**
     * Finds a node with the specified ID via depth-first search.
     *
     * @param id the node ID to locate
     * @return the matching node, or null if not found
     */
    public RdrNode findNode(int id) {
        if (root == null) {
            return null;
        }
        return findNode(root, id);
    }

    // --- Private helpers ---

    /**
     * Recursive RDR traversal.
     * The RDR semantics: when a node's condition is true, we try its true-child first
     * (exception refinement). Only if the true-child's entire subtree yields no result do
     * we use the current node as the answer. When a condition is false, we traverse to
     * the false-child to find alternatives.
     */
    private RdrNode traverse(RdrNode node, Map<String, String> context, RdrNode lastSatisfied) {
        if (node == null) {
            return lastSatisfied;
        }
        if (node.evaluate(context)) {
            // This node's condition is satisfied; try to refine via true-child
            RdrNode refined = traverse(node.getTrueChild(), context, node);
            return refined != null ? refined : node;
        } else {
            // This node's condition is not satisfied; try the alternative (false-child)
            return traverse(node.getFalseChild(), context, lastSatisfied);
        }
    }

    private RdrNode findNode(RdrNode current, int id) {
        if (current == null) {
            return null;
        }
        if (current.getId() == id) {
            return current;
        }
        RdrNode found = findNode(current.getTrueChild(), id);
        if (found != null) {
            return found;
        }
        return findNode(current.getFalseChild(), id);
    }

    private void collectNodes(RdrNode node, List<RdrNode> nodes) {
        if (node == null) {
            return;
        }
        nodes.add(node);
        collectNodes(node.getTrueChild(), nodes);
        collectNodes(node.getFalseChild(), nodes);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RdrTree other)) return false;
        return Objects.equals(taskName, other.taskName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(taskName);
    }

    @Override
    public String toString() {
        return "RdrTree{taskName='%s', nodeCount=%d}".formatted(taskName, nodeCount);
    }
}
