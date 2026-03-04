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
 * DSPy teleprompters (optimizers) for automatic prompt optimization.
 *
 * <p>Teleprompters automatically improve DSPy modules using training data.
 * The name comes from "tele-" (at a distance) + "prompter" (one who prompts).
 *
 * <h2>Available Teleprompters:</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.dspy.teleprompter.BootstrapFewShot} - Generates few-shot examples from successful runs</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.teleprompter.MIPROv2} - Multi-prompt instruction optimization</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * {@snippet :
 * // Bootstrap few-shot examples
 * var optimizer = BootstrapFewShot.<Predict<?>>builder()
 *     .metric(Metric.accuracy("outcome"))
 *     .maxExamples(5)
 *     .minScore(0.8)
 *     .build();
 *
 * Predict<?> optimized = optimizer.compile(predictor, trainset);
 * }
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.dspy.teleprompter;
