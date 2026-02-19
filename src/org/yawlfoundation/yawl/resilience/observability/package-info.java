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
 * Observability utilities for YAWL resilience patterns.
 *
 * <p>This package provides comprehensive observability for fallback operations,
 * including distributed tracing, metrics collection, and alerting.</p>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.resilience.observability.FallbackObservability} -
 *       Central class for tracking fallback invocations with OTEL spans and P1 Andon alerts</li>
 * </ul>
 *
 * <h2>Metrics Exposed</h2>
 * <ul>
 *   <li>{@code yawl_fallback_invocations_total} - Counter by operation, component, reason</li>
 *   <li>{@code yawl_fallback_stale_data_served_total} - Counter of stale data served</li>
 *   <li>{@code yawl_fallback_data_freshness_seconds} - Gauge of data age</li>
 *   <li>{@code yawl_fallback_operation_duration} - Timer for fallback operations</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li>{@code org.yawlfoundation.yawl.integration.pool.YawlConnectionPool} - Connection fallback tracking</li>
 *   <li>{@code org.yawlfoundation.yawl.integration.autonomous.resilience.FallbackHandler} - Generic fallback handling</li>
 *   <li>{@code org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker} - Circuit breaker fallbacks</li>
 *   <li>{@code org.yawlfoundation.yawl.resilience.provider.YawlResilienceProvider} - Resilience4j integration</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @see org.yawlfoundation.yawl.resilience.observability.FallbackObservability
 */
package org.yawlfoundation.yawl.resilience.observability;
