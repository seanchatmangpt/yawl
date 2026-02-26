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
 * Predictive AI connections: Process outcome and resource demand prediction.
 *
 * <p><strong>Connection 1: Predictive AI</strong>
 *
 * <p>The predictive package provides two key capabilities:
 *
 * <ul>
 *   <li><strong>Case Outcome Prediction:</strong>
 *     {@link org.yawlfoundation.yawl.pi.predictive.CaseOutcomePredictor} estimates
 *     the probability of case completion and identifies failure risk factors using
 *     ONNX machine learning models or DNA oracle historical analysis.
 *
 *   <li><strong>Bottleneck Identification:</strong>
 *     {@link org.yawlfoundation.yawl.pi.predictive.BottleneckPredictor} analyzes
 *     task wait times to identify the slowest task in a workflow specification,
 *     enabling proactive resource allocation.
 *
 *   <li><strong>Training Data Extraction:</strong>
 *     {@link org.yawlfoundation.yawl.pi.predictive.ProcessMiningTrainingDataExtractor}
 *     converts workflow event logs into tabular datasets suitable for training
 *     predictive models.
 *
 *   <li><strong>Model Registry:</strong>
 *     {@link org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry}
 *     manages ONNX Runtime models, providing thread-safe inference capabilities
 *     with automatic model discovery and loading.
 * </ul>
 *
 * <p><strong>Feature Extraction:</strong>
 *
 * <p>Both predictors extract case-level features from workflow events:
 * <ul>
 *   <li>Case duration (milliseconds)
 *   <li>Task count
 *   <li>Distinct work items
 *   <li>Cancellation indicators
 *   <li>Average task wait time
 * </ul>
 *
 * <p><strong>Integration with Core Components:</strong>
 *
 * <p>Predictive AI integrates with:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore}
 *     for event log retrieval
 *   <li>{@link org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle}
 *     for fallback risk assessment
 *   <li>{@link org.yawlfoundation.yawl.engine.YSpecificationID} for workflow identity
 *   <li>ONNX Runtime for model inference (ai.onnxruntime package)
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.pi.predictive;
