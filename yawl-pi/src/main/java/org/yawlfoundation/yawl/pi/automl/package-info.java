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
 * <p><b>Note:</b> The core TPOT2 integration has been extracted to the
 * standalone {@code yawl-tpot2} module for reuse across YAWL and external projects.
 * This package now provides process-intelligence-specific AutoML facades
 * that delegate to {@link org.yawlfoundation.yawl.tpot2.Tpot2Bridge}.
 *
 * <h2>Core types (in yawl-tpot2 module)</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.Tpot2Bridge} — subprocess bridge for TPOT2</li>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.Tpot2Config} — configuration record</li>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.Tpot2Result} — result with ONNX model</li>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.Tpot2TaskType} — process mining task types</li>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.TrainingDataset} — training data wrapper</li>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.Tpot2Exception} — checked exception</li>
 * </ul>
 *
 * <h2>Supported use cases ({@link org.yawlfoundation.yawl.tpot2.Tpot2TaskType})</h2>
 * <ul>
 *   <li><b>CASE_OUTCOME</b> — binary classification: will this case complete or fail?</li>
 *   <li><b>REMAINING_TIME</b> — regression: how many ms until case completion?</li>
 *   <li><b>NEXT_ACTIVITY</b> — multiclass: which activity comes next?</li>
 *   <li><b>ANOMALY_DETECTION</b> — binary: is this case normal or anomalous?</li>
 * </ul>
 *
 * <h2>Entry points in this package</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.pi.automl.ProcessMiningAutoMl} — static facade
 *       for end-to-end training: extract training data → TPOT2 → register ONNX model</li>
 * </ul>
 *
 * <h2>Runtime requirements</h2>
 * <p>Python 3.9+ with the following packages must be on PATH:
 * <pre>pip install tpot2 skl2onnx numpy pandas scikit-learn</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.pi.automl;
