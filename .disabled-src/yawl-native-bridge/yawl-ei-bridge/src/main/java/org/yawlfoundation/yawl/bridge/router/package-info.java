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
 * Bridge Router Package for Native Call Routing.
 *
 * <p>This package implements the Three-Domain Native Bridge Pattern for YAWL,
 * routing ontology triples between different execution domains:</p>
 *
 * <ul>
 *   <li><strong>JVM Domain</strong> - Executes in-process via QLeverEngine</li>
 *   <li><strong>BEAM Domain</strong> - Executes via Erlang interface bridge</li>
 *   <li><strong>Direct Domain</strong> - Currently blocked for security</li>
 * </ul>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.bridge.router.CallPattern} - Execution domain enum</li>
 *   <li>{@link org.yawlfoundation.yawl.bridge.router.NativeCall} - Immutable triple record</li>
 *   <li>{@link org.yawlfoundation.yawl.bridge.router.BridgeRouter} - Main routing logic</li>
 *   <li>{@link org.yawlfoundation.yawl.bridge.router.RoutingResult} - Execution outcome container</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Create a router
 * try (BridgeRouter router = new BridgeRouter(true)) {
 *
 *     // Route a JVM call
 *     NativeCall jvmCall = NativeCall.of(
 *         "http://example.org/workflow/123",
 *         "http://schema.org/name",
 *         "Order Processing",
 *         CallPattern.JVM
 *     );
 *
 *     RoutingResult result = router.route(jvmCall);
 *     if (result.isSuccess()) {
 *         Object data = result.getResult();
 *         // Process successful result
 *     }
 *
 *     // Route a BEAM call
 *     NativeCall beamCall = NativeCall.of(
 *         "/path/to/data.json",
 *         "http://example.org/import",
 *         "log_import",
 *         CallPattern.BEAM
 *     );
 *
 *     result = router.route(beamCall);
 *     if (result.isFailure()) {
 *         System.err.println("Error: " + result.getErrorMessage());
 *     }
 * }
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>All components are thread-safe and designed for concurrent execution.
 * The BridgeRouter uses structured concurrency for proper task management.</p>
 *
 * <h3>Error Handling</h3>
 * <p>Routing failures are wrapped in {@link org.yawlfoundation.yawl.bridge.router.BridgeRoutingException}
 * which provides contextual information about the failed call and pattern.</p>
 *
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.bridge.router;