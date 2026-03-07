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
 * Internal LTL (Linear Temporal Logic) Model Checker for YAWL.
 *
 * <h2>Overview</h2>
 * <p>This package provides a pure Java implementation of LTL model checking
 * for YAWL workflow specifications. No external model checkers (SPIN, NuSMV)
 * are required.</p>
 *
 * <h2>Van der Aalst's Soundness Properties</h2>
 * <p>Implements the four soundness properties from van der Aalst's 1997 paper:
 * <ol>
 *   <li><b>Option to complete</b>: Every case can reach completion</li>
 *   <li><b>Proper completion</b>: Only one token at output condition</li>
 *   <li><b>No dead tasks</b>: Every task can fire</li>
 *   <li><b>No deadlock</b>: No state without progress</li>
 * </ol>
 *
 * <h2>LTL Operators Supported</h2>
 * <table border="1">
 *   <tr><th>Operator</th><th>Syntax</th><th>Description</th></tr>
 *   <tr><td>Next</td><td>X(p)</td><td>p holds in next state</td></tr>
 *   <tr><td>Finally</td><td>F(p), ◇p</td><td>p holds eventually</td></tr>
 *   <tr><td>Globally</td><td>G(p), □p</td><td>p holds always</td></tr>
 *   <tr><td>Until</td><td>p U q</td><td>p holds until q</td></tr>
 *   <tr><td>Release</td><td>p R q</td><td>q holds until p</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create model checker
 * LtlModelChecker checker = new LtlModelChecker();
 *
 * // Verify single property
 * ModelCheckResult result = checker.verify(specification,
 *     TemporalProperty.EVENTUALLY_TERMINATES);
 *
 * if (result.isSatisfied()) {
 *     System.out.println("Property satisfied!");
 * }
 *
 * // Verify all soundness properties
 * SoundnessReport report = checker.verifySoundness(specification);
 * System.out.println(report.detailedReport());
 * }</pre>
 *
 * <h2>Implementation Details</h2>
 * <ul>
 *   <li>Explicit-state model checking with BFS state space exploration</li>
 *   <li>On-the-fly Büchi automaton construction from LTL formulas</li>
 *   <li>Nested DFS for accepting cycle detection</li>
 *   <li>Counterexample generation for violated properties</li>
 * </ul>
 *
 * <h2>PhD Publication Notes</h2>
 * <p>This implementation supports ICSE/SE conference publication with:
 * <ul>
 *   <li>Statistical rigor (99% confidence intervals)</li>
 *   <li>Van der Aalst's soundness theorem implementation</li>
 *   <li>Armstrong-style fault tolerance verification</li>
 *   <li>Comprehensive test coverage (85%+ mutation score target)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see LtlModelChecker
 * @see LtlFormula
 * @see TemporalProperty
 * @see <a href="https://doi.org/10.1016/S0304-3975(96)00152-5">Van der Aalst, 1997</a>
 */
package org.yawlfoundation.yawl.verification.ltl;
