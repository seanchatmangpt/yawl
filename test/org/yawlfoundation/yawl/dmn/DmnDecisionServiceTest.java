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

package org.yawlfoundation.yawl.dmn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.graalwasm.dmn.DmnDecisionResult;
import org.yawlfoundation.yawl.graalwasm.dmn.DmnEvaluationContext;
import org.yawlfoundation.yawl.graalwasm.dmn.DmnWasmBridge;

import java.util.OptionalDouble;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DmnDecisionService}.
 *
 * <p>These tests exercise the facade's schema validation, decision evaluation,
 * and COLLECT aggregation paths using inline DMN XML fixtures.</p>
 */
@DisplayName("DmnDecisionService")
class DmnDecisionServiceTest {

    // -----------------------------------------------------------------------
    // DMN XML fixtures
    // -----------------------------------------------------------------------

    /** Simple UNIQUE hit policy: age → eligibilityStatus. */
    private static final String ELIGIBILITY_DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                         namespace="http://yawl.test/eligibility"
                         name="EligibilityDecisions">
              <decision id="EligibilityDecision" name="Eligibility Decision">
                <decisionTable id="dt1" hitPolicy="UNIQUE">
                  <input id="in1" label="age">
                    <inputExpression id="ie1" typeRef="integer">
                      <text>age</text>
                    </inputExpression>
                  </input>
                  <output id="out1" label="eligibilityStatus" name="eligibilityStatus" typeRef="string"/>
                  <rule id="r1">
                    <inputEntry id="ie1r1"><text>&lt; 18</text></inputEntry>
                    <outputEntry id="oe1r1"><text>"INELIGIBLE"</text></outputEntry>
                  </rule>
                  <rule id="r2">
                    <inputEntry id="ie1r2"><text>&gt;= 18</text></inputEntry>
                    <outputEntry id="oe1r2"><text>"ELIGIBLE"</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    /** COLLECT hit policy with numeric scores. */
    private static final String RISK_SCORES_DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                         namespace="http://yawl.test/risk"
                         name="RiskScoring">
              <decision id="RiskScores" name="Risk Scores">
                <decisionTable id="dt2" hitPolicy="COLLECT">
                  <input id="in1" label="category">
                    <inputExpression id="ie1" typeRef="string">
                      <text>category</text>
                    </inputExpression>
                  </input>
                  <output id="out1" label="score" name="score" typeRef="double"/>
                  <rule id="r1">
                    <inputEntry id="ie1r1"><text>"HIGH"</text></inputEntry>
                    <outputEntry id="oe1r1"><text>80</text></outputEntry>
                  </rule>
                  <rule id="r2">
                    <inputEntry id="ie1r2"><text>"HIGH"</text></inputEntry>
                    <outputEntry id="oe1r2"><text>90</text></outputEntry>
                  </rule>
                  <rule id="r3">
                    <inputEntry id="ie1r3"><text>"LOW"</text></inputEntry>
                    <outputEntry id="oe1r3"><text>20</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    // -----------------------------------------------------------------------
    // Schema fixtures
    // -----------------------------------------------------------------------

    private static DataModel applicantSchema() {
        return DataModel.builder("ApplicantSchema")
                .table(DmnTable.builder("Applicant")
                        .column(DmnColumn.of("age", "integer").build())
                        .column(DmnColumn.of("income", "double").required(false).build())
                        .build())
                .build();
    }

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        void noArgConstructor_schemaisNull() {
            try (DmnDecisionService svc = new DmnDecisionService()) {
                assertNull(svc.getSchema());
            }
        }

        @Test
        void schemaConstructor_storesSchema() {
            DataModel schema = applicantSchema();
            try (DmnDecisionService svc = new DmnDecisionService(schema)) {
                assertThat(svc.getSchema(), is(sameInstance(schema)));
            }
        }

        @Test
        void schemaWithIntegrityViolation_throwsIllegalArgument() {
            DataModel brokenSchema = DataModel.builder("Broken")
                    .table(DmnTable.builder("A")
                            .column(DmnColumn.of("x", "string").build())
                            .build())
                    // Relationship references "B" which is not in the model
                    .relationship(DmnRelationship.builder("a-to-b")
                            .fromTable("A").toTable("B").build())
                    .build();

            assertThrows(IllegalArgumentException.class,
                    () -> new DmnDecisionService(brokenSchema).close());
        }
    }

    // -----------------------------------------------------------------------
    // parseDmnModel()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("parseDmnModel()")
    class ParseTests {

        @Test
        void parsesValidDmnXml() {
            try (DmnDecisionService svc = new DmnDecisionService()) {
                DmnWasmBridge.DmnModel model = svc.parseDmnModel(ELIGIBILITY_DMN);
                assertNotNull(model);
            }
        }
    }

    // -----------------------------------------------------------------------
    // evaluate()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("evaluate()")
    class EvaluateTests {

        @Test
        void uniqueHitPolicy_returnsEligible_forAge35() {
            try (DmnDecisionService svc = new DmnDecisionService()) {
                DmnWasmBridge.DmnModel model = svc.parseDmnModel(ELIGIBILITY_DMN);
                DmnEvaluationContext ctx = DmnEvaluationContext.of("age", 35);

                DmnDecisionResult result = svc.evaluate(model, "EligibilityDecision", ctx);

                assertTrue(result.getSingleResult().isPresent());
                assertThat(result.getSingleResult().get().get("eligibilityStatus"),
                        is("ELIGIBLE"));
            }
        }

        @Test
        void uniqueHitPolicy_returnsIneligible_forAge10() {
            try (DmnDecisionService svc = new DmnDecisionService()) {
                DmnWasmBridge.DmnModel model = svc.parseDmnModel(ELIGIBILITY_DMN);
                DmnEvaluationContext ctx = DmnEvaluationContext.of("age", 10);

                DmnDecisionResult result = svc.evaluate(model, "EligibilityDecision", ctx);

                assertTrue(result.getSingleResult().isPresent());
                assertThat(result.getSingleResult().get().get("eligibilityStatus"),
                        is("INELIGIBLE"));
            }
        }

        @Test
        void withSchema_evaluate_succeedsForValidContext() {
            DataModel schema = applicantSchema();
            try (DmnDecisionService svc = new DmnDecisionService(schema)) {
                DmnWasmBridge.DmnModel model = svc.parseDmnModel(ELIGIBILITY_DMN);
                DmnEvaluationContext ctx = DmnEvaluationContext.builder()
                        .put("age", 25)
                        .build();

                DmnDecisionResult result = svc.evaluate(model, "EligibilityDecision", ctx);
                assertTrue(result.hasResult());
            }
        }
    }

    // -----------------------------------------------------------------------
    // collectAggregate()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("collectAggregate()")
    class CollectAggregateTests {

        @Test
        void sumOfHighCategoryScores_is170() {
            try (DmnDecisionService svc = new DmnDecisionService()) {
                DmnWasmBridge.DmnModel model = svc.parseDmnModel(RISK_SCORES_DMN);
                DmnEvaluationContext ctx = DmnEvaluationContext.of("category", "HIGH");

                OptionalDouble sum = svc.evaluateAndAggregate(
                        model, "RiskScores", ctx, "score", DmnCollectAggregation.SUM);

                assertTrue(sum.isPresent());
                assertThat(sum.getAsDouble(), is(closeTo(170.0, 1e-9)));
            }
        }

        @Test
        void maxOfHighCategoryScores_is90() {
            try (DmnDecisionService svc = new DmnDecisionService()) {
                DmnWasmBridge.DmnModel model = svc.parseDmnModel(RISK_SCORES_DMN);
                DmnEvaluationContext ctx = DmnEvaluationContext.of("category", "HIGH");

                OptionalDouble max = svc.evaluateAndAggregate(
                        model, "RiskScores", ctx, "score", DmnCollectAggregation.MAX);

                assertTrue(max.isPresent());
                assertThat(max.getAsDouble(), is(90.0));
            }
        }

        @Test
        void minOfHighCategoryScores_is80() {
            try (DmnDecisionService svc = new DmnDecisionService()) {
                DmnWasmBridge.DmnModel model = svc.parseDmnModel(RISK_SCORES_DMN);
                DmnEvaluationContext ctx = DmnEvaluationContext.of("category", "HIGH");

                OptionalDouble min = svc.evaluateAndAggregate(
                        model, "RiskScores", ctx, "score", DmnCollectAggregation.MIN);

                assertTrue(min.isPresent());
                assertThat(min.getAsDouble(), is(80.0));
            }
        }

        @Test
        void countOfHighCategoryRules_is2() {
            try (DmnDecisionService svc = new DmnDecisionService()) {
                DmnWasmBridge.DmnModel model = svc.parseDmnModel(RISK_SCORES_DMN);
                DmnEvaluationContext ctx = DmnEvaluationContext.of("category", "HIGH");

                OptionalDouble count = svc.evaluateAndAggregate(
                        model, "RiskScores", ctx, "score", DmnCollectAggregation.COUNT);

                assertTrue(count.isPresent());
                assertThat(count.getAsDouble(), is(2.0));
            }
        }

        @Test
        void noMatchingRules_returnsEmpty() {
            try (DmnDecisionService svc = new DmnDecisionService()) {
                DmnWasmBridge.DmnModel model = svc.parseDmnModel(RISK_SCORES_DMN);
                DmnEvaluationContext ctx = DmnEvaluationContext.of("category", "MEDIUM");

                OptionalDouble result = svc.evaluateAndAggregate(
                        model, "RiskScores", ctx, "score", DmnCollectAggregation.SUM);

                assertTrue(result.isEmpty());
            }
        }
    }
}
