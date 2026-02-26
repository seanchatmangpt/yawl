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
 * Stateless YAWL engine implementation.
 * Provides a lightweight, event-driven workflow engine without persistent state,
 * suitable for embedded applications, testing, and scenarios requiring full control
 * over case lifecycle and serialization.
 *
 * <h3>Virtual Thread Advantages (v6.0.0-GA)</h3>
 * <p>The stateless engine leverages Java 21+ virtual threads for maximum efficiency:
 * <ul>
 *   <li>Virtual thread-based case processing eliminates thread synchronization overhead</li>
 *   <li>Automatic virtual thread lifecycle management for each workflow instance</li>
 *   <li>Massive horizontal scaling with millions of concurrent cases</li>
 *   <li>Zero thread pool configuration or tuning required</li>
 *   <li>Reduced memory footprint compared to traditional thread models</li>
 *   <li>Better resource utilization in cloud-native environments</li>
 * </ul>
 * Virtual threads enable unprecedented scalability for stateless workflow processing
 * while maintaining the simplicity and reliability of the stateless model.</p>
 *
 * @since 6.0.0-GA
 * @author YAWL Foundation
 */
package org.yawlfoundation.yawl.stateless;
