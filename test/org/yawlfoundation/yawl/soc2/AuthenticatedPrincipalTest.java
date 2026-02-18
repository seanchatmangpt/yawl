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

package org.yawlfoundation.yawl.soc2;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;

import java.time.Instant;
import java.util.Set;

/**
 * SOC2 CC6 - AuthenticatedPrincipal Authorization Tests.
 *
 * <p>Tests map to SOC 2 Trust Service Criteria:
 * CC6.1 - Logical access controls restrict access.
 * CC6.3 - Roles are designed to support least-privilege policies.
 *
 * <p>Covers:
 * <ul>
 *   <li>Null/blank username throws IllegalArgumentException</li>
 *   <li>Null permissions throws NullPointerException</li>
 *   <li>isAuthenticated() always returns true for constructed principals</li>
 *   <li>hasPermission() with wildcard PERM_ALL grants any permission</li>
 *   <li>hasPermission() with explicit permission grants only that permission</li>
 *   <li>hasPermission() with empty permissions denies everything</li>
 *   <li>isExpired() returns false for non-null future expiry</li>
 *   <li>isExpired() returns true for past expiry</li>
 *   <li>isExpired() returns false when expiresAt is null (API keys)</li>
 *   <li>Permissions set is immutable after construction</li>
 *   <li>equals() and hashCode() are consistent</li>
 *   <li>toString() contains username and scheme</li>
 * </ul>
 *
 * <p>Chicago TDD: real AuthenticatedPrincipal, no mocks.
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
public class AuthenticatedPrincipalTest extends TestCase {

    public AuthenticatedPrincipalTest(String name) {
        super(name);
    }

    private AuthenticatedPrincipal principal(String username, Set<String> permissions,
                                              String scheme, Instant expiresAt) {
        return new AuthenticatedPrincipal(username, permissions, scheme, Instant.now(), expiresAt);
    }

    // =========================================================================
    // CC6.1 - Constructor validation
    // =========================================================================

    /**
     * SOC2 CC6.1: Null username must throw - no anonymous principals.
     */
    public void testNullUsernameThrows() {
        try {
            principal(null, Set.of("workflow:launch"), "Bearer", null);
            fail("Expected IllegalArgumentException for null username");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    /**
     * SOC2 CC6.1: Blank username must throw - no anonymous principals.
     */
    public void testBlankUsernameThrows() {
        try {
            principal("   ", Set.of("workflow:launch"), "Bearer", null);
            fail("Expected IllegalArgumentException for blank username");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    /**
     * SOC2 CC6.1: Empty username must throw.
     */
    public void testEmptyUsernameThrows() {
        try {
            principal("", Set.of("workflow:launch"), "Bearer", null);
            fail("Expected IllegalArgumentException for empty username");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    /**
     * SOC2 CC6.3: Null permissions must throw - undefined permissions = security gap.
     */
    public void testNullPermissionsThrows() {
        try {
            principal("agent-1", null, "Bearer", null);
            fail("Expected NullPointerException for null permissions");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    /**
     * SOC2 CC6.1: Null auth scheme must throw.
     */
    public void testNullAuthSchemeThrows() {
        try {
            new AuthenticatedPrincipal("agent", Set.of(), null, Instant.now(), null);
            fail("Expected NullPointerException for null authScheme");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    /**
     * SOC2 CC6.1: Null authenticatedAt must throw.
     */
    public void testNullAuthenticatedAtThrows() {
        try {
            new AuthenticatedPrincipal("agent", Set.of(), "Bearer", null, null);
            fail("Expected NullPointerException for null authenticatedAt");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // CC6.1 - isAuthenticated contract
    // =========================================================================

    /**
     * SOC2 CC6.1: Any constructed AuthenticatedPrincipal is always authenticated.
     * Unauthenticated state must never produce this object.
     */
    public void testIsAuthenticatedAlwaysTrue() {
        AuthenticatedPrincipal p = principal("agent-x", Set.of(), "ApiKey", null);
        assertTrue("AuthenticatedPrincipal.isAuthenticated() must always return true",
                p.isAuthenticated());
    }

    // =========================================================================
    // CC6.3 - hasPermission least-privilege enforcement
    // =========================================================================

    /**
     * SOC2 CC6.3: Wildcard PERM_ALL grants any permission check.
     */
    public void testPermAllGrantsAnyPermission() {
        AuthenticatedPrincipal p = principal("admin", Set.of(AuthenticatedPrincipal.PERM_ALL),
                "Bearer", null);

        assertTrue("PERM_ALL must grant workflow:launch",
                p.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
        assertTrue("PERM_ALL must grant workflow:query",
                p.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
        assertTrue("PERM_ALL must grant workflow:cancel",
                p.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL));
        assertTrue("PERM_ALL must grant workitem:manage",
                p.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE));
        assertTrue("PERM_ALL must grant any custom permission",
                p.hasPermission("some:custom:permission"));
    }

    /**
     * SOC2 CC6.3: Explicit single permission grants only that permission.
     */
    public void testExplicitPermissionGrantsOnlyThatPermission() {
        AuthenticatedPrincipal p = principal("readonly-agent",
                Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY), "Bearer", null);

        assertTrue("workflow:query must be granted",
                p.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
        assertFalse("workflow:launch must NOT be granted to read-only agent",
                p.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
        assertFalse("workflow:cancel must NOT be granted to read-only agent",
                p.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL));
        assertFalse("workitem:manage must NOT be granted to read-only agent",
                p.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE));
    }

    /**
     * SOC2 CC6.3: Empty permission set must deny all access (deny-by-default).
     */
    public void testEmptyPermissionsDeniesAll() {
        AuthenticatedPrincipal p = principal("no-access-agent", Set.of(), "mTLS", null);

        assertFalse("Empty permissions must deny workflow:launch",
                p.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
        assertFalse("Empty permissions must deny workflow:query",
                p.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
        assertFalse("Empty permissions must deny workflow:cancel",
                p.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL));
        assertFalse("Empty permissions must deny workitem:manage",
                p.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE));
        assertFalse("Empty permissions must deny wildcard",
                p.hasPermission(AuthenticatedPrincipal.PERM_ALL));
    }

    /**
     * SOC2 CC6.3: Multi-permission set grants exactly configured permissions.
     */
    public void testMultiPermissionGrantsCorrectSubset() {
        Set<String> perms = Set.of(
                AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH,
                AuthenticatedPrincipal.PERM_WORKFLOW_QUERY
        );
        AuthenticatedPrincipal p = principal("limited-agent", perms, "ApiKey", null);

        assertTrue("workflow:launch must be granted", p.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
        assertTrue("workflow:query must be granted",  p.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
        assertFalse("workflow:cancel must be denied", p.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL));
        assertFalse("workitem:manage must be denied", p.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE));
    }

    // =========================================================================
    // CC7 - Expiry enforcement (credential lifecycle)
    // =========================================================================

    /**
     * SOC2 CC7: Principal with null expiresAt must report not-expired (API keys, no-expiry).
     */
    public void testNullExpiryIsNeverExpired() {
        AuthenticatedPrincipal p = principal("api-key-user", Set.of(AuthenticatedPrincipal.PERM_ALL),
                "ApiKey", null);
        assertFalse("Principal with no expiry must never be expired",
                p.isExpired());
    }

    /**
     * SOC2 CC7: Principal with future expiry must not be expired.
     */
    public void testFutureExpiryIsNotExpired() {
        Instant future = Instant.now().plusSeconds(3600); // 1 hour from now
        AuthenticatedPrincipal p = principal("jwt-user", Set.of(AuthenticatedPrincipal.PERM_ALL),
                "Bearer", future);
        assertFalse("Principal with future expiry must not be expired",
                p.isExpired());
    }

    /**
     * SOC2 CC7: Principal with past expiry must be expired - enforces token rotation.
     */
    public void testPastExpiryIsExpired() {
        Instant past = Instant.now().minusSeconds(1); // 1 second ago
        AuthenticatedPrincipal p = principal("expired-user", Set.of(AuthenticatedPrincipal.PERM_ALL),
                "Bearer", past);
        assertTrue("Principal with past expiry must be expired",
                p.isExpired());
    }

    // =========================================================================
    // CC6.1 - Immutability of permissions
    // =========================================================================

    /**
     * SOC2 CC6.1: The permissions set returned by getPermissions() must be immutable.
     * Callers must not be able to escalate privileges by mutating the set.
     */
    public void testPermissionsSetIsImmutable() {
        AuthenticatedPrincipal p = principal("agent",
                Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY), "Bearer", null);

        try {
            p.getPermissions().add(AuthenticatedPrincipal.PERM_ALL);
            fail("getPermissions() must return an immutable set - privilege escalation blocked");
        } catch (UnsupportedOperationException e) {
            // Expected - immutable set
        }
    }

    // =========================================================================
    // CC6.1 - Accessor correctness
    // =========================================================================

    public void testGetUsername() {
        AuthenticatedPrincipal p = principal("test-agent", Set.of(), "Bearer", null);
        assertEquals("test-agent", p.getUsername());
    }

    public void testGetAuthScheme() {
        AuthenticatedPrincipal p = principal("agent", Set.of(), "mTLS", null);
        assertEquals("mTLS", p.getAuthScheme());
    }

    public void testGetExpiresAt() {
        Instant expiry = Instant.now().plusSeconds(300);
        AuthenticatedPrincipal p = principal("agent", Set.of(), "Bearer", expiry);
        assertEquals(expiry, p.getExpiresAt());
    }

    public void testGetAuthenticatedAtIsRecent() {
        Instant before = Instant.now().minusSeconds(1);
        AuthenticatedPrincipal p = principal("agent", Set.of(), "Bearer", null);
        Instant after = Instant.now().plusSeconds(1);

        assertNotNull(p.getAuthenticatedAt());
        assertTrue("authenticatedAt must be recent",
                p.getAuthenticatedAt().isAfter(before) && p.getAuthenticatedAt().isBefore(after));
    }

    // =========================================================================
    // Object contract
    // =========================================================================

    public void testEqualsAndHashCodeConsistent() {
        Instant now = Instant.now();
        AuthenticatedPrincipal p1 = new AuthenticatedPrincipal("agent", Set.of(), "Bearer", now, null);
        AuthenticatedPrincipal p2 = new AuthenticatedPrincipal("agent", Set.of(), "Bearer", now, null);

        assertEquals("equals() must be consistent", p1, p2);
        assertEquals("hashCode() must match for equal objects", p1.hashCode(), p2.hashCode());
    }

    public void testToStringContainsUsernameAndScheme() {
        AuthenticatedPrincipal p = principal("audit-user", Set.of(), "ApiKey", null);
        String str = p.toString();

        assertTrue("toString must contain username for audit", str.contains("audit-user"));
        assertTrue("toString must contain scheme for audit", str.contains("ApiKey"));
    }

    // =========================================================================
    // Test suite
    // =========================================================================

    public static Test suite() {
        return new TestSuite(AuthenticatedPrincipalTest.class);
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
