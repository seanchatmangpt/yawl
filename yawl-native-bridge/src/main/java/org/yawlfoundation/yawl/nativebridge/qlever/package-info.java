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
 * JVM/QLever Domain Layer Interfaces.
 *
 * <p>This package contains the Three-Domain Native Bridge interfaces for the JVM domain
 * QLever SPARQL engine. It implements sub-10ns in-process querying via Panama FFM.</p>
 *
 * <h3>Layer Structure:</h3>
 * <ul>
 *   <li>Layer 3: {@link org.yawlfoundation.yawl.nativebridge.qlever.QLeverEngine} -
 *       High-level SPARQL API (pure Java 25 interface)</li>
 *   <li>Layer 2: {@link org.yawlfoundation.yawl.nativebridge.qlever.NativeHandle} -
 *       Typed bridge with memory management and error conversion</li>
 *   <li>Layer 1: jextract-generated bindings from qlever_ffi.h</li>
 *   <li>Native: libqlever_ffi.so - C++ Hourglass façade over QLever engine</li>
 * </ul>
 *
 * <h3>Error Model:</h3>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.qlever.QLeverException} - Base exception</li>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.qlever.QLeverParseException} - Query parsing errors</li>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.qlever.QLeverSemanticException} - Query semantic errors</li>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.qlever.QLeverRuntimeException} - Runtime execution errors</li>
 * </ul>
 *
 * <h3>Memory Management:</h3>
 * <ul>
 *   <li>Shared Arena for engine lifetime (Arena.ofShared())</li>
 *   <li>Confined Arena for per-call operations (Arena.ofConfined())</li>
 *   <li>AutoCloseable for resource cleanup</li>
 *   <li>QLeverStatus → QLeverException conversion</li>
 * </ul>
 */
package org.yawlfoundation.yawl.nativebridge.qlever;