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

package org.yawlfoundation.yawl.integration.fmea;

/**
 * Enumeration of user-level FMEA failure modes for YAWL v6.
 *
 * <p>Each constant encodes Severity (S), Occurrence (O), and Detection (D) scores
 * on a 1–10 scale (10 = worst), following the same RPN formula used by the
 * Observatory build-risk FMEA (FM1–FM7).
 *
 * <pre>
 *   RPN = Severity × Occurrence × Detection
 * </pre>
 *
 * <table>
 *   <tr><th>ID</th><th>Name</th><th>S</th><th>O</th><th>D</th><th>RPN</th></tr>
 *   <tr><td>FM_U1</td><td>Credential Expiry</td>
 *       <td>8</td><td>4</td><td>3</td><td>96</td></tr>
 *   <tr><td>FM_U2</td><td>Missing Permission</td>
 *       <td>7</td><td>5</td><td>2</td><td>70</td></tr>
 *   <tr><td>FM_U3</td><td>Tenant Isolation Breach</td>
 *       <td>10</td><td>2</td><td>4</td><td>80</td></tr>
 *   <tr><td>FM_U4</td><td>Insufficient Scope</td>
 *       <td>7</td><td>4</td><td>2</td><td>56</td></tr>
 *   <tr><td>FM_U5</td><td>Admin Scope Elevation</td>
 *       <td>9</td><td>2</td><td>3</td><td>54</td></tr>
 *   <tr><td>FM_U6</td><td>Resource Over Capacity</td>
 *       <td>6</td><td>6</td><td>3</td><td>108</td></tr>
 *   <tr><td>FM_U7</td><td>Resource Unavailable</td>
 *       <td>8</td><td>3</td><td>5</td><td>120</td></tr>
 * </table>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public enum UserFailureModeType {

    /**
     * FM_U1 — Credential expires during or between operations.
     * An {@code AuthenticatedPrincipal} whose {@code expiresAt} is in the past
     * should be rejected before any privileged operation is delegated.
     * S=8 (auth bypass), O=4 (monthly in long-lived sessions), D=3 (auto-detectable).
     */
    FM_U1_CREDENTIAL_EXPIRY(8, 4, 3,
        "Credential expires during session",
        "Check AuthenticatedPrincipal.isExpired() before every privileged operation"),

    /**
     * FM_U2 — Principal lacks the specific permission required by an A2A skill.
     * A principal with a non-wildcard permission set that does not include the
     * required permission string must not be granted access.
     * S=7 (privilege escalation), O=5 (weekly in mixed-role deployments), D=2 (immediate throw).
     */
    FM_U2_MISSING_PERMISSION(7, 5, 2,
        "Principal lacks required permission for A2A skill",
        "Check AuthenticatedPrincipal.hasPermission(required) before delegating"),

    /**
     * FM_U3 — A tenant attempts to access a case owned by a different tenant.
     * {@code TenantContext.isAuthorized(caseId)} must return {@code false} for
     * cases registered under a different tenant ID.
     * S=10 (data leakage), O=2 (rare, mis-routing), D=4 (detectable with context check).
     */
    FM_U3_TENANT_ISOLATION_BREACH(10, 2, 4,
        "Cross-tenant case access attempted",
        "Enforce TenantContext.isAuthorized(caseId) on every case operation"),

    /**
     * FM_U4 — OIDC token lacks the OAuth2 scope required for the requested operation.
     * {@code OidcUserContext.hasScope(requiredScope)} returns {@code false} and
     * the token does not carry {@code yawl:admin} to imply all scopes.
     * S=7 (unauthorized operation), O=4 (monthly scope misconfiguration), D=2 (immediate check).
     */
    FM_U4_INSUFFICIENT_SCOPE(7, 4, 2,
        "OIDC token lacks required OAuth2 scope",
        "Check OidcUserContext.hasScope() against YawlOAuth2Scopes constant"),

    /**
     * FM_U5 — Token claims {@code yawl:admin} scope but carries no realm roles.
     * Admin scope without supporting role evidence may indicate a forged or
     * misconfigured token. Expected: admin token also carries at least one role.
     * S=9 (potential scope forgery), O=2 (rare), D=3 (role cross-check detects it).
     */
    FM_U5_ADMIN_SCOPE_ELEVATION(9, 2, 3,
        "Admin scope claimed without supporting realm role evidence",
        "Verify realm_access roles are non-empty when yawl:admin scope is present"),

    /**
     * FM_U6 — A work item is routed to a resource that is already at full capacity.
     * Allocation must not proceed when {@code allocated >= capacity}.
     * S=6 (queue overflow / starvation), O=6 (weekly under load), D=3 (capacity check).
     */
    FM_U6_RESOURCE_OVER_CAPACITY(6, 6, 3,
        "Work item routed to at-capacity resource",
        "Enforce capacity check (allocated < capacity) before every allocation"),

    /**
     * FM_U7 — The resource pool is empty; no resource is available to receive the work item.
     * Pre-screening the pool size before routing prevents silent queue loss.
     * S=8 (work item dropped), O=3 (monthly under peak load), D=5 (pool-size check needed).
     */
    FM_U7_RESOURCE_UNAVAILABLE(8, 3, 5,
        "No available resource in pool for work item routing",
        "Pre-screen pool size before routing; raise alert when pool is exhausted");

    // -----------------------------------------------------------------------

    private final int severity;
    private final int occurrence;
    private final int detection;
    private final String description;
    private final String mitigation;

    UserFailureModeType(int severity, int occurrence, int detection,
                        String description, String mitigation) {
        this.severity    = severity;
        this.occurrence  = occurrence;
        this.detection   = detection;
        this.description = description;
        this.mitigation  = mitigation;
    }

    /**
     * Risk Priority Number: {@code Severity × Occurrence × Detection}.
     * Higher RPN = higher risk. Maximum possible RPN = 1000 (10 × 10 × 10).
     *
     * @return computed RPN value
     */
    public int rpn() {
        return severity * occurrence * detection;
    }

    /** Severity score (1–10, 10 = catastrophic impact). */
    public int getSeverity()   { return severity; }

    /** Occurrence score (1–10, 10 = occurs on every change). */
    public int getOccurrence() { return occurrence; }

    /** Detection score (1–10, 10 = no detection possible). */
    public int getDetection()  { return detection; }

    /** Human-readable description of the failure mode. */
    public String getDescription() { return description; }

    /** Recommended mitigation action. */
    public String getMitigation()  { return mitigation; }
}
