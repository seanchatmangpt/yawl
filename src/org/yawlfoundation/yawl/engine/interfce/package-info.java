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
 * Provides interfaces between the engine and external services and applications, and
 * the structures and methods required to marshal information between them.
 *
 * <h3>Virtual Thread Support for Interfaces (v6.0.0-GA)</h3>
 * <p>Interface layer optimized for Java 21+ virtual thread execution:
 * <ul>
 *   <li>Virtual thread-compatible external service communication patterns</li>
 *   <li>Asynchronous interface calls with virtual thread backpressure</li>
 *   <li>Thread-safe interface implementations for concurrent access</li>
 *   <li>Optimized network communication with virtual thread scheduling</li>
 * </ul>
 * Virtual thread integration enables seamless handling of millions of concurrent
 * external service interactions without resource exhaustion.</p>
 *
 * @since 6.0.0-GA
 * @author YAWL Foundation
 */
package org.yawlfoundation.yawl.engine.interfce;