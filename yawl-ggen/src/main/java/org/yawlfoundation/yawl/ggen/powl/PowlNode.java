/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.powl;

/**
 * Sealed interface representing a node in a POWL (Partially Ordered Workflow Language) model.
 * A POWL node is either an activity (leaf) or an operator node (composite with children).
 * This sealed hierarchy enables exhaustive pattern matching in Java 21+.
 */
public sealed interface PowlNode permits PowlActivity, PowlOperatorNode {
    // Marker interface; implementations provide specific node semantics
}
