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
 * Blue Ocean #7: Formal Process Verification — Petri net soundness analysis.
 *
 * <p>This package provides soundness verification for YAWL workflows using
 * Petri net graph analysis and formal deadlock pattern detection.
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.verification.SoundnessVerifier}
 *       - Main verification engine using BFS reachability and SCC cycle detection</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.verification.DeadlockPattern}
 *       - Enum of 7 deadlock patterns with SPARQL queries and remediation advice</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.verification.VerificationFinding}
 *       - Immutable record of a single detected issue</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.verification.VerificationReport}
 *       - Complete verification results with soundness status and counts</li>
 * </ul>
 *
 * <h2>Verification Patterns (7)</h2>
 * <ol>
 *   <li><strong>Unreachable Task</strong> — task has no incoming arc from start</li>
 *   <li><strong>Dead Transition</strong> — task can never fire due to precondition</li>
 *   <li><strong>Implicit Deadlock</strong> — AND-join with unreachable input(s)</li>
 *   <li><strong>Missing OR-Join</strong> — multiple paths merge without consolidation</li>
 *   <li><strong>Orphaned Place</strong> — place with no outgoing transitions (token trap)</li>
 *   <li><strong>Livelock</strong> — cycle with no exit to completion</li>
 *   <li><strong>Improper Termination</strong> — multiple tokens at end place</li>
 * </ol>
 *
 * <h2>Algorithms</h2>
 * <ul>
 *   <li><strong>Reachability (BFS)</strong> — Forward pass from start place to identify
 *       all reachable places and transitions</li>
 *   <li><strong>Cycle Detection (DFS + SCC)</strong> — Identifies strongly connected
 *       components to detect livelocks</li>
 *   <li><strong>Fixed-Point Iteration</strong> — Computes dead transitions via iterative
 *       closure (transitions fed only by dead transitions are also dead)</li>
 *   <li><strong>Convergence Analysis</strong> — Detects missing merge transitions where
 *       multiple execution paths converge</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 * <pre>
 * // Build Petri net adjacency maps from a workflow specification
 * Map&lt;String, Set&lt;String&gt;&gt; placeToTransitions = ...;
 * Map&lt;String, Set&lt;String&gt;&gt; transitionToPlaces = ...;
 *
 * // Create verifier
 * var verifier = new SoundnessVerifier(
 *     placeToTransitions,
 *     transitionToPlaces,
 *     "start_place_id",
 *     "end_place_id"
 * );
 *
 * // Run verification
 * VerificationReport report = verifier.verify();
 *
 * // Check results
 * if (report.isSound()) {
 *     System.out.println("Workflow is sound!");
 * } else {
 *     System.out.println("Found " + report.deadlockCount() + " deadlock(s)");
 *     for (var finding : report.findings()) {
 *         System.out.println("  - " + finding);
 *     }
 * }
 * </pre>
 *
 * <h2>Integration with YAWL Engine</h2>
 * <p>The verifier is designed to integrate with:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.elements.YSpecification} —
 *       Extract net structure (tasks, conditions, flow arcs)</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.YEngine} —
 *       Pre-execution soundness check before case instantiation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.YawlMcpServer} —
 *       Expose verification as MCP tool endpoint</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.a2a.YawlA2AServer} —
 *       Provide A2A endpoint for autonomous agent verification queries</li>
 * </ul>
 *
 * <h2>SPARQL Support</h2>
 * <p>Each deadlock pattern exposes a SPARQL SELECT query via
 * {@link org.yawlfoundation.yawl.integration.verification.DeadlockPattern#sparqlQuery()}.
 * These queries can be executed against a semantic knowledge graph representation
 * of the Petri net for alternative implementation paths.
 *
 * <h2>Performance</h2>
 * <ul>
 *   <li>Linear in net size O(V + E) for BFS reachability</li>
 *   <li>O(V + E) for SCC detection via DFS</li>
 *   <li>O(V²) worst-case for fixed-point iteration on dead transitions</li>
 *   <li>Typical verification completes in milliseconds even for large nets</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since YAWL 6.0.0
 */
package org.yawlfoundation.yawl.integration.verification;
