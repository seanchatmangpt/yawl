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

import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffToken;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stateless analyser that checks YAWL v6 A2A objects for FMEA failure conditions.
 *
 * <p>Each {@code analyze*} method evaluates one A2A subject against the relevant
 * subset of {@link A2AFailureModeType} failure modes and returns an
 * {@link A2AFmeaReport}. A report with {@link A2AFmeaReport#isClean()} {@code true}
 * means no violations were detected; {@code false} means at least one failure mode
 * fired and the caller should reject or log the request.
 *
 * <p>This class is intentionally stateless — instantiate once and reuse across
 * requests, or create per-request; both patterns are safe for concurrent use.
 *
 * <h2>Failure modes by method</h2>
 * <table>
 *   <tr><th>Method</th><th>Failure Modes Checked</th></tr>
 *   <tr><td>{@link #analyzeAgentPrincipal}</td>
 *       <td>FM_A1 (credential expiry), FM_A2 (missing skill permission)</td></tr>
 *   <tr><td>{@link #analyzeHandoffToken}</td>
 *       <td>FM_A3 (handoff token expiry), FM_A4 (handoff self-reference)</td></tr>
 *   <tr><td>{@link #analyzeSkillAccess}</td>
 *       <td>FM_A5 (skill not registered), FM_A6 (insufficient skill permission)</td></tr>
 *   <tr><td>{@link #analyzeAuthConfiguration}</td>
 *       <td>FM_A7 (no auth scheme configured)</td></tr>
 * </table>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * A2AFmeaAnalyzer analyzer = new A2AFmeaAnalyzer();
 *
 * A2AFmeaReport report = analyzer.analyzeAgentPrincipal(principal, "workflow:launch");
 * if (!report.isClean()) {
 *     throw new SecurityException("A2A FMEA " + report.status()
 *         + " RPN=" + report.totalRpn());
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class A2AFmeaAnalyzer {

    /**
     * Analyse an A2A {@link AuthenticatedPrincipal} for credential and permission failures.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_A1</b> — fires when {@code principal.isExpired()} returns {@code true}</li>
     *   <li><b>FM_A2</b> — fires when {@code principal.hasPermission(requiredPermission)}
     *       returns {@code false}</li>
     * </ul>
     *
     * @param principal          the authenticated agent principal to evaluate; must not be {@code null}
     * @param requiredPermission the permission string that this operation requires;
     *                           must not be {@code null}
     * @return an {@link A2AFmeaReport} with zero or more violations; never {@code null}
     */
    public A2AFmeaReport analyzeAgentPrincipal(AuthenticatedPrincipal principal,
                                               String requiredPermission) {
        Objects.requireNonNull(principal,          "principal must not be null");
        Objects.requireNonNull(requiredPermission, "requiredPermission must not be null");

        List<A2AFmeaViolation> violations = new ArrayList<>();

        // FM_A1 — agent credential expiry
        if (principal.isExpired()) {
            String expiryEvidence = "token expired at " + principal.getExpiresAt()
                + ", now is " + Instant.now();
            violations.add(new A2AFmeaViolation(
                A2AFailureModeType.FM_A1_AGENT_CREDENTIAL_EXPIRY,
                "agent=" + principal.getUsername()
                    + ", scheme=" + principal.getAuthScheme(),
                expiryEvidence
            ));
        }

        // FM_A2 — missing skill permission
        if (!principal.hasPermission(requiredPermission)) {
            violations.add(new A2AFmeaViolation(
                A2AFailureModeType.FM_A2_MISSING_SKILL_PERMISSION,
                "agent=" + principal.getUsername()
                    + ", scheme=" + principal.getAuthScheme(),
                "required='" + requiredPermission
                    + "', held=" + principal.getPermissions()
            ));
        }

        return new A2AFmeaReport(Instant.now(), violations);
    }

    /**
     * Analyse a {@link HandoffToken} for protocol failures.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_A3</b> — fires when {@code !token.isValid()} (the token's
     *       {@code expiresAt} is in the past)</li>
     *   <li><b>FM_A4</b> — fires when {@code token.fromAgent().equals(token.toAgent())}
     *       (the agent is attempting to hand off the work item to itself)</li>
     * </ul>
     *
     * @param token the handoff token to evaluate; must not be {@code null}
     * @return an {@link A2AFmeaReport} with zero or more violations; never {@code null}
     */
    public A2AFmeaReport analyzeHandoffToken(HandoffToken token) {
        Objects.requireNonNull(token, "token must not be null");

        List<A2AFmeaViolation> violations = new ArrayList<>();

        // FM_A3 — handoff token expiry
        if (!token.isValid()) {
            violations.add(new A2AFmeaViolation(
                A2AFailureModeType.FM_A3_HANDOFF_TOKEN_EXPIRY,
                "workItemId=" + token.workItemId()
                    + ", fromAgent=" + token.fromAgent()
                    + ", toAgent=" + token.toAgent(),
                "token expired at " + token.expiresAt()
            ));
        }

        // FM_A4 — handoff self-reference
        if (token.fromAgent().equals(token.toAgent())) {
            violations.add(new A2AFmeaViolation(
                A2AFailureModeType.FM_A4_HANDOFF_SELF_REFERENCE,
                "workItemId=" + token.workItemId()
                    + ", fromAgent=" + token.fromAgent()
                    + ", toAgent=" + token.toAgent(),
                "fromAgent == toAgent: " + token.fromAgent()
            ));
        }

        return new A2AFmeaReport(Instant.now(), violations);
    }

    /**
     * Analyse a skill access request for registry and permission failures.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_A5</b> — fires when {@code skillId} is not present in
     *       {@code registeredSkillIds}</li>
     *   <li><b>FM_A6</b> — fires when the skill IS registered but
     *       {@code agentPermissions} does not contain the wildcard {@code "*"}
     *       or the {@code requiredPermission}. FM_A6 is only evaluated when the
     *       skill exists (FM_A5 not fired), since an unregistered skill's
     *       permission requirements are unknown.</li>
     * </ul>
     *
     * @param skillId             the skill identifier being requested; must not be {@code null}
     * @param registeredSkillIds  the set of skill IDs registered on this agent; must not be {@code null}
     * @param agentPermissions    the permissions held by the requesting agent; must not be {@code null}
     * @param requiredPermission  the permission the target skill requires; must not be {@code null}
     * @return an {@link A2AFmeaReport} with zero or more violations; never {@code null}
     */
    public A2AFmeaReport analyzeSkillAccess(String skillId,
                                            Set<String> registeredSkillIds,
                                            Set<String> agentPermissions,
                                            String requiredPermission) {
        Objects.requireNonNull(skillId,            "skillId must not be null");
        Objects.requireNonNull(registeredSkillIds, "registeredSkillIds must not be null");
        Objects.requireNonNull(agentPermissions,   "agentPermissions must not be null");
        Objects.requireNonNull(requiredPermission, "requiredPermission must not be null");

        List<A2AFmeaViolation> violations = new ArrayList<>();

        boolean skillRegistered = registeredSkillIds.contains(skillId);

        // FM_A5 — skill not registered
        if (!skillRegistered) {
            violations.add(new A2AFmeaViolation(
                A2AFailureModeType.FM_A5_SKILL_NOT_REGISTERED,
                "skillId=" + skillId,
                "skill '" + skillId + "' is not in the registered skill set "
                    + registeredSkillIds
            ));
        }

        // FM_A6 — skill registered but agent lacks required permission
        // Only check when skill exists; if it doesn't, permissions are irrelevant
        if (skillRegistered
                && !agentPermissions.contains(AuthenticatedPrincipal.PERM_ALL)
                && !agentPermissions.contains(requiredPermission)) {
            violations.add(new A2AFmeaViolation(
                A2AFailureModeType.FM_A6_INSUFFICIENT_SKILL_PERMISSION,
                "skillId=" + skillId,
                "required='" + requiredPermission
                    + "', held=" + agentPermissions
            ));
        }

        return new A2AFmeaReport(Instant.now(), violations);
    }

    /**
     * Analyse the A2A server's authentication configuration for misconfiguration.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_A7</b> — fires when {@code configuredSchemes} is empty, meaning
     *       the server has no authentication providers and will accept every request
     *       without credential validation</li>
     * </ul>
     *
     * @param configuredSchemes the set of authentication scheme names currently active
     *                          (e.g. {@code {"Bearer", "ApiKey"}}); must not be {@code null}
     * @return an {@link A2AFmeaReport} with zero or one violation; never {@code null}
     */
    public A2AFmeaReport analyzeAuthConfiguration(Set<String> configuredSchemes) {
        Objects.requireNonNull(configuredSchemes, "configuredSchemes must not be null");

        List<A2AFmeaViolation> violations = new ArrayList<>();

        // FM_A7 — no authentication scheme configured
        if (configuredSchemes.isEmpty()) {
            violations.add(new A2AFmeaViolation(
                A2AFailureModeType.FM_A7_NO_AUTH_SCHEME_CONFIGURED,
                "configuredSchemes=" + configuredSchemes,
                "configuredSchemes is empty — server accepts all requests without authentication"
            ));
        }

        return new A2AFmeaReport(Instant.now(), violations);
    }
}
