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
 * AutoML connection for YAWL Process Intelligence: TPOT2-powered pipeline
 * discovery for process mining prediction tasks.
 *
 * <p>This package adds a sixth PI connection, {@code "automl"}, alongside the
 * existing connections: {@code predictive}, {@code prescriptive}, {@code optimization},
 * {@code rag}, and {@code dataprep}. It uses TPOT2 (evolutionary hyperparameter
 * optimisation) to automatically discover optimal sklearn pipelines for four
 * process mining use cases, then exports them as ONNX models compatible with
 * {@link org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry}.
 *
 * <h2>Supported use cases ({@link org.yawlfoundation.yawl.pi.automl.Tpot2TaskType})</h2>
 * <ul>
 *   <li><b>CASE_OUTCOME</b> — binary classification: will this case complete or fail?</li>
 *   <li><b>REMAINING_TIME</b> — regression: how many ms until case completion?</li>
 *   <li><b>NEXT_ACTIVITY</b> — multiclass: which activity comes next?</li>
 *   <li><b>ANOMALY_DETECTION</b> — binary: is this case normal or anomalous?</li>
 * </ul>
 *
 * <h2>Entry points</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.pi.automl.ProcessMiningAutoMl} — static facade
 *       for end-to-end training: extract training data → TPOT2 → register ONNX model</li>
 *   <li>{@link org.yawlfoundation.yawl.pi.automl.Tpot2Bridge} — subprocess bridge
 *       for direct TPOT2 invocation against a {@link org.yawlfoundation.yawl.pi.predictive.TrainingDataset}</li>
 * </ul>
 *
 * <h2>Bridge pattern: subprocess (not GraalPy)</h2>
 * <p>Unlike {@code PowlPythonBridge} (which embeds Python via GraalPy), this package
 * launches TPOT2 as an external subprocess via {@link java.lang.ProcessBuilder}. This
 * is intentional: TPOT2 requires heavy Python dependencies (sklearn, numpy, tpot2,
 * skl2onnx) that are incompatible with GraalPy's sandboxed execution model. The
 * subprocess approach works on all JDKs including Temurin 25.
 *
 * <h2>Runtime requirements</h2>
 * <p>Python 3.9+ with the following packages must be on PATH:
 * <pre>pip install tpot2 skl2onnx numpy pandas scikit-learn</pre>
 * <p>When Python or the required packages are unavailable, all methods throw
 * {@link org.yawlfoundation.yawl.pi.PIException} with connection {@code "automl"}.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.pi.automl;
