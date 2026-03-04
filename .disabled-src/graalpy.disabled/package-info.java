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
 * YAWL GraalPy Integration — in-process Python execution for Java enterprise workflows.
 *
 * <p>This package provides a seamless, high-performance, and secure mechanism for YAWL
 * workflow tasks to execute Python code directly in the JVM memory space, targeting
 * integration with Python's AI, ML, and Data Science ecosystems.</p>
 *
 * <h2>Architecture</h2>
 * <p>The integration uses the GraalVM Polyglot API (GraalPy) to run Python 3 code
 * inside the JVM without inter-process communication overhead:</p>
 *
 * <pre>
 * Java Workflow Task
 *   └─ PythonExecutionEngine      (main API)
 *        ├─ PythonContextPool     (thread-safe Context lifecycle)
 *        │    └─ PythonExecutionContext  (wraps GraalVM Context)
 *        ├─ TypeMarshaller        (Java ↔ Python type conversion)
 *        ├─ PythonSandboxConfig   (POSIX emulation, security isolation)
 *        ├─ PythonBytecodeCache   (compiled .pyc caching)
 *        └─ PythonVirtualEnvironment  (pip/venv management)
 *
 * PythonInterfaceGenerator        (generates typed Java interfaces from .pyi stubs)
 * </pre>
 *
 * <h2>Runtime Requirement</h2>
 * <p><strong>GraalVM JDK 24.1+ is required at runtime</strong> for Python execution.
 * The Polyglot API compiles on any JDK 21+, but {@code Context.create("python")}
 * throws {@link org.graalvm.polyglot.PolyglotException} if the GraalPy language
 * implementation is not on the classpath. See the GraalVM documentation for
 * installation instructions.</p>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Create engine (reuse across calls)
 * PythonExecutionEngine engine = PythonExecutionEngine.builder()
 *     .sandboxed(true)
 *     .contextPoolSize(4)
 *     .build();
 *
 * // Execute Python and get result
 * Object result = engine.eval("'Hello from Python ' + str(42)");
 * // result = "Hello from Python 42"
 *
 * // Invoke a Python function from a module
 * engine.evalScript(Path.of("sentiment.py"));
 * double score = (double) engine.invokePythonFunction("sentiment", "analyze", "text");
 *
 * engine.close();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see org.yawlfoundation.yawl.graalpy.PythonExecutionEngine
 * @see org.yawlfoundation.yawl.graalpy.PythonInterfaceGenerator
 */
@NullMarked
package org.yawlfoundation.yawl.graalpy;

import org.jspecify.annotations.NullMarked;
