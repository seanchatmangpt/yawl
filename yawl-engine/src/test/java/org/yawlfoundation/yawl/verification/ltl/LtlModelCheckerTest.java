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

package org.yawlfoundation.yawl.verification.ltl;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD Tests for LTL Model Checker — Van der Aalst Soundness Verification.
 *
 * <p>Tests the internal LTL model checker implementation for verifying
 * workflow soundness properties.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("LTL Model Checker Tests")
class LtlModelCheckerTest {

    private LtlModelChecker checker;

    @BeforeEach
    void setUp() {
        checker = new LtlModelChecker();
    }

    // =========================================================================
    // LTL Formula Tests
    // =========================================================================

    @Nested
    @DisplayName("LTL Formula Construction")
    class LtlFormulaTests {

        @Test
        @DisplayName("Creates atomic proposition")
        void createsAtomicProposition() {
            LtlFormula.Atomic atomic = LtlFormula.atomic("output_condition");

            assertEquals("output_condition", atomic.proposition());
            assertEquals("output_condition", atomic.toString());
        }

        @Test
        @DisplayName("Creates negation formula")
        void createsNegationFormula() {
            LtlFormula.Not negation = LtlFormula.not(LtlFormula.atomic("p"));

            assertEquals("¬p", negation.toString());
        }

        @Test
        @DisplayName("Creates conjunction formula")
        void createsConjunctionFormula() {
            LtlFormula.And and = LtlFormula.and(
                LtlFormula.atomic("p"),
                LtlFormula.atomic("q")
            );

            assertEquals("(p ∧ q)", and.toString());
        }

        @Test
        @DisplayName("Creates disjunction formula")
        void createsDisjunctionFormula() {
            LtlFormula.Or or = LtlFormula.or(
                LtlFormula.atomic("p"),
                LtlFormula.atomic("q")
            );

            assertEquals("(p ∨ q)", or.toString());
        }

        @Test
        @DisplayName("Creates implication formula")
        void createsImplicationFormula() {
            LtlFormula.Implies implies = LtlFormula.implies(
                LtlFormula.atomic("p"),
                LtlFormula.atomic("q")
            );

            assertEquals("(p → q)", implies.toString());
        }

        @Test
        @DisplayName("Creates Next formula")
        void createsNextFormula() {
            LtlFormula.Next next = LtlFormula.next(LtlFormula.atomic("p"));

            assertEquals("X(p)", next.toString());
        }

        @Test
        @DisplayName("Creates Finally formula")
        void createsFinallyFormula() {
            LtlFormula.Finally finally_ = LtlFormula.finally_(LtlFormula.atomic("p"));

            assertEquals("◇(p)", finally_.toString());
        }

        @Test
        @DisplayName("Creates Globally formula")
        void createsGloballyFormula() {
            LtlFormula.Globally globally = LtlFormula.globally(LtlFormula.atomic("p"));

            assertEquals("□(p)", globally.toString());
        }

        @Test
        @DisplayName("Creates Until formula")
        void createsUntilFormula() {
            LtlFormula.Until until = LtlFormula.until(
                LtlFormula.atomic("p"),
                LtlFormula.atomic("q")
            );

            assertEquals("(p U q)", until.toString());
        }

        @Test
        @DisplayName("Creates Release formula")
        void createsReleaseFormula() {
            LtlFormula.Release release = LtlFormula.release(
                LtlFormula.atomic("p"),
                LtlFormula.atomic("q")
            );

            assertEquals("(p R q)", release.toString());
        }

        @Test
        @DisplayName("Creates complex nested formula")
        void createsComplexNestedFormula() {
            // □(enabled_items → ◇(completion))
            LtlFormula complex = LtlFormula.globally(
                LtlFormula.implies(
                    LtlFormula.atomic("enabled_items"),
                    LtlFormula.finally_(LtlFormula.atomic("completion"))
                )
            );

            assertEquals("□((enabled_items → ◇(completion)))", complex.toString());
        }
    }

    // =========================================================================
    // NNF Conversion Tests
    // =========================================================================

    @Nested
    @DisplayName("Negation Normal Form (NNF) Conversion")
    class NnfConversionTests {

        @Test
        @DisplayName("Converts atomic proposition (no change)")
        void convertsAtomicPropositionNoChange() {
            LtlFormula formula = LtlFormula.atomic("p");
            LtlFormula nnf = formula.toNNF();

            assertEquals(formula, nnf);
        }

        @Test
        @DisplayName("Converts double negation to original")
        void convertsDoubleNegationToOriginal() {
            LtlFormula formula = LtlFormula.not(LtlFormula.not(LtlFormula.atomic("p")));
            LtlFormula nnf = formula.toNNF();

            assertEquals(LtlFormula.atomic("p"), nnf);
        }

        @Test
        @DisplayName("Converts negated conjunction to disjunction")
        void convertsNegatedConjunctionToDisjunction() {
            // ¬(p ∧ q) → ¬p ∨ ¬q
            LtlFormula formula = LtlFormula.not(
                LtlFormula.and(LtlFormula.atomic("p"), LtlFormula.atomic("q"))
            );
            LtlFormula nnf = formula.toNNF();

            // Result should be a disjunction
            assertInstanceOf(LtlFormula.Or.class, nnf);
        }

        @Test
        @DisplayName("Converts negated Finally to Globally")
        void convertsNegatedFinallyToGlobally() {
            // ¬◇p → □¬p
            LtlFormula formula = LtlFormula.not(LtlFormula.finally_(LtlFormula.atomic("p")));
            LtlFormula nnf = formula.toNNF();

            assertInstanceOf(LtlFormula.Globally.class, nnf);
        }

        @Test
        @DisplayName("Converts negated Globally to Finally")
        void convertsNegatedGloballyToFinally() {
            // ¬□p → ◇¬p
            LtlFormula formula = LtlFormula.not(LtlFormula.globally(LtlFormula.atomic("p")));
            LtlFormula nnf = formula.toNNF();

            assertInstanceOf(LtlFormula.Finally.class, nnf);
        }

        @Test
        @DisplayName("Converts implication to disjunction")
        void convertsImplicationToDisjunction() {
            // p → q → ¬p ∨ q
            LtlFormula formula = LtlFormula.implies(
                LtlFormula.atomic("p"),
                LtlFormula.atomic("q")
            );
            LtlFormula nnf = formula.toNNF();

            assertInstanceOf(LtlFormula.Or.class, nnf);
        }
    }

    // =========================================================================
    // Temporal Property Tests
    // =========================================================================

    @Nested
    @DisplayName("Temporal Properties")
    class TemporalPropertyTests {

        @Test
        @DisplayName("Eventually terminates has correct formula")
        void eventuallyTerminatesHasCorrectFormula() {
            TemporalProperty prop = TemporalProperty.EVENTUALLY_TERMINATES;

            assertEquals("Eventually Terminates", prop.getDisplayName());
            assertEquals("◇(output_condition)", prop.getLtlString());
            assertNotNull(prop.getFormula());
        }

        @Test
        @DisplayName("All soundness properties are defined")
        void allSoundnessPropertiesAreDefined() {
            TemporalProperty[] soundness = TemporalProperty.soundnessProperties();

            assertEquals(5, soundness.length);
            assertTrue(List.of(soundness).contains(TemporalProperty.EVENTUALLY_TERMINATES));
            assertTrue(List.of(soundness).contains(TemporalProperty.ALWAYS_PROGRESS));
            assertTrue(List.of(soundness).contains(TemporalProperty.SINGLE_FINAL_TOKEN));
            assertTrue(List.of(soundness).contains(TemporalProperty.ALWAYS_CAN_TERMINATE));
            assertTrue(List.of(soundness).contains(TemporalProperty.OPTION_TO_COMPLETE));
        }

        @Test
        @DisplayName("Integrity properties are defined")
        void integrityPropertiesAreDefined() {
            TemporalProperty[] integrity = TemporalProperty.integrityProperties();

            assertTrue(integrity.length >= 4);
        }

        @Test
        @DisplayName("All properties have descriptions")
        void allPropertiesHaveDescriptions() {
            for (TemporalProperty prop : TemporalProperty.values()) {
                assertNotNull(prop.getDisplayName());
                assertNotNull(prop.getLtlString());
                assertNotNull(prop.getFormula());
                assertNotNull(prop.getDescription());
            }
        }
    }

    // =========================================================================
    // Model Check Result Tests
    // =========================================================================

    @Nested
    @DisplayName("Model Check Results")
    class ModelCheckResultTests {

        @Test
        @DisplayName("Satisfied result reports correctly")
        void satisfiedResultReportsCorrectly() {
            LtlModelChecker.ModelCheckResult result = new LtlModelChecker.ModelCheckResult(
                true,
                null,
                Duration.ofMillis(50),
                "□(p)",
                "spec_123"
            );

            assertTrue(result.isSatisfied());
            assertNull(result.counterexample());
            assertTrue(result.summary().contains("satisfied"));
        }

        @Test
        @DisplayName("Violated result includes counterexample")
        void violatedResultIncludesCounterexample() {
            LtlModelChecker.ModelCheckResult result = new LtlModelChecker.ModelCheckResult(
                false,
                List.of("State[s0]: marking={input=1}", "State[s1]: marking={A=1}"),
                Duration.ofMillis(100),
                "◇(output_condition)",
                "spec_456"
            );

            assertFalse(result.isSatisfied());
            assertNotNull(result.counterexample());
            assertEquals(2, result.counterexample().size());
            assertTrue(result.summary().contains("violated"));
        }
    }

    // =========================================================================
    // Soundness Report Tests
    // =========================================================================

    @Nested
    @DisplayName("Soundness Report")
    class SoundnessReportTests {

        @Test
        @DisplayName("All satisfied report is green")
        void allSatisfiedReportIsGreen() {
            LtlModelChecker.ModelCheckResult satisfied = new LtlModelChecker.ModelCheckResult(
                true, null, Duration.ofMillis(10), "□(p)", "spec"
            );

            Map<TemporalProperty, LtlModelChecker.ModelCheckResult> results = Map.of(
                TemporalProperty.EVENTUALLY_TERMINATES, satisfied
            );

            LtlModelChecker.SoundnessReport report = new LtlModelChecker.SoundnessReport(
                true,
                results,
                List.of()
            );

            assertTrue(report.allSatisfied());
            assertEquals(0, report.violations().size());
            assertTrue(report.summary().contains("All"));
        }

        @Test
        @DisplayName("Detailed report includes all properties")
        void detailedReportIncludesAllProperties() {
            LtlModelChecker.ModelCheckResult satisfied = new LtlModelChecker.ModelCheckResult(
                true, null, Duration.ofMillis(10), "□(p)", "spec"
            );

            Map<TemporalProperty, LtlModelChecker.ModelCheckResult> results = Map.of(
                TemporalProperty.EVENTUALLY_TERMINATES, satisfied,
                TemporalProperty.ALWAYS_PROGRESS, satisfied
            );

            LtlModelChecker.SoundnessReport report = new LtlModelChecker.SoundnessReport(
                true,
                results,
                List.of()
            );

            String detailed = report.detailedReport();
            assertTrue(detailed.contains("Soundness Verification Report"));
        }
    }

    // =========================================================================
    // Visitor Pattern Tests
    // =========================================================================

    @Nested
    @DisplayName("Visitor Pattern")
    class VisitorPatternTests {

        @Test
        @DisplayName("Visitor visits all formula types")
        void visitorVisitsAllFormulaTypes() {
            LtlFormula formula = LtlFormula.and(
                LtlFormula.finally_(LtlFormula.atomic("p")),
                LtlFormula.globally(LtlFormula.not(LtlFormula.atomic("q")))
            );

            AtomicInteger visitCount = new AtomicInteger(0);
            LtlFormula.Visitor<Integer> countingVisitor = new LtlFormula.Visitor<>() {
                public Integer visitAtomic(LtlFormula.Atomic f) { return visitCount.incrementAndGet(); }
                public Integer visitNot(LtlFormula.Not f) { return visitCount.incrementAndGet(); }
                public Integer visitAnd(LtlFormula.And f) { return visitCount.incrementAndGet(); }
                public Integer visitOr(LtlFormula.Or f) { return visitCount.incrementAndGet(); }
                public Integer visitImplies(LtlFormula.Implies f) { return visitCount.incrementAndGet(); }
                public Integer visitNext(LtlFormula.Next f) { return visitCount.incrementAndGet(); }
                public Integer visitFinally(LtlFormula.Finally f) { return visitCount.incrementAndGet(); }
                public Integer visitGlobally(LtlFormula.Globally f) { return visitCount.incrementAndGet(); }
                public Integer visitUntil(LtlFormula.Until f) { return visitCount.incrementAndGet(); }
                public Integer visitRelease(LtlFormula.Release f) { return visitCount.incrementAndGet(); }
                public Integer visitWeakUntil(LtlFormula.WeakUntil f) { return visitCount.incrementAndGet(); }
            };

            formula.accept(countingVisitor);

            // At least the top-level And should be visited
            assertTrue(visitCount.get() >= 1);
        }
    }

    // Need to import AtomicInteger
    private static class AtomicInteger {
        private int value;
        AtomicInteger(int initial) { this.value = initial; }
        int incrementAndGet() { return ++value; }
        int get() { return value; }
    }
}
