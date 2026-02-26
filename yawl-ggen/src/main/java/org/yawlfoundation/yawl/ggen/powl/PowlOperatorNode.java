/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.powl;

import java.util.List;
import java.util.Objects;

/**
 * A composite node in a POWL model representing a control-flow operator.
 * Operator nodes have children that are coordinated according to the operator type.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>id must not be blank</li>
 *   <li>type must not be null</li>
 *   <li>children must not be empty</li>
 *   <li>LOOP operators must have exactly 2 children: the do-child and redo-child</li>
 * </ul>
 *
 * @param id       the unique identifier for this operator node (must not be blank)
 * @param type     the control-flow operator type (SEQUENCE, XOR, PARALLEL, or LOOP)
 * @param children the child nodes (must not be empty; LOOP requires exactly 2)
 */
public record PowlOperatorNode(String id, PowlOperatorType type, List<PowlNode> children)
        implements PowlNode {

    /**
     * Compact constructor enforcing structural invariants.
     */
    public PowlOperatorNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(children, "children must not be null");
        if (children.isEmpty()) {
            throw new IllegalArgumentException("children must not be empty");
        }
        if (type == PowlOperatorType.LOOP && children.size() != 2) {
            throw new IllegalArgumentException(
                "LOOP operator requires exactly 2 children (do, redo), got: " + children.size()
            );
        }
        // Defensive copy to ensure immutability
        children = List.copyOf(children);
    }
}
