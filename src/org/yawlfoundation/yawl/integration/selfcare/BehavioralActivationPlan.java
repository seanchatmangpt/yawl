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

package org.yawlfoundation.yawl.integration.selfcare;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable action plan embodying the behavioral activation principle:
 * <em>act first — motivation and wellbeing follow from action, not the reverse.</em>
 *
 * <p>Actions are ordered easiest-first so that {@link #nextAction()} always returns
 * the lowest-barrier entry point. The autonomic engine calls {@code nextAction()} to
 * trigger the first step without requiring the person to deliberate.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * BehavioralActivationPlan plan = engine.generatePlan(OTDomain.SELF_CARE, 3);
 *
 * // The immediate "act now" step — no deliberation required
 * SelfCareAction first = plan.nextAction();
 *
 * // Total time commitment across all actions
 * Duration total = plan.totalDuration();
 *
 * System.out.println("Start here: " + first.title());
 * System.out.println("Total commitment: " + total.toMinutes() + " min");
 * }</pre>
 *
 * @param planId       stable UUID for this plan
 * @param domain       the OT performance area this plan addresses
 * @param actions      ordered list of actions, easiest-first; must not be empty
 * @param generatedAt  when this plan was created
 * @param rationale    brief explanation of why these actions were chosen
 *
 * @since YAWL 6.0
 */
public record BehavioralActivationPlan(
        String planId,
        OTDomain domain,
        List<SelfCareAction> actions,
        Instant generatedAt,
        String rationale
) {

    /** Compact constructor validates all fields. */
    public BehavioralActivationPlan {
        Objects.requireNonNull(planId, "planId must not be null");
        if (planId.isBlank()) {
            throw new IllegalArgumentException("planId must not be blank");
        }
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(actions, "actions must not be null");
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("actions must not be empty");
        }
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
        if (rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
        // Defensive copy to enforce immutability
        actions = List.copyOf(actions);
    }

    /**
     * Returns the first action in the plan — the immediate "act now" entry point.
     *
     * <p>This is always the lowest-barrier action (shortest estimated duration or
     * lowest intensity). The autonomic engine triggers this without requiring
     * user deliberation.</p>
     *
     * @return the first (easiest) action in the plan
     */
    public SelfCareAction nextAction() {
        return actions.getFirst();
    }

    /**
     * Returns the total estimated duration across all actions in this plan.
     *
     * @return sum of {@link SelfCareAction#estimated()} for all actions
     */
    public Duration totalDuration() {
        return actions.stream()
            .map(SelfCareAction::estimated)
            .reduce(Duration.ZERO, Duration::plus);
    }
}
