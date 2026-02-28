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
 * Anomaly forensics and root cause analysis (RCA) for YAWL workflows.
 *
 * <h2>Overview</h2>
 * <p>This package provides real-time root cause analysis for workflow anomalies
 * detected by {@link org.yawlfoundation.yawl.observability.AnomalyDetector}.
 * When an anomaly is detected (e.g., task latency spike), the
 * {@link org.yawlfoundation.yawl.dspy.forensics.AnomalyForensicsEngine}
 * subscribes to the event and uses DSPy's MultiChainComparison to generate
 * competing root-cause hypotheses, then ranks them by plausibility.</p>
 *
 * <h2>Architecture</h2>
 * <pre>
 * AnomalyDetector (fires anomaly alert)
 *   ↓
 * AnomalyForensicsEngine (event subscriber)
 *   ├─ Collects context from YAWLTelemetry, BottleneckDetector
 *   ├─ Builds AnomalyContext (metric, duration, deviation, samples, cases)
 *   ├─ Calls PythonDspyBridge.runForensics(context)
 *   └─ Logs ForensicsReport via StructuredLogger
 *       ↓
 * dspy_anomaly_forensics.py
 *   ├─ Chain A: Resource contention hypothesis
 *   ├─ Chain B: Data volume spike hypothesis
 *   └─ Chain C: External dependency failure hypothesis
 *       ↓
 *   ForensicsReport (rootCause, confidence, evidenceChain, recommendation)
 * </pre>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.dspy.forensics.AnomalyForensicsEngine}
 *       — Main engine subscribing to anomaly events and orchestrating RCA</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.forensics.AnomalyContext}
 *       — Immutable snapshot of anomaly telemetry (metric, duration, samples, cases)</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.forensics.ForensicsReport}
 *       — RCA output: root cause, confidence, evidence chain, recommendation</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li><strong>AnomalyDetector</strong>: Engine subscribes via @EventListener</li>
 *   <li><strong>YAWLTelemetry</strong>: Collects recent metric samples and concurrent cases</li>
 *   <li><strong>BottleneckDetector</strong>: Accesses bottleneck scores for hypothesis narrowing</li>
 *   <li><strong>PythonDspyBridge</strong>: Executes DSPy MultiChainComparison for RCA</li>
 *   <li><strong>StructuredLogger</strong>: Logs forensics reports in JSON format</li>
 * </ul>
 *
 * <h2>Python Module</h2>
 * <p>The DSPy module {@code dspy_anomaly_forensics.py} implements:</p>
 * <ul>
 *   <li>{@code AnomalyRootCauseSignature}: Type-safe input/output contract</li>
 *   <li>{@code ResourceContentionHypothesis}: Chain A analysis</li>
 *   <li>{@code DataVolumeSpikeHypothesis}: Chain B analysis</li>
 *   <li>{@code ExternalDependencyHypothesis}: Chain C analysis</li>
 *   <li>{@code AnomalyRootCauseModule}: Main module using MultiChainComparison</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // In Spring configuration
 * @Bean
 * public AnomalyForensicsEngine forensicsEngine(
 *     PythonDspyBridge dspyBridge,
 *     YAWLTelemetry telemetry,
 *     BottleneckDetector bottleneckDetector,
 *     MeterRegistry meterRegistry
 * ) {
 *     return new AnomalyForensicsEngine(dspyBridge, telemetry, bottleneckDetector, meterRegistry);
 * }
 *
 * // AnomalyDetector fires event
 * AnomalyForensicsEngine.AnomalyEvent event = new AnomalyForensicsEngine.AnomalyEvent(
 *     "task_processing_latency",
 *     5000L,  // anomaly persisted 5 seconds
 *     3.2,    // 320% of baseline
 *     150L    // baseline was 150ms
 * );
 *
 * // Engine processes asynchronously
 * engine.onAnomaly(event);
 *
 * // Structured log output:
 * // {
 * //   "metric": "task_processing_latency",
 * //   "duration_ms": 5000,
 * //   "deviation_factor": 3.2,
 * //   "root_cause": "Resource contention from 12 concurrent cases",
 * //   "confidence": 0.85,
 * //   "evidence_chain": ["metric spike +320%", "concurrent cases spike 8→12", ...],
 * //   "recommendation": "Scale up agents pool by 4 units"
 * // }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All classes in this package are thread-safe. {@link AnomalyForensicsEngine}
 * uses a virtual thread executor to avoid blocking the anomaly detector.</p>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><strong>Async processing</strong>: Anomaly detection is not blocked</li>
 *   <li><strong>DSPy compilation caching</strong>: First RCA has ~500ms overhead, subsequent calls ~50ms</li>
 *   <li><strong>Memory footprint</strong>: ~2-5 MB per forensics module instance</li>
 *   <li><strong>Latency SLA</strong>: Target RCA completion within 2-5 seconds of anomaly detection</li>
 * </ul>
 *
 * @author YAWL Foundation (Agent C — Blue Ocean Innovation)
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.dspy.forensics;
