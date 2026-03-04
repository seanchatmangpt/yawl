/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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
 * Bridge Routing Logic for Three-Domain Native Bridge Pattern.
 *
 * <p>This package contains the central routing component that enforces the
 * Three-Domain Native Bridge Pattern by analyzing NativeCall triples and
 * routing them to the appropriate domain based on callPattern requirements.</p>
 *
 * <h3>Key Components:</h3>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.router.BridgeRouter} -
 *       Central router that enforces domain separation</li>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.router.NativeCall} -
 *       Ontology-derived call triple representation</li>
 *   <li>Call pattern routing: "jvm", "beam", or "direct"</li>
 *   <li>Error handling with {@link org.yawlfoundation.yawl.nativebridge.router.BridgeException}</li>
 * </ul>
 *
 * <h3>Call Pattern Routing Logic:</h3>
 * <table>
 *   <tr><th>callPattern</th><th>Path</th><th>Latency</th><th>Use Case</th></tr>
 *   <tr><td>"jvm"</td><td>JVM → Panama FFM → native library</td><td>~10ns</td><td>QLever SPARQL queries</td></tr>
 *   <tr><td>"beam"</td><td>JVM → Unix socket → BEAM → NIF</td><td>~5-20µs</td><td>Process mining capabilities</td></tr>
 *   <tr><td>"direct"</td><td>JVM → Panama FFM → rust4pm</td><td>~100ns</td><td>Never used (escape valve)</td></tr>
 * </table>
 *
 * <h3>Cross-Domain Contract Enforcement:</h3>
 * <ul>
 *   <li>"jvm" pattern: In-process only, no boundary crossing</li>
 *   <li>"beam" pattern: Crosses Boundary A, maintains isolation</li>
 *   <li>"direct" pattern: Architectural escape valve (disabled)</li>
 * </ul>
 *
 * <h3>Registry Kind Routing:</h3>
 * <ul>
 *   <li>"inline": Short-lived objects, no registry needed</li>
 *   <li>"OcelId", "SlimOcelId", "PetriNetId": Mnesia-backed persistent handles</li>
 *   <li>Automatic serialization/deserialization of UUID handles</li>
 * </ul>
 */
package org.yawlfoundation.yawl.nativebridge.router;