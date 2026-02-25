/**
 * Prometheus metrics collection and exposure for YAWL MCP-A2A application.
 *
 * <p>This package provides comprehensive metrics collection using Micrometer
 * with Prometheus exposition format. Metrics cover all aspects of the
 * MCP-A2A integration including:</p>
 *
 * <h2>Metrics Categories</h2>
 * <ul>
 *   <li><strong>MCP Metrics</strong>: Tool call counts, latencies, errors</li>
 *   <li><strong>A2A Metrics</strong>: Message counts, processing times, agent communication</li>
 *   <li><strong>Connection Pool Metrics</strong>: Active connections, pool utilization</li>
 *   <li><strong>Error Metrics</strong>: Error rates by component, failure patterns</li>
 *   <li><strong>Workflow Metrics</strong>: Active cases, work items, throughput</li>
 *   <li><strong>Health Metrics</strong>: Component health status, readiness probes</li>
 * </ul>
 *
 * <h2>Metric Naming Convention</h2>
 * <p>All metrics follow Prometheus naming best practices:</p>
 * <ul>
 *   <li>Prefix: {@code yawl_} for application-specific metrics</li>
 *   <li>Suffix: {@code _total} for counters, {@code _seconds} for durations</li>
 *   <li>Units: Base units (seconds, bytes) for Prometheus compatibility</li>
 *   <li>Labels: Low-cardinality dimensions only</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Metrics are configured via {@code application.yml}:</p>
 * <pre>{@code
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: prometheus,health,metrics
 *   metrics:
 *     tags:
 *       application: yawl-mcp-a2a-app
 *       version: 6.0.0-Beta
 *     distribution:
 *       percentiles-histogram:
 *         yawl_mcp_tool_duration_seconds: true
 *         yawl_a2a_message_duration_seconds: true
 * }</pre>
 *
 * @see MetricsConfiguration
 * @see MetricsService
 * @see io.micrometer.prometheus.PrometheusMeterRegistry
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.mcp.a2a.service.metrics;
