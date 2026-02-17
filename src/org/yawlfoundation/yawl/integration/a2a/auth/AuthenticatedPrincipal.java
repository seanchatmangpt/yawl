package org.yawlfoundation.yawl.integration.a2a.auth;

import io.a2a.server.auth.User;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, verified identity produced by a successful authentication.
 *
 * <p>Implements {@link io.a2a.server.auth.User} so it can be passed directly
 * to {@code ServerCallContext} in the A2A SDK. Every field is populated from
 * the verified credential: username from the JWT subject or client-certificate
 * common name, permissions from JWT claims or a static policy mapped from the
 * SPIFFE ID, and expiry from the token or certificate validity period.
 *
 * <p>Instances are constructed exclusively by {@link A2AAuthenticationProvider}
 * implementations and are never modified after construction.
 *
 * <p>Permission names used by the A2A server:
 * <ul>
 *   <li>{@code workflow:launch} - allowed to call the launch_workflow skill</li>
 *   <li>{@code workflow:query} - allowed to call the query_workflows skill</li>
 *   <li>{@code workflow:cancel} - allowed to call the cancel_workflow skill</li>
 *   <li>{@code workitem:manage} - allowed to call the manage_workitems skill</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class AuthenticatedPrincipal implements User {

    /** Wildcard permission that grants access to all skills. */
    public static final String PERM_ALL = "*";
    public static final String PERM_WORKFLOW_LAUNCH  = "workflow:launch";
    public static final String PERM_WORKFLOW_QUERY   = "workflow:query";
    public static final String PERM_WORKFLOW_CANCEL  = "workflow:cancel";
    public static final String PERM_WORKITEM_MANAGE  = "workitem:manage";

    private final String username;
    private final Set<String> permissions;
    private final String authScheme;
    private final Instant authenticatedAt;
    private final Instant expiresAt;

    /**
     * Construct a verified principal. All parameters except {@code expiresAt}
     * are required.
     *
     * @param username        verified identity name; never null or blank
     * @param permissions     granted permissions; never null (use empty set for
     *                        no access, use {@link Set#of(Object)} with
     *                        {@link #PERM_ALL} for full access)
     * @param authScheme      scheme that produced this principal (e.g.
     *                        {@code "Bearer"}, {@code "ApiKey"}, {@code "mTLS"})
     * @param authenticatedAt when authentication was performed
     * @param expiresAt       when the credential expires, or {@code null} if the
     *                        credential has no expiry (API keys)
     */
    public AuthenticatedPrincipal(String username,
                                  Set<String> permissions,
                                  String authScheme,
                                  Instant authenticatedAt,
                                  Instant expiresAt) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                "Authenticated principal must have a non-blank username");
        }
        Objects.requireNonNull(permissions, "permissions must not be null");
        Objects.requireNonNull(authScheme, "authScheme must not be null");
        Objects.requireNonNull(authenticatedAt, "authenticatedAt must not be null");

        this.username        = username;
        this.permissions     = Collections.unmodifiableSet(permissions);
        this.authScheme      = authScheme;
        this.authenticatedAt = authenticatedAt;
        this.expiresAt       = expiresAt;
    }

    // ------------------------------------------------------------------ User

    /**
     * Returns {@code true} always: this object is only ever created after
     * successful credential verification. An unauthenticated request never
     * produces an {@code AuthenticatedPrincipal}; the server returns HTTP 401
     * instead.
     */
    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public String getUsername() {
        return username;
    }

    // -------------------------------------------------------- Principal data

    /**
     * The set of permissions granted to this principal. An empty set means no
     * access. The wildcard value {@code "*"} grants access to every operation.
     *
     * @return immutable set of permission strings
     */
    public Set<String> getPermissions() {
        return permissions;
    }

    /**
     * Returns {@code true} when this principal holds the given permission or
     * the wildcard {@link #PERM_ALL}.
     *
     * @param permission the permission name to check
     * @return {@code true} if access is granted
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(PERM_ALL) || permissions.contains(permission);
    }

    /**
     * The authentication scheme that produced this principal.
     *
     * @return scheme name (e.g. {@code "Bearer"}, {@code "ApiKey"}, {@code "mTLS"})
     */
    public String getAuthScheme() {
        return authScheme;
    }

    /**
     * Timestamp at which this principal was authenticated.
     *
     * @return authentication instant
     */
    public Instant getAuthenticatedAt() {
        return authenticatedAt;
    }

    /**
     * Expiry of the underlying credential, or {@code null} if the credential
     * does not expire.
     *
     * @return expiry instant or {@code null}
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Returns {@code true} when the credential has passed its expiry time.
     * Always returns {@code false} when {@link #getExpiresAt()} is
     * {@code null}.
     *
     * @return {@code true} if expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    // ---------------------------------------------------------------- Object

    @Override
    public String toString() {
        return "AuthenticatedPrincipal{"
            + "username='" + username + '\''
            + ", scheme='" + authScheme + '\''
            + ", permissions=" + permissions
            + ", expiresAt=" + expiresAt
            + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthenticatedPrincipal that)) return false;
        return Objects.equals(username, that.username)
            && Objects.equals(authScheme, that.authScheme)
            && Objects.equals(authenticatedAt, that.authenticatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, authScheme, authenticatedAt);
    }
}
