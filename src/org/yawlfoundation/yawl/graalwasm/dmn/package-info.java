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
 * Full DMN 1.3 support through GraalWASM — Decision Model and Notation evaluation
 * with FEEL expression acceleration via an in-process WebAssembly engine.
 *
 * <h2>Architecture</h2>
 * <pre>
 * DmnWasmBridge                    ← public API entry point
 *   ├─ WasmExecutionEngine         ← GraalVM Polyglot WASM runtime
 *   │    └─ dmn_feel_engine.wasm   ← FEEL numeric operations (16 exports)
 *   └─ Java DMN evaluator
 *        ├─ javax.xml.parsers      ← DMN XML parsing (namespace-aware)
 *        ├─ FEEL unary test engine ← strings, numerics, ranges, negation, OR-lists
 *        └─ Hit policy engine      ← all 7 DMN hit policies
 * </pre>
 *
 * <h2>Supported DMN features</h2>
 * <ul>
 *   <li>DMN 1.2 ({@code http://www.omg.org/spec/DMN/20180521/MODEL/}) and
 *       DMN 1.3 ({@code https://www.omg.org/spec/DMN/20191111/MODEL/}) namespaces</li>
 *   <li>All seven hit policies: UNIQUE, FIRST, ANY, COLLECT, RULE ORDER, PRIORITY,
 *       OUTPUT ORDER</li>
 *   <li>FEEL unary tests: string literals, numeric comparisons, ranges ({@code [1..10]}),
 *       wildcard ({@code -}), OR-lists ({@code "A","B"}), negation ({@code not("A")})</li>
 *   <li>FEEL numeric operations via WASM: +, -, *, /, comparisons, floor, ceiling,
 *       sqrt, abs, min, max</li>
 *   <li>Decision Requirements Graph (DRG): decisions referencing other decisions</li>
 *   <li>Multiple input/output columns per decision table</li>
 *   <li>Null-safe evaluation ({@code -} wildcard matches any value including null)</li>
 * </ul>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * try (DmnWasmBridge bridge = new DmnWasmBridge()) {
 *     DmnWasmBridge.DmnModel model = bridge.parseDmnModel(dmnXml);
 *
 *     DmnEvaluationContext ctx = DmnEvaluationContext.builder()
 *         .put("age", 35)
 *         .put("riskCategory", "HIGH")
 *         .build();
 *
 *     DmnDecisionResult result = bridge.evaluateDecision(model, "eligibility", ctx);
 *     result.getSingleResult().ifPresent(row ->
 *         System.out.println(row.get("eligibilityStatus"))
 *     );
 * }
 * }</pre>
 *
 * <h2>WASM FEEL engine</h2>
 * <p>The bundled {@code wasm/dmn_feel_engine.wasm} is a 361-byte hand-crafted WebAssembly
 * module providing 16 FEEL numeric operations. It is loaded on demand per evaluation call
 * via {@link org.yawlfoundation.yawl.graalwasm.WasmExecutionEngine} and closed after each use.</p>
 *
 * <h2>Runtime requirement</h2>
 * <p>GraalVM JDK 24.1+ with {@code wasm} language support on the classpath.</p>
 *
 * @see org.yawlfoundation.yawl.graalwasm.dmn.DmnWasmBridge
 * @see org.yawlfoundation.yawl.graalwasm.dmn.DmnEvaluationContext
 * @see org.yawlfoundation.yawl.graalwasm.dmn.DmnDecisionResult
 * @see org.yawlfoundation.yawl.graalwasm.dmn.DmnHitPolicy
 * @see org.yawlfoundation.yawl.graalwasm.dmn.DmnException
 */
@NullMarked
package org.yawlfoundation.yawl.graalwasm.dmn;

import org.jspecify.annotations.NullMarked;
