/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

/**
 * YAWL Worklet Service — Dynamic workflow selection via Ripple Down Rules (RDR).
 *
 * <p>The worklet service provides runtime workflow adaptation by substituting tasks
 * with "worklets" — small workflow fragments selected via a rule-based inference engine
 * based on Ripple Down Rules (RDR). Key concepts:
 *
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.worklet.RdrNode} — A single rule in the RDR tree,
 *       consisting of a condition and a conclusion (worklet name).</li>
 *   <li>{@link org.yawlfoundation.yawl.worklet.RdrTree} — A binary decision tree of RDR nodes,
 *       supporting rule selection via path traversal.</li>
 *   <li>{@link org.yawlfoundation.yawl.worklet.RdrSet} — A collection of RDR trees keyed by
 *       task name, enabling per-task rule sets.</li>
 *   <li>{@link org.yawlfoundation.yawl.worklet.WorkletRecord} — Represents a selected worklet
 *       with its specification ID and case binding information.</li>
 * </ul>
 *
 * <p>The RDR algorithm:
 * <ol>
 *   <li>Start at the root node.</li>
 *   <li>Evaluate the condition using the current work item context.</li>
 *   <li>If true, traverse to the true-child; if false, traverse to the false-child.</li>
 *   <li>The last satisfied node provides the selected worklet conclusion.</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0-Beta
 */
package org.yawlfoundation.yawl.worklet;
