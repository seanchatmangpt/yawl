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
 * Sealed class hierarchies for workflow results and outcomes.
 *
 * <p>This package provides three sealed class hierarchies that model the distinct
 * result domains in the YAWL engine:</p>
 *
 * <h2>Hierarchies</h2>
 * <dl>
 *   <dt>{@link org.yawlfoundation.yawl.elements.results.WorkflowResult}</dt>
 *   <dd>Models the outcome of executing a workflow specification instance. Permits:
 *       {@code SuccessfulWorkflow}, {@code FailedWorkflow}, {@code TimedOutWorkflow}.</dd>
 *
 *   <dt>{@link org.yawlfoundation.yawl.elements.results.CaseOutcome}</dt>
 *   <dd>Models the lifecycle completion state of a case. Permits:
 *       {@code CaseCompleted}, {@code CaseFailed}, {@code CaseCancelled}.</dd>
 *
 *   <dt>{@link org.yawlfoundation.yawl.elements.results.EventResult}</dt>
 *   <dd>Models the processing result of an engine event. Permits:
 *       {@code EventAccepted}, {@code EventRejected}, {@code EventInvalid}.</dd>
 * </dl>
 *
 * <h2>Design Rationale</h2>
 * <p>Sealed classes restrict inheritance to a closed, known set of subtypes defined
 * in this package. This constraint provides three benefits:</p>
 * <ol>
 *   <li><b>Exhaustive pattern matching</b>: The compiler verifies that switch
 *       expressions on sealed types cover all permitted subtypes, eliminating
 *       unhandled-case bugs without a {@code default} branch.</li>
 *   <li><b>Type safety</b>: Callers cannot introduce unexpected subtypes that bypass
 *       engine invariants.</li>
 *   <li><b>Immutability</b>: All classes in this package are immutable. Fields are
 *       final and no mutating methods are provided.</li>
 * </ol>
 *
 * <h2>Exhaustive Switch Usage</h2>
 * <pre>{@code
 * WorkflowResult result = engine.executeWorkflow(specID, caseParams);
 * String summary = switch (result) {
 *     case SuccessfulWorkflow s -> "OK in " + s.durationMs() + "ms";
 *     case FailedWorkflow f     -> "FAIL: " + f.reason();
 *     case TimedOutWorkflow t   -> "TIMEOUT after " + t.timeoutMs() + "ms";
 * };
 * }</pre>
 *
 * @see org.yawlfoundation.yawl.elements.results.WorkflowResult
 * @see org.yawlfoundation.yawl.elements.results.CaseOutcome
 * @see org.yawlfoundation.yawl.elements.results.EventResult
 */
package org.yawlfoundation.yawl.elements.results;
