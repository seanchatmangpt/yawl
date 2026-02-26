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

import org.yawlfoundation.yawl.mcp.a2a.demo.PatternDemoRunner;
import org.yawlfoundation.yawl.mcp.a2a.demo.config.DemoConfig;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.LifestyleGoal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;

/**
 * Selects and demonstrates Van der Aalst Workflow Control-flow Patterns (WCPs)
 * relevant to an occupational therapy lifestyle redesign.
 *
 * <p>Maps each OT goal area to the WCPs that best describe the control-flow
 * structure for executing that area of the person's life plan, then runs
 * {@link PatternDemoRunner} in self-play mode to validate the patterns against
 * the YAWL engine.</p>
 *
 * <h2>WCP ↔ Life Area Mapping</h2>
 * <ul>
 *   <li>{@code productivity} — WCP-1 (Sequence), WCP-2 (Parallel Split),
 *       WCP-4 (Exclusive Choice)</li>
 *   <li>{@code self-care} — WCP-1 (Sequence), WCP-3 (Synchronization),
 *       WCP-21 (Structured Loop)</li>
 *   <li>{@code leisure} — WCP-4 (Exclusive Choice), WCP-11 (Implicit Termination)</li>
 *   <li>(always included) — WCP-3 (Synchronization), to converge all goal streams</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class VanDerAalstPatternAdvisor {

    /** Human-readable names for each WCP used in life management. */
    private static final Map<String, String> PATTERN_NAMES = Map.of(
        "WCP-1",  "Sequence",
        "WCP-2",  "Parallel Split",
        "WCP-3",  "Synchronization",
        "WCP-4",  "Exclusive Choice",
        "WCP-11", "Implicit Termination",
        "WCP-21", "Structured Loop"
    );

    /**
     * Applicability descriptions: outer key = patternId, inner key = goalArea.
     * Each combination describes how that WCP applies to that life area.
     */
    private static final Map<String, Map<String, String>> APPLICABILITY = Map.of(
        "WCP-1", Map.of(
            "productivity", "Order daily work tasks sequentially: plan → execute → review",
            "self-care",    "Structure self-care routines in sequence: morning → movement → rest",
            "leisure",      "Chain leisure activities in a purposeful order"
        ),
        "WCP-2", Map.of(
            "productivity", "Run productivity streams in parallel (e.g. deep work + admin)",
            "self-care",    "Execute self-care habits concurrently where possible",
            "leisure",      "Pursue multiple leisure interests simultaneously"
        ),
        "WCP-3", Map.of(
            "productivity", "Synchronise all goal streams before weekly review",
            "self-care",    "Converge self-care outcomes before progress assessment",
            "leisure",      "Combine leisure outcomes for holistic wellbeing evaluation"
        ),
        "WCP-4", Map.of(
            "productivity", "Choose between focus modes (deep work vs collaboration) based on energy",
            "self-care",    "Select self-care intervention based on symptom profile",
            "leisure",      "Branch to active or restorative leisure based on energy level"
        ),
        "WCP-11", Map.of(
            "productivity", "Complete productivity goal when all sub-tasks finish naturally",
            "self-care",    "Conclude self-care phase on reaching target score",
            "leisure",      "End leisure activity organically when satisfaction is reached"
        ),
        "WCP-21", Map.of(
            "productivity", "Repeat productivity sprints until target output achieved",
            "self-care",    "Loop self-care habits until OT progress threshold met",
            "leisure",      "Cycle through leisure experiences to build repertoire"
        )
    );

    /** WCP IDs assigned to each OT goal area. */
    private static final Map<String, List<String>> AREA_TO_PATTERNS = Map.of(
        "productivity", List.of("WCP-1", "WCP-2", "WCP-4"),
        "self-care",    List.of("WCP-1", "WCP-3", "WCP-21"),
        "leisure",      List.of("WCP-4", "WCP-11")
    );

    /** Always-included convergence pattern. */
    private static final String CONVERGENCE_PATTERN = "WCP-3";

    /**
     * Selects deduplicated WCP IDs relevant to the given lifestyle goals.
     *
     * <p>WCP-3 (Synchronization) is always included as the convergence anchor.
     * Goal areas not found in the static map default to the productivity patterns.</p>
     *
     * @param goals OT lifestyle goals produced by the swarm (non-null, non-empty)
     * @return ordered deduplicated list of WCP IDs
     */
    List<String> selectPatterns(List<LifestyleGoal> goals) {
        SequencedSet<String> selected = new LinkedHashSet<>();
        selected.add(CONVERGENCE_PATTERN);

        for (LifestyleGoal goal : goals) {
            String area = normaliseArea(goal.targetArea());
            List<String> patterns = AREA_TO_PATTERNS.getOrDefault(area,
                AREA_TO_PATTERNS.get("productivity"));
            selected.addAll(patterns);
        }

        return List.copyOf(selected);
    }

    /**
     * Runs the van der Aalst WCP demo for the goal-relevant patterns and returns
     * structured insights with applicability annotations.
     *
     * <p>Uses {@link PatternDemoRunner} in self-play mode (parallel execution,
     * auto-complete enabled, metrics on, no Z.AI commentary). The
     * {@code demonstrated} flag on each insight reflects whether the demo
     * runner exited cleanly (exit code 0).</p>
     *
     * @param goals   OT lifestyle goals (determines which patterns to select)
     * @param timeout execution timeout forwarded to the demo runner
     * @return list of WCP insights, one per unique (patternId, goalArea) pair
     */
    public List<WcpPatternInsight> run(List<LifestyleGoal> goals, Duration timeout) {
        if (goals == null || goals.isEmpty()) {
            throw new IllegalArgumentException("Goals must be non-null and non-empty");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }

        List<String> patternIds = selectPatterns(goals);

        DemoConfig demoConfig = DemoConfig.builder()
            .outputFormat(DemoConfig.OutputFormat.CONSOLE)
            .parallelExecution(true)
            .autoComplete(true)
            .enableMetrics(true)
            .withCommentary(false)
            .tokenAnalysis(false)
            .timeoutSeconds((int) timeout.toSeconds())
            .patternIds(patternIds)
            .build();

        int exitCode = new PatternDemoRunner(demoConfig).run();
        boolean allDemonstrated = (exitCode == 0);

        return buildInsights(patternIds, goals, allDemonstrated);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private List<WcpPatternInsight> buildInsights(List<String> patternIds,
                                                  List<LifestyleGoal> goals,
                                                  boolean demonstrated) {
        List<WcpPatternInsight> insights = new ArrayList<>();

        for (String patternId : patternIds) {
            String patternName = PATTERN_NAMES.getOrDefault(patternId, patternId);
            Map<String, String> applicabilityByArea = APPLICABILITY.get(patternId);

            // Emit one insight per goal area that uses this pattern
            for (LifestyleGoal goal : goals) {
                String area = normaliseArea(goal.targetArea());
                List<String> patternsForArea = AREA_TO_PATTERNS.getOrDefault(area,
                    AREA_TO_PATTERNS.get("productivity"));

                if (patternsForArea.contains(patternId) || patternId.equals(CONVERGENCE_PATTERN)) {
                    String applicability = resolveApplicability(applicabilityByArea, area);
                    // Deduplicate: skip if same patternId+area already added
                    boolean alreadyAdded = insights.stream()
                        .anyMatch(i -> i.patternId().equals(patternId)
                                    && i.goalArea().equalsIgnoreCase(area));
                    if (!alreadyAdded) {
                        insights.add(new WcpPatternInsight(
                            patternId, patternName, area, applicability, demonstrated));
                    }
                }
            }
        }

        return List.copyOf(insights);
    }

    private String resolveApplicability(Map<String, String> applicabilityByArea, String area) {
        if (applicabilityByArea == null) {
            return "Applies workflow control-flow structure to " + area;
        }
        return applicabilityByArea.getOrDefault(area,
            applicabilityByArea.getOrDefault("productivity",
                "Applies workflow control-flow structure to " + area));
    }

    private String normaliseArea(String targetArea) {
        if (targetArea == null) return "productivity";
        String lower = targetArea.toLowerCase();
        // Map common synonyms to canonical OT areas
        if (lower.contains("health") || lower.contains("care") || lower.contains("self")) {
            return "self-care";
        }
        if (lower.contains("leisure") || lower.contains("hobby") || lower.contains("fun")) {
            return "leisure";
        }
        return "productivity";
    }
}
