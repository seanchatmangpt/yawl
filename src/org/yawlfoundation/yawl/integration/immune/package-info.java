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
 * Workflow Immune System: Runtime deadlock detection and prediction.
 *
 * <h2>Overview</h2>
 * <p>The Immune System package provides real-time deadlock detection for YAWL workflows
 * by integrating Petri net soundness verification into the workflow engine's task firing
 * pipeline. When tasks fire, the system analyzes the remaining workflow state to detect
 * and predict deadlock patterns before they occur.</p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li><strong>WorkflowImmuneSystem</strong> - ObserverGateway implementation that
 *       intercepts task firings and performs deadlock analysis</li>
 *   <li><strong>DeadlockPrediction</strong> - Immutable record representing a detected
 *       deadlock prediction with confidence scores and affected elements</li>
 *   <li><strong>ImmuneSystemConfig</strong> - Configuration for the immune system's
 *       analysis depth, auto-compensation mode, and pattern filtering</li>
 *   <li><strong>ImmuneReport</strong> - Aggregated report of all predictions and
 *       avoidance statistics across case executions</li>
 * </ul>
 *
 * <h2>Deadlock Patterns Detected</h2>
 * <p>The immune system leverages {@link org.yawlfoundation.yawl.integration.verification.SoundnessVerifier}
 * to detect 7 core deadlock patterns:</p>
 * <ol>
 *   <li><strong>UNREACHABLE_TASK</strong> - Task has no incoming arc from reachable places</li>
 *   <li><strong>DEAD_TRANSITION</strong> - Task can never fire due to unsatisfiable preconditions</li>
 *   <li><strong>IMPLICIT_DEADLOCK</strong> - AND-join waiting for tokens from unreachable branches</li>
 *   <li><strong>MISSING_OR_JOIN</strong> - Multiple paths converge without a merge transition</li>
 *   <li><strong>ORPHANED_PLACE</strong> - Place with no outgoing transitions (token trap)</li>
 *   <li><strong>LIVELOCK</strong> - Cycle with no exit to end place (infinite loop)</li>
 *   <li><strong>IMPROPER_TERMINATION</strong> - Multiple tokens possible at end place</li>
 * </ol>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * // Create immune system with default config
 * ImmuneSystemConfig config = ImmuneSystemConfig.defaultConfig();
 * WorkflowImmuneSystem immune = new WorkflowImmuneSystem(
 *     config,
 *     prediction -> System.out.println("Deadlock: " + prediction)
 * );
 *
 * // Register with engine (typically done by integration framework)
 * engine.registerObserverGateway(immune);
 *
 * // Run cases; immune system analyzes each task firing
 * // Predictions are emitted via the listener callback
 *
 * // Generate report
 * ImmuneReport report = immune.getReport();
 * System.out.println(report.toString());
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>The immune system is designed for concurrent case execution. It uses thread-safe
 * collections (CopyOnWriteArrayList) and atomic counters to support virtual thread
 * processing without synchronization overhead.</p>
 *
 * <h2>Performance Characteristics</h2>
 * <p>Soundness verification runs in polynomial time (typically O(V+E) for graph reachability).
 * For typical YAWL workflows with 10-100 tasks, verification completes in &lt;10ms.</p>
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li><strong>ObserverGateway</strong> - Implements the engine's observer pattern
 *       to intercept task firing events</li>
 *   <li><strong>SoundnessVerifier</strong> - Uses real graph algorithms for deadlock detection</li>
 *   <li><strong>YAnnouncement</strong> - Extracts task and case information from firing events</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see org.yawlfoundation.yawl.integration.verification.SoundnessVerifier
 * @see org.yawlfoundation.yawl.engine.ObserverGateway
 */
package org.yawlfoundation.yawl.integration.immune;
