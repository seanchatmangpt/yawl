/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.lifecycle;

import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.config.GregVerseConfig;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation.GregVerseSimulation;
import org.yawlfoundation.yawl.mcp.a2a.therapy.OTSwarmConfig;
import org.yawlfoundation.yawl.mcp.a2a.therapy.OTSwarmCoordinator;
import org.yawlfoundation.yawl.mcp.a2a.therapy.OTSwarmResult;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.LifestyleGoal;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OTPatient;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the zero cognitive load life management system.
 *
 * <p>A single call to {@link #run} drives three autonomous subsystems sequentially:</p>
 *
 * <ol>
 *   <li><b>Phase 1 — OT Lifestyle Redesign Swarm</b>: Converts the minimal
 *       {@link LifeContext} into an {@link OTPatient}, then executes the full
 *       8-phase occupational therapy workflow via {@link OTSwarmCoordinator}
 *       with {@code autoAdvance=true}. No human input required.</li>
 *   <li><b>Phase 2 — Van der Aalst WCP Demo</b>: Selects and demonstrates the
 *       Workflow Control-flow Patterns that best structure the OT goals, using
 *       {@link VanDerAalstPatternAdvisor} in self-play mode via
 *       {@code PatternDemoRunner}. Skipped if {@code config.runPatternDemo()} is false.</li>
 *   <li><b>Phase 3 — GregVerse Self-Play</b>: Runs all GregVerse business advisor
 *       agents autonomously via {@link GregVerseSimulation} with
 *       {@link GregVerseConfig#forSelfPlay()}. Skipped if {@code config.runAdvisors()}
 *       is false.</li>
 * </ol>
 *
 * <p>Results from all three pillars are synthesised into a {@link LifeManagementResult}
 * containing a unified {@link LifeManagementPlan}.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class LifeManagementOrchestrator {

    /**
     * Maps GregVerse agent IDs to the OT goal area they best address.
     * Agents not listed here default to "productivity".
     */
    private static final Map<String, String> AGENT_AREA = Map.of(
        "greg-isenberg",  "productivity",
        "justin-welsh",   "productivity",
        "james",          "productivity",
        "dan-romero",     "productivity",
        "blake-anderson", "self-care",
        "dickie-bush",    "self-care",
        "leo-leojrr",     "leisure",
        "nicolas-cole",   "leisure"
    );

    /**
     * Executes the full zero cognitive load life management pipeline.
     *
     * @param context the user's minimal lifestyle context (non-null)
     * @param config  orchestration configuration (non-null)
     * @return complete life management result (never null)
     * @throws IllegalArgumentException if context or config is null
     * @throws IOException              if the OT workflow YAML cannot be loaded
     * @throws YSyntaxException         if YAML-to-YAWL-XML conversion fails
     */
    public LifeManagementResult run(LifeContext context, LifeManagementConfig config)
            throws IOException, YSyntaxException {
        if (context == null) throw new IllegalArgumentException("LifeContext must not be null");
        if (config == null) throw new IllegalArgumentException("LifeManagementConfig must not be null");

        // Phase 1: OT lifecycle
        OTPatient patient = buildPatient(context);
        OTSwarmConfig otConfig = new OTSwarmConfig(
            patient.id(), true, config.maxAdaptationCycles(), config.timeout());
        OTSwarmResult otResult = new OTSwarmCoordinator().execute(patient, otConfig);

        // Phase 2: van der Aalst WCP demo
        List<WcpPatternInsight> wcpInsights;
        if (config.runPatternDemo() && !otResult.goals().isEmpty()) {
            wcpInsights = new VanDerAalstPatternAdvisor()
                .run(otResult.goals(), config.timeout());
        } else {
            wcpInsights = List.of();
        }

        // Phase 3: GregVerse self-play advisors
        GregVerseReport gvReport = null;
        if (config.runAdvisors()) {
            gvReport = new GregVerseSimulation(GregVerseConfig.forSelfPlay()).run();
        }

        // Phase 4: synthesis
        LifeManagementPlan plan = synthesize(otResult, wcpInsights, gvReport, context);

        boolean success = otResult.success();
        String summary = buildSummary(context, otResult, wcpInsights, gvReport);

        return new LifeManagementResult(
            UUID.randomUUID().toString(),
            context,
            otResult,
            wcpInsights,
            gvReport,
            plan,
            Instant.now(),
            success,
            summary
        );
    }

    // ── package-private helpers (visible to tests) ───────────────────────────

    /**
     * Derives an {@link OTPatient} from the user's {@link LifeContext}.
     *
     * <p>Field mappings:</p>
     * <ul>
     *   <li>{@code id} — random UUID</li>
     *   <li>{@code condition} — focus areas joined by ", "</li>
     *   <li>{@code referralReason} — "Self-directed lifestyle redesign"</li>
     *   <li>{@code functionalGoal} — "Optimise {primaryArea} with {hours} hrs/week"</li>
     * </ul>
     *
     * @param context the user's lifestyle context
     * @return corresponding OTPatient record
     */
    OTPatient buildPatient(LifeContext context) {
        return new OTPatient(
            UUID.randomUUID().toString(),
            context.name(),
            context.age(),
            String.join(", ", context.focusAreas()),
            "Self-directed lifestyle redesign",
            "Optimise " + context.primaryFocusArea()
                + " with " + context.weeklyHoursAvailable() + " hrs/week"
        );
    }

    /**
     * Synthesises results from all three pillars into a unified plan.
     *
     * @param otResult    OT swarm result
     * @param wcpInsights WCP pattern insights (may be empty)
     * @param gvReport    GregVerse report (may be null if advisor layer skipped)
     * @param context     the original user context
     * @return synthesised life management plan
     */
    LifeManagementPlan synthesize(OTSwarmResult otResult,
                                  List<WcpPatternInsight> wcpInsights,
                                  GregVerseReport gvReport,
                                  LifeContext context) {
        List<AdvisorInsight> advisorInsights = extractAdvisorInsights(gvReport);

        String title = "Life Management Plan for " + context.name();

        String narrative = buildNarrative(context, otResult.goals(), wcpInsights, advisorInsights);

        return new LifeManagementPlan(
            title,
            otResult.goals(),
            wcpInsights,
            advisorInsights,
            narrative
        );
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private List<AdvisorInsight> extractAdvisorInsights(GregVerseReport gvReport) {
        if (gvReport == null) return List.of();

        List<AdvisorInsight> insights = new ArrayList<>();
        for (GregVerseReport.AgentResult ar : gvReport.agentResults()) {
            String area = AGENT_AREA.getOrDefault(ar.agentId(), "productivity");
            insights.add(new AdvisorInsight(
                area,
                ar.agentId(),
                ar.displayName(),
                ar.output() != null ? ar.output() : "",
                ar.success()
            ));
        }
        return List.copyOf(insights);
    }

    private String buildNarrative(LifeContext context,
                                  List<LifestyleGoal> goals,
                                  List<WcpPatternInsight> wcps,
                                  List<AdvisorInsight> advisors) {
        int goalCount = goals.size();
        int wcpCount = wcps.stream().filter(WcpPatternInsight::demonstrated).mapToInt(w -> 1).sum();
        int advisorCount = (int) advisors.stream().filter(AdvisorInsight::success).count();

        String primaryArea = context.primaryFocusArea();
        String areasJoined = String.join(", ", context.focusAreas());

        return String.format(
            "%s's life management plan targets %s (focus areas: %s). "
            + "The OT swarm identified %d SMART goals. "
            + "%d van der Aalst workflow patterns structure the execution roadmap. "
            + "%d GregVerse advisors contributed productivity and lifestyle insights. "
            + "Engage %d hrs/week to achieve sustainable progress.",
            context.name(), primaryArea, areasJoined,
            goalCount, wcpCount, advisorCount,
            context.weeklyHoursAvailable()
        );
    }

    private String buildSummary(LifeContext context,
                                OTSwarmResult otResult,
                                List<WcpPatternInsight> wcps,
                                GregVerseReport gvReport) {
        int goalCount = otResult.goals().size();
        int wcpCount = (int) wcps.stream().filter(WcpPatternInsight::demonstrated).count();
        int advisorCount = gvReport != null ? gvReport.getSuccessfulAgents() : 0;
        String status = otResult.success() ? "SUCCESS" : "PARTIAL";
        return String.format("[%s] %s: %d goals, %d WCPs demonstrated, %d advisors engaged",
            status, context.name(), goalCount, wcpCount, advisorCount);
    }
}
