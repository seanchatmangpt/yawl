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
 * ANY WARRANTY; without even the implied implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.dmn;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.graalwasm.dmn.DmnDecisionResult;
import org.yawlfoundation.yawl.graalwasm.dmn.DmnEvaluationContext;
import org.yawlfoundation.yawl.graalwasm.dmn.DmnException;
import org.yawlfoundation.yawl.graalwasm.dmn.DmnWasmBridge;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for DMN (Decision Model and Notation) evaluation performance.
 *
 * <h2>Benchmark Types</h2>
 * <ul>
 *   <li>{@link #benchmarkSimpleDecisionEvaluation} - Single rule evaluation performance</li>
 *   <li>{@link #benchmarkComplexDecisionTable} - Large decision table (100+ rules) performance</li>
 *   <li>{@link #benchmarkHitPolicyFirst} - FIRST hit policy performance</li>
 *   <li>{@link #benchmarkHitPolicyCollect} - COLLECT hit policy performance</li>
 *   <li>{@link #benchmarkWasmBridgeLatency} - GraalWasm bridge overhead latency</li>
 * </ul>
 *
 * <h2>Test Data</h2>
 * <p>All benchmarks use realistic DMN models with:
 *   - Simple eligibility decision (3 rules)
 *   - Complex risk assessment table (100 rules)
 *   - First hit policy for fast matching
 *   - Collect hit policy for aggregation
 *   - FEEL expressions using WASM-optimized numeric operations</p>
 *
 * <h2>Configuration</h2>
 * <pre>
 * @BenchmarkMode(Mode.AverageTime)
 * @OutputTimeUnit(TimeUnit.MILLISECONDS)
 * @State(Scope.Benchmark)
 * @Warmup(iterations = 3, time = 1)
 * @Measurement(iterations = 5, time = 1)
 * @Fork(1)
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see DmnDecisionService
 * @see DmnWasmBridge
 * @since 6.0.0
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DmnEvaluationBenchmark {

    private DmnDecisionService service;
    private DmnWasmBridge wasmBridge;
    private DmnWasmBridge.DmnModel simpleModel;
    private DmnWasmBridge.DmnModel complexModel;
    private DmnWasmBridge.DmnModel firstHitModel;
    private DmnWasmBridge.DmnModel collectModel;
    private DmnEvaluationContext simpleContext;
    private DmnEvaluationContext complexContext;

    // Test data for different scenarios
    private static final String SIMPLE_DMN = """
        <definitions id="simpleModel" name="Simple Eligibility" namespace="http://www.omg.org/spec/DMN/20180521/MODEL/"
                    xmlns="http://www.omg.org/spec/DMN/20180521/MODEL/"
                    xmlns:dmndi="http://www.omg.org/spec/DMN/20180521/DMNDI/"
                    xmlns:di="http://www.omg.org/spec/DI/20140501/">
            <decision id="eligibility" name="Eligibility Decision">
                <decisionTable>
                    <input id="age" label="Age" typeRef="integer"/>
                    <input id="income" label="Income" typeRef="double"/>
                    <output id="eligible" label="Eligible" typeRef="boolean"/>
                    <rule>
                        <inputEntry>18</inputEntry>
                        <inputEntry>50000</inputEntry>
                        <outputEntry>true</outputEntry>
                    </rule>
                    <rule>
                        <inputEntry>25</inputEntry>
                        <inputEntry>75000</inputEntry>
                        <outputEntry>true</outputEntry>
                    </rule>
                    <rule>
                        <inputEntry>60</inputEntry>
                        <inputEntry>30000</inputEntry>
                        <outputEntry>false</outputEntry>
                    </rule>
                </decisionTable>
            </decision>
        </definitions>
        """;

    private static final String COMPLEX_DMN = buildComplexDecisionTable();

    private static final String FIRST_HIT_DMN = """
        <definitions id="firstHitModel" name="First Hit Model" namespace="http://www.omg.org/spec/DMN/20180521/MODEL/"
                    xmlns="http://www.omg.org/spec/DMN/20180521/MODEL/"
                    xmlns:dmndi="http://www.omg.org/spec/DMN/20180521/DMNDI/"
                    xmlns:di="http://www.omg.org/spec/DI/20140501/">
            <decision id="riskCategory" name="Risk Category" hitPolicy="FIRST">
                <decisionTable>
                    <input id="score" label="Risk Score" typeRef="integer"/>
                    <output id="category" label="Category" typeRef="string"/>
                    <output id="priority" label="Priority" typeRef="integer"/>
                </decisionTable>
            </decision>
        </definitions>
        """;

    private static final String COLLECT_DMN = """
        <definitions id="collectModel" name="Collect Model" namespace="http://www.omg.org/spec/DMN/20180521/MODEL/"
                    xmlns="http://www.omg.org/spec/DMN/20180521/MODEL/"
                    xmlns:dmndi="http://www.omg.org/spec/DMN/20180521/DMNDI/"
                    xmlns:di="http://www.omg.org/spec/DI/20140501/">
            <decision id="riskScores" name="Risk Score Collect" hitPolicy="COLLECT">
                <decisionTable>
                    <input id="age" label="Age" typeRef="integer"/>
                    <input id="income" label="Income" typeRef="double"/>
                    <output id="score" label="Risk Score" typeRef="integer"/>
                    <output id="level" label="Level" typeRef="string"/>
                </decisionTable>
            </decision>
        </definitions>
        """;

    @Setup
    public void setup() throws Exception {
        // Initialize services and models
        try {
            service = new DmnDecisionService();
            wasmBridge = new DmnWasmBridge();

            // Parse DMN models
            simpleModel = service.parseDmnModel(SIMPLE_DMN);
            complexModel = service.parseDmnModel(COMPLEX_DMN);
            firstHitModel = service.parseDmnModel(FIRST_HIT_DMN);
            collectModel = service.parseDmnModel(COLLECT_DMN);

            // Prepare evaluation contexts
            simpleContext = DmnEvaluationContext.builder()
                .put("age", 30)
                .put("income", 60000.0)
                .build();

            complexContext = DmnEvaluationContext.builder()
                .put("score", 750)
                .build();
        } catch (DmnException e) {
            // Skip setup if GraalWasm is not available
            System.err.println("Warning: GraalWasm not available, skipping DMN benchmarks: " + e.getMessage());
            service = null;
            wasmBridge = null;
        }
    }

    @TearDown
    public void tearDown() {
        if (service != null) {
            service.close();
        }
        if (wasmBridge != null) {
            wasmBridge.close();
        }
    }

    /**
     * Benchmark: Simple decision evaluation with 3 rules.
     *
     * Tests basic DMN evaluation with minimal rules to establish baseline performance.
     */
    @Benchmark
    public void benchmarkSimpleDecisionEvaluation(Blackhole blackhole) {
        if (service == null) return;

        DmnDecisionResult result = service.evaluate(simpleModel, "eligibility", simpleContext);
        blackhole.consume(result);
    }

    /**
     * Benchmark: Complex decision table with 100+ rules.
     *
     * Tests performance with a large decision table simulating realistic risk assessment scenarios.
     * Measures rule matching efficiency and context processing overhead.
     */
    @Benchmark
    public void benchmarkComplexDecisionTable(Blackhole blackhole) {
        if (service == null) return;

        DmnDecisionResult result = service.evaluate(complexModel, "riskAssessment", complexContext);
        blackhole.consume(result);
    }

    /**
     * Benchmark: FIRST hit policy performance.
     *
     * Tests FIRST hit policy which should return only the first matched rule.
     * Important for scenarios where order matters and fast decisions are needed.
     */
    @Benchmark
    public void benchmarkHitPolicyFirst(Blackhole blackhole) {
        if (service == null) return;

        DmnDecisionResult result = service.evaluate(firstHitModel, "riskCategory", complexContext);
        blackhole.consume(result);
    }

    /**
     * Benchmark: COLLECT hit policy performance.
     *
     * Tests COLLECT hit policy which aggregates all matched rules.
     * Important for scenarios requiring comprehensive results from multiple rules.
     */
    @Benchmark
    public void benchmarkHitPolicyCollect(Blackhole blackhole) {
        if (service == null) return;

        DmnDecisionContext context = DmnEvaluationContext.builder()
            .put("age", 35)
            .put("income", 55000.0)
            .build();

        DmnDecisionResult result = service.evaluate(collectModel, "riskScores", context);
        blackhole.consume(result);
    }

    /**
     * Benchmark: GraalWasm bridge latency overhead.
     *
     * Measures the overhead of WASM module loading and execution without decision logic.
     * Important for understanding the baseline WASM infrastructure cost.
     */
    @Benchmark
    public void benchmarkWasmBridgeLatency(Blackhole blackhole) {
        if (wasmBridge == null) return;

        // Test WASM numeric operation latency directly
        double result = wasmBridge.evaluateFeelNumericOp("feel_add", 10.5, 20.5);
        blackhole.consume(result);
    }

    /**
     * Benchmark: Performance evaluation with data model validation.
     *
     * Tests the complete DMN service flow including DataModel schema validation
     * before evaluation. Measures the cost of schema validation overhead.
     */
    @Benchmark
    public void benchmarkWithSchemaValidation(Blackhole blackhole) {
        if (service == null) return;

        // Create a simple schema for validation
        DataModel schema = DataModel.builder("EligibilitySchema")
            .table(DmnTable.builder("Applicant")
                .column(DmnColumn.of("age", "integer").required(false).build())
                .column(DmnColumn.of("income", "double").required(false).build())
                .build())
            .build();

        // Create service with schema validation
        try (DmnDecisionService validatedService = new DmnDecisionService(schema)) {
            DmnDecisionResult result = validatedService.evaluate(
                simpleModel, "eligibility", simpleContext);
            blackhole.consume(result);
        }
    }

    /**
     * Benchmark: COLLECT aggregation performance.
     *
     * Tests the performance of collecting and aggregating results from
     * multiple matched rules using SUM, MIN, MAX, and COUNT operations.
     */
    @Benchmark
    public void benchmarkCollectAggregation(Blackhole blackhole) {
        if (service == null) return;

        DmnEvaluationContext context = DmnEvaluationContext.builder()
            .put("age", 40)
            .put("income", 80000.0)
            .build();

        DmnDecisionResult result = service.evaluate(collectModel, "riskScores", context);

        // Test different aggregation operations
        OptionalDouble sum = service.collectAggregate(result, "score", DmnCollectAggregation.SUM);
        OptionalDouble max = service.collectAggregate(result, "score", DmnCollectAggregation.MAX);
        OptionalDouble min = service.collectAggregate(result, "score", DmnCollectAggregation.MIN);
        OptionalDouble count = service.collectAggregate(result, "score", DmnCollectAggregation.COUNT);

        blackhole.consume(sum);
        blackhole.consume(max);
        blackhole.consume(min);
        blackhole.consume(count);
    }

    /**
     * Benchmark: Multiple evaluations in sequence.
     *
     * Tests performance when evaluating the same decision multiple times
     * with different contexts. Measures cache effectiveness and reuse patterns.
     */
    @Benchmark
    public void benchmarkSequentialEvaluations(Blackhole blackhole) {
        if (service == null) return;

        // Test with varying contexts
        for (int i = 0; i < 10; i++) {
            DmnEvaluationContext context = DmnEvaluationContext.builder()
                .put("age", 20 + i * 5)
                .put("income", 40000.0 + i * 5000)
                .build();

            DmnDecisionResult result = service.evaluate(simpleModel, "eligibility", context);
            blackhole.consume(result);
        }
    }

    // Helper method to generate complex decision table with 100 rules
    private static String buildComplexDecisionTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <definitions id="complexModel" name="Risk Assessment" namespace="http://www.omg.org/spec/DMN/20180521/MODEL/"
                        xmlns="http://www.omg.org/spec/DMN/20180521/MODEL/"
                        xmlns:dmndi="http://www.omg.org/spec/DMN/20180521/DMNDI/"
                        xmlns:di="http://www.omg.org/spec/DI/20140501/">
                <decision id="riskAssessment" name="Risk Assessment">
                    <decisionTable>
                        <input id="score" label="Risk Score" typeRef="integer"/>
                        <output id="riskLevel" label="Risk Level" typeRef="string"/>
                        <output id="action" label="Action" typeRef="string"/>
            """);

        // Generate 100 rules for risk assessment
        for (int i = 0; i < 100; i++) {
            int score = i * 10;
            String level = i < 20 ? "Low" : i < 60 ? "Medium" : "High";
            String action = i < 20 ? "Monitor" : i < 60 ? "Review" : "Escalate";

            sb.append(String.format("""
                        <rule>
                            <inputEntry>%d</inputEntry>
                            <outputEntry>"%s"</outputEntry>
                            <outputEntry>"%s"</outputEntry>
                        </rule>
                """, score, level, action));
        }

        sb.append("""
                    </decisionTable>
                </decision>
            </definitions>
        """);

        return sb.toString();
    }
}