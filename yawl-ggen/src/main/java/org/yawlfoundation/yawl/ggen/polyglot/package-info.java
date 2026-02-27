/**
 * Copyright 2004-2026 YAWL Foundation
 *
 * This package provides Java-Python interoperability capabilities through GraalPy integration,
 * enabling seamless execution of PM4Py process mining algorithms within YAWL workflows.
 *
 * <p>The polyglot package leverages Java 25 features including:
 * <ul>
 *   <li><strong>Virtual Threads</strong>: For efficient concurrent execution of Python-based process mining
 *       algorithms without blocking Java threads</li>
 *   <li><strong>Foreign Function & Memory API</strong>: Direct integration with Python's C API
 *       for high-performance data exchange</li>
 *   <li><strong>Pattern Matching for switch</strong>: Enhanced type-safe dispatch for Python object
 *       handling</li>
 * </ul>
 *
 * <p>Key components:
 * <ul>
 *   <li>PM4Py process mining integration for workflow analysis</li>
 *   <li>Seamless Java-Python data type conversion</li>
 *   <li>Isolated Python execution contexts for YAWL case instances</li>
 *   <li>GraalPy-based Python script compilation and execution</li>
 * </ul>
 *
 * <p>Since: 6.0.0-GA
 */
package org.yawlfoundation.yawl.ggen.polyglot;