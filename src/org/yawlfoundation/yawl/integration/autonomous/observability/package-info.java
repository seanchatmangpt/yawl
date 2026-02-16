/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

/**
 * Production observability components for autonomous YAWL agents.
 *
 * <p>This package provides comprehensive observability for production monitoring:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.observability.MetricsCollector} -
 *       Prometheus-compatible metrics with counters and histograms</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.observability.StructuredLogger} -
 *       Structured logging with correlation IDs and JSON formatting</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.observability.HealthCheck} -
 *       Health check endpoints for Kubernetes readiness/liveness probes</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Metrics collection
 * MetricsCollector metrics = new MetricsCollector(9090);  // HTTP on port 9090
 * Map<String, String> labels = Map.of("agent", "ordering", "domain", "Ordering");
 * metrics.incrementCounter("tasks_completed_total", labels);
 * metrics.recordDuration("task_duration_seconds", durationMs, labels);
 * // Access metrics at http://localhost:9090/metrics
 *
 * // Structured logging
 * StructuredLogger logger = new StructuredLogger(GenericPartyAgent.class);
 * String correlationId = logger.setCorrelationId(null);  // Auto-generate UUID
 * logger.setContext("agent", "ordering");
 * logger.logTaskStarted(taskId, contextMap);
 * logger.logTaskCompleted(taskId, durationMs, contextMap);
 *
 * // Health checks
 * HealthCheck health = new HealthCheck(
 *     "http://localhost:8080/yawl",  // YAWL engine URL
 *     "https://open.bigmodel.cn",    // ZAI API URL
 *     5000,                          // Timeout
 *     9091                           // HTTP port
 * );
 * health.registerCheck("database", () ->
 *     HealthCheck.CheckResult.healthy("DB connection OK")
 * );
 * // Access health at http://localhost:9091/health
 * }</pre>
 *
 * <h2>Prometheus Integration</h2>
 * <p>The MetricsCollector exports metrics in Prometheus text format at the {@code /metrics}
 * endpoint, compatible with Prometheus scraping and Grafana dashboards.
 *
 * <h2>Kubernetes Integration</h2>
 * <p>HealthCheck provides standard Kubernetes probe endpoints:
 * <ul>
 *   <li>{@code /health} - Full health status with all checks</li>
 *   <li>{@code /health/ready} - Readiness probe (200 = ready, 503 = not ready)</li>
 *   <li>{@code /health/live} - Liveness probe (always 200 unless process dead)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All components in this package are thread-safe. StructuredLogger uses SLF4J MDC
 * which is ThreadLocal-based, ensuring per-thread context isolation.
 *
 * @author YAWL Production Validator
 * @version 5.2
 * @since 5.2
 */
package org.yawlfoundation.yawl.integration.autonomous.observability;
