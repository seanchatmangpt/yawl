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
 * Metrics and health indicators for YAWL interfaces.
 *
 * <h2>Package Overview</h2>
 * <p>This package provides OpenTelemetry-based metrics and Spring Boot Actuator
 * health indicators for monitoring all YAWL interfaces.</p>
 *
 * <h2>YAWL Interfaces</h2>
 * <table border="1">
 *   <tr><th>Interface</th><th>Purpose</th><th>Primary Metrics</th></tr>
 *   <tr>
 *     <td>Interface A</td>
 *     <td>Design and management - specification uploads, account management</td>
 *     <td>Request count, latency</td>
 *   </tr>
 *   <tr>
 *     <td>Interface B</td>
 *     <td>Work item client - task checkout, checkin, case management</td>
 *     <td>Request count, latency, work items processed</td>
 *   </tr>
 *   <tr>
 *     <td>Interface E</td>
 *     <td>Log gateway - process analytics, event queries</td>
 *     <td>Query count, query latency</td>
 *   </tr>
 *   <tr>
 *     <td>Interface X</td>
 *     <td>Exception handling - event notifications to external services</td>
 *     <td>Notifications, retries, failures</td>
 *   </tr>
 * </table>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.metrics.InterfaceMetrics} -
 *       Central metrics provider for all interfaces using OpenTelemetry</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.metrics.InterfaceXHealthIndicator} -
 *       Health indicator for Interface X with circuit breaker and dead letter queue monitoring</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.metrics.ResilienceHealthIndicator} -
 *       Aggregate health indicator for all Resilience4j patterns</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
package org.yawlfoundation.yawl.engine.interfce.metrics;
