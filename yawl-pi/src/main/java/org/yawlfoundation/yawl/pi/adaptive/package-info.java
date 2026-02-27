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
 * Real-time adaptive workflow intelligence — Diátaxis enterprise use cases.
 *
 * <h2>Diátaxis — Explanation</h2>
 *
 * <p>This package answers: <em>"Why does running AutoML inside the workflow engine
 * change what is possible in enterprise process management?"</em></p>
 *
 * <h2>The co-location thesis</h2>
 *
 * <p>Process intelligence and workflow execution have historically been separate
 * systems, connected by batch ETL.  The lag between what happens in a process and
 * what an ML model knows about it is measured in hours or days.  YAWL v6.0 dissolves
 * this boundary: {@link org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry}
 * runs ONNX models in the same JVM as {@link org.yawlfoundation.yawl.engine.YEngine}.
 * The prediction latency is the ONNX Runtime inference time — microseconds.</p>
 *
 * <h2>What becomes tractable in real-time</h2>
 *
 * <table border="1">
 *   <caption>Capabilities unlocked by co-location</caption>
 *   <tr>
 *     <th>Capability</th>
 *     <th>Batch (traditional)</th>
 *     <th>Co-located (YAWL v6.0)</th>
 *   </tr>
 *   <tr>
 *     <td>Fraud detection</td>
 *     <td>Nightly batch; fraud completes before detection</td>
 *     <td>Rejected in the engine callback before first task routes</td>
 *   </tr>
 *   <tr>
 *     <td>SLA monitoring</td>
 *     <td>15-min polling dashboards; breach discovered after the fact</td>
 *     <td>Remaining-time predicted at every task transition; escalation proactive</td>
 *   </tr>
 *   <tr>
 *     <td>Case complexity routing</td>
 *     <td>Static rules in specification; no adaptation to data drift</td>
 *     <td>TPOT2-trained outcome model reroutes complex cases on case start</td>
 *   </tr>
 *   <tr>
 *     <td>Model retraining</td>
 *     <td>Weekly/monthly scheduled jobs; stale models</td>
 *     <td>{@link org.yawlfoundation.yawl.pi.automl.ProcessMiningAutoMl} called on any
 *         milestone (e.g. every 500 case completions) without stopping the engine</td>
 *   </tr>
 *   <tr>
 *     <td>Training data extraction</td>
 *     <td>ETL pipelines to data warehouse; overnight lag</td>
 *     <td>{@link org.yawlfoundation.yawl.pi.predictive.ProcessMiningTrainingDataExtractor}
 *         reads directly from the live
 *         {@link org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore}</td>
 *   </tr>
 *   <tr>
 *     <td>Next-activity recommendation</td>
 *     <td>Not done in real-time; requires offline process mining tooling</td>
 *     <td>Predicted on every work-item completion; high-confidence paths boosted
 *         in queue priority</td>
 *   </tr>
 * </table>
 *
 * <h2>Package structure (Diátaxis mapping)</h2>
 *
 * <ul>
 *   <li><strong>Tutorial</strong> —
 *       {@link org.yawlfoundation.yawl.pi.adaptive.EnterpriseAutoMlPatterns}:
 *       one-line factory methods for insurance, healthcare, financial, operations</li>
 *   <li><strong>How-to</strong> —
 *       {@link org.yawlfoundation.yawl.pi.adaptive.PredictiveAdaptationRules}:
 *       factory methods for individual adaptation rules and pre-built rule sets</li>
 *   <li><strong>Reference</strong> —
 *       {@link org.yawlfoundation.yawl.pi.adaptive.PredictiveProcessObserver}:
 *       full {@link org.yawlfoundation.yawl.engine.ObserverGateway} implementation
 *       with per-callback semantics documented</li>
 *   <li><strong>Explanation</strong> — this package-info.java</li>
 * </ul>
 *
 * <h2>Integration overview</h2>
 *
 * <pre>
 * YEngine ──annoucement callbacks──▶ PredictiveProcessObserver
 *                                            │
 *                                  ONNX inference (μs)
 *                                    PredictiveModelRegistry
 *                                            │
 *                                     ProcessEvent emitted
 *                                            │
 *                               EventDrivenAdaptationEngine
 *                                            │
 *                                     AdaptationResult
 *                                  (REJECT | ESCALATE | REROUTE | …)
 * </pre>
 *
 * <h2>Seventh PI connection</h2>
 *
 * <p>This package introduces the seventh PI exception connection string:
 * {@code "adaptive"}.  It joins: {@code predictive}, {@code prescriptive},
 * {@code optimization}, {@code rag}, {@code dataprep}, {@code automl}.</p>
 *
 * @see org.yawlfoundation.yawl.pi.adaptive.EnterpriseAutoMlPatterns
 * @see org.yawlfoundation.yawl.pi.adaptive.PredictiveAdaptationRules
 * @see org.yawlfoundation.yawl.pi.adaptive.PredictiveProcessObserver
 * @see org.yawlfoundation.yawl.pi.automl.ProcessMiningAutoMl
 */
package org.yawlfoundation.yawl.pi.adaptive;
