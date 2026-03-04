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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.processmining.discovery;

import java.util.List;
import java.util.Objects;

/**
 * Process Tree sealed interface hierarchy for Inductive Miner results.
 *
 * <p>A process tree is a hierarchical representation of a process model
 * using composition operators. Each node is either a leaf (activity),
 * silent transition (τ), or an operator node combining child trees.</p>
 *
 * <h2>Tree Structure</h2>
 * <pre>{@code
 * ProcessTree tree =
 *   new Sequence(List.of(
 *     new Leaf("a"),
 *     new Parallel(List.of(
 *       new Leaf("b"),
 *       new Leaf("c")
 *     )),
 *     new Leaf("d")
 *   ));
 * }</pre>
 *
 * <h2>Properties</h2>
 * <ul>
 *   <li><strong>Sound by construction</strong>: Every valid execution path is defined</li>
 *   <li><strong>Deadlock-free</strong>: All operators ensure progress</li>
 *   <li><strong>Immutable</strong>: Use sealed records for thread-safety</li>
 * </ul>
 *
 * <h2>Operator Semantics</h2>
 * <ul>
 *   <li><strong>Sequence(children)</strong>: Execute children in order, left to right</li>
 *   <li><strong>ExclusiveChoice(children)</strong>: Execute one child at runtime</li>
 *   <li><strong>Parallel(children)</strong>: Execute all children concurrently</li>
 *   <li><strong>Loop(body, redo)</strong>: Execute body, then optionally redo, then exit</li>
 *   <li><strong>Leaf(activity)</strong>: Execute a single activity</li>
 *   <li><strong>Silent()</strong>: Execute without observable behavior (ε transition)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public sealed interface ProcessTree
        permits ProcessTree.Leaf, ProcessTree.Silent, ProcessTree.Sequence,
                ProcessTree.ExclusiveChoice, ProcessTree.Parallel, ProcessTree.Loop {

    /**
     * Leaf node representing a single activity execution.
     *
     * @param activity Non-null activity name
     */
    record Leaf(String activity) implements ProcessTree {
        /**
         * Construct a leaf node.
         *
         * @throws NullPointerException if activity is null
         */
        public Leaf {
            Objects.requireNonNull(activity, "activity cannot be null");
        }

        @Override
        public String toString() {
            return activity;
        }

        @Override
        public boolean isSound() {
            return true;
        }
    }

    /**
     * Silent transition (τ) representing unobservable behavior.
     *
     * <p>Used as a placeholder when no observable activity occurs
     * (e.g., in empty sublogs or epsilon transitions in loops).</p>
     */
    record Silent() implements ProcessTree {
        @Override
        public String toString() {
            return "τ";
        }

        @Override
        public boolean isSound() {
            return true;
        }
    }

    /**
     * Sequence operator: execute children in order.
     *
     * <p>Semantics: child₁ → child₂ → ... → childₙ</p>
     *
     * @param children Non-empty list of child trees
     */
    record Sequence(List<ProcessTree> children) implements ProcessTree {
        /**
         * Construct a sequence node.
         *
         * @throws NullPointerException if children is null
         * @throws IllegalArgumentException if children is empty
         */
        public Sequence {
            Objects.requireNonNull(children, "children cannot be null");
            if (children.isEmpty()) {
                throw new IllegalArgumentException("Sequence requires at least one child");
            }
            children = List.copyOf(children);  // defensive copy
        }

        @Override
        public String toString() {
            return "→(" + String.join(", ", children.stream().map(Object::toString).toList()) + ")";
        }

        @Override
        public boolean isSound() {
            return children.stream().allMatch(ProcessTree::isSound);
        }
    }

    /**
     * Exclusive choice operator: execute one child at runtime.
     *
     * <p>Semantics: choose one of child₁, child₂, ..., childₙ</p>
     *
     * @param children Non-empty list of mutually exclusive child trees
     */
    record ExclusiveChoice(List<ProcessTree> children) implements ProcessTree {
        /**
         * Construct an exclusive choice node.
         *
         * @throws NullPointerException if children is null
         * @throws IllegalArgumentException if children is empty
         */
        public ExclusiveChoice {
            Objects.requireNonNull(children, "children cannot be null");
            if (children.isEmpty()) {
                throw new IllegalArgumentException("ExclusiveChoice requires at least one child");
            }
            children = List.copyOf(children);  // defensive copy
        }

        @Override
        public String toString() {
            return "×(" + String.join(", ", children.stream().map(Object::toString).toList()) + ")";
        }

        @Override
        public boolean isSound() {
            return children.stream().allMatch(ProcessTree::isSound);
        }
    }

    /**
     * Parallel operator: execute all children concurrently.
     *
     * <p>Semantics: all children execute in parallel, synchronize at end</p>
     *
     * @param children Non-empty list of concurrently executable child trees
     */
    record Parallel(List<ProcessTree> children) implements ProcessTree {
        /**
         * Construct a parallel node.
         *
         * @throws NullPointerException if children is null
         * @throws IllegalArgumentException if children is empty
         */
        public Parallel {
            Objects.requireNonNull(children, "children cannot be null");
            if (children.isEmpty()) {
                throw new IllegalArgumentException("Parallel requires at least one child");
            }
            children = List.copyOf(children);  // defensive copy
        }

        @Override
        public String toString() {
            return "∧(" + String.join(", ", children.stream().map(Object::toString).toList()) + ")";
        }

        @Override
        public boolean isSound() {
            return children.stream().allMatch(ProcessTree::isSound);
        }
    }

    /**
     * Loop operator: execute body, optionally redo, then exit.
     *
     * <p>Semantics: do { body } while { redo decision }</p>
     *
     * @param body Main loop body (non-null)
     * @param redo Redo component executed if loop repeats (typically Silent if no redo)
     */
    record Loop(ProcessTree body, ProcessTree redo) implements ProcessTree {
        /**
         * Construct a loop node.
         *
         * @throws NullPointerException if body or redo is null
         */
        public Loop {
            Objects.requireNonNull(body, "body cannot be null");
            Objects.requireNonNull(redo, "redo cannot be null");
        }

        @Override
        public String toString() {
            return "↺(" + body + ", " + redo + ")";
        }

        @Override
        public boolean isSound() {
            return body.isSound() && redo.isSound();
        }
    }

    /**
     * Check if this tree represents a sound process model.
     *
     * <p>A tree is sound if:</p>
     * <ul>
     *   <li>Every leaf activity is reachable from the root</li>
     *   <li>Every leaf can reach a final state (no deadlocks)</li>
     *   <li>All operator children are also sound</li>
     * </ul>
     *
     * <p>Trees built by InductiveMiner are always sound by construction.</p>
     *
     * @return true if tree is sound, false otherwise
     */
    boolean isSound();
}
