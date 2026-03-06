/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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
 * Fluent API for TPOT2 AutoML integration.
 *
 * <p>This package provides a Python DSPy-like fluent API for TPOT2,
 * following the JOR4J (Java > OTP > Rust/Python) pattern.
 *
 * <h2>Architecture (JOR4J Pattern)</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  JAVA 25                                    Layer 3: Domain API  │
 * │  (Virtual Threads, Panama FFM, Records)       Tpot2.class        │
 * └─────────────────────────┬───────────────────────────────────────┘
 *                          │ libei (Erlang Interface, 0.1ms)
 *                          ▼
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Erlang/OTP 28                              Layer 2: Distribution │
 * │  (Process isolation, supervision trees)    tpot2_bridge           │
 * └─────────────────────────┬───────────────────────────────────────┘
 *                          │ NIF / PyO3 (0.2ms)
 *                          ▼
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Python 3.12                              Layer 1: Native          │
 * │  (tpot2, scikit-learn, skl2onnx)          TPOT2 optimizer        │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Configure TPOT2
 * Tpot2.configure(config -> config
 *     .taskType(Tpot2TaskType.CASE_OUTCOME)
 *     .generations(10)
 *     .maxTimeMins(30));
 *
 * // Create optimizer
 * Tpot2Optimizer optimizer = Tpot2.optimizer()
 *     .trainingData(features, labels)
 *     .build();
 *
 * // Fit model
 * Tpot2Result result = optimizer.fit();
 *
 * // Get ONNX model
 * byte[] onnx = result.onnxModelBytes();
 * }</pre>
 *
 * <h2>Process Mining Tasks</h2>
 * <ul>
 *   <li><b>CASE_OUTCOME</b>: Predict binary/multiclass case outcome</li>
 *   <li><b>REMAINING_TIME</b>: Predict remaining case time (regression)</li>
 *   <li><b>NEXT_ACTIVITY</b>: Predict next activity (multiclass classification)</li>
 *   <li><b>ANOMALY_DETECTION</b>: Detect anomalous behavior (binary classification)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.tpot2.fluent;
