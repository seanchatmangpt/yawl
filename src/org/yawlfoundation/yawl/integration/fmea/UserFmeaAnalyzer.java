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

import org.yawlfoundation.yawl.engine.TenantContext;
import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;
import org.yawlfoundation.yawl.integration.oauth2.OidcUserContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stateless analyser that checks YAWL v6 user objects for FMEA failure conditions.
 *
 * <p>Each {@code analyze*} method evaluates one user subject against the relevant
 * subset of {@link UserFailureModeType} failure modes and returns a
 * {@link UserFmeaReport}. A report with {@link UserFmeaReport#isClean()} {@code true}
 * means no violations were detected; {@code false} means at least one failure mode
 * fired and the caller should reject or log the request.
 *
 * <p>This class is intentionally stateless — instantiate once and reuse across
 * requests, or create per-request; both patterns are safe for concurrent use.
 *
 * <h2>Failure modes by method</h2>
 * <table>
 *   <tr><th>Method</th><th>Failure Modes Checked</th></tr>
 *   <tr><td>{@link #analyzePrincipal}</td>
 *       <td>FM_U1 (credential expiry), FM_U2 (missing permission)</td></tr>
 *   <tr><td>{@link #analyzeOidcContext}</td>
 *       <td>FM_U4 (insufficient scope), FM_U5 (admin scope elevation)</td></tr>
 *   <tr><td>{@link #analyzeTenantAccess}</td>
 *       <td>FM_U3 (tenant isolation breach)</td></tr>
 *   <tr><td>{@link #analyzeResourceCapacity}</td>
 *       <td>FM_U6 (resource over capacity), FM_U7 (resource unavailable)</td></tr>
 * </table>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * UserFmeaAnalyzer analyzer = new UserFmeaAnalyzer();
 *
 * UserFmeaReport report = analyzer.analyzePrincipal(principal, "workflow:launch");
 * if (!report.isClean()) {
 *     throw new SecurityException("FMEA " + report.status()
 *         + " RPN=" + report.totalRpn());
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class UserFmeaAnalyzer {

    /**
     * Analyse an {@link AuthenticatedPrincipal} for credential and permission failures.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_U1</b> — fires when {@code principal.isExpired()} returns {@code true}</li>
     *   <li><b>FM_U2</b> — fires when {@code principal.hasPermission(requiredPermission)}
     *       returns {@code false}</li>
     * </ul>
     *
     * @param principal          the authenticated principal to evaluate; must not be {@code null}
     * @param requiredPermission the permission string that this operation requires;
     *                           must not be {@code null}
     * @return a {@link UserFmeaReport} with zero or more violations; never {@code null}
     */
    public UserFmeaReport analyzePrincipal(AuthenticatedPrincipal principal,
                                           String requiredPermission) {
        Objects.requireNonNull(principal,          "principal must not be null");
        Objects.requireNonNull(requiredPermission, "requiredPermission must not be null");

        List<UserFmeaViolation> violations = new ArrayList<>();

        // FM_U1 — credential expiry
        if (principal.isExpired()) {
            String expiryEvidence = "token expired at " + principal.getExpiresAt()
                + ", now is " + Instant.now();
            violations.add(new UserFmeaViolation(
                UserFailureModeType.FM_U1_CREDENTIAL_EXPIRY,
                "principal=" + principal.getUsername()
                    + ", scheme=" + principal.getAuthScheme(),
                expiryEvidence
            ));
        }

        // FM_U2 — missing permission
        if (!principal.hasPermission(requiredPermission)) {
            violations.add(new UserFmeaViolation(
                UserFailureModeType.FM_U2_MISSING_PERMISSION,
                "principal=" + principal.getUsername()
                    + ", scheme=" + principal.getAuthScheme(),
                "required='" + requiredPermission
                    + "', held=" + principal.getPermissions()
            ));
        }

        return new UserFmeaReport(Instant.now(), violations);
    }

    /**
     * Analyse an {@link OidcUserContext} for scope and role anomalies.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_U4</b> — fires when {@code context.hasScope(requiredScope)} returns
     *       {@code false}</li>
     *   <li><b>FM_U5</b> — fires when the token carries the {@code yawl:admin} scope
     *       but the context has no realm roles, which may indicate a forged or
     *       misconfigured token</li>
     * </ul>
     *
     * @param context       the OIDC user context to evaluate; must not be {@code null}
     * @param requiredScope the OAuth2 scope string required for the operation;
     *                      must not be {@code null}
     * @return a {@link UserFmeaReport} with zero or more violations; never {@code null}
     */
    public UserFmeaReport analyzeOidcContext(OidcUserContext context,
                                             String requiredScope) {
        Objects.requireNonNull(context,       "context must not be null");
        Objects.requireNonNull(requiredScope, "requiredScope must not be null");

        List<UserFmeaViolation> violations = new ArrayList<>();

        // FM_U4 — insufficient scope
        if (!context.hasScope(requiredScope)) {
            violations.add(new UserFmeaViolation(
                UserFailureModeType.FM_U4_INSUFFICIENT_SCOPE,
                "subject=" + context.getSubject(),
                "required='" + requiredScope + "', granted=" + context.getScopes()
            ));
        }

        // FM_U5 — admin scope without any realm role evidence
        if (context.isAdmin() && context.getRoles().isEmpty()) {
            violations.add(new UserFmeaViolation(
                UserFailureModeType.FM_U5_ADMIN_SCOPE_ELEVATION,
                "subject=" + context.getSubject(),
                "yawl:admin scope present but realm_access roles are empty"
            ));
        }

        return new UserFmeaReport(Instant.now(), violations);
    }

    /**
     * Analyse a {@link TenantContext} access attempt for tenant isolation violations.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_U3</b> — fires when {@code tenant.isAuthorized(caseId)} returns
     *       {@code false} AND the case is known to exist (i.e. it is registered
     *       under a different tenant in the global mapping). A case that is simply
     *       unknown returns a clean report — the caller should handle unknown cases
     *       separately via a not-found response.</li>
     * </ul>
     *
     * @param tenant the tenant context making the access request; must not be {@code null}
     * @param caseId the case identifier being accessed; must not be {@code null}
     * @return a {@link UserFmeaReport} with zero or one violation; never {@code null}
     */
    public UserFmeaReport analyzeTenantAccess(TenantContext tenant, String caseId) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(caseId, "caseId must not be null");

        List<UserFmeaViolation> violations = new ArrayList<>();

        // FM_U3 — cross-tenant access: case exists but belongs to a different tenant
        if (!tenant.isAuthorized(caseId)) {
            String owningTenant = TenantContext.getTenantForCase(caseId);
            if (owningTenant != null) {
                // Case is registered, but under a different tenant — isolation breach
                violations.add(new UserFmeaViolation(
                    UserFailureModeType.FM_U3_TENANT_ISOLATION_BREACH,
                    "tenant=" + tenant.getTenantId() + ", caseId=" + caseId,
                    "case is owned by tenant='" + owningTenant
                        + "', access by tenant='" + tenant.getTenantId() + "' denied"
                ));
            }
            // If owningTenant == null the case simply doesn't exist — not an FMEA violation
        }

        return new UserFmeaReport(Instant.now(), violations);
    }

    /**
     * Analyse resource capacity values for allocation failure modes.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_U6</b> — fires when {@code allocated >= capacity} (the target
     *       resource is at or over its maximum capacity)</li>
     *   <li><b>FM_U7</b> — fires when {@code availablePoolSize == 0} (the resource
     *       pool contains no available resource for routing)</li>
     * </ul>
     *
     * @param capacity          maximum number of work items the target resource can hold;
     *                          must be &ge; 0
     * @param allocated         current number of items allocated to the target resource;
     *                          must be &ge; 0
     * @param availablePoolSize number of resources in the pool that are not at capacity;
     *                          must be &ge; 0
     * @return a {@link UserFmeaReport} with zero or more violations; never {@code null}
     * @throws IllegalArgumentException if any argument is negative
     */
    public UserFmeaReport analyzeResourceCapacity(int capacity,
                                                  int allocated,
                                                  int availablePoolSize) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0, got " + capacity);
        }
        if (allocated < 0) {
            throw new IllegalArgumentException("allocated must be >= 0, got " + allocated);
        }
        if (availablePoolSize < 0) {
            throw new IllegalArgumentException(
                "availablePoolSize must be >= 0, got " + availablePoolSize);
        }

        List<UserFmeaViolation> violations = new ArrayList<>();

        // FM_U6 — target resource at or over capacity
        if (allocated >= capacity) {
            violations.add(new UserFmeaViolation(
                UserFailureModeType.FM_U6_RESOURCE_OVER_CAPACITY,
                "capacity=" + capacity + ", allocated=" + allocated,
                "resource is at capacity (" + allocated + "/" + capacity
                    + "); work item cannot be routed here"
            ));
        }

        // FM_U7 — no available resource in pool
        if (availablePoolSize == 0) {
            violations.add(new UserFmeaViolation(
                UserFailureModeType.FM_U7_RESOURCE_UNAVAILABLE,
                "availablePoolSize=" + availablePoolSize,
                "resource pool is exhausted; no resource available for routing"
            ));
        }

        return new UserFmeaReport(Instant.now(), violations);
    }
}
