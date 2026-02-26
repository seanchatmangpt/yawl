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
 * Provides for the management of work item timers.
 *
 * <h3>Virtual Thread Integration (v6.0.0-GA)</h3>
 * <p>Timer management optimized for Java 21+ virtual threads:
 * <ul>
 *   <li>Virtual thread-based timer scheduling for improved scalability</li>
 *   <li>Efficient timer allocation without thread pool management</li>
 *   <li>Virtual thread-compatible timer events and notifications</li>
 *   <li>Reduced timer scheduling overhead for large-scale workflows</li>
 * </ul>
 * Virtual thread integration ensures efficient timer management even with
 * millions of concurrent timers across numerous workflow instances.</p>
 *
 * @since 6.0.0-GA
 * @author YAWL Foundation
 */
package org.yawlfoundation.yawl.engine.time;