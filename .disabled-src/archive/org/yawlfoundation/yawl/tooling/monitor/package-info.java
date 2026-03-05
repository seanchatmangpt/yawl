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
 * Monitoring and observability tools for YAWL v6.0.0.
 *
 * <p>This package provides monitoring dashboards, alerting integration,
 * and real-time observability for YAWL workflow execution. Integrates
 * with OpenTelemetry, Prometheus, and popular monitoring platforms.
 *
 * <h2>Monitoring Capabilities</h2>
 * <ul>
 *   <li><b>Real-time Dashboards</b> - Live workflow execution visualization</li>
 *   <li><b>Alert Rules</b> - Configurable alerts for SLA violations</li>
 *   <li><b>Metrics Collection</b> - Prometheus-compatible metrics export</li>
 *   <li><b>Trace Analysis</b> - Distributed trace visualization and analysis</li>
 *   <li><b>Health Checks</b> - Liveness and readiness probes for orchestration</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li><b>Prometheus</b> - Metrics scraping endpoint at {@code /metrics}</li>
 *   <li><b>Grafana</b> - Pre-built dashboard templates</li>
 *   <li><b>Jaeger/Zipkin</b> - Distributed trace visualization</li>
 *   <li><b>AlertManager</b> - Alert routing and notification</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.observability.YawlMetrics
 * @see org.yawlfoundation.yawl.observability.HealthCheckEndpoint
 */
package org.yawlfoundation.yawl.tooling.monitor;
