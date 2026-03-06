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
 * DSPy-powered interpretation layer for TPOT2 AutoML results.
 *
 * <p>This package provides intelligent interpretation of machine learning
 * pipeline optimization results using the DSPy fluent API.
 *
 * <h2>JOR4J Meta-Layer Architecture</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Layer 4: Interpretation (DSPy)                                 │
 * │  Tpot2DspyInterpreter → LLM → Structured Insights               │
 * └─────────────────────────┬───────────────────────────────────────┘
 *                          │
 * ┌─────────────────────────▼───────────────────────────────────────┐
 * │  Layer 3: AutoML (TPOT2)                                        │
 * │  Tpot2Optimizer → Genetic Programming → ONNX Pipeline           │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.interpreter.Tpot2ResultInterpreter} -
 *       Basic interpretation without LLM</li>
 *   <li>{@link org.yawlfoundation.yawl.tpot2.interpreter.Tpot2DspyInterpreter} -
 *       DSPy-powered intelligent interpretation</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Configure DSPy first
 * Dspy.configure(lm -> lm
 *     .model("groq/llama-3.3-70b")
 *     .apiKey(System.getenv("GROQ_API_KEY")));
 *
 * // Run TPOT2 optimization
 * Tpot2Result result = optimizer.fit();
 *
 * // Create interpreter
 * Tpot2DspyInterpreter interpreter = new Tpot2DspyInterpreter(result, config);
 *
 * // Get interpretation
 * Interpretation interp = interpreter.interpret();
 *
 * System.out.println(interp.explanation());
 * System.out.println(interp.recommendations());
 * System.out.println(interp.deploymentReadiness());
 * }</pre>
 *
 * <h2>Interpretation Output</h2>
 * <ul>
 *   <li><b>Explanation</b>: Natural language explanation of the pipeline</li>
 *   <li><b>Recommendations</b>: Actionable improvement suggestions</li>
 *   <li><b>Deployment Readiness</b>: Production readiness assessment</li>
 *   <li><b>Feature Insights</b>: Analysis of feature importance and data</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.tpot2.interpreter;
