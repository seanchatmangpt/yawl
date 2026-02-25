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

package org.yawlfoundation.yawl.pi.prescriptive;

import java.util.Comparator;
import java.util.List;

/**
 * Scores and ranks recommended process actions.
 *
 * <p>Evaluates actions based on baseline risk score, assigning confidence scores
 * that reflect the expected benefit of each action. Higher scores indicate
 * more impactful interventions.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ActionRecommender {

    /**
     * Score a single action given baseline risk.
     *
     * <p>Scoring heuristics:
     * <ul>
     *   <li>RerouteAction: 0.7 if risk > 0.5, else 0.3
     *   <li>EscalateAction: 0.8 if risk > 0.7, else 0.4
     *   <li>ReallocateResourceAction: 0.6 if risk > 0.5, else 0.2
     *   <li>NoOpAction: 0.1 (baseline)
     * </ul>
     *
     * @param action Action to evaluate
     * @param baselineRiskScore Risk score [0.0, 1.0] for the case
     * @return Recommendation score
     */
    public double scoreAction(ProcessAction action, double baselineRiskScore) {
        return switch (action) {
            case RerouteAction ra -> baselineRiskScore > 0.5 ? 0.7 : 0.3;
            case EscalateAction ea -> baselineRiskScore > 0.7 ? 0.8 : 0.4;
            case ReallocateResourceAction rra -> baselineRiskScore > 0.5 ? 0.6 : 0.2;
            case NoOpAction noa -> 0.1;
            default -> 0.0;
        };
    }

    /**
     * Rank a list of actions from best to worst.
     *
     * <p>Sorts actions by recommendation score in descending order.
     * Uses baselineRiskScore to determine appropriateness of each action.
     *
     * @param actions Actions to rank
     * @param baselineRiskScore Case risk score [0.0, 1.0]
     * @return Sorted list (highest scoring action first)
     */
    public List<ProcessAction> rank(List<ProcessAction> actions, double baselineRiskScore) {
        return actions.stream()
            .sorted(Comparator.comparingDouble((ProcessAction a) ->
                scoreAction(a, baselineRiskScore)).reversed())
            .toList();
    }
}
