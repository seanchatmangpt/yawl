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
 * YAWL Process Intelligence (PI) Module — Foundation Layer.
 *
 * <h2>Overview</h2>
 *
 * <p>The PI module implements "No AI Without PI" — five AI connections for process mining
 * and intelligent workflow optimization per van der Aalst 2025 Object-Centric Process Mining (OCPM).
 *
 * <h3>Five PI Connections</h3>
 * <ol>
 *   <li><strong>Predictive AI</strong> ({@link org.yawlfoundation.yawl.pi.predictive}):
 *       Case outcome prediction and bottleneck identification using ONNX models</li>
 *   <li><strong>Prescriptive AI</strong> ({@link org.yawlfoundation.yawl.pi.prescriptive}):
 *       Automated action recommendations (reroute, escalate, reallocate)</li>
 *   <li><strong>Optimization</strong>: Resource allocation and task sequencing (planned)</li>
 *   <li><strong>RAG</strong>: Retrieval-augmented generation for process knowledge (planned)</li>
 *   <li><strong>Data Preparation</strong>: Feature engineering and event log normalization (planned)</li>
 * </ol>
 *
 * <h2>Architecture</h2>
 *
 * <pre>
 * YEngine / YNetRunner
 *     ↓
 * ProcessIntelligenceFacade (entry point)
 *     ├─→ Predictive: CaseOutcomePredictor, BottleneckPredictor
 *     ├─→ Prescriptive: PrescriptiveEngine, ActionRecommender
 *     ├─→ WorkflowDNAOracle (learns failure patterns via RDF)
 *     └─→ PredictiveModelRegistry (ONNX model inference)
 * </pre>
 *
 * <h2>Integration Points</h2>
 *
 * <ul>
 *   <li><strong>WorkflowEventStore</strong>: Event sourcing for case history
 *   <li><strong>WorkflowDNAOracle</strong>: Workflow pattern discovery
 *   <li><strong>PredictiveModelRegistry</strong>: ONNX Runtime model management
 *   <li><strong>ZaiService</strong>: Optional Z.AI integration for RAG features
 * </ul>
 *
 * <h2>Core Classes</h2>
 *
 * <ul>
 *   <li>{@link PIException}: Checked exception for PI failures
 *   <li>{@link PISession}: Immutable session state
 *   <li>{@link PIFacadeConfig}: Dependency injection configuration
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.pi;
