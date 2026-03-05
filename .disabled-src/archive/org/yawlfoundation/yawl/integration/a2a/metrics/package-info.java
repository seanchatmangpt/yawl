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
 * Metrics collection for virtual thread monitoring in A2A server.
 *
 * <p>This package provides metrics collection and reporting for the
 * {@link org.yawlfoundation.yawl.integration.a2a.VirtualThreadYawlA2AServer},
 * enabling observability of virtual thread behavior and request processing.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.a2a.metrics.VirtualThreadMetrics} -
 *       Main metrics collector with thread-safe counters and latency tracking</li>
 * </ul>
 *
 * <h2>Metrics Available</h2>
 * <ul>
 *   <li><b>Request counts</b>: Total, successful, failed, and active requests</li>
 *   <li><b>Latency distribution</b>: Average, min, max, and percentiles (p50, p90, p95, p99)</li>
 *   <li><b>Thread statistics</b>: Total threads, daemon threads, peak threads</li>
 *   <li><b>Server uptime</b>: Time since server start</li>
 * </ul>
 *
 * <h2>Integration with Monitoring Systems</h2>
 * <p>The {@code VirtualThreadMetrics.toJson()} method provides JSON output suitable
 * for scraping by Prometheus, Datadog, or other monitoring systems.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>All classes in this package are designed for concurrent access from virtual threads.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
package org.yawlfoundation.yawl.integration.a2a.metrics;
