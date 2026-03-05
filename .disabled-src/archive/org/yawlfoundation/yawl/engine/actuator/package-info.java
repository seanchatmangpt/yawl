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
 * Spring Boot Actuator integration for YAWL engine cloud-native deployments.
 *
 * <p>This package provides production-ready health checks, readiness probes,
 * liveness probes, and metrics collection for deploying YAWL on Kubernetes,
 * Google Cloud Run, and other cloud platforms.
 *
 * <h2>Health Endpoints</h2>
 * <ul>
 *   <li>/actuator/health - Overall system health</li>
 *   <li>/actuator/health/liveness - Process liveness (Kubernetes liveness probe)</li>
 *   <li>/actuator/health/readiness - Traffic readiness (Kubernetes readiness probe)</li>
 * </ul>
 *
 * <h2>Metrics Endpoints</h2>
 * <ul>
 *   <li>/actuator/metrics - JSON metrics</li>
 *   <li>/actuator/prometheus - Prometheus format metrics</li>
 * </ul>
 *
 * <h2>Health Indicators</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.actuator.health.YEngineHealthIndicator} - Engine state and capacity</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.actuator.health.YDatabaseHealthIndicator} - Database connectivity</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.actuator.health.YExternalServicesHealthIndicator} - A2A/MCP agent health</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.actuator.health.YLivenessHealthIndicator} - Process liveness</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.actuator.health.YReadinessHealthIndicator} - Traffic readiness</li>
 * </ul>
 *
 * <h2>Metrics Collectors</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.actuator.metrics.YWorkflowMetrics} - Workflow execution metrics</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.actuator.metrics.YAgentPerformanceMetrics} - Agent performance metrics</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.actuator.metrics.YResourceMetrics} - JVM resource metrics</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Kubernetes deployment.yaml
 * livenessProbe:
 *   httpGet:
 *     path: /actuator/health/liveness
 *     port: 8080
 *   initialDelaySeconds: 60
 *   periodSeconds: 10
 *
 * readinessProbe:
 *   httpGet:
 *     path: /actuator/health/readiness
 *     port: 8080
 *   initialDelaySeconds: 30
 *   periodSeconds: 5
 * }</pre>
 *
 * @see org.springframework.boot.actuate.health.HealthIndicator
 * @see io.micrometer.core.instrument.MeterRegistry
 * @since 5.2
 */
package org.yawlfoundation.yawl.engine.actuator;
