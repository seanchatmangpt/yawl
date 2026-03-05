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
 */
package org.yawlfoundation.yawl.engine.observability;
