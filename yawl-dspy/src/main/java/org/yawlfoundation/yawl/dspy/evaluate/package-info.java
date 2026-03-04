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
 * Evaluation metrics for DSPy modules.
 *
 * <p>Metrics score the quality of module outputs against expected values.
 * They are used by teleprompters to optimize modules and by evaluation
 * pipelines to measure performance.
 *
 * <h2>Built-in Metrics:</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.dspy.evaluate.Metric#exactMatch()}</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.evaluate.Metric#contains()}</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.evaluate.Metric#accuracy(String...)}</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.evaluate.Metric#f1Score(String, String)}</li>
 * </ul>
 *
 * <h2>Custom Metrics:</h2>
 * {@snippet :
 * Metric custom = (predicted, expected, trace) -> {
 *     // Custom scoring logic
 *     return predicted.getString("answer").equals(expected.get("answer")) ? 1.0 : 0.0;
 * };
 * }
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.dspy.evaluate;
