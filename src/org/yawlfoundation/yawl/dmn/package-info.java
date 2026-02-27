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

/**
 * YAWL DMN — data-modelling-sdk-inspired schema types with GraalWASM decision evaluation.
 *
 * <h2>Architecture</h2>
 * <pre>
 * DataModel                          (top-level entity schema)
 *   ├─ DmnTable (1..*)               (named entity with typed columns)
 *   │    └─ DmnColumn (1..*)         (name + typeRef + required flag)
 *   └─ DmnRelationship (0..*)        (directed edge with EndpointCardinality)
 *        ├─ sourceCardinality        (crow's feet min/max at source)
 *        └─ targetCardinality        (crow's feet min/max at target)
 *
 * DmnDecisionService                 (high-level facade, AutoCloseable)
 *   ├─ DataModel                     (optional schema validation)
 *   ├─ DmnWasmBridge                 (from yawl-graalwasm; FEEL + hit policies)
 *   └─ DmnCollectAggregation         (SUM/MIN/MAX/COUNT; MIN+MAX via WASM)
 *
 * EndpointCardinality                (enum: ZERO_ONE | ONE_ONE | ZERO_MANY | ONE_MANY)
 * DmnCollectAggregation              (enum: SUM | MIN | MAX | COUNT)
 * </pre>
 *
 * <h2>Design basis</h2>
 * <p>The schema types ({@link org.yawlfoundation.yawl.dmn.DataModel},
 * {@link org.yawlfoundation.yawl.dmn.DmnTable},
 * {@link org.yawlfoundation.yawl.dmn.DmnColumn},
 * {@link org.yawlfoundation.yawl.dmn.DmnRelationship}) mirror the
 * {@code data-modelling-sdk} Rust crate's {@code DataModel / Table / Column / Relationship}
 * structs, extended with {@link org.yawlfoundation.yawl.dmn.EndpointCardinality}
 * (crow's feet min/max notation) on both relationship endpoints.</p>
 *
 * <p>Decision evaluation delegates to
 * {@link org.yawlfoundation.yawl.graalwasm.dmn.DmnWasmBridge} in the
 * {@code yawl-graalwasm} module, which runs a FEEL numeric engine in-process via
 * GraalVM WebAssembly. The {@link org.yawlfoundation.yawl.dmn.DmnCollectAggregation}
 * {@code MIN} and {@code MAX} operators use the same WASM binary for pairwise
 * f64 comparison.</p>
 *
 * <h2>Quick start — schema-validated decision evaluation</h2>
 * <pre>{@code
 * DataModel schema = DataModel.builder("LoanSchema")
 *     .table(DmnTable.builder("Applicant")
 *         .column(DmnColumn.of("age", "integer").build())
 *         .column(DmnColumn.of("income", "double").build())
 *         .build())
 *     .table(DmnTable.builder("Product")
 *         .column(DmnColumn.of("productType", "string").build())
 *         .build())
 *     .relationship(DmnRelationship.builder("applicant-product")
 *         .fromTable("Applicant")
 *         .toTable("Product")
 *         .sourceCardinality(EndpointCardinality.ONE_ONE)
 *         .targetCardinality(EndpointCardinality.ZERO_MANY)
 *         .build())
 *     .build();
 *
 * try (DmnDecisionService svc = new DmnDecisionService(schema)) {
 *     DmnWasmBridge.DmnModel model = svc.parseDmnModel(dmnXml);
 *
 *     DmnEvaluationContext ctx = DmnEvaluationContext.builder()
 *         .put("age", 35)
 *         .put("income", 55000.0)
 *         .put("productType", "FIXED")
 *         .build();
 *
 *     DmnDecisionResult result = svc.evaluate(model, "EligibilityDecision", ctx);
 *     result.getSingleResult().ifPresent(row ->
 *         System.out.println("Eligible: " + row.get("eligible")));
 * }
 * }</pre>
 *
 * <h2>Quick start — COLLECT aggregation with WASM-backed MIN/MAX</h2>
 * <pre>{@code
 * try (DmnDecisionService svc = new DmnDecisionService()) {
 *     DmnWasmBridge.DmnModel model = svc.parseDmnModel(dmnXml);
 *     DmnEvaluationContext ctx = DmnEvaluationContext.of("applicantId", "A123");
 *
 *     OptionalDouble maxScore = svc.evaluateAndAggregate(
 *         model, "RiskScores", ctx, "score", DmnCollectAggregation.MAX);
 *     OptionalDouble total = svc.evaluateAndAggregate(
 *         model, "RiskScores", ctx, "score", DmnCollectAggregation.SUM);
 * }
 * }</pre>
 *
 * <h2>EndpointCardinality notation</h2>
 * <pre>
 * ZERO_ONE  (0..1)  — circle-dash        optional, singular
 * ONE_ONE   (1..1)  — double-dash        mandatory, singular
 * ZERO_MANY (0..*)  — circle-crow's-foot optional, unbounded
 * ONE_MANY  (1..*)  — dash-crow's-foot   mandatory, unbounded
 * </pre>
 *
 * @see org.yawlfoundation.yawl.dmn.DataModel
 * @see org.yawlfoundation.yawl.dmn.DmnDecisionService
 * @see org.yawlfoundation.yawl.dmn.DmnCollectAggregation
 * @see org.yawlfoundation.yawl.dmn.EndpointCardinality
 * @see org.yawlfoundation.yawl.graalwasm.dmn.DmnWasmBridge
 */
@NullMarked
package org.yawlfoundation.yawl.dmn;

import org.jspecify.annotations.NullMarked;
