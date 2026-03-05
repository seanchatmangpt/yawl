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
 * Kong Gateway configuration generator for YAWL v6.0.0.
 *
 * <p>This package provides declarative Kong Gateway configuration generation
 * for deploying YAWL behind Kong. Generates {@code kong.yaml} declarative config
 * with routes, services, and plugins for rate limiting, authentication, and
 * observability.
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.gateway.kong.KongConfigurationGenerator} -
 *       Generates complete Kong declarative configuration from YAWL endpoint metadata</li>
 * </ul>
 *
 * <h2>Generated Configuration Structure</h2>
 * <pre>{@code
 * _format_version: "3.0"
 * services:
 *   - name: yawl-engine
 *     url: http://yawl-engine:8080
 *     routes:
 *       - name: case-operations
 *         paths: [/yawl/case]
 *         plugins:
 *           - name: rate-limiting
 *             config:
 *               minute: 100
 *               policy: local
 *           - name: jwt
 *           - name: correlation-id
 *       - name: spec-operations
 *         paths: [/yawl/spec]
 *         plugins:
 *           - name: rate-limiting
 *             config:
 *               minute: 600
 * }</pre>
 *
 * <h2>Plugins Configured</h2>
 * <ul>
 *   <li><b>rate-limiting</b> - Tiered rate limits (critical: 10/min, standard: 100/min, read: 600/min)</li>
 *   <li><b>jwt</b> - JWT authentication with RS256/HS256 support</li>
 *   <li><b>request-transformer</b> - Header injection (X-Request-ID, X-Forwarded-For)</li>
 *   <li><b>response-transformer</b> - CORS headers, security headers</li>
 *   <li><b>correlation-id</b> - Request tracing across services</li>
 *   <li><b>prometheus</b> - Metrics exposure for monitoring</li>
 *   <li><b>circuit-breaker</b> - Fault tolerance (Kong Enterprise)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Generate Kong configuration
 * KongConfigurationGenerator generator = new KongConfigurationGenerator();
 * String kongYaml = generator.generate(
 *     "http://yawl-engine:8080",
 *     RateLimitConfig.defaultTiered(),
 *     AuthConfig.jwtWithIssuer("https://auth.example.com")
 * );
 *
 * // Write to file for Kong
 * Files.writeString(Path.of("kong.yaml"), kongYaml);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.integration.gateway.GatewayRouteDefinition
 * @see org.yawlfoundation.yawl.integration.gateway.GatewayCircuitBreakerConfig
 */
package org.yawlfoundation.yawl.integration.gateway.kong;
