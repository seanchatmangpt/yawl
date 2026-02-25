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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffToken;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link A2AFmeaAnalyzer}.
 *
 * Chicago TDD: real YAWL A2A objects, no mocks.
 * Each nested class covers one {@code analyze*} method and its failure modes.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("unit")
class A2AFmeaAnalyzerTest {

    private A2AFmeaAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new A2AFmeaAnalyzer();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Valid (non-expired) agent principal with the given permissions. */
    private static AuthenticatedPrincipal validPrincipal(String agentId, Set<String> perms) {
        return new AuthenticatedPrincipal(
            agentId, perms, "Bearer",
            Instant.now().minus(1, ChronoUnit.MINUTES),
            Instant.now().plus(1, ChronoUnit.HOURS)
        );
    }

    /** Expired agent principal (expiresAt one hour in the past). */
    private static AuthenticatedPrincipal expiredPrincipal() {
        return new AuthenticatedPrincipal(
            "expired-agent", Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH), "Bearer",
            Instant.now().minus(2, ChronoUnit.HOURS),
            Instant.now().minus(1, ChronoUnit.HOURS)
        );
    }

    /** Non-expiring agent principal (null expiresAt — API key style). */
    private static AuthenticatedPrincipal nonExpiringPrincipal(String agentId, Set<String> perms) {
        return new AuthenticatedPrincipal(
            agentId, perms, "ApiKey",
            Instant.now().minus(1, ChronoUnit.HOURS),
            null   // API keys don't expire
        );
    }

    /**
     * Valid handoff token (expires 60 seconds from now).
     * Use {@link HandoffToken#withExpiresAt(Instant)} to create expired variants.
     */
    private static HandoffToken validToken(String fromAgent, String toAgent) {
        return new HandoffToken(
            "WI-42", fromAgent, toAgent,
            "session-handle-abc",
            Instant.now().plus(60, ChronoUnit.SECONDS),
            "jwt.header.signature"
        );
    }

    /** Expired handoff token (expiresAt one second in the past). */
    private static HandoffToken expiredToken(String fromAgent, String toAgent) {
        return validToken(fromAgent, toAgent)
            .withExpiresAt(Instant.now().minus(1, ChronoUnit.SECONDS));
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeAgentPrincipal — FM_A1 and FM_A2")
    class AnalyzeAgentPrincipal {

        @Test
        @DisplayName("cleanPrincipal_withRequiredPermission_returnsGreenReport")
        void cleanPrincipal_withRequiredPermission_returnsGreenReport() {
            AuthenticatedPrincipal p = validPrincipal("agent-A",
                Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));

            A2AFmeaReport report = analyzer.analyzeAgentPrincipal(p, "workflow:launch");

            assertTrue(report.isClean(), "clean principal should produce GREEN report");
            assertEquals("GREEN", report.status());
            assertEquals(0, report.totalRpn());
            assertTrue(report.violations().isEmpty());
        }

        @Test
        @DisplayName("expiredPrincipal_triggersFM_A1")
        void expiredPrincipal_triggersFM_A1() {
            AuthenticatedPrincipal p = expiredPrincipal();

            A2AFmeaReport report = analyzer.analyzeAgentPrincipal(p, "workflow:launch");

            assertFalse(report.isClean());
            assertEquals("RED", report.status());
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == A2AFailureModeType.FM_A1_AGENT_CREDENTIAL_EXPIRY),
                "expired principal must trigger FM_A1");
        }

        @Test
        @DisplayName("nonExpiringPrincipal_nullExpiresAt_neverTriggersFM_A1")
        void nonExpiringPrincipal_nullExpiresAt_neverTriggersFM_A1() {
            AuthenticatedPrincipal p = nonExpiringPrincipal("api-key-agent",
                Set.of(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE));

            A2AFmeaReport report = analyzer.analyzeAgentPrincipal(p, "workitem:manage");

            assertTrue(report.isClean(),
                "non-expiring principal (null expiresAt) must not trigger FM_A1");
        }

        @Test
        @DisplayName("missingPermission_triggersFM_A2")
        void missingPermission_triggersFM_A2() {
            AuthenticatedPrincipal p = validPrincipal("agent-B",
                Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));  // only query, not launch

            A2AFmeaReport report = analyzer.analyzeAgentPrincipal(p, "workflow:launch");

            assertFalse(report.isClean());
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == A2AFailureModeType.FM_A2_MISSING_SKILL_PERMISSION),
                "principal without required permission must trigger FM_A2");
        }

        @Test
        @DisplayName("wildcardPermission_withAnyRequiredPerm_returnsGreenReport")
        void wildcardPermission_withAnyRequiredPerm_returnsGreenReport() {
            AuthenticatedPrincipal p = validPrincipal("admin-agent",
                Set.of(AuthenticatedPrincipal.PERM_ALL));   // wildcard grants everything

            A2AFmeaReport report = analyzer.analyzeAgentPrincipal(p, "upgrade:execute");

            assertTrue(report.isClean(), "wildcard principal must pass any permission check");
        }

        @Test
        @DisplayName("expiredAndMissingPermission_triggersBothFM_A1andFM_A2")
        void expiredAndMissingPermission_triggersBothViolations() {
            AuthenticatedPrincipal p = new AuthenticatedPrincipal(
                "stale-agent", Set.of("workflow:query"), "Bearer",
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS)   // expired
            );

            A2AFmeaReport report = analyzer.analyzeAgentPrincipal(p, "workflow:launch");

            assertEquals(2, report.violations().size(), "both FM_A1 and FM_A2 should fire");
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == A2AFailureModeType.FM_A1_AGENT_CREDENTIAL_EXPIRY));
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == A2AFailureModeType.FM_A2_MISSING_SKILL_PERMISSION));
            assertTrue(report.totalRpn() > 0);
        }

        @Test
        @DisplayName("analyzeAgentPrincipal_nullPrincipal_throwsNullPointerException")
        void analyzeAgentPrincipal_nullPrincipal_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeAgentPrincipal(null, "workflow:launch"));
        }

        @Test
        @DisplayName("analyzeAgentPrincipal_nullPermission_throwsNullPointerException")
        void analyzeAgentPrincipal_nullPermission_throwsNullPointerException() {
            AuthenticatedPrincipal p = validPrincipal("agent-A",
                Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeAgentPrincipal(p, null));
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeHandoffToken — FM_A3 and FM_A4")
    class AnalyzeHandoffToken {

        @Test
        @DisplayName("cleanToken_distinctAgents_returnsGreenReport")
        void cleanToken_distinctAgents_returnsGreenReport() {
            HandoffToken token = validToken("agent-A", "agent-B");

            A2AFmeaReport report = analyzer.analyzeHandoffToken(token);

            assertTrue(report.isClean(), "valid token with distinct agents should produce GREEN");
            assertEquals("GREEN", report.status());
            assertEquals(0, report.totalRpn());
        }

        @Test
        @DisplayName("expiredToken_triggersFM_A3")
        void expiredToken_triggersFM_A3() {
            HandoffToken token = expiredToken("agent-A", "agent-B");

            A2AFmeaReport report = analyzer.analyzeHandoffToken(token);

            assertFalse(report.isClean());
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == A2AFailureModeType.FM_A3_HANDOFF_TOKEN_EXPIRY),
                "expired token must trigger FM_A3");
        }

        @Test
        @DisplayName("validToken_doesNotTriggerFM_A3")
        void validToken_doesNotTriggerFM_A3() {
            HandoffToken token = validToken("agent-A", "agent-B");

            A2AFmeaReport report = analyzer.analyzeHandoffToken(token);

            assertFalse(report.violations().stream()
                .anyMatch(v -> v.mode() == A2AFailureModeType.FM_A3_HANDOFF_TOKEN_EXPIRY),
                "valid token must not trigger FM_A3");
        }

        @Test
        @DisplayName("selfReferenceToken_triggersFM_A4")
        void selfReferenceToken_triggersFM_A4() {
            HandoffToken token = validToken("agent-A", "agent-A");   // same agent

            A2AFmeaReport report = analyzer.analyzeHandoffToken(token);

            assertFalse(report.isClean());
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == A2AFailureModeType.FM_A4_HANDOFF_SELF_REFERENCE),
                "fromAgent == toAgent must trigger FM_A4");
        }

        @Test
        @DisplayName("distinctAgents_doesNotTriggerFM_A4")
        void distinctAgents_doesNotTriggerFM_A4() {
            HandoffToken token = validToken("agent-A", "agent-B");

            A2AFmeaReport report = analyzer.analyzeHandoffToken(token);

            assertFalse(report.violations().stream()
                .anyMatch(v -> v.mode() == A2AFailureModeType.FM_A4_HANDOFF_SELF_REFERENCE),
                "distinct agents must not trigger FM_A4");
        }

        @Test
        @DisplayName("expiredAndSelfReference_triggersBothFM_A3andFM_A4")
        void expiredAndSelfReference_triggersBothViolations() {
            HandoffToken token = expiredToken("agent-A", "agent-A");

            A2AFmeaReport report = analyzer.analyzeHandoffToken(token);

            assertEquals(2, report.violations().size(), "both FM_A3 and FM_A4 should fire");
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == A2AFailureModeType.FM_A3_HANDOFF_TOKEN_EXPIRY));
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == A2AFailureModeType.FM_A4_HANDOFF_SELF_REFERENCE));
        }

        @Test
        @DisplayName("analyzeHandoffToken_nullToken_throwsNullPointerException")
        void analyzeHandoffToken_nullToken_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeHandoffToken(null));
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeSkillAccess — FM_A5 and FM_A6")
    class AnalyzeSkillAccess {

        private static final Set<String> REGISTRY = Set.of(
            "launch_workflow", "query_workflows", "manage_workitems", "handoff_workitem");

        @Test
        @DisplayName("registeredSkill_withCorrectPermission_returnsGreenReport")
        void registeredSkill_withCorrectPermission_returnsGreenReport() {
            A2AFmeaReport report = analyzer.analyzeSkillAccess(
                "launch_workflow",
                REGISTRY,
                Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
                AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH
            );

            assertTrue(report.isClean());
            assertEquals("GREEN", report.status());
        }

        @Test
        @DisplayName("unregisteredSkill_triggersFM_A5")
        void unregisteredSkill_triggersFM_A5() {
            A2AFmeaReport report = analyzer.analyzeSkillAccess(
                "unknown_skill",
                REGISTRY,
                Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
                AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH
            );

            assertFalse(report.isClean());
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == A2AFailureModeType.FM_A5_SKILL_NOT_REGISTERED),
                "unregistered skill must trigger FM_A5");
        }

        @Test
        @DisplayName("unregisteredSkill_doesNotTriggerFM_A6")
        void unregisteredSkill_doesNotAlsoTriggerFM_A6() {
            // FM_A6 should not fire when the skill is not registered (FM_A5 covers it)
            A2AFmeaReport report = analyzer.analyzeSkillAccess(
                "unknown_skill",
                REGISTRY,
                Set.of(),   // no permissions — but FM_A6 should be suppressed
                "workflow:launch"
            );

            assertEquals(1, report.violations().size(), "only FM_A5 should fire");
            assertEquals(A2AFailureModeType.FM_A5_SKILL_NOT_REGISTERED,
                report.violations().get(0).mode());
        }

        @Test
        @DisplayName("registeredSkill_withMissingPermission_triggersFM_A6")
        void registeredSkill_withMissingPermission_triggersFM_A6() {
            A2AFmeaReport report = analyzer.analyzeSkillAccess(
                "launch_workflow",
                REGISTRY,
                Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),   // only query, not launch
                AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH
            );

            assertFalse(report.isClean());
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == A2AFailureModeType.FM_A6_INSUFFICIENT_SKILL_PERMISSION),
                "registered skill with missing permission must trigger FM_A6");
        }

        @Test
        @DisplayName("registeredSkill_withWildcardPermission_doesNotTriggerFM_A6")
        void registeredSkill_withWildcardPermission_doesNotTriggerFM_A6() {
            A2AFmeaReport report = analyzer.analyzeSkillAccess(
                "launch_workflow",
                REGISTRY,
                Set.of(AuthenticatedPrincipal.PERM_ALL),   // wildcard
                AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH
            );

            assertTrue(report.isClean(),
                "wildcard permission must satisfy any skill permission check");
        }

        @Test
        @DisplayName("registeredSkill_withExactPermission_doesNotTriggerFM_A6")
        void registeredSkill_withExactPermission_doesNotTriggerFM_A6() {
            A2AFmeaReport report = analyzer.analyzeSkillAccess(
                "manage_workitems",
                REGISTRY,
                Set.of(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
                AuthenticatedPrincipal.PERM_WORKITEM_MANAGE
            );

            assertTrue(report.isClean(), "exact required permission must not trigger FM_A6");
        }

        @Test
        @DisplayName("analyzeSkillAccess_nullSkillId_throwsNullPointerException")
        void analyzeSkillAccess_nullSkillId_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeSkillAccess(
                    null, REGISTRY,
                    Set.of("workflow:launch"), "workflow:launch"));
        }

        @Test
        @DisplayName("analyzeSkillAccess_nullRegisteredIds_throwsNullPointerException")
        void analyzeSkillAccess_nullRegisteredIds_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeSkillAccess(
                    "launch_workflow", null,
                    Set.of("workflow:launch"), "workflow:launch"));
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeAuthConfiguration — FM_A7")
    class AnalyzeAuthConfiguration {

        @Test
        @DisplayName("noSchemesConfigured_triggersFM_A7")
        void noSchemesConfigured_triggersFM_A7() {
            A2AFmeaReport report = analyzer.analyzeAuthConfiguration(Set.of());

            assertFalse(report.isClean());
            assertEquals("RED", report.status());
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == A2AFailureModeType.FM_A7_NO_AUTH_SCHEME_CONFIGURED),
                "empty scheme set must trigger FM_A7");
        }

        @Test
        @DisplayName("oneSchemesConfigured_returnsGreenReport")
        void oneSchemesConfigured_returnsGreenReport() {
            A2AFmeaReport report = analyzer.analyzeAuthConfiguration(Set.of("Bearer"));

            assertTrue(report.isClean(), "at least one scheme must produce GREEN");
            assertEquals("GREEN", report.status());
        }

        @Test
        @DisplayName("multipleSchemes_returnsGreenReport")
        void multipleSchemes_returnsGreenReport() {
            A2AFmeaReport report = analyzer.analyzeAuthConfiguration(
                Set.of("Bearer", "ApiKey", "mTLS"));

            assertTrue(report.isClean());
            assertEquals(0, report.totalRpn());
        }

        @Test
        @DisplayName("analyzeAuthConfiguration_nullSchemes_throwsNullPointerException")
        void analyzeAuthConfiguration_nullSchemes_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeAuthConfiguration(null));
        }

        @Test
        @DisplayName("immutableSetAsInput_handledSafely")
        void immutableSetAsInput_handledSafely() {
            // Unmodifiable set (e.g. from Set.of) must not cause any unexpected behaviour
            Set<String> immutableSchemes = Set.copyOf(Set.of("Bearer"));
            A2AFmeaReport report = analyzer.analyzeAuthConfiguration(immutableSchemes);

            assertTrue(report.isClean());
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("A2AFailureModeType — RPN values")
    class FailureModeTypeRpn {

        @Test
        @DisplayName("allModesHavePositiveRpn")
        void allModesHavePositiveRpn() {
            for (A2AFailureModeType mode : A2AFailureModeType.values()) {
                assertTrue(mode.rpn() > 0,
                    mode.name() + " must have positive RPN");
            }
        }

        @Test
        @DisplayName("rpnEqualsProductOfSOD")
        void rpnEqualsProductOfSOD() {
            for (A2AFailureModeType mode : A2AFailureModeType.values()) {
                int expected = mode.getSeverity() * mode.getOccurrence() * mode.getDetection();
                assertEquals(expected, mode.rpn(),
                    mode.name() + " RPN must equal S×O×D");
            }
        }

        @Test
        @DisplayName("allModesHaveNonBlankDescriptionAndMitigation")
        void allModesHaveNonBlankDescriptionAndMitigation() {
            for (A2AFailureModeType mode : A2AFailureModeType.values()) {
                assertFalse(mode.getDescription().isBlank(),
                    mode.name() + " must have non-blank description");
                assertFalse(mode.getMitigation().isBlank(),
                    mode.name() + " must have non-blank mitigation");
            }
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("A2AFmeaReport — report behaviour")
    class ReportBehaviour {

        @Test
        @DisplayName("violations_listIsImmutable")
        void violations_listIsImmutable() {
            A2AFmeaReport report = analyzer.analyzeHandoffToken(
                expiredToken("agent-A", "agent-A"));   // 2 violations
            assertThrows(UnsupportedOperationException.class,
                () -> report.violations().clear(),
                "violations list must be immutable");
        }

        @Test
        @DisplayName("status_greenWhenNoViolations")
        void status_greenWhenNoViolations() {
            A2AFmeaReport report = analyzer.analyzeAuthConfiguration(Set.of("Bearer"));
            assertEquals("GREEN", report.status());
        }

        @Test
        @DisplayName("status_redWhenAnyViolation")
        void status_redWhenAnyViolation() {
            A2AFmeaReport report = analyzer.analyzeAuthConfiguration(Set.of());
            assertEquals("RED", report.status());
        }

        @Test
        @DisplayName("totalRpn_sumsAllViolationRpns")
        void totalRpn_sumsAllViolationRpns() {
            // FM_A3 (RPN=105) + FM_A4 (RPN=80) = 185
            A2AFmeaReport report = analyzer.analyzeHandoffToken(
                expiredToken("agent-A", "agent-A"));

            int expectedRpn = A2AFailureModeType.FM_A3_HANDOFF_TOKEN_EXPIRY.rpn()
                            + A2AFailureModeType.FM_A4_HANDOFF_SELF_REFERENCE.rpn();
            assertEquals(expectedRpn, report.totalRpn());
        }

        @Test
        @DisplayName("analyzedAt_isRecentTimestamp")
        void analyzedAt_isRecentTimestamp() {
            Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);
            A2AFmeaReport report = analyzer.analyzeAuthConfiguration(Set.of("Bearer"));
            Instant after  = Instant.now().plus(1, ChronoUnit.SECONDS);

            assertTrue(report.analyzedAt().isAfter(before),
                "analyzedAt should be after test start");
            assertTrue(report.analyzedAt().isBefore(after),
                "analyzedAt should be before test end");
        }
    }
}
