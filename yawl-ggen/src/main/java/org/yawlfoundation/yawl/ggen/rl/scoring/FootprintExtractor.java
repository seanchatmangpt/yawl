/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl.scoring;

import org.yawlfoundation.yawl.ggen.powl.PowlActivity;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlNode;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorNode;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Extracts a FootprintMatrix from a POWL model.
 * The footprint captures control-flow relationships between activities.
 */
public class FootprintExtractor {

    /**
     * Extracts a FootprintMatrix from a POWL model by traversing its tree structure.
     *
     * @param model the POWL model to analyze (must not be null)
     * @return a FootprintMatrix capturing the model's control-flow relationships
     * @throws IllegalArgumentException if model is null
     */
    public FootprintMatrix extract(PowlModel model) {
        Objects.requireNonNull(model, "model must not be null");

        Set<List<String>> directSuccession = new HashSet<>();
        Set<List<String>> concurrency = new HashSet<>();
        Set<List<String>> exclusive = new HashSet<>();

        traverse(model.root(), directSuccession, concurrency, exclusive);

        return new FootprintMatrix(directSuccession, concurrency, exclusive);
    }

    /**
     * Recursively traverses a POWL node, accumulating control-flow relationships.
     *
     * @param node               the node to traverse
     * @param directSuccession   set to accumulate successor relationships
     * @param concurrency        set to accumulate concurrent relationships
     * @param exclusive          set to accumulate exclusive relationships
     */
    private void traverse(PowlNode node, Set<List<String>> directSuccession,
                          Set<List<String>> concurrency, Set<List<String>> exclusive) {
        switch (node) {
            case PowlActivity activity -> {
                // Leaf node; no relationships to extract
            }
            case PowlOperatorNode operator -> {
                switch (operator.type()) {
                    case SEQUENCE -> extractSequence(operator.children(), directSuccession,
                            concurrency, exclusive);
                    case XOR -> extractXor(operator.children(), directSuccession,
                            concurrency, exclusive);
                    case PARALLEL -> extractParallel(operator.children(), directSuccession,
                            concurrency, exclusive);
                    case LOOP -> extractLoop(operator.children(), directSuccession,
                            concurrency, exclusive);
                }
                // Recursively traverse children
                for (PowlNode child : operator.children()) {
                    traverse(child, directSuccession, concurrency, exclusive);
                }
            }
        }
    }

    /**
     * SEQUENCE: add direct succession relationships between consecutive children.
     * For each consecutive pair (children[i], children[i+1]),
     * add (lastActivity(children[i]), firstActivity(children[i+1])) to directSuccession.
     */
    private void extractSequence(List<PowlNode> children, Set<List<String>> directSuccession,
                                 Set<List<String>> concurrency, Set<List<String>> exclusive) {
        for (int i = 0; i < children.size() - 1; i++) {
            Set<String> lastActivities = collectActivities(children.get(i));
            Set<String> firstActivities = collectActivities(children.get(i + 1));

            for (String last : lastActivities) {
                for (String first : firstActivities) {
                    directSuccession.add(List.of(last, first));
                }
            }
        }
    }

    /**
     * XOR: add exclusive relationships between all pairs of children.
     * For each pair of children (a, b), add (first(a), first(b)) to exclusive.
     */
    private void extractXor(List<PowlNode> children, Set<List<String>> directSuccession,
                            Set<List<String>> concurrency, Set<List<String>> exclusive) {
        for (int i = 0; i < children.size(); i++) {
            for (int j = i + 1; j < children.size(); j++) {
                Set<String> firstA = collectActivities(children.get(i));
                Set<String> firstB = collectActivities(children.get(j));

                for (String a : firstA) {
                    for (String b : firstB) {
                        exclusive.add(List.of(a, b));
                        exclusive.add(List.of(b, a)); // Symmetric
                    }
                }
            }
        }
    }

    /**
     * PARALLEL: add concurrency relationships between all pairs of children.
     * For each pair of children (a, b), add (first(a), first(b)) to concurrency.
     */
    private void extractParallel(List<PowlNode> children, Set<List<String>> directSuccession,
                                 Set<List<String>> concurrency, Set<List<String>> exclusive) {
        for (int i = 0; i < children.size(); i++) {
            for (int j = i + 1; j < children.size(); j++) {
                Set<String> firstA = collectActivities(children.get(i));
                Set<String> firstB = collectActivities(children.get(j));

                for (String a : firstA) {
                    for (String b : firstB) {
                        concurrency.add(List.of(a, b));
                        concurrency.add(List.of(b, a)); // Symmetric
                    }
                }
            }
        }
    }

    /**
     * LOOP: add direct succession relationships from redo-child back to do-child.
     * For a LOOP with do-child and redo-child, add:
     *   (last(doChild), first(redoChild))
     *   (last(redoChild), first(doChild))
     */
    private void extractLoop(List<PowlNode> children, Set<List<String>> directSuccession,
                             Set<List<String>> concurrency, Set<List<String>> exclusive) {
        if (children.size() != 2) {
            throw new IllegalStateException(
                "LOOP must have exactly 2 children, got: " + children.size()
            );
        }

        PowlNode doChild = children.get(0);
        PowlNode redoChild = children.get(1);

        Set<String> doActivities = collectActivities(doChild);
        Set<String> redoActivities = collectActivities(redoChild);

        for (String doActivity : doActivities) {
            for (String redoActivity : redoActivities) {
                directSuccession.add(List.of(doActivity, redoActivity));
                directSuccession.add(List.of(redoActivity, doActivity));
            }
        }
    }

    /**
     * Collects all activity labels reachable from a given node.
     *
     * @param node the node to analyze
     * @return a set of activity labels in the node's subtree
     */
    private Set<String> collectActivities(PowlNode node) {
        Set<String> activities = new HashSet<>();
        collectActivitiesRec(node, activities);
        return activities;
    }

    /**
     * Recursively collects all activity labels in a subtree.
     */
    private void collectActivitiesRec(PowlNode node, Set<String> activities) {
        switch (node) {
            case PowlActivity activity -> activities.add(activity.label());
            case PowlOperatorNode operator -> {
                for (PowlNode child : operator.children()) {
                    collectActivitiesRec(child, activities);
                }
            }
        }
    }
}
