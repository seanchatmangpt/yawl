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
 * YAWL GraalWasm Integration — in-process WebAssembly execution for Java enterprise workflows.
 *
 * <h2>Architecture</h2>
 * <pre>
 * WasmExecutionEngine    (general-purpose WASM; creates on-demand WasmModule instances)
 *   └─ WasmModule        (loaded+instantiated WASM module, owns its GraalWasm Context)
 * WasmBinaryCache        (ConcurrentHashMap of parsed Source objects)
 * WasmSandboxConfig      (WASI level, IO access, preopened directories)
 * WasmException          (7 ErrorKind values)
 *
 * Rust4pmBridge          (OCEL2 process mining via @aarkue/process_mining_wasm v0.1.3)
 *   └─ JavaScriptExecutionEngine (from yawl-graaljs, JS+WASM polyglot, wasm-bindgen support)
 *
 * dmn/DmnWasmBridge      (Full DMN 1.3 support — see subpackage)
 *   ├─ WasmExecutionEngine → dmn_feel_engine.wasm (FEEL numeric operations)
 *   └─ Java DMN evaluator (XML parsing, hit policies, FEEL unary tests, DRG)
 * </pre>
 *
 * <h2>Quick Start — General WASM</h2>
 * <pre>{@code
 * WasmExecutionEngine engine = WasmExecutionEngine.builder()
 *     .sandboxConfig(WasmSandboxConfig.withWasi())
 *     .build();
 *
 * try (WasmModule mod = engine.loadModuleFromClasspath("wasm/my-module.wasm", "my-module")) {
 *     Value result = mod.execute("my_function", 42);
 * }
 * engine.close();
 * }</pre>
 *
 * <h2>Quick Start — rust4pm OCEL2 process mining</h2>
 * <pre>{@code
 * try (Rust4pmBridge bridge = new Rust4pmBridge()) {
 *     String json = bridge.parseOcel2XmlToJsonString(ocel2XmlContent);
 * }
 * }</pre>
 *
 * <h2>Quick Start — DMN 1.3 decision evaluation</h2>
 * <pre>{@code
 * try (DmnWasmBridge bridge = new DmnWasmBridge()) {
 *     DmnWasmBridge.DmnModel model = bridge.parseDmnModel(dmnXml);
 *     DmnEvaluationContext ctx = DmnEvaluationContext.builder()
 *         .put("age", 35)
 *         .put("riskCategory", "HIGH")
 *         .build();
 *     DmnDecisionResult result = bridge.evaluateDecision(model, "eligibility", ctx);
 *     result.getSingleResult().ifPresent(row ->
 *         log.info("Decision: {}", row.get("eligibilityStatus"))
 *     );
 * }
 * }</pre>
 *
 * <h2>Runtime requirement</h2>
 * <p>GraalVM JDK 24.1+ with wasm language support on classpath. {@link Rust4pmBridge} also
 * requires GraalJS (yawl-graaljs).</p>
 *
 * @see org.yawlfoundation.yawl.graalwasm.WasmExecutionEngine
 * @see org.yawlfoundation.yawl.graalwasm.Rust4pmBridge
 * @see org.yawlfoundation.yawl.graalwasm.dmn.DmnWasmBridge
 */
@NullMarked
package org.yawlfoundation.yawl.graalwasm;

import org.jspecify.annotations.NullMarked;
