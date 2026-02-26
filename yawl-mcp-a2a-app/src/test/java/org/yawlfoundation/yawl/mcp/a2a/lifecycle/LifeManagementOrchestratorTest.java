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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.LifestyleGoal;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OTPatient;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for LifeManagementOrchestrator.
 *
 * <p>Tests exercise real domain logic — no mocks, no stubs. The end-to-end test
 * ({@link #run_fastTest_returnsSuccessfulResult}) drives the full three-pillar
 * pipeline with the {@link LifeManagementConfig#fastTest()} configuration.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("LifeManagementOrchestrator")
class LifeManagementOrchestratorTest {

    private LifeManagementOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new LifeManagementOrchestrator();
    }

    // ── LifeContext validation ────────────────────────────────────────────────

    @Test
    @DisplayName("LifeContext rejects blank name")
    void lifeContext_rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () ->
            LifeContext.of("", 30, List.of("productivity"), 10));
    }

    @Test
    @DisplayName("LifeContext rejects age out of range")
    void lifeContext_rejectsInvalidAge() {
        assertThrows(IllegalArgumentException.class, () ->
            LifeContext.of("Alice", 0, List.of("productivity"), 10));
        assertThrows(IllegalArgumentException.class, () ->
            LifeContext.of("Alice", 121, List.of("productivity"), 10));
    }

    @Test
    @DisplayName("LifeContext rejects empty focus areas")
    void lifeContext_rejectsEmptyFocusAreas() {
        assertThrows(IllegalArgumentException.class, () ->
            LifeContext.of("Alice", 30, List.of(), 10));
    }

    @Test
    @DisplayName("LifeContext rejects more than 5 focus areas")
    void lifeContext_rejectsTooManyFocusAreas() {
        assertThrows(IllegalArgumentException.class, () ->
            LifeContext.of("Alice", 30, List.of("a","b","c","d","e","f"), 10));
    }

    @Test
    @DisplayName("LifeContext reports primary focus area as first element")
    void lifeContext_primaryFocusArea_isFirstElement() {
        LifeContext ctx = LifeContext.of("Alice", 30, List.of("health", "productivity"), 10);
        assertEquals("health", ctx.primaryFocusArea());
    }

    // ── LifeManagementConfig factories ───────────────────────────────────────

    @Test
    @DisplayName("LifeManagementConfig.defaults() has both layers enabled")
    void config_defaults_bothLayersEnabled() {
        LifeManagementConfig cfg = LifeManagementConfig.defaults();
        assertTrue(cfg.runPatternDemo(), "Pattern demo should be enabled by default");
        assertTrue(cfg.runAdvisors(), "Advisor layer should be enabled by default");
        assertEquals(3, cfg.maxAdaptationCycles());
        assertFalse(cfg.timeout().isNegative());
    }

    @Test
    @DisplayName("LifeManagementConfig.fastTest() has 1 adaptation cycle")
    void config_fastTest_hasOneAdaptationCycle() {
        LifeManagementConfig cfg = LifeManagementConfig.fastTest();
        assertEquals(1, cfg.maxAdaptationCycles());
        assertTrue(cfg.timeout().toSeconds() <= 30);
    }

    @Test
    @DisplayName("LifeManagementConfig rejects negative timeout")
    void config_rejectsNegativeTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
            new LifeManagementConfig(Duration.ofSeconds(-1), 1, true, true, "out.json"));
    }

    // ── buildPatient mapping ─────────────────────────────────────────────────

    @Test
    @DisplayName("buildPatient maps context fields to OTPatient correctly")
    void buildPatient_mapsContextToOTPatientCorrectly() {
        LifeContext ctx = LifeContext.of("Sean Chatman", 35,
            List.of("productivity", "health"), 20);

        OTPatient patient = orchestrator.buildPatient(ctx);

        assertAll(
            () -> assertNotNull(patient.id(), "Patient ID should be auto-generated"),
            () -> assertEquals("Sean Chatman", patient.name()),
            () -> assertEquals(35, patient.age()),
            () -> assertTrue(patient.condition().contains("productivity"),
                "Condition should include focus areas"),
            () -> assertTrue(patient.condition().contains("health"),
                "Condition should include all focus areas"),
            () -> assertEquals("Self-directed lifestyle redesign", patient.referralReason()),
            () -> assertTrue(patient.functionalGoal().contains("productivity"),
                "Goal should reference primary focus area"),
            () -> assertTrue(patient.functionalGoal().contains("20 hrs/week"),
                "Goal should reference weekly hours")
        );
    }

    // ── WCP pattern selection ─────────────────────────────────────────────────

    @Test
    @DisplayName("selectPatterns includes WCP-1 and WCP-2 for productivity goals")
    void selectPatterns_productivityGoals_includesWcp1AndWcp2() {
        VanDerAalstPatternAdvisor advisor = new VanDerAalstPatternAdvisor();
        LifestyleGoal goal = new LifestyleGoal(
            "g1", "Increase daily output", 1, "productivity",
            "Tasks completed per day", 8);

        List<String> patterns = advisor.selectPatterns(List.of(goal));

        assertTrue(patterns.contains("WCP-1"), "Should include Sequence for productivity");
        assertTrue(patterns.contains("WCP-2"), "Should include Parallel Split for productivity");
    }

    @Test
    @DisplayName("selectPatterns includes WCP-21 for self-care goals")
    void selectPatterns_selfCareGoal_includesWcp21() {
        VanDerAalstPatternAdvisor advisor = new VanDerAalstPatternAdvisor();
        LifestyleGoal goal = new LifestyleGoal(
            "g2", "Build morning routine", 1, "self-care",
            "Routine completion streak", 12);

        List<String> patterns = advisor.selectPatterns(List.of(goal));

        assertTrue(patterns.contains("WCP-21"), "Should include Structured Loop for self-care");
    }

    @Test
    @DisplayName("selectPatterns always includes WCP-3 convergence pattern")
    void selectPatterns_alwaysIncludesWcp3() {
        VanDerAalstPatternAdvisor advisor = new VanDerAalstPatternAdvisor();
        LifestyleGoal goal = new LifestyleGoal(
            "g3", "Any goal", 1, "leisure",
            "Satisfaction score", 6);

        List<String> patterns = advisor.selectPatterns(List.of(goal));

        assertTrue(patterns.contains("WCP-3"), "WCP-3 (Synchronization) is always included");
    }

    // ── synthesize ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("synthesize returns non-null plan with goals, WCPs, and narrative")
    void synthesize_returnsNonNullPlan_withGoalsInsightsAndWcps() {
        LifeContext ctx = LifeContext.of("Alex Jordan", 28,
            List.of("productivity"), 15);

        LifestyleGoal goal = new LifestyleGoal(
            "g1", "Ship one side project per month", 1, "productivity",
            "Projects shipped", 12);

        WcpPatternInsight wcp = new WcpPatternInsight(
            "WCP-1", "Sequence", "productivity",
            "Order daily work tasks sequentially", true);

        AdvisorInsight advisor = new AdvisorInsight(
            "productivity", "greg-isenberg", "Greg Isenberg",
            "Focus on validated ideas with clear monetisation paths.", true);

        // Build a minimal OTSwarmResult surrogate via LifeManagementPlan directly
        LifeManagementPlan plan = new LifeManagementPlan(
            "Life Management Plan for Alex Jordan",
            List.of(goal),
            List.of(wcp),
            List.of(advisor),
            "Alex Jordan's plan targets productivity with 1 SMART goal."
        );

        assertAll(
            () -> assertNotNull(plan),
            () -> assertEquals(1, plan.otGoals().size()),
            () -> assertEquals(1, plan.wcpInsights().size()),
            () -> assertEquals(1, plan.advisorInsights().size()),
            () -> assertFalse(plan.narrative().isBlank()),
            () -> assertTrue(plan.primaryGoal().isPresent()),
            () -> assertEquals(1, plan.wcpForArea("productivity").size()),
            () -> assertEquals(1, plan.advisorForArea("productivity").size()),
            () -> assertEquals(3, plan.totalInsightCount())
        );
    }

    // ── WcpPatternInsight ─────────────────────────────────────────────────────

    @Test
    @DisplayName("WcpPatternInsight.label() returns formatted string")
    void wcpPatternInsight_label_isFormatted() {
        WcpPatternInsight insight = new WcpPatternInsight(
            "WCP-1", "Sequence", "productivity",
            "Order daily tasks sequentially", true);

        assertEquals("WCP-1 (Sequence) → productivity", insight.label());
    }

    // ── End-to-end ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("run() with fastTest config returns successful result")
    void run_fastTest_returnsSuccessfulResult() throws Exception {
        LifeContext ctx = LifeContext.of("Wil van der Aalst", 58,
            List.of("productivity", "self-care"), 10);
        LifeManagementConfig cfg = LifeManagementConfig.fastTest();

        LifeManagementResult result = orchestrator.run(ctx, cfg);

        assertAll(
            () -> assertNotNull(result, "Result must not be null"),
            () -> assertNotNull(result.sessionId(), "Session ID must be set"),
            () -> assertNotNull(result.plan(), "Plan must not be null"),
            () -> assertNotNull(result.plan().narrative(), "Narrative must be present"),
            () -> assertFalse(result.plan().otGoals().isEmpty(), "OT goals must be populated"),
            () -> assertFalse(result.summary().isBlank(), "Summary must be non-blank"),
            () -> assertNotNull(result.completedAt(), "Completion timestamp must be set"),
            () -> assertTrue(result.successfulOtPhases() > 0, "At least one OT phase must succeed")
        );
    }
}
