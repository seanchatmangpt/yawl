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
 * Prescriptive AI connections: Workflow optimization and intervention recommendations.
 *
 * <p><strong>Connection 2: Prescriptive AI</strong>
 *
 * <p>The prescriptive package provides decision-making capabilities for workflow improvement:
 *
 * <ul>
 *   <li><strong>Action Recommendation:</strong>
 *     {@link org.yawlfoundation.yawl.pi.prescriptive.PrescriptiveEngine} generates
 *     ranked lists of recommended interventions to prevent case failure, including
 *     rerouting to alternate paths, escalation, and resource reallocation.
 *
 *   <li><strong>Process Actions (Sealed):</strong>
 *     {@link org.yawlfoundation.yawl.pi.prescriptive.ProcessAction} sealed interface
 *     with four implementations:
 *     <ul>
 *       <li>{@link org.yawlfoundation.yawl.pi.prescriptive.RerouteAction} - redirect to alternate task
 *       <li>{@link org.yawlfoundation.yawl.pi.prescriptive.EscalateAction} - escalate for human review
 *       <li>{@link org.yawlfoundation.yawl.pi.prescriptive.ReallocateResourceAction} - assign to different resource
 *       <li>{@link org.yawlfoundation.yawl.pi.prescriptive.NoOpAction} - no intervention needed
 *     </ul>
 *
 *   <li><strong>Action Scoring:</strong>
 *     {@link org.yawlfoundation.yawl.pi.prescriptive.ActionRecommender} scores and ranks
 *     actions based on baseline risk score, ensuring the highest-impact interventions
 *     appear first in recommendation lists.
 *
 *   <li><strong>Constraint Validation:</strong>
 *     {@link org.yawlfoundation.yawl.pi.prescriptive.ProcessConstraintModel} validates
 *     recommended actions against workflow ordering constraints (using Apache Jena RDF),
 *     ensuring no action violates task precedence rules.
 * </ul>
 *
 * <p><strong>Recommendation Flow:</strong>
 *
 * <pre>
 * Case Outcome Prediction
 *   ↓
 * Generate Candidates:
 *   - If risk > 0.5: ReallocateResourceAction
 *   - If risk > 0.7: EscalateAction
 *   - Alternative paths: RerouteAction per DNA oracle
 *   - Always: NoOpAction
 *   ↓
 * Filter by Constraint Feasibility
 *   ↓
 * Score and Rank by ActionRecommender
 *   ↓
 * Return Sorted List
 * </pre>
 *
 * <p><strong>Integration with Core Components:</strong>
 *
 * <p>Prescriptive AI integrates with:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.pi.predictive.CaseOutcomePrediction} for risk input
 *   <li>{@link org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle} for alternative paths
 *   <li>Apache Jena RDF (org.apache.jena package) for constraint modeling
 *   <li>Pattern matching on sealed {@link org.yawlfoundation.yawl.pi.prescriptive.ProcessAction}
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.pi.prescriptive;
