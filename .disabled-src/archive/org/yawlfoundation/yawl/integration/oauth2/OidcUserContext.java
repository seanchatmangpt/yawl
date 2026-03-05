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

package org.yawlfoundation.yawl.integration.oauth2;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Immutable OIDC user context extracted from a validated JWT access token.
 *
 * <p>Carries identity and authorization data for use in YAWL request processing,
 * audit logging, and RBAC authorization decisions. Created exclusively by
 * {@link OAuth2TokenValidator#validate(String)} after full token verification.
 *
 * <p>Scope conventions (defined in {@link YawlOAuth2Scopes}):
 * <ul>
 *   <li>{@code yawl:admin}    - full administrative access</li>
 *   <li>{@code yawl:designer} - specification load/unload, participant management</li>
 *   <li>{@code yawl:operator} - case launch/cancel, work item operations</li>
 *   <li>{@code yawl:monitor}  - read-only access to cases, work items, logs</li>
 *   <li>{@code yawl:agent}    - autonomous workflow agent access</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class OidcUserContext {

    private final String              subject;
    private final String              email;
    private final String              displayName;
    private final Set<String>         scopes;
    private final Set<String>         roles;
    private final Instant             tokenExpiry;
    private final Map<String, Object> rawClaims;

    /**
     * Construct an OIDC user context. All collections are defensively copied
     * and made unmodifiable.
     *
     * @param subject     JWT subject (user ID or service account ID)
     * @param email       email claim, may be null for service accounts
     * @param displayName name claim, may be null for service accounts
     * @param scopes      OAuth2 scopes granted to this token
     * @param roles       application roles from realm_access or resource_access claims
     * @param tokenExpiry token expiration instant
     * @param rawClaims   all JWT payload claims as string values for audit logging
     */
    public OidcUserContext(String subject, String email, String displayName,
                           Set<String> scopes, Set<String> roles,
                           Instant tokenExpiry, Map<String, Object> rawClaims) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (tokenExpiry == null) {
            throw new IllegalArgumentException("tokenExpiry must not be null");
        }
        this.subject     = subject;
        this.email       = email;
        this.displayName = displayName;
        this.scopes      = Collections.unmodifiableSet(scopes != null ? scopes : Set.of());
        this.roles       = Collections.unmodifiableSet(roles  != null ? roles  : Set.of());
        this.tokenExpiry = tokenExpiry;
        this.rawClaims   = Collections.unmodifiableMap(rawClaims != null ? rawClaims : Map.of());
    }

    // -------------------------------------------------------------------------
    // Identity accessors
    // -------------------------------------------------------------------------

    /** JWT subject claim - unique identity of the token holder. */
    public String getSubject() { return subject; }

    /** Email address, or null for service account tokens. */
    public String getEmail() { return email; }

    /** Display name, or null for service account tokens. */
    public String getDisplayName() { return displayName; }

    /** Token expiry instant. */
    public Instant getTokenExpiry() { return tokenExpiry; }

    /** All raw JWT payload claims as string values (suitable for audit logging). */
    public Map<String, Object> getRawClaims() { return rawClaims; }

    // -------------------------------------------------------------------------
    // Authorization accessors
    // -------------------------------------------------------------------------

    /** Granted OAuth2 scopes from the {@code scope} or {@code permissions} claim. */
    public Set<String> getScopes() { return scopes; }

    /** Application roles from {@code realm_access.roles} or {@code resource_access.*.roles}. */
    public Set<String> getRoles() { return roles; }

    /**
     * Returns true if this token grants the specified scope.
     *
     * @param scope scope to check (e.g. {@code "yawl:operator"})
     * @return true if the scope is present
     */
    public boolean hasScope(String scope) {
        return scopes.contains(scope)
            || scopes.contains(YawlOAuth2Scopes.ADMIN); // admin implies all scopes
    }

    /**
     * Returns true if this token carries the specified role.
     *
     * @param role role name to check
     * @return true if the role is present
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /**
     * Returns true if this is an admin token (has {@code yawl:admin} scope).
     *
     * @return true for admin tokens
     */
    public boolean isAdmin() {
        return scopes.contains(YawlOAuth2Scopes.ADMIN);
    }

    /**
     * Returns a log-safe string representation containing subject, scopes, and expiry.
     * Does NOT include email or raw claims which may contain PII.
     *
     * @return log-safe string
     */
    @Override
    public String toString() {
        return "OidcUserContext{subject='" + subject
             + "', scopes=" + scopes
             + ", roles=" + roles
             + ", expiry=" + tokenExpiry + '}';
    }
}
