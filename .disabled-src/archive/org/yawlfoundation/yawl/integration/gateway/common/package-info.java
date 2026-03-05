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
 * Common gateway abstractions for YAWL v6.0.0.
 *
 * <p>This package provides shared interfaces, configurations, and utilities
 * used across all gateway integration implementations (Kong, Traefik, AWS).
 *
 * <h2>Shared Components</h2>
 * <ul>
 *   <li><b>Route Abstractions</b> - Common route definition interfaces</li>
 *   <li><b>Authentication Models</b> - Shared auth configuration types</li>
 *   <li><b>Rate Limiting Config</b> - Tiered rate limit definitions</li>
 *   <li><b>Health Check Models</b> - Backend health check configuration</li>
 * </ul>
 *
 * <h2>Rate Limiting Tiers</h2>
 * <p>Standard YAWL rate limiting tiers used across all gateways:
 * <ul>
 *   <li><b>Critical</b> - 10 req/min (case launch, spec load)</li>
 *   <li><b>Standard</b> - 100 req/min (work item operations)</li>
 *   <li><b>Read-only</b> - 600 req/min (status queries, list operations)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.integration.gateway.GatewayRouteDefinition
 * @see org.yawlfoundation.yawl.integration.gateway.GatewayCircuitBreakerConfig
 */
package org.yawlfoundation.yawl.integration.gateway.common;
