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

/**
 * OT-aligned sealed interface representing a single self-care action.
 *
 * <p>Actions are the atomic units of a {@link BehavioralActivationPlan}. Each subtype
 * maps to an Occupational Therapy performance area and carries domain-specific metadata.
 * Pattern-match over the four permitted subtypes to implement domain-specific logic.</p>
 *
 * <h2>Subtypes</h2>
 * <ul>
 *   <li>{@link DailyLivingAction} — ADL/IADL task backed by a Gregverse workflow spec</li>
 *   <li>{@link PhysicalActivity} — Exercise or movement, with intensity level 1–5</li>
 *   <li>{@link CognitiveActivity} — Memory, attention, or executive-function task</li>
 *   <li>{@link SocialEngagement} — Interaction with others, with expected group size</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SelfCareAction action = plan.nextAction();
 * String display = switch (action) {
 *     case DailyLivingAction a -> "ADL: " + a.title() + " (spec: " + a.specId() + ")";
 *     case PhysicalActivity  a -> "Move: " + a.title() + " @ intensity " + a.intensityLevel();
 *     case CognitiveActivity a -> "Think: " + a.title() + " — target: " + a.cognitiveTarget();
 *     case SocialEngagement  a -> "Connect: " + a.title() + " (group ≤ " + a.groupSize() + ")";
 * };
 * }</pre>
 *
 * @since YAWL 6.0
 */
public sealed interface SelfCareAction {

    /** Stable identifier for this action (UUID or Gregverse spec fragment). */
    String id();

    /** Short, imperative title (e.g. "Take a 5-minute walk"). */
    String title();

    /** One-sentence description of what to do. */
    String description();

    /** Estimated time to complete the action. Must be positive. */
    Duration estimated();

    /** OT performance area this action belongs to. */
    OTDomain domain();

    // ─── Permitted subtypes ───────────────────────────────────────────────

    /**
     * An Activity of Daily Living (ADL) or Instrumental ADL backed by a Gregverse
     * workflow specification. The {@link #specId()} links to the full YAWL spec
     * in the Gregverse registry.
     */
    record DailyLivingAction(
            String id,
            String title,
            String description,
            Duration estimated,
            OTDomain domain,
            String specId
    ) implements SelfCareAction {

        public DailyLivingAction {
            validate(id, title, description, estimated, domain);
            if (specId == null || specId.isBlank()) {
                throw new IllegalArgumentException("specId must not be null or blank");
            }
        }
    }

    /**
     * A physical activity or exercise action. {@link #intensityLevel()} is on a 1–5
     * scale (1 = gentle stretching, 5 = vigorous aerobic activity).
     */
    record PhysicalActivity(
            String id,
            String title,
            String description,
            Duration estimated,
            OTDomain domain,
            int intensityLevel
    ) implements SelfCareAction {

        public PhysicalActivity {
            validate(id, title, description, estimated, domain);
            if (intensityLevel < 1 || intensityLevel > 5) {
                throw new IllegalArgumentException(
                    "intensityLevel must be in range [1, 5], got: " + intensityLevel);
            }
        }
    }

    /**
     * A cognitive activity targeting memory, attention, or executive function.
     * {@link #cognitiveTarget()} names the faculty being exercised
     * (e.g. "working memory", "sustained attention", "planning").
     */
    record CognitiveActivity(
            String id,
            String title,
            String description,
            Duration estimated,
            OTDomain domain,
            String cognitiveTarget
    ) implements SelfCareAction {

        public CognitiveActivity {
            validate(id, title, description, estimated, domain);
            if (cognitiveTarget == null || cognitiveTarget.isBlank()) {
                throw new IllegalArgumentException("cognitiveTarget must not be null or blank");
            }
        }
    }

    /**
     * A social engagement action. {@link #groupSize()} is the expected number of participants
     * (1 = solo/reflection, 2 = dyadic, 3+ = group).
     */
    record SocialEngagement(
            String id,
            String title,
            String description,
            Duration estimated,
            OTDomain domain,
            int groupSize
    ) implements SelfCareAction {

        public SocialEngagement {
            validate(id, title, description, estimated, domain);
            if (groupSize < 1) {
                throw new IllegalArgumentException("groupSize must be at least 1, got: " + groupSize);
            }
        }
    }

    // ─── Shared validation ────────────────────────────────────────────────

    private static void validate(String id, String title, String description,
                                  Duration estimated, OTDomain domain) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be null or blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be null or blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be null or blank");
        }
        if (estimated == null || estimated.isNegative() || estimated.isZero()) {
            throw new IllegalArgumentException("estimated must be a positive duration");
        }
        if (domain == null) {
            throw new IllegalArgumentException("domain must not be null");
        }
    }
}
