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
 * A2A server authentication framework.
 *
 * <p>Provides pluggable, layered authentication for the YAWL A2A server
 * (port 8081). Every inbound request must present verifiable credentials
 * before any workflow operation is executed.
 *
 * <h2>Architecture</h2>
 * <pre>
 * HTTP request
 *      │
 *      ▼
 * {@link org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider}
 *      │
 *      ├── {@link org.yawlfoundation.yawl.integration.a2a.auth.SpiffeAuthenticationProvider}
 *      │       mTLS - preferred for service-to-service
 *      │       Validates SPIFFE X.509 SVID from TLS client certificate
 *      │
 *      ├── {@link org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider}
 *      │       JWT Bearer (HS256) - for external agent clients
 *      │       Validates Authorization: Bearer header
 *      │
 *      └── {@link org.yawlfoundation.yawl.integration.a2a.auth.ApiKeyAuthenticationProvider}
 *              API Key (HMAC-SHA256) - for operational tooling
 *              Validates X-API-Key header
 * </pre>
 *
 * <h2>Key types</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.a2a.auth.A2AAuthenticationProvider}
 *       - the strategy interface implemented by each provider</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal}
 *       - immutable, verified identity produced on success; also implements
 *       {@link io.a2a.server.auth.User}</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.a2a.auth.A2AAuthenticationException}
 *       - thrown on failure; translated to HTTP 401 by the server</li>
 * </ul>
 *
 * <h2>Configuration (environment variables)</h2>
 * <pre>
 * A2A_AUTH_REQUIRED=true               # Hard-fail when no provider configured
 * A2A_JWT_SECRET=&lt;min-32-chars&gt;       # JWT HMAC-SHA256 signing key
 * A2A_JWT_ISSUER=yawl-auth             # Optional JWT issuer validation
 * A2A_API_KEY_MASTER=&lt;hex-string&gt;     # HMAC master key for API-key digests
 * A2A_API_KEY=&lt;raw-key&gt;              # Default API key (auto-registered)
 * A2A_SPIFFE_TRUST_DOMAIN=yawl.cloud  # Accepted SPIFFE trust domain
 * </pre>
 */
package org.yawlfoundation.yawl.integration.a2a.auth;
