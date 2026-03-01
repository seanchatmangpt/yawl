/**
 * Actor monitoring and observability for YAWL.
 *
 * This package provides comprehensive monitoring capabilities for actor-based systems:
 *
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.observability.actor.ActorHealthMetrics} - Core metrics collection</li>
 *   <li>{@link org.yawlfoundation.yawl.observability.actor.ActorTracer} - Distributed tracing</li>
 *   <li>{@link org.yawlfoundation.yawl.observability.actor.ActorAlertManager} - Alert management</li>
 *   <li>{@link org.yawlfoundation.yawl.observability.actor.ActorDashboardData} - Dashboard data</li>
 *   <li>{@link org.yawlfoundation.yawl.observability.actor.ActorAnomalyDetector} - Anomaly detection</li>
 *   <li>{@link org.yawlfoundation.yawl.observability.actor.ActorObservabilityService} - Main orchestration service</li>
 * </ul>
 *
 * Features:
 * - Real-time actor health monitoring
 * - Distributed tracing for message flows
 * - Configurable alerting with thresholds
 * - Dashboard data for visualization
 * - Advanced anomaly detection algorithms
 * - Integration with existing YAWL observability
 *
 * <h2>Quick Start</h2>
 *
 * <pre>{@code
 * // Initialize the observability service
 * ActorObservabilityService.initialize(meterRegistry);
 *
 * // Record actor events
 * ActorObservabilityService.getInstance().recordActorCreated("actor-1", "worker");
 * ActorObservabilityService.getInstance().recordMessageProcessing("actor-1", "task", 1000000L, 1024L);
 *
 * // Get dashboard data
 * ActorDashboardData.DashboardOverview overview =
 *     ActorObservabilityService.getInstance().getDashboardOverview();
 * }</pre>
 *
 * <h2>Configuration</h2>
 *
 * The system can be configured through system properties:
 *
 * <ul>
 *   <li>{@code yawl.actor.metrics.interval} - Metrics collection interval in seconds (default: 10)</li>
 *   <li>{@code yawl.actor.health.check.interval} - Health check interval in seconds (default: 30)</li>
 *   <li>{@code yawl.actor.alerting.enabled} - Enable alerting (default: true)</li>
 *   <li>{@code yawl.actor.anomaly.enabled} - Enable anomaly detection (default: true)</li>
 *   <li>{@code yawl.actor.dashboard.enabled} - Enable dashboard (default: true)</li>
 * </ul>
 */
package org.yawlfoundation.yawl.observability.actor;