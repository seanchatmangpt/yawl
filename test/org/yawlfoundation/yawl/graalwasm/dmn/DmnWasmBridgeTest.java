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

package org.yawlfoundation.yawl.graalwasm.dmn;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago-style TDD tests for DmnWasmBridge.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>DMN model parsing (DMN 1.3 namespace)</li>
 *   <li>All seven hit policies</li>
 *   <li>FEEL unary tests: strings, numerics, ranges, negation, OR-lists, wildcards</li>
 *   <li>FEEL numeric operations via WASM</li>
 *   <li>Decision Requirements Graph (DRG)</li>
 *   <li>Error conditions</li>
 * </ul>
 */
class DmnWasmBridgeTest {

    private DmnWasmBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new DmnWasmBridge();
    }

    @AfterEach
    void tearDown() {
        bridge.close();
    }

    // ── Model parsing ────────────────────────────────────────────────────────

    @Nested
    class ModelParsing {

        @Test
        void parseDmnModel_simpleModel_returnsModel() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(DISH_DMN);

            assertThat(model, notNullValue());
            assertThat(model.id(), equalTo("dish-decision"));
            assertThat(model.name(), equalTo("Dish Decision"));
            assertThat(model.decisions(), hasKey("dish"));
        }

        @Test
        void parseDmnModel_dmn12Namespace_parsesSuccessfully() {
            String dmn12 = DISH_DMN.replace(
                    "https://www.omg.org/spec/DMN/20191111/MODEL/",
                    "http://www.omg.org/spec/DMN/20180521/MODEL/");

            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(dmn12);
            assertThat(model.decisions(), hasKey("dish"));
        }

        @Test
        void parseDmnModel_nullXml_throwsModelParseError() {
            DmnException ex = assertThrows(DmnException.class,
                    () -> bridge.parseDmnModel(null));
            assertThat(ex.getErrorKind(), equalTo(DmnException.ErrorKind.MODEL_PARSE_ERROR));
        }

        @Test
        void parseDmnModel_blankXml_throwsModelParseError() {
            DmnException ex = assertThrows(DmnException.class,
                    () -> bridge.parseDmnModel("   "));
            assertThat(ex.getErrorKind(), equalTo(DmnException.ErrorKind.MODEL_PARSE_ERROR));
        }

        @Test
        void parseDmnModel_malformedXml_throwsModelParseError() {
            DmnException ex = assertThrows(DmnException.class,
                    () -> bridge.parseDmnModel("<not valid xml>><><"));
            assertThat(ex.getErrorKind(), equalTo(DmnException.ErrorKind.MODEL_PARSE_ERROR));
        }

        @Test
        void parseDmnModel_multipleDecisions_allParsed() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(ELIGIBILITY_DMN_WITH_DRG);
            assertThat(model.decisions().size(), greaterThanOrEqualTo(2));
            assertThat(model.decisionIds(), hasItems("creditScore", "eligibility"));
        }
    }

    // ── Hit policy: UNIQUE ───────────────────────────────────────────────────

    @Nested
    class HitPolicyUnique {

        @Test
        void unique_singleMatch_returnsResult() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(DISH_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.of("season", "Fall");

            DmnDecisionResult result = bridge.evaluateDecision(model, "dish", ctx);

            assertThat(result.hasResult(), is(true));
            assertThat(result.matchCount(), equalTo(1));
            assertThat(result.getSingleOutputValue("dish"), equalTo("Pork Ribs"));
        }

        @Test
        void unique_noMatch_returnsEmpty() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(DISH_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.of("season", "Monsoon");

            DmnDecisionResult result = bridge.evaluateDecision(model, "dish", ctx);

            assertThat(result.hasResult(), is(false));
            assertThat(result.getSingleResult(), equalTo(Optional.empty()));
        }

        @Test
        void unique_multipleMatches_throwsHitPolicyViolation() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(UNIQUE_VIOLATION_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.of("category", "A");

            DmnException ex = assertThrows(DmnException.class,
                    () -> bridge.evaluateDecision(model, "decision", ctx));
            assertThat(ex.getErrorKind(), equalTo(DmnException.ErrorKind.HIT_POLICY_VIOLATION));
        }

        @Test
        void unique_wildcardRule_alwaysMatches() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(WILDCARD_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.of("value", "anything");

            DmnDecisionResult result = bridge.evaluateDecision(model, "decision", ctx);

            assertThat(result.hasResult(), is(true));
            assertThat(result.getSingleOutputValue("result"), equalTo("default"));
        }
    }

    // ── Hit policy: FIRST ────────────────────────────────────────────────────

    @Nested
    class HitPolicyFirst {

        @Test
        void first_multipleMatchingRules_returnsFirstMatch() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(FIRST_HIT_POLICY_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.of("score", 75);

            DmnDecisionResult result = bridge.evaluateDecision(model, "grade", ctx);

            assertThat(result.hasResult(), is(true));
            assertThat(result.getHitPolicy(), equalTo(DmnHitPolicy.FIRST));
            assertThat(result.matchCount(), equalTo(1));
            assertThat(result.getSingleOutputValue("grade"), equalTo("B"));
        }

        @Test
        void first_noMatch_returnsEmpty() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(FIRST_HIT_POLICY_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.of("score", 200);

            DmnDecisionResult result = bridge.evaluateDecision(model, "grade", ctx);
            assertThat(result.hasResult(), is(false));
        }
    }

    // ── Hit policy: COLLECT ──────────────────────────────────────────────────

    @Nested
    class HitPolicyCollect {

        @Test
        void collect_multipleMatches_returnsAllResults() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(COLLECT_HIT_POLICY_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.of("category", "premium");

            DmnDecisionResult result = bridge.evaluateDecision(model, "benefits", ctx);

            assertThat(result.hasResult(), is(true));
            assertThat(result.getHitPolicy(), equalTo(DmnHitPolicy.COLLECT));
            assertThat(result.matchCount(), equalTo(2));
        }

        @Test
        void collect_collectOutputValues_returnsAllOutputsForColumn() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(COLLECT_HIT_POLICY_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.of("category", "premium");

            DmnDecisionResult result = bridge.evaluateDecision(model, "benefits", ctx);
            List<Object> benefits = result.collectOutputValues("benefit");

            assertThat(benefits, hasItems("Free Shipping", "Extended Warranty"));
        }
    }

    // ── Hit policy: ANY ──────────────────────────────────────────────────────

    @Nested
    class HitPolicyAny {

        @Test
        void any_allMatchesSameOutput_succeeds() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(ANY_HIT_POLICY_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.of("status", "approved");

            DmnDecisionResult result = bridge.evaluateDecision(model, "decision", ctx);

            assertThat(result.hasResult(), is(true));
            assertThat(result.getHitPolicy(), equalTo(DmnHitPolicy.ANY));
        }
    }

    // ── Hit policy: RULE ORDER ───────────────────────────────────────────────

    @Nested
    class HitPolicyRuleOrder {

        @Test
        void ruleOrder_multipleMatches_returnsAllInOrder() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(RULE_ORDER_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.of("score", 85);

            DmnDecisionResult result = bridge.evaluateDecision(model, "actions", ctx);

            assertThat(result.hasResult(), is(true));
            assertThat(result.getHitPolicy(), equalTo(DmnHitPolicy.RULE_ORDER));
            assertThat(result.matchCount(), greaterThanOrEqualTo(1));
        }
    }

    // ── FEEL expression evaluation ───────────────────────────────────────────

    @Nested
    class FeelExpressions {

        @Test
        void feelMatches_wildcardDash_matchesAnyString() {
            assertThat(bridge.feelMatches("-", "anything"), is(true));
            assertThat(bridge.feelMatches("-", null), is(true));
            assertThat(bridge.feelMatches("-", 42), is(true));
        }

        @Test
        void feelMatches_stringLiteral_matchesExactString() {
            assertThat(bridge.feelMatches("\"Spring\"", "Spring"), is(true));
            assertThat(bridge.feelMatches("\"Spring\"", "Fall"), is(false));
            assertThat(bridge.feelMatches("\"Spring\"", null), is(false));
        }

        @Test
        void feelMatches_numericComparison_ge() {
            assertThat(bridge.feelMatches(">= 18", 18), is(true));
            assertThat(bridge.feelMatches(">= 18", 25), is(true));
            assertThat(bridge.feelMatches(">= 18", 17), is(false));
        }

        @Test
        void feelMatches_numericComparison_lt() {
            assertThat(bridge.feelMatches("< 100", 99), is(true));
            assertThat(bridge.feelMatches("< 100", 100), is(false));
        }

        @Test
        void feelMatches_numericComparison_eq() {
            assertThat(bridge.feelMatches("= 42", 42), is(true));
            assertThat(bridge.feelMatches("= 42", 43), is(false));
        }

        @Test
        void feelMatches_numericComparison_ne() {
            assertThat(bridge.feelMatches("!= 0", 1), is(true));
            assertThat(bridge.feelMatches("!= 0", 0), is(false));
        }

        @Test
        void feelMatches_closedRange_matchesInclusiveBounds() {
            assertThat(bridge.feelMatches("[1..10]", 1), is(true));
            assertThat(bridge.feelMatches("[1..10]", 5), is(true));
            assertThat(bridge.feelMatches("[1..10]", 10), is(true));
            assertThat(bridge.feelMatches("[1..10]", 0), is(false));
            assertThat(bridge.feelMatches("[1..10]", 11), is(false));
        }

        @Test
        void feelMatches_openRange_excludesBounds() {
            assertThat(bridge.feelMatches("(1..10)", 2), is(true));
            assertThat(bridge.feelMatches("(1..10)", 1), is(false));
            assertThat(bridge.feelMatches("(1..10)", 10), is(false));
        }

        @Test
        void feelMatches_halfOpenRange_lowerClosed() {
            assertThat(bridge.feelMatches("[1..10)", 1), is(true));
            assertThat(bridge.feelMatches("[1..10)", 10), is(false));
        }

        @Test
        void feelMatches_halfOpenRange_upperClosed() {
            assertThat(bridge.feelMatches("(1..10]", 1), is(false));
            assertThat(bridge.feelMatches("(1..10]", 10), is(true));
        }

        @Test
        void feelMatches_orList_matchesAnyItem() {
            assertThat(bridge.feelMatches("\"Spring\",\"Summer\"", "Spring"), is(true));
            assertThat(bridge.feelMatches("\"Spring\",\"Summer\"", "Summer"), is(true));
            assertThat(bridge.feelMatches("\"Spring\",\"Summer\"", "Fall"), is(false));
        }

        @Test
        void feelMatches_negation_invertsMatch() {
            assertThat(bridge.feelMatches("not(\"A\")", "B"), is(true));
            assertThat(bridge.feelMatches("not(\"A\")", "A"), is(false));
        }

        @Test
        void feelMatches_booleanTrue() {
            assertThat(bridge.feelMatches("true", Boolean.TRUE), is(true));
            assertThat(bridge.feelMatches("true", Boolean.FALSE), is(false));
        }

        @Test
        void feelMatches_booleanFalse() {
            assertThat(bridge.feelMatches("false", Boolean.FALSE), is(true));
            assertThat(bridge.feelMatches("false", Boolean.TRUE), is(false));
        }

        @Test
        void feelMatches_nullInput_onlyMatchesWildcard() {
            assertThat(bridge.feelMatches("-", null), is(true));
            assertThat(bridge.feelMatches("\"A\"", null), is(false));
            assertThat(bridge.feelMatches(">= 5", null), is(false));
        }
    }

    // ── WASM FEEL numeric operations ─────────────────────────────────────────

    @Nested
    class FeelWasmOps {

        @Test
        void feelAdd_sumsTwoNumbers() {
            assertThat(bridge.evaluateFeelNumericOp("feel_add", 3.5, 2.5), closeTo(6.0, 0.0001));
        }

        @Test
        void feelSubtract_differenceOfTwoNumbers() {
            assertThat(bridge.evaluateFeelNumericOp("feel_subtract", 10.0, 3.0), closeTo(7.0, 0.0001));
        }

        @Test
        void feelMultiply_productOfTwoNumbers() {
            assertThat(bridge.evaluateFeelNumericOp("feel_multiply", 4.0, 2.5), closeTo(10.0, 0.0001));
        }

        @Test
        void feelDivide_quotientOfTwoNumbers() {
            assertThat(bridge.evaluateFeelNumericOp("feel_divide", 10.0, 4.0), closeTo(2.5, 0.0001));
        }

        @Test
        void feelFloor_roundsDown() {
            assertThat(bridge.evaluateFeelUnaryNumericOp("feel_floor", 3.7), closeTo(3.0, 0.0001));
            assertThat(bridge.evaluateFeelUnaryNumericOp("feel_floor", -3.1), closeTo(-4.0, 0.0001));
        }

        @Test
        void feelCeil_roundsUp() {
            assertThat(bridge.evaluateFeelUnaryNumericOp("feel_ceil", 3.1), closeTo(4.0, 0.0001));
            assertThat(bridge.evaluateFeelUnaryNumericOp("feel_ceil", -3.7), closeTo(-3.0, 0.0001));
        }

        @Test
        void feelSqrt_squareRoot() {
            assertThat(bridge.evaluateFeelUnaryNumericOp("feel_sqrt", 9.0), closeTo(3.0, 0.0001));
            assertThat(bridge.evaluateFeelUnaryNumericOp("feel_sqrt", 2.0), closeTo(1.4142, 0.0001));
        }

        @Test
        void feelAbs_absoluteValue() {
            assertThat(bridge.evaluateFeelUnaryNumericOp("feel_abs", -5.0), closeTo(5.0, 0.0001));
            assertThat(bridge.evaluateFeelUnaryNumericOp("feel_abs", 5.0), closeTo(5.0, 0.0001));
        }

        @Test
        void feelMin_minimumOfTwoNumbers() {
            assertThat(bridge.evaluateFeelNumericOp("feel_min", 3.0, 7.0), closeTo(3.0, 0.0001));
            assertThat(bridge.evaluateFeelNumericOp("feel_min", 7.0, 3.0), closeTo(3.0, 0.0001));
        }

        @Test
        void feelMax_maximumOfTwoNumbers() {
            assertThat(bridge.evaluateFeelNumericOp("feel_max", 3.0, 7.0), closeTo(7.0, 0.0001));
        }

        @Test
        void unknownWasmOp_throwsFeelExpressionError() {
            DmnException ex = assertThrows(DmnException.class,
                    () -> bridge.evaluateFeelNumericOp("nonexistent_op", 1.0, 2.0));
            assertThat(ex.getErrorKind(), equalTo(DmnException.ErrorKind.FEEL_EXPRESSION_ERROR));
        }
    }

    // ── Multi-input decision tables ───────────────────────────────────────────

    @Nested
    class MultiInputDecisions {

        @Test
        void eligibilityDecision_bothInputsMatch_returnsApproved() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(ELIGIBILITY_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.builder()
                    .put("age", 35)
                    .put("income", 60000)
                    .build();

            DmnDecisionResult result = bridge.evaluateDecision(model, "eligibility", ctx);

            assertThat(result.hasResult(), is(true));
            assertThat(result.getSingleOutputValue("status"), equalTo("Approved"));
        }

        @Test
        void eligibilityDecision_ageTooLow_returnsDeclined() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(ELIGIBILITY_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.builder()
                    .put("age", 16)
                    .put("income", 60000)
                    .build();

            DmnDecisionResult result = bridge.evaluateDecision(model, "eligibility", ctx);

            assertThat(result.hasResult(), is(true));
            assertThat(result.getSingleOutputValue("status"), equalTo("Declined"));
        }

        @Test
        void eligibilityDecision_incomeTooLow_returnsDeclined() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(ELIGIBILITY_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.builder()
                    .put("age", 35)
                    .put("income", 15000)
                    .build();

            DmnDecisionResult result = bridge.evaluateDecision(model, "eligibility", ctx);

            assertThat(result.hasResult(), is(true));
            assertThat(result.getSingleOutputValue("status"), equalTo("Declined"));
        }
    }

    // ── Decision Requirements Graph ───────────────────────────────────────────

    @Nested
    class DecisionRequirementsGraph {

        @Test
        void drgDecision_resolvesRequiredDecisionOutput() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(ELIGIBILITY_DMN_WITH_DRG);
            DmnEvaluationContext ctx = DmnEvaluationContext.of("creditScore", 750);

            DmnDecisionResult result = bridge.evaluateDecision(model, "eligibility", ctx);

            assertThat(result.hasResult(), is(true));
        }
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Nested
    class ErrorHandling {

        @Test
        void evaluateDecision_unknownDecisionId_throwsDecisionNotFound() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(DISH_DMN);
            DmnEvaluationContext ctx = DmnEvaluationContext.of("season", "Fall");

            DmnException ex = assertThrows(DmnException.class,
                    () -> bridge.evaluateDecision(model, "nonexistent", ctx));
            assertThat(ex.getErrorKind(), equalTo(DmnException.ErrorKind.DECISION_NOT_FOUND));
        }

        @Test
        void closedBridge_throwsOnParseDmnModel() {
            bridge.close();
            DmnException ex = assertThrows(DmnException.class,
                    () -> bridge.parseDmnModel(DISH_DMN));
            assertThat(ex.getErrorKind(), equalTo(DmnException.ErrorKind.EVALUATION_ERROR));
        }

        @Test
        void closedBridge_throwsOnEvaluate() {
            DmnWasmBridge.DmnModel model = bridge.parseDmnModel(DISH_DMN);
            bridge.close();

            DmnException ex = assertThrows(DmnException.class,
                    () -> bridge.evaluateDecision(model, "dish", DmnEvaluationContext.empty()));
            assertThat(ex.getErrorKind(), equalTo(DmnException.ErrorKind.EVALUATION_ERROR));
        }

        @Test
        void closedBridge_closeIsIdempotent() {
            assertDoesNotThrow(() -> {
                bridge.close();
                bridge.close();
            });
        }
    }

    // ── DmnDecisionResult ─────────────────────────────────────────────────────

    @Nested
    class DmnDecisionResultTests {

        @Test
        void singleResult_singleHitPolicy_returnsResult() {
            DmnDecisionResult result = DmnDecisionResult.builder("d1", DmnHitPolicy.UNIQUE)
                    .addMatchedRule(Map.of("output", "value"))
                    .build();

            assertThat(result.getSingleResult().isPresent(), is(true));
            assertThat(result.getSingleResult().get(), hasEntry("output", "value"));
        }

        @Test
        void singleResult_multiHitPolicy_returnsEmpty() {
            DmnDecisionResult result = DmnDecisionResult.builder("d1", DmnHitPolicy.COLLECT)
                    .addMatchedRule(Map.of("output", "v1"))
                    .addMatchedRule(Map.of("output", "v2"))
                    .build();

            assertThat(result.getSingleResult().isPresent(), is(false));
            assertThat(result.matchCount(), equalTo(2));
        }

        @Test
        void collectOutputValues_returnsAllValuesForColumn() {
            DmnDecisionResult result = DmnDecisionResult.builder("d1", DmnHitPolicy.COLLECT)
                    .addMatchedRule(Map.of("item", "A"))
                    .addMatchedRule(Map.of("item", "B"))
                    .addMatchedRule(Map.of("item", "C"))
                    .build();

            assertThat(result.collectOutputValues("item"), contains("A", "B", "C"));
        }

        @Test
        void noResult_hasResultIsFalse() {
            DmnDecisionResult result = DmnDecisionResult.builder("d1", DmnHitPolicy.UNIQUE).build();
            assertThat(result.hasResult(), is(false));
            assertThat(result.matchCount(), equalTo(0));
        }
    }

    // ── DmnEvaluationContext ──────────────────────────────────────────────────

    @Nested
    class DmnEvaluationContextTests {

        @Test
        void builderPut_bindsVariable() {
            DmnEvaluationContext ctx = DmnEvaluationContext.of("x", 42);
            assertThat(ctx.get("x"), equalTo(42));
        }

        @Test
        void empty_returnsEmptyContext() {
            assertThat(DmnEvaluationContext.empty().isEmpty(), is(true));
        }

        @Test
        void contains_returnsTrueForBoundVariable() {
            DmnEvaluationContext ctx = DmnEvaluationContext.of("a", "v");
            assertThat(ctx.contains("a"), is(true));
            assertThat(ctx.contains("b"), is(false));
        }

        @Test
        void putAll_mergesBindings() {
            DmnEvaluationContext ctx = DmnEvaluationContext.builder()
                    .putAll(Map.of("a", 1, "b", 2))
                    .build();
            assertThat(ctx.size(), equalTo(2));
        }

        @Test
        void blankName_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> DmnEvaluationContext.builder().put("", "value"));
        }
    }

    // ── DmnHitPolicy ─────────────────────────────────────────────────────────

    @Nested
    class DmnHitPolicyTests {

        @Test
        void fromDmnValue_canonicalName_resolves() {
            assertThat(DmnHitPolicy.fromDmnValue("UNIQUE"), equalTo(DmnHitPolicy.UNIQUE));
            assertThat(DmnHitPolicy.fromDmnValue("FIRST"), equalTo(DmnHitPolicy.FIRST));
            assertThat(DmnHitPolicy.fromDmnValue("COLLECT"), equalTo(DmnHitPolicy.COLLECT));
            assertThat(DmnHitPolicy.fromDmnValue("RULE ORDER"), equalTo(DmnHitPolicy.RULE_ORDER));
            assertThat(DmnHitPolicy.fromDmnValue("OUTPUT ORDER"), equalTo(DmnHitPolicy.OUTPUT_ORDER));
        }

        @Test
        void fromDmnValue_singleLetterAnnotation_resolves() {
            assertThat(DmnHitPolicy.fromDmnValue("U"), equalTo(DmnHitPolicy.UNIQUE));
            assertThat(DmnHitPolicy.fromDmnValue("F"), equalTo(DmnHitPolicy.FIRST));
            assertThat(DmnHitPolicy.fromDmnValue("C"), equalTo(DmnHitPolicy.COLLECT));
            assertThat(DmnHitPolicy.fromDmnValue("R"), equalTo(DmnHitPolicy.RULE_ORDER));
        }

        @Test
        void fromDmnValue_null_returnsUnique() {
            assertThat(DmnHitPolicy.fromDmnValue(null), equalTo(DmnHitPolicy.UNIQUE));
        }

        @Test
        void fromDmnValue_empty_returnsUnique() {
            assertThat(DmnHitPolicy.fromDmnValue(""), equalTo(DmnHitPolicy.UNIQUE));
        }

        @Test
        void isSingleHit_correctForAllPolicies() {
            assertThat(DmnHitPolicy.UNIQUE.isSingleHit(), is(true));
            assertThat(DmnHitPolicy.FIRST.isSingleHit(), is(true));
            assertThat(DmnHitPolicy.ANY.isSingleHit(), is(true));
            assertThat(DmnHitPolicy.PRIORITY.isSingleHit(), is(true));
            assertThat(DmnHitPolicy.COLLECT.isSingleHit(), is(false));
            assertThat(DmnHitPolicy.RULE_ORDER.isSingleHit(), is(false));
            assertThat(DmnHitPolicy.OUTPUT_ORDER.isSingleHit(), is(false));
        }

        @Test
        void isMultiHit_correctForAllPolicies() {
            assertThat(DmnHitPolicy.COLLECT.isMultiHit(), is(true));
            assertThat(DmnHitPolicy.RULE_ORDER.isMultiHit(), is(true));
            assertThat(DmnHitPolicy.OUTPUT_ORDER.isMultiHit(), is(true));
            assertThat(DmnHitPolicy.UNIQUE.isMultiHit(), is(false));
        }
    }

    // ── DmnException ─────────────────────────────────────────────────────────

    @Nested
    class DmnExceptionTests {

        @Test
        void constructor_setsErrorKind() {
            DmnException ex = new DmnException("msg", DmnException.ErrorKind.MODEL_PARSE_ERROR);
            assertThat(ex.getErrorKind(), equalTo(DmnException.ErrorKind.MODEL_PARSE_ERROR));
            assertThat(ex.getMessage(), equalTo("msg"));
        }

        @Test
        void constructorWithCause_preservesCause() {
            Throwable cause = new RuntimeException("root cause");
            DmnException ex = new DmnException("msg", DmnException.ErrorKind.WASM_LOAD_ERROR, cause);
            assertThat(ex.getCause(), sameInstance(cause));
        }

        @Test
        void allErrorKindsHaveDescriptions() {
            for (DmnException.ErrorKind kind : DmnException.ErrorKind.values()) {
                assertThat(kind.getDescription(), not(emptyOrNullString()));
            }
        }
    }

    // ── DMN XML fixtures ──────────────────────────────────────────────────────

    static final String DISH_DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                         id="dish-decision"
                         name="Dish Decision"
                         namespace="http://camunda.org/schema/1.0/dmn">
              <decision id="dish" name="Dish">
                <decisionTable id="dishTable" hitPolicy="UNIQUE">
                  <input id="input1" label="Season">
                    <inputExpression id="inputExpr1" typeRef="string">
                      <text>season</text>
                    </inputExpression>
                  </input>
                  <output id="output1" label="Dish" name="dish" typeRef="string"/>
                  <rule id="rule1">
                    <inputEntry id="ie1"><text>"Fall"</text></inputEntry>
                    <outputEntry id="oe1"><text>"Pork Ribs"</text></outputEntry>
                  </rule>
                  <rule id="rule2">
                    <inputEntry id="ie2"><text>"Winter"</text></inputEntry>
                    <outputEntry id="oe2"><text>"Roast Goose"</text></outputEntry>
                  </rule>
                  <rule id="rule3">
                    <inputEntry id="ie3"><text>"Spring"</text></inputEntry>
                    <outputEntry id="oe3"><text>"Dry Aged Beef"</text></outputEntry>
                  </rule>
                  <rule id="rule4">
                    <inputEntry id="ie4"><text>"Summer"</text></inputEntry>
                    <outputEntry id="oe4"><text>"Light Salad"</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    static final String ELIGIBILITY_DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                         id="eligibility"
                         name="Eligibility"
                         namespace="http://example.org/dmn">
              <decision id="eligibility" name="Eligibility">
                <decisionTable id="eligibilityTable" hitPolicy="FIRST">
                  <input id="input1" label="Age">
                    <inputExpression id="inputExpr1" typeRef="integer">
                      <text>age</text>
                    </inputExpression>
                  </input>
                  <input id="input2" label="Income">
                    <inputExpression id="inputExpr2" typeRef="integer">
                      <text>income</text>
                    </inputExpression>
                  </input>
                  <output id="output1" label="Status" name="status" typeRef="string"/>
                  <rule id="rule1">
                    <inputEntry id="ie1"><text>&lt; 18</text></inputEntry>
                    <inputEntry id="ie2"><text>-</text></inputEntry>
                    <outputEntry id="oe1"><text>"Declined"</text></outputEntry>
                  </rule>
                  <rule id="rule2">
                    <inputEntry id="ie3"><text>&gt;= 18</text></inputEntry>
                    <inputEntry id="ie4"><text>&lt; 20000</text></inputEntry>
                    <outputEntry id="oe2"><text>"Declined"</text></outputEntry>
                  </rule>
                  <rule id="rule3">
                    <inputEntry id="ie5"><text>&gt;= 18</text></inputEntry>
                    <inputEntry id="ie6"><text>&gt;= 20000</text></inputEntry>
                    <outputEntry id="oe3"><text>"Approved"</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    static final String UNIQUE_VIOLATION_DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                         id="unique-violation"
                         name="Unique Violation"
                         namespace="http://example.org/dmn">
              <decision id="decision" name="Decision">
                <decisionTable id="table" hitPolicy="UNIQUE">
                  <input id="input1" label="Category">
                    <inputExpression id="inputExpr1" typeRef="string">
                      <text>category</text>
                    </inputExpression>
                  </input>
                  <output id="output1" label="Result" name="result" typeRef="string"/>
                  <rule id="rule1">
                    <inputEntry id="ie1"><text>"A"</text></inputEntry>
                    <outputEntry id="oe1"><text>"First"</text></outputEntry>
                  </rule>
                  <rule id="rule2">
                    <inputEntry id="ie2"><text>"A"</text></inputEntry>
                    <outputEntry id="oe2"><text>"Second"</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    static final String WILDCARD_DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                         id="wildcard"
                         name="Wildcard"
                         namespace="http://example.org/dmn">
              <decision id="decision" name="Decision">
                <decisionTable id="table" hitPolicy="FIRST">
                  <input id="input1" label="Value">
                    <inputExpression id="inputExpr1" typeRef="string">
                      <text>value</text>
                    </inputExpression>
                  </input>
                  <output id="output1" label="Result" name="result" typeRef="string"/>
                  <rule id="rule1">
                    <inputEntry id="ie1"><text>-</text></inputEntry>
                    <outputEntry id="oe1"><text>"default"</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    static final String FIRST_HIT_POLICY_DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                         id="grade"
                         name="Grade"
                         namespace="http://example.org/dmn">
              <decision id="grade" name="Grade">
                <decisionTable id="gradeTable" hitPolicy="FIRST">
                  <input id="input1" label="Score">
                    <inputExpression id="inputExpr1" typeRef="integer">
                      <text>score</text>
                    </inputExpression>
                  </input>
                  <output id="output1" label="Grade" name="grade" typeRef="string"/>
                  <rule id="rule1">
                    <inputEntry id="ie1"><text>&gt;= 90</text></inputEntry>
                    <outputEntry id="oe1"><text>"A"</text></outputEntry>
                  </rule>
                  <rule id="rule2">
                    <inputEntry id="ie2"><text>&gt;= 70</text></inputEntry>
                    <outputEntry id="oe2"><text>"B"</text></outputEntry>
                  </rule>
                  <rule id="rule3">
                    <inputEntry id="ie3"><text>&gt;= 50</text></inputEntry>
                    <outputEntry id="oe3"><text>"C"</text></outputEntry>
                  </rule>
                  <rule id="rule4">
                    <inputEntry id="ie4"><text>&lt; 50</text></inputEntry>
                    <outputEntry id="oe4"><text>"F"</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    static final String COLLECT_HIT_POLICY_DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                         id="benefits"
                         name="Benefits"
                         namespace="http://example.org/dmn">
              <decision id="benefits" name="Benefits">
                <decisionTable id="benefitsTable" hitPolicy="COLLECT">
                  <input id="input1" label="Category">
                    <inputExpression id="inputExpr1" typeRef="string">
                      <text>category</text>
                    </inputExpression>
                  </input>
                  <output id="output1" label="Benefit" name="benefit" typeRef="string"/>
                  <rule id="rule1">
                    <inputEntry id="ie1"><text>"premium"</text></inputEntry>
                    <outputEntry id="oe1"><text>"Free Shipping"</text></outputEntry>
                  </rule>
                  <rule id="rule2">
                    <inputEntry id="ie2"><text>"premium"</text></inputEntry>
                    <outputEntry id="oe2"><text>"Extended Warranty"</text></outputEntry>
                  </rule>
                  <rule id="rule3">
                    <inputEntry id="ie3"><text>"standard"</text></inputEntry>
                    <outputEntry id="oe3"><text>"Standard Support"</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    static final String ANY_HIT_POLICY_DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                         id="any"
                         name="Any"
                         namespace="http://example.org/dmn">
              <decision id="decision" name="Decision">
                <decisionTable id="table" hitPolicy="ANY">
                  <input id="input1" label="Status">
                    <inputExpression id="inputExpr1" typeRef="string">
                      <text>status</text>
                    </inputExpression>
                  </input>
                  <output id="output1" label="Action" name="action" typeRef="string"/>
                  <rule id="rule1">
                    <inputEntry id="ie1"><text>"approved"</text></inputEntry>
                    <outputEntry id="oe1"><text>"proceed"</text></outputEntry>
                  </rule>
                  <rule id="rule2">
                    <inputEntry id="ie2"><text>"approved"</text></inputEntry>
                    <outputEntry id="oe2"><text>"proceed"</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    static final String RULE_ORDER_DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                         id="actions"
                         name="Actions"
                         namespace="http://example.org/dmn">
              <decision id="actions" name="Actions">
                <decisionTable id="table" hitPolicy="RULE ORDER">
                  <input id="input1" label="Score">
                    <inputExpression id="inputExpr1" typeRef="integer">
                      <text>score</text>
                    </inputExpression>
                  </input>
                  <output id="output1" label="Action" name="action" typeRef="string"/>
                  <rule id="rule1">
                    <inputEntry id="ie1"><text>&gt;= 80</text></inputEntry>
                    <outputEntry id="oe1"><text>"Alert"</text></outputEntry>
                  </rule>
                  <rule id="rule2">
                    <inputEntry id="ie2"><text>&gt;= 50</text></inputEntry>
                    <outputEntry id="oe2"><text>"Monitor"</text></outputEntry>
                  </rule>
                  <rule id="rule3">
                    <inputEntry id="ie3"><text>&gt;= 90</text></inputEntry>
                    <outputEntry id="oe3"><text>"Escalate"</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    static final String ELIGIBILITY_DMN_WITH_DRG = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                         id="eligibility-drg"
                         name="Eligibility with DRG"
                         namespace="http://example.org/dmn">
              <decision id="creditScore" name="Credit Score Rating">
                <decisionTable id="csTable" hitPolicy="FIRST">
                  <input id="input1" label="Credit Score">
                    <inputExpression id="inputExpr1" typeRef="integer">
                      <text>creditScore</text>
                    </inputExpression>
                  </input>
                  <output id="output1" label="Rating" name="creditRating" typeRef="string"/>
                  <rule id="rule1">
                    <inputEntry id="ie1"><text>&gt;= 700</text></inputEntry>
                    <outputEntry id="oe1"><text>"Good"</text></outputEntry>
                  </rule>
                  <rule id="rule2">
                    <inputEntry id="ie2"><text>[500..699]</text></inputEntry>
                    <outputEntry id="oe2"><text>"Fair"</text></outputEntry>
                  </rule>
                  <rule id="rule3">
                    <inputEntry id="ie3"><text>&lt; 500</text></inputEntry>
                    <outputEntry id="oe3"><text>"Poor"</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
              <decision id="eligibility" name="Eligibility">
                <informationRequirement id="req1">
                  <requiredDecision href="#creditScore"/>
                </informationRequirement>
                <decisionTable id="eligTable" hitPolicy="FIRST">
                  <input id="input2" label="Credit Rating">
                    <inputExpression id="inputExpr2" typeRef="string">
                      <text>creditRating</text>
                    </inputExpression>
                  </input>
                  <output id="output2" label="Decision" name="decision" typeRef="string"/>
                  <rule id="rule4">
                    <inputEntry id="ie4"><text>"Good"</text></inputEntry>
                    <outputEntry id="oe4"><text>"Approved"</text></outputEntry>
                  </rule>
                  <rule id="rule5">
                    <inputEntry id="ie5"><text>"Fair"</text></inputEntry>
                    <outputEntry id="oe5"><text>"Manual Review"</text></outputEntry>
                  </rule>
                  <rule id="rule6">
                    <inputEntry id="ie6"><text>"Poor"</text></inputEntry>
                    <outputEntry id="oe6"><text>"Declined"</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;
}
