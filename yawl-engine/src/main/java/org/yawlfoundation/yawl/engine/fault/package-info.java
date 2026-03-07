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
 * Armstrong-Style Fault Injection Framework for YAWL Property-Based Testing.
 *
 * <h2>Overview</h2>
 * <p>This package implements Joe Armstrong's fault tolerance philosophy for
 * testing workflow engine reliability. The core principle: "Let it crash,
 * but verify it recovers."</p>
 *
 * <h2>Armstrong's Philosophy (Erlang/OTP)</h2>
 * <blockquote>
 * "The key to building reliable systems is to design for failure, not to try
 * to prevent it. If you can crash and recover, you're reliable."
 * — Joe Armstrong
 * </blockquote>
 *
 * <h2>Components</h2>
 * <ul>
 *   <li>{@link FaultInjector} - Core fault injection engine</li>
 *   <li>{@link FaultModel} - Enumeration of fault types (crash, message loss, etc.)</li>
 *   <li>{@link CrashPoint} - Specification of injection point in workflow</li>
 *   <li>{@link RecoveryVerifier} - Post-crash consistency verification</li>
 * </ul>
 *
 * <h2>Fault Types</h2>
 * <table border="1">
 *   <tr><th>Type</th><th>Description</th><th>Erlang Analogy</th></tr>
 *   <tr><td>PROCESS_CRASH</td><td>Thread death, OOM</td><td>Process exit</td></tr>
 *   <tr><td>MESSAGE_LOSS</td><td>Network partition</td><td>Lost message</td></tr>
 *   <tr><td>MESSAGE_CORRUPTION</td><td>Data corruption</td><td>Byzantine lite</td></tr>
 *   <tr><td>TIMING_FAILURE</td><td>Timeout, slow response</td><td>Network latency</td></tr>
 *   <tr><td>BYZANTINE_FAILURE</td><td>Arbitrary behavior</td><td>Worst case</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create fault injector with 10% probability
 * FaultInjector injector = new FaultInjector(0.1);
 * injector.setRandomMode(true);
 *
 * // Register fault observer
 * injector.onFault(fault -> log.info("Injected: {}", fault));
 *
 * // Use in property-based test
 * @Property(tries = 1000)
 * void crashRecoveryPreservesConsistency(YSpecification spec) {
 *     YNetRunner runner = engine.launchCase(spec, caseId);
 *
 *     // Random fault injection during execution
 *     injector.maybeInject(InjectionPhase.WORKITEM_CHECKOUT, "task_A");
 *
 *     // Verify recovery
 *     RecoveryVerifier verifier = new RecoveryVerifier();
 *     RecoveryReport report = verifier.verify(runner);
 *     assertTrue(report.isConsistent());
 * }
 * }</pre>
 *
 * <h2>Integration with jqwik Property Testing</h2>
 * <p>Use with jqwik for exhaustive fault injection testing:
 * <pre>{@code
 * @Property(tries = 1000)
 * void faultToleranceProperty(
 *     @ForAll("validSpecifications") YSpecification spec,
 *     @ForAll("crashPoints") CrashPoint crashPoint
 * ) {
 *     FaultInjector injector = new FaultInjector();
 *     injector.addFaultPoint(crashPoint);
 *
 *     YNetRunner runner = engine.launchCase(spec, caseId);
 *     executeWorkflow(runner, injector);
 *
 *     RecoveryVerifier.assertConsistent(runner);
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see <a href="https://ferd.ca/rtfm-erlang-s-let-it-crash-explained.html">Erlang's "Let It Crash" Explained</a>
 * @see <a href="https://www.erlang.org/doc/design_principles/fault_principles.html">Erlang Fault Tolerance Principles</a>
 */
package org.yawlfoundation.yawl.engine.fault;
