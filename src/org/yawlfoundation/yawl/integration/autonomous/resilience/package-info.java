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
 * Resilience patterns for autonomous agent operations.
 *
 * <p>This package provides circuit breakers, retry policies, and fallback
 * handlers to ensure robust operation of autonomous agents when external
 * services are unavailable.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker} -
 *       Prevents cascading failures by failing fast when services are unhealthy</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.resilience.RetryPolicy} -
 *       Configurable retry with exponential backoff and jitter</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.resilience.FallbackHandler} -
 *       Provides fallback responses when operations fail</li>
 * </ul>
 *
 * <p>Circuit breaker states: CLOSED (normal), OPEN (failing fast), HALF_OPEN (testing recovery)</p>
 */
package org.yawlfoundation.yawl.integration.autonomous.resilience;
