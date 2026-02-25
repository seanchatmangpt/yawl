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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.pi.optimization;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of a process scheduling optimization.
 *
 * <p>Contains the ordered task sequence, scheduled start times, and
 * feasibility information.</p>
 *
 * @param orderedTaskIds tasks ordered by scheduling priority
 * @param scheduledStartTimes mapping from taskId to scheduled start time
 * @param feasible true if schedule is feasible (all constraints met)
 * @param infeasibilityReason reason for infeasibility (null if feasible)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record SchedulingResult(
    List<String> orderedTaskIds,
    Map<String, Instant> scheduledStartTimes,
    boolean feasible,
    String infeasibilityReason
) {}
