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
 * Provides for the engine-side creation, authentication and maintenance of logon
 * accounts and sessions (for custom services and external applications).
 *
 * <h2>v6.0.0-GA Features</h2>
 * <p>This package includes enhanced identity management and security capabilities:</p>
 * <ul>
 *   <li><strong>OAuth 2.0 PKCE Support</strong>: Secure authorization code flow
 *       with Proof Key for Code Exchange to prevent authorization code interception</li>
 *   <li><strong>JWT Refresh Token Mechanism</strong>: Automatic token refresh
 *       maintaining session continuity while rotating access credentials</li>
 *   <li><strong>Multi-Tenant Session Isolation</strong>: Strict separation of
 *       authentication contexts across organizational boundaries</li>
 *   <li><strong>OpenID Connect Integration</strong>: Standardized federated
 *       authentication with identity providers</li>
 * </ul>
 * <p>Integration with {@link org.yawlfoundation.yawl.integration.oauth2 OAuth2} package
 * provides comprehensive OAuth 2.0 and OpenID Connect implementations.</p>
 *
 * @since 6.0.0-GA
 */package org.yawlfoundation.yawl.authentication;
