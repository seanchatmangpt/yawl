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
 * The main engine package, handling the execution of process instances, their
 * nets and tasks. Also responsible for process persistence and event announcements.
 *
 * <h3>Virtual Thread Support (v6.0.0-GA)</h3>
 * <p>The YAWL engine now supports Java 21+ virtual threads for improved scalability:
 * <ul>
 *   <li>Per-case virtual threads for task execution</li>
 *   <li>Virtual thread pools for work item processing</li>
 *   <li>Structured concurrency for parallel task processing</li>
 *   <li>Efficient thread management without manual pooling</li>
 * </ul>
 * Virtual threads enable handling of millions of concurrent workflow instances
 * with minimal resource consumption.</p>
 *
 * <h3>GRPO Integration (v6.0.0-GA)</h3>
 * <p>Support for Guarded Resource Pool Optimization (GRPO) patterns:
 * <ul>
 *   <li>Resource-aware task scheduling with QoS guarantees</li>
 *   <li>Dynamic resource allocation based on case priorities</li>
 *   <li>Resource contention management and deadlock prevention</li>
 *   <li>Resource usage metrics and optimization feedback loops</li>
 * </ul>
 * GRPO ensures optimal resource utilization while maintaining workflow integrity.</p>
 *
 * @since 6.0.0-GA
 * @author YAWL Foundation
 */
package org.yawlfoundation.yawl.engine;