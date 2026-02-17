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
 * Health checks and observability for autonomous agents.
 *
 * <p>This package provides production-ready health monitoring for autonomous
 * agent deployments, including HTTP endpoints for Kubernetes probes.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.observability.HealthCheck} -
 *       HTTP health check server with readiness and liveness probes</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.observability.MetricsCollector} -
 *       Prometheus-compatible metrics collection</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.observability.StructuredLogger} -
 *       JSON-structured logging for log aggregation</li>
 * </ul>
 *
 * <p>Health checks verify:</p>
 * <ul>
 *   <li>YAWL engine connectivity</li>
 *   <li>External service availability (Z.AI, etc.)</li>
 *   <li>Custom registered health indicators</li>
 * </ul>
 */
package org.yawlfoundation.yawl.integration.autonomous.observability;
