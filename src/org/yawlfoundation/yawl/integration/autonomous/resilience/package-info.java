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
 * Resilience patterns for autonomous agent operations.
 *
 * <p>Provides fault tolerance primitives that agents use to handle
 * transient failures when communicating with the YAWL engine and
 * external services.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker} -
 *       Three-state circuit breaker (CLOSED/OPEN/HALF_OPEN) that fast-fails when a
 *       downstream service is unavailable, preventing cascade failures.</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.resilience.RetryPolicy} -
 *       Exponential backoff retry policy for transient failures with configurable
 *       max attempts and initial backoff duration.</li>
 * </ul>
 *
 * <p>All resilience components use {@link java.util.concurrent.locks.ReentrantLock}
 * instead of {@code synchronized} to avoid pinning virtual threads.</p>
 *
 * @since YAWL 6.0
 */
package org.yawlfoundation.yawl.integration.autonomous.resilience;
