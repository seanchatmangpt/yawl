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
 * Embedded QLever SPARQL engine integration via Java 25 Panama FFM.
 *
 * <p>This package provides in-process QLever SPARQL query execution for
 * sub-100µs latency, eliminating HTTP overhead compared to the remote
 * {@link org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverSparqlEngine}.</p>
 *
 * <h2>Key Components</h2>
 * <table border="1">
 *   <tr><th>Class</th><th>Description</th></tr>
 *   <tr>
 *     <td>{@link org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine}</td>
 *     <td>Main entry point — implements SparqlEngine interface</td>
 *   </tr>
 *   <tr>
 *     <td>{@link org.yawlfoundation.yawl.qlever.QLeverFfiBindings}</td>
 *     <td>Panama FFM bindings to native libqlever_ffi</td>
 *   </tr>
 *   <tr>
 *     <td>{@link org.yawlfoundation.yawl.qlever.QLeverFfiException}</td>
 *     <td>Runtime exception for FFI-level failures</td>
 *   </tr>
 * </table>
 *
 * <h2>Architecture</h2>
 * <p>Uses the Hourglass pattern for native integration:</p>
 * <pre>
 * Java (QLeverEmbeddedSparqlEngine)
 *         ↓ Panama FFM
 * C Façade (libqlever_ffi.so - extern "C" wrappers)
 *         ↓ C++ linkage
 * QLever C++ Core (Index, QueryPlanner, ResultTable)
 * </pre>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><b>Query latency:</b> &lt;100µs (vs 10-100ms for HTTP)</li>
 *   <li><b>Memory:</b> Zero-copy result transfer via FFM MemorySegment</li>
 *   <li><b>CPU:</b> No serialization/deserialization overhead</li>
 *   <li><b>Deployment:</b> Single JVM process, no separate QLever server</li>
 * </ul>
 *
 * <h2>Requirements</h2>
 * <ul>
 *   <li>Java 25+ with {@code --enable-preview --enable-native-access=ALL-UNNAMED}</li>
 *   <li>Pre-built QLever index directory</li>
 *   <li>{@code libqlever_ffi.so/dylib/dll} in {@code java.library.path}</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * {@snippet :
 * Path indexPath = Path.of("/var/lib/qlever/workflow-index");
 * try (SparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
 *     String turtle = engine.constructToTurtle("""
 *         PREFIX workflow: <http://yawl.io/workflow#>
 *         CONSTRUCT { ?case workflow:status ?status }
 *         WHERE { ?case workflow:status ?status }
 *         LIMIT 100
 *         """);
 *     System.out.println(turtle);
 * }
 * }
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 * @see org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngine
 * @see org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverSparqlEngine
 */
package org.yawlfoundation.yawl.qlever;
