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
 * Enumeration of A2A (Agent-to-Agent) FMEA failure modes for YAWL v6.
 *
 * <p>Each constant encodes Severity (S), Occurrence (O), and Detection (D) scores
 * on a 1–10 scale (10 = worst), following the same RPN formula used by the
 * Observatory build-risk FMEA (FM1–FM7) and the user-level FMEA (FM_U1–FM_U7).
 *
 * <pre>
 *   RPN = Severity × Occurrence × Detection
 * </pre>
 *
 * <table>
 *   <tr><th>ID</th><th>Name</th><th>S</th><th>O</th><th>D</th><th>RPN</th></tr>
 *   <tr><td>FM_A1</td><td>Agent Credential Expiry</td>
 *       <td>9</td><td>4</td><td>2</td><td>72</td></tr>
 *   <tr><td>FM_A2</td><td>Missing Skill Permission</td>
 *       <td>8</td><td>5</td><td>3</td><td>120</td></tr>
 *   <tr><td>FM_A3</td><td>Handoff Token Expiry</td>
 *       <td>7</td><td>5</td><td>3</td><td>105</td></tr>
 *   <tr><td>FM_A4</td><td>Handoff Self-Reference</td>
 *       <td>8</td><td>2</td><td>5</td><td>80</td></tr>
 *   <tr><td>FM_A5</td><td>Skill Not Registered</td>
 *       <td>6</td><td>4</td><td>2</td><td>48</td></tr>
 *   <tr><td>FM_A6</td><td>Insufficient Skill Permission</td>
 *       <td>7</td><td>4</td><td>3</td><td>84</td></tr>
 *   <tr><td>FM_A7</td><td>No Auth Scheme Configured</td>
 *       <td>10</td><td>2</td><td>5</td><td>100</td></tr>
 * </table>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public enum A2AFailureModeType {

    /**
     * FM_A1 — Agent JWT or certificate expires mid-workflow.
     * An {@code AuthenticatedPrincipal} whose {@code expiresAt} is in the past
     * must be rejected before any A2A skill is invoked.
     * S=9 (security bypass), O=4 (monthly in long-lived agent sessions), D=2 (auto-detectable).
     */
    FM_A1_AGENT_CREDENTIAL_EXPIRY(9, 4, 2,
        "Agent JWT/cert expires mid-workflow",
        "Refresh agent credentials before invoking A2A skills; check isExpired() pre-call"),

    /**
     * FM_A2 — Agent principal lacks the permission required to invoke the target skill.
     * A principal whose permission set does not include the required permission string
     * (and does not carry the wildcard {@code "*"}) must not be granted access.
     * S=8 (privilege escalation), O=5 (weekly in mixed-role deployments), D=3 (permission check).
     */
    FM_A2_MISSING_SKILL_PERMISSION(8, 5, 3,
        "Agent lacks required permission for invoked skill",
        "Grant required permission to agent credential or acquire broader-scoped token"),

    /**
     * FM_A3 — Handoff token has passed its 60-second TTL before the target agent accepts.
     * {@code HandoffToken.isValid()} returns {@code false} when {@code expiresAt} is in the past.
     * Expired tokens must be rejected to prevent replay attacks.
     * S=7 (work item stuck), O=5 (weekly under network latency), D=3 (expiry check needed).
     */
    FM_A3_HANDOFF_TOKEN_EXPIRY(7, 5, 3,
        "Handoff token past 60-second TTL before target agent accepts",
        "Generate a fresh handoff token immediately before dispatch; minimise latency"),

    /**
     * FM_A4 — Agent attempts to hand off a work item to itself ({@code fromAgent == toAgent}).
     * Self-referential handoffs violate the A2A protocol and must be rejected at token creation.
     * S=8 (protocol violation / potential loop), O=2 (rare, misconfiguration), D=5 (identifier check).
     */
    FM_A4_HANDOFF_SELF_REFERENCE(8, 2, 5,
        "Agent attempts to hand off work item to itself (fromAgent == toAgent)",
        "Validate distinct agent identifiers before generating HandoffToken"),

    /**
     * FM_A5 — The requested skill ID is absent from the agent's registered skill set.
     * Callers should discover available skills via {@code /.well-known/agent.json}
     * before invoking to avoid routing to a non-existent skill.
     * S=6 (request fails), O=4 (monthly in evolving skill registries), D=2 (registry check).
     */
    FM_A5_SKILL_NOT_REGISTERED(6, 4, 2,
        "Requested skill ID absent from agent's registered skill set",
        "Discover available skills via /.well-known/agent.json before invoking"),

    /**
     * FM_A6 — The target skill is registered but the agent principal lacks the permission
     * that the skill requires. Distinct from FM_A2: the skill exists; the credential is
     * underprivileged for that specific skill.
     * S=7 (skill blocked), O=4 (monthly as skills gain narrower permission scopes), D=3 (check required).
     */
    FM_A6_INSUFFICIENT_SKILL_PERMISSION(7, 4, 3,
        "Skill is registered but agent lacks the skill's required permission",
        "Check skill.getRequiredPermissions() against principal before dispatch"),

    /**
     * FM_A7 — The A2A server is deployed with no authentication providers configured.
     * An empty {@code configuredSchemes} set means every request is accepted without
     * credential validation — a critical security misconfiguration.
     * S=10 (all requests accepted unauthenticated), O=2 (rare, deployment error), D=5 (no self-detection).
     */
    FM_A7_NO_AUTH_SCHEME_CONFIGURED(10, 2, 5,
        "A2A server deployed with no authentication providers — all requests accepted",
        "Configure at least one of: A2A_JWT_SECRET, A2A_API_KEY_MASTER, A2A_SPIFFE_TRUST_DOMAIN");

    // -----------------------------------------------------------------------

    private final int severity;
    private final int occurrence;
    private final int detection;
    private final String description;
    private final String mitigation;

    A2AFailureModeType(int severity, int occurrence, int detection,
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
