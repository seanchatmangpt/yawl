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
 * DSPy Fluent API - Java wrapper mirroring Python DSPy library (dspy==3.1.3).
 *
 * <p>This package provides a fluent, type-safe Java API that mirrors the Python DSPy
 * library. It follows the JOR4J (Java > OTP > Rust/Python > OTP > Java) pattern
 * for fault-tolerant polyglot integration.
 *
 * <h2>Key Classes:</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.dspy.fluent.Dspy} - Main entry point (mirrors Python {@code import dspy})</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.fluent.DspyLM} - Language model configuration</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.fluent.DspySignature} - Input/output contract</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.fluent.DspyModule} - Executable prediction module</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.fluent.DspyExample} - Few-shot training example</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.fluent.DspyResult} - Prediction result</li>
 * </ul>
 *
 * <h2>Quick Start:</h2>
 * <pre>{@code
 * import org.yawlfoundation.yawl.dspy.fluent.*;
 *
 * // Configure
 * Dspy.configure(lm -> lm
 *     .model("groq/gpt-oss-20b")
 *     .apiKey(System.getenv("GROQ_API_KEY"))
 *     .temperature(0.0));
 *
 * // Define signature
 * DspySignature sig = Dspy.signature("question -> answer");
 *
 * // Create predictor
 * DspyModule predictor = Dspy.predict(sig);
 *
 * // Run prediction
 * DspyResult result = predictor.predict("question", "What is YAWL?");
 * String answer = result.getString("answer");
 * }</pre>
 *
 * <h2>Python → Java Mapping:</h2>
 * <table border="1">
 *   <tr><th>Python</th><th>Java</th></tr>
 *   <tr><td>{@code dspy.LM(model, api_key)}</td><td>{@code Dspy.lm().model(model).apiKey(key)}</td></tr>
 *   <tr><td>{@code dspy.configure(lm=lm)}</td><td>{@code Dspy.configure(lm -> lm...)}</td></tr>
 *   <tr><td>{@code class Sig(dspy.Signature)}</td><td>{@code Dspy.signature("input -> output")}</td></tr>
 *   <tr><td>{@code dspy.Predict(signature)}</td><td>{@code Dspy.predict(signature)}</td></tr>
 *   <tr><td>{@code dspy.ChainOfThought(sig)}</td><td>{@code Dspy.chainOfThought(signature)}</td></tr>
 *   <tr><td>{@code dspy.Example(input=..., output=...)}</td><td>{@code Dspy.example().input(k,v).output(k,v)}</td></tr>
 *   <tr><td>{@code BootstrapFewShot().compile()}</td><td>{@code Dspy.bootstrap().compile(program)}</td></tr>
 *   <tr><td>{@code program(**inputs)}</td><td>{@code program.predict("key", value)}</td></tr>
 * </table>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see <a href="https://dspy.ai/">DSPy Documentation</a>
 */
package org.yawlfoundation.yawl.dspy.fluent;
