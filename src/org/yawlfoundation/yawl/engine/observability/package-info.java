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
 * OpenTelemetry observability integration for the YAWL engine.
 *
 * <p>This package provides distributed tracing, metrics collection, and
 * telemetry for YAWL workflow operations. It integrates with OpenTelemetry
 * for production-grade observability.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.observability.YAWLTelemetry} - Central telemetry provider</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.observability.YAWLTracing} - Distributed tracing support</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.observability.OpenTelemetryConfig} - OTLP configuration</li>
 * </ul>
 *
 * <h3>Virtual Thread Metrics Documentation (v6.0.0-GA)</h3>
 * <p>Enhanced observability for Java 21+ virtual thread environments:
 * <ul>
 *   <li>Virtual thread count, creation, and termination metrics</li>
 *   <li>Per-workflow virtual thread utilization tracking</li>
 *   <li>Virtual thread pinning detection and alerting</li>
 *   <li>Virtual thread pool monitoring and optimization metrics</li>
 *   <li>Structured concurrency visualization in distributed traces</li>
 * </ul>
 * Virtual thread metrics provide insights into workflow scalability and
 * resource utilization patterns.</p>
 *
 * @since 6.0.0-GA
 * @author YAWL Foundation
 */
package org.yawlfoundation.yawl.engine.observability;
