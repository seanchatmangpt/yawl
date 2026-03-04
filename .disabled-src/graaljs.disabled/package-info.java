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
 * YAWL GraalJS Integration — in-process JavaScript execution for Java enterprise workflows.
 *
 * <h2>Architecture</h2>
 * <pre>
 * JavaScriptExecutionEngine  (main API)
 *   ├─ JavaScriptContextPool          thread-safe pool of GraalJS Contexts
 *   │    └─ JavaScriptExecutionContext  one GraalJS interpreter per pool slot
 *   ├─ JsTypeMarshaller               Java ↔ JavaScript type conversion
 *   └─ JavaScriptSandboxConfig        security policy (STRICT/STANDARD/PERMISSIVE/forWasm)
 * </pre>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
 *     .sandboxed(true)
 *     .contextPoolSize(4)
 *     .build();
 *
 * String greeting = engine.evalToString("'Hello from GraalJS ' + 42");
 * Map<String, Object> data = engine.evalToMap("({ name: 'YAWL', version: 6 })");
 * engine.close();
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>All public methods are thread-safe. The internal {@link org.yawlfoundation.yawl.graaljs.JavaScriptContextPool}
 * distributes concurrent requests across pooled GraalJS contexts.</p>
 *
 * <h2>Runtime requirement</h2>
 * <p>GraalVM JDK 24.1+ must be used. On standard JDK, all eval* calls throw
 * {@link org.yawlfoundation.yawl.graaljs.JavaScriptException} with kind {@code RUNTIME_NOT_AVAILABLE}.</p>
 *
 * @see org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine
 */
@NullMarked
package org.yawlfoundation.yawl.graaljs;

import org.jspecify.annotations.NullMarked;
