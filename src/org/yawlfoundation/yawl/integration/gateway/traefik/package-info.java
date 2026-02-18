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
 * Traefik Gateway configuration generator for YAWL v6.0.0.
 *
 * <p>This package provides dynamic Traefik configuration generation for
 * deploying YAWL behind Traefik reverse proxy. Generates YAML/TOML provider
 * rules with middlewares for rate limiting, authentication, retry, and
 * circuit breaker patterns.
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.gateway.traefik.TraefikConfigurationGenerator} -
 *       Generates Traefik dynamic configuration from YAWL endpoint metadata</li>
 * </ul>
 *
 * <h2>Generated Configuration Structure (YAML)</h2>
 * <pre>{@code
 * http:
 *   routers:
 *     yawl-case-router:
 *       rule: "PathPrefix(`/yawl/case`)"
 *       service: yawl-engine
 *       middlewares:
 *         - yawl-rate-limit
 *         - yawl-auth
 *         - yawl-headers
 *       tls:
 *         certResolver: letsencrypt
 *
 *   services:
 *     yawl-engine:
 *       loadBalancer:
 *         servers:
 *           - url: "http://yawl-engine:8080"
 *         healthCheck:
 *           path: /yawl/health
 *           interval: 10s
 *
 *   middlewares:
 *     yawl-rate-limit:
 *       rateLimit:
 *         average: 100
 *         burst: 200
 *         period: 1m
 *     yawl-auth:
 *       forwardAuth:
 *         address: http://auth-service/verify
 *         trustForwardHeader: true
 *     yawl-circuit-breaker:
 *       circuitBreaker:
 *         expression: "NetworkErrorRatio() > 0.5"
 * }</pre>
 *
 * <h2>Middlewares Configured</h2>
 * <ul>
 *   <li><b>stripPrefix</b> - Remove path prefixes for backend routing</li>
 *   <li><b>rateLimit</b> - Configurable rate limiting per endpoint tier</li>
 *   <li><b>basicAuth</b> - HTTP Basic authentication (development)</li>
 *   <li><b>forwardAuth</b> - External authentication service (OIDC, custom)</li>
 *   <li><b>retry</b> - Automatic retry with exponential backoff</li>
 *   <li><b>circuitBreaker</b> - Fault tolerance with configurable thresholds</li>
 *   <li><b>headers</b> - Security headers, CORS configuration</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Generate Traefik configuration
 * TraefikConfigurationGenerator generator = new TraefikConfigurationGenerator();
 * String traefikYaml = generator.generate(
 *     "http://yawl-engine:8080",
 *     RateLimitConfig.defaultTiered(),
 *     AuthConfig.forwardAuth("http://auth:8080/verify")
 * );
 *
 * // Write to Traefik dynamic config directory
 * Files.writeString(Path.of("/etc/traefik/dynamic/yawl.yml"), traefikYaml);
 * }</pre>
 *
 * <h2>Deployment Integration</h2>
 * <ul>
 *   <li><b>Docker Labels</b> - Generate container labels for Docker provider</li>
 *   <li><b>Kubernetes IngressRoute</b> - CRD configuration for Traefik Helm chart</li>
 *   <li><b>File Provider</b> - Static YAML/TOML files for manual deployment</li>
 *   <li><b>Consul/Etcd</b> - Dynamic configuration via key-value store</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.integration.gateway.GatewayRouteDefinition
 * @see org.yawlfoundation.yawl.integration.gateway.GatewayCircuitBreakerConfig
 */
package org.yawlfoundation.yawl.integration.gateway.traefik;
