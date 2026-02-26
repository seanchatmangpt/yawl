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

import java.util.List;

/**
 * Immutable result of process sequence alignment and conformance checking.
 *
 * <p>Quantifies the differences between an observed activity sequence
 * (from event log) and a reference model sequence using edit distance metrics.</p>
 *
 * @param observedActivities activities recorded in the event log
 * @param referenceActivities activities expected from the process model
 * @param synchronousMoves count of perfectly matching activities (cost = 0)
 * @param moveOnLogMoves count of extra activities in log (deviations)
 * @param moveOnModelMoves count of missing activities (skips)
 * @param alignmentCost total weighted cost of alignment
 * @param fitnessDelta improvement ratio from aligning (0.0 to 1.0)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record AlignmentResult(
    List<String> observedActivities,
    List<String> referenceActivities,
    int synchronousMoves,
    int moveOnLogMoves,
    int moveOnModelMoves,
    double alignmentCost,
    double fitnessDelta
) {}
