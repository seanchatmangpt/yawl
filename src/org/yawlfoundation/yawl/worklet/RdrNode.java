/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

import java.util.Map;
import java.util.Objects;

/**
 * A single node in a Ripple Down Rules (RDR) decision tree.
 *
 * <p>Each RDR node has:
 * <ul>
 *   <li>A numeric ID (unique within its tree)</li>
 *   <li>A {@link RdrCondition} that is evaluated against a work item data context</li>
 *   <li>A conclusion (worklet name to invoke when this node is the last satisfied rule)</li>
 *   <li>References to true-child and false-child nodes for tree traversal</li>
 * </ul>
 *
 * <p>The RDR traversal algorithm:
 * <ol>
 *   <li>Evaluate this node's condition against the current context.</li>
 *   <li>If true, traverse to {@link #getTrueChild()} (exception case — more specific rule).</li>
 *   <li>If false (or no true-child exists), traverse to {@link #getFalseChild()} (alternative).</li>
 *   <li>The last satisfied node's conclusion is returned as the selected worklet.</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 */
public class RdrNode {

    private final int id;
    private final RdrCondition condition;
    private final String conclusion;
    private RdrNode trueChild;
    private RdrNode falseChild;

    /**
     * Constructs an RDR node with the given ID, condition, and conclusion.
     *
     * @param id         the unique node ID within its tree (must be >= 0)
     * @param condition  the condition to evaluate (must not be null)
     * @param conclusion the worklet name to invoke if this node is selected (must not be null or blank)
     * @throws IllegalArgumentException if id is negative, condition is null, or conclusion is blank
     */
    public RdrNode(int id, RdrCondition condition, String conclusion) {
        if (id < 0) {
            throw new IllegalArgumentException("Node ID must be non-negative, got: " + id);
        }
        if (condition == null) {
            throw new IllegalArgumentException("Node condition must not be null");
        }
        if (conclusion == null || conclusion.isBlank()) {
            throw new IllegalArgumentException("Node conclusion must not be null or blank");
        }
        this.id = id;
        this.condition = condition;
        this.conclusion = conclusion.trim();
        this.trueChild = null;
        this.falseChild = null;
    }

    /**
     * Convenience constructor accepting condition as a string expression.
     *
     * @param id                  the unique node ID within its tree
     * @param conditionExpression the condition expression string
     * @param conclusion          the worklet name conclusion
     */
    public RdrNode(int id, String conditionExpression, String conclusion) {
        this(id, new RdrCondition(conditionExpression), conclusion);
    }

    /**
     * Returns this node's unique identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the condition associated with this node.
     */
    public RdrCondition getCondition() {
        return condition;
    }

    /**
     * Returns the worklet name conclusion for this node.
     * This is the worklet that will be launched if this node is the last satisfied rule.
     */
    public String getConclusion() {
        return conclusion;
    }

    /**
     * Returns the true-child node (traversed when this node's condition evaluates to true).
     * May be null if this node has no true-child.
     */
    public RdrNode getTrueChild() {
        return trueChild;
    }

    /**
     * Sets the true-child node for this node.
     *
     * @param trueChild the node to traverse when this node's condition is true; may be null
     */
    public void setTrueChild(RdrNode trueChild) {
        this.trueChild = trueChild;
    }

    /**
     * Returns the false-child node (traversed when this node's condition evaluates to false).
     * May be null if this node has no false-child.
     */
    public RdrNode getFalseChild() {
        return falseChild;
    }

    /**
     * Sets the false-child node for this node.
     *
     * @param falseChild the node to traverse when this node's condition is false; may be null
     */
    public void setFalseChild(RdrNode falseChild) {
        this.falseChild = falseChild;
    }

    /**
     * Evaluates this node's condition against the provided context.
     *
     * @param context the data context (attribute name to value map)
     * @return true if the condition is satisfied, false otherwise
     */
    public boolean evaluate(Map<String, String> context) {
        return condition.evaluate(context);
    }

    /**
     * Returns true if this node is a leaf node (has neither true nor false children).
     */
    public boolean isLeaf() {
        return trueChild == null && falseChild == null;
    }

    /**
     * Returns true if this node has a true-child.
     */
    public boolean hasTrueChild() {
        return trueChild != null;
    }

    /**
     * Returns true if this node has a false-child.
     */
    public boolean hasFalseChild() {
        return falseChild != null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RdrNode other)) return false;
        return id == other.id &&
               Objects.equals(condition, other.condition) &&
               Objects.equals(conclusion, other.conclusion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, condition, conclusion);
    }

    @Override
    public String toString() {
        return "RdrNode{id=%d, condition='%s', conclusion='%s', hasTrue=%b, hasFalse=%b}"
                .formatted(id, condition.getExpression(), conclusion, trueChild != null, falseChild != null);
    }
}
