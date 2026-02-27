/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.powl;

/**
 * Control-flow operator types in the Partially Ordered Workflow Language (POWL).
 * Each operator defines how its child nodes are sequenced and coordinated.
 */
public enum PowlOperatorType {
    /** Sequential execution: each child follows the previous one. */
    SEQUENCE,

    /** Exclusive choice: exactly one child executes based on guard conditions. */
    XOR,

    /** Parallel execution: all children execute concurrently. */
    PARALLEL,

    /** Loop: repeatedly execute the "do" child until the "redo" child completes (break condition). */
    LOOP
}
