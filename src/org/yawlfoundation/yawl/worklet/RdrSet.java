/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A set of {@link RdrTree} instances, keyed by task name.
 *
 * <p>An RdrSet holds one tree per task, enabling a workflow specification to have
 * different RDR rule sets for different tasks. When a work item is activated,
 * the engine looks up the tree for that task's name and runs the selection algorithm.
 *
 * <p>Specification ID is stored to link this rule set to a particular workflow spec.
 *
 * @author YAWL Foundation
 * @version 6.0.0-Beta
 */
public class RdrSet {

    private final String specificationId;
    private final Map<String, RdrTree> trees;

    /**
     * Constructs an empty RdrSet for the given specification.
     *
     * @param specificationId the workflow specification ID this rule set belongs to
     *                        (must not be null or blank)
     * @throws IllegalArgumentException if specificationId is null or blank
     */
    public RdrSet(String specificationId) {
        if (specificationId == null || specificationId.isBlank()) {
            throw new IllegalArgumentException("Specification ID must not be null or blank");
        }
        this.specificationId = specificationId.trim();
        this.trees = new HashMap<>();
    }

    /**
     * Returns the specification ID this rule set belongs to.
     */
    public String getSpecificationId() {
        return specificationId;
    }

    /**
     * Returns the number of trees (one per task) in this set.
     */
    public int size() {
        return trees.size();
    }

    /**
     * Returns true if no trees have been added.
     */
    public boolean isEmpty() {
        return trees.isEmpty();
    }

    /**
     * Adds a tree to this set, keyed by the tree's task name.
     *
     * @param tree the RDR tree to add (must not be null)
     * @throws IllegalArgumentException if tree is null
     * @throws IllegalStateException if a tree for the same task name already exists
     */
    public void addTree(RdrTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("RdrTree must not be null");
        }
        String taskName = tree.getTaskName();
        if (trees.containsKey(taskName)) {
            throw new IllegalStateException(
                    "A tree for task '" + taskName + "' already exists in this set");
        }
        trees.put(taskName, tree);
    }

    /**
     * Replaces any existing tree for the given task with the provided one.
     *
     * @param tree the tree to add or replace (must not be null)
     * @throws IllegalArgumentException if tree is null
     */
    public void putTree(RdrTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("RdrTree must not be null");
        }
        trees.put(tree.getTaskName(), tree);
    }

    /**
     * Returns the RDR tree for the specified task name, or null if none exists.
     *
     * @param taskName the task name to look up
     * @return the corresponding tree, or null
     */
    public RdrTree getTree(String taskName) {
        return trees.get(taskName);
    }

    /**
     * Returns true if a tree exists for the specified task name.
     *
     * @param taskName the task name to check
     */
    public boolean hasTree(String taskName) {
        return trees.containsKey(taskName);
    }

    /**
     * Removes the tree for the specified task name.
     *
     * @param taskName the task name whose tree to remove
     * @return the removed tree, or null if no tree existed for that task
     */
    public RdrTree removeTree(String taskName) {
        return trees.remove(taskName);
    }

    /**
     * Returns an unmodifiable view of all trees in this set.
     */
    public Collection<RdrTree> getTrees() {
        return Collections.unmodifiableCollection(trees.values());
    }

    /**
     * Returns an unmodifiable view of all task names that have trees in this set.
     */
    public Collection<String> getTaskNames() {
        return Collections.unmodifiableSet(trees.keySet());
    }

    /**
     * Selects a worklet for the given task and context.
     *
     * <p>Looks up the tree for {@code taskName} and invokes
     * {@link RdrTree#select(Map)} with the provided context.
     *
     * @param taskName the task name whose rule tree to use
     * @param context  the data context to evaluate rules against
     * @return the selected worklet name, or null if no rule applies or no tree exists
     * @throws IllegalArgumentException if taskName or context is null
     */
    public String select(String taskName, Map<String, String> context) {
        if (taskName == null) {
            throw new IllegalArgumentException("Task name must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("Selection context must not be null");
        }
        RdrTree tree = trees.get(taskName);
        if (tree == null) {
            return null;
        }
        return tree.select(context);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RdrSet other)) return false;
        return Objects.equals(specificationId, other.specificationId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(specificationId);
    }

    @Override
    public String toString() {
        return "RdrSet{specId='%s', treesCount=%d}".formatted(specificationId, trees.size());
    }
}
