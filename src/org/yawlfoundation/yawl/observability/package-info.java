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
 * OpenTelemetry observability integration for YAWL v6.0.0.
 *
 * <p>This package provides production-ready observability through OpenTelemetry (OTEL)
 * for distributed tracing, metrics collection, and structured logging. All telemetry
 * is exported via OTLP (OpenTelemetry Protocol) to compatible backends.
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.observability.OpenTelemetryInitializer} -
 *       Bootstrap OTEL SDK with OTLP exporters, configures tracing and metrics pipelines</li>
 *   <li>{@link org.yawlfoundation.yawl.observability.YawlMetrics} -
 *       Workflow-specific metrics: case duration, work item throughput, engine health</li>
 *   <li>{@link org.yawlfoundation.yawl.observability.WorkflowSpanBuilder} -
 *       Creates distributed trace spans for case lifecycle, work item processing,
 *       and engine operations with proper parent-child linkage</li>
 *   <li>{@link org.yawlfoundation.yawl.observability.StructuredLogger} -
 *       JSON-formatted logging with correlation IDs, workflow context, and OTEL integration</li>
 *   <li>{@link org.yawlfoundation.yawl.observability.HealthCheckEndpoint} -
 *       HTTP endpoint exposing liveness, readiness, and health status for Kubernetes probes</li>
 * </ul>
 *
 * <h2>Configuration (Environment Variables)</h2>
 * <pre>{@code
 * # Enable OpenTelemetry (default: true)
 * OTEL_ENABLED=true
 *
 * # OTLP endpoint for traces and metrics (default: http://localhost:4317)
 * OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
 *
 * # Service name for trace attribution
 * OTEL_SERVICE_NAME=yawl-engine
 *
 * # Sampling probability (0.0-1.0, default: 1.0 for dev, 0.1 for prod)
 * OTEL_TRACES_SAMPLER_ARG=0.1
 *
 * # Metrics export interval (milliseconds, default: 60000)
 * OTEL_METRIC_EXPORT_INTERVAL=60000
 * }</pre>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Initialize at application startup
 * OpenTelemetryInitializer.init();
 *
 * // Create workflow spans
 * Span caseSpan = WorkflowSpanBuilder.forCase(caseId)
 *     .withSpecId(specId)
 *     .withParentContext(parentContext)
 *     .start();
 *
 * // Record metrics
 * YawlMetrics.recordCaseLaunched(specId);
 * YawlMetrics.recordWorkItemCompleted(taskName, durationMs);
 *
 * // Structured logging with context
 * StructuredLogger.info("Case launched")
 *     .with("caseId", caseId)
 *     .with("specId", specId)
 *     .log();
 * }</pre>
 *
 * <h2>Metrics Exposed</h2>
 * <ul>
 *   <li><b>yawl.case.launched</b> - Counter of cases started (by spec ID)</li>
 *   <li><b>yawl.case.completed</b> - Counter of cases finished (by spec ID, outcome)</li>
 *   <li><b>yawl.case.duration</b> - Histogram of case execution time (milliseconds)</li>
 *   <li><b>yawl.workitem.created</b> - Counter of work items created (by task name)</li>
 *   <li><b>yawl.workitem.completed</b> - Counter of work items finished (by task name)</li>
 *   <li><b>yawl.workitem.duration</b> - Histogram of work item processing time</li>
 *   <li><b>yawl.engine.active_cases</b> - Gauge of currently running cases</li>
 *   <li><b>yawl.engine.active_workitems</b> - Gauge of pending work items</li>
 * </ul>
 *
 * <h2>Trace Attributes</h2>
 * <p>All workflow spans include standard semantic attributes:
 * <ul>
 *   <li>{@code yawl.case.id} - Unique case identifier</li>
 *   <li>{@code yawl.spec.id} - Specification identifier</li>
 *   <li>{@code yawl.spec.version} - Specification version</li>
 *   <li>{@code yawl.task.name} - Task/condition name</li>
 *   <li>{@code yawl.workitem.id} - Work item identifier</li>
 *   <li>{@code yawl.workitem.status} - Current status (created, started, completed, failed)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see io.opentelemetry.api.OpenTelemetry
 * @see io.opentelemetry.api.trace.Span
 * @see io.opentelemetry.api.metrics.Meter
 */
package org.yawlfoundation.yawl.observability;
