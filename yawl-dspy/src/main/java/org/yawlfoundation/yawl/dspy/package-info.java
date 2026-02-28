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
 * DSPy (Declarative Self-Improving Language Models) integration for YAWL workflows.
 *
 * <h2>Overview</h2>
 * This package provides a Java API for executing DSPy programs within YAWL
 * workflow tasks via GraalPy. DSPy enables declarative specification of LLM-based
 * computation pipelines with automatic optimization and caching.
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li><strong>PythonDspyBridge</strong> — Main entry point; reuses PythonExecutionEngine
 *       to compile and execute DSPy programs.</li>
 *   <li><strong>DspyProgram</strong> — Serializable immutable model of a DSPy program
 *       with source code, input schema, and metadata.</li>
 *   <li><strong>DspyExecutionResult</strong> — Type-safe result model capturing
 *       output dictionary, trace, and metrics.</li>
 *   <li><strong>DspyExecutionMetrics</strong> — Observability data including
 *       compilation time, execution time, token counts, and quality scores.</li>
 *   <li><strong>DspyProgramCache</strong> — Thread-safe LRU cache for compiled
 *       DSPy programs (max 100 entries).</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Create bridge (once per JVM)
 * PythonDspyBridge bridge = new PythonDspyBridge(pythonEngine);
 *
 * // Define a DSPy program
 * DspyProgram program = DspyProgram.builder()
 *     .name("sentiment-analyzer")
 *     .source("""
 *         import dspy
 *         class SentimentAnalyzer(dspy.Module):
 *             def __init__(self):
 *                 self.classify = dspy.ChainOfThought("text -> sentiment")
 *             def forward(self, text):
 *                 return self.classify(text=text)
 *         """)
 *     .build();
 *
 * // Execute (compiled programs are cached)
 * DspyExecutionResult result = bridge.execute(program, Map.of(
 *     "text", "YAWL is fantastic!"
 * ));
 *
 * Map<String, Object> output = result.getOutput();
 * // output contains {"sentiment": "positive", ...}
 *
 * DspyExecutionMetrics metrics = result.getMetrics();
 * System.out.println("Compilation: " + metrics.compilationTimeMs() + "ms");
 * System.out.println("Execution: " + metrics.executionTimeMs() + "ms");
 * }</pre>
 *
 * <h2>Caching Strategy</h2>
 * <p>Compiled DSPy programs are cached in memory (LRU, max 100). The cache key is
 * derived from program name + source hash. Subsequent executions of the same program
 * skip compilation overhead.</p>
 *
 * <h2>Context Pooling</h2>
 * <p>The bridge reuses GraalPy execution contexts from the underlying
 * {@code PythonExecutionEngine}'s context pool. This enables concurrent execution
 * of multiple programs without blocking.</p>
 *
 * <h2>Error Handling</h2>
 * <p>DSPy runtime errors are wrapped in {@code PythonException}. Execution failures
 * include the DSPy traceback and source code for debugging.</p>
 *
 * <h2>Integration with Team 1 (dspy_powl_generator.py)</h2>
 * <p>This module provides the Java side of the DSPy-YAWL integration. Team 1
 * provides Python code (dspy_powl_generator.py) that generates DSPy programs.
 * The PythonDspyBridge consumes Python-generated program sources.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.dspy;
