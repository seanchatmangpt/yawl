/**
 * YAWL Erlang/OTP 28 Bridge — Java 25 Panama FFM integration with Erlang erl_interface.
 *
 * <h3>Architecture (3 layers)</h3>
 * <ol>
 *   <li><strong>Layer 1</strong> ({@code generated/}) — Hand-written Panama FFM bindings for
 *       {@code libei.so}. Simulates jextract output from OTP 28.3.1 {@code ei.h}.
 *       Graceful degradation when library absent.</li>
 *   <li><strong>Layer 2</strong> ({@code bridge/}) — Typed bridge: {@code ErlangNode} manages
 *       the connection lifetime via {@code Arena.ofShared()}; {@code ErlTerm} sealed hierarchy
 *       encodes/decodes all 13 Erlang term types.</li>
 *   <li><strong>Layer 3</strong> ({@code processmining/}) — Domain API: zero FFI types
 *       visible at the call site. Use {@code ErlangBridge} for all application code.</li>
 * </ol>
 *
 * <h3>Quick start</h3>
 * <pre>
 *   // Requires: -Derlang.library.path=/path/to/libei.so
 *   // Discover path: bash scripts/build-erlang.sh
 *   try (ErlangBridge bridge = ErlangBridge.connect("yawl@localhost", "secretcookie")) {
 *       String caseId = bridge.launchCase("MySpec");
 *   }
 * </pre>
 *
 * @see org.yawlfoundation.yawl.erlang.generated.ei_h
 * @see org.yawlfoundation.yawl.erlang.bridge.ErlangNode
 * @see org.yawlfoundation.yawl.erlang.processmining.ErlangBridge
 */
package org.yawlfoundation.yawl.erlang;
