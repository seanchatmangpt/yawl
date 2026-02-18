/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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
 * Metrics collection for YAWL resilience patterns.
 *
 * <p>This package provides Micrometer-based metrics collection for monitoring
 * resilience pattern behavior and performance.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.resilience.metrics.ResilienceMetricsCollector} -
 *       Collector for circuit breaker, retry, rate limiter, and bulkhead metrics</li>
 * </ul>
 *
 * <p>Metrics exposed:</p>
 * <ul>
 *   <li>Circuit breaker state transitions and call rates</li>
 *   <li>Retry attempts and success/failure rates</li>
 *   <li>Rate limiter availability and wait times</li>
 *   <li>Bulkhead concurrent execution and queue sizes</li>
 * </ul>
 */
package org.yawlfoundation.yawl.resilience.metrics;
