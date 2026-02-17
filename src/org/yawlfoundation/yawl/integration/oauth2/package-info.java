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
 * OAuth2/OIDC enterprise integration for YAWL v6.0.0.
 *
 * <p>This package implements the full OAuth2 authorization framework and OpenID Connect
 * identity layer for securing YAWL workflow API access. Two grant flows are supported:
 *
 * <ul>
 *   <li><b>Authorization Code + PKCE</b> - for interactive (human) clients. The YAWL
 *       portal and designer tools use this flow to obtain access tokens on behalf of
 *       logged-in users. PKCE eliminates the need for a client secret in public clients.</li>
 *   <li><b>Client Credentials</b> - for machine-to-machine access. Autonomous agents,
 *       CI pipelines, and external service integrations use this flow to obtain tokens
 *       representing their own identity rather than a delegated user.</li>
 * </ul>
 *
 * <h2>RBAC Model</h2>
 * <p>YAWL roles are hierarchical:
 * <pre>
 *   yawl:admin
 *     |-- yawl:designer   (load/unload specs, manage participants)
 *     |-- yawl:operator   (launch/cancel cases, check-in/out work items)
 *     |-- yawl:monitor    (read-only: cases, work items, logs)
 *     |-- yawl:agent      (autonomous workflow processing)
 * </pre>
 *
 * <h2>Token Validation Pipeline</h2>
 * <p>Every inbound API request passes through:
 * <ol>
 *   <li>{@link org.yawlfoundation.yawl.integration.oauth2.OAuth2TokenValidator} -
 *       signature + expiry + issuer + audience checks</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.oauth2.RbacAuthorizationEnforcer} -
 *       scope-to-operation permission mapping</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.oauth2.OidcUserContext} -
 *       user identity extraction from OIDC claims for audit trails</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * <p>All configuration via environment variables:
 * <ul>
 *   <li>{@code YAWL_OAUTH2_ISSUER_URI} - OIDC provider base URI (e.g. Keycloak realm URL)</li>
 *   <li>{@code YAWL_OAUTH2_AUDIENCE} - required audience claim (default: {@code yawl-api})</li>
 *   <li>{@code YAWL_OAUTH2_JWKS_URI} - override JWKS endpoint (auto-discovered if omitted)</li>
 *   <li>{@code YAWL_OAUTH2_CLIENT_ID} - client ID for client-credentials flow</li>
 *   <li>{@code YAWL_OAUTH2_CLIENT_SECRET} - client secret (via secret manager in production)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.oauth2;
