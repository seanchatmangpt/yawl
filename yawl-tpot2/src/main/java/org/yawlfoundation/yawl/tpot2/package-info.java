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
 * TPOT2 AutoML integration for YAWL.
 *
 * <p>This module provides subprocess-based integration with the TPOT2 Python library
 * for automated machine learning pipeline optimization. Key components:
 *
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.Tpot2Bridge} — Main entry point for running AutoML</li>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.Tpot2Config} — Immutable configuration for training runs</li>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.Tpot2Result} — Immutable result containing ONNX model</li>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.Tpot2TaskType} — Process mining task types</li>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.TrainingDataset} — Training data wrapper</li>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.Tpot2Exception} — Checked exception for failures</li>
 * </ul>
 *
 * <p><b>Usage example:</b>
 * {@snippet :
 * // Create training data
 * TrainingDataset dataset = new TrainingDataset(
 *     List.of("caseDurationMs", "taskCount"),
 *     List.of(new double[]{1200.0, 3.0}, new double[]{8000.0, 7.0}),
 *     List.of("completed", "failed"),
 *     "spec-001",
 *     2
 * );
 *
 * // Configure AutoML
 * Tpot2Config config = Tpot2Config.forCaseOutcome();
 *
 * // Run optimization
 * try (Tpot2Bridge bridge = new Tpot2Bridge()) {
 *     Tpot2Result result = bridge.fit(dataset, config);
 *     byte[] onnxModel = result.onnxModelBytes();
 * }
 * }
 *
 * <p><b>Python requirements:</b>
 * <ul>
 *   <li>Python 3.9+ on PATH</li>
 *   <li>tpot2: {@code pip install tpot2}</li>
 *   <li>skl2onnx: {@code pip install skl2onnx}</li>
 *   <li>scikit-learn, numpy, pandas</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.tpot2;
