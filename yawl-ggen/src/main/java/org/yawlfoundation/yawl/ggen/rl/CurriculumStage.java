/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

/**
 * RL training curriculum stages for POWL process generation.
 *
 * <p>Stage A (VALIDITY_GAP): Uses LLM-as-judge to suppress syntax errors and
 * align coarse semantics. Universal reward weight = 1.0, verifiable = 0.0.
 *
 * <p>Stage B (BEHAVIORAL_CONSOLIDATION): Uses verifiable footprints agreement
 * to lock in precise control-flow and concurrency relations.
 * Universal reward weight = 0.0, verifiable = 1.0.
 */
public enum CurriculumStage {
    /** Stage A: validity gap closing via LLM judge */
    VALIDITY_GAP,
    /** Stage B: behavioral consolidation via footprints agreement */
    BEHAVIORAL_CONSOLIDATION
}
