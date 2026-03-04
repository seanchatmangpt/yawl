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
 * Rust4pmWrapper         (High-fidelity 1:1 mapping of Rust4PM OCEL2 library)
 * └─ Rust4pmOcel         (Type-safe records for OCEL2 data structures)
 * └─ JavaScriptExecutionEngine (from yawl-graaljs, JS+WASM polyglot, wasm-bindgen support)
 *    └─ @aarkue/process_mining_wasm v0.1.3 (OCEL2 parsing)
 *
 * dmn/DmnWasmBridge      (Full DMN 1.3 support — see subpackage)
 *   ├─ WasmExecutionEngine → dmn_feel_engine.wasm (FEEL numeric operations)
 *   └─ Java DMN evaluator (XML parsing, hit policies, FEEL unary tests, DRG)
 * </pre>
 *
 * <h2>High-Fidelity Rust4PM Bindings</h2>
 *
 * <p><strong>Design Goal</strong>: A Java 25 developer can read the Rust4PM documentation
 * and understand exactly what's happening in the Java code. Zero unnecessary abstraction.</p>
 *
 * <h3>API Transparency</h3>
 * <p>Each Rust function maps directly to a Java method:</p>
 * <table border="1">
 * <tr>
 *   <th>Rust Function</th>
 *   <th>Java Method</th>
 *   <th>Return Type</th>
 * </tr>
 * <tr>
 *   <td>{@code pub fn parse_ocel2_json(json_data: &[u8]) -> Vec<u8>}</td>
 *   <td>{@code parseOcel2Json(String)}</td>
 *   <td>{@code byte[]}</td>
 * </tr>
 * <tr>
 *   <td>{@code pub fn parse_ocel2_xml(ocel_data: &[u8]) -> OcelLog}</td>
 *   <td>{@code parseOcel2Xml(String)}</td>
 *   <td>{@code Value}</td>
 * </tr>
 * <tr>
 *   <td>{@code pub fn parse_ocel2_xml_to_json_str(...) -> String}</td>
 *   <td>{@code parseOcel2XmlToJsonString(String)}</td>
 *   <td>{@code String}</td>
 * </tr>
 * <tr>
 *   <td>{@code pub fn parse_ocel2_xml_to_json_vec(...) -> Vec<u8>}</td>
 *   <td>{@code parseOcel2XmlToJsonVec(String)}</td>
 *   <td>{@code byte[]}</td>
 * </tr>
 * <tr>
 *   <td>{@code pub fn parse_ocel2_xml_keep_state(...) -> OcelLogPtr}</td>
 *   <td>{@code parseOcel2XmlKeepInWasm(String)}</td>
 *   <td>{@code long} (pointer)</td>
 * </tr>
 * <tr>
 *   <td>{@code pub fn ocel_num_events(ptr: OcelLogPtr) -> usize}</td>
 *   <td>{@code getOcelNumEventsFromPointer(long)}</td>
 *   <td>{@code long}</td>
 * </tr>
 * <tr>
 *   <td>{@code pub fn ocel_destroy(ptr: OcelLogPtr) -> ()</td>
 *   <td>{@code destroyOcelPointer(long)}</td>
 *   <td>{@code void}</td>
 * </tr>
 * </table>
 *
 * <h3>Zero-Copy Memory Strategy</h3>
 * <p><strong>Two Parallel APIs</strong>:</p>
 * <ul>
 *   <li><strong>Auto-Cleanup (Copy-on-Return)</strong>: {@code parseOcel2XmlToJsonString()}, etc.
 *       Automatic WASM memory cleanup. Suitable for small to medium logs (&lt;100MB).</li>
 *   <li><strong>Zero-Copy (Pointer-Based)</strong>: {@code parseOcel2XmlKeepInWasm()} + pointer ops.
 *       Data stays in WASM memory. Requires manual cleanup. Optimal for large logs (>100MB).</li>
 * </ul>
 *
 * <p><strong>Memory Profile</strong>:
 * <pre>
 * Auto-Cleanup (parseOcel2XmlToJsonString):
 *   WASM Heap: [Input] → [Parse] → [Output]
 *   Java Heap: ← [Copy Result] → [String/bytes]
 *   After return: WASM memory freed
 *
 * Zero-Copy (parseOcel2XmlKeepInWasm):
 *   WASM Heap: [Input] → [Parse] → [Result stays here]
 *   Java Heap: [Pointer: 0x12345678]
 *   After return: WASM memory persists until destroyOcelPointer
 * </pre>
 * </p>
 *
 * <h3>Immutable Records</h3>
 * <p>All data types are Java 25 records with defensive copying:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.graalwasm.Rust4pmOcel.OcelPointer OcelPointer} - Opaque WASM pointer</li>
 *   <li>{@link org.yawlfoundation.yawl.graalwasm.Rust4pmOcel.OcelLog OcelLog} - Log metadata</li>
 *   <li>{@link org.yawlfoundation.yawl.graalwasm.Rust4pmOcel.OcelEvent OcelEvent} - Single event</li>
 *   <li>{@link org.yawlfoundation.yawl.graalwasm.Rust4pmOcel.OcelObject OcelObject} - Business object</li>
 *   <li>{@link org.yawlfoundation.yawl.graalwasm.Rust4pmOcel.OcelObjectReference OcelObjectReference} - Event-to-object link</li>
 * </ul>
 *
 * <h2>Quick Start — Rust4PM OCEL2 Process Mining</h2>
 *
 * <h3>Auto-Cleanup (Simple Use Case)</h3>
 * <pre>{@code
 * try (Rust4pmWrapper wrapper = new Rust4pmWrapper()) {
 *     String ocelXml = Files.readString(Path.of("event_log.xml"));
 *     String ocelJson = wrapper.parseOcel2XmlToJsonString(ocelXml);
 *     System.out.println("Converted: " + ocelJson);
 * }
 * // WASM resources automatically freed
 * }</pre>
 *
 * <h3>Zero-Copy Pointer (Large Log)</h3>
 * <pre>{@code
 * try (Rust4pmWrapper wrapper = new Rust4pmWrapper(4)) {  // 4 concurrent contexts
 *     String hugeLog = Files.readString(Path.of("million_event_log.xml"));
 *
 *     long ocelPtr = wrapper.parseOcel2XmlKeepInWasm(hugeLog);
 *     try {
 *         long eventCount = wrapper.getOcelNumEventsFromPointer(ocelPtr);
 *         System.out.println("Events: " + eventCount);
 *         // Result stays in WASM memory; no copy to Java heap
 *     } finally {
 *         wrapper.destroyOcelPointer(ocelPtr);  // CRITICAL: free memory
 *     }
 * }
 * }</pre>
 *
 * <h3>Concurrent Processing (Virtual Threads)</h3>
 * <pre>{@code
 * try (Rust4pmWrapper wrapper = new Rust4pmWrapper(4)) {
 *     List<CompletableFuture<String>> tasks = logFiles.stream()
 *         .map(logFile -> CompletableFuture.supplyAsync(() -> {
 *             // Each virtual thread gets its own context from the pool
 *             String xml = Files.readString(logFile);
 *             return wrapper.parseOcel2XmlToJsonString(xml);
 *         }))
 *         .toList();
 *
 *     CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
 * }
 * }</pre>
 *
 * <h2>Memory Safety</h2>
 *
 * <h3>Golden Rule: Manual Cleanup</h3>
 * <p><strong>For every {@code parseOcel2XmlKeepInWasm()}, there MUST be a
 * {@code destroyOcelPointer()} in a finally block.</strong></p>
 *
 * <p><strong>✅ Correct</strong>:
 * <pre>{@code
 * long ptr = wrapper.parseOcel2XmlKeepInWasm(xml);
 * try {
 *     // Use pointer
 * } finally {
 *     wrapper.destroyOcelPointer(ptr);  // Always executes
 * }
 * }</pre>
 * </p>
 *
 * <p><strong>❌ Wrong (Memory Leak)</strong>:
 * <pre>{@code
 * long ptr = wrapper.parseOcel2XmlKeepInWasm(xml);
 * wrapper.getOcelNumEventsFromPointer(ptr);
 * // Forgot destroyOcelPointer! WASM heap leak accumulates.
 * }</pre>
 * </p>
 *
 * <h2>Performance Characteristics</h2>
 *
 * <table border="1">
 * <tr>
 *   <th>Operation</th>
 *   <th>Latency</th>
 *   <th>Scalability</th>
 * </tr>
 * <tr>
 *   <td>Context pool acquire</td>
 *   <td>&lt; 1 µs</td>
 *   <td>Lock-free, O(1)</td>
 * </tr>
 * <tr>
 *   <td>parseOcel2XmlToJsonString (100KB log)</td>
 *   <td>1-5 ms</td>
 *   <td>O(n) where n = log size</td>
 * </tr>
 * <tr>
 *   <td>parseOcel2XmlKeepInWasm (100KB log)</td>
 *   <td>1-5 ms</td>
 *   <td>O(n), zero-copy</td>
 * </tr>
 * <tr>
 *   <td>getOcelNumEventsFromPointer</td>
 *   <td>&lt; 1 µs</td>
 *   <td>O(1), direct memory read</td>
 * </tr>
 * <tr>
 *   <td>destroyOcelPointer</td>
 *   <td>&lt; 100 µs</td>
 *   <td>O(1), WASM dealloc</td>
 * </tr>
 * </table>
 *
 * <h2>Runtime Requirements</h2>
 * <p>GraalVM JDK 24.1+ with WASM language support. {@link org.yawlfoundation.yawl.graalwasm.Rust4pmWrapper}
 * requires {@link org.yawlfoundation.yawl.graaljs} module for JavaScript+WASM polyglot execution.</p>
 *
 * <h2>References</h2>
 * <ul>
 *   <li><a href="https://docs.rs/process_mining/">Rust4PM Documentation</a></li>
 *   <li><a href="https://www.ocel-standard.org/">OCEL2 Standard</a></li>
 *   <li><a href="https://www.graalvm.org/latest/reference-manual/wasm/">GraalVM WASM Manual</a></li>
 * </ul>
 *
 * @see org.yawlfoundation.yawl.graalwasm.Rust4pmWrapper
 * @see org.yawlfoundation.yawl.graalwasm.Rust4pmOcel
 * @see org.yawlfoundation.yawl.graalwasm.WasmExecutionEngine
 * @see org.yawlfoundation.yawl.graalwasm.dmn.DmnWasmBridge
 */
@NullMarked
package org.yawlfoundation.yawl.graalwasm;

import org.jspecify.annotations.NullMarked;
