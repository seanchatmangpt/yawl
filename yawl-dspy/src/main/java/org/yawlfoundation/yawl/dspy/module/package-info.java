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
 * DSPy modules for LLM programs.
 *
 * <p>Modules are the building blocks of DSPy programs. Each module:
 * <ul>
 *   <li>Has a signature defining its input/output contract</li>
 *   <li>Can be composed with other modules into pipelines</li>
 *   <li>Can be optimized by teleprompters</li>
 *   <li>Can be compiled to efficient inference programs</li>
 * </ul>
 *
 * <h2>Available Modules:</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.dspy.module.Predict} - Basic prediction</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.module.ChainOfThought} - Step-by-step reasoning</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.module.ReAct} - Reasoning + Acting with tools</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * {@snippet :
 * // Create a predictor
 * var predictor = new Predict<>(signature, llmClient);
 *
 * // Run prediction
 * var result = predictor.run(Map.of("input", value));
 *
 * // Optimize with teleprompter
 * var optimizer = new BootstrapFewShot<>(metric, 5);
 * var optimized = optimizer.compile(predictor, trainset);
 * }
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.dspy.module;
